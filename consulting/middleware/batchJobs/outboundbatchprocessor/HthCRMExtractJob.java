package outboundbatchprocessor;

import java.io.BufferedWriter;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.sql.Connection;
import java.sql.Date;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;
import util.AESUtil;
import util.LoadProperties;

/** BCOH2H-1293: independent, read-only HTH CRM daily CSV extract. */
public final class HthCRMExtractJob {
    private static final ZoneId HONG_KONG = ZoneId.of("Asia/Hong_Kong");
    private static final DateTimeFormatter FILE_DATE = DateTimeFormatter.BASIC_ISO_DATE;
    private static final String SOURCE_TABLE = "HTH_BEA.HTH_CRM_EVENT_DETAILS";
    private static final String RUN_TABLE = "HTH_BEA.HTH_CRM_EXTRACT_RUN";
    private static final Set<String> NEVER_EXPORT = new HashSet<String>(Arrays.asList(
            "AR_TOKEN", "TOKEN_ID", "PASSWORD", "PASSWORD_HASH", "CODE_CIPHER",
            "ENCRYPTED_CREDENTIALS", "REQUEST_BODY", "RESPONSE_BODY"));

    private HthCRMExtractJob() {
    }

    public static void main(String[] args) {
        String stage = "ARGUMENTS";
        try {
            Options options = Options.parse(args);
            stage = "CONFIG";
            Configuration config = Configuration.load(options.configPath);
            stage = "DB_CONNECT";
            try (Connection connection = openConnection()) {
                connection.setAutoCommit(true);
                stage = "RUN";
                new HthCRMExtractJobRunner(connection, config, options).run();
            }
        } catch (Exception failure) {
            // Log only the stage and exception type, never SQL values or credentials.
            System.err.println("HTH_CRM_EXTRACT stage=" + stage + " result=FAILED type="
                    + failure.getClass().getSimpleName() + " code=" + safeErrorCode(failure));
            System.exit(1);
        }
    }

    private static Connection openConnection() throws Exception {
        String configDir = System.getenv("batchConfigPath");
        if (configDir == null || configDir.trim().isEmpty()) {
            throw new IllegalStateException("batchConfigPath missing");
        }
        Properties encrypted = LoadProperties.getPropertiesFromFile(
                Paths.get(configDir, "batch_config.properties").toString());
        String passphrase = AESUtil.decryptdefault(required(encrypted, "encr_passphrase"));
        String username = AESUtil.decrypt(required(encrypted, "db.username"), passphrase);
        String password = AESUtil.decrypt(required(encrypted, "db.password"), passphrase);
        String hostname = AESUtil.decrypt(required(encrypted, "db.hostname"), passphrase);
        String port = AESUtil.decrypt(required(encrypted, "db.port"), passphrase);
        String service = AESUtil.decrypt(required(encrypted, "db.servicename"), passphrase);
        String url = "jdbc:oracle:thin:@//" + hostname + ":" + port + "/" + service;
        return DriverManager.getConnection(url, username, password);
    }

