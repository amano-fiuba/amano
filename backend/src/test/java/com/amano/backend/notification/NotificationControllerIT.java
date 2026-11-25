package com.amano.backend.notification;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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

/** 035 T019: the five REST endpoints against PostgreSQL with real session cookies. */
class NotificationControllerIT extends AbstractIntegrationIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private JwtService jwtService;

    private long ana;
    private long juan;
    private Cookie anaSession;
    private Cookie juanSession;
    private long unreadRecent;
    private long unreadOld;
    private long readOne;
    private long foreign;

    @BeforeEach
    void setUp() throws Exception {
        greenMail.purgeEmailFromAllMailboxes();
        ana = user("Ana");
        juan = user("Juan");
        anaSession = new Cookie("amano_auth", jwtService.issueToken(ana));
        juanSession = new Cookie("amano_auth", jwtService.issueToken(juan));
        unreadRecent = notice(ana, "GROUP_EXPENSE", "GROUP", null, false, "2026-11-23T10:00:00Z");
        unreadOld = notice(ana, "FRIEND_REQUEST", "FRIEND_REQUEST", null, false, "2026-11-22T10:00:00Z");
        readOne = notice(ana, "REMINDER", "GROUP", null, true, "2026-11-21T10:00:00Z");
        foreign = notice(juan, "DEBT_WHISTLE", "DEBT", null, false, "2026-11-23T09:00:00Z");
    }

    @Test
    void listIsOrderedDescendingAndCarriesTheUnreadCount() throws Exception {
        mockMvc.perform(get("/api/v1/notifications").param("limit", "2").cookie(anaSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.notifications.length()").value(2))
                .andExpect(jsonPath("$.notifications[0].id").value(unreadRecent))
                .andExpect(jsonPath("$.notifications[1].id").value(unreadOld))
                .andExpect(jsonPath("$.unreadCount").value(2))
                .andExpect(jsonPath("$.nextCursor").isString());
    }

    @Test
    void popoverOrderingPutsUnreadFirstEvenWithAnOlderReadNotice() throws Exception {
        mockMvc.perform(get("/api/v1/notifications").param("limit", "5").param("order", "unread-first")
                        .cookie(anaSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.notifications[0].id").value(unreadRecent))
                .andExpect(jsonPath("$.notifications[2].id").value(readOne));
    }

    @Test
    void markReadIsScopedToTheCallerAndAForeignIdIsNeutral404() throws Exception {
        mockMvc.perform(patch("/api/v1/notifications/{id}/read", readOne).cookie(anaSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.readAt").isString());

        mockMvc.perform(patch("/api/v1/notifications/{id}/read", foreign).cookie(anaSession))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("No se encontró la notificación"));

        assertThatUnread(juan, 1);
    }

    @Test
    void markAllReadOnlyTouchesTheCallersRows() throws Exception {
        mockMvc.perform(patch("/api/v1/notifications/read-all").cookie(anaSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.updated").value(2));

        assertThatUnread(ana, 0);
        assertThatUnread(juan, 1);
    }

    @Test
    void preferencesAreCreatedWithDefaultsAndPersistedPerUser() throws Exception {
        mockMvc.perform(get("/api/v1/notifications/preferences").cookie(anaSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.channels.inApp").value(true))
                .andExpect(jsonPath("$.channels.email").value(true))
                .andExpect(jsonPath("$.eventTypes.friendActivity").value(true));

        String body = """
                {"channels":{"inApp":true,"email":false},
                 "eventTypes":{"remindersAndWhistles":true,"debtDueAndPendingCollections":false,
                               "groupActivity":false,"friendActivity":true}}
                """;
        mockMvc.perform(put("/api/v1/notifications/preferences").cookie(anaSession)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.channels.email").value(false))
                .andExpect(jsonPath("$.eventTypes.groupActivity").value(false));

        mockMvc.perform(get("/api/v1/notifications/preferences").cookie(juanSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.channels.email").value(true));

        mockMvc.perform(put("/api/v1/notifications/preferences").cookie(anaSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"channels\":{\"inApp\":true},\"eventTypes\":{}}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$['properties']['fields']['channels.email']").exists());
    }

    @Test
    void requestsWithoutASessionAreRejected() throws Exception {
        mockMvc.perform(get("/api/v1/notifications")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/notifications/preferences")).andExpect(status().isUnauthorized());
        mockMvc.perform(patch("/api/v1/notifications/read-all")).andExpect(status().isUnauthorized());
    }

    @Test
    void anUnverifiedAccountIsRejectedEvenWithAValidCookie() throws Exception {
        long pending = jdbc.queryForObject("""
                INSERT INTO users (first_name, last_name, email, password_hash, terms_accepted_at, email_verified_at)
                VALUES ('Pendiente', 'Apellido', ?, 'x', now(), NULL) RETURNING id
                """, Long.class, "pending-" + UUID.randomUUID() + "@example.com");
        Cookie pendingSession = new Cookie("amano_auth", jwtService.issueToken(pending));

        mockMvc.perform(get("/api/v1/notifications").cookie(pendingSession))
                .andExpect(status().isUnauthorized());
    }

    private void assertThatUnread(long userId, int expected) {
        int actual = jdbc.queryForObject(
                "SELECT COUNT(*) FROM notifications WHERE user_id = ? AND read_at IS NULL", Integer.class, userId);
        org.assertj.core.api.Assertions.assertThat(actual).isEqualTo(expected);
    }

    private long user(String name) {
        return jdbc.queryForObject("""
                INSERT INTO users (first_name, last_name, email, password_hash, terms_accepted_at, email_verified_at)
                VALUES (?, 'Apellido', ?, 'x', now(), now()) RETURNING id
                """, Long.class, name, "notif-" + UUID.randomUUID() + "@example.com");
    }

    private long notice(long userId, String type, String kind, Long groupId, boolean read, String createdAt) {
        return jdbc.queryForObject("""
                INSERT INTO notifications (user_id, type, title, message, target_kind, group_id, created_at, read_at)
                VALUES (?, ?, 'Título', 'Mensaje de prueba', ?, ?, ?::timestamptz, ?::timestamptz)
                RETURNING id
                """, Long.class, userId, type, kind, groupId, createdAt,
                read ? createdAt : null);
    }
}
