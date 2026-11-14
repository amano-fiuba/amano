package com.amano.backend.groupexpense;

/**
 * Snapshot data for recurrence state-change events recorded in the group
 * activity feed (029 FR-018). The name is captured so the feed text can survive
 * later edits of the series.
 */
public record RecurringActivityDetails(Long expenseId, String description, String status) {
}
