package com.hkbea.hth.service.payment;

import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.hkbea.common.exception.ParameterException;
import com.hkbea.hth.constants.PaymentConstants;
import com.hkbea.hth.entity.PaymentPayoutRecord;
import com.hkbea.hth.exception.HTHSupportErrorMapping;
import com.hkbea.hth.ipe.model.BatchRoutingCreditTransferRequest;
import com.hkbea.hth.ipe.model.CreditorAccountInfo;
import com.hkbea.hth.ipe.model.RoutingTransactionDetailRequest;
import com.hkbea.hth.ipe.model.Tt;
import com.hkbea.hth.ipe.model.TransferPaymentResultQueryResponse;
import com.hkbea.hth.model.Creditor;
import com.hkbea.hth.model.Currency;
import com.hkbea.hth.model.LocalPayment;
import com.hkbea.hth.model.Payment;
import com.hkbea.hth.model.PaymentToAccount;
import com.hkbea.hth.model.PostalAddress;
import com.hkbea.hth.utils.AccountUtils;
import com.hkbea.hth.utils.CurrencyUtils;
import com.hkbea.hth.wmg.model.DataField;
import com.hkbea.hth.wmg.model.RequestModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Telegraphic Transfer (TT) overseas payment.
 * Enriches the smart-routing accepted request with the tt block, submits to IPE
 * and persists to HTH_PAYMENT_PAYOUT (shared with FPS/CHATS, pending + update).
 * EBK already deducted the transfer limit.
 */
@Service
public class TtPaymentService extends OverseasPaymentService {

    // TT supported currencies：HKD, USD, EUR, AUD, CAD, CHF, CNY, GBP, JPY, NZD, SGD
    private static final Set<Currency> TT_SUPPORTED_CURRENCIES =
            EnumSet.of(Currency.HKD, Currency.USD,Currency.CNY
                    ,Currency.EUR,Currency.AUD,Currency.CAD
                    ,Currency.CHF,Currency.GBP,Currency.JPY
                    ,Currency.NZD,Currency.SGD);

    /**
     * HTH_PAYMENT_PAYOUT.PAYMENT_TYPE：TT
     */
    private static final String TT_PAYOUT_PAYMENT_TYPE = "TT";
    private static final String TT_WORKFLOW = "Workflows\\OTT_Main";

    private static final String TT_ADDRESS_REGEX = "^[0-9a-zA-Z/?:().,'+ !#$%&*=^_`{|}~\";<>@\\[\\]\\\\-]{1,70}$";
    private static final String TT_TOWN_NAME_REGEX = "^[0-9a-zA-Z/?:().,'+ !#$%&*=^_`{|}~\";<>@\\[\\]\\\\-]{1,35}$";
    private static final String TT_COUNTRY_CODE_REGEX = "[A-Z]{2,2}";
    private static final String TT_INSTRUCTION_PRIORITY = "1";
    private static final String TT_BLANK = " ";
    /** supplementaryData.creditorAccount values: registered vs ad-hoc beneficiary. */
    private static final String TT_PAYEE_PRODUCT_REGISTERED = "CB97";
    private static final String TT_PAYEE_PRODUCT_DEFAULT = "0000";
    private static final String TT_PAYEE_ACCOUNT_INDICATOR = "N";

    /** ISO 3166 alpha-2 country code set (built into the JDK, maintained with the JDK release). */
    private static final Set<String> ISO_COUNTRIES = Set.of(java.util.Locale.getISOCountries());

    @Autowired
    private TtPaymentServiceSupport ttPaymentServiceSupport;

    @Override
    protected String routingPaymentMethod() {
        return TT_PAYOUT_PAYMENT_TYPE;
    }

    /**
     * TT persistence phase 1: inserts the pending payout record before the IPE
     * submission and returns its id. Best-effort (log-only on failure).
     */
    @Override
    protected String insertPendingRecord(LocalPayment localPayment,
                                         BatchRoutingCreditTransferRequest routingRequest,
                                         Payment payment) {
        return insertPendingPayoutRecord(localPayment, routingRequest);
    }

