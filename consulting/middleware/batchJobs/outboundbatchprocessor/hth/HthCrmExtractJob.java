package outboundbatchprocessor.hth;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.charset.StandardCharsets;
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
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Properties;
import util.AESUtil;
import util.LoadProperties;

/** Independent HTH/DSP extract; never reads or updates BCO CRM tables or batch markers. */
public final class HthCrmExtractJob {
    private static final ZoneId HKT = ZoneId.of("Asia/Hong_Kong");
    private static final DateTimeFormatter DATE = DateTimeFormatter.BASIC_ISO_DATE;
    private static final String CONFIG_FILE = "hth_crm_batch_config.properties";
    // API_MTB_latest.xlsx / HTH_CRM_MAPPING_APIs, rows 12-72, in file order.
    static final String[] HEADERS = {
        "record_type", "Filler_01", "Event_Id", "Event_Dte", "Event_Time",
        "Chnl_Id", "Chnl_Type_Code", "Event_Status_Code", "Cr_Dr_Ind",
        "Fee_Chrg_Code", "Event_Actv_Type_Code", "Fin_Ind", "User_Id",
        "Event_Country_Code", "Acct_Nbr", "Elect_Add", "FPS_ID", "FPS_Acct_Nbr",
        "FPS_Req_Result", "Debit_Acct_Nbr", "Event_Ccy_Code", "Event_Amt",
        "Fee_Chrg_Amt", "Fee_Ccy_Code", "Trf_Dte", "Trf_Freq",
        "Proxy_ID_Type", "Proxy_ID", "Payee_Name", "Payee_Bank_Code",
        "Event_Rem", "Ref_Nbr", "From_Dte", "To_Dte", "Mandate_Id",
        "eDDA_Maint_Action", "Source_Trx_Ref_Nbr", "Pay_Cat_Purp_Code",
        "Pay_Purp_Code", "IP_Address", "Event_Amt_Hke", "Event_Ex_Rate",
        "Mrch_Id", "Acct_Ccy_Code", "Acct_Amt", "Suspicious_Activity",
        "Country_Name", "Region", "Suspicious_Ind", "Party_Int_Nbr",
        "Event_Credit_Acct_Type", "Event_Credit_Acct_Nbr", "FPS_Bus_Service_Cd",
        "Event_Remitter_Name", "Adv_Freq_Type", "Adv_Type", "API_URL",
        "Message_ID", "No_Financial_Transactions", "Transaction_Status", "Report_Type"
    };
    static final String SQL = buildSql();

    private HthCrmExtractJob() { }

    private static String buildSql() {
        StringBuilder sql = new StringBuilder("SELECT ID");
        for (int i = 0; i < HEADERS.length; i++) {
            if (i == 0) sql.append(",'50'");
            else if (i == 1) sql.append(",'1'");
            else sql.append(',').append(HEADERS[i].toUpperCase(java.util.Locale.ROOT));
        }
        return sql.append(" FROM HTH_BEA.HTH_CRM_EVENT")
            .append(" WHERE CREATION_DATE >= ? AND CREATION_DATE < ?")
            .append(" ORDER BY CREATION_DATE, ID").toString();
    }

    static LocalDate date(String[] args) {
        if (args.length == 0) return LocalDate.now(HKT).minusDays(1);
        if (args.length != 2 || !"--date".equals(args[0])) {
            throw new IllegalArgumentException("Usage: HthCrmExtractJob [--date yyyyMMdd]");
        }
        try {
            return LocalDate.parse(args[1], DATE);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("Invalid --date; expected yyyyMMdd", e);
        }
    }

