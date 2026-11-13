package com.hkbea.batch.mtb.util;

import static org.junit.jupiter.api.Assertions.*;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BatchConfigTest {
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
}
