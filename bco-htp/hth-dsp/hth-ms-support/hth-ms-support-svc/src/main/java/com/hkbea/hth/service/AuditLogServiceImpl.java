package com.hkbea.hth.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hkbea.common.exception.ParameterException;
import com.hkbea.common.exception.SystemException;
import com.hkbea.hth.api.AuditLogApi;
import com.hkbea.hth.aspect.crm.CrmEventLog;
import com.hkbea.hth.aspect.fmo.FmoBusinessType;
import com.hkbea.hth.aspect.fmo.FmoEventLog;
import com.hkbea.hth.constants.CrmConstants;
import com.hkbea.hth.constants.PaymentConstants;
import com.hkbea.hth.constants.TimeConstants;
import com.hkbea.hth.entity.AuditLogRecord;
import com.hkbea.hth.entity.Management;
import com.hkbea.hth.entity.PaymentInternal;
import com.hkbea.hth.entity.PaymentPayoutRecord;
import com.hkbea.hth.exception.HTHSupportErrorMapping;
import com.hkbea.hth.mapper.AuditLogMapper;
import com.hkbea.hth.mapper.HthManagementMapper;
import com.hkbea.hth.mapper.HthPaymentPayoutMapper;
import com.hkbea.hth.mapper.PaymentInternalMapper;
import com.hkbea.hth.model.AuditLog;
import com.hkbea.hth.model.AuditLogRequest;
import com.hkbea.hth.model.AuditLogResponse;
import com.hkbea.hth.utils.PartyResolver;
import jakarta.annotation.Resource;
import jakarta.ws.rs.Path;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Path("/")
public class AuditLogServiceImpl implements AuditLogApi {

    private static final Logger log = LoggerFactory.getLogger(AuditLogServiceImpl.class);

    private static final int MAX_PAGE_SIZE = 50;

    @Value("${hth.auditlog.max-query-period-days:180}")
    private int maxQueryPeriodDays;

    // Setter for testing purposes
    public void setMaxQueryPeriodDays(int maxQueryPeriodDays) {
        this.maxQueryPeriodDays = maxQueryPeriodDays;
    }

    @Resource
    private AuditLogMapper auditLogMapper;
    @Autowired
    private PartyResolver partyResolver;
    @Resource
    private PaymentInternalMapper paymentInternalMapper;
    @Resource
    private HthPaymentPayoutMapper hthPaymentPayoutMapper;
    @Resource
    private HthManagementMapper hthManagementMapper;

    @Override
    @FmoEventLog(businessType = FmoBusinessType.AUDIT_ENQUIRY)
    @CrmEventLog(eventActvTypeCode = CrmConstants.HTH_AUDIT)
    public AuditLogResponse queryAuditLogs(AuditLogRequest auditLogRequest) {
        if (auditLogRequest == null) {
            throw new ParameterException("request", HTHSupportErrorMapping.PARAMETER_MISSED_EXCEPTION);
        }
        log.info("Received audit log query request: pageNo={}, pageSize={}, messageId={}, closeId={}, clientId={}, fromDateTime={}, toDateTime={}, lastNDays={}",
                auditLogRequest.getPageNumber(), auditLogRequest.getPageSize(),
                auditLogRequest.getMessageId(), auditLogRequest.getCloseId(), auditLogRequest.getClientId(),
                auditLogRequest.getFromDateTime(), auditLogRequest.getToDateTime(), auditLogRequest.getLastNDays());
        String closeId = partyResolver.resolveCloseId();
        String clientId = partyResolver.resolveClientId();
        // BCOH2H-876: reject an explicit foreign scope before binding both query keys to the token.
        if ((!StringUtils.isBlank(auditLogRequest.getCloseId())
                && !closeId.equals(auditLogRequest.getCloseId().trim()))
                || (!StringUtils.isBlank(auditLogRequest.getClientId())
                && !clientId.equals(auditLogRequest.getClientId().trim()))) {
            throw new ParameterException(HTHSupportErrorMapping.AUDIT_LOG_ACCESS_DENIED_EXCEPTION);
        }
        // Also enforce company enablement when this service is invoked outside the HTH filter.
        partyResolver.resolvePartyId();
        auditLogRequest.setCloseId(closeId);
        auditLogRequest.setClientId(clientId);
        return processAuditLogQuery(auditLogRequest);
    }

