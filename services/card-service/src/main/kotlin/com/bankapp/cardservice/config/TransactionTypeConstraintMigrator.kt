package com.bankapp.cardservice.config

import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component

/**
 * Hibernate ddl-auto=update does not refresh Postgres CHECK constraints when
 * enum values are added. Align transactions_type_check on startup.
 */
@Component
class TransactionTypeConstraintMigrator(
    private val jdbcTemplate: JdbcTemplate,
) : ApplicationRunner {
    override fun run(args: ApplicationArguments) {
        jdbcTemplate.execute(
            """
            ALTER TABLE transactions DROP CONSTRAINT IF EXISTS transactions_type_check;
            ALTER TABLE transactions ADD CONSTRAINT transactions_type_check
                CHECK (type IN ('TOPUP', 'PURCHASE', 'CREDIT_DISBURSEMENT'));
            """.trimIndent(),
        )
    }
}
