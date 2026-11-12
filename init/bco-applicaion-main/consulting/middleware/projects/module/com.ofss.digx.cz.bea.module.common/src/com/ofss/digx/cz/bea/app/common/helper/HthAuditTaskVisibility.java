package com.ofss.digx.cz.bea.app.common.helper;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.ofss.digx.app.adapter.AdapterFactoryConfigurator;
import com.ofss.digx.app.adapter.IAdapterFactory;
import com.ofss.digx.cz.bea.app.common.adapter.hostuserdetails.IHostUserDetailsInvocationAdapter;
import com.ofss.digx.cz.bea.common.constants.CommonAdapterConstants;
import com.ofss.digx.cz.bea.common.constants.CommonAdapterFactoryConstants;
import com.ofss.fc.infra.log.impl.MultiEntityLogger;

/** Request-scoped visibility of the new HTH onboarding audit choices only. */
public final class HthAuditTaskVisibility {
    private static final Logger LOGGER = MultiEntityLogger.getUniqueInstance()
            .getLogger(HthAuditTaskVisibility.class.getName());
    // Explicit IDs: never filter by translated labels or a general HTH prefix.
    // In particular, released Inquiry and shared BCO tasks must remain visible.
    private static final Set<String> CM_TASKS = new HashSet<String>(Arrays.asList(
            "MT_N_CUS_HTH", "MT_N_UUS_HTH", "MT_N_RUS_HTH",
            "UAT_N_HUA_NEW", "UAT_N_HUA_EDT", "UAT_N_HUA_DEL",
            "UAT_N_HAP_GEN", "UAT_N_HAP_REGEN", "UAT_N_HAP_RVL",
            "CM_N_HAP_SETUP", "CM_N_HAP_RESET", "PP_N_HTH_CERT_UPL"));
    private static final Set<String> BM_TASKS = new HashSet<String>(Arrays.asList(
            "PP_N_HTH_ENB", "PP_N_HTH_EDT", "PP_N_HTH_DIS"));
    private Boolean cmEnabled;
    private Boolean bmEnabled;

    public boolean isVisible(String taskId) {
        if (CM_TASKS.contains(taskId)) {
            if (cmEnabled == null) {
                cmEnabled = readToggle("ENABLE_HTH_CM_ONBOARDING");
            }
            return cmEnabled.booleanValue();
        }
        if (BM_TASKS.contains(taskId)) {
            if (bmEnabled == null) {
                bmEnabled = readToggle("ENABLE_HTH_BM_ONBOARDING");
            }
            return bmEnabled.booleanValue();
        }
        return true;
    }

    private boolean readToggle(String key) {
        try {
            // Same live CustomConfigUtil bridge as the existing onboarding UI toggle.
            IAdapterFactory factory = AdapterFactoryConfigurator.getInstance()
                    .getAdapterFactory(CommonAdapterFactoryConstants.HOSTUSER_DETAILS_ADAPTER_FACTORY);
            IHostUserDetailsInvocationAdapter adapter = (IHostUserDetailsInvocationAdapter)
                    factory.getAdapter(CommonAdapterConstants.HOST_USERDETAILS_INVOCATION_ADAPTER);
            String value = adapter.getCustomConfigUtilValue(key, "N");
            return value != null && "Y".equalsIgnoreCase(value.trim());
        } catch (java.lang.Exception e) {
            LOGGER.log(Level.WARNING,
                    "HTH audit toggle read failed: key={0}, exceptionType={1}; default=N",
                    new Object[] { key, e.getClass().getName() });
            return false;
        }
    }
}
