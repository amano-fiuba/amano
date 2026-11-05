package com.amano.backend.groups;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Persistence for {@code group_members}. Active membership = left_at IS NULL
 * (V7/data-model.md). Includes the countActiveByGroupId helper used together
 * with {@link SimulatedProfileRepository} to compute membersCount.
 */
public interface GroupMemberRepository extends JpaRepository<GroupMember, GroupMember.GroupMemberId> {

    List<GroupMember> findByIdGroupId(Long groupId);

    List<GroupMember> findByIdGroupIdAndLeftAtIsNull(Long groupId);

    Optional<GroupMember> findByIdGroupIdAndIdUserId(Long groupId, Long userId);

    boolean existsByIdGroupIdAndIdUserId(Long groupId, Long userId);

    long countByIdGroupIdAndRole(Long groupId, String role);

    @Query("SELECT COUNT(m) FROM GroupMember m WHERE m.id.groupId = :groupId AND m.leftAt IS NULL")
    long countActiveByGroupId(@Param("groupId") Long groupId);

    /** True when both accounts are active members of at least one common group. */
    @Query("""
            SELECT CASE WHEN COUNT(m) > 0 THEN true ELSE false END
            FROM GroupMember m, GroupMember other
            WHERE m.id.groupId = other.id.groupId
              AND m.id.userId = :a AND m.leftAt IS NULL
              AND other.id.userId = :b AND other.leftAt IS NULL
            """)
    boolean shareActiveGroup(@Param("a") Long a, @Param("b") Long b);

    /** Admins still active, for the last-admin guard (US4/US5). */
    @Query("""
            SELECT COUNT(m) FROM GroupMember m
            WHERE m.id.groupId = :groupId AND m.role = 'ADMIN' AND m.leftAt IS NULL
            """)
    long countActiveAdminsByGroupId(@Param("groupId") Long groupId);
}
