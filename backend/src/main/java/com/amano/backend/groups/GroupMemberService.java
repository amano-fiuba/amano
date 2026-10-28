package com.amano.backend.groups;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.amano.backend.common.FieldValidationException;
import com.amano.backend.groups.dto.AddMemberResponse;
import com.amano.backend.groups.dto.SearchCandidatesResponse;
import com.amano.backend.groups.port.FriendshipPort;
import com.amano.backend.user.User;
import com.amano.backend.user.UserRepository;

/**
 * Member addition rules (US3, tasks T045/T046/T048). Registered members may
 * only come from the caller's friendship relation (FriendshipPort; never the
 * whole user base) and always join with role MEMBER. Simulated profiles (name +
 * email) are stored WITHOUT a group_members row (no user account). Every
 * addition passes through {@link GroupPermissionChecker}: ADMIN always; MEMBER
 * only when members_cannot_invite = false. Each success records MEMBER_ADDED
 * via {@link ActivityEventService}. Rejection reasons match spec "Alcance y
 * limitaciones": ya es miembro, no es amigo, casilla de invitación.
 */
@Service
public class GroupMemberService {

    private static final String EMAIL_PATTERN =
            "^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$";

    private final GroupRepository groupRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final SimulatedProfileRepository simulatedProfileRepository;
    private final GroupPermissionsRepository groupPermissionsRepository;
    private final FriendshipPort friendshipPort;
    private final UserRepository userRepository;
    private final GroupPermissionChecker permissionChecker;
    private final ActivityEventService activityEventService;

    public GroupMemberService(GroupRepository groupRepository,
            GroupMemberRepository groupMemberRepository,
            SimulatedProfileRepository simulatedProfileRepository,
            GroupPermissionsRepository groupPermissionsRepository,
            FriendshipPort friendshipPort,
            UserRepository userRepository,
            GroupPermissionChecker permissionChecker,
            ActivityEventService activityEventService) {
        this.groupRepository = groupRepository;
        this.groupMemberRepository = groupMemberRepository;
        this.simulatedProfileRepository = simulatedProfileRepository;
        this.groupPermissionsRepository = groupPermissionsRepository;
        this.friendshipPort = friendshipPort;
        this.userRepository = userRepository;
        this.permissionChecker = permissionChecker;
        this.activityEventService = activityEventService;
    }

    @Transactional
    public AddMemberResponse addRegisteredMember(Long callerId, Long groupId, String emailRaw) {
        GroupMember callerMembership = activeCallerMembership(callerId, groupId);
        GroupPermissions permissions = permissionsOf(groupId);
        if (!permissionChecker.canAddMembers(callerMembership.getRole(), permissions)) {
            throw new GroupPermissionDeniedException("No podés invitar gente a este grupo");
        }

        String email = emailRaw == null ? "" : emailRaw.trim().toLowerCase();
        if (email.isEmpty()) {
            throw new FieldValidationException(Map.of("identifier", "Ingresá el correo del amigo"));
        }
        if (!friendshipPort.isFriend(callerId, email)) {
            throw new NotFriendException("La persona que querés agregar no está entre tus amigos");
        }
        User friend = userRepository.findByEmail(email)
                .orElseThrow(() -> new FieldValidationException(Map.of("identifier",
                        "No existe una cuenta con ese correo")));
        Optional<GroupMember> existingRow =
                groupMemberRepository.findByIdGroupIdAndIdUserId(groupId, friend.getId());
        if (existingRow.isPresent() && existingRow.get().getLeftAt() == null) {
            throw new AlreadyMemberException();
        }
        if (existingRow.isPresent()) {
            // re-add a previously removed member: the row is reused, the baja is undone
            GroupMember row = existingRow.get();
            row.setRole(GroupMember.ROLE_MEMBER);
            row.setLeftAt(null);
            groupMemberRepository.save(row);
        }
        else {
            groupMemberRepository.save(new GroupMember(friend.getId(), groupId, GroupMember.ROLE_MEMBER));
        }
        activityEventService.record(groupId, GroupActivity.GroupActivityType.MEMBER_ADDED,
                callerId, "{\"type\":\"REGISTERED\",\"email\":\"" + friend.getEmail() + "\"}");

        return memberResponse(callerMembership.getRole(),
                new AddMemberResponse.MemberInfo(friend.getId(), "REGISTERED",
                        friend.getFirstName() + " " + friend.getLastName(), friend.getEmail(),
                        GroupMember.ROLE_MEMBER));
    }

