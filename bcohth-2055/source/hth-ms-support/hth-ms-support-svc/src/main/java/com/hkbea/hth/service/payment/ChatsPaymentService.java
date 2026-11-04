package com.hkbea.hth.service.payment;

import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.hkbea.common.exception.ParameterException;
import com.hkbea.common.exception.ServiceException;
import com.hkbea.common.header.CommonHeader;
import com.hkbea.hth.api.AccountApi;
import com.hkbea.hth.constants.CacheConstants;
import com.hkbea.hth.entity.PartyAccount;
import com.hkbea.hth.entity.PaymentPayoutRecord;
import com.hkbea.hth.exception.HTHSupportErrorMapping;
import com.hkbea.hth.ipe.api.PayemtRoutApi;
import com.hkbea.hth.ipe.model.BatchRoutingCreditTransferRequest;
import com.hkbea.hth.ipe.model.ChatsRemittance;
import com.hkbea.hth.ipe.model.CustomerNotificationInformRequest;
import com.hkbea.hth.ipe.model.Debtor;
import com.hkbea.hth.ipe.model.DebtorAgent;
import com.hkbea.hth.ipe.model.FinancialInstitution;
import com.hkbea.hth.ipe.model.Purpose;
import com.hkbea.hth.ipe.model.SupplementaryData;
import com.hkbea.hth.ipe.model.TransferPaymentResultQueryResponse;
import com.hkbea.hth.mapper.HthPaymentPayoutMapper;
import com.hkbea.hth.mapper.InternalPayeeMapper;
import com.hkbea.hth.mapper.PartyAccountMapper;
import com.hkbea.hth.mapper.PartyPreferenceMapper;
import com.hkbea.hth.model.*;
import com.hkbea.hth.utils.AccountUtils;
import com.hkbea.hth.utils.CurrencyUtils;
import com.hkbea.hth.utils.ScameterValidatorUtil;
import com.hkbea.hth.constants.PaymentConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.*;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;

import com.hkbea.hth.model.Currency;

/**
 * Handles the complete CHATS payment flow: validation, Scameter checking,
 * enrichment of the smart-routing accepted request, IPE submission and payout
 * persistence.
 */
@Service
public class ChatsPaymentService {
    private static final Logger log = LoggerFactory.getLogger(ChatsPaymentService.class);

    //chats service time , Mon - Fri. , 09:00 to 16:30
    private static final LocalTime CHATS_SERVICE_START_TIME = LocalTime.of(9, 0);
    private static final LocalTime CHATS_SERVICE_END_TIME = LocalTime.of(16, 30);
    // CHATS supported currencies：HKD, USD, CNY, EUR
    private static final Set<Currency> CHATS_SUPPORTED_CURRENCIES =
            EnumSet.of(Currency.HKD, Currency.CNY, Currency.USD, Currency.EUR);
    private static final String CHATS_NETWORK = "CHATS";

    private static final String CHATS_PRINT_ADVICE_INDICATOR = "Y";
    private static final String CHATS_LM_INDICATOR = "y";
    //TODO DepositAc2 is bener address , if registered select from bco db DIGX_PY_DOMESTIC_PAYEE ADDRESS_LINE1
    private static final String CHATS_DEPOSIT_ACCOUNT_NAME_2 = "6304357992232185";
    private static final String CHATS_DEBTOR_BANK_BIC = "BEASHKHHXXX";
    private static final String CHATS_PURPOSE = "/IFT01/";

    @Autowired
    private PayemtRoutApi payemtRoutApi;
    @Autowired
    private ScameterValidatorUtil scameterValidatorUtil;
    @Autowired
    private AccountApi accountApi;
    @Autowired
    private PartyAccountMapper partyAccountMapper;
    @Autowired
    private InternalPayeeMapper internalPayeeMapper;
    @Autowired
    private HthPaymentPayoutMapper paymentPayoutRecordMapper;
    @Autowired
    private PartyPreferenceMapper partyPreferenceMapper;
    @Autowired
    private CommonHeader headers;
    @Autowired
    private PaymentCommonService paymentCommonService;
    @Autowired
    private CacheManager cacheManager;

