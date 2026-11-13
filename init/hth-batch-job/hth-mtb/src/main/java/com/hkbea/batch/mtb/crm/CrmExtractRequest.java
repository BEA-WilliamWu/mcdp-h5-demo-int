package com.hkbea.batch.mtb.crm;

import java.nio.file.Path;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

public record CrmExtractRequest(LocalDate day, Path directory, String filePattern) {
    public static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Hong_Kong");
    public static final String DEFAULT_PATTERN = "hthtomtb_mvp1apis_D{date}.csv";
    private static final DateTimeFormatter FILE_DATE = DateTimeFormatter.ofPattern("yyMMdd", Locale.ROOT);

    public CrmExtractRequest {
        if (day == null || day.getYear() < 1 || day.getYear() > 9999) {
            throw new IllegalArgumentException("CRM date must be yyyyMMdd");
        }
        directory = directory.toAbsolutePath().normalize();
        if (filePattern == null || filePattern.isEmpty()) filePattern = DEFAULT_PATTERN;
        if (!filePattern.contains("{date}")) {
            throw new IllegalArgumentException("crm.file.pattern must contain {date}");
        }
        String name = filePattern.replace("{date}", FILE_DATE.format(day));
        if (!name.matches("[A-Za-z0-9_.-]+\\.csv") || name.startsWith(".")) {
            throw new IllegalArgumentException("Invalid crm.file.pattern");
        }
    }

    public String fileName() {
        return filePattern.replace("{date}", FILE_DATE.format(day));
    }

    public static LocalDate parseDay(String value, Clock clock) {
        if (value == null) return LocalDate.now(clock.withZone(BUSINESS_ZONE)).minusDays(1);
        if (!value.matches("[0-9]{8}") || value.startsWith("0000")) {
            throw new IllegalArgumentException("CRM date must be yyyyMMdd");
        }
        return LocalDate.parse(value, DateTimeFormatter.BASIC_ISO_DATE);
    }
}
