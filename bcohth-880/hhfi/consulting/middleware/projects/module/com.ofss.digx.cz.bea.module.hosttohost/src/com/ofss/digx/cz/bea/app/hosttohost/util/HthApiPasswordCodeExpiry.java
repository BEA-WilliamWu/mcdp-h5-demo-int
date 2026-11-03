package com.ofss.digx.cz.bea.app.hosttohost.util;

import java.time.Instant;
import java.time.ZoneId;

/** BCOH2H-880: approved Codes expire at 23:59:59 on the next Hong Kong calendar day. */
public final class HthApiPasswordCodeExpiry {
    private static final ZoneId HONG_KONG = ZoneId.of("Asia/Hong_Kong");
    private HthApiPasswordCodeExpiry() { }

    public static long nextDayEnd(long approvedAtMillis) {
        return Instant.ofEpochMilli(approvedAtMillis).atZone(HONG_KONG).toLocalDate()
                .plusDays(2).atStartOfDay(HONG_KONG).minusSeconds(1).toInstant().toEpochMilli();
    }
}
