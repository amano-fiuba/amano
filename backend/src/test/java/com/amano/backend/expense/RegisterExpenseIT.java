package com.amano.backend.expense;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import com.amano.backend.config.JwtService;

import jakarta.servlet.http.Cookie;
import tools.jackson.databind.ObjectMapper;

/**
 * Feature 007 US1 contract tests (T023): POST /api/expenses over a real
 * PostgreSQL. The owner always comes from the session cookie, amounts are read
 * back exactly from NUMERIC(11,2), and invalid catalogs never persist a row.
 */
@SpringBootTest(properties = {
        "jwt.secret=2b7e151628aed2a6abf7158809cf4f3c2b7e151628aed2a6abf7158809cf4f3c"
})
@AutoConfigureMockMvc
@Testcontainers
class RegisterExpenseIT {

    private static final String FIELDS = "fields";
    private static final List<String> SC004_AMOUNTS = List.of(
            "0.01", "0.10", "0.99", "1.00", "1.10", "10.10", "100.01", "1234.56",
            "999999.99", "123456789.01", "999999999.98", "999999999.99");
    private static final List<String> CURRENCIES = List.of("ARS", "USD", "EUR");

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18-alpine");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private ObjectMapper objectMapper;

    private long userIdOf(String normalizedEmail) {
        return jdbcTemplate.queryForObject("SELECT id FROM users WHERE email = ?", Long.class,
                normalizedEmail);
    }

    private Cookie sessionOf(String normalizedEmail) {
        return new Cookie("amano_auth", jwtService.issueToken(userIdOf(normalizedEmail)));
    }

    /** Registration of feature 003 → account is verified directly in the DB. */
    private Cookie verifiedSessionOf(String normalizedEmail) {
        jdbcTemplate.update(
                "UPDATE users SET email_verified_at = CURRENT_TIMESTAMP WHERE email = ?",
                normalizedEmail);
        return sessionOf(normalizedEmail);
    }

