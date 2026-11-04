package com.hkbea.hth.exception;

import com.hkbea.common.exception.ErrorMapping;

public class HTHSupportErrorMapping extends ErrorMapping {
    /** Parameter exception: 400xxxx */
    public static final int INVALID_PARAMETER_FORMAT = 400105;
    public static final int INVALID_VALUE_RANGE = 400107;
    public static final int DATE_RANGE_MUTUALLY_EXCLUSIVE = 400108;
    public static final int INVALID_DATE_RANGE = 400109;
    public static final int INVALID_PARAMETER_VALUE = 400110;

    /** Parameter exception: 51xxxx */
    public static final int PET_ID_TOO_LARGE_EXCEPTION = 510001;

    /** Parameter exception: 50xxxx (Account API) */
    public static final int MANDATORY_PARAM_MISSING_EXCEPTION = 500020;
    public static final int INVALID_ACCOUNT_FORMAT_EXCEPTION = 500021;
    public static final int INVALID_CURRENCY_FORMAT_EXCEPTION = 500022;
    public static final int MANDATORY_PARAM_EMPTY_EXCEPTION = 500023;
    public static final int BATCH_SIZE_EXCEED_EXCEPTION = 500024;
    public static final int CURRENCY_LIST_SIZE_EXCEED_EXCEPTION = 500025;
    public static final int DUPLICATE_ACCOUNT_EXCEPTION = 500026;
    public static final int INVALID_PAGE_NO = 500061;
    public static final int INVALID_PAGE_SIZE = 500062;
    public static final int INVALID_DATE_FORMAT = 500063;
    public static final int START_TIME_AFTER_END_TIME = 500064;
    public static final int INVALID_CURSOR_FORMAT_EXCEPTION = 500065;
    public static final int CURSOR_NOT_FOUND_IN_HISTORY_EXCEPTION = 500066;
    public static final int TRA_INVALID_ACCOUNT_FORMAT_EXCEPTION = 500201;

    /** Auth exception: 50007x */
    public static final int CLIENT_ID_NOT_ENABLED_EXCEPTION = 500070;
    public static final int INVALID_ACCESS_TOKEN_EXCEPTION = 500071;
    public static final int INVALID_REQUEST_ENDPOINT_EXCEPTION = 500072;
    public static final int INVALID_REQUEST_PARTID_EXCEPTION = 500073;
    public static final int AUDIT_LOG_ACCESS_DENIED_EXCEPTION = 500074;

    /** System excpetion: 7xxxxx */

    /** Parameter exception: 50xxxx (Transaction History / FX / shared) */
    public static final int INVALID_ACCOUNT_TYPE_EXCEPTION = 500029;
    public static final int INVALID_TRANSACTION_PERIOD_EXCEPTION = 500030;
    public static final int INVALID_DATE_RANGE_EXCEPTION = 500031;
    public static final int DATE_RANGE_EXCEEDS_TWO_YEARS_EXCEPTION = 500032;
    public static final int AMOUNT_POSITIVE_EXCEPTION = 500033;
    public static final int MANDATORY_FROM_AMOUNT_EXCEPTION = 500034;
    public static final int MANDATORY_TO_AMOUNT_EXCEPTION = 500035;
    public static final int INVALID_AMOUNT_RANGE_EXCEPTION = 500036;
    public static final int MANDATORY_CHEQUE_NUMBER_EXCEPTION = 500037;
    public static final int INVALID_CHEQUE_NUMBER_FORMAT_EXCEPTION = 500038;
    public static final int MANDATORY_TRANSACTION_DESCRIPTION_EXCEPTION = 500039;
    public static final int CURRENCY_CODES_MISSING = 500040;
    public static final int UNSUPPORTED_CURRENCY_PROVIDED = 500041;
    public static final int AMOUNTCURRENCY_EXCEPTION = 500042;
    public static final int CURRENCIES_CANNOT_BE_THE_SAME = 500043;
    public static final int MESSAGEID_DUPLICATE_EXCEPTION = 500046;
    public static final int INVALID_ENQUIRY_TYPE = 500047;
    public static final int INVALID_ENQUIRY_TYPE_PARAMETER = 500049;
    public static final int DATE_RANGE_EXCEEDS_60_DAYS_EXCEPTION = 500048;
    public static final int INVALID_DATE_FORMAT_EXCEPTION = 500050;
    public static final int INVALID_ACCOUNT_LENGTH_EXCEPTION = 500051;
    public static final int INVALID_WITHDRAWAL_ACCOUNT_EXCEPTION = 500052;
    public static final int WITHDRAWAL_ACCOUNT_TYPE_MISMATCH_EXCEPTION = 500053;
    public static final int WITHDRAWAL_ACCOUNT_CURRENCY_NOT_SUPPORTED_EXCEPTION = 500054;
    public static final int WITHDRAWAL_ACCOUNT_TYPE_AND_CURRENCY_MISMATCH_EXCEPTION = 500067;
    public static final int INVALID_BIGCODE_FORMAT_EXCEPTION = 500055;
    public static final int INVALID_CURRENCY_PRECISION = 500056;
    public static final int FX_DEAL_AMOUNT_BELOW_MINIMUM_EXCEPTION = 500057;
    public static final int INVALID_ACCOUNT = 500058;
    public static final int INVALID_ACCOUNT_TYPE = 500059;
    public static final int UNSUPPORTED_CURRENCY = 500060;

    // ===================================================================
    // /payment/v1/payment
    // ===================================================================

