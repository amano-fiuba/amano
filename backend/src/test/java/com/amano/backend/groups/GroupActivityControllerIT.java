package com.amano.backend.groups;

import static org.assertj.core.api.Assertions.assertThat;
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
 * US9 contract tests (T050, IT): {@code GET /api/groups/{id}/activity} serves
 * the preserved history — pagination, eventType filter, descending order,
 * actor + timestamp in every entry, and entries alive after the related member
 * is removed.
 */
@SpringBootTest(properties = {
        "jwt.secret=2b7e151628aed2a6abf7158809cf4f3c2b7e151628aed2a6abf7158809cf4f3c",
        "groups.friendship.stub-friend-emails=friend@example.com"
})
@AutoConfigureMockMvc
@Testcontainers
class GroupActivityControllerIT {

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

        // recorded events (write side of the history): US3 addition and US2
        // permission change. US1 creation does not record MEMBER_ADDED by
        // design (events begin with the stories that use them).
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
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.totalPages").value(1))
                // newest first
                .andExpect(jsonPath("$.content[0].eventType").value("PERMISSION_CHANGED"))
                .andExpect(jsonPath("$.content[1].eventType").value("MEMBER_ADDED"))
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
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.totalPages").value(2))
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
                .andExpect(jsonPath("$.totalElements").value(2));
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
