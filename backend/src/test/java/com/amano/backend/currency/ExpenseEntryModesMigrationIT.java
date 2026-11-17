package com.amano.backend.currency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Arrays;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import com.amano.backend.support.AbstractIntegrationIT;
import com.amano.backend.user.Currency;

/**
 * 027 T017/T046: V29 widens the currency CHECKs of expenses, group expenses,
 * debts, payments and preferences to the {@link Currency} catalog (leaving the
 * group primary currency on ARS/USD/EUR) and V30 adds the nullable 007
 * classification to group expenses; V31 lets an expense with a friend live
 * without group (exactly one of group / friend) and its debts without group.
 */
class ExpenseEntryModesMigrationIT extends AbstractIntegrationIT {

    @Autowired
    private JdbcTemplate jdbc;

    private String definitionOf(String constraint) {
        return jdbc.queryForObject("SELECT pg_get_constraintdef(oid) FROM pg_constraint WHERE conname = ?",
                String.class, constraint);
    }

    @Test
    void widenedChecksListExactlyTheEnumCurrencies() {
        for (String constraint : new String[] {"ck_expenses_currency", "ck_group_expenses_currency",
                "ck_debts_currency", "ck_payments_currency", "ck_user_currencies_code"}) {
            String definition = definitionOf(constraint);
            Arrays.stream(Currency.values()).forEach(currency ->
                    assertThat(definition).as(constraint).contains("'" + currency.name() + "'"));
            assertThat(definition).as(constraint).doesNotContain("'XAU'", "'VND'", "'MXP'");
        }
    }

    @Test
    void groupPrimaryCurrencyKeepsTheOriginalCatalog() {
        assertThat(definitionOf("ck_groups_primary_currency"))
                .contains("'ARS'", "'USD'", "'EUR'")
                .doesNotContain("'BRL'");
    }

    @Test
    void preferencesAcceptNewCurrenciesAndRejectUnsupportedOnes() {
        Long userId = jdbc.queryForObject("""
                INSERT INTO users (first_name, email, password_hash, terms_accepted_at)
                VALUES ('Ana', ?, 'x', now()) RETURNING id
                """, Long.class, "ana-" + System.nanoTime() + "@amano.dev");

        jdbc.update("INSERT INTO user_currencies (user_id, currency_code) VALUES (?, 'BRL')", userId);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM user_currencies WHERE user_id = ?",
                Integer.class, userId)).isOne();

        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO user_currencies (user_id, currency_code) VALUES (?, 'XAU')", userId))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private long groupExpenseWith(String category, String type, String paymentMethod) {
        Long userId = jdbc.queryForObject("""
                INSERT INTO users (first_name, email, password_hash, terms_accepted_at)
                VALUES ('Juan', ?, 'x', now()) RETURNING id
                """, Long.class, "juan-" + System.nanoTime() + "@amano.dev");
        Long groupId = jdbc.queryForObject("INSERT INTO groups (name) VALUES ('Depto') RETURNING id", Long.class);
        return jdbc.queryForObject("""
                INSERT INTO group_expenses (group_id, created_by_user_id, payer_user_id, amount, currency,
                    expense_date, idempotency_key, category, expense_type, payment_method)
                VALUES (?, ?, ?, 10.00, 'ARS', CURRENT_DATE, gen_random_uuid(), ?, ?, ?) RETURNING id
                """, Long.class, groupId, userId, userId, category, type, paymentMethod);
    }

    @Test
    void groupExpensesAcceptTheClassificationCatalogsAndNull() {
        assertThat(groupExpenseWith("PETS", "RECURRING", "VIRTUAL_WALLET")).isPositive();
        assertThat(groupExpenseWith(null, null, null)).isPositive();
    }

    @Test
    void groupExpensesRejectValuesOutsideTheCatalogs() {
        assertThatThrownBy(() -> groupExpenseWith("PARTY", "ONE_TIME", "CASH"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> groupExpenseWith("FOOD", "WEEKLY", "CASH"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> groupExpenseWith("FOOD", "ONE_TIME", "BITCOIN"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void anExpenseHasExactlyOneOfGroupOrFriend() {
        Long a = jdbc.queryForObject("INSERT INTO users (first_name, email, password_hash, terms_accepted_at)"
                + " VALUES ('Ana', ?, 'x', now()) RETURNING id", Long.class, "a-" + System.nanoTime() + "@amano.dev");
        Long b = jdbc.queryForObject("INSERT INTO users (first_name, email, password_hash, terms_accepted_at)"
                + " VALUES ('Beto', ?, 'x', now()) RETURNING id", Long.class, "b-" + System.nanoTime() + "@amano.dev");
        Long groupId = jdbc.queryForObject("INSERT INTO groups (name) VALUES ('G') RETURNING id", Long.class);
        String insert = """
                INSERT INTO group_expenses (group_id, friend_user_id, created_by_user_id, payer_user_id, amount,
                    currency, expense_date, idempotency_key)
                VALUES (?, ?, ?, ?, 10.00, 'ARS', CURRENT_DATE, gen_random_uuid()) RETURNING id
                """;

        Long direct = jdbc.queryForObject(insert, Long.class, null, b, a, a);
        assertThat(direct).isPositive();
        assertThatThrownBy(() -> jdbc.queryForObject(insert, Long.class, null, null, a, a))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.queryForObject(insert, Long.class, groupId, b, a, a))
                .isInstanceOf(DataIntegrityViolationException.class);

        Long share = jdbc.queryForObject("""
                INSERT INTO group_expense_shares (expense_id, position, participant_type, user_id, participant_name,
                    amount) VALUES (?, 1, 'REGISTERED', ?, 'B', 5.00) RETURNING id
                """, Long.class, direct, b);
        jdbc.update("""
                INSERT INTO debts (group_id, expense_id, share_id, debtor_type, debtor_user_id, debtor_name,
                    creditor_user_id, currency, amount, paid_amount, status, created_at, updated_at)
                VALUES (NULL, ?, ?, 'REGISTERED', ?, 'B', ?, 'ARS', 5.00, 0, 'PENDING', now(), now())
                """, direct, share, b, a);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM debts WHERE expense_id = ? AND group_id IS NULL",
                Integer.class, direct)).isOne();
    }
}
