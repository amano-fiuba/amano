package com.amano.backend.groups.port;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import com.amano.backend.support.AbstractIntegrationIT;

/**
 * 017 T025 (US2): the real {@link DebtPort} answers from the {@code debts}
 * table (research.md D7, FR-017/FR-018). A user is blocked by any unsettled
 * debt (PENDING, PARTIALLY_PAID, PAYMENT_REPORTED or OVERDUE) of THAT group, as debtor or creditor; a
 * simulated profile, as debtor. PAID debts and other groups never count, and
 * users and profiles are never mixed even when they share the numeric id.
 */
class LedgerDebtPortIT extends AbstractIntegrationIT {

    private static final AtomicInteger SEQUENCE = new AtomicInteger();

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

    /** 022 T059: the four unsettled states block the debtor and the creditor alike. */
    @ParameterizedTest
    @ValueSource(strings = { "PENDING", "PARTIALLY_PAID", "PAYMENT_REPORTED", "OVERDUE" })
    void t059EveryUnsettledStateBlocksTheDebtorAndTheCreditor(String status) {
        userDebt(groupId, expenseId, juan, ana, "PARTIALLY_PAID".equals(status) ? "20.00" : "0", status);

        assertThat(debtPort.hasPendingDebtsForUser(groupId, juan)).as("debtor in %s", status).isTrue();
        assertThat(debtPort.hasPendingDebtsForUser(groupId, ana)).as("creditor in %s", status).isTrue();
        assertThat(debtPort.hasPendingDebtsForUser(groupId, pedro)).as("uninvolved in %s", status).isFalse();
    }

    /** 022 T059: a reported or overdue debt of a simulated profile blocks its removal too. */
    @ParameterizedTest
    @ValueSource(strings = { "PAYMENT_REPORTED", "OVERDUE" })
    void t059AReportedOrOverdueDebtOfASimulatedProfileBlocksItsRemoval(String status) {
        profileDebt(abuela, "0", status);

        assertThat(debtPort.hasPendingDebtsForSimulatedProfile(groupId, abuela)).isTrue();
    }

    /** 022 T059: PAID and VERIFIED are settled and never block either side. */
    @ParameterizedTest
    @ValueSource(strings = { "PAID", "VERIFIED" })
    void t059SettledStatesDoNotBlockAnyone(String status) {
        userDebt(groupId, expenseId, juan, ana, "50.00", status);
        profileDebt(abuela, "50.00", status);

        assertThat(debtPort.hasPendingDebtsForUser(groupId, juan)).as("debtor in %s", status).isFalse();
        assertThat(debtPort.hasPendingDebtsForUser(groupId, ana)).as("creditor in %s", status).isFalse();
        assertThat(debtPort.hasPendingDebtsForSimulatedProfile(groupId, abuela)).isFalse();
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

    /** Next simulated profile of the group gets exactly {@code id} (deterministic, no assumption). */
    private long profileWithId(long id, String name) {
        Long exists = jdbc.queryForObject(
                "SELECT COUNT(*) FROM simulated_profiles WHERE id = ?", Long.class, id);
        if (exists == 0) {
            jdbc.update("""
                    INSERT INTO simulated_profiles (id, group_id, name, email)
                    VALUES (?, ?, ?, ?)
                    """, id, groupId, name, name + id + "@ledger.dev");
        }
        // Keep the sequence ahead of every explicit id so later default inserts
        // (including the next method's @BeforeEach) cannot collide.
        jdbc.queryForObject("""
                SELECT setval('simulated_profiles_id_seq',
                    GREATEST((SELECT COALESCE(MAX(id), 1) FROM simulated_profiles), 1))
                """, Long.class);
        return id;
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
