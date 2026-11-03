package com.amano.backend.groups;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import com.amano.backend.common.FieldValidationException;
import com.amano.backend.groups.dto.ActivityHistoryResponse;
import com.amano.backend.user.User;
import com.amano.backend.user.UserRepository;

import tools.jackson.databind.json.JsonMapper;

/**
 * 014 (T020): read side of the history — filter by one or more types, 400 on
 * an unknown type, size cap, actors resolved in one query, invalid stored
 * details degrade to null, 404 for non members and 403 without permission.
 */
class GroupActivityServiceTest {

    private static final Long GROUP_ID = 42L;
    private static final Long CALLER_ID = 7L;

    private GroupActivityRepository activityRepository;
    private UserRepository userRepository;
    private GroupMemberRepository groupMemberRepository;
    private GroupPermissionChecker permissionChecker;
    private GroupPermissionsRepository groupPermissionsRepository;
    private GroupActivityService service;

    @BeforeEach
    void setUp() {
        activityRepository = Mockito.mock(GroupActivityRepository.class);
        userRepository = Mockito.mock(UserRepository.class);
        groupMemberRepository = Mockito.mock(GroupMemberRepository.class);
        permissionChecker = Mockito.mock(GroupPermissionChecker.class);
        groupPermissionsRepository = Mockito.mock(GroupPermissionsRepository.class);
        service = new GroupActivityService(activityRepository, userRepository, groupMemberRepository,
                permissionChecker, groupPermissionsRepository, JsonMapper.builder().build());

        when(groupMemberRepository.findByIdGroupIdAndIdUserId(GROUP_ID, CALLER_ID))
                .thenReturn(Optional.of(new GroupMember(CALLER_ID, GROUP_ID, GroupMember.ROLE_MEMBER)));
        when(groupPermissionsRepository.findByGroupId(GROUP_ID)).thenReturn(Optional.empty());
        when(permissionChecker.canViewGroup(any(), any())).thenReturn(true);
        when(activityRepository.findByGroupIdOrderByCreatedAtDescIdDesc(eq(GROUP_ID), any(Pageable.class)))
                .thenAnswer(invocation -> new PageImpl<>(List.of(), invocation.getArgument(1), 0));
        when(activityRepository.findByGroupIdAndEventTypeInOrderByCreatedAtDescIdDesc(eq(GROUP_ID),
                anyCollection(), any(Pageable.class)))
                .thenAnswer(invocation -> new PageImpl<>(List.of(), invocation.getArgument(2), 0));
    }

    private static GroupActivity event(Long id, String type, Long actorId, String details) {
        GroupActivity event = new GroupActivity(GROUP_ID, GroupActivity.GroupActivityType.valueOf(type),
                actorId, details);
        ReflectionTestUtils.setField(event, "id", id);
        ReflectionTestUtils.setField(event, "createdAt", OffsetDateTime.parse("2026-11-03T10:00:00Z"));
        return event;
    }

    private static User user(Long id, String firstName, String lastName) {
        User user = new User();
        ReflectionTestUtils.setField(user, "id", id);
        user.setFirstName(firstName);
        user.setLastName(lastName);
        return user;
    }

    @Test
    void withoutFilterUsesDefaultsAndResolvesActorsInASingleQuery() {
        Page<GroupActivity> page = new PageImpl<>(List.of(
                event(3L, "MEMBER_REMOVED", CALLER_ID, "{\"type\":\"REGISTERED\",\"name\":\"Juan\"}"),
                event(2L, "MEMBER_ADDED", CALLER_ID, "{\"type\":\"SIMULATED\"}"),
                event(1L, "MEMBER_LEFT", 99L, null)));
        when(activityRepository.findByGroupIdOrderByCreatedAtDescIdDesc(eq(GROUP_ID), any(Pageable.class)))
                .thenReturn(page);
        when(userRepository.findAllById(any())).thenReturn(List.of(user(CALLER_ID, "Ana", "López")));

        ActivityHistoryResponse response = service.getHistory(CALLER_ID, GROUP_ID, null, null, null);

        assertThat(response.getPage()).isEqualTo(1);
        assertThat(response.getSize()).isEqualTo(GroupActivityService.DEFAULT_SIZE);
        assertThat(response.getTotalElements()).isEqualTo(3);
        assertThat(response.getContent()).extracting(ActivityHistoryResponse.Entry::getId)
                .containsExactly(3L, 2L, 1L);
        ActivityHistoryResponse.Entry first = response.getContent().get(0);
        assertThat(first.getActor().getFirstName()).isEqualTo("Ana");
        assertThat(first.getDetails().get("name").asString()).isEqualTo("Juan");
        assertThat(first.getCreatedAt()).isNotNull();
        // actor 99 does not exist anymore → null actor; missing details → null
        assertThat(response.getContent().get(2).getActor()).isNull();
        assertThat(response.getContent().get(2).getDetails()).isNull();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Iterable<Long>> ids = ArgumentCaptor.forClass(Iterable.class);
        verify(userRepository, times(1)).findAllById(ids.capture());
        assertThat(ids.getValue()).containsExactly(CALLER_ID, 99L);
        verify(userRepository, never()).findById(any());
    }

