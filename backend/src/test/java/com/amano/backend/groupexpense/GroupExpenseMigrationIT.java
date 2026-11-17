package com.amano.backend.groupexpense;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import com.amano.backend.support.AbstractIntegrationIT;

/**
 * 017 T005: Flyway applies {@code V13__create_group_expenses_and_debts.sql} on a
 * real PostgreSQL and the constraints of data-model.md hold even for rows
 * written by hand. Starting the context also runs {@code ddl-auto=validate},
 * so the JPA mappings must agree with the migration.
 */
class GroupExpenseMigrationIT extends AbstractIntegrationIT {

    @Autowired
    private JdbcTemplate jdbc;

    private long payerId;
    private long debtorId;
    private long groupId;
    private long profileId;
    private long expenseId;

    @BeforeEach
    void seed() {
        jdbc.update("DELETE FROM debts");
        jdbc.update("DELETE FROM group_expense_shares");
        jdbc.update("DELETE FROM group_expenses");
        payerId = user("ana");
        debtorId = user("juan");
        groupId = jdbc.queryForObject("INSERT INTO groups (name) VALUES ('Depto') RETURNING id", Long.class);
        profileId = jdbc.queryForObject(
                "INSERT INTO simulated_profiles (group_id, name, email) VALUES (?, 'Abuela', ?) RETURNING id",
                Long.class, groupId, "abuela-" + System.nanoTime() + "@amano.dev");
        expenseId = jdbc.queryForObject("""
                INSERT INTO group_expenses (group_id, created_by_user_id, payer_user_id, amount, currency,
                    expense_date, description, idempotency_key)
                VALUES (?, ?, ?, 100.00, 'ARS', CURRENT_DATE, 'Súper', gen_random_uuid()) RETURNING id
                """, Long.class, groupId, payerId, payerId);
    }

    private long user(String name) {
        return jdbc.queryForObject("""
                INSERT INTO users (first_name, email, password_hash, terms_accepted_at)
                VALUES (?, ?, 'x', now()) RETURNING id
                """, Long.class, name, name + "-" + System.nanoTime() + "@amano.dev");
    }

    private long registeredShare(int position, long userId, String amount) {
        return jdbc.queryForObject("""
                INSERT INTO group_expense_shares (expense_id, position, participant_type, user_id,
                    participant_name, amount)
                VALUES (?, ?, 'REGISTERED', ?, 'Nombre', ?::numeric) RETURNING id
                """, Long.class, expenseId, position, userId, amount);
    }

    private long simulatedShare(int position, String amount) {
        return jdbc.queryForObject("""
                INSERT INTO group_expense_shares (expense_id, position, participant_type,
                    simulated_profile_id, participant_name, amount)
                VALUES (?, ?, 'SIMULATED', ?, 'Abuela', ?::numeric) RETURNING id
                """, Long.class, expenseId, position, profileId, amount);
    }

    private void debt(long shareId, String paid, String status) {
        jdbc.update("""
                INSERT INTO debts (group_id, expense_id, share_id, debtor_type, debtor_user_id,
                    debtor_name, creditor_user_id, currency, amount, paid_amount, status)
                VALUES (?, ?, ?, 'REGISTERED', ?, 'Juan', ?, 'ARS', 50.00, ?::numeric, ?)
                """, groupId, expenseId, shareId, debtorId, payerId, paid, status);
    }

    @Test
    void theThreeTablesExist() {
        List<String> tables = jdbc.queryForList("""
                SELECT table_name FROM information_schema.tables
                WHERE table_schema = 'public'
                  AND table_name IN ('group_expenses', 'group_expense_shares', 'debts')
                ORDER BY table_name
                """, String.class);

        assertThat(tables).containsExactly("debts", "group_expense_shares", "group_expenses");
    }

    @Test
    void aNewDebtStartsPendingWithNothingPaid() {
        long share = registeredShare(1, debtorId, "50.00");
        jdbc.update("""
                INSERT INTO debts (group_id, expense_id, share_id, debtor_type, debtor_user_id,
                    debtor_name, creditor_user_id, currency, amount)
                VALUES (?, ?, ?, 'REGISTERED', ?, 'Juan', ?, 'ARS', 50.00)
                """, groupId, expenseId, share, debtorId, payerId);

        var row = jdbc.queryForMap("SELECT status, paid_amount FROM debts WHERE share_id = ?", share);

        assertThat(row.get("status")).isEqualTo("PENDING");
        assertThat(row.get("paid_amount").toString()).isEqualTo("0.00");
    }

    @Test
    void statusConsistentWithPaidAmountIsAccepted() {
        debt(registeredShare(1, debtorId, "50.00"), "0", "PENDING");
        debt(registeredShare(2, user("pedro"), "50.00"), "20.00", "PARTIALLY_PAID");
        debt(registeredShare(3, user("lucia"), "50.00"), "50.00", "PAID");

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM debts", Integer.class)).isEqualTo(3);
    }

