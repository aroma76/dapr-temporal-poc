package com.bank.transaction;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * What it does: Spring Data JPA repository for the 'accounts' table in YugabyteDB.
 * Why it exists: Provides standard CRUD operations (findById, save) without manual SQL boilerplate.
 * Technology used: Spring Data JPA (JpaRepository).
 * Communication: Injected into and used by TransactionService.
 */
@Repository
public interface AccountRepository extends JpaRepository<Account, String> {
}

