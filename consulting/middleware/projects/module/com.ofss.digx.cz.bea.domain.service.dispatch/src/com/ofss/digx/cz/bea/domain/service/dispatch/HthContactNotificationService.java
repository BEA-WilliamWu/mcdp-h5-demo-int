package com.ofss.digx.cz.bea.domain.service.dispatch;

import com.ofss.digx.app.AbstractApplication;
import com.ofss.digx.app.Interaction;
import com.ofss.fc.service.response.TransactionStatus;
import com.ofss.digx.cz.bea.app.sms.dto.user.HthContactNotificationPlan;
import com.ofss.digx.cz.bea.app.sms.dto.user.HthProfileContactUpdateActivityLogDTO;
import com.ofss.fc.app.context.SessionContext;
import com.ofss.digx.cz.bea.app.customconfig.util.CustomConfigUtil;
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
        if (!"OBDX_BU".equals(context.getTargetUnit()) || !enabled()) return;
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
                    Interaction.begin(context);
                    try {
                        NotificationDetail detail = new NotificationDetail();
                        detail.setRecipientId(row.party);
                        detail.setRecipientType(SubscriberType.EXTERNAL.toString());
                        detail.setDestination("EMAIL".equals(row.channel) ? DestinationType.EMAIL : DestinationType.SMS);
                        detail.setDispatchAddress(row.address);
                        HthProfileContactUpdateActivityLogDTO log = new HthProfileContactUpdateActivityLogDTO();
                        log.setCustomerId(row.party);
                        log.setHthContactNotificationId(row.id);
                        // Reuse BCO metadata getters; userId remains the changed user even for AP delivery.
                        log.setUserId(row.user);
                        log.setProfileUser(escape(row.user.split("@")[0]));
                        log.setEmailId(escape(HthContactNotificationPlan.text(row.newEmail).replaceAll("(?<=.....).", "*")));
                        String kind = "BOTH".equals(row.change) ? "EMAILMOB" : ("EMAIL".equals(row.change) ? "EMAIL" : "MOB");
                        log.setEngMailSubj("BOTH".equals(row.change) ? "Email Address and Mobile No. Update" :
                                ("EMAIL".equals(row.change) ? "Email Address Update" : "Mobile No. Update"));
                        log.setEngMailContent("BOTH".equals(row.change) ? "email address and mobile no." :
                                ("EMAIL".equals(row.change) ? "email address." : "mobile no."));
                        log.setZhMailSubj(CustomConfigUtil.readConfigValue("INFOUPDATE_" + kind + "_UPDATE_MAILSUBJ", ""));
                        log.setZhMailContent(CustomConfigUtil.readConfigValue("INFOUPDATE_" + kind + "_UPDATE_MAILCONTENT", ""));
                        if ("EMAIL".equals(row.channel) && (HthContactNotificationPlan.text(log.getZhMailSubj()).isEmpty()
                                || HthContactNotificationPlan.text(log.getZhMailContent()).isEmpty()))
                            throw new IllegalStateException("BCO contact mail configuration missing");
                        log.setNotificationDetails(new NotificationDetail[] {detail});
                        TransactionStatus status = super.registerActivityAndGenerateEvent(context, HthContactNotificationPlan.activity(row.event()),
                                row.event(), new Date(), log);
                        if (status == null || status.getErrorCode() != null)
                            throw new IllegalStateException("Contact event registration failed");
                    } finally { Interaction.close(); }
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
