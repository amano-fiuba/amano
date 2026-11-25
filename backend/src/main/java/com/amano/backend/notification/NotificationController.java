package com.amano.backend.notification;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.amano.backend.notification.dto.NotificationPreferencesRequest;
import com.amano.backend.notification.dto.NotificationPreferencesResponse;

/**
 * Five REST endpoints of feature 035 (contracts/notifications-api.md). The
 * caller always comes from the session principal: a client-sent user id is
 * never accepted. Helpers mirror the controllers already in the project.
 */
@RestController
public class NotificationController {

    private final NotificationService service;
    private final NotificationPreferenceService preferenceService;

    public NotificationController(NotificationService service, NotificationPreferenceService preferenceService) {
        this.service = service;
        this.preferenceService = preferenceService;
    }

    @GetMapping(path = "/api/v1/notifications", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> list(
            @RequestParam(name = "limit", required = false) Integer limit,
            @RequestParam(name = "before", required = false) String before,
            @RequestParam(name = "order", required = false) String order) {
        Long userId = currentUserId();
        if (userId == null) {
            return unauthorized();
        }
        return ResponseEntity.ok(service.list(userId, limit, before, order));
    }

    @PatchMapping(path = "/api/v1/notifications/{id}/read", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> markRead(@PathVariable Long id) {
        Long userId = currentUserId();
        if (userId == null) {
            return unauthorized();
        }
        return ResponseEntity.ok(service.markRead(userId, id));
    }

    @PatchMapping(path = "/api/v1/notifications/read-all", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> markAllRead() {
        Long userId = currentUserId();
        if (userId == null) {
            return unauthorized();
        }
        return ResponseEntity.ok(Map.of("updated", service.markAllRead(userId)));
    }

    @GetMapping(path = "/api/v1/notifications/preferences", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> getPreferences() {
        Long userId = currentUserId();
        if (userId == null) {
            return unauthorized();
        }
        NotificationPreferencesResponse preferences = preferenceService.get(userId);
        return ResponseEntity.ok(preferences);
    }

    @PutMapping(path = "/api/v1/notifications/preferences", consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> updatePreferences(
            @RequestBody(required = false) NotificationPreferencesRequest request) {
        Long userId = currentUserId();
        if (userId == null) {
            return unauthorized();
        }
        return ResponseEntity.ok(preferenceService.update(userId, request));
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
