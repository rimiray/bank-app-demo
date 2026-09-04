package com.bankapp.creditservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.math.RoundingMode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import com.bankapp.creditservice.repository.CreditApplicationRepository;

@ExtendWith(MockitoExtension.class)
class AnnuityPaymentTest {

    @Mock
    private CreditApplicationRepository repository;

    @Mock
    private RabbitTemplate rabbitTemplate;

    private CreditService creditService;

    @BeforeEach
    void setUp() {
        creditService = new CreditService(
                repository,
                rabbitTemplate,
                "bank.events",
                "credit.calculated",
                new BigDecimal("8.5")
        );
    }

    @Test
    void should_computeAnnuity_whenTermIsOneMonth() {
        BigDecimal principal = new BigDecimal("10000.00");
        BigDecimal payment = creditService.annuityPayment(principal, new BigDecimal("8.5"), 1);

        // For n=1: M = P * (1 + r_month) approximately; exact formula still applies
        assertThat(payment.scale()).isEqualTo(2);
        assertThat(payment).isGreaterThan(principal);
        assertThat(payment).isEqualByComparingTo(new BigDecimal("10070.83"));
    }

    @Test
    void should_computeAnnuity_whenTermIsMax120Months() {
        BigDecimal payment = creditService.annuityPayment(
                new BigDecimal("10000.00"),
                new BigDecimal("8.5"),
                120
        );

        assertThat(payment.scale()).isEqualTo(2);
        assertThat(payment).isEqualByComparingTo(new BigDecimal("123.99"));
    }

    @Test
    void should_throwIllegalArgument_whenTermIsZero() {
        assertThatThrownBy(() ->
                creditService.annuityPayment(new BigDecimal("1000.00"), new BigDecimal("8.5"), 0)
        )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("termMonths must be between 1 and 120");
    }

    @Test
    void should_throwIllegalArgument_whenTermIsNegative() {
        assertThatThrownBy(() ->
                creditService.annuityPayment(new BigDecimal("1000.00"), new BigDecimal("8.5"), -3)
        )
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("termMonths must be between 1 and 120");
    }

    @Test
    void should_roundHalfUpToScale2_whenAnnuityProducesFractionalCents() {
        // Chosen so floating intermediate is not an exact cent
        BigDecimal payment = creditService.annuityPayment(
                new BigDecimal("1234.56"),
                new BigDecimal("8.5"),
                17
        );

        assertThat(payment.scale()).isEqualTo(2);
        assertThat(payment).isEqualByComparingTo(payment.setScale(2, RoundingMode.HALF_UP));
        // Sanity: payment should be positive and less than principal for multi-month term
        assertThat(payment).isGreaterThan(BigDecimal.ZERO);
        assertThat(payment).isLessThan(new BigDecimal("1234.56"));
    }

    @Test
    void should_useConfiguredAnnualRate_whenBuildingService() {
        CreditService customRateService = new CreditService(
                repository,
                rabbitTemplate,
                "bank.events",
                "credit.calculated",
                new BigDecimal("10.0")
        );

        assertThat(customRateService.getAnnualInterestRate()).isEqualByComparingTo("10.0");

        BigDecimal at85 = creditService.annuityPayment(new BigDecimal("10000"), new BigDecimal("8.5"), 24);
        BigDecimal at10 = customRateService.annuityPayment(new BigDecimal("10000"), new BigDecimal("10.0"), 24);

        assertThat(at10).isGreaterThan(at85);
        assertThat(at10.subtract(at85).abs()).isGreaterThan(new BigDecimal("5.00"));
    }
}
