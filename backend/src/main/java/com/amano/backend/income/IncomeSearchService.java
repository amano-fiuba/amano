package com.amano.backend.income;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.amano.backend.common.FieldValidationException;
import com.amano.backend.expense.ExpenseSearchService;

import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;

/**
 * Search inside the personal incomes (specs/033-income-management, FR-008). It follows
 * {@code ExpenseSearchService}: an optional term of 2 to 100 characters, after
 * trimming, matches an income when it appears in its description or in the Spanish
 * label of its category, ignoring case and accents; the period comes from
 * {@link IncomePeriod} (year, month of that year, and an optional from/to range, all
 * of them on the income date); the result is one page, newest income date first (ties
 * by the last registered), and a page past the last one answers the last. The payment
 * method is not searched. Only the incomes of the session user are visible.
 */
@Service
public class IncomeSearchService {

    private static final Sort NEWEST_FIRST =
            Sort.by(Sort.Order.desc("incomeDate"), Sort.Order.desc("id"));
    private static final char LIKE_ESCAPE = '\\';

    private final IncomeRepository incomeRepository;
    private final IncomeService incomeService;

    public IncomeSearchService(IncomeRepository incomeRepository, IncomeService incomeService) {
        this.incomeRepository = incomeRepository;
        this.incomeService = incomeService;
    }

    /** Raw query parameters, exactly as received. */
    public record Query(String term, String year, String month, String from, String to,
            String page, String size) {
    }

    /** Validated criteria: normalized term ({@code null} = no text filter), period, page and size. */
    record Criteria(String normalizedTerm, IncomePeriod period, int page, int size) {
    }

    @Transactional(readOnly = true)
    public IncomeSearchResponse search(Long userId, Query rawQuery) {
        Criteria criteria = validate(rawQuery);
        Specification<Income> specification = specificationOf(userId, criteria);
        Page<Income> page = incomeRepository.findAll(specification,
                PageRequest.of(criteria.page() - 1, criteria.size(), NEWEST_FIRST));
        // A page past the last one (an old link, fewer incomes) shows the last one.
        if (page.getContent().isEmpty() && page.getTotalPages() > 0) {
            page = incomeRepository.findAll(specification,
                    PageRequest.of(page.getTotalPages() - 1, criteria.size(), NEWEST_FIRST));
        }
        return new IncomeSearchResponse(incomeService.withConversions(userId, page.getContent()),
                page.getNumber() + 1, criteria.size(), page.getTotalElements(),
                page.getTotalPages());
    }

    /** Years with incomes of the user, newest first. */
    @Transactional(readOnly = true)
    public List<Integer> years(Long userId) {
        return incomeRepository.findIncomeYears(userId);
    }

    static Criteria validate(Query raw) {
        Map<String, String> errors = new LinkedHashMap<>();
        String term = raw.term() == null ? "" : raw.term().strip();
        if (term.isEmpty()) {
            term = null;
        }
        else if (term.length() < ExpenseSearchService.MIN_LENGTH) {
            errors.put("q", ExpenseSearchService.TERM_TOO_SHORT);
        }
        else if (term.length() > ExpenseSearchService.MAX_LENGTH) {
            errors.put("q", ExpenseSearchService.TERM_TOO_LONG);
        }
        IncomePeriod period = null;
        try {
            period = IncomePeriod.of(raw.year(), raw.month(), raw.from(), raw.to());
        }
        catch (FieldValidationException invalidPeriod) {
            errors.putAll(invalidPeriod.getFields());
        }
        Integer page = integer(raw.page(), Integer.MAX_VALUE, "page",
                ExpenseSearchService.PAGE_INVALID, errors);
        Integer size = integer(raw.size(), ExpenseSearchService.MAX_PAGE_SIZE, "size",
                ExpenseSearchService.SIZE_INVALID, errors);
        if (!errors.isEmpty()) {
            throw new FieldValidationException(errors);
        }
        return new Criteria(term == null ? null : IncomeSearchLabels.normalize(term), period,
                page == null ? 1 : page,
                size == null ? ExpenseSearchService.DEFAULT_PAGE_SIZE : size);
    }

    /** Optional integer parameter within {@code [1, max]}; blank means absent. */
    private static Integer integer(String raw, int max, String field, String message,
            Map<String, String> errors) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            int value = Integer.parseInt(raw.strip());
            if (value >= 1 && value <= max) {
                return value;
            }
        }
        catch (NumberFormatException invalid) {
            // Falls through to the field error.
        }
        errors.put(field, message);
        return null;
    }

    private static Specification<Income> specificationOf(Long userId, Criteria criteria) {
        String term = criteria.normalizedTerm();
        String pattern = term == null ? null
                : "%" + ExpenseSearchService.escapeLike(term) + "%";
        Set<IncomeCategory> categories =
                term == null ? Set.of() : IncomeSearchLabels.categoriesMatching(term);

        return (root, query, cb) -> {
            List<Predicate> filters = new ArrayList<>();
            filters.add(cb.equal(root.get("userId"), userId));
            filters.add(cb.between(root.get("incomeDate"), criteria.period().start(),
                    criteria.period().end()));
            if (pattern == null) {
                return cb.and(filters.toArray(Predicate[]::new));
            }

            Expression<String> folded = cb.lower(cb.function("translate", String.class,
                    root.get("description"), cb.literal(ExpenseSearchService.ACCENTED),
                    cb.literal(ExpenseSearchService.PLAIN)));
            List<Predicate> matches = new ArrayList<>();
            matches.add(cb.like(folded, pattern, LIKE_ESCAPE));
            if (!categories.isEmpty()) {
                matches.add(root.get("category").in(categories));
            }
            filters.add(cb.or(matches.toArray(Predicate[]::new)));
            return cb.and(filters.toArray(Predicate[]::new));
        };
    }
}
