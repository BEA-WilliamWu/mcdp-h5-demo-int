package com.ofss.digx.cz.bea.hth.crm.batch;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.Writer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.HashSet;
import java.util.Set;

/** HTH-only daily CRM extract. Does not use or change BCO batch markers. */
public final class HthCrmExtractJob {
  private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Hong_Kong");
  private static final DateTimeFormatter DATE = DateTimeFormatter.BASIC_ISO_DATE;
  private static final String TABLE = "DIGX_CZ_CRM_EVENT3_DETAILS";
  static final Field[] FIELDS = {
      new Field("record_type", "'50'"),
      new Field("Filler_01", "'1'"),
      new Field("Event_Id", "EVENT_ID"),
      new Field("Event_Dte", "EVENT_DTE"),
      new Field("Event_Time", "SUBSTR(EVENT_TIME,1,2)||':'||SUBSTR(EVENT_TIME,3,2)||':'||SUBSTR(EVENT_TIME,5,2)"),
      new Field("Chnl_Id", "CHNL_ID"),
      new Field("Chnl_Type_Code", "CHNL_TYPE_CODE"),
      new Field("Event_Status_Code", "EVENT_STATUS_CODE"),
      new Field("Cr_Dr_Ind", "CR_DR_IND"),
      new Field("Fee_Chrg_Code", "FEE_CHRG_CODE"),
      new Field("Event_Actv_Type_Code", "EVENT_ACTV_TYPE_CODE"),
      new Field("Fin_Ind", "FIN_IND"),
      new Field("Self_Srv_Ind", "SELF_SRV_IND"),
      new Field("User_Id", "USER_ID"),
      new Field("Event_Country_Code", "EVENT_COUNTRY_CODE"),
      new Field("Acct_Nbr", "ACCT_NBR"),
      new Field("Phone_Nbr", "PHONE_NBR"),
      new Field("Phone_Nbr_Acct_Nbr", "PHONE_NBR_ACCT_NBR"),
      new Field("Phone_Nbr_Req_Result", "PHONE_NBR_REQ_RESULT"),
      new Field("Phone_Default_Ind", "PHONE_DEFAULT_IND"),
      new Field("Elect_Add", "ELECT_ADD"),
      new Field("Elect_Add_Acct_Nbr", "ELECT_ADD_ACCT_NBR"),
      new Field("Elect_Add_Req_Result", "ELECT_ADD_REQ_RESULT"),
      new Field("Elect_Add_Default_Ind", "ELECT_ADD_DEFAULT_IND"),
      new Field("FPS_ID", "FPS_ID"),
      new Field("FPS_Acct_Nbr", "FPS_ACCT_NBR"),
      new Field("FPS_Req_Result", "FPS_REQ_RESULT"),
      new Field("Debit_Acct_Nbr", "REGEXP_SUBSTR(DEBIT_ACCT_NBR,'[^~]+',1,1)"),
      new Field("Event_Ccy_Code", "EVENT_CCY_CODE"),
      new Field("Event_Amt", "EVENT_AMT"),
      new Field("Fee_Chrg_Amt", "FEE_CHRG_AMT"),
      new Field("Fee_Ccy_Code", "FEE_CCY_CODE"),
      new Field("Trf_Dte", "TRF_DTE"),
      new Field("Trf_Freq", "TRF_FREQ"),
      new Field("Proxy_ID_Type", "PROXY_ID_TYPE"),
      new Field("Proxy_ID", "PROXY_ID"),
      new Field("Payee_Name", "PAYEE_NAME"),
      new Field("Payee_Bank_Code", "PAYEE_BANK_CODE"),
      new Field("Event_Rem", "EVENT_REM"),
      new Field("Ref_Nbr", "REF_NBR"),
      new Field("From_Dte", "FROM_DTE"),
      new Field("To_Dte", "TO_DTE"),
      new Field("Mandate_Id", "MANDATE_ID"),
      new Field("eDDA_Maint_Action", "EDDA_MAINT_ACTION"),
      new Field("Source_Trx_Ref_Nbr", "SOURCE_TRX_REF_NBR"),
      new Field("Pay_Cat_Purp_Code", "PAY_CAT_PURP_CODE"),
      new Field("Pay_Purp_Code", "PAY_PURP_CODE"),
      new Field("Device_Id", "DEVICE_ID"),
      new Field("Mobile_Brand", "MOBILE_BRAND"),
      new Field("Platform_Code", "PLATFORM_CODE"),
      new Field("Device_Model", "DEVICE_MODEL"),
      new Field("Device_OS_Version", "DEVICE_OS_VERSION"),
      new Field("IP_Address", "IP_ADDRESS"),
      new Field("Event_Amt_Hke", "EVENT_AMT_HKE"),
      new Field("Event_Ex_Rate", "EVENT_EX_RATE"),
      new Field("Mrch_Id", "MRCH_ID"),
      new Field("Acct_Ccy_Code", "ACCT_CCY_CODE"),
      new Field("Acct_Amt", "ACCT_AMT"),
      new Field("Screen_Id", "SCREEN_ID"),
      new Field("OMB_txn", "OMB_FLAG"),
      new Field("Multi_App_Rej_Txn_Cnt", "MULTI_APP_REJ_TXN_CNT"),
      new Field("Coupon_Code", "COUPON_CODE"),
      new Field("TT_Auto_Route", "TT_AUTO_ROUTE"),
      new Field("Suspicious_Activity", "SUSPICIOUS_ACTIVITY"),
      new Field("Country_Name", "COUNTRY_NAME"),
      new Field("Region", "REGION"),
      new Field("Suspicious_Ind", "SUSPICIOUS_IND"),
      new Field("Sweep_instruction_type", "LM_SWEEP_INSTRUCTION_TYPE"),
      new Field("Frequency_execution_day", "LM_FREQUENCY_EXECUTIONDAY"),
      new Field("Effective_date", "LM_EFFECTIVEDATE"),
      new Field("rule_setup_date", "LM_RULE_SETUPDATE"),
      new Field("sweeping_amount_threshold", "LM_SWEEPING_AMOUNT_THRESHOLD"),
      new Field("fpxTxnNbr", "LM_FPXTXN_NBR"),
      new Field("instructionNbr", "LM_INSTRUCTION_NBR"),
      new Field("FPS_Ref_Nbr", "FPS_REF_NBR"),
      new Field("Party_Int_Nbr", "PARTY_INT_NBR"),
      new Field("Event_Credit_Acct_Type", "EVENT_CREDIT_ACCT_TYPE"),
      new Field("Event_Credit_Acct_Nbr", "EVENT_CREDIT_ACCT_NBR"),
      new Field("FPS_Bus_Service_Cd", "FPS_BUS_SERVICE_CD"),
      new Field("Event_Remitter_Name", "EVENT_REMITTER_NAME"),
      new Field("Filler_02", "'  '"),
      new Field("Adv_Freq_Type", "ADV_FREQ_TYPE"),
      new Field("Adv_Type", "ADV_TYPE"),
      new Field("Ar_Token", "AR_TOKEN"),
      new Field("Treasury_Ref", "TREASURY_REF")
  };
  static final String SQL = buildSql();

