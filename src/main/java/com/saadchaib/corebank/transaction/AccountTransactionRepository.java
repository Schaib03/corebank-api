package com.saadchaib.corebank.transaction;

import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AccountTransactionRepository extends JpaRepository<AccountTransaction, UUID> {

    Page<AccountTransaction> findByAccountId(UUID accountId, Pageable pageable);

    Page<AccountTransaction> findByAccountIdAndType(UUID accountId, TransactionType type, Pageable pageable);

    List<AccountTransaction> findByReference(UUID reference);
}
