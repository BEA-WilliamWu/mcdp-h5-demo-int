package com.hkbea.batch.mtb.crm;

import java.io.IOException;
import java.sql.SQLException;
import java.time.LocalDate;

public interface CrmEventRepository {
    /** Consume one row at a time; do not buffer a day's events in memory. */
    void readDay(LocalDate day, RowConsumer consumer) throws SQLException, IOException;

    @FunctionalInterface
    interface RowConsumer {
        void accept(CrmEvent event) throws IOException;
    }
}
