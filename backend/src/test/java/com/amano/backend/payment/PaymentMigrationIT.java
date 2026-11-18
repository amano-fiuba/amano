package com.amano.backend.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import com.amano.backend.support.AbstractIntegrationIT;

/**
 * 018 T005/T006: Flyway applies {@code V14__create_payments.sql} on a real
 * PostgreSQL and the constraints of data-model.md hold even for rows written
 * by hand. Starting the context also runs {@code ddl-auto=validate}, so the
 * {@code Payment} mapping must agree with the migration (T012).
 */
class PaymentMigrationIT extends AbstractIntegrationIT {

    @Autowired
    private JdbcTemplate jdbc;

    private long ana;
    private long juan;
    private long groupId;
    private long profileId;
    private long debtId;

    @BeforeEach
    void seed() {
        ana = user("ana");
        juan = user("juan");
        groupId = jdbc.queryForObject("INSERT INTO groups (name) VALUES ('Depto') RETURNING id", Long.class);
        profileId = jdbc.queryForObject(
                "INSERT INTO simulated_profiles (group_id, name, email) VALUES (?, 'Abuela', ?) RETURNING id",
                Long.class, groupId, "abuela-" + System.nanoTime() + "@amano.dev");
        long expenseId = jdbc.queryForObject("""
                INSERT INTO group_expenses (group_id, created_by_user_id, payer_user_id, amount, currency,
                    expense_date, idempotency_key)
                VALUES (?, ?, ?, 100.00, 'ARS', CURRENT_DATE, gen_random_uuid()) RETURNING id
                """, Long.class, groupId, ana, ana);
        long shareId = jdbc.queryForObject("""
                INSERT INTO group_expense_shares (expense_id, position, participant_type, user_id,
                    participant_name, amount)
                VALUES (?, 1, 'REGISTERED', ?, 'Juan', 50.00) RETURNING id
                """, Long.class, expenseId, juan);
        debtId = jdbc.queryForObject("""
                INSERT INTO debts (group_id, expense_id, share_id, debtor_type, debtor_user_id,
                    debtor_name, creditor_user_id, currency, amount)
                VALUES (?, ?, ?, 'REGISTERED', ?, 'Juan', ?, 'ARS', 50.00) RETURNING id
                """, Long.class, groupId, expenseId, shareId, juan, ana);
    }

    private long user(String name) {
        return jdbc.queryForObject("""
                INSERT INTO users (first_name, email, password_hash, terms_accepted_at)
                VALUES (?, ?, 'x', now()) RETURNING id
                """, Long.class, name, name + "-" + System.nanoTime() + "@amano.dev");
    }

    /** A payment row with every column chosen by the test. */
    private void payment(String payerType, Long payerUser, Long payerProfile, long receiver, long registeredBy,
            String amount, String currency, UUID key) {
        jdbc.update("""
                INSERT INTO payments (group_id, debt_id, payer_type, payer_user_id, payer_simulated_profile_id,
                    payer_name, receiver_user_id, receiver_name, registered_by_user_id, amount, currency,
                    payment_date, idempotency_key, status, legacy)
                VALUES (?, ?, ?, ?, ?, 'Pagador', ?, 'Ana', ?, ?::numeric, ?, CURRENT_DATE, ?, 'CONFIRMED', TRUE)
                """, groupId, debtId, payerType, payerUser, payerProfile, receiver, registeredBy, amount, currency,
                key);
    }

    private void validRegistered(String amount) {
        payment("REGISTERED", juan, null, ana, juan, amount, "ARS", UUID.randomUUID());
    }

    /** A 022 payment row (legacy FALSE) with the given status. */
    private void activePayment(String status) {
        jdbc.update("""
                INSERT INTO payments (group_id, debt_id, payer_type, payer_user_id, payer_simulated_profile_id,
                    payer_name, receiver_user_id, receiver_name, registered_by_user_id, amount, currency,
                    payment_date, idempotency_key, status, legacy)
                VALUES (?, ?, 'REGISTERED', ?, NULL, 'Pagador', ?, 'Ana', ?, 50.00, 'ARS',
                    CURRENT_DATE, ?, ?, FALSE)
                """, groupId, debtId, juan, ana, juan, UUID.randomUUID(), status);
    }

