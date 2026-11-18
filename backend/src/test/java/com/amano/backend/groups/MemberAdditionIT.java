package com.amano.backend.groups;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.BeforeEach;
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
 * US3 contract tests (T042, IT): {@code POST /api/groups/{id}/members} adds
 * registered friends and simulated profiles (201), rejects duplicates with the
 * reason, enforces the invite casilla for MEMBER (403) and {@code GET
 * /api/groups/{id}/candidates?q=} returns ONLY the caller's friends
 * (FriendshipPort).
 */
class MemberAdditionIT extends AbstractIntegrationIT {

    /** Real friendships replacing the old stub property (see {@link FriendshipFixtures}). */
    private static final java.util.List<String> FRIEND_FIXTURES = java.util.List.of("friend@example.com", "invitee@example.com");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private ObjectMapper objectMapper;

    /**
     * {@link #createGroup(Cookie)} always adds {@code friend@example.com}, so
     * that account must exist regardless of which method runs first.
     */
    @BeforeEach
    void ensureFriendFixtures() {
        for (String email : FRIEND_FIXTURES) {
            jdbcTemplate.update("""
                    INSERT INTO users (first_name, email, password_hash, terms_accepted_at, email_verified_at)
                    VALUES ('Friend', ?, 'x', now(), CURRENT_TIMESTAMP)
                    ON CONFLICT (email) DO UPDATE SET email_verified_at = CURRENT_TIMESTAMP
                    """, email);
        }
    }

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

    @Test
    void adminAddsRegisteredFriendAndThenTheDuplicateIsRejected() throws Exception {
        Cookie admin = verifiedSessionOf("add-admin@example.com");
        verifiedSessionOf("friend@example.com");
        verifiedSessionOf("invitee@example.com");
        long groupId = createGroup(admin);

        mockMvc.perform(post("/api/groups/" + groupId + "/members").cookie(admin)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"memberType\": \"REGISTERED\", \"identifier\": \"invitee@example.com\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.member.role").value("MEMBER"))
                .andExpect(jsonPath("$.member.type").value("REGISTERED"))
                .andExpect(jsonPath("$.action").value("ADDED"));

        mockMvc.perform(post("/api/groups/" + groupId + "/members").cookie(admin)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"memberType\": \"REGISTERED\", \"identifier\": \"invitee@example.com\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Esa persona ya es miembro del grupo"));
    }

    @Test
    void adminAddsSimulatedProfileWithoutGroupMembersRow() throws Exception {
        Cookie admin = verifiedSessionOf("sim-add-admin@example.com");
        long groupId = createGroup(admin);

        mockMvc.perform(post("/api/groups/" + groupId + "/members").cookie(admin)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"memberType\": \"SIMULATED\", \"identifier\": \"nuevo@example.com\","
                        + " \"name\": \"Nuevo Simulado\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.member.type").value("SIMULATED"))
                .andExpect(jsonPath("$.member.name").value("Nuevo Simulado"))
                .andExpect(jsonPath("$.member.role").value("MEMBER"));

        Integer rows = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM simulated_profiles WHERE group_id = ? AND email = ?",
                Integer.class, groupId, "nuevo@example.com");
        Integer memberRows = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM group_members gm JOIN users u ON u.id = gm.user_id"
                        + " WHERE gm.group_id = ? AND u.email = ?",
                Integer.class, groupId, "nuevo@example.com");
        assertThat(rows).isEqualTo(1);
        assertThat(memberRows).isEqualTo(0);
    }

    @Test
    void memberIsForbiddenWhenTheInviteCasillaIsOn() throws Exception {
        Cookie admin = verifiedSessionOf("invite-casilla-admin@example.com");
        verifiedSessionOf("friend@example.com");
        verifiedSessionOf("invitee@example.com");
        long groupId = createGroup(admin);

        mockMvc.perform(put("/api/groups/" + groupId).cookie(admin)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"permissions\": {\"membersCannotInvite\": true}}"))
                .andExpect(status().isOk());

        long memberId = jdbcTemplate.queryForObject(
                "SELECT user_id FROM group_members WHERE group_id = ? AND role = 'MEMBER'",
                Long.class, groupId);
        Cookie member = new Cookie("amano_auth", jwtService.issueToken(memberId));

        mockMvc.perform(post("/api/groups/" + groupId + "/members").cookie(member)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"memberType\": \"REGISTERED\", \"identifier\": \"invitee@example.com\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value("No podés invitar gente a este grupo"));
    }

    @Test
    void candidateSearchReturnsOnlyTheCallersFriends() throws Exception {
        Cookie admin = verifiedSessionOf("candidates-admin@example.com");
        verifiedSessionOf("friend@example.com");
        verifiedSessionOf("invitee@example.com");
        long groupId = createGroup(admin);

        mockMvc.perform(get("/api/groups/" + groupId + "/candidates?q=inv").cookie(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].email").value("invitee@example.com"));

        mockMvc.perform(get("/api/groups/" + groupId + "/candidates?q=i").cookie(admin))
                .andExpect(status().isBadRequest());
    }

    @Test
    void unauthenticatedAdditionIsUnauthorized() throws Exception {
        mockMvc.perform(post("/api/groups/1/members")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"memberType\": \"SIMULATED\", \"identifier\": \"a@b.com\", \"name\": \"A\"}"))
                .andExpect(status().isUnauthorized());
    }
}
