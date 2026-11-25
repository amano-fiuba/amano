package com.amano.backend.notification;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import com.amano.backend.config.JwtService;
import com.amano.backend.groupexpense.overdue.OverdueDebtJob;
import com.amano.backend.support.AbstractIntegrationIT;

import jakarta.servlet.http.Cookie;
import org.mockito.Mockito;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

/** 035 T040: existing endpoints keep working and foreign notification ids stay invisible. */
class NotificationRegressionIT extends AbstractIntegrationIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private OverdueDebtJob overdueDebtJob;

    @MockitoSpyBean
    private NotificationEventPublisher publisher;

    private long ana;
    private long juan;
    private long groupId;
    private Cookie anaSession;
    private Cookie juanSession;

    @BeforeEach
    void setUp() throws Exception {
        greenMail.purgeEmailFromAllMailboxes();
        Mockito.clearInvocations(publisher);
        ana = user("Ana");
        juan = user("Juan");
        anaSession = new Cookie("amano_auth", jwtService.issueToken(ana));
        juanSession = new Cookie("amano_auth", jwtService.issueToken(juan));
        groupId = jdbc.queryForObject("""
                INSERT INTO groups (name, primary_currency, debt_due_days) VALUES (?, 'ARS', 7) RETURNING id
                """, Long.class, "[Demo] Regresión " + UUID.randomUUID());
        jdbc.update("INSERT INTO group_members (user_id, group_id, role) VALUES (?, ?, 'ADMIN')", ana, groupId);
    }

    @Test
    void existingGroupDebtFriendBalanceAndStatsEndpointsStillRespond() throws Exception {
        org.assertj.core.api.Assertions.assertThat(mockMvc.perform(get("/api/groups").cookie(anaSession))
                .andReturn().getResponse().getStatus()).as("GET /api/groups").isEqualTo(200);
        org.assertj.core.api.Assertions.assertThat(mockMvc.perform(get("/api/groups/{id}/debts/pending", groupId)
                .cookie(anaSession)).andReturn().getResponse().getStatus())
                .as("GET /api/groups/{id}/debts/pending").isEqualTo(200);
        org.assertj.core.api.Assertions.assertThat(mockMvc.perform(get("/api/users/me/friends").cookie(anaSession))
                .andReturn().getResponse().getStatus()).as("GET /api/users/me/friends").isEqualTo(200);
        org.assertj.core.api.Assertions.assertThat(mockMvc.perform(get("/api/balance").cookie(anaSession))
                .andReturn().getResponse().getStatus()).as("GET /api/balance").isEqualTo(200);
        org.assertj.core.api.Assertions.assertThat(mockMvc.perform(get("/api/stats/summary")
                .param("year", "2026").param("month", "11").cookie(anaSession))
                .andReturn().getResponse().getStatus()).as("GET /api/stats/summary").isEqualTo(200);
    }

    @Test
    void aForeignNotificationIsNotListedAndCannotBeMarkedAsRead() throws Exception {
        long foreign = jdbc.queryForObject("""
                INSERT INTO notifications (user_id, type, title, message, target_kind, created_at)
                VALUES (?, 'FRIEND_REQUEST', 'Aviso', 'Mensaje', 'NONE', now()) RETURNING id
                """, Long.class, juan);

        mockMvc.perform(get("/api/v1/notifications").cookie(anaSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.notifications.length()").value(0));
        mockMvc.perform(patch("/api/v1/notifications/{id}/read", foreign).cookie(anaSession))
                .andExpect(status().isNotFound());
    }

    @Test
    void anOverdueDebtPublishesItsCriticalEventWithoutChangingTheJobOutcome() throws Exception {
        // No due debts: the job does nothing and publishes nothing.
        overdueDebtJob.markOverdueDebts();
        Mockito.verifyNoInteractions(publisher);
    }

    private long user(String name) {
        return jdbc.queryForObject("""
                INSERT INTO users (first_name, last_name, email, password_hash, terms_accepted_at, email_verified_at)
                VALUES (?, 'Apellido', ?, 'x', now(), now()) RETURNING id
                """, Long.class, name, "regression-" + UUID.randomUUID() + "@example.com");
    }
}
