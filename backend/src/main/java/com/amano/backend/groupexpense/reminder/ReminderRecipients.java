package com.amano.backend.groupexpense.reminder;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.springframework.stereotype.Component;

import com.amano.backend.groupexpense.Debt;
import com.amano.backend.groupexpense.GroupExpense;
import com.amano.backend.groupexpense.ParticipantType;
import com.amano.backend.groups.GroupMemberRepository;
import com.amano.backend.groups.SimulatedProfile;
import com.amano.backend.groups.SimulatedProfileRepository;
import com.amano.backend.user.User;
import com.amano.backend.user.UserRepository;

/**
 * Resolves the recipients of the reminders (025): a registered debtor uses its
 * {@code User.email}, a simulated debtor its {@code SimulatedProfile.email},
 * and the failure notice goes to every active admin of the group.
 */
@Component
public class ReminderRecipients {

    private final UserRepository userRepository;
    private final SimulatedProfileRepository simulatedProfileRepository;
    private final GroupMemberRepository groupMemberRepository;

    public ReminderRecipients(UserRepository userRepository,
            SimulatedProfileRepository simulatedProfileRepository,
            GroupMemberRepository groupMemberRepository) {
        this.userRepository = userRepository;
        this.simulatedProfileRepository = simulatedProfileRepository;
        this.groupMemberRepository = groupMemberRepository;
    }

    /** Email of the debtor, or empty when the account/profile no longer exists. */
    public Optional<String> emailOf(Debt debt) {
        if (debt == null) {
            return Optional.empty();
        }
        if (debt.getDebtorType() == ParticipantType.SIMULATED) {
            if (debt.getDebtorSimulatedProfileId() == null) {
                return Optional.empty();
            }
            return simulatedProfileRepository.findById(debt.getDebtorSimulatedProfileId())
                    .map(SimulatedProfile::getEmail);
        }
        if (debt.getDebtorUserId() == null) {
            return Optional.empty();
        }
        return userRepository.findById(debt.getDebtorUserId()).map(User::getEmail);
    }

    /** Email of the payer of an expense (always a registered account). */
    public Optional<String> payerEmail(GroupExpense expense) {
        if (expense == null || expense.getPayerUserId() == null) {
            return Optional.empty();
        }
        return userRepository.findById(expense.getPayerUserId()).map(User::getEmail);
    }

    /** Emails of all active admins; empty when the group has none. */
    public List<String> activeAdminEmails(Long groupId) {
        List<Long> ids = groupMemberRepository.findActiveAdminUserIds(groupId);
        if (ids.isEmpty()) {
            return List.of();
        }
        return userRepository.findAllById(ids).stream()
                .map(User::getEmail)
                .filter(Objects::nonNull)
                .toList();
    }
}
