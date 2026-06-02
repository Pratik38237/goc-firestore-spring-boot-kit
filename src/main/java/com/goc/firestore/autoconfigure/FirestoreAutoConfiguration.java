package com.goc.firestore.autoconfigure;

import java.io.IOException;
import java.io.InputStream;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.FirestoreOptions;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.cloud.FirestoreClient;

import com.goc.firestore.service.FirestoreService;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.util.StringUtils;

/**
 * Auto-configuration that initializes a {@link FirebaseApp} and exposes a ready-to-use
 * {@link Firestore} bean for the consuming Spring Boot application.
 *
 * <p>This library deliberately ships <strong>no</strong> service account JSON. The
 * parent application provides the credentials, either by pointing
 * {@code goc.firestore.credentials-location} at its own service account file, or by
 * relying on Google Application Default Credentials.</p>
 *
 * <p>Both the {@link FirebaseApp} and {@link Firestore} beans are declared with
 * {@link ConditionalOnMissingBean}, so an application can fully override them when it
 * needs custom behaviour.</p>
 */
@AutoConfiguration
@ConditionalOnClass({ FirebaseApp.class, Firestore.class })
@ConditionalOnProperty(prefix = "goc.firestore", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(FirestoreProperties.class)
public class FirestoreAutoConfiguration {

    /**
     * Logical name of the {@link FirebaseApp} managed by this starter. Using a dedicated
     * name avoids clashing with any default {@link FirebaseApp} the consuming application
     * may have initialized on its own.
     */
    public static final String FIREBASE_APP_NAME = "goc-firestore";

    @Bean
    @ConditionalOnMissingBean
    public FirebaseApp firebaseApp(FirestoreProperties properties, ResourceLoader resourceLoader) throws IOException {
        for (FirebaseApp existing : FirebaseApp.getApps()) {
            if (FIREBASE_APP_NAME.equals(existing.getName())) {
                return existing;
            }
        }

        GoogleCredentials credentials = loadCredentials(properties, resourceLoader);

        FirebaseOptions.Builder optionsBuilder = FirebaseOptions.builder()
                .setCredentials(credentials);

        if (StringUtils.hasText(properties.getProjectId())) {
            optionsBuilder.setProjectId(properties.getProjectId());
        }

        if (StringUtils.hasText(properties.getDatabaseId())) {
            optionsBuilder.setFirestoreOptions(FirestoreOptions.newBuilder()
                    .setDatabaseId(properties.getDatabaseId())
                    .build());
        }

        return FirebaseApp.initializeApp(optionsBuilder.build(), FIREBASE_APP_NAME);
    }

    @Bean
    @ConditionalOnMissingBean
    public Firestore firestore(FirebaseApp firebaseApp) {
        return FirestoreClient.getFirestore(firebaseApp);
    }

    @Bean
    @ConditionalOnMissingBean
    public FirestoreService firestoreService(Firestore firestore) {
        return new FirestoreService(firestore);
    }

    /**
     * Loads the credentials supplied by the parent application. When no location is
     * configured, falls back to Google Application Default Credentials.
     */
    private GoogleCredentials loadCredentials(FirestoreProperties properties, ResourceLoader resourceLoader)
            throws IOException {
        String location = properties.getCredentialsLocation();
        if (!StringUtils.hasText(location)) {
            return GoogleCredentials.getApplicationDefault();
        }

        Resource resource = resourceLoader.getResource(location);
        if (!resource.exists()) {
            throw new IllegalStateException(
                    "Firebase service account file not found at 'goc.firestore.credentials-location=" + location
                            + "'. The consuming application must provide a valid service account JSON.");
        }

        try (InputStream serviceAccount = resource.getInputStream()) {
            return GoogleCredentials.fromStream(serviceAccount);
        }
    }
}