    private AuditLogResponse processAuditLogQuery(AuditLogRequest auditLogRequest) {
        validateRequest(auditLogRequest);
        LambdaQueryWrapper<AuditLogRecord> queryWrapper = Wrappers.lambdaQuery();

        if (auditLogRequest.getMessageId() != null && !auditLogRequest.getMessageId().trim().isEmpty()) {
            queryWrapper.eq(AuditLogRecord::getMessageId, auditLogRequest.getMessageId());
        }
        if (auditLogRequest.getCloseId() != null && !auditLogRequest.getCloseId().trim().isEmpty()) {
            queryWrapper.eq(AuditLogRecord::getCloseId, auditLogRequest.getCloseId());
        }
        if (auditLogRequest.getClientId() != null && !auditLogRequest.getClientId().trim().isEmpty()) {
            queryWrapper.eq(AuditLogRecord::getClientId, auditLogRequest.getClientId());
        }
        OffsetDateTime finalFromDateTime = (OffsetDateTime) auditLogRequest.getAdditionalProperties().get("finalFromDateTime");
        OffsetDateTime finalToDateTime = (OffsetDateTime) auditLogRequest.getAdditionalProperties().get("finalToDateTime");

        if (finalFromDateTime != null) {
            queryWrapper.ge(AuditLogRecord::getCreateTime, Date.from(finalFromDateTime.toInstant()));
        }
        if (finalToDateTime != null) {
            queryWrapper.le(AuditLogRecord::getCreateTime, Date.from(finalToDateTime.toInstant()));
        }

        queryWrapper.orderByDesc(AuditLogRecord::getCreateTime);

        Page<AuditLogRecord> auditLogRecordPage = new Page<>(auditLogRequest.getPageNumber(), auditLogRequest.getPageSize());

        Page<AuditLogRecord> resultPage;
        try {
            resultPage = auditLogMapper.selectPage(auditLogRecordPage, queryWrapper);
        } catch (Exception e) {
            log.error("Failed to query audit logs", e);
            throw new SystemException(HTHSupportErrorMapping.AUDIT_LOG_SYSTEM_ERROR);
        }

        AuditLogResponse response = new AuditLogResponse(auditLogRequest);
        response.setTotalRecords((int) resultPage.getTotal());
        if (resultPage.getRecords() != null && !resultPage.getRecords().isEmpty()) {
            List<AuditLog> auditLogs = resultPage.getRecords().stream().map(this::mapToAuditLog).collect(Collectors.toList());
            response.setAuditLog(auditLogs);
        }
        // Set fromDateTime and toDateTime from additional properties
        if (finalFromDateTime != null) {
            response.setFromDateTime(finalFromDateTime.format(TimeConstants.ISO_8601_FORMATTER));
        }
        if (finalToDateTime != null) {
            response.setToDateTime(finalToDateTime.format(TimeConstants.ISO_8601_FORMATTER));
        }
        return response;
    }

    @Override
    public AuditLogResponse queryAuditLogsByPartyId(String partyId, AuditLogRequest auditLogRequest) {
        if (StringUtils.isBlank(partyId)) {
            throw new ParameterException(HTHSupportErrorMapping.INVALID_PARAMETER_VALUE, "partyId", partyId);
        }
        Management management = hthManagementMapper.selectOne(Wrappers.<Management>lambdaQuery()
                .eq(Management::getPartyId, partyId)
                .eq(Management::getHthStatus, PaymentConstants.HTH_STATUS_ENABLED)); // Assuming "ENABLED" is the status for active records
        if (management == null) {
            throw new ParameterException(HTHSupportErrorMapping.INVALID_PARAMETER_VALUE, "partyId", partyId);
        }
        auditLogRequest.setClientId(management.getUamClientId());
        return processAuditLogQuery(auditLogRequest);
    }

