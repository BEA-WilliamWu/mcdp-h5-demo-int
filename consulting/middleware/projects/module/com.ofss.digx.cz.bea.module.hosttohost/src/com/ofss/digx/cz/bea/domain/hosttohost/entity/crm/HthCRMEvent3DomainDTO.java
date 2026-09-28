package com.ofss.digx.cz.bea.domain.hosttohost.entity.crm;

import java.math.BigDecimal;
import java.sql.Timestamp;

import com.ofss.fc.framework.domain.AbstractDomainObject;
import com.ofss.fc.framework.domain.IPersistenceObject;

/**
 * Independent HTH CRM record based on the BCO ORM field model.
 * HTH metadata is appended to that model; out-of-scope and secret fields stay null.
 */
public class HthCRMEvent3DomainDTO extends AbstractDomainObject implements IPersistenceObject {
    private static final long serialVersionUID = 1L;

    private HthCRMEvent3DomainKey key;
    private String recordType;
    private String filler01;
    private String eventDte;
    private String eventTime;
    private String chnlId;
    private String chnlTypeCode;
    private String eventStatusCode;
    private String crDrInd;
    private String feeChrgCode;
    private String eventActvTypeCode;
    private String finInd;
    private String selfSrvInd;
    private String userId;
    private String eventCountryCode;
    private String acctNbr;
    private String phoneNbr;
    private String phoneNbrAcctNbr;
    private String phoneNbrReqResult;
    private String phoneDefaultInd;
    private String electAdd;
    private String electAddAcctNbr;
    private String electAddReqResult;
    private String electAddDefaultInd;
    private String fpsId;
    private String fpsAcctNbr;
    private String fpsReqResult;
    private String debitAcctNbr;
    private String eventCcyCode;
    private BigDecimal eventAmt;
    private BigDecimal feeChrgAmt;
    private String feeCcyCode;
    private String trfDte;
    private String trfFreq;
    private String proxyIdType;
    private String proxyId;
    private String payeeName;
    private String payeeBankCode;
    private String eventRem;
    private String refNbr;
    private String fromDte;
    private String toDte;
    private String mandateId;
    private String eddaMaintAction;
    private String sourceTrxRefNbr;
    private String payCatPurpCode;
    private String payPurpCode;
    private String deviceId;
    private String mobileBrand;
    private String platformCode;
    private String deviceModel;
    private String deviceOsVersion;
    private String ipAddress;
    private String filler02;
    private BigDecimal eventAmtHke;
    private BigDecimal eventExRate;
    private String mrchId;
    private String acctCcyCode;
    private BigDecimal acctAmt;
    private String screenId;
    private String ombFlag;
    private String multiAppRejTxnCnt;
    private String couponCode;
    private String actlintrate;
    private String tTAutoRoute;
    private String suspiciousActivity;
    private String suspiciousInd;
    private String withMrch;
    private String cmpyName;
    private String docId;
    private String docType;
    private String docCountryCode;
    private String mrchUserId;
    private String mrchUserName;
    private String docCmpyName;
    private String docAcctNbr;
    private String sleMrchName;
    private String format;
    private String txnCount;
    private String advFreqType;
    private String advType;
    private String sleMrchId;
    private String ar_Token;
    private String treasury_Ref;
    private String lmSweepInstructionType;
    private String lmFrequencyExecutionDay;
    private String lmEffectiveDate;
    private String lmRuleSetupDate;
    private String lmSweepingAmountThreshold;
    private String lmFpxTxnNbr;
    private String lmInstructionNbr;
    private String userRole;
    private String tokenId;
    private String errCode;
    private String bioType;
    private String regMethod;
    private String authMethod;
    private String lang;
    private String appVersion;
    private String sourceSystem;
    private String channelType;
    private String targetUserChannel;
    private String activityKey;
    private String phase;
    private String targetUserId;
    private String relationshipType;
    private String serviceId;
    private String taskCode;
    private String sourceActionId;
    private String requestId;
    private String errorCode;
    private String dedupKey;
    private Timestamp createdAt;

    public HthCRMEvent3DomainDTO() {
    }

