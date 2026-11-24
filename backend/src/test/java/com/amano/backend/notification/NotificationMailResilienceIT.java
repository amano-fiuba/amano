package com.amano.backend.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.amano.backend.notification.NotificationEvent.EmailPolicy;
import com.amano.backend.notification.dto.NotificationTargetResponse;
import com.amano.backend.support.AbstractIntegrationIT;

/** 035 T039: an SMTP failure on a critical event must not fail delivery of the in-app notice. */
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class NotificationMailResilienceIT extends AbstractIntegrationIT {

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private NotificationEventPublisher publisher;

    @MockitoBean
    private NotificationMailService mailService;

    private long userId;

    @BeforeEach
    void setUp() {
        userId = jdbc.queryForObject("""
                INSERT INTO users (first_name, last_name, email, password_hash, terms_accepted_at, email_verified_at)
                VALUES ('Ana', 'Apellido', ?, 'x', now(), now()) RETURNING id
                """, Long.class, "mail-resilience-" + UUID.randomUUID() + "@example.com");
    }

    @Test
    void anSmtpFailureDoesNotFailTheCriticalEventDelivery() {
        doThrow(new RuntimeException("SMTP caído")).when(mailService)
                .send(anyString(), any(NotificationType.class), anyString(), anyString());

        assertThatCode(() -> publisher.publishSafely(new NotificationEvent(
                userId, NotificationType.DEBT_OVERDUE, "Vencimiento", "Tenés una deuda vencida",
                new NotificationTargetResponse("DEBT", 42L, 9L, 3L, null), EmailPolicy.ALWAYS)))
                .doesNotThrowAnyException();

        verify(mailService).send(anyString(), any(NotificationType.class), anyString(), anyString());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM notifications", Integer.class)).isEqualTo(1);
    }
}
