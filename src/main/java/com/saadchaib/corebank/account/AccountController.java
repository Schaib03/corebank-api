package com.saadchaib.corebank.account;

import com.saadchaib.corebank.account.AccountDtos.AccountResponse;
import com.saadchaib.corebank.account.AccountDtos.OpenAccountRequest;
import com.saadchaib.corebank.account.AccountDtos.StatusUpdateRequest;
import com.saadchaib.corebank.security.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Accounts")
public class AccountController {

    private final AccountService accountService;

    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    @PostMapping("/api/accounts")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Open an account for the logged-in customer")
    public AccountResponse open(CurrentUser user, @Valid @RequestBody OpenAccountRequest request) {
        return AccountResponse.from(accountService.open(user.requireCustomerId(), request));
    }

    @GetMapping("/api/accounts")
    @Operation(summary = "Accounts of the logged-in customer")
    public List<AccountResponse> mine(CurrentUser user) {
        return accountService.listForCustomer(user.requireCustomerId()).stream()
                .map(AccountResponse::from)
                .toList();
    }

    @GetMapping("/api/accounts/{id}")
    @Operation(summary = "Get one account")
    public AccountResponse get(CurrentUser user, @PathVariable UUID id) {
        return AccountResponse.from(accountService.getAccessible(id, user));
    }

    @GetMapping("/api/admin/customers/{customerId}/accounts")
    @Operation(summary = "Accounts of any customer (ADMIN)")
    public List<AccountResponse> forCustomer(@PathVariable UUID customerId) {
        return accountService.listForCustomer(customerId).stream().map(AccountResponse::from).toList();
    }

    @PatchMapping("/api/admin/accounts/{id}/status")
    @Operation(summary = "Freeze, reactivate or close an account (ADMIN)")
    public AccountResponse changeStatus(@PathVariable UUID id, @Valid @RequestBody StatusUpdateRequest request) {
        return AccountResponse.from(accountService.changeStatus(id, request.status()));
    }
}