    /**
     * TT persistence phase 2: updates the pending payout record with the
     * submission outcome. Best-effort (log-only on failure).
     */
    @Override
    protected void persistPaymentRecord(String recordId,
                                        LocalPayment localPayment,
                                        BatchRoutingCreditTransferRequest routingRequest,
                                        Payment payment,
                                        TransferPaymentResultQueryResponse result) {
        PaymentServiceSupport.updatePayoutRecord(paymentPayoutRecordMapper, log,
                TT_PAYOUT_PAYMENT_TYPE, recordId, payment, result);
    }

    /**
     * Kicks off the WMG workflow instance ({@code OTT_Main}) with the payment
     * data; failures are logged but do not block the payment submission.
     */
    @Override
    protected void createProcessInstanceExecute(LocalPayment localPayment, String batchId) {
        try {
            RequestModel requestModel = buildRequestModel(localPayment, batchId);
            // Async call wmg and insert HTH_WMG_PROCESS
            ttPaymentServiceSupport.createProcessInstanceExecute(requestModel, localPayment, batchId);
        } catch (Exception e) {
            log.error("Failed Build RequestModel, paymentId={}, error={}", batchId, e.getMessage(), e);
        }
    }

    /**
     * Builds the WMG workflow request model from the payment (remitter,
     * beneficiary and transaction fields) keyed by the batch id.
     */
    private RequestModel buildRequestModel(LocalPayment localPayment, String batchId) {
        RequestModel requestModel = new RequestModel();
        requestModel.setWorkflow(TT_WORKFLOW);

        DataField dataField = new DataField();
        dataField.setSystemCode(PaymentConstants.CHANNEL_HTH);
        dataField.setTransType(localPayment.getPaymentType().value());
        dataField.setTransNo(localPayment.getTransactionID());
        dataField.setHoSTRefNo(headers.getMessageId());
        dataField.setRemitAccNo(formatPayerAccountNoFmt(localPayment.getFromAccount().getAccountNumber()));
        dataField.setRemitAccCurr(localPayment.getFromAccount().getCurrency());
        dataField.setTransAmount(localPayment.getTxnAmount().getAmount().toString());
        dataField.setTransCurrency(localPayment.getTxnAmount().getCurrency().toString());
        dataField.setTransExeDate(LocalDate.parse(localPayment.getSettlementDate())
                .format(DateTimeFormatter.ofPattern("dd-MM-yyyy")));
        dataField.setTransValueDate(LocalDate.now().format(DateTimeFormatter.ofPattern("dd-MM-yyyy")));
        dataField.setBeneBankName(localPayment.getCreditor().getCreditorName());
        dataField.setBeneBankBIC(localPayment.getCreditorAgent().getFinancialInstitutionId().getBicfi());
        dataField.setBeneBankCity(localPayment.getCreditor().getPostalAddress().getTownName());
        dataField.setBeneBankCty(localPayment.getCreditor().getPostalAddress().getCountry());
        dataField.setBeneAccNo(localPayment.getToAccount().getAccountNumber());
        dataField.setBeneNameAddress1(localPayment.getCreditor().getPostalAddress().getAddressLine1());
        dataField.setBeneNameAddress2(localPayment.getCreditor().getPostalAddress().getAddressLine2());
        dataField.setBeneCty(localPayment.getCreditor().getPostalAddress().getCountry());
        dataField.setBenePhoneNo(localPayment.getToAccount().getMobileNumber());
        dataField.setTtRef(batchId);
        requestModel.setDatafield(dataField);

        return requestModel;
    }

