package com.amano.backend.friendship;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import com.amano.backend.config.JwtProperties;
import com.amano.backend.config.JwtService;

import jakarta.servlet.http.Cookie;
import tools.jackson.databind.ObjectMapper;

/**
 * Feature 012 contract tests over a real PostgreSQL: the respond endpoint
 * (PUT /api/users/me/friendship-requests/{id}) with its 200/400/401/404/409
 * codes, the persisted V12 transition (status + responded_at coherence) and the
 * single mutation guarantee under a concurrent race (SC-005). The 011 POST and
 * GET endpoints run afterwards as a regression anchor.
 */
@SpringBootTest(properties = {
        "jwt.secret=2b7e151628aed2a6abf7158809cf4f3c2b7e151628aed2a6abf7158809cf4f3c"
})
@AutoConfigureMockMvc
@Testcontainers
class FriendshipRequestRespondIT {

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

    @Autowired
    private FriendshipRequestService friendshipRequestService;

    // ----- Helpers ----------------------------------------------------------

    private static final String SECRET =
            "2b7e151628aed2a6abf7158809cf4f3c2b7e151628aed2a6abf7158809cf4f3c";

    /** Inserts a verified account directly and returns its id. */
    private long insertVerifiedUser(String email) {
        jdbcTemplate.update(
                "INSERT INTO users (first_name, email, password_hash, terms_accepted_at, "
                        + "email_verified_at) VALUES (?, ?, ?, now(), now())",
                "Bruno", email, "hash");
        return jdbcTemplate.queryForObject("SELECT id FROM users WHERE email = ?",
                Long.class, email);
    }

    /** Creates a pending friendship request directly (the 011 contract shape). */
    private long pendingRequest(long senderId, long recipientId) {
        return jdbcTemplate.queryForObject("""
                INSERT INTO friendship_requests (sender_id, recipient_id, status)
                VALUES (?, ?, 'PENDING') RETURNING id
                """, Long.class, senderId, recipientId);
    }

    private static Cookie sessionFor(long userId) {
        return new Cookie("amano_auth", new JwtService(
                new JwtProperties(SECRET, java.time.Duration.ofSeconds(86400))).issueToken(userId));
    }

    private Map<String, Object> rowOf(long id) {
        return jdbcTemplate.queryForMap(
                "SELECT status, sender_id, recipient_id, created_at, responded_at "
                        + "FROM friendship_requests WHERE id = ?", id);
    }

    private static String body(String action) {
        return "{\"action\":" + (action == null ? "null" : "\"" + action + "\"") + "}";
    }

    private ResultActions respond(Cookie session, String requestId, String action) throws Exception {
        return mockMvc.perform(put("/api/users/me/friendship-requests/" + requestId)
                .cookie(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(action)));
    }

    // ----- US1: acceptance (FR-002, FR-003) ----------------------------------

