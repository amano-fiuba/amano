package com.amano.backend.groupexpense;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.amano.backend.common.FieldValidationException;
import com.amano.backend.expense.ExpenseAmountNormalizer;
import com.amano.backend.expense.ExpenseDateValidator;
import com.amano.backend.expense.ExpenseDescriptionNormalizer;
import com.amano.backend.expense.IdempotencyConflictException;
import com.amano.backend.groupexpense.exchange.BcraExchangeRateClient;
import com.amano.backend.groupexpense.exchange.ExchangeRateProvider;
import com.amano.backend.groupexpense.exchange.ExchangeRateQuote;
import com.amano.backend.groups.ActivityEventService;
import com.amano.backend.groups.ExpenseActivityDetails;
import com.amano.backend.groups.Group;
import com.amano.backend.groups.GroupMember;
import com.amano.backend.groups.GroupActivity;
import com.amano.backend.groups.GroupMemberRepository;
import com.amano.backend.groups.GroupNotFoundException;
import com.amano.backend.groups.GroupPermissionChecker;
import com.amano.backend.groups.GroupPermissionDeniedException;
import com.amano.backend.groups.GroupPermissions;
import com.amano.backend.groups.GroupPermissionsRepository;
import com.amano.backend.groups.GroupRepository;
import com.amano.backend.groups.SimulatedProfile;
import com.amano.backend.groups.SimulatedProfileRepository;
import com.amano.backend.user.Currency;
import com.amano.backend.user.User;
import com.amano.backend.user.UserRepository;

/**
 * Minimal group expense and its debts (017 US1, contracts/group-expenses-api.md).
 * The creator is always the session principal. Only active members may
 * register or read expenses (neutral 404 otherwise, as in 010); the two V9
 * casillas are decided by {@link GroupPermissionChecker} (research.md D8). The
 * amount, date and description follow the 007 rules and messages; every
 * invalid field is accumulated in one pass before any write. The expense, its
 * shares, its PENDING debts and the EXPENSE_CREATED history event are written
 * in ONE transaction (FR-013). An existing {@code (creator, Idempotency-Key)}
 * is replayed when the normalized payload matches and rejected with 422 when
 * it differs (research.md D9), exactly like {@code ExpenseService}.
 */
@Service
public class GroupExpenseService {

    static final String NOT_MEMBER = "No existe el grupo solicitado o no sos miembro";
    static final String EXPENSE_NOT_FOUND = "No existe el gasto solicitado en este grupo";
    static final String CANNOT_CREATE = "No podés registrar gastos en este grupo";
    static final String ONLY_OWN_PAYMENTS = "Solo podés registrar gastos pagados por vos";
    static final String PAYER_REQUIRED = "Indicá quién pagó";
    static final String PAYER_NOT_MEMBER = "Quien pagó tiene que ser miembro del grupo";
    static final String PARTICIPANTS_REQUIRED = "Elegí al menos un participante además de quien pagó";
    static final String PARTICIPANTS_REPEATED = "Hay participantes repetidos";
    static final String PARTICIPANTS_NOT_IN_GROUP = "Algún participante no pertenece al grupo";
    static final String AMOUNT_TOO_SMALL = "El monto no alcanza para dividirlo entre %d participantes";
    static final String UNKNOWN_PROPERTY = "No se permite informar este campo";

    /** 022 D2/T023/T024: split method and per-participant values. */
    static final String SPLIT_METHOD_INVALID = "El método de reparto debe ser EQUAL, CUSTOM o PERCENTAGE";
    static final String PAYER_MUST_PARTICIPATE = "Quien pagó tiene que participar del reparto";
    static final String CUSTOM_AMOUNT_INVALID = "Cada importe personalizado tiene que ser mayor que 0";
    static final String CUSTOM_SUM_INVALID = "Los importes personalizados deben sumar exactamente el total del gasto";
    static final String PERCENTAGE_INVALID = "Cada porcentaje tiene que ser mayor que 0";
    static final String PERCENTAGE_SUM_INVALID = "Los porcentajes deben sumar exactamente 100";
    static final String EQUAL_NO_SPLIT_VALUES = "El reparto en partes iguales no acepta importes ni porcentajes por participante";
    static final String MIXED_SPLIT_VALUES = "Elegí importe o porcentaje, no ambos";

    private static final BigDecimal ONE_HUNDRED = new BigDecimal("100");
    private static final Pattern PLAIN_DECIMAL = Pattern.compile("^\\d+(\\.\\d+)?$");

    /** Same texts as {@code ExpenseService} (007) for currency and date format. */
    static final String CURRENCY_REQUIRED = "La moneda es obligatoria";
    static final String CURRENCY_INVALID = "La moneda no es válida";
    static final String DATE_FORMAT = "La fecha debe tener el formato aaaa-mm-dd";

    private final GroupExpenseRepository expenseRepository;
    private final GroupExpenseShareRepository shareRepository;
    private final DebtRepository debtRepository;
    private final GroupRepository groupRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final GroupPermissionsRepository groupPermissionsRepository;
    private final GroupPermissionChecker permissionChecker;
    private final SimulatedProfileRepository simulatedProfileRepository;
    private final UserRepository userRepository;
    private final ActivityEventService activityEventService;
    private final ExchangeRateProvider exchangeRateProvider;

