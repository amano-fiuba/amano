package com.amano.backend.groupexpense;

import java.time.OffsetDateTime;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Representation of a group expense with its shares and debts
 * (contracts/group-expenses-api.md; 022 D8/T051). Amounts are canonical decimal
 * text with two decimals, as in 007 and 014. Names are the copies stored when
 * the expense was registered; payer and creator use the current visible name.
 * Conversion fields ({@code convertedAmount}, {@code convertedCurrency},
 * {@code exchangeRate}, {@code rateDate}, {@code rateSource}) and
 * {@code splitMethod} are additive (022 D8 and D2/T066).
 */
public record GroupExpenseResponse(
        Long id,
        Long groupId,
        String amount,
        String currency,
        @JsonInclude(JsonInclude.Include.NON_NULL) String convertedAmount,
        @JsonInclude(JsonInclude.Include.NON_NULL) String convertedCurrency,
        @JsonInclude(JsonInclude.Include.NON_NULL) String exchangeRate,
        @JsonInclude(JsonInclude.Include.NON_NULL) String rateDate,
        @JsonInclude(JsonInclude.Include.NON_NULL) String rateSource,
        String date,
        @JsonInclude(JsonInclude.Include.NON_NULL) String description,
        Person payer,
        Person createdBy,
        OffsetDateTime createdAt,
        List<Share> shares,
        List<DebtView> debts,
        String splitMethod) {

    /** 022 constructor without the split method, kept for backward compatibility. */
    public GroupExpenseResponse(
            Long id,
            Long groupId,
            String amount,
            String currency,
            String convertedAmount,
            String convertedCurrency,
            String exchangeRate,
            String rateDate,
            String rateSource,
            String date,
            String description,
            Person payer,
            Person createdBy,
            OffsetDateTime createdAt,
            List<Share> shares,
            List<DebtView> debts) {
        this(id, groupId, amount, currency, convertedAmount, convertedCurrency, exchangeRate, rateDate,
                rateSource, date, description, payer, createdBy, createdAt, shares, debts, "EQUAL");
    }

    /** 017 constructor without conversion fields, kept for backward compatibility. */
    public GroupExpenseResponse(
            Long id,
            Long groupId,
            String amount,
            String currency,
            String date,
            String description,
            Person payer,
            Person createdBy,
            OffsetDateTime createdAt,
            List<Share> shares,
            List<DebtView> debts) {
        this(id, groupId, amount, currency, null, null, null, null, null, date, description, payer,
                createdBy, createdAt, shares, debts, "EQUAL");
    }

    /** A registered user. */
    public record Person(Long userId, String name) {
    }

    /** One share, in request order. */
    public record Share(int position, String type, Long userId, Long simulatedProfileId, String name,
            String amount, boolean isPayer) {
    }

    /** The debtor of a debt: a registered user or a simulated profile (id null once deleted). */
    public record Debtor(String type, Long userId, Long simulatedProfileId, String name) {
    }

    /**
     * One debt with its state (research.md D5). {@code canRegisterPayment}
     * (018 FR-016) tells whether the session user may pay it now: the debt is
     * not PAID and the user is its debtor, its creditor or an ADMIN.
     */
    public record DebtView(Long id, Debtor debtor, Person creditor, String amount, String paidAmount,
            String outstanding, String currency, String status, boolean canRegisterPayment) {

        /** View of {@code debt} with the current creditor name (shared with 018 payments). */
        public static DebtView of(Debt debt, String creditorName, boolean canRegisterPayment) {
            return new DebtView(debt.getId(),
                    new Debtor(debt.getDebtorType().name(), debt.getDebtorUserId(),
                            debt.getDebtorSimulatedProfileId(), debt.getDebtorName()),
                    new Person(debt.getCreditorUserId(), creditorName),
                    debt.getAmount().toPlainString(), debt.getPaidAmount().toPlainString(),
                    debt.getOutstanding().toPlainString(), debt.getCurrency().name(), debt.getStatus().name(),
                    canRegisterPayment);
        }
    }
}
