package com.goc.firestore.service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.concurrent.locks.ReentrantLock;

import com.goc.firestore.autoconfigure.FirestoreProperties;

import org.springframework.util.StringUtils;

/**
 * Appends one line per Firestore {@code save} / {@code delete} to a dedicated operations
 * log file configured by the consuming application.
 */
public final class FirestoreOperationAuditLogger {

    private static final String DEFAULT_LOG_FILE = "logs/firestore-operations.log";

    private final boolean enabled;
    private final Path logFile;
    private final ReentrantLock writeLock = new ReentrantLock();

    FirestoreOperationAuditLogger(boolean enabled, Path logFile) {
        this.enabled = enabled;
        this.logFile = logFile;
    }

    public static FirestoreOperationAuditLogger from(FirestoreProperties properties) {
        if (properties == null || !properties.isOperationsLogEnabled()) {
            return disabled();
        }
        String configured = properties.getOperationsLogFile();
        String path = StringUtils.hasText(configured) ? configured : DEFAULT_LOG_FILE;
        return new FirestoreOperationAuditLogger(true, resolveLogPath(path));
    }

    public static FirestoreOperationAuditLogger disabled() {
        return new FirestoreOperationAuditLogger(false, null);
    }

    /**
     * Records the outcome of a save or delete. Never throws; failures to write are
     * reported to SLF4J only.
     */
    public void log(String operation, String path, long durationMs, boolean success, String errorMessage) {
        if (!enabled) {
            return;
        }
        String line = formatLine(operation, path, durationMs, success, errorMessage);
        writeLock.lock();
        try {
            ensureParentDirectory();
            Files.writeString(logFile, line + System.lineSeparator(), StandardCharsets.UTF_8,
                    java.nio.file.StandardOpenOption.CREATE,
                    java.nio.file.StandardOpenOption.APPEND);
        }
        catch (IOException e) {
            org.slf4j.LoggerFactory.getLogger(FirestoreOperationAuditLogger.class)
                    .warn("Failed to append Firestore operation audit log to {}: {}", logFile, e.getMessage());
        }
        finally {
            writeLock.unlock();
        }
    }

    static String formatPath(String... pathSegments) {
        if (pathSegments == null || pathSegments.length == 0) {
            return "";
        }
        return String.join("/", pathSegments);
    }

    static String formatLine(String operation, String path, long durationMs, boolean success, String errorMessage) {
        String status = success ? "OK" : "FAILED";
        StringBuilder line = new StringBuilder(128);
        line.append(Instant.now()).append(" | ");
        line.append(operation).append(" | ");
        line.append(path).append(" | ");
        line.append(durationMs).append("ms | ");
        line.append(status);
        if (!success && StringUtils.hasText(errorMessage)) {
            line.append(" | ").append(sanitize(errorMessage));
        }
        return line.toString();
    }

    private static String sanitize(String message) {
        return message.replace('\n', ' ').replace('\r', ' ');
    }

    private static Path resolveLogPath(String location) {
        String normalized = location.trim();
        if (normalized.regionMatches(true, 0, "file:", 0, 5)) {
            normalized = normalized.substring(5);
        }
        Path path = Path.of(normalized);
        if (!path.isAbsolute()) {
            String userDir = System.getProperty("user.dir", ".");
            path = Path.of(userDir).resolve(path).normalize();
        }
        return path;
    }

    private void ensureParentDirectory() throws IOException {
        Path parent = logFile.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
    }

    boolean isEnabled() {
        return enabled;
    }

    Path getLogFile() {
        return logFile;
    }
}
