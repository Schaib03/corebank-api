package com.saadchaib.corebank.auth;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.saadchaib.corebank.support.IntegrationTest;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

class AuthIT extends IntegrationTest {

    @Test
    void registerThenLoginAndReadProfile() throws Exception {
        String email = randomEmail();
        register(email);
        String token = login(email.toUpperCase(), "password123");

        mvc.perform(get("/api/me").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.firstName").value("Test"));
    }

    @Test
    void duplicateEmailIsConflict() throws Exception {
        String email = randomEmail();
        register(email);

        var body = Map.of("firstName", "A", "lastName", "B", "email", email, "password", "password123");
        mvc.perform(postJson("/api/auth/register", body, null))
                .andExpect(status().isConflict())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("https://corebank.local/problems/conflict"));
    }

    @Test
    void invalidRegistrationListsFieldErrors() throws Exception {
        var body = Map.of("firstName", "", "lastName", "B", "email", "not-an-email", "password", "short");
        mvc.perform(postJson("/api/auth/register", body, null))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Invalid request"))
                .andExpect(jsonPath("$.errors.email").exists())
                .andExpect(jsonPath("$.errors.password").exists())
                .andExpect(jsonPath("$.errors.firstName").exists());
    }

    @Test
    void wrongPasswordIs401() throws Exception {
        String email = randomEmail();
        register(email);
        mvc.perform(postJson("/api/auth/login", Map.of("email", email, "password", "wrong-password"), null))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Invalid email or password."));
    }

    @Test
    void missingOrInvalidTokenIs401ProblemDetail() throws Exception {
        mvc.perform(get("/api/accounts"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer"))
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(401));

        mvc.perform(get("/api/accounts").header(HttpHeaders.AUTHORIZATION, "Bearer not.a.jwt"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void userCannotCallAdminEndpoints() throws Exception {
        String token = registerNew();
        mvc.perform(get("/api/admin/customers").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.title").value("Forbidden"));
    }

    @Test
    void bootstrapAdminCanListCustomers() throws Exception {
        registerNew();
        mvc.perform(get("/api/admin/customers?size=5").header(HttpHeaders.AUTHORIZATION, bearer(adminToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value(5))
                .andExpect(jsonPath("$.content").isArray());
    }
}