    /** Copy the complete event projection without sharing mutable key or timestamp state. */
    public HthCRMEvent3DomainDTO(HthCRMEvent3DomainDTO source) {
        if (source == null) {
            throw new IllegalArgumentException("Source HTH CRM event is required");
        }
        if (source.key != null) {
            key = new HthCRMEvent3DomainKey();
            key.setEventId(source.key.getEventId());
        }
        recordType = source.recordType;
        filler01 = source.filler01;
        eventDte = source.eventDte;
        eventTime = source.eventTime;
        chnlId = source.chnlId;
        chnlTypeCode = source.chnlTypeCode;
        eventStatusCode = source.eventStatusCode;
        crDrInd = source.crDrInd;
        feeChrgCode = source.feeChrgCode;
        eventActvTypeCode = source.eventActvTypeCode;
        finInd = source.finInd;
        selfSrvInd = source.selfSrvInd;
        userId = source.userId;
        eventCountryCode = source.eventCountryCode;
        acctNbr = source.acctNbr;
        phoneNbr = source.phoneNbr;
        phoneNbrAcctNbr = source.phoneNbrAcctNbr;
        phoneNbrReqResult = source.phoneNbrReqResult;
        phoneDefaultInd = source.phoneDefaultInd;
        electAdd = source.electAdd;
        electAddAcctNbr = source.electAddAcctNbr;
        electAddReqResult = source.electAddReqResult;
        electAddDefaultInd = source.electAddDefaultInd;
        fpsId = source.fpsId;
        fpsAcctNbr = source.fpsAcctNbr;
        fpsReqResult = source.fpsReqResult;
        debitAcctNbr = source.debitAcctNbr;
        eventCcyCode = source.eventCcyCode;
        eventAmt = source.eventAmt;
        feeChrgAmt = source.feeChrgAmt;
        feeCcyCode = source.feeCcyCode;
        trfDte = source.trfDte;
        trfFreq = source.trfFreq;
        proxyIdType = source.proxyIdType;
        proxyId = source.proxyId;
        payeeName = source.payeeName;
        payeeBankCode = source.payeeBankCode;
        eventRem = source.eventRem;
        refNbr = source.refNbr;
        fromDte = source.fromDte;
        toDte = source.toDte;
        mandateId = source.mandateId;
        eddaMaintAction = source.eddaMaintAction;
        sourceTrxRefNbr = source.sourceTrxRefNbr;
        payCatPurpCode = source.payCatPurpCode;
        payPurpCode = source.payPurpCode;
        deviceId = source.deviceId;
        mobileBrand = source.mobileBrand;
        platformCode = source.platformCode;
        deviceModel = source.deviceModel;
        deviceOsVersion = source.deviceOsVersion;
        ipAddress = source.ipAddress;
        filler02 = source.filler02;
        eventAmtHke = source.eventAmtHke;
        eventExRate = source.eventExRate;
        mrchId = source.mrchId;
        acctCcyCode = source.acctCcyCode;
        acctAmt = source.acctAmt;
        screenId = source.screenId;
        ombFlag = source.ombFlag;
        multiAppRejTxnCnt = source.multiAppRejTxnCnt;
        couponCode = source.couponCode;
        actlintrate = source.actlintrate;
        tTAutoRoute = source.tTAutoRoute;
        suspiciousActivity = source.suspiciousActivity;
        suspiciousInd = source.suspiciousInd;
        withMrch = source.withMrch;
        cmpyName = source.cmpyName;
        docId = source.docId;
        docType = source.docType;
        docCountryCode = source.docCountryCode;
        mrchUserId = source.mrchUserId;
        mrchUserName = source.mrchUserName;
        docCmpyName = source.docCmpyName;
        docAcctNbr = source.docAcctNbr;
        sleMrchName = source.sleMrchName;
        format = source.format;
        txnCount = source.txnCount;
        advFreqType = source.advFreqType;
        advType = source.advType;
        sleMrchId = source.sleMrchId;
        ar_Token = source.ar_Token;
        treasury_Ref = source.treasury_Ref;
        lmSweepInstructionType = source.lmSweepInstructionType;
        lmFrequencyExecutionDay = source.lmFrequencyExecutionDay;
        lmEffectiveDate = source.lmEffectiveDate;
        lmRuleSetupDate = source.lmRuleSetupDate;
        lmSweepingAmountThreshold = source.lmSweepingAmountThreshold;
        lmFpxTxnNbr = source.lmFpxTxnNbr;
        lmInstructionNbr = source.lmInstructionNbr;
        userRole = source.userRole;
        tokenId = source.tokenId;
        errCode = source.errCode;
        bioType = source.bioType;
        regMethod = source.regMethod;
        authMethod = source.authMethod;
        lang = source.lang;
        appVersion = source.appVersion;
        sourceSystem = source.sourceSystem;
        channelType = source.channelType;
        targetUserChannel = source.targetUserChannel;
        activityKey = source.activityKey;
        phase = source.phase;
        targetUserId = source.targetUserId;
        relationshipType = source.relationshipType;
        serviceId = source.serviceId;
        taskCode = source.taskCode;
        sourceActionId = source.sourceActionId;
        requestId = source.requestId;
        errorCode = source.errorCode;
        dedupKey = source.dedupKey;
        if (source.createdAt != null) {
            createdAt = new Timestamp(source.createdAt.getTime());
            createdAt.setNanos(source.createdAt.getNanos());
        }
    }

