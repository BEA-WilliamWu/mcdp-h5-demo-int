package com.hkbea.batch.mtb.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Database Utility - manages database connections for batch processing
 *
 * Uses Oracle JDBC driver with configuration from BatchConfig.
 *
 * @author HTH Batch Team
 * @version 1.0.0
 */
public class DBUtil {

    private static final Logger logger = LoggerFactory.getLogger(DBUtil.class);

    private static Connection connection;
    private static BatchConfig config;

    /**
     * Initialize database connection with configuration
     */
    public static synchronized void init(BatchConfig batchConfig) throws Exception {
        config = batchConfig;
        connection = openConnection(batchConfig);
    }

    /** Caller owns this connection; CRM avoids the scaffold's global transaction. */
    public static Connection openConnection(BatchConfig batchConfig) throws SQLException {
        String driver = batchConfig.getProperty(BatchConfig.DB_DRIVER_CLASS, "oracle.jdbc.OracleDriver");
        String url = batchConfig.required(BatchConfig.DB_CONN_STRING);
        String user = batchConfig.required(BatchConfig.DB_USERNAME);
        String password = batchConfig.required(BatchConfig.DB_PASSWORD);
        try {
            Class.forName(driver);
        } catch (ClassNotFoundException e) {
            throw new SQLException("Configured JDBC driver unavailable", e);
        }
        Connection opened = DriverManager.getConnection(url, user, password);
        try {
            opened.setAutoCommit(batchConfig.getBooleanProperty(BatchConfig.DB_AUTOCOMMIT, false));
            return opened;
        } catch (SQLException e) {
            try { opened.close(); } catch (SQLException close) { e.addSuppressed(close); }
            throw e;
        }
    }

    /**
     * Get the current database connection
     */
    public static synchronized Connection getConnection() throws SQLException {
        if (connection == null || connection.isClosed()) {
            throw new SQLException("Database connection not initialized. Call init() first.");
        }
        return connection;
    }

    /**
     * Close the database connection
     */
    public static synchronized void close() {
        if (connection != null) {
            try {
                connection.close();
                logger.info("Database connection closed");
            } catch (SQLException e) {
                logger.error("Error closing database connection", e);
            }
            connection = null;
        }
    }

    /**
     * Close a ResultSet silently
     */
    public static void close(ResultSet rs) {
        if (rs != null) {
            try {
                rs.close();
            } catch (SQLException e) {
                logger.error("Error closing ResultSet", e);
            }
        }
    }

    /**
     * Close a Statement silently
     */
    public static void close(Statement stmt) {
        if (stmt != null) {
            try {
                stmt.close();
            } catch (SQLException e) {
                logger.error("Error closing Statement", e);
            }
        }
    }

    /**
     * Commit the current transaction
     */
    public static void commit() throws SQLException {
        if (connection != null && !connection.getAutoCommit()) {
            connection.commit();
        }
    }

    /**
     * Rollback the current transaction
     */
    public static void rollback() {
        if (connection != null) {
            try {
                if (!connection.getAutoCommit()) {
                    connection.rollback();
                }
            } catch (SQLException e) {
                logger.error("Error rolling back transaction", e);
            }
        }
    }
}