    /**
     * CHATS payment flow: validate, enrich the routing request accepted by
     * smart-routing in place, then submit and persist.
     */
    public Payment processPayment(LocalPayment localPayment, BatchRoutingCreditTransferRequest routingRequest) {
        if (localPayment == null) {
            throw logAndCreateException(HTHSupportErrorMapping.MISSING_MANDATORY_PARAMETER);
        }
        PaymentValidationSupport.validateChatsRules(localPayment);
        PaymentValidationSupport.useDebitCurrencyForCreditorAccount(localPayment);
        validateChats(localPayment);
        scameterValidatorUtil.checkChats(localPayment);
        applyChatsRoutingDetails(routingRequest, localPayment);
        return buildChatsResponse(localPayment, routingRequest);
    }

    /**
     * Writes the CHATS-specific blocks onto the routing request that was already
     * validated by smart-routing. Preserve the customer instruction fields
     * and enrich the bank-owned fields required for the outbound CHATS message.
     */
    private void applyChatsRoutingDetails(BatchRoutingCreditTransferRequest routingRequest,
                                          LocalPayment localPayment) {
        PaymentRoutingPayload.applyTypeEnvelope(routingRequest,
                PaymentConstants.BG3_CHANNEL_REFERENCE_OUTWARD, CHATS_NETWORK);
        var txn = PaymentRoutingPayload.firstTransaction(routingRequest);

        String debitCurrency = localPayment.getFromAccount().getCurrency();
        String debitExternalAccountNo = localPayment.getFromAccount().getAccountNumber();
        String debitInternalAccountNo = AccountUtils.ext2intAccNo(
                debitExternalAccountNo,
                localPayment.getFromAccount().getAccountType().value().substring(0, 1));
        String productCode = partyAccountMapper.selectProductCode(debitInternalAccountNo, localPayment.getPartyId());
        PartyAccount partyAccount = StringUtils.isBlank(productCode) ? null
                : partyAccountMapper.selectAccountStatus(
                localPayment.getPartyId(), debitExternalAccountNo, productCode);
        String payerName = partyPreferenceMapper.selectCustomerNameByPartyId(localPayment.getPartyId());

        txn.setRemittanceInformation(PaymentRoutingPayload.remittance(
                PaymentConstants.BG3_CUSTOMER_REMARKS_OUTWARD, localPayment.getPaymentDetails()));

        // CHATS uses a bank-owned purpose, regardless of the caller's category purpose.
        Purpose purpose = new Purpose();
        purpose.setProprietary(CHATS_PURPOSE);
        txn.setPurpose(purpose);

        DebtorAgent debtorAgent = new DebtorAgent();
        FinancialInstitution debtorBank = new FinancialInstitution();
        debtorBank.setBicfi(CHATS_DEBTOR_BANK_BIC);
        debtorAgent.setFinancialInstitution(debtorBank);
        txn.setDebtorAgent(debtorAgent);

        // Charges: CHATS carries the OVR indicator as bearer plus the HKD/other fee code.
        Currency currency = localPayment.getTxnAmount().getCurrency();
        String chargeBearer = localPayment.getChargesOption().value().substring(0, 1);
        String feeCode = Currency.HKD == currency
                ? PaymentConstants.FEE_CODE_FPS_HKD : PaymentConstants.FEE_CODE_FPS_OTHER;
        txn.setCharges(PaymentRoutingPayload.charges(null, null, chargeBearer, feeCode));

        // Retain any trusted debtor address already populated in the routed request.
        if (txn.getDebtor() == null) {
            txn.setDebtor(new Debtor());
        }
        txn.getDebtor().setType(Debtor.TypeEnum.CORPORATE);
        txn.getDebtor().setName(payerName);
        txn.setDebtorAccount(PaymentRoutingPayload.debtorAccount(debitInternalAccountNo,
                debitInternalAccountNo, null, PaymentConstants.PAYER_ACCOUNT_TYPE_CSA, debitCurrency,
                PaymentRoutingPayload.parseCompanyCode(partyAccount == null ? null : partyAccount.getCompanyCode()),
                productCode));

        PostalAddress creditorAddress = localPayment.getCreditor().getPostalAddress();
        String creditorName = localPayment.getCreditor().getCreditorName();
        txn.setCreditor(PaymentRoutingPayload.creditor(
                com.hkbea.hth.ipe.model.Creditor.TypeEnum.ACCOUNT, creditorName, null, null,
                creditorAddress.getAddressLine1(), creditorAddress.getAddressLine2(),
                creditorAddress.getTownName(), creditorAddress.getCountry()));
        txn.setCreditorAccount(PaymentRoutingPayload.creditorAccount(creditorName,
                localPayment.getToAccount().getAccountNumber(),
                localPayment.getToAccount().getCurrency().toString(), null, null,
                creditorAddress.getCountry(), null));

        String memberId = localPayment.getCreditorAgent().getFinancialInstitutionId().getClearingSystemMemberId();
        String bicfi = localPayment.getCreditorAgent().getFinancialInstitutionId().getBicfi();
        txn.setCreditorAgent(PaymentRoutingPayload.creditorAgent(bicfi, memberId, null,
                creditorAddress.getAddressLine1(), creditorAddress.getAddressLine2(), null,  creditorAddress.getCountry()));

        String partyId = StringUtils.isEmpty(localPayment.getPartyId())
                ? PaymentConstants.MOCK_PARTY_ID : localPayment.getPartyId();
        String payeeId = partyId != null && !PaymentConstants.MOCK_PARTY_ID.equals(partyId)
                ? internalPayeeMapper.selectDomesticPayeeId(partyId, localPayment.getToAccount().getAccountNumber())
                : null;
        localPayment.setPayeeId(payeeId);

        ChatsRemittance chatsRemittance = new ChatsRemittance();
        chatsRemittance.setChatAcTpCode(findPreDesignatedAccounts(localPayment.getPartyId(), localPayment.getToAccount().getId()));
        chatsRemittance.setPrntAdvInd(CHATS_PRINT_ADVICE_INDICATOR);
        chatsRemittance.setLmInd(CHATS_LM_INDICATOR);
        chatsRemittance.setDepositAccountName1(creditorName);
        chatsRemittance.setDepositAccountName2(CHATS_DEPOSIT_ACCOUNT_NAME_2);
        chatsRemittance.setPinCnt(0);

        SupplementaryData supplementaryData = new SupplementaryData();
        supplementaryData.setDebitInternalAccount(debitInternalAccountNo);
        supplementaryData.setProductType(productCode);
        supplementaryData.setHdrCibExtAcNo(localPayment.getPartyId());
        supplementaryData.setHdrCibIntAcNo(AccountUtils.ext2intAccNo(localPayment.getPartyId(), ""));
        supplementaryData.setChatsRemittance(chatsRemittance);
        txn.setSupplementaryData(supplementaryData);

        CustomerNotificationInformRequest notification = paymentCommonService.buildCustomerNotificationInfo(
                localPayment, PaymentConstants.NOTIFICATION_TYPE_EMAIL_SMS, localPayment.getPayeeId());
        txn.setNotification(PaymentRoutingPayload.notification(notification));
    }

