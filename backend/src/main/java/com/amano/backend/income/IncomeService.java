package com.amano.backend.income;

import java.math.BigDecimal;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.amano.backend.common.FieldValidationException;
import com.amano.backend.expense.ExpenseAmountNormalizer;
import com.amano.backend.expense.ExpenseClassification;
import com.amano.backend.expense.ExpenseDateValidator;
import com.amano.backend.expense.ExpenseDescriptionNormalizer;
import com.amano.backend.expense.IdempotencyConflictException;
import com.amano.backend.user.Currency;

/**
 * Registration of a personal income (specs/032-income-management, US1). It follows
 * {@code ExpenseService}: the owner is always the SESSION principal, every invalid
 * field is accumulated in one pass before any write (so a missing value can never
 * surface as a {@code NullPointerException}), and an existing
 * {@code (user, Idempotency-Key)} is replayed when the normalized payload matches and
 * rejected with {@code 422} when it differs (FR-005); the unique index is the
 * concurrency backstop. Incomes are immutable: this service only creates them.
 */
@Service
public class IncomeService {

    static final String CURRENCY_REQUIRED = "La moneda es obligatoria";
    static final String CURRENCY_INVALID = "La moneda no es válida";
    static final String DATE_FORMAT = "La fecha debe tener el formato aaaa-mm-dd";
    static final String DATE_TOO_FAR = "La fecha no puede ser más de un año posterior a hoy";
    static final String CATEGORY_REQUIRED = ExpenseClassification.CATEGORY_REQUIRED;
    static final String CATEGORY_INVALID = ExpenseClassification.CATEGORY_INVALID;
    static final String PAYMENT_REQUIRED = "El medio de cobro es obligatorio";
    static final String PAYMENT_INVALID = "El medio de cobro no es válido";
    static final String RESERVED_PROPERTY = "No se permite informar este campo";

    /** Fields that must never select the owner of an income. */
    private static final Set<String> RESERVED_PROPERTIES = Set.of(
            "userId", "creatorId", "creator", "user", "participants", "participantIds",
            "splits", "divisions", "shares", "debts", "friends", "groups");

    private final IncomeRepository incomeRepository;

    public IncomeService(IncomeRepository incomeRepository) {
        this.incomeRepository = incomeRepository;
    }

    /** Result of a registration: the representation plus whether a row was inserted. */
    public record Registration(IncomeResponse income, boolean created) {
    }

    private record NormalizedIncome(BigDecimal amount, Currency currency, LocalDate date,
            IncomeCategory category, IncomePaymentMethod paymentMethod, String description) {
    }

    /**
     * Validates and normalizes the whole payload, replays an existing operation of the
     * same user/key or persists a new income linked to {@code userId}.
     */
    @Transactional
    public Registration registerIncome(Long userId, UUID idempotencyKey,
            RegisterIncomeRequest request) {
        NormalizedIncome normalized = validateAndNormalize(request);

        Optional<Income> existing = incomeRepository.findByUserIdAndIdempotencyKey(
                userId, idempotencyKey);
        if (existing.isPresent()) {
            return replay(existing.get(), normalized);
        }

        Income income = new Income();
        income.setUserId(userId);
        income.setAmount(normalized.amount());
        income.setCurrency(normalized.currency());
        income.setIncomeDate(normalized.date());
        income.setCategory(normalized.category());
        income.setPaymentMethod(normalized.paymentMethod());
        income.setDescription(normalized.description());
        income.setIdempotencyKey(idempotencyKey);

        return new Registration(IncomeResponse.from(incomeRepository.save(income)), true);
    }

    /**
     * Resolves the unique-index race after the failed transaction was rolled back: the
     * winner is re-read in a new transaction and compared with the same rules. A
     * missing winner means the integrity failure was not the idempotency race.
     */
    @Transactional(readOnly = true)
    public Registration resolveConcurrentRegistration(Long userId, UUID idempotencyKey,
            RegisterIncomeRequest request) {
        NormalizedIncome normalized = validateAndNormalize(request);
        Income winner = incomeRepository.findByUserIdAndIdempotencyKey(userId, idempotencyKey)
                .orElseThrow(() -> new IllegalStateException(
                        "Idempotency conflict without a persisted winner"));
        return replay(winner, normalized);
    }

    private static Registration replay(Income existing, NormalizedIncome normalized) {
        if (!samePayload(existing, normalized)) {
            throw new IdempotencyConflictException();
        }
        return new Registration(IncomeResponse.from(existing), false);
    }

    private static boolean samePayload(Income existing, NormalizedIncome normalized) {
        return existing.getAmount().compareTo(normalized.amount()) == 0
                && existing.getCurrency() == normalized.currency()
                && existing.getIncomeDate().equals(normalized.date())
                && existing.getCategory() == normalized.category()
                && existing.getPaymentMethod() == normalized.paymentMethod()
                && Objects.equals(existing.getDescription(), normalized.description());
    }

    private static NormalizedIncome validateAndNormalize(RegisterIncomeRequest request) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();

        String amountError = ExpenseAmountNormalizer.validationError(request.getAmount());
        if (amountError != null) {
            fieldErrors.put("amount", amountError);
        }
        Currency currency = ExpenseClassification.parseCatalog(request.getCurrency(),
                Currency.class, "currency", CURRENCY_REQUIRED, CURRENCY_INVALID, fieldErrors);
        LocalDate date = parseDate(request.getDate(), request.getDeviceTimeZone(), fieldErrors);
        IncomeCategory category = ExpenseClassification.parseCatalog(request.getCategory(),
                IncomeCategory.class, "category", CATEGORY_REQUIRED, CATEGORY_INVALID,
                fieldErrors);
        IncomePaymentMethod paymentMethod = ExpenseClassification.parseCatalog(
                request.getPaymentMethod(), IncomePaymentMethod.class, "paymentMethod",
                PAYMENT_REQUIRED, PAYMENT_INVALID, fieldErrors);
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

        return new NormalizedIncome(ExpenseAmountNormalizer.normalize(request.getAmount()),
                currency, date, category, paymentMethod,
                ExpenseDescriptionNormalizer.normalize(request.getDescription()));
    }

    /**
     * Required {@code yyyy-MM-dd} date, not later than one year after today in the
     * device zone (FR-009); UTC when the zone is absent or invalid. A date in the
     * future is accepted: it is a scheduled income, shown as projected.
     */
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
        if (date.isAfter(todayOf(deviceTimeZone).plusYears(1))) {
            fieldErrors.put("date", DATE_TOO_FAR);
            return null;
        }
        return date;
    }

    /** Today in the device zone; UTC when the zone is absent or invalid. */
    private static LocalDate todayOf(String deviceTimeZone) {
        try {
            return LocalDate.now(ZoneId.of(deviceTimeZone));
        }
        catch (DateTimeException | NullPointerException exception) {
            return LocalDate.now(ZoneOffset.UTC);
        }
    }
}
