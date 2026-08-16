package ali.com.banking.banking_backend.repository;

import ali.com.banking.banking_backend.entity.Customer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CustomerRepository extends JpaRepository<Customer, Long> {

    Optional<Customer> findByEmail(String email);

    boolean existsByEmail(String email);

    boolean existsByPhone(String phone);

    boolean existsByNationalId(String nationalId);

    Optional<Customer> findByNationalId(String nationalId);
}