    private String findPreDesignatedAccounts(String partyId, String toAccountId) {
        return internalPayeeMapper.findPreDesignatedAccounts(partyId, toAccountId) > 0 ? "98" : "";
    }

    /**
     * CHATS validation orchestrator: debtor account, amount, creditor account,
     * chargesOption, currency consistency, then balance.
     */
    private void validateChats(LocalPayment chats) {
        if (chats == null) {
            throw logAndCreateException(HTHSupportErrorMapping.MISSING_MANDATORY_PARAMETER);
        }

        validateFromAccount(chats.getFromAccount(), chats.getPartyId());
        validateTxnAmount(chats.getTxnAmount());
        validateChatsToAccount(chats);
        validateChargesOption(chats.getChargesOption());
        //validate chats service time
        validateServiceTime();
        validateChatsCurrencyConsistency(chats);
        validateSufficientBalance(
                AccountUtils.ext2intAccNo(
                        chats.getFromAccount().getAccountNumber(),
                        chats.getFromAccount().getAccountType().value().substring(0, 1)),
                chats.getFromAccount().getCurrency().toString(),
                chats.getTxnAmount().getAmount());
    }

    /**
     * Debtor account checks: 14-15 digit "015" account, valid currency/type,
     * and product/status lookup (rejects closed accounts).
     */
    private void validateFromAccount(BeaAccount fromAccount , String partyId) {
        if (fromAccount == null) {
            throw logAndCreateException(HTHSupportErrorMapping.DEBIT_ACCOUNT_MISSING);
        }
        validateLocalAccountNumber(fromAccount.getAccountNumber());
        validateCurrency(CurrencyUtils.parseLenient(fromAccount.getCurrency()), HTHSupportErrorMapping.DEBIT_CURRENCY_MISSING_OR_INVALID);
        if (fromAccount.getAccountType() == null) {
            throw logAndCreateException(HTHSupportErrorMapping.DEBIT_ACCOUNT_TYPE_MISSING);
        }
        String debitInternalAccountNo = AccountUtils.ext2intAccNo(
                fromAccount.getAccountNumber(), fromAccount.getAccountType().value().substring(0, 1));
        String productCode = partyAccountMapper.selectProductCode(debitInternalAccountNo, partyId);
        PartyAccount partyAccount = StringUtils.isBlank(productCode) ? null
                : partyAccountMapper.selectAccountStatus(
                partyId, fromAccount.getAccountNumber(), productCode);
        if (partyAccount == null) {
            log.warn("Debit account does not exist, partyId={}, accountNumber={}, productCode={}",
                    partyId, fromAccount.getAccountNumber(), productCode);
            throw new ServiceException(HTHSupportErrorMapping.ACCOUNT_NOT_FOUND_EXCEPTION);
        }
        if (PaymentConstants.ACCOUNT_STATUS_CLOSED.equalsIgnoreCase(partyAccount.getStatus())) {
            log.warn("Debit account status is invalid, partyId={}, accountNumber={}, productCode={}, status={}",
                    partyId, fromAccount.getAccountNumber(), productCode, partyAccount.getStatus());
            throw new ServiceException(HTHSupportErrorMapping.FPS_DEBIT_ACCOUNT_STATUS_INVALID);
        }
    }

