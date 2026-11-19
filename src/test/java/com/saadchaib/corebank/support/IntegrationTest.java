package com.saadchaib.corebank.support;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Full application on H2 (or PostgreSQL in CI). Tests never clean the database, so every
 * test creates its own users with random emails and only asserts on its own data.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public abstract class IntegrationTest {

    @Autowired
    protected MockMvc mvc;

    @Autowired
    protected ObjectMapper json;

    protected static String randomEmail() {
        return "user-" + UUID.randomUUID().toString().substring(0, 8) + "@test.ma";
    }

    protected String register(String email) throws Exception {
        var body = Map.of("firstName", "Test", "lastName", "User", "email", email, "password", "password123");
        return token(mvc.perform(postJson("/api/auth/register", body, null))
                .andExpect(status().isCreated()));
    }

    protected String registerNew() throws Exception {
        return register(randomEmail());
    }

    protected String login(String email, String password) throws Exception {
        return token(mvc.perform(postJson("/api/auth/login", Map.of("email", email, "password", password), null))
                .andExpect(status().isOk()));
    }

    protected String adminToken() throws Exception {
        return login("admin@corebank.test", "admin-password-123");
    }

    protected JsonNode openAccount(String token, String currency) throws Exception {
        return read(mvc.perform(postJson("/api/accounts", Map.of("type", "CURRENT", "currency", currency), token))
                .andExpect(status().isCreated()));
    }

    protected void deposit(String token, String accountId, String amount) throws Exception {
        mvc.perform(postJson("/api/accounts/" + accountId + "/deposits", Map.of("amount", amount), token))
                .andExpect(status().isCreated());
    }

    protected MockHttpServletRequestBuilder postJson(String url, Object body, String token) throws Exception {
        MockHttpServletRequestBuilder request = post(url)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(body));
        return token == null ? request : request.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
    }

    protected static String bearer(String token) {
        return "Bearer " + token;
    }

    protected JsonNode read(org.springframework.test.web.servlet.ResultActions result) throws Exception {
        return json.readTree(result.andReturn().getResponse().getContentAsString());
    }

    private String token(org.springframework.test.web.servlet.ResultActions result) throws Exception {
        return read(result).get("accessToken").asText();
    }
}
