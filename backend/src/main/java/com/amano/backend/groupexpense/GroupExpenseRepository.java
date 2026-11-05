package com.amano.backend.groupexpense;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

/** Persistence of {@link GroupExpense} (017). */
public interface GroupExpenseRepository extends JpaRepository<GroupExpense, Long> {

    Optional<GroupExpense> findByCreatedByUserIdAndIdempotencyKey(Long createdByUserId, UUID idempotencyKey);

    Optional<GroupExpense> findByIdAndGroupId(Long id, Long groupId);
}
