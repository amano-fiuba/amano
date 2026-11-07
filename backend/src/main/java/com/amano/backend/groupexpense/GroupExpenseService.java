package com.amano.backend.groupexpense;

import java.math.BigDecimal;
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
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.amano.backend.common.FieldValidationException;
import com.amano.backend.expense.ExpenseAmountNormalizer;
import com.amano.backend.expense.ExpenseDateValidator;
import com.amano.backend.expense.ExpenseDescriptionNormalizer;
import com.amano.backend.expense.IdempotencyConflictException;
import com.amano.backend.groups.ActivityEventService;
import com.amano.backend.groups.ExpenseActivityDetails;
import com.amano.backend.groups.GroupMember;
import com.amano.backend.groups.GroupMemberRepository;
import com.amano.backend.groups.GroupNotFoundException;
import com.amano.backend.groups.GroupPermissionChecker;
import com.amano.backend.groups.GroupPermissionDeniedException;
import com.amano.backend.groups.GroupPermissions;
import com.amano.backend.groups.GroupPermissionsRepository;
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

    /** Same texts as {@code ExpenseService} (007) for currency and date format. */
    static final String CURRENCY_REQUIRED = "La moneda es obligatoria";
    static final String CURRENCY_INVALID = "La moneda no es válida";
    static final String DATE_FORMAT = "La fecha debe tener el formato aaaa-mm-dd";

    private final GroupExpenseRepository expenseRepository;
    private final GroupExpenseShareRepository shareRepository;
    private final DebtRepository debtRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final GroupPermissionsRepository groupPermissionsRepository;
    private final GroupPermissionChecker permissionChecker;
    private final SimulatedProfileRepository simulatedProfileRepository;
    private final UserRepository userRepository;
    private final ActivityEventService activityEventService;

    public GroupExpenseService(GroupExpenseRepository expenseRepository,
            GroupExpenseShareRepository shareRepository,
            DebtRepository debtRepository,
            GroupMemberRepository groupMemberRepository,
            GroupPermissionsRepository groupPermissionsRepository,
            GroupPermissionChecker permissionChecker,
            SimulatedProfileRepository simulatedProfileRepository,
            UserRepository userRepository,
            ActivityEventService activityEventService) {
        this.expenseRepository = expenseRepository;
        this.shareRepository = shareRepository;
        this.debtRepository = debtRepository;
        this.groupMemberRepository = groupMemberRepository;
        this.groupPermissionsRepository = groupPermissionsRepository;
        this.permissionChecker = permissionChecker;
        this.simulatedProfileRepository = simulatedProfileRepository;
        this.userRepository = userRepository;
        this.activityEventService = activityEventService;
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
            Long payerUserId, String payerName, List<ResolvedParticipant> participants, int payerIndex) {
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

        GroupExpense expense = new GroupExpense();
        expense.setGroupId(groupId);
        expense.setCreatedByUserId(callerId);
        expense.setPayerUserId(normalized.payerUserId());
        expense.setAmount(normalized.amount());
        expense.setCurrency(normalized.currency());
        expense.setExpenseDate(normalized.date());
        expense.setDescription(normalized.description());
        expense.setIdempotencyKey(idempotencyKey);
        GroupExpense saved = expenseRepository.save(expense);

        List<BigDecimal> amounts = EqualSplit.split(normalized.amount(), normalized.participants().size(),
                normalized.payerIndex());
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
                debts.add(Debt.pendingFor(saved, savedShare));
            }
        }
        List<Debt> savedDebts = debtRepository.saveAll(debts);

        activityEventService.recordExpenseCreated(groupId, callerId, new ExpenseActivityDetails(
                saved.getId(), saved.getAmount(), saved.getCurrency().name(), saved.getDescription(),
                saved.getPayerUserId(), normalized.payerName()));

        return new Registration(toResponse(saved, shares, savedDebts, caller), true);
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
        return toResponse(expense, shareRepository.findByExpenseIdOrderByPositionAsc(expense.getId()),
                debtRepository.findByExpenseId(expense.getId()), caller);
    }

    private Registration replay(GroupExpense existing, Long groupId, NormalizedExpense normalized,
            GroupMember caller) {
        List<GroupExpenseShare> shares = shareRepository.findByExpenseIdOrderByPositionAsc(existing.getId());
        if (!existing.getGroupId().equals(groupId) || !samePayload(existing, shares, normalized)) {
            throw new IdempotencyConflictException();
        }
        return new Registration(toResponse(existing, shares, debtRepository.findByExpenseId(existing.getId()),
                caller), false);
    }

    private static boolean samePayload(GroupExpense existing, List<GroupExpenseShare> shares,
            NormalizedExpense normalized) {
        if (existing.getAmount().compareTo(normalized.amount()) != 0
                || existing.getCurrency() != normalized.currency()
                || !existing.getExpenseDate().equals(normalized.date())
                || !Objects.equals(existing.getDescription(), normalized.description())
                || !existing.getPayerUserId().equals(normalized.payerUserId())
                || shares.size() != normalized.participants().size()) {
            return false;
        }
        for (int i = 0; i < shares.size(); i++) {
            GroupExpenseShare share = shares.get(i);
            ResolvedParticipant participant = normalized.participants().get(i);
            Long shareId = share.getParticipantType() == ParticipantType.REGISTERED
                    ? share.getUserId() : share.getSimulatedProfileId();
            if (share.getParticipantType() != participant.type() || !Objects.equals(shareId, participant.id())) {
                return false;
            }
        }
        return true;
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

        if (amountError == null && participants != null) {
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

        int payerIndex = -1;
        for (int i = 0; i < participants.size(); i++) {
            if (participants.get(i).isUser(payer)) {
                payerIndex = i;
            }
        }
        return new NormalizedExpense(ExpenseAmountNormalizer.normalize(request.getAmount()), currency, date,
                ExpenseDescriptionNormalizer.normalize(request.getDescription()), payer,
                activeMemberNames.get(payer), participants, payerIndex);
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
        if (resolved.stream().allMatch(one -> one.isUser(payer))) {
            fieldErrors.put("participants", PARTICIPANTS_REQUIRED);
            return null;
        }
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
            List<Debt> debts, GroupMember caller) {
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
        return new GroupExpenseResponse(expense.getId(), expense.getGroupId(), expense.getAmount().toPlainString(),
                expense.getCurrency().name(), expense.getExpenseDate().toString(), expense.getDescription(), payer,
                new GroupExpenseResponse.Person(expense.getCreatedByUserId(),
                        names.get(expense.getCreatedByUserId())),
                expense.getCreatedAt(), shareViews, debtViews);
    }

    /** 018 FR-016: the debt is not settled and the caller is its debtor, its creditor or an ADMIN. */
    private boolean canRegisterPayment(Debt debt, GroupMember caller) {
        return !debt.getStatus().isSettled()
                && permissionChecker.canRegisterPayment(caller.getRole(), caller.getId().getUserId(),
                        debt.getDebtorUserId(), debt.getCreditorUserId());
    }

    private static String nameOf(User user) {
        String name = ActivityEventService.displayName(user);
        return name == null ? user.getEmail() : name;
    }
}