    /** /payment/v1/payment : parameter errors (50xxxx) */
    public static final int MISSING_MANDATORY_PARAMETER = 500101;
    public static final int DEBIT_ACCOUNT_NUMBER_MISSING = 500102;
    public static final int DEBIT_CURRENCY_MISSING_OR_INVALID = 500103;
    public static final int PAYMENT_CURRENCY_MISSING_OR_INVALID = 500104;
    public static final int CURRENCY_INDICATOR_MISSING_OR_INVALID = 500105;
    public static final int AMOUNT_MISSING_OR_INVALID = 500106;
    public static final int TIME_OF_TRANSFER_MISSING_OR_INVALID = 500107;
    public static final int TRANSFER_DATE_REQUIRED_FOR_PAY_LATER = 500108;
    public static final int TRANSFER_DATE_FORMAT_INVALID = 500109;
    public static final int INCONSISTENT_CURRENCY = 500110;
    public static final int ACCOUNT_IDENTIFIER_MISSING = 500111;
    public static final int BENEFICIARY_ACCOUNT_NUMBER_MISSING = 500112;
    public static final int BENEFICIARY_ACCOUNT_NUMBER_FORMAT_INVALID = 500113;
    public static final int BENEFICIARY_ACCOUNT_NAME_MISSING = 500114;
    public static final int BENEFICIARY_ACCOUNT_NAME_INVALID = 500115;
    public static final int BENEFICIARY_TELEPHONE_MISSING = 500116;
    public static final int BENEFICIARY_TELEPHONE_FORMAT_INVALID = 500117;
    public static final int BENEFICIARY_EMAIL_MISSING = 500118;
    public static final int BENEFICIARY_EMAIL_FORMAT_INVALID = 500119;
    public static final int BENEFICIARY_FPS_ID_MISSING = 500120;
    public static final int BENEFICIARY_FPS_ID_FORMAT_INVALID = 500121;
    public static final int CHARGES_OPTION_MISSING_OR_INVALID = 500122;
    public static final int LIMIT_DEDUCTION_FAILED = 500123;
    public static final int BENEFICIARY_BANK_CODE_UNSUPPORTED = 500124;
    public static final int BENEFICIARY_ACCOUNT_NAME_NOMATCHING = 500125;
    public static final int CUSTOMER_TRANSACTION_ID_MISSING_OR_INVALID = 500126;
    public static final int CUSTOMER_TRANSACTION_ID_DUPLICATE = 500127;
    public static final int END_TO_END_ID_INVALID = 500129;
    public static final int COL_REJECT_ACCOUNT_TYPE = 500130;
    public static final int TD_REJECT_ACCOUNT_TYPE = 500131;
    public static final int EXECUTION_DATE_MISSING_OR_INVALID = 500135;
    public static final int EXECUTION_DATE_NOT_CURRENT_DATE = 500136;
    public static final int PAYER_PAYEE_CURRENCY_NO_MATCHES = 500137;
    public static final int PAYER_ACCOUNT_CURRENCY_INVALID = 500138;
    public static final int MEMBER_ID_MISSING_OR_INVALID = 500139;
    public static final int PAYEE_BIC_CODE_INVALID = 500140;
    public static final int PAYEE_ADDRESS_LINE1_MISSING_OR_INVALID = 500141;
    public static final int PAYEE_TOWN_NAME_MISSING_OR_INVALID = 500142;
    public static final int PAYEE_COUNTRY_MISSING_OR_INVALID = 500143;
    public static final int PAYEE_ADDRESS_LINE2_INVALID = 500144;
    public static final int POURPOSE_PROPRIETARY_INVALID = 500146;
    public static final int TT_PAYEE_INFO_MISSING = 500147;
    public static final int TT_CHARGE_BEARER_MISSING = 500148;
    public static final int DR_CURRENCY_MUST_BE_CNY = 500149;
    public static final int DR_PURPOSE_CODE_MISSING = 500150;
    public static final int DEBTORACCOUNT_ID_MISSING_OR_INVALID = 500160;
    public static final int INSTRUCTEDAMOUNT_AMOUNT_MISSING_OR_INVALID = 500161;
    public static final int CREDITORACCOUNT_ID_LENGTH_EXCEPTION = 500162;
    public static final int CREDITORNAME_MISSING_OR_INVALID = 500163;
    public static final int CREDITORNAME_LENGTH_EXCEPTION = 500164;
    public static final int CREDITORNAME_ENGLISH_EXCEPTION = 500165;
    public static final int PAYMENTDEATAIL_CHINESE_EXCEPTION = 500166;
    public static final int PAYMENTDEATAIL_LENGTH_EXCEPTION = 500167;
    public static final int PAYMENTDEATAIL_ENGLISH_EXCEPTION = 500168;
    public static final int ONUS_AND_DR_PAYMENTDEATAIL_NO_MISSING = 500169;
    public static final int TT_AND_CHATS_CHARGEBEARER_INVALID = 500170;
    public static final int NON_TT_AND_CHATS_CHARGEBEARER_NOT_MISSING = 500171;
    public static final int CREDITORACCOUNT_ID_MISSING_OR_INVALID = 500172;
    /** intermediaryAgent.financialInstitutionId.BICFI: mandatory (field not provided) for TT/DR */
    public static final int INTERMEDIARY_BICFI_MANDATORY_FOR_TT_DR = 500173;
    /** intermediaryAgent.financialInstitutionId.BICFI: provided but null/blank/leading-trailing space/invalid format */
    public static final int INTERMEDIARY_BICFI_MISSING_OR_INVALID = 500174;
    /** intermediaryAgent.financialInstitutionId.BICFI: must be blank for ONUS/FPS/CHATS */
    public static final int INTERMEDIARY_BICFI_MUST_BE_BLANK = 500175;
    /** creditorAgent.financialInstitutionId.BICFI: mandatory for TT (missing/blank/invalid format) */
    public static final int CREDITOR_AGENT_BICFI_MISSING_OR_INVALID = 500176;
    /** creditorAgent: BICFI and clearingSystemMemberId must not be provided together */
    public static final int CREDITOR_AGENT_BIC_MEMBER_ID_CONFLICT = 500177;
    /** creditor.postalAddress.country: not a valid ISO 3166 alpha-2 country code */
    public static final int PAYEE_COUNTRY_INVALID_ISO_CODE = 500178;
    public static final int DR_CREDITORNAME_LENGTH_EXCEPTION = 500179;
    public static final int INSUFFICIENT_BALANCE = 500044;
    public static final int ACCOUNT_CANNOT_BE_THE_SAME = 500045;
    public static final int TXN_AMOUNT_CURRENCY_INVALID = 500180;
    public static final int INSUFFICIENT_FROM_ACCOUNT_BALANCE = 500181;
    public static final int BIC_OR_MEMBER_ID_MISSING_OR_INVALID = 500182;
    public static final int INTERMEDIARY_BICFI_MANDATORY_FOR_TT_CNY = 500183;
    public static final int DEBIT_ACCOUNT_MISSING = 500184;
    public static final int DEBIT_ACCOUNT_TYPE_MISSING = 500185;
    public static final int DEBIT_ACCOUNT_NUMBER_FORMAT_INVALID = 500186;
    public static final int CREDITOR_ACCOUNT_MISSING = 500187;
    public static final int CREDITOR_CURRENCY_MISSING_OR_INVALID = 500188;
    public static final int TXN_AMOUNT_MISSING = 500189;
    public static final int PAYMENT_BENEFICIARY_CURRENCY_NO_MATCHES = 500190;
    public static final int CHATS_DEBIT_CURRENCY_NOT_SUPPORTED = 500191;
    public static final int CHATS_PAYMENT_CURRENCY_NOT_SUPPORTED = 500192;
    public static final int CHATS_CREDITOR_CURRENCY_NOT_SUPPORTED = 500193;
    public static final int OVERSEAS_DEBIT_CURRENCY_NOT_SUPPORTED = 500194;
    public static final int OVERSEAS_PAYMENT_CURRENCY_NOT_SUPPORTED = 500195;
    public static final int OVERSEAS_CREDITOR_CURRENCY_NOT_SUPPORTED = 500196;
    public static final int DR_DEBIT_CURRENCY_NOT_SUPPORTED = 500197;
    public static final int DR_PAYMENT_CURRENCY_NOT_SUPPORTED = 500198;

