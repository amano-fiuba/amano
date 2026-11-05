package com.amano.backend.groupexpense;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

/** Persistence of {@link GroupExpenseShare} (017). */
public interface GroupExpenseShareRepository extends JpaRepository<GroupExpenseShare, Long> {

    List<GroupExpenseShare> findByExpenseIdOrderByPositionAsc(Long expenseId);
}
