package com.amano.backend.groups;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import com.amano.backend.common.FieldValidationException;
import com.amano.backend.groups.dto.GroupDetailResponse;
import com.amano.backend.groups.dto.UpdateGroupRequest;
import com.amano.backend.user.User;
import com.amano.backend.user.UserRepository;

/**
 * Unit tests of {@link GroupService#updateGroupSettings} (T034, US2). Every
 * authorization decision goes through {@link GroupPermissionChecker} (role +
 * casillas): the casillas are ADMIN-only; name and description need
 * members_can_edit_group_data for a MEMBER. The six casillas change independently and defaults at creation are
 * all false (T007 / decision 11).
 */
class GroupSettingsServiceTest {

    private static final Long ADMIN_ID = 7L;
    private static final Long MEMBER_ID = 9L;
    private static final Long GROUP_ID = 42L;

    private GroupRepository groupRepository;
    private GroupMemberRepository groupMemberRepository;
    private SimulatedProfileRepository simulatedProfileRepository;
    private GroupPermissionsRepository groupPermissionsRepository;
    private com.amano.backend.groups.port.FriendshipPort friendshipPort;
    private UserRepository userRepository;
    private GroupPermissionChecker permissionChecker;
    private ActivityEventService activityEventService;
    private GroupService service;

    @BeforeEach
    void setUp() {
        groupRepository = Mockito.mock(GroupRepository.class);
        groupMemberRepository = Mockito.mock(GroupMemberRepository.class);
        simulatedProfileRepository = Mockito.mock(SimulatedProfileRepository.class);
        groupPermissionsRepository = Mockito.mock(GroupPermissionsRepository.class);
        friendshipPort = Mockito.mock(com.amano.backend.groups.port.FriendshipPort.class);
        userRepository = Mockito.mock(UserRepository.class);
        permissionChecker = Mockito.mock(GroupPermissionChecker.class);
        when(permissionChecker.canViewGroup(Mockito.anyString(), any())).thenReturn(true);
        activityEventService = Mockito.mock(ActivityEventService.class);

        service = new GroupService(groupRepository, groupMemberRepository, simulatedProfileRepository,
                groupPermissionsRepository, friendshipPort, userRepository, permissionChecker,
                activityEventService, Mockito.mock(com.amano.backend.groups.port.DebtPort.class),
                Mockito.mock(com.amano.backend.user.UserAvatarRepository.class),
                Mockito.mock(GroupPhotoRepository.class),
                Mockito.mock(com.amano.backend.user.AvatarValidator.class));

        when(groupRepository.save(any(Group.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(groupMemberRepository.save(any(GroupMember.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(simulatedProfileRepository.save(any(SimulatedProfile.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(groupPermissionsRepository.save(any(GroupPermissions.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(userRepository.findById(any())).thenReturn(Optional.of(user(ADMIN_ID, "ana@example.com")));
    }

    private static User user(Long id, String email) {
        User user = new User();
        org.springframework.test.util.ReflectionTestUtils.setField(user, "id", id);
        user.setFirstName("Ana");
        user.setLastName("López");
        user.setEmail(email);
        return user;
    }

    private void givenGroup(Long callerId, String callerRole) {
        Group group = new Group();
        org.springframework.test.util.ReflectionTestUtils.setField(group, "id", GROUP_ID);
        group.setName("Amigos para viajar");
        when(groupRepository.findById(GROUP_ID)).thenReturn(Optional.of(group));

        GroupMember membership = new GroupMember(callerId, GROUP_ID, callerRole);
        when(groupMemberRepository.findByIdGroupIdAndIdUserId(GROUP_ID, callerId)).thenReturn(
                Optional.of(membership));

        GroupPermissions permissions = new GroupPermissions();
        org.springframework.test.util.ReflectionTestUtils.setField(permissions, "id", GROUP_ID);
        permissions.setGroupId(GROUP_ID);
        when(groupPermissionsRepository.findByGroupId(GROUP_ID)).thenReturn(Optional.of(permissions));

        when(groupMemberRepository.countActiveByGroupId(GROUP_ID)).thenReturn(2L);
        when(simulatedProfileRepository.countByGroupId(GROUP_ID)).thenReturn(0L);
    }

    private static UpdateGroupRequest requestWithAllCasillas(
            Boolean expenses, Boolean invite, Boolean editGroupData, Boolean simulated,
            Boolean expensesForOthers, Boolean payments) {
        UpdateGroupRequest request = new UpdateGroupRequest();
        UpdateGroupRequest.PermissionsInput permissions = new UpdateGroupRequest.PermissionsInput();
        permissions.setMembersCannotMakeExpenses(expenses);
        permissions.setMembersCannotInvite(invite);
        permissions.setMembersCanEditGroupData(editGroupData);
        permissions.setMembersCanManageSimulatedProfiles(simulated);
        permissions.setMembersCanGenerateExpensesForOthers(expensesForOthers);
        permissions.setPaymentsRequireValidation(payments);
        request.setPermissions(permissions);
        return request;
    }

    @Test
    void adminAlwaysEditsSettingsAndEachCasillaIsIndependent() {
        givenGroup(ADMIN_ID, GroupMember.ROLE_ADMIN);
        when(permissionChecker.canManagePermissions(eq(GroupMember.ROLE_ADMIN), any())).thenReturn(true);

        service.updateGroupSettings(ADMIN_ID, GROUP_ID, requestWithAllCasillas(
                true, true, true, true, true, true));

        org.mockito.ArgumentCaptor<GroupPermissions> saved =
                org.mockito.ArgumentCaptor.forClass(GroupPermissions.class);
        verify(groupPermissionsRepository).save(saved.capture());
        assertThat(saved.getValue().isMembersCannotMakeExpenses()).isTrue();
        assertThat(saved.getValue().isMembersCannotInvite()).isTrue();
        assertThat(saved.getValue().isMembersCanEditGroupData()).isTrue();
        assertThat(saved.getValue().isMembersCanManageSimulatedProfiles()).isTrue();
        assertThat(saved.getValue().isMembersCanGenerateExpensesForOthers()).isTrue();
        assertThat(saved.getValue().isPaymentsRequireValidation()).isTrue();
    }

    @Test
    void memberEditsNameAndDescriptionWhenTheCasillaAllowsIt() {
        givenGroup(MEMBER_ID, GroupMember.ROLE_MEMBER);
        when(permissionChecker.canEditGroupData(eq(GroupMember.ROLE_MEMBER), any())).thenReturn(true);
        UpdateGroupRequest request = new UpdateGroupRequest();
        request.setName("Nuevo nombre");
        request.setDescription("Nueva descripción");

        service.updateGroupSettings(MEMBER_ID, GROUP_ID, request);

        verify(groupPermissionsRepository, Mockito.never()).save(any(GroupPermissions.class));
    }

    @Test
    void memberIsDeniedGroupDataWhenTheCasillaIsOff() {
        givenGroup(MEMBER_ID, GroupMember.ROLE_MEMBER);
        when(permissionChecker.canEditGroupData(eq(GroupMember.ROLE_MEMBER), any())).thenReturn(false);
        UpdateGroupRequest request = new UpdateGroupRequest();
        request.setDescription("Nueva descripción");

        var exception = catchThrowableOfType(
                () -> service.updateGroupSettings(MEMBER_ID, GROUP_ID, request),
                GroupPermissionDeniedException.class);

        assertThat(exception).isNotNull();
    }

    @Test
    void memberIsDeniedWhenChangingPermissions() {
        givenGroup(MEMBER_ID, GroupMember.ROLE_MEMBER);
        when(permissionChecker.canManagePermissions(eq(GroupMember.ROLE_MEMBER), any())).thenReturn(false);

        var exception = catchThrowableOfType(() -> service.updateGroupSettings(MEMBER_ID, GROUP_ID,
                requestWithAllCasillas(true, null, null, null, null, null)),
                GroupPermissionDeniedException.class);

        assertThat(exception).isNotNull();
        verify(groupPermissionsRepository, Mockito.never()).save(any(GroupPermissions.class));
    }

    @Test
    void authorizationIsAlwaysDelegatedToThePermissionChecker() {
        givenGroup(ADMIN_ID, GroupMember.ROLE_ADMIN);
        when(permissionChecker.canManagePermissions(anyString(), any())).thenReturn(true);

        service.updateGroupSettings(ADMIN_ID, GROUP_ID, requestWithAllCasillas(
                null, null, true, null, null, null));

        verify(permissionChecker).canManagePermissions(eq(GroupMember.ROLE_ADMIN),
                Mockito.any(GroupPermissions.class));
    }

    @Test
    void editingPermissionsRecordsAPermissionChangedEvent() {
        givenGroup(ADMIN_ID, GroupMember.ROLE_ADMIN);
        when(permissionChecker.canManagePermissions(eq(GroupMember.ROLE_ADMIN), any())).thenReturn(true);

        service.updateGroupSettings(ADMIN_ID, GROUP_ID, requestWithAllCasillas(
                null, true, null, null, null, null));

        verify(activityEventService).record(eq(GROUP_ID),
                eq(GroupActivity.GroupActivityType.PERMISSION_CHANGED), eq(ADMIN_ID), anyMap());
    }

    @Test
    void unknownGroupIsReportedAsNotFound() {
        when(groupRepository.findById(GROUP_ID)).thenReturn(Optional.empty());

        var exception = catchThrowableOfType(() -> service.updateGroupSettings(ADMIN_ID, GROUP_ID,
                requestWithAllCasillas(null, null, null, null, null, null)),
                GroupNotFoundException.class);

        assertThat(exception).isNotNull();
        Mockito.verifyNoInteractions(permissionChecker);
    }

    @Test
    void presentNameMustStayNonEmpty() {
        givenGroup(ADMIN_ID, GroupMember.ROLE_ADMIN);
        when(permissionChecker.canEditGroupData(anyString(), any())).thenReturn(true);

        UpdateGroupRequest request = new UpdateGroupRequest();
        request.setName("   ");

        var exception = catchThrowableOfType(() -> service.updateGroupSettings(ADMIN_ID, GROUP_ID, request),
                FieldValidationException.class);

        assertThat(exception).isNotNull();
        assertThat(exception.getFields()).containsKey("name");
    }

    @Test
    void partialUpdateLeavesUnspecifiedCasillasUntouched() {
        givenGroup(ADMIN_ID, GroupMember.ROLE_ADMIN);
        when(permissionChecker.canManagePermissions(eq(GroupMember.ROLE_ADMIN), any())).thenReturn(true);

        service.updateGroupSettings(ADMIN_ID, GROUP_ID, requestWithAllCasillas(
                null, true, Boolean.FALSE, null, null, null));

        verify(groupPermissionsRepository).save(Mockito.argThat(saved ->
                saved.isMembersCannotMakeExpenses() == false
                        && saved.isMembersCannotInvite() == true
                        && saved.isMembersCanEditGroupData() == false
                        && saved.isMembersCanManageSimulatedProfiles() == false));
    }

    @Test
    void groupCreationSeedsPermissionsWithExpensesForOthersTrueAndTheRestFalse() {
        com.amano.backend.groups.dto.CreateGroupRequest.MemberInput member =
                new com.amano.backend.groups.dto.CreateGroupRequest.MemberInput();
        member.setMemberType("REGISTERED");
        member.setIdentifier("friend@example.com");
        com.amano.backend.groups.dto.CreateGroupRequest request =
                new com.amano.backend.groups.dto.CreateGroupRequest();
        request.setName("Grupo");
        request.setMember(member);

        when(groupRepository.save(any(Group.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(friendshipPort.isFriend(ADMIN_ID, "friend@example.com")).thenReturn(true);
        when(userRepository.findByEmail("friend@example.com"))
                .thenReturn(Optional.of(user(MEMBER_ID, "friend@example.com")));

        service.createGroup(ADMIN_ID, request);

        org.mockito.ArgumentCaptor<GroupPermissions> saved =
                org.mockito.ArgumentCaptor.forClass(GroupPermissions.class);
        verify(groupPermissionsRepository).save(saved.capture());
        assertThat(saved.getValue().isMembersCannotMakeExpenses()).isFalse();
        assertThat(saved.getValue().isMembersCannotInvite()).isFalse();
        assertThat(saved.getValue().isMembersCanEditGroupData()).isFalse();
        assertThat(saved.getValue().isMembersCanManageSimulatedProfiles()).isFalse();
        // 022 T060: new groups start with expenses-for-others enabled
        assertThat(saved.getValue().isMembersCanGenerateExpensesForOthers()).isTrue();
        assertThat(saved.getValue().isPaymentsRequireValidation()).isFalse();
    }
}
