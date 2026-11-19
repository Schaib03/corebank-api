package com.saadchaib.corebank.account;

import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public final class AccountDtos {

    private AccountDtos() {
    }

    public record OpenAccountRequest(@NotNull AccountType type, @NotNull Currency currency) {
    }

    public record StatusUpdateRequest(@NotNull AccountStatus status) {
    }

    public record AccountResponse(
            UUID id,
            String accountNumber,
            UUID customerId,
            AccountType type,
            Currency currency,
            BigDecimal balance,
            AccountStatus status,
            Instant createdAt) {

        public static AccountResponse from(Account account) {
            return new AccountResponse(
                    account.getId(),
                    account.getAccountNumber(),
                    account.getCustomer().getId(),
                    account.getType(),
                    account.getCurrency(),
                    account.getBalance(),
                    account.getStatus(),
                    account.getCreatedAt());
        }
    }
}
