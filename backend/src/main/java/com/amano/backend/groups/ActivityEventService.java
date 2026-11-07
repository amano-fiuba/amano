package com.amano.backend.groups;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.amano.backend.user.User;

import tools.jackson.databind.ObjectMapper;

/**
 * Records {@link GroupActivity} rows (010 US9; 014). Rows are standalone
 * (preservation pattern): only groupId is referenced and the affected member's
 * name is copied into the details, so they survive later member, expense or
 * payment changes. {@code Propagation.MANDATORY}: every event is written inside
 * the transaction of the business change that produced it.
 *
 * <p>The details are serialized with Jackson (014 research.md D3), never by
 * string concatenation, so names with quotes still produce valid JSON. Keys
 * with a {@code null} value are omitted.
 */
@Service
public class ActivityEventService {

    private final GroupActivityRepository repository;
    private final ObjectMapper objectMapper;

    public ActivityEventService(GroupActivityRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public GroupActivity record(Long groupId, GroupActivity.GroupActivityType type, Long actorUserId,
            Map<String, ?> details) {
        return repository.save(new GroupActivity(groupId, type, actorUserId, toJson(details)));
    }

    /** 015 FR-007: latest event per group; groups without events are absent. */
    @Transactional(readOnly = true)
    public Map<Long, OffsetDateTime> lastActivityByGroup(Collection<Long> groupIds) {
        if (groupIds.isEmpty()) {
            return Map.of();
        }
        return repository.findLastActivityByGroupIds(groupIds).stream()
                .collect(Collectors.toMap(GroupActivityRepository.LastActivity::getGroupId,
                        GroupActivityRepository.LastActivity::getLastActivityAt));
    }

    /**
     * Hook for the group-expenses module (014 US2, FR-006/FR-008; 022 D11, T061):
     * records {@code EXPENSE_CREATED} with the data-model format, including the
     * optional payer fields when present.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public GroupActivity recordExpenseCreated(Long groupId, Long actorUserId, ExpenseActivityDetails details) {
        if (details == null) {
            throw new IllegalArgumentException("Faltan los datos del gasto");
        }
        String currency = validatedCurrency(details.amount(), details.currency());
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("expenseId", details.expenseId());
        map.put("amount", details.amount().toPlainString());
        map.put("currency", currency);
        map.put("description", blankToNull(details.description()));
        if (details.payerUserId() != null) {
            map.put("payerUserId", details.payerUserId());
        }
        map.put("payerName", blankToNull(details.payerName()));
        return record(groupId, GroupActivity.GroupActivityType.EXPENSE_CREATED, actorUserId, map);
    }

    /**
     * Hook for the group-payments module (014 US2, FR-007/FR-008):
     * records {@code PAYMENT_MADE} with the data-model format.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public GroupActivity recordPaymentMade(Long groupId, Long actorUserId, PaymentActivityDetails details) {
        return recordPaymentEvent(groupId, GroupActivity.GroupActivityType.PAYMENT_MADE, actorUserId, details);
    }

    /**
     * 022 US8 (T047/T049): records {@code PAYMENT_REGISTERED} when a full debt
     * payment is registered, preserving the debt status transition.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public GroupActivity recordPaymentRegistered(Long groupId, Long actorUserId, PaymentActivityDetails details) {
        return recordPaymentEvent(groupId, GroupActivity.GroupActivityType.PAYMENT_REGISTERED, actorUserId, details);
    }

    /**
     * 022 US8 (T049): records {@code PAYMENT_CONFIRMED} when an admin confirms a
     * reported payment ({@code PAYMENT_REPORTED → VERIFIED}).
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public GroupActivity recordPaymentConfirmed(Long groupId, Long actorUserId, PaymentActivityDetails details) {
        return recordPaymentEvent(groupId, GroupActivity.GroupActivityType.PAYMENT_CONFIRMED, actorUserId, details);
    }

    /**
     * 022 US8 (T049): records {@code PAYMENT_REJECTED} when an admin rejects a
     * reported payment ({@code PAYMENT_REPORTED → PENDING/PARTIALLY_PAID}).
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public GroupActivity recordPaymentRejected(Long groupId, Long actorUserId, PaymentActivityDetails details) {
        return recordPaymentEvent(groupId, GroupActivity.GroupActivityType.PAYMENT_REJECTED, actorUserId, details);
    }

    /**
     * 022 US8 (T049): records {@code DEBT_OVERDUE} when the scheduled job
     * transitions a {@code PENDING} debt to {@code OVERDUE}.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public GroupActivity recordDebtOverdue(Long groupId, Long debtId, Long expenseId, BigDecimal amount,
            String currency, String debtorName, String creditorName) {
        String normalizedCurrency = validatedCurrency(amount, currency);
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("debtId", debtId);
        map.put("expenseId", expenseId);
        map.put("amount", amount.toPlainString());
        map.put("currency", normalizedCurrency);
        map.put("fromName", blankToNull(debtorName));
        map.put("toName", blankToNull(creditorName));
        map.put("debtStatusBefore", "PENDING");
        map.put("debtStatusAfter", "OVERDUE");
        return record(groupId, GroupActivity.GroupActivityType.DEBT_OVERDUE, null, map);
    }

    /**
     * 025: records {@code DEBT_REMINDER_SENT} after a successful delivery. The
     * snapshot of the mail itself lives in {@code reminders}; this event only
     * keeps the data needed by the history.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public GroupActivity recordDebtReminderSent(Long groupId, Long reminderId, Long debtId, Long expenseId,
            BigDecimal amount, String currency, String debtorName, String creditorName, LocalDate dueDate) {
        String normalizedCurrency = validatedCurrency(amount, currency);
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("reminderId", reminderId);
        map.put("debtId", debtId);
        map.put("expenseId", expenseId);
        map.put("amount", amount.toPlainString());
        map.put("currency", normalizedCurrency);
        map.put("fromName", blankToNull(debtorName));
        map.put("toName", blankToNull(creditorName));
        map.put("dueDate", dueDate == null ? null : dueDate.toString());
        return record(groupId, GroupActivity.GroupActivityType.DEBT_REMINDER_SENT, null, map);
    }

    /**
     * 022 US8 (T048): records {@code LIQUIDATION_PROPOSED} when a member
     * generates a settlement proposal for the group, at most once per group
     * and day (option a) to avoid filling the history with noise on repeated
     * GET requests.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public GroupActivity recordLiquidationProposed(Long groupId, Long actorUserId, int transferCount,
            String primaryCurrency, OffsetDateTime dayStart, OffsetDateTime dayEnd) {
        if (dayStart != null && dayEnd != null
                && repository.existsByGroupIdAndEventTypeAndCreatedAtBetween(
                        groupId, GroupActivity.GroupActivityType.LIQUIDATION_PROPOSED.name(), dayStart, dayEnd)) {
            return null;
        }
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("transferCount", transferCount);
        map.put("currency", blankToNull(primaryCurrency));
        return record(groupId, GroupActivity.GroupActivityType.LIQUIDATION_PROPOSED, actorUserId, map);
    }

    /** Overload without deduplication window, kept for direct unit testing. */
    @Transactional(propagation = Propagation.MANDATORY)
    public GroupActivity recordLiquidationProposed(Long groupId, Long actorUserId, int transferCount,
            String primaryCurrency) {
        return recordLiquidationProposed(groupId, actorUserId, transferCount, primaryCurrency, null, null);
    }

    private GroupActivity recordPaymentEvent(Long groupId, GroupActivity.GroupActivityType type, Long actorUserId,
            PaymentActivityDetails details) {
        if (details == null) {
            throw new IllegalArgumentException("Faltan los datos del pago");
        }
        String currency = validatedCurrency(details.amount(), details.currency());
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("paymentId", details.paymentId());
        map.put("amount", details.amount().toPlainString());
        map.put("currency", currency);
        map.put("fromName", blankToNull(details.fromName()));
        map.put("toName", blankToNull(details.toName()));
        // 018 (plan.md D6): additive keys, only when present, so 014 entries keep their shape.
        if (details.expenseId() != null) {
            map.put("expenseId", details.expenseId());
        }
        if (details.debtId() != null) {
            map.put("debtId", details.debtId());
        }
        if (details.debtStatusBefore() != null) {
            map.put("debtStatusBefore", blankToNull(details.debtStatusBefore()));
        }
        if (details.debtStatusAfter() != null) {
            map.put("debtStatusAfter", blankToNull(details.debtStatusAfter()));
        }
        return record(groupId, type, actorUserId, map);
    }

    /** Visible name of a registered user, without "null" or extra spaces (014 D4). */
    public static String displayName(User user) {
        if (user == null) {
            return null;
        }
        String first = user.getFirstName() == null ? "" : user.getFirstName().trim();
        String last = user.getLastName() == null ? "" : user.getLastName().trim();
        String full = (first + " " + last).trim();
        return full.isEmpty() ? null : full;
    }

    /** Details of an alta/baja of a registered member (014 data-model.md). */
    public static Map<String, Object> registeredMemberDetails(User user) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("type", "REGISTERED");
        details.put("userId", user.getId());
        details.put("name", displayName(user));
        details.put("email", user.getEmail());
        return details;
    }

    /** Details of an alta/baja of a simulated profile (014 data-model.md). */
    public static Map<String, Object> simulatedMemberDetails(SimulatedProfile profile) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("type", "SIMULATED");
        details.put("simulatedProfileId", profile.getId());
        details.put("name", profile.getName());
        details.put("email", profile.getEmail());
        return details;
    }

    private String toJson(Map<String, ?> details) {
        Map<String, Object> clean = new LinkedHashMap<>();
        if (details != null) {
            details.forEach((key, value) -> {
                if (value != null) {
                    clean.put(key, value);
                }
            });
        }
        return objectMapper.writeValueAsString(clean);
    }

    private static String validatedCurrency(BigDecimal amount, String currency) {
        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException("El monto debe ser mayor a cero");
        }
        String normalized = currency == null ? "" : currency.trim().toUpperCase(Locale.ROOT);
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("La moneda es obligatoria");
        }
        return normalized;
    }

    private static String blankToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