    private static String required(Properties properties, String name) {
        String value = properties.getProperty(name);
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException("Missing property: " + name);
        }
        return value.trim();
    }

    private static String safeErrorCode(Exception failure) {
        if (failure instanceof SQLException) {
            return "DB-" + ((SQLException) failure).getErrorCode();
        }
        return "NONE";
    }

    static String csv(String value) {
        if (value == null) {
            return "";
        }
        if (value.indexOf(',') >= 0 || value.indexOf('"') >= 0 || value.indexOf('\r') >= 0
                || value.indexOf('\n') >= 0) {
            return '"' + value.replace("\"", "\"\"") + '"';
        }
        return value;
    }

    static String formatValue(Object value) {
        if (value == null) {
            return "";
        }
        if (value instanceof BigDecimal) {
            return ((BigDecimal) value).toPlainString();
        }
        if (value instanceof Timestamp) {
            return ((Timestamp) value).toLocalDateTime().toString();
        }
        if (value instanceof Date) {
            return ((Date) value).toLocalDate().toString();
        }
        return String.valueOf(value);
    }

    static final class Options {
        final Path configPath;
        final LocalDate businessDate;
        final String runType;

        Options(Path configPath, LocalDate businessDate, String runType) {
            this.configPath = configPath;
            this.businessDate = businessDate;
            this.runType = runType;
        }

        static Options parse(String[] args) {
            String directory = System.getenv("batchConfigPath");
            Path config = directory == null ? null : Paths.get(directory, "hth_crm_extract.properties");
            LocalDate date = null;
            boolean manual = false;
            for (String arg : args) {
                if (arg.startsWith("--config=")) {
                    config = Paths.get(arg.substring("--config=".length()));
                } else if (arg.startsWith("--date=")) {
                    try {
                        date = LocalDate.parse(arg.substring("--date=".length()), DateTimeFormatter.ISO_LOCAL_DATE);
                    } catch (DateTimeParseException invalid) {
                        throw new IllegalArgumentException("Invalid business date", invalid);
                    }
                    manual = true;
                } else {
                    throw new IllegalArgumentException("Unsupported argument");
                }
            }
            if (config == null) {
                throw new IllegalArgumentException("Extract configuration path missing");
            }
            LocalDate today = LocalDate.now(HONG_KONG);
            if (date == null) {
                date = today.minusDays(1);
            }
            if (!date.isBefore(today)) {
                throw new IllegalArgumentException("Business date must be before today");
            }
            return new Options(config, date, manual ? "MANUAL" : "SCHEDULED");
        }
    }

    static final class Configuration {
        final Path outputDirectory;
        final String fileNamePattern;
        final List<String> columns;
        final boolean header;
        final boolean headerOnlyWhenEmpty;

        Configuration(Path outputDirectory, String fileNamePattern, List<String> columns,
                boolean header, boolean headerOnlyWhenEmpty) {
            this.outputDirectory = outputDirectory;
            this.fileNamePattern = fileNamePattern;
            this.columns = columns;
            this.header = header;
            this.headerOnlyWhenEmpty = headerOnlyWhenEmpty;
        }

        static Configuration load(Path path) throws Exception {
            Properties properties = LoadProperties.getPropertiesFromFile(path.toString());
            Path output = Paths.get(required(properties, "output.directory")).toAbsolutePath().normalize();
            String pattern = required(properties, "file.name.pattern");
            if (!pattern.matches("[A-Za-z0-9_.-]*\\{date\\}[A-Za-z0-9_.-]*")
                    || pattern.contains("..")) {
                throw new IllegalArgumentException("Invalid file name pattern");
            }
            List<String> columns = new ArrayList<String>();
            Set<String> unique = new HashSet<String>();
            for (String raw : required(properties, "csv.columns").split(",", -1)) {
                String column = raw.trim().toUpperCase(Locale.ROOT);
                if (!column.matches("[A-Z][A-Z0-9_]*") || NEVER_EXPORT.contains(column)
                        || !unique.add(column)) {
                    throw new IllegalArgumentException("Invalid CSV column configuration");
                }
                columns.add(column);
            }
            if (columns.isEmpty()) {
                throw new IllegalArgumentException("CSV columns missing");
            }
            String headerValue = properties.getProperty("csv.header", "true").trim();
            if (!"true".equalsIgnoreCase(headerValue) && !"false".equalsIgnoreCase(headerValue)) {
                throw new IllegalArgumentException("Invalid csv.header value");
            }
            boolean header = Boolean.parseBoolean(headerValue);
            String empty = properties.getProperty("empty.policy", "HEADER_ONLY").trim().toUpperCase(Locale.ROOT);
            if (!"HEADER_ONLY".equals(empty) && !"NO_FILE".equals(empty)) {
                throw new IllegalArgumentException("Invalid empty policy");
            }
            if ("HEADER_ONLY".equals(empty) && !header) {
                throw new IllegalArgumentException("HEADER_ONLY requires csv.header=true");
            }
            return new Configuration(output, pattern, columns, header, "HEADER_ONLY".equals(empty));
        }

        String fileName(LocalDate date) {
            return fileNamePattern.replace("{date}", date.format(FILE_DATE));
        }
    }

    static final class HthCRMExtractJobRunner {
        private final Connection connection;
        private final Configuration config;
        private final Options options;
        private final String runId = UUID.randomUUID().toString();
        private String stage = "RUN_START";
        private Path temporaryFile;
        private long extractedCount;
        private String publishedFileName;
        private long publishedFileBytes;

        HthCRMExtractJobRunner(Connection connection, Configuration config, Options options) {
            this.connection = connection;
            this.config = config;
            this.options = options;
        }

        void run() throws Exception {
            insertRun();
            System.out.println("HTH_CRM_EXTRACT stage=RUN_START runId=" + runId
                    + " businessDate=" + options.businessDate);
            try {
                Files.createDirectories(config.outputDirectory);
                Path finalFile = config.outputDirectory.resolve(config.fileName(options.businessDate));
                stage = "FILE_CREATE";
                temporaryFile = Files.createTempFile(config.outputDirectory,
                        "." + finalFile.getFileName() + ".", ".tmp");
                stage = "EXTRACT";
                long count = writeCsv(temporaryFile);
                String fileName = null;
                long bytes = 0;
                if (count == 0 && !config.headerOnlyWhenEmpty) {
                    Files.delete(temporaryFile);
                    temporaryFile = null;
                    stage = "EMPTY_FILE_REMOVE";
                    Files.deleteIfExists(finalFile);
                } else {
                    stage = "FILE_PUBLISH";
                    try {
                        Files.move(temporaryFile, finalFile, StandardCopyOption.ATOMIC_MOVE,
                                StandardCopyOption.REPLACE_EXISTING);
                    } catch (AtomicMoveNotSupportedException unsupported) {
                        throw new IOException("Atomic file publication unavailable", unsupported);
                    }
                    temporaryFile = null;
                    fileName = finalFile.getFileName().toString();
                    bytes = Files.size(finalFile);
                    publishedFileName = fileName;
                    publishedFileBytes = bytes;
                }
                stage = "RUN_SUCCESS";
                finishRun("SUCCESS", count, fileName, bytes, null, null, null);
                System.out.println("HTH_CRM_EXTRACT stage=RUN_SUCCESS runId=" + runId
                        + " businessDate=" + options.businessDate + " count=" + count
                        + " file=" + (fileName == null ? "NONE" : fileName));
            } catch (Exception failure) {
                if (temporaryFile != null) {
                    try {
                        Files.deleteIfExists(temporaryFile);
                    } catch (IOException ignored) {
                        // The original failure remains authoritative.
                    }
                }
                String failureStage = stage;
                try {
                    finishRun("FAILED", extractedCount, publishedFileName, publishedFileBytes, failureStage,
                            failure.getClass().getSimpleName(), safeErrorCode(failure));
                } catch (SQLException logFailure) {
                    System.err.println("HTH_CRM_EXTRACT stage=RUN_LOG_FAILED runId=" + runId
                            + " type=" + logFailure.getClass().getSimpleName());
                }
                System.err.println("HTH_CRM_EXTRACT stage=" + failureStage + " runId=" + runId
                        + " result=FAILED type=" + failure.getClass().getSimpleName()
                        + " code=" + safeErrorCode(failure));
                throw failure;
            }
        }

        private void insertRun() throws SQLException {
            String sql = "INSERT INTO " + RUN_TABLE + " (RUN_ID,BUSINESS_DATE,RUN_TYPE,OPERATOR_ID,"
                    + "STARTED_AT,STATUS,RECORD_COUNT) VALUES (?,?,?,?,?,'RUNNING',0)";
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, runId);
                statement.setDate(2, Date.valueOf(options.businessDate));
                statement.setString(3, options.runType);
                statement.setString(4, System.getProperty("user.name", "batch"));
                statement.setTimestamp(5, Timestamp.valueOf(LocalDateTime.now(HONG_KONG)));
                statement.executeUpdate();
            }
        }

        private long writeCsv(Path file) throws SQLException, IOException {
            String sql = "SELECT " + String.join(",", config.columns) + " FROM " + SOURCE_TABLE
                    + " WHERE CREATED_AT >= ? AND CREATED_AT < ? ORDER BY CREATED_AT,EVENT_ID";
            long count = 0;
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setTimestamp(1, Timestamp.valueOf(options.businessDate.atStartOfDay()));
                statement.setTimestamp(2, Timestamp.valueOf(options.businessDate.plusDays(1).atStartOfDay()));
                statement.setFetchSize(500);
                try (ResultSet rows = statement.executeQuery();
                        BufferedWriter writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8,
                                StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)) {
                    if (config.header) {
                        writer.write(String.join(",", config.columns));
                        writer.write('\n');
                    }
                    while (rows.next()) {
                        for (int index = 1; index <= config.columns.size(); index++) {
                            if (index > 1) {
                                writer.write(',');
                            }
                            writer.write(csv(formatValue(rows.getObject(index))));
                        }
                        writer.write('\n');
                        count++;
                        extractedCount = count;
                    }
                }
            }
            try (FileChannel channel = FileChannel.open(file, StandardOpenOption.WRITE)) {
                channel.force(true);
            }
            return count;
        }

        private void finishRun(String status, long count, String fileName, long bytes,
                String errorStage, String errorType, String errorCode) throws SQLException {
            String sql = "UPDATE " + RUN_TABLE + " SET ENDED_AT=?,STATUS=?,RECORD_COUNT=?,"
                    + "FILE_NAME=?,FILE_BYTES=?,ERROR_STAGE=?,ERROR_TYPE=?,ERROR_CODE=?,ERROR_MESSAGE=?"
                    + " WHERE RUN_ID=? AND STATUS='RUNNING'";
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setTimestamp(1, Timestamp.valueOf(LocalDateTime.now(HONG_KONG)));
                statement.setString(2, status);
                statement.setLong(3, count);
                statement.setString(4, fileName);
                statement.setLong(5, bytes);
                statement.setString(6, errorStage);
                statement.setString(7, errorType);
                statement.setString(8, errorCode);
                statement.setString(9, errorStage == null ? null : errorStage + " failed (" + errorType
                        + ", " + errorCode + ")");
                statement.setString(10, runId);
                if (statement.executeUpdate() != 1) {
                    throw new SQLException("Run row no longer active");
                }
            }
        }
    }
}
