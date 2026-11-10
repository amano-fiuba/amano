package com.amano.backend.groups;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.amano.backend.groups.dto.ReminderSettingsRequest;
import com.amano.backend.groups.dto.ReminderSettingsResponse;

/**
 * Reminder settings endpoints (025 T026). Additive to the existing group
 * contract; the caller always comes from the session principal.
 */
@RestController
public class GroupReminderSettingsController {

    private final GroupReminderSettingsService settingsService;

    public GroupReminderSettingsController(GroupReminderSettingsService settingsService) {
        this.settingsService = settingsService;
    }

    @GetMapping(path = "/api/groups/{id}/reminder-settings", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> getSettings(@PathVariable Long id) {
        Long userId = currentUserId();
        if (userId == null) {
            return unauthorized();
        }
        ReminderSettingsResponse response = settingsService.getSettings(userId, id);
        return ResponseEntity.ok(response);
    }

    @PutMapping(path = "/api/groups/{id}/reminder-settings", consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> updateSettings(@PathVariable Long id,
            @RequestBody(required = false) ReminderSettingsRequest request) {
        Long userId = currentUserId();
        if (userId == null) {
            return unauthorized();
        }
        ReminderSettingsResponse response = settingsService.updateSettings(userId, id, request);
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
