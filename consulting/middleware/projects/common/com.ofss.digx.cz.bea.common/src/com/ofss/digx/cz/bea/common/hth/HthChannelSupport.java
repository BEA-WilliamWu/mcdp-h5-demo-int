package com.ofss.digx.cz.bea.common.hth;

/** Pure channel/service checks. No HTH module, configuration, Adapter or database access. */
public final class HthChannelSupport {
    private HthChannelSupport() { }
    public static boolean isHthChannel(Object channel) {
        return "HTH".equalsIgnoreCase(String.valueOf(channel)) || "H2H".equalsIgnoreCase(String.valueOf(channel));
    }
    public static boolean isHthChange(Object oldChannel, Object newChannel) {
        return isHthChannel(oldChannel) || isHthChannel(newChannel);
    }
    public static boolean isHthApproval(String service, Object userChannel) {
        if (service == null) return false;
        String user = "com.ofss.digx.cz.bea.app.sms.service.user.UserExtensionData.";
        if (service.equals(user + "create") || service.equals(user + "update")) return isHthChannel(userChannel);
        String hth = "com.ofss.digx.cz.bea.app.hosttohost.service.";
        return service.equals(hth + "HostToHostManagement.submit")
            || service.equals(hth + "HostToHostManagement.edit")
            || service.equals(hth + "HostToHostManagement.disable")
            || service.equals(hth + "HostToHostUserAccess.submit")
            || service.equals(hth + "HostToHostUserAccess.edit")
            || service.equals(hth + "HostToHostUserAccess.delete");
    }
}
