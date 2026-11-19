package com.saadchaib.corebank.transaction;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public final class TransactionDtos {

    private TransactionDtos() {
    }

    public record AmountRequest(
            @NotNull
            @DecimalMin(value = "0.01")
            @DecimalMax(value = "1000000.00", message = "must not exceed 1,000,000.00 per operation")
            @Digits(integer = 13, fraction = 2)
            BigDecimal amount,
            @Size(max = 255) String description) {
    }

    public record TransferRequest(
            @NotNull UUID fromAccountId,
            @NotBlank @Pattern(regexp = "^\\d{16}$", message = "must be a 16-digit account number")
            String toAccountNumber,
            @NotNull
            @DecimalMin(value = "0.01")
            @DecimalMax(value = "1000000.00", message = "must not exceed 1,000,000.00 per operation")
            @Digits(integer = 13, fraction = 2)
            BigDecimal amount,
            @Size(max = 255) String description) {
    }

    public record TransactionResponse(
            UUID id,
            UUID accountId,
            TransactionType type,
            BigDecimal amount,
            BigDecimal balanceAfter,
            UUID reference,
            String counterpartyAccount,
            String description,
            Instant createdAt) {

        public static TransactionResponse from(AccountTransaction tx) {
            return new TransactionResponse(
                    tx.getId(),
                    tx.getAccount().getId(),
                    tx.getType(),
                    tx.getAmount(),
                    tx.getBalanceAfter(),
                    tx.getReference(),
                    tx.getCounterpartyAccount(),
                    tx.getDescription(),
                    tx.getCreatedAt());
        }
    }
}
