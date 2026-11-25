package com.amano.backend.notification;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import com.amano.backend.config.JwtService;
import com.amano.backend.support.AbstractIntegrationIT;

import jakarta.servlet.http.Cookie;

/** 035 T038: preferences CRUD, defaults and per-user isolation against PostgreSQL. */
class NotificationPreferenceIT extends AbstractIntegrationIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private JwtService jwtService;

    private Cookie anaSession;
    private Cookie juanSession;

    @BeforeEach
    void setUp() throws Exception {
        greenMail.purgeEmailFromAllMailboxes();
        long ana = user("Ana");
        long juan = user("Juan");
        anaSession = new Cookie("amano_auth", jwtService.issueToken(ana));
        juanSession = new Cookie("amano_auth", jwtService.issueToken(juan));
    }

    @Test
    void firstReadCreatesAllSixFlagsEnabledAndPersistsThem() throws Exception {
        mockMvc.perform(get("/api/v1/notifications/preferences").cookie(anaSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.channels.inApp").value(true))
                .andExpect(jsonPath("$.channels.email").value(true))
                .andExpect(jsonPath("$.eventTypes.remindersAndWhistles").value(true))
                .andExpect(jsonPath("$.eventTypes.debtDueAndPendingCollections").value(true))
                .andExpect(jsonPath("$.eventTypes.groupActivity").value(true))
                .andExpect(jsonPath("$.eventTypes.friendActivity").value(true));

        String body = """
                {"channels":{"inApp":false,"email":true},
                 "eventTypes":{"remindersAndWhistles":false,"debtDueAndPendingCollections":true,
                               "groupActivity":false,"friendActivity":true}}
                """;
        mockMvc.perform(put("/api/v1/notifications/preferences").cookie(anaSession)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.channels.inApp").value(false))
                .andExpect(jsonPath("$.eventTypes.groupActivity").value(false));

        mockMvc.perform(get("/api/v1/notifications/preferences").cookie(anaSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.channels.inApp").value(false))
                .andExpect(jsonPath("$.eventTypes.remindersAndWhistles").value(false));
    }

    @Test
    void preferencesAreIsolatedPerUser() throws Exception {
        String body = """
                {"channels":{"inApp":false,"email":false},
                 "eventTypes":{"remindersAndWhistles":true,"debtDueAndPendingCollections":true,
                               "groupActivity":true,"friendActivity":true}}
                """;
        mockMvc.perform(put("/api/v1/notifications/preferences").cookie(anaSession)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/notifications/preferences").cookie(juanSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.channels.inApp").value(true))
                .andExpect(jsonPath("$.channels.email").value(true));
    }

    @Test
    void aMissingBooleanIsRejectedWithPerFieldDetailsWithoutWriting() throws Exception {
        int before = jdbc.queryForObject(
                "SELECT COUNT(*) FROM user_notification_preferences", Integer.class);
        mockMvc.perform(put("/api/v1/notifications/preferences").cookie(anaSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"channels\":{\"inApp\":true},\"eventTypes\":{}}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$['properties']['fields']['channels.email']").exists());

        org.assertj.core.api.Assertions.assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM user_notification_preferences", Integer.class)).isEqualTo(before);
    }

    private long user(String name) {
        return jdbc.queryForObject("""
                INSERT INTO users (first_name, last_name, email, password_hash, terms_accepted_at, email_verified_at)
                VALUES (?, 'Apellido', ?, 'x', now(), now()) RETURNING id
                """, Long.class, name, "prefs-" + UUID.randomUUID() + "@example.com");
    }
}