  private HthCrmExtractJob() { }

  static final class Field {
    final String header;
    final String expression;
    Field(String header, String expression) {
      this.header = header;
      this.expression = expression;
    }
  }

  private static String buildSql() {
    StringBuilder sql = new StringBuilder("SELECT ");
    for (int i = 0; i < FIELDS.length; i++) {
      if (i > 0) sql.append(',');
      sql.append(FIELDS[i].expression);
    }
    sql.append(" FROM ").append(TABLE);
    sql.append(" WHERE EVENT_DTE = ? AND CHNL_ID = 'ELE-HTH'");
    sql.append(" AND EVENT_ACTV_TYPE_CODE IN ('HTH_PWD_CRD','HTH_PWD_UPD')");
    sql.append(" ORDER BY EVENT_ID");
    return sql.toString();
  }

  static LocalDate businessDate(String[] args) {
    LocalDate date = LocalDate.now(BUSINESS_ZONE).minusDays(1);
    for (int i = 0; i < args.length; i++) {
      if ("--date".equals(args[i])) {
        if (++i == args.length) throw new IllegalArgumentException("--date requires yyyyMMdd");
        try {
          date = LocalDate.parse(args[i], DATE);
        } catch (DateTimeParseException e) {
          throw new IllegalArgumentException("Invalid --date; expected yyyyMMdd", e);
        }
      } else if ("--help".equals(args[i])) {
        System.out.println("Usage: HthCrmExtractJob [--date yyyyMMdd]");
        System.exit(0);
      } else {
        throw new IllegalArgumentException("Unknown argument: " + args[i]);
      }
    }
    return date;
  }

