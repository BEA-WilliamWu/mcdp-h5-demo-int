package com.hkbea.batch.mtb;

import com.hkbea.batch.mtb.crm.CrmExtractRequest;
import com.hkbea.batch.mtb.util.BatchConfig;
import com.hkbea.batch.mtb.util.DBUtil;
import java.sql.SQLException;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.Locale;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** HTH standalone launcher; CRM_EXTRACT [yyyyMMdd] is the production extract task. */
public final class MTBBatchMain {
    private static final Logger LOG = LoggerFactory.getLogger(MTBBatchMain.class);
    public static final String BATCH_TYPE_CRM_EXTRACT = "CRM_EXTRACT";
    public static final String BATCH_TYPE_LOAD_DATA = "LOAD_DATA";
    public static final String BATCH_TYPE_PROCESS_TXN = "PROCESS_TXN";
    public static final String BATCH_TYPE_GENERATE_REPORT = "GENERATE_REPORT";
    private static final Set<String> TYPES = Set.of(BATCH_TYPE_CRM_EXTRACT,
        BATCH_TYPE_LOAD_DATA, BATCH_TYPE_PROCESS_TXN, BATCH_TYPE_GENERATE_REPORT);

    private MTBBatchMain() { }

    public static void main(String[] args) { System.exit(run(args, Clock.systemUTC())); }

    static int run(String[] args, Clock clock) {
        String type = args.length == 0 ? "" : args[0].toUpperCase(Locale.ROOT);
        LocalDate day = null;
        try {
            if (!TYPES.contains(type)) throw new IllegalArgumentException();
            if (BATCH_TYPE_CRM_EXTRACT.equals(type)) {
                if (args.length > 2) throw new IllegalArgumentException();
                day = CrmExtractRequest.parseDay(args.length == 2 ? args[1] : null, clock);
            }
        } catch (RuntimeException e) {
            LOG.error("Usage: MTBBatch CRM_EXTRACT [yyyyMMdd]; invalid task or date. "
                + "Scaffold tasks: LOAD_DATA, PROCESS_TXN, GENERATE_REPORT");
            return 2;
        }

        String stage = "CONFIG";
        String started = ZonedDateTime.now(CrmExtractRequest.BUSINESS_ZONE).toString();
        boolean legacyConnection = false;
        try {
            BatchConfig config = BatchConfig.getInstance();
            MTBBatchExecutor executor = new MTBBatchExecutor();
            if (BATCH_TYPE_CRM_EXTRACT.equals(type)) {
                stage = "EXTRACT";
                executor.executeCrmExtract(config, day);
            } else {
                // Preserve pre-existing example tasks; CRM never calls these.
                stage = "SCAFFOLD";
                legacyConnection = true;
                DBUtil.init(config);
                switch (type) {
                    case BATCH_TYPE_LOAD_DATA -> executor.executeLoadData(args);
                    case BATCH_TYPE_PROCESS_TXN -> executor.executeProcessTxn(args);
                    case BATCH_TYPE_GENERATE_REPORT -> executor.executeGenerateReport(args);
                    default -> throw new IllegalStateException();
                }
            }
            return 0;
        } catch (Exception e) {
            String prefix = BATCH_TYPE_CRM_EXTRACT.equals(type) ? "HTH_CRM_1293" : "HTH_BATCH";
            LOG.error("{} stage=END status=FAILED phase={} start={} end={} date={} count=0 size=-1 errorType={} sqlState={} vendorCode={}",
                prefix, stage, started, ZonedDateTime.now(CrmExtractRequest.BUSINESS_ZONE), day, e.getClass().getSimpleName(),
                e instanceof SQLException sql ? sql.getSQLState() : "-",
                e instanceof SQLException sql ? sql.getErrorCode() : 0);
            if (e instanceof BatchConfig.ConfigurationException) LOG.error("{}", e.getMessage());
            // Driver exception messages can contain JDBC URLs, credentials or row data.
            return 50;
        } finally {
            if (legacyConnection) DBUtil.close();
        }
    }
}
