package com.hkbea.hth.service.payment;

import com.hkbea.hth.constants.PaymentConstants;
import com.hkbea.hth.ipe.model.Amount;
import com.hkbea.hth.ipe.model.ChargeAmount;
import com.hkbea.hth.ipe.model.Charges;
import com.hkbea.hth.ipe.model.Creditor;
import com.hkbea.hth.ipe.model.CreditorAccount;
import com.hkbea.hth.ipe.model.CreditorAgent;
import com.hkbea.hth.ipe.model.Debtor;
import com.hkbea.hth.ipe.model.DebtorAccount;
import com.hkbea.hth.ipe.model.EquivalentAmount;
import com.hkbea.hth.ipe.model.FinancialInstitution;
import com.hkbea.hth.ipe.model.GroupHeader;
import com.hkbea.hth.ipe.model.InstructedAmount;
import com.hkbea.hth.ipe.model.IntermediaryAgent;
import com.hkbea.hth.ipe.model.PaymentIdentification;
import com.hkbea.hth.ipe.model.PaymentInformation;
import com.hkbea.hth.ipe.model.PaymentMethodEnum;
import com.hkbea.hth.ipe.model.PostalAddress;
import com.hkbea.hth.ipe.model.ProxyIdentification;
import com.hkbea.hth.ipe.model.Purpose;
import com.hkbea.hth.ipe.model.RemittanceInformation;
import com.hkbea.hth.ipe.model.BatchRoutingCreditTransferRequest;
import com.hkbea.hth.ipe.model.RoutingTransactionDetailRequest;
import com.hkbea.hth.ipe.model.SettlementAmount;
import com.hkbea.hth.model.Currency;
import com.hkbea.hth.model.LocalPayment;
import com.hkbea.hth.model.TxnAmount;
import com.hkbea.hth.utils.AccountUtils;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Maps the internal payment model onto the nested smart-routing request model.
 * <p>
 * The routing request is built directly from {@link LocalPayment}: the flat
 * {@code BatchCreditTransferRequest} is no longer used as an intermediate for
 * the smart-routing flow.
 */
public final class PaymentModelConvert {

    private static final DateTimeFormatter BATCH_ID_FORMATTER =
            DateTimeFormatter.ofPattern(PaymentConstants.BATCH_ID_TIMESTAMP_PATTERN);

    private PaymentModelConvert() {
    }

    /**
     * Maps the internal payment type to the payment method used by the smart-routing API.
     */
    public static String toRoutingTransactionType(LocalPayment.PaymentTypeEnum paymentType) {
        if (paymentType == null || paymentType == LocalPayment.PaymentTypeEnum.OTHERS) {
            return PaymentConstants.PAYMENT_METHOD_OTHERS;
        }
        return paymentType == LocalPayment.PaymentTypeEnum.ONUS
                ? PaymentConstants.PAYMENT_METHOD_INTERNAL
                : paymentType.value();
    }

    /**
     * Maps the transaction type returned by smart-routing validation back to the internal payment type.
     * ONUS is represented as INTERNAL on the new contract; the other payment types are unchanged.
     */
    public static LocalPayment.PaymentTypeEnum toInternalPaymentType(String transactionType) {
        if (transactionType == null || transactionType.isBlank()) {
            return null;
        }
        String value = transactionType.trim();
        if (PaymentConstants.PAYMENT_METHOD_OTHERS.equals(value) || "OTHERS".equalsIgnoreCase(value)) {
            return LocalPayment.PaymentTypeEnum.OTHERS;
        }
        if (PaymentConstants.PAYMENT_METHOD_INTERNAL.equalsIgnoreCase(value) || "ONUS".equalsIgnoreCase(value)) {
            return LocalPayment.PaymentTypeEnum.ONUS;
        }
        for (LocalPayment.PaymentTypeEnum type : LocalPayment.PaymentTypeEnum.values()) {
            if (type.value().equalsIgnoreCase(value)) {
                return type;
            }
        }
        return null;
    }

