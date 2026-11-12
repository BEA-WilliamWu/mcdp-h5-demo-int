package com.hkbea.hth.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.hkbea.common.constant.CommonConstant;
import com.hkbea.common.exception.ParameterException;
import com.hkbea.common.exception.ServiceException;
import com.hkbea.hth.api.PaymentApi;
import com.hkbea.hth.aspect.crm.CrmEventLog;
import com.hkbea.hth.aspect.fmo.FmoBusinessType;
import com.hkbea.hth.aspect.fmo.FmoEventLog;
import com.hkbea.hth.bg3.api.AccountApi;
import com.hkbea.hth.bg3.model.bg9ch7e0.request.*;
import com.hkbea.hth.bg3.model.bg9ch7e0.response.Bgech7e0Response;
import com.hkbea.hth.bg3.model.bg9ch7e0.response.Eh7e0Output;
import com.hkbea.hth.constants.CrmConstants;
import com.hkbea.hth.constants.PaymentConstants;
import com.hkbea.hth.ebk.constant.PaymentLimitApiConstants;
import com.hkbea.hth.ebk.model.Fpu01RespOArea;
import com.hkbea.hth.entity.HthUserAccessAccount;
import com.hkbea.hth.entity.PaymentInternal;
import com.hkbea.hth.entity.PaymentPayoutRecord;
import com.hkbea.hth.entity.ProductDetails;
import com.hkbea.hth.exception.HTHSupportErrorMapping;
import com.hkbea.hth.ipe.api.LocalPaymentApi;
import com.hkbea.hth.ipe.api.PayemtRoutApi;
import com.hkbea.hth.ipe.model.BatchRoutingCreditTransferRequest;
import com.hkbea.hth.ipe.model.PaymentResultQueryApiResponse;
import com.hkbea.hth.ipe.model.PaymentResultQueryRequest;
import com.hkbea.hth.ipe.model.PaymentResultQueryResponse;
import com.hkbea.hth.mapper.*;
import com.hkbea.hth.model.*;
import com.hkbea.hth.model.Currency;
import com.hkbea.hth.service.payment.*;
import com.hkbea.hth.utils.CacheHelperUtils;
import com.hkbea.hth.utils.FineGrainedEntitlementUtil;
import com.hkbea.hth.utils.PartyResolver;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.UriInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.*;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;


/**
 * Service implementation for the payment APIs (POST /payment/v1/payment and
 * GET enquiry). It owns the contract-wide validation, the IPE smart-routing
 * call and the dispatch to the five payment-type services; each type-specific
 * service owns its own rules, submission and two-phase persistence.
 */
@Service
@Path("/")
public class PaymentServiceImpl implements PaymentApi {
    private static final Pattern EXECUTION_DATE_PATTERN = Pattern.compile("[0-9]{4}-[0-9]{2}-[0-9]{2}");

    private static final String CUSTOMER_TRANSACTION_ID_REGEX = "^.{1,35}$";

    private static final int SEGMENT_LENGTH = 35;
    private static final int ENQUIRY_TXN_REF_MAX_LENGTH = 50;

    public static final Pattern FROM_ACCOUNT_NUMERIC_PATTERN = Pattern.compile("^\\d{14,35}$");
    public static final Pattern TO_ACCOUNT_ALNUM_PATTERN = Pattern.compile("^[0-9a-zA-Z]{1,34}$");
    private static final Set<Currency> SPECIAL_CURRENCIES = EnumSet.of(Currency.JPY);
    private static final Map<BeaAccount.AccountTypeEnum, Set<String>> TYPE_CURRENCIES = Map.of(
            BeaAccount.AccountTypeEnum.SSA, Set.of("HKD"),
            BeaAccount.AccountTypeEnum.SAV, Set.of("HKD"));

    @Autowired
    private LocalPaymentApi localPaymentApi;
    @Autowired
    private PaymentInternalMapper paymentInternalMapper;

    @Autowired
    private PartyResolver partyResolver;

    @Autowired
    private HthNwPaymentPayoutMapper payoutMapper;

    @Autowired
    private FpsPaymentService fpsPaymentService;
    @Autowired
    private ChatsPaymentService chatsPaymentService;
    @Autowired
    private OnUsPaymentService onUsPaymentService;
    @Autowired
    private ProductDetailsMapper productDetailsMapper;
    @Autowired
    private TtPaymentService ttPaymentService;
    @Autowired
    private DrPaymentService drPaymentService;

    @Autowired
    private HthPaymentPayoutMapper hthPaymentPayoutMapper;

    @Autowired
    private PartyAccountMapper partyAccountMapper;

    @Autowired
    private HthUserAccessAccountMapper hthUserAccessAccountMapper;
    @Autowired
    private PayemtRoutApi payemtRoutApi;
    @Autowired
    protected PartyPreferenceMapper partyPreferenceMapper;
    @Autowired
    private FineGrainedEntitlementUtil fineGrainedEntitlementUtil;
    @Context
    private UriInfo uriInfo;
    @Autowired
    private AccountApi accountApi;
    @Autowired
    private TransferLimitService transferLimitService;
    @Autowired
    private CacheHelperUtils cacheHelperUtils;

    private static final Logger log = LoggerFactory.getLogger(PaymentServiceImpl.class);

    private static final ZoneId ENQUIRY_ZONE = ZoneId.systemDefault();