  static String csvCell(String value) {
    if (value == null) value = "";
    return '"' + value.replace("\"", "\"\"") + '"';
  }

  static int writeCsv(ResultSet rows, Writer writer) throws Exception {
    for (int i = 0; i < FIELDS.length; i++) {
      if (i > 0) writer.write(',');
      writer.write(csvCell(FIELDS[i].header));
    }
    writer.write('\n');
    Set<String> eventIds = new HashSet<String>();
    int count = 0;
    while (rows.next()) {
      String eventId = rows.getString(3);
      if (eventId == null || eventId.isEmpty() || !eventIds.add(eventId)) {
        throw new IllegalStateException("Missing or duplicate EVENT_ID in HTH CRM extract");
      }
      for (int i = 0; i < FIELDS.length; i++) {
        if (i > 0) writer.write(',');
        writer.write(csvCell(rows.getString(i + 1)));
      }
      writer.write('\n');
      count++;
    }
    return count;
  }

  private static String required(String name) {
    String value = System.getenv(name);
    if (value == null || value.trim().isEmpty()) {
      throw new IllegalArgumentException("Missing environment variable " + name);
    }
    return value;
  }

  private static String fileName(LocalDate date) {
    String pattern = System.getenv("HTH_CRM_FILE_PATTERN");
    if (pattern == null || pattern.isEmpty()) pattern = "HTH_CRM_{date}.csv";
    if (!pattern.contains("{date}")) {
      throw new IllegalArgumentException("HTH_CRM_FILE_PATTERN must contain {date}");
    }
    String name = pattern.replace("{date}", DATE.format(date));
    if (!name.matches("[A-Za-z0-9_.-]+\\.csv") || name.startsWith(".")) {
      throw new IllegalArgumentException("Invalid HTH_CRM_FILE_PATTERN");
    }
    return name;
  }

  public static void main(String[] args) {
    String start = java.time.ZonedDateTime.now(BUSINESS_ZONE).toString();
    String filename = "";
    int count = 0;
    Path temporary = null;
    boolean failed = false;
    try {
      LocalDate date = businessDate(args);
      filename = fileName(date);
      Path directory = Paths.get(required("HTH_CRM_OUTPUT_DIR"));
      Files.createDirectories(directory);
      directory = directory.toRealPath();
      Path output = directory.resolve(filename);
      Path lockPath = directory.resolve(filename + ".lock");
      System.out.println("HTH_CRM_1293 stage=START start=" + start + " date=" + DATE.format(date) + " file=" + output);
      try (FileChannel lockChannel = FileChannel.open(lockPath, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
           FileLock lock = lockChannel.tryLock()) {
        if (lock == null) throw new IllegalStateException("Extract already running for this date");
        temporary = Files.createTempFile(directory, filename + ".", ".tmp");
        try (Connection connection = DriverManager.getConnection(
                 required("HTH_CRM_JDBC_URL"), required("HTH_CRM_JDBC_USER"), required("HTH_CRM_JDBC_PASSWORD"));
             PreparedStatement statement = connection.prepareStatement(SQL)) {
          connection.setReadOnly(true);
          statement.setString(1, DATE.format(date));
          statement.setFetchSize(500);
          try (ResultSet rows = statement.executeQuery();
               BufferedWriter writer = Files.newBufferedWriter(temporary, java.nio.charset.StandardCharsets.UTF_8)) {
            count = writeCsv(rows, writer);
          }
        }
        try {
          Files.move(temporary, output, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException e) {
          throw new IOException("Atomic publication is required for the extract directory", e);
        }
        temporary = null;
      }
      System.out.println("HTH_CRM_1293 stage=END status=SUCCESS start=" + start
          + " end=" + java.time.ZonedDateTime.now(BUSINESS_ZONE)
          + " count=" + count + " file=" + filename);
    } catch (Exception e) {
      failed = true;
      System.err.println("HTH_CRM_1293 stage=END status=FAILED start=" + start
          + " end=" + java.time.ZonedDateTime.now(BUSINESS_ZONE)
          + " count=" + count + " file=" + filename
          + " errorType=" + e.getClass().getSimpleName() + " error=" + e.getMessage());
    } finally {
      if (temporary != null) {
        try { Files.deleteIfExists(temporary); } catch (IOException ignored) { }
      }
    }
    if (failed) System.exit(1);
  }
}
