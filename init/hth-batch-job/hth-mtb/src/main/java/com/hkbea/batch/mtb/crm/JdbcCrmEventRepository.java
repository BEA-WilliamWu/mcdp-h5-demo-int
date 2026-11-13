package com.hkbea.batch.mtb.crm;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Read-only HTH/DSP repository. Never uses BCO CRM tables or processed markers. */
public final class JdbcCrmEventRepository implements CrmEventRepository {
    private static final String SQL = buildSql();
    private final ConnectionFactory connections;

    @FunctionalInterface
    public interface ConnectionFactory {
        Connection open() throws SQLException;
    }

    public JdbcCrmEventRepository(ConnectionFactory connections) {
        this.connections = connections;
    }

    private static String buildSql() {
        StringBuilder sql = new StringBuilder("SELECT ID, '50', '1'");
        CrmFileLayout.COLUMNS.stream().skip(2)
            .forEach(column -> sql.append(", ").append(column.toUpperCase(Locale.ROOT)));
        return sql.append(" FROM HTH_BEA.HTH_CRM_EVENT")
            .append(" WHERE CREATION_DATE >= ? AND CREATION_DATE < ?")
            .append(" ORDER BY CREATION_DATE, ID").toString();
    }

    @Override
    public void readDay(LocalDate day, RowConsumer consumer) throws SQLException, IOException {
        try (Connection connection = connections.open()) {
            connection.setReadOnly(true);
            try (PreparedStatement query = connection.prepareStatement(SQL)) {
                // Oracle DATE has no zone; preserve the HTH/DSP business-day boundary.
                query.setTimestamp(1, Timestamp.valueOf(day.atStartOfDay()));
                query.setTimestamp(2, Timestamp.valueOf(day.plusDays(1).atStartOfDay()));
                query.setFetchSize(500);
                try (ResultSet rows = query.executeQuery()) {
                    while (rows.next()) {
                        List<String> values = new ArrayList<>(CrmFileLayout.COLUMNS.size());
                        for (int i = 0; i < CrmFileLayout.COLUMNS.size(); i++) {
                            values.add(rows.getString(i + 2));
                        }
                        consumer.accept(new CrmEvent(rows.getString(1), values));
                    }
                }
            }
        }
    }
}
