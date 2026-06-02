package com.goc.firestore.autoconfigure;

import com.google.cloud.firestore.Firestore;
import com.google.firebase.FirebaseApp;
import org.junit.jupiter.api.Test;

import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class FirestoreAutoConfigurationTests {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(FirestoreAutoConfiguration.class));

    @Test
    void doesNotCreateBeansWhenDisabled() {
        this.contextRunner
                .withPropertyValues("goc.firestore.enabled=false")
                .run(context -> {
                    assertThat(context).doesNotHaveBean(FirebaseApp.class);
                    assertThat(context).doesNotHaveBean(Firestore.class);
                });
    }

    @Test
    void failsWithHelpfulMessageWhenCredentialsFileMissing() {
        this.contextRunner
                .withPropertyValues("goc.firestore.credentials-location=classpath:does-not-exist.json")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context).getFailure()
                            .rootCause()
                            .hasMessageContaining("service account file not found");
                });
    }
}