    /**
     * Builds the routing-validation request directly from the internal payment DTO.
     * The same object is later enriched by the routed payment service and submitted
     * to IPE, so the route and submit payloads share one mapping source.
     */
    public static BatchRoutingCreditTransferRequest toRoutingRequest(LocalPayment payment) {
        if (payment == null) {
            return null;
        }

        String batchId = buildBatchId();
        LocalDate executionDate = parseDate(payment.getSettlementDate());

        BatchRoutingCreditTransferRequest target = new BatchRoutingCreditTransferRequest();
        target.setPaymentInformation(toPaymentInformation(payment, executionDate));
        target.setGroupHeader(toGroupHeader(batchId, executionDate, payment));
        target.setTransactions(new ArrayList<>(Collections.singletonList(toRoutingTransactionDetail(payment, batchId))));
        return target;
    }

    /** Fresh batch id: channel + timestamp + random digits (mirrors the IPE batch-id format). */
    public static String buildBatchId() {
        return PaymentConstants.CHANNEL_HTH
                + LocalDateTime.now().format(BATCH_ID_FORMATTER)
                + AccountUtils.generateDigits();
    }

    private static PaymentInformation toPaymentInformation(LocalPayment payment, LocalDate executionDate) {
        PaymentInformation information = new PaymentInformation();
        information.setChannel(PaymentInformation.ChannelEnum.HTH);
        information.setPaymentMethod(PaymentMethodEnum
                .fromValue(toRoutingTransactionType(LocalPayment.PaymentTypeEnum
                        .fromValue(payment.getPaymentType().value()))));
        information.setRequestedExecutionDate(executionDate);
        return information;
    }

    private static GroupHeader toGroupHeader(String batchId, LocalDate executionDate, LocalPayment payment) {
        GroupHeader header = new GroupHeader();
        header.setMessageId(batchId);
        header.setCreationDateTime(executionDate != null && executionDate.isAfter(LocalDate.now())
                ? GroupHeader.CreationDateTimeEnum.LATER
                : GroupHeader.CreationDateTimeEnum.NOW);
        header.setNumberOfTransactions(1);
        if (payment.getTxnAmount() != null) {
            header.setControlSum(payment.getTxnAmount().getAmount());
        }
        return header;
    }

    /**
     * Transaction detail assembled straight from the payment: identification,
     * amounts, charges, remittance, purpose, debtor/creditor blocks. Blocks stay
     * null when the source fields are absent, exactly like the previous two-step
     * mapping (LocalPayment -> legacy -> routing).
     */
    private static RoutingTransactionDetailRequest toRoutingTransactionDetail(LocalPayment payment, String batchId) {
        RoutingTransactionDetailRequest txn = new RoutingTransactionDetailRequest();

        PaymentIdentification identification = new PaymentIdentification();
        identification.setEndToEndId(routingEndToEndId(payment, batchId));
        txn.setPaymentIdentification(identification);

        txn.setAmount(toAmount(payment));
        txn.setCharges(toCharges(payment));
        txn.setRemittanceInformation(toRemittanceInformation(payment));
        txn.setPurpose(toPurpose(payment));
        txn.setDebtor(toDebtor(payment));
        txn.setDebtorAccount(toDebtorAccount(payment));
        txn.setCreditor(toCreditor(payment));
        txn.setCreditorAccount(toCreditorAccount(payment));
        txn.setCreditorAgent(toCreditorAgent(payment));
        txn.setIntermediaryAgent(toIntermediaryAgent(payment));
        return txn;
    }

    /**
     * Reapply after route selection: CHATS carries the payer's reference, while
     * other channels retain their existing internal transaction identifier.
     */
    public static void applyRoutedPaymentIdentification(LocalPayment payment,
                                                        BatchRoutingCreditTransferRequest request) {
        RoutingTransactionDetailRequest txn = PaymentRoutingPayload.firstTransaction(request);
        if (txn == null) {
            return;
        }
        if (txn.getPaymentIdentification() == null) {
            txn.setPaymentIdentification(new PaymentIdentification());
        }
        txn.getPaymentIdentification().setEndToEndId(
                routingEndToEndId(payment, PaymentRoutingPayload.batchId(request)));
    }

    private static String routingEndToEndId(LocalPayment payment, String batchId) {
        if (payment.getPaymentType() != LocalPayment.PaymentTypeEnum.CHATS) {
            return batchId;
        }
        String customerId = payment.getPaymentIdentification() == null
                ? null : payment.getPaymentIdentification().getEndToEndId();
        return StringUtils.hasText(customerId) ? customerId : batchId;
    }