    @Autowired
    public GroupExpenseService(GroupExpenseRepository expenseRepository,
            GroupExpenseShareRepository shareRepository,
            DebtRepository debtRepository,
            GroupRepository groupRepository,
            GroupMemberRepository groupMemberRepository,
            GroupPermissionsRepository groupPermissionsRepository,
            GroupPermissionChecker permissionChecker,
            SimulatedProfileRepository simulatedProfileRepository,
            UserRepository userRepository,
            ActivityEventService activityEventService,
            ExchangeRateProvider exchangeRateProvider) {
        this.expenseRepository = expenseRepository;
        this.shareRepository = shareRepository;
        this.debtRepository = debtRepository;
        this.groupRepository = groupRepository;
        this.groupMemberRepository = groupMemberRepository;
        this.groupPermissionsRepository = groupPermissionsRepository;
        this.permissionChecker = permissionChecker;
        this.simulatedProfileRepository = simulatedProfileRepository;
        this.userRepository = userRepository;
        this.activityEventService = activityEventService;
        this.exchangeRateProvider = exchangeRateProvider;
    }

    /** 017 constructor kept for existing unit tests without multi-currency mocks. */
    public GroupExpenseService(GroupExpenseRepository expenseRepository,
            GroupExpenseShareRepository shareRepository,
            DebtRepository debtRepository,
            GroupMemberRepository groupMemberRepository,
            GroupPermissionsRepository groupPermissionsRepository,
            GroupPermissionChecker permissionChecker,
            SimulatedProfileRepository simulatedProfileRepository,
            UserRepository userRepository,
            ActivityEventService activityEventService) {
        this(expenseRepository, shareRepository, debtRepository, null, groupMemberRepository,
                groupPermissionsRepository, permissionChecker, simulatedProfileRepository,
                userRepository, activityEventService, null);
    }

    /** Result of a registration: the representation plus whether rows were inserted. */
    public record Registration(GroupExpenseResponse expense, boolean created) {
    }

    /** A validated participant with the name copied at registration time. */
    private record ResolvedParticipant(ParticipantType type, Long id, String name) {

        boolean isUser(Long userId) {
            return type == ParticipantType.REGISTERED && id.equals(userId);
        }
    }

    private record NormalizedExpense(BigDecimal amount, Currency currency, LocalDate date, String description,
            Long payerUserId, String payerName, List<ResolvedParticipant> participants, int payerIndex,
            SplitMethod splitMethod, List<BigDecimal> customAmounts, List<BigDecimal> percentages) {
    }

    /** Parsed values of the requested split; both lists are null when any participant is invalid. */
    private record ParsedSplit(List<BigDecimal> amounts, List<BigDecimal> percentages) {
    }

