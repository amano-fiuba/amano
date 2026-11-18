package com.amano.backend.income;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
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
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;
import com.amano.backend.support.AbstractIntegrationIT;

/**
 * Access control of the personal incomes (specs/033-income-management, US5, FR-007):
 * another person's income, a missing one and a non-numeric id answer the very same
 * 404, nothing is readable without a verified session, and every required field of
 * the registration is reported by name.
 */
class IncomeAccessIT extends AbstractIntegrationIT {

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
        payload.put("deviceTimeZone", "UTC");
        return payload;
    }

    private ResultActions create(long userId, Map<String, String> body) throws Exception {
        return mockMvc.perform(post("/api/incomes")
                .cookie(sessionOf(userId))
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)));
    }

    private long createdBy(long userId) throws Exception {
        String response = create(userId, payload()).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("id").asLong();
    }

    private JsonNode withoutInstance(String body) throws Exception {
        ObjectNode node = (ObjectNode) objectMapper.readTree(body);
        node.remove("instance");
        return node;
    }

    private String bodyOf(ResultActions result) throws Exception {
        return result.andReturn().getResponse().getContentAsString();
    }

    @Test
    void theOwnerReadsTheIncomeWithItsConversions() throws Exception {
        long owner = newUser("dueno", true);
        long id = createdBy(owner);

        mockMvc.perform(get("/api/incomes/" + id).cookie(sessionOf(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.amount").value("850000.00"))
                .andExpect(jsonPath("$.paymentMethod").value("TRANSFER"))
                .andExpect(jsonPath("$.projected").value(false))
                .andExpect(jsonPath("$.conversions").isArray());
    }

    @Test
    void aForeignAMissingAndANonNumericIdAnswerTheSameNeutral404() throws Exception {
        long owner = newUser("propietaria", true);
        long intruder = newUser("intruso", true);
        long id = createdBy(owner);

        String foreign = bodyOf(mockMvc.perform(get("/api/incomes/" + id).cookie(sessionOf(intruder)))
                .andExpect(status().isNotFound()));
        String missing = bodyOf(mockMvc.perform(get("/api/incomes/987654321").cookie(sessionOf(intruder)))
                .andExpect(status().isNotFound()));
        String letters = bodyOf(mockMvc.perform(get("/api/incomes/abc").cookie(sessionOf(intruder)))
                .andExpect(status().isNotFound()));
        String overflow = bodyOf(mockMvc.perform(
                get("/api/incomes/99999999999999999999").cookie(sessionOf(intruder)))
                .andExpect(status().isNotFound()));

        // Only `instance` differs: it echoes the path the caller itself asked for.
        assertThat(withoutInstance(missing)).isEqualTo(withoutInstance(foreign));
        assertThat(withoutInstance(letters)).isEqualTo(withoutInstance(foreign));
        assertThat(withoutInstance(overflow)).isEqualTo(withoutInstance(foreign));
        assertThat(objectMapper.readTree(foreign).get("title").asText())
                .isEqualTo("Ingreso no encontrado");
        assertThat(objectMapper.readTree(foreign).get("detail").asText())
                .isEqualTo("No encontramos ese ingreso");
        // The error never reveals the id nor the owner.
        assertThat(objectMapper.readTree(foreign).get("title").asText()).doesNotContainPattern("\\d");
        assertThat(objectMapper.readTree(foreign).get("detail").asText()).doesNotContainPattern("\\d");
    }

    @Test
    void theIncomeOfAnotherPersonNeverShowsInTheirSearchOrYears() throws Exception {
        long owner = newUser("lista-a", true);
        long other = newUser("lista-b", true);
        createdBy(owner);

        mockMvc.perform(get("/api/incomes/search").cookie(sessionOf(other)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
        mockMvc.perform(get("/api/incomes/years").cookie(sessionOf(other)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.years").isEmpty());
    }

    @Test
    void everyReadAndTheRegistrationNeedASession() throws Exception {
        long owner = newUser("sinsesion", true);
        long id = createdBy(owner);

        mockMvc.perform(get("/api/incomes/" + id)).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/incomes/search")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/incomes/years")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/incomes")
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload())))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void anUnverifiedAccountCannotReadNorRegisterAnything() throws Exception {
        long owner = newUser("verificada", true);
        long id = createdBy(owner);
        long pending = newUser("pendiente-acceso", false);

        mockMvc.perform(get("/api/incomes/" + id).cookie(sessionOf(pending)))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/incomes/search").cookie(sessionOf(pending)))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/incomes/years").cookie(sessionOf(pending)))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/balance").cookie(sessionOf(pending)))
                .andExpect(status().isUnauthorized());
        create(pending, payload()).andExpect(status().isUnauthorized());
    }

    @Test
    void eachMissingRequiredFieldIsReportedByItsName() throws Exception {
        long owner = newUser("campos", true);

        for (String field : List.of("amount", "currency", "date", "category", "paymentMethod")) {
            Map<String, String> body = payload();
            body.remove(field);
            create(owner, body)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.properties.fields." + field).exists());
        }
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM incomes WHERE user_id = ?", Integer.class, owner)).isZero();
    }
}
