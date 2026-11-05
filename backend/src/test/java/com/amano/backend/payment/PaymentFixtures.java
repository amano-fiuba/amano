package com.amano.backend.payment;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import org.springframework.test.util.ReflectionTestUtils;

import com.amano.backend.groupexpense.Debt;
import com.amano.backend.groupexpense.GroupExpense;
import com.amano.backend.groupexpense.GroupExpenseShare;
import com.amano.backend.user.Currency;

/**
 * 018: debts of the 017 example (Ana pays $ 10.000 for herself, Juan and
 * Abuela) for the unit tests of {@code payment}.
 */
final class PaymentFixtures {

    static final long GROUP_ID = 3L;
    static final long EXPENSE_ID = 12L;
    static final long ANA = 7L;
    static final long JUAN = 15L;
    static final long ABUELA = 4L;
    static final LocalDate EXPENSE_DATE = LocalDate.of(2026, 11, 1);

    private PaymentFixtures() {
    }

    static GroupExpense expense() {
        GroupExpense expense = new GroupExpense();
        ReflectionTestUtils.setField(expense, "id", EXPENSE_ID);
        expense.setGroupId(GROUP_ID);
        expense.setCreatedByUserId(ANA);
        expense.setPayerUserId(ANA);
        expense.setAmount(new BigDecimal("10000.00"));
        expense.setCurrency(Currency.ARS);
        expense.setExpenseDate(EXPENSE_DATE);
        expense.setIdempotencyKey(UUID.randomUUID());
        return expense;
    }

    /** PENDING debt of Juan towards Ana for 3.333,33 (id 31). */
    static Debt juanDebt() {
        GroupExpenseShare share = GroupExpenseShare.registered(EXPENSE_ID, 1, JUAN, "Juan Pérez",
                new BigDecimal("3333.33"));
        ReflectionTestUtils.setField(share, "id", 41L);
        Debt debt = Debt.pendingFor(expense(), share);
        ReflectionTestUtils.setField(debt, "id", 31L);
        return debt;
    }

    /** PENDING debt of the simulated profile Abuela towards Ana for 3.333,33 (id 32). */
    static Debt abuelaDebt() {
        GroupExpenseShare share = GroupExpenseShare.simulated(EXPENSE_ID, 2, ABUELA, "Abuela",
                new BigDecimal("3333.33"));
        ReflectionTestUtils.setField(share, "id", 42L);
        Debt debt = Debt.pendingFor(expense(), share);
        ReflectionTestUtils.setField(debt, "id", 32L);
        return debt;
    }
}
