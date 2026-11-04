package com.amano.backend.groups;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.amano.backend.common.FieldValidationException;
import com.amano.backend.groups.dto.ActivityHistoryResponse;
import com.amano.backend.user.User;
import com.amano.backend.user.UserRepository;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Read layer of the group history (010 US9; 014): paginated queries in
 * descending chronological order with an optional filter by one or more event
 * types; the write side is {@link ActivityEventService}. Rows are preserved even
 * when the related member, expense or payment is later modified or deleted.
 */
@Service
public class GroupActivityService {

    static final int DEFAULT_SIZE = 20;
    static final int MAX_SIZE = 100;
    static final String INVALID_EVENT_TYPE_MESSAGE = "El tipo de evento no es válido";

    private final GroupActivityRepository activityRepository;
    private final UserRepository userRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final GroupPermissionChecker groupPermissionChecker;
    private final GroupPermissionsRepository groupPermissionsRepository;
    private final ObjectMapper objectMapper;

    public GroupActivityService(GroupActivityRepository activityRepository, UserRepository userRepository,
            GroupMemberRepository groupMemberRepository,
            GroupPermissionChecker groupPermissionChecker,
            GroupPermissionsRepository groupPermissionsRepository,
            ObjectMapper objectMapper) {
        this.activityRepository = activityRepository;
        this.userRepository = userRepository;
        this.groupMemberRepository = groupMemberRepository;
        this.groupPermissionChecker = groupPermissionChecker;
        this.groupPermissionsRepository = groupPermissionsRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * History of a group the caller belongs to (todos los miembros activos);
     * 404 neutral when the group or the membership do not exist. {@code eventType}
     * accepts one or more comma-separated types (014 FR-010); an unknown type is
     * a 400. {@code size} is capped at {@value #MAX_SIZE} (014 FR-011).
     */
    @Transactional(readOnly = true)
    public ActivityHistoryResponse getHistory(Long callerId, Long groupId, Integer page, Integer size,
            String eventType) {
        GroupMember membership = groupMemberRepository.findByIdGroupIdAndIdUserId(groupId, callerId)
                .filter(row -> row.getLeftAt() == null)
                .orElseThrow(() -> new GroupNotFoundException(
                        "No existe el grupo solicitado o no sos miembro"));
        if (!groupPermissionChecker.canViewGroup(membership.getRole(),
                permissionsOf(groupId))) {
            throw new GroupPermissionDeniedException("No podés ver el historial de este grupo");
        }
        Set<String> eventTypes = parseEventTypes(eventType);
        Pageable pageable = PageRequest.of(normalizedPage(page), normalizedSize(size));
        Page<GroupActivity> result = eventTypes.isEmpty()
                ? activityRepository.findByGroupIdOrderByCreatedAtDescIdDesc(groupId, pageable)
                : activityRepository.findByGroupIdAndEventTypeInOrderByCreatedAtDescIdDesc(
                        groupId, eventTypes, pageable);

        Map<Long, User> actors = actorsOf(result.getContent());
        ActivityHistoryResponse response = new ActivityHistoryResponse();
        result.getContent().forEach(event -> response.getContent().add(toEntry(event, actors)));
        response.setTotalElements(result.getTotalElements());
        response.setTotalPages(result.getTotalPages());
        response.setPage(pageable.getPageNumber() + 1);
        response.setSize(pageable.getPageSize());
        return response;
    }

    /** 014 FR-013: the actors of a page are resolved in a single query. */
    private Map<Long, User> actorsOf(List<GroupActivity> events) {
        Set<Long> ids = events.stream()
                .map(GroupActivity::getActorUserId)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (ids.isEmpty()) {
            return Map.of();
        }
        return userRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(User::getId, Function.identity(), (first, second) -> first));
    }

    private ActivityHistoryResponse.Entry toEntry(GroupActivity event, Map<Long, User> actors) {
        User user = event.getActorUserId() == null ? null : actors.get(event.getActorUserId());
        ActivityHistoryResponse.Actor actor = user == null ? null
                : new ActivityHistoryResponse.Actor(user.getId(), user.getFirstName(), user.getLastName());
        return new ActivityHistoryResponse.Entry(event.getId(), event.getEventType(), actor,
                parseDetails(event.getDetailsJson()), event.getCreatedAt());
    }

    /** 014 D9: rows stored before 014 may hold invalid JSON; they degrade to null. */
    private JsonNode parseDetails(String detailsJson) {
        if (detailsJson == null || detailsJson.isBlank()) {
            return null;
        }
        try {
            JsonNode node = objectMapper.readTree(detailsJson);
            return node != null && node.isObject() ? node : null;
        }
        catch (JacksonException exception) {
            return null;
        }
    }

    private GroupPermissions permissionsOf(Long groupId) {
        return groupPermissionsRepository.findByGroupId(groupId).orElse(null);
    }

    private static Set<String> parseEventTypes(String eventType) {
        Set<String> types = new LinkedHashSet<>();
        if (eventType == null || eventType.isBlank()) {
            return types;
        }
        for (String raw : eventType.split(",")) {
            String value = raw.trim();
            if (value.isEmpty()) {
                continue;
            }
            try {
                types.add(GroupActivity.GroupActivityType.valueOf(value.toUpperCase(Locale.ROOT)).name());
            }
            catch (IllegalArgumentException exception) {
                throw new FieldValidationException(Map.of("eventType", INVALID_EVENT_TYPE_MESSAGE));
            }
        }
        return types;
    }

    private static int normalizedPage(Integer page) {
        if (page == null || page < 1) {
            return 0;
        }
        return page - 1;
    }

    private static int normalizedSize(Integer size) {
        if (size == null || size < 1) {
            return DEFAULT_SIZE;
        }
        return Math.min(size, MAX_SIZE);
    }
}
