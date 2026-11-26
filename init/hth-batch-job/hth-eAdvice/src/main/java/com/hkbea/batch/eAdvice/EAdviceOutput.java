package com.hkbea.batch.eAdvice;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

/** Protects the one delivery file allowed per advice type and business date. */
final class EAdviceOutput {
    private EAdviceOutput() { }

    @FunctionalInterface
    interface Job {
        void run(Path output) throws IOException, SQLException;
    }

    static void withLock(Path outputPath, Job job) throws SQLException {
        Path absolute = outputPath.toAbsolutePath().normalize();
        try {
            Path directory = Files.createDirectories(absolute.getParent()).toRealPath();
            Path output = directory.resolve(absolute.getFileName());
            Path lockPath = directory.resolve(output.getFileName() + ".lock");
            try (FileChannel channel = FileChannel.open(lockPath, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
                 FileLock lock = channel.tryLock()) {
                if (lock == null) {
                    throw new IOException("eAdvice already running for " + output);
                }
                job.run(output);
            }
            // Keep the lock file so another run cannot create a second lock on a different inode.
        } catch (IOException | OverlappingFileLockException e) {
            throw new SQLException("Failed to complete eAdvice output " + absolute + ": " + e.getMessage(), e);
        }
    }

    /** Called under the output lock. Returns false when a zero-row rerun keeps the existing file. */
    static boolean publish(Path output, String day, List<String> lines, Connection connection,
                           List<String> paymentIds, String flag) throws IOException, SQLException {
        if (Files.exists(output, LinkOption.NOFOLLOW_LINKS)) {
            if (lines.isEmpty() && Files.isRegularFile(output, LinkOption.NOFOLLOW_LINKS)) {
                return false;
            }
            throw new IOException("Output already exists and was retained: " + output);
        }

        // Match the previous output file permissions by creating the file with the process umask.
        Path temporary = Files.createFile(output.resolveSibling(output.getFileName() + "." + UUID.randomUUID() + ".tmp"));
        try {
            write(temporary, day, lines);
            publishAndMark(temporary, output, connection, paymentIds, flag);
            return true;
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private static void write(Path temporary, String day, List<String> lines) throws IOException {
        try (BufferedWriter writer = Files.newBufferedWriter(temporary, Charset.defaultCharset())) {
            writer.write("01" + day + "CCB" + " ".repeat(487));
            writer.newLine();
            for (String line : lines) {
                writer.write(line);
                writer.newLine();
            }

            long checksum = Long.parseLong(day) + lines.size();
            writer.write("99" + String.format("%08d%010d", lines.size(), checksum) + " ".repeat(480));
            writer.newLine();
        }
    }

    private static void publishAndMark(Path temporary, Path output, Connection connection,
                                       List<String> paymentIds, String flag) throws IOException, SQLException {
        boolean hasUpdates = !paymentIds.isEmpty();
        if (hasUpdates && connection.getAutoCommit()) {
            throw new SQLException("eAdvice requires jdbc.conn.autocommit=false to publish and mark payments");
        }
        try {
            if (hasUpdates) {
                updateBatchStatus(connection, paymentIds, flag);
            }
            Files.move(temporary, output, StandardCopyOption.ATOMIC_MOVE);
            if (hasUpdates) {
                connection.commit();
            }
        } catch (SQLException | IOException failure) {
            if (hasUpdates) {
                try {
                    connection.rollback();
                } catch (SQLException rollbackFailure) {
                    failure.addSuppressed(rollbackFailure);
                }
            }
            // A commit error can leave the outcome uncertain. Retain any published file for reconciliation.
            throw failure;
        }
    }

    private static void updateBatchStatus(Connection connection, List<String> paymentIds, String flag)
            throws SQLException {
        String sql = "UPDATE HTH_BEA.HTH_PAYMENT_PAYOUT "
                + "SET BATCH_STATUS_FLAG = NVL(BATCH_STATUS_FLAG, '') || ? WHERE PAYMENT_ID = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, flag);
            for (String id : paymentIds) {
                statement.setString(2, id);
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }
}