    @Test
    void statusThatContradictsThePaidAmountIsRejected() {
        long share = registeredShare(1, debtorId, "50.00");

        assertThatThrownBy(() -> debt(share, "0", "PAID")).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> debt(share, "50.00", "PENDING")).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> debt(share, "50.00", "PARTIALLY_PAID"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> debt(share, "10.00", "PENDING")).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void paidAmountCannotExceedTheDebtNorBeNegative() {
        long share = registeredShare(1, debtorId, "50.00");

        assertThatThrownBy(() -> debt(share, "60.00", "PAID")).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> debt(share, "-1.00", "PENDING")).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void unknownStatusIsRejected() {
        long share = registeredShare(1, debtorId, "50.00");

        assertThatThrownBy(() -> debt(share, "0", "CANCELLED")).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void aRegisteredShareNeedsAUserAndNoProfile() {
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO group_expense_shares (expense_id, position, participant_type,
                    participant_name, amount)
                VALUES (?, 1, 'REGISTERED', 'Nadie', 10.00)
                """, expenseId)).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO group_expense_shares (expense_id, position, participant_type, user_id,
                    simulated_profile_id, participant_name, amount)
                VALUES (?, 1, 'REGISTERED', ?, ?, 'Mezcla', 10.00)
                """, expenseId, debtorId, profileId)).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void aSimulatedShareCannotPointToAUser() {
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO group_expense_shares (expense_id, position, participant_type, user_id,
                    participant_name, amount)
                VALUES (?, 1, 'SIMULATED', ?, 'Mezcla', 10.00)
                """, expenseId, debtorId)).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void theSameParticipantCannotAppearTwiceInAnExpense() {
        registeredShare(1, debtorId, "50.00");
        simulatedShare(2, "25.00");

        assertThatThrownBy(() -> registeredShare(3, debtorId, "25.00"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> simulatedShare(4, "25.00")).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void thePayerCannotOweHimself() {
        long share = registeredShare(1, payerId, "50.00");

        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO debts (group_id, expense_id, share_id, debtor_type, debtor_user_id,
                    debtor_name, creditor_user_id, currency, amount)
                VALUES (?, ?, ?, 'REGISTERED', ?, 'Ana', ?, 'ARS', 50.00)
                """, groupId, expenseId, share, payerId, payerId))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void oneDebtPerShare() {
        long share = registeredShare(1, debtorId, "50.00");
        debt(share, "0", "PENDING");

        assertThatThrownBy(() -> debt(share, "0", "PENDING")).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void deletingASimulatedProfileKeepsItsShareAndDebtWithTheCopiedName() {
        long share = simulatedShare(1, "50.00");
        jdbc.update("""
                INSERT INTO debts (group_id, expense_id, share_id, debtor_type,
                    debtor_simulated_profile_id, debtor_name, creditor_user_id, currency, amount,
                    paid_amount, status)
                VALUES (?, ?, ?, 'SIMULATED', ?, 'Abuela', ?, 'ARS', 50.00, 50.00, 'PAID')
                """, groupId, expenseId, share, profileId, payerId);

        jdbc.update("DELETE FROM simulated_profiles WHERE id = ?", profileId);

        var shareRow = jdbc.queryForMap(
                "SELECT simulated_profile_id, participant_name FROM group_expense_shares WHERE id = ?", share);
        var debtRow = jdbc.queryForMap(
                "SELECT debtor_simulated_profile_id, debtor_name FROM debts WHERE share_id = ?", share);
        assertThat(shareRow.get("simulated_profile_id")).isNull();
        assertThat(shareRow.get("participant_name")).isEqualTo("Abuela");
        assertThat(debtRow.get("debtor_simulated_profile_id")).isNull();
        assertThat(debtRow.get("debtor_name")).isEqualTo("Abuela");
    }

    @Test
    void theIdempotencyKeyIsUniquePerCreator() {
        jdbc.update("""
                INSERT INTO group_expenses (group_id, created_by_user_id, payer_user_id, amount, currency,
                    expense_date, idempotency_key)
                VALUES (?, ?, ?, 10.00, 'ARS', CURRENT_DATE, '11111111-1111-1111-1111-111111111111')
                """, groupId, payerId, payerId);

        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO group_expenses (group_id, created_by_user_id, payer_user_id, amount, currency,
                    expense_date, idempotency_key)
                VALUES (?, ?, ?, 20.00, 'ARS', CURRENT_DATE, '11111111-1111-1111-1111-111111111111')
                """, groupId, payerId, payerId)).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void theOpenDebtIndexesArePartial() {
        List<String> definitions = jdbc.queryForList("""
                SELECT indexdef FROM pg_indexes
                WHERE schemaname = 'public' AND tablename = 'debts' AND indexname LIKE 'ix_debts_open_%'
                """, String.class);

        assertThat(definitions).hasSize(3).allSatisfy(definition -> assertThat(definition).contains("WHERE"));
    }

    @Test
    void theSplitMethodDefaultsToEqualAndOnlyAcceptsTheThreeKnownValues() {
        assertThat(jdbc.queryForObject("SELECT split_method FROM group_expenses WHERE id = ?",
                String.class, expenseId)).isEqualTo("EQUAL");

        jdbc.update("UPDATE group_expenses SET split_method = 'CUSTOM' WHERE id = ?", expenseId);
        jdbc.update("UPDATE group_expenses SET split_method = 'PERCENTAGE' WHERE id = ?", expenseId);
        assertThatThrownBy(() -> jdbc.update(
                "UPDATE group_expenses SET split_method = 'HALF' WHERE id = ?", expenseId))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void v23DefaultMembersCanGenerateExpensesForOthersIsTrue() {
        String colDefault = jdbc.queryForObject("""
                SELECT column_default FROM information_schema.columns
                WHERE table_schema = 'public' AND table_name = 'group_permissions'
                  AND column_name = 'members_can_generate_expenses_for_others'
                """, String.class);
        assertThat(colDefault).containsIgnoringCase("true");
    }
}
