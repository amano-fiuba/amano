package com.amano.backend.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;

import com.amano.backend.config.JwtService;
import com.amano.backend.support.AbstractIntegrationIT;

import jakarta.servlet.http.Cookie;
import org.mockito.Mockito;

/** 035 T039: a failed notification persistence must never fail the business flow (FR-032, SC-007). */
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class NotificationResilienceIT extends AbstractIntegrationIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private JwtService jwtService;

    @MockitoSpyBean
    private NotificationRepository notificationRepository;

    private Cookie anaSession;
    private long juan;

    @BeforeEach
    void setUp() throws Exception {
        greenMail.purgeEmailFromAllMailboxes();
        long ana = user("Ana");
        juan = user("Juan");
        anaSession = new Cookie("amano_auth", jwtService.issueToken(ana));
    }

    @Test
    void aNotificationPersistenceFailureDoesNotFailTheFriendshipRequestFlow() throws Exception {
        Mockito.doThrow(new RuntimeException("BD caída"))
                .when(notificationRepository).save(any(Notification.class));

        mockMvc.perform(post("/api/users/me/friendship-requests").cookie(anaSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"recipientId\":" + juan + "}"))
                .andExpect(status().is2xxSuccessful());

        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM friendship_requests WHERE recipient_id = ?", Integer.class, juan))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM notifications", Integer.class)).isZero();
    }

    private long user(String name) {
        return jdbc.queryForObject("""
                INSERT INTO users (first_name, last_name, email, password_hash, terms_accepted_at, email_verified_at)
                VALUES (?, 'Apellido', ?, 'x', now(), now()) RETURNING id
                """, Long.class, name, "resilience-" + UUID.randomUUID() + "@example.com");
    }
}
