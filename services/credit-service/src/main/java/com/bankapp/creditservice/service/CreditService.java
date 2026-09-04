package com.bankapp.creditservice.service;

import com.bankapp.creditservice.domain.CreditApplication;
import com.bankapp.creditservice.domain.CreditStatus;
import com.bankapp.creditservice.dto.CreditCalculationRequest;
import com.bankapp.creditservice.dto.CreditCalculationResponse;
import com.bankapp.creditservice.event.CreditCalculatedEvent;
import com.bankapp.creditservice.repository.CreditApplicationRepository;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CreditService {

    private static final BigDecimal MONTHS_IN_YEAR = new BigDecimal("12");
    private static final BigDecimal COLLATERAL_LTV = new BigDecimal("0.70");
    private static final BigDecimal MAX_PAYMENT_TO_INCOME = new BigDecimal("0.40");
    private static final MathContext MC = new MathContext(16, RoundingMode.HALF_UP);

    private final CreditApplicationRepository repository;
    private final RabbitTemplate rabbitTemplate;
    private final String exchange;
    private final String routingKey;
    private final BigDecimal annualInterestRate;

    public CreditService(
            CreditApplicationRepository repository,
            RabbitTemplate rabbitTemplate,
            @Value("${app.rabbitmq.exchange}") String exchange,
            @Value("${app.rabbitmq.routing-key}") String routingKey,
            @Value("${app.credit.annual-interest-rate:8.5}") BigDecimal annualInterestRate
    ) {
        this.repository = repository;
        this.rabbitTemplate = rabbitTemplate;
        this.exchange = exchange;
        this.routingKey = routingKey;
        this.annualInterestRate = annualInterestRate;
    }

    @Transactional
    public CreditCalculationResponse calculate(CreditCalculationRequest request) {
        BigDecimal collateral = request.getAiCollateralValueEur() != null
                ? request.getAiCollateralValueEur()
                : BigDecimal.ZERO;

        BigDecimal monthlyPayment = annuityPayment(
                request.getRequestedAmount(),
                annualInterestRate,
                request.getTermMonths()
        );

        BigDecimal approvedLimit = request.getRequestedAmount();
        if (collateral.compareTo(BigDecimal.ZERO) > 0) {
            approvedLimit = approvedLimit.add(
                    collateral.multiply(COLLATERAL_LTV, MC).setScale(2, RoundingMode.HALF_UP)
            );
        }

        CreditStatus status = isAffordable(monthlyPayment, request.getMonthlyIncome())
                ? CreditStatus.APPROVED
                : CreditStatus.REJECTED;

        CreditApplication saved = repository.save(CreditApplication.builder()
                .requestedAmount(request.getRequestedAmount())
                .monthlyIncome(request.getMonthlyIncome())
                .termMonths(request.getTermMonths())
                .aiCollateralValueEur(collateral)
                .monthlyPayment(monthlyPayment)
                .interestRate(annualInterestRate)
                .approvedLimit(approvedLimit)
                .status(status)
                .build());

        rabbitTemplate.convertAndSend(exchange, routingKey, CreditCalculatedEvent.builder()
                .applicationId(saved.getId())
                .requestedAmount(saved.getRequestedAmount())
                .monthlyPayment(saved.getMonthlyPayment())
                .interestRate(saved.getInterestRate())
                .approvedLimit(saved.getApprovedLimit())
                .status(saved.getStatus().name())
                .createdAt(saved.getCreatedAt())
                .build());

        return CreditCalculationResponse.builder()
                .monthlyPayment(saved.getMonthlyPayment())
                .interestRate(saved.getInterestRate())
                .approvedLimit(saved.getApprovedLimit())
                .status(saved.getStatus().name())
                .build();
    }

    /**
     * M = P * r(1+r)^n / ((1+r)^n - 1), r is monthly rate from annual percent.
     */
    BigDecimal annuityPayment(BigDecimal principal, BigDecimal annualPercent, int termMonths) {
        if (termMonths <= 0) {
            throw new IllegalArgumentException("termMonths must be between 1 and 120");
        }
        if (termMonths > 120) {
            throw new IllegalArgumentException("termMonths must be between 1 and 120");
        }
        BigDecimal monthlyRate = annualPercent
                .divide(new BigDecimal("100"), MC)
                .divide(MONTHS_IN_YEAR, MC);
        if (monthlyRate.compareTo(BigDecimal.ZERO) == 0) {
            return principal.divide(BigDecimal.valueOf(termMonths), 2, RoundingMode.HALF_UP);
        }
        double r = monthlyRate.doubleValue();
        double factor = Math.pow(1.0 + r, termMonths);
        double payment = principal.doubleValue() * (r * factor) / (factor - 1.0);
        return BigDecimal.valueOf(payment).setScale(2, RoundingMode.HALF_UP);
    }

    BigDecimal getAnnualInterestRate() {
        return annualInterestRate;
    }

    private boolean isAffordable(BigDecimal monthlyPayment, BigDecimal monthlyIncome) {
        BigDecimal maxPayment = monthlyIncome.multiply(MAX_PAYMENT_TO_INCOME, MC);
        return monthlyPayment.compareTo(maxPayment) <= 0;
    }
}
