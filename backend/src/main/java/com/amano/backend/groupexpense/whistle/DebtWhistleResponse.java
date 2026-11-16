package com.amano.backend.groupexpense.whistle;

import java.time.OffsetDateTime;

/**
 * 201 body of {@code POST /api/groups/{groupId}/debts/{debtId}/whistle}
 * (030 contracts/debt-whistle.md). It never carries the debtor's email
 * (FR-016). {@code nextAvailableAt} is when the same pair may whistle again.
 */
public record DebtWhistleResponse(
        Long whistleId,
        Long debtId,
        String debtorName,
        OffsetDateTime sentAt,
        OffsetDateTime nextAvailableAt) {
}
