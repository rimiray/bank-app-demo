package com.bankapp.creditservice.repository;

import com.bankapp.creditservice.domain.CreditApplication;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CreditApplicationRepository extends JpaRepository<CreditApplication, UUID> {
}
