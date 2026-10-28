package com.amano.backend.groups;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import tools.jackson.databind.node.JsonNodeFactory;

/**
 * US1 contract tests (T024) over a real PostgreSQL: creation with an
 * additional member (201), validation errors (400), the not-a-friend case
 * (404), the groups list of the caller and the group detail.
 */
@SpringBootTest(properties = {
        "jwt.secret=2b7e151628aed2a6abf7158809cf4f3c2b7e151628aed2a6abf7158809cf4f3c",
        "groups.friendship.stub-friend-emails=friend@example.com,listfriend@example.com"
})
@AutoConfigureMockMvc
@Testcontainers
class GroupControllerIT {

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

    private long userIdOf(String normalizedEmail) {
        return jdbcTemplate.queryForObject("SELECT id FROM users WHERE email = ?", Long.class, normalizedEmail);
    }

    /** Registration of feature 003 → account is verified directly in the DB. */
    private Cookie verifiedSessionOf(String rawEmail) throws Exception {
        mockMvc.perform(multipart("/api/auth/register")
                .part(
                        new MockPart("firstName", "Ana".getBytes(StandardCharsets.UTF_8)),
                        new MockPart("email", rawEmail.getBytes(StandardCharsets.UTF_8)),
                        new MockPart("password", "Passw0rd!".getBytes(StandardCharsets.UTF_8)),
                        new MockPart("confirmPassword", "Passw0rd!".getBytes(StandardCharsets.UTF_8)),
                        new MockPart("acceptTerms", "true".getBytes(StandardCharsets.UTF_8))))
                .andExpect(status().isCreated());
        jdbcTemplate.update(
                "UPDATE users SET email_verified_at = CURRENT_TIMESTAMP WHERE email = ?", rawEmail);
        return new Cookie("amano_auth", jwtService.issueToken(userIdOf(rawEmail)));
    }

    private String postGroup(Cookie session, String name, Map<String, String> memberMap) throws Exception {
        var body = JsonNodeFactory.instance.objectNode();
        body.put("name", name);
        if (memberMap != null) {
            var member = body.putObject("member");
            memberMap.forEach(member::put);
        }
        return mockMvc.perform(post("/api/groups")
                .cookie(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
                .andReturn().getResponse().getContentAsString();
    }

    @Test
    void creationReturnsCreatedGroupWithCreatorAdminAndSeedMember() throws Exception {
        Cookie session = verifiedSessionOf("creator@example.com");
        Cookie friend = verifiedSessionOf("friend@example.com");
        jdbcTemplate.update("UPDATE users SET email_verified_at = CURRENT_TIMESTAMP WHERE email = ?",
                "friend@example.com");

        mockMvc.perform(post("/api/groups")
                .cookie(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "name", "Amigos para viajar",
                        "description", "Viaje al sur",
                        "member", Map.of(
                                "memberType", "REGISTERED",
                                "identifier", "friend@example.com")))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.name").value("Amigos para viajar"))
                .andExpect(jsonPath("$.membersCount").value(2))
                .andExpect(jsonPath("$.creatorRole").value("ADMIN"))
                .andExpect(jsonPath("$.creator.email").value("creator@example.com"));
    }

    @Test
    void creationWithSimulatedProfileSucceeds() throws Exception {
        Cookie session = verifiedSessionOf("simcreator@example.com");

        mockMvc.perform(post("/api/groups")
                .cookie(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "name", "Proyecto X",
                        "member", Map.of(
                                "memberType", "SIMULATED",
                                "identifier", "invitado@example.com",
                                "name", "Invitado de prueba")))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.membersCount").value(2));
    }

    @Test
    void unauthenticatedCreationIsUnauthorized() throws Exception {
        mockMvc.perform(post("/api/groups")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("name", "Grupo"))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void blankNameIsRejected() throws Exception {
        Cookie session = verifiedSessionOf("blank@example.com");

        mockMvc.perform(post("/api/groups")
                .cookie(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("name", "   "))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void creationWithoutAdditionalMemberIsRejected() throws Exception {
        Cookie session = verifiedSessionOf("nomember@example.com");

        mockMvc.perform(post("/api/groups")
                .cookie(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("name", "Solo yo"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void registeredMemberOutsideFriendshipIsNotFound() throws Exception {
        Cookie session = verifiedSessionOf("stranger-creator@example.com");
        verifiedSessionOf("stranger@example.com");

        mockMvc.perform(post("/api/groups")
                .cookie(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "name", "Grupo",
                        "member", Map.of(
                                "memberType", "REGISTERED",
                                "identifier", "stranger@example.com")))))
                .andExpect(status().isNotFound());
    }

    @Test
    void groupsListAndDetailOfTheCaller() throws Exception {
        Cookie session = verifiedSessionOf("lister@example.com");
        verifiedSessionOf("listfriend@example.com");

        String created = postGroup(session, "Lista", Map.of(
                "memberType", "REGISTERED",
                "identifier", "listfriend@example.com"));
        long groupId = objectMapper.readTree(created).get("id").asLong();

        mockMvc.perform(get("/api/groups").cookie(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Lista"))
                .andExpect(jsonPath("$[0].currentUserRole").value("ADMIN"))
                .andExpect(jsonPath("$[0].memberCount").value(2));

        mockMvc.perform(get("/api/groups/" + groupId).cookie(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Lista"))
                .andExpect(jsonPath("$.memberCount").value(2))
                .andExpect(jsonPath("$.currentUserRole").value("ADMIN"))
                .andExpect(jsonPath("$.isActiveForUser").value(true))
                .andExpect(jsonPath("$.permissions.membersCannotMakeExpenses").value(false));

        mockMvc.perform(get("/api/groups/999999").cookie(session))
                .andExpect(status().isNotFound());
    }
}
