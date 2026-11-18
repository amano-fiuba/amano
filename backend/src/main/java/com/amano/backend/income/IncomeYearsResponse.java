package com.amano.backend.income;

import java.util.List;

/** Years (by income date) with at least one income of the person, newest first. */
public record IncomeYearsResponse(List<Integer> years) {
}
