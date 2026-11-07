package com.amano.backend.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * 018 T005/T006: Flyway applies {@code V14__create_payments.sql} on a real
 * PostgreSQL and the constraints of data-model.md hold even for rows written
 * by hand. Starting the context also runs {@code ddl-auto=validate}, so the
 * {@code Payment} mapping must agree with the migration (T012).
 */
@SpringBootTest(properties = {
        "jwt.secret=2b7e151628aed2a6abf7158809cf4f3c2b7e151628aed2a6abf7158809cf4f3c"
})
@Testcontainers
class PaymentMigrationIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18-alpine");

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

    @Test
    void theTableAndItsIndexesExist() {
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM information_schema.tables
                WHERE table_schema = 'public' AND table_name = 'payments'
                """, Integer.class)).isEqualTo(1);
        List<String> indexes = jdbc.queryForList(
                "SELECT indexname FROM pg_indexes WHERE tablename = 'payments' ORDER BY indexname", String.class);
        assertThat(indexes).contains("ix_payments_debt", "ix_payments_group",
                "uq_payments_registered_by_idempotency_key");
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
        assertThatThrownBy(() -> payment("REGISTERED", juan, null, ana, juan, "1.00", "BRL", UUID.randomUUID()))
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

        var row = jdbc.queryForMap("SELECT payer_simulated_profile_id, payer_name FROM payments WHERE payer_type = 'SIMULATED'");
        assertThat(row.get("payer_simulated_profile_id")).isNull();
        assertThat(row.get("payer_name")).isEqualTo("Pagador");
    }
}
