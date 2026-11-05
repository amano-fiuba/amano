package com.amano.backend.groups;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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

    /** Most recent event of a group (015 FR-007). */
    interface LastActivity {

        Long getGroupId();

        OffsetDateTime getLastActivityAt();
    }

    /** 015 FR-007: one aggregate query for all the groups of a listing. */
    @Query("""
            select a.groupId as groupId, max(a.createdAt) as lastActivityAt
            from GroupActivity a
            where a.groupId in :groupIds
            group by a.groupId
            """)
    List<LastActivity> findLastActivityByGroupIds(@Param("groupIds") Collection<Long> groupIds);
}