    /**
     * Validates the payload, replays an existing operation of the same
     * creator/key or persists a new expense with its shares, debts and history
     * event (FR-001..FR-015).
     */
    @Transactional
    public Registration create(Long callerId, Long groupId, UUID idempotencyKey,
            CreateGroupExpenseRequest request) {
        GroupMember caller = activeMembership(callerId, groupId);
        GroupPermissions permissions = groupPermissionsRepository.findByGroupId(groupId).orElse(null);
        if (!permissionChecker.canCreateExpenses(caller.getRole(), permissions)) {
            throw new GroupPermissionDeniedException(CANNOT_CREATE);
        }
        Long payer = request.getPayerUserId();
        if (payer != null && !payer.equals(callerId)
                && !permissionChecker.canExpenseForOthers(caller.getRole(), permissions)) {
            throw new GroupPermissionDeniedException(ONLY_OWN_PAYMENTS);
        }
        NormalizedExpense normalized = validateAndNormalize(groupId, request);

        Optional<GroupExpense> existing =
                expenseRepository.findByCreatedByUserIdAndIdempotencyKey(callerId, idempotencyKey);
        if (existing.isPresent()) {
            return replay(existing.get(), groupId, normalized, caller);
        }

        Currency groupCurrency = primaryCurrencyOf(groupId, normalized.currency());
        BigDecimal convertedAmount;
        BigDecimal exchangeRate;
        LocalDate rateDate;
        String rateSource;
        if (normalized.currency() == groupCurrency) {
            // 022 D8 / T054: same currency -> rate 1, no external call, convertedAmount = amount
            convertedAmount = normalized.amount();
            exchangeRate = BigDecimal.ONE;
            rateDate = normalized.date();
            rateSource = BcraExchangeRateClient.SOURCE_SAME_CURRENCY;
        }
        else {
            // 022 D8 / T050: fetch quote before any DB write; if unavailable, throws 503 and nothing is saved
            ExchangeRateQuote quote = requireExchangeRateProvider()
                    .getQuote(normalized.currency(), groupCurrency, normalized.date());
            exchangeRate = quote.rate();
            rateDate = quote.rateDate();
            rateSource = quote.rateSource();
            convertedAmount = normalized.amount()
                    .multiply(exchangeRate)
                    .setScale(2, RoundingMode.HALF_UP);
            if (normalized.splitMethod() == SplitMethod.EQUAL
                    && !EqualSplit.fits(convertedAmount, normalized.participants().size())) {
                throw new FieldValidationException(Map.of(
                        "amount", AMOUNT_TOO_SMALL.formatted(normalized.participants().size())));
            }
        }

        List<BigDecimal> amounts = sharesOf(convertedAmount, normalized);
        GroupExpense expense = new GroupExpense();
        expense.setGroupId(groupId);
        expense.setCreatedByUserId(callerId);
        expense.setPayerUserId(normalized.payerUserId());
        expense.setAmount(normalized.amount());
        expense.setCurrency(normalized.currency());
        expense.setConvertedAmount(convertedAmount);
        expense.setExchangeRate(exchangeRate);
        expense.setRateDate(rateDate);
        expense.setRateSource(rateSource);
        expense.setSplitMethod(normalized.splitMethod());
        expense.setExpenseDate(normalized.date());
        expense.setDescription(normalized.description());
        expense.setIdempotencyKey(idempotencyKey);
        GroupExpense saved = expenseRepository.save(expense);

        List<GroupExpenseShare> shares = new ArrayList<>();
        List<Debt> debts = new ArrayList<>();
        for (int position = 0; position < amounts.size(); position++) {
            ResolvedParticipant participant = normalized.participants().get(position);
            GroupExpenseShare share = participant.type() == ParticipantType.REGISTERED
                    ? GroupExpenseShare.registered(saved.getId(), position, participant.id(), participant.name(),
                            amounts.get(position))
                    : GroupExpenseShare.simulated(saved.getId(), position, participant.id(), participant.name(),
                            amounts.get(position));
            GroupExpenseShare savedShare = shareRepository.save(share);
            shares.add(savedShare);
            if (position != normalized.payerIndex()) {
                debts.add(Debt.pendingFor(saved, savedShare, groupCurrency));
            }
        }
        List<Debt> savedDebts = debtRepository.saveAll(debts);

        activityEventService.recordExpenseCreated(groupId, callerId, new ExpenseActivityDetails(
                saved.getId(), saved.getAmount(), saved.getCurrency().name(), saved.getDescription(),
                saved.getPayerUserId(), normalized.payerName()));

        return new Registration(toResponse(saved, shares, savedDebts, caller, groupCurrency), true);
    }

    /**
     * Resolves the unique-index race after the failed transaction was rolled
     * back: the winner is re-read and compared with the same rules (as 007).
     */
    @Transactional(readOnly = true)
    public Registration resolveConcurrentRegistration(Long callerId, Long groupId, UUID idempotencyKey,
            CreateGroupExpenseRequest request) {
        GroupMember caller = activeMembership(callerId, groupId);
        NormalizedExpense normalized = validateAndNormalize(groupId, request);
        GroupExpense winner = expenseRepository.findByCreatedByUserIdAndIdempotencyKey(callerId, idempotencyKey)
                .orElseThrow(() -> new IllegalStateException(
                        "Idempotency conflict without a persisted winner"));
        return replay(winner, groupId, normalized, caller);
    }

    /** Detail of an expense of a group the caller belongs to (FR-020). */
    @Transactional(readOnly = true)
    public GroupExpenseResponse get(Long callerId, Long groupId, Long expenseId) {
        GroupMember caller = activeMembership(callerId, groupId);
        GroupExpense expense = expenseRepository.findByIdAndGroupId(expenseId, groupId)
                .orElseThrow(() -> new GroupNotFoundException(EXPENSE_NOT_FOUND));
        Currency groupCurrency = primaryCurrencyOf(groupId, expense.getCurrency());
        return toResponse(expense, shareRepository.findByExpenseIdOrderByPositionAsc(expense.getId()),
                debtRepository.findByExpenseId(expense.getId()), caller, groupCurrency);
    }

    /**
     * T069 (022): read-only quote from {@code currency} to the group's primary
     * currency on {@code date}. Reuses {@link ExchangeRateProvider} (and its
     * cache); same currency answers rate 1 without calling it. Writes nothing and
     * deliberately holds no transaction, so no DB connection is kept during the
     * external call.
     */
    public ExchangeQuoteResponse getExchangeQuote(Long callerId, Long groupId, String currency, String date) {
        activeMembership(callerId, groupId);
        Currency groupCurrency = primaryCurrencyOf(groupId, Currency.ARS);
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        Currency from = null;
        try {
            from = Currency.valueOf(currency == null ? "" : currency.trim().toUpperCase());
        }
        catch (IllegalArgumentException e) {
            fieldErrors.put("currency", currency == null || currency.isBlank() ? CURRENCY_REQUIRED : CURRENCY_INVALID);
        }
        LocalDate parsedDate = null;
        try {
            parsedDate = LocalDate.parse(date == null ? "" : date.trim());
            String dateError = ExpenseDateValidator.validationError(parsedDate, null);
            if (dateError != null) {
                fieldErrors.put("date", dateError);
            }
        }
        catch (DateTimeParseException e) {
            fieldErrors.put("date", DATE_FORMAT);
        }
        if (!fieldErrors.isEmpty()) {
            throw new FieldValidationException(fieldErrors);
        }

        if (from == groupCurrency) {
            return new ExchangeQuoteResponse(
                    BigDecimal.ONE.setScale(BcraExchangeRateClient.RATE_SCALE, RoundingMode.HALF_UP),
                    parsedDate.toString(), BcraExchangeRateClient.SOURCE_SAME_CURRENCY, groupCurrency.name());
        }
        ExchangeRateQuote quote = requireExchangeRateProvider().getQuote(from, groupCurrency, parsedDate);
        return new ExchangeQuoteResponse(quote.rate(), quote.rateDate().toString(), quote.rateSource(),
                groupCurrency.name());
    }

