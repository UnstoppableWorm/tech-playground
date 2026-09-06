package com.example.delivery;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DeliveryApplicationTest {
    @Test
    void readsAValidDeploymentContextFromEnvironment() {
        DeliveryApplication.DeploymentContext context = DeliveryApplication.deploymentContext(Map.of(
                "APP_COUNTRY", "KR",
                "APP_ENV", "stg"
        ));

        assertEquals(new DeliveryApplication.DeploymentContext("kr", "stg"), context);
    }

    @Test
    void rejectsAnUnsupportedCountry() {
        assertThrows(IllegalArgumentException.class,
                () -> DeliveryApplication.validateDeployment("jp", "prd"));
    }

    @Test
    void buildsAnActuatorCompatibleHealthResponse() {
        String payload = DeliveryApplication.healthPayload(
                new DeliveryApplication.DeploymentContext("eu", "prd"));

        assertEquals("{\"status\":\"UP\",\"country\":\"eu\",\"environment\":\"prd\"}", payload);
    }
}