    @Test
    void invalidOrNonObjectStoredDetailsDegradeToNull() {
        when(activityRepository.findByGroupIdOrderByCreatedAtDescIdDesc(eq(GROUP_ID), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(
                        event(2L, "MEMBER_ADDED", null, "{\"name\":\"Abuela \"Tita\"\"}"),
                        event(1L, "MEMBER_ADDED", null, "[1,2]"))));

        ActivityHistoryResponse response = service.getHistory(CALLER_ID, GROUP_ID, 1, 20, "  ");

        assertThat(response.getContent()).extracting(ActivityHistoryResponse.Entry::getDetails)
                .containsOnlyNulls();
        assertThat(response.getContent()).extracting(ActivityHistoryResponse.Entry::getActor)
                .containsOnlyNulls();
        verify(userRepository, never()).findAllById(any());
    }

    @Test
    void severalCommaSeparatedTypesAreNormalizedAndQueriedTogether() {
        service.getHistory(CALLER_ID, GROUP_ID, 2, 5, " member_removed , MEMBER_LEFT,,member_removed");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Collection<String>> types = ArgumentCaptor.forClass(Collection.class);
        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(activityRepository).findByGroupIdAndEventTypeInOrderByCreatedAtDescIdDesc(eq(GROUP_ID),
                types.capture(), pageable.capture());
        assertThat(types.getValue()).containsExactly("MEMBER_REMOVED", "MEMBER_LEFT");
        assertThat(pageable.getValue().getPageNumber()).isEqualTo(1);
        assertThat(pageable.getValue().getPageSize()).isEqualTo(5);
    }

    @Test
    void unknownEventTypeIsAFieldValidationError() {
        FieldValidationException exception = catchThrowableOfType(
                () -> service.getHistory(CALLER_ID, GROUP_ID, 1, 20, "MEMBER_ADDED,NOPE"),
                FieldValidationException.class);

        assertThat(exception.getFields()).containsEntry("eventType",
                GroupActivityService.INVALID_EVENT_TYPE_MESSAGE);
        verify(activityRepository, never()).findByGroupIdAndEventTypeInOrderByCreatedAtDescIdDesc(any(),
                anyCollection(), any());
    }

    @Test
    void pageSizeIsCappedAndInvalidValuesFallBackToDefaults() {
        ActivityHistoryResponse capped = service.getHistory(CALLER_ID, GROUP_ID, 1, 500, null);
        assertThat(capped.getSize()).isEqualTo(GroupActivityService.MAX_SIZE);

        ActivityHistoryResponse defaults = service.getHistory(CALLER_ID, GROUP_ID, 0, 0, null);
        assertThat(defaults.getPage()).isEqualTo(1);
        assertThat(defaults.getSize()).isEqualTo(GroupActivityService.DEFAULT_SIZE);
    }

    @Test
    void nonMemberOrFormerMemberGetsTheNeutralNotFound() {
        when(groupMemberRepository.findByIdGroupIdAndIdUserId(GROUP_ID, 8L)).thenReturn(Optional.empty());
        GroupMember former = new GroupMember(9L, GROUP_ID, GroupMember.ROLE_MEMBER);
        former.setLeftAt(OffsetDateTime.now());
        when(groupMemberRepository.findByIdGroupIdAndIdUserId(GROUP_ID, 9L)).thenReturn(Optional.of(former));

        assertThat(catchThrowableOfType(() -> service.getHistory(8L, GROUP_ID, 1, 20, null),
                GroupNotFoundException.class)).isNotNull();
        assertThat(catchThrowableOfType(() -> service.getHistory(9L, GROUP_ID, 1, 20, null),
                GroupNotFoundException.class)).isNotNull();
    }

    @Test
    void memberWithoutViewPermissionIsForbidden() {
        when(permissionChecker.canViewGroup(any(), any())).thenReturn(false);

        assertThat(catchThrowableOfType(() -> service.getHistory(CALLER_ID, GROUP_ID, 1, 20, null),
                GroupPermissionDeniedException.class)).isNotNull();
    }
}
