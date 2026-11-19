package com.saadchaib.corebank.transaction;

import static org.assertj.core.api.Assertions.assertThat;

import com.saadchaib.corebank.account.Account;
import com.saadchaib.corebank.account.AccountDtos.OpenAccountRequest;
import com.saadchaib.corebank.account.AccountRepository;
import com.saadchaib.corebank.account.AccountService;
import com.saadchaib.corebank.account.AccountType;
import com.saadchaib.corebank.account.Currency;
import com.saadchaib.corebank.common.BusinessRuleException;
import com.saadchaib.corebank.customer.Customer;
import com.saadchaib.corebank.customer.CustomerRepository;
import com.saadchaib.corebank.security.CurrentUser;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class ConcurrencyIT {

    @Autowired
    TransactionService transactions;

    @Autowired
    AccountService accountService;

    @Autowired
    AccountRepository accountRepository;

    @Autowired
    CustomerRepository customers;

    private final CurrentUser admin = new CurrentUser(UUID.randomUUID(), "admin", List.of("ADMIN"), null);

    private Account newAccount(String initialBalance) {
        Customer customer = customers.save(new Customer("Load", "Test", UUID.randomUUID() + "@test.ma", null));
        Account account = accountService.open(customer.getId(), new OpenAccountRequest(AccountType.CURRENT, Currency.MAD));
        transactions.deposit(admin, account.getId(), new BigDecimal(initialBalance), "seed");
        return account;
    }

    private BigDecimal balance(Account account) {
        return accountRepository.findById(account.getId()).orElseThrow().getBalance();
    }

    private static void runConcurrently(int threads, List<Callable<Void>> tasks) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Void>> futures = new ArrayList<>();
        for (Callable<Void> task : tasks) {
            futures.add(pool.submit(() -> {
                start.await();
                return task.call();
            }));
        }
        start.countDown();
        for (Future<Void> future : futures) {
            future.get(30, TimeUnit.SECONDS);
        }
        pool.shutdown();
    }

    @Test
    void parallelWithdrawalsNeverOverdraw() throws Exception {
        Account account = newAccount("500.00");
        AtomicInteger succeeded = new AtomicInteger();
        AtomicInteger rejected = new AtomicInteger();

        List<Callable<Void>> tasks = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            tasks.add(() -> {
                try {
                    transactions.withdraw(admin, account.getId(), new BigDecimal("100.00"), null);
                    succeeded.incrementAndGet();
                } catch (BusinessRuleException e) {
                    rejected.incrementAndGet();
                }
                return null;
            });
        }
        runConcurrently(12, tasks);

        assertThat(succeeded.get()).isEqualTo(5);
        assertThat(rejected.get()).isEqualTo(7);
        assertThat(balance(account)).isEqualByComparingTo("0.00");
    }

    @Test
    void opposingTransfersDoNotDeadlockAndConserveMoney() throws Exception {
        Account a = newAccount("1000.00");
        Account b = newAccount("1000.00");

        List<Callable<Void>> tasks = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            boolean aToB = i % 2 == 0;
            tasks.add(() -> {
                transactions.transfer(admin,
                        aToB ? a.getId() : b.getId(),
                        aToB ? b.getAccountNumber() : a.getAccountNumber(),
                        new BigDecimal("10.00"), null);
                return null;
            });
        }
        runConcurrently(8, tasks);

        assertThat(balance(a).add(balance(b))).isEqualByComparingTo("2000.00");
        assertThat(balance(a)).isEqualByComparingTo("1000.00");
    }
}
