package com.amano.backend.groups;

import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.amano.backend.groups.dto.AddMemberResponse;
import com.amano.backend.groups.dto.AdminRoleResponse;
import com.amano.backend.groups.dto.ActivityHistoryResponse;
import com.amano.backend.groups.dto.RemovalResponse;
import com.amano.backend.groups.dto.CreateGroupRequest;
import com.amano.backend.groups.dto.GroupDetailResponse;
import com.amano.backend.groups.dto.GroupResponse;
import com.amano.backend.groups.dto.GroupSummaryResponse;
import com.amano.backend.groups.dto.LeaveGroupResponse;
import com.amano.backend.groups.dto.SearchCandidatesResponse;
import com.amano.backend.groups.dto.UpdateGroupRequest;

/**
 * Group endpoints (US1, contracts/api-contracts.md). Like
 * {@code ExpenseController}, {@code POST /api/groups} always takes the creator
 * from the session principal placed by {@code JwtCookieAuthenticationFilter};
 * the payload cannot choose it.
 */
@RestController
public class GroupController {

    private final GroupService groupService;
    private final GroupMemberService memberService;
    private final GroupActivityService activityService;

    public GroupController(GroupService groupService, GroupMemberService memberService,
            GroupActivityService activityService) {
        this.groupService = groupService;
        this.memberService = memberService;
        this.activityService = activityService;
    }