    /** /payment/v1/payment : service & system errors */
    public static final int IPE_ROUT_EXCEPTION = 610002;
    public static final int NO_PAYMENT_CHANNEL_AVAILABLE = 610003;
    public static final int FPS_MOBILE_IDENTIFIER_NOT_SUPPORTED = 600060;
    public static final int FPS_DEBIT_ACCOUNT_STATUS_INVALID = 600061;
    public static final int CHATS_NOT_ALLOWED_FOR_PAYEE = 600118;
    public static final int ACCOUNT_NOT_SUPPORTED_FOR_CHATS = 600119;
    public static final int CHATS_CURRENCY_VALIDATION_FAILED = 600120;
    public static final int DEBIT_PAYMENT_CURRENCY_COMBINATION_INVALID = 600121;
    public static final int CHATS_SERVICE_NOT_AVAILABLE = 600122;
    public static final int CHATS_TIMT_SERVICE_NOT_AVAILABLE = 600123;
    public static final int SCAMETER_PAYMENT_REJECTED = 600130;
    public static final int PAYMENT_NOT_FOUND_EXCEPTION = 600131;
    public static final int OVERSEAS_IPE_CALL_FAILED = 600140;
    public static final int OVERSEAS_CURRENCY_VALIDATION_FAILED = 600141;
    public static final int OVERSEAS_COUNTRY_VALIDATION_FAILED = 600142;
    /**
     * Beneficiary account does not exist.
     */
    public static final int BENEFICIARY_ACCOUNT_NOT_FOUND = 600201;
    /**
     * Beneficiary account is not active.
     */
    public static final int BENEFICIARY_ACCOUNT_NOT_ACTIVE = 600202;
    /**
     * Beneficiary account payment transfer is blocked.
     */
    public static final int PAYMENT_TRANSFER_BLOCK = 600203;
    public static final int SCAMETER_SERVICE_UNAVAILABLE = 700131;
    // The payment flow can also surface these shared codes kept in their own groups:
    //   ACCOUNT_NOT_FOUND_EXCEPTION (600020), ADC_CALL_FAILED_EXCEPTION (600036)

    // ===================================================================

    /** Service exception: 61xxxx */
    public static final int NO_PET_EXCEPTION = 610001;

    /** Service exception: 60xxxx (Account API) */
    public static final int ACCOUNT_NOT_FOUND_EXCEPTION = 600020;
    public static final int BALANCE_NOT_FOUND_EXCEPTION = 600022;
    public static final int CLIENT_NOT_PERMISSIONS_EXCEPTION = 600021;
    public static final int CLIENT_NOT_ACCOUNT_PERMISSIONS_EXCEPTION = 600023;
    public static final int USER_NOT_ACCOUNT_INQUIRY_PERMISSIONS_EXCEPTION = 600024;
    public static final int USER_NOT_PERMISSIONS_EXCEPTION = 600025;
    public static final int API_ACCESS_DENIED_EXCEPTION = 600026;
    public static final int ACCOUNT_LIST_ACCESS_DENIED_EXCEPTION = 600027;
    public static final int NO_ACCOUNT_ASSIGNED_FOR_FUNCTION_EXCEPTION = 600028;
    public static final int RATE_LIMIT_EXCEEDED_EXCEPTION = 600029;

    /**
     * Exchange rate has changed.
     */
    public static final int EXCHANGE_RATE_CHANGED_EXCEPTION = 600051;
    public static final int QUERY_PARAMETER_MISMATCH_EXCEPTION = 600052;

    /** Service exception: FX API */
    public static final int RATE_ID_EXPIRED_EXCEPTION = 600053;
    public static final int RATE_ID_ALREADY_USED_EXCEPTION = 600054;
    public static final int RATE_ID_CURRENCY_PAIR_MISMATCH_EXCEPTION = 600056;
    public static final int RATE_ID_ACCOUNT_NUMBER_MISMATCH_EXCEPTION = 600057;
    public static final int ACCOUNT_OWNER_MISMATCH_EXCEPTION = 600058;
    public static final int OUTSIDE_BUSINESS_WINDOW = 600059;
    public static final int INVALID_CURRENCY_PAIR = 600030;
    public static final int EXCHANGE_RATE_UNAVAILABLE = 600031;
    public static final int UNABLE_TO_VALIDATE_TRADING_RESTRICTION = 600032;
    public static final int FX_TRANSACTION_NOT_ALLOWED_RESTRICTED_PERIOD = 600033;

    public static final int BG3_CALL_FAILED_EXCEPTION = 600035;
    public static final int ADC_CALL_FAILED_EXCEPTION = 600036;
    public static final int FX_CALL_FAILED_EXCEPTION = 600037;

