package com.saadchaib.corebank.transaction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.saadchaib.corebank.account.Account;
import com.saadchaib.corebank.account.AccountRepository;
import com.saadchaib.corebank.account.AccountStatus;
import com.saadchaib.corebank.account.Currency;
import com.saadchaib.corebank.common.BusinessRuleException;
import com.saadchaib.corebank.common.NotFoundException;
import com.saadchaib.corebank.customer.Customer;
import com.saadchaib.corebank.security.CurrentUser;
import com.saadchaib.corebank.support.TestData;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TransactionServiceTest {

    @Mock
    AccountRepository accounts;

    @Mock
    AccountTransactionRepository transactions;

    SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
    TransactionService service;

    Customer alice = TestData.customer();
    Customer bob = TestData.customer();
    CurrentUser aliceUser = TestData.userFor(alice);

    @BeforeEach
    void setUp() {
        service = new TransactionService(accounts, transactions, meterRegistry);
    }

    private void stubLock(Account... toLock) {
        for (Account account : toLock) {
            when(accounts.findByIdForUpdate(account.getId())).thenReturn(Optional.of(account));
        }
    }

    private void stubNumber(Account account) {
        when(accounts.findIdByAccountNumber(account.getAccountNumber())).thenReturn(Optional.of(account.getId()));
    }

    @Test
    void depositCreditsTheLockedAccountAndRecordsBalanceAfter() {
        Account account = TestData.account(alice, Currency.MAD, "10.00");
        stubLock(account);
        when(transactions.save(any())).thenAnswer(inv -> inv.getArgument(0));

        AccountTransaction tx = service.deposit(aliceUser, account.getId(), new BigDecimal("5.5"), "cash");

        assertThat(account.getBalance()).isEqualByComparingTo("15.50");
        assertThat(tx.getType()).isEqualTo(TransactionType.DEPOSIT);
        assertThat(tx.getAmount()).isEqualTo(new BigDecimal("5.50"));
        assertThat(tx.getBalanceAfter()).isEqualByComparingTo("15.50");
        assertThat(meterRegistry.get("corebank.operations").tag("type", "deposit").counter().count()).isEqualTo(1);
    }

    @Test
    void withdrawWithInsufficientFundsSavesNothing() {
        Account account = TestData.account(alice, Currency.MAD, "10.00");
        stubLock(account);

        assertThatThrownBy(() -> service.withdraw(aliceUser, account.getId(), new BigDecimal("10.01"), null))
                .isInstanceOf(BusinessRuleException.class);
        verify(transactions, never()).save(any());
    }

    @Test
    void cannotMoveMoneyOnSomeoneElsesAccount() {
        Account bobs = TestData.account(bob, Currency.MAD, "100.00");
        stubLock(bobs);

        assertThatThrownBy(() -> service.withdraw(aliceUser, bobs.getId(), BigDecimal.ONE, null))
                .isInstanceOf(NotFoundException.class);
        assertThat(bobs.getBalance()).isEqualByComparingTo("100.00");
    }

    @Test
    void transferMovesMoneyAndWritesTwoLegsWithTheSameReference() {
        Account from = TestData.account(alice, Currency.MAD, "100.00");
        Account to = TestData.account(bob, Currency.MAD, "0.00");
        stubNumber(to);
        stubLock(from, to);
        when(transactions.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.transfer(aliceUser, from.getId(), to.getAccountNumber(), new BigDecimal("40"), "rent");

        assertThat(from.getBalance()).isEqualByComparingTo("60.00");
        assertThat(to.getBalance()).isEqualByComparingTo("40.00");
        ArgumentCaptor<AccountTransaction> saved = ArgumentCaptor.forClass(AccountTransaction.class);
        verify(transactions, times(2)).save(saved.capture());
        List<AccountTransaction> legs = saved.getAllValues();
        assertThat(legs).extracting(AccountTransaction::getType)
                .containsExactly(TransactionType.TRANSFER_OUT, TransactionType.TRANSFER_IN);
        assertThat(legs.get(0).getReference()).isEqualTo(legs.get(1).getReference());
        assertThat(legs.get(0).getCounterpartyAccount()).isEqualTo(to.getAccountNumber());
    }

    @Test
    void transferLocksAccountsInIdOrderWhicheverDirection() {
        Account a = TestData.account(alice, Currency.MAD, "100.00");
        Account b = TestData.account(alice, Currency.MAD, "100.00");
        Account lower = a.getId().compareTo(b.getId()) < 0 ? a : b;
        Account higher = lower == a ? b : a;
        stubNumber(lower);
        stubNumber(higher);
        stubLock(a, b);
        when(transactions.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.transfer(aliceUser, higher.getId(), lower.getAccountNumber(), BigDecimal.ONE, null);
        service.transfer(aliceUser, lower.getId(), higher.getAccountNumber(), BigDecimal.ONE, null);

        InOrder order = inOrder(accounts);
        for (int i = 0; i < 2; i++) {
            order.verify(accounts).findByIdForUpdate(lower.getId());
            order.verify(accounts).findByIdForUpdate(higher.getId());
        }
    }

    @Test
    void transferToSameAccountIsRejected() {
        Account account = TestData.account(alice, Currency.MAD, "100.00");
        stubNumber(account);

        assertThatThrownBy(() -> service.transfer(aliceUser, account.getId(), account.getAccountNumber(), BigDecimal.ONE, null))
                .extracting("code").isEqualTo("same-account");
    }

    @Test
    void transferBetweenCurrenciesIsRejected() {
        Account from = TestData.account(alice, Currency.MAD, "100.00");
        Account to = TestData.account(bob, Currency.EUR, "0.00");
        stubNumber(to);
        stubLock(from, to);

        assertThatThrownBy(() -> service.transfer(aliceUser, from.getId(), to.getAccountNumber(), BigDecimal.ONE, null))
                .extracting("code").isEqualTo("currency-mismatch");
        assertThat(from.getBalance()).isEqualByComparingTo("100.00");
    }

    @Test
    void transferToFrozenAccountIsRejectedBeforeDebiting() {
        Account from = TestData.account(alice, Currency.MAD, "100.00");
        Account to = TestData.account(bob, Currency.MAD, "0.00");
        to.changeStatus(AccountStatus.FROZEN);
        stubNumber(to);
        stubLock(from, to);

        assertThatThrownBy(() -> service.transfer(aliceUser, from.getId(), to.getAccountNumber(), BigDecimal.ONE, null))
                .extracting("code").isEqualTo("account-not-active");
        assertThat(from.getBalance()).isEqualByComparingTo("100.00");
    }

    @Test
    void transferToUnknownAccountIsNotFound() {
        when(accounts.findIdByAccountNumber("7001000000000000")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.transfer(
                aliceUser, java.util.UUID.randomUUID(), "7001000000000000", BigDecimal.ONE, null))
                .isInstanceOf(NotFoundException.class);
    }
}
