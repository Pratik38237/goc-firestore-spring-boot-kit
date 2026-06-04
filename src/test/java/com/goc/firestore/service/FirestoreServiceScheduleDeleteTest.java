package com.goc.firestore.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FirestoreServiceScheduleDeleteTest {

    @Test
    void scheduleDeleteRejectsNegativeDelay() {
        FirestoreService service = new FirestoreService(null);

        assertThatThrownBy(() -> service.scheduleDelete(-1, "clients", "c1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("delayMillis");
    }
}
