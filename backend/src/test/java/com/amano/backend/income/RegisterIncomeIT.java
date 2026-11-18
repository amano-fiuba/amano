package com.amano.backend.income;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.amano.backend.config.JwtService;

import jakarta.servlet.http.Cookie;
import tools.jackson.databind.ObjectMapper;
import com.amano.backend.support.AbstractIntegrationIT;

/**
 * Contract tests of {@code POST /api/incomes} over a real PostgreSQL
 * (specs/033-income-management, US1): the owner comes from the session cookie,
 * the idempotency key makes a retry safe, and every invalid request is rejected
 * without persisting a row.
 */
class RegisterIncomeIT extends AbstractIntegrationIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private ObjectMapper objectMapper;

    private long newUser(String name, boolean verified) {
        return jdbcTemplate.queryForObject("""
                INSERT INTO users (first_name, email, password_hash, terms_accepted_at, email_verified_at)
                VALUES (?, ?, 'x', now(), CASE WHEN ? THEN now() END) RETURNING id
                """, Long.class, name, name + "-" + System.nanoTime() + "@amano.dev", verified);
    }

    private Cookie sessionOf(long userId) {
        return new Cookie("amano_auth", jwtService.issueToken(userId));
    }

    private static Map<String, String> payload() {
        Map<String, String> payload = new HashMap<>();
        payload.put("amount", "850000.00");
        payload.put("currency", "ARS");
        payload.put("date", LocalDate.now(ZoneOffset.UTC).toString());
        payload.put("category", "SALARY");
        payload.put("paymentMethod", "TRANSFER");
        payload.put("description", "Sueldo");
        payload.put("deviceTimeZone", "UTC");
        return payload;
    }

    private ResultActions create(Cookie session, UUID key, Map<String, ?> body) throws Exception {
        var request = post("/api/incomes")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body));
        if (session != null) {
            request.cookie(session);
        }
        if (key != null) {
            request.header("Idempotency-Key", key.toString());
        }
        return mockMvc.perform(request);
    }

    private int incomesOf(long userId) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM incomes WHERE user_id = ?", Integer.class, userId);
    }

    @Test
    void createsTheIncomeLinkedToTheSessionUserWithTheContractRepresentation() throws Exception {
        long userId = newUser("alta", true);
        UUID key = UUID.randomUUID();

        String response = create(sessionOf(userId), key, payload())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.amount").value("850000.00"))
                .andExpect(jsonPath("$.currency").value("ARS"))
                .andExpect(jsonPath("$.category").value("SALARY"))
                .andExpect(jsonPath("$.paymentMethod").value("TRANSFER"))
                .andExpect(jsonPath("$.description").value("Sueldo"))
                .andExpect(jsonPath("$.projected").value(false))
                .andExpect(jsonPath("$.createdAt").exists())
                .andExpect(jsonPath("$.conversions").doesNotExist())
                .andReturn().getResponse().getContentAsString();

        long id = objectMapper.readTree(response).get("id").asLong();
        Map<String, Object> row = jdbcTemplate.queryForMap("""
                SELECT user_id, amount::text AS amount, currency, category, payment_method,
                       description, idempotency_key::text AS key FROM incomes WHERE id = ?
                """, id);
        assertThat(row.get("user_id")).isEqualTo(userId);
        assertThat(row.get("amount")).isEqualTo("850000.00");
        assertThat(row.get("key")).isEqualTo(key.toString());
    }

    @Test
    void anIncomeWithoutDescriptionIsRegistered() throws Exception {
        long userId = newUser("sindesc", true);
        Map<String, String> body = payload();
        body.remove("description");
        body.put("category", "SALE");

        create(sessionOf(userId), UUID.randomUUID(), body)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.description").doesNotExist());
    }

    @Test
    void aFutureDateWithinOneYearIsAcceptedAndShownAsProjected() throws Exception {
        long userId = newUser("futuro", true);
        Map<String, String> body = payload();
        body.put("date", LocalDate.now(ZoneOffset.UTC).plusMonths(2).toString());

        create(sessionOf(userId), UUID.randomUUID(), body)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.projected").value(true));
    }

    @Test
    void aDateMoreThanOneYearAheadIsRejected() throws Exception {
        long userId = newUser("lejos", true);
        Map<String, String> body = payload();
        body.put("date", LocalDate.now(ZoneOffset.UTC).plusYears(1).plusDays(2).toString());

        create(sessionOf(userId), UUID.randomUUID(), body)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.properties.fields.date").exists());
        assertThat(incomesOf(userId)).isZero();
    }

    @Test
    void theSameKeyWithTheSamePayloadReturnsTheSameIncomeWithoutDuplicating() throws Exception {
        long userId = newUser("replay", true);
        UUID key = UUID.randomUUID();

        String first = create(sessionOf(userId), key, payload())
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String second = create(sessionOf(userId), key, payload())
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();

        assertThat(objectMapper.readTree(second).get("id"))
                .isEqualTo(objectMapper.readTree(first).get("id"));
        assertThat(incomesOf(userId)).isEqualTo(1);
    }

    @Test
    void theSameKeyWithAnotherPayloadIsRejectedAs422() throws Exception {
        long userId = newUser("reuso", true);
        UUID key = UUID.randomUUID();
        create(sessionOf(userId), key, payload()).andExpect(status().isCreated());

        Map<String, String> other = payload();
        other.put("amount", "1.00");
        create(sessionOf(userId), key, other)
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.properties.code").value("IDEMPOTENCY_KEY_REUSED"));
        assertThat(incomesOf(userId)).isEqualTo(1);
    }

    @Test
    void theSameKeyOfAnotherUserIsAnIndependentOperation() throws Exception {
        long first = newUser("dueno-a", true);
        long second = newUser("dueno-b", true);
        UUID key = UUID.randomUUID();

        create(sessionOf(first), key, payload()).andExpect(status().isCreated());
        create(sessionOf(second), key, payload()).andExpect(status().isCreated());

        assertThat(incomesOf(first)).isEqualTo(1);
        assertThat(incomesOf(second)).isEqualTo(1);
    }

    @Test
    void aMissingOrMalformedIdempotencyKeyIsRejected() throws Exception {
        long userId = newUser("clave", true);

        create(sessionOf(userId), null, payload()).andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/incomes")
                        .cookie(sessionOf(userId))
                        .header("Idempotency-Key", "no-es-un-uuid")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.properties.fields.idempotencyKey").exists());
        assertThat(incomesOf(userId)).isZero();
    }

    @Test
    void everyInvalidFieldIsReportedNextToItsNameAndNothingIsPersisted() throws Exception {
        long userId = newUser("invalidos", true);
        Map<String, String> body = new HashMap<>();
        body.put("amount", "0");
        body.put("currency", "XYZ");
        body.put("date", "ayer");
        body.put("category", "FOOD");
        body.put("paymentMethod", "CREDIT_CARD");
        body.put("description", "a".repeat(201));

        create(sessionOf(userId), UUID.randomUUID(), body)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.properties.fields.amount").exists())
                .andExpect(jsonPath("$.properties.fields.currency").exists())
                .andExpect(jsonPath("$.properties.fields.date").exists())
                .andExpect(jsonPath("$.properties.fields.category").exists())
                .andExpect(jsonPath("$.properties.fields.paymentMethod").exists())
                .andExpect(jsonPath("$.properties.fields.description").exists());
        assertThat(incomesOf(userId)).isZero();
    }

    @Test
    void missingRequiredFieldsAreReported() throws Exception {
        long userId = newUser("faltan", true);

        create(sessionOf(userId), UUID.randomUUID(), Map.of())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.properties.fields.amount").exists())
                .andExpect(jsonPath("$.properties.fields.currency").exists())
                .andExpect(jsonPath("$.properties.fields.date").exists())
                .andExpect(jsonPath("$.properties.fields.category").exists())
                .andExpect(jsonPath("$.properties.fields.paymentMethod").exists());
    }

    @Test
    void theClientCannotChooseTheOwner() throws Exception {
        long userId = newUser("intruso", true);
        long victim = newUser("victima", true);
        Map<String, Object> body = new HashMap<>(payload());
        body.put("userId", victim);

        create(sessionOf(userId), UUID.randomUUID(), body)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.properties.fields.userId").exists());
        assertThat(incomesOf(victim)).isZero();
        assertThat(incomesOf(userId)).isZero();
    }

    @Test
    void aRequestWithoutSessionIsUnauthorizedAndDoesNotPersist() throws Exception {
        int before = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM incomes", Integer.class);

        create(null, UUID.randomUUID(), payload()).andExpect(status().isUnauthorized());

        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM incomes", Integer.class))
                .isEqualTo(before);
    }

    @Test
    void anUnverifiedAccountIsRejectedLikeTheRestOfThePersonalApi() throws Exception {
        long userId = newUser("pendiente", false);

        // The verified-account rule answers 401 for every personal endpoint (as for /api/expenses).
        create(sessionOf(userId), UUID.randomUUID(), payload())
                .andExpect(status().isUnauthorized());
        assertThat(incomesOf(userId)).isZero();
    }
}
