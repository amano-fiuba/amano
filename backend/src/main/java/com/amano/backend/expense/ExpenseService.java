package com.amano.backend.expense;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.amano.backend.common.FieldValidationException;
import com.amano.backend.user.Currency;

/**
 * Registration of a personal expense (US1, contracts/expenses-api.md). The owner
 * is always the SESSION principal; the request never carries a user id. Values
 * are normalized with the shared utilities of the foundational phase and every
 * invalid field is accumulated in one pass before any write, so a missing or
 * {@code null} required value can never surface as a {@code NullPointerException}
 * (FR-021/FR-022). An existing {@code (user, Idempotency-Key)} is replayed when
 * the normalized payload matches and rejected with {@code 422} when it differs
 * (FR-027); the unique index is the concurrency backstop.
 */
@Service
public class ExpenseService {

    private static final String CURRENCY_REQUIRED = "La moneda es obligatoria";
    private static final String CURRENCY_INVALID = "La moneda no es válida";
    private static final String DATE_FORMAT = "La fecha debe tener el formato aaaa-mm-dd";
    private static final String CATEGORY_REQUIRED = "La categoría es obligatoria";
    private static final String CATEGORY_INVALID = "La categoría no es válida";
    private static final String TYPE_REQUIRED = "El tipo de gasto es obligatorio";
    private static final String TYPE_INVALID = "El tipo de gasto no es válido";
    private static final String PAYMENT_REQUIRED = "El medio de pago es obligatorio";
    private static final String PAYMENT_INVALID = "El medio de pago no es válido";
    private static final String RESERVED_PROPERTY = "No se permite informar este campo";

    /** Fields of future shared-expense models that must never select the owner (FR-005/FR-006). */
    private static final Set<String> RESERVED_PROPERTIES = Set.of(
            "userId", "creatorId", "creator", "user", "participants", "participantIds",
            "splits", "divisions", "shares", "debts", "friends", "groups");

    private final ExpenseRepository expenseRepository;

    public ExpenseService(ExpenseRepository expenseRepository) {
        this.expenseRepository = expenseRepository;
    }

    /** Result of a registration: the representation plus whether a row was inserted. */
    public record Registration(ExpenseResponse expense, boolean created) {
    }

    private record NormalizedExpense(BigDecimal amount, Currency currency, LocalDate date,
            ExpenseCategory category, ExpenseType type, PaymentMethod paymentMethod,
            String description) {
    }

    /**
     * Validates and normalizes the whole payload, replays an existing operation of
     * the same user/key or persists a new expense linked to {@code userId}.
     */
    @Transactional
    public Registration registerExpense(Long userId, UUID idempotencyKey,
            RegisterExpenseRequest request) {
        NormalizedExpense normalized = validateAndNormalize(request);

        Optional<Expense> existing = expenseRepository.findByUserIdAndIdempotencyKey(
                userId, idempotencyKey);
        if (existing.isPresent()) {
            return replay(existing.get(), normalized);
        }

        Expense expense = new Expense();
        expense.setUserId(userId);
        expense.setAmount(normalized.amount());
        expense.setCurrency(normalized.currency());
        expense.setExpenseDate(normalized.date());
        expense.setCategory(normalized.category());
        expense.setType(normalized.type());
        expense.setPaymentMethod(normalized.paymentMethod());
        expense.setDescription(normalized.description());
        expense.setIdempotencyKey(idempotencyKey);

        return new Registration(ExpenseResponse.from(expenseRepository.save(expense)), true);
    }

    /**
     * Resolves the unique-index race after the failed transaction was rolled back:
     * the winner is re-read in a new transaction and compared with the same rules.
     * A missing winner means the integrity failure was not the idempotency race.
     */
    @Transactional(readOnly = true)
    public Registration resolveConcurrentRegistration(Long userId, UUID idempotencyKey,
            RegisterExpenseRequest request) {
        NormalizedExpense normalized = validateAndNormalize(request);
        Expense winner = expenseRepository.findByUserIdAndIdempotencyKey(userId, idempotencyKey)
                .orElseThrow(() -> new IllegalStateException(
                        "Idempotency conflict without a persisted winner"));
        return replay(winner, normalized);
    }

