package com.amano.backend.groups;

import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.amano.backend.groups.dto.AddMemberResponse;
import com.amano.backend.groups.dto.ActivityHistoryResponse;
import com.amano.backend.groups.dto.CreateGroupRequest;
import com.amano.backend.groups.dto.GroupDetailResponse;
import com.amano.backend.groups.dto.GroupResponse;
import com.amano.backend.groups.dto.GroupSummaryResponse;
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