    /**
     * POST /payment/v1/payment entry point.
     * <p>
     * Logic call chain:
     * 1. Aspects - @CrmEventLog(HTH_PAYMENT) and @FmoEventLog(PAYMENT) wrap this
     * method: both run the business call first, then asynchronously report
     * the outcome (result/exception) to CRM (CrmEvent table) and FMO
     * (SC FMO API) without blocking or altering the response. The CRM
     * wildcard type is resolved from the request's paymentType.
     * 2. Contract conversion - PaymentInstructionConvert.toLocalPayment maps
     * the external PaymentInstructionRequest onto the internal LocalPayment
     * model.
     * 3. Core flow - processLocalPayment(LocalPayment):
     * 3.1 resolve partyId/closeId from the access token (best effort);
     * 3.2 validateMandatoryFields - presence of transactionID, debtor and
     * creditor accounts, amount, currency, creditor name and settlement
     * date;
     * 3.3 validateCommon - amount, transactionID format + 2-year duplicate
     * check, endToEndId, account ids, account type/currency resolution,
     * settlement date, paymentDetails splitting;
     * 3.4 PaymentCommonRoutingBuilder.prepareCommonRoutingRequest builds the
     * routing request (debtor internal account + product code);
     * 3.5 PaymentRoutingSubmitter.route asks IPE smart-routing which channel
     * applies (ONUS/CHATS/FPS/TT/DR);
     * 3.6 dispatch to the routed service OnUs/Chats/Fps/Tt/DrPaymentService
     * processPayment, which validates its own rules, persists a pending
     * record, submits the accepted request to IPE and updates the record
     * with the outcome.
     */
    @Override
    @CrmEventLog(eventActvTypeCode = CrmConstants.HTH_PAYMENT)
    @FmoEventLog(businessType = FmoBusinessType.PAYMENT)
    public Payment processLocalPayment(PaymentInstructionRequest request) {
        LocalPayment localPayment = PaymentInstructionConvert.toLocalPayment(request);
        try {
            return processLocalPayment(localPayment);
        } finally {
            if (localPayment != null && localPayment.getPaymentType() != null) {
                request.setPaymentType(localPayment.getPaymentType().value());
            }
        }
    }

    /**
     * Core payment flow:
     * 1. resolve partyId/closeId from the access token;
     * 2. validateCommon - contract-wide checks in fixed order;
     * 3. build the common routing request and ask IPE which payment type
     * (ONUS/CHATS/FPS/TT/DR) applies;
     * 4. dispatch to the type-specific service, which performs its own
     * validation, submission and persistence.
     */
    Payment processLocalPayment(LocalPayment localPayment) {
        String partyId = resolvePartyId();
        String closeId = resolveCloseId();
        if (localPayment != null) {
            localPayment.setPartyId(partyId);
            localPayment.setCloseId(closeId);
        }
        // Only contract-wide checks and common field normalisation live here. Each
        // payment type remains responsible for its own rules and IPE mapping.
        validateMandatoryFields(localPayment);

        String path = uriInfo.getPath();
        fineGrainedEntitlementUtil.isAuthorizedAccount(path, localPayment.getFromAccount().getId());

        validateCommon(localPayment);

        String customerName = partyPreferenceMapper.selectCustomerNameByPartyId(localPayment.getPartyId());
        if (LocalPayment.PaymentTypeEnum.CHATS == localPayment.getPaymentType()) {
            PaymentValidationSupport.validateChatsIpeAddress(localPayment);
            PaymentValidationSupport.validateChatsDebtorName(customerName);
        }
        localPayment.getFromAccount().setName(customerName);
        BatchRoutingCreditTransferRequest routingRequest =
                PaymentCommonRoutingBuilder.prepareCommonRoutingRequest(
                        localPayment, partyAccountMapper);

        LocalPayment.PaymentTypeEnum routedType =
                PaymentRoutingSubmitter.route(payemtRoutApi, routingRequest);
        localPayment.setPaymentType(routedType);
        validateLimit(localPayment);
        cacheHelperUtils.saveMtbInfo(mtbInfo -> mtbInfo.setPaymentType(routedType));

        // The exact request validated by smart routing is handed to the routed
        // service, which enriches it in place and submits the same object, so
        // route/submit/persistence share one batch identifier. Each service
        // persists a pending record before the IPE submission and updates it
        // with the outcome afterwards (two-phase persistence).
        return switch (routedType) {
            case ONUS -> onUsPaymentService.processPayment(localPayment, routingRequest);
            case CHATS -> chatsPaymentService.processPayment(localPayment, routingRequest);
            case FPS -> fpsPaymentService.processPayment(localPayment, routingRequest);
            case TT -> ttPaymentService.processPayment(localPayment, routingRequest);
            case DR -> drPaymentService.processPayment(localPayment, routingRequest);
            default -> throw new ParameterException(HTHSupportErrorMapping.IPE_ROUT_EXCEPTION);
        };
    }

    // ===================== Network Payment Payout Persistence =====================

    /**
     * Best-effort party id resolution from the access token; returns {@code null}
     * (rather than failing) when the token does not carry it.
     */
    private String resolvePartyId() {
        try {
            return partyResolver.resolvePartyId();
        } catch (Exception e) {
            log.warn("Failed to resolve partyId from access token", e);
            return null;
        }
    }

    /**
     * Best-effort close-id resolution from the access token; returns {@code null}
     * (rather than failing) when the token does not carry it.
     */
    private String resolveCloseId() {
        try {
            return partyResolver.resolveCloseId();
        } catch (Exception e) {
            log.warn("Failed to resolve closeId from access token", e);
            return null;
        }
    }

