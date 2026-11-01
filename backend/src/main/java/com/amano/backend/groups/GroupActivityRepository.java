package com.amano.backend.groups;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Persistence for {@code group_activity} history events (paginated read; write
 * goes through {@link ActivityEventService}).
 */
public interface GroupActivityRepository extends JpaRepository<GroupActivity, Long> {

    Page<GroupActivity> findByGroupIdOrderByCreatedAtDescIdDesc(Long groupId, Pageable pageable);

    Page<GroupActivity> findByGroupIdAndEventTypeOrderByCreatedAtDescIdDesc(
            Long groupId, String eventType, Pageable pageable);

    List<GroupActivity> findTop50ByGroupIdOrderByCreatedAtDescIdDesc(Long groupId);
}
