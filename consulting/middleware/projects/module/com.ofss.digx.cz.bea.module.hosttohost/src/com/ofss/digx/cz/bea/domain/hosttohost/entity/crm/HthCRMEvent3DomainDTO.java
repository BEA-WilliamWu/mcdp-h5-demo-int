package com.ofss.digx.cz.bea.domain.hosttohost.entity.crm;

import java.sql.Timestamp;

import com.ofss.fc.framework.domain.AbstractDomainObject;
import com.ofss.fc.framework.domain.IPersistenceObject;

/** Persistent HTH CRM record; contains only the approved event projection. */
public class HthCRMEvent3DomainDTO extends AbstractDomainObject implements IPersistenceObject {
    private static final long serialVersionUID = 1L;

    private HthCRMEvent3DomainKey key;
    private String eventDte;
    private String eventTime;
    private String sourceSystem;
    private String channelType;
    private String targetUserChannel;
    private String activityKey;
    private String eventActvTypeCode;
    private String eventStatusCode;
    private String phase;
    private String finInd;
    private String userId;
    private String targetUserId;
    private String acctNbr;
    private String relationshipType;
    private String serviceId;
    private String taskCode;
    private String sourceTrxRefNbr;
    private String sourceActionId;
    private String requestId;
    private String ipAddress;
    private String errorCode;
    private String dedupKey;
    private Timestamp createdAt;

    public HthCRMEvent3DomainKey getKey() {
        return key;
    }

    public void setKey(HthCRMEvent3DomainKey key) {
        this.key = key;
    }

    public String getEventDte() {
        return eventDte;
    }

    public void setEventDte(String eventDte) {
        this.eventDte = eventDte;
    }

    public String getEventTime() {
        return eventTime;
    }

    public void setEventTime(String eventTime) {
        this.eventTime = eventTime;
    }

    public String getSourceSystem() {
        return sourceSystem;
    }

    public void setSourceSystem(String sourceSystem) {
        this.sourceSystem = sourceSystem;
    }

    public String getChannelType() {
        return channelType;
    }

    public void setChannelType(String channelType) {
        this.channelType = channelType;
    }

    public String getTargetUserChannel() {
        return targetUserChannel;
    }

    public void setTargetUserChannel(String targetUserChannel) {
        this.targetUserChannel = targetUserChannel;
    }

    public String getActivityKey() {
        return activityKey;
    }

    public void setActivityKey(String activityKey) {
        this.activityKey = activityKey;
    }

    public String getEventActvTypeCode() {
        return eventActvTypeCode;
    }

    public void setEventActvTypeCode(String eventActvTypeCode) {
        this.eventActvTypeCode = eventActvTypeCode;
    }

    public String getEventStatusCode() {
        return eventStatusCode;
    }

    public void setEventStatusCode(String eventStatusCode) {
        this.eventStatusCode = eventStatusCode;
    }

    public String getPhase() {
        return phase;
    }

    public void setPhase(String phase) {
        this.phase = phase;
    }

    public String getFinInd() {
        return finInd;
    }

    public void setFinInd(String finInd) {
        this.finInd = finInd;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getTargetUserId() {
        return targetUserId;
    }

    public void setTargetUserId(String targetUserId) {
        this.targetUserId = targetUserId;
    }

    public String getAcctNbr() {
        return acctNbr;
    }

    public void setAcctNbr(String acctNbr) {
        this.acctNbr = acctNbr;
    }

    public String getRelationshipType() {
        return relationshipType;
    }

    public void setRelationshipType(String relationshipType) {
        this.relationshipType = relationshipType;
    }

    public String getServiceId() {
        return serviceId;
    }

    public void setServiceId(String serviceId) {
        this.serviceId = serviceId;
    }

    public String getTaskCode() {
        return taskCode;
    }

    public void setTaskCode(String taskCode) {
        this.taskCode = taskCode;
    }

    public String getSourceTrxRefNbr() {
        return sourceTrxRefNbr;
    }

    public void setSourceTrxRefNbr(String sourceTrxRefNbr) {
        this.sourceTrxRefNbr = sourceTrxRefNbr;
    }

    public String getSourceActionId() {
        return sourceActionId;
    }

    public void setSourceActionId(String sourceActionId) {
        this.sourceActionId = sourceActionId;
    }

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public void setIpAddress(String ipAddress) {
        this.ipAddress = ipAddress;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public void setErrorCode(String errorCode) {
        this.errorCode = errorCode;
    }

    public String getDedupKey() {
        return dedupKey;
    }

    public void setDedupKey(String dedupKey) {
        this.dedupKey = dedupKey;
    }

    public Timestamp getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Timestamp createdAt) {
        this.createdAt = createdAt;
    }

    /** Business validation is performed before the event projection is assembled. */
    @Override
    protected void validate() {
    }

    /** Do not expose customer identifiers or event metadata through object logging. */
    @Override
    public String toString() {
        return "HthCRMEvent3DomainDTO";
    }
}
