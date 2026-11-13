package com.hkbea.batch.mtb;

import static org.junit.jupiter.api.Assertions.*;
import com.hkbea.batch.mtb.crm.CrmFileLayout;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MTBBatchMainTest {
    @TempDir Path directory;

    @Test void invalidArgumentsFailBeforeLoadingConfigurationOrOpeningDatabase() {
        for (String[] args : List.of(new String[0], new String[]{"unknown"},
                new String[]{"CRM_EXTRACT", "20260230"}, new String[]{"CRM_EXTRACT", "20261007", "extra"})) {
            assertEquals(2, MTBBatchMain.run(args, Clock.systemUTC()));
        }
    }

    @Test void subprocessUsesNativeConfigurationAndReturnsSuccessOrFailureWithSafeLogs() throws Exception {
        Path config = Files.createDirectory(directory.resolve("config with spaces"));
        Path output = directory.resolve("output with spaces");
        String jdbc = "jdbc:h2:" + directory.resolve("test-db") + ";MODE=Oracle";
        String password = "synthetic-secret-do-not-log";
        try (var conn = DriverManager.getConnection(jdbc, "sa", password); var sql = conn.createStatement()) {
            sql.execute("CREATE SCHEMA HTH_BEA");
            StringBuilder ddl = new StringBuilder("CREATE TABLE HTH_BEA.HTH_CRM_EVENT (ID VARCHAR(40), CREATION_DATE TIMESTAMP");
            CrmFileLayout.COLUMNS.stream().skip(2).forEach(c -> ddl.append(',').append(c).append(" VARCHAR(2000)"));
            sql.execute(ddl.append(')').toString());
            sql.execute("INSERT INTO HTH_BEA.HTH_CRM_EVENT (ID,CREATION_DATE,EVENT_ID,EVENT_TIME) "
                + "VALUES ('test-id', TIMESTAMP '2026-10-07 12:34:56','test-event','123456')");
        }
        Properties database = new Properties();
        database.setProperty("jdbc.driver.class", "org.h2.Driver");
        database.setProperty("jdbc.conn.string", jdbc);
        database.setProperty("jdbc.conn.username", "sa");
        database.setProperty("jdbc.conn.password", password);
        write(config.resolve("db.ibank.batch.properties"), database);
        Properties system = new Properties();
        system.setProperty("batch.output.dir", output.toString());
        write(config.resolve("system_batch.properties"), system);
        Path log = directory.resolve("run.log");
        assertEquals(0, launch(config, log));
        Path csv = output.resolve("hthtomtb_mvp1apis_D261007.csv");
        byte[] first = Files.readAllBytes(csv);
        assertEquals(4, Files.readAllLines(csv).size());
        String success = Files.readString(log);
        assertTrue(success.contains("stage=END status=SUCCESS"));
        assertTrue(success.contains("count=1 size=" + first.length));
        assertFalse(success.contains(password));
        assertFalse(success.contains(jdbc));
        assertEquals(0, launch(config, log));
        assertArrayEquals(first, Files.readAllBytes(csv));
        try (var conn = DriverManager.getConnection(jdbc, "sa", password); var sql = conn.createStatement()) {
            sql.execute("DROP TABLE HTH_BEA.HTH_CRM_EVENT");
        }
        assertEquals(50, launch(config, log));
        assertTrue(Files.readString(log).contains("status=FAILED"));
        assertTrue(Files.readString(log).contains("sqlState="));
        assertFalse(Files.readString(log).contains(password));
        assertArrayEquals(first, Files.readAllBytes(csv));
        Files.delete(config.resolve("db.ibank.batch.properties"));
        assertEquals(50, launch(config, log));
        assertTrue(Files.readString(log).contains("phase=CONFIG"));
    }

    private int launch(Path config, Path log) throws Exception {
        List<String> args = new ArrayList<>(List.of(
            Path.of(System.getProperty("java.home"), "bin", "java").toString(),
            "-Dconfig.path=" + config, "-Dbatch.log.dir=" + directory.resolve("logs"), "-cp",
            System.getProperty("surefire.test.class.path", System.getProperty("java.class.path")),
            MTBBatchMain.class.getName(), "CRM_EXTRACT", "20261007"));
        Process process = new ProcessBuilder(args).redirectErrorStream(true).redirectOutput(log.toFile()).start();
        if (!process.waitFor(30, TimeUnit.SECONDS)) {
            process.destroyForcibly(); fail("Batch did not exit");
        }
        return process.exitValue();
    }

    private void write(Path path, Properties properties) throws Exception {
        try (var stream = Files.newOutputStream(path)) { properties.store(stream, "Synthetic test configuration"); }
    }
}