    /**
     * CHATS service window: submissions are only accepted Mon-Fri within
     * CHATS_SERVICE_START_TIME..CHATS_SERVICE_END_TIME.
     */
    private void validateServiceTime(){
        LocalDateTime now = LocalDateTime.now();
        DayOfWeek dayOfWeek = now.getDayOfWeek();
        LocalTime time = now.toLocalTime();
        if (dayOfWeek == DayOfWeek.SATURDAY || dayOfWeek == DayOfWeek.SUNDAY
                || time.isBefore(CHATS_SERVICE_START_TIME) || time.isAfter(CHATS_SERVICE_END_TIME)){
            throw logAndCreateException(HTHSupportErrorMapping.CHATS_TIMT_SERVICE_NOT_AVAILABLE);
        }
    }

    /**
     * Creditor account checks: account number format, currency and payee name.
     */
    private void validateChatsToAccount(LocalPayment chats) {
        PaymentToAccount toAccount = chats.getToAccount();
        if (toAccount == null) {
            throw logAndCreateException(HTHSupportErrorMapping.CREDITOR_ACCOUNT_MISSING);
        }

        validateAccountNumber(toAccount.getAccountNumber());
        validateCurrency(toAccount.getCurrency(), HTHSupportErrorMapping.CREDITOR_CURRENCY_MISSING_OR_INVALID);
        validateAccountName(chats.getCreditor().getCreditorName());
    }

    /**
     * chargeBearer presence check (OUR/SHA rules are validated by the
     * type-specific rules already).
     */
    private void validateChargesOption(LocalPayment.ChargesOptionEnum chargesOption) {
        if (chargesOption == null) {
            throw logAndCreateException(HTHSupportErrorMapping.CHARGES_OPTION_MISSING_OR_INVALID);
        }
    }

    /**
     * CHATS currency rules: all three currencies must be CHATS-supported
     * (HKD/CNY/USD/EUR), and debit, payment and creditor currencies must match.
     */
    private void validateChatsCurrencyConsistency(LocalPayment chats) {
        Currency fromCurrency = CurrencyUtils.parseLenient(chats.getFromAccount().getCurrency());
        Currency txnCurrency = chats.getTxnAmount().getCurrency();
        Currency toCurrency = chats.getToAccount().getCurrency();

        if (!CHATS_SUPPORTED_CURRENCIES.contains(fromCurrency)) {
            throw logAndCreateException(HTHSupportErrorMapping.CHATS_DEBIT_CURRENCY_NOT_SUPPORTED);
        }
        if (!CHATS_SUPPORTED_CURRENCIES.contains(txnCurrency)) {
            throw logAndCreateException(HTHSupportErrorMapping.CHATS_PAYMENT_CURRENCY_NOT_SUPPORTED);
        }
        if (!CHATS_SUPPORTED_CURRENCIES.contains(toCurrency)) {
            throw logAndCreateException(HTHSupportErrorMapping.CHATS_CREDITOR_CURRENCY_NOT_SUPPORTED);
        }
        if (fromCurrency != txnCurrency) {
            throw logAndCreateException(HTHSupportErrorMapping.PAYER_PAYEE_CURRENCY_NO_MATCHES);
        }
        if (txnCurrency != toCurrency) {
            throw logAndCreateException(HTHSupportErrorMapping.PAYMENT_BENEFICIARY_CURRENCY_NO_MATCHES);
        }
    }

