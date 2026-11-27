package com.hkbea.batch.mtb.util;

import static org.junit.jupiter.api.Assertions.*;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BatchConfigTest {
    // Generated with BCO AESUtil using only synthetic test values.
    private static final String SYNTHETIC_KEY = "synthetic-wrapping-key";
    private static final String SYNTHETIC_CONFIG = """
        encr_passphrase=TM/BFkqmpbFW/Ef/bg8yTO29dUt9cz++yXc18w3u2nQie5bIFdDTUM1AQKQ8ZtIGFDjEobhSpxYHAdoeoSrMWw==
        db.hostname=+bJaGEZkApD2OlKTMeQ6JQ==
        db.port=M2oYo99jEyJffrNFmQeSMA==
        db.servicename=UWkJVjvEy3Bz4ypsxWvTPg==
        db.username=AmUlwKaIHyo8ZXu3noJHNw==
        db.password=p7X/bWoDJEbdlEC/m2Jj3w==
        """;

    @TempDir Path directory;

    @Test void externalFilesAreRequiredWithNoPackagedFallback() throws Exception {
        assertThrows(IOException.class, () -> BatchConfig.load(directory));
        Files.writeString(directory.resolve("db.ibank.batch.properties"), "jdbc.conn.username=test\n");
        assertThrows(IOException.class, () -> BatchConfig.load(directory));
        Files.writeString(directory.resolve("system_batch.properties"), "batch.output.dir=./output\n");
        var config = BatchConfig.load(directory);
        assertEquals("test", config.getProperty(BatchConfig.DB_USERNAME));
        assertThrows(BatchConfig.ConfigurationException.class, () -> config.required(BatchConfig.DB_PASSWORD));
    }

    @Test void environmentSecretsAreResolvedLiterallyAndMissingValuesFailClosed() {
        Properties props = new Properties();
        props.setProperty(BatchConfig.DB_PASSWORD, "${SECRET}");
        String value = "synthetic-$dollar\\path!";
        var config = BatchConfig.fromProperties(props, name -> value);
        assertEquals(value, config.required(BatchConfig.DB_PASSWORD));
        config.getProperties().clear();
        assertEquals(value, config.required(BatchConfig.DB_PASSWORD));
        var error = assertThrows(BatchConfig.ConfigurationException.class,
            () -> BatchConfig.fromProperties(props, name -> null));
        assertTrue(error.getMessage().contains("jdbc.conn.password"));
        assertFalse(error.getMessage().contains(value));
    }

    @Test void readsBcoFieldsInTheExistingJdbcConfiguration() throws Exception {
        writeExternalConfig(SYNTHETIC_CONFIG);
        var env = Map.of(
            "HTH_BATCH_BCO_WRAPPING_KEY", SYNTHETIC_KEY
        );

        var config = BatchConfig.load(directory, env::get);

        assertEquals("jdbc:oracle:thin:@//db.example.test:1521/testdb", config.required(BatchConfig.DB_CONN_STRING));
        assertEquals("test-user", config.required(BatchConfig.DB_USERNAME));
        assertEquals("test-${literal}", config.required(BatchConfig.DB_PASSWORD));
        assertEquals("false", config.required(BatchConfig.DB_AUTOCOMMIT));
        assertEquals("./output", config.required(BatchConfig.BATCH_OUTPUT_DIR));
    }

    @Test void incorrectWrappingKeyFailsWithoutFallingBackOrExposingIt() throws Exception {
        writeExternalConfig(SYNTHETIC_CONFIG);
        String invalidKey = "incorrect-synthetic-key";
        var env = Map.of(
            "HTH_BATCH_BCO_WRAPPING_KEY", invalidKey,
            "HTH_BATCH_JDBC_URL", "jdbc:h2:mem:test",
            "HTH_BATCH_JDBC_USER", "fallback-user",
            "HTH_BATCH_JDBC_PASSWORD", "fallback-password"
        );
        var error = assertThrows(BatchConfig.ConfigurationException.class, () -> BatchConfig.load(directory, env::get));
        assertEquals("Invalid BCO encrypted value for encr_passphrase", error.getMessage());
        assertFalse(error.getMessage().contains(invalidKey));
        assertNull(error.getCause());
    }

    @Test void missingEncryptedFieldFailsWithTheFieldName() throws Exception {
        writeExternalConfig(SYNTHETIC_CONFIG.replace("db.password=p7X/bWoDJEbdlEC/m2Jj3w==", ""));
        var env = Map.of(
            "HTH_BATCH_BCO_WRAPPING_KEY", SYNTHETIC_KEY
        );
        var error = assertThrows(BatchConfig.ConfigurationException.class, () -> BatchConfig.load(directory, env::get));
        assertEquals("Missing BCO configuration key db.password", error.getMessage());
    }

    @Test void encryptedFieldsWithoutTheWrappingKeyFailInConfiguration() throws Exception {
        writeExternalConfig(SYNTHETIC_CONFIG);
        var error = assertThrows(BatchConfig.ConfigurationException.class,
            () -> BatchConfig.load(directory, name -> null));
        assertEquals("Missing environment variable HTH_BATCH_BCO_WRAPPING_KEY", error.getMessage());
    }

    @Test void bcoFieldsAreOnlyResolvedForAnExternalConfigurationDirectory() {
        Properties source = new Properties();
        source.setProperty("encr_passphrase", "unused-in-packaged-properties");
        source.setProperty(BatchConfig.DB_CONN_STRING, "${HTH_BATCH_JDBC_URL}");
        var env = Map.of("HTH_BATCH_JDBC_URL", "jdbc:h2:mem:test");

        assertEquals("jdbc:h2:mem:test",
            BatchConfig.fromProperties(source, env::get).required(BatchConfig.DB_CONN_STRING));
    }

    private void writeExternalConfig(String encrypted) throws IOException {
        Files.writeString(directory.resolve("db.ibank.batch.properties"), """
            jdbc.conn.string=${HTH_BATCH_JDBC_URL}
            jdbc.conn.username=${HTH_BATCH_JDBC_USER}
            jdbc.conn.password=${HTH_BATCH_JDBC_PASSWORD}
            jdbc.conn.autocommit=false
            """ + encrypted);
        Files.writeString(directory.resolve("system_batch.properties"), "batch.output.dir=./output\n");
    }
}
