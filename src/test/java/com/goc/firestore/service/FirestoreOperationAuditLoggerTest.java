package com.goc.firestore.service;

import java.nio.file.Files;
import java.nio.file.Path;

import com.goc.firestore.autoconfigure.FirestoreProperties;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

class FirestoreOperationAuditLoggerTest {

    @TempDir
    Path tempDir;

    @Test
    void formatPathJoinsSegments() {
        assertThat(FirestoreOperationAuditLogger.formatPath("clients", "c1", "jobs", "j1"))
                .isEqualTo("clients/c1/jobs/j1");
    }

    @Test
    void formatLineIncludesOperationPathDurationAndStatus() {
        String line = FirestoreOperationAuditLogger.formatLine("SAVE", "clients/c1/jobs/j1", 42, true, null);
        assertThat(line).contains("SAVE");
        assertThat(line).contains("clients/c1/jobs/j1");
        assertThat(line).contains("42ms");
        assertThat(line).contains("OK");
    }

    @Test
    void formatLineIncludesErrorOnFailure() {
        String line = FirestoreOperationAuditLogger.formatLine("DELETE", "a/b", 1, false, "timeout");
        assertThat(line).contains("FAILED");
        assertThat(line).contains("timeout");
    }

    @Test
    void appendsEntryToConfiguredFile() throws Exception {
        Path logFile = tempDir.resolve("firestore-ops.log");
        FirestoreOperationAuditLogger logger = new FirestoreOperationAuditLogger(true, logFile);

        logger.log("SAVE", "clients/x/jobs/y", 10, true, null);
        logger.log("DELETE", "clients/x/jobs/y", 5, false, "not found");

        String content = Files.readString(logFile);
        assertThat(content).contains("SAVE");
        assertThat(content).contains("DELETE");
        assertThat(content).contains("clients/x/jobs/y");
        assertThat(content).contains("FAILED");
        assertThat(content).contains("not found");
        assertThat(content.split(System.lineSeparator())).hasSizeGreaterThanOrEqualTo(2);
    }

    @Test
    void fromPropertiesResolvesRelativePathAgainstUserDir() {
        FirestoreProperties properties = new FirestoreProperties();
        properties.setOperationsLogEnabled(true);
        properties.setOperationsLogFile("logs/test-firestore-operations.log");

        FirestoreOperationAuditLogger logger = FirestoreOperationAuditLogger.from(properties);
        assertThat(logger.isEnabled()).isTrue();
        assertThat(logger.getLogFile()).isEqualTo(
                Path.of(System.getProperty("user.dir")).resolve("logs/test-firestore-operations.log").normalize());
    }

    @Test
    void disabledLoggerDoesNotWrite() {
        FirestoreOperationAuditLogger logger = FirestoreOperationAuditLogger.disabled();
        assertThat(logger.isEnabled()).isFalse();
        logger.log("SAVE", "a/b", 1, true, null);
    }
}
