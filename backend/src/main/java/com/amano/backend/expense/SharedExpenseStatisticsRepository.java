package com.amano.backend.expense;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;
import java.util.List;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import com.amano.backend.groupexpense.GroupExpense;
import com.amano.backend.user.Currency;

/**
 * Read-only queries of the statistics (specs/032-estadisticas-gastos) over the
 * shared expenses: the person's shares in group and friend expenses, and the
 * years with any expense of the person (plan D2, D3, D9).
 *
 * <p>A share is in the currency the expense was split in: the group currency
 * when the expense was converted (022), the expense currency for friend
 * expenses (no group) and for group expenses in another currency registered
 * before 022, whose {@code converted_amount} stayed {@code NULL} (V19).
 */
public interface SharedExpenseStatisticsRepository extends Repository<GroupExpense, Long> {

    @Query(value = """
            SELECT COALESCE(e.category, 'OTHER') AS category,
                   CASE WHEN e.converted_amount IS NULL THEN e.currency
                        ELSE COALESCE(g.primary_currency, e.currency) END AS currency,
                   e.expense_date AS expense_date,
                   SUM(s.amount) AS amount,
                   COUNT(*) AS share_count
            FROM group_expense_shares s
            JOIN group_expenses e ON e.id = s.expense_id
            LEFT JOIN groups g ON g.id = e.group_id
            WHERE s.participant_type = 'REGISTERED'
              AND s.user_id = :userId
              AND e.expense_date BETWEEN :from AND :to
            GROUP BY 1, 2, 3
            """, nativeQuery = true)
    List<Object[]> sumSharesRows(@Param("userId") Long userId, @Param("from") LocalDate from,
            @Param("to") LocalDate to);

    @Query(value = """
            SELECT y FROM (
                SELECT CAST(EXTRACT(YEAR FROM x.expense_date) AS INTEGER) AS y
                FROM expenses x
                WHERE x.user_id = :userId
                UNION
                SELECT CAST(EXTRACT(YEAR FROM e.expense_date) AS INTEGER) AS y
                FROM group_expense_shares s
                JOIN group_expenses e ON e.id = s.expense_id
                WHERE s.participant_type = 'REGISTERED' AND s.user_id = :userId
            ) AS years
            ORDER BY y DESC
            """, nativeQuery = true)
    List<Number> findYearsRows(@Param("userId") Long userId);

    /**
     * Shares of the person in group and friend expenses between two expense dates
     * (inclusive), summed by category ({@code OTHER} for expenses before 027),
     * share currency and date (FR-007, FR-012).
     */
    default List<CategorySubtotal> sumSharesByCategoryCurrencyAndDate(Long userId, LocalDate from,
            LocalDate to) {
        return sumSharesRows(userId, from, to).stream()
                .map(row -> new CategorySubtotal(
                        ExpenseCategory.valueOf((String) row[0]),
                        Currency.valueOf((String) row[1]),
                        toLocalDate(row[2]),
                        (BigDecimal) row[3],
                        ((Number) row[4]).longValue()))
                .toList();
    }

    /** Years (by expense date) with a personal expense or a share of the person, newest first. */
    default List<Integer> findYears(Long userId) {
        return findYearsRows(userId).stream().map(Number::intValue).toList();
    }

    private static LocalDate toLocalDate(Object value) {
        return value instanceof LocalDate date ? date : ((Date) value).toLocalDate();
    }
}
