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

    /** 022 D7/T063: groups that configure a deadline; null means "never overdue". */
    List<Group> findByDebtDueDaysIsNotNull();

    /**
     * 017 FR-027: number of group expenses of the group, for {@code expensesCount}
     * of the detail. JPQL over the {@code GroupExpense} entity (017 research.md D8)
     * so {@link GroupService} needs no new dependency.
     */
    @Query("SELECT COUNT(e) FROM GroupExpense e WHERE e.groupId = :groupId")
    long countGroupExpenses(@Param("groupId") Long groupId);
}