    private void registerAccount(String rawEmail, String password, String firstName)
            throws Exception {
        var firstNamePart = new org.springframework.mock.web.MockPart(
                "firstName", firstName.getBytes(StandardCharsets.UTF_8));
        var emailPart = new org.springframework.mock.web.MockPart(
                "email", rawEmail.getBytes(StandardCharsets.UTF_8));
        var passwordPart = new org.springframework.mock.web.MockPart(
                "password", password.getBytes(StandardCharsets.UTF_8));
        var confirmPart = new org.springframework.mock.web.MockPart(
                "confirmPassword", password.getBytes(StandardCharsets.UTF_8));
        var termsPart = new org.springframework.mock.web.MockPart(
                "acceptTerms", "true".getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(multipart("/api/auth/register")
                        .part(firstNamePart, emailPart, passwordPart, confirmPart, termsPart))
                .andExpect(status().isCreated());
    }

    private static Map<String, String> validPayload(String date) {
        return Map.of(
                "amount", "10.10",
                "currency", "ARS",
                "date", date,
                "category", "FOOD",
                "type", "ONE_TIME",
                "paymentMethod", "CASH",
                "description", "Almuerzo",
                "deviceTimeZone", "UTC");
    }

    private org.springframework.test.web.servlet.ResultActions createExpense(
            Cookie session, UUID key, Map<String, String> payload) throws Exception {
        return mockMvc.perform(post("/api/expenses")
                .cookie(session)
                .header("Idempotency-Key", key.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(payload)));
    }

    private int rowsWithKey(String email, UUID key) {
        return jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM expenses e
                JOIN users u ON u.id = e.user_id
                WHERE u.email = ? AND e.idempotency_key = ?
                """, Integer.class, email, key);
    }

    @Test
    void createsTheExpenseLinkedToTheSessionUserWithTheContractRepresentation()
            throws Exception {
        String email = "gasto.alta@example.com";
        registerAccount(email, "S3gura!2026", "Ana");
        Cookie session = verifiedSessionOf(email);
        UUID key = UUID.randomUUID();
        String today = LocalDate.now(ZoneId.of("America/Argentina/Buenos_Aires")).toString();

        String response = createExpense(session, key, validPayload(today))
                .andExpect(status().isCreated())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.amount").value("10.10"))
                .andExpect(jsonPath("$.currency").value("ARS"))
                .andExpect(jsonPath("$.date").value(today))
                .andExpect(jsonPath("$.category").value("FOOD"))
                .andExpect(jsonPath("$.type").value("ONE_TIME"))
                .andExpect(jsonPath("$.paymentMethod").value("CASH"))
                .andExpect(jsonPath("$.description").value("Almuerzo"))
                .andExpect(jsonPath("$.createdAt").exists())
                .andReturn().getResponse().getContentAsString();

        long expenseId = objectMapper.readTree(response).get("id").asLong();
        Map<String, Object> row = jdbcTemplate.queryForMap("""
                SELECT user_id, amount::text AS amount, currency, expense_date::text AS date,
                       category, expense_type, payment_method, description, idempotency_key::text AS key
                FROM expenses WHERE id = ?
                """, expenseId);
        assertThat(row.get("user_id")).isEqualTo(userIdOf(email));
        assertThat(row.get("amount")).isEqualTo("10.10");
        assertThat(row.get("currency")).isEqualTo("ARS");
        assertThat(row.get("date")).isEqualTo(today);
        assertThat(row.get("category")).isEqualTo("FOOD");
        assertThat(row.get("expense_type")).isEqualTo("ONE_TIME");
        assertThat(row.get("payment_method")).isEqualTo("CASH");
        assertThat(row.get("description")).isEqualTo("Almuerzo");
        assertThat(row.get("key")).isEqualTo(key.toString());
    }

    @Test
    void absentDescriptionIsOmittedAndPersistedAsNull() throws Exception {
        String email = "gasto.sin.descripcion@example.com";
        registerAccount(email, "S3gura!2026", "Ana");
        Cookie session = verifiedSessionOf(email);
        UUID key = UUID.randomUUID();
        Map<String, String> payload = Map.of(
                "amount", "0.01",
                "currency", "USD",
                "date", LocalDate.now(ZoneOffset.UTC).toString(),
                "category", "OTHER",
                "type", "RECURRING",
                "paymentMethod", "OTHER",
                "deviceTimeZone", "UTC");

        createExpense(session, key, payload)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.description").doesNotExist());

        String stored = jdbcTemplate.queryForObject(
                "SELECT description FROM expenses WHERE idempotency_key = ?", String.class, key);
        assertThat(stored).isNull();
    }

    @Test
    void sc004AmountsAreReadExactlyInEveryCurrency() throws Exception {
        String email = "gasto.sc004@example.com";
        registerAccount(email, "S3gura!2026", "Ana");
        Cookie session = verifiedSessionOf(email);
        long userId = userIdOf(email);
        String today = LocalDate.now(ZoneOffset.UTC).toString();

        for (String amount : SC004_AMOUNTS) {
            for (String currency : CURRENCIES) {
                UUID key = UUID.randomUUID();
                Map<String, String> payload = Map.of(
                        "amount", amount,
                        "currency", currency,
                        "date", today,
                        "category", "FOOD",
                        "type", "ONE_TIME",
                        "paymentMethod", "CASH",
                        "deviceTimeZone", "UTC");

                createExpense(session, key, payload)
                        .andExpect(status().isCreated())
                        .andExpect(jsonPath("$.amount").value(amount))
                        .andExpect(jsonPath("$.currency").value(currency));

                String stored = jdbcTemplate.queryForObject("""
                        SELECT amount::text FROM expenses
                        WHERE user_id = ? AND idempotency_key = ?
                        """, String.class, userId, key);
                assertThat(stored).as("%s %s", amount, currency).isEqualTo(amount);
            }
        }
    }

    @Test
    void replayOfTheSameOperationAnswers200WithTheSameExpense() throws Exception {
        String email = "gasto.replay@example.com";
        registerAccount(email, "S3gura!2026", "Ana");
        Cookie session = verifiedSessionOf(email);
        UUID key = UUID.randomUUID();
        Map<String, String> payload = validPayload(LocalDate.now(ZoneOffset.UTC).toString());

        String first = createExpense(session, key, payload)
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String replay = createExpense(session, key, payload)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").isNumber())
                .andReturn().getResponse().getContentAsString();

        assertThat(objectMapper.readTree(replay).get("id").asLong())
                .isEqualTo(objectMapper.readTree(first).get("id").asLong());
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM expenses WHERE idempotency_key = ?", Integer.class, key))
                .isEqualTo(1);
    }

    @Test
    void requestWithoutSessionIsUnauthorizedAndDoesNotPersist() throws Exception {
        UUID key = UUID.randomUUID();
        Map<String, String> payload = validPayload(LocalDate.now(ZoneOffset.UTC).toString());

        mockMvc.perform(post("/api/expenses")
                        .header("Idempotency-Key", key.toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Necesitas iniciar sesión para continuar"));

        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM expenses WHERE idempotency_key = ?", Integer.class, key))
                .isZero();
    }

    @Test
    void pendingAccountIsUnauthorizedAndDoesNotPersist() throws Exception {
        String email = "gasto.pendiente@example.com";
        registerAccount(email, "S3gura!2026", "Ana");
        UUID key = UUID.randomUUID();
        Map<String, String> payload = validPayload(LocalDate.now(ZoneOffset.UTC).toString());

        createExpense(sessionOf(email), key, payload)
                .andExpect(status().isUnauthorized());

        assertThat(rowsWithKey(email, key)).isZero();
    }

    @Test
    void advancedDeviceTimeZoneAcceptsItsOwnToday() throws Exception {
        String email = "gasto.zona.adelantada@example.com";
        registerAccount(email, "S3gura!2026", "Ana");
        Cookie session = verifiedSessionOf(email);
        String zone = "Pacific/Kiritimati";
        String todayInZone = LocalDate.now(ZoneId.of(zone)).toString();
        Map<String, String> payload = Map.of(
                "amount", "10.10",
                "currency", "ARS",
                "date", todayInZone,
                "category", "FOOD",
                "type", "ONE_TIME",
                "paymentMethod", "CASH",
                "deviceTimeZone", zone);

        createExpense(session, UUID.randomUUID(), payload)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.date").value(todayInZone));
    }

    @Test
    void missingDeviceTimeZoneAppliesTheOneDayTolerance() throws Exception {
        String email = "gasto.sin.zona@example.com";
        registerAccount(email, "S3gura!2026", "Ana");
        Cookie session = verifiedSessionOf(email);
        Map<String, String> payload = Map.of(
                "amount", "10.10",
                "currency", "ARS",
                "date", LocalDate.now(ZoneOffset.UTC).plusDays(1).toString(),
                "category", "FOOD",
                "type", "ONE_TIME",
                "paymentMethod", "CASH");

        createExpense(session, UUID.randomUUID(), payload)
                .andExpect(status().isCreated());
    }

    @Test
    void manipulatedCatalogValuesAreRejectedWithoutPersisting() throws Exception {
        String email = "gasto.catalogo@example.com";
        registerAccount(email, "S3gura!2026", "Ana");
        Cookie session = verifiedSessionOf(email);
        UUID key = UUID.randomUUID();
        Map<String, String> payload = Map.of(
                "amount", "10.10",
                "currency", "GBP",
                "date", LocalDate.now(ZoneOffset.UTC).toString(),
                "category", "PARTY",
                "type", "WEEKLY",
                "paymentMethod", "BITCOIN",
                "deviceTimeZone", "UTC");

        createExpense(session, key, payload)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.properties." + FIELDS + ".currency").exists())
                .andExpect(jsonPath("$.properties." + FIELDS + ".category").exists())
                .andExpect(jsonPath("$.properties." + FIELDS + ".type").exists())
                .andExpect(jsonPath("$.properties." + FIELDS + ".paymentMethod").exists());

        assertThat(rowsWithKey(email, key)).isZero();
    }

    @Test
    void missingOrInvalidIdempotencyKeyIsABadRequest() throws Exception {
        String email = "gasto.clave@example.com";
        registerAccount(email, "S3gura!2026", "Ana");
        Cookie session = verifiedSessionOf(email);
        Map<String, String> payload = validPayload(LocalDate.now(ZoneOffset.UTC).toString());

        mockMvc.perform(post("/api/expenses")
                        .cookie(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(
                        "Falta la cabecera Idempotency-Key en la solicitud"));

        mockMvc.perform(post("/api/expenses")
                        .cookie(session)
                        .header("Idempotency-Key", "not-a-uuid")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.properties." + FIELDS + ".idempotencyKey").exists());
    }
}