    @Test
    void theTableAndItsIndexesExist() {
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM information_schema.tables
                WHERE table_schema = 'public' AND table_name = 'payments'
                """, Integer.class)).isEqualTo(1);
        List<String> indexes = jdbc.queryForList(
                "SELECT indexname FROM pg_indexes WHERE tablename = 'payments' ORDER BY indexname", String.class);
        assertThat(indexes).contains("ix_payments_debt", "ix_payments_group",
                "uq_payments_registered_by_idempotency_key", "uq_payments_reported_debt");
        // V25 (022 T079): the V18 index that limited a debt to one active payment is gone.
        assertThat(indexes).doesNotContain("uq_payments_active_debt");
        assertThat(jdbc.queryForObject(
                "SELECT indexdef FROM pg_indexes WHERE indexname = 'uq_payments_reported_debt'", String.class))
                .contains("UNIQUE", "(debt_id)", "REPORTED");
    }

    /**
     * 022 T079 (D6, V25): at most one payment waits for validation per debt, and
     * any number of CONFIRMED payments (partial payments) can coexist with it.
     * Replaces the V18 rule "one active non-legacy payment per debt", whose
     * intent (no double processing of a debt) now lives in the row lock and the
     * balance check; the database keeps the one-REPORTED-at-a-time guarantee.
     */
    @Test
    void onlyOneReportedPaymentIsAllowedPerDebtAndConfirmedOnesCoexist() {
        activePayment("REPORTED");

        // A second payment waiting for validation is rejected by the database...
        assertThatThrownBy(() -> activePayment("REPORTED"))
                .isInstanceOf(DataIntegrityViolationException.class);
        // ...whatever its legacy flag.
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO payments (group_id, debt_id, payer_type, payer_user_id, payer_name,
                    receiver_user_id, receiver_name, registered_by_user_id, amount, currency,
                    payment_date, idempotency_key, status, legacy)
                VALUES (?, ?, 'REGISTERED', ?, 'Pagador', ?, 'Ana', ?, 5.00, 'ARS',
                    CURRENT_DATE, ?, 'REPORTED', TRUE)
                """, groupId, debtId, juan, ana, juan, UUID.randomUUID()))
                .isInstanceOf(DataIntegrityViolationException.class);

        // Several CONFIRMED payments (legacy or not) live next to the reported one.
        activePayment("CONFIRMED");
        activePayment("CONFIRMED");
        validRegistered("1.00");
        validRegistered("2.00");
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM payments WHERE debt_id = ? AND status = 'CONFIRMED'",
                Integer.class, debtId)).isEqualTo(4);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM payments WHERE debt_id = ? AND status = 'REPORTED'",
                Integer.class, debtId)).isEqualTo(1);

        // Once the reported payment is decided, the debt accepts a new one.
        jdbc.update("UPDATE payments SET status = 'REJECTED' WHERE debt_id = ? AND status = 'REPORTED'", debtId);
        activePayment("REPORTED");
        jdbc.update("UPDATE payments SET status = 'CONFIRMED' WHERE debt_id = ? AND status = 'REPORTED'", debtId);
        activePayment("REPORTED");
    }

    @Test
    void validRegisteredAndSimulatedPaymentsAreAccepted() {
        validRegistered("10.00");
        payment("SIMULATED", null, profileId, ana, ana, "0.01", "USD", UUID.randomUUID());

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM payments WHERE debt_id = ?", Integer.class, debtId))
                .isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT created_at IS NOT NULL FROM payments LIMIT 1", Boolean.class))
                .isTrue();
    }

    @Test
    void thePayerMustMatchItsType() {
        assertThatThrownBy(() -> payment("OTHER", juan, null, ana, juan, "1.00", "ARS", UUID.randomUUID()))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> payment("REGISTERED", null, null, ana, juan, "1.00", "ARS", UUID.randomUUID()))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> payment("REGISTERED", juan, profileId, ana, juan, "1.00", "ARS", UUID.randomUUID()))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> payment("SIMULATED", juan, profileId, ana, ana, "1.00", "ARS", UUID.randomUUID()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void nobodyPaysThemself() {
        assertThatThrownBy(() -> payment("REGISTERED", ana, null, ana, ana, "1.00", "ARS", UUID.randomUUID()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void amountAndCurrencyAreChecked() {
        assertThatThrownBy(() -> validRegistered("0"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> payment("REGISTERED", juan, null, ana, juan, "1.00", "XAU", UUID.randomUUID()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void theIdempotencyKeyIsUniquePerRegisteringUser() {
        UUID key = UUID.randomUUID();
        payment("REGISTERED", juan, null, ana, juan, "1.00", "ARS", key);
        payment("REGISTERED", juan, null, ana, ana, "1.00", "ARS", key);

        assertThatThrownBy(() -> payment("REGISTERED", juan, null, ana, juan, "2.00", "ARS", key))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void removingTheSimulatedProfileKeepsThePaymentAndItsName() {
        payment("SIMULATED", null, profileId, ana, ana, "5.00", "ARS", UUID.randomUUID());

        jdbc.update("DELETE FROM simulated_profiles WHERE id = ?", profileId);

        var row = jdbc.queryForMap("""
                SELECT payer_simulated_profile_id, payer_name
                FROM payments
                WHERE payer_type = 'SIMULATED' AND debt_id = ?
                """, debtId);
        assertThat(row.get("payer_simulated_profile_id")).isNull();
        assertThat(row.get("payer_name")).isEqualTo("Pagador");
    }
}
