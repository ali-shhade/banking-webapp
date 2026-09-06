package ali.com.banking.banking_backend.transaction.repository;

import ali.com.banking.banking_backend.transaction.entity.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TransactionRepository extends JpaRepository<Transaction, Long> {

    Optional<Transaction> findByReference(String reference);

    List<Transaction> findByAccount_AccountIdOrderByCreatedAtDesc(Long accountId);
}
