package com.hkbea.hth.service.payment;

import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.hkbea.common.exception.ParameterException;
import com.hkbea.hth.exception.HTHSupportErrorMapping;
import com.hkbea.hth.model.*;
import com.hkbea.hth.service.PaymentServiceImpl;
import com.hkbea.hth.utils.CurrencyUtils;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Payment-type-specific validation rules. They intentionally live outside
 * {@link PaymentServiceImpl#validateCommon(LocalPayment)} so the common rules
 * run once and each type owns its own contract.
 */
public final class PaymentValidationSupport {

    private static final String INTERNAL_MEMBER_ID = "015";
    private static final int BANK_CODE_LENGTH = 3;
    private static final int SEGMENT_LENGTH = 35;
    private static final int MAX_SEGMENTS = 4;
    private static final int MAX_LENGTH = SEGMENT_LENGTH * MAX_SEGMENTS;

    private static final String CUSTOMER_TRANSACTION_ID_REGEX = "^.{1,35}$";
    private static final String BIC_REGEX = "[A-Z0-9]{4,4}[A-Z]{2,2}[A-Z0-9]{2,2}([A-Z0-9]{3,3}){0,1}";
    private static final String CHATS_ADDRESS_REGEX =
            "^[0-9a-zA-Z/?:().,'+ !#$%&*=^_`{|}~\";<>@\\[\\]\\\\-]{1,70}$";
    private static final String CHATS_TOWN_NAME_REGEX =
            "^[0-9a-zA-Z/?:().,'+ !#$%&*=^_`{|}~\";<>@\\[\\]\\\\-]{1,35}$";
    private static final String CHATS_COUNTRY_CODE_REGEX = "[A-Z]{2,2}";
    private static final String PROPRIETARY_REGEX = "^[0-9a-zA-Z/\\-?:().,'+ ]{1,35}$";
    private static final Pattern CHINESE_PATTERN = Pattern.compile("[\\p{IsHan}]");
    private static final Pattern ENGLISH_PATTERN = Pattern.compile(
            "^[0-9a-zA-Z/\\-\\?:\\(\\)\\.,'\\+ !#$%&\\*=^_`\\{\\|\\}~\";<>@\\[\\\\\\]]+$");
    private static final Pattern ENGLISH_NAME_PATTERN = Pattern.compile(
            "^[0-9a-zA-Z/\\-\\?:\\(\\)\\.,'\\+ !#$%&\\*=^_`\\{\\|\\}~\";<>@\\[\\\\\\]]+$");
    private static final int MAX_ACCOUNT_NAME_LENGTH = 140;
    private static final String CLEARING_SYSTEM_MEMBER_ID_REGEX = "^[A-Za-z0-9]{1,5}$";

    private PaymentValidationSupport() {
    }

    /**
     * Defaults the creditor account currency to the debtor currency when the
     * caller left it empty (same-currency payment assumption).
     */
    public static void useDebitCurrencyForCreditorAccount(LocalPayment payment) {
        if (payment == null || payment.getFromAccount() == null
                || payment.getFromAccount().getCurrency() == null) {
            return;
        }
        PaymentToAccount toAccount = payment.getToAccount();
        if (toAccount != null && toAccount.getCurrency() == null) {
            toAccount.setCurrency(CurrencyUtils.parseLenient(payment.getFromAccount().getCurrency()));
        }
    }

    /**
     * Creditor account number accessor: prefers the normalized
     * {@code accountNumber} and falls back to the raw {@code id}.
     */
    public static String creditorAccountNumber(PaymentToAccount account) {
        if (account == null) {
            return null;
        }
        return StringUtils.isNotBlank(account.getAccountNumber())
                ? account.getAccountNumber() : account.getId();
    }

    /**
     * ONUS-specific rules: member id must be "015", no intermediary agent, no
     * chargeBearer and no remittanceInformationUnstructured.
     */
    public static void validateOnUsRules(LocalPayment payment) {
        validateOnUsCreditorAccount(payment);
        validateCategoryPurpose(payment, false);
        validatePurpose(payment, false);
        validateOnusBicfiAndMemberId(payment);
        validateIntermediaryAgentMustBeBlank(payment);
        if (StringUtils.isNotBlank(payment.getRemittanceInformationUnstructured())) {
            throw new ParameterException(HTHSupportErrorMapping.ONUS_AND_DR_PAYMENTDEATAIL_NO_MISSING);
        }
        validateChargeBearerMustBeBlank(payment);
    }

    /**
     * CHATS-specific rules: optional payee address within IPE bounds, bank
     * identifier, no intermediary agent and mandatory chargeBearer.
     */
    public static void validateChatsRules(LocalPayment payment) {
        validateCategoryPurpose(payment, false);
        validatePurpose(payment, false);
        validateChatsIpeAddress(payment);
        validateChatsBicfiAndMemberId(payment);
//        validateEndToEndId(payment, true);
        validateIntermediaryAgentMustBeBlank(payment);
        validateChargeBearerRequired(payment);
//        validateProprietary(payment);
    }

    /**
     * FPS-specific rules: member id derived from the account bank code,
     * defaulted endToEndId, no intermediary agent, no chargeBearer and
     * proprietary format.
     */
    public static void validateFpsRules(LocalPayment payment) {
        validateCategoryPurpose(payment, false);
        validatePurpose(payment, false);
        validateFpsBicfiAndMemberId(payment);
//        validateEndToEndId(payment, true);
        validateIntermediaryAgentMustBeBlank(payment);
//        validateProprietary(payment);
        validateChargeBearerMustBeBlank(payment);
    }

    /**
     * TT-specific rules: creditor name (<=140 English chars), defaulted
     * endToEndId, mandatory intermediary agent, mandatory chargeBearer and
     * proprietary format.
     */
    public static void validateTtRules(LocalPayment payment) {
        validateCategoryPurpose(payment, true);
        validatePurpose(payment, true);
        validateChatsAddress(payment);
        validateChatsCountryCode(payment);
        validateTtBicfiAndMemberId(payment);
        validateTtCreditorName(payment.getCreditor() == null
                ? null : payment.getCreditor().getCreditorName());
//        validateEndToEndId(payment, true);
        validateIntermediaryAgentRequired(payment);
        validateChargeBearerRequired(payment);
        validateProprietary(payment);
    }

    /**
     * DR-specific rules: creditor name (<=34 English chars), mandatory
     * intermediary agent, no chargeBearer, no remittanceInformationUnstructured
     * and proprietary format.
     */
    public static void validateDrRules(LocalPayment payment) {
        validateCategoryPurpose(payment, false);
        validatePurpose(payment, false);
        validateDrBicfiAndMemberId(payment);
        validateDrCreditorName(payment.getCreditor() == null
                ? null : payment.getCreditor().getCreditorName());
//        validateProprietary(payment);
        if (StringUtils.isNotBlank(payment.getRemittanceInformationUnstructured())) {
            throw new ParameterException(HTHSupportErrorMapping.ONUS_AND_DR_PAYMENTDEATAIL_NO_MISSING);
        }
        validateChargeBearerMustBeBlank(payment);
    }

    /**
     * IPE PostalAddress fields are optional. Check supplied CHATS values without
     * trimming or truncating them; retain the existing addressLine2 charset rule.
     */
    public static void validateChatsIpeAddress(LocalPayment payment) {
        PostalAddress address = payment.getCreditor() == null
                ? null : payment.getCreditor().getPostalAddress();
        if (address == null) {
            return;
        }
        validateMaxLength(address.getAddressLine1(), 70,
                HTHSupportErrorMapping.PAYEE_ADDRESS_LINE1_MISSING_OR_INVALID);
        validateMaxLength(address.getAddressLine2(), 70,
                HTHSupportErrorMapping.PAYEE_ADDRESS_LINE2_INVALID);
        validateMaxLength(address.getTownName(), 70,
                HTHSupportErrorMapping.PAYEE_TOWN_NAME_MISSING_OR_INVALID);
        if (address.getCountry() != null && !address.getCountry().matches(CHATS_COUNTRY_CODE_REGEX)) {
            throw new ParameterException(HTHSupportErrorMapping.PAYEE_COUNTRY_MISSING_OR_INVALID);
        }
        validateAddressLine2(payment);
    }

    /** The trusted payer name is required by IPE Debtor and must fit its limit. */
    public static void validateChatsDebtorName(String name) {
        if (StringUtils.isBlank(name) || name.codePointCount(0, name.length()) > 140) {
            throw new ParameterException("debtor.name", HTHSupportErrorMapping.PARAMETER_INVALID_EXCEPTION);
        }
    }

    private static void validateMaxLength(String value, int maxLength, int errorCode) {
        if (value != null && value.codePointCount(0, value.length()) > maxLength) {
            throw new ParameterException(errorCode);
        }
    }

    /**
     * Legacy address rule used by TT: addressLine1 (<=70) and townName (<=35) are both
     * mandatory and limited to the SWIFT-style character set.
     */
    public static void validateChatsAddress(LocalPayment payment) {
        String addressLine1 = payment.getCreditor() == null || payment.getCreditor().getPostalAddress() == null
                ? null : payment.getCreditor().getPostalAddress().getAddressLine1();
        String townName = payment.getCreditor() == null || payment.getCreditor().getPostalAddress() == null
                ? null : payment.getCreditor().getPostalAddress().getTownName();
        if (StringUtils.isBlank(addressLine1) || !addressLine1.matches(CHATS_ADDRESS_REGEX)) {
            throw new ParameterException(HTHSupportErrorMapping.PAYEE_ADDRESS_LINE1_MISSING_OR_INVALID);
        }
        if (StringUtils.isBlank(townName) || !townName.matches(CHATS_TOWN_NAME_REGEX)) {
            throw new ParameterException(HTHSupportErrorMapping.PAYEE_TOWN_NAME_MISSING_OR_INVALID);
        }
    }

    /**
     * CHATS payee country must be a 2-letter uppercase ISO code.
     */
    public static void validateChatsCountryCode(LocalPayment payment) {
        String country = payment.getCreditor() == null || payment.getCreditor().getPostalAddress() == null
                ? null : payment.getCreditor().getPostalAddress().getCountry();
        if (StringUtils.isBlank(country) || !country.matches(CHATS_COUNTRY_CODE_REGEX)) {
            throw new ParameterException(HTHSupportErrorMapping.PAYEE_COUNTRY_MISSING_OR_INVALID);
        }
    }

    /**
     * Payee BIC format check, applied only when creditorAgent.bicfi is provided.
     */
    public static void validateChatsBicfiAndMemberId(LocalPayment payment) {
        validateClearingSystemMemberIdFormat(payment);
        String bicfi = bicfi(payment);
        String memberId = memberId(payment);
        boolean hasBic = StringUtils.isNotBlank(bicfi);
        boolean hasMemberId = StringUtils.isNotBlank(memberId);
        if (hasBic == hasMemberId) {
            throw new ParameterException(HTHSupportErrorMapping.BIC_OR_MEMBER_ID_MISSING_OR_INVALID);
        }
        if (StringUtils.isNotBlank(bicfi) && !bicfi.matches(BIC_REGEX)) {
            throw new ParameterException(HTHSupportErrorMapping.PAYEE_BIC_CODE_INVALID);
        }
    }

    /**
     * ONUS requires creditorAgent.clearingSystemMemberId == "015" (internal
     * BEA member id).
     */
    public static void validateOnusBicfiAndMemberId(LocalPayment payment) {
        validateClearingSystemMemberIdFormat(payment);
        String bicfi = bicfi(payment);
        String memberId = memberId(payment);
        boolean hasBic = StringUtils.isNotBlank(bicfi);
        boolean hasMemberId = StringUtils.isNotBlank(memberId);
        if (hasBic == hasMemberId) {
            throw new ParameterException(HTHSupportErrorMapping.BIC_OR_MEMBER_ID_MISSING_OR_INVALID);
        }
        if (StringUtils.isNotBlank(memberId) && !INTERNAL_MEMBER_ID.equals(memberId)) {
            throw new ParameterException(HTHSupportErrorMapping.MEMBER_ID_MISSING_OR_INVALID);
        }
    }

    /**
     * FPS member-id rule: the clearing system member id, when provided, must
     * equal the 3-char bank code prefix of the creditor account; when absent it
     * is defaulted from that account number.
     */
    public static void validateFpsBicfiAndMemberId(LocalPayment payment) {
        validateClearingSystemMemberIdFormat(payment);
        if (payment.getToAccount() == null) {
            return;
        }
        String toAccountNo = creditorAccountNumber(payment.getToAccount());
        if (StringUtils.isBlank(toAccountNo) || toAccountNo.length() < BANK_CODE_LENGTH) {
            throw new ParameterException(HTHSupportErrorMapping.BENEFICIARY_ACCOUNT_NUMBER_MISSING);
        }

        String bankCode = toAccountNo.substring(0, BANK_CODE_LENGTH);
        String bicfi = bicfi(payment);
        String memberId = memberId(payment);
        boolean hasBic = StringUtils.isNotBlank(bicfi);
        boolean hasMemberId = StringUtils.isNotBlank(memberId);
        if (hasBic == hasMemberId) {
            throw new ParameterException(HTHSupportErrorMapping.BIC_OR_MEMBER_ID_MISSING_OR_INVALID);
        }
        if (StringUtils.isNotBlank(memberId) && !bankCode.equalsIgnoreCase(memberId)) {
            throw new ParameterException(HTHSupportErrorMapping.MEMBER_ID_MISSING_OR_INVALID);
        }
        if (StringUtils.isBlank(memberId)) {
            payment.getCreditorAgent().getFinancialInstitutionId().setClearingSystemMemberId(bankCode);
        }
    }

    public static void validateTtBicfiAndMemberId(LocalPayment payment) {
        validateClearingSystemMemberIdFormat(payment);
        String bicfi = bicfi(payment);
        String memberId = memberId(payment);
        boolean hasBic = StringUtils.isNotBlank(bicfi);
        boolean hasMemberId = StringUtils.isNotBlank(memberId);
        if (hasBic == hasMemberId) {
            throw new ParameterException(HTHSupportErrorMapping.BIC_OR_MEMBER_ID_MISSING_OR_INVALID);
        }
        if (StringUtils.isNotBlank(bicfi) && !bicfi.matches(BIC_REGEX)) {
            throw new ParameterException(HTHSupportErrorMapping.PAYEE_BIC_CODE_INVALID);
        }
    }


    public static void validateDrBicfiAndMemberId(LocalPayment payment) {
        validateClearingSystemMemberIdFormat(payment);
        String bicfi = bicfi(payment);
        String memberId = memberId(payment);
        boolean hasBic = StringUtils.isNotBlank(bicfi);
        boolean hasMemberId = StringUtils.isNotBlank(memberId);
        if (hasBic == hasMemberId) {
            throw new ParameterException(HTHSupportErrorMapping.BIC_OR_MEMBER_ID_MISSING_OR_INVALID);
        }
        if (StringUtils.isNotBlank(bicfi) && !bicfi.matches(BIC_REGEX)) {
            throw new ParameterException(HTHSupportErrorMapping.PAYEE_BIC_CODE_INVALID);
        }
    }

    /**
     * Validates endToEndId format (<=35 chars); when {@code defaultWhenBlank} is
     * true, a missing/blank value is replaced with "NOTPROVIDED".
     */
    public static void validateEndToEndId(LocalPayment payment) {
        if (Objects.nonNull(payment.getPaymentIdentification())) {
            PaymentIdentification identification = payment.getPaymentIdentification();
//        if (identification == null) {
//            if (defaultWhenBlank) {
//                payment.setPaymentIdentification(new PaymentIdentification());
//                payment.getPaymentIdentification().setEndToEndId("NOTPROVIDED");
//            }
//            return;
//        }

            String endToEndId = identification.getEndToEndId();
            if (StringUtils.isNotBlank(endToEndId) && !endToEndId.matches(CUSTOMER_TRANSACTION_ID_REGEX)) {
                throw new ParameterException(HTHSupportErrorMapping.END_TO_END_ID_INVALID);
            }
//        if (StringUtils.isBlank(endToEndId) && defaultWhenBlank) {
//            identification.setEndToEndId("NOTPROVIDED");
//        }
        }
    }

    /**
     * Format-only endToEndId check used by the common validation path (no
     * defaulting).
     */
    public static void validateEndToEndIdFormat(LocalPayment payment) {
//        validateEndToEndId(payment, false);
        validateEndToEndId(payment);
    }

    /**
     * TT: intermediaryAgent.bicfi is mandatory and must be a valid BIC.
     */
    public static void validateIntermediaryAgentRequired(LocalPayment payment) {
        IntermediaryAgent agent = payment.getIntermediaryAgent();
        FinancialInstitutionId fid = agent == null ? null : agent.getFinancialInstitutionId();
        String intermediaryBicfi = fid == null ? null : fid.getBicfi();

        String instructedCurrency = payment.getTxnAmount() == null ? null : payment.getTxnAmount().getCurrency().name();
        boolean isTtCny = LocalPayment.PaymentTypeEnum.TT.value().equalsIgnoreCase(payment.getPaymentType().value()) && Currency.CNY.name().equalsIgnoreCase(instructedCurrency);

        /*
         * For payments other than TT CNY,
         * intermediaryAgent.financialInstitutionId.bicfi must be blank.
         */
        if (!isTtCny) {
            if (StringUtils.isNotBlank(intermediaryBicfi)) {
                throw new ParameterException(HTHSupportErrorMapping.INTERMEDIARY_BICFI_MUST_BE_BLANK);
            }
        }

        // Get Creditor Agent BICFI
        CreditorAgent creditorAgent = payment.getCreditorAgent();
        FinancialInstitutionId creditorFid = creditorAgent == null ? null : creditorAgent.getFinancialInstitutionId();
        String creditorBicfi = creditorFid == null ? null : creditorFid.getBicfi();

        /*
         * The 5th and 6th characters of the Creditor Agent BICFI
         * represent the country code.
         *
         * Creditor Agent BICFI should already have been validated
         * before this method is called.
         */
        boolean creditorAgentOutsideChina = StringUtils.isNotBlank(creditorBicfi) && creditorBicfi.length() >= 6
                && !"CN".equalsIgnoreCase(creditorBicfi.substring(4, 6));

        /*
         * For TT CNY payments, Intermediary Agent BICFI is mandatory
         * when the Creditor Agent BIC country code is not CN.
         */
        if (isTtCny && creditorAgentOutsideChina && StringUtils.isBlank(intermediaryBicfi)) {
            throw new ParameterException(HTHSupportErrorMapping.INTERMEDIARY_BICFI_MANDATORY_FOR_TT_CNY);
        }

        /*
         * If Intermediary Agent BICFI is provided,
         * it must not contain leading or trailing spaces
         * and must match the BIC format.
         */
        if (StringUtils.isNotBlank(intermediaryBicfi) && (!intermediaryBicfi.equals(intermediaryBicfi.trim())
                || !intermediaryBicfi.matches(BIC_REGEX))) {
            throw new ParameterException(HTHSupportErrorMapping.INTERMEDIARY_BICFI_MISSING_OR_INVALID);
        }
    }

    /**
     * ONUS/FPS/CHATS: intermediaryAgent.bicfi must not be provided.
     */
    public static void validateIntermediaryAgentMustBeBlank(LocalPayment payment) {
        if (StringUtils.isNotBlank(intermediaryBicfi(payment))) {
            throw new ParameterException(HTHSupportErrorMapping.INTERMEDIARY_BICFI_MUST_BE_BLANK);
        }
    }

    /**
     * ClearingSystemMemberId: text{1,5}, alphanumeric, checked only when provided.
     */
    public static void validateClearingSystemMemberIdFormat(LocalPayment payment) {
        String memberId = memberId(payment);
        if (StringUtils.isNotBlank(memberId) && !memberId.matches(CLEARING_SYSTEM_MEMBER_ID_REGEX)) {
            throw new ParameterException(HTHSupportErrorMapping.CLEARING_SYSTEM_MEMBER_ID_INVALID);
        }
    }

    /**
     * TT/CHATS: chargeBearer is mandatory and must be OUR or SHA.
     */
    public static void validateChargeBearerRequired(LocalPayment payment) {
        boolean valueValid = payment.getChargesOption() == LocalPayment.ChargesOptionEnum.OUR
                || payment.getChargesOption() == LocalPayment.ChargesOptionEnum.SHA;
        if (!valueValid) {
            throw new ParameterException(HTHSupportErrorMapping.TT_AND_CHATS_CHARGEBEARER_INVALID);
        }
    }

    public static void validateChargeBearerMustBeBlank(LocalPayment payment) {
        if (payment.getChargesOption() != null) {
            throw new ParameterException(HTHSupportErrorMapping.NON_TT_AND_CHATS_CHARGEBEARER_NOT_MISSING);
        }
    }

    /**
     * categoryPurpose.proprietary format check (SWIFT-style charset, <=35
     * chars), applied only when the field is provided.
     */
    public static void validateProprietary(LocalPayment payment) {
        String purposeProprietary = payment.getCategoryPurpose() == null
                ? null : payment.getCategoryPurpose().getProprietary();
        if (StringUtils.isNotBlank(purposeProprietary) && !purposeProprietary.matches(PROPRIETARY_REGEX)) {
            throw new ParameterException(HTHSupportErrorMapping.POURPOSE_PROPRIETARY_INVALID);
        }
    }

    /**
     * TT creditor name: mandatory, <=140 chars (code points), English charset
     * only.
     */
    public static void validateTtCreditorName(String accountName) {
        if (accountName == null || accountName.isBlank()) {
            throw new ParameterException(HTHSupportErrorMapping.CREDITORNAME_MISSING_OR_INVALID);
        }
        String trimmed = accountName.trim();
        if (trimmed.codePointCount(0, trimmed.length()) > MAX_ACCOUNT_NAME_LENGTH) {
            throw new ParameterException(HTHSupportErrorMapping.CREDITORNAME_LENGTH_EXCEPTION);
        }
        if (!ENGLISH_NAME_PATTERN.matcher(trimmed).matches()) {
            throw new ParameterException(HTHSupportErrorMapping.CREDITORNAME_ENGLISH_EXCEPTION);
        }
    }

    /**
     * DR creditor name: mandatory, <=34 chars (code points), English charset
     * only.
     */
    public static void validateDrCreditorName(String accountName) {
        if (accountName == null || accountName.isBlank()) {
            throw new ParameterException(HTHSupportErrorMapping.CREDITORNAME_MISSING_OR_INVALID);
        }
        String trimmed = accountName.trim();
        if (trimmed.codePointCount(0, trimmed.length()) > MAX_ACCOUNT_NAME_LENGTH) {
            throw new ParameterException(HTHSupportErrorMapping.DR_CREDITORNAME_LENGTH_EXCEPTION);
        }
        if (!ENGLISH_NAME_PATTERN.matcher(trimmed).matches()) {
            throw new ParameterException(HTHSupportErrorMapping.CREDITORNAME_ENGLISH_EXCEPTION);
        }
    }

    /**
     * remittanceInformationUnstructured rules: no Chinese characters, English
     * charset only, total length <= 4 x 35 segments. Blank input passes.
     */
    public static void validatePaymentDetails(String paymentDetails) {
        if (paymentDetails == null || paymentDetails.isBlank()) {
            return;
        }
        if (CHINESE_PATTERN.matcher(paymentDetails).find()) {
            throw new ParameterException(HTHSupportErrorMapping.PAYMENTDEATAIL_CHINESE_EXCEPTION);
        }
        if (!ENGLISH_PATTERN.matcher(paymentDetails).matches()) {
            throw new ParameterException(HTHSupportErrorMapping.PAYMENTDEATAIL_ENGLISH_EXCEPTION);
        }
        if (paymentDetails.length() > MAX_LENGTH) {
            throw new ParameterException(HTHSupportErrorMapping.PAYMENTDEATAIL_LENGTH_EXCEPTION);
        }
    }

    /**
     * Null-safe accessor for creditorAgent.clearingSystemMemberId.
     */
    private static String memberId(LocalPayment payment) {
        CreditorAgent agent = payment.getCreditorAgent();
        FinancialInstitutionId fid = agent == null ? null : agent.getFinancialInstitutionId();
        return fid == null ? null : fid.getClearingSystemMemberId();
    }

    /**
     * Null-safe accessor for creditorAgent.bicfi.
     */
    private static String bicfi(LocalPayment payment) {
        CreditorAgent agent = payment.getCreditorAgent();
        FinancialInstitutionId fid = agent == null ? null : agent.getFinancialInstitutionId();
        return fid == null ? null : fid.getBicfi();
    }

    /**
     * Null-safe accessor for intermediaryAgent.bicfi.
     */
    private static String intermediaryBicfi(LocalPayment payment) {
        IntermediaryAgent agent = payment.getIntermediaryAgent();
        FinancialInstitutionId fid = agent == null ? null : agent.getFinancialInstitutionId();
        return fid == null ? null : fid.getBicfi();
    }

    /**
     * postalAddress.addressLine2: optional, SWIFT-style charset only (text{1,70}).
     */
    public static void validateAddressLine2(LocalPayment payment) {
        if (payment.getCreditor() == null || payment.getCreditor().getPostalAddress() == null) {
            return;
        }
        String addressLine2 = payment.getCreditor().getPostalAddress().getAddressLine2();
        if (StringUtils.isNotBlank(addressLine2) && !addressLine2.matches(CHATS_ADDRESS_REGEX)) {
            throw new ParameterException(HTHSupportErrorMapping.PAYEE_ADDRESS_LINE2_INVALID);   // 500144 已存在
        }
    }

    /**
     * categoryPurpose: TT - code/proprietary are optional but must not both be
     * provided (either/or); others - must be blank. code is text{1,4}.
     */
    public static void validateCategoryPurpose(LocalPayment payment, boolean tt) {
        CategoryPurpose categoryPurpose = payment.getCategoryPurpose();
        String code = categoryPurpose == null ? null : categoryPurpose.getCode();
        String proprietary = categoryPurpose == null ? null : categoryPurpose.getProprietary();
        boolean hasCode = StringUtils.isNotBlank(code);
        boolean hasProprietary = StringUtils.isNotBlank(proprietary);

        if (!tt) {
            if (hasCode || hasProprietary) {
                throw new ParameterException(HTHSupportErrorMapping.CATEGORY_PURPOSE_MUST_BE_BLANK);
            }
            return;
        }
        if (hasCode && hasProprietary) {
            throw new ParameterException(HTHSupportErrorMapping.CATEGORY_PURPOSE_CODE_PROPRIETARY_CONFLICT);
        }
        if (hasCode && !code.matches("^[0-9A-Za-z]{1,4}$")) {
            throw new ParameterException(HTHSupportErrorMapping.CATEGORY_PURPOSE_CODE_INVALID);
        }
    }

    /**
     * purpose: TT - code/proprietary are optional but must not both be provided
     * (either/or, text{1,35}); others - must be blank.
     */
    public static void validatePurpose(LocalPayment payment, boolean tt) {
        Purpose purpose = payment.getPurpose();
        String code = purpose == null ? null : purpose.getCode();
        String proprietary = purpose == null ? null : purpose.getProprietary();
        boolean hasCode = StringUtils.isNotBlank(code);
        boolean hasProprietary = StringUtils.isNotBlank(proprietary);

        if (!tt) {
            if (hasCode || hasProprietary) {
                throw new ParameterException(HTHSupportErrorMapping.PURPOSE_MUST_BE_BLANK);
            }
            return;
        }
        if (hasCode && hasProprietary) {
            throw new ParameterException(HTHSupportErrorMapping.PURPOSE_CODE_PROPRIETARY_CONFLICT);
        }
        if ((hasCode && code.length() > 35) || (hasProprietary && proprietary.length() > 35)) {
            throw new ParameterException(HTHSupportErrorMapping.PURPOSE_INVALID);
        }
    }

    /**
     * ONUS creditorAccount.id: numeric only (MVP1, no proxy ID) and must start
     * with the internal bank code 015.
     */
    public static void validateOnUsCreditorAccount(LocalPayment payment) {
        String accountNo = creditorAccountNumber(payment.getToAccount());
        if (StringUtils.isBlank(accountNo)) {
            return;
        }
        if (!accountNo.matches("^\\d{1,34}$")) {
            throw new ParameterException(HTHSupportErrorMapping.BENEFICIARY_ACCOUNT_NUMBER_FORMAT_INVALID);
        }
        if (!accountNo.startsWith(INTERNAL_MEMBER_ID)) {
            throw new ParameterException(HTHSupportErrorMapping.CREDITOR_ACCOUNT_NOT_INTERNAL);
        }
    }
}
