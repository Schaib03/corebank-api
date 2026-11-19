package com.saadchaib.corebank.account;

import com.saadchaib.corebank.account.AccountDtos.OpenAccountRequest;
import com.saadchaib.corebank.common.NotFoundException;
import com.saadchaib.corebank.customer.CustomerService;
import com.saadchaib.corebank.security.CurrentUser;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountService {

    private static final int MAX_NUMBER_ATTEMPTS = 5;

    private final AccountRepository accounts;
    private final CustomerService customerService;
    private final AccountNumberGenerator numberGenerator;

    public AccountService(
            AccountRepository accounts, CustomerService customerService, AccountNumberGenerator numberGenerator) {
        this.accounts = accounts;
        this.customerService = customerService;
        this.numberGenerator = numberGenerator;
    }

    @Transactional
    public Account open(UUID customerId, OpenAccountRequest request) {
        var customer = customerService.get(customerId);
        return accounts.save(new Account(uniqueNumber(), customer, request.type(), request.currency()));
    }

    @Transactional(readOnly = true)
    public List<Account> listForCustomer(UUID customerId) {
        return accounts.findByCustomerIdOrderByCreatedAtAsc(customerId);
    }

    @Transactional(readOnly = true)
    public Account getAccessible(UUID accountId, CurrentUser user) {
        Account account = accounts.findById(accountId).orElseThrow(AccountService::notFound);
        checkAccess(account, user);
        return account;
    }

    @Transactional
    public Account changeStatus(UUID accountId, AccountStatus status) {
        Account account = accounts.findByIdForUpdate(accountId).orElseThrow(AccountService::notFound);
        account.changeStatus(status);
        return account;
    }

    public static void checkAccess(Account account, CurrentUser user) {
        // 404 rather than 403, so account ids cannot be probed
        if (!user.isAdmin() && !account.getCustomer().getId().equals(user.customerId())) {
            throw notFound();
        }
    }

    static NotFoundException notFound() {
        return new NotFoundException("Account not found.");
    }

    private String uniqueNumber() {
        for (int i = 0; i < MAX_NUMBER_ATTEMPTS; i++) {
            String candidate = numberGenerator.next();
            if (!accounts.existsByAccountNumber(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException("Could not generate a unique account number.");
    }
}
