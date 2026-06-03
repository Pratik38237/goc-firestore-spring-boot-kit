# goc-firestore-spring-boot-kit

A generic Spring Boot starter that auto-initializes Firebase and exposes a ready-to-use
`Firestore` bean to the consuming application.

> This library **never** contains or bundles a Firebase service account JSON.
> The consuming (parent) application supplies its own credentials.

## What it does

When added to a Spring Boot app, the starter auto-configures:

- a `com.google.firebase.FirebaseApp` (named `goc-firestore`), and
- a `com.google.cloud.firestore.Firestore` bean

Both beans are declared with `@ConditionalOnMissingBean`, so the consuming application
can override either of them.

## 1. Add the dependency

```xml
<dependency>
    <groupId>io.github.Pratik38237</groupId>
    <artifactId>goc-firestore-spring-boot-kit</artifactId>
    <version>0.0.1</version>
</dependency>
```

## 2. Provide the service account JSON (in the parent app)

The starter loads credentials from a Spring `Resource` location that **you** configure.
The file lives in the consuming application, not in this library.

```yaml
# application.yml of the consuming application
goc:
  firestore:
    enabled: true                                              # optional, default true
    credentials-location: file:/etc/secrets/firebase-key.json  # file: or classpath:
    project-id: my-gcp-project                                 # optional
    database-id: "(default)"                                   # optional, named DBs
```

Common ways the parent app provides the file:

- A mounted secret / file path: `file:/etc/secrets/firebase-service-account.json`
- A classpath resource the parent app owns: `classpath:firebase-service-account.json`
- **Application Default Credentials**: leave `credentials-location` blank when running on
  Cloud Run / GKE / Cloud Functions, where the platform provides credentials.

## 3. Inject and use Firestore

```java
@Service
public class UserService {

    private final Firestore firestore;

    public UserService(Firestore firestore) {
        this.firestore = firestore;
    }

    public void save(String id, Map<String, Object> data) throws Exception {
        firestore.collection("users").document(id).set(data).get();
    }
}
```

## Configuration properties

| Property                            | Default  | Description                                                        |
|-------------------------------------|----------|--------------------------------------------------------------------|
| `goc.firestore.enabled`             | `true`   | Toggles the auto-configuration on/off.                             |
| `goc.firestore.credentials-location`| _(none)_ | `Resource` location of the service account JSON. Blank = use ADC.  |
| `goc.firestore.project-id`          | _(none)_ | Optional GCP project id; inferred from credentials when not set.   |
| `goc.firestore.database-id`         | _(none)_ | Optional Firestore database id for named (non-default) databases.  |
