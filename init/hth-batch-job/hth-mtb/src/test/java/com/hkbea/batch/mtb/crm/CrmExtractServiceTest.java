package com.hkbea.batch.mtb.crm;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CrmExtractServiceTest {
    @TempDir Path output;
    private static final LocalDate DAY = LocalDate.of(2026, 9, 30);
    private String url;

    @BeforeEach
    void createDatabase() throws Exception {
        url = "jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=Oracle;DB_CLOSE_DELAY=-1";
        try (Connection connection = connect(); var sql = connection.createStatement()) {
            sql.execute("CREATE SCHEMA HTH_BEA");
            StringBuilder ddl = new StringBuilder("CREATE TABLE HTH_BEA.HTH_CRM_EVENT (ID VARCHAR(40), CREATION_DATE TIMESTAMP");
            // Include physical fields which are not part of the file projection.
            for (String column : CrmFileLayout.COLUMNS) ddl.append(',').append(column).append(" VARCHAR(2000)");
            sql.execute(ddl.append(", DB_ONLY_COLUMN VARCHAR(100))").toString());
            // A BCO sentinel table must remain untouched and must never be extracted.
            sql.execute("CREATE TABLE DIGX_CZ_CRM_EVENT3_DETAILS (EVENT_ID VARCHAR(30))");
            sql.execute("INSERT INTO DIGX_CZ_CRM_EVENT3_DETAILS VALUES ('BCO_SENTINEL')");
        }
    }

    private Connection connect() throws SQLException { return DriverManager.getConnection(url, "sa", "test"); }
    private CrmExtractRequest request() { return new CrmExtractRequest(DAY, output, null); }
    private CrmExtractService service() { return new CrmExtractService(new JdbcCrmEventRepository(this::connect)); }

    private void insert(String id, String time, String eventTime, String remark) throws Exception {
        try (var conn = connect(); var statement = conn.prepareStatement("INSERT INTO HTH_BEA.HTH_CRM_EVENT "
                + "(ID,CREATION_DATE,EVENT_ID,EVENT_TIME,EVENT_REM,RECORD_TYPE,FILLER_01,DB_ONLY_COLUMN) VALUES (?,?,?,?,?,'unexpected','unexpected','private')")) {
            statement.setString(1, id);
            statement.setTimestamp(2, Timestamp.valueOf(time));
            statement.setString(3, id);
            statement.setString(4, eventTime);
            statement.setString(5, remark);
            statement.executeUpdate();
        }
    }

    @Test void queriesOnlyBusinessDayInStableOrderAndDoesNotUpdateSource() throws Exception {
        insert("before", "2026-09-29 23:59:59", "000000", null);
        insert("later", "2026-09-30 23:59:59", "235959", "中文,\\path\r\n\"quoted\"");
        insert("a2", "2026-09-30 00:00:00", "12:34:56", null);
        insert("a1", "2026-09-30 00:00:00", "123456", null);
        insert("after", "2026-10-01 00:00:00", "000000", null);
        var result = service().extract(request());
        List<String> lines = Files.readAllLines(result.file());
        assertEquals(3, result.count());
        assertEquals(Files.size(result.file()), result.size());
        assertEquals(6, lines.size());
        assertTrue(lines.get(2).startsWith("\"50\",\"1\",\"a1\""));
        assertTrue(lines.get(3).startsWith("\"50\",\"1\",\"a2\""));
        assertTrue(lines.get(4).startsWith("\"50\",\"1\",\"later\""));
        assertTrue(lines.get(4).contains("中文,\\\\path\\r\\n\\\"quoted\\\""));
        String content = Files.readString(result.file());
        assertFalse(content.contains("BCO_SENTINEL"));
        assertFalse(content.contains("private"));
        assertFalse(content.contains("unexpected"));
        assertFalse(content.contains("\r"));
        assertEquals("0020260930HTHMTB" + " ".repeat(84), lines.getFirst());
        assertEquals("99" + "000000000000003" + "000000020260933" + " ".repeat(68), lines.getLast());
        try (var connection = connect(); var sql = connection.createStatement()) {
            try (var rows = sql.executeQuery("SELECT COUNT(*) FROM HTH_BEA.HTH_CRM_EVENT WHERE RECORD_TYPE='unexpected'")) {
                assertTrue(rows.next()); assertEquals(5, rows.getInt(1));
            }
            try (var rows = sql.executeQuery("SELECT EVENT_ID FROM DIGX_CZ_CRM_EVENT3_DETAILS")) {
                assertTrue(rows.next()); assertEquals("BCO_SENTINEL", rows.getString(1));
            }
        }
    }

    @Test void zeroRowsStillProducesCompleteFileAndRerunReplacesSameName() throws Exception {
        var empty = service().extract(request());
        byte[] initial = Files.readAllBytes(empty.file());
        assertEquals(0, empty.count());
        assertEquals(3, Files.readAllLines(empty.file()).size());
        assertArrayEquals(initial, Files.readAllBytes(service().extract(request()).file()));
        insert("one", "2026-09-30 09:00:00", "090000", null);
        var updated = service().extract(request());
        assertEquals(empty.file(), updated.file());
        assertEquals(1, updated.count());
        assertEquals(4, Files.readAllLines(updated.file()).size());
        assertNoTempFiles();
    }

    @Test void failingRowPreservesPublishedFileAndCleansTemporaryFile() throws Exception {
        Path published = service().extract(request()).file();
        byte[] initial = Files.readAllBytes(published);
        insert("valid", "2026-09-30 09:00:00", "090000", null);
        insert("bad", "2026-09-30 10:00:00", "invalid", null);
        assertThrows(IllegalStateException.class, () -> service().extract(request()));
        assertArrayEquals(initial, Files.readAllBytes(published));
        assertNoTempFiles();
    }

    @Test void databaseFailureAfterOneRowNeverPublishesPartialFile() throws Exception {
        Path published = service().extract(request()).file();
        byte[] initial = Files.readAllBytes(published);
        CrmExtractService failing = new CrmExtractService((day, consumer) -> {
            consumer.accept(event("test", null));
            throw new SQLException("simulated disconnect");
        });
        assertThrows(SQLException.class, () -> failing.extract(request()));
        assertArrayEquals(initial, Files.readAllBytes(published));
        assertNoTempFiles();
    }

    @Test void sameDateLockRejectsConcurrentRunBeforeReadingDatabase() throws Exception {
        CrmExtractService neverRead = new CrmExtractService((day, consumer) -> fail("Lock must be checked first"));
        try (FileChannel channel = FileChannel.open(output.resolve(request().fileName() + ".lock"),
                StandardOpenOption.CREATE, StandardOpenOption.WRITE); var lock = channel.lock()) {
            assertThrows(IOException.class, () -> neverRead.extract(request()));
        }
        assertNoTempFiles();
    }

    @Test void oldBcoHostedJobGoldenFilesAreByteIdentical() throws Exception {
        for (int count = 0; count < 3; count++) {
            int n = count;
            LocalDate day = DAY.minusDays(count == 0 ? 0 : count);
            CrmExtractService fixture = new CrmExtractService((d, consumer) -> {
                if (n == 1) consumer.accept(event("id-1", "a,\"b\"\nline"));
                if (n == 2) {
                    consumer.accept(event("id-1", "中文,\\path\r\n\"quoted\""));
                    consumer.accept(event("id-2", null));
                }
            });
            var result = fixture.extract(new CrmExtractRequest(day, output, null));
            try (var expected = getClass().getResourceAsStream("/golden/" + count + ".csv")) {
                assertNotNull(expected);
                assertArrayEquals(expected.readAllBytes(), Files.readAllBytes(result.file()));
            }
        }
    }

    private static CrmEvent event(String id, String remark) {
        List<String> values = new ArrayList<>(Collections.nCopies(61, null));
        values.set(0, "50"); values.set(1, "1"); values.set(4, "123456"); values.set(30, remark);
        return new CrmEvent(id, values);
    }

    private void assertNoTempFiles() throws Exception {
        try (var files = Files.list(output)) {
            assertFalse(files.anyMatch(path -> path.toString().endsWith(".tmp")));
        }
    }
}
