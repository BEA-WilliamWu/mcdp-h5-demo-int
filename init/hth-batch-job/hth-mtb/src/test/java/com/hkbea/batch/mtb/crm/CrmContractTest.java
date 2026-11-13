package com.hkbea.batch.mtb.crm;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Collections;
import org.junit.jupiter.api.Test;

class CrmContractTest {
    @Test void defaultDayUsesHongKongBusinessDateNotJvmZone() {
        Clock clock = Clock.fixed(Instant.parse("2026-10-07T17:00:00Z"), ZoneOffset.UTC);
        assertEquals(LocalDate.of(2026, 10, 7), CrmExtractRequest.parseDay(null, clock));
        assertEquals(LocalDate.of(2025, 1, 6), CrmExtractRequest.parseDay("20250106", clock));
        for (String bad : new String[] {"20250230", "2026107", "20261007Z", "00000101", "20261007\n"}) {
            assertThrows(RuntimeException.class, () -> CrmExtractRequest.parseDay(bad, clock));
        }
    }

    @Test void filenameUsesSixDigitDateAndCannotEscapeOutputDirectory() {
        var day = LocalDate.of(2025, 1, 6);
        assertEquals("hthtomtb_mvp1apis_D250106.csv", new CrmExtractRequest(day, Path.of("."), null).fileName());
        assertEquals("HTH_CRM_250106.csv", new CrmExtractRequest(day, Path.of("."), "HTH_CRM_{date}.csv").fileName());
        for (String pattern : new String[] {"../{date}.csv", "/{date}.csv", ".{date}.csv", "fixed.csv", "{date}.dat"}) {
            assertThrows(IllegalArgumentException.class, () -> new CrmExtractRequest(day, Path.of("."), pattern));
        }
    }

    @Test void fieldsAndNumericTrailerBoundsAreEnforced() {
        assertThrows(IllegalStateException.class, () -> new CrmEvent(null, Collections.nCopies(61, null)));
        assertThrows(IllegalStateException.class, () -> new CrmEvent("id", Collections.nCopies(62, null)));
        assertEquals("000002147483648", CrmCsvWriter.number15(2147483648L));
        assertThrows(IllegalArgumentException.class, () -> CrmCsvWriter.number15(-1));
        assertThrows(IllegalArgumentException.class, () -> CrmCsvWriter.number15(1000000000000000L));
    }
}
