package com.amano.backend.expense;

import java.text.Normalizer;
import java.time.LocalDate;
import java.time.YearMonth;
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

import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;

/**
 * Search inside the personal activity (specs/031-buscar-en-actividad-personal). A
 * term of 2 to 100 characters, after trimming, matches an expense when it appears
 * in its description or in the Spanish label of its category or payment method,
 * ignoring case and accents (FR-003/FR-004). The term is optional: without it the
 * search lists every expense of the chosen year (FR-012). It covers every expense
 * of the session user, optionally restricted to one year, and one month of that
 * year, of the expense date (FR-005..FR-007, FR-020), newest registration first,
 * one page at a time (FR-021..FR-023, plan D11).
 */
@Service
public class ExpenseSearchService {

    static final int MIN_LENGTH = 2;
    static final int MAX_LENGTH = 100;
    static final int DEFAULT_PAGE_SIZE = 20;
    static final int MAX_PAGE_SIZE = 50;

    static final String TERM_TOO_SHORT = "Ingresá al menos 2 caracteres para buscar";
    static final String TERM_TOO_LONG = "La búsqueda admite hasta 100 caracteres";
    static final String YEAR_INVALID = "El año no es válido";
    static final String MONTH_INVALID = "El mes no es válido";
    static final String MONTH_WITHOUT_YEAR = "Elegí un año para filtrar por mes";
    static final String PAGE_INVALID = "La página no es válida";
    static final String SIZE_INVALID = "El tamaño de página no es válido";

    private static final Sort NEWEST_FIRST =
            Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"));

    /**
     * Accented letters folded in the database with {@code translate}; {@link #PLAIN}
     * holds the same letters without their diacritic, position by position. No
     * {@code unaccent} extension is required (plan D2).
     */
    static final String ACCENTED = "ÁÀÂÄÃÉÈÊËÍÌÎÏÓÒÔÖÕÚÙÛÜÑÇáàâäãéèêëíìîïóòôöõúùûüñç";
    static final String PLAIN = stripAccents(ACCENTED);

    private static final char LIKE_ESCAPE = '\\';

    private final ExpenseRepository expenseRepository;
    private final ExpenseService expenseService;

    public ExpenseSearchService(ExpenseRepository expenseRepository,
            ExpenseService expenseService) {
        this.expenseRepository = expenseRepository;
        this.expenseService = expenseService;
    }

    /**
     * Validated criteria: normalized term ({@code null} = no text filter), optional
     * year ({@code null} = all years) and month ({@code null} = the whole year), and
     * the 1-based page with its size.
     */
    record Criteria(String normalizedTerm, Integer year, Integer month, int page, int size) {
    }

    /** Raw query parameters, exactly as received. */
    public record Query(String term, String year, String month, String page, String size) {
    }

    @Transactional(readOnly = true)
    public ExpenseSearchResponse search(Long userId, Query rawQuery) {
        Criteria criteria = validate(rawQuery);
        Specification<Expense> specification = specificationOf(userId, criteria);
        Page<Expense> page = expenseRepository.findAll(specification,
                PageRequest.of(criteria.page() - 1, criteria.size(), NEWEST_FIRST));
        // A page past the last one (an old link, fewer expenses) shows the last one (plan D11).
        if (page.getContent().isEmpty() && page.getTotalPages() > 0) {
            page = expenseRepository.findAll(specification,
                    PageRequest.of(page.getTotalPages() - 1, criteria.size(), NEWEST_FIRST));
        }
        return new ExpenseSearchResponse(expenseService.withConversions(userId, page.getContent()),
                page.getNumber() + 1, criteria.size(), page.getTotalElements(), page.getTotalPages());
    }

    /** Years with expenses of the user, newest first (FR-006). */
    @Transactional(readOnly = true)
    public List<Integer> years(Long userId) {
        return expenseRepository.findExpenseYears(userId);
    }

