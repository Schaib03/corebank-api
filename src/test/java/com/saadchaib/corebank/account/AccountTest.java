package com.saadchaib.corebank.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.saadchaib.corebank.common.BusinessRuleException;
import com.saadchaib.corebank.support.TestData;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class AccountTest {

    private final Account account = TestData.account(TestData.customer(), Currency.MAD, "100.00");

    @Test
    void creditAndDebitChangeTheBalance() {
        account.credit(new BigDecimal("50.25"));
        account.debit(new BigDecimal("30.00"));
        assertThat(account.getBalance()).isEqualByComparingTo("120.25");
    }

    @Test
    void debitCannotOverdraw() {
        assertThatThrownBy(() -> account.debit(new BigDecimal("100.01")))
                .isInstanceOf(BusinessRuleException.class)
                .extracting("code").isEqualTo("insufficient-funds");
        assertThat(account.getBalance()).isEqualByComparingTo("100.00");
    }

    @Test
    void frozenAccountRejectsMovements() {
        account.changeStatus(AccountStatus.FROZEN);
        assertThatThrownBy(() -> account.credit(BigDecimal.ONE))
                .extracting("code").isEqualTo("account-not-active");
    }

    @Test
    void onlyEmptyAccountsCanBeClosedAndClosedIsFinal() {
        assertThatThrownBy(() -> account.changeStatus(AccountStatus.CLOSED))
                .extracting("code").isEqualTo("balance-not-zero");

        account.debit(new BigDecimal("100.00"));
        account.changeStatus(AccountStatus.CLOSED);
        assertThatThrownBy(() -> account.changeStatus(AccountStatus.ACTIVE))
                .extracting("code").isEqualTo("account-closed");
    }
}
