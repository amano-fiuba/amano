package com.amano.backend.groups;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockPart;
import org.springframework.test.web.servlet.MockMvc;

import com.amano.backend.config.JwtService;
import com.amano.backend.support.AbstractIntegrationIT;

import jakarta.servlet.http.Cookie;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * US9 contract tests (T050, IT): {@code GET /api/groups/{id}/activity} serves
 * the preserved history — pagination, eventType filter, descending order,
 * actor + timestamp in every entry, and entries alive after the related member
 * is removed.
 */
class GroupActivityControllerIT extends AbstractIntegrationIT {

    /** Real friendships replacing the old stub property (see {@link FriendshipFixtures}). */
    private static final java.util.List<String> FRIEND_FIXTURES = java.util.List.of("friend@example.com");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private ObjectMapper objectMapper;

    private Cookie verifiedSessionOf(String rawEmail) {
        jdbcTemplate.update("""
                INSERT INTO users (first_name, email, password_hash, terms_accepted_at, email_verified_at)
                VALUES ('Ana', ?, 'x', now(), CURRENT_TIMESTAMP)
                ON CONFLICT (email) DO UPDATE SET email_verified_at = CURRENT_TIMESTAMP
                """, rawEmail);
        FriendshipFixtures.link(jdbcTemplate, rawEmail, FRIEND_FIXTURES);
        return new Cookie("amano_auth", jwtService.issueToken(userIdOf(rawEmail)));
    }

    private long userIdOf(String email) {
        return jdbcTemplate.queryForObject("SELECT id FROM users WHERE email = ?", Long.class, email);
    }

