package com.amano.backend.income;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

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
 * Migration T003/T007 (specs/032-income-management): Flyway applies
 * {@code V38__create_incomes.sql} on a real PostgreSQL, the schema matches
 * data-model.md and the CHECK, UNIQUE and FOREIGN KEY constraints reject invalid
 * rows. Starting the Spring context also runs {@code ddl-auto=validate}, so the
 * JPA mapping of {@link Income} must agree with the migration.
 */
@SpringBootTest(properties = {
        "jwt.secret=2b7e151628aed2a6abf7158809cf4f3c2b7e151628aed2a6abf7158809cf4f3c"
})
@Testcontainers
class IncomeSchemaIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18-alpine");

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private IncomeRepository incomeRepository;

    @Test
    void flywayCreatesTheIncomesTableWithTheExpectedColumns() {
        List<String> columns = jdbcTemplate.queryForList("""
                SELECT column_name FROM information_schema.columns
                WHERE table_schema = 'public' AND table_name = 'incomes'
                ORDER BY ordinal_position
                """, String.class);

        // No updated_at: incomes are immutable once registered.
        assertThat(columns).containsExactly(
                "id", "user_id", "amount", "currency", "income_date", "category",
                "payment_method", "description", "idempotency_key", "created_at");
    }

    @Test
    void incomesCarriesTheKeyConstraintsAndIndex() {
        List<String> constraints = jdbcTemplate.queryForList("""
                SELECT conname FROM pg_constraint
                WHERE conrelid = 'public.incomes'::regclass
                """, String.class);
        assertThat(constraints).contains(
                "fk_incomes_user",
                "ck_incomes_amount",
                "ck_incomes_currency",
                "ck_incomes_category",
                "ck_incomes_payment_method",
                "ck_incomes_description",
                "uq_incomes_user_idempotency_key");

        String index = jdbcTemplate.queryForObject("""
                SELECT indexdef FROM pg_indexes
                WHERE schemaname = 'public' AND tablename = 'incomes'
                  AND indexname = 'ix_incomes_user_date'
                """, String.class);
        assertThat(index).contains("user_id").contains("income_date DESC").contains("id DESC");
    }

    @Test
    void foreignKeyRemovesIncomesWithTheirAccount() {
        String definition = jdbcTemplate.queryForObject("""
                SELECT pg_get_constraintdef(oid) FROM pg_constraint
                WHERE conname = 'fk_incomes_user'
                """, String.class);
        assertThat(definition)
                .contains("FOREIGN KEY (user_id) REFERENCES users(id)")
                .contains("ON DELETE CASCADE");

        long userId = insertUser("cascade");
        insertIncome(userId, "100.00", "ARS", "SALARY", "TRANSFER", null, UUID.randomUUID());
        jdbcTemplate.update("DELETE FROM users WHERE id = ?", userId);

        Integer remaining = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM incomes WHERE user_id = ?", Integer.class, userId);
        assertThat(remaining).isZero();
    }

    @Test
    void rejectsAnAmountBelowOneCent() {
        long userId = insertUser("amount");
        assertThatThrownBy(() -> insertIncome(userId, "0.00", "ARS", "SALARY", "CASH", null,
                UUID.randomUUID())).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertIncome(userId, "-5.00", "ARS", "SALARY", "CASH", null,
                UUID.randomUUID())).isInstanceOf(DataIntegrityViolationException.class);
        insertIncome(userId, "0.01", "ARS", "SALARY", "CASH", null, UUID.randomUUID());
    }

    @Test
    void rejectsACategoryOutsideTheCatalog() {
        long userId = insertUser("category");
        assertThatThrownBy(() -> insertIncome(userId, "10.00", "ARS", "FOOD", "CASH", null,
                UUID.randomUUID())).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void rejectsAPaymentMethodOutsideTheCatalog() {
        long userId = insertUser("payment");
        assertThatThrownBy(() -> insertIncome(userId, "10.00", "ARS", "SALARY", "CREDIT_CARD",
                null, UUID.randomUUID())).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void rejectsACurrencyOutsideTheThirtyTwoSupported() {
        long userId = insertUser("currency");
        assertThatThrownBy(() -> insertIncome(userId, "10.00", "XYZ", "SALARY", "CASH", null,
                UUID.randomUUID())).isInstanceOf(DataIntegrityViolationException.class);
        insertIncome(userId, "10.00", "ZAR", "SALARY", "CASH", null, UUID.randomUUID());
    }

    @Test
    void rejectsADescriptionOfMoreThanTwoHundredCharacters() {
        long userId = insertUser("description");
        insertIncome(userId, "10.00", "ARS", "SALARY", "CASH", "a".repeat(200), UUID.randomUUID());
        assertThatThrownBy(() -> insertIncome(userId, "10.00", "ARS", "SALARY", "CASH",
                "a".repeat(201), UUID.randomUUID()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void rejectsARepeatedIdempotencyKeyOfTheSameUserButNotOfAnotherOne() {
        long first = insertUser("key-a");
        long second = insertUser("key-b");
        UUID key = UUID.randomUUID();
        insertIncome(first, "10.00", "ARS", "SALARY", "CASH", null, key);

        assertThatThrownBy(() -> insertIncome(first, "20.00", "USD", "SALE", "TRANSFER", null, key))
                .isInstanceOf(DataIntegrityViolationException.class);
        insertIncome(second, "10.00", "ARS", "SALARY", "CASH", null, key);
    }

    @Test
    void repositoryFindsIncomesByOwnerAndKeyAndListsTheirYears() {
        long owner = insertUser("repo-owner");
        long other = insertUser("repo-other");
        UUID key = UUID.randomUUID();
        insertIncome(owner, "10.00", "ARS", "SALARY", "CASH", null, key,
                LocalDate.of(2024, 3, 1));
        insertIncome(owner, "20.00", "ARS", "SALE", "CASH", null, UUID.randomUUID(),
                LocalDate.of(2026, 5, 2));
        insertIncome(owner, "30.00", "ARS", "SALE", "CASH", null, UUID.randomUUID(),
                LocalDate.of(2026, 6, 2));
        insertIncome(other, "40.00", "ARS", "SALE", "CASH", null, UUID.randomUUID(),
                LocalDate.of(2021, 6, 2));

        Income found = incomeRepository.findByUserIdAndIdempotencyKey(owner, key).orElseThrow();
        assertThat(found.getAmount()).isEqualByComparingTo(new BigDecimal("10.00"));
        assertThat(found.getCategory()).isEqualTo(IncomeCategory.SALARY);
        assertThat(found.getPaymentMethod()).isEqualTo(IncomePaymentMethod.CASH);
        assertThat(found.getIncomeDate()).isEqualTo(LocalDate.of(2024, 3, 1));
        assertThat(found.getCreatedAt()).isNotNull();
        assertThat(found.getDescription()).isNull();
        assertThat(found.getUserId()).isEqualTo(owner);
        assertThat(found.getIdempotencyKey()).isEqualTo(key);
        assertThat(found.getCurrency().name()).isEqualTo("ARS");

        assertThat(incomeRepository.findByUserIdAndIdempotencyKey(other, key)).isEmpty();
        assertThat(incomeRepository.findByIdAndUserId(found.getId(), owner)).isPresent();
        assertThat(incomeRepository.findByIdAndUserId(found.getId(), other)).isEmpty();
        assertThat(incomeRepository.findIncomeYears(owner)).containsExactly(2026, 2024);
        assertThat(incomeRepository.findIncomeYears(other)).containsExactly(2021);
    }

    private long insertUser(String name) {
        String email = name + "-" + UUID.randomUUID() + "@example.com";
        return jdbcTemplate.queryForObject("""
                INSERT INTO users (first_name, email, password_hash, terms_accepted_at)
                VALUES ('Lucia', ?, 'hash', now()) RETURNING id
                """, Long.class, email);
    }

    private void insertIncome(long userId, String amount, String currency, String category,
            String paymentMethod, String description, UUID key) {
        insertIncome(userId, amount, currency, category, paymentMethod, description, key,
                LocalDate.of(2026, 11, 17));
    }

    private void insertIncome(long userId, String amount, String currency, String category,
            String paymentMethod, String description, UUID key, LocalDate date) {
        jdbcTemplate.update("""
                INSERT INTO incomes (user_id, amount, currency, income_date, category,
                                     payment_method, description, idempotency_key)
                VALUES (?, ?::numeric, ?, ?, ?, ?, ?, ?)
                """, userId, amount, currency, date, category, paymentMethod, description, key);
    }
}