    public static final String EXPENSE_HAS_PARTICIPANTS = "No se permite editar un gasto que ya tiene participantes";
    public static final String EXPENSE_HAS_DEBTS = "No se puede eliminar un gasto con deudas asociadas";
    public static final String EXPENSE_EDIT_EMPTY = "Enviá al menos un campo para editar";
    public static final String NOT_ALLOWED_TO_EDIT = "No podés editar este gasto";
    public static final String NOT_ALLOWED_TO_DELETE = "No podés eliminar este gasto";

    @Transactional
    public GroupExpenseResponse updatePersonal(Long callerId, Long groupId, Long expenseId,
            Map<String, String> fields) {
        GroupMember caller = activeMembership(callerId, groupId);
        GroupExpense expense = expenseRepository.findByIdAndGroupId(expenseId, groupId)
                .orElseThrow(() -> new GroupNotFoundException(EXPENSE_NOT_FOUND));

        // permission
        if (!caller.getId().getUserId().equals(expense.getCreatedByUserId())
                && !caller.getId().getUserId().equals(expense.getPayerUserId())
                && !GroupMember.ROLE_ADMIN.equals(caller.getRole())) {
            throw new GroupPermissionDeniedException(NOT_ALLOWED_TO_EDIT);
        }

        List<GroupExpenseShare> shares = shareRepository.findByExpenseIdOrderByPositionAsc(expense.getId());
        List<Debt> debts = debtRepository.findByExpenseId(expense.getId());
        if (shares.size() > 1 || !debts.isEmpty()) {
            throw new GroupExpenseConflictException(EXPENSE_HAS_PARTICIPANTS, "EXPENSE_HAS_PARTICIPANTS");
        }

        if (fields == null || fields.isEmpty()) {
            throw new FieldValidationException(Map.of("body", EXPENSE_EDIT_EMPTY));
        }

        // apply
        BigDecimal newAmount = expense.getAmount();
        Currency newCurrency = expense.getCurrency();
        LocalDate newDate = expense.getExpenseDate();
        String newDescription = expense.getDescription();

        if (fields.containsKey("amount")) {
            newAmount = ExpenseAmountNormalizer.normalize(fields.get("amount"));
        }
        if (fields.containsKey("currency")) {
            newCurrency = Currency.valueOf(fields.get("currency"));
        }
        if (fields.containsKey("date")) {
            newDate = LocalDate.parse(fields.get("date"));
            String err = ExpenseDateValidator.validationError(newDate, null);
            if (err != null) {
                throw new FieldValidationException(Map.of("date", err));
            }
        }
        if (fields.containsKey("description")) {
            newDescription = ExpenseDescriptionNormalizer.normalize(fields.get("description"));
        }

        Currency groupCurrency = primaryCurrencyOf(groupId, newCurrency);
        BigDecimal convertedAmount = newAmount;
        BigDecimal exchangeRate = BigDecimal.ONE;
        LocalDate rateDate = newDate;
        String rateSource = "SAME_CURRENCY";
        if (newCurrency != groupCurrency) {
            ExchangeRateQuote quote = exchangeRateProvider.getQuote(newCurrency, groupCurrency, newDate);
            if (quote == null) {
                throw new com.amano.backend.groupexpense.exchange.ExchangeRateUnavailableException();
            }
            exchangeRate = quote.rate();
            convertedAmount = newAmount.multiply(exchangeRate).setScale(2, RoundingMode.HALF_UP);
            rateDate = quote.rateDate();
            rateSource = quote.rateSource();
        }

        expense.setAmount(newAmount);
        expense.setCurrency(newCurrency);
        expense.setExpenseDate(newDate);
        expense.setDescription(newDescription);
        expense.setConvertedAmount(convertedAmount);
        expense.setExchangeRate(exchangeRate);
        expense.setRateDate(rateDate);
        expense.setRateSource(rateSource);

        if (!shares.isEmpty()) {
            shares.get(0).setAmount(convertedAmount);
            shareRepository.save(shares.get(0));
        }
        GroupExpense saved = expenseRepository.save(expense);
        activityEventService.record(groupId, GroupActivity.GroupActivityType.EXPENSE_EDITED,
                callerId, Map.of("expenseId", expenseId));
        return toResponse(saved, shareRepository.findByExpenseIdOrderByPositionAsc(saved.getId()),
                debtRepository.findByExpenseId(saved.getId()), caller, groupCurrency);
    }

