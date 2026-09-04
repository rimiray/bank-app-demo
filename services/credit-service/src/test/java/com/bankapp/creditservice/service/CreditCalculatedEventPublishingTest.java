package com.bankapp.creditservice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bankapp.creditservice.domain.CreditApplication;
import com.bankapp.creditservice.domain.CreditStatus;
import com.bankapp.creditservice.dto.CreditCalculationRequest;
import com.bankapp.creditservice.event.CreditCalculatedEvent;
import com.bankapp.creditservice.repository.CreditApplicationRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

@ExtendWith(MockitoExtension.class)
class CreditCalculatedEventPublishingTest {

    @Mock
    private CreditApplicationRepository repository;

    @Mock
    private RabbitTemplate rabbitTemplate;

    @Captor
    private ArgumentCaptor<CreditCalculatedEvent> eventCaptor;

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
    void should_publishCreditCalculatedEvent_toBankEventsExchangeWithCreditCalculatedRoutingKey() {
        UUID applicationId = UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee");
        Instant createdAt = Instant.parse("2026-01-15T10:00:00Z");

        when(repository.save(any(CreditApplication.class))).thenAnswer(invocation -> {
            CreditApplication app = invocation.getArgument(0);
            app.setId(applicationId);
            app.setCreatedAt(createdAt);
            return app;
        });

        CreditCalculationRequest request = new CreditCalculationRequest(
                new BigDecimal("10000.00"),
                new BigDecimal("3500.00"),
                24,
                new BigDecimal("150.00")
        );

        creditService.calculate(request);

        verify(rabbitTemplate).convertAndSend(
                eq("bank.events"),
                eq("credit.calculated"),
                eventCaptor.capture()
        );

        CreditCalculatedEvent event = eventCaptor.getValue();
        assertThat(event.getApplicationId()).isEqualTo(applicationId);
        assertThat(event.getRequestedAmount()).isEqualByComparingTo("10000.00");
        assertThat(event.getInterestRate()).isEqualByComparingTo("8.5");
        assertThat(event.getMonthlyPayment()).isNotNull();
        assertThat(event.getMonthlyPayment().scale()).isEqualTo(2);
        assertThat(event.getApprovedLimit()).isEqualByComparingTo("10105.00"); // 10000 + 150*0.7
        assertThat(event.getStatus()).isEqualTo(CreditStatus.APPROVED.name());
        assertThat(event.getCreatedAt()).isEqualTo(createdAt);
    }
}
