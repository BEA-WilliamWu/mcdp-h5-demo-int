package com.hkbea.batch.mtb.example;

import com.hkbea.batch.mtb.util.BatchConfig;
import com.hkbea.batch.mtb.util.DBUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Example MTB Batch Job - demonstrates how to create a custom batch job
 *
 * This is a template class showing the pattern for implementing batch jobs.
 * Copy this class and modify it for your specific batch requirements.
 *
 * @author HTH Batch Team
 * @version 1.0.0
 */
public class ExampleMTBBatch {

    private static final Logger logger = LoggerFactory.getLogger(ExampleMTBBatch.class);

    /**
     * Main method - can be run independently
     */
    public static void main(String[] args) {
        logger.info("Example MTB Batch Job Starting...");

        try {
            // Initialize configuration
            BatchConfig config = BatchConfig.getInstance();

            // Initialize database
            DBUtil.init(config);

            // Execute batch logic
            ExampleMTBBatch batch = new ExampleMTBBatch();
            batch.execute(args);

            logger.info("Example MTB Batch Job completed successfully");

        } catch (Exception e) {
            logger.error("Example MTB Batch Job failed", e);
            System.exit(1);
        } finally {
            try {
                DBUtil.close();
            } catch (Exception e) {
                logger.error("Error closing database", e);
            }
        }
    }

    /**
     * Execute the batch job
     */
    public void execute(String[] args) throws Exception {
        String timestamp = args.length > 0 ? args[0] : String.valueOf(System.currentTimeMillis());
        logger.info("Executing with timestamp: {}", timestamp);

        Connection conn = null;
        PreparedStatement pstmt = null;
        ResultSet rs = null;

        try {
            conn = DBUtil.getConnection();
            conn.setAutoCommit(false);

            // Example 1: Query data
            String selectSql = "SELECT TXN_ID, AMOUNT, CCY FROM MTB_TRANSACTIONS WHERE CREATE_TIME > SYSDATE - 1";
            pstmt = conn.prepareStatement(selectSql);
            rs = pstmt.executeQuery();

            int count = 0;
            while (rs.next()) {
                String txnId = rs.getString("TXN_ID");
                double amount = rs.getDouble("AMOUNT");
                String ccy = rs.getString("CCY");
                logger.debug("Record: txnId={}, amount={}, ccy={}", txnId, amount, ccy);
                count++;
            }
            rs.close();
            pstmt.close();

            logger.info("Queried {} records", count);

            // Example 2: Update data
            String updateSql = "UPDATE MTB_TRANSACTIONS SET STATUS = 'PROCESSED' WHERE STATUS = 'PENDING'";
            pstmt = conn.prepareStatement(updateSql);
            int updated = pstmt.executeUpdate();
            logger.info("Updated {} records", updated);
            pstmt.close();

            // Example 3: Insert data
            String insertSql = "INSERT INTO MTB_BATCH_LOG (BATCH_NAME, BATCH_TIME, RECORD_COUNT) VALUES (?, SYSDATE, ?)";
            pstmt = conn.prepareStatement(insertSql);
            pstmt.setString(1, "ExampleMTBBatch");
            pstmt.setInt(2, count);
            int inserted = pstmt.executeUpdate();
            logger.info("Inserted {} log records", inserted);
            pstmt.close();

            // Commit transaction
            conn.commit();

        } catch (SQLException e) {
            logger.error("Error in batch execution", e);
            if (conn != null) {
                try {
                    conn.rollback();
                } catch (SQLException ex) {
                    logger.error("Error rolling back", ex);
                }
            }
            throw e;
        } finally {
            DBUtil.close(rs);
            DBUtil.close(pstmt);
        }
    }
}
