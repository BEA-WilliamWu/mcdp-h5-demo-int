package com.ofss.digx.cz.bea.domain.service.dispatch;

import com.ofss.digx.cz.bea.app.email.dto.alerts.MNGEmailAlertsDTO;
import com.ofss.digx.cz.bea.app.email.dto.alerts.MNGSmsAlertDTO;
import com.ofss.digx.cz.bea.app.sms.dto.user.HthContactNotificationPlan;
import com.ofss.digx.cz.bea.app.hosttohost.dto.HthUserAccessNotificationPlan;
import com.ofss.digx.cz.bea.app.hosttohost.dto.HthUserAccessActivityLogDTO;
import com.ofss.digx.cz.bea.app.sms.dto.user.HthProfileContactUpdateActivityLogDTO;
import com.ofss.digx.cz.bea.extxface.alert.IMNGEmailAdapter;
import com.ofss.digx.cz.bea.extxface.alert.IMNGSmsAdapter;
import com.ofss.digx.extxface.extxface.ExtxfaceAdapterFactory;
import com.ofss.fc.app.ep.dto.action.AlertRequestDTO;
import com.ofss.fc.domain.ep.service.dispatch.DispatchResult;
import com.ofss.fc.enumeration.DeterminantType;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Allowlisted 851/1216 MNG delivery. Uses BCO request builders/adapters with durable snapshot destinations. */
final class HthContactNotificationDispatch {
    private static final Logger LOG = Logger.getLogger(HthContactNotificationDispatch.class.getName());
    private HthContactNotificationDispatch() { }
    static boolean matches(AlertRequestDTO request) {
        return request != null && request.getEventAction() != null && request.getEventAction().getKeyDTO() != null
                && (HthContactNotificationPlan.isEvent(request.getEventAction().getKeyDTO().getEventId())
                    || HthUserAccessNotificationPlan.isEvent(request.getEventAction().getKeyDTO().getEventId()));
    }
    static DispatchResult dispatch(AlertRequestDTO request, String channel, String body, String subject) {
        DispatchResult result = new DispatchResult(); result.setIsDispatchSuccessfull(false);
        String id = null;
        String logPrefix = "HTH_CONTACT";
        try {
            if (!matches(request)) return result;
            String event = request.getEventAction().getKeyDTO().getEventId();
            boolean access = HthUserAccessNotificationPlan.isEvent(event);
            if (access) logPrefix = "HTH_ACCESS";
            if (access ? !HthUserAccessNotificationService.enabled() : !HthContactNotificationService.enabled()) return result;
            // Respect the existing dispatcher mock switch without marking a real delivery successful.
            if (!"false".equalsIgnoreCase(com.ofss.fc.infra.config.ConfigurationFactory.getInstance()
                    .getConfigurations("DispatchDetails").get("isDispatchMocked", "false"))) {
                result.setMessage("HTH contact notification held by dispatcher mock configuration");
                return result;
            }
            if (access) {
                if (!(request.getActivityLog() instanceof HthUserAccessActivityLogDTO))
                    throw new IllegalStateException("Missing access notification snapshot");
                id = ((HthUserAccessActivityLogDTO) request.getActivityLog()).getHthAccessNotificationId();
            } else {
                if (!(request.getActivityLog() instanceof HthProfileContactUpdateActivityLogDTO))
                    throw new IllegalStateException("Missing contact snapshot");
                id = ((HthProfileContactUpdateActivityLogDTO) request.getActivityLog()).getHthContactNotificationId();
            }
            if (id == null || !id.matches("[0-9a-f]{32}")) throw new IllegalStateException("Missing contact reference");
            if (body == null || unresolved(body, access) || unresolved(subject, access))
                throw new IllegalStateException("Unresolved contact template");
            final HthContactNotificationRepository repository = HthContactNotificationRepository.configured(access);
            String hash = Base64.getEncoder().encodeToString(MessageDigest.getInstance("SHA-256")
                    .digest(body.getBytes(StandardCharsets.UTF_8)));
            HthContactNotificationRepository.Row row = repository.claimDispatch(id, channel, event,
                    request.getCodActDataId(), subject, hash);
            if (row == null) {
                // An existing attempt owns this recipient. Framework retries must not resend it.
                result.setIsDispatchSuccessfull(true); result.setMessage("Contact notification already claimed");
                return result;
            }
            String outcome = "UNKNOWN";
            try { outcome = send(row, body, subject); }
            finally { repository.dispatched(row.id, outcome); }
            result.setIsDispatchSuccessfull("SUBMITTED".equals(outcome));
            result.setMessage("HTH contact notification " + outcome);
            LOG.log(Level.INFO, logPrefix + " notification={0} stage=MNG outcome={1}", new Object[] {id, outcome});
        } catch (java.lang.Exception e) {
            LOG.log(Level.WARNING, logPrefix + " notification={0} stage=DISPATCH exception={1}",
                    new Object[] {id, e.getClass().getSimpleName()});
            result.setMessage("HTH contact notification pending reconciliation");
        }
        return result;
    }
    private static boolean unresolved(String text, boolean access) {
        if (text == null) return false;
        return access ? text.contains("#userNameId#") || text.contains("#userSysDate#") || text.contains("#compName#")
                : text.contains("#hthContact");
    }
    private static String send(HthContactNotificationRepository.Row row, String body, String subject)
            throws java.lang.Exception {
        if ("EMAIL".equals(row.channel)) {
            IMNGEmailAdapter adapter = ExtxfaceAdapterFactory.getInstance().getAdapter(IMNGEmailAdapter.class,
                    "createAlert", DeterminantType.Enterprise);
            List<MNGEmailAlertsDTO> response = adapter.createAlert(new EmailDispatcher().buildMNGrequest(
                    row.address, body, subject, row.mngRef(), row.activity(), row.description()));
            if (response == null || response.size() != 1 || response.get(0) == null) return "UNKNOWN";
            return response.get(0).getErrorCode() == null ? "SUBMITTED" : "REJECTED";
        }
        IMNGSmsAdapter adapter = ExtxfaceAdapterFactory.getInstance().getAdapter(IMNGSmsAdapter.class,
                "createAlert", DeterminantType.Enterprise);
        List<MNGSmsAlertDTO> response = adapter.createAlert(new SMSDispatcher().buildMNGrequest(
                row.address, body, row.mngRef(), row.activity(), row.description()));
        if (response == null || response.size() != 1 || response.get(0) == null) return "UNKNOWN";
        return response.get(0).getErrorCode() == null ? "SUBMITTED" : "REJECTED";
    }
}
