package com.amano.backend.expense;

import java.util.List;

/**
 * One page of the personal activity search (031, FR-021..FR-023): the expenses of
 * the page, newest first, the 1-based page returned (the last one when the asked
 * page does not exist), the page size and the totals of the whole criteria.
 */
public record ExpenseSearchResponse(List<ExpenseResponse> expenses, int page, int size,
        long totalElements, int totalPages) {
}
