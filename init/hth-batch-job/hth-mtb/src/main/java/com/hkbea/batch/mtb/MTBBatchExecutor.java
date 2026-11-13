package com.hkbea.batch.mtb;

import com.hkbea.batch.mtb.util.DBUtil;
import com.hkbea.batch.mtb.util.BatchConfig;
import com.hkbea.batch.mtb.crm.CrmExtractRequest;
import com.hkbea.batch.mtb.crm.CrmExtractService;
import com.hkbea.batch.mtb.crm.JdbcCrmEventRepository;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * MTB Batch Executor - handles the actual batch processing logic
 *
 * @author HTH Batch Team
 * @version 1.0.0
 */
public class MTBBatchExecutor {

    private static final Logger logger = LoggerFactory.getLogger(MTBBatchExecutor.class);

    /** Independent HTH/DSP CRM extract; no BCO processor, utility or configuration dependency. */
    public CrmExtractService.Result executeCrmExtract(BatchConfig config, LocalDate day) throws Exception {
        CrmExtractRequest request = new CrmExtractRequest(day,
            Path.of(config.required(BatchConfig.BATCH_OUTPUT_DIR)),
            config.getProperty("crm.file.pattern", CrmExtractRequest.DEFAULT_PATTERN));
        String start = ZonedDateTime.now(CrmExtractRequest.BUSINESS_ZONE).toString();
        logger.info("HTH_CRM_1293 stage=START time={} date={} file={}", start, day, request.fileName());
        CrmExtractService service = new CrmExtractService(
            new JdbcCrmEventRepository(() -> DBUtil.openConnection(config)));
        CrmExtractService.Result result = service.extract(request);
        logger.info("HTH_CRM_1293 stage=END status=SUCCESS start={} end={} count={} size={} file={}",
            start, ZonedDateTime.now(CrmExtractRequest.BUSINESS_ZONE), result.count(), result.size(), result.file());
        return result;
    }

    /**
     * Execute LOAD_DATA batch - load MTB data from source system
     */
    public void executeLoadData(String[] args) throws Exception {
        logger.info("Executing LOAD_DATA batch...");
        String timestamp = args.length > 1 ? args[1] : String.valueOf(System.currentTimeMillis());
        logger.info("Timestamp: {}", timestamp);

        Connection conn = null;
        PreparedStatement pstmt = null;
        ResultSet rs = null;

        try {
            conn = DBUtil.getConnection();
            conn.setAutoCommit(false);

            // Example: Load MTB transaction data
            String sql = "SELECT MTB_TXN_ID, MTB_TXN_TYPE, MTB_AMOUNT, MTB_CCY, MTB_VALUE_DATE " +
                         "FROM MTB_TRANSACTIONS " +
                         "WHERE LOAD_STATUS = 'PENDING' " +
                         "ORDER BY MTB_TXN_ID";

            pstmt = conn.prepareStatement(sql);
            rs = pstmt.executeQuery();

            int count = 0;
            List<Map<String, Object>> batchRecords = new ArrayList<>();

            while (rs.next()) {
                Map<String, Object> record = new HashMap<>();
                record.put("txnId", rs.getString("MTB_TXN_ID"));
                record.put("txnType", rs.getString("MTB_TXN_TYPE"));
                record.put("amount", rs.getBigDecimal("MTB_AMOUNT"));
                record.put("currency", rs.getString("MTB_CCY"));
                record.put("valueDate", rs.getDate("MTB_VALUE_DATE"));
                batchRecords.add(record);
                count++;

                if (count % 1000 == 0) {
                    logger.info("Loaded {} records so far...", count);
                }
            }

            logger.info("Total records loaded: {}", count);

            // Process loaded data
            processDataBatch(conn, batchRecords);

            conn.commit();
            logger.info("LOAD_DATA batch completed successfully");

        } catch (SQLException e) {
            logger.error("Error in LOAD_DATA batch: {}", e.getMessage(), e);
            if (conn != null) {
                try {
                    conn.rollback();
                } catch (SQLException ex) {
                    logger.error("Error rolling back transaction", ex);
                }
            }
            throw e;
        } finally {
            DBUtil.close(rs);
            DBUtil.close(pstmt);
        }
    }