    /**
     * Inserts the pending HTH_PAYMENT_PAYOUT row (status null) before
     * submission; returns the row id for the post-submission update.
     */
    private String insertPendingPayoutRecord(LocalPayment req,
                                             BatchRoutingCreditTransferRequest routingRequest) {
        String customerTransactionId = PaymentRoutingPayload.endToEndId(routingRequest);
        String prodCode = PaymentRoutingPayload.debtorProductCode(routingRequest);
        String id = UUID.randomUUID().toString().replace("-", "");

        String partyId = req.getPartyId();
        String payeeId = req.getToAccount() != null && partyId != null
                && !PaymentConstants.MOCK_PARTY_ID.equals(partyId)
                ? internalPayeeMapper.selectInternationalPayeeId(partyId, req.getToAccount().getAccountNumber())
                : null;
        PaymentToAccount toAccount = req.getToAccount();

        try {
            PaymentPayoutRecord entity = new PaymentPayoutRecord();
            entity.setId(id);
            entity.setMessageId(headers.getMessageId());
            entity.setPaymentId(customerTransactionId);
            entity.setCustomerTransactionId(req.getTransactionID());
            entity.setExecutionDate(req.getSettlementDate());
            if (req.getToAccount() != null && req.getCreditorAgent() != null) {
                entity.setCreditBicCode(req.getCreditorAgent().getFinancialInstitutionId().getBicfi());
                entity.setCreditMemberId(req.getCreditorAgent().getFinancialInstitutionId().getClearingSystemMemberId());
                if (req.getCreditor().getPostalAddress() != null) {
                    entity.setCreditAddressLine1(req.getCreditor().getPostalAddress().getAddressLine1());
                    entity.setCreditAddressLine2(req.getCreditor().getPostalAddress().getAddressLine2());
                    entity.setCreditTownName(req.getCreditor().getPostalAddress().getTownName());
                    entity.setCreditCountryCode(req.getCreditor().getPostalAddress().getCountry());
                }
            }
            entity.setIpeRefNo(null);
            entity.setPaymentDate(LocalDateTime.now());
            entity.setCreatedBy(PaymentConstants.SYSTEM_USER);
            entity.setCreationDate(LocalDateTime.now());
            entity.setLastUpdatedBy(PaymentConstants.SYSTEM_USER);
            entity.setLastUpdatedDate(LocalDateTime.now());
            entity.setPaymentType(TT_PAYOUT_PAYMENT_TYPE);
            entity.setPartyId(partyId);
            entity.setPartyName(PaymentConstants.MOCK_PARTY_NAME);
            entity.setPayeeId(payeeId);
            entity.setPayeeIdfn(PaymentConstants.PAYEE_IDFN_ACCTNO);
            entity.setDebitAccountId(req.getFromAccount().getAccountNumber());
            entity.setDebitAccountCur(req.getFromAccount().getCurrency().toString());
            entity.setDebitAccountType(req.getFromAccount().getAccountType() != null
                    ? req.getFromAccount().getAccountType().value() : null);
            entity.setProdCode(prodCode);
            entity.setCtgyPurp(req.getCategoryPurpose() != null
                    ? req.getCategoryPurpose().getCode() : null);
            entity.setPurp(req.getCategoryPurpose() != null
                    ? req.getCategoryPurpose().getProprietary() : null);
            if (toAccount != null) {
                entity.setCreditAccountId(toAccount.getAccountNumber());
                entity.setCreditAccountName(accountNameOrFallback(req));
                entity.setCreditAccountType(toAccount.getAccountType() != null
                        ? toAccount.getAccountType().value() : null);
                entity.setCreditAccountCur(toAccount.getCurrency().toString());
            }
            entity.setInstdAmount(req.getTxnAmount().getAmount());
            entity.setInstdCurrency(req.getTxnAmount().getCurrency().toString());
            entity.setHkdEquivalentAmount(req.getTxnAmount().getAmount());
            entity.setSvcCur(req.getTxnAmount().getCurrency().toString());
            entity.setSvcAmount(PaymentConstants.MOCK_SVC_AMOUNT);
            entity.setSvcType(req.getChargesOption() != null ? req.getChargesOption().toString() : null);
            entity.setRemarks(joinPaymentDetails(req.getPaymentDetails()));
            entity.setStatus(null);
            entity.setIpeStatus(null);
            entity.setIpeStatusReason(null);
            entity.setStatusFlag(PaymentConstants.MOCK_OBJECT_STATUS);
            paymentPayoutRecordMapper.insert(entity);
            log.info("TT pending payout record inserted, customerTransactionId={}", customerTransactionId);
        } catch (Exception e) {
            log.error("Failed to insert TT pending payout record, customerTransactionId={}, error={}",
                    customerTransactionId, e.getMessage(), e);
        }
        return id;
    }

    /**
     * TT-specific checks on top of the shared overseas validation: payee
     * info, currency consistency and payee address/country. IPE checks balances.
     */
    @Override
    protected void validateSpecific(LocalPayment payment) {
        validateTtToAccount(payment);
        validateTtCurrencyConsistency(payment);
        validateTtAddress(payment);
    }

