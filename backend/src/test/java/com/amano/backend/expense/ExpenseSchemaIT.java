package com.amano.backend.expense;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import com.amano.backend.support.AbstractIntegrationIT;

/**
 * Migration T004: Flyway applies {@code V4__create_expenses.sql} on a real
 * PostgreSQL and the resulting schema matches data-model.md. Starting the Spring
 * context also runs {@code ddl-auto=validate}, so the JPA mapping of
 * {@link Expense} must agree with the migration.
 */
class ExpenseSchemaIT extends AbstractIntegrationIT {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void flywayCreatesTheExpensesTableWithTheExpectedColumns() {
        Integer tables = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM information_schema.tables
                WHERE table_schema = 'public' AND table_name = 'expenses'
                """, Integer.class);
        assertThat(tables).isEqualTo(1);

        List<String> columns = jdbcTemplate.queryForList("""
                SELECT column_name FROM information_schema.columns
                WHERE table_schema = 'public' AND table_name = 'expenses'
                ORDER BY ordinal_position
                """, String.class);

        assertThat(columns).containsExactly(
                "id", "user_id", "amount", "currency", "expense_date", "category",
                "expense_type", "payment_method", "description", "idempotency_key", "created_at",
                // V5 (feature 009): recurrence columns, all nullable.
                "recurrence_frequency", "recurrence_status", "recurrence_next_date",
                "recurrence_source_id");
    }

    @Test
    void expensesCarriesTheKeyConstraints() {
        List<String> constraints = jdbcTemplate.queryForList("""
                SELECT conname FROM pg_constraint
                WHERE conrelid = 'public.expenses'::regclass
                """, String.class);

        assertThat(constraints).contains(
                "fk_expenses_user",
                "ck_expenses_amount",
                "ck_expenses_currency",
                "ck_expenses_category",
                "ck_expenses_type",
                "ck_expenses_payment_method",
                "ck_expenses_description",
                "uq_expenses_user_idempotency_key");
    }

    @Test
    void userIdempotencyKeyIsUniqueAndIndexed() {
        String definition = jdbcTemplate.queryForObject("""
                SELECT indexdef FROM pg_indexes
                WHERE schemaname = 'public' AND tablename = 'expenses'
                  AND indexname = 'uq_expenses_user_idempotency_key'
                """, String.class);

        assertThat(definition)
                .contains("UNIQUE")
                .contains("user_id")
                .contains("idempotency_key");
    }

    @Test
    void foreignKeyRemovesExpensesWithTheirAccount() {
        String definition = jdbcTemplate.queryForObject("""
                SELECT pg_get_constraintdef(oid) FROM pg_constraint
                WHERE conname = 'fk_expenses_user'
                """, String.class);

        assertThat(definition)
                .contains("FOREIGN KEY (user_id) REFERENCES users(id)")
                .contains("ON DELETE CASCADE");
    }
}
