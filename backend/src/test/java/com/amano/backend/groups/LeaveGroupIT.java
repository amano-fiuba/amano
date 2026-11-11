package com.amano.backend.groups;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockPart;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import com.amano.backend.config.JwtService;

import jakarta.servlet.http.Cookie;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * US5 contract tests (T064): {@code POST /api/groups/{id}/leave} leaves the
 * group (left_at set + MEMBER_LEFT), the group persists for the others, and
 * the last admin is blocked. Groups here have no expenses, so nobody has
 * debts; the end-to-end blocking by pending debts (017 US2) lives in
 * {@link GroupDebtBlockingIT}.
 */
@SpringBootTest(properties = {
        "jwt.secret=2b7e151628aed2a6abf7158809cf4f3c2b7e151628aed2a6abf7158809cf4f3c"
})
@AutoConfigureMockMvc
@Testcontainers
class LeaveGroupIT {

    /** Real friendships replacing the old stub property (see {@link FriendshipFixtures}). */
    private static final java.util.List<String> FRIEND_FIXTURES = java.util.List.of("friend@example.com");

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

    private Cookie verifiedSessionOf(String rawEmail) throws Exception {
        mockMvc.perform(multipart("/api/auth/register")
                .part(
                        new MockPart("firstName", "Ana".getBytes(StandardCharsets.UTF_8)),
                        new MockPart("email", rawEmail.getBytes(StandardCharsets.UTF_8)),
                        new MockPart("password", "Passw0rd!".getBytes(StandardCharsets.UTF_8)),
                        new MockPart("confirmPassword", "Passw0rd!".getBytes(StandardCharsets.UTF_8)),
                        new MockPart("acceptTerms", "true".getBytes(StandardCharsets.UTF_8))))
                .andExpect(r -> assertThat(r.getResponse().getStatus()).isIn(201, 409));
        jdbcTemplate.update(
                "UPDATE users SET email_verified_at = CURRENT_TIMESTAMP WHERE email = ?", rawEmail);
        FriendshipFixtures.link(jdbcTemplate, rawEmail, FRIEND_FIXTURES);
        return new Cookie("amano_auth", jwtService.issueToken(userIdOf(rawEmail)));
    }

    private long userIdOf(String email) {
        return jdbcTemplate.queryForObject("SELECT id FROM users WHERE email = ?", Long.class, email);
    }

    private long createGroup(Cookie session) throws Exception {
        String created = mockMvc.perform(post("/api/groups")
                .cookie(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\": \"Amigos para viajar\", \"member\": {\"memberType\": \"REGISTERED\","
                        + " \"identifier\": \"friend@example.com\"}}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        JsonNode body = objectMapper.readTree(created);
        return body.get("id").asLong();
    }

    @Test
    void memberLeavesTheGroupAndItPersistsForTheOthers() throws Exception {
        Cookie admin = verifiedSessionOf("leave-admin@example.com");
        verifiedSessionOf("friend@example.com");
        long groupId = createGroup(admin);
        long memberId = userIdOf("friend@example.com");
        Cookie member = new Cookie("amano_auth", jwtService.issueToken(memberId));

        mockMvc.perform(post("/api/groups/" + groupId + "/leave").cookie(member))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.groupLeft").value(true))
                .andExpect(jsonPath("$.message").isNotEmpty());

        String leftAt = jdbcTemplate.queryForObject(
                "SELECT left_at FROM group_members WHERE group_id = ? AND user_id = ?",
                String.class, groupId, memberId);
        assertThat(leftAt).isNotNull(); // soft baja; the row is preserved

        // the group persists: the remaining admin can still read it and the
        // leaving is recorded as MEMBER_LEFT (NOT MEMBER_REMOVED)
        mockMvc.perform(get("/api/groups/" + groupId + "/activity").cookie(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].eventType").value("MEMBER_LEFT"));

    }

    @Test
    void theOnlyAdminCannotLeaveWhileOtherMembersRemain() throws Exception {
        Cookie admin = verifiedSessionOf("leave-only-admin@example.com");
        verifiedSessionOf("friend@example.com");
        long groupId = createGroup(admin);

        mockMvc.perform(get("/api/groups/" + groupId).cookie(admin))
                .andExpect(jsonPath("$.isLastAdmin").value(true))
                .andExpect(jsonPath("$.isLastMember").value(false));

        mockMvc.perform(post("/api/groups/" + groupId + "/leave").cookie(admin))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value(
                        "Sos el último administrador: asigná otro administrador antes de salir"));
    }

    @Test
    void theLastMemberLeavesAndTheGroupStopsShowingButItsRecordsRemain() throws Exception {
        Cookie admin = verifiedSessionOf("leave-last-member@example.com");
        verifiedSessionOf("friend@example.com");
        long groupId = createGroup(admin);
        Cookie member = new Cookie("amano_auth", jwtService.issueToken(userIdOf("friend@example.com")));
        mockMvc.perform(post("/api/groups/" + groupId + "/leave").cookie(member))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.groupDeleted").value(false));

        // a simulated profile does not count as another member
        jdbcTemplate.update("INSERT INTO simulated_profiles (group_id, name, email) VALUES (?, ?, ?)",
                groupId, "Simulada", "simulada@example.com");
        mockMvc.perform(get("/api/groups/" + groupId).cookie(admin))
                .andExpect(jsonPath("$.isLastMember").value(true))
                .andExpect(jsonPath("$.isLastAdmin").value(true));

        mockMvc.perform(post("/api/groups/" + groupId + "/leave").cookie(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.groupLeft").value(true))
                .andExpect(jsonPath("$.groupDeleted").value(true));

        mockMvc.perform(get("/api/groups/" + groupId).cookie(admin))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/groups/" + groupId + "/leave").cookie(admin))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/groups").cookie(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == " + groupId + ")]").isEmpty());

        // nothing was deleted: spec 020 FR-013
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM groups WHERE id = ?",
                Long.class, groupId)).isEqualTo(1L);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM group_members WHERE group_id = ?",
                Long.class, groupId)).isEqualTo(2L);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM group_members WHERE group_id = ? AND left_at IS NULL",
                Long.class, groupId)).isZero();
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM simulated_profiles WHERE group_id = ?",
                Long.class, groupId)).isEqualTo(1L);
    }

    @Test
    void outsideUserReceivesTheNotFound() throws Exception {
        Cookie outsider = verifiedSessionOf("outsider-leave@example.com");

        mockMvc.perform(post("/api/groups/999999/leave").cookie(outsider))
                .andExpect(status().isNotFound());
    }

    @Test
    void unauthenticatedLeaveIsUnauthorized() throws Exception {
        mockMvc.perform(post("/api/groups/1/leave"))
                .andExpect(status().isUnauthorized());
    }
}