    /**
     * TT payee address: addressLine1 (<=70) and townName (<=35) mandatory,
     * SWIFT-style charset; country validated via {@link #validateCountry}.
     */
    private void validateTtAddress(LocalPayment tt) {
        String payeeAddressLine1 = tt.getCreditor().getPostalAddress().getAddressLine1();
        String payeeTownName = tt.getCreditor().getPostalAddress().getTownName();
        String country = tt.getCreditor().getPostalAddress().getCountry();
        if (StringUtils.isBlank(payeeAddressLine1) || !payeeAddressLine1.matches(TT_ADDRESS_REGEX)) {
            throw new ParameterException(HTHSupportErrorMapping.PAYEE_ADDRESS_LINE1_MISSING_OR_INVALID);
        }

        if (StringUtils.isBlank(payeeTownName) || !payeeTownName.matches(TT_TOWN_NAME_REGEX)) {
            throw new ParameterException(HTHSupportErrorMapping.PAYEE_TOWN_NAME_MISSING_OR_INVALID);
        }

        validateCountry(country);
    }

    /**
     * Country code validation per the spec ("2 characters A-Z in capital letter" + "ISO Country Code"):
     * 1) format: two capital letters (existing TT_COUNTRY_CODE_REGEX);
     * 2) validity: must be an ISO 3166 alpha-2 country code (new; "ZZ" etc. rejected -> 500178).
     */
    private void validateCountry(String country) {
        if (StringUtils.isBlank(country) || !country.matches(TT_COUNTRY_CODE_REGEX)) {
            throw new ParameterException(HTHSupportErrorMapping.PAYEE_COUNTRY_MISSING_OR_INVALID);
        }
        if (!ISO_COUNTRIES.contains(country)) {
            throw new ParameterException(HTHSupportErrorMapping.PAYEE_COUNTRY_INVALID_ISO_CODE);
        }
    }

    /**
     * TT payee info presence: toAccount, creditor name, account number and a
     * valid beneficiary currency.
     */
    private void validateTtToAccount(LocalPayment payment) {
        PaymentToAccount toAccount = payment.getToAccount();
        if (toAccount == null) {
            throw logAndCreateException(HTHSupportErrorMapping.TT_PAYEE_INFO_MISSING);
        }
        if (StringUtils.isBlank(payment.getCreditor().getCreditorName())) {
            throw logAndCreateException(HTHSupportErrorMapping.TT_PAYEE_INFO_MISSING);
        }
        if (StringUtils.isBlank(toAccount.getAccountNumber())) {
            throw logAndCreateException(HTHSupportErrorMapping.TT_PAYEE_INFO_MISSING);
        }
        validateCurrency(
                toAccount.getCurrency(),
                HTHSupportErrorMapping.CREDITOR_CURRENCY_MISSING_OR_INVALID);
    }

    /**
     * TT currency rule: payment currency must equal creditor account currency.
     */
    private void validateTtCurrencyConsistency(LocalPayment payment) {
        Currency fromCurrency = CurrencyUtils.parseLenient(payment.getFromAccount().getCurrency());
        Currency txnCurrency = payment.getTxnAmount().getCurrency();
        Currency toCurrency = payment.getToAccount().getCurrency();

        if (!TT_SUPPORTED_CURRENCIES.contains(fromCurrency)) {
            throw logAndCreateException(HTHSupportErrorMapping.OVERSEAS_DEBIT_CURRENCY_NOT_SUPPORTED);
        }
        if (!TT_SUPPORTED_CURRENCIES.contains(txnCurrency)) {
            throw logAndCreateException(HTHSupportErrorMapping.OVERSEAS_PAYMENT_CURRENCY_NOT_SUPPORTED);
        }
        if (!TT_SUPPORTED_CURRENCIES.contains(toCurrency)) {
            throw logAndCreateException(HTHSupportErrorMapping.OVERSEAS_CREDITOR_CURRENCY_NOT_SUPPORTED);
        }
        if (fromCurrency != txnCurrency) {
            throw logAndCreateException(HTHSupportErrorMapping.PAYER_PAYEE_CURRENCY_NO_MATCHES);
        }
        if (txnCurrency != toCurrency) {
            throw logAndCreateException(HTHSupportErrorMapping.PAYMENT_BENEFICIARY_CURRENCY_NO_MATCHES);
        }
    }

