package com.hkbea.hth.bff.service;

import com.hkbea.common.exception.ServiceException;
import com.hkbea.hth.api.PaymentApi;
import com.hkbea.hth.bff.exception.HTHBffErrorMapping;
import com.hkbea.hth.bff.maintenance.entity.MaintenanceWindow;
import com.hkbea.hth.bff.maintenance.service.ServiceWindowService;
import com.hkbea.hth.bff.maintenance.service.ThirdPartyWindowService;
import com.hkbea.hth.bff.model.PaymentTransaction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentEnquiryBffTest {
    private static final String REF = "HTH20261111093000000001";
    @Mock private PaymentApi supportPaymentApi;
    @Mock private ThirdPartyWindowService thirdPartyWindowService;
    @Mock private ServiceWindowService serviceWindowService;
    private PaymentServiceImpl service;

    @BeforeEach
    void setUp() {
        new HTHBffErrorMapping();
        service = new PaymentServiceImpl(supportPaymentApi, thirdPartyWindowService, serviceWindowService);
    }

    @Test
    void allFivePaymentTypesPreserveAllEighteenResponseFields() {
        when(thirdPartyWindowService.findActiveForEnquiry()).thenReturn(Optional.empty());
        OffsetDateTime time = OffsetDateTime.parse("2026-11-11T09:30:00+08:00");
        for (com.hkbea.hth.model.PaymentTransaction.PaymentTypeEnum type
                : com.hkbea.hth.model.PaymentTransaction.PaymentTypeEnum.values()) {
            com.hkbea.hth.model.PaymentTransaction source = new com.hkbea.hth.model.PaymentTransaction()
                    .referenceNumber(REF).paymentType(type)
                    .status(com.hkbea.hth.model.PaymentTransaction.StatusEnum.REJECTED)
                    .rejectMessage("Rejected by downstream")
                    .asOf(time).executionDate(time.minusMinutes(1)).initiatedDate(time.minusMinutes(2))
                    .beneficiaryAccountName("BENEFICIARY").beneficiaryAccountNumber("BEN-ACCOUNT")
                    .transferAmount(new BigDecimal("123.45")).transferCurrency("HKD").note("Invoice 123")
                    .remitterAccountNumber("DEBIT-ACCOUNT").remitterAccountType("CUR")
                    .remitterProductName("Current Account").remitterBank("The Bank of East Asia Limited")
                    .remitterAccountCurrency("HKD").externalReferenceId("stored reference");
            when(supportPaymentApi.enquireTransactionRecord(REF)).thenReturn(source);

            PaymentTransaction actual = service.enquireTransactionRecord(REF);

            assertAll(type.value(),
                    () -> assertEquals(REF, actual.getReferenceNumber()),
                    () -> assertEquals(type.value(), actual.getPaymentType().value()),
                    () -> assertEquals(PaymentTransaction.StatusEnum.REJECTED, actual.getStatus()),
                    () -> assertEquals(source.getRejectMessage(), actual.getRejectMessage()),
                    () -> assertEquals(source.getAsOf(), actual.getAsOf()),
                    () -> assertEquals(source.getExecutionDate(), actual.getExecutionDate()),
                    () -> assertEquals(source.getInitiatedDate(), actual.getInitiatedDate()),
                    () -> assertEquals(source.getBeneficiaryAccountName(), actual.getBeneficiaryAccountName()),
                    () -> assertEquals(source.getBeneficiaryAccountNumber(), actual.getBeneficiaryAccountNumber()),
                    () -> assertEquals(source.getTransferAmount(), actual.getTransferAmount()),
                    () -> assertEquals(source.getTransferCurrency(), actual.getTransferCurrency()),
                    () -> assertEquals(source.getNote(), actual.getNote()),
                    () -> assertEquals(source.getRemitterAccountNumber(), actual.getRemitterAccountNumber()),
                    () -> assertEquals(source.getRemitterAccountType(), actual.getRemitterAccountType()),
                    () -> assertEquals(source.getRemitterProductName(), actual.getRemitterProductName()),
                    () -> assertEquals(source.getRemitterBank(), actual.getRemitterBank()),
                    () -> assertEquals(source.getRemitterAccountCurrency(), actual.getRemitterAccountCurrency()),
                    () -> assertEquals(source.getExternalReferenceId(), actual.getExternalReferenceId()));
        }
        verify(supportPaymentApi, times(5)).enquireTransactionRecord(REF);
        verifyNoInteractions(serviceWindowService);
    }

    @Test
    void ipeMaintenanceRejectsBeforeCallingSupportWithExistingCode() {
        MaintenanceWindow window = new MaintenanceWindow();
        window.setSystemName("IPE");
        when(thirdPartyWindowService.findActiveForEnquiry()).thenReturn(Optional.of(window));
        ServiceException ex = assertThrows(ServiceException.class, () -> service.enquireTransactionRecord(REF));
        assertEquals(600211, ex.getErrCode());
        assertEquals(HTHBffErrorMapping.MESSAGE_MAP.get(600211), ex.getMessage());
        verifyNoInteractions(supportPaymentApi, serviceWindowService);
    }

    @Test
    void notFoundErrorIsForwardedWithoutChangingItsCodeOrMessage() {
        when(thirdPartyWindowService.findActiveForEnquiry()).thenReturn(Optional.empty());
        ServiceException missing = new ServiceException(600131);
        when(supportPaymentApi.enquireTransactionRecord(REF)).thenThrow(missing);
        assertSame(missing, assertThrows(ServiceException.class, () -> service.enquireTransactionRecord(REF)));
    }
}