    static Criteria validate(Query raw) {
        Map<String, String> errors = new LinkedHashMap<>();
        String term = raw.term() == null ? "" : raw.term().strip();
        if (term.isEmpty()) {
            term = null;
        }
        else if (term.length() < MIN_LENGTH) {
            errors.put("q", TERM_TOO_SHORT);
        }
        else if (term.length() > MAX_LENGTH) {
            errors.put("q", TERM_TOO_LONG);
        }
        Integer year = integer(raw.year(), 1, 9999, "year", YEAR_INVALID, errors);
        Integer month = integer(raw.month(), 1, 12, "month", MONTH_INVALID, errors);
        if (month != null && year == null && !errors.containsKey("year")) {
            errors.put("month", MONTH_WITHOUT_YEAR);
        }
        Integer page = integer(raw.page(), 1, Integer.MAX_VALUE, "page", PAGE_INVALID, errors);
        Integer size = integer(raw.size(), 1, MAX_PAGE_SIZE, "size", SIZE_INVALID, errors);
        if (!errors.isEmpty()) {
            throw new FieldValidationException(errors);
        }
        return new Criteria(term == null ? null : ExpenseSearchLabels.normalize(term), year, month,
                page == null ? 1 : page, size == null ? DEFAULT_PAGE_SIZE : size);
    }

    /** Optional integer parameter within {@code [min, max]}; blank means absent. */
    private static Integer integer(String raw, int min, int max, String field, String message,
            Map<String, String> errors) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            int value = Integer.parseInt(raw.strip());
            if (value >= min && value <= max) {
                return value;
            }
        }
        catch (NumberFormatException invalid) {
            // Falls through to the field error.
        }
        errors.put(field, message);
        return null;
    }

    private static Specification<Expense> specificationOf(Long userId, Criteria criteria) {
        String term = criteria.normalizedTerm();
        String pattern = term == null ? null : "%" + escapeLike(term) + "%";
        Set<ExpenseCategory> categories =
                term == null ? Set.of() : ExpenseSearchLabels.categoriesMatching(term);
        Set<PaymentMethod> methods =
                term == null ? Set.of() : ExpenseSearchLabels.paymentMethodsMatching(term);

        return (root, query, cb) -> {
            List<Predicate> filters = new ArrayList<>();
            filters.add(cb.equal(root.get("userId"), userId));
            if (criteria.year() != null) {
                LocalDate from;
                LocalDate to;
                if (criteria.month() != null) {
                    YearMonth month = YearMonth.of(criteria.year(), criteria.month());
                    from = month.atDay(1);
                    to = month.atEndOfMonth();
                }
                else {
                    from = LocalDate.of(criteria.year(), 1, 1);
                    to = LocalDate.of(criteria.year(), 12, 31);
                }
                filters.add(cb.between(root.get("expenseDate"), from, to));
            }
            if (pattern == null) {
                return cb.and(filters.toArray(Predicate[]::new));
            }

            Expression<String> folded = cb.lower(cb.function("translate", String.class,
                    root.get("description"), cb.literal(ACCENTED), cb.literal(PLAIN)));
            List<Predicate> matches = new ArrayList<>();
            matches.add(cb.like(folded, pattern, LIKE_ESCAPE));
            if (!categories.isEmpty()) {
                matches.add(root.get("category").in(categories));
            }
            if (!methods.isEmpty()) {
                matches.add(root.get("paymentMethod").in(methods));
            }
            filters.add(cb.or(matches.toArray(Predicate[]::new)));
            return cb.and(filters.toArray(Predicate[]::new));
        };
    }

    /** {@code %}, {@code _} and the escape itself are searched literally (edge cases). */
    static String escapeLike(String term) {
        StringBuilder escaped = new StringBuilder(term.length());
        for (char character : term.toCharArray()) {
            if (character == LIKE_ESCAPE || character == '%' || character == '_') {
                escaped.append(LIKE_ESCAPE);
            }
            escaped.append(character);
        }
        return escaped.toString();
    }

    private static String stripAccents(String letters) {
        StringBuilder plain = new StringBuilder(letters.length());
        for (char letter : letters.toCharArray()) {
            String decomposed = Normalizer.normalize(String.valueOf(letter), Normalizer.Form.NFD);
            plain.append(decomposed.charAt(0));
        }
        return plain.toString();
    }
}
