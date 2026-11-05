package com.amano.backend.groups;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.amano.backend.common.FieldValidationException;
import com.amano.backend.groups.dto.CreateGroupRequest;
import com.amano.backend.groups.dto.GroupDetailResponse;
import com.amano.backend.groups.dto.GroupMemberView;
import com.amano.backend.groups.dto.GroupResponse;
import com.amano.backend.groups.dto.GroupSummaryResponse;
import com.amano.backend.groups.dto.LeaveGroupResponse;
import com.amano.backend.groups.dto.UpdateGroupRequest;
import com.amano.backend.groups.port.DebtPort;
import com.amano.backend.groups.port.FriendshipPort;
import com.amano.backend.user.User;
import com.amano.backend.user.UserAvatar;
import com.amano.backend.user.UserAvatarRepository;
import com.amano.backend.user.UserRepository;

/**
 * Groups business rules (US1, tasks T027). Creation is atomic: the group, the
 * creator as ADMIN, the additional member (registered friend or simulated
 * profile) and the default permissions row (all casillas false) are written in
 * one transaction, or nothing at all (FR: "no queda nada a medias").
 */
@Service
public class GroupService {

    private final GroupRepository groupRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final SimulatedProfileRepository simulatedProfileRepository;
    private final GroupPermissionsRepository groupPermissionsRepository;
    private final FriendshipPort friendshipPort;
    private final UserRepository userRepository;
    private final GroupPermissionChecker permissionChecker;
    private final ActivityEventService activityEventService;
    private final DebtPort debtPort;
    private final UserAvatarRepository userAvatarRepository;
    private final GroupPhotoRepository groupPhotoRepository;
    private final com.amano.backend.user.AvatarValidator avatarValidator;

    public GroupService(GroupRepository groupRepository,
            GroupMemberRepository groupMemberRepository,
            SimulatedProfileRepository simulatedProfileRepository,
            GroupPermissionsRepository groupPermissionsRepository,
            FriendshipPort friendshipPort,
            UserRepository userRepository,
            GroupPermissionChecker permissionChecker,
            ActivityEventService activityEventService,
            DebtPort debtPort,
            UserAvatarRepository userAvatarRepository,
            GroupPhotoRepository groupPhotoRepository,
            com.amano.backend.user.AvatarValidator avatarValidator) {
        this.groupRepository = groupRepository;
        this.groupMemberRepository = groupMemberRepository;
        this.simulatedProfileRepository = simulatedProfileRepository;
        this.groupPermissionsRepository = groupPermissionsRepository;
        this.friendshipPort = friendshipPort;
        this.userRepository = userRepository;
        this.permissionChecker = permissionChecker;
        this.activityEventService = activityEventService;
        this.debtPort = debtPort;
        this.userAvatarRepository = userAvatarRepository;
        this.groupPhotoRepository = groupPhotoRepository;
        this.avatarValidator = avatarValidator;
    }

    @Transactional
    public GroupResponse createGroup(Long creatorId, CreateGroupRequest request) {
        String name = request.getName() == null ? "" : request.getName().trim();
        if (name.isEmpty()) {
            throw new FieldValidationException(Map.of("name", "El nombre del grupo es obligatorio"));
        }

        CreateGroupRequest.MemberInput memberInput = request.getMember();
        if (memberInput == null) {
            throw new FieldValidationException(Map.of("member",
                    "Agregá al menos un miembro adicional (amigo registrado o perfil simulado)"));
        }

        // Everything is validated/resolved BEFORE any insert: a failure leaves
        // no partial rows even without depending on the rollback (atomicity).
        String memberType = nullToBlank(memberInput.getMemberType());
        if (!"SIMULATED".equalsIgnoreCase(memberType) && !"REGISTERED".equalsIgnoreCase(memberType)) {
            throw new FieldValidationException(Map.of("member", "El tipo de miembro no es válido"));
        }
        User friend = null;
        if ("REGISTERED".equalsIgnoreCase(memberType)) {
            friend = resolveRegisterdFriend(creatorId, memberInput.getIdentifier());
        }
        else {
            validateSimulatedMember(memberInput);
        }

        Group group = new Group();
        group.setName(name);
        group.setDescription(trimOrNull(request.getDescription()));
        Group savedGroup = groupRepository.save(group);

        groupMemberRepository.save(new GroupMember(creatorId, savedGroup.getId(), GroupMember.ROLE_ADMIN));

        Map<String, Object> initialMemberDetails;
        if (friend == null) {
            SimulatedProfile profile = new SimulatedProfile();
            profile.setGroupId(savedGroup.getId());
            profile.setName(memberInput.getName().trim());
            profile.setEmail(memberInput.getIdentifier().trim());
            SimulatedProfile savedProfile = simulatedProfileRepository.save(profile);
            initialMemberDetails = ActivityEventService.simulatedMemberDetails(
                    savedProfile == null ? profile : savedProfile);
        }
        else {
            groupMemberRepository.save(new GroupMember(friend.getId(), savedGroup.getId(), GroupMember.ROLE_MEMBER));
            initialMemberDetails = ActivityEventService.registeredMemberDetails(friend);
        }

        groupPermissionsRepository.save(defaultPermissions(savedGroup.getId()));

        // alta of the initial member, in the same transaction (014 FR-002)
        initialMemberDetails.put("atCreation", true);
        activityEventService.record(savedGroup.getId(), GroupActivity.GroupActivityType.MEMBER_ADDED,
                creatorId, initialMemberDetails);

        User creator = userRepository.findById(creatorId).orElseThrow();
        return buildResponse(savedGroup, creator, 2);
    }

