package com.saadchaib.corebank.account;

import com.saadchaib.corebank.common.BusinessRuleException;
import com.saadchaib.corebank.customer.Customer;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "accounts")
public class Account {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "account_number", nullable = false, unique = true, updatable = false)
    private String accountNumber;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false, updatable = false)
    private Customer customer;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private AccountType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private Currency currency;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal balance = BigDecimal.ZERO.setScale(2);

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AccountStatus status = AccountStatus.ACTIVE;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected Account() {
    }

    public Account(String accountNumber, Customer customer, AccountType type, Currency currency) {
        this.accountNumber = accountNumber;
        this.customer = customer;
        this.type = type;
        this.currency = currency;
    }

    public void credit(BigDecimal amount) {
        ensureActive();
        balance = balance.add(amount);
    }

    public void debit(BigDecimal amount) {
        ensureActive();
        if (balance.compareTo(amount) < 0) {
            throw new BusinessRuleException("insufficient-funds", "Insufficient funds on account " + accountNumber + ".");
        }
        balance = balance.subtract(amount);
    }

    public void ensureActive() {
        if (status != AccountStatus.ACTIVE) {
            throw new BusinessRuleException("account-not-active", "Account " + accountNumber + " is " + status + ".");
        }
    }

    public void changeStatus(AccountStatus newStatus) {
        if (status == AccountStatus.CLOSED) {
            throw new BusinessRuleException("account-closed", "A closed account cannot be reopened.");
        }
        if (newStatus == AccountStatus.CLOSED && balance.signum() != 0) {
            throw new BusinessRuleException("balance-not-zero", "Only an account with a zero balance can be closed.");
        }
        status = newStatus;
    }

    public UUID getId() {
        return id;
    }

    public String getAccountNumber() {
        return accountNumber;
    }

    public Customer getCustomer() {
        return customer;
    }

    public AccountType getType() {
        return type;
    }

    public Currency getCurrency() {
        return currency;
    }

    public BigDecimal getBalance() {
        return balance;
    }

    public AccountStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
