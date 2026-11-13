package com.hkbea.batch.mtb.crm;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.sql.SQLException;

/** Owns file publication; JDBC and the wire format are separate collaborators. */
public final class CrmExtractService {
    private final CrmEventRepository repository;

    public CrmExtractService(CrmEventRepository repository) {
        this.repository = repository;
    }

    public record Result(Path file, long count, long size) { }

    public Result extract(CrmExtractRequest request) throws IOException, SQLException {
        Files.createDirectories(request.directory());
        Path directory = request.directory().toRealPath();
        Path output = directory.resolve(request.fileName());
        try (FileChannel channel = FileChannel.open(directory.resolve(request.fileName() + ".lock"),
                StandardOpenOption.CREATE, StandardOpenOption.WRITE);
             FileLock lock = channel.tryLock()) {
            if (lock == null) throw new IOException("Extract already running for this date");
            return publish(request, directory, output);
        } catch (OverlappingFileLockException e) {
            throw new IOException("Extract already running for this date", e);
        }
        // Keep the lock file: deleting it would permit concurrent locks on different inodes.
    }

    private Result publish(CrmExtractRequest request, Path directory, Path output)
            throws IOException, SQLException {
        Path temporary = Files.createTempFile(directory, request.fileName() + '.', ".tmp");
        try {
            long count;
            try (BufferedWriter writer = Files.newBufferedWriter(temporary, StandardCharsets.UTF_8)) {
                CrmCsvWriter csv = new CrmCsvWriter(writer, request.day());
                repository.readDay(request.day(), csv::append);
                count = csv.finish();
            }
            long size = Files.size(temporary);
            // No non-atomic fallback: a failed run must not replace an existing complete file.
            Files.move(temporary, output, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            return new Result(output, count, size);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }
}