    /**
     * Enquires a payment transaction by its reference number.
     * <p>
     * Looks the transaction up by PAYMENT_ID and the caller's PARTY_ID in the
     * INTERNAL table (On-Us/DR) first, then the PAYOUT table (FPS/CHATS/TT). While
     * the stored status is Pending or Processing, the latest status is refreshed from IPE
     * and persisted.
     *
     * @param txnRef payment transaction reference (PAYMENT_ID)
     * @return the payment transaction with its latest status
     * @throws ParameterException if txnRef is missing/blank (500101) or exceeds 50 characters (500000)
     * @throws ServiceException   if no transaction is found for the caller's company (600131)
     */
    @Override
    @CrmEventLog(eventActvTypeCode = CrmConstants.HTH_LP_INQ)
    public PaymentTransaction enquireTransactionRecord(String txnRef) {
        // 1. Resolve the caller's company and validate input
        String partyId = partyResolver.resolvePartyId();
        if (StringUtils.isBlank(txnRef)) {
            throw new ParameterException(HTHSupportErrorMapping.MISSING_MANDATORY_PARAMETER);
        }
        if (txnRef.length() > ENQUIRY_TXN_REF_MAX_LENGTH) {
            throw new ParameterException("txnRef", HTHSupportErrorMapping.PARAMETER_INVALID_EXCEPTION);
        }

        // 2. Look up On-Us/DR records first; refresh Pending/Processing from IPE
        PaymentInternal internal = paymentInternalMapper.selectOne(
                new LambdaQueryWrapper<PaymentInternal>()
                        .eq(PaymentInternal::getPaymentId, txnRef)
                        .eq(PaymentInternal::getPartyId, partyId));
        if (internal != null) {
            String path = uriInfo.getPath();
            fineGrainedEntitlementUtil.isAuthorizedAccount(path, internal.getDebitAccountId());
            refreshInternalIfPending(internal);
            return toPaymentTransaction(internal);
        }

        // 3. Then look up FPS/CHATS/TT records; refresh Pending/Processing from IPE
        PaymentPayoutRecord payout = payoutMapper.selectOne(
                new LambdaQueryWrapper<PaymentPayoutRecord>()
                        .eq(PaymentPayoutRecord::getPaymentId, txnRef)
                        .eq(PaymentPayoutRecord::getPartyId, partyId));
        if (payout != null) {
            String path = uriInfo.getPath();
            fineGrainedEntitlementUtil.isAuthorizedAccount(path, payout.getDebitAccountId());
            refreshPayoutIfPending(payout);
            return toPaymentTransaction(payout);
        }

        // 4. Not found in either table
        log.warn("Payment transaction record not found, txnRef={}, partyId={}", txnRef, partyId);
        throw new ServiceException(HTHSupportErrorMapping.PAYMENT_NOT_FOUND_EXCEPTION);
    }

    /**
     * Result of querying IPE for a stored pending payment: the normalized new
     * status/reject message plus whether a DB update is required.
     */
    private record RefreshDecision(PaymentResultQueryResponse result, String newStatus,
                                   String newRejectMessage, boolean changed) {
    }

    /**
     * Queries IPE for the latest state of a stored payment and decides whether it
     * has materially changed. Returns {@code null} when the stored payment is not
     * pending (no refresh needed) or when the IPE enquiry is inconclusive (keep
     * stored state).
     */
    private RefreshDecision evaluateRefresh(String storedStatus, String storedRejectMessage,
                                            String paymentId) {
        if (!isPendingStatus(storedStatus)) {
            return null;
        }
        // Query IPE for the latest result; keep the stored state if inconclusive
        PaymentResultQueryResponse result = queryIpePaymentResult(paymentId);
        if (result == null || result.getStatus() == null) {
            return null;
        }
        String newStatus = PaymentResponseSupport.toPaymentStatus(result.getStatus()).value();
        String newRejectMessage = result.getStatus() == PaymentResultQueryResponse.StatusEnum.FAILED
                ? result.getErrorMessage() : null;
        boolean changed = !newStatus.equalsIgnoreCase(storedStatus)
                || (newRejectMessage != null && !newRejectMessage.equals(storedRejectMessage));
        return new RefreshDecision(result, newStatus, newRejectMessage, changed);
    }

    /**
     * Refreshes a pending On-Us (INTERNAL) payment from IPE: updates the in-memory
     * entity for the response and, only when status or reject message changed,
     * performs a sparse DB update.
     */
    private void refreshInternalIfPending(PaymentInternal entity) {
        RefreshDecision refresh = evaluateRefresh(
                entity.getStatus(), entity.getRejectMessage(), entity.getPaymentId());
        if (refresh == null) {
            return;
        }
        PaymentResultQueryResponse result = refresh.result();

        // Update the in-memory entity so the response carries the fresh state
        entity.setStatus(refresh.newStatus());
        if (refresh.newRejectMessage() != null) {
            entity.setRejectMessage(refresh.newRejectMessage());
        }

        // Sparse DB update only when status or reject message changed
        if (!refresh.changed()) {
            return;
        }
        PaymentInternal update = new PaymentInternal();
        update.setId(entity.getId());
        update.setStatus(refresh.newStatus());
        update.setIpeStatus(result.getStatus().value());
        update.setLastUpdatedBy(PaymentConstants.SYSTEM_USER);
        update.setLastUpdatedDate(result.getLastUpdatedAt() == null ? new Date() : result.getLastUpdatedAt());
        entity.setLastUpdatedDate(update.getLastUpdatedDate());
        if (refresh.newRejectMessage() != null) {
            update.setRejectMessage(refresh.newRejectMessage());
        }
        if (result.getStatusReason() != null) {
            update.setIpeStatusReason(result.getStatusReason());
        }
        paymentInternalMapper.updateById(update);
        log.info("On-Us payment status refreshed from IPE, paymentId={}, status={}",
                entity.getId(), refresh.newStatus());
    }

