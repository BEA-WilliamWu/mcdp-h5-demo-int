package com.ofss.digx.cz.bea.app.sms.dto.user;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/** 851 recipient policy. Event names identify distinct existing BCO message meanings. */
public final class HthContactNotificationPlan {
    public static final String ACTIVITY = "com.ofss.digx.cz.bea.app.sms.service.user.UserExtensionData.update";
    public static final String EMAIL = "INFO_UPDATE_BY_CORP_ADMIN";
    public static final String EMAIL_SMS = "USER_EMAIL_ADDRESS_UPDATE";
    public static final String MOBILE_SMS = "USER_MOBILE_NUMBER_UPDATED_REMINDER";
    public static final String NEW_MOBILE_SMS = "CORPORATEPLUS_WELCOME_MAIL";
    private HthContactNotificationPlan() { }
    public static String text(String value) { return value == null ? "" : value.trim(); }
    public static String email(String value) { return text(value).toLowerCase(Locale.ROOT); }
    public static String mobile(String country, String number) {
        String prefix = text(country).replace("+", "");
        String local = text(number).replaceAll("[\\s()-]", "");
        if (!prefix.matches("[0-9]{1,4}") || !local.matches("[0-9]{4,14}")) return "";
        return prefix + local;
    }
    public static String change(String oldEmail, String newEmail, String oldMobile, String newMobile) {
        boolean e = !email(oldEmail).equals(email(newEmail));
        boolean m = !text(oldMobile).equals(text(newMobile));
        return e ? (m ? "BOTH" : "EMAIL") : (m ? "MOBILE" : "NONE");
    }
    public static String id(String... parts) {
        StringBuilder key = new StringBuilder();
        for (String part : parts) key.append(text(part).length()).append(':').append(text(part));
        return UUID.nameUUIDFromBytes(key.toString().getBytes(StandardCharsets.UTF_8)).toString().replace("-", "");
    }
    public static String deliveryId(String unit, String reference, String user, String change,
            String event, String channel, String address) {
        return id(unit, reference, user, change, event, channel, address);
    }
    public static boolean isEvent(String event) {
        return EMAIL.equals(event) || EMAIL_SMS.equals(event) || MOBILE_SMS.equals(event) || NEW_MOBILE_SMS.equals(event);
    }
    public static String activity(String event) {
        return NEW_MOBILE_SMS.equals(event) ? ACTIVITY.replace(".update", ".create") : ACTIVITY;
    }
    public static List<Recipient> recipients(String change, String user, String approver,
            String oldEmail, String newEmail, String oldMobile, String newMobile,
            String approverEmail, String approverMobile, String companyEmail) {
        Map<String, Recipient> recipients = new LinkedHashMap<String, Recipient>();
        if ("NONE".equals(change)) return new ArrayList<Recipient>();
        add(recipients, "API", "EMAIL", oldEmail, user, EMAIL);
        add(recipients, "API", "EMAIL", newEmail, user, EMAIL);
        add(recipients, "AP", "EMAIL", approverEmail, approver, EMAIL);
        add(recipients, "COMPANY", "EMAIL", companyEmail, user, EMAIL);
        if (!"EMAIL".equals(change)) {
            add(recipients, "API", "SMS", oldMobile, user, MOBILE_SMS);
            add(recipients, "API", "SMS", newMobile, user, NEW_MOBILE_SMS);
            // AP receives the BCO update reminder, never the welcome-to-new-number SMS.
            add(recipients, "AP", "SMS", approverMobile, approver, MOBILE_SMS);
        }
        if (!"MOBILE".equals(change)) {
            add(recipients, "API", "SMS", newMobile, user, EMAIL_SMS);
            add(recipients, "AP", "SMS", approverMobile, approver, EMAIL_SMS);
        }
        return new ArrayList<Recipient>(recipients.values());
    }
    private static void add(Map<String, Recipient> rows, String role, String channel, String address,
            String user, String event) {
        address = "EMAIL".equals(channel) ? email(address) : text(address);
        if (address.isEmpty()) return;
        String key = id(event, channel, address);
        Recipient prior = rows.get(key);
        if (prior == null) rows.put(key, new Recipient(role, channel, address, user, event));
        else if (!prior.hasRole(role)) prior.role += "," + role;
    }
    public static boolean hasRole(String roles, String role) {
        return ("," + text(roles) + ",").contains("," + role + ",");
    }
    public static final class Recipient {
        public String role;
        public final String channel, address, user, event;
        private Recipient(String role, String channel, String address, String user, String event) {
            this.role = role; this.channel = channel; this.address = address; this.user = user; this.event = event;
        }
        public boolean hasRole(String value) { return HthContactNotificationPlan.hasRole(role, value); }
    }
}