    @Transactional
    public void deletePersonal(Long callerId, Long groupId, Long expenseId) {
        GroupMember caller = activeMembership(callerId, groupId);
        GroupExpense expense = expenseRepository.findByIdAndGroupId(expenseId, groupId)
                .orElseThrow(() -> new GroupNotFoundException(EXPENSE_NOT_FOUND));

        if (!caller.getId().getUserId().equals(expense.getCreatedByUserId())
                && !caller.getId().getUserId().equals(expense.getPayerUserId())
                && !GroupMember.ROLE_ADMIN.equals(caller.getRole())) {
            throw new GroupPermissionDeniedException(NOT_ALLOWED_TO_DELETE);
        }

        List<Debt> debts = debtRepository.findByExpenseId(expense.getId());
        if (!debts.isEmpty()) {
            throw new GroupExpenseConflictException(EXPENSE_HAS_DEBTS, "EXPENSE_HAS_DEBTS");
        }
        List<GroupExpenseShare> shares = shareRepository.findByExpenseIdOrderByPositionAsc(expense.getId());
        if (shares.size() > 1) {
            throw new GroupExpenseConflictException("No se permite eliminar un gasto que ya tiene participantes",
                    "EXPENSE_HAS_PARTICIPANTS");
        }

        shareRepository.deleteAll(shares);
        expenseRepository.delete(expense);
        activityEventService.record(groupId, GroupActivity.GroupActivityType.EXPENSE_DELETED,
                callerId, Map.of("expenseId", expenseId));
    }

    private Registration replay(GroupExpense existing, Long groupId, NormalizedExpense normalized,
            GroupMember caller) {
        List<GroupExpenseShare> shares = shareRepository.findByExpenseIdOrderByPositionAsc(existing.getId());
        if (!existing.getGroupId().equals(groupId) || !samePayload(existing, shares, normalized)) {
            throw new IdempotencyConflictException();
        }
        Currency groupCurrency = primaryCurrencyOf(groupId, existing.getCurrency());
        return new Registration(toResponse(existing, shares, debtRepository.findByExpenseId(existing.getId()),
                caller, groupCurrency), false);
    }

    private Currency primaryCurrencyOf(Long groupId, Currency fallback) {
        if (groupRepository == null) {
            return fallback;
        }
        return groupRepository.findById(groupId)
                .map(Group::getPrimaryCurrency)
                .filter(Objects::nonNull)
                .orElse(fallback);
    }

    private ExchangeRateProvider requireExchangeRateProvider() {
        if (exchangeRateProvider == null) {
            throw new IllegalStateException("ExchangeRateProvider no configurado");
        }
        return exchangeRateProvider;
    }

    private static boolean samePayload(GroupExpense existing, List<GroupExpenseShare> shares,
            NormalizedExpense normalized) {
        if (existing.getSplitMethod() != normalized.splitMethod()
                || existing.getAmount().compareTo(normalized.amount()) != 0
                || existing.getCurrency() != normalized.currency()
                || !existing.getExpenseDate().equals(normalized.date())
                || !Objects.equals(existing.getDescription(), normalized.description())
                || !existing.getPayerUserId().equals(normalized.payerUserId())
                || shares.size() != normalized.participants().size()) {
            return false;
        }
        BigDecimal total = existing.getConvertedAmount() == null
                ? existing.getAmount() : existing.getConvertedAmount();
        List<BigDecimal> expected = expectedShares(total, normalized);
        if (expected == null) {
            return false;
        }
        for (int i = 0; i < shares.size(); i++) {
            GroupExpenseShare share = shares.get(i);
            ResolvedParticipant participant = normalized.participants().get(i);
            Long shareId = share.getParticipantType() == ParticipantType.REGISTERED
                    ? share.getUserId() : share.getSimulatedProfileId();
            if (share.getParticipantType() != participant.type() || !Objects.equals(shareId, participant.id())
                    || share.getAmount().compareTo(expected.get(i)) != 0) {
                return false;
            }
        }
        return true;
    }

    /**
     * Shares computed from the converted total for the requested method. Used
     * both to persist a new expense and to compare an idempotent replay.
     */
    private static List<BigDecimal> sharesOf(BigDecimal total, NormalizedExpense normalized) {
        return switch (normalized.splitMethod()) {
            case EQUAL -> EqualSplit.split(total, normalized.participants().size(), normalized.payerIndex());
            case CUSTOM -> {
                if (sumOf(normalized.customAmounts()).compareTo(normalized.amount()) != 0) {
                    throw new FieldValidationException(Map.of("participants", CUSTOM_SUM_INVALID));
                }
                yield ExpenseSplit.custom(total, normalized.customAmounts(), normalized.amount(),
                        normalized.payerIndex());
            }
            case PERCENTAGE -> {
                if (sumOf(normalized.percentages()).compareTo(ONE_HUNDRED) != 0) {
                    throw new FieldValidationException(Map.of("participants", PERCENTAGE_SUM_INVALID));
                }
                List<BigDecimal> shares =
                        ExpenseSplit.percentage(total, normalized.percentages(), normalized.payerIndex());
                if (shares.stream().anyMatch(share -> share.compareTo(ExpenseAmountNormalizer.MIN) < 0)) {
                    throw new FieldValidationException(Map.of("amount",
                            AMOUNT_TOO_SMALL.formatted(normalized.participants().size())));
                }
                yield shares;
            }
        };
    }

