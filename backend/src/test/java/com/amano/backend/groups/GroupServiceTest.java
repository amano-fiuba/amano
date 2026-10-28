package com.amano.backend.groups;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import com.amano.backend.common.FieldValidationException;
import com.amano.backend.groups.NotFriendException;
import com.amano.backend.groups.dto.CreateGroupRequest;
import com.amano.backend.groups.dto.GroupResponse;
import com.amano.backend.user.User;

/**
 * Unit tests of {@link GroupService#createGroup} (T023, US1): the creator is
 * ADMIN, an additional member is mandatory (registered friend via
 * FriendshipPort or simulated profile), the name is only required and
 * non-empty (decision 2 — no uniqueness/length assertions) and a failure
 * leaves no partial rows.
 */
class GroupServiceTest {

    private static final Long CREATOR_ID = 7L;

    private GroupRepository groupRepository;
    private GroupMemberRepository groupMemberRepository;
    private SimulatedProfileRepository simulatedProfileRepository;
    private GroupPermissionsRepository groupPermissionsRepository;
    private com.amano.backend.groups.port.FriendshipPort friendshipPort;
    private com.amano.backend.user.UserRepository userRepository;
    private GroupService service;

    @BeforeEach
    void setUp() {
        groupRepository = Mockito.mock(GroupRepository.class);
        groupMemberRepository = Mockito.mock(GroupMemberRepository.class);
        simulatedProfileRepository = Mockito.mock(SimulatedProfileRepository.class);
        groupPermissionsRepository = Mockito.mock(GroupPermissionsRepository.class);
        friendshipPort = Mockito.mock(com.amano.backend.groups.port.FriendshipPort.class);
        userRepository = Mockito.mock(com.amano.backend.user.UserRepository.class);

        when(groupRepository.save(any(Group.class))).thenAnswer(invocation -> {
            Group saved = invocation.getArgument(0);
            org.springframework.test.util.ReflectionTestUtils.setField(saved, "id", 42L);
            return saved;
        });
        when(groupMemberRepository.save(any(GroupMember.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(simulatedProfileRepository.save(any(SimulatedProfile.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(groupPermissionsRepository.save(any(GroupPermissions.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(userRepository.findById(CREATOR_ID)).thenReturn(
                Optional.of(registeredUser(CREATOR_ID, "creator@example.com")));

        service = new GroupService(groupRepository, groupMemberRepository, simulatedProfileRepository,
                groupPermissionsRepository, friendshipPort, userRepository,
                Mockito.mock(GroupPermissionChecker.class), Mockito.mock(ActivityEventService.class),
                Mockito.mock(com.amano.backend.groups.port.DebtPort.class),
                Mockito.mock(com.amano.backend.user.UserAvatarRepository.class),
                Mockito.mock(GroupPhotoRepository.class),
                Mockito.mock(com.amano.backend.user.AvatarValidator.class));
    }

    private static User registeredUser(Long id, String email) {
        User user = new User();
        org.springframework.test.util.ReflectionTestUtils.setField(user, "id", id);
        user.setFirstName("Ana");
        user.setLastName("López");
        user.setEmail(email);
        return user;
    }

    private static CreateGroupRequest.MemberInput registeredMember() {
        CreateGroupRequest.MemberInput member = new CreateGroupRequest.MemberInput();
        member.setMemberType("REGISTERED");
        member.setIdentifier("friend@example.com");
        return member;
    }

    private static CreateGroupRequest registeredMemberPrompt() {
        return request("Amigos para viajar", registeredMember());
    }

    private static CreateGroupRequest request(String name, CreateGroupRequest.MemberInput member) {
        CreateGroupRequest request = new CreateGroupRequest();
        request.setName(name);
        request.setDescription("  Viaje al sur  ");
        request.setMember(member);
        return request;
    }

    @Test
    void creatorBecomesAdminAndFriendJoinsAsMember() {
        when(friendshipPort.isFriend(CREATOR_ID, "friend@example.com")).thenReturn(true);
        when(userRepository.findByEmail("friend@example.com")).thenReturn(
                Optional.of(registeredUser(9L, "friend@example.com")));

        GroupResponse response = service.createGroup(CREATOR_ID, registeredMemberPrompt());

        assertThat(response.getId()).isEqualTo(42L);
        assertThat(response.getName()).isEqualTo("Amigos para viajar");
        assertThat(response.getDescription()).isEqualTo("Viaje al sur");
        assertThat(response.getMembersCount()).isEqualTo(2);
        assertThat(response.getCreatorRole()).isEqualTo("ADMIN");
        assertThat(response.getCreator().getId()).isEqualTo(CREATOR_ID);

        List<GroupMember> members = captorMembers();
        assertThat(members).hasSize(2);
        assertThat(members.get(0).getId().getUserId()).isEqualTo(CREATOR_ID);
        assertThat(members.get(0).getRole()).isEqualTo(GroupMember.ROLE_ADMIN);
        assertThat(members.get(1).getId().getUserId()).isEqualTo(9L);
        assertThat(members.get(1).getRole()).isEqualTo(GroupMember.ROLE_MEMBER);

        // Defaults at creation: one permissions row, all casillas false (T007).
        org.mockito.ArgumentCaptor<GroupPermissions> permissions =
                org.mockito.ArgumentCaptor.forClass(GroupPermissions.class);
        verify(groupPermissionsRepository).save(permissions.capture());
        assertThat(permissions.getValue().getGroupId()).isEqualTo(42L);
        assertThat(permissions.getValue().isMembersCannotMakeExpenses()).isFalse();
        assertThat(permissions.getValue().isMembersCannotInvite()).isFalse();
        assertThat(permissions.getValue().isMembersCannotEditSettings()).isFalse();
        assertThat(permissions.getValue().isMembersCanManageSimulatedProfiles()).isFalse();
        assertThat(permissions.getValue().isMembersCanGenerateExpensesForOthers()).isFalse();
        assertThat(permissions.getValue().isPaymentsRequireValidation()).isFalse();
    }

    private List<GroupMember> captorMembers() {
        org.mockito.ArgumentCaptor<GroupMember> captor = org.mockito.ArgumentCaptor.forClass(GroupMember.class);
        verify(groupMemberRepository, Mockito.atLeast(1)).save(captor.capture());
        return captor.getAllValues();
    }

    @Test
    void simulatedProfileIsStoredWithoutUserAccount() {
        CreateGroupRequest.MemberInput member = new CreateGroupRequest.MemberInput();
        member.setMemberType("SIMULATED");
        member.setIdentifier("invitado@example.com");
        member.setName("Invitado de prueba");

        GroupResponse response = service.createGroup(CREATOR_ID, request("Proyecto X", member));

        assertThat(response.getMembersCount()).isEqualTo(2);
        List<GroupMember> members = captorMembers();
        assertThat(members).hasSize(1); // only the creator: a simulated profile never gets a group_members row
        assertThat(members.get(0).getId().getUserId()).isEqualTo(CREATOR_ID);

        org.mockito.ArgumentCaptor<SimulatedProfile> profile =
                org.mockito.ArgumentCaptor.forClass(SimulatedProfile.class);
        verify(simulatedProfileRepository).save(profile.capture());
        assertThat(profile.getValue().getGroupId()).isEqualTo(42L);
        assertThat(profile.getValue().getName()).isEqualTo("Invitado de prueba");
        assertThat(profile.getValue().getEmail()).isEqualTo("invitado@example.com");
    }

    @Test
    void blankNameIsRejectedWithoutPartialRows() {
        FieldValidationException exception = catchThrowableOfType(
                () -> service.createGroup(CREATOR_ID, request("   ", registeredMember())),
                FieldValidationException.class);

        assertThat(exception.getFields()).containsKey("name");
        verify(groupRepository, never()).save(any(Group.class));
        verify(groupMemberRepository, never()).save(any(GroupMember.class));
        verify(simulatedProfileRepository, never()).save(any(SimulatedProfile.class));
    }

    @Test
    void missingAdditionalMemberIsRejected() {
        FieldValidationException exception = catchThrowableOfType(
                () -> service.createGroup(CREATOR_ID, request("Solo yo", null)),
                FieldValidationException.class);

        assertThat(exception.getFields()).containsKey("member");
        verify(groupRepository, never()).save(any(Group.class));
    }

    @Test
    void registeredMemberOutsideFriendshipIsRejected() {
        when(friendshipPort.isFriend(CREATOR_ID, "stranger@example.com")).thenReturn(false);
        CreateGroupRequest.MemberInput member = new CreateGroupRequest.MemberInput();
        member.setMemberType("REGISTERED");
        member.setIdentifier("stranger@example.com");

        NotFriendException exception = catchThrowableOfType(
                () -> service.createGroup(CREATOR_ID, request("Grupo", member)),
                NotFriendException.class);

        assertThat(exception.getMessage()).isNotBlank();
        verify(userRepository, never()).findByEmail(any());
        verify(groupRepository, never()).save(any(Group.class));
    }
}
