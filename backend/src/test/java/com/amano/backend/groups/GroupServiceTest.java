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
    private ActivityEventService activityEventService;
    private GroupService service;

    @BeforeEach
    void setUp() {
        groupRepository = Mockito.mock(GroupRepository.class);
        activityEventService = Mockito.mock(ActivityEventService.class);
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
                Mockito.mock(GroupPermissionChecker.class), activityEventService,
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
        assertThat(permissions.getValue().isMembersCanEditGroupData()).isFalse();
        assertThat(permissions.getValue().isMembersCanManageSimulatedProfiles()).isFalse();
        assertThat(permissions.getValue().isMembersCanGenerateExpensesForOthers()).isFalse();
        assertThat(permissions.getValue().isPaymentsRequireValidation()).isFalse();

        // 014 FR-002: the alta of the initial member is recorded, marked atCreation
        assertThat(initialMemberDetails()).containsEntry("type", "REGISTERED")
                .containsEntry("userId", 9L)
                .containsEntry("name", "Ana López")
                .containsEntry("email", "friend@example.com")
                .containsEntry("atCreation", true);
    }

    @SuppressWarnings("unchecked")
    private java.util.Map<String, Object> initialMemberDetails() {
        org.mockito.ArgumentCaptor<java.util.Map<String, Object>> details =
                org.mockito.ArgumentCaptor.forClass(java.util.Map.class);
        verify(activityEventService).record(org.mockito.ArgumentMatchers.eq(42L),
                org.mockito.ArgumentMatchers.eq(GroupActivity.GroupActivityType.MEMBER_ADDED),
                org.mockito.ArgumentMatchers.eq(CREATOR_ID), details.capture());
        return details.getValue();
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

        assertThat(initialMemberDetails()).containsEntry("type", "SIMULATED")
                .containsEntry("name", "Invitado de prueba")
                .containsEntry("email", "invitado@example.com")
                .containsEntry("atCreation", true);
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
        Mockito.verifyNoInteractions(activityEventService);
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

    private static CreateGroupRequest.MemberInput registered(String email) {
        CreateGroupRequest.MemberInput member = new CreateGroupRequest.MemberInput();
        member.setMemberType("REGISTERED");
        member.setIdentifier(email);
        return member;
    }

    private static CreateGroupRequest.MemberInput simulated(String email) {
        CreateGroupRequest.MemberInput member = new CreateGroupRequest.MemberInput();
        member.setMemberType("SIMULATED");
        member.setIdentifier(email);
        member.setName("Invitado");
        return member;
    }

    private static CreateGroupRequest requestWithMembers(CreateGroupRequest.MemberInput... members) {
        CreateGroupRequest request = new CreateGroupRequest();
        request.setName("Viaje");
        request.setMembers(List.of(members));
        return request;
    }

    /** 023 FR-029: a repeated friend counts once. */
    @Test
    void aRepeatedFriendJoinsOnlyOnce() {
        when(friendshipPort.isFriend(CREATOR_ID, "friend@example.com")).thenReturn(true);
        when(userRepository.findByEmail("friend@example.com")).thenReturn(
                Optional.of(registeredUser(9L, "friend@example.com")));

        GroupResponse response = service.createGroup(CREATOR_ID,
                requestWithMembers(registered("friend@example.com"), registered("friend@example.com")));

        assertThat(response.getMembersCount()).isEqualTo(2);
    }

    /** 023 FR-029: an invalid member anywhere in the list creates nothing. */
    @Test
    void anInvalidMemberAtTheEndOfTheListCreatesNothing() {
        when(friendshipPort.isFriend(CREATOR_ID, "friend@example.com")).thenReturn(true);
        when(userRepository.findByEmail("friend@example.com")).thenReturn(
                Optional.of(registeredUser(9L, "friend@example.com")));
        when(friendshipPort.isFriend(CREATOR_ID, "stranger@example.com")).thenReturn(false);

        catchThrowableOfType(
                () -> service.createGroup(CREATOR_ID, requestWithMembers(
                        registered("friend@example.com"), registered("stranger@example.com"))),
                NotFriendException.class);

        verify(groupRepository, never()).save(any(Group.class));
    }

    /** 023 FR-029: the unique index would otherwise surface as a 500. */
    @Test
    void repeatedSimulatedEmailsAreRejected() {
        FieldValidationException exception = catchThrowableOfType(
                () -> service.createGroup(CREATOR_ID, requestWithMembers(
                        simulated("invitado@example.com"), simulated("INVITADO@example.com"))),
                FieldValidationException.class);

        assertThat(exception.getFields()).containsKey("member");
        verify(groupRepository, never()).save(any(Group.class));
    }

    /** 023 FR-028: {@code members} wins when both arrive. */
    @Test
    void membersWinsOverTheSingleMember() {
        when(friendshipPort.isFriend(CREATOR_ID, "friend@example.com")).thenReturn(true);
        when(userRepository.findByEmail("friend@example.com")).thenReturn(
                Optional.of(registeredUser(9L, "friend@example.com")));
        CreateGroupRequest request = requestWithMembers(registered("friend@example.com"));
        request.setMember(simulated("ignorado@example.com"));

        GroupResponse response = service.createGroup(CREATOR_ID, request);

        assertThat(response.getMembersCount()).isEqualTo(2);
        verify(simulatedProfileRepository, never()).save(any());
    }

    @Test
    void anEmptyMembersListIsRejected() {
        FieldValidationException exception = catchThrowableOfType(
                () -> service.createGroup(CREATOR_ID, requestWithMembers()),
                FieldValidationException.class);

        assertThat(exception.getFields()).containsKey("member");
    }

    /** 017 T016 (FR-027): the group detail reports the real number of group expenses. */
    @Test
    void theGroupDetailReportsTheRealNumberOfExpenses() {
        Group group = new Group();
        org.springframework.test.util.ReflectionTestUtils.setField(group, "id", 42L);
        group.setName("Depto");
        when(groupRepository.findById(42L)).thenReturn(Optional.of(group));
        when(groupMemberRepository.findByIdGroupIdAndIdUserId(42L, CREATOR_ID))
                .thenReturn(Optional.of(new GroupMember(CREATOR_ID, 42L, GroupMember.ROLE_ADMIN)));
        when(groupRepository.countGroupExpenses(42L)).thenReturn(5L);

        assertThat(service.getGroup(CREATOR_ID, 42L).getExpensesCount()).isEqualTo(5L);
    }
}
