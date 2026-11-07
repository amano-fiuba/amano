package com.amano.backend.payment;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.amano.backend.config.JwtService;

import jakarta.servlet.http.Cookie;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * 018: the group of the integration tests, built through the real API. Ana
 * (ADMIN) pays $ 10.000 "Súper" for herself, Juan (MEMBER) and the simulated
 * profile Abuela, so Juan and Abuela owe 3.333,33 each. Carla (MEMBER) is in
 * the group but is not part of the debts.
 */
final class PaymentScenario {

    private static final AtomicInteger SEQUENCE = new AtomicInteger();
    static final String EXPENSE_DATE = "2026-11-05";

    final long ana;
    final long juan;
    final long carla;
    final long outsider;
    final Cookie anaSession;
    final Cookie juanSession;
    final Cookie carlaSession;
    final Cookie outsiderSession;
    final long groupId;
    final long abuela;
    final long expenseId;
    final long juanDebt;
    final long abuelaDebt;

    private final MockMvc mockMvc;
    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    PaymentScenario(MockMvc mockMvc, JdbcTemplate jdbc, JwtService jwtService, ObjectMapper objectMapper)
            throws Exception {
        this.mockMvc = mockMvc;
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
        int n = SEQUENCE.incrementAndGet() + (int) (System.nanoTime() % 100000);
        ana = user(jdbc, "Ana", "López", "ana" + n + "@payments.dev");
        juan = user(jdbc, "Juan", "Pérez", "juan" + n + "@payments.dev");
        carla = user(jdbc, "Carla", "Gómez", "carla" + n + "@payments.dev");
        outsider = user(jdbc, "Otra", "Persona", "otra" + n + "@payments.dev");
        for (long friend : new long[] { juan, carla }) {
            jdbc.update("""
                    INSERT INTO friendship_requests (sender_id, recipient_id, status, responded_at)
                    VALUES (?, ?, 'ACCEPTED', now())
                    """, ana, friend);
        }
        anaSession = new Cookie("amano_auth", jwtService.issueToken(ana));
        juanSession = new Cookie("amano_auth", jwtService.issueToken(juan));
        carlaSession = new Cookie("amano_auth", jwtService.issueToken(carla));
        outsiderSession = new Cookie("amano_auth", jwtService.issueToken(outsider));

        String created = mockMvc.perform(post("/api/groups").cookie(anaSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\": \"Depto\", \"member\": {\"memberType\": \"REGISTERED\","
                        + " \"identifier\": \"juan" + n + "@payments.dev\"}}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        groupId = objectMapper.readTree(created).get("id").asLong();
        mockMvc.perform(post("/api/groups/" + groupId + "/members").cookie(anaSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"memberType\": \"REGISTERED\", \"identifier\": \"carla" + n + "@payments.dev\"}"))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/groups/" + groupId + "/members").cookie(anaSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"memberType\": \"SIMULATED\", \"identifier\": \"abuela" + n + "@payments.dev\","
                        + " \"name\": \"Abuela\"}"))
                .andExpect(status().isCreated());
        abuela = jdbc.queryForObject("SELECT id FROM simulated_profiles WHERE group_id = ?", Long.class, groupId);

        String expense = mockMvc.perform(post("/api/groups/" + groupId + "/expenses").cookie(anaSession)
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"amount": "10000.00", "currency": "ARS", "date": "%s", "description": "Súper",
                         "deviceTimeZone": "America/Argentina/Buenos_Aires", "payerUserId": %d,
                         "participants": [
                           {"type": "REGISTERED", "userId": %d},
                           {"type": "REGISTERED", "userId": %d},
                           {"type": "SIMULATED", "simulatedProfileId": %d}]}
                        """.formatted(EXPENSE_DATE, ana, ana, juan, abuela)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        JsonNode expenseJson = objectMapper.readTree(expense);
        expenseId = expenseJson.get("id").asLong();
        juanDebt = expenseJson.get("debts").get(0).get("id").asLong();
        abuelaDebt = expenseJson.get("debts").get(1).get("id").asLong();
    }

    static long user(JdbcTemplate jdbc, String first, String last, String email) {
        return jdbc.queryForObject("""
                INSERT INTO users (first_name, last_name, email, password_hash, terms_accepted_at, email_verified_at)
                VALUES (?, ?, ?, 'x', now(), now()) RETURNING id
                """, Long.class, first, last, email);
    }

    static String body(long debtId, String amount, String date) {
        return """
                {"debtId": %d, "amount": "%s", "date": "%s", "deviceTimeZone": "America/Argentina/Buenos_Aires"}
                """.formatted(debtId, amount, date);
    }

    ResultActions pay(Cookie session, String key, String body) throws Exception {
        var request = post("/api/payments").contentType(MediaType.APPLICATION_JSON).content(body);
        if (session != null) {
            request.cookie(session);
        }
        if (key != null) {
            request.header("Idempotency-Key", key);
        }
        return mockMvc.perform(request);
    }

    ResultActions pay(Cookie session, long debtId, String amount) throws Exception {
        return pay(session, UUID.randomUUID().toString(), body(debtId, amount, EXPENSE_DATE));
    }

    static String fullBody(String amount, String idempotencyKey) {
        return """
                {"amount": "%s", "idempotencyKey": "%s"}
                """.formatted(amount, idempotencyKey);
    }

    /** 022 US6: full payment through the group debt endpoint. */
    ResultActions registerFull(Cookie session, long debtId, String amount, String key) throws Exception {
        var request = post("/api/groups/" + groupId + "/debts/" + debtId + "/payments")
                .contentType(MediaType.APPLICATION_JSON)
                .content(fullBody(amount, key));
        if (session != null) {
            request.cookie(session);
        }
        return mockMvc.perform(request);
    }

    ResultActions registerFull(Cookie session, long debtId, String amount) throws Exception {
        return registerFull(session, debtId, amount, UUID.randomUUID().toString());
    }

    /** 022 US5: registers a payment with validation and returns its id. */
    long registerReported(Cookie session, long debtId, String amount) throws Exception {
        String body = registerFull(session, debtId, amount)
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("paymentId").asLong();
    }

    /** 022 US5: administrator confirmation of a reported payment. */
    ResultActions confirmPayment(Cookie session, long paymentId) throws Exception {
        return decidePayment(session, paymentId, "confirm");
    }

    /** 022 US5: administrator rejection of a reported payment. */
    ResultActions rejectPayment(Cookie session, long paymentId) throws Exception {
        return decidePayment(session, paymentId, "reject");
    }

    private ResultActions decidePayment(Cookie session, long paymentId, String action) throws Exception {
        var request = post("/api/groups/" + groupId + "/payments/" + paymentId + "/" + action);
        if (session != null) {
            request.cookie(session);
        }
        return mockMvc.perform(request);
    }

    /** Promotes an existing active member to ADMIN for the decision tests. */
    void makeAdmin(long userId) {
        jdbc.update("UPDATE group_members SET role = 'ADMIN' WHERE group_id = ? AND user_id = ?", groupId, userId);
    }

    String statusOf(long debtId) {
        return jdbc.queryForObject("SELECT status FROM debts WHERE id = ?", String.class, debtId);
    }

    int paymentEvents() {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM group_activity WHERE group_id = ? AND event_type = 'PAYMENT_MADE'",
                Integer.class, groupId);
    }

    int payments(long debtId) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM payments WHERE debt_id = ?", Integer.class, debtId);
    }

    int activePayments(long debtId) {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM payments WHERE debt_id = ? AND legacy = FALSE", Integer.class, debtId);
    }

    void requireValidation() {
        jdbc.update("UPDATE group_permissions SET payments_require_validation = TRUE WHERE group_id = ?", groupId);
    }
}
