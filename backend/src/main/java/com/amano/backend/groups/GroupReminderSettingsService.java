package com.amano.backend.groups;

import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.amano.backend.common.FieldValidationException;
import com.amano.backend.groupexpense.Debt;
import com.amano.backend.groupexpense.DebtRepository;
import com.amano.backend.groupexpense.GroupExpense;
import com.amano.backend.groupexpense.GroupExpenseRepository;
import com.amano.backend.groupexpense.reminder.Reminder;
import com.amano.backend.groupexpense.reminder.ReminderKind;
import com.amano.backend.groupexpense.reminder.ReminderRepository;
import com.amano.backend.groupexpense.reminder.ReminderSchedule;
import com.amano.backend.groupexpense.reminder.ReminderStatus;
import com.amano.backend.groups.dto.ReminderSettingsRequest;
import com.amano.backend.groups.dto.ReminderSettingsResponse;

/**
 * Reminder settings of a group (025 T025). Admin-only: a non-admin member gets
 * 403 and a non-member or unknown group the same neutral 404. The update is
 * atomic, validates after trimming and immediately reschedules the
 * {@code SCHEDULED} rows that were never claimed.
 */
@Service
public class GroupReminderSettingsService {

    public static final int DEFAULT_SEND_DAYS = 5;
    public static final List<Integer> ALLOWED_SEND_DAYS = List.of(7, 5, 3, 1);
    static final String INVALID_SEND_DAYS = "El tiempo de envío debe ser 7, 5, 3 o 1 días antes";
    static final String MESSAGE_TOO_LONG = "El mensaje no puede superar los 500 caracteres";
    static final int MAX_MESSAGE_LENGTH = 500;

    private final GroupRepository groupRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final GroupPermissionChecker permissionChecker;
    private final ReminderRepository reminderRepository;
    private final DebtRepository debtRepository;
    private final GroupExpenseRepository groupExpenseRepository;
    private final Clock clock;

    public GroupReminderSettingsService(GroupRepository groupRepository,
            GroupMemberRepository groupMemberRepository, GroupPermissionChecker permissionChecker,
            ReminderRepository reminderRepository, DebtRepository debtRepository,
            GroupExpenseRepository groupExpenseRepository, Clock clock) {
        this.groupRepository = groupRepository;
        this.groupMemberRepository = groupMemberRepository;
        this.permissionChecker = permissionChecker;
        this.reminderRepository = reminderRepository;
        this.debtRepository = debtRepository;
        this.groupExpenseRepository = groupExpenseRepository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public ReminderSettingsResponse getSettings(Long callerId, Long groupId) {
        Group group = requireAdmin(callerId, groupId,
                "Solo un administrador puede ver la configuración de recordatorios");
        return toResponse(group);
    }

    @Transactional
    public ReminderSettingsResponse updateSettings(Long callerId, Long groupId,
            ReminderSettingsRequest request) {
        Group group = requireAdmin(callerId, groupId,
                "Solo un administrador puede modificar la configuración de recordatorios");
        Map<String, String> errors = new LinkedHashMap<>();
        int sendDays = group.getReminderSendDays() == null
                ? DEFAULT_SEND_DAYS : group.getReminderSendDays();
        boolean sendDaysChanged = false;
        if (request != null && request.isSendDaysPresent()) {
            Integer value = request.getSendDays();
            if (value == null || !ALLOWED_SEND_DAYS.contains(value)) {
                errors.put("sendDays", INVALID_SEND_DAYS);
            }
            else {
                sendDays = value;
                sendDaysChanged = true;
            }
        }
        String message = group.getReminderMessage();
        boolean messageChanged = false;
        if (request != null && request.isMessagePresent()) {
            String normalized = normalizeMessage(request.getMessage());
            if (normalized != null && normalized.length() > MAX_MESSAGE_LENGTH) {
                errors.put("message", MESSAGE_TOO_LONG);
            }
            else {
                message = normalized;
                messageChanged = true;
            }
        }
        if (!errors.isEmpty()) {
            throw new FieldValidationException(errors);
        }
        if (sendDaysChanged) {
            group.setReminderSendDays(sendDays);
        }
        if (messageChanged) {
            group.setReminderMessage(message);
        }
        groupRepository.save(group);
        if (sendDaysChanged) {
            reschedule(group, sendDays);
        }
        return toResponse(group);
    }

    private Group requireAdmin(Long callerId, Long groupId, String forbiddenMessage) {
        GroupMember membership = groupMemberRepository.findByIdGroupIdAndIdUserId(groupId, callerId)
                .filter(row -> row.getLeftAt() == null)
                .orElseThrow(() -> new GroupNotFoundException(
                        "No existe el grupo solicitado o no sos miembro"));
        if (!permissionChecker.canManageReminderSettings(membership.getRole())) {
            throw new GroupPermissionDeniedException(forbiddenMessage);
        }
        return groupRepository.findById(groupId)
                .orElseThrow(() -> new GroupNotFoundException(
                        "No existe el grupo solicitado o no sos miembro"));
    }

    private void reschedule(Group group, int sendDays) {
        if (group.getDebtDueDays() == null) {
            return;
        }
        OffsetDateTime now = OffsetDateTime.now(clock);
        for (Reminder reminder : reminderRepository.findByGroupIdAndStatusAndClaimedAtIsNull(group.getId(),
                ReminderStatus.SCHEDULED)) {
            GroupExpense expense = expenseOf(reminder);
            if (expense == null || expense.getExpenseDate() == null) {
                continue;
            }
            LocalDate dueDate = ReminderSchedule.dueDate(expense.getExpenseDate(), group.getDebtDueDays());
            LocalDate sendDate = ReminderSchedule.sendDate(dueDate, sendDays);
            if (!sendDate.equals(reminder.getSendDate())) {
                reminderRepository.reschedule(reminder.getId(), sendDate, now);
            }
        }
    }

    private GroupExpense expenseOf(Reminder reminder) {
        if (reminder.getKind() == ReminderKind.PAYER_NOTICE) {
            return reminder.getExpenseId() == null ? null
                    : groupExpenseRepository.findById(reminder.getExpenseId()).orElse(null);
        }
        Debt debt = reminder.getDebtId() == null ? null
                : debtRepository.findById(reminder.getDebtId()).orElse(null);
        return debt == null ? null : groupExpenseRepository.findById(debt.getExpenseId()).orElse(null);
    }

    private ReminderSettingsResponse toResponse(Group group) {
        int sendDays = group.getReminderSendDays() == null
                ? DEFAULT_SEND_DAYS : group.getReminderSendDays();
        return new ReminderSettingsResponse(sendDays, group.getReminderMessage(),
                DEFAULT_SEND_DAYS, ALLOWED_SEND_DAYS);
    }

    private static String normalizeMessage(String message) {
        if (message == null) {
            return null;
        }
        String trimmed = message.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
