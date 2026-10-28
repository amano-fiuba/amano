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

import com.amano.backend.common.FieldValidationException;
import com.amano.backend.groups.dto.AddMemberResponse;
import com.amano.backend.user.User;
import com.amano.backend.user.UserRepository;

/**
 * Unit tests of {@link GroupMemberService} (T041, US3): registered members are
 * added ONLY from the caller's friendship relation (FriendshipPort — never the
 * whole user base), duplicates are rejected with the reason, simulated profiles
 * (name + email, no group_members row) become members, and the authorization
 * (role + casilla members_cannot_invite) goes through
 * {@link GroupPermissionChecker}: member adds when the casilla is false and is
 * rejected when it is true.
 */
class GroupMemberServiceTest {

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
    private GroupMemberService service;

    @BeforeEach
    void setUp() {
        groupRepository = Mockito.mock(GroupRepository.class);
        groupMemberRepository = Mockito.mock(GroupMemberRepository.class);
        simulatedProfileRepository = Mockito.mock(SimulatedProfileRepository.class);
        groupPermissionsRepository = Mockito.mock(GroupPermissionsRepository.class);
        friendshipPort = Mockito.mock(com.amano.backend.groups.port.FriendshipPort.class);
        userRepository = Mockito.mock(UserRepository.class);
        permissionChecker = Mockito.mock(GroupPermissionChecker.class);
        activityEventService = Mockito.mock(ActivityEventService.class);

        service = new GroupMemberService(groupRepository, groupMemberRepository, simulatedProfileRepository,
                groupPermissionsRepository, friendshipPort, userRepository, permissionChecker,
                activityEventService, Mockito.mock(com.amano.backend.groups.port.DebtPort.class));

        when(simulatedProfileRepository.save(any(SimulatedProfile.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(groupMemberRepository.save(any(GroupMember.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Group group = new Group();
        org.springframework.test.util.ReflectionTestUtils.setField(group, "id", GROUP_ID);
        group.setName("Amigos para viajar");
        when(groupRepository.findById(GROUP_ID)).thenReturn(Optional.of(group));

        GroupPermissions permissions = new GroupPermissions();
        org.springframework.test.util.ReflectionTestUtils.setField(permissions, "id", GROUP_ID);
        permissions.setGroupId(GROUP_ID);
        when(groupPermissionsRepository.findByGroupId(GROUP_ID)).thenReturn(Optional.of(permissions));
    }

    private static User user(Long id, String email) {
        User user = new User();
        org.springframework.test.util.ReflectionTestUtils.setField(user, "id", id);
        user.setFirstName("Beto");
        user.setLastName("Pérez");
        user.setEmail(email);
        return user;
    }

    private void givenAdminCaller() {
        when(groupMemberRepository.findByIdGroupIdAndIdUserId(GROUP_ID, ADMIN_ID))
                .thenReturn(Optional.of(new GroupMember(ADMIN_ID, GROUP_ID, GroupMember.ROLE_ADMIN)));
        when(permissionChecker.canAddMembers(eq(GroupMember.ROLE_ADMIN), any())).thenReturn(true);
    }

    private void givenMemberCaller() {
        when(groupMemberRepository.findByIdGroupIdAndIdUserId(GROUP_ID, MEMBER_ID))
                .thenReturn(Optional.of(new GroupMember(MEMBER_ID, GROUP_ID, GroupMember.ROLE_MEMBER)));
    }

    private static UserRepository FRIEND_REPOSITORY(User friend) {
        UserRepository repo = Mockito.mock(UserRepository.class);
        when(repo.findByEmail(friend.getEmail())).thenReturn(Optional.of(friend));
        return repo;
    }

    @Test
    void adminAddsARegisteredFriendWithMemberRoleAndTheEventIsRecorded() {
        givenAdminCaller();
        User friend = user(15L, "friend@example.com");
        userRepository = FRIEND_REPOSITORY(friend);
        service = new GroupMemberService(groupRepository, groupMemberRepository, simulatedProfileRepository,
                groupPermissionsRepository, friendshipPort, userRepository, permissionChecker,
                activityEventService, Mockito.mock(com.amano.backend.groups.port.DebtPort.class));
        when(friendshipPort.isFriend(ADMIN_ID, "friend@example.com")).thenReturn(true);
        when(groupMemberRepository.existsByIdGroupIdAndIdUserId(GROUP_ID, 15L)).thenReturn(false);

        AddMemberResponse response = service.addRegisteredMember(ADMIN_ID, GROUP_ID, "friend@example.com");

        assertThat(response.getMember().getRole()).isEqualTo(GroupMember.ROLE_MEMBER);
        assertThat(response.getMember().getType()).isEqualTo("REGISTERED");
        assertThat(response.getMember().getEmail()).isEqualTo("friend@example.com");
        assertThat(response.getGroupMemberRole()).isEqualTo(GroupMember.ROLE_ADMIN);

        org.mockito.ArgumentCaptor<GroupMember> saved = org.mockito.ArgumentCaptor.forClass(GroupMember.class);
        verify(groupMemberRepository).save(saved.capture());
        assertThat(saved.getValue().getId().getUserId()).isEqualTo(15L);
        assertThat(saved.getValue().getRole()).isEqualTo(GroupMember.ROLE_MEMBER);

        verify(activityEventService).record(eq(GROUP_ID),
                eq(GroupActivity.GroupActivityType.MEMBER_ADDED), eq(ADMIN_ID), anyString());
    }

    @Test
    void registeredMemberOutsideFriendshipIsRejectedWithTheNotAFriendReason() {
        givenAdminCaller();
        User stranger = user(15L, "stranger@example.com");
        userRepository = FRIEND_REPOSITORY(stranger);
        service = new GroupMemberService(groupRepository, groupMemberRepository, simulatedProfileRepository,
                groupPermissionsRepository, friendshipPort, userRepository, permissionChecker,
                activityEventService, Mockito.mock(com.amano.backend.groups.port.DebtPort.class));
        when(friendshipPort.isFriend(ADMIN_ID, "stranger@example.com")).thenReturn(false);

        var exception = catchThrowableOfType(
                () -> service.addRegisteredMember(ADMIN_ID, GROUP_ID, "stranger@example.com"),
                NotFriendException.class);

        assertThat(exception).isNotNull();
        assertThat(exception.getMessage()).contains("amigos");
        Mockito.verifyNoInteractions(activityEventService, Mockito.mock(com.amano.backend.groups.port.DebtPort.class));
    }

    @Test
    void someoneAlreadyInTheGroupIsRejectedAsAlreadyAMember() {
        givenAdminCaller();
        User friend = user(15L, "friend@example.com");
        userRepository = FRIEND_REPOSITORY(friend);
        service = new GroupMemberService(groupRepository, groupMemberRepository, simulatedProfileRepository,
                groupPermissionsRepository, friendshipPort, userRepository, permissionChecker,
                activityEventService, Mockito.mock(com.amano.backend.groups.port.DebtPort.class));
        when(friendshipPort.isFriend(ADMIN_ID, "friend@example.com")).thenReturn(true);
        when(groupMemberRepository.findByIdGroupIdAndIdUserId(GROUP_ID, 15L)).thenReturn(
                Optional.of(new GroupMember(15L, GROUP_ID, GroupMember.ROLE_MEMBER)));

        var exception = catchThrowableOfType(
                () -> service.addRegisteredMember(ADMIN_ID, GROUP_ID, "friend@example.com"),
                FieldValidationException.class);

        assertThat(exception).isNotNull();
        assertThat(exception.getFields()).containsKey("identifier");
        assertThat(exception.getFields().get("identifier")).contains("ya es miembro");
    }

    @Test
    void simulatedProfileBecomesMemberWithoutGroupMembersRow() {
        givenAdminCaller();
        when(simulatedProfileRepository.existsByGroupIdAndEmail(GROUP_ID, "invitado@example.com")).thenReturn(false);

        AddMemberResponse response = service.addSimulatedProfile(ADMIN_ID, GROUP_ID,
                "Juan Simulado", "invitado@example.com");

        assertThat(response.getMember().getType()).isEqualTo("SIMULATED");
        assertThat(response.getMember().getName()).isEqualTo("Juan Simulado");
        assertThat(response.getMember().getRole()).isEqualTo(GroupMember.ROLE_MEMBER);

        org.mockito.ArgumentCaptor<SimulatedProfile> saved =
                org.mockito.ArgumentCaptor.forClass(SimulatedProfile.class);
        verify(simulatedProfileRepository).save(saved.capture());
        assertThat(saved.getValue().getName()).isEqualTo("Juan Simulado");
        assertThat(saved.getValue().getEmail()).isEqualTo("invitado@example.com");

        org.mockito.ArgumentCaptor<GroupMember> members =
                org.mockito.ArgumentCaptor.forClass(GroupMember.class);
        Mockito.verify(groupMemberRepository, Mockito.never()).save(members.capture());

        verify(activityEventService).record(eq(GROUP_ID),
                eq(GroupActivity.GroupActivityType.MEMBER_ADDED), eq(ADMIN_ID), anyString());
    }

    @Test
    void simulatedProfileWithDuplicatedEmailInTheGroupIsRejected() {
        givenAdminCaller();
        when(simulatedProfileRepository.existsByGroupIdAndEmail(GROUP_ID, "invitado@example.com"))
                .thenReturn(true);

        var exception = catchThrowableOfType(
                () -> service.addSimulatedProfile(ADMIN_ID, GROUP_ID, "Otro", "invitado@example.com"),
                FieldValidationException.class);

        assertThat(exception).isNotNull();
        assertThat(exception.getFields().get("identifier")).contains("ya");
    }

    @Test
    void memberAddsWhenTheInviteCasillaIsOff() {
        givenMemberCaller();
        when(permissionChecker.canAddMembers(GroupMember.ROLE_MEMBER,
                groupPermissionsRepository.findByGroupId(GROUP_ID).orElseThrow())).thenReturn(true);
        User friend = user(15L, "friend@example.com");
        userRepository = FRIEND_REPOSITORY(friend);
        service = new GroupMemberService(groupRepository, groupMemberRepository, simulatedProfileRepository,
                groupPermissionsRepository, friendshipPort, userRepository, permissionChecker,
                activityEventService, Mockito.mock(com.amano.backend.groups.port.DebtPort.class));
        when(friendshipPort.isFriend(MEMBER_ID, "friend@example.com")).thenReturn(true);
        when(groupMemberRepository.existsByIdGroupIdAndIdUserId(GROUP_ID, 15L)).thenReturn(false);

        AddMemberResponse response =
                service.addRegisteredMember(MEMBER_ID, GROUP_ID, "friend@example.com");

        assertThat(response.getMember().getRole()).isEqualTo(GroupMember.ROLE_MEMBER);
        assertThat(response.getGroupMemberRole()).isEqualTo(GroupMember.ROLE_MEMBER);
    }

    @Test
    void memberIsDeniedWhenTheInviteCasillaIsOn() {
        givenMemberCaller();
        when(permissionChecker.canAddMembers(GroupMember.ROLE_MEMBER,
                groupPermissionsRepository.findByGroupId(GROUP_ID).orElseThrow())).thenReturn(false);

        var exception = catchThrowableOfType(
                () -> service.addRegisteredMember(MEMBER_ID, GROUP_ID, "friend@example.com"),
                GroupPermissionDeniedException.class);

        assertThat(exception).isNotNull();
        assertThat(exception.getMessage()).contains("No podés invitar gente");
        Mockito.verifyNoInteractions(activityEventService, Mockito.mock(com.amano.backend.groups.port.DebtPort.class));
    }

    @Test
    void simulatedProfileNeedsNameAndEmail() {
        givenAdminCaller();

        var exception = catchThrowableOfType(
                () -> service.addSimulatedProfile(ADMIN_ID, GROUP_ID, " ", "invitado@example.com"),
                FieldValidationException.class);

        assertThat(exception).isNotNull();
        assertThat(exception.getFields()).containsKey("name");

        exception = catchThrowableOfType(
                () -> service.addSimulatedProfile(ADMIN_ID, GROUP_ID, "Juan", "no-es-un-correo"),
                FieldValidationException.class);

        assertThat(exception).isNotNull();
        assertThat(exception.getFields()).containsKey("identifier");
    }
}
