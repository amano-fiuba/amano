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
 * US4 contract tests (T056, IT): {@code DELETE /api/groups/{id}/members/{userId}}
 * applies the soft baja and records MEMBER_REMOVED, rejects a member-of-the-group
 * caller (403), rejects removing another ADMIN ("primero quitale el rol"), and
 * {@code DELETE /api/groups/{id}/simulated-profiles/{profileId}} physically
 * deletes the row (by admin, or by member when the casilla is on). Pending
 * debts cannot happen end-to-end with the NoOp DebtPort stub (always zero
 * debts) — that case is covered by unit tests mocking the port (T055).
 */
@SpringBootTest(properties = {
        "jwt.secret=2b7e151628aed2a6abf7158809cf4f3c2b7e151628aed2a6abf7158809cf4f3c"
})
@AutoConfigureMockMvc
@Testcontainers
class MemberRemovalIT {

    /** Real friendships replacing the old stub property (see {@link FriendshipFixtures}). */
    private static final java.util.List<String> FRIEND_FIXTURES = java.util.List.of("friend@example.com", "invitee@example.com");

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
    void adminRemovesTheMemberAndTheRowIsSoftDeleted() throws Exception {
        Cookie admin = verifiedSessionOf("removal-admin@example.com");
        verifiedSessionOf("friend@example.com");
        long groupId = createGroup(admin);
        long memberId = userIdOf("friend@example.com");

        mockMvc.perform(delete("/api/groups/" + groupId + "/members/" + memberId).cookie(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.removed").value(true))
                .andExpect(jsonPath("$.message").isNotEmpty());

        String state = jdbcTemplate.queryForObject(
                "SELECT left_at || '|' || role FROM group_members WHERE group_id = ? AND user_id = ?",
                String.class, groupId, memberId);
        assertThat(state).matches("[^|]+\\|MEMBER"); // left_at set, role kept, row preserved

        mockMvc.perform(get("/api/groups/" + groupId + "/activity").cookie(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].eventType").value("MEMBER_REMOVED"))
                .andExpect(jsonPath("$.content[0].actor.id").value(userIdOf("removal-admin@example.com")));
    }

    @Test
    void memberCallerIsForbidden() throws Exception {
        Cookie admin = verifiedSessionOf("removal-member-admin@example.com");
        verifiedSessionOf("friend@example.com");
        long groupId = createGroup(admin);
        long memberId = userIdOf("friend@example.com");
        Cookie member = new Cookie("amano_auth", jwtService.issueToken(memberId));

        mockMvc.perform(delete("/api/groups/" + groupId + "/members/" + memberId).cookie(member))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value("No sos admin de este grupo"));
    }

    @Test
    void adminCannotRemoveAnotherAdminSoTheRoleMustBeRevokedFirst() throws Exception {
        Cookie admin = verifiedSessionOf("removal-admin2-admin@example.com");
        verifiedSessionOf("friend@example.com");
        long groupId = createGroup(admin);
        long memberId = userIdOf("friend@example.com");

        // prep: the US10 grant endpoint does not exist yet (Fase 9); the role
        // switch is set directly and covered end-to-end by AdminRoleIT later.
        jdbcTemplate.update("UPDATE group_members SET role = 'ADMIN' WHERE group_id = ? AND user_id = ?",
                groupId, memberId);

        mockMvc.perform(delete("/api/groups/" + groupId + "/members/" + memberId).cookie(admin))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value(
                        "No podés eliminar directamente a otro administrador: primero quitale el rol"));
    }

    @Test
    void adminDeletesTheSimulatedProfilePhysically() throws Exception {
        Cookie admin = verifiedSessionOf("sim-removal-admin@example.com");

        String created = mockMvc.perform(post("/api/groups")
                .cookie(admin)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\": \"Simulado Baja\", \"member\": {\"memberType\": \"SIMULATED\","
                        + " \"identifier\": \"simulado@amano.dev\", \"name\": \"Juan Simulado\"}}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long groupId = objectMapper.readTree(created).get("id").asLong();
        long profileId = jdbcTemplate.queryForObject(
                "SELECT id FROM simulated_profiles WHERE group_id = ?", Long.class, groupId);

        mockMvc.perform(delete("/api/groups/" + groupId + "/simulated-profiles/" + profileId).cookie(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.removed").value(true));

        Integer rows = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM simulated_profiles WHERE id = ?", Integer.class, profileId);
        assertThat(rows).isZero();
    }

    @Test
    void memberRemovesSimulatedProfileWhenTheCasillaIsOn() throws Exception {
        Cookie admin = verifiedSessionOf("sim-casilla-admin@example.com");
        verifiedSessionOf("friend@example.com");
        long groupId = createGroup(admin);

        mockMvc.perform(put("/api/groups/" + groupId).cookie(admin)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"permissions\": {\"membersCanManageSimulatedProfiles\": true}}"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/groups/" + groupId + "/members").cookie(admin)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"memberType\": \"SIMULATED\", \"identifier\": \"otro@amano.dev\","
                        + " \"name\": \"Otro Simulado\"}"))
                .andExpect(status().isCreated());
        long profileId = jdbcTemplate.queryForObject(
                "SELECT id FROM simulated_profiles WHERE group_id = ? AND email = 'otro@amano.dev'",
                Long.class, groupId);

        Cookie member = new Cookie("amano_auth", jwtService.issueToken(userIdOf("friend@example.com")));
        mockMvc.perform(delete("/api/groups/" + groupId + "/simulated-profiles/" + profileId).cookie(member))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.removed").value(true));
    }

    @Test
    void unauthenticatedRemovalIsUnauthorized() throws Exception {
        mockMvc.perform(delete("/api/groups/1/members/2"))
                .andExpect(status().isUnauthorized());
    }
}