    public HthCRMEvent3DomainDTO copy() {
        return new HthCRMEvent3DomainDTO(this);
    }

    public HthCRMEvent3DomainKey getKey() {
        return key;
    }

    public void setKey(HthCRMEvent3DomainKey key) {
        this.key = key;
    }

    public String getRecordType() {
        return recordType;
    }

    public void setRecordType(String recordType) {
        this.recordType = recordType;
    }

    public String getFiller01() {
        return filler01;
    }

    public void setFiller01(String filler01) {
        this.filler01 = filler01;
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

    public String getChnlId() {
        return chnlId;
    }

    public void setChnlId(String chnlId) {
        this.chnlId = chnlId;
    }

    public String getChnlTypeCode() {
        return chnlTypeCode;
    }

    public void setChnlTypeCode(String chnlTypeCode) {
        this.chnlTypeCode = chnlTypeCode;
    }

    public String getEventStatusCode() {
        return eventStatusCode;
    }

    public void setEventStatusCode(String eventStatusCode) {
        this.eventStatusCode = eventStatusCode;
    }

    public String getCrDrInd() {
        return crDrInd;
    }

    public void setCrDrInd(String crDrInd) {
        this.crDrInd = crDrInd;
    }

    public String getFeeChrgCode() {
        return feeChrgCode;
    }

    public void setFeeChrgCode(String feeChrgCode) {
        this.feeChrgCode = feeChrgCode;
    }

    public String getEventActvTypeCode() {
        return eventActvTypeCode;
    }

    public void setEventActvTypeCode(String eventActvTypeCode) {
        this.eventActvTypeCode = eventActvTypeCode;
    }

    public String getFinInd() {
        return finInd;
    }

    public void setFinInd(String finInd) {
        this.finInd = finInd;
    }

    public String getSelfSrvInd() {
        return selfSrvInd;
    }

    public void setSelfSrvInd(String selfSrvInd) {
        this.selfSrvInd = selfSrvInd;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getEventCountryCode() {
        return eventCountryCode;
    }

    public void setEventCountryCode(String eventCountryCode) {
        this.eventCountryCode = eventCountryCode;
    }

    public String getAcctNbr() {
        return acctNbr;
    }

    public void setAcctNbr(String acctNbr) {
        this.acctNbr = acctNbr;
    }

    public String getPhoneNbr() {
        return phoneNbr;
    }

    public void setPhoneNbr(String phoneNbr) {
        this.phoneNbr = phoneNbr;
    }

    public String getPhoneNbrAcctNbr() {
        return phoneNbrAcctNbr;
    }

    public void setPhoneNbrAcctNbr(String phoneNbrAcctNbr) {
        this.phoneNbrAcctNbr = phoneNbrAcctNbr;
    }

    public String getPhoneNbrReqResult() {
        return phoneNbrReqResult;
    }

    public void setPhoneNbrReqResult(String phoneNbrReqResult) {
        this.phoneNbrReqResult = phoneNbrReqResult;
    }

    public String getPhoneDefaultInd() {
        return phoneDefaultInd;
    }

    public void setPhoneDefaultInd(String phoneDefaultInd) {
        this.phoneDefaultInd = phoneDefaultInd;
    }

    public String getElectAdd() {
        return electAdd;
    }

    public void setElectAdd(String electAdd) {
        this.electAdd = electAdd;
    }

    public String getElectAddAcctNbr() {
        return electAddAcctNbr;
    }

    public void setElectAddAcctNbr(String electAddAcctNbr) {
        this.electAddAcctNbr = electAddAcctNbr;
    }

    public String getElectAddReqResult() {
        return electAddReqResult;
    }

    public void setElectAddReqResult(String electAddReqResult) {
        this.electAddReqResult = electAddReqResult;
    }

    public String getElectAddDefaultInd() {
        return electAddDefaultInd;
    }

    public void setElectAddDefaultInd(String electAddDefaultInd) {
        this.electAddDefaultInd = electAddDefaultInd;
    }

    public String getFpsId() {
        return fpsId;
    }

    public void setFpsId(String fpsId) {
        this.fpsId = fpsId;
    }

    public String getFpsAcctNbr() {
        return fpsAcctNbr;
    }

    public void setFpsAcctNbr(String fpsAcctNbr) {
        this.fpsAcctNbr = fpsAcctNbr;
    }

    public String getFpsReqResult() {
        return fpsReqResult;
    }

    public void setFpsReqResult(String fpsReqResult) {
        this.fpsReqResult = fpsReqResult;
    }

    public String getDebitAcctNbr() {
        return debitAcctNbr;
    }

    public void setDebitAcctNbr(String debitAcctNbr) {
        this.debitAcctNbr = debitAcctNbr;
    }

    public String getEventCcyCode() {
        return eventCcyCode;
    }

    public void setEventCcyCode(String eventCcyCode) {
        this.eventCcyCode = eventCcyCode;
    }

    public BigDecimal getEventAmt() {
        return eventAmt;
    }

    public void setEventAmt(BigDecimal eventAmt) {
        this.eventAmt = eventAmt;
    }

    public BigDecimal getFeeChrgAmt() {
        return feeChrgAmt;
    }

    public void setFeeChrgAmt(BigDecimal feeChrgAmt) {
        this.feeChrgAmt = feeChrgAmt;
    }

    public String getFeeCcyCode() {
        return feeCcyCode;
    }

    public void setFeeCcyCode(String feeCcyCode) {
        this.feeCcyCode = feeCcyCode;
    }

    public String getTrfDte() {
        return trfDte;
    }

    public void setTrfDte(String trfDte) {
        this.trfDte = trfDte;
    }

    public String getTrfFreq() {
        return trfFreq;
    }

    public void setTrfFreq(String trfFreq) {
        this.trfFreq = trfFreq;
    }

    public String getProxyIdType() {
        return proxyIdType;
    }

    public void setProxyIdType(String proxyIdType) {
        this.proxyIdType = proxyIdType;
    }

    public String getProxyId() {
        return proxyId;
    }

    public void setProxyId(String proxyId) {
        this.proxyId = proxyId;
    }

    public String getPayeeName() {
        return payeeName;
    }

    public void setPayeeName(String payeeName) {
        this.payeeName = payeeName;
    }

    public String getPayeeBankCode() {
        return payeeBankCode;
    }

    public void setPayeeBankCode(String payeeBankCode) {
        this.payeeBankCode = payeeBankCode;
    }

    public String getEventRem() {
        return eventRem;
    }

    public void setEventRem(String eventRem) {
        this.eventRem = eventRem;
    }

    public String getRefNbr() {
        return refNbr;
    }

    public void setRefNbr(String refNbr) {
        this.refNbr = refNbr;
    }

    public String getFromDte() {
        return fromDte;
    }

    public void setFromDte(String fromDte) {
        this.fromDte = fromDte;
    }

    public String getToDte() {
        return toDte;
    }

    public void setToDte(String toDte) {
        this.toDte = toDte;
    }

    public String getMandateId() {
        return mandateId;
    }

    public void setMandateId(String mandateId) {
        this.mandateId = mandateId;
    }

    public String getEddaMaintAction() {
        return eddaMaintAction;
    }

    public void setEddaMaintAction(String eddaMaintAction) {
        this.eddaMaintAction = eddaMaintAction;
    }

    public String getSourceTrxRefNbr() {
        return sourceTrxRefNbr;
    }

    public void setSourceTrxRefNbr(String sourceTrxRefNbr) {
        this.sourceTrxRefNbr = sourceTrxRefNbr;
    }

    public String getPayCatPurpCode() {
        return payCatPurpCode;
    }

    public void setPayCatPurpCode(String payCatPurpCode) {
        this.payCatPurpCode = payCatPurpCode;
    }

    public String getPayPurpCode() {
        return payPurpCode;
    }

    public void setPayPurpCode(String payPurpCode) {
        this.payPurpCode = payPurpCode;
    }

    public String getDeviceId() {
        return deviceId;
    }

    public void setDeviceId(String deviceId) {
        this.deviceId = deviceId;
    }

    public String getMobileBrand() {
        return mobileBrand;
    }

    public void setMobileBrand(String mobileBrand) {
        this.mobileBrand = mobileBrand;
    }

    public String getPlatformCode() {
        return platformCode;
    }

    public void setPlatformCode(String platformCode) {
        this.platformCode = platformCode;
    }

    public String getDeviceModel() {
        return deviceModel;
    }

    public void setDeviceModel(String deviceModel) {
        this.deviceModel = deviceModel;
    }

    public String getDeviceOsVersion() {
        return deviceOsVersion;
    }

    public void setDeviceOsVersion(String deviceOsVersion) {
        this.deviceOsVersion = deviceOsVersion;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public void setIpAddress(String ipAddress) {
        this.ipAddress = ipAddress;
    }

    public String getFiller02() {
        return filler02;
    }

    public void setFiller02(String filler02) {
        this.filler02 = filler02;
    }

    public BigDecimal getEventAmtHke() {
        return eventAmtHke;
    }

    public void setEventAmtHke(BigDecimal eventAmtHke) {
        this.eventAmtHke = eventAmtHke;
    }

    public BigDecimal getEventExRate() {
        return eventExRate;
    }

    public void setEventExRate(BigDecimal eventExRate) {
        this.eventExRate = eventExRate;
    }

    public String getMrchId() {
        return mrchId;
    }

    public void setMrchId(String mrchId) {
        this.mrchId = mrchId;
    }

    public String getAcctCcyCode() {
        return acctCcyCode;
    }

    public void setAcctCcyCode(String acctCcyCode) {
        this.acctCcyCode = acctCcyCode;
    }

    public BigDecimal getAcctAmt() {
        return acctAmt;
    }

    public void setAcctAmt(BigDecimal acctAmt) {
        this.acctAmt = acctAmt;
    }

    public String getScreenId() {
        return screenId;
    }

    public void setScreenId(String screenId) {
        this.screenId = screenId;
    }

    public String getOmbFlag() {
        return ombFlag;
    }

    public void setOmbFlag(String ombFlag) {
        this.ombFlag = ombFlag;
    }

    public String getMultiAppRejTxnCnt() {
        return multiAppRejTxnCnt;
    }

    public void setMultiAppRejTxnCnt(String multiAppRejTxnCnt) {
        this.multiAppRejTxnCnt = multiAppRejTxnCnt;
    }

    public String getCouponCode() {
        return couponCode;
    }

    public void setCouponCode(String couponCode) {
        this.couponCode = couponCode;
    }

    public String getActlintrate() {
        return actlintrate;
    }

    public void setActlintrate(String actlintrate) {
        this.actlintrate = actlintrate;
    }

    public String gettTAutoRoute() {
        return tTAutoRoute;
    }

    public void settTAutoRoute(String tTAutoRoute) {
        this.tTAutoRoute = tTAutoRoute;
    }

    public String getSuspiciousActivity() {
        return suspiciousActivity;
    }

    public void setSuspiciousActivity(String suspiciousActivity) {
        this.suspiciousActivity = suspiciousActivity;
    }

    public String getSuspiciousInd() {
        return suspiciousInd;
    }

    public void setSuspiciousInd(String suspiciousInd) {
        this.suspiciousInd = suspiciousInd;
    }

    public String getWithMrch() {
        return withMrch;
    }

    public void setWithMrch(String withMrch) {
        this.withMrch = withMrch;
    }

    public String getCmpyName() {
        return cmpyName;
    }

    public void setCmpyName(String cmpyName) {
        this.cmpyName = cmpyName;
    }

    public String getDocId() {
        return docId;
    }

    public void setDocId(String docId) {
        this.docId = docId;
    }

    public String getDocType() {
        return docType;
    }

    public void setDocType(String docType) {
        this.docType = docType;
    }

    public String getDocCountryCode() {
        return docCountryCode;
    }

    public void setDocCountryCode(String docCountryCode) {
        this.docCountryCode = docCountryCode;
    }

    public String getMrchUserId() {
        return mrchUserId;
    }

    public void setMrchUserId(String mrchUserId) {
        this.mrchUserId = mrchUserId;
    }

    public String getMrchUserName() {
        return mrchUserName;
    }

    public void setMrchUserName(String mrchUserName) {
        this.mrchUserName = mrchUserName;
    }

    public String getDocCmpyName() {
        return docCmpyName;
    }

    public void setDocCmpyName(String docCmpyName) {
        this.docCmpyName = docCmpyName;
    }

    public String getDocAcctNbr() {
        return docAcctNbr;
    }

    public void setDocAcctNbr(String docAcctNbr) {
        this.docAcctNbr = docAcctNbr;
    }

    public String getSleMrchName() {
        return sleMrchName;
    }

    public void setSleMrchName(String sleMrchName) {
        this.sleMrchName = sleMrchName;
    }

    public String getFormat() {
        return format;
    }

    public void setFormat(String format) {
        this.format = format;
    }

    public String getTxnCount() {
        return txnCount;
    }

    public void setTxnCount(String txnCount) {
        this.txnCount = txnCount;
    }

    public String getAdvFreqType() {
        return advFreqType;
    }

    public void setAdvFreqType(String advFreqType) {
        this.advFreqType = advFreqType;
    }

    public String getAdvType() {
        return advType;
    }

    public void setAdvType(String advType) {
        this.advType = advType;
    }

    public String getSleMrchId() {
        return sleMrchId;
    }

    public void setSleMrchId(String sleMrchId) {
        this.sleMrchId = sleMrchId;
    }

    public String getAr_Token() {
        return ar_Token;
    }

    public void setAr_Token(String ar_Token) {
        this.ar_Token = ar_Token;
    }

    public String getTreasury_Ref() {
        return treasury_Ref;
    }

    public void setTreasury_Ref(String treasury_Ref) {
        this.treasury_Ref = treasury_Ref;
    }

    public String getLmSweepInstructionType() {
        return lmSweepInstructionType;
    }

    public void setLmSweepInstructionType(String lmSweepInstructionType) {
        this.lmSweepInstructionType = lmSweepInstructionType;
    }

    public String getLmFrequencyExecutionDay() {
        return lmFrequencyExecutionDay;
    }

    public void setLmFrequencyExecutionDay(String lmFrequencyExecutionDay) {
        this.lmFrequencyExecutionDay = lmFrequencyExecutionDay;
    }

    public String getLmEffectiveDate() {
        return lmEffectiveDate;
    }

    public void setLmEffectiveDate(String lmEffectiveDate) {
        this.lmEffectiveDate = lmEffectiveDate;
    }

    public String getLmRuleSetupDate() {
        return lmRuleSetupDate;
    }

    public void setLmRuleSetupDate(String lmRuleSetupDate) {
        this.lmRuleSetupDate = lmRuleSetupDate;
    }

    public String getLmSweepingAmountThreshold() {
        return lmSweepingAmountThreshold;
    }

    public void setLmSweepingAmountThreshold(String lmSweepingAmountThreshold) {
        this.lmSweepingAmountThreshold = lmSweepingAmountThreshold;
    }

    public String getLmFpxTxnNbr() {
        return lmFpxTxnNbr;
    }

    public void setLmFpxTxnNbr(String lmFpxTxnNbr) {
        this.lmFpxTxnNbr = lmFpxTxnNbr;
    }

    public String getLmInstructionNbr() {
        return lmInstructionNbr;
    }

    public void setLmInstructionNbr(String lmInstructionNbr) {
        this.lmInstructionNbr = lmInstructionNbr;
    }

    public String getUserRole() {
        return userRole;
    }

    public void setUserRole(String userRole) {
        this.userRole = userRole;
    }

    public String getTokenId() {
        return tokenId;
    }

    public void setTokenId(String tokenId) {
        this.tokenId = tokenId;
    }

    public String getErrCode() {
        return errCode;
    }

    public void setErrCode(String errCode) {
        this.errCode = errCode;
    }

    public String getBioType() {
        return bioType;
    }

    public void setBioType(String bioType) {
        this.bioType = bioType;
    }

    public String getRegMethod() {
        return regMethod;
    }

    public void setRegMethod(String regMethod) {
        this.regMethod = regMethod;
    }

    public String getAuthMethod() {
        return authMethod;
    }

    public void setAuthMethod(String authMethod) {
        this.authMethod = authMethod;
    }

    public String getLang() {
        return lang;
    }

    public void setLang(String lang) {
        this.lang = lang;
    }

    public String getAppVersion() {
        return appVersion;
    }

    public void setAppVersion(String appVersion) {
        this.appVersion = appVersion;
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

    public String getPhase() {
        return phase;
    }

    public void setPhase(String phase) {
        this.phase = phase;
    }

    public String getTargetUserId() {
        return targetUserId;
    }

    public void setTargetUserId(String targetUserId) {
        this.targetUserId = targetUserId;
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
