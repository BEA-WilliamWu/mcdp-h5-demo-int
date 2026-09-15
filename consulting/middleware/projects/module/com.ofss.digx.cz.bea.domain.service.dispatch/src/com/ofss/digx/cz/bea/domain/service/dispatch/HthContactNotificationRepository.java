package com.ofss.digx.cz.bea.domain.service.dispatch;

import com.ofss.digx.cz.bea.app.sms.dto.user.HthContactNotificationPlan;
import com.ofss.fc.infra.config.ConfigurationFactory;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import javax.naming.InitialContext;
import javax.sql.DataSource;

/** Delivery ledger only. Business staging uses the caller's DIGX ORM transaction. */
final class HthContactNotificationRepository {
    private static final String TABLE = "DIGX_CZ_HTH_CONTACT_NOTIFY";
    private final DataSource source;
    HthContactNotificationRepository(DataSource source) { this.source = source; }
    static HthContactNotificationRepository configured() throws java.lang.Exception {
        String jndi = ConfigurationFactory.getInstance().getConfigurations("HthProfileContactNotification")
                .get("dispatchDataSource", "NONXA");
        InitialContext context = new InitialContext();
        try { return new HthContactNotificationRepository((DataSource) context.lookup(jndi)); }
        finally { context.close(); }
    }
    private static final class Work implements AutoCloseable {
        final Connection connection;
        Work(DataSource source) throws SQLException {
            connection = source.getConnection();
            try { connection.setAutoCommit(false); }
            catch (SQLException e) { connection.close(); throw e; }
        }
        @Override public void close() throws SQLException {
            try { connection.rollback(); } finally { connection.close(); }
        }
    }
    static int update(Connection c, String sql, Object... args) throws SQLException {
        try (PreparedStatement statement = c.prepareStatement(sql)) {
            for (int i = 0; i < args.length; i++) statement.setObject(i + 1, args[i]);
            return statement.executeUpdate();
        }
    }
    List<Row> pending(String unit) throws SQLException {
        try (Work work = new Work(source); PreparedStatement p = work.connection.prepareStatement("SELECT * FROM " + TABLE
                + " WHERE TARGET_UNIT = ? AND (STATE = 'READY' OR (STATE IN ('PUBLISHING','PUBLISHED') "
                + "AND UPDATED_AT < CURRENT_TIMESTAMP - INTERVAL '5' MINUTE)) ORDER BY CREATED_AT, ID")) {
            p.setString(1, unit); p.setMaxRows(100);
            List<Row> rows = new ArrayList<Row>();
            try (ResultSet rs = p.executeQuery()) { while (rs.next()) rows.add(new Row(rs)); }
            work.connection.commit(); return rows;
        }
    }
    Row read(Connection c, String id) throws SQLException {
        try (PreparedStatement p = c.prepareStatement("SELECT * FROM " + TABLE + " WHERE ID = ?")) {
            p.setString(1, id);
            try (ResultSet rs = p.executeQuery()) { return rs.next() ? new Row(rs) : null; }
        }
    }
    boolean claimPublication(String id) throws SQLException {
        try (Work work = new Work(source)) {
            Connection c = work.connection;
            int count = update(c, "UPDATE " + TABLE + " SET STATE = 'PUBLISHING', UPDATED_AT = CURRENT_TIMESTAMP "
                + "WHERE ID = ? AND (STATE = 'READY' OR (STATE IN ('PUBLISHING','PUBLISHED') "
                + "AND UPDATED_AT < CURRENT_TIMESTAMP - INTERVAL '5' MINUTE))", id);
            c.commit(); return count == 1;
        }
    }
    void published(String id) throws SQLException {
        try (Work work = new Work(source)) {
            Connection c = work.connection;
            update(c, "UPDATE " + TABLE + " SET STATE = 'PUBLISHED', UPDATED_AT = CURRENT_TIMESTAMP "
                    + "WHERE ID = ? AND STATE = 'PUBLISHING'", id);
            c.commit();
        }
    }
    Row claimDispatch(String id, String channel, String event, String activityDataId,
            String subject, String bodyHash) throws SQLException {
        try (Work work = new Work(source)) {
            Connection c = work.connection;
            Row row = read(c, id);
            if (row == null || !channel.equals(row.channel) || !row.event().equals(event)) {
                c.rollback(); throw new SQLException("HTH contact notification context mismatch");
            }
            int count = update(c, "UPDATE " + TABLE + " SET STATE = 'SENDING', COD_ACT_DATA_ID = ?, "
                + "UPDATED_AT = CURRENT_TIMESTAMP WHERE ID = ? AND STATE IN ('PUBLISHING','PUBLISHED')",
                activityDataId, id);
            if (count == 0) { c.rollback(); return null; }
            // Durable MNG reference is committed BEFORE external IO. Failure here prevents sending.
            update(c, "INSERT INTO DIGX_CZ_EMAIL_MNG (REFNUMBER, RECIPIENTID, MESSAGEBODY, SUBJECT, "
                + "CUSTOMERID, PARTYID, ACTIVITYID, ACTIONID, EVENTID, ORG_REF_NO, ALERT_TYPE, "
                + "RESPONSE_STATUS, COD_ACT_DATA_ID, TXNTYPE, LAST_UPDATED_DATE) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, 'A', ?, ?, ?, 'Pending', ?, ?, CURRENT_TIMESTAMP)",
                row.mngRef(), row.address, bodyHash, subject, row.recipientUser, row.party,
                HthContactNotificationPlan.ACTIVITY, event, row.reference, channel, activityDataId,
                "HTH Profile Contact Update");
            c.commit(); return row;
        }
    }
    void dispatched(String id, String state) throws SQLException {
        if (!"SUBMITTED".equals(state) && !"REJECTED".equals(state) && !"UNKNOWN".equals(state))
            throw new IllegalArgumentException("Invalid dispatch outcome");
        try (Work work = new Work(source)) {
            Connection c = work.connection;
            update(c, "UPDATE " + TABLE + " SET STATE = ?, UPDATED_AT = CURRENT_TIMESTAMP "
                    + "WHERE ID = ? AND STATE = 'SENDING'", state, id);
            update(c, "UPDATE DIGX_CZ_EMAIL_MNG SET RESPONSE_STATUS = ?, LAST_UPDATED_DATE = CURRENT_TIMESTAMP "
                    + "WHERE REFNUMBER = ?", "SUBMITTED".equals(state) ? "Success" :
                    ("REJECTED".equals(state) ? "Failed" : "Exception"), "HC" + id);
            c.commit();
        }
    }
    void reconcile(String unit) throws SQLException {
        try (Work work = new Work(source)) {
            Connection c = work.connection;
            // This table contains negative EMAIL delivery receipts. Its generic processed flag is irrelevant.
            update(c, "UPDATE " + TABLE + " N SET STATE = 'DELIVERY_FAILED', UPDATED_AT = CURRENT_TIMESTAMP "
                + "WHERE TARGET_UNIT = ? AND CHANNEL = 'EMAIL' AND STATE IN ('SUBMITTED','UNKNOWN','SENDING') "
                + "AND EXISTS (SELECT 1 FROM DIGX_CZ_BATCH_BOUNCE_BACK_CCBEMAIL B "
                + "WHERE B.SRC_SYS_REF_NUM = 'HC' || N.ID)", unit);
            // A crashed in-flight submission is UNKNOWN, never automatically resent.
            update(c, "UPDATE " + TABLE + " SET STATE = 'UNKNOWN', UPDATED_AT = CURRENT_TIMESTAMP "
                + "WHERE TARGET_UNIT = ? AND STATE = 'SENDING' "
                + "AND UPDATED_AT < CURRENT_TIMESTAMP - INTERVAL '15' MINUTE", unit);
            c.commit();
        }
        List<String> ids = new ArrayList<String>();
        try (Work work = new Work(source); PreparedStatement p = work.connection.prepareStatement("SELECT ID FROM " + TABLE
                + " WHERE TARGET_UNIT = ? AND STATE IN ('REJECTED','DELIVERY_FAILED') ORDER BY CREATED_AT, ID")) {
            p.setString(1, unit); p.setMaxRows(100);
            try (ResultSet rs = p.executeQuery()) { while (rs.next()) ids.add(rs.getString(1)); }
            work.connection.commit();
        }
        for (String id : ids) fallback(id);
    }
    void fallback(String id) throws SQLException {
        try (Work work = new Work(source)) {
            Connection c = work.connection;
            // Row lock serializes concurrent receipt processing; child PK also enforces once only.
            try (PreparedStatement lock = c.prepareStatement("SELECT ID FROM " + TABLE + " WHERE ID = ? FOR UPDATE")) {
                lock.setString(1, id); try (ResultSet ignored = lock.executeQuery()) { if (!ignored.next()) return; }
            }
            Row row = read(c, id);
            if (!"REJECTED".equals(row.state) && !"DELIVERY_FAILED".equals(row.state)) { c.rollback(); return; }
            String address = "";
            if ("API".equals(row.role) && "EMAIL".equals(row.channel) && row.parent == null) {
                try (PreparedStatement p = c.prepareStatement("SELECT OFFICE_EMAIL FROM DIGX_PI_PARTY_PREFERENCES WHERE PARTYID = ?")) {
                    p.setString(1, row.party);
                    try (ResultSet rs = p.executeQuery()) { if (rs.next()) address = HthContactNotificationPlan.email(rs.getString(1)); }
                }
            }
            boolean fallback = !address.isEmpty() && !address.equals(HthContactNotificationPlan.email(row.address));
            if (fallback) {
                String child = HthContactNotificationPlan.id(row.id, "FALLBACK");
                update(c, "INSERT INTO " + TABLE + " (ID, TARGET_UNIT, APPROVAL_REF, PARTY_ID, TARGET_USER_ID, "
                    + "APPROVER_ID, CHANGE_TYPE, RECIPIENT_ROLE, CHANNEL, ADDRESS, RECIPIENT_USER_ID, USER_LOCALE, "
                    + "APPROVED_AT, STATE, PARENT_ID, CREATED_AT, UPDATED_AT) "
                    + "SELECT ?, TARGET_UNIT, APPROVAL_REF, PARTY_ID, TARGET_USER_ID, APPROVER_ID, CHANGE_TYPE, "
                    + "'FALLBACK', 'EMAIL', ?, TARGET_USER_ID, USER_LOCALE, APPROVED_AT, 'READY', ID, "
                    + "CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM " + TABLE + " WHERE ID = ? "
                    + "AND NOT EXISTS (SELECT 1 FROM " + TABLE + " WHERE ID = ?)", child, address, id, child);
            }
            update(c, "UPDATE " + TABLE + " SET STATE = ?, UPDATED_AT = CURRENT_TIMESTAMP WHERE ID = ?",
                    fallback ? "FALLBACK_QUEUED" : "FAILED_FINAL", id);
            c.commit();
        }
    }
    static final class Row {
        final String id, unit, reference, party, user, approver, change, role, channel, address,
                recipientUser, locale, approvedAt, state, parent;
        Row(ResultSet r) throws SQLException {
            id=r.getString("ID"); unit=r.getString("TARGET_UNIT"); reference=r.getString("APPROVAL_REF");
            party=r.getString("PARTY_ID"); user=r.getString("TARGET_USER_ID"); approver=r.getString("APPROVER_ID");
            change=r.getString("CHANGE_TYPE"); role=r.getString("RECIPIENT_ROLE"); channel=r.getString("CHANNEL");
            address=r.getString("ADDRESS"); recipientUser=r.getString("RECIPIENT_USER_ID");
            locale=r.getString("USER_LOCALE"); approvedAt=r.getString("APPROVED_AT");
            state=r.getString("STATE"); parent=r.getString("PARENT_ID");
        }
        String mngRef() { return "HC" + id; }
        String event() { return HthContactNotificationPlan.event(change, role); }
    }
}
