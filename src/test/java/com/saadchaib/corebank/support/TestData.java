package com.saadchaib.corebank.support;

import com.saadchaib.corebank.account.Account;
import com.saadchaib.corebank.account.AccountType;
import com.saadchaib.corebank.account.Currency;
import com.saadchaib.corebank.customer.Customer;
import com.saadchaib.corebank.security.CurrentUser;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.springframework.test.util.ReflectionTestUtils;

public final class TestData {

    private TestData() {
    }

    public static Customer customer() {
        Customer customer = new Customer("Saad", "Test", UUID.randomUUID() + "@test.ma", null);
        ReflectionTestUtils.setField(customer, "id", UUID.randomUUID());
        return customer;
    }

    public static Account account(Customer owner, Currency currency, String balance) {
        Account account = new Account(randomNumber(), owner, AccountType.CURRENT, currency);
        ReflectionTestUtils.setField(account, "id", UUID.randomUUID());
        ReflectionTestUtils.setField(account, "balance", new BigDecimal(balance));
        return account;
    }

    public static CurrentUser userFor(Customer customer) {
        return new CurrentUser(UUID.randomUUID(), customer.getEmail(), List.of("USER"), customer.getId());
    }

    private static String randomNumber() {
        return String.format("7001%012d", Math.abs(UUID.randomUUID().getMostSignificantBits() % 1_000_000_000_000L));
    }
}
