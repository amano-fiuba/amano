package com.amano.backend.groups.port;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * 017 T025 (US2): the real {@link DebtPort} answers from the {@code debts}
 * table (research.md D7, FR-017/FR-018). A user is blocked by any unsettled
 * debt (PENDING or PARTIALLY_PAID) of THAT group, as debtor or creditor; a
 * simulated profile, as debtor. PAID debts and other groups never count, and
 * users and profiles are never mixed even when they share the numeric id.
 */
@SpringBootTest(properties = {
        "jwt.secret=2b7e151628aed2a6abf7158809cf4f3c2b7e151628aed2a6abf7158809cf4f3c"
})
@Testcontainers
class LedgerDebtPortIT {

    private static final AtomicInteger SEQUENCE = new AtomicInteger();

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18-alpine");

    @Autowired
    private DebtPort debtPort;

    @Autowired
    private JdbcTemplate jdbc;

    private long ana;
    private long juan;
    private long pedro;
    private long groupId;
    private long otherGroupId;
    private long abuela;
    private long expenseId;
    private int position;

    @BeforeEach
    void seed() {
        int n = SEQUENCE.incrementAndGet();
        ana = user("ana" + n);
        juan = user("juan" + n);
        pedro = user("pedro" + n);
        groupId = group();
        otherGroupId = group();
        abuela = jdbc.queryForObject(
                "INSERT INTO simulated_profiles (group_id, name, email) VALUES (?, 'Abuela', ?) RETURNING id",
                Long.class, groupId, "abuela" + n + "@ledger.dev");
        expenseId = expense(groupId);
        position = 0;
    }

    private long user(String name) {
        return jdbc.queryForObject("""
                INSERT INTO users (first_name, email, password_hash, terms_accepted_at)
                VALUES (?, ?, 'x', now()) RETURNING id
                """, Long.class, name, name + "@ledger.dev");
    }

    private long group() {
        return jdbc.queryForObject("INSERT INTO groups (name) VALUES ('Depto') RETURNING id", Long.class);
    }

    private long expense(long group) {
        return jdbc.queryForObject("""
                INSERT INTO group_expenses (group_id, created_by_user_id, payer_user_id, amount, currency,
                    expense_date, idempotency_key)
                VALUES (?, ?, ?, 100.00, 'ARS', CURRENT_DATE, gen_random_uuid()) RETURNING id
                """, Long.class, group, ana, ana);
    }

    /** Debt of a registered {@code debtor} towards {@code creditor} in {@code group}. */
    private void userDebt(long group, long expense, long debtor, long creditor, String paid, String status) {
        long share = jdbc.queryForObject("""
                INSERT INTO group_expense_shares (expense_id, position, participant_type, user_id,
                    participant_name, amount)
                VALUES (?, ?, 'REGISTERED', ?, 'Deudor', 50.00) RETURNING id
                """, Long.class, expense, position++, debtor);
        jdbc.update("""
                INSERT INTO debts (group_id, expense_id, share_id, debtor_type, debtor_user_id, debtor_name,
                    creditor_user_id, currency, amount, paid_amount, status)
                VALUES (?, ?, ?, 'REGISTERED', ?, 'Deudor', ?, 'ARS', 50.00, ?::numeric, ?)
                """, group, expense, share, debtor, creditor, paid, status);
    }

    private void profileDebt(long profile, String paid, String status) {
        long share = jdbc.queryForObject("""
                INSERT INTO group_expense_shares (expense_id, position, participant_type, simulated_profile_id,
                    participant_name, amount)
                VALUES (?, ?, 'SIMULATED', ?, 'Abuela', 50.00) RETURNING id
                """, Long.class, expenseId, position++, profile);
        jdbc.update("""
                INSERT INTO debts (group_id, expense_id, share_id, debtor_type, debtor_simulated_profile_id,
                    debtor_name, creditor_user_id, currency, amount, paid_amount, status)
                VALUES (?, ?, ?, 'SIMULATED', ?, 'Abuela', ?, 'ARS', 50.00, ?::numeric, ?)
                """, groupId, expenseId, share, profile, ana, paid, status);
    }

    @Test
    void theRealPortReplacesTheStub() {
        assertThat(debtPort).isInstanceOf(LedgerDebtPort.class);
    }

