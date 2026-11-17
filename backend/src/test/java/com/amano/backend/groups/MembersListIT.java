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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockPart;
import org.springframework.test.web.servlet.MockMvc;

import com.amano.backend.config.JwtService;
import com.amano.backend.support.AbstractIntegrationIT;

import jakarta.servlet.http.Cookie;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * US6 contract tests (T076): {@code GET /api/groups/{id}/members} lists the
 * ACTIVE registered members (left_at IS NULL) plus the group's simulated
 * profiles with name, avatar and role; simulated profiles are members by
 * group_id alone (no group_members row). Outsiders get the neutral 404; no
 * session gets 401.
 */
class MembersListIT extends AbstractIntegrationIT {

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

    private long createGroup(Cookie session) throws Exception {
        String created = mockMvc.perform(post("/api/groups")
                .cookie(session)
                .contentType("application/json")
                .content("{\"name\": \"Amigos para viajar\", \"member\": {\"memberType\": \"REGISTERED\","
                        + " \"identifier\": \"friend@example.com\"}}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(created).get("id").asLong();
    }

    private void addSimulated(Cookie session, long groupId, String slug) throws Exception {
        mockMvc.perform(post("/api/groups/" + groupId + "/members").cookie(session)
                .contentType("application/json")
                .content("{\"memberType\": \"SIMULATED\", \"identifier\": \"sim-" + slug
                        + "@amano.dev\", \"name\": \"Juan Simulado\"}"))
                .andExpect(r -> assertThat(r.getResponse().getStatus())
                        .as(r.getResponse().getContentAsString()).isIn(201, 409));
    }

    @Test
    void listsActiveMembersAndSimulatedProfilesForAMemberOfTheGroup() throws Exception {
        Cookie admin = verifiedSessionOf("members-admin@example.com");
        verifiedSessionOf("friend@example.com");
        jdbcTemplate.update("UPDATE users SET cbu = ? WHERE email = ?",
                "0000003100010000000001", "members-admin@example.com");
        long groupId = createGroup(admin);
        addSimulated(admin, groupId, "juan");

        String content = mockMvc.perform(get("/api/groups/" + groupId + "/members").cookie(admin))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode members = objectMapper.readTree(content);
        assertThat(members.size()).isEqualTo(3);
        JsonNode simulated = null;
        JsonNode adminRow = null;
        for (JsonNode row : members) {
            if (row.path("simulatedProfileId").isNumber()) {
                simulated = row;
            }
            if (row.path("role").asText().equals("ADMIN")) {
                adminRow = row;
            }
        }
        assertThat(adminRow.path("role").asText()).isEqualTo("ADMIN");
        // Group members can copy the CBU/CVU of a registered member to transfer.
        assertThat(adminRow.path("cbuCvu").asText()).isEqualTo("0000003100010000000001");
        assertThat(simulated.path("name").asText()).isEqualTo("Juan Simulado");
        assertThat(simulated.path("role").asText()).isEqualTo("MEMBER");
        assertThat(simulated.path("userId").isNull()).isTrue(); // no group_members row
        assertThat(simulated.path("cbuCvu").isNull()).isTrue(); // simulated → no CBU
    }

    @Test
    void aLeftMemberDisappearsFromTheList() throws Exception {
        Cookie admin = verifiedSessionOf("members-left-admin@example.com");
        verifiedSessionOf("friend@example.com");
        long groupId = createGroup(admin);
        long memberId = userIdOf("friend@example.com");
        jdbcTemplate.update(
                "UPDATE group_members SET left_at = CURRENT_TIMESTAMP WHERE group_id = ? AND user_id = ?",
                groupId, memberId);

        mockMvc.perform(get("/api/groups/" + groupId + "/members").cookie(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1)) // admin only
                .andExpect(jsonPath("$[0].name").isNotEmpty());
    }

    @Test
    void aRegisteredMemberWithoutCustomAvatarReportsANullAvatar() throws Exception {
        Cookie admin = verifiedSessionOf("members-avatar-admin@example.com");
        verifiedSessionOf("friend@example.com");
        long groupId = createGroup(admin);

        mockMvc.perform(get("/api/groups/" + groupId + "/members").cookie(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].name").isNotEmpty())
                .andExpect(jsonPath("$[*].avatar").exists()); // null → gray default in the UI
    }

    @Test
    void anOutsiderReceivesTheNeutralNotFound() throws Exception {
        Cookie outsider = verifiedSessionOf("members-outsider@example.com");

        mockMvc.perform(get("/api/groups/999999/members").cookie(outsider))
                .andExpect(status().isNotFound());
    }

    @Test
    void unauthenticatedMembersRequestIsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/groups/1/members"))
                .andExpect(status().isUnauthorized());
    }
}
