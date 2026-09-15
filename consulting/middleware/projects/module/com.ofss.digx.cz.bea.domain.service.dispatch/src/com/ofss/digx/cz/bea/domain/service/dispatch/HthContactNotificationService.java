package com.ofss.digx.cz.bea.domain.service.dispatch;

import com.ofss.digx.app.AbstractApplication;
import com.ofss.digx.cz.bea.app.sms.dto.user.HthContactNotificationPlan;
import com.ofss.digx.cz.bea.app.sms.dto.user.HthProfileContactUpdateActivityLogDTO;
import com.ofss.fc.app.context.SessionContext;
import com.ofss.fc.datatype.Date;
import com.ofss.fc.enumeration.ep.DestinationType;
import com.ofss.fc.enumeration.ep.SubscriberType;
import com.ofss.fc.infra.config.ConfigurationFactory;
import com.ofss.fc.xface.ep.dto.NotificationDetail;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Runs from the existing BatchExecutionScheduler, outside the profile update transaction. */
public final class HthContactNotificationService extends AbstractApplication {
    private static final Logger LOG = Logger.getLogger(HthContactNotificationService.class.getName());
    static boolean enabled() {
        return ConfigurationFactory.getInstance().getConfigurations("HthProfileContactNotification")
                .getBoolean("HTH_PROFILE_CONTACT_NOTIFICATION_ENABLED", false);
    }
    public void process(SessionContext context) {
        if (!enabled()) return;
        String originalLocale = context.getUserLocale();
        String originalParty = context.getTransactingPartyCode();
        try {
            HthContactNotificationRepository repository = HthContactNotificationRepository.configured();
            repository.reconcile(context.getTargetUnit());
            for (HthContactNotificationRepository.Row row : repository.pending(context.getTargetUnit())) {
                try {
                    if (!repository.claimPublication(row.id)) continue;
                    context.setTransactingPartyCode(row.party);
                    context.setUserLocale(locale(row.locale));
                    NotificationDetail detail = new NotificationDetail();
                    detail.setRecipientId(row.party);
                    detail.setRecipientType(SubscriberType.EXTERNAL.toString());
                    detail.setDestination("EMAIL".equals(row.channel) ? DestinationType.EMAIL : DestinationType.SMS);
                    detail.setDispatchAddress(row.address);
                    HthProfileContactUpdateActivityLogDTO log = new HthProfileContactUpdateActivityLogDTO();
                    log.setCustomerId(row.party);
                    log.setHthContactNotificationId(row.id);
                    // ID is encoded as text; no raw email/mobile is embedded in a template.
                    log.setHthContactUserName(escape(row.user.substring(0, row.user.lastIndexOf('@'))));
                    log.setHthContactApprovedAt(row.approvedAt);
                    log.setNotificationDetails(new NotificationDetail[] {detail});
                    super.registerActivityAndGenerateEvent(context, HthContactNotificationPlan.ACTIVITY,
                            row.event(), new Date(), log);
                    repository.published(row.id);
                } catch (java.lang.Exception e) {
                    LOG.log(Level.WARNING, "HTH_CONTACT notification={0} stage=PUBLICATION exception={1}",
                            new Object[] {row.id, e.getClass().getSimpleName()});
                }
            }
        } catch (java.lang.Exception e) {
            LOG.log(Level.SEVERE, "HTH_CONTACT stage=BATCH exception={0}", e.getClass().getSimpleName());
        } finally {
            context.setUserLocale(originalLocale); context.setTransactingPartyCode(originalParty);
        }
    }
    static String locale(String locale) {
        return "zh-Hant".equalsIgnoreCase(locale) ? "zh-Hant" :
                ("zh-Hans-CN".equalsIgnoreCase(locale) ? "zh-Hans-CN" : "en");
    }
    private static String escape(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&#39;");
    }
}