    /**
     * Same refresh as {@link #refreshInternalIfPending} but for shared
     * FPS/CHATS/TT/DR payout records.
     */
    private void refreshPayoutIfPending(PaymentPayoutRecord entity) {
        RefreshDecision refresh = evaluateRefresh(
                entity.getStatus(), entity.getRejectMessage(), entity.getPaymentId());
        if (refresh == null) {
            return;
        }
        PaymentResultQueryResponse result = refresh.result();

        // Update the in-memory entity so the response carries the fresh state
        entity.setStatus(refresh.newStatus());
        if (refresh.newRejectMessage() != null) {
            entity.setRejectMessage(refresh.newRejectMessage());
        }

        // Sparse DB update only when status or reject message changed
        if (!refresh.changed()) {
            return;
        }
        PaymentPayoutRecord update = new PaymentPayoutRecord();
        update.setId(entity.getId());
        update.setStatus(refresh.newStatus());
        update.setIpeStatus(result.getStatus().value());
        update.setLastUpdatedBy(PaymentConstants.SYSTEM_USER);
        update.setLastUpdatedDate(result.getLastUpdatedAt() == null ? LocalDateTime.now() : result.getLastUpdatedAt().toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime());
        entity.setLastUpdatedDate(update.getLastUpdatedDate());
        if (refresh.newRejectMessage() != null) {
            update.setRejectMessage(refresh.newRejectMessage());
        }
        if (result.getStatusReason() != null) {
            update.setIpeStatusReason(result.getStatusReason());
        }
        payoutMapper.updateById(update);
        log.info("{} payment status refreshed from IPE, paymentId={}, status={}",
                entity.getPaymentType(), entity.getCustomerTransactionId(), refresh.newStatus());
    }

    /**
     * Queries the IPE payment-result API by PAYMENT_ID. Returns {@code null} on any
     * failure or inconclusive response so callers can keep the stored status.
     */
    private PaymentResultQueryResponse queryIpePaymentResult(String paymentId) {
        try {
            PaymentResultQueryRequest query = new PaymentResultQueryRequest();
            query.setTransactionId(paymentId);
            PaymentResultQueryApiResponse response = localPaymentApi.queryPaymentResults(query);
            if (response == null || !PaymentConstants.SUCC_CODE_00.equals(response.getCode())
                    || response.getData() == null || response.getData().isEmpty()) {
                log.warn("IPE payment result enquiry inconclusive, paymentId={}, code={}",
                        paymentId, response == null ? null : response.getCode());
                return null;
            }
            return response.getData().getFirst();
        } catch (Exception e) {
            log.error("IPE payment result enquiry failed, paymentId={}", paymentId, e);
            return null;
        }
    }

    private boolean isPendingStatus(String status) {
        return PaymentTransaction.StatusEnum.PENDING.value().equalsIgnoreCase(status)
                || Payment.StatusEnum.PROCESSING.value().equalsIgnoreCase(status);
    }

    /**
     * Maps an On-Us (INTERNAL) entity to the enquiry response model.
     */
    private PaymentTransaction toPaymentTransaction(PaymentInternal entity) {
        PaymentTransaction detail = new PaymentTransaction();
        detail.setReferenceNumber(entity.getPaymentId());
        detail.setStatus(toEnquiryStatus(entity.getStatus()));
        detail.setRejectMessage(entity.getRejectMessage());
        detail.setPaymentType(toPaymentType(entity.getPaymentType()));
        detail.setTransferAmount(entity.getInstdAmount());
        detail.setTransferCurrency(entity.getDebitAccountCur());

        detail.setBeneficiaryAccountNumber(entity.getCreditAccountId());
        detail.setBeneficiaryAccountName(entity.getCreditAccountName());
        detail.setInitiatedDate(toOffsetDateTime(entity.getCreationDate()));
        detail.setRemitterAccountNumber(entity.getDebitAccountId());
        detail.setRemitterAccountCurrency(entity.getDebitAccountCur());

        detail.setExecutionDate(toOffsetDateTime(entity.getPaymentDate()));
        detail.setAsOf(toOffsetDateTime(entity.getLastUpdatedDate()));
        detail.setExternalReferenceId(entity.getEndToEndId());
        detail.setNote(entity.getRemarks());
        applyRemitterDetails(detail, entity.getProductCode());

        return detail;
    }

    /**
     * Maps a shared payout record (FPS/CHATS/TT/DR) to the enquiry response model.
     * Same shape as the INTERNAL mapping, but currency/product code come from
     * different entity fields.
     */
    private PaymentTransaction toPaymentTransaction(PaymentPayoutRecord entity) {
        PaymentTransaction detail = new PaymentTransaction();
        detail.setReferenceNumber(entity.getPaymentId());
        detail.setStatus(toEnquiryStatus(entity.getStatus()));
        detail.setRejectMessage(entity.getRejectMessage());
        detail.setPaymentType(toPaymentType(entity.getPaymentType()));
        detail.setTransferAmount(entity.getInstdAmount());
        detail.setTransferCurrency(entity.getInstdCurrency());

        detail.setRemitterAccountCurrency(entity.getInstdCurrency());
        detail.setRemitterAccountNumber(entity.getDebitAccountId());
        detail.setBeneficiaryAccountNumber(entity.getCreditAccountId());
        detail.setBeneficiaryAccountName(entity.getCreditAccountName());

        detail.setInitiatedDate(toOffsetDateTime(entity.getCreationDate()));
        detail.setExecutionDate(toOffsetDateTime(entity.getPaymentDate()));
        detail.setAsOf(toOffsetDateTime(entity.getLastUpdatedDate()));
        detail.setExternalReferenceId(entity.getEndToEndId());
        detail.setNote(entity.getRemarks());
        applyRemitterDetails(detail, entity.getProdCode());

        return detail;
    }