    /** System exception: 7xxxxx */
    public static final int AUDIT_LOG_SYSTEM_ERROR = 700060;
    public static final int SYSTEM_EXCEPTION = 700000;

    static {
        // ===================================================================
        // /payment/v1/payment : parameter errors
        // ===================================================================
        MESSAGE_MAP.put(INSUFFICIENT_BALANCE, "The transaction could not be processed because the account balance for the specified currency could not be found or the transaction amount exceeds the available daily transaction limit.");
        MESSAGE_MAP.put(ACCOUNT_CANNOT_BE_THE_SAME, "Parameters 'fromAccount' and 'toAccount' cannot be the same.");
        MESSAGE_MAP.put(MISSING_MANDATORY_PARAMETER, "Missing mandatory parameter.");
        MESSAGE_MAP.put(DEBIT_ACCOUNT_NUMBER_MISSING, "The mandatory parameter 'debtorAccount.id' is missing or empty.");
        MESSAGE_MAP.put(DEBIT_CURRENCY_MISSING_OR_INVALID, "The mandatory parameter 'debtorAccount.currency' is missing or invalid.");
        MESSAGE_MAP.put(PAYMENT_CURRENCY_MISSING_OR_INVALID, "The mandatory parameter 'instructedAmount.currency' is missing or invalid.");
        MESSAGE_MAP.put(CURRENCY_INDICATOR_MISSING_OR_INVALID, "The mandatory parameter 'currencyIndicator' is missing or invalid.");
        MESSAGE_MAP.put(AMOUNT_MISSING_OR_INVALID, "The mandatory parameter 'instructedAmount.amount' is missing or invalid.");
        MESSAGE_MAP.put(TIME_OF_TRANSFER_MISSING_OR_INVALID, "The mandatory parameter 'timeOfTransfer' is missing or invalid.");
        MESSAGE_MAP.put(TRANSFER_DATE_REQUIRED_FOR_PAY_LATER, "The mandatory parameter 'transferDate' is missing or empty for pay-later transactions.");
        MESSAGE_MAP.put(TRANSFER_DATE_FORMAT_INVALID, "The parameter 'transferDate' is not in a valid date format.");
        MESSAGE_MAP.put(INCONSISTENT_CURRENCY, "Inconsistent currency.");
        MESSAGE_MAP.put(ACCOUNT_IDENTIFIER_MISSING, "The mandatory parameter 'creditorAccount.accountIdentifier' is missing or empty.");
        MESSAGE_MAP.put(BENEFICIARY_ACCOUNT_NUMBER_MISSING, "The mandatory parameter 'creditorAccount.id' is missing or empty.");
        MESSAGE_MAP.put(BENEFICIARY_ACCOUNT_NUMBER_FORMAT_INVALID, "The parameter 'creditorAccount.id' is not in a valid format.");
        MESSAGE_MAP.put(BENEFICIARY_ACCOUNT_NAME_MISSING, "The mandatory parameter 'creditor.creditorName' is missing or empty.");
        MESSAGE_MAP.put(BENEFICIARY_ACCOUNT_NAME_INVALID, "The parameter 'creditor.creditorName' is invalid.");
        MESSAGE_MAP.put(BENEFICIARY_TELEPHONE_MISSING, "The mandatory parameter 'creditorAccount.id' is missing or empty.");
        MESSAGE_MAP.put(BENEFICIARY_TELEPHONE_FORMAT_INVALID, "The parameter 'creditorAccount.id' is not a valid telephone number.");
        MESSAGE_MAP.put(BENEFICIARY_EMAIL_MISSING, "The mandatory parameter 'creditorAccount.id' is missing or empty.");
        MESSAGE_MAP.put(BENEFICIARY_EMAIL_FORMAT_INVALID, "The parameter 'creditorAccount.id' is not a valid email address.");
        MESSAGE_MAP.put(BENEFICIARY_FPS_ID_MISSING, "The mandatory parameter 'creditorAccount.id' is missing or empty.");
        MESSAGE_MAP.put(BENEFICIARY_FPS_ID_FORMAT_INVALID, "The parameter 'creditorAccount.id' is not a valid FPS ID.");
        MESSAGE_MAP.put(CHARGES_OPTION_MISSING_OR_INVALID, "The parameter 'chargeBearer' is missing or invalid.");
        MESSAGE_MAP.put(LIMIT_DEDUCTION_FAILED, "Please adjust your transfer amount or request a limit increase.");
        MESSAGE_MAP.put(BENEFICIARY_BANK_CODE_UNSUPPORTED, "beneficiary bank code is not in the supported FPS participant list.");
        MESSAGE_MAP.put(BENEFICIARY_ACCOUNT_NAME_NOMATCHING, "Beneficiary Account Name Mismatch");
        MESSAGE_MAP.put(CUSTOMER_TRANSACTION_ID_MISSING_OR_INVALID, "transactionID is missing or invalid.");
        MESSAGE_MAP.put(CUSTOMER_TRANSACTION_ID_DUPLICATE, "transactionID is a duplicate of a transaction processed within the last 2 years");
        MESSAGE_MAP.put(END_TO_END_ID_INVALID, "paymentIdentification.endToEndId is invalid");
        MESSAGE_MAP.put(COL_REJECT_ACCOUNT_TYPE, "Collection Account is not eligible for payment transaction.");
        MESSAGE_MAP.put(TD_REJECT_ACCOUNT_TYPE, "Time Deposit Account is not eligible for payment transaction.");
        MESSAGE_MAP.put(EXECUTION_DATE_MISSING_OR_INVALID, "settlementDate is missing or invalid (yyyy-mm-dd)");
        MESSAGE_MAP.put(EXECUTION_DATE_NOT_CURRENT_DATE, "settlementDate must be the current date");
        MESSAGE_MAP.put(PAYER_PAYEE_CURRENCY_NO_MATCHES, "debtorAccount.currency and instructedAmount.currency do not match.");
        MESSAGE_MAP.put(PAYER_ACCOUNT_CURRENCY_INVALID, "debtorAccount.currency is invalid");
        MESSAGE_MAP.put(MEMBER_ID_MISSING_OR_INVALID, "creditorAgent.financialInstitutionId.clearingSystemMemberId is missing or invalid");
        MESSAGE_MAP.put(PAYEE_BIC_CODE_INVALID, "creditorAgent.financialInstitutionId.bicfi is invalid");
        MESSAGE_MAP.put(PAYEE_ADDRESS_LINE1_MISSING_OR_INVALID, "creditor.postalAddress.addressLine1 is missing or invalid");
        MESSAGE_MAP.put(PAYEE_TOWN_NAME_MISSING_OR_INVALID, "creditor.postalAddress.townName is missing or invalid");
        MESSAGE_MAP.put(PAYEE_COUNTRY_MISSING_OR_INVALID, "creditor.postalAddress.country is missing or invalid");
        MESSAGE_MAP.put(PAYEE_ADDRESS_LINE2_INVALID, "creditor.postalAddress.addressLine2 is invalid");
        MESSAGE_MAP.put(POURPOSE_PROPRIETARY_INVALID, "categoryPurpose.proprietary is invalid");
        MESSAGE_MAP.put(TT_PAYEE_INFO_MISSING, "TT payment requires complete beneficiary information (name/accountNo/currency).");
        MESSAGE_MAP.put(TT_CHARGE_BEARER_MISSING, "The parameter 'chargeBearer' is missing for TT payment.");
        MESSAGE_MAP.put(DR_CURRENCY_MUST_BE_CNY, "DR payment only supports CNY.");
        MESSAGE_MAP.put(DR_PURPOSE_CODE_MISSING, "The mandatory parameter 'categoryPurpose.code' is missing.");
        MESSAGE_MAP.put(DEBTORACCOUNT_ID_MISSING_OR_INVALID, "debtorAccount.id is missing or invalid." );
        MESSAGE_MAP.put(INSTRUCTEDAMOUNT_AMOUNT_MISSING_OR_INVALID, "instructedAmount.amount is missing or invalid." );
        MESSAGE_MAP.put(CREDITORACCOUNT_ID_LENGTH_EXCEPTION, "The parameter 'creditorAccount.id' must not exceed 34 characters." );
        MESSAGE_MAP.put(CREDITORNAME_MISSING_OR_INVALID, "The mandatory parameter 'creditor.creditorName' is missing or invalid." );
        MESSAGE_MAP.put(CREDITORNAME_LENGTH_EXCEPTION, "The parameter 'creditor.creditorName' must not exceed 140 characters." );
        MESSAGE_MAP.put(CREDITORNAME_ENGLISH_EXCEPTION, "The parameter 'creditor.creditorName' must contain either English characters only." );
        MESSAGE_MAP.put(PAYMENTDEATAIL_CHINESE_EXCEPTION, "remittanceInformationUnstructured must not contain Chinese characters." );
        MESSAGE_MAP.put(PAYMENTDEATAIL_LENGTH_EXCEPTION, "remittanceInformationUnstructured must not exceed 140 characters." );
        MESSAGE_MAP.put(PAYMENTDEATAIL_ENGLISH_EXCEPTION, "The remittanceInformationUnstructured field allows only English letters, numbers, spaces, and specified special characters." );
        MESSAGE_MAP.put(ONUS_AND_DR_PAYMENTDEATAIL_NO_MISSING, "remittanceInformationUnstructured must be blank for ONUS and DR payments." );
        MESSAGE_MAP.put(TT_AND_CHATS_CHARGEBEARER_INVALID, "For TT and CHATS transactions, chargeBearer is mandatory and must be either OUR or SHA." );
        MESSAGE_MAP.put(NON_TT_AND_CHATS_CHARGEBEARER_NOT_MISSING, "For payment types other than TT and CHATS, chargeBearer must be blank." );
        MESSAGE_MAP.put(CREDITORACCOUNT_ID_MISSING_OR_INVALID, "creditorAccount.id is missing or invalid." );
        MESSAGE_MAP.put(INTERMEDIARY_BICFI_MANDATORY_FOR_TT_DR, "intermediaryAgent.financialInstitutionId.bicfi is mandatory for TT and DR" );
        MESSAGE_MAP.put(INTERMEDIARY_BICFI_MISSING_OR_INVALID, "intermediaryAgent.financialInstitutionId.bicfi is missing or invalid" );
        MESSAGE_MAP.put(INTERMEDIARY_BICFI_MUST_BE_BLANK, "intermediaryAgent.financialInstitutionId.bicfi must be blank for payments other than TT CNY." );
        MESSAGE_MAP.put(CREDITOR_AGENT_BICFI_MISSING_OR_INVALID, "creditorAgent.financialInstitutionId.bicfi is missing or invalid" );
        MESSAGE_MAP.put(CREDITOR_AGENT_BIC_MEMBER_ID_CONFLICT, "creditorAgent.financialInstitutionId.bicfi and creditorAgent.financialInstitutionId.clearingSystemMemberId cannot be provided together" );
        MESSAGE_MAP.put(PAYEE_COUNTRY_INVALID_ISO_CODE, "creditor.postalAddress.country is not a valid ISO 3166 alpha-2 country code" );
        MESSAGE_MAP.put(DR_CREDITORNAME_LENGTH_EXCEPTION, "The parameter 'creditor.creditorName' must not exceed 34 characters." );
        MESSAGE_MAP.put(TXN_AMOUNT_CURRENCY_INVALID, "instructedAmount.currency missing or invalid");
        MESSAGE_MAP.put(INSUFFICIENT_FROM_ACCOUNT_BALANCE, "the fromAccount has insufficient balance for the txnAmount.");
        MESSAGE_MAP.put(BIC_OR_MEMBER_ID_MISSING_OR_INVALID, "BICFI and Clearing System Member ID are mutually exclusive. Provide either one.");
        MESSAGE_MAP.put(INTERMEDIARY_BICFI_MANDATORY_FOR_TT_CNY, "intermediaryAgent.financialInstitutionId.bicfi is mandatory for TT CNY payments when the country code of creditorAgent.financialInstitutionId.bicfi is not CN.");
        MESSAGE_MAP.put(DEBIT_ACCOUNT_MISSING, "The mandatory parameter 'debtorAccount' is missing or empty.");
        MESSAGE_MAP.put(DEBIT_ACCOUNT_TYPE_MISSING, "The mandatory parameter 'debtorAccount.accountType' is missing or empty.");
        MESSAGE_MAP.put(DEBIT_ACCOUNT_NUMBER_FORMAT_INVALID, "The parameter 'debtorAccount.id' is not in a valid format.");
        MESSAGE_MAP.put(CREDITOR_ACCOUNT_MISSING, "The mandatory parameter 'creditorAccount' is missing or empty.");
        MESSAGE_MAP.put(CREDITOR_CURRENCY_MISSING_OR_INVALID, "The mandatory parameter 'creditorAccount.currency' is missing or invalid.");
        MESSAGE_MAP.put(TXN_AMOUNT_MISSING, "The mandatory parameter 'instructedAmount' is missing or empty.");
        MESSAGE_MAP.put(PAYMENT_BENEFICIARY_CURRENCY_NO_MATCHES, "instructedAmount.currency and creditorAccount.currency do not match.");
        MESSAGE_MAP.put(CHATS_DEBIT_CURRENCY_NOT_SUPPORTED, "debtorAccount.currency is not supported for CHATS transfers.");
        MESSAGE_MAP.put(CHATS_PAYMENT_CURRENCY_NOT_SUPPORTED, "instructedAmount.currency is not supported for CHATS transfers.");
        MESSAGE_MAP.put(CHATS_CREDITOR_CURRENCY_NOT_SUPPORTED, "creditorAccount.currency is not supported for CHATS transfers.");
        MESSAGE_MAP.put(OVERSEAS_DEBIT_CURRENCY_NOT_SUPPORTED, "debtorAccount.currency is not supported for overseas (TT) payments.");
        MESSAGE_MAP.put(OVERSEAS_PAYMENT_CURRENCY_NOT_SUPPORTED, "instructedAmount.currency is not supported for overseas (TT) payments.");
        MESSAGE_MAP.put(OVERSEAS_CREDITOR_CURRENCY_NOT_SUPPORTED, "creditorAccount.currency is not supported for overseas (TT) payments.");
        MESSAGE_MAP.put(DR_DEBIT_CURRENCY_NOT_SUPPORTED, "debtorAccount.currency is not supported for DR payments. Supported currencies: HKD, USD, CNY.");
        MESSAGE_MAP.put(DR_PAYMENT_CURRENCY_NOT_SUPPORTED, "instructedAmount.currency is not supported for DR payments. Supported currencies: HKD, USD, CNY.");

        // ===================================================================
        // /payment/v1/payment : service & system errors
        // ===================================================================
        MESSAGE_MAP.put(FPS_MOBILE_IDENTIFIER_NOT_SUPPORTED, "Currently, transactions are only supported via account numbers.");
        MESSAGE_MAP.put(FPS_DEBIT_ACCOUNT_STATUS_INVALID, "Invalid account status.");
        MESSAGE_MAP.put(CHATS_NOT_ALLOWED_FOR_PAYEE, "CHATS not allowed for selected payee identification.");
        MESSAGE_MAP.put(ACCOUNT_NOT_SUPPORTED_FOR_CHATS, "Beneficiary account is not supported for CHATS transfers.");
        MESSAGE_MAP.put(CHATS_CURRENCY_VALIDATION_FAILED, "CHATS currency validation failed.");
        MESSAGE_MAP.put(DEBIT_PAYMENT_CURRENCY_COMBINATION_INVALID, "Debit and payment currency pair is not supported.");
        MESSAGE_MAP.put(CHATS_SERVICE_NOT_AVAILABLE, "CHATS service is not available.");
        MESSAGE_MAP.put(CHATS_TIMT_SERVICE_NOT_AVAILABLE, "The chats time is not supported.");
        MESSAGE_MAP.put(SCAMETER_PAYMENT_REJECTED, "Payment rejected due to a high-risk beneficiary alert.");
        MESSAGE_MAP.put(PAYMENT_NOT_FOUND_EXCEPTION, "Payment transaction not found for the given transactionId.");
        MESSAGE_MAP.put(OVERSEAS_IPE_CALL_FAILED, "Overseas payment submission failed.");
        MESSAGE_MAP.put(OVERSEAS_CURRENCY_VALIDATION_FAILED, "Overseas payment currency validation failed.");
        MESSAGE_MAP.put(OVERSEAS_COUNTRY_VALIDATION_FAILED, "Overseas payment country validation failed.");
        MESSAGE_MAP.put(BENEFICIARY_ACCOUNT_NOT_FOUND, "Beneficiary account does not exist.");
        MESSAGE_MAP.put(BENEFICIARY_ACCOUNT_NOT_ACTIVE, "Beneficiary account is not active.");
        MESSAGE_MAP.put(PAYMENT_TRANSFER_BLOCK, "Transfer to the selected beneficiary account is not allowed.");
        MESSAGE_MAP.put(NO_PAYMENT_CHANNEL_AVAILABLE, "No eligible payment channel is available for this payment. Please check the payment details and try again later.");
        MESSAGE_MAP.put(SCAMETER_SERVICE_UNAVAILABLE, "The beneficiary screening service is temporarily unavailable. Please try again later.");
        // ===================================================================
        // end of /payment/v1/payment
        // ===================================================================

        MESSAGE_MAP.put(TRA_INVALID_ACCOUNT_FORMAT_EXCEPTION, "The parameter 'accountNumber' is not in a valid format.");

        MESSAGE_MAP.put(PET_ID_TOO_LARGE_EXCEPTION, "Pet ID must be less than 1000.");
        MESSAGE_MAP.put(NO_PET_EXCEPTION, "No pet found with the specified ID.");
        MESSAGE_MAP.put(IPE_ROUT_EXCEPTION, "Failed to invoke IPE Routing.");

        // Account API mappings
        MESSAGE_MAP.put(MANDATORY_PARAM_MISSING_EXCEPTION, "The mandatory parameter 'accountNumber' is missing or empty.");
        MESSAGE_MAP.put(INVALID_ACCOUNT_FORMAT_EXCEPTION, "The parameter 'accountNumber' is not in a valid format.");
        MESSAGE_MAP.put(INVALID_CURRENCY_FORMAT_EXCEPTION, "Invalid currency code. Please provide a valid 3-letter ISO currency code (e.g., HKD, USD).");
        MESSAGE_MAP.put(MANDATORY_PARAM_EMPTY_EXCEPTION, "Mandatory parameters are missing or empty in the request.");
        MESSAGE_MAP.put(BATCH_SIZE_EXCEED_EXCEPTION, "The number of requested accounts exceeds the maximum allowed batch size of 30.");
        MESSAGE_MAP.put(CURRENCY_LIST_SIZE_EXCEED_EXCEPTION, "Currency list size exceeds maximum possible unique currencies.");
        MESSAGE_MAP.put(ACCOUNT_NOT_FOUND_EXCEPTION, "The specified account does not exist or is not accessible.");
        MESSAGE_MAP.put(BALANCE_NOT_FOUND_EXCEPTION, "No balance information was found for the requested account.");
        MESSAGE_MAP.put(CLIENT_NOT_PERMISSIONS_EXCEPTION, "Account service is not enabled for your company.");
        MESSAGE_MAP.put(CLIENT_NOT_ACCOUNT_PERMISSIONS_EXCEPTION, "This profile does not have permission to query the requested account(s).");
        MESSAGE_MAP.put(USER_NOT_ACCOUNT_INQUIRY_PERMISSIONS_EXCEPTION, "This profile is not authorized to perform account inquiry.");
        MESSAGE_MAP.put(USER_NOT_PERMISSIONS_EXCEPTION, "This profile is not authorized to access this service.");
        MESSAGE_MAP.put(API_ACCESS_DENIED_EXCEPTION, "Access denied. User does not have permission to perform this action.");
        MESSAGE_MAP.put(ACCOUNT_LIST_ACCESS_DENIED_EXCEPTION, "You do not have permission this account list.");
        MESSAGE_MAP.put(NO_ACCOUNT_ASSIGNED_FOR_FUNCTION_EXCEPTION, "You do not have any account(s) assigned for this function. Please contact your Authorized Person for assignment.");
        MESSAGE_MAP.put(RATE_LIMIT_EXCEEDED_EXCEPTION, "Rate limit exceeded. Please retry later.");
        MESSAGE_MAP.put(DUPLICATE_ACCOUNT_EXCEPTION, "Duplicate account number found in the request.");
        MESSAGE_MAP.put(INVALID_WITHDRAWAL_ACCOUNT_EXCEPTION, "Please enter another valid withdrawal account.");
        MESSAGE_MAP.put(WITHDRAWAL_ACCOUNT_TYPE_MISMATCH_EXCEPTION, "Please enter an account type that matches your withdrawal account, or enter another withdrawal account.");
        MESSAGE_MAP.put(WITHDRAWAL_ACCOUNT_CURRENCY_NOT_SUPPORTED_EXCEPTION, "Please enter a currency supported by the withdrawal account, or enter another withdrawal account.");
        MESSAGE_MAP.put(WITHDRAWAL_ACCOUNT_TYPE_AND_CURRENCY_MISMATCH_EXCEPTION, "Please enter an account type and currency that match your withdrawal account, or enter another withdrawal account.");
        MESSAGE_MAP.put(INVALID_BIGCODE_FORMAT_EXCEPTION, "The parameter 'BICFI' is not in a valid format.");

        // Bank Transfer / FX parameter mappings
        MESSAGE_MAP.put(CURRENCY_CODES_MISSING, "One or both currency codes are missing.");
        MESSAGE_MAP.put(UNSUPPORTED_CURRENCY_PROVIDED, "Invalid or unsupported currency code provided.");
        MESSAGE_MAP.put(CURRENCIES_CANNOT_BE_THE_SAME, "Parameters 'fromCurrency' and 'toCurrency' cannot be the same.");
        MESSAGE_MAP.put(AMOUNTCURRENCY_EXCEPTION, "Parameter 'amountCurrency' must be the same as 'fromCurrency' or 'toCurrency'.");
        MESSAGE_MAP.put(MESSAGEID_DUPLICATE_EXCEPTION, "the Message-Id is a duplicate of a transaction processed within the last 24 hours.");
        MESSAGE_MAP.put(INVALID_ENQUIRY_TYPE, "Invalid enquiry type. Allowed values are I or Q.");
        MESSAGE_MAP.put(INVALID_ENQUIRY_TYPE_PARAMETER, "For enquiry type I, 'toCurrency' must be HKD when provided.");

        // Transaction History / generic mappings
        MESSAGE_MAP.put(INVALID_PAGE_NO, "Invalid page number. Page number must be greater than or equal to 1.");
        MESSAGE_MAP.put(INVALID_PAGE_SIZE, "Invalid page size. Page size must be between 1 and 50.");
        MESSAGE_MAP.put(INVALID_DATE_FORMAT, "The parameters 'startTime' or 'endTime' are not in a valid format.");
        MESSAGE_MAP.put(START_TIME_AFTER_END_TIME, "'startTime' cannot be later than 'endTime'.");
        MESSAGE_MAP.put(AUDIT_LOG_SYSTEM_ERROR, "Unable to retrieve audit log records at this time. Please try again later.");
        MESSAGE_MAP.put(CLIENT_ID_NOT_ENABLED_EXCEPTION, "HTH API service is not enabled for the client's company");
        MESSAGE_MAP.put(INVALID_ACCESS_TOKEN_EXCEPTION, "Invalid or expired access token.");
        MESSAGE_MAP.put(AUDIT_LOG_ACCESS_DENIED_EXCEPTION,
                "Access denied. You are authorized to view only your own audit logs.");
        MESSAGE_MAP.put(SYSTEM_EXCEPTION, "Unexpected system error occurred while processing the request.");
        MESSAGE_MAP.put(INVALID_ACCOUNT_TYPE_EXCEPTION, "Invalid account type. Please enter a valid account type.");
        MESSAGE_MAP.put(INVALID_TRANSACTION_PERIOD_EXCEPTION, "Invalid transactionPeriod. Allowed values: TODAY, DATE_RANGE.");
        MESSAGE_MAP.put(INVALID_DATE_RANGE_EXCEPTION, "Invalid date range.fromDate must be before or equal to toDate and cannot be a future date.");
        MESSAGE_MAP.put(DATE_RANGE_EXCEEDS_TWO_YEARS_EXCEPTION, "Date range exceeds the maximum allowed period of 2 years.");
        MESSAGE_MAP.put(BG3_CALL_FAILED_EXCEPTION, "BG3 host call failed. Please retry later.");
        MESSAGE_MAP.put(ADC_CALL_FAILED_EXCEPTION, "ADC service call failed. Please retry later.");
        MESSAGE_MAP.put(FX_CALL_FAILED_EXCEPTION, "Unable to retrieve FX at this time. Please try again later.");

        MESSAGE_MAP.put(MANDATORY_FROM_AMOUNT_EXCEPTION, "The mandatory parameter 'minAmount' is missing or empty.");
        MESSAGE_MAP.put(MANDATORY_TO_AMOUNT_EXCEPTION, "The mandatory parameter 'maxAmount' is missing or empty.");
        MESSAGE_MAP.put(INVALID_AMOUNT_RANGE_EXCEPTION, "The minAmount should be less than the maxAmount.");
        MESSAGE_MAP.put(MANDATORY_CHEQUE_NUMBER_EXCEPTION, "Please enter cheque number.");
        MESSAGE_MAP.put(INVALID_CHEQUE_NUMBER_FORMAT_EXCEPTION, "Please enter a cheque number of up to 6 digits.");
        MESSAGE_MAP.put(MANDATORY_TRANSACTION_DESCRIPTION_EXCEPTION, "Please enter transaction description.");
        MESSAGE_MAP.put(AMOUNT_POSITIVE_EXCEPTION, "The amount must be greater than 0.");
        MESSAGE_MAP.put(DATE_RANGE_EXCEEDS_60_DAYS_EXCEPTION, "The date range must not exceed 60 days.");
        MESSAGE_MAP.put(INVALID_ACCOUNT_LENGTH_EXCEPTION, "Invalid account number format. Expected format: a 14- or 15-digit numeric string.");
        MESSAGE_MAP.put(INVALID_DATE_FORMAT_EXCEPTION, "Invalid date format. fromDate and toDate must use yyyy-mm-dd.");
        MESSAGE_MAP.put(INVALID_CURSOR_FORMAT_EXCEPTION, "Invalid cursor format. Expected 9 digit numeric string.");
        MESSAGE_MAP.put(CURSOR_NOT_FOUND_IN_HISTORY_EXCEPTION, "No historical record was found for this cursor number.");

        // Auth Account API mappings
        MESSAGE_MAP.put(INVALID_REQUEST_ENDPOINT_EXCEPTION, "Exception occurred while reading the request endpoint.");
        MESSAGE_MAP.put(INVALID_REQUEST_PARTID_EXCEPTION, "Exception occurred while reading the request partyId.");

        // FX  Service API mappings
        MESSAGE_MAP.put(EXCHANGE_RATE_CHANGED_EXCEPTION, "Exchange rate has changed. Please resubmit with the latest quotation.");
        MESSAGE_MAP.put(QUERY_PARAMETER_MISMATCH_EXCEPTION, "deal Input parameters must match query parameters.");
        MESSAGE_MAP.put(RATE_ID_EXPIRED_EXCEPTION, "Invalid or Expired Rate ID. Please perform the rate inquiry again.");
        MESSAGE_MAP.put(RATE_ID_ALREADY_USED_EXCEPTION, "RATE_ID has already been used and cannot be reused.");
        MESSAGE_MAP.put(RATE_ID_CURRENCY_PAIR_MISMATCH_EXCEPTION, "The requested currency pair does not match the currency pair associated with the provided rateId.");
        MESSAGE_MAP.put(RATE_ID_ACCOUNT_NUMBER_MISMATCH_EXCEPTION, "The requested account number does not match the account number associated with the provided rateId.");
        MESSAGE_MAP.put(ACCOUNT_OWNER_MISMATCH_EXCEPTION, "ACCOUNT_OWNER_MISMATCH: fromAccount and toAccount must belong to the same customer.");
        MESSAGE_MAP.put(INVALID_CURRENCY_PRECISION, "Invalid currency precision. JPY amounts must not contain decimal places.");
        MESSAGE_MAP.put(FX_DEAL_AMOUNT_BELOW_MINIMUM_EXCEPTION, "FX Deal Amount should be greater than the equivalent of HKD 1,000.");
        MESSAGE_MAP.put(OUTSIDE_BUSINESS_WINDOW, "The foreign exchange market is closed. Please try again during business hours.");
        // FX Inquiry service error mappings
        MESSAGE_MAP.put(INVALID_CURRENCY_PAIR, "The exchange currency is a currency pair that is not supported for now");
        MESSAGE_MAP.put(EXCHANGE_RATE_UNAVAILABLE, "The exchange rate of these two currencies cannot be used or has expired");
        MESSAGE_MAP.put(INVALID_ACCOUNT, "The requested account number is invalid." );
        MESSAGE_MAP.put(INVALID_ACCOUNT_TYPE, "The requested account type is unsupported." );
        MESSAGE_MAP.put(UNSUPPORTED_CURRENCY, "The requested currency is unsupported." );
        MESSAGE_MAP.put(UNABLE_TO_VALIDATE_TRADING_RESTRICTION, "Unable to validate trading restriction. Please try again later.");
        MESSAGE_MAP.put(FX_TRANSACTION_NOT_ALLOWED_RESTRICTED_PERIOD, "FX transaction is not allowed for the debit account during the restricted trading period.");
        MESSAGE_MAP.put(INVALID_VALUE_RANGE, "Valid value range of %s is %s.");
        MESSAGE_MAP.put(DATE_RANGE_MUTUALLY_EXCLUSIVE, "[%s, %s] cannot coexist with [%s]");
        MESSAGE_MAP.put(INVALID_PARAMETER_FORMAT, "Invalid format of %s, supported format is %s.");
        MESSAGE_MAP.put(INVALID_DATE_RANGE, "%s must be earlier than or equal to %s.");
        MESSAGE_MAP.put(INVALID_PARAMETER_VALUE, "The specified parameter value is unknown to HTH: %s=<%s>.");
    }
}