    private static Registration replay(Expense existing, NormalizedExpense normalized) {
        if (!samePayload(existing, normalized)) {
            throw new IdempotencyConflictException();
        }
        return new Registration(ExpenseResponse.from(existing), false);
    }

    private static boolean samePayload(Expense existing, NormalizedExpense normalized) {
        return existing.getAmount().compareTo(normalized.amount()) == 0
                && existing.getCurrency() == normalized.currency()
                && existing.getExpenseDate().equals(normalized.date())
                && existing.getCategory() == normalized.category()
                && existing.getType() == normalized.type()
                && existing.getPaymentMethod() == normalized.paymentMethod()
                && Objects.equals(existing.getDescription(), normalized.description());
    }

    private static NormalizedExpense validateAndNormalize(RegisterExpenseRequest request) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();

        String amountError = ExpenseAmountNormalizer.validationError(request.getAmount());
        if (amountError != null) {
            fieldErrors.put("amount", amountError);
        }
        Currency currency = parseCatalog(request.getCurrency(), Currency.class, "currency",
                CURRENCY_REQUIRED, CURRENCY_INVALID, fieldErrors);
        LocalDate date = parseDate(request.getDate(), request.getDeviceTimeZone(), fieldErrors);
        ExpenseCategory category = parseCatalog(request.getCategory(), ExpenseCategory.class,
                "category", CATEGORY_REQUIRED, CATEGORY_INVALID, fieldErrors);
        ExpenseType type = parseCatalog(request.getType(), ExpenseType.class, "type",
                TYPE_REQUIRED, TYPE_INVALID, fieldErrors);
        PaymentMethod paymentMethod = parseCatalog(request.getPaymentMethod(), PaymentMethod.class,
                "paymentMethod", PAYMENT_REQUIRED, PAYMENT_INVALID, fieldErrors);
        String descriptionError = ExpenseDescriptionNormalizer.validationError(
                request.getDescription());
        if (descriptionError != null) {
            fieldErrors.put("description", descriptionError);
        }
        for (String reserved : request.getUnknownProperties().keySet()) {
            if (RESERVED_PROPERTIES.contains(reserved)) {
                fieldErrors.put(reserved, RESERVED_PROPERTY);
            }
        }

        if (!fieldErrors.isEmpty()) {
            throw new FieldValidationException(fieldErrors);
        }

        return new NormalizedExpense(ExpenseAmountNormalizer.normalize(request.getAmount()),
                currency, date, category, type, paymentMethod,
                ExpenseDescriptionNormalizer.normalize(request.getDescription()));
    }

    private static <E extends Enum<E>> E parseCatalog(String raw, Class<E> type, String field,
            String requiredMessage, String invalidMessage, Map<String, String> fieldErrors) {
        if (raw == null || raw.isBlank()) {
            fieldErrors.put(field, requiredMessage);
            return null;
        }
        try {
            return Enum.valueOf(type, raw);
        }
        catch (IllegalArgumentException exception) {
            fieldErrors.put(field, invalidMessage);
            return null;
        }
    }

    private static LocalDate parseDate(String raw, String deviceTimeZone,
            Map<String, String> fieldErrors) {
        if (raw == null || raw.isBlank()) {
            fieldErrors.put("date", ExpenseDateValidator.REQUIRED_MESSAGE);
            return null;
        }
        LocalDate date;
        try {
            date = LocalDate.parse(raw);
        }
        catch (DateTimeParseException exception) {
            fieldErrors.put("date", DATE_FORMAT);
            return null;
        }
        String dateError = ExpenseDateValidator.validationError(date, deviceTimeZone);
        if (dateError != null) {
            fieldErrors.put("date", dateError);
            return null;
        }
        return date;
    }
}