    /**
     * Fills the remitter product fields shared by both enquiry mappings; product
     * name falls back to an empty string when the product code is unknown.
     */
    private void applyRemitterDetails(PaymentTransaction detail, String productCode) {
        ProductDetails productDetails = getProductDetails(productCode);
        if (productDetails != null) {
            detail.setRemitterProductName(productDetails.getProductDescriptionShort());
        } else {
            detail.setRemitterProductName("");
        }
        detail.setRemitterBank("The Bank of East Asia Limited");
        detail.setRemitterAccountType(AccountProductType.getValueByProductCode(productCode));
    }

    /**
     * query product short name
     *
     * @param productCode product code
     * @return product detail
     */
    private ProductDetails getProductDetails(String productCode) {
        if (productCode == null) return null;

        LambdaQueryWrapper<ProductDetails> queryWrapper = Wrappers.lambdaQuery();
        queryWrapper.eq(ProductDetails::getProductCode, productCode);

        return productDetailsMapper.selectOne(queryWrapper);
    }

    /**
     * Maps the stored STATUS string onto the response enum, case-insensitively.
     *
     * @param status stored status value
     * @return the matching enum constant, or {@code null} if unknown
     */
    private PaymentTransaction.StatusEnum toEnquiryStatus(String status) {
        if (StringUtils.isBlank(status)) {
            return null;
        }
        for (PaymentTransaction.StatusEnum candidate : PaymentTransaction.StatusEnum.values()) {
            if (candidate.value().equalsIgnoreCase(status)) {
                return candidate;
            }
        }
        return null;
    }

    /**
     * Maps the stored PAYMENT_TYPE onto the response enum; unknown or blank
     * values (legacy rows) map to {@code null} instead of failing the enquiry.
     */
    private PaymentTransaction.PaymentTypeEnum toPaymentType(String paymentType) {
        if (StringUtils.isBlank(paymentType)) {
            return null;
        }
        try {
            return PaymentTransaction.PaymentTypeEnum.fromValue(paymentType);
        } catch (IllegalArgumentException e) {
            log.warn("Unknown stored payment type, paymentType={}", paymentType);
            return null;
        }
    }

    private OffsetDateTime toOffsetDateTime(Date value) {
        return value == null ? null : value.toInstant().atZone(ENQUIRY_ZONE).toOffsetDateTime();
    }

    private OffsetDateTime toOffsetDateTime(LocalDateTime value) {
        return value == null ? null : value.atZone(ENQUIRY_ZONE).toOffsetDateTime();
    }

    /**
     * Validates global mandatory parameters before general validation and routing.
     * Checks presence of transactionID, debtorAccount, instructedAmount, creditor.creditorName, creditorAccount, and settlementDate.
     */
    public void validateMandatoryFields(LocalPayment payment) {
        if (payment == null) {
            throw new ParameterException(HTHSupportErrorMapping.MISSING_MANDATORY_PARAMETER);
        }
        if (StringUtils.isBlank(payment.getTransactionID())) {
            throw new ParameterException(HTHSupportErrorMapping.CUSTOMER_TRANSACTION_ID_MISSING_OR_INVALID);
        }
        if (payment.getFromAccount() == null || StringUtils.isBlank(payment.getFromAccount().getId())) {
            throw new ParameterException(HTHSupportErrorMapping.DEBTORACCOUNT_ID_MISSING_OR_INVALID);
        }
        if (payment.getTxnAmount() == null || payment.getTxnAmount().getAmount() == null) {
            throw new ParameterException(HTHSupportErrorMapping.AMOUNT_MISSING_OR_INVALID);
        }
        if (payment.getTxnAmount().getCurrency() == null) {
            throw new ParameterException(HTHSupportErrorMapping.TXN_AMOUNT_CURRENCY_INVALID);
        }
        if (payment.getCreditor() == null || StringUtils.isBlank(payment.getCreditor().getCreditorName())) {
            throw new ParameterException(HTHSupportErrorMapping.CREDITORNAME_MISSING_OR_INVALID);
        }
        if (payment.getToAccount() == null || StringUtils.isBlank(payment.getToAccount().getId())) {
            throw new ParameterException(HTHSupportErrorMapping.CREDITORACCOUNT_ID_MISSING_OR_INVALID);
        }
        if (StringUtils.isBlank(payment.getSettlementDate())) {
            throw new ParameterException(HTHSupportErrorMapping.EXECUTION_DATE_MISSING_OR_INVALID);
        }
    }

    /**
     * Contract-wide validation executed before routing, in a fixed order that
     * callers rely on: amount presence -> transactionID (format + 2-year
     * duplicate check) -> endToEndId -> account ids -> account type/currency
     * resolution -> settlement date -> paymentDetails splitting/validation.
     * Payment-type-specific rules are NOT checked here; each service validates
     * its own fields after dispatch.
     */
    public void validateCommon(LocalPayment payment) {
        validateAccountId(payment);
        validateAmount(payment);
        validateTransactionId(payment);
        PaymentValidationSupport.validateEndToEndIdFormat(payment);
        validateAccountTypeAndCurrency(payment);
        validateExecutionDate(payment.getSettlementDate());
        PaymentValidationSupport.validateAddressLine2(payment);
        validatePaymentDetails(payment);

    }

