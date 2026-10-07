package com.ofss.digx.cz.bea.hth.crm.batch;

import java.io.StringWriter;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.ResultSet;
import java.time.LocalDate;

/** Dependency-free tests for 1293's date selection, mapping and CSV integrity. */
public final class HthCrmExtractJobTest {
  private static void check(boolean value, String message) {
    if (!value) throw new AssertionError(message);
  }

  private static ResultSet rows(final String[][] data) {
    InvocationHandler handler = new InvocationHandler() {
      int row = -1;
      public Object invoke(Object proxy, Method method, Object[] args) {
        if ("next".equals(method.getName())) return ++row < data.length;
        if ("getString".equals(method.getName())) return data[row][((Integer) args[0]) - 1];
        throw new UnsupportedOperationException(method.getName());
      }
    };
    return (ResultSet) Proxy.newProxyInstance(ResultSet.class.getClassLoader(),
        new Class<?>[] {ResultSet.class}, handler);
  }

  private static String[] record(String eventId, String activity) {
    String[] record = new String[HthCrmExtractJob.FIELDS.length];
    record[0] = "50";
    record[2] = eventId;
    record[10] = activity;
    record[20] = "A,\"B\"";
    return record;
  }

  public static void main(String[] args) throws Exception {
    check(HthCrmExtractJob.FIELDS.length == 85, "field count differs from mapping rows 11-95");
    check("Filler_02".equals(HthCrmExtractJob.FIELDS[80].header), "Filler_02 order");
    check("Treasury_Ref".equals(HthCrmExtractJob.FIELDS[84].header), "last field order");
    check(HthCrmExtractJob.SQL.contains("CHNL_ID = 'ELE-HTH'"), "HTH-only predicate missing");
    check(HthCrmExtractJob.SQL.contains("EVENT_DTE = ?"), "date predicate missing");
    check(HthCrmExtractJob.SQL.contains("'HTH_PWD_CRD','HTH_PWD_UPD'"), "activities missing");
    check(!HthCrmExtractJob.SQL.contains("BATCH_PROCESSED_DATE"), "must not depend on BCO marker");
    check(LocalDate.of(2026, 10, 7).equals(HthCrmExtractJob.businessDate(new String[] {"--date", "20261007"})), "rerun date");
    boolean invalidDate = false;
    try { HthCrmExtractJob.businessDate(new String[] {"--date", "20260230"}); }
    catch (IllegalArgumentException expected) { invalidDate = true; }
    check(invalidDate, "invalid dates must fail");

    StringWriter empty = new StringWriter();
    check(HthCrmExtractJob.writeCsv(rows(new String[0][]), empty) == 0, "empty count");
    check(empty.toString().split("\\n").length == 1, "empty must have only header");

    StringWriter filled = new StringWriter();
    int count = HthCrmExtractJob.writeCsv(rows(new String[][] {
        record("CDC1", "HTH_PWD_CRD"), record("CDC2", "HTH_PWD_UPD")}), filled);
    check(count == 2, "record count");
    check(filled.toString().split("\\n").length == 3, "header + 2 records");
    check(filled.toString().contains("\"A,\"\"B\"\"\""), "CSV quoting");
    check(filled.toString().contains("\"HTH_PWD_UPD\""), "activity must not truncate");

    boolean duplicate = false;
    try {
      HthCrmExtractJob.writeCsv(rows(new String[][] {
          record("CDC1", "HTH_PWD_CRD"), record("CDC1", "HTH_PWD_CRD")}), new StringWriter());
    } catch (IllegalStateException expected) { duplicate = true; }
    check(duplicate, "duplicate EVENT_ID must fail safely");
    System.out.println("HTH CRM extract tests passed");
  }
}
