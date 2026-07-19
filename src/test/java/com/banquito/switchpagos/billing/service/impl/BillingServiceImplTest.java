package com.banquito.switchpagos.billing.service.impl;

import com.banquito.switchpagos.billing.client.CoreBankingClient;
import com.banquito.switchpagos.billing.dto.event.BatchLinesCompletedEvent;
import com.banquito.switchpagos.billing.dto.request.CoreCommissionChargeRequest;
import com.banquito.switchpagos.billing.dto.response.CoreCommissionChargeResponse;
import com.banquito.switchpagos.billing.exception.CoreBankingClientException;
import com.banquito.switchpagos.billing.mapper.BillingMapper;
import com.banquito.switchpagos.billing.model.BatchBilling;
import com.banquito.switchpagos.billing.model.BillingCoreRequest;
import com.banquito.switchpagos.billing.model.CommissionCalculation;
import com.banquito.switchpagos.billing.repository.BatchBillingRepository;
import com.banquito.switchpagos.billing.repository.BillingCoreRequestRepository;
import com.banquito.switchpagos.billing.repository.CommissionCalculationRepository;
import com.banquito.switchpagos.billing.service.BillingCompletedEventPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class BillingServiceImplTest {

    private BatchBillingRepository batchBillingRepository;
    private CommissionCalculationRepository commissionCalculationRepository;
    private BillingCoreRequestRepository billingCoreRequestRepository;
    private CoreBankingClient coreBankingClient;
    private BillingCompletedEventPublisher eventPublisher;
    private BillingMapper billingMapper;
    private BillingServiceImpl service;

    @BeforeEach
    void setUp() {
        batchBillingRepository = mock(BatchBillingRepository.class);
        commissionCalculationRepository = mock(CommissionCalculationRepository.class);
        billingCoreRequestRepository = mock(BillingCoreRequestRepository.class);
        coreBankingClient = mock(CoreBankingClient.class);
        eventPublisher = mock(BillingCompletedEventPublisher.class);
        billingMapper = mock(BillingMapper.class);

        service = new BillingServiceImpl(
                batchBillingRepository, commissionCalculationRepository,
                billingCoreRequestRepository, coreBankingClient,
                eventPublisher, billingMapper,
                "1792103456001", "0010000010599");
    }

    // ==================== CASOS NEGATIVOS (70%) ====================

    @Test
    void shouldRejectNullEvent() {
        assertThrows(IllegalArgumentException.class, () -> service.processBatchLinesCompleted(null));
        verify(batchBillingRepository, never()).save(any());
    }

    @Test
    void shouldRejectEventWithoutEventId() {
        BatchLinesCompletedEvent event = createValidEvent();
        event.setEventId(null);
        assertThrows(IllegalArgumentException.class, () -> service.processBatchLinesCompleted(event));
        verify(batchBillingRepository, never()).save(any());
    }

    @Test
    void shouldRejectEventWithoutBatchId() {
        BatchLinesCompletedEvent event = createValidEvent();
        event.setBatchId(null);
        assertThrows(IllegalArgumentException.class, () -> service.processBatchLinesCompleted(event));
        verify(batchBillingRepository, never()).save(any());
    }

    @Test
    void shouldRejectEventWithoutCorrelationId() {
        BatchLinesCompletedEvent event = createValidEvent();
        event.setCorrelationId(null);
        assertThrows(IllegalArgumentException.class, () -> service.processBatchLinesCompleted(event));
        verify(batchBillingRepository, never()).save(any());
    }

    @Test
    void shouldIgnoreDuplicateEvent() {
        BatchLinesCompletedEvent event = createValidEvent();
        BatchBilling existing = new BatchBilling();

        when(batchBillingRepository.findByBatchId(event.getBatchId())).thenReturn(Optional.of(existing));

        service.processBatchLinesCompleted(event);

        verify(coreBankingClient, never()).requestCommissionCharge(any());
        verify(eventPublisher, never()).publish(any());
    }

    @Test
    void shouldHandleMissingCoreFundingId() {
        BatchLinesCompletedEvent event = createValidEvent();
        event.setCoreFundingId(null);
        BatchBilling billing = createBilling(event);
        billing.setCoreFundingId(null);

        when(batchBillingRepository.findByBatchId(event.getBatchId())).thenReturn(Optional.empty());
        when(billingMapper.toBatchBilling(any(), any(), any(), any(), any(), any(), any(), any())).thenReturn(billing);

        service.processBatchLinesCompleted(event);

        verify(coreBankingClient, never()).requestCommissionCharge(any());
        verify(eventPublisher).publish(any());
    }

    @Test
    void shouldHandleCoreException() {
        BatchLinesCompletedEvent event = createValidEvent();
        BatchBilling billing = createBilling(event);
        BillingCoreRequest coreRequestRecord = new BillingCoreRequest();

        when(batchBillingRepository.findByBatchId(event.getBatchId())).thenReturn(Optional.empty());
        when(billingMapper.toBatchBilling(any(), any(), any(), any(), any(), any(), any(), any())).thenReturn(billing);
        when(billingMapper.toCommissionCalculation(any(), any())).thenReturn(new CommissionCalculation());
        when(billingMapper.toBillingCoreRequest(any(), any(), any())).thenReturn(coreRequestRecord);
        when(coreBankingClient.requestCommissionCharge(any()))
                .thenThrow(new CoreBankingClientException("Core error", 500, Boolean.FALSE, new RuntimeException()));

        service.processBatchLinesCompleted(event);

        verify(eventPublisher).publish(any());
    }

    @Test
    void shouldHandleCoreRejectionStatus() {
        BatchLinesCompletedEvent event = createValidEvent();
        BatchBilling billing = createBilling(event);
        BillingCoreRequest coreRequestRecord = new BillingCoreRequest();
        CoreCommissionChargeResponse response = new CoreCommissionChargeResponse();
        response.setStatus("REJECTED");

        when(batchBillingRepository.findByBatchId(event.getBatchId())).thenReturn(Optional.empty());
        when(billingMapper.toBatchBilling(any(), any(), any(), any(), any(), any(), any(), any())).thenReturn(billing);
        when(billingMapper.toCommissionCalculation(any(), any())).thenReturn(new CommissionCalculation());
        when(billingMapper.toBillingCoreRequest(any(), any(), any())).thenReturn(coreRequestRecord);
        when(coreBankingClient.requestCommissionCharge(any())).thenReturn(response);

        service.processBatchLinesCompleted(event);

        verify(eventPublisher).publish(any());
    }

    @Test
    void shouldHandleCoreResponseWithNullStatus() {
        BatchLinesCompletedEvent event = createValidEvent();
        BatchBilling billing = createBilling(event);
        BillingCoreRequest coreRequestRecord = new BillingCoreRequest();
        CoreCommissionChargeResponse response = new CoreCommissionChargeResponse();
        response.setStatus(null);

        when(batchBillingRepository.findByBatchId(event.getBatchId())).thenReturn(Optional.empty());
        when(billingMapper.toBatchBilling(any(), any(), any(), any(), any(), any(), any(), any())).thenReturn(billing);
        when(billingMapper.toCommissionCalculation(any(), any())).thenReturn(new CommissionCalculation());
        when(billingMapper.toBillingCoreRequest(any(), any(), any())).thenReturn(coreRequestRecord);
        when(coreBankingClient.requestCommissionCharge(any())).thenReturn(response);

        service.processBatchLinesCompleted(event);

        verify(eventPublisher).publish(any());
    }

    // ==================== CASOS LÍMITE (20%) ====================

    @Test
    void shouldResolveUnitFeeForZeroLines() {
        BigDecimal fee = service.resolveUnitFee(0);
        assertEquals(new BigDecimal("0.00").setScale(2, RoundingMode.HALF_UP), fee);
    }

    @Test
    void shouldResolveUnitFeeFor10Lines() {
        BigDecimal fee = service.resolveUnitFee(10);
        assertEquals(new BigDecimal("0.50").setScale(2, RoundingMode.HALF_UP), fee);
    }

    @Test
    void shouldResolveUnitFeeFor100Lines() {
        BigDecimal fee = service.resolveUnitFee(100);
        assertEquals(new BigDecimal("0.40").setScale(2, RoundingMode.HALF_UP), fee);
    }

    @Test
    void shouldResolveUnitFeeFor500Lines() {
        BigDecimal fee = service.resolveUnitFee(500);
        assertEquals(new BigDecimal("0.30").setScale(2, RoundingMode.HALF_UP), fee);
    }

    @Test
    void shouldResolveUnitFeeFor1000Lines() {
        BigDecimal fee = service.resolveUnitFee(1000);
        assertEquals(new BigDecimal("0.20").setScale(2, RoundingMode.HALF_UP), fee);
    }

    @Test
    void shouldResolveUnitFeeFor10000Lines() {
        BigDecimal fee = service.resolveUnitFee(10000);
        assertEquals(new BigDecimal("0.10").setScale(2, RoundingMode.HALF_UP), fee);
    }

    @Test
    void shouldResolveUnitFeeForOver10000Lines() {
        BigDecimal fee = service.resolveUnitFee(10001);
        assertEquals(new BigDecimal("0.05").setScale(2, RoundingMode.HALF_UP), fee);
    }

    @Test
    void shouldResolveUnitFeeForNullLines() {
        BigDecimal fee = service.resolveUnitFee(null);
        assertEquals(new BigDecimal("0.00").setScale(2, RoundingMode.HALF_UP), fee);
    }

    // ==================== HAPPY PATH (10%) ====================

    @Test
    void shouldProcessBillingSuccessfully() {
        BatchLinesCompletedEvent event = createValidEvent();
        BatchBilling billing = createBilling(event);
        BillingCoreRequest coreRequestRecord = new BillingCoreRequest();
        CoreCommissionChargeResponse response = new CoreCommissionChargeResponse();
        response.setStatus("APPROVED");
        response.setCoreTransactionId("tx-123");

        when(batchBillingRepository.findByBatchId(event.getBatchId())).thenReturn(Optional.empty());
        when(billingMapper.toBatchBilling(any(), any(), any(), any(), any(), any(), any(), any())).thenReturn(billing);
        when(billingMapper.toCommissionCalculation(any(), any())).thenReturn(new CommissionCalculation());
        when(billingMapper.toBillingCoreRequest(any(), any(), any())).thenReturn(coreRequestRecord);
        when(coreBankingClient.requestCommissionCharge(any())).thenReturn(response);

        service.processBatchLinesCompleted(event);

        verify(batchBillingRepository, atLeast(3)).save(any());
        verify(coreBankingClient).requestCommissionCharge(any());
        verify(eventPublisher).publish(any());
    }

    // ==================== HELPERS ====================

    private BatchLinesCompletedEvent createValidEvent() {
        BatchLinesCompletedEvent event = new BatchLinesCompletedEvent();
        event.setEventId(UUID.randomUUID());
        event.setBatchId(UUID.randomUUID());
        event.setCorrelationId(UUID.randomUUID());
        event.setCompanyRuc("1792103456001");
        event.setSourceAccountNumber("0010000010599");
        event.setCoreFundingId("reservation-123");
        event.setBillableLines(10);
        event.setCurrency("USD");
        return event;
    }

    private BatchBilling createBilling(BatchLinesCompletedEvent event) {
        BatchBilling billing = new BatchBilling();
        billing.setBillingId(UUID.randomUUID());
        billing.setBatchId(event.getBatchId());
        billing.setCorrelationId(event.getCorrelationId());
        billing.setCoreFundingId(event.getCoreFundingId());
        billing.setBillableLines(event.getBillableLines());
        billing.setUnitFee(new BigDecimal("0.50"));
        billing.setCommissionSubtotal(new BigDecimal("5.00"));
        billing.setCurrency("USD");
        billing.setStatus("COMISION_CALCULADA");
        billing.setBillingCompletedEventPublished(false);
        return billing;
    }
}