    private void validateRequest(AuditLogRequest request) {
        if (request.getPageNumber() < 1) {
            throw new ParameterException(HTHSupportErrorMapping.INVALID_VALUE_RANGE, "pageNumber", "1 and above");
        }

        if (request.getPageSize() < 1 || request.getPageSize() > MAX_PAGE_SIZE) {
            throw new ParameterException(HTHSupportErrorMapping.INVALID_VALUE_RANGE, "pageSize", "1-"+MAX_PAGE_SIZE);
        }

        boolean hasDateTimeRange = (request.getFromDateTime() != null && !request.getFromDateTime().trim().isEmpty()) ||
                (request.getToDateTime() != null && !request.getToDateTime().trim().isEmpty());
        boolean hasLastNDays = request.getLastNDays() != null;

        if (hasDateTimeRange && hasLastNDays) {
            throw new ParameterException(HTHSupportErrorMapping.DATE_RANGE_MUTUALLY_EXCLUSIVE,
                    "fromDateTime", "toDateTime", "lastNDays");
        }

        OffsetDateTime finalFromDateTime = null;
        OffsetDateTime finalToDateTime = null;

        if (hasLastNDays) {
            if (request.getLastNDays() <= 0 || request.getLastNDays() > maxQueryPeriodDays) {
                throw new ParameterException(HTHSupportErrorMapping.INVALID_VALUE_RANGE, "lastNDays", "1-"+maxQueryPeriodDays);
            }
            finalToDateTime = OffsetDateTime.now(TimeConstants.HK_ZONE_OFFSET).withHour(23).withMinute(59).withSecond(59).withNano(0);
            finalFromDateTime = finalToDateTime.minusDays(request.getLastNDays() - 1).withHour(0).withMinute(0).withSecond(0).withNano(0);
        } else if (hasDateTimeRange) {
            OffsetDateTime fromDateTime = parseDateTime(request.getFromDateTime(), "fromDateTime");
            OffsetDateTime toDateTime = parseDateTime(request.getToDateTime(), "toDateTime");

            if (fromDateTime == null && toDateTime == null) {
                // Both not provided, default to today
                finalFromDateTime = OffsetDateTime.now(TimeConstants.HK_ZONE_OFFSET).withHour(0).withMinute(0).withSecond(0).withNano(0);
                finalToDateTime = OffsetDateTime.now(TimeConstants.HK_ZONE_OFFSET).withHour(23).withMinute(59).withSecond(59).withNano(0);
            } else if (fromDateTime == null) {
                // fromDateTime not provided, default to start of toDateTime's day
                finalToDateTime = toDateTime;
                finalFromDateTime = toDateTime.withHour(0).withMinute(0).withSecond(0).withNano(0);
            } else if (toDateTime == null) {
                // toDateTime not provided, default to end of fromDateTime's day
                finalFromDateTime = fromDateTime;
                finalToDateTime = fromDateTime.withHour(23).withMinute(59).withSecond(59).withNano(0);
            } else {
                finalFromDateTime = fromDateTime;
                finalToDateTime = toDateTime;
            }

            if (finalFromDateTime.isAfter(finalToDateTime)) {
                throw new ParameterException(HTHSupportErrorMapping.INVALID_DATE_RANGE, "<fromDateTime>", "<toDateTime>");
            }

            OffsetDateTime earliestAllowedFromDateTime = OffsetDateTime.now(TimeConstants.HK_ZONE_OFFSET).minusDays(maxQueryPeriodDays).withHour(0).withMinute(0).withSecond(0).withNano(0);
            if (finalFromDateTime.isBefore(earliestAllowedFromDateTime)) {
                throw new ParameterException(HTHSupportErrorMapping.INVALID_VALUE_RANGE, "fromDateTime", "last "+maxQueryPeriodDays+" days");
            }
        } else {
            // No date/time parameters, default to today
            finalFromDateTime = OffsetDateTime.now(TimeConstants.HK_ZONE_OFFSET).withHour(0).withMinute(0).withSecond(0).withNano(0);
            finalToDateTime = OffsetDateTime.now(TimeConstants.HK_ZONE_OFFSET).withHour(23).withMinute(59).withSecond(59).withNano(0);
        }

        request.getAdditionalProperties().put("finalFromDateTime", finalFromDateTime);
        request.getAdditionalProperties().put("finalToDateTime", finalToDateTime);
    }

    private OffsetDateTime parseDateTime(String dateTimeStr, String fieldName) {
        if (dateTimeStr == null || dateTimeStr.trim().isEmpty()) {
            return null;
        }
        try {
            return OffsetDateTime.parse(dateTimeStr, TimeConstants.ISO_8601_FORMATTER);
        } catch (DateTimeParseException e) {
            throw new ParameterException(HTHSupportErrorMapping.INVALID_PARAMETER_FORMAT, fieldName, "YYYY-MM-DDTHH:mm:ss±08:00");
        }
    }

    private AuditLog mapToAuditLog(AuditLogRecord record) {
        AuditLog result = new AuditLog();
        result.setMessageId(record.getMessageId());
        result.setClientId(record.getClientId());
        result.setCloseId(record.getCloseId());
        result.setClientIP(record.getClientIp());
        result.setChannel(record.getChannel());
        result.setHttpMethod(record.getHttpMethod());
        result.setRequestUri(record.getRequestUri());
        result.setQueryString(record.getQueryString());
        result.setStatusCode(record.getStatusCode());
        result.setDurationMs(record.getDurationMs());

        if (record.getCreateTime() != null) {
            result.setCreateTime(record.getCreateTime().toInstant().atOffset(TimeConstants.HK_ZONE_OFFSET));
        }

        // Check if it's a payment interface
        String requestUri = record.getRequestUri();
        if (requestUri != null && (requestUri.contains("payment"))) {
            String messageId = record.getMessageId();
            if (messageId != null && !messageId.trim().isEmpty()) {
                // Query HTH_PAYMENT_INTERNAL
                LambdaQueryWrapper<PaymentInternal> internalWrapper = Wrappers.lambdaQuery();
                internalWrapper.eq(PaymentInternal::getMessageId, messageId);
                PaymentInternal paymentInternal = paymentInternalMapper.selectOne(internalWrapper);

                if (paymentInternal != null && paymentInternal.getIpeRefNo() != null) {
                    result.setTransactionReference(paymentInternal.getIpeRefNo());
                } else {
                    // If not found in HTH_PAYMENT_INTERNAL, query HTH_PAYMENT_PAYOUT
                    LambdaQueryWrapper<PaymentPayoutRecord> payoutWrapper = Wrappers.lambdaQuery();
                    payoutWrapper.eq(PaymentPayoutRecord::getMessageId, messageId);
                    PaymentPayoutRecord paymentPayout = hthPaymentPayoutMapper.selectOne(payoutWrapper);

                    if (paymentPayout != null && paymentPayout.getIpeRefNo() != null) {
                        result.setTransactionReference(paymentPayout.getIpeRefNo());
                    }
                }
            }
        }

        return result;
    }
}