    private User resolveRegisterdFriend(Long creatorId, String identifier) {
        String email = identifier == null ? "" : identifier.trim();
        if (email.isEmpty()) {
            throw new FieldValidationException(Map.of("member", "Ingresá el correo del amigo"));
        }
        if (!friendshipPort.isFriend(creatorId, email)) {
            throw new NotFriendException(
                    "La persona que querés agregar no está entre tus amigos");
        }
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new FieldValidationException(Map.of("member",
                        "No existe una cuenta con ese correo")));
    }

    private void validateSimulatedMember(CreateGroupRequest.MemberInput memberInput) {
        String email = memberInput.getIdentifier() == null ? "" : memberInput.getIdentifier().trim();
        String profileName = memberInput.getName() == null ? "" : memberInput.getName().trim();
        LinkedHashMap<String, String> errors = new LinkedHashMap<>();
        if (profileName.isEmpty()) {
            errors.put("name", "Ingresá el nombre del perfil simulado");
        }
        if (email.isEmpty()) {
            errors.put("identifier", "Ingresá el correo del perfil simulado");
        }
        if (!errors.isEmpty()) {
            throw new FieldValidationException(errors);
        }
    }

    private static GroupPermissions defaultPermissions(Long groupId) {
        GroupPermissions permissions = new GroupPermissions();
        permissions.setGroupId(groupId);
        return permissions;
    }

    private static String trimOrNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static String nullToBlank(String value) {
        return value == null ? "" : value;
    }

    private static GroupResponse buildResponse(Group group, User creator, int membersCount) {
        GroupResponse response = new GroupResponse();
        response.setId(group.getId());
        response.setName(group.getName());
        response.setDescription(group.getDescription());
        response.setPhoto(group.getPhoto());
        response.setCreatedAt(group.getCreatedAt());
        response.setUpdatedAt(group.getUpdatedAt());
        response.setCreator(new GroupResponse.Creator(creator.getId(), creator.getFirstName(),
                creator.getLastName(), creator.getEmail()));
        response.setMembersCount(membersCount);
        response.setCreatorRole(GroupMember.ROLE_ADMIN);
        return response;
    }

    /**
     * T037/T039 (US2): partial update of name, description and the permission
     * casillas. Authorization always goes through {@link GroupPermissionChecker}
     * (ADMIN always; MEMBER only when members_cannot_edit_settings = false);
     * server-side enforcement so calling the endpoint directly cannot bypass the
     * casillas. Unspecified casillas keep their stored value.
     */
    @Transactional
    public GroupDetailResponse updateGroupSettings(Long userId, Long groupId, UpdateGroupRequest request) {
        Group group = groupRepository.findById(groupId)
                .orElseThrow(() -> new GroupNotFoundException(
                        "No existe el grupo solicitado o no sos miembro"));
        GroupMember membership = activeMembership(userId, groupId);
        GroupPermissions permissions = groupPermissionsRepository.findByGroupId(groupId)
                .orElseGet(() -> defaultPermissions(groupId));

        if (!permissionChecker.canEditSettings(membership.getRole(), permissions)) {
            throw new GroupPermissionDeniedException("No podés editar los ajustes de este grupo");
        }

        if (request == null) {
            throw new FieldValidationException(Map.of("name", "El nombre del grupo es obligatorio"));
        }

        boolean permissionsChanged = false;
        UpdateGroupRequest.PermissionsInput input = request.getPermissions();
        if (input != null) {
            if (input.getMembersCannotMakeExpenses() != null
                    && input.getMembersCannotMakeExpenses() != permissions.isMembersCannotMakeExpenses()) {
                permissions.setMembersCannotMakeExpenses(input.getMembersCannotMakeExpenses());
                permissionsChanged = true;
            }
            if (input.getMembersCannotInvite() != null
                    && input.getMembersCannotInvite() != permissions.isMembersCannotInvite()) {
                permissions.setMembersCannotInvite(input.getMembersCannotInvite());
                permissionsChanged = true;
            }
            if (input.getMembersCannotEditSettings() != null
                    && input.getMembersCannotEditSettings() != permissions.isMembersCannotEditSettings()) {
                permissions.setMembersCannotEditSettings(input.getMembersCannotEditSettings());
                permissionsChanged = true;
            }
            if (input.getMembersCanManageSimulatedProfiles() != null
                    && input.getMembersCanManageSimulatedProfiles() != permissions
                            .isMembersCanManageSimulatedProfiles()) {
                permissions.setMembersCanManageSimulatedProfiles(input.getMembersCanManageSimulatedProfiles());
                permissionsChanged = true;
            }
            if (input.getMembersCanGenerateExpensesForOthers() != null
                    && input.getMembersCanGenerateExpensesForOthers() != permissions
                            .isMembersCanGenerateExpensesForOthers()) {
                permissions.setMembersCanGenerateExpensesForOthers(
                        input.getMembersCanGenerateExpensesForOthers());
                permissionsChanged = true;
            }
            if (input.getPaymentsRequireValidation() != null
                    && input.getPaymentsRequireValidation() != permissions.isPaymentsRequireValidation()) {
                permissions.setPaymentsRequireValidation(input.getPaymentsRequireValidation());
                permissionsChanged = true;
            }
        }

        if (request.getDescription() != null) {
            group.setDescription(trimOrNull(request.getDescription()));
        }
        if (request.getName() != null) {
            String name = request.getName().trim();
            if (name.isEmpty()) {
                throw new FieldValidationException(Map.of("name", "El nombre del grupo es obligatorio"));
            }
            group.setName(name);
        }

        if (permissionsChanged) {
            groupPermissionsRepository.save(permissions);
            activityEventService.record(groupId, GroupActivity.GroupActivityType.PERMISSION_CHANGED,
                    userId, Map.of("fields", List.of("permissions")));
        }

        return toDetail(userId, group, membership);
    }

    /**
     * T078 (US6): active members of the group (group_members with left_at
     * NULL + the simulated profiles, which are members by group_id alone)
     * with display name, avatar URL (existing user-avatar mechanism; NULL →
     * the UI renders the gray default) and the per-group role. Viewed by both
     * roles — the decision goes through {@link GroupPermissionChecker#canViewMembers}.
     */
    @Transactional(readOnly = true)
    public List<GroupMemberView> getMembers(Long userId, Long groupId) {
        groupRepository.findById(groupId)
                .orElseThrow(() -> new GroupNotFoundException(
                        "No existe el grupo solicitado o no sos miembro"));
        GroupMember membership = activeMembership(userId, groupId);
        GroupPermissions permissions =
                groupPermissionsRepository.findByGroupId(groupId).orElse(null);
        if (!permissionChecker.canViewMembers(membership.getRole(), permissions)) {
            throw new GroupPermissionDeniedException("No podés ver los miembros de este grupo");
        }

        List<GroupMemberView> members = new java.util.ArrayList<>();
        for (GroupMember row : groupMemberRepository.findByIdGroupIdAndLeftAtIsNull(groupId)) {
            User user = userRepository.findById(row.getId().getUserId()).orElse(null);
            if (user == null) {
                continue;
            }
            String fullName = user.getFirstName() + (user.getLastName() != null
                    ? " " + user.getLastName()
                    : "");
            String avatarUrl = userAvatarRepository.findById(user.getId())
                    .<String>map(stored -> "/api/users/" + user.getId() + "/avatar")
                    .orElse(null); // no custom avatar → NULL → gray default in the UI
            members.add(new GroupMemberView(user.getId(), null, fullName, avatarUrl,
                    row.getRole()));
        }
        for (SimulatedProfile simulated : simulatedProfileRepository.findByGroupId(groupId)) {
            members.add(new GroupMemberView(null, simulated.getId(), simulated.getName(),
                    null, GroupMember.ROLE_MEMBER));
        }
        return members;
    }

    /**
     * T084/T085 (US8): the ADMIN uploads or replaces the group photo. The
     * format/size gate REUSES the existing {@code AvatarValidator} (content
     * based JPG/PNG; oversize → 413 via its MaxUploadSize semantics; the
     * contract's "5MB/400" wording is the documented drift resolved in favor
     * of reusing the validator). On any failure the previous photo is
     * conserved (nothing is touched before validation). Success stores the
     * bytes in {@code group_photos} (V11) and the URL in {@code groups.photo},
     * and records PHOTO_CHANGED.
     */
    @Transactional
    public com.amano.backend.groups.dto.GroupPhotoResponse uploadPhoto(Long userId, Long groupId,
            org.springframework.web.multipart.MultipartFile photo) {
        Group group = groupRepository.findById(groupId)
                .orElseThrow(() -> new GroupNotFoundException(
                        "No existe el grupo solicitado o no sos miembro"));
        GroupMember membership = activeMembership(userId, groupId);
        GroupPermissions permissions = groupPermissionsRepository.findByGroupId(groupId).orElse(null);
        if (!permissionChecker.canManagePhoto(membership.getRole(), permissions)) {
            throw new GroupPermissionDeniedException(
                    "Solo un administrador puede cambiar la foto del grupo");
        }
        if (photo == null || photo.isEmpty()) {
            throw new com.amano.backend.common.FieldValidationException(java.util.Map.of("photo",
                    "Debés seleccionar un archivo de imagen"));
        }
        avatarValidator.validate(photo); // 400 format / 413 size (existing mechanism)

        GroupPhoto stored = groupPhotoRepository.findById(groupId).orElseGet(GroupPhoto::new);
        stored.setGroupId(groupId);
        stored.setContentType(avatarValidator.realContentType(photo));
        try {
            stored.setData(photo.getBytes());
        }
        catch (java.io.IOException exception) {
            throw new java.io.UncheckedIOException(exception);
        }
        GroupPhoto saved = groupPhotoRepository.saveAndFlush(stored); // timestamp set by Hibernate

        String url = photoUrlOf(saved);
        group.setPhoto(url);
        groupRepository.save(group);
        activityEventService.record(groupId, GroupActivity.GroupActivityType.PHOTO_CHANGED,
                userId, Map.of());
        return new com.amano.backend.groups.dto.GroupPhotoResponse(url, saved.getUpdatedAt());
    }

    /** Served bytes for GET /api/groups/{id}/photo: members only (neutral 404 otherwise). */
    @Transactional(readOnly = true)
    public com.amano.backend.groups.dto.GroupPhotoContent getPhoto(Long userId, Long groupId) {
        groupRepository.findById(groupId)
                .orElseThrow(() -> new GroupNotFoundException(
                        "No existe el grupo solicitado o no sos miembro"));
        activeMembership(userId, groupId);
        GroupPhoto stored = groupPhotoRepository.findById(groupId)
                .orElseThrow(() -> new GroupNotFoundException(
                        "Este grupo no tiene foto"));
        return new com.amano.backend.groups.dto.GroupPhotoContent(
                stored.getContentType(), stored.getData());
    }

    private static String photoUrlOf(GroupPhoto stored) {
        return "/api/groups/" + stored.getGroupId() + "/photo?v="
                + Long.toHexString(com.amano.backend.user.UserAccountResponse
                        .versionOf(stored.getData()));
    }

    /**
     * T066 (US5): the user leaves the group. Blocked by pending balances via
     * {@link DebtPort} (spec motive 2, own wording) or by the last-admin guard
     * (spec motive 4). Success completes left_at (soft baja; the row is
     * preserved), records MEMBER_LEFT (NOT MEMBER_REMOVED) and the group is
     * NEVER dissolved — it persists for the remaining members.
     */
    @Transactional
    public LeaveGroupResponse leaveGroup(Long userId, Long groupId) {
        groupRepository.findById(groupId)
                .orElseThrow(() -> new GroupNotFoundException(
                        "No existe el grupo solicitado o no sos miembro"));
        GroupMember membership = activeMembership(userId, groupId);
        if (debtPort.hasPendingDebts(groupId, userId)) {
            throw new PendingDebtsException(
                    "No podés abandonar el grupo: tenés saldos pendientes con otros miembros");
        }
        if (GroupMember.ROLE_ADMIN.equals(membership.getRole())
                && groupMemberRepository.countActiveAdminsByGroupId(groupId) <= 1) {
            throw new LastAdminException();
        }

        membership.setLeftAt(java.time.OffsetDateTime.now());
        groupMemberRepository.save(membership);
        activityEventService.record(groupId, GroupActivity.GroupActivityType.MEMBER_LEFT,
                userId, Map.of());
        return new LeaveGroupResponse("Se abandonó el grupo. Tus gastos previos se conservan.",
                true);
    }

    /**
     * Active membership of the user in the group, or the neutral 404 when the
     * row does not exist or left_at is set (non-members never see the group).
     */
    private GroupMember activeMembership(Long userId, Long groupId) {
        GroupMember membership =
                groupMemberRepository.findByIdGroupIdAndIdUserId(groupId, userId)
                        .orElseThrow(() -> new GroupNotFoundException(
                                "No existe el grupo solicitado o no sos miembro"));
        if (membership.getLeftAt() != null) {
            throw new GroupNotFoundException("No existe el grupo solicitado o no sos miembro");
        }
        if (!permissionChecker.canViewGroup(membership.getRole(),
                groupPermissionsRepository.findByGroupId(groupId).orElse(null))) {
            throw new GroupPermissionDeniedException("No podés ver este grupo");
        }
        return membership;
    }

    /** T029: groups where the caller is an active member, newest first. */
    @Transactional(readOnly = true)
    public List<GroupSummaryResponse> listUserGroups(Long userId) {
        List<Group> groups = groupRepository.findActiveGroupsOfUser(userId);
        Map<Long, OffsetDateTime> lastActivity = activityEventService.lastActivityByGroup(
                groups.stream().map(Group::getId).toList());
        return groups.stream()
                .map(group -> toSummary(userId, group, lastActivity))
                .toList();
    }

    /**
     * T030: detail of a group of the caller. 404 when the group does not exist
     * or the caller is not a member (one neutral case; same as US6 read rules).
     */
    @Transactional(readOnly = true)
    public GroupDetailResponse getGroup(Long userId, Long groupId) {
        Group group = groupRepository.findById(groupId)
                .orElseThrow(() -> new GroupNotFoundException(
                        "No existe el grupo solicitado o no sos miembro"));
        Optional<GroupMember> membershipOpt =
                groupMemberRepository.findByIdGroupIdAndIdUserId(groupId, userId);
        boolean active = membershipOpt.isPresent() && membershipOpt.get().getLeftAt() == null;
        if (!active) {
            throw new GroupNotFoundException("No existe el grupo solicitado o no sos miembro");
        }
        return toDetail(userId, group, membershipOpt.get());
    }

    private GroupSummaryResponse toSummary(Long userId, Group group,
            Map<Long, OffsetDateTime> lastActivity) {
        GroupSummaryResponse summary = new GroupSummaryResponse();
        summary.setId(group.getId());
        summary.setName(group.getName());
        summary.setDescription(group.getDescription());
        summary.setPhoto(group.getPhoto());
        summary.setCreatedAt(group.getCreatedAt());
        summary.setLastActivityAt(lastActivity.getOrDefault(group.getId(), group.getCreatedAt()));
        summary.setMemberCount(activeMemberCount(group.getId()));
        groupMemberRepository.findByIdGroupIdAndIdUserId(group.getId(), userId)
                .ifPresent(membership -> summary.setCurrentUserRole(membership.getRole()));
        return summary;
    }

    private GroupDetailResponse toDetail(Long userId, Group group, GroupMember membership) {
        GroupDetailResponse detail = new GroupDetailResponse();
        detail.setId(group.getId());
        detail.setName(group.getName());
        detail.setDescription(group.getDescription());
        detail.setPhoto(group.getPhoto());
        detail.setCreatedAt(group.getCreatedAt());
        detail.setUpdatedAt(group.getUpdatedAt());
        detail.setActiveForUser(membership.getLeftAt() == null);
        detail.setMemberCount(activeMemberCount(group.getId()));
        detail.setCurrentUserRole(membership.getRole());
        detail.setPermissions(groupPermissionsRepository.findByGroupId(group.getId()).orElse(null));
        detail.setLastActivity(null); // history events come with later stories (T021 hook points)
        detail.setExpensesCount(0);
        return detail;
    }

    /** Active members = group_members with left_at NULL + the group's simulated profiles (T006). */
    private long activeMemberCount(Long groupId) {
        return groupMemberRepository.countActiveByGroupId(groupId)
                + simulatedProfileRepository.countByGroupId(groupId);
    }
}
