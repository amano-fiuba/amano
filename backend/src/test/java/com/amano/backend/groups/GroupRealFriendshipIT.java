package com.amano.backend.groups;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.Map;

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
import tools.jackson.databind.ObjectMapper;

/**
 * End to end with the REAL friendships (010 FR-007/FR-009, fix of the
 * {@code NoOpFriendshipPort} stub): a friendship created with the endpoints of
 * 011/012 lets the user search, add and create groups with that friend; a
 * pending or rejected request does not. No stub property, no fixtures.
 */
@SpringBootTest(properties = {
        "jwt.secret=2b7e151628aed2a6abf7158809cf4f3c2b7e151628aed2a6abf7158809cf4f3c"
})
@AutoConfigureMockMvc
@Testcontainers
class GroupRealFriendshipIT {

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

    private Cookie verifiedSessionOf(String email, String firstName) throws Exception {
        mockMvc.perform(multipart("/api/auth/register")
                .part(
                        new MockPart("firstName", firstName.getBytes(StandardCharsets.UTF_8)),
                        new MockPart("email", email.getBytes(StandardCharsets.UTF_8)),
                        new MockPart("password", "Passw0rd!".getBytes(StandardCharsets.UTF_8)),
                        new MockPart("confirmPassword", "Passw0rd!".getBytes(StandardCharsets.UTF_8)),
                        new MockPart("acceptTerms", "true".getBytes(StandardCharsets.UTF_8))))
                .andExpect(status().isCreated());
        jdbcTemplate.update("UPDATE users SET email_verified_at = CURRENT_TIMESTAMP WHERE email = ?", email);
        return new Cookie("amano_auth", jwtService.issueToken(userIdOf(email)));
    }

    private long userIdOf(String email) {
        return jdbcTemplate.queryForObject("SELECT id FROM users WHERE email = ?", Long.class, email);
    }

    private long sendRequest(Cookie sender, String recipientEmail) throws Exception {
        String body = mockMvc.perform(post("/api/users/me/friendship-requests").cookie(sender)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"recipientId\": " + userIdOf(recipientEmail) + "}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("id").asLong();
    }

    private void respond(Cookie recipient, long requestId, String action) throws Exception {
        mockMvc.perform(put("/api/users/me/friendship-requests/" + requestId).cookie(recipient)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"action\": \"" + action + "\"}"))
                .andExpect(status().isOk());
    }

    private long groupWithSimulatedMember(Cookie admin) throws Exception {
        String body = mockMvc.perform(post("/api/groups").cookie(admin)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\": \"Depto\", \"member\": {\"memberType\": \"SIMULATED\","
                        + " \"identifier\": \"abuela@example.com\", \"name\": \"Abuela\"}}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("id").asLong();
    }

    private org.springframework.test.web.servlet.ResultActions addRegistered(Cookie admin, long groupId,
            String email) throws Exception {
        return mockMvc.perform(post("/api/groups/" + groupId + "/members").cookie(admin)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"memberType\": \"REGISTERED\", \"identifier\": \"" + email + "\"}"));
    }

    @Test
    void acceptedFriendCanBeSearchedAndAddedButPendingCannot() throws Exception {
        Cookie ana = verifiedSessionOf("ana.real@example.com", "Ana");
        Cookie beto = verifiedSessionOf("beto.real@example.com", "Beto");
        long groupId = groupWithSimulatedMember(ana);

        // not friends yet
        addRegistered(ana, groupId, "beto.real@example.com")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("La persona que querés agregar no está entre tus amigos"));

        // pending request: still not a friend
        long requestId = sendRequest(ana, "beto.real@example.com");
        mockMvc.perform(get("/api/groups/" + groupId + "/candidates?q=beto").cookie(ana))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(0));
        addRegistered(ana, groupId, "beto.real@example.com").andExpect(status().isNotFound());

        // accepted by Beto: now Ana finds and adds him
        respond(beto, requestId, "ACCEPT");
        mockMvc.perform(get("/api/groups/" + groupId + "/candidates?q=beto").cookie(ana))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].email").value("beto.real@example.com"));
        addRegistered(ana, groupId, "BETO.real@example.com ")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.member.type").value("REGISTERED"))
                .andExpect(jsonPath("$.member.email").value("beto.real@example.com"));

        Integer rows = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM group_members WHERE group_id = ? AND user_id = ? AND left_at IS NULL",
                Integer.class, groupId, userIdOf("beto.real@example.com"));
        assertThat(rows).isEqualTo(1);
    }

    @Test
    void friendshipWorksInBothDirectionsAndAtGroupCreation() throws Exception {
        Cookie carla = verifiedSessionOf("carla.real@example.com", "Carla");
        Cookie dani = verifiedSessionOf("dani.real@example.com", "Dani");

        // Dani sent the request and Carla accepted: Carla can still use Dani
        respond(carla, sendRequest(dani, "carla.real@example.com"), "ACCEPT");

        mockMvc.perform(post("/api/groups").cookie(carla)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("name", "Viaje", "member",
                        Map.of("memberType", "REGISTERED", "identifier", "dani.real@example.com")))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.membersCount").value(2));
        // and the other direction works too
        long groupId = groupWithSimulatedMember(dani);
        addRegistered(dani, groupId, "carla.real@example.com").andExpect(status().isCreated());
    }

    @Test
    void rejectedRequestIsNotAFriendship() throws Exception {
        Cookie eli = verifiedSessionOf("eli.real@example.com", "Eli");
        Cookie fede = verifiedSessionOf("fede.real@example.com", "Fede");
        respond(fede, sendRequest(eli, "fede.real@example.com"), "REJECT");
        long groupId = groupWithSimulatedMember(eli);

        addRegistered(eli, groupId, "fede.real@example.com").andExpect(status().isNotFound());
    }
}