    /**
     * Persists a pending payout record, submits the enriched routing request
     * to IPE, then updates the record with the result and returns the response.
     */
    private Payment buildChatsResponse(LocalPayment chats, BatchRoutingCreditTransferRequest routingRequest) {
        Payment payment = new Payment();
        String transactionId = PaymentRoutingPayload.endToEndId(routingRequest);
        payment.setTxnRef(transactionId);
        String payoutId = insertPendingPayoutRecord(chats, routingRequest);
        TransferPaymentResultQueryResponse result = PaymentServiceSupport.submitRoutingRequest(
                payemtRoutApi, log, routingRequest, payment, null,
                PaymentResponseSupport.RejectMessagePolicy.ERROR_MESSAGE_WITH_FALLBACK);
        payment.setTxnTime(OffsetDateTime.now().withNano(0));
        payment.setOriginalEndtoendID(chats.getPaymentIdentification().getEndToEndId());
        payment.setOriginalTransactionID(chats.getTransactionID());
        PaymentServiceSupport.updatePayoutRecord(paymentPayoutRecordMapper, log, CHATS_NETWORK, payoutId, payment, result);
        savePaymentFee(result);
        return payment;
    }

    private void savePaymentFee(TransferPaymentResultQueryResponse result) {
        try {
            String messageId = headers.getMessageId();
            Cache cache = cacheManager.getCache(CacheConstants.OPCODE_MAPPING);
            if (cache != null) {
                cache.put(messageId, result.getOverChargeCcy() + ":" + result.getOverChargeAmount());
            }
        } catch (Exception e) {
            log.warn("Redis cache PUT failed for savePaymentFee messageId={}. OverCharge={}", result.getOverChargeCcy(), result.getOverChargeAmount());
        }
    }