    private static List<BigDecimal> expectedShares(BigDecimal total, NormalizedExpense normalized) {
        if (normalized.splitMethod() == SplitMethod.EQUAL && !EqualSplit.fits(total, normalized.participants().size())) {
            return null;
        }
        if (normalized.splitMethod() == SplitMethod.CUSTOM
                && sumOf(normalized.customAmounts()).compareTo(normalized.amount()) != 0) {
            return null;
        }
        if (normalized.splitMethod() == SplitMethod.PERCENTAGE
                && sumOf(normalized.percentages()).compareTo(ONE_HUNDRED) != 0) {
            return null;
        }
        try {
            return sharesOf(total, normalized);
        }
        catch (FieldValidationException | IllegalArgumentException exception) {
            return null;
        }
    }

    private static BigDecimal sumOf(List<BigDecimal> values) {
        if (values == null) {
            return BigDecimal.ZERO;
        }
        return values.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private GroupMember activeMembership(Long userId, Long groupId) {
        return groupMemberRepository.findByIdGroupIdAndIdUserId(groupId, userId)
                .filter(membership -> membership.getLeftAt() == null)
                .orElseThrow(() -> new GroupNotFoundException(NOT_MEMBER));
    }

    private NormalizedExpense validateAndNormalize(Long groupId, CreateGroupExpenseRequest request) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();

        String amountError = ExpenseAmountNormalizer.validationError(request.getAmount());
        if (amountError != null) {
            fieldErrors.put("amount", amountError);
        }
        Currency currency = parseCurrency(request.getCurrency(), fieldErrors);
        LocalDate date = parseDate(request.getDate(), request.getDeviceTimeZone(), fieldErrors);
        String descriptionError = ExpenseDescriptionNormalizer.validationError(request.getDescription());
        if (descriptionError != null) {
            fieldErrors.put("description", descriptionError);
        }

        Map<Long, String> activeMemberNames = activeMemberNames(groupId);
        Long payer = request.getPayerUserId();
        if (payer == null) {
            fieldErrors.put("payerUserId", PAYER_REQUIRED);
        }
        else if (!activeMemberNames.containsKey(payer)) {
            fieldErrors.put("payerUserId", PAYER_NOT_MEMBER);
        }
        List<ResolvedParticipant> participants = resolveParticipants(groupId, request.getParticipants(),
                payer, activeMemberNames, fieldErrors);
        SplitMethod splitMethod = parseSplitMethod(request.getSplitMethod(), fieldErrors);
        int payerIndex = payerIndex(participants, payer);
        List<BigDecimal> customAmounts = null;
        List<BigDecimal> percentages = null;

        // Only validate split values when the method itself is valid; an unknown
        // method already answers with SPLIT_METHOD_INVALID.
        if (participants != null && !fieldErrors.containsKey("splitMethod")) {
            if (splitMethod == SplitMethod.EQUAL) {
                if (request.getParticipants().stream().anyMatch(GroupExpenseService::hasSplitValue)) {
                    fieldErrors.put("participants", EQUAL_NO_SPLIT_VALUES);
                }
            }
            else {
                if (payerIndex < 0 && payer != null && activeMemberNames.containsKey(payer)) {
                    fieldErrors.put("participants", PAYER_MUST_PARTICIPATE);
                }
                ParsedSplit parsed = validateSplitValues(request.getParticipants(), splitMethod, fieldErrors);
                if (parsed.amounts() != null) {
                    if (splitMethod == SplitMethod.CUSTOM) {
                        customAmounts = parsed.amounts();
                    }
                    else {
                        percentages = parsed.percentages();
                        if (sumOf(percentages).compareTo(ONE_HUNDRED) != 0) {
                            fieldErrors.put("participants", PERCENTAGE_SUM_INVALID);
                            percentages = null;
                        }
                    }
                }
            }
        }

        if (amountError == null && participants != null && splitMethod == SplitMethod.EQUAL) {
            BigDecimal amount = ExpenseAmountNormalizer.normalize(request.getAmount());
            if (!EqualSplit.fits(amount, participants.size())) {
                fieldErrors.put("amount", AMOUNT_TOO_SMALL.formatted(participants.size()));
            }
        }
        for (String unknown : request.getUnknownProperties().keySet()) {
            fieldErrors.put(unknown, UNKNOWN_PROPERTY);
        }

        if (!fieldErrors.isEmpty()) {
            throw new FieldValidationException(fieldErrors);
        }

        return new NormalizedExpense(ExpenseAmountNormalizer.normalize(request.getAmount()), currency, date,
                ExpenseDescriptionNormalizer.normalize(request.getDescription()), payer,
                activeMemberNames.get(payer), participants, payerIndex, splitMethod, customAmounts, percentages);
    }