    /**
     * Fills TT-specific routing blocks: settlement/charge info (charges),
     * payee account/address blocks, instruction priority and the tt detail.
     */
    @Override
    protected void enrichRoutingDetail(RoutingTransactionDetailRequest txn, LocalPayment localPayment) {
        PaymentToAccount toAccount = localPayment.getToAccount();
        Currency currency = localPayment.getTxnAmount().getCurrency();
        String chargeBearer = localPayment.getChargesOption().value().substring(0, 1);
        String feeCode = Currency.HKD == currency
                ? PaymentConstants.FEE_CODE_FPS_HKD : PaymentConstants.FEE_CODE_FPS_OTHER;
        // charge currency follows the debtor account currency (legacy debitAmountCcy).
        String chargeCurrency = txn.getDebtorAccount() == null
                ? null : txn.getDebtorAccount().getCurrency();
        txn.setCharges(PaymentRoutingPayload.charges(serviceChargeAmount(), chargeCurrency, chargeBearer, feeCode));

        Creditor creditor = localPayment.getCreditor();
        PostalAddress postal = creditorPostalAddress(localPayment);
        String country = postal == null ? null : postal.getCountry();
        if (Objects.nonNull(creditor) && postal != null) {
            txn.setCreditor(PaymentRoutingPayload.creditor(
                    com.hkbea.hth.ipe.model.Creditor.TypeEnum.ACCOUNT, creditor.getCreditorName(),
                    null, null, postal.getAddressLine1(), postal.getAddressLine2(),
                    postal.getTownName(), country));
        }
        txn.setCreditorAccount(PaymentRoutingPayload.creditorAccount(
                creditor != null ? creditor.getCreditorName() : null,
                toAccount.getAccountNumber(), toAccount.getCurrency().toString(),
                null, null, country, null));

        String bicfi = localPayment.getCreditorAgent() != null
                && localPayment.getCreditorAgent().getFinancialInstitutionId() != null
                ? localPayment.getCreditorAgent().getFinancialInstitutionId().getBicfi() : null;
        String memberId = localPayment.getCreditorAgent() != null
                && localPayment.getCreditorAgent().getFinancialInstitutionId() != null
                ? localPayment.getCreditorAgent().getFinancialInstitutionId().getClearingSystemMemberId() : null;
        txn.setCreditorAgent(PaymentRoutingPayload.creditorAgent(bicfi, memberId, null,
                localPayment.getCreditor().getPostalAddress().getAddressLine1(),
                localPayment.getCreditor().getPostalAddress().getAddressLine2(), null, country));

        txn.setPaymentTypeInformation(instructionPriority(TT_INSTRUCTION_PRIORITY));

        // Registered beneficiary carries CB97, ad-hoc payees the default product code.
        String payeeId = internalPayeeMapper.selectInternationalPayeeId(
                localPayment.getPartyId(), toAccount.getAccountNumber());
        CreditorAccountInfo creditorAccountInfo = new CreditorAccountInfo();
        creditorAccountInfo.setProductCode(StringUtils.isNotBlank(payeeId) ? TT_PAYEE_PRODUCT_REGISTERED : TT_PAYEE_PRODUCT_DEFAULT);
        creditorAccountInfo.setIndicator(TT_PAYEE_ACCOUNT_INDICATOR);
        txn.getSupplementaryData().setCreditorAccount(creditorAccountInfo);

        Tt tt = new Tt();
        tt.setAccountHolderName(txn.getDebtor() == null || txn.getDebtor().getName() == null ? null : txn.getDebtor().getName().trim());
        tt.setPriority(TT_INSTRUCTION_PRIORITY);
        tt.setLmInd(TT_INSTRUCTION_PRIORITY);
        tt.setLmInstrNum(TT_INSTRUCTION_PRIORITY);
        tt.setRmbTradeFlag(TT_BLANK);
        tt.setUaePurFund(TT_BLANK);
        tt.setBfPurFund(TT_BLANK);
        txn.getSupplementaryData().setTt(tt);
    }
}
