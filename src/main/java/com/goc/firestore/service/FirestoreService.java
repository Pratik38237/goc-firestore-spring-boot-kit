package com.goc.firestore.service;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.SetOptions;
import com.google.cloud.firestore.WriteResult;

import org.springframework.util.Assert;

/**
 * Thin, reusable helper around {@link Firestore} for the common save / get / delete
 * operations, including documents nested in sub-collections.
 *
 * <p>Paths are expressed as alternating <em>collection, documentId</em> segments, so
 * the pattern
 * {@code db.collection("collectionName").document(collectionId).collection("subCollectionName").document(subCollectionId)}
 * becomes:</p>
 *
 * <pre>
 * firestoreService.delete(COLLECTION, collectionId, SUB_COLLECTION, subCollectionId);
 * firestoreService.scheduleDelete(10_000, COLLECTION, collectionId, SUB_COLLECTION, subCollectionId);
 * firestoreService.save(jobData, COLLECTION, collectionId, SUB_COLLECTION, subCollectionId);
 * Job job = firestoreService.get(Job.class, COLLECTION, collectionId, SUB_COLLECTION, subCollectionId);
 * </pre>
 *
 * <p>Each {@link #save} and {@link #delete} can append a line to the dedicated operations
 * log file when {@code goc.firestore.operations-log-enabled=true}.</p>
 *
 * <p>The write/read methods block on the underlying {@code ApiFuture} so callers get a
 * simple synchronous API. If you need the raw async {@code Firestore}, use
 * {@link #getFirestore()} or {@link #document(String...)}.</p>
 */
public class FirestoreService {

    private static final ScheduledExecutorService DELETE_SCHEDULER = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread thread = new Thread(r, "goc-firestore-delete-scheduler");
        thread.setDaemon(true);
        return thread;
    });

    private final Firestore firestore;
    private final FirestoreOperationAuditLogger operationAuditLogger;

    public FirestoreService(Firestore firestore) {
        this(firestore, FirestoreOperationAuditLogger.disabled());
    }

    public FirestoreService(Firestore firestore, FirestoreOperationAuditLogger operationAuditLogger) {
        this.firestore = firestore;
        this.operationAuditLogger = operationAuditLogger != null
                ? operationAuditLogger
                : FirestoreOperationAuditLogger.disabled();
    }

    /**
     * Builds a {@link DocumentReference} from alternating
     * {@code collection, documentId} path segments (e.g.
     * {@code "collectionName", collectionId, "subCollectionName", subCollectionId}).
     */
    public DocumentReference document(String... pathSegments) {
        Assert.notNull(pathSegments, "pathSegments must not be null");
        Assert.isTrue(pathSegments.length >= 2 && pathSegments.length % 2 == 0,
                "pathSegments must contain an even number of entries (collection, documentId, ...)");

        DocumentReference reference = this.firestore.collection(pathSegments[0]).document(pathSegments[1]);
        for (int i = 2; i < pathSegments.length; i += 2) {
            reference = reference.collection(pathSegments[i]).document(pathSegments[i + 1]);
        }
        return reference;
    }

    /**
     * Pushes (creates or fully overwrites) a document at the given path.
     *
     * @param data         the POJO / map to persist
     * @param pathSegments alternating collection / documentId segments
     * @return the write result reported by Firestore
     */
    public WriteResult save(Object data, String... pathSegments)
            throws ExecutionException, InterruptedException {
        String path = FirestoreOperationAuditLogger.formatPath(pathSegments);
        long startNanos = System.nanoTime();
        try {
            WriteResult result = document(pathSegments).set(data, SetOptions.merge()).get();
            operationAuditLogger.log("SAVE", path, elapsedMs(startNanos), true, null);
            return result;
        }
        catch (ExecutionException | InterruptedException | RuntimeException e) {
            operationAuditLogger.log("SAVE", path, elapsedMs(startNanos), false, e.getMessage());
            throw e;
        }
    }

    /**
     * Deletes the document at the given path.
     *
     * <p>Mirrors:
     * {@code db.collection(COLLECTION).document(collectionId)
     *           .collection(SUB_COLLECTION).document(subCollectionId).delete()}.</p>
     *
     * @param pathSegments alternating collection / documentId segments
     * @return the write result reported by Firestore
     */
    public WriteResult delete(String... pathSegments)
            throws ExecutionException, InterruptedException {
        return executeDelete(pathSegments);
    }

    /**
     * Schedules deletion of the document after {@code delayMillis}. Returns immediately;
     * the delete (and audit log entry) runs once the delay elapses.
     *
     * @param delayMillis  delay before delete; {@code 0} runs the delete on the scheduler
     *                     thread as soon as possible (non-blocking for the caller)
     * @param pathSegments alternating collection / documentId segments
     */
    public void scheduleDelete(long delayMillis, String... pathSegments) {
        Assert.isTrue(delayMillis >= 0, "delayMillis must not be negative");
        Assert.notNull(pathSegments, "pathSegments must not be null");
        String[] pathCopy = pathSegments.clone();
        DELETE_SCHEDULER.schedule(() -> runScheduledDelete(pathCopy), delayMillis, TimeUnit.MILLISECONDS);
    }

    private void runScheduledDelete(String[] pathSegments) {
        try {
            executeDelete(pathSegments);
        }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            String path = FirestoreOperationAuditLogger.formatPath(pathSegments);
            operationAuditLogger.log("DELETE", path, 0, false, "scheduled delete interrupted");
        }
        catch (ExecutionException e) {
            // executeDelete already audit-logs failures
        }
    }

    private WriteResult executeDelete(String[] pathSegments)
            throws ExecutionException, InterruptedException {
        String path = FirestoreOperationAuditLogger.formatPath(pathSegments);
        long startNanos = System.nanoTime();
        try {
            WriteResult result = document(pathSegments).delete().get();
            operationAuditLogger.log("DELETE", path, elapsedMs(startNanos), true, null);
            return result;
        }
        catch (ExecutionException | InterruptedException | RuntimeException e) {
            operationAuditLogger.log("DELETE", path, elapsedMs(startNanos), false, e.getMessage());
            throw e;
        }
    }

    /**
     * Reads the document at the given path and maps it to {@code type}, or returns
     * {@code null} when the document does not exist.
     */
    public <T> T get(Class<T> type, String... pathSegments)
            throws ExecutionException, InterruptedException {
        DocumentSnapshot snapshot = document(pathSegments).get().get();
        return snapshot.exists() ? snapshot.toObject(type) : null;
    }

    /**
     * Returns whether a document exists at the given path.
     */
    public boolean exists(String... pathSegments)
            throws ExecutionException, InterruptedException {
        return document(pathSegments).get().get().exists();
    }

    /**
     * Direct access to the underlying {@link Firestore} for queries and operations not
     * covered by the convenience methods.
     */
    public Firestore getFirestore() {
        return this.firestore;
    }

    private static long elapsedMs(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000L;
    }
}
