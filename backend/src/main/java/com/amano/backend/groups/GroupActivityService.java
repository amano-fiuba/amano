package com.amano.backend.groups;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.amano.backend.groups.dto.ActivityHistoryResponse;
import com.amano.backend.user.User;
import com.amano.backend.user.UserRepository;

/**
 * Read layer of the group history (US9, T052): paginated queries in descending
 * chronological order with an optional eventType filter; the write side is
 * {@link ActivityEventService}. The rows are preserved even when the related
 * member/expense/payment is later modified or deleted (standalone pattern).
 * Gastos y pagos are hook points: they become visible once the future expenses
 * module records its events.
 */
@Service
public class GroupActivityService {

    private final GroupActivityRepository activityRepository;
    private final UserRepository userRepository;
    private final GroupMemberRepository groupMemberRepository;

    public GroupActivityService(GroupActivityRepository activityRepository, UserRepository userRepository,
            GroupMemberRepository groupMemberRepository) {
        this.activityRepository = activityRepository;
        this.userRepository = userRepository;
        this.groupMemberRepository = groupMemberRepository;
    }

    /**
     * T053 (US9): history of a group the caller belongs to (todos los
     * miembros); 404 neutral when group/ membership do not exist.
     */
    @Transactional(readOnly = true)
    public ActivityHistoryResponse getHistory(Long callerId, Long groupId, Integer page, Integer size,
            String eventType) {
        groupMemberRepository.findByIdGroupIdAndIdUserId(groupId, callerId)
                .filter(membership -> membership.getLeftAt() == null)
                .orElseThrow(() -> new GroupNotFoundException(
                        "No existe el grupo solicitado o no sos miembro"));
        Pageable pageable = PageRequest.of(normalizedPage(page), normalizedSize(size));
        boolean filtered = eventType != null && !eventType.isBlank();
        Page<GroupActivity> result =
                filtered
                        ? activityRepository
                                .findByGroupIdAndEventTypeOrderByCreatedAtDescIdDesc(
                                        groupId, normalizeEventType(eventType), pageable)
                        : activityRepository
                                .findByGroupIdOrderByCreatedAtDescIdDesc(groupId, pageable);

        ActivityHistoryResponse response = new ActivityHistoryResponse();
        result.getContent().forEach(event ->
                response.getContent()
                        .add(toEntry(event)));
        response.setTotalElements(result.getTotalElements());
        response.setTotalPages(result.getTotalPages());
        response.setPage(pageable.getPageNumber() + 1);
        response.setSize(pageable.getPageSize());
        return response;
    }

    private ActivityHistoryResponse.Entry toEntry(GroupActivity event) {
        var actor = event.getActorUserId() == null ? null
                : userRepository.findById(event.getActorUserId()).map(user ->
                        new ActivityHistoryResponse.Actor(user.getId(), user.getFirstName(),
                                user.getLastName()))
                        .orElse(null);
        return new ActivityHistoryResponse.Entry(event.getId(), event.getEventType(), actor,
                event.getDetailsJson(), event.getCreatedAt());
    }

    private static int normalizedPage(Integer page) {
        if (page == null || page < 1) {
            return 0;
        }
        return page - 1;
    }

    private static int normalizedSize(Integer size) {
        if (size == null || size < 1) {
            return 20;
        }
        return size;
    }

    private static String normalizeEventType(String eventType) {
        try {
            return GroupActivity.GroupActivityType.valueOf(eventType.trim().toUpperCase()).name();
        }
        catch (IllegalArgumentException exception) {
            // Unknown filter: keep the value as-is for an empty result
            return eventType.trim();
        }
    }
}
