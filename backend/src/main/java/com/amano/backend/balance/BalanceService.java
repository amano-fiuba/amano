package com.amano.backend.balance;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.amano.backend.common.CurrencyDateTotal;
import com.amano.backend.expense.ExpenseRepository;
import com.amano.backend.groupexpense.exchange.ExchangeRateProvider;
import com.amano.backend.groupexpense.exchange.ExchangeRateQuote;
import com.amano.backend.groupexpense.exchange.ExchangeRateUnavailableException;
import com.amano.backend.income.IncomePeriod;
import com.amano.backend.income.IncomeRepository;
import com.amano.backend.user.Currency;
import com.amano.backend.user.User;
import com.amano.backend.user.UserRepository;

/**
 * Personal balance (specs/033-income-management, FR-010 to FR-012): the incomes
 * already received minus the expenses already registered in a period, in the
 * account's main currency. Movements are summed per {@code (currency, date)} in the
 * database and each group is converted with the quote of its own date, so a closed
 * period never changes. A missing quote for an expense or a received income fails
 * the whole balance ({@link ExchangeRateUnavailableException}, 503): a partial
 * balance would be misleading. Scheduled incomes (date after today in UTC) are only
 * informative and never fail it: their date has no quote yet, so today's quote is the
 * fallback, and without one {@code projectedIncome} is left out.
 */
@Service
public class BalanceService {

    private static final int SCALE = 2;

    private final IncomeRepository incomeRepository;
    private final ExpenseRepository expenseRepository;
    private final UserRepository userRepository;
    private final ExchangeRateProvider exchangeRateProvider;

    public BalanceService(IncomeRepository incomeRepository, ExpenseRepository expenseRepository,
            UserRepository userRepository, ExchangeRateProvider exchangeRateProvider) {
        this.incomeRepository = incomeRepository;
        this.expenseRepository = expenseRepository;
        this.userRepository = userRepository;
        this.exchangeRateProvider = exchangeRateProvider;
    }

    @Transactional(readOnly = true)
    public BalanceResponse balance(Long userId, IncomePeriod period) {
        Currency main = mainCurrency(userRepository.findById(userId)
                .map(User::getCurrencies).orElse(Set.of()));
        LocalDate today = LocalDate.now(ZoneOffset.UTC);

        List<CurrencyDateTotal> incomes = incomeRepository.sumByCurrencyAndDate(
                userId, period.start(), period.end());
        List<CurrencyDateTotal> received = incomes.stream()
                .filter(row -> !row.date().isAfter(today)).toList();
        List<CurrencyDateTotal> scheduled = incomes.stream()
                .filter(row -> row.date().isAfter(today)).toList();
        List<CurrencyDateTotal> spent = expenseRepository.sumByCurrencyAndDate(
                userId, period.start(), period.end());

        BigDecimal income = convert(received, main);
        BigDecimal expenses = convert(spent, main);
        BigDecimal projected = convertScheduled(scheduled, main, today);
        return new BalanceResponse(main.name(), format(income),
                projected == null ? null : format(projected), format(expenses),
                format(income.subtract(expenses)));
    }

    /**
     * Main currency of the account: ARS, then USD, then EUR, then the first other
     * preference in catalog order; ARS when there is none (the same rule as the
     * frontend {@code preferredCurrency}, 002 FR-011).
     */
    static Currency mainCurrency(Set<Currency> preferences) {
        if (preferences == null || preferences.isEmpty()) {
            return Currency.ARS;
        }
        for (Currency candidate : List.of(Currency.ARS, Currency.USD, Currency.EUR)) {
            if (preferences.contains(candidate)) {
                return candidate;
            }
        }
        return preferences.stream().min(Enum::compareTo).orElse(Currency.ARS);
    }

    /** Sum of the groups converted with the quote of each group's date; a missing quote fails. */
    private BigDecimal convert(List<CurrencyDateTotal> rows, Currency main) {
        BigDecimal sum = BigDecimal.ZERO;
        for (CurrencyDateTotal row : rows) {
            sum = sum.add(convertRow(row, main, row.date()));
        }
        return sum;
    }

    /**
     * Scheduled incomes: the quote of their (future) date, else today's; {@code null}
     * when some group has neither, so the balance never fails because of them.
     */
    private BigDecimal convertScheduled(List<CurrencyDateTotal> rows, Currency main,
            LocalDate today) {
        BigDecimal sum = BigDecimal.ZERO;
        for (CurrencyDateTotal row : rows) {
            try {
                sum = sum.add(convertRow(row, main, row.date()));
            }
            catch (ExchangeRateUnavailableException noQuoteForThatDate) {
                try {
                    sum = sum.add(convertRow(row, main, today));
                }
                catch (ExchangeRateUnavailableException noQuoteToday) {
                    return null;
                }
            }
        }
        return sum;
    }

    private BigDecimal convertRow(CurrencyDateTotal row, Currency main, LocalDate quoteDate) {
        ExchangeRateQuote quote = exchangeRateProvider.getQuote(row.currency(), main, quoteDate);
        if (quote == null || quote.rate() == null) {
            throw new ExchangeRateUnavailableException();
        }
        return row.total().multiply(quote.rate()).setScale(SCALE, RoundingMode.HALF_UP);
    }

    private static String format(BigDecimal amount) {
        return amount.setScale(SCALE, RoundingMode.HALF_UP).toPlainString();
    }
}
