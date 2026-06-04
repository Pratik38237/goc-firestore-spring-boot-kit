package com.goc.firestore.autoconfigure;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for the Firestore starter.
 *
 * <p>The consuming (parent) application is responsible for supplying the Firebase
 * service account JSON. This library never bundles credentials of any kind.</p>
 *
 * <p>Example {@code application.yml}:</p>
 * <pre>
 * goc:
 *   firestore:
 *     enabled: true
 *     credentials-location: file:/etc/secrets/firebase-service-account.json
 *     project-id: my-gcp-project          # optional
 *     database-id: "(default)"            # optional, for named databases
 *     operations-log-enabled: true         # optional, dedicated save/delete audit file
 *     operations-log-file: logs/firestore-operations.log
 * </pre>
 */
@ConfigurationProperties(prefix = "goc.firestore")
public class FirestoreProperties {

    /**
     * Whether Firebase/Firestore auto-configuration is enabled.
     */
    private boolean enabled = true;

    /**
     * Spring {@code Resource} location of the Firebase service account JSON provided
     * by the consuming application, e.g. {@code file:/path/to/key.json} or
     * {@code classpath:firebase-service-account.json}.
     *
     * <p>When left blank, Google Application Default Credentials (ADC) are used, which
     * is convenient for environments such as Cloud Run / GKE / Cloud Functions where
     * credentials are provided by the platform.</p>
     */
    private String credentialsLocation;

    /**
     * Optional GCP project id. When not set, it is inferred from the credentials.
     */
    private String projectId;

    /**
     * Optional Firestore database id, used when targeting a named (non-default)
     * database. When not set, the default database is used.
     */
    private String databaseId;

    /**
     * When {@code true}, each {@code save} and {@code delete} on {@link com.goc.firestore.service.FirestoreService}
     * appends one line to {@link #operationsLogFile}.
     */
    private boolean operationsLogEnabled = false;

    /**
     * Dedicated audit log file for Firestore write operations. Relative paths are resolved
     * against {@code user.dir}. Supports a {@code file:} prefix.
     */
    private String operationsLogFile = "logs/firestore-operations.log";

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getCredentialsLocation() {
        return credentialsLocation;
    }

    public void setCredentialsLocation(String credentialsLocation) {
        this.credentialsLocation = credentialsLocation;
    }

    public String getProjectId() {
        return projectId;
    }

    public void setProjectId(String projectId) {
        this.projectId = projectId;
    }

    public String getDatabaseId() {
        return databaseId;
    }

    public void setDatabaseId(String databaseId) {
        this.databaseId = databaseId;
    }

    public boolean isOperationsLogEnabled() {
        return operationsLogEnabled;
    }

    public void setOperationsLogEnabled(boolean operationsLogEnabled) {
        this.operationsLogEnabled = operationsLogEnabled;
    }

    public String getOperationsLogFile() {
        return operationsLogFile;
    }

    public void setOperationsLogFile(String operationsLogFile) {
        this.operationsLogFile = operationsLogFile;
    }
}
