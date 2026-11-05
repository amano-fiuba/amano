package com.amano.backend.groups;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import com.amano.backend.groups.port.DebtPort;

/**
 * Unit tests of {@link GroupMemberService} removals (T055, US4): removal of a
 * MEMBER by an ADMIN (soft baja), blocked when {@link DebtPort} reports pending
 * debts, blocked when the target is another ADMIN ("primero quitale el rol"),
 * simulated removal by an ADMIN and by a MEMBER when the casilla
 * members_can_manage_simulated_profiles is active, and simulated removal
 * blocked by debts. Simulated profiles have NO self-removal flow (there is no
 * such method).
 */
class MemberRemovalServiceTest {

    private static final Long ADMIN_ID = 7L;
    private static final Long MEMBER_ID = 9L;
    private static final Long TARGET_ID = 15L;
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
        debtPort = Mockito.mock(DebtPort.class);

        service = new GroupMemberService(groupRepository, groupMemberRepository, simulatedProfileRepository,
                groupPermissionsRepository, friendshipPort, userRepository, permissionChecker,
                activityEventService, debtPort);

        Group group = new Group();
        org.springframework.test.util.ReflectionTestUtils.setField(group, "id", GROUP_ID);
        group.setName("Amigos para viajar");
        when(groupRepository.findById(GROUP_ID)).thenReturn(Optional.of(group));

        GroupPermissions permissions = new GroupPermissions();
        org.springframework.test.util.ReflectionTestUtils.setField(permissions, "id", GROUP_ID);
        permissions.setGroupId(GROUP_ID);
        when(groupPermissionsRepository.findByGroupId(GROUP_ID)).thenReturn(Optional.of(permissions));