    private static String required(String name) {
        String value = System.getenv(name);
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException("Missing environment variable " + name);
        }
        return value;
    }

    static final class DatabaseConfig {
        final String url;
        final String user;
        final String password;

        DatabaseConfig(String url, String user, String password) {
            this.url = url;
            this.user = user;
            this.password = password;
        }
    }

    private static String encryptedValue(Properties properties, String key, String passphrase)
            throws Exception {
        String encrypted = properties.getProperty(key);
        if (encrypted == null || encrypted.trim().isEmpty()) {
            throw new IllegalArgumentException("Missing HTH CRM configuration key " + key);
        }
        String value = AESUtil.decrypt(encrypted, passphrase);
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException("Empty HTH CRM configuration key " + key);
        }
        return value;
    }

    static DatabaseConfig loadDatabaseConfig(Path configDirectory) throws Exception {
        Properties properties = LoadProperties.getPropertiesFromFile(
            configDirectory.resolve(CONFIG_FILE).toString());
        String encryptedPassphrase = properties.getProperty("encr_passphrase");
        if (encryptedPassphrase == null || encryptedPassphrase.trim().isEmpty()) {
            throw new IllegalArgumentException("Missing HTH CRM configuration key encr_passphrase");
        }
        String passphrase = AESUtil.decryptdefault(encryptedPassphrase);
        if (passphrase == null || passphrase.trim().isEmpty()) {
            throw new IllegalArgumentException("Empty HTH CRM configuration key encr_passphrase");
        }
        String hostname = encryptedValue(properties, "db.hostname", passphrase);
        String port = encryptedValue(properties, "db.port", passphrase);
        String service = encryptedValue(properties, "db.servicename", passphrase);
        String user = encryptedValue(properties, "db.username", passphrase);
        String password = encryptedValue(properties, "db.password", passphrase);
        return new DatabaseConfig("jdbc:oracle:thin:@//" + hostname + ":" + port + "/" + service,
            user, password);
    }

    static String fileName(LocalDate date, String pattern) {
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

    private static String csv(String value) {
        if (value == null) value = "";
        // MTB Interface File Spec, CSV option: double-quoted cells and backslash escapes.
        return '"' + value.replace("\\", "\\\\").replace("\"", "\\\"")
            .replace("\r", "\\r").replace("\n", "\\n") + '"';
    }

    private static String outputValue(int index, String value) {
        if (index == 4 && value != null && !value.isEmpty()) {
            if (value.matches("[0-9]{6}")) {
                return value.substring(0, 2) + ':' + value.substring(2, 4)
                    + ':' + value.substring(4, 6);
            }
            if (!value.matches("[0-9]{2}:[0-9]{2}:[0-9]{2}")) {
                throw new IllegalStateException("Invalid Event_Time format");
            }
        }
        return value;
    }

    private static String failureReason(Exception e, String password) {
        String reason = e.getMessage();
        if (reason == null || reason.isEmpty()) reason = e.getClass().getSimpleName();
        if (password != null && !password.isEmpty()) reason = reason.replace(password, "[REDACTED]");
        reason = reason.replace('\r', ' ').replace('\n', ' ');
        if (reason.length() > 500) reason = reason.substring(0, 500);
        if (e instanceof SQLException) {
            SQLException sql = (SQLException) e;
            reason += " sqlState=" + sql.getSQLState() + " vendorCode=" + sql.getErrorCode();
        }
        return reason;
    }

    static int writeCsv(ResultSet rows, BufferedWriter writer) throws Exception {
        for (int i = 0; i < HEADERS.length; i++) {
            if (i != 0) writer.write(',');
            writer.write(csv(HEADERS[i]));
        }
        writer.write('\n');
        int count = 0;
        while (rows.next()) {
            String id = rows.getString(1);
            if (id == null || id.isEmpty()) {
                throw new IllegalStateException("Missing HTH_CRM_EVENT.ID");
            }
            for (int i = 0; i < HEADERS.length; i++) {
                if (i != 0) writer.write(',');
                writer.write(csv(outputValue(i, rows.getString(i + 2))));
            }
            writer.write('\n');
            count++;
        }
        return count;
    }

    static int run(LocalDate day, Path directory, String name, String jdbcUrl,
                   String jdbcUser, String jdbcPassword) throws Exception {
        Files.createDirectories(directory);
        directory = directory.toRealPath();
        Path output = directory.resolve(name);
        Path temp = null;
        try (FileChannel channel = FileChannel.open(directory.resolve(name + ".lock"),
                 StandardOpenOption.CREATE, StandardOpenOption.WRITE);
             FileLock lock = channel.tryLock()) {
            if (lock == null) throw new IllegalStateException("Extract already running for this date");
            temp = Files.createTempFile(directory, name + ".", ".tmp");
            int count;
            try (Connection connection = DriverManager.getConnection(jdbcUrl, jdbcUser, jdbcPassword)) {
                connection.setReadOnly(true);
                try (PreparedStatement query = connection.prepareStatement(SQL)) {
                    // Oracle DATE is timezone-free; HTH writes CREATION_DATE in HKT business time.
                    query.setTimestamp(1, Timestamp.valueOf(day.atStartOfDay()));
                    query.setTimestamp(2, Timestamp.valueOf(day.plusDays(1).atStartOfDay()));
                    query.setFetchSize(500);
                    try (ResultSet rows = query.executeQuery();
                         BufferedWriter writer = Files.newBufferedWriter(temp, StandardCharsets.UTF_8)) {
                        count = writeCsv(rows, writer);
                    }
                }
            }
            try {
                Files.move(temp, output, StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                throw new IOException("Atomic file replacement is required for the HTH output directory", e);
            }
            temp = null;
            return count;
        } finally {
            if (temp != null) Files.deleteIfExists(temp);
        }
    }

    public static void main(String[] args) {
        String started = java.time.ZonedDateTime.now(HKT).toString();
        String file = "";
        int count = 0;
        DatabaseConfig database = null;
        try {
            LocalDate day = date(args);
            file = fileName(day, System.getenv("HTH_CRM_FILE_PATTERN"));
            System.out.println("HTH_CRM_1293 stage=START time=" + started
                + " date=" + DATE.format(day) + " file=" + file);
            database = loadDatabaseConfig(Paths.get(required("batchConfigPath")));
            count = run(day, Paths.get(required("HTH_CRM_OUTPUT_DIR")), file,
                database.url, database.user, database.password);
            System.out.println("HTH_CRM_1293 stage=END status=SUCCESS start=" + started
                + " end=" + java.time.ZonedDateTime.now(HKT) + " count=" + count
                + " file=" + file);
        } catch (Exception e) {
            System.err.println("HTH_CRM_1293 stage=END status=FAILED start=" + started
                + " end=" + java.time.ZonedDateTime.now(HKT) + " count=" + count
                + " file=" + file + " errorType=" + e.getClass().getSimpleName()
                + " reason=" + failureReason(e, database == null ? null : database.password));
            System.exit(1);
        }
    }
}