    /**
     * Inserts a pending payout record into HTH_PAYMENT_PAYOUT before the IPE submission.
     * The batch/end-to-end identifiers come from the routing request that was
     * validated by smart-routing; MESSAGE_ID from the inbound header; IPE_REF_NO
     * is left null until the IPE response is available. Remaining unknown fields are
     * filled with placeholder values to be updated after the IPE response.
     */
    private String insertPendingPayoutRecord(LocalPayment req, BatchRoutingCreditTransferRequest routingRequest) {
        String batchId = PaymentRoutingPayload.batchId(routingRequest);
        String customerTransactionId = PaymentRoutingPayload.endToEndId(routingRequest);
        String prodCode = PaymentRoutingPayload.debtorProductCode(routingRequest);
        String id = UUID.randomUUID().toString().replace("-", "");

        String partyId = req.getPartyId();
        if (partyId == null || partyId.isBlank()) {
            partyId = req.getPartyId() != null && !req.getPartyId().isBlank() ? req.getPartyId() : PaymentConstants.MOCK_PARTY_ID;
        }
        String payeeId = partyId != null && !PaymentConstants.MOCK_PARTY_ID.equals(partyId)
                ? internalPayeeMapper.selectDomesticPayeeId(
                partyId, req.getToAccount().getAccountNumber())
                : null;
        PaymentToAccount toAccount = req.getToAccount();

        try {
            PaymentPayoutRecord entity = new PaymentPayoutRecord();
            entity.setId(id);
            // key identifiers (IPE_REF_NO stays null until the IPE response arrives)
            entity.setMessageId(headers.getMessageId());
            entity.setPaymentId(customerTransactionId);
            entity.setCustomerTransactionId(req.getTransactionID());
            entity.setEndToEndId(batchId);
            entity.setExecutionDate(req.getSettlementDate());
            entity.setCreditBicCode(req.getCreditorAgent().getFinancialInstitutionId().getBicfi());
            entity.setCreditMemberId(req.getCreditorAgent().getFinancialInstitutionId().getClearingSystemMemberId());

            entity.setCreditAddressLine1(req.getCreditor().getPostalAddress().getAddressLine1());
            entity.setCreditAddressLine2(req.getCreditor().getPostalAddress().getAddressLine2());
            entity.setCreditTownName(req.getCreditor().getPostalAddress().getTownName());
            entity.setCreditCountryCode(req.getCreditor().getPostalAddress().getCountry());
            entity.setIpeRefNo(null);
            // audit / time fields
            entity.setPaymentDate(LocalDateTime.now());
            entity.setCreatedBy(PaymentConstants.SYSTEM_USER);
            entity.setCreationDate(LocalDateTime.now());
            entity.setLastUpdatedBy(PaymentConstants.SYSTEM_USER);
            entity.setLastUpdatedDate(LocalDateTime.now());
            // payment type
            entity.setPaymentType(CHATS_NETWORK);
            // party / payee
            entity.setPartyId(partyId);
            entity.setPartyName(PaymentConstants.MOCK_PARTY_NAME);
            entity.setPayeeId(payeeId);
            entity.setPayeeIdfn(PaymentConstants.PAYEE_IDFN_ACCTNO);
            // debit account
            entity.setDebitAccountId(req.getFromAccount().getAccountNumber());
            entity.setDebitAccountCur(req.getFromAccount().getCurrency());
            entity.setDebitAccountType(req.getFromAccount().getAccountType().value());
            entity.setProdCode(prodCode);
            // credit account
            entity.setCreditAccountId(toAccount.getAccountNumber());
            entity.setCreditAccountName(PaymentServiceSupport.accountNameOrFallback(req));
            entity.setCreditAccountType(toAccount.getAccountType() != null ? toAccount.getAccountType().value() : null);
            entity.setCreditAccountCur(toAccount.getCurrency().toString());
            // amount
            entity.setInstdAmount(req.getTxnAmount().getAmount());
            entity.setInstdCurrency(req.getTxnAmount().getCurrency().toString());
            entity.setHkdEquivalentAmount(req.getTxnAmount().getAmount());
            // service charge
            entity.setSvcCur(req.getTxnAmount().getCurrency().toString());
            entity.setSvcAmount(PaymentConstants.MOCK_SVC_AMOUNT);
            entity.setSvcType(req.getChargesOption().toString());
            // remarks
            entity.setRemarks(PaymentServiceSupport.joinPaymentDetails(req.getPaymentDetails()));
            // status (pending before IPE)
            entity.setStatus(Payment.StatusEnum.PROCESSING.value());
            entity.setIpeStatus(null);
            entity.setIpeStatusReason(null);
            entity.setStatusFlag(PaymentConstants.MOCK_OBJECT_STATUS);
            if (paymentPayoutRecordMapper.insert(entity) != 1) {
                throw new ServiceException(HTHSupportErrorMapping.GENERAL_SERVICE_EXCEPTION);
            }
            log.info("{} pending payout record inserted, customerTransactionId={}, status={}",
                    CHATS_NETWORK, customerTransactionId, entity.getStatus());
        } catch (Exception e) {
            log.error("Failed to insert {} pending payout record, customerTransactionId={}, error={}",
                    CHATS_NETWORK, customerTransactionId, e.getMessage(), e);
            // Do not create an external payment without a durable enquiry record.
            throw new ServiceException(HTHSupportErrorMapping.GENERAL_SERVICE_EXCEPTION);
        }
        return id;
    }

    private void validateSufficientBalance(String accountNumber, String currency, BigDecimal amount) {
        PaymentServiceSupport.validateSufficientBalance(accountApi, log, accountNumber, currency, amount);
    }

    private void validateAccountNumber(String accountNumber) {
        PaymentServiceSupport.validateAccountNumber(log, accountNumber,
                HTHSupportErrorMapping.BENEFICIARY_ACCOUNT_NUMBER_MISSING,
                HTHSupportErrorMapping.BENEFICIARY_ACCOUNT_NUMBER_FORMAT_INVALID);
    }

    private void validateLocalAccountNumber(String accountNumber) {
        PaymentServiceSupport.validateLocalAccountNumber(log, accountNumber,
                HTHSupportErrorMapping.DEBIT_ACCOUNT_NUMBER_MISSING,
                HTHSupportErrorMapping.DEBIT_ACCOUNT_NUMBER_FORMAT_INVALID);
    }

    private void validateCurrency(Currency currency, int errorCode) {
        PaymentServiceSupport.validateCurrency(log, currency, errorCode);
    }

    private void validateTxnAmount(TxnAmount txnAmount) {
        PaymentServiceSupport.validateTxnAmount(log, txnAmount);
    }

    private void validateAccountName(String accountName) {
        PaymentServiceSupport.validateAccountName(log, accountName);
    }

    private ParameterException logAndCreateException(int errorCode) {
        return PaymentServiceSupport.logAndCreateException(log, errorCode);
    }

}