    @Test
    void nobodyHasDebtsInAGroupWithoutExpenses() {
        assertThat(debtPort.hasPendingDebtsForUser(groupId, juan)).isFalse();
        assertThat(debtPort.hasPendingDebtsForUser(groupId, ana)).isFalse();
        assertThat(debtPort.hasPendingDebtsForSimulatedProfile(groupId, abuela)).isFalse();
    }

    @Test
    void aPendingDebtBlocksTheDebtorAndTheCreditor() {
        userDebt(groupId, expenseId, juan, ana, "0", "PENDING");

        assertThat(debtPort.hasPendingDebtsForUser(groupId, juan)).isTrue();
        assertThat(debtPort.hasPendingDebtsForUser(groupId, ana)).isTrue();
        assertThat(debtPort.hasPendingDebtsForUser(groupId, pedro)).isFalse();
    }

    @Test
    void aPartiallyPaidDebtStillBlocksBothParties() {
        userDebt(groupId, expenseId, juan, ana, "20.00", "PARTIALLY_PAID");

        assertThat(debtPort.hasPendingDebtsForUser(groupId, juan)).isTrue();
        assertThat(debtPort.hasPendingDebtsForUser(groupId, ana)).isTrue();
    }

    @Test
    void aPaidDebtDoesNotBlock() {
        userDebt(groupId, expenseId, juan, ana, "50.00", "PAID");
        profileDebt(abuela, "50.00", "PAID");

        assertThat(debtPort.hasPendingDebtsForUser(groupId, juan)).isFalse();
        assertThat(debtPort.hasPendingDebtsForUser(groupId, ana)).isFalse();
        assertThat(debtPort.hasPendingDebtsForSimulatedProfile(groupId, abuela)).isFalse();
    }

    @Test
    void oneOpenDebtAmongPaidOnesStillBlocks() {
        userDebt(groupId, expenseId, juan, ana, "50.00", "PAID");
        userDebt(groupId, expense(groupId), juan, ana, "0", "PENDING");

        assertThat(debtPort.hasPendingDebtsForUser(groupId, juan)).isTrue();
    }

    @Test
    void debtsOfAnotherGroupDoNotCount() {
        userDebt(otherGroupId, expense(otherGroupId), juan, ana, "0", "PENDING");

        assertThat(debtPort.hasPendingDebtsForUser(groupId, juan)).isFalse();
        assertThat(debtPort.hasPendingDebtsForUser(groupId, ana)).isFalse();
        assertThat(debtPort.hasPendingDebtsForUser(otherGroupId, juan)).isTrue();
    }

    @Test
    void aSimulatedProfileIsBlockedByItsOwnOpenDebts() {
        profileDebt(abuela, "0", "PENDING");

        assertThat(debtPort.hasPendingDebtsForSimulatedProfile(groupId, abuela)).isTrue();
        assertThat(debtPort.hasPendingDebtsForSimulatedProfile(otherGroupId, abuela)).isFalse();
    }

    /** Next simulated profile of the group gets exactly {@code id} (test-only sequence move). */
    private long profileWithId(long id, String name) {
        Long maxProfile = jdbc.queryForObject("SELECT COALESCE(MAX(id), 0) FROM simulated_profiles", Long.class);
        assumeTrue(id > maxProfile, "the target id must be free in simulated_profiles");
        jdbc.queryForObject("SELECT setval('simulated_profiles_id_seq', ?)", Long.class, id - 1);
        long created = jdbc.queryForObject(
                "INSERT INTO simulated_profiles (group_id, name, email) VALUES (?, ?, ?) RETURNING id",
                Long.class, groupId, name, name + id + "@ledger.dev");
        assertThat(created).isEqualTo(id);
        return created;
    }

    @Test
    void aProfileIsNotBlockedByTheDebtsOfAUserWithTheSameId() {
        long tio = profileWithId(juan, "Tio");
        userDebt(groupId, expenseId, juan, ana, "0", "PENDING");

        assertThat(debtPort.hasPendingDebtsForUser(groupId, juan)).isTrue();
        assertThat(debtPort.hasPendingDebtsForSimulatedProfile(groupId, tio)).isFalse();
    }

    @Test
    void aUserIsNotBlockedByTheDebtsOfAProfileWithTheSameId() {
        long prima = profileWithId(pedro, "Prima");
        profileDebt(prima, "0", "PENDING");

        assertThat(debtPort.hasPendingDebtsForSimulatedProfile(groupId, prima)).isTrue();
        assertThat(debtPort.hasPendingDebtsForUser(groupId, pedro)).isFalse();
    }
}