    /**
     * Execute PROCESS_TXN batch - process MTB transactions
     */
    public void executeProcessTxn(String[] args) throws Exception {
        logger.info("Executing PROCESS_TXN batch...");
        String timestamp = args.length > 1 ? args[1] : String.valueOf(System.currentTimeMillis());
        logger.info("Timestamp: {}", timestamp);

        Connection conn = null;
        PreparedStatement pstmt = null;

        try {
            conn = DBUtil.getConnection();
            conn.setAutoCommit(false);

            // Example: Update transaction status
            String sql = "UPDATE MTB_TRANSACTIONS " +
                         "SET LOAD_STATUS = 'PROCESSED', " +
                         "    UPDATE_TIME = SYSDATE, " +
                         "    BATCH_TIMESTAMP = ? " +
                         "WHERE LOAD_STATUS = 'PENDING'";

            pstmt = conn.prepareStatement(sql);
            pstmt.setString(1, timestamp);

            int updatedCount = pstmt.executeUpdate();
            logger.info("Processed {} transactions", updatedCount);

            conn.commit();
            logger.info("PROCESS_TXN batch completed successfully");

        } catch (SQLException e) {
            logger.error("Error in PROCESS_TXN batch: {}", e.getMessage(), e);
            if (conn != null) {
                try {
                    conn.rollback();
                } catch (SQLException ex) {
                    logger.error("Error rolling back transaction", ex);
                }
            }
            throw e;
        } finally {
            DBUtil.close(pstmt);
        }
    }

    /**
     * Execute GENERATE_REPORT batch - generate MTB reports
     */
    public void executeGenerateReport(String[] args) throws Exception {
        logger.info("Executing GENERATE_REPORT batch...");
        String timestamp = args.length > 1 ? args[1] : String.valueOf(System.currentTimeMillis());
        String outputFile = args.length > 2 ? args[2] : "mtb_report_" + timestamp + ".csv";
        logger.info("Timestamp: {}", timestamp);
        logger.info("Output file: {}", outputFile);

        Connection conn = null;
        PreparedStatement pstmt = null;
        ResultSet rs = null;

        try {
            conn = DBUtil.getConnection();

            // Example: Generate summary report
            String sql = "SELECT MTB_TXN_TYPE, " +
                         "       COUNT(*) AS TXN_COUNT, " +
                         "       SUM(MTB_AMOUNT) AS TOTAL_AMOUNT, " +
                         "       MTB_CCY " +
                         "FROM MTB_TRANSACTIONS " +
                         "WHERE TRUNC(CREATE_TIME) = TRUNC(SYSDATE) " +
                         "GROUP BY MTB_TXN_TYPE, MTB_CCY " +
                         "ORDER BY MTB_TXN_TYPE";

            pstmt = conn.prepareStatement(sql);
            rs = pstmt.executeQuery();

            int count = 0;
            while (rs.next()) {
                logger.info("Report Row: Type={}, Count={}, Amount={}, CCY={}",
                        rs.getString("MTB_TXN_TYPE"),
                        rs.getInt("TXN_COUNT"),
                        rs.getBigDecimal("TOTAL_AMOUNT"),
                        rs.getString("MTB_CCY"));
                count++;
            }

            logger.info("Report generated with {} rows", count);
            logger.info("GENERATE_REPORT batch completed successfully");

        } catch (SQLException e) {
            logger.error("Error in GENERATE_REPORT batch: {}", e.getMessage(), e);
            throw e;
        } finally {
            DBUtil.close(rs);
            DBUtil.close(pstmt);
        }
    }

    /**
     * Process a batch of loaded data records
     */
    private void processDataBatch(Connection conn, List<Map<String, Object>> records) throws SQLException {
        if (records.isEmpty()) {
            logger.info("No records to process");
            return;
        }

        PreparedStatement pstmt = null;
        try {
            String sql = "INSERT INTO MTB_PROCESSED_DATA (TXN_ID, TXN_TYPE, AMOUNT, CCY, VALUE_DATE, PROCESS_TIME) " +
                         "VALUES (?, ?, ?, ?, ?, SYSDATE)";
            pstmt = conn.prepareStatement(sql);

            int batchCount = 0;
            for (Map<String, Object> record : records) {
                pstmt.setString(1, (String) record.get("txnId"));
                pstmt.setString(2, (String) record.get("txnType"));
                pstmt.setBigDecimal(3, (java.math.BigDecimal) record.get("amount"));
                pstmt.setString(4, (String) record.get("currency"));
                pstmt.setDate(5, (java.sql.Date) record.get("valueDate"));
                pstmt.addBatch();
                batchCount++;

                if (batchCount % 500 == 0) {
                    pstmt.executeBatch();
                    logger.info("Processed batch of {} records", batchCount);
                }
            }

            if (batchCount % 500 != 0) {
                pstmt.executeBatch();
            }

            logger.info("Total records processed: {}", batchCount);

        } finally {
            DBUtil.close(pstmt);
        }
    }
}
