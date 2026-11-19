package com.saadchaib.corebank.transaction;

import com.saadchaib.corebank.common.PageResponse;
import com.saadchaib.corebank.security.CurrentUser;
import com.saadchaib.corebank.transaction.TransactionDtos.AmountRequest;
import com.saadchaib.corebank.transaction.TransactionDtos.TransactionResponse;
import com.saadchaib.corebank.transaction.TransactionDtos.TransferRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Transactions")
public class TransactionController {

    private final TransactionService transactionService;

    public TransactionController(TransactionService transactionService) {
        this.transactionService = transactionService;
    }

    @PostMapping("/api/accounts/{id}/deposits")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Deposit money")
    public TransactionResponse deposit(CurrentUser user, @PathVariable UUID id, @Valid @RequestBody AmountRequest request) {
        return TransactionResponse.from(
                transactionService.deposit(user, id, request.amount(), request.description()));
    }

    @PostMapping("/api/accounts/{id}/withdrawals")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Withdraw money")
    public TransactionResponse withdraw(CurrentUser user, @PathVariable UUID id, @Valid @RequestBody AmountRequest request) {
        return TransactionResponse.from(
                transactionService.withdraw(user, id, request.amount(), request.description()));
    }

    @PostMapping("/api/transfers")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Transfer from one of your accounts to any account number")
    public TransactionResponse transfer(CurrentUser user, @Valid @RequestBody TransferRequest request) {
        return TransactionResponse.from(transactionService.transfer(
                user, request.fromAccountId(), request.toAccountNumber(), request.amount(), request.description()));
    }

    @GetMapping("/api/accounts/{id}/transactions")
    @Operation(summary = "Paginated account history, newest first")
    public PageResponse<TransactionResponse> history(
            CurrentUser user,
            @PathVariable UUID id,
            @RequestParam(required = false) TransactionType type,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return PageResponse.of(transactionService.history(user, id, type, pageable), TransactionResponse::from);
    }
}
