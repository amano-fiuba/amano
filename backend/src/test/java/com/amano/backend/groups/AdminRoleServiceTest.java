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

/**
 * Unit tests of the US10 role operations of {@link GroupMemberService} (T069):
 * GRANT member→admin, REVOKE admin→member keeping the membership, the
 * last-admin revocation guard, the caller authorization, per-group scope and
 * atomic persistence (the saved row carries exactly one role).
 */
class AdminRoleServiceTest {

    private static final Long CALLER_ID = 7L;
    private static final Long TARGET_ID = 11L;
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
    private GroupMemberService service;

    @BeforeEach
    void setUp() {
        groupRepository = Mockito.mock(GroupRepository.class);
        groupMemberRepository = Mockito.mock(GroupMemberRepository.class);
        simulatedProfileRepository = Mockito.mock(SimulatedProfileRepository.class);
        groupPermissionsRepository = Mockito.mock(GroupPermissionsRepository.class);
        friendshipPort = Mockito.mock(com.amano.backend.groups.port.FriendshipPort.class);
        userRepository = Mockito.mock(com.amano.backend.user.UserRepository.class);
        permissionChecker = Mockito.mock(GroupPermissionChecker.class);
        activityEventService = Mockito.mock(ActivityEventService.class);
        debtPort = Mockito.mock(com.amano.backend.groups.port.DebtPort.class);

        service = new GroupMemberService(groupRepository, groupMemberRepository,
                simulatedProfileRepository, groupPermissionsRepository, friendshipPort,
                userRepository, permissionChecker, activityEventService, debtPort);

        Group group = new Group();
        org.springframework.test.util.ReflectionTestUtils.setField(group, "id", GROUP_ID);
        group.setName("Equipo de viajes");
        when(groupRepository.findById(GROUP_ID)).thenReturn(Optional.of(group));
        when(groupMemberRepository.save(any(GroupMember.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(permissionChecker.canManageRoles(Mockito.anyString(), any())).thenReturn(true);
    }

    private void givenActiveMembership(Long userId, String role) {
        when(groupMemberRepository.findByIdGroupIdAndIdUserId(GROUP_ID, userId))
                .thenReturn(Optional.of(new GroupMember(userId, GROUP_ID, role)));
    }

    @Test
    void adminGrantsTheRoleToAnActiveMember() {
        givenActiveMembership(CALLER_ID, GroupMember.ROLE_ADMIN);
        givenActiveMembership(TARGET_ID, GroupMember.ROLE_MEMBER);

        service.grantAdminRole(CALLER_ID, GROUP_ID, TARGET_ID);

        org.mockito.ArgumentCaptor<GroupMember> saved =
                org.mockito.ArgumentCaptor.forClass(GroupMember.class);
        verify(groupMemberRepository).save(saved.capture());
        assertThat(saved.getValue().getRole()).isEqualTo(GroupMember.ROLE_ADMIN);
        assertThat(saved.getValue().getLeftAt()).isNull(); // membership kept
    }

    @Test
    void memberCallerCannotGrantTheRole() {
        givenActiveMembership(CALLER_ID, GroupMember.ROLE_MEMBER);
        givenActiveMembership(TARGET_ID, GroupMember.ROLE_MEMBER);
        when(permissionChecker.canManageRoles(eq(GroupMember.ROLE_MEMBER), any())).thenReturn(false);

        var exception = catchThrowableOfType(
                () -> service.grantAdminRole(CALLER_ID, GROUP_ID, TARGET_ID),
                GroupPermissionDeniedException.class);

        assertThat(exception).isNotNull();
        Mockito.verifyNoInteractions(activityEventService);
    }

    @Test
    void grantIsRejectedForAnUnknownOrInactiveTarget() {
        givenActiveMembership(CALLER_ID, GroupMember.ROLE_ADMIN);
        when(groupMemberRepository.findByIdGroupIdAndIdUserId(GROUP_ID, TARGET_ID))
                .thenReturn(Optional.empty());

        var exception = catchThrowableOfType(
                () -> service.grantAdminRole(CALLER_ID, GROUP_ID, TARGET_ID),
                GroupNotFoundException.class);

        assertThat(exception).isNotNull();
    }

    @Test
    void adminRevokesTheRoleAndTheMembershipIsKept() {
        givenActiveMembership(CALLER_ID, GroupMember.ROLE_ADMIN);
        givenActiveMembership(TARGET_ID, GroupMember.ROLE_ADMIN);
        when(groupMemberRepository.countActiveAdminsByGroupId(GROUP_ID)).thenReturn(2L);

        service.revokeAdminRole(CALLER_ID, GROUP_ID, TARGET_ID);

        org.mockito.ArgumentCaptor<GroupMember> saved =
                org.mockito.ArgumentCaptor.forClass(GroupMember.class);
        verify(groupMemberRepository).save(saved.capture());
        assertThat(saved.getValue().getRole()).isEqualTo(GroupMember.ROLE_MEMBER);
        assertThat(saved.getValue().getLeftAt()).isNull();
    }

    @Test
    void theLastAdminCannotBeRevoked() {
        givenActiveMembership(CALLER_ID, GroupMember.ROLE_ADMIN);
        givenActiveMembership(TARGET_ID, GroupMember.ROLE_ADMIN);
        when(groupMemberRepository.countActiveAdminsByGroupId(GROUP_ID)).thenReturn(1L);

        var exception = catchThrowableOfType(
                () -> service.revokeAdminRole(CALLER_ID, GROUP_ID, TARGET_ID),
                LastAdminException.class);

        assertThat(exception).isNotNull();
        Mockito.verifyNoInteractions(activityEventService);
    }

    @Test
    void revokingAPlainMemberIsRejected() {
        givenActiveMembership(CALLER_ID, GroupMember.ROLE_ADMIN);
        givenActiveMembership(TARGET_ID, GroupMember.ROLE_MEMBER);

        var exception = catchThrowableOfType(
                () -> service.revokeAdminRole(CALLER_ID, GROUP_ID, TARGET_ID),
                GroupNotFoundException.class);

        assertThat(exception).isNotNull();
        assertThat(exception.getMessage()).contains("administrador");
    }
}
