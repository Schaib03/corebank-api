package com.saadchaib.corebank.transaction;

import com.saadchaib.corebank.account.Account;
import com.saadchaib.corebank.account.AccountRepository;
import com.saadchaib.corebank.account.AccountService;
import com.saadchaib.corebank.common.BusinessRuleException;
import com.saadchaib.corebank.common.NotFoundException;
import com.saadchaib.corebank.security.CurrentUser;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TransactionService {

    private final AccountRepository accounts;
    private final AccountTransactionRepository transactions;
    private final MeterRegistry meterRegistry;

    public TransactionService(
            AccountRepository accounts, AccountTransactionRepository transactions, MeterRegistry meterRegistry) {
        this.accounts = accounts;
        this.transactions = transactions;
        this.meterRegistry = meterRegistry;
    }

    @Transactional
    public AccountTransaction deposit(CurrentUser user, UUID accountId, BigDecimal amount, String description) {
        Account account = lockAccessible(accountId, user);
        BigDecimal value = normalize(amount);
        account.credit(value);
        count(TransactionType.DEPOSIT);
        return transactions.save(new AccountTransaction(
                account, TransactionType.DEPOSIT, value, UUID.randomUUID(), null, description));
    }

    @Transactional
    public AccountTransaction withdraw(CurrentUser user, UUID accountId, BigDecimal amount, String description) {
        Account account = lockAccessible(accountId, user);
        BigDecimal value = normalize(amount);
        account.debit(value);
        count(TransactionType.WITHDRAWAL);
        return transactions.save(new AccountTransaction(
                account, TransactionType.WITHDRAWAL, value, UUID.randomUUID(), null, description));
    }

    /**
     * Moves money between two accounts in one database transaction.
     *
     * Both rows are locked with SELECT ... FOR UPDATE, always in the same order (smallest id
     * first). If transfer A->B and transfer B->A run at the same time, both try to lock the
     * same row first, so one simply waits for the other instead of deadlocking.
     */
    @Transactional
    public AccountTransaction transfer(
            CurrentUser user, UUID fromAccountId, String toAccountNumber, BigDecimal amount, String description) {
        UUID toAccountId = accounts.findIdByAccountNumber(toAccountNumber)
                .orElseThrow(() -> new NotFoundException("Destination account not found."));
        if (toAccountId.equals(fromAccountId)) {
            throw new BusinessRuleException("same-account", "Cannot transfer to the same account.");
        }

        boolean fromFirst = fromAccountId.compareTo(toAccountId) < 0;
        Account first = lock(fromFirst ? fromAccountId : toAccountId);
        Account second = lock(fromFirst ? toAccountId : fromAccountId);
        Account from = fromFirst ? first : second;
        Account to = fromFirst ? second : first;

        AccountService.checkAccess(from, user);
        if (from.getCurrency() != to.getCurrency()) {
            throw new BusinessRuleException("currency-mismatch",
                    "Both accounts must use the same currency (" + from.getCurrency() + " vs " + to.getCurrency() + ").");
        }

        BigDecimal value = normalize(amount);
        to.ensureActive();
        from.debit(value);
        to.credit(value);

        UUID reference = UUID.randomUUID();
        AccountTransaction out = transactions.save(new AccountTransaction(
                from, TransactionType.TRANSFER_OUT, value, reference, to.getAccountNumber(), description));
        transactions.save(new AccountTransaction(
                to, TransactionType.TRANSFER_IN, value, reference, from.getAccountNumber(), description));
        count(TransactionType.TRANSFER_OUT);
        return out;
    }

    @Transactional(readOnly = true)
    public Page<AccountTransaction> history(CurrentUser user, UUID accountId, TransactionType type, Pageable pageable) {
        Account account = accounts.findById(accountId).orElseThrow(() -> new NotFoundException("Account not found."));
        AccountService.checkAccess(account, user);
        return type == null
                ? transactions.findByAccountId(accountId, pageable)
                : transactions.findByAccountIdAndType(accountId, type, pageable);
    }

    private Account lockAccessible(UUID accountId, CurrentUser user) {
        Account account = lock(accountId);
        AccountService.checkAccess(account, user);
        return account;
    }

    private Account lock(UUID accountId) {
        return accounts.findByIdForUpdate(accountId)
                .orElseThrow(() -> new NotFoundException("Account not found."));
    }

    private static BigDecimal normalize(BigDecimal amount) {
        return amount.setScale(2, RoundingMode.UNNECESSARY);
    }

    private void count(TransactionType type) {
        Counter.builder("corebank.operations")
                .description("Completed money movements")
                .tag("type", type.name().toLowerCase())
                .register(meterRegistry)
                .increment();
    }
}
