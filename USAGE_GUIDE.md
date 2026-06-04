# goc-firestore-spring-boot-kit — Step-by-step usage guide

Use this guide to add the library to **any Spring Boot** project and call Firestore through `FirestoreService`.

---

## Prerequisites

- Java **21+**
- Spring Boot **3.x / 4.x** (starter parent used by your app)
- A Firebase project with **Firestore** enabled
- A **service account JSON** key file (downloaded from Firebase Console → Project settings → Service accounts)

> This library **does not** ship credentials. Your application must provide the JSON file.

---

## Step 1 — Add dependency in `pom.xml`

Add inside `<dependencies>`:

```xml
<dependency>
    <groupId>io.github.pratik38237</groupId>
    <artifactId>goc-firestore-spring-boot-kit</artifactId>
    <version>0.0.2</version>
</dependency>
```

Refresh Maven in your IDE after adding the dependency.

---

## Step 2 — Configure `application.properties`

Add to `src/main/resources/application.properties`:

```properties
# --- Required: path to your Firebase service account JSON ---
# Use file: prefix so Spring loads from disk (recommended on Windows/Linux)
goc.firestore.credentials-location=file:${user.dir}/config/firebase-service-account.json

# --- Optional ---
goc.firestore.enabled=true
goc.firestore.project-id=your-gcp-project-id

# --- Operations audit log (save / delete history in a separate file) ---
goc.firestore.operations-log-enabled=true
# Folder: creates firestore-operations.log inside this directory
goc.firestore.operations-log-location=${user.dir}/logs
# Or full file path:
# goc.firestore.operations-log-location=${user.dir}/logs/firestore-operations.log
```

### Property reference

| Property | Required | Default | Description |
|----------|----------|---------|-------------|
| `goc.firestore.credentials-location` | **Yes** (local dev) | — | `file:...` or `classpath:...` to service account JSON |
| `goc.firestore.enabled` | No | `true` | Set `false` to disable auto-configuration |
| `goc.firestore.project-id` | No | from JSON | GCP project id |
| `goc.firestore.operations-log-enabled` | No | `false` | Enable dedicated save/delete log file |
| `goc.firestore.operations-log-location` | No | `logs/firestore-operations.log` | **Folder** or **file** path (see below) |

### `operations-log-location` — folder or file

| Value example | Log file created |
|---------------|------------------|
| `logs` | `{user.dir}/logs/firestore-operations.log` |
| `logs/` | Same |
| `${user.dir}/logs` | Same |
| `logs/custom.log` | `{user.dir}/logs/custom.log` |
| *(not set, logging enabled)* | `{user.dir}/logs/firestore-operations.log` |

---

## Complete example (push + delayed delete)

```java
@Component
public class NotificationManager {

    private static final String COLLECTION = "collectionName";
    private static final String SUB_COLLECTION = "subCollectionName";
    private static final long DELETE_AFTER_MS = 10_000L;

    private final FirestoreService firestore;

    public NotificationManager(FirestoreService firestore) {
        this.firestore = firestore;
    }

    public void pushAndScheduleCleanup(String collectionId, String subCollectionId, Map<String, Object> payload)
            throws ExecutionException, InterruptedException {

        firestore.save(payload, COLLECTION, collectionId, SUB_COLLECTION, subCollectionId);

        if (shouldDelete(payload)) {
            firestore.scheduleDelete(DELETE_AFTER_MS, COLLECTION, collectionId, SUB_COLLECTION, subCollectionId);
        }
    }

    private boolean shouldDelete(Map<String, Object> payload) {
        // your logic, e.g. COMPLETED or FAILED
        return true;
    }
}
```

---

## Step 3 — View operations audit log

When `goc.firestore.operations-log-enabled=true`, open the log file, for example:

```text
your-project/logs/firestore-operations.log
```

Example lines:

```text
2026-06-03T18:58:06.816044500 IST | SAVE | collectionName/abc/subCollectionName/doc-1 | 5634ms | OK
2026-06-03T18:58:38.359698000 IST | DELETE | collectionName/abc/subCollectionName/doc-1 | 400ms | OK
```

| Field | Meaning |
|-------|---------|
| Timestamp | When the operation finished (IST, `Asia/Kolkata`, UTC+05:30) |
| SAVE / DELETE | Operation type |
| Path | Document path |
| duration | Round-trip time to Firestore |
| OK / FAILED | Result |

---

## Step 4 — Run and verify

1. Start your Spring Boot application.
2. Confirm startup has **no** errors about:
   - missing `goc.firestore.credentials-location`
   - `FirebaseApp with name [DEFAULT] doesn't exist`
   - service account file not found
3. Trigger a save/delete from your code.
4. Check Firestore Console for the document.
5. Check `logs/firestore-operations.log` if audit logging is enabled.

---

## Troubleshooting

| Error | Fix |
|-------|-----|
| `Could not resolve placeholder 'firebase.credentials.path'` | Remove old `FirebaseConfig`; use `goc.firestore.credentials-location` only |
| `service account file not found` | Use `file:` prefix; verify JSON path and filename |
| `FirebaseApp [DEFAULT] doesn't exist` | Remove `FirestoreClient.getFirestore()`; use injected `FirestoreService` |
| Audit log empty | Set `goc.firestore.operations-log-enabled=true` |
| Slow first SAVE (5+ seconds) | Normal cold start; later saves are usually faster |
