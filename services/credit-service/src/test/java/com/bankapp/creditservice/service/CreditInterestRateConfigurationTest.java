package com.bankapp.creditservice.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.TestPropertySource;
import com.bankapp.creditservice.repository.CreditApplicationRepository;

@SpringBootTest(classes = CreditService.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
@TestPropertySource(properties = {
        "app.rabbitmq.exchange=bank.events",
        "app.rabbitmq.routing-key=credit.calculated",
        "app.credit.annual-interest-rate=12.5",
        "spring.autoconfigure.exclude="
                + "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,"
                + "org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration,"
                + "org.springframework.boot.autoconfigure.amqp.RabbitAutoConfiguration,"
                + "org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration"
})
class CreditInterestRateConfigurationTest {

    @MockBean
    private CreditApplicationRepository repository;

    @MockBean
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private CreditService creditService;

    @Test
    void should_loadAnnualInterestRate_fromApplicationProperties() {
        assertThat(creditService.getAnnualInterestRate()).isEqualByComparingTo("12.5");
    }
}