    private void validateLimit(LocalPayment payment) {
        if (LocalPayment.PaymentTypeEnum.FPS.equals(payment.getPaymentType())) {
            BeaAccount limitAccount = PaymentServiceSupport.limitAccount(
                    payment.getFromAccount(), payment.getPartyId());

            Fpu01RespOArea fpu01RespOArea = transferLimitService.selectFasterPaymentLimit(
                    fpsPaymentService.buildFpsUpdateLimit(payment), limitAccount);
            if (!PaymentLimitApiConstants.SUCCESS_CODE.equals(fpu01RespOArea.getEbzwk00oHeader().getEbzwk00oHdrRespCode())) {
                throw new ParameterException(HTHSupportErrorMapping.LIMIT_DEDUCTION_FAILED);
            }

            transferLimitService.releaseFasterPaymentLimit(
                    fpsPaymentService.buildFpsReleaseLimit(fpu01RespOArea.getEbzwk00oFpu01(), payment, "02"),
                    limitAccount);
        }
    }

    private Eh7e0Output queryAccountInfo(String accountId18) {
        Bgech7e0Request req = buildBgech7e0Request(accountId18);
        Bgech7e0Response resp = accountApi.queryAccountInfo(req);

        if (resp == null || resp.getDfhcommarea() == null) return null;

        var out = resp.getDfhcommarea().getQaechcc().getBgech7e0().getEh7e0Output();

        if (out == null || !"00".equals(out.getEh7e0CodReturnO())) return null;

        return out;
    }

    private Bgech7e0Request buildBgech7e0Request(String acct18) {
        Bgech7e0Request req = new Bgech7e0Request();
        DfhcommareaReq7e0 dfh = new DfhcommareaReq7e0();

        // Use a fixed value for qaechcc.
        QaechccReq7e0 q = new QaechccReq7e0();
        q.setQaechccTypChannel("IN");
        q.setQaechccApplId("BG");
        q.setQaechccCollecName("BG9CH7E0");
        q.setQaechccCollecFunc("");
        q.setQaechccDateOp("");
        q.setQaechccCodEntity("0015");
        q.setQaechccCodBrn("");
        q.setQaechccCodTerm("");
        q.setQaechccCodUser("");
        q.setQaechccErrData("");
        q.setQaechccFrArea("");

        // Nested Object：Bgech7e0 → Eh7e0Record → Eh7e0Input
        Bgech7e0 b = new Bgech7e0();
        Eh7e0Record r = new Eh7e0Record();
        Eh7e0Input i = new Eh7e0Input();

        /**
         * Convert the 18-digit account number into the corresponding input parameters for BG9CH7E0
         * e.g.：001505216815038341 → entI=15, cenRegI=521, accI=6815038341
         */
        Eh7e0CacI c = new Eh7e0CacI();
        c.setEh7e0EntI(Integer.parseInt(acct18.substring(0, 4)));
        c.setEh7e0CenRegI(Integer.parseInt(acct18.substring(4, 8)));
        c.setEh7e0AccI(Long.parseLong(acct18.substring(8)));
        i.setEh7e0CacI(c);
        i.setEh7e0FccI("");

        r.setEh7e0Input(i);
        b.setEh7e0Record(r);
        q.setBgech7e0(b);
        dfh.setQaechcc(q);
        req.setDfhcommarea(dfh);
        return req;
    }

    /**
     * Validates debtor account id (14-35 digits) and creditor account id
     * (1-34 alphanumeric).
     */
    private void validateAccountId(LocalPayment payment) {
        String fromAccountId = payment.getFromAccount().getId();
        if (StringUtils.isBlank(fromAccountId)) {
            throw new ParameterException(HTHSupportErrorMapping.DEBTORACCOUNT_ID_MISSING_OR_INVALID);
        }
        if (!FROM_ACCOUNT_NUMERIC_PATTERN.matcher(fromAccountId).matches()) {
            throw new ParameterException(HTHSupportErrorMapping.DEBIT_ACCOUNT_NUMBER_FORMAT_INVALID);
        }

        List<HthUserAccessAccount> hthUserAccessAccounts = hthUserAccessAccountMapper.selectFromAccountInfo(payment.getPartyId(), payment.getCloseId(), fromAccountId);
        if (hthUserAccessAccounts.isEmpty()) {
            throw new ParameterException(HTHSupportErrorMapping.DEBTORACCOUNT_ID_MISSING_OR_INVALID);
        }
        Eh7e0Output acctInfo = queryAccountInfo(hthUserAccessAccounts.getFirst().getAccountNumber());
        if (acctInfo == null) {
            throw new ParameterException(HTHSupportErrorMapping.DEBTORACCOUNT_ID_MISSING_OR_INVALID);
        }
        if (!"N".equalsIgnoreCase(acctInfo.getEh7e0FlgAccHoldO().trim())) {
            throw new ParameterException(HTHSupportErrorMapping.FPS_DEBIT_ACCOUNT_STATUS_INVALID);
        }

        PaymentToAccount toAccount = payment.getToAccount();
        // null-guard added: matcher(null) previously threw NPE and surfaced as 900000 General exception
        if (toAccount == null || StringUtils.isBlank(toAccount.getId())
                || !TO_ACCOUNT_ALNUM_PATTERN.matcher(toAccount.getId()).matches()) {
            throw new ParameterException(HTHSupportErrorMapping.CREDITORACCOUNT_ID_MISSING_OR_INVALID);
        }
    }

    /**
     * Minimal instructedAmount presence/non-negative check; full amount rules
     */
    private void validateAmount(LocalPayment payment) {
        BigDecimal amount = payment.getTxnAmount().getAmount();
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0
                || amount.compareTo(CommonConstant.MAX_AMOUNT) > 0) {
            throw new ParameterException(HTHSupportErrorMapping.AMOUNT_MISSING_OR_INVALID);
        }

        // Special currencies (e.g. JPY) must not have a decimal part
        if (SPECIAL_CURRENCIES.contains(payment.getTxnAmount().getCurrency())
                && amount.remainder(BigDecimal.ONE).compareTo(BigDecimal.ZERO) != 0) {
            throw new ParameterException(HTHSupportErrorMapping.SPECIAL_ZERO_DECIMAL_CURRENCIES);
        }