    public static String EndToEndId(LocalPayment payment) {
        String endToEndId = payment.getPaymentIdentification() == null
                ? null : payment.getPaymentIdentification().getEndToEndId();

        if (LocalPayment.PaymentTypeEnum.TT.equals(payment.getPaymentType())
                || LocalPayment.PaymentTypeEnum.CHATS.equals(payment.getPaymentType())
                || LocalPayment.PaymentTypeEnum.FPS.equals(payment.getPaymentType())) {
            return StringUtils.hasText(endToEndId) ? endToEndId : PaymentConstants.END_TO_END_ID;
        }else {
            return StringUtils.hasText(endToEndId) ? endToEndId : null;
        }
    }

    private static Amount toAmount(LocalPayment payment) {
        if (payment.getTxnAmount() == null) {
            return null;
        }
        java.math.BigDecimal amount = payment.getTxnAmount().getAmount();
        String currency = payment.getTxnAmount().getCurrency() == null
                ? null : payment.getTxnAmount().getCurrency().toString();
        if (amount == null && currency == null) {
            return null;
        }

        Amount target = new Amount();
        // instructed: amount or currency present; settlement: settlement currency
        // present; equivalent: HKD equivalent mirrors the instructed amount.
        InstructedAmount instructed = new InstructedAmount();
        instructed.setAmount(amount);
        instructed.setCurrency(currency);
        target.setInstructedAmount(instructed);
        if (currency != null) {
            SettlementAmount settlement = new SettlementAmount();
            settlement.setCurrency(currency);
            settlement.setAmount(amount);
            target.setSettlementAmount(settlement);
        }
        if (amount != null) {
            EquivalentAmount equivalent = new EquivalentAmount();
            equivalent.setAmount(amount);
            target.setEquivalentAmount(equivalent);
        }
        return target;
    }

    private static Charges toCharges(LocalPayment payment) {
        if (payment.getChargesOption() == null) {
            return null;
        }
        String chargeBearer = payment.getChargesOption().value().substring(0, 1);
        Currency currency = payment.getTxnAmount().getCurrency();
        String feeCode = Currency.HKD == currency
                ? PaymentConstants.FEE_CODE_FPS_HKD : PaymentConstants.FEE_CODE_FPS_OTHER;

        Charges target = new Charges();
        if (currency != null) {
            ChargeAmount chargeAmount = new ChargeAmount();
            chargeAmount.setAmount(null);
            chargeAmount.setCurrency(currency.toString());
            target.setChargeAmount(chargeAmount);
        }
        target.setChargeBearer(chargeBearer);
        target.setFeeCode(feeCode);
        return target;
    }

    private static RemittanceInformation toRemittanceInformation(LocalPayment payment) {
        List<String> details = payment.getPaymentDetails();
        boolean hasUnstructured = details != null && !details.isEmpty();
        if (!hasUnstructured) {
            return null;
        }
        RemittanceInformation target = new RemittanceInformation();
        target.setUnstructured(details);
        return target;
    }

    private static Purpose toPurpose(LocalPayment payment) {
        if (payment.getCategoryPurpose() == null) {
            return null;
        }
        String code = payment.getCategoryPurpose().getCode();
        String proprietary = payment.getCategoryPurpose().getProprietary();
        if (code == null && proprietary == null) {
            return null;
        }
        Purpose target = new Purpose();
        target.setCode(code);
        target.setProprietary(proprietary);
        return target;
    }

    private static Debtor toDebtor(LocalPayment payment) {
        com.hkbea.hth.model.BeaAccount fromAccount = payment.getFromAccount();
        if (fromAccount == null) {
            return null;
        }
        Debtor target = new Debtor();
        target.setType(Debtor.TypeEnum.CORPORATE);
        target.setName(fromAccount.getName());
        return target;
    }

