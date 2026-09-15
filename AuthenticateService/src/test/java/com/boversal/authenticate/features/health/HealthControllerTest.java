package com.boversal.authenticate.features.health;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.Test;

class HealthControllerTest {
    @Test
    void healthReturnsServiceStatus() {
        Map<String, Object> response = new HealthController().health();

        assertThat(response).containsEntry("status", "healthy");
        assertThat(response).containsEntry("service", "AuthenticateService");
        assertThat(response).containsKey("timestamp");
    }
}