    @PostMapping(path = "/api/groups", consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> createGroup(@RequestBody(required = false) CreateGroupRequest request) {
        Long userId = currentUserId();
        if (userId == null) {
            return unauthorized();
        }
        GroupResponse group = groupService.createGroup(userId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(group);
    }

    @GetMapping(path = "/api/groups", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> listGroups() {
        Long userId = currentUserId();
        if (userId == null) {
            return unauthorized();
        }
        List<GroupSummaryResponse> groups = groupService.listUserGroups(userId);
        return ResponseEntity.ok(groups);
    }

    @GetMapping(path = "/api/groups/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> getGroup(@PathVariable Long id) {
        Long userId = currentUserId();
        if (userId == null) {
            return unauthorized();
        }
        GroupDetailResponse group = groupService.getGroup(userId, id);
        return ResponseEntity.ok(group);
    }

    /** T038 (US2): partial settings update; authz in GroupService/Checker. */
    @PutMapping(path = "/api/groups/{id}", consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> updateGroup(@PathVariable Long id,
            @RequestBody(required = false) UpdateGroupRequest request) {
        Long userId = currentUserId();
        if (userId == null) {
            return unauthorized();
        }
        GroupDetailResponse group = groupService.updateGroupSettings(userId, id, request);
        return ResponseEntity.ok(group);
    }

    /** T047 (US3): add a registered friend or a simulated profile. */
    @PostMapping(path = "/api/groups/{id}/members", consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> addMember(@PathVariable Long id,
            @RequestBody(required = false) CreateGroupRequest.MemberInput request) {
        Long userId = currentUserId();
        if (userId == null) {
            return unauthorized();
        }
        if (request == null) {
            return ResponseEntity.badRequest().body(Map.of(
                    "detail", "La solicitud contiene campos inválidos",
                    "fields", Map.of("member", "Ingresá qué miembro querés agregar")));
        }
        AddMemberResponse response = "REGISTERED".equalsIgnoreCase(request.getMemberType())
                ? memberService.addRegisteredMember(userId, id, request.getIdentifier())
                : memberService.addSimulatedProfile(userId, id, request.getName(), request.getIdentifier());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /** T044 (US3): friend-only candidate search for the add-member picker. */
    @GetMapping(path = "/api/groups/{id}/candidates", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> searchCandidates(@PathVariable Long id,
            @org.springframework.web.bind.annotation.RequestParam(name = "q") String query) {
        Long userId = currentUserId();
        if (userId == null) {
            return unauthorized();
        }
        SearchCandidatesResponse response = memberService.searchCandidates(userId, id, query);
        return ResponseEntity.ok(response);
    }

    /** T053 (US9): paginated descending activity history of the group. */
    @GetMapping(path = "/api/groups/{id}/activity", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> getActivity(@PathVariable Long id,
            @org.springframework.web.bind.annotation.RequestParam(name = "page", required = false) Integer page,
            @org.springframework.web.bind.annotation.RequestParam(name = "size", required = false) Integer size,
            @org.springframework.web.bind.annotation.RequestParam(name = "eventType", required = false) String eventType) {
        Long userId = currentUserId();
        if (userId == null) {
            return unauthorized();
        }
        ActivityHistoryResponse response = activityService.getHistory(userId, id, page, size, eventType);
        return ResponseEntity.ok(response);
    }

    /** T060 (US4): admin-only soft baja of a registered member. */
    @DeleteMapping(path = "/api/groups/{id}/members/{userId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> removeMember(@PathVariable Long id, @PathVariable Long userId) {
        Long callerId = currentUserId();
        if (callerId == null) {
            return unauthorized();
        }
        RemovalResponse response = memberService.removeMember(callerId, id, userId);
        return ResponseEntity.ok(response);
    }

    /** T061 (US4): admin (or authorized member) removal of a simulated profile. */
    @DeleteMapping(path = "/api/groups/{id}/simulated-profiles/{profileId}",
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> removeSimulatedProfile(@PathVariable Long id, @PathVariable Long profileId) {
        Long callerId = currentUserId();
        if (callerId == null) {
            return unauthorized();
        }
        RemovalResponse response = memberService.removeSimulatedProfile(callerId, id, profileId);
        return ResponseEntity.ok(response);
    }

    /** T067 (US5): the caller leaves the group (soft baja, MEMBER_LEFT). */
    @PostMapping(path = "/api/groups/{id}/leave", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> leaveGroup(@PathVariable Long id) {
        Long callerId = currentUserId();
        if (callerId == null) {
            return unauthorized();
        }
        LeaveGroupResponse response = groupService.leaveGroup(callerId, id);
        return ResponseEntity.ok(response);
    }

    /** T074 (US10): assign the ADMIN role (solo ADMIN, per-group). */
    @PostMapping(path = "/api/groups/{id}/members/{userId}/admin",
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> grantAdminRole(@PathVariable Long id, @PathVariable Long userId) {
        Long callerId = currentUserId();
        if (callerId == null) {
            return unauthorized();
        }
        AdminRoleResponse response = memberService.grantAdminRole(callerId, id, userId);
        return ResponseEntity.ok(response);
    }

    /** T074 (US10): revoke the ADMIN role (solo ADMIN; last admin blocked). */
    @DeleteMapping(path = "/api/groups/{id}/members/{userId}/admin",
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> revokeAdminRole(@PathVariable Long id, @PathVariable Long userId) {
        Long callerId = currentUserId();
        if (callerId == null) {
            return unauthorized();
        }
        AdminRoleResponse response = memberService.revokeAdminRole(callerId, id, userId);
        return ResponseEntity.ok(response);
    }

    /** T084 (US8): only an ADMIN uploads/replaces the group photo (multipart). */
    @PostMapping(path = "/api/groups/{id}/photo", consumes = "multipart/form-data",
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> uploadGroupPhoto(@PathVariable Long id,
            @org.springframework.web.bind.annotation.RequestPart(value = "file",
                    required = false) org.springframework.web.multipart.MultipartFile file) {
        Long callerId = currentUserId();
        if (callerId == null) {
            return unauthorized();
        }
        com.amano.backend.groups.dto.GroupPhotoResponse response =
                groupService.uploadPhoto(callerId, id, file);
        return ResponseEntity.ok(response);
    }

    /** T084 (US8): serves the group photo bytes (members only, neutral 404). */
    @GetMapping(path = "/api/groups/{id}/photo")
    public ResponseEntity<?> getGroupPhoto(@PathVariable Long id) {
        Long callerId = currentUserId();
        if (callerId == null) {
            return unauthorized();
        }
        com.amano.backend.groups.dto.GroupPhotoContent content = groupService.getPhoto(callerId, id);
        MediaType mediaType = MediaType.valueOf(content.contentType());
        return ResponseEntity.ok()
                .contentType(mediaType)
                .header("X-Content-Type-Options", "nosniff")
                .cacheControl(org.springframework.http.CacheControl.noCache()
                        .cachePrivate()
                        .mustRevalidate())
                .body(content.data());
    }

    /** T078 (US6): active members (registered + simulated), viewable by both roles. */
    @GetMapping(path = "/api/groups/{id}/members", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> getMembers(@PathVariable Long id) {
        Long callerId = currentUserId();
        if (callerId == null) {
            return unauthorized();
        }
        return ResponseEntity.ok(groupService.getMembers(callerId, id));
    }

    private static Long currentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof Long userId) {
            return userId;
        }
        return null;
    }

    private static ResponseEntity<Map<String, Object>> unauthorized() {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(
                Map.of("detail", "Debés iniciar sesión para continuar"));
    }
}
