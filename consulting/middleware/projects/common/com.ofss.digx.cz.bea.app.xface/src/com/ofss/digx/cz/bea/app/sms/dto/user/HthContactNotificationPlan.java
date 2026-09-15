package com.ofss.digx.cz.bea.app.sms.dto.user;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/** Pure recipient policy for BCOH2H-851. API and approver emails have different semantics. */
public final class HthContactNotificationPlan {
    private HthContactNotificationPlan() { }
    public static final String ACTIVITY =
        "com.ofss.digx.cz.bea.app.sms.service.user.UserExtensionData.update";

    public static String text(String value) { return value == null ? "" : value.trim(); }
    public static String email(String value) {
        String address = text(value);
        int at = address.lastIndexOf('@');
        return at < 0 ? address : address.substring(0, at) + address.substring(at).toLowerCase(Locale.ROOT);
    }
    public static String mobile(String country, String number) {
        String code = text(country).replaceFirst("^\\+", "");
        String local = text(number).replaceAll("[\\s()-]", "");
        if (local.isEmpty()) return "";
        // CM persists country and local number separately. Missing country is not guessed.
        if (!code.matches("[0-9]{1,4}") || !local.matches("[0-9]{4,15}")) return "";
        return code + local;
    }
    public static String change(String oldEmail, String newEmail, String oldMobile, String newMobile) {
        boolean e = !email(oldEmail).equals(email(newEmail));
        boolean m = !text(oldMobile).equals(text(newMobile));
        return e ? (m ? "BOTH" : "EMAIL") : (m ? "MOBILE" : "NONE");
    }
    public static String event(String change, String role) {
        String kind = "BOTH".equals(change) ? "CONTACT" : change;
        if (!"CONTACT".equals(kind) && !"EMAIL".equals(kind) && !"MOBILE".equals(kind))
            throw new IllegalArgumentException("Invalid contact change type");
        return "HTH_PROFILE_" + kind + "_UPDATED_" + ("AP".equals(role) ? "AP" : "API");
    }
    public static boolean isEvent(String event) {
        for (String change : new String[] {"BOTH", "EMAIL", "MOBILE"})
            for (String role : new String[] {"API", "AP"})
                if (event(change, role).equals(event)) return true;
        return false;
    }
    public static String id(String... parts) {
        StringBuilder key = new StringBuilder("851:");
        for (String part : parts) key.append(text(part).length()).append(':').append(text(part));
        return UUID.nameUUIDFromBytes(key.toString().getBytes(StandardCharsets.UTF_8)).toString().replace("-", "");
    }
    public static List<Recipient> recipients(String change, String user, String approver,
            String oldEmail, String newEmail, String oldMobile, String newMobile,
            String approverEmail, String approverMobile) {
        Map<String, Recipient> rows = new LinkedHashMap<String, Recipient>();
        if ("NONE".equals(change)) return new ArrayList<Recipient>();
        add(rows, "API", "EMAIL", email(oldEmail), user);
        add(rows, "API", "EMAIL", email(newEmail), user);
        add(rows, "API", "SMS", oldMobile, user);
        add(rows, "API", "SMS", newMobile, user);
        add(rows, "AP", "EMAIL", email(approverEmail), approver);
        // Matrix #7/#8/#9 SMS wording is identical for API/AP: one SMS per number.
        add(rows, "AP", "SMS", approverMobile, approver);
        return new ArrayList<Recipient>(rows.values());
    }
    private static void add(Map<String, Recipient> rows, String role, String channel, String address, String user) {
        address = text(address);
        if (address.isEmpty()) return;
        String semantic = "SMS".equals(channel) ? "SHARED" : role;
        String key = id(semantic, channel, address);
        if (!rows.containsKey(key)) rows.put(key, new Recipient(role, channel, address, user));
    }
    public static final class Recipient {
        public final String role, channel, address, user;
        Recipient(String role, String channel, String address, String user) {
            this.role = role; this.channel = channel; this.address = address; this.user = user;
        }
    }
}
