package com.saadchaib.corebank;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.saadchaib.corebank.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.jdbc.core.JdbcTemplate;

// Spring Boot turns metrics export off in tests unless asked for.
@AutoConfigureObservability
class OperationsIT extends IntegrationTest {

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void healthProbesArePublic() throws Exception {
        mvc.perform(get("/actuator/health/liveness")).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
        mvc.perform(get("/actuator/health/readiness")).andExpect(status().isOk());
    }

    @Test
    void prometheusExposesBusinessMetrics() throws Exception {
        String token = registerNew();
        deposit(token, openAccount(token, "MAD").get("id").asText(), "5");

        String metrics = mvc.perform(get("/actuator/prometheus"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(metrics).contains("corebank_operations_total{application=\"corebank-api\",type=\"deposit\"}");
        assertThat(metrics).contains("http_server_requests_seconds_count");
    }

    @Test
    void openApiDocumentIsServed() throws Exception {
        mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/transfers'].post").exists())
                .andExpect(jsonPath("$.components.securitySchemes.bearer.scheme").value("bearer"));
    }

    @Test
    void schemaIsManagedByFlyway() {
        Integer applied = jdbc.queryForObject(
                "select count(*) from flyway_schema_history where success = true", Integer.class);
        assertThat(applied).isGreaterThanOrEqualTo(1);
    }
}