    @Transactional
    public AddMemberResponse addSimulatedProfile(Long callerId, Long groupId, String nameRaw,
            String emailRaw) {
        GroupMember callerMembership = activeCallerMembership(callerId, groupId);
        GroupPermissions permissions = permissionsOf(groupId);
        if (!permissionChecker.canAddMembers(callerMembership.getRole(), permissions)) {
            throw new GroupPermissionDeniedException("No podés invitar gente a este grupo");
        }

        String name = nameRaw == null ? "" : nameRaw.trim();
        String email = emailRaw == null ? "" : emailRaw.trim().toLowerCase();
        LinkedHashMap<String, String> errors = new LinkedHashMap<>();
        if (name.isEmpty()) {
            errors.put("name", "Ingresá el nombre del perfil simulado");
        }
        if (email.isEmpty()) {
            errors.put("identifier", "Ingresá el correo del perfil simulado");
        }
        else if (email.length() > 191) {
            errors.put("identifier", "El correo es demasiado largo (máximo 191 caracteres)");
        }
        else if (!email.matches(EMAIL_PATTERN)) {
            errors.put("identifier", "El correo no es válido");
        }
        if (!errors.isEmpty()) {
            throw new FieldValidationException(errors);
        }

        Optional<GroupMember> existingRegistered =
                userRepository.findByEmail(email).flatMap(
                        user -> groupMemberRepository.findByIdGroupIdAndIdUserId(groupId, user.getId()));
        boolean registeredActive = existingRegistered.isPresent()
                && existingRegistered.get().getLeftAt() == null;
        if (registeredActive || simulatedProfileRepository.existsByGroupIdAndEmail(groupId, email)) {
            throw new AlreadyMemberException();
        }

        SimulatedProfile profile = new SimulatedProfile();
        profile.setGroupId(groupId);
        profile.setName(name);
        profile.setEmail(email);
        SimulatedProfile saved = simulatedProfileRepository.save(profile);

        activityEventService.record(groupId, GroupActivity.GroupActivityType.MEMBER_ADDED,
                callerId, "{\"type\":\"SIMULATED\",\"name\":\"" + name + "\",\"email\":\"" + email + "\"}");

        return memberResponse(callerMembership.getRole(),
                new AddMemberResponse.MemberInfo(saved.getId(), "SIMULATED", name, email,
                        GroupMember.ROLE_MEMBER));
    }

    /** T044 (US3): ONLY the caller's friends; never the whole user base. */
    @Transactional(readOnly = true)
    public SearchCandidatesResponse searchCandidates(Long callerId, Long groupId, String queryRaw) {
        GroupMember callerMembership = activeCallerMembership(callerId, groupId);
        GroupPermissions permissions = permissionsOf(groupId);
        if (!permissionChecker.canAddMembers(callerMembership.getRole(), permissions)) {
            throw new GroupPermissionDeniedException("No podés invitar gente a este grupo");
        }
        String query = queryRaw == null ? "" : queryRaw.trim().toLowerCase();
        if (query.length() < 2) {
            throw new FieldValidationException(Map.of("q", "Ingresá al menos 2 caracteres para buscar"));
        }
        return groupRepository.findById(groupId)
                .map(group -> searchCandidatesInGroup(callerId, groupId, query))
                .orElseThrow(() -> new GroupNotFoundException(
                        "No existe el grupo solicitado o no sos miembro"));
    }

    private SearchCandidatesResponse searchCandidatesInGroup(Long callerId, Long groupId, String query) {
        SearchCandidatesResponse response = new SearchCandidatesResponse();
        friendshipPort.friendEmailsOf(callerId).stream()
                .filter(email -> email != null && matchesQuery(email, query))
                .filter(email -> userRepository.findByEmail(email.trim().toLowerCase()).isPresent())
                .filter(email -> notMemberOfGroup(email.trim().toLowerCase(), groupId))
                .sorted()
                .forEach(email -> userRepository.findByEmail(email.trim().toLowerCase())
                        .ifPresent(user -> response.getContent()
                                .add(new SearchCandidatesResponse.Candidate(user.getId(),
                                        user.getFirstName(), user.getLastName(), user.getEmail()))));
        return response;
    }

    private static boolean matchesQuery(String email, String query) {
        return email.trim().toLowerCase().contains(query);
    }

    private boolean notMemberOfGroup(String email, Long groupId) {
        return !simulatedProfileRepository.existsByGroupIdAndEmail(groupId, email)
                && userRepository.findByEmail(email)
                        .map(user -> groupMemberRepository
                                .findByIdGroupIdAndIdUserId(groupId, user.getId())
                                .map(m -> m.getLeftAt() != null) // active member becomes excluded
                                .orElse(true))
                        .orElse(true);
    }

    private GroupMember activeCallerMembership(Long callerId, Long groupId) {
        groupRepository.findById(groupId)
                .orElseThrow(() -> new GroupNotFoundException(
                        "No existe el grupo solicitado o no sos miembro"));
        GroupMember membership =
                groupMemberRepository.findByIdGroupIdAndIdUserId(groupId, callerId)
                        .orElseThrow(() -> new GroupNotFoundException(
                                "No existe el grupo solicitado o no sos miembro"));
        if (membership.getLeftAt() != null) {
            throw new GroupNotFoundException("No existe el grupo solicitado o no sos miembro");
        }
        return membership;
    }

    private GroupPermissions permissionsOf(Long groupId) {
        return groupPermissionsRepository.findByGroupId(groupId).orElse(null);
    }

    private static AddMemberResponse memberResponse(String callerRole, AddMemberResponse.MemberInfo member) {
        AddMemberResponse response = new AddMemberResponse();
        response.setMember(member);
        response.setGroupMemberRole(callerRole);
        return response;
    }
}
