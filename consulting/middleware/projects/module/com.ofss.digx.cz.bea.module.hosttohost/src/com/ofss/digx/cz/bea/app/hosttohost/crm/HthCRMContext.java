package com.ofss.digx.cz.bea.app.hosttohost.crm;

import java.util.Map;
import java.util.prefs.Preferences;

import com.ofss.digx.cz.bea.common.constants.CZCommonConstants;
import com.ofss.digx.cz.bea.common.framework.crm.CRMConstants;
import com.ofss.digx.cz.bea.common.util.BeaParser;
import com.ofss.digx.cz.bea.common.util.BeaParserClient;
import com.ofss.digx.cz.bea.common.util.CZLocaleUtils;
import com.ofss.fc.app.context.SessionContext;
import com.ofss.fc.infra.config.ConfigurationFactory;
import com.ofss.fc.infra.thread.ThreadAttribute;

/**
 * HTH projection of the common metadata collected by BCO CRMAsserter.
 * Capture on the request thread, before transaction completion can lose its context.
 * Reads BCO configuration and utilities without invoking or changing the BCO collector.
 */
public final class HthCRMContext {
    private HthCRMContext() { }

    public static void capture(Map<String, Object> snapshot) {
        // Only these named values cross into the event; never copy headers or token data.
        for (String key : new String[] {"crmRecordType", "crmFiller01", "crmChnlId", "crmChnlTypeCode",
                "crmFeeChrgCode", "crmEventCountryCode", "crmLang", "crmMobileBrand", "crmDeviceModel",
                "crmDeviceOsVersion"}) snapshot.remove(key);
        Object task = ThreadAttribute.get(ThreadAttribute.CURRENT_TASK);
        if (string(task) == null) task = attribute(ThreadAttribute.CURRENT_TASK);
        put(snapshot, "crmTask", task, 100);
        put(snapshot, "crmIp", attribute("FMO_IP_ADDRESS"), 64);
        put(snapshot, "crmUserRole", attribute("User-Role"), 32);
        put(snapshot, "crmBioType", attribute("Bio-Type"), 32);
        put(snapshot, "crmRegMethod", attribute("Reg-Method"), 64);
        put(snapshot, "crmAuthMethod", attribute("Auth-Method"), 64);
        put(snapshot, "crmAppVersion", attribute("App-Version"), 64);
        String error = string(snapshot.get("crmApprovalErrorCode"));
        if (error == null || !error.matches("[A-Za-z0-9_.:-]{1,100}"))
            error = string(attribute("TFA_ERR_CODE"));
        snapshot.put("crmErrCode", error == null ? null : error.substring(Math.max(0, error.length() - 20)));
        Object admin = attribute("isAdmin");
        snapshot.put("crmSelfSrvInd", admin instanceof Boolean
                ? ((Boolean) admin ? CRMConstants.CRM_BANK_INDICATOR : CRMConstants.CRM_CORPORATE_INDICATOR)
                : null);
        Object oneManBank = ThreadAttribute.get(CZCommonConstants.IS_OMB_ENABLED);
        if (!(oneManBank instanceof Boolean)) oneManBank = attribute(CZCommonConstants.IS_OMB_ENABLED);
        // The platform initializes false before evaluating the rule. Only true proves
        // an affirmative result; do not invent N or rerun approval/host queries for CRM.
        snapshot.put("crmOmbFlag", Boolean.FALSE.equals(admin) && Boolean.TRUE.equals(oneManBank) ? "Y" : null);

        Object session = ThreadAttribute.get(ThreadAttribute.SESSION_CONTEXT);
        SessionContext context = session instanceof SessionContext ? (SessionContext) session : null;
        if (string(snapshot.get("actorUserId")) == null) {
            Object user = attribute("FMO_USER_ID");
            if (string(user) == null && context != null) user = context.getUserId();
            put(snapshot, "actorUserId", user, 256);
        }
        if (string(snapshot.get("partyId")) == null && context != null)
            put(snapshot, "partyId", context.getTransactingPartyCode(), 64);
        Object reference = ThreadAttribute.get("TRANS_REF");
        if (string(reference) == null) reference = attribute("TRANS_REF");
        put(snapshot, "crmReference", reference, 128);

        try {
            Preferences config = ConfigurationFactory.getInstance().getConfigurations(CRMConstants.CRM_CONFIGURATION);
            put(snapshot, "crmRecordType", config.get(CRMConstants.CRM_RECORD_TYPE, null), 16);
            // Filler may deliberately contain spaces; unlike identifiers, do not discard it.
            snapshot.put("crmFiller01", limit(config.get(CRMConstants.CRM_FILLER_01, null), 64));
            put(snapshot, "crmChnlId", "BCM".equals(attribute("Login-Channel"))
                    ? CRMConstants.CRM_CHNL_ID_BCM : config.get(CRMConstants.CRM_CHNL_ID_INTERNET, null), 64);
            put(snapshot, "crmChnlTypeCode", config.get(CRMConstants.CRM_CHNL_TYPE_CODE_INTERNET, null), 32);
            put(snapshot, "crmFeeChrgCode", config.get(CRMConstants.CRM_FEE_CHARGE_CODE, null), 32);
            put(snapshot, "crmEventCountryCode", config.get(CRMConstants.CRM_EVENT_COUNTRY_CODE, null), 8);
        } catch (RuntimeException | LinkageError failure) {
            // Optional metadata must not suppress an otherwise valid onboarding event.
            HthCRMAsserter.log("CONTEXT_CONFIG_FAILED", null, failure);
        }
        try {
            String locale = CZLocaleUtils.getUserLocale();
            snapshot.put("crmLang", CZCommonConstants.LOCALE_SIMPLI_CHI.equalsIgnoreCase(locale) ? "sc"
                    : CZCommonConstants.LOCALE_TRAD_CHI.equalsIgnoreCase(locale) ? "tc" : "en");
        } catch (RuntimeException | LinkageError failure) {
            HthCRMAsserter.log("CONTEXT_LOCALE_FAILED", null, failure);
        }
        try {
            String agent = string(attribute("FMO_USER_AGENT"));
            if (agent != null) {
                BeaParserClient client = new BeaParser().parse(limit(agent, 4096));
                if (client != null) {
                    if (client.getOs() != null)
                        put(snapshot, "crmMobileBrand", client.getOs().getSystem(), 128);
                    if (client.getBrowser() != null) {
                        put(snapshot, "crmDeviceModel", client.getBrowser().getBrand(), 128);
                        put(snapshot, "crmDeviceOsVersion", client.getBrowser().getVersion(), 128);
                    }
                }
            }
        } catch (RuntimeException | LinkageError failure) {
            HthCRMAsserter.log("CONTEXT_DEVICE_FAILED", null, failure);
        }
    }

    private static Object attribute(String name) {
        return com.ofss.digx.infra.thread.ThreadAttribute.get(name);
    }

    private static String string(Object value) {
        return value instanceof String && !((String) value).trim().isEmpty() ? (String) value : null;
    }

    private static void put(Map<String, Object> values, String name, Object value, int length) {
        values.put(name, limit(string(value), length));
    }

    private static String limit(String value, int length) {
        return value == null || value.length() <= length ? value : value.substring(0, length);
    }
}
