package com.amano.backend.groups;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import com.amano.backend.groups.port.DebtPort;

/**
 * Unit tests of {@link GroupService#leaveGroup} (T063, US5): success leaves
 * the group (soft baja, MEMBER_LEFT event), blocked by pending balances, blocked
 * by the last-admin guard, the group persists for remaining members, and there
 * is no path for simulated profiles to leave (no service method accepts them).
 */
class GroupLeaveServiceTest {

    private static final Long USER_ID = 7L;
    private static final Long OTHER_ID = 9L;
    private static final Long GROUP_ID = 42L;

    private GroupRepository groupRepository;
    private GroupMemberRepository groupMemberRepository;
    private SimulatedProfileRepository simulatedProfileRepository;
    private GroupPermissionsRepository groupPermissionsRepository;
    private com.amano.backend.groups.port.FriendshipPort friendshipPort;
    private com.amano.backend.user.UserRepository userRepository;
    private GroupPermissionChecker permissionChecker;
    private ActivityEventService activityEventService;
    private com.amano.backend.groups.port.DebtPort debtPort;
    private GroupService service;

    @BeforeEach
    void setUp() {
        groupRepository = Mockito.mock(GroupRepository.class);
        groupMemberRepository = Mockito.mock(GroupMemberRepository.class);
        simulatedProfileRepository = Mockito.mock(SimulatedProfileRepository.class);
        groupPermissionsRepository = Mockito.mock(GroupPermissionsRepository.class);
        friendshipPort = Mockito.mock(com.amano.backend.groups.port.FriendshipPort.class);
        userRepository = Mockito.mock(com.amano.backend.user.UserRepository.class);
        permissionChecker = Mockito.mock(GroupPermissionChecker.class);
        when(permissionChecker.canViewGroup(Mockito.anyString(), any())).thenReturn(true);
        activityEventService = Mockito.mock(ActivityEventService.class);
        debtPort = Mockito.mock(DebtPort.class);

        service = new GroupService(groupRepository, groupMemberRepository, simulatedProfileRepository,
                groupPermissionsRepository, friendshipPort, userRepository, permissionChecker,
                activityEventService, debtPort,
                Mockito.mock(com.amano.backend.user.UserAvatarRepository.class),
                Mockito.mock(GroupPhotoRepository.class),
                Mockito.mock(com.amano.backend.user.AvatarValidator.class));

        Group group = new Group();
        org.springframework.test.util.ReflectionTestUtils.setField(group, "id", GROUP_ID);
        group.setName("Departamento 2026");
        when(groupRepository.findById(GROUP_ID)).thenReturn(Optional.of(group));
        when(groupMemberRepository.save(any(GroupMember.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void userWithoutBalancesLeavesTheGroupAndTheRowIsSoftDeleted() {
        when(groupMemberRepository.findByIdGroupIdAndIdUserId(GROUP_ID, USER_ID))
                .thenReturn(Optional.of(new GroupMember(USER_ID, GROUP_ID, GroupMember.ROLE_MEMBER)));
        when(debtPort.hasPendingDebtsForUser(GROUP_ID, USER_ID)).thenReturn(false);

        com.amano.backend.groups.dto.LeaveGroupResponse response = service.leaveGroup(USER_ID, GROUP_ID);

        assertThat(response.isGroupLeft()).isTrue();
        verify(groupMemberRepository).save(any(GroupMember.class));
        verify(activityEventService).record(eq(GROUP_ID),
                eq(GroupActivity.GroupActivityType.MEMBER_LEFT), eq(USER_ID), any());
    }

    @Test
    void leavingIsBlockedByPendingBalances() {
        when(groupMemberRepository.findByIdGroupIdAndIdUserId(GROUP_ID, USER_ID))
                .thenReturn(Optional.of(new GroupMember(USER_ID, GROUP_ID, GroupMember.ROLE_MEMBER)));
        when(debtPort.hasPendingDebtsForUser(GROUP_ID, USER_ID)).thenReturn(true);

        var exception = catchThrowableOfType(() -> service.leaveGroup(USER_ID, GROUP_ID),
                PendingDebtsException.class);

        assertThat(exception).isNotNull();
        assertThat(exception.getMessage()).contains("saldos pendientes");
        Mockito.verifyNoInteractions(activityEventService);
    }

    @Test
    void theLastAdminCannotLeave() {
        when(groupMemberRepository.findByIdGroupIdAndIdUserId(GROUP_ID, USER_ID))
                .thenReturn(Optional.of(new GroupMember(USER_ID, GROUP_ID, GroupMember.ROLE_ADMIN)));
        when(groupMemberRepository.countActiveAdminsByGroupId(GROUP_ID)).thenReturn(1L);
        when(groupMemberRepository.countActiveByGroupId(GROUP_ID)).thenReturn(2L);
        when(debtPort.hasPendingDebtsForUser(GROUP_ID, USER_ID)).thenReturn(false);

        var exception = catchThrowableOfType(() -> service.leaveGroup(USER_ID, GROUP_ID),
                LastAdminException.class);

        assertThat(exception).isNotNull();
        assertThat(exception.getMessage()).contains("administrador");
        Mockito.verifyNoInteractions(activityEventService);
    }

    @Test
    void theLastMemberLeavesEvenIfTheyAreTheOnlyAdmin() {
        when(groupMemberRepository.findByIdGroupIdAndIdUserId(GROUP_ID, USER_ID))
                .thenReturn(Optional.of(new GroupMember(USER_ID, GROUP_ID, GroupMember.ROLE_ADMIN)));
        when(groupMemberRepository.countActiveAdminsByGroupId(GROUP_ID)).thenReturn(1L);
        when(groupMemberRepository.countActiveByGroupId(GROUP_ID)).thenReturn(1L);
        when(debtPort.hasPendingDebtsForUser(GROUP_ID, USER_ID)).thenReturn(false);

        var response = service.leaveGroup(USER_ID, GROUP_ID);

        assertThat(response.isGroupDeleted()).isTrue();
        verify(groupMemberRepository).save(any(GroupMember.class));
        Mockito.verify(groupRepository, Mockito.never()).deleteById(any()); // spec 020: soft baja only
    }

    @Test
    void pendingBalancesAlsoBlockTheLastMember() {
        when(groupMemberRepository.findByIdGroupIdAndIdUserId(GROUP_ID, USER_ID))
                .thenReturn(Optional.of(new GroupMember(USER_ID, GROUP_ID, GroupMember.ROLE_ADMIN)));
        when(groupMemberRepository.countActiveByGroupId(GROUP_ID)).thenReturn(1L);
        when(debtPort.hasPendingDebtsForUser(GROUP_ID, USER_ID)).thenReturn(true);

        var exception = catchThrowableOfType(() -> service.leaveGroup(USER_ID, GROUP_ID),
                PendingDebtsException.class);

        assertThat(exception).isNotNull();
        verify(groupMemberRepository, Mockito.never()).save(any(GroupMember.class));
    }

    @Test
    void theGroupPersistsForTheRemainingMembers() {
        when(groupMemberRepository.findByIdGroupIdAndIdUserId(GROUP_ID, USER_ID))
                .thenReturn(Optional.of(new GroupMember(USER_ID, GROUP_ID, GroupMember.ROLE_MEMBER)));
        when(debtPort.hasPendingDebtsForUser(GROUP_ID, USER_ID)).thenReturn(false);
        when(simulatedProfileRepository.countByGroupId(GROUP_ID)).thenReturn(2L);
        when(groupMemberRepository.countActiveByGroupId(GROUP_ID)).thenReturn(3L);

        var response = service.leaveGroup(USER_ID, GROUP_ID);

        assertThat(response.isGroupDeleted()).isFalse();
        // no dissolution: the group row is never deleted by leaving
        Mockito.verify(groupRepository, Mockito.never()).deleteById(any());
    }

    @Test
    void nonMemberGetsTheNeutralNotFound() {
        when(groupMemberRepository.findByIdGroupIdAndIdUserId(GROUP_ID, USER_ID))
                .thenReturn(Optional.empty());

        var exception = catchThrowableOfType(() -> service.leaveGroup(USER_ID, GROUP_ID),
                GroupNotFoundException.class);

        assertThat(exception).isNotNull();
    }
}