    private static SplitMethod parseSplitMethod(String raw, Map<String, String> fieldErrors) {
        if (raw == null || raw.isBlank()) {
            return SplitMethod.EQUAL;
        }
        try {
            return SplitMethod.valueOf(raw);
        }
        catch (IllegalArgumentException exception) {
            fieldErrors.put("splitMethod", SPLIT_METHOD_INVALID);
            return SplitMethod.EQUAL;
        }
    }

    private static int payerIndex(List<ResolvedParticipant> participants, Long payer) {
        if (participants == null || payer == null) {
            return -1;
        }
        for (int i = 0; i < participants.size(); i++) {
            if (participants.get(i).isUser(payer)) {
                return i;
            }
        }
        return -1;
    }

    private static boolean hasSplitValue(CreateGroupExpenseRequest.Participant participant) {
        return participant != null
                && (isPresent(participant.amount()) || isPresent(participant.percentage()));
    }

    private static boolean isPresent(String raw) {
        return raw != null && !raw.isBlank();
    }

    /** Validates every participant value for the requested method, accumulating one error. */
    private static ParsedSplit validateSplitValues(List<CreateGroupExpenseRequest.Participant> raw,
            SplitMethod method, Map<String, String> fieldErrors) {
        List<BigDecimal> amounts = new ArrayList<>();
        List<BigDecimal> percentages = new ArrayList<>();
        boolean invalid = false;
        for (CreateGroupExpenseRequest.Participant participant : raw) {
            String rawAmount = participant == null ? null : participant.amount();
            String rawPercentage = participant == null ? null : participant.percentage();
            boolean hasAmount = isPresent(rawAmount);
            boolean hasPercentage = isPresent(rawPercentage);
            if (hasAmount && hasPercentage) {
                fieldErrors.put("participants", MIXED_SPLIT_VALUES);
                invalid = true;
            }
            else if (method == SplitMethod.CUSTOM) {
                BigDecimal amount = customAmount(rawAmount);
                if (amount == null) {
                    fieldErrors.put("participants", CUSTOM_AMOUNT_INVALID);
                    invalid = true;
                }
                else {
                    amounts.add(amount);
                }
            }
            else {
                BigDecimal percentage = percentage(rawPercentage);
                if (percentage == null) {
                    fieldErrors.put("participants", PERCENTAGE_INVALID);
                    invalid = true;
                }
                else {
                    percentages.add(percentage);
                }
            }
        }
        return invalid ? new ParsedSplit(null, null) : new ParsedSplit(amounts, percentages);
    }

    /** Canonical positive amount with at most two decimals, or null when invalid. */
    private static BigDecimal customAmount(String raw) {
        if (!isPresent(raw) || !PLAIN_DECIMAL.matcher(raw.trim()).matches()) {
            return null;
        }
        BigDecimal value = new BigDecimal(raw.trim());
        if (value.scale() > 2 || value.signum() <= 0) {
            return null;
        }
        return value.setScale(2);
    }

    /** Plain positive decimal percentage, or null when invalid. */
    private static BigDecimal percentage(String raw) {
        if (!isPresent(raw) || !PLAIN_DECIMAL.matcher(raw.trim()).matches()) {
            return null;
        }
        BigDecimal value = new BigDecimal(raw.trim());
        return value.signum() > 0 ? value : null;
    }

    /** Visible names of the active registered members of the group, by user id. */
    private Map<Long, String> activeMemberNames(Long groupId) {
        Set<Long> ids = groupMemberRepository.findByIdGroupIdAndLeftAtIsNull(groupId).stream()
                .map(membership -> membership.getId().getUserId())
                .collect(Collectors.toSet());
        Map<Long, String> names = new HashMap<>();
        for (User user : userRepository.findAllById(ids)) {
            if (ids.contains(user.getId())) {
                names.put(user.getId(), nameOf(user));
            }
        }
        return names;
    }

    /**
     * Participants in request order, or {@code null} after recording the field
     * error: at least one besides the payer, no repetitions, all of the group.
     */
    private List<ResolvedParticipant> resolveParticipants(Long groupId,
            List<CreateGroupExpenseRequest.Participant> raw, Long payer, Map<Long, String> activeMemberNames,
            Map<String, String> fieldErrors) {
        if (raw == null || raw.isEmpty()) {
            fieldErrors.put("participants", PARTICIPANTS_REQUIRED);
            return null;
        }
        Map<Long, SimulatedProfile> profiles = simulatedProfileRepository.findByGroupId(groupId).stream()
                .collect(Collectors.toMap(SimulatedProfile::getId, Function.identity()));
        List<ResolvedParticipant> resolved = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        boolean repeated = false;
        for (CreateGroupExpenseRequest.Participant participant : raw) {
            ResolvedParticipant one = resolve(participant, activeMemberNames, profiles);
            if (one == null) {
                fieldErrors.put("participants", PARTICIPANTS_NOT_IN_GROUP);
                return null;
            }
            repeated |= !seen.add(one.type() + ":" + one.id());
            resolved.add(one);
        }
        if (repeated) {
            fieldErrors.put("participants", PARTICIPANTS_REPEATED);
            return null;
        }
        // A participant list made up solely of the payer is a personal expense:
        // no debts are generated and splitMethod does not apply (022 T067).
        return resolved;
    }