    @Test
    void acceptingReturnsTheFinalStatusAndPersistsItWithTheResponseDate() throws Exception {
        long sender = insertVerifiedUser("bruno-" + UUID.randomUUID() + "@example.com");
        long recipient = insertVerifiedUser("camila-" + UUID.randomUUID() + "@example.com");
        long requestId = pendingRequest(sender, recipient);

        mockMvc.perform(put("/api/users/me/friendship-requests/" + requestId)
                .cookie(sessionFor(recipient))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body("ACCEPT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(requestId))
                .andExpect(jsonPath("$.status").value("ACCEPTED"))
                .andExpect(jsonPath("$.createdAt").isNotEmpty())
                .andExpect(jsonPath("$.respondedAt").isNotEmpty())
                .andExpect(jsonPath("$.sender.id").value(sender))
                .andExpect(jsonPath("$.sender.firstName").value("Bruno"))
                .andExpect(jsonPath("$.sender.lastName").value(nullValue()))
                .andExpect(jsonPath("$.sender.email").isNotEmpty());

        assertThat(rowOf(requestId).get("status")).isEqualTo("ACCEPTED");
        assertThat(rowOf(requestId).get("responded_at")).isNotNull();
    }

    /** The answered request leaves the pending inbox of the recipient (FR-003). */
    @Test
    void theRespondedRequestLeavesThePendingInbox() throws Exception {
        long firstSender = insertVerifiedUser("bruno-" + UUID.randomUUID() + "@example.com");
        long secondSender = insertVerifiedUser("agustin-" + UUID.randomUUID() + "@example.com");
        long recipient = insertVerifiedUser("camila-" + UUID.randomUUID() + "@example.com");
        long answered = pendingRequest(firstSender, recipient);
        long remaining = pendingRequest(secondSender, recipient);

        mockMvc.perform(put("/api/users/me/friendship-requests/" + answered)
                .cookie(sessionFor(recipient))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body("ACCEPT")))
                .andExpect(status().isOk());

        MvcResult received = mockMvc
                .perform(get("/api/users/me/friendship-requests/received")
                        .cookie(sessionFor(recipient)))
                .andExpect(status().isOk())
                .andReturn();
        var list = objectMapper.readTree(received.getResponse().getContentAsString());
        assertThat(list).hasSize(1);
        assertThat(list.get(0).get("id").asLong()).isEqualTo(remaining);
    }

    // ----- US2: rejection (FR-005, FR-006) -----------------------------------

    @Test
    void rejectingPersistsRejectedWithoutAnyFriendshipAndAllowsReSending() throws Exception {
        long sender = insertVerifiedUser("bruno-" + UUID.randomUUID() + "@example.com");
        long recipient = insertVerifiedUser("camila-" + UUID.randomUUID() + "@example.com");
        long requestId = pendingRequest(sender, recipient);

        mockMvc.perform(put("/api/users/me/friendship-requests/" + requestId)
                .cookie(sessionFor(recipient))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body("REJECT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.respondedAt").isNotEmpty());

        assertThat(rowOf(requestId).get("status")).isEqualTo("REJECTED");

        // The historical rejection does not block a new submission (FR-006, 011 unchanged).
        mockMvc.perform(post("/api/users/me/friendship-requests")
                .cookie(sessionFor(sender))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"recipientId\":" + recipient + "}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    // ----- US3: access, validations, conflicts and concurrency ----------------

    @Test
    void invalidActionAndInvalidIdAre400WithoutTouchingTheRow() throws Exception {
        long sender = insertVerifiedUser("bruno-" + UUID.randomUUID() + "@example.com");
        long recipient = insertVerifiedUser("camila-" + UUID.randomUUID() + "@example.com");
        long requestId = pendingRequest(sender, recipient);

        mockMvc.perform(put("/api/users/me/friendship-requests/" + requestId)
                .cookie(sessionFor(recipient))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body("MAYBE")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.properties.fields.action").value("La acción no es válida"));

        mockMvc.perform(put("/api/users/me/friendship-requests/0")
                .cookie(sessionFor(recipient))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body("ACCEPT")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.properties.fields.id").value("La solicitud no es válida"));

        mockMvc.perform(put("/api/users/me/friendship-requests/no-es-numero")
                .cookie(sessionFor(recipient))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body("ACCEPT")))
                .andExpect(status().isBadRequest());

        assertThat(rowOf(requestId).get("status")).isEqualTo("PENDING");
    }

    @Test
    void unknownOrForeignRequestIsANeutral404WithoutDataChange() throws Exception {
        long sender = insertVerifiedUser("bruno-" + UUID.randomUUID() + "@example.com");
        long recipient = insertVerifiedUser("camila-" + UUID.randomUUID() + "@example.com");
        long requestId = pendingRequest(sender, recipient);

        // Unknown identifier, same neutral body (FR-009).
        mockMvc.perform(put("/api/users/me/friendship-requests/99999")
                .cookie(sessionFor(recipient))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body("ACCEPT")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("No se encontró la solicitud de amistad"));

        // The sender CANNOT respond to their own request (FR-007); same body.
        mockMvc.perform(put("/api/users/me/friendship-requests/" + requestId)
                .cookie(sessionFor(sender))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body("ACCEPT")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("No se encontró la solicitud de amistad"));

        assertThat(rowOf(requestId).get("status")).isEqualTo("PENDING");
    }

    @Test
    void anAlreadyAcceptedRequestIs409AndStaysUntouched() throws Exception {
        long sender = insertVerifiedUser("bruno-" + UUID.randomUUID() + "@example.com");
        long recipient = insertVerifiedUser("camila-" + UUID.randomUUID() + "@example.com");
        long requestId = pendingRequest(sender, recipient);

        respond(sessionFor(recipient), Long.toString(requestId), "ACCEPT")
                .andExpect(status().isOk());
        Object respondedAtBefore = rowOf(requestId).get("responded_at");

        mockMvc.perform(put("/api/users/me/friendship-requests/" + requestId)
                .cookie(sessionFor(recipient))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body("REJECT")))
                .andExpect(status().isConflict())
                .andExpect(
                        jsonPath("$.detail").value("Esta solicitud de amistad ya fue respondida"));

        assertThat(rowOf(requestId).get("status")).isEqualTo("ACCEPTED");
        assertThat(rowOf(requestId).get("responded_at")).isEqualTo(respondedAtBefore);
    }

    @Test
    void theVerifiedSessionRuleIsInheritedFromApiUsers() throws Exception {
        long sender = insertVerifiedUser("bruno-" + UUID.randomUUID() + "@example.com");
        long recipient = insertVerifiedUser("camila-" + UUID.randomUUID() + "@example.com");
        long requestId = pendingRequest(sender, recipient);

        mockMvc.perform(put("/api/users/me/friendship-requests/" + requestId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body("ACCEPT")))
                .andExpect(status().isUnauthorized());
    }

    /**
     * SC-005: exactly one concurrent answer wins and the rest receives the 409
     * without writing anything. The service is invoked from two raw threads so
     * the conditional UPDATE is raced against the real database.
     */
    @Test
    void concurrentResponsesProduceExactlyOneTransition() throws Exception {
        long sender = insertVerifiedUser("bruno-" + UUID.randomUUID() + "@example.com");
        long recipient = insertVerifiedUser("camila-" + UUID.randomUUID() + "@example.com");
        long requestId = pendingRequest(sender, recipient);

        CountDownLatch start = new CountDownLatch(1);
        CompletableFuture<String> accepter = CompletableFuture
                .supplyAsync(() -> answer(start, requestId, recipient, "ACCEPT"));
        CompletableFuture<String> rejecter = CompletableFuture
                .supplyAsync(() -> answer(start, requestId, recipient, "REJECT"));
        start.countDown();

        String acceptOutcome = accepter.get(10, TimeUnit.SECONDS);
        String rejectOutcome = rejecter.get(10, TimeUnit.SECONDS);

        // Exactly one transition wins; the loser observes the conflict and
        // writes nothing (no mixed states, no double responded_at).
        List<String> outcomes = List.of(acceptOutcome, rejectOutcome);
        long successes = outcomes.stream().filter(outcome -> outcome.startsWith("OK")).count();
        long conflicts = outcomes.stream().filter(outcome -> outcome.equals("CONFLICT")).count();
        assertThat(successes).isEqualTo(1);
        assertThat(conflicts).isEqualTo(1);

        String expected = List.of(acceptOutcome, rejectOutcome).contains("OK:ACCEPT")
                ? "ACCEPTED"
                : "REJECTED";
        Map<String, Object> row = rowOf(requestId);
        assertThat(row.get("status")).isEqualTo(expected);
        assertThat(row.get("responded_at")).isNotNull();
    }

    private String answer(CountDownLatch start, long requestId, long recipientId, String action) {
        try {
            start.await();
            friendshipRequestService.respond(recipientId, Long.toString(requestId),
                    new RespondFriendshipRequestRequest(action));
            return "OK:" + action;
        }
        catch (FriendshipRequestAlreadyRespondedException exception) {
            return "CONFLICT";
        }
        catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return "ERROR";
        }
    }
}

/** Issues valid session tokens directly (004 contract) for inserted accounts. */
final class SessionTokens {

    private static final String SECRET = "2b7e151628aed2a6abf7158809cf4f3c2b7e151628aed2a6abf7158809cf4f3c";

    private SessionTokens() {
    }

    static Cookie issue(long userId) {
        return new Cookie("amano_auth", new JwtService(
                new JwtProperties(SECRET, java.time.Duration.ofSeconds(86400))).issueToken(userId));
    }
}