    private static DebtorAccount toDebtorAccount(LocalPayment payment) {
        com.hkbea.hth.model.BeaAccount fromAccount = payment.getFromAccount();
        if (fromAccount == null) {
            return null;
        }
        String accountNumber = firstNonBlank(fromAccount.getAccountNumber(), fromAccount.getId());
        String accountType = fromAccount.getAccountType() != null
                ? fromAccount.getAccountType().value() : fromAccount.getType();
        String internalAccountNo = toInternalAccountNo(accountNumber, fromAccount.getAccountType() == null
                ? null : fromAccount.getAccountType().value());
        String currency = fromAccount.getCurrency();
        if (accountNumber == null && internalAccountNo == null && accountType == null && currency == null) {
            return null;
        }

        DebtorAccount target = new DebtorAccount();
        target.setIdentification(accountNumber);
        target.setInternalAccountNo(internalAccountNo);
        target.setAccountType(accountType);
        target.setCurrency(currency);
        return target;
    }

    private static Creditor toCreditor(LocalPayment payment) {
        com.hkbea.hth.model.PaymentToAccount toAccount = payment.getToAccount();
        com.hkbea.hth.model.Creditor source = payment.getCreditor();

        String accountNumber = toAccount == null ? null
                : firstNonBlank(toAccount.getAccountNumber(), toAccount.getId());
        String mobile = toAccount == null ? null : toAccount.getMobileNumber();
        String email = source == null
                ? (toAccount == null ? null : toAccount.getEmailAddress())
                : firstNonBlank(toAccount == null ? null : toAccount.getEmailAddress(), source.getEmailAddress());
        String name = source == null ? null : source.getCreditorName();
        String line1 = null;
        String line2 = null;
        String townName = null;
        String country = null;
        if (source != null && source.getPostalAddress() != null) {
            line1 = source.getPostalAddress().getAddressLine1();
            line2 = source.getPostalAddress().getAddressLine2();
            townName = source.getPostalAddress().getTownName();
            country = source.getPostalAddress().getCountry();
        }
        if (name == null && mobile == null && email == null && line1 == null
                && townName == null && country == null) {
            return null;
        }

        Creditor target = new Creditor();
        if (toAccount != null) {
            target.setType(toLegacyPayeeType(toAccount) == Creditor.TypeEnum.PROXY
                    ? Creditor.TypeEnum.PROXY : Creditor.TypeEnum.ACCOUNT);
        }
        target.setName(name);
        target.setMobileNumber(mobile);
        target.setEmailAddress(email);
        if (line1 != null || line2 != null || townName != null || country != null) {
            PostalAddress address = new PostalAddress();
            address.setAddressLine1(line1);
            address.setAddressLine2(line2);
            address.setTownName(townName);
            address.setCountry(country);
            target.setPostalAddress(address);
        }
        return target;
    }

    private static CreditorAccount toCreditorAccount(LocalPayment payment) {
        com.hkbea.hth.model.PaymentToAccount toAccount = payment.getToAccount();
        com.hkbea.hth.model.Creditor source = payment.getCreditor();
        String accountNumber = toAccount == null ? null
                : firstNonBlank(toAccount.getAccountNumber(), toAccount.getId());
        String proxyId = toAccount != null && toLegacyPayeeType(toAccount) == Creditor.TypeEnum.PROXY
                ? firstNonBlank(toAccount.getFpsId(), toAccount.getMobileNumber(),
                        toAccount.getEmailAddress(), accountNumber)
                : null;
        String proxyType = toAccount == null || toAccount.getAccountIdentifier() == null
                ? null : toAccount.getAccountIdentifier().value();
        String country = source == null || source.getPostalAddress() == null
                ? null : source.getPostalAddress().getCountry();
        String accountName = source == null ? null : source.getCreditorName();
        String currency = toAccount == null || toAccount.getCurrency() == null
                ? null : toAccount.getCurrency().toString();
        if (currency == null){
            TxnAmount txnAmount = payment.getTxnAmount();
            currency = txnAmount == null || txnAmount.getCurrency() == null
                    ? null : txnAmount.getCurrency().toString();
        }
        String telephone = toAccount == null ? null : toAccount.getMobileNumber();

        if (accountName == null && accountNumber == null && currency == null
                && proxyId == null && proxyType == null && country == null && telephone == null) {
            return null;
        }

        CreditorAccount target = new CreditorAccount();
        target.setAccountName(accountName);
        target.setIdentification(accountNumber);
        target.setCurrency(currency);
        if (proxyId != null || proxyType != null) {
            ProxyIdentification proxy = new ProxyIdentification();
            proxy.setProxyId(proxyId);
            proxy.setProxyType(proxyType);
            target.setProxyIdentification(proxy);
        }
        target.setCountry(country);
        target.setTelephoneNumber(telephone);
        return target;
    }

