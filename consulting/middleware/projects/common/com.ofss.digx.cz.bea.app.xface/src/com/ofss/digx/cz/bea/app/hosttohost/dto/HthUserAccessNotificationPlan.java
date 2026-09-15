package com.ofss.digx.cz.bea.app.hosttohost.dto;

import com.ofss.digx.cz.bea.app.sms.dto.user.HthContactNotificationPlan;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/** Stable 1216 identities. No account numbers or permission details enter messages. */
public final class HthUserAccessNotificationPlan {
    private HthUserAccessNotificationPlan() { }
    public static final String CONFIG = "HthUserAccessNotification";
    public static final String ENABLED = "HTH_USER_ACCESS_NOTIFICATION_ENABLED";
    private static final String SERVICE = "com.ofss.digx.cz.bea.app.hosttohost.service.HostToHostUserAccess.";
    public static String event(String change) {
        if ("LINK".equals(change)) return "HTH_USER_ACCESS_LINKED";
        if ("UPDATE".equals(change)) return "HTH_USER_ACCESS_UPDATED";
        throw new IllegalArgumentException("Invalid HTH access notification change");
    }
    public static String activity(String change) {
        if ("LINK".equals(change)) return SERVICE + "submit";
        if ("UPDATE".equals(change)) return SERVICE + "edit";
        throw new IllegalArgumentException("Invalid HTH access notification activity");
    }
    public static boolean isEvent(String event) {
        return "HTH_USER_ACCESS_LINKED".equals(event) || "HTH_USER_ACCESS_UPDATED".equals(event);
    }
    public static String id(String... parts) {
        StringBuilder key = new StringBuilder("1216:");
        for (String part : parts) {
            String value = HthContactNotificationPlan.text(part);
            key.append(value.length()).append(':').append(value);
        }
        return UUID.nameUUIDFromBytes(key.toString().getBytes(StandardCharsets.UTF_8)).toString().replace("-", "");
    }
}
