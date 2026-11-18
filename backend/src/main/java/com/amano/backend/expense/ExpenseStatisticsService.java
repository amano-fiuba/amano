package com.amano.backend.expense;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.amano.backend.common.FieldValidationException;
import com.amano.backend.groupexpense.exchange.ExchangeRateProvider;
import com.amano.backend.statistics.StatisticsConverter;
import com.amano.backend.user.Currency;
import com.amano.backend.user.User;
import com.amano.backend.user.UserRepository;

/**
 * Statistics of one month (specs/032-estadisticas-gastos): the personal expenses
 * by their amount and the person's shares in group and friend expenses, each
 * summed by category after converting every subtotal to the calculation currency
 * with the quote of its expense date (FR-005..FR-012, plan D2..D8).
 */
@Service
public class ExpenseStatisticsService {

    static final String YEAR_INVALID = ExpenseSearchService.YEAR_INVALID;
    static final String MONTH_INVALID = ExpenseSearchService.MONTH_INVALID;

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private final ExpenseRepository expenseRepository;
    private final SharedExpenseStatisticsRepository sharedRepository;
    private final UserRepository userRepository;
    private final ExchangeRateProvider exchangeRateProvider;

    public ExpenseStatisticsService(ExpenseRepository expenseRepository,
            SharedExpenseStatisticsRepository sharedRepository, UserRepository userRepository,
            ExchangeRateProvider exchangeRateProvider) {
        this.expenseRepository = expenseRepository;
        this.sharedRepository = sharedRepository;
        this.userRepository = userRepository;
        this.exchangeRateProvider = exchangeRateProvider;
    }

    @Transactional(readOnly = true)
    public ExpenseStatisticsResponse statistics(Long userId, String rawYear, String rawMonth) {
        YearMonth period = validate(rawYear, rawMonth);
        LocalDate from = period.atDay(1);
        LocalDate to = period.atEndOfMonth();
        StatisticsConverter converter = new StatisticsConverter(calculationCurrency(userId),
                exchangeRateProvider);

        ExpenseStatisticsResponse.Breakdown personal = breakdown(
                expenseRepository.sumByCategoryCurrencyAndDate(userId, from, to), converter);
        ExpenseStatisticsResponse.Breakdown group = breakdown(
                sharedRepository.sumSharesByCategoryCurrencyAndDate(userId, from, to), converter);
        // The combined total adds the rounded totals, so it matches what is shown (plan D7).
        BigDecimal total = new BigDecimal(personal.total()).add(new BigDecimal(group.total()));

        return new ExpenseStatisticsResponse(period.getYear(), period.getMonthValue(),
                converter.target(), money(total), converter.excluded(), personal, group,
                sharedRepository.findYears(userId));
    }

    /** Both parameters are required: year 1..9999 and month 1..12 (FR-013, plan D10). */
    static YearMonth validate(String rawYear, String rawMonth) {
        Map<String, String> errors = new LinkedHashMap<>();
        Integer year = integer(rawYear, 1, 9999);
        Integer month = integer(rawMonth, 1, 12);
        if (year == null) {
            errors.put("year", YEAR_INVALID);
        }
        if (month == null) {
            errors.put("month", MONTH_INVALID);
        }
        if (!errors.isEmpty()) {
            throw new FieldValidationException(errors);
        }
        return YearMonth.of(year, month);
    }

    private static Integer integer(String raw, int min, int max) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            int value = Integer.parseInt(raw.strip());
            return value >= min && value <= max ? value : null;
        }
        catch (NumberFormatException invalid) {
            return null;
        }
    }

    /**
     * The first preferred currency in catalog order, ARS without preferences
     * (FR-008, plan D5): the preferences are a set, so they have no order of their own.
     */
    Currency calculationCurrency(Long userId) {
        Set<Currency> preferred = userRepository.findById(userId)
                .map(User::getCurrencies)
                .orElse(Set.of());
        return Arrays.stream(Currency.values())
                .filter(preferred::contains)
                .findFirst()
                .orElse(Currency.ARS);
    }

    /**
     * Sums the converted subtotals by category, then derives the total, the
     * percentages and the order (FR-011, FR-012). Rounding happens only here, at
     * the end, over the unrounded sums (plan D7).
     */
    private static ExpenseStatisticsResponse.Breakdown breakdown(List<CategorySubtotal> subtotals,
            StatisticsConverter converter) {
        Map<ExpenseCategory, BigDecimal> sums = new EnumMap<>(ExpenseCategory.class);
        for (CategorySubtotal subtotal : subtotals) {
            converter.convert(subtotal.currency(), subtotal.date(), subtotal.amount(),
                            subtotal.count())
                    .ifPresent(amount -> sums.merge(subtotal.category(), amount, BigDecimal::add));
        }
        BigDecimal total = sums.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        List<ExpenseStatisticsResponse.CategoryAmount> categories = sums.entrySet().stream()
                .filter(entry -> entry.getValue().signum() > 0)
                .sorted(Comparator.<Map.Entry<ExpenseCategory, BigDecimal>, BigDecimal>comparing(
                                Map.Entry::getValue).reversed()
                        .thenComparing(entry -> entry.getKey().ordinal()))
                .map(entry -> new ExpenseStatisticsResponse.CategoryAmount(entry.getKey(),
                        money(entry.getValue()), percentage(entry.getValue(), total)))
                .toList();
        return new ExpenseStatisticsResponse.Breakdown(money(total), categories);
    }

    private static String money(BigDecimal amount) {
        return amount.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private static String percentage(BigDecimal amount, BigDecimal total) {
        return amount.multiply(HUNDRED).divide(total, 1, RoundingMode.HALF_UP).toPlainString();
    }
}
