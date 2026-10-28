package com.amano.backend.groups;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Persistence for {@code groups}. The user's groups query joins active
 * {@code group_members} rows (left_at IS NULL, data-model.md).
 */
public interface GroupRepository extends JpaRepository<Group, Long> {

    @Query("""
            SELECT g FROM Group g
            WHERE EXISTS (SELECT 1 FROM GroupMember m
                          WHERE m.id.groupId = g.id
                            AND m.id.userId = :userId
                            AND m.leftAt IS NULL)
            ORDER BY g.createdAt DESC, g.id DESC
            """)
    List<Group> findActiveGroupsOfUser(@Param("userId") Long userId);

    Optional<Group> findById(Long id);
}
