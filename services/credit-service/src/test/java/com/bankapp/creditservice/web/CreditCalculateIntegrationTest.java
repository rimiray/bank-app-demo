package com.bankapp.creditservice.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bankapp.creditservice.domain.CreditApplication;
import com.bankapp.creditservice.domain.CreditStatus;
import com.bankapp.creditservice.repository.CreditApplicationRepository;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest
@AutoConfigureMockMvc
class CreditCalculateIntegrationTest {

    private static final Logger log = LoggerFactory.getLogger(CreditCalculateIntegrationTest.class);

    /**
     * Prefer an ephemeral Testcontainers Postgres. If the Docker API is unavailable
     * to Testcontainers (seen with some Docker Desktop setups), fall back to the
     * local docker-compose {@code bank_db} on localhost:5432.
     */
    private static final PostgreSQLContainer<?> POSTGRES;

    static {
        PostgreSQLContainer<?> container = new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"))
                .withDatabaseName("bank_db_test")
                .withUsername("bank_user")
                .withPassword("bank_password");
        PostgreSQLContainer<?> started = null;
        try {
            container.start();
            started = container;
            log.info("Using Testcontainers PostgreSQL at {}", container.getJdbcUrl());
        } catch (Throwable ex) {
            log.warn(
                    "Testcontainers PostgreSQL unavailable ({}), falling back to localhost:5432 bank_db",
                    ex.toString()
            );
            try {
                container.close();
            } catch (Exception ignored) {
                // best-effort cleanup
            }
        }
        POSTGRES = started;
    }

    @DynamicPropertySource
    static void registerDatasource(DynamicPropertyRegistry registry) {
        if (POSTGRES != null && POSTGRES.isRunning()) {
            registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
            registry.add("spring.datasource.username", POSTGRES::getUsername);
            registry.add("spring.datasource.password", POSTGRES::getPassword);
        } else {
            registry.add("spring.datasource.url", () -> "jdbc:postgresql://localhost:5432/bank_db");
            registry.add("spring.datasource.username", () -> "bank_user");
            registry.add("spring.datasource.password", () -> "bank_password");
        }
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "update");
        registry.add("app.credit.annual-interest-rate", () -> "8.5");
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CreditApplicationRepository repository;

    @MockBean
    private RabbitTemplate rabbitTemplate;

    @BeforeEach
    void cleanApplications() {
        repository.deleteAll();
    }

    @Test
    void should_persistCreditApplication_whenCalculateEndpointSucceeds() throws Exception {
        mockMvc.perform(post("/api/v1/credits/calculate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "requestedAmount": 10000.00,
                                  "monthlyIncome": 3500.00,
                                  "termMonths": 24,
                                  "aiCollateralValueEur": 150.00
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.interestRate").value(8.5));

        List<CreditApplication> saved = repository.findAll();
        assertThat(saved).hasSize(1);

        CreditApplication app = saved.getFirst();
        assertThat(app.getId()).isNotNull();
        assertThat(app.getRequestedAmount()).isEqualByComparingTo("10000.00");
        assertThat(app.getMonthlyIncome()).isEqualByComparingTo("3500.00");
        assertThat(app.getTermMonths()).isEqualTo(24);
        assertThat(app.getAiCollateralValueEur()).isEqualByComparingTo("150.00");
        assertThat(app.getInterestRate()).isEqualByComparingTo("8.5");
        assertThat(app.getApprovedLimit()).isEqualByComparingTo("10105.00");
        assertThat(app.getMonthlyPayment()).isNotNull();
        assertThat(app.getMonthlyPayment().scale()).isEqualTo(2);
        assertThat(app.getStatus()).isEqualTo(CreditStatus.APPROVED);
        assertThat(app.getCreatedAt()).isNotNull();
    }

    @Test
    void should_return400_whenTermMonthsIsZero() throws Exception {
        mockMvc.perform(post("/api/v1/credits/calculate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "requestedAmount": 10000.00,
                                  "monthlyIncome": 3500.00,
                                  "termMonths": 0
                                }
                                """))
                .andExpect(status().isBadRequest());
    }
}
