package com.amano.backend.groups;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Records {@link GroupActivity} rows (US history, tasks.md T021). Rows are
 * standalone (preservation pattern): only groupId is referenced, so they
 * survive later member/expense/payment changes. REQUIRES_NEW so an event is
 * preserved even when the surrounding transaction later rolls back? No —
 * callers record after their success path; default propagation keeps the event
 * atomic with the business change that produced it (D: atómico, tasks US1).
 */
@Service
public class ActivityEventService {

    private final GroupActivityRepository repository;

    public ActivityEventService(GroupActivityRepository repository) {
        this.repository = repository;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public GroupActivity record(Long groupId, GroupActivity.GroupActivityType type, Long actorUserId, String detailsJson) {
        return repository.save(new GroupActivity(groupId, type, actorUserId, detailsJson));
    }
}
