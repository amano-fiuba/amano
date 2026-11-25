package com.amano.backend.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import com.amano.backend.notification.dto.NotificationListResponse;

/** 035 T018: inbox ordering, cursor, counter, idempotence and isolation. */
@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    private static final long USER = 7L;
    private static final Instant NOW = Instant.parse("2026-11-23T12:00:00Z");

    @Mock
    private NotificationRepository repository;

    private NotificationService service;

    @BeforeEach
    void setUp() {
        service = new NotificationService(repository, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void historyIsNewestFirstAndCarriesTheTotalUnreadCount() {
        Notification first = notice(11L, NotificationType.GROUP_EXPENSE, false);
        Notification second = notice(10L, NotificationType.FRIEND_REQUEST, true);
        when(repository.findByUserIdOrderByCreatedAtDescIdDesc(eq(USER), any(Pageable.class)))
                .thenReturn(List.of(first, second));
        when(repository.countByUserIdAndReadAtIsNull(USER)).thenReturn(1L);

        NotificationListResponse result = service.list(USER, 20, null, "history");

        assertThat(result.unreadCount()).isEqualTo(1);
        assertThat(result.nextCursor()).isNull();
        assertThat(result.notifications()).extracting(n -> n.id()).containsExactly(11L, 10L);
        assertThat(result.notifications().get(0).target().kind()).isEqualTo("GROUP");
        assertThat(result.notifications().get(1).target().kind()).isEqualTo("FRIEND_REQUEST");
    }

    @Test
    void popoverUsesUnreadFirstAndIgnoresAnIncomingCursor() {
        when(repository.findUnreadFirst(eq(USER), any(Pageable.class)))
                .thenReturn(List.of(notice(12L, NotificationType.REMINDER, false)));
        when(repository.countByUserIdAndReadAtIsNull(USER)).thenReturn(3L);

        NotificationListResponse result = service.list(USER, 5, "cursor-de-otra-lista", "unread-first");

        assertThat(result.notifications()).hasSize(1);
        verify(repository).findUnreadFirst(eq(USER), any(Pageable.class));
        verify(repository, never()).findHistoryBefore(any(), any(), any(), any());
        ArgumentCaptor<Pageable> page = ArgumentCaptor.forClass(Pageable.class);
        verify(repository).findUnreadFirst(eq(USER), page.capture());
        assertThat(page.getValue().getPageSize()).isEqualTo(6);
    }

    @Test
    void anInvalidCursorFallsBackToTheFirstPage() {
        when(repository.findByUserIdOrderByCreatedAtDescIdDesc(eq(USER), any(Pageable.class)))
                .thenReturn(List.of(notice(11L, NotificationType.PAYMENT_RECORDED, true)));
        when(repository.countByUserIdAndReadAtIsNull(USER)).thenReturn(0L);

        service.list(USER, 20, "%%%no-es-base64%%%", "history");

        verify(repository).findByUserIdOrderByCreatedAtDescIdDesc(eq(USER), any(Pageable.class));
        verify(repository, never()).findHistoryBefore(any(), any(), any(), any());
    }

    @Test
    void aFullPageProducesAnOpaqueCursorForTheNextRequest() {
        Notification first = notice(11L, NotificationType.FRIEND_ACTIVITY, true);
        Notification second = notice(10L, NotificationType.DEBT_WHISTLE, true);
        Notification third = notice(9L, NotificationType.DEBT_WHISTLE, true);
        when(repository.findByUserIdOrderByCreatedAtDescIdDesc(eq(USER), any(Pageable.class)))
                .thenReturn(List.of(first, second, third));
        when(repository.countByUserIdAndReadAtIsNull(USER)).thenReturn(3L);

        NotificationListResponse result = service.list(USER, 2, null, "history");

        assertThat(result.notifications()).extracting(n -> n.id()).containsExactly(11L, 10L);
        assertThat(result.nextCursor()).isNotBlank();

        when(repository.findHistoryBefore(eq(USER), eq(second.getCreatedAt()), eq(second.getId()),
                any(Pageable.class))).thenReturn(List.of(third));
        service.list(USER, 2, result.nextCursor(), "history");
        verify(repository).findHistoryBefore(eq(USER), eq(second.getCreatedAt()), eq(second.getId()),
                any(Pageable.class));
    }

    @Test
    void limitIsClampedToTheAllowedRange() {
        when(repository.findByUserIdOrderByCreatedAtDescIdDesc(eq(USER), any(Pageable.class)))
                .thenReturn(List.of());
        when(repository.countByUserIdAndReadAtIsNull(USER)).thenReturn(0L);

        service.list(USER, 500, null, "history");
        service.list(USER, -3, null, "history");

        ArgumentCaptor<Pageable> pages = ArgumentCaptor.forClass(Pageable.class);
        verify(repository, org.mockito.Mockito.times(2))
                .findByUserIdOrderByCreatedAtDescIdDesc(eq(USER), pages.capture());
        assertThat(pages.getAllValues()).extracting(Pageable::getPageSize).containsExactly(101, 2);
    }

    @Test
    void markReadIsIdempotentAndScopedToTheOwner() {
        Notification alreadyRead = notice(11L, NotificationType.REMINDER, true);
        when(repository.findByIdAndUserId(11L, USER)).thenReturn(Optional.of(alreadyRead));
        when(repository.save(any(Notification.class))).thenAnswer(invocation -> invocation.getArgument(0));

        assertThat(service.markRead(USER, 11L).readAt()).isEqualTo(alreadyRead.getReadAt());
        assertThat(alreadyRead.getReadAt()).isEqualTo(OffsetDateTime.ofInstant(NOW, ZoneOffset.UTC).minusDays(1));
    }

    @Test
    void markReadOfAForeignOrMissingIdIsNotFound() {
        when(repository.findByIdAndUserId(99L, USER)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.markRead(USER, 99L))
                .isInstanceOf(NotificationNotFoundException.class);
        verify(repository, never()).save(any());
    }

    @Test
    void markAllReadReturnsOnlyTheRowsChangedForThisUser() {
        when(repository.markAllRead(USER, OffsetDateTime.ofInstant(NOW, ZoneOffset.UTC))).thenReturn(4);

        assertThat(service.markAllRead(USER)).isEqualTo(4);
    }

    private static Notification notice(long id, NotificationType type, boolean read) {
        Notification notification = new Notification();
        notification.setId(id);
        notification.setUserId(USER);
        notification.setType(type);
        notification.setTitle("Aviso " + id);
        notification.setMessage("Texto del aviso");
        notification.setTargetKind(type == NotificationType.FRIEND_REQUEST
                ? NotificationTargetKind.FRIEND_REQUEST : NotificationTargetKind.GROUP);
        notification.setGroupId(42L);
        notification.setFriendRequestId(9L);
        notification.setCreatedAt(OffsetDateTime.ofInstant(NOW, ZoneOffset.UTC).minusMinutes(id));
        if (read) {
            notification.markRead(OffsetDateTime.ofInstant(NOW, ZoneOffset.UTC).minusDays(1));
        }
        return notification;
    }
}
