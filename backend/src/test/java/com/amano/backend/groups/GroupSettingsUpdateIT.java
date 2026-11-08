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
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import com.amano.backend.config.JwtService;

import jakarta.servlet.http.Cookie;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.JsonNodeFactory;

/**
 * US2 contract tests (T035, Testcards / real PostgreSQL): {@code PUT
 * /api/groups/{id}} saves the permission casillas (200 with the updated
 * state), denies a MEMBER when members_can_edit_group_data = false (403) and
 * reports unknown groups (404).
 */
@SpringBootTest(properties = {
        "jwt.secret=2b7e151628aed2a6abf7158809cf4f3c2b7e151628aed2a6abf7158809cf4f3c"
})
@AutoConfigureMockMvc
@Testcontainers
class GroupSettingsUpdateIT {

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
                .andExpect(status().isCreated());
        jdbcTemplate.update(
                "UPDATE users SET email_verified_at = CURRENT_TIMESTAMP WHERE email = ?", rawEmail);
        FriendshipFixtures.link(jdbcTemplate, rawEmail, FRIEND_FIXTURES);
        return new Cookie("amano_auth", jwtService.issueToken(userIdOf(rawEmail)));
    }

    private void registeredFriend() throws Exception {
        mockMvc.perform(multipart("/api/auth/register")
                .part(
                        new MockPart("firstName", "Beto".getBytes(StandardCharsets.UTF_8)),
                        new MockPart("email", "friend@example.com".getBytes(StandardCharsets.UTF_8)),
                        new MockPart("password", "Passw0rd!".getBytes(StandardCharsets.UTF_8)),
                        new MockPart("confirmPassword", "Passw0rd!".getBytes(StandardCharsets.UTF_8)),
                        new MockPart("acceptTerms", "true".getBytes(StandardCharsets.UTF_8))))
                .andExpect(result -> assertThat(result.getResponse().getStatus()).isIn(201, 409));
        jdbcTemplate.update(
                "UPDATE users SET email_verified_at = CURRENT_TIMESTAMP WHERE email = ?",
                "friend@example.com");
        FriendshipFixtures.link(jdbcTemplate, "friend@example.com", FRIEND_FIXTURES);
    }

    private long userIdOf(String normalizedEmail) {
        return jdbcTemplate.queryForObject("SELECT id FROM users WHERE email = ?", Long.class, normalizedEmail);
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
    void adminUpdatesPermissionsAndSeesTheSavedState() throws Exception {
        Cookie admin = verifiedSessionOf("admin-settings@example.com");
        registeredFriend();
        long groupId = createGroup(admin);

        mockMvc.perform(put("/api/groups/" + groupId).cookie(admin)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"permissions\": {\"membersCannotInvite\": true,"
                        + " \"membersCanEditGroupData\": true,"
                        + " \"membersCanManageSimulatedProfiles\": true,"
                        + " \"paymentsRequireValidation\": true}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.permissions.membersCannotInvite").value(true))
                .andExpect(jsonPath("$.permissions.membersCanEditGroupData").value(true))
                .andExpect(jsonPath("$.permissions.membersCanManageSimulatedProfiles").value(true))
                .andExpect(jsonPath("$.permissions.paymentsRequireValidation").value(true))
                .andExpect(jsonPath("$.permissions.membersCannotMakeExpenses").value(false))
                // 022 T060: untouched by this partial update, so it keeps the new default (true)
                .andExpect(jsonPath("$.permissions.membersCanGenerateExpensesForOthers").value(true));

        mockMvc.perform(get("/api/groups/" + groupId).cookie(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.permissions.membersCannotInvite").value(true));
    }

    @Test
    void memberIsForbiddenAfterTheAdminTurnsOnTheCasilla() throws Exception {
        Cookie admin = verifiedSessionOf("member-denied-admin@example.com");
        registeredFriend();
        long groupId = createGroup(admin);

        mockMvc.perform(put("/api/groups/" + groupId).cookie(admin)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"permissions\": {\"membersCanEditGroupData\": false}}"))
                .andExpect(status().isOk());

        long memberId = jdbcTemplate
                .queryForObject("SELECT user_id FROM group_members WHERE group_id = ? AND role = 'MEMBER'",
                        Long.class, groupId);
        Cookie member = new Cookie("amano_auth", jwtService.issueToken(memberId));

        mockMvc.perform(put("/api/groups/" + groupId).cookie(member)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"description\": \"Intento inválido\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value("No podés editar los datos de este grupo"));
    }

    @Test
    void memberCanEditGroupDataOnceTheAdminAllowsIt() throws Exception {
        Cookie admin = verifiedSessionOf("member-ok-admin@example.com");
        registeredFriend();
        long groupId = createGroup(admin);

        mockMvc.perform(put("/api/groups/" + groupId).cookie(admin)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"permissions\": {\"membersCanEditGroupData\": true}}"))
                .andExpect(status().isOk());

        long memberId = jdbcTemplate
                .queryForObject("SELECT user_id FROM group_members WHERE group_id = ? AND role = 'MEMBER'",
                        Long.class, groupId);
        Cookie member = new Cookie("amano_auth", jwtService.issueToken(memberId));

        mockMvc.perform(put("/api/groups/" + groupId).cookie(member)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"permissions\": {\"membersCannotInvite\": true}}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(put("/api/groups/" + groupId).cookie(member)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"description\": \"Descripción del grupo\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.description").value("Descripción del grupo"));
    }

    @Test
    void unknownGroupIsNotFound() throws Exception {
        Cookie admin = verifiedSessionOf("settings-404-admin@example.com");

        mockMvc.perform(put("/api/groups/999999").cookie(admin)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\": \"X\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void unauthenticatedUpdateIsUnauthorized() throws Exception {
        mockMvc.perform(put("/api/groups/1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
                .andExpect(status().isUnauthorized());
    }

    // ---- 022 T078: debtDueDays ----

    private ResultActions putGroup(Cookie session, long groupId, String body) throws Exception {
        return mockMvc.perform(put("/api/groups/" + groupId).cookie(session)
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private Integer storedDebtDueDays(long groupId) {
        return jdbcTemplate.queryForObject("SELECT debt_due_days FROM groups WHERE id = ?", Integer.class, groupId);
    }

    @Test
    void t078AdminSetsKeepsAndClearsTheDebtDueDays() throws Exception {
        Cookie admin = verifiedSessionOf("due-days-admin@example.com");
        registeredFriend();
        long groupId = createGroup(admin);

        mockMvc.perform(get("/api/groups/" + groupId).cookie(admin))
                .andExpect(jsonPath("$.debtDueDays").value(org.hamcrest.Matchers.nullValue()));

        putGroup(admin, groupId, "{\"debtDueDays\": 15}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.debtDueDays").value(15));
        assertThat(storedDebtDueDays(groupId)).isEqualTo(15);
        mockMvc.perform(get("/api/groups/" + groupId).cookie(admin))
                .andExpect(jsonPath("$.debtDueDays").value(15));

        // omitted → unchanged; 0 is valid
        putGroup(admin, groupId, "{\"description\": \"Otra\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.debtDueDays").value(15));
        putGroup(admin, groupId, "{\"debtDueDays\": 0}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.debtDueDays").value(0));

        // explicit null → never overdue
        putGroup(admin, groupId, "{\"debtDueDays\": null}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.debtDueDays").value(org.hamcrest.Matchers.nullValue()));
        assertThat(storedDebtDueDays(groupId)).isNull();
    }

    @Test
    void t078InvalidDebtDueDaysAnswer400AndKeepTheStoredValue() throws Exception {
        Cookie admin = verifiedSessionOf("due-days-invalid@example.com");
        registeredFriend();
        long groupId = createGroup(admin);
        putGroup(admin, groupId, "{\"debtDueDays\": 10}").andExpect(status().isOk());

        for (String bad : new String[] {"-1", "3651"}) {
            putGroup(admin, groupId, "{\"debtDueDays\": " + bad + "}")
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.properties.fields.debtDueDays")
                            .value("Los días para el vencimiento tienen que ser un número entero entre 0 y 3650"));
        }
        assertThat(storedDebtDueDays(groupId)).isEqualTo(10);
    }

    @Test
    void t078OnlyAnAdminCanConfigureTheDebtDueDays() throws Exception {
        Cookie admin = verifiedSessionOf("due-days-member-admin@example.com");
        registeredFriend();
        long groupId = createGroup(admin);
        putGroup(admin, groupId, "{\"permissions\": {\"membersCanEditGroupData\": true}}")
                .andExpect(status().isOk());
        long memberId = jdbcTemplate
                .queryForObject("SELECT user_id FROM group_members WHERE group_id = ? AND role = 'MEMBER'",
                        Long.class, groupId);
        Cookie member = new Cookie("amano_auth", jwtService.issueToken(memberId));

        putGroup(member, groupId, "{\"debtDueDays\": 5}")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail")
                        .value("Solo un administrador puede configurar el vencimiento de las deudas"));
        assertThat(storedDebtDueDays(groupId)).isNull();
    }
}
