package com.goc.firestore.service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;

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
    void formatTimestampUsesIndiaStandardTime() {
        assertThat(FirestoreOperationAuditLogger.formatTimestamp(
                Instant.parse("2026-06-04T01:20:43.810225800Z")))
                .isEqualTo("2026-06-04T06:50:43.810225800 IST");
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
    void fromPropertiesResolvesFilePathAgainstUserDir() {
        FirestoreProperties properties = new FirestoreProperties();
        properties.setOperationsLogEnabled(true);
        properties.setOperationsLogLocation("logs/test-firestore-operations.log");

        FirestoreOperationAuditLogger logger = FirestoreOperationAuditLogger.from(properties);
        assertThat(logger.isEnabled()).isTrue();
        assertThat(logger.getLogFile()).isEqualTo(
                Path.of(System.getProperty("user.dir")).resolve("logs/test-firestore-operations.log").normalize());
    }

    @Test
    void resolveOperationsLogFileTreatsFolderPath() {
        Path logFile = FirestoreOperationAuditLogger.resolveOperationsLogFile("logs/");
        assertThat(logFile).isEqualTo(
                Path.of(System.getProperty("user.dir")).resolve("logs/firestore-operations.log").normalize());
    }

    @Test
    void resolveOperationsLogFileTreatsFolderWithoutTrailingSlash() {
        Path logFile = FirestoreOperationAuditLogger.resolveOperationsLogFile("audit-logs");
        assertThat(logFile).isEqualTo(
                Path.of(System.getProperty("user.dir")).resolve("audit-logs/firestore-operations.log").normalize());
    }

    @Test
    void resolveOperationsLogFileTreatsExplicitFilePath() {
        Path logFile = FirestoreOperationAuditLogger.resolveOperationsLogFile("logs/custom-audit.log");
        assertThat(logFile).isEqualTo(
                Path.of(System.getProperty("user.dir")).resolve("logs/custom-audit.log").normalize());
    }

    @Test
    void fromPropertiesUsesFolderAndCreatesStandardFileName(@TempDir Path projectLogs) throws Exception {
        FirestoreProperties properties = new FirestoreProperties();
        properties.setOperationsLogEnabled(true);
        properties.setOperationsLogLocation(projectLogs.toString());

        FirestoreOperationAuditLogger logger = FirestoreOperationAuditLogger.from(properties);
        logger.log("SAVE", "clients/x/jobs/y", 1, true, null);

        Path expectedFile = projectLogs.resolve(FirestoreOperationAuditLogger.DEFAULT_LOG_FILENAME);
        assertThat(Files.exists(expectedFile)).isTrue();
        assertThat(Files.readString(expectedFile)).contains("SAVE");
    }

    @Test
    void deprecatedOperationsLogFilePropertyStillWorks() {
        FirestoreProperties properties = new FirestoreProperties();
        properties.setOperationsLogEnabled(true);
        properties.setOperationsLogFile("logs/legacy.log");

        FirestoreOperationAuditLogger logger = FirestoreOperationAuditLogger.from(properties);
        assertThat(logger.getLogFile()).isEqualTo(
                Path.of(System.getProperty("user.dir")).resolve("logs/legacy.log").normalize());
    }

    @Test
    void disabledLoggerDoesNotWrite() {
        FirestoreOperationAuditLogger logger = FirestoreOperationAuditLogger.disabled();
        assertThat(logger.isEnabled()).isFalse();
        logger.log("SAVE", "a/b", 1, true, null);
    }
}
