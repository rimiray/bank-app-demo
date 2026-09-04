package com.bankapp.cardservice.config

import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.core.annotation.Order
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component

/**
 * Ensure cards.created_at exists and is backfilled in physical insert order
 * (ctid) so UI can sort stably. Hibernate ddl-auto does not reliably add NOT NULL
 * columns to non-empty tables.
 */
@Component
@Order(1)
class CardCreatedAtMigrator(
    private val jdbcTemplate: JdbcTemplate,
) : ApplicationRunner {
    override fun run(args: ApplicationArguments) {
        jdbcTemplate.execute(
            """
            ALTER TABLE cards ADD COLUMN IF NOT EXISTS created_at TIMESTAMPTZ;
            """.trimIndent(),
        )
        jdbcTemplate.execute(
            """
            WITH ordered AS (
                SELECT id, row_number() OVER (ORDER BY ctid) AS rn
                FROM cards
            )
            UPDATE cards c
            SET created_at = TIMESTAMPTZ '2024-01-01 00:00:00+00'
                + ((o.rn - 1) * INTERVAL '1 second')
            FROM ordered o
            WHERE c.id = o.id
              AND c.created_at IS NULL;
            """.trimIndent(),
        )
        jdbcTemplate.execute(
            """
            ALTER TABLE cards ALTER COLUMN created_at SET DEFAULT NOW();
            UPDATE cards SET created_at = NOW() WHERE created_at IS NULL;
            ALTER TABLE cards ALTER COLUMN created_at SET NOT NULL;
            """.trimIndent(),
        )
    }
}
