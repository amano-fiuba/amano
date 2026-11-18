package com.amano.backend.income;

import java.util.List;

/**
 * One page of the income search (specs/033-income-management, FR-008): the incomes of
 * the page by income date, newest first, the 1-based page returned (the last one when
 * the asked page does not exist), the page size and the totals of the whole criteria.
 */
public record IncomeSearchResponse(List<IncomeResponse> incomes, int page, int size,
        long totalElements, int totalPages) {
}
