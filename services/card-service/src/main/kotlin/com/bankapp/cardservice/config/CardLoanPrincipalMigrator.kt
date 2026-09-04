package com.bankapp.cardservice.config

import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.core.annotation.Order
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component

@Component
@Order(2)
class CardLoanPrincipalMigrator(
    private val jdbcTemplate: JdbcTemplate,
) : ApplicationRunner {
    override fun run(args: ApplicationArguments) {
        jdbcTemplate.execute(
            """
            ALTER TABLE cards ADD COLUMN IF NOT EXISTS loan_principal NUMERIC(19,2);
            UPDATE cards SET loan_principal = 0 WHERE loan_principal IS NULL;
            ALTER TABLE cards ALTER COLUMN loan_principal SET DEFAULT 0;
            ALTER TABLE cards ALTER COLUMN loan_principal SET NOT NULL;
            """.trimIndent(),
        )
    }
}
