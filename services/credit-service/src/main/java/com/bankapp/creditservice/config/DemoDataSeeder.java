package com.bankapp.creditservice.config;

import com.bankapp.creditservice.domain.CreditApplication;
import com.bankapp.creditservice.domain.CreditStatus;
import com.bankapp.creditservice.repository.CreditApplicationRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Seeds one approved credit application when the table is empty so live/demo
 * environments show persisted scoring history immediately.
 */
@Component
public class DemoDataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);

    private final CreditApplicationRepository repository;

    public DemoDataSeeder(CreditApplicationRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (repository.count() > 0) {
            return;
        }

        CreditApplication demo = CreditApplication.builder()
                .id(UUID.fromString("11111111-1111-1111-1111-111111111111"))
                .requestedAmount(new BigDecimal("10000.00"))
                .monthlyIncome(new BigDecimal("3500.00"))
                .termMonths(24)
                .aiCollateralValueEur(new BigDecimal("150.00"))
                .monthlyPayment(new BigDecimal("455.23"))
                .interestRate(new BigDecimal("8.50"))
                .approvedLimit(new BigDecimal("10105.00"))
                .status(CreditStatus.APPROVED)
                .createdAt(Instant.parse("2026-01-15T10:00:00Z"))
                .build();

        repository.save(demo);
        log.info("Seeded demo credit application id={}", demo.getId());
    }
}
