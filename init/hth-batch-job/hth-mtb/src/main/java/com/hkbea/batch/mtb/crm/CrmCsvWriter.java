package com.hkbea.batch.mtb.crm;

import java.io.IOException;
import java.io.Writer;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/** v16 control records plus the existing column-label row and 61 CSV fields. */
public final class CrmCsvWriter {
    private final Writer writer;
    private final String date;
    private long count;
    private boolean finished;

    public CrmCsvWriter(Writer writer, LocalDate day) throws IOException {
        this.writer = writer;
        this.date = DateTimeFormatter.BASIC_ISO_DATE.format(day);
        if (!date.matches("[0-9]{8}") || day.getYear() < 1) {
            throw new IllegalArgumentException("CRM date must be yyyyMMdd");
        }
        writer.write("00" + date + "HTHMTB" + " ".repeat(84) + '\n');
        writeFields(CrmFileLayout.COLUMNS);
    }

    public void append(CrmEvent event) throws IOException {
        if (finished) throw new IllegalStateException("CSV already finished");
        for (int i = 0; i < event.fields().size(); i++) {
            if (i != 0) writer.write(',');
            String value = event.fields().get(i);
            writer.write(quote(i == 4 ? eventTime(value) : value));
        }
        writer.write('\n');
        count = Math.incrementExact(count);
    }

    public long finish() throws IOException {
        if (finished) throw new IllegalStateException("CSV already finished");
        writer.write("99" + number15(count)
            + number15(Math.addExact(Long.parseLong(date), count)) + " ".repeat(68) + '\n');
        finished = true;
        return count;
    }

    private void writeFields(List<String> values) throws IOException {
        for (int i = 0; i < values.size(); i++) {
            if (i != 0) writer.write(',');
            writer.write(quote(values.get(i)));
        }
        writer.write('\n');
    }

    static String number15(long value) {
        if (value < 0 || value > 999999999999999L) {
            throw new IllegalArgumentException("HTH trailer value exceeds 15 digits");
        }
        return String.format(Locale.ROOT, "%015d", value);
    }

    private static String quote(String value) {
        if (value == null) value = "";
        return '"' + value.replace("\\", "\\\\").replace("\"", "\\\"")
            .replace("\r", "\\r").replace("\n", "\\n") + '"';
    }

    private static String eventTime(String value) {
        if (value == null || value.isEmpty()) return value;
        if (value.matches("[0-9]{6}")) {
            return value.substring(0, 2) + ':' + value.substring(2, 4) + ':' + value.substring(4, 6);
        }
        if (!value.matches("[0-9]{2}:[0-9]{2}:[0-9]{2}")) {
            throw new IllegalStateException("Invalid Event_Time format");
        }
        return value;
    }
}
