package com.saadchaib.corebank.transaction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.saadchaib.corebank.support.IntegrationTest;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

class BankingFlowIT extends IntegrationTest {

    private JsonNode account(String token, String id) throws Exception {
        return read(mvc.perform(get("/api/accounts/" + id).header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk()));
    }

    @Test
    void depositWithdrawAndTransfer() throws Exception {
        String alice = registerNew();
        String bob = registerNew();
        JsonNode aliceAccount = openAccount(alice, "MAD");
        JsonNode bobAccount = openAccount(bob, "MAD");
        String aliceId = aliceAccount.get("id").asText();

        assertThat(aliceAccount.get("accountNumber").asText()).matches("7001\\d{12}");
        assertThat(aliceAccount.get("status").asText()).isEqualTo("ACTIVE");

        deposit(alice, aliceId, "1000.00");
        mvc.perform(postJson("/api/accounts/" + aliceId + "/withdrawals", Map.of("amount", "150.50"), alice))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.type").value("WITHDRAWAL"))
                .andExpect(jsonPath("$.balanceAfter").value(849.50));

        var transfer = Map.of(
                "fromAccountId", aliceId,
                "toAccountNumber", bobAccount.get("accountNumber").asText(),
                "amount", "300",
                "description", "Loyer novembre");
        mvc.perform(postJson("/api/transfers", transfer, alice))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.type").value("TRANSFER_OUT"))
                .andExpect(jsonPath("$.amount").value(300.00))
                .andExpect(jsonPath("$.counterpartyAccount").value(bobAccount.get("accountNumber").asText()));

        assertThat(account(alice, aliceId).get("balance").decimalValue()).isEqualByComparingTo("549.50");
        String bobId = bobAccount.get("id").asText();
        assertThat(account(bob, bobId).get("balance").decimalValue()).isEqualByComparingTo("300.00");

        mvc.perform(get("/api/accounts/" + bobId + "/transactions").header(HttpHeaders.AUTHORIZATION, bearer(bob)))
                .andExpect(jsonPath("$.content[0].type").value("TRANSFER_IN"))
                .andExpect(jsonPath("$.content[0].counterpartyAccount").value(aliceAccount.get("accountNumber").asText()))
                .andExpect(jsonPath("$.content[0].description").value("Loyer novembre"));
    }

    @Test
    void historyIsPaginatedNewestFirstAndFilterable() throws Exception {
        String token = registerNew();
        String id = openAccount(token, "EUR").get("id").asText();
        for (int i = 1; i <= 5; i++) {
            deposit(token, id, i + ".00");
        }
        mvc.perform(postJson("/api/accounts/" + id + "/withdrawals", Map.of("amount", "1"), token))
                .andExpect(status().isCreated());

        mvc.perform(get("/api/accounts/" + id + "/transactions?page=0&size=4")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(4))
                .andExpect(jsonPath("$.totalElements").value(6))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.content[0].type").value("WITHDRAWAL"));

        mvc.perform(get("/api/accounts/" + id + "/transactions?type=DEPOSIT&size=50")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(jsonPath("$.totalElements").value(5));
    }

    @Test
    void insufficientFundsIs422WithCode() throws Exception {
        String token = registerNew();
        String id = openAccount(token, "MAD").get("id").asText();
        deposit(token, id, "10.00");

        mvc.perform(postJson("/api/accounts/" + id + "/withdrawals", Map.of("amount", "10.01"), token))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("insufficient-funds"))
                .andExpect(jsonPath("$.type").value("https://corebank.local/problems/insufficient-funds"));
    }

    @Test
    void amountsAreValidated() throws Exception {
        String token = registerNew();
        String id = openAccount(token, "MAD").get("id").asText();

        for (String bad : new String[] {"0", "-5", "10.001", "1000000.01"}) {
            mvc.perform(postJson("/api/accounts/" + id + "/deposits", Map.of("amount", bad), token))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors.amount").exists());
        }
    }

    @Test
    void customersCannotSeeOrUseEachOthersAccounts() throws Exception {
        String alice = registerNew();
        String mallory = registerNew();
        String aliceId = openAccount(alice, "MAD").get("id").asText();
        deposit(alice, aliceId, "100");
        String malloryId = openAccount(mallory, "MAD").get("id").asText();

        mvc.perform(get("/api/accounts/" + aliceId).header(HttpHeaders.AUTHORIZATION, bearer(mallory)))
                .andExpect(status().isNotFound());
        mvc.perform(postJson("/api/accounts/" + aliceId + "/withdrawals", Map.of("amount", "1"), mallory))
                .andExpect(status().isNotFound());
        var steal = Map.of("fromAccountId", aliceId, "toAccountNumber",
                account(mallory, malloryId).get("accountNumber").asText(), "amount", "50");
        mvc.perform(postJson("/api/transfers", steal, mallory)).andExpect(status().isNotFound());

        assertThat(account(alice, aliceId).get("balance").decimalValue()).isEqualByComparingTo("100.00");
    }

    @Test
    void transferBetweenCurrenciesIsRejected() throws Exception {
        String token = registerNew();
        String mad = openAccount(token, "MAD").get("id").asText();
        String eurNumber = openAccount(token, "EUR").get("accountNumber").asText();
        deposit(token, mad, "100");

        mvc.perform(postJson("/api/transfers",
                        Map.of("fromAccountId", mad, "toAccountNumber", eurNumber, "amount", "10"), token))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("currency-mismatch"));
    }

    @Test
    void adminFreezesAndClosesAccounts() throws Exception {
        String user = registerNew();
        String admin = adminToken();
        String id = openAccount(user, "USD").get("id").asText();
        deposit(user, id, "20");

        mvc.perform(patch("/api/admin/accounts/" + id + "/status")
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"FROZEN\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FROZEN"));

        mvc.perform(postJson("/api/accounts/" + id + "/deposits", Map.of("amount", "1"), user))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("account-not-active"));

        mvc.perform(patch("/api/admin/accounts/" + id + "/status")
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"CLOSED\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("balance-not-zero"));

        // Admin can read any account.
        mvc.perform(get("/api/accounts/" + id).header(HttpHeaders.AUTHORIZATION, bearer(admin)))
                .andExpect(status().isOk());
    }
}
