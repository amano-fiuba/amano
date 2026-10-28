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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.amano.backend.groups.dto.CreateGroupRequest;
import com.amano.backend.groups.dto.GroupDetailResponse;
import com.amano.backend.groups.dto.GroupResponse;
import com.amano.backend.groups.dto.GroupSummaryResponse;

/**
 * Group endpoints (US1, contracts/api-contracts.md). Like
 * {@code ExpenseController}, {@code POST /api/groups} always takes the creator
 * from the session principal placed by {@code JwtCookieAuthenticationFilter};
 * the payload cannot choose it.
 */
@RestController
public class GroupController {

    private final GroupService groupService;

    public GroupController(GroupService groupService) {
        this.groupService = groupService;
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
