package com.hkbea.batch.mtb.util;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Native HTH configuration. External configuration never falls back to packaged defaults. */
public final class BatchConfig {
    public static final String DB_DRIVER_CLASS = "jdbc.driver.class";
    public static final String DB_CONN_STRING = "jdbc.conn.string";
    public static final String DB_USERNAME = "jdbc.conn.username";
    public static final String DB_PASSWORD = "jdbc.conn.password";
    public static final String DB_AUTOCOMMIT = "jdbc.conn.autocommit";
    public static final String DB_POOL_SIZE = "jdbc.pool.conn.size";
    public static final String DB_TIMEOUT = "jdbc.conn.timeout";
    public static final String BATCH_OUTPUT_DIR = "batch.output.dir";
    public static final String BATCH_ERROR_DIR = "batch.error.dir";
    public static final String BATCH_LOG_DIR = "batch.log.dir";
    private static final Pattern ENV = Pattern.compile("\\$\\{([A-Za-z_][A-Za-z0-9_]*)}");
    private static BatchConfig instance;
    private final Properties properties = new Properties();

    private BatchConfig(Properties source, Function<String, String> environment) {
        source.forEach((key, raw) -> {
            Matcher matcher = ENV.matcher((String) raw);
            StringBuilder value = new StringBuilder();
            while (matcher.find()) {
                String replacement = environment.apply(matcher.group(1));
                if (replacement == null || replacement.isBlank()) {
                    throw new ConfigurationException("Missing environment variable for " + key);
                }
                matcher.appendReplacement(value, Matcher.quoteReplacement(replacement));
            }
            matcher.appendTail(value);
            properties.setProperty((String) key, value.toString());
        });
    }

    public static synchronized BatchConfig getInstance() throws IOException {
        if (instance == null) {
            String directory = System.getProperty("config.path");
            instance = load(directory == null || directory.isBlank() ? null : Path.of(directory));
        }
        return instance;
    }

    public static BatchConfig load(Path directory) throws IOException {
        Properties source = new Properties();
        for (String name : new String[] {"db.ibank.batch.properties", "system_batch.properties"}) {
            try (InputStream input = directory == null
                    ? BatchConfig.class.getResourceAsStream("/config/" + name)
                    : Files.newInputStream(directory.resolve(name))) {
                if (input == null) throw new IOException("Missing batch configuration file " + name);
                source.load(input);
            }
        }
        // encrypt.properties was an unused scaffold. No implicit BCO AES/decryption fallback.
        return fromProperties(source, System::getenv);
    }

    public static BatchConfig fromProperties(Properties source, Function<String, String> environment) {
        return new BatchConfig(source, environment);
    }

    public String required(String key) {
        String value = getProperty(key);
        if (value == null || value.isBlank()) throw new ConfigurationException("Missing configuration key " + key);
        return value;
    }

    public String getProperty(String key) { return properties.getProperty(key); }
    public String getProperty(String key, String fallback) { return properties.getProperty(key, fallback); }

    public int getIntProperty(String key, int fallback) {
        String value = getProperty(key);
        if (value == null || value.isBlank()) return fallback;
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            throw new ConfigurationException("Invalid integer configuration key " + key);
        }
    }

    public boolean getBooleanProperty(String key, boolean fallback) {
        String value = getProperty(key);
        return value == null || value.isBlank() ? fallback : Boolean.parseBoolean(value);
    }

    public Properties getProperties() {
        Properties copy = new Properties();
        copy.putAll(properties);
        return copy;
    }

    /** Messages contain key names only, never configuration values or credentials. */
    public static final class ConfigurationException extends IllegalArgumentException {
        public ConfigurationException(String message) { super(message); }
    }
}