    private static ResolvedParticipant resolve(CreateGroupExpenseRequest.Participant participant,
            Map<Long, String> activeMemberNames, Map<Long, SimulatedProfile> profiles) {
        if (participant == null || participant.type() == null) {
            return null;
        }
        if (ParticipantType.REGISTERED.name().equals(participant.type())) {
            Long userId = participant.userId();
            if (userId == null || participant.simulatedProfileId() != null
                    || !activeMemberNames.containsKey(userId)) {
                return null;
            }
            return new ResolvedParticipant(ParticipantType.REGISTERED, userId, activeMemberNames.get(userId));
        }
        if (ParticipantType.SIMULATED.name().equals(participant.type())) {
            Long profileId = participant.simulatedProfileId();
            if (profileId == null || participant.userId() != null || !profiles.containsKey(profileId)) {
                return null;
            }
            return new ResolvedParticipant(ParticipantType.SIMULATED, profileId, profiles.get(profileId).getName());
        }
        return null;
    }

    private static Currency parseCurrency(String raw, Map<String, String> fieldErrors) {
        if (raw == null || raw.isBlank()) {
            fieldErrors.put("currency", CURRENCY_REQUIRED);
            return null;
        }
        try {
            return Currency.valueOf(raw);
        }
        catch (IllegalArgumentException exception) {
            fieldErrors.put("currency", CURRENCY_INVALID);
            return null;
        }
    }

    private static LocalDate parseDate(String raw, String deviceTimeZone, Map<String, String> fieldErrors) {
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

    private GroupExpenseResponse toResponse(GroupExpense expense, List<GroupExpenseShare> shares,
            List<Debt> debts, GroupMember caller, Currency groupCurrency) {
        Map<Long, String> names = new HashMap<>();
        Set<Long> people = new HashSet<>(List.of(expense.getPayerUserId(), expense.getCreatedByUserId()));
        for (User user : userRepository.findAllById(people)) {
            names.put(user.getId(), nameOf(user));
        }
        Map<Long, Integer> positionOfShare = new HashMap<>();
        List<GroupExpenseResponse.Share> shareViews = new ArrayList<>();
        for (GroupExpenseShare share : shares) {
            positionOfShare.put(share.getId(), share.getPosition());
            shareViews.add(new GroupExpenseResponse.Share(share.getPosition(), share.getParticipantType().name(),
                    share.getUserId(), share.getSimulatedProfileId(), share.getParticipantName(),
                    share.getAmount().toPlainString(), share.isOf(expense.getPayerUserId())));
        }
        GroupExpenseResponse.Person payer =
                new GroupExpenseResponse.Person(expense.getPayerUserId(), names.get(expense.getPayerUserId()));
        List<GroupExpenseResponse.DebtView> debtViews = debts.stream()
                .sorted((a, b) -> Integer.compare(positionOfShare.getOrDefault(a.getShareId(), 0),
                        positionOfShare.getOrDefault(b.getShareId(), 0)))
                .map(debt -> GroupExpenseResponse.DebtView.of(debt, names.get(debt.getCreditorUserId()),
                        canRegisterPayment(debt, caller)))
                .toList();

        String convertedAmount = expense.getConvertedAmount() == null
                ? null : expense.getConvertedAmount().setScale(2, RoundingMode.HALF_UP).toPlainString();
        String convertedCurrency = convertedAmount == null || groupCurrency == null
                ? null : groupCurrency.name();
        String exchangeRate = expense.getExchangeRate() == null
                ? null : expense.getExchangeRate().stripTrailingZeros().toPlainString();
        String rateDate = expense.getRateDate() == null
                ? null : expense.getRateDate().toString();

        return new GroupExpenseResponse(
                expense.getId(),
                expense.getGroupId(),
                expense.getAmount().toPlainString(),
                expense.getCurrency().name(),
                convertedAmount,
                convertedCurrency,
                exchangeRate,
                rateDate,
                expense.getRateSource(),
                expense.getExpenseDate().toString(),
                expense.getDescription(),
                payer,
                new GroupExpenseResponse.Person(expense.getCreatedByUserId(),
                        names.get(expense.getCreatedByUserId())),
                expense.getCreatedAt(),
                shareViews,
                debtViews,
                expense.getSplitMethod() == null ? SplitMethod.EQUAL.name()
                        : expense.getSplitMethod().name());
    }

    /**
     * 018 FR-016 / 022: the debt accepts a payment (pending, partially paid or overdue; not settled and
     * with no payment waiting for validation) and the caller is its debtor, its creditor or an ADMIN.
     */
    private boolean canRegisterPayment(Debt debt, GroupMember caller) {
        return debt.getStatus().isPayable()
                && permissionChecker.canRegisterPayment(caller.getRole(), caller.getId().getUserId(),
                        debt.getDebtorUserId(), debt.getCreditorUserId());
    }

    private static String nameOf(User user) {
        String name = ActivityEventService.displayName(user);
        return name == null ? user.getEmail() : name;
    }
}
