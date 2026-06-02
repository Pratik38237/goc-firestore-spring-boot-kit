package com.goc.firestore.service;

import java.util.concurrent.ExecutionException;

import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.WriteResult;

import org.springframework.util.Assert;

/**
 * Thin, reusable helper around {@link Firestore} for the common save / get / delete
 * operations, including documents nested in sub-collections.
 *
 * <p>Paths are expressed as alternating <em>collection, documentId</em> segments, so
 * the pattern
 * {@code db.collection("clients").document(clientId).collection("jobs").document(jobKey)}
 * becomes:</p>
 *
 * <pre>
 * firestoreService.delete(COLLECTION_CLIENTS, clientId, COLLECTION_JOBS, jobKey);
 * firestoreService.save(jobData, COLLECTION_CLIENTS, clientId, COLLECTION_JOBS, jobKey);
 * Job job = firestoreService.get(Job.class, COLLECTION_CLIENTS, clientId, COLLECTION_JOBS, jobKey);
 * </pre>
 *
 * <p>The write/read methods block on the underlying {@code ApiFuture} so callers get a
 * simple synchronous API. If you need the raw async {@code Firestore}, use
 * {@link #getFirestore()} or {@link #document(String...)}.</p>
 */
public class FirestoreService {

    private final Firestore firestore;

    public FirestoreService(Firestore firestore) {
        this.firestore = firestore;
    }

    /**
     * Builds a {@link DocumentReference} from alternating
     * {@code collection, documentId} path segments (e.g.
     * {@code "clients", clientId, "jobs", jobKey}).
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
        return document(pathSegments).set(data).get();
    }

    /**
     * Deletes the document at the given path.
     *
     * <p>Mirrors:
     * {@code db.collection(COLLECTION_CLIENTS).document(clientId)
     *           .collection(COLLECTION_JOBS).document(jobKey).delete()}.</p>
     *
     * @param pathSegments alternating collection / documentId segments
     * @return the write result reported by Firestore
     */
    public WriteResult delete(String... pathSegments)
            throws ExecutionException, InterruptedException {
        return document(pathSegments).delete().get();
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
}