    private long createGroupAndAddMembers() throws Exception {
        Cookie admin = verifiedSessionOf("history-admin@example.com");
        verifiedSessionOf("friend@example.com");

        String created = mockMvc.perform(post("/api/groups")
                .cookie(admin)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\": \"Historia\", \"member\": {\"memberType\": \"REGISTERED\","
                        + " \"identifier\": \"friend@example.com\"}}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        JsonNode body = objectMapper.readTree(created);
        long groupId = body.get("id").asLong();

        // recorded events (write side of the history): the initial member at
        // creation (014 FR-002), US3 addition and US2 permission change.
        mockMvc.perform(post("/api/groups/" + groupId + "/members").cookie(admin)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"memberType\": \"SIMULATED\", \"identifier\": \"simulado@example.com\","
                        + " \"name\": \"Simulado\"}"))
                .andExpect(status().isCreated());
        mockMvc.perform(put("/api/groups/" + groupId).cookie(admin)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"permissions\": {\"membersCannotInvite\": true}}"))
                .andExpect(status().isOk());
        return groupId;
    }

    @Test
    void historyIsDescendingAndShowsActorAndTimestamp() throws Exception {
        long groupId = createGroupAndAddMembers();
        Cookie admin = verifiedSessionOf("history-admin@example.com");

        mockMvc.perform(get("/api/groups/" + groupId + "/activity").cookie(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.totalPages").value(1))
                // newest first
                .andExpect(jsonPath("$.content[0].eventType").value("PERMISSION_CHANGED"))
                .andExpect(jsonPath("$.content[1].eventType").value("MEMBER_ADDED"))
                .andExpect(jsonPath("$.content[2].eventType").value("MEMBER_ADDED"))
                .andExpect(jsonPath("$.content[0].actor.id").isNumber())
                .andExpect(jsonPath("$.content[0].createdAt").isNotEmpty());
    }

    @Test
    void eventTypeFilterAndPaginationWork() throws Exception {
        long groupId = createGroupAndAddMembers();
        Cookie admin = verifiedSessionOf("history-admin@example.com");

        mockMvc.perform(get("/api/groups/" + groupId + "/activity").cookie(admin)
                .param("eventType", "PERMISSION_CHANGED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].eventType").value("PERMISSION_CHANGED"));

        mockMvc.perform(get("/api/groups/" + groupId + "/activity").cookie(admin)
                .param("eventType", "PAYMENT_MADE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));

        mockMvc.perform(get("/api/groups/" + groupId + "/activity").cookie(admin)
                .param("page", "1").param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.totalPages").value(3))
                .andExpect(jsonPath("$.size").value(1))
                .andExpect(jsonPath("$.page").value(1));
    }

    @Test
    void entriesArePreservedAfterTheMemberIsRemoved() throws Exception {
        long groupId = createGroupAndAddMembers();

        // physical delete of the simulated profile row (direct, US4 pending)
        jdbcTemplate.update("DELETE FROM simulated_profiles WHERE group_id = ?", groupId);

        Cookie admin = verifiedSessionOf("history-admin@example.com");
        mockMvc.perform(get("/api/groups/" + groupId + "/activity").cookie(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(3))
                // 014 D4: the alta keeps the copy of the name after the row is gone
                .andExpect(jsonPath("$.content[1].details.name").value("Simulado"));
    }

    // ---- 014: altas y bajas con quién y a quién (US1) ----

    @Test
    void initialMemberAltaIsRecordedAtCreationWithItsName() throws Exception {
        long groupId = createGroupAndAddMembers();
        Cookie admin = verifiedSessionOf("history-admin@example.com");

        mockMvc.perform(get("/api/groups/" + groupId + "/activity").cookie(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[2].eventType").value("MEMBER_ADDED"))
                .andExpect(jsonPath("$.content[2].actor.id").value(userIdOf("history-admin@example.com")))
                .andExpect(jsonPath("$.content[2].details.type").value("REGISTERED"))
                .andExpect(jsonPath("$.content[2].details.userId").value(userIdOf("friend@example.com")))
                .andExpect(jsonPath("$.content[2].details.name").value("Ana"))
                .andExpect(jsonPath("$.content[2].details.email").value("friend@example.com"))
                .andExpect(jsonPath("$.content[2].details.atCreation").value(true))
                .andExpect(jsonPath("$.content[1].details.type").value("SIMULATED"))
                .andExpect(jsonPath("$.content[1].details.atCreation").doesNotExist())
                .andExpect(jsonPath("$.content[0].details.fields[0]").value("permissions"));
    }

    @Test
    void namesWithQuotesAreStoredAsValidJsonAndBajasKeepTheCopy() throws Exception {
        long groupId = createGroupAndAddMembers();
        Cookie admin = verifiedSessionOf("history-admin@example.com");
        String tricky = "Abuela \"Tita\" \\ Ñandú";

        mockMvc.perform(post("/api/groups/" + groupId + "/members").cookie(admin)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(java.util.Map.of(
                        "memberType", "SIMULATED", "identifier", "tita@example.com", "name", tricky))))
                .andExpect(status().isCreated());
        long profileId = jdbcTemplate.queryForObject(
                "SELECT id FROM simulated_profiles WHERE group_id = ? AND email = ?", Long.class,
                groupId, "tita@example.com");
        mockMvc.perform(delete("/api/groups/" + groupId + "/simulated-profiles/" + profileId).cookie(admin))
                .andExpect(status().isOk());

        // the stored text is valid JSON (SC-002)
        String stored = jdbcTemplate.queryForObject(
                "SELECT details_json FROM group_activity WHERE group_id = ? AND event_type = 'MEMBER_REMOVED'",
                String.class, groupId);
        assertThat(objectMapper.readTree(stored).get("name").asString()).isEqualTo(tricky);

        mockMvc.perform(get("/api/groups/" + groupId + "/activity").cookie(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].eventType").value("MEMBER_REMOVED"))
                .andExpect(jsonPath("$.content[0].details.type").value("SIMULATED"))
                .andExpect(jsonPath("$.content[0].details.simulatedProfileId").value(profileId))
                .andExpect(jsonPath("$.content[0].details.name").value(tricky))
                .andExpect(jsonPath("$.content[1].eventType").value("MEMBER_ADDED"))
                .andExpect(jsonPath("$.content[1].details.name").value(tricky));
    }

    @Test
    void legacyExpenseCreatedEventWithoutPayerStillWorksAlongsideNewEventWithPayer() throws Exception {
        long groupId = createGroupAndAddMembers();
        Cookie admin = verifiedSessionOf("history-admin@example.com");
        long adminId = userIdOf("history-admin@example.com");
        long friendId = userIdOf("friend@example.com");

        // Legacy pre-022 EXPENSE_CREATED row without payerUserId / payerName
        jdbcTemplate.update("""
                INSERT INTO group_activity (group_id, event_type, actor_user_id, details_json, created_at)
                VALUES (?, 'EXPENSE_CREATED', ?, '{"expenseId":1,"amount":"800.00","currency":"ARS","description":"Pan"}', NOW() - INTERVAL '1 hour')
                """, groupId, adminId);

        // New 022 EXPENSE_CREATED row with payerUserId / payerName
        var transaction = new org.springframework.transaction.support.TransactionTemplate(transactionManager);
        transaction.executeWithoutResult(status -> activityEventService.recordExpenseCreated(groupId, adminId,
                new ExpenseActivityDetails(2L, new java.math.BigDecimal("1500.00"), "ARS", "Cena", friendId, "Juan Pérez")));

        mockMvc.perform(get("/api/groups/" + groupId + "/activity").cookie(admin)
                .param("eventType", "EXPENSE_CREATED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[0].details.expenseId").value(2))
                .andExpect(jsonPath("$.content[0].details.payerUserId").value(friendId))
                .andExpect(jsonPath("$.content[0].details.payerName").value("Juan Pérez"))
                .andExpect(jsonPath("$.content[1].details.expenseId").value(1))
                .andExpect(jsonPath("$.content[1].details.amount").value("800.00"))
                .andExpect(jsonPath("$.content[1].details.description").value("Pan"))
                .andExpect(jsonPath("$.content[1].details.payerUserId").doesNotExist())
                .andExpect(jsonPath("$.content[1].details.payerName").doesNotExist());
    }

    @Test
    void registeredBajaKeepsTheNameAndLegacyInvalidDetailsDegradeToNull() throws Exception {
        long groupId = createGroupAndAddMembers();
        Cookie admin = verifiedSessionOf("history-admin@example.com");
        long friendId = userIdOf("friend@example.com");

        mockMvc.perform(delete("/api/groups/" + groupId + "/members/" + friendId).cookie(admin))
                .andExpect(status().isOk());
        // a row written before 014 with concatenated (invalid) JSON
        jdbcTemplate.update("INSERT INTO group_activity (group_id, event_type, actor_user_id, details_json,"
                + " created_at) VALUES (?, 'MEMBER_ADDED', NULL, ?, NOW() - INTERVAL '1 day')",
                groupId, "{\"name\":\"Juan \"el tano\"\"}");

        mockMvc.perform(get("/api/groups/" + groupId + "/activity").cookie(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(5))
                .andExpect(jsonPath("$.content[0].eventType").value("MEMBER_REMOVED"))
                .andExpect(jsonPath("$.content[0].details.userId").value(friendId))
                .andExpect(jsonPath("$.content[0].details.name").value("Ana"))
                .andExpect(jsonPath("$.content[0].details.email").value("friend@example.com"))
                // oldest: the legacy row, without actor nor valid details
                .andExpect(jsonPath("$.content[4].actor").isEmpty())
                .andExpect(jsonPath("$.content[4].details").isEmpty());
    }

    // ---- 014: gastos y pagos con el enganche (US2) ----

    @Autowired
    private ActivityEventService activityEventService;

    @Autowired
    private org.springframework.transaction.PlatformTransactionManager transactionManager;

    @Test
    void expenseAndPaymentHooksAppearInTheHistory() throws Exception {
        long groupId = createGroupAndAddMembers();
        Cookie admin = verifiedSessionOf("history-admin@example.com");
        long adminId = userIdOf("history-admin@example.com");

        var transaction = new org.springframework.transaction.support.TransactionTemplate(transactionManager);
        transaction.executeWithoutResult(status -> activityEventService.recordExpenseCreated(groupId, adminId,
                new ExpenseActivityDetails(10L, new java.math.BigDecimal("1500.00"), "ars", "Súper")));
        transaction.executeWithoutResult(status -> activityEventService.recordPaymentMade(groupId, adminId,
                new PaymentActivityDetails(3L, new java.math.BigDecimal("500"), "ARS", "Juan", "Ana")));

        mockMvc.perform(get("/api/groups/" + groupId + "/activity").cookie(admin)
                .param("eventType", "EXPENSE_CREATED,PAYMENT_MADE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[0].eventType").value("PAYMENT_MADE"))
                .andExpect(jsonPath("$.content[0].actor.id").value(adminId))
                .andExpect(jsonPath("$.content[0].details.paymentId").value(3))
                .andExpect(jsonPath("$.content[0].details.amount").value("500"))
                .andExpect(jsonPath("$.content[0].details.fromName").value("Juan"))
                .andExpect(jsonPath("$.content[0].details.toName").value("Ana"))
                .andExpect(jsonPath("$.content[1].eventType").value("EXPENSE_CREATED"))
                .andExpect(jsonPath("$.content[1].details.expenseId").value(10))
                .andExpect(jsonPath("$.content[1].details.amount").value("1500.00"))
                .andExpect(jsonPath("$.content[1].details.currency").value("ARS"))
                .andExpect(jsonPath("$.content[1].details.description").value("Súper"));
    }

    @Test
    void hooksRequireAnEnclosingTransaction() {
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> activityEventService.recordExpenseCreated(1L, 1L,
                new ExpenseActivityDetails(1L, java.math.BigDecimal.ONE, "ARS", null)))
                .isInstanceOf(org.springframework.transaction.IllegalTransactionStateException.class);
    }

    // ---- 014: filtro y paginación (US3) ----

    @Test
    void severalTypesCanBeFilteredTogether() throws Exception {
        long groupId = createGroupAndAddMembers();
        Cookie admin = verifiedSessionOf("history-admin@example.com");
        mockMvc.perform(delete("/api/groups/" + groupId + "/members/" + userIdOf("friend@example.com"))
                .cookie(admin))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/groups/" + groupId + "/activity").cookie(admin)
                .param("eventType", "member_removed,MEMBER_LEFT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].eventType").value("MEMBER_REMOVED"));
    }

    @Test
    void unknownEventTypeIsABadRequest() throws Exception {
        long groupId = createGroupAndAddMembers();
        Cookie admin = verifiedSessionOf("history-admin@example.com");

        mockMvc.perform(get("/api/groups/" + groupId + "/activity").cookie(admin)
                .param("eventType", "MEMBER_ADDED,NOPE"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.properties.fields.eventType").value("El tipo de evento no es válido"));
    }

    @Test
    void pageSizeIsCappedAtOneHundred() throws Exception {
        long groupId = createGroupAndAddMembers();
        Cookie admin = verifiedSessionOf("history-admin@example.com");

        mockMvc.perform(get("/api/groups/" + groupId + "/activity").cookie(admin).param("size", "500"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value(100))
                .andExpect(jsonPath("$.content.length()").value(3));
    }

    @Test
    void unknownGroupIsNotFound() throws Exception {
        Cookie admin = verifiedSessionOf("history-404-admin@example.com");

        mockMvc.perform(get("/api/groups/999999/activity").cookie(admin))
                .andExpect(status().isNotFound());
    }

    @Test
    void unauthenticatedHistoryIsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/groups/1/activity"))
                .andExpect(status().isUnauthorized());
    }
}
