package com.ofss.digx.cz.bea.domain.service.dispatch;

import com.ofss.digx.app.AbstractApplication;
import com.ofss.digx.cz.bea.app.hosttohost.dto.HthUserAccessActivityLogDTO;
import com.ofss.digx.cz.bea.app.hosttohost.dto.HthUserAccessNotificationPlan;
import com.ofss.fc.app.context.SessionContext;
import com.ofss.fc.datatype.Date;
import com.ofss.fc.enumeration.ep.DestinationType;
import com.ofss.fc.enumeration.ep.SubscriberType;
import com.ofss.fc.infra.config.ConfigurationFactory;
import com.ofss.fc.xface.ep.dto.NotificationDetail;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Publishes only committed 1216 intents; shares the proven 851 delivery state machine. */
public final class HthUserAccessNotificationService extends AbstractApplication {
    private static final Logger LOG = Logger.getLogger(HthUserAccessNotificationService.class.getName());
    static boolean enabled() {
        return ConfigurationFactory.getInstance().getConfigurations(HthUserAccessNotificationPlan.CONFIG)
                .getBoolean(HthUserAccessNotificationPlan.ENABLED, false);
    }
    public void process(SessionContext context) {
        if (!enabled() || !"OBDX_BU".equals(context.getTargetUnit())) return;
        String locale = context.getUserLocale(), party = context.getTransactingPartyCode();
        try {
            HthContactNotificationRepository repository = HthContactNotificationRepository.configured(true);
            repository.reconcile(context.getTargetUnit());
            for (HthContactNotificationRepository.Row row : repository.pending(context.getTargetUnit())) {
                try {
                    if (!repository.claimPublication(row.id)) continue;
                    context.setTransactingPartyCode(row.party);
                    context.setUserLocale(HthContactNotificationService.locale(row.locale));
                    NotificationDetail detail = new NotificationDetail();
                    detail.setRecipientId(row.party);
                    detail.setRecipientType(SubscriberType.EXTERNAL.toString());
                    detail.setDestination("EMAIL".equals(row.channel) ? DestinationType.EMAIL : DestinationType.SMS);
                    detail.setDispatchAddress(row.address);
                    HthUserAccessActivityLogDTO log = new HthUserAccessActivityLogDTO();
                    log.setCustomerId(row.party);
                    log.setHthAccessNotificationId(row.id);
                    log.setUserNameId(escape(row.user.substring(0, row.user.lastIndexOf('@'))));
                    log.setUserSysDate(escape(row.approvedAt));
                    log.setCompName(escape(row.companyName));
                    log.setNotificationDetails(new NotificationDetail[] {detail});
                    super.registerActivityAndGenerateEvent(context, row.activity(), row.event(), new Date(), log);
                    repository.published(row.id);
                } catch (java.lang.Exception e) {
                    LOG.log(Level.WARNING, "HTH_ACCESS notification={0} stage=PUBLICATION exception={1}",
                            new Object[] {row.id, e.getClass().getSimpleName()});
                }
            }
        } catch (java.lang.Exception e) {
            LOG.log(Level.SEVERE, "HTH_ACCESS stage=BATCH exception={0}", e.getClass().getSimpleName());
        } finally {
            context.setUserLocale(locale); context.setTransactingPartyCode(party);
        }
    }
    private static String escape(String value) {
        return value == null ? "" : value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&#39;");
    }
}
