package com.amano.backend.statistics;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.amano.backend.groupexpense.exchange.ExchangeRateProvider;
import com.amano.backend.income.IncomeCategory;
import com.amano.backend.income.IncomeRepository;
import com.amano.backend.user.Currency;

/**
 * Received incomes of one month by category (specs/034-income-statistics,
 * FR-008..FR-013): each subtotal is converted with the quote of its income date,
 * summed per category, and rounded only at the end.
 */
@Service
public class IncomeStatisticsService {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private final IncomeRepository incomeRepository;
    private final ExchangeRateProvider exchangeRateProvider;

    public IncomeStatisticsService(IncomeRepository incomeRepository,
            ExchangeRateProvider exchangeRateProvider) {
        this.incomeRepository = incomeRepository;
        this.exchangeRateProvider = exchangeRateProvider;
    }

    @Transactional(readOnly = true)
    public IncomeStatisticsResponse statistics(Long userId, YearMonth period, Currency target) {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        StatisticsConverter converter = new StatisticsConverter(target, exchangeRateProvider);
        Map<IncomeCategory, BigDecimal> sums = new EnumMap<>(IncomeCategory.class);
        for (IncomeCategorySubtotal subtotal : incomeRepository.sumByCategoryCurrencyAndDate(
                userId, period.atDay(1), period.atEndOfMonth(), today)) {
            converter.convert(subtotal.currency(), subtotal.date(), subtotal.amount(),
                    subtotal.count())
                    .ifPresent(amount -> sums.merge(subtotal.category(), amount, BigDecimal::add));
        }
        BigDecimal total = sums.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        List<IncomeStatisticsResponse.CategoryAmount> categories = sums.entrySet().stream()
                .filter(entry -> entry.getValue().signum() > 0)
                .sorted(Comparator.<Map.Entry<IncomeCategory, BigDecimal>, BigDecimal>comparing(
                                Map.Entry::getValue).reversed()
                        .thenComparing(entry -> entry.getKey().ordinal()))
                .map(entry -> new IncomeStatisticsResponse.CategoryAmount(entry.getKey(),
                        money(entry.getValue()), percentage(entry.getValue(), total)))
                .toList();
        return new IncomeStatisticsResponse(money(total), converter.excluded(), categories);
    }

    private static String money(BigDecimal amount) {
        return amount.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private static String percentage(BigDecimal amount, BigDecimal total) {
        return amount.multiply(HUNDRED).divide(total, 1, RoundingMode.HALF_UP).toPlainString();
    }
}
