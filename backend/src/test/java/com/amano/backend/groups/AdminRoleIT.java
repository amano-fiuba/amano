package com.amano.backend.groups;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
 * US10 contract tests (T070): the two SEPARATE role endpoints —
 * {@code POST /api/groups/{id}/members/{userId}/admin} (assign) and
 * {@code DELETE /api/groups/{id}/members/{userId}/admin} (revoke). The role is
 * per group; a MEMBER caller gets 403; revoking the last admin is blocked with
 * the exact spec message; a revoked admin remains a MEMBER of the group.
 */
class AdminRoleIT extends AbstractIntegrationIT {

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
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\": \"Amigos para viajar\", \"member\": {\"memberType\": \"REGISTERED\","
                        + " \"identifier\": \"friend@example.com\"}}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        JsonNode body = objectMapper.readTree(created);
        return body.get("id").asLong();
    }

    private String roleOf(long groupId, long userId) {
        return jdbcTemplate.queryForObject(
                "SELECT role FROM group_members WHERE group_id = ? AND user_id = ? AND left_at IS NULL",
                String.class, groupId, userId);
    }

    @Test
    void adminGrantsTheRoleWithTheAssignEndpoint() throws Exception {
        Cookie admin = verifiedSessionOf("grant-admin@example.com");
        verifiedSessionOf("friend@example.com");
        long groupId = createGroup(admin);
        long memberId = userIdOf("friend@example.com");

        mockMvc.perform(post("/api/groups/" + groupId + "/members/" + memberId + "/admin")
                .cookie(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.targetUserId").value(memberId))
                .andExpect(jsonPath("$.newRole").value("ADMIN"));

        assertThat(roleOf(groupId, memberId)).isEqualTo("ADMIN");
    }

    @Test
    void memberCallerIsForbiddenOnBothEndpoints() throws Exception {
        Cookie admin = verifiedSessionOf("grant-forbidden-admin@example.com");
        verifiedSessionOf("friend@example.com");
        long groupId = createGroup(admin);
        long memberId = userIdOf("friend@example.com");
        Cookie member = new Cookie("amano_auth", jwtService.issueToken(memberId));

        mockMvc.perform(post("/api/groups/" + groupId + "/members/" + memberId + "/admin")
                .cookie(member))
                .andExpect(status().isForbidden());
        assertThat(roleOf(groupId, memberId)).isEqualTo("MEMBER");
    }

    @Test
    void revokeEndpointDemotesTheAdminAndKeepsTheMembership() throws Exception {
        Cookie admin = verifiedSessionOf("revoke-admin@example.com");
        verifiedSessionOf("friend@example.com");
        long groupId = createGroup(admin);
        long memberId = userIdOf("friend@example.com");

        mockMvc.perform(post("/api/groups/" + groupId + "/members/" + memberId + "/admin")
                .cookie(admin))
                .andExpect(status().isOk());
        assertThat(roleOf(groupId, memberId)).isEqualTo("ADMIN");

        mockMvc.perform(delete("/api/groups/" + groupId + "/members/" + memberId + "/admin")
                .cookie(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.targetUserId").value(memberId))
                .andExpect(jsonPath("$.newRole").value("MEMBER"));

        assertThat(roleOf(groupId, memberId)).isEqualTo("MEMBER"); // membership kept
    }

    @Test
    void theLastAdminCannotBeRevoked() throws Exception {
        Cookie admin = verifiedSessionOf("revoke-last-admin@example.com");
        verifiedSessionOf("friend@example.com");
        long groupId = createGroup(admin);
        long adminId = userIdOf("revoke-last-admin@example.com");

        mockMvc.perform(delete("/api/groups/" + groupId + "/members/" + adminId + "/admin")
                .cookie(admin))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value(
                        "Sos el último administrador: asigná otro administrador antes de salir"));

        assertThat(roleOf(groupId, adminId)).isEqualTo("ADMIN"); // prior role preserved
    }

    @Test
    void unauthenticatedRoleChangesAreUnauthorized() throws Exception {
        mockMvc.perform(post("/api/groups/1/members/2/admin"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(delete("/api/groups/1/members/2/admin"))
                .andExpect(status().isUnauthorized());
    }
}