        // fd=2: at most 2 decimal places (9(15).9(2))
        if (amount.stripTrailingZeros().scale() > 2) {
            throw new ParameterException(HTHSupportErrorMapping.MAX_AMOUNT_DECIMAL_PLACES);
        }

    }

    /**
     * Converts {@code remittanceInformationUnstructured} into validated 35-char
     * {@code paymentDetails} segments used by downstream mappings.
     */
    private void validatePaymentDetails(LocalPayment payment) {
        String paymentDetail = payment.getRemittanceInformationUnstructured();
        if (StringUtils.isNotBlank(paymentDetail)) {
            payment.setPaymentDetails(splitPaymentDetails(paymentDetail));
        }
    }

    /**
     * Validates the free-text remittance information and splits it into 35-char
     * segments (payment engine field length). Empty input yields an empty list.
     */
    public static List<String> splitPaymentDetails(String paymentDetails) {
        if (paymentDetails == null || paymentDetails.isBlank()) {
            return new ArrayList<>();
        }
        PaymentValidationSupport.validatePaymentDetails(paymentDetails);

        List<String> result = new ArrayList<>();
        for (int start = 0; start < paymentDetails.length(); start += SEGMENT_LENGTH) {
            int end = Math.min(start + SEGMENT_LENGTH, paymentDetails.length());
            result.add(paymentDetails.substring(start, end));
        }
        return result;
    }

    /**
     * Validates {@code transactionID} (present, max 35 chars) and rejects
     * duplicates within the last 2 years across both payment tables.
     */
    public void validateTransactionId(LocalPayment localPayment) {
        String transactionId = localPayment.getTransactionID();
        if (StringUtils.isBlank(transactionId)) {
            log.warn("transactionID is blank :{}", transactionId);
            throw new ParameterException(HTHSupportErrorMapping.CUSTOMER_TRANSACTION_ID_MISSING_OR_INVALID);
        }

        if (!transactionId.matches(CUSTOMER_TRANSACTION_ID_REGEX)) {
            throw new ParameterException(HTHSupportErrorMapping.CUSTOMER_TRANSACTION_ID_MISSING_OR_INVALID);
        }

        LocalDate today = LocalDate.now();
        LocalDate twoYearsAgo = today.minusYears(2);
        checkDuplicateValue(localPayment, transactionId, twoYearsAgo, today);

        localPayment.setTransactionID(transactionId);
    }

    /**
     * Counts matching transactionID records in the On-Us and payout tables within
     * [twoYearsAgo, today]; throws {@code CUSTOMER_TRANSACTION_ID_DUPLICATE} when
     * any exists.
     */
    public void checkDuplicateValue(LocalPayment localPayment, String transactionId, LocalDate twoYearsAgo, LocalDate today) {
        long count = 0;

        LocalDateTime startDateTime = twoYearsAgo.atStartOfDay();
        LocalDateTime endDateTime = today.atTime(LocalTime.MAX);
        count = paymentInternalMapper.selectCount(
                new LambdaQueryWrapper<PaymentInternal>()
                        .eq(PaymentInternal::getPartyId, localPayment.getPartyId())
                        .eq(PaymentInternal::getCustomerTransactionId, transactionId)
                        .between(PaymentInternal::getCreationDate, startDateTime, endDateTime));

        count += hthPaymentPayoutMapper.selectCount(
                new LambdaQueryWrapper<PaymentPayoutRecord>()
                        .eq(PaymentPayoutRecord::getPartyId, localPayment.getPartyId())
                        .eq(PaymentPayoutRecord::getCustomerTransactionId, transactionId)
                        .between(PaymentPayoutRecord::getCreationDate, startDateTime, endDateTime));

        if (count > 0) {
            throw new ParameterException(HTHSupportErrorMapping.CUSTOMER_TRANSACTION_ID_DUPLICATE);
        }
    }

    /**
     * Validates {@code settlementDate}: yyyy-mm-dd format and must equal the
     * current date (pay-later submissions are not accepted by this API).
     */
    public void validateExecutionDate(String executionDate) {
        if (StringUtils.isBlank(executionDate) || !EXECUTION_DATE_PATTERN.matcher(executionDate).matches()) {
            throw new ParameterException(HTHSupportErrorMapping.EXECUTION_DATE_MISSING_OR_INVALID);
        }
        LocalDate date;
        try {
            date = LocalDate.parse(executionDate);
        } catch (DateTimeParseException e) {
            throw new ParameterException(HTHSupportErrorMapping.EXECUTION_DATE_MISSING_OR_INVALID);
        }

        if (!date.equals(LocalDate.now())) {
            throw new ParameterException(HTHSupportErrorMapping.EXECUTION_DATE_NOT_CURRENT_DATE);
        }
    }

    /**
     * Normalises account fields ({@code id} -> {@code accountNumber}, {@code type}
     * -> enum) and resolves the debtor account's product type/currency via
     * {@link #setFromAccount}.
     */
    public void validateAccountTypeAndCurrency(LocalPayment payment) {
        String partyId = payment.getPartyId();
        String closeId = payment.getCloseId();
        BeaAccount fromAccount = payment.getFromAccount();
        if (StringUtils.isNotBlank(fromAccount.getType()) && StringUtils.isNotBlank(fromAccount.getCurrency())) {
            throw new ParameterException(HTHSupportErrorMapping.DEBTOR_TYPE_CURRENCY_MUTUALLY_EXCLUSIVE);
        }
        PaymentToAccount toAccount = payment.getToAccount();
        try {
            fromAccount.setAccountNumber(fromAccount.getId());
            fromAccount.setAccountType(StringUtils.isNotBlank(fromAccount.getType()) ? BeaAccount.AccountTypeEnum.fromValue(fromAccount.getType()) : null);
            toAccount.setAccountNumber(toAccount.getId());
            toAccount.setAccountType(StringUtils.isNotBlank(toAccount.getType()) ? PaymentToAccount.AccountTypeEnum.fromValue(toAccount.getType()) : null);
        } catch (IllegalArgumentException e) {
            throw new ParameterException(HTHSupportErrorMapping.INVALID_ACCOUNT_TYPE_EXCEPTION);
        }
        setFromAccount(payment, fromAccount, partyId, closeId);
        if (fromAccount.getAccountType() != null && StringUtils.isNotBlank(fromAccount.getCurrency())) {
            Set<String> allowed = TYPE_CURRENCIES.get(fromAccount.getAccountType());
            if (allowed != null && !allowed.contains(fromAccount.getCurrency().trim().toUpperCase())) {
                throw new ParameterException(HTHSupportErrorMapping.DEBTOR_CURRENCY_TYPE_MISMATCH);   // 新增 500209
            }
        }
    }

    /**
     * Enriches the debtor account from {@code hthUserAccessAccount}: rejects
     * unknown (ACCOUNT_NOT_FOUND), collection (COL) and time-deposit (TD)
     * accounts, then derives the default account type and currency (SSA/AIO
     * accounts always use SSA/HKD).
     */
    private void setFromAccount(LocalPayment payment, BeaAccount fromAccount, String partyId, String closeId) {
        List<HthUserAccessAccount> hthUserAccessAccounts = hthUserAccessAccountMapper.selectFromAccountInfo(partyId, closeId, fromAccount.getAccountNumber());

        String productCode = hthUserAccessAccounts.stream()
                .filter(Objects::nonNull)
                .map(HthUserAccessAccount::getProductCode)
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .distinct()
                .collect(Collectors.joining(","));

        if (Objects.nonNull(fromAccount) && Objects.nonNull(fromAccount.getAccountType())) {
            boolean allProductCodesStartWith68 = StringUtils.isNotBlank(productCode)
                    && Arrays.stream(productCode.split(","))
                    .map(String::trim)
                    .filter(code -> !code.isEmpty())
                    .allMatch(code -> code.startsWith("68"));

            if (!allProductCodesStartWith68) {
                throw new ParameterException(HTHSupportErrorMapping.ERR_ACCOUNT_TYPE_MISMATCH);
            }

            if (AccountProductType.COL.name().equals(fromAccount.getAccountType().name())) {
                throw new ParameterException(HTHSupportErrorMapping.COL_REJECT_ACCOUNT_TYPE);
            }
            if (AccountProductType.TD.name().equals(fromAccount.getAccountType().name())) {
                throw new ParameterException(HTHSupportErrorMapping.TD_REJECT_ACCOUNT_TYPE);
            }
        } else {
            String accountTypes = hthUserAccessAccounts.stream()
                    .filter(Objects::nonNull)
                    .map(HthUserAccessAccount::getAccountType)
                    .filter(Objects::nonNull)
                    .map(String::trim)
                    .filter(value -> !value.isEmpty())
                    .distinct()
                    .collect(Collectors.joining(","));

            if (StringUtils.isBlank(accountTypes)) {
                throw new ParameterException(HTHSupportErrorMapping.ACCOUNT_NOT_FOUND_EXCEPTION);
            }

            if (accountTypes.contains(AccountProductType.COL.name())) {
                throw new ParameterException(HTHSupportErrorMapping.COL_REJECT_ACCOUNT_TYPE);
            }

            if (accountTypes.contains(AccountProductType.TD.name())) {
                throw new ParameterException(HTHSupportErrorMapping.TD_REJECT_ACCOUNT_TYPE);
            }

            if (AccountProductType.containsProductType(productCode, AccountProductType.COL)) {
                throw new ParameterException(HTHSupportErrorMapping.COL_REJECT_ACCOUNT_TYPE);
            }

            if (AccountProductType.containsProductType(productCode, AccountProductType.TD)) {
                throw new ParameterException(HTHSupportErrorMapping.TD_REJECT_ACCOUNT_TYPE);
            }
        }

        if (Objects.isNull(fromAccount.getAccountType())) {
            if (AccountProductType.containsProductType(productCode, AccountProductType.SSA) ||
                    AccountProductType.containsProductType(productCode, AccountProductType.AIO)) {
                fromAccount.setAccountType(BeaAccount.AccountTypeEnum.SAV);
            } else {
                String accountType = AccountProductType.getValueByProductCode(productCode.split(",")[0]);
                if (accountType == null) {
                    // Unmapped product code: fail with a domain error instead of NPE
                    throw new ParameterException(HTHSupportErrorMapping.DEBIT_ACCOUNT_TYPE_MISSING);
                }
                fromAccount.setAccountType(BeaAccount.AccountTypeEnum.valueOf(accountType));
            }
        }

        if (Objects.isNull(fromAccount.getCurrency())) {
            if (AccountProductType.containsProductType(productCode, AccountProductType.SSA) ||
                    AccountProductType.containsProductType(productCode, AccountProductType.AIO)) {
                fromAccount.setCurrency(Currency.HKD.toString());
            } else {
                fromAccount.setCurrency(hthUserAccessAccounts.stream()
                        .filter(a -> productCode.split(",")[0].equals(a.getProductCode()))
                        .map(HthUserAccessAccount::getCurrency)
                        .filter(Objects::nonNull)
                        .findFirst()
                        .orElse(null));
            }
        }
    }

}