    /**
     * Creditor agent block. The bank address lines mirror the creditor postal
     * address lines (as the previous two-step mapping did via
     * payeeBankAddress1/2), so the block is also created when only those are present.
     */
    private static CreditorAgent toCreditorAgent(LocalPayment payment) {
        String bicfi = null;
        String memberId = null;
        if (payment.getCreditorAgent() != null && payment.getCreditorAgent().getFinancialInstitutionId() != null) {
            bicfi = payment.getCreditorAgent().getFinancialInstitutionId().getBicfi();
            memberId = payment.getCreditorAgent().getFinancialInstitutionId().getClearingSystemMemberId();
        }
        String line1 = payment.getCreditor() != null && payment.getCreditor().getPostalAddress() != null
                ? payment.getCreditor().getPostalAddress().getAddressLine1() : null;
        String line2 = payment.getCreditor() != null && payment.getCreditor().getPostalAddress() != null
                ? payment.getCreditor().getPostalAddress().getAddressLine2() : null;
        if (bicfi == null && memberId == null && line1 == null && line2 == null) {
            return null;
        }

        CreditorAgent target = new CreditorAgent();
        if (bicfi != null || memberId != null) {
            FinancialInstitution institution = new FinancialInstitution();
            institution.setBicfi(bicfi);
            institution.setMemberId(memberId);
            // bankCode mirrors memberId, as in the previous two-step mapping.
            institution.setBankCode(memberId);
            target.setFinancialInstitution(institution);
        }
        if (line1 != null || line2 != null) {
            com.hkbea.hth.ipe.model.CreditorAgentPostalAddress address =
                    new com.hkbea.hth.ipe.model.CreditorAgentPostalAddress();
            address.setAddressLine1(line1);
            address.setAddressLine2(line2);
            target.setPostalAddress(address);
        }
        return target;
    }

    private static IntermediaryAgent toIntermediaryAgent(LocalPayment payment) {
        if (payment.getIntermediaryAgent() == null
                || payment.getIntermediaryAgent().getFinancialInstitutionId() == null) {
            return null;
        }
        String bicfi = payment.getIntermediaryAgent().getFinancialInstitutionId().getBicfi();
        String line1 = payment.getCreditor() != null && payment.getCreditor().getPostalAddress() != null
                ? payment.getCreditor().getPostalAddress().getAddressLine1() : null;
        String line2 = payment.getCreditor() != null && payment.getCreditor().getPostalAddress() != null
                ? payment.getCreditor().getPostalAddress().getAddressLine2() : null;

        IntermediaryAgent target = new IntermediaryAgent();
        if (bicfi != null) {
            FinancialInstitution institution = new FinancialInstitution();
            institution.setBicfi(bicfi);
            target.setFinancialInstitution(institution);
        }
        if (line1 != null || line2 != null) {
            com.hkbea.hth.ipe.model.CreditorAgentPostalAddress address =
                    new com.hkbea.hth.ipe.model.CreditorAgentPostalAddress();
            address.setAddressLine1(line1);
            address.setAddressLine2(line2);
            target.setPostalAddress(address);
        }
        return target;
    }

    private static Creditor.TypeEnum toLegacyPayeeType(com.hkbea.hth.model.PaymentToAccount account) {
        if (account == null || account.getAccountIdentifier() == null) {
            return Creditor.TypeEnum.ACCOUNT;
        }
        return switch (account.getAccountIdentifier()) {
            case _02, _03, _04 -> Creditor.TypeEnum.PROXY;
            default -> Creditor.TypeEnum.ACCOUNT;
        };
    }

    private static String toInternalAccountNo(String accountNumber, String accountType) {
        if (accountNumber == null || accountNumber.isBlank() || accountType == null || accountType.isBlank()) {
            return null;
        }
        return AccountUtils.ext2intAccNo(accountNumber, accountType.substring(0, 1));
    }

    private static LocalDate parseDate(String value) {
        if (ObjectUtils.isEmpty(value)) {
            return null;
        }
        try {
            return LocalDate.parse(value);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return null;
    }
}
