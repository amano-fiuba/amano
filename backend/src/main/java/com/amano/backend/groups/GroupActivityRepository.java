package com.amano.backend.groups;

import java.util.Collection;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Persistence for {@code group_activity} history events (paginated read; write
 * goes through {@link ActivityEventService}). Order: {@code created_at DESC},
 * {@code id DESC} as tie-breaker (010 US9).
 */
public interface GroupActivityRepository extends JpaRepository<GroupActivity, Long> {

    Page<GroupActivity> findByGroupIdOrderByCreatedAtDescIdDesc(Long groupId, Pageable pageable);

    /** 014 FR-010: one or more event types (e.g. the "Bajas" filter). */
    Page<GroupActivity> findByGroupIdAndEventTypeInOrderByCreatedAtDescIdDesc(
            Long groupId, Collection<String> eventTypes, Pageable pageable);
}
