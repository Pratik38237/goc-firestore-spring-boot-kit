package com.goc.firestore.service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.locks.ReentrantLock;

import com.goc.firestore.autoconfigure.FirestoreProperties;

import org.springframework.util.StringUtils;

/**
 * Appends one line per Firestore {@code save} / {@code delete} to a dedicated operations
 * log file configured by the consuming application.
 */
public final class FirestoreOperationAuditLogger {

    static final String DEFAULT_LOG_FILENAME = "firestore-operations.log";
    private static final String DEFAULT_LOG_LOCATION = "logs/" + DEFAULT_LOG_FILENAME;
    private static final ZoneId OPERATIONS_LOG_ZONE = ZoneId.of("Asia/Kolkata");
    private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter
            .ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSSSSSSS");

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
        String configured = properties.getOperationsLogLocation();
        if (!StringUtils.hasText(configured)) {
            configured = DEFAULT_LOG_LOCATION;
        }
        return new FirestoreOperationAuditLogger(true, resolveOperationsLogFile(configured));
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

    static String formatTimestamp(Instant instant) {
        return TIMESTAMP_FORMAT.format(instant.atZone(OPERATIONS_LOG_ZONE)) + " IST";
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
        line.append(formatTimestamp(Instant.now())).append(" | ");
        line.append(operation).append(" | ");
        line.append(path).append(" | ");
        line.append(durationMs).append("ms | ");
        line.append(status);
        if (!success && StringUtils.hasText(errorMessage)) {
            line.append(" | ").append(sanitize(errorMessage));
        }
        return line.toString();
    }

    /**
     * Resolves {@code location} to a log file path. Folders receive
     * {@value #DEFAULT_LOG_FILENAME}; values ending in {@code .log} are treated as files.
     */
    static Path resolveOperationsLogFile(String location) {
        String normalized = stripFilePrefix(location.trim());
        Path resolved = toAbsolutePath(normalized);
        if (treatAsDirectory(location, resolved)) {
            return resolved.resolve(DEFAULT_LOG_FILENAME).normalize();
        }
        return resolved;
    }

    private static boolean treatAsDirectory(String rawLocation, Path resolved) {
        if (rawLocation.endsWith("/") || rawLocation.endsWith("\\")) {
            return true;
        }
        if (Files.exists(resolved) && Files.isDirectory(resolved)) {
            return true;
        }
        String fileName = resolved.getFileName().toString();
        return !fileName.toLowerCase().endsWith(".log");
    }

    private static String stripFilePrefix(String location) {
        if (location.regionMatches(true, 0, "file:", 0, 5)) {
            return location.substring(5);
        }
        return location;
    }

    private static Path toAbsolutePath(String normalized) {
        Path path = Path.of(normalized);
        if (!path.isAbsolute()) {
            String userDir = System.getProperty("user.dir", ".");
            path = Path.of(userDir).resolve(path).normalize();
        }
        return path.normalize();
    }

    private static String sanitize(String message) {
        return message.replace('\n', ' ').replace('\r', ' ');
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