        when(groupMemberRepository.countActiveAdminsByGroupId(GROUP_ID)).thenReturn(2L);
        when(permissionChecker.canRemoveMembers(eq(GroupMember.ROLE_ADMIN), any())).thenReturn(true);
        when(permissionChecker.canViewGroup(Mockito.anyString(), any())).thenReturn(true);
        when(groupMemberRepository.save(any(GroupMember.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(groupPermissionsRepository.save(any(GroupPermissions.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    private void givenActiveMembership(Long userId, String role) {
        when(groupMemberRepository.findByIdGroupIdAndIdUserId(GROUP_ID, userId))
                .thenReturn(Optional.of(new GroupMember(userId, GROUP_ID, role)));
    }

    private static SimulatedProfile profile(Long id, String name, String email) {
        SimulatedProfile profile = new SimulatedProfile();
        org.springframework.test.util.ReflectionTestUtils.setField(profile, "id", id);
        profile.setGroupId(GROUP_ID);
        profile.setName(name);
        profile.setEmail(email);
        return profile;
    }

    @Test
    void adminRemovesAnActiveMemberWithSoftBajaAndTheEventIsRecorded() {
        givenActiveMembership(ADMIN_ID, GroupMember.ROLE_ADMIN);
        givenActiveMembership(TARGET_ID, GroupMember.ROLE_MEMBER);
        when(debtPort.hasPendingDebtsForUser(GROUP_ID, TARGET_ID)).thenReturn(false);

        service.removeMember(ADMIN_ID, GROUP_ID, TARGET_ID);

        org.mockito.ArgumentCaptor<GroupMember> saved = org.mockito.ArgumentCaptor.forClass(GroupMember.class);
        verify(groupMemberRepository).save(saved.capture());
        assertThat(saved.getValue().getLeftAt()).isNotNull(); // soft baja: the row is kept
        assertThat(saved.getValue().getRole()).isEqualTo(GroupMember.ROLE_MEMBER);
        assertThat(saved.getValue().getId().getUserId()).isEqualTo(TARGET_ID);

        // 014 FR-003 / D4: without the user row, the baja still keeps type and id
        assertThat(recordedRemovalDetails()).containsExactly(
                java.util.Map.entry("type", "REGISTERED"), java.util.Map.entry("userId", TARGET_ID));
    }

    @Test
    void removalKeepsACopyOfTheRemovedMembersNameAndEmail() {
        givenActiveMembership(ADMIN_ID, GroupMember.ROLE_ADMIN);
        givenActiveMembership(TARGET_ID, GroupMember.ROLE_MEMBER);
        when(debtPort.hasPendingDebtsForUser(GROUP_ID, TARGET_ID)).thenReturn(false);
        com.amano.backend.user.User target = new com.amano.backend.user.User();
        org.springframework.test.util.ReflectionTestUtils.setField(target, "id", TARGET_ID);
        target.setFirstName("Juan");
        target.setLastName(null);
        target.setEmail("juan@example.com");
        when(userRepository.findById(TARGET_ID)).thenReturn(Optional.of(target));

        service.removeMember(ADMIN_ID, GROUP_ID, TARGET_ID);

        assertThat(recordedRemovalDetails()).containsExactly(
                java.util.Map.entry("type", "REGISTERED"), java.util.Map.entry("userId", TARGET_ID),
                java.util.Map.entry("name", "Juan"), java.util.Map.entry("email", "juan@example.com"));
    }

    @SuppressWarnings("unchecked")
    private java.util.Map<String, Object> recordedRemovalDetails() {
        org.mockito.ArgumentCaptor<java.util.Map<String, Object>> details =
                org.mockito.ArgumentCaptor.forClass(java.util.Map.class);
        verify(activityEventService).record(eq(GROUP_ID),
                eq(GroupActivity.GroupActivityType.MEMBER_REMOVED), eq(ADMIN_ID), details.capture());
        return details.getValue();
    }

    @Test
    void memberCannotRemoveAnyone() {
        givenActiveMembership(MEMBER_ID, GroupMember.ROLE_MEMBER);
        givenActiveMembership(TARGET_ID, GroupMember.ROLE_MEMBER);
        when(permissionChecker.canRemoveMembers(eq(GroupMember.ROLE_MEMBER), any())).thenReturn(false);

        var exception = catchThrowableOfType(() -> service.removeMember(MEMBER_ID, GROUP_ID, TARGET_ID),
                GroupPermissionDeniedException.class);

        assertThat(exception).isNotNull();
        assertThat(exception.getMessage()).contains("No sos admin");
        Mockito.verifyNoInteractions(activityEventService);
    }

    @Test
    void adminCannotRemoveAnotherAdminSoTheRoleMustBeRevokedFirst() {
        givenActiveMembership(ADMIN_ID, GroupMember.ROLE_ADMIN);
        givenActiveMembership(TARGET_ID, GroupMember.ROLE_ADMIN);

        var exception = catchThrowableOfType(() -> service.removeMember(ADMIN_ID, GROUP_ID, TARGET_ID),
                AdminRemovalBlockedException.class);

        assertThat(exception).isNotNull();
        assertThat(exception.getMessage()).contains("primero");
        Mockito.verifyNoInteractions(activityEventService);
    }

    @Test
    void removalIsBlockedWhenTheTargetHasPendingDebts() {
        givenActiveMembership(ADMIN_ID, GroupMember.ROLE_ADMIN);
        givenActiveMembership(TARGET_ID, GroupMember.ROLE_MEMBER);
        when(debtPort.hasPendingDebtsForUser(GROUP_ID, TARGET_ID)).thenReturn(true);

        var exception = catchThrowableOfType(() -> service.removeMember(ADMIN_ID, GROUP_ID, TARGET_ID),
                PendingDebtsException.class);

        assertThat(exception).isNotNull();
        assertThat(exception.getMessage()).contains("deudas pendientes");
        Mockito.verifyNoInteractions(activityEventService);
    }

    @Test
    void unknownOrInactiveTargetIsReportedAsNotFound() {
        givenActiveMembership(ADMIN_ID, GroupMember.ROLE_ADMIN);
        when(groupMemberRepository.findByIdGroupIdAndIdUserId(GROUP_ID, TARGET_ID))
                .thenReturn(Optional.empty());

        var exception = catchThrowableOfType(() -> service.removeMember(ADMIN_ID, GROUP_ID, TARGET_ID),
                GroupNotFoundException.class);

        assertThat(exception).isNotNull();
    }

    @Test
    void adminRemovesASimulatedProfileWithPhysicalDelete() {
        givenActiveMembership(ADMIN_ID, GroupMember.ROLE_ADMIN);
        when(permissionChecker.canRemoveSimulatedProfiles(eq(GroupMember.ROLE_ADMIN), any())).thenReturn(true);
        SimulatedProfile simulated = profile(TARGET_ID, "Juan Simulado", "simulado@amano.dev");
        when(simulatedProfileRepository.findById(TARGET_ID)).thenReturn(Optional.of(simulated));
        when(debtPort.hasPendingDebtsForSimulatedProfile(GROUP_ID, TARGET_ID)).thenReturn(false);

        service.removeSimulatedProfile(ADMIN_ID, GROUP_ID, TARGET_ID);

        verify(simulatedProfileRepository).delete(simulated); // physical DELETE (T006 model)
        assertThat(recordedRemovalDetails()).containsExactly(
                java.util.Map.entry("type", "SIMULATED"), java.util.Map.entry("simulatedProfileId", TARGET_ID),
                java.util.Map.entry("name", "Juan Simulado"), java.util.Map.entry("email", "simulado@amano.dev"));
    }

    @Test
    void memberRemovesASimulatedProfileOnlyWhenTheCasillaAllowsIt() {
        givenActiveMembership(MEMBER_ID, GroupMember.ROLE_MEMBER);
        when(permissionChecker.canRemoveSimulatedProfiles(eq(GroupMember.ROLE_MEMBER), any())).thenReturn(true);
        SimulatedProfile simulated = profile(TARGET_ID, "Juan Simulado", "simulado@amano.dev");
        when(simulatedProfileRepository.findById(TARGET_ID)).thenReturn(Optional.of(simulated));
        when(debtPort.hasPendingDebtsForSimulatedProfile(GROUP_ID, TARGET_ID)).thenReturn(false);

        service.removeSimulatedProfile(MEMBER_ID, GROUP_ID, TARGET_ID);

        verify(simulatedProfileRepository).delete(simulated);
    }

    @Test
    void memberIsForbiddenWhenTheSimulatedCasillaIsOff() {
        givenActiveMembership(MEMBER_ID, GroupMember.ROLE_MEMBER);
        when(permissionChecker.canRemoveSimulatedProfiles(eq(GroupMember.ROLE_MEMBER), any())).thenReturn(false);
        when(simulatedProfileRepository.findById(TARGET_ID)).thenReturn(
                Optional.of(profile(TARGET_ID, "Juan Simulado", "simulado@amano.dev")));

        var exception = catchThrowableOfType(
                () -> service.removeSimulatedProfile(MEMBER_ID, GROUP_ID, TARGET_ID),
                GroupPermissionDeniedException.class);

        assertThat(exception).isNotNull();
        Mockito.verifyNoInteractions(activityEventService);
    }

    @Test
    void simulatedRemovalIsBlockedByPendingDebts() {
        givenActiveMembership(ADMIN_ID, GroupMember.ROLE_ADMIN);
        when(permissionChecker.canRemoveSimulatedProfiles(eq(GroupMember.ROLE_ADMIN), any())).thenReturn(true);
        when(simulatedProfileRepository.findById(TARGET_ID)).thenReturn(
                Optional.of(profile(TARGET_ID, "Juan Simulado", "simulado@amano.dev")));
        when(debtPort.hasPendingDebtsForSimulatedProfile(GROUP_ID, TARGET_ID)).thenReturn(true);

        var exception = catchThrowableOfType(
                () -> service.removeSimulatedProfile(ADMIN_ID, GROUP_ID, TARGET_ID),
                PendingDebtsException.class);

        assertThat(exception).isNotNull();
        Mockito.verifyNoInteractions(activityEventService);
    }

    /**
     * 017 research.md D7: the simulated-profile removal asks the port about the
     * PROFILE, never about a user that happens to share the same numeric id.
     */
    @Test
    void simulatedRemovalAsksAboutTheProfileNotAUserWithTheSameId() {
        givenActiveMembership(ADMIN_ID, GroupMember.ROLE_ADMIN);
        when(permissionChecker.canRemoveSimulatedProfiles(eq(GroupMember.ROLE_ADMIN), any())).thenReturn(true);
        SimulatedProfile simulated = profile(TARGET_ID, "Juan Simulado", "simulado@amano.dev");
        when(simulatedProfileRepository.findById(TARGET_ID)).thenReturn(Optional.of(simulated));
        when(debtPort.hasPendingDebtsForUser(GROUP_ID, TARGET_ID)).thenReturn(true);
        when(debtPort.hasPendingDebtsForSimulatedProfile(GROUP_ID, TARGET_ID)).thenReturn(false);

        service.removeSimulatedProfile(ADMIN_ID, GROUP_ID, TARGET_ID);

        verify(simulatedProfileRepository).delete(simulated);
        verify(debtPort, Mockito.never()).hasPendingDebtsForUser(any(), any());
    }

    /** 017 research.md D7: a registered removal asks about the user, never about a profile. */
    @Test
    void memberRemovalAsksAboutTheUserNotAProfile() {
        givenActiveMembership(ADMIN_ID, GroupMember.ROLE_ADMIN);
        givenActiveMembership(TARGET_ID, GroupMember.ROLE_MEMBER);
        when(debtPort.hasPendingDebtsForUser(GROUP_ID, TARGET_ID)).thenReturn(false);

        service.removeMember(ADMIN_ID, GROUP_ID, TARGET_ID);

        verify(debtPort, Mockito.never()).hasPendingDebtsForSimulatedProfile(any(), any());
    }
}
