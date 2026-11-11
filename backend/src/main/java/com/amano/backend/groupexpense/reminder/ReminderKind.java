package com.amano.backend.groupexpense.reminder;

/**
 * Kind of reminder stored in {@code reminders} (025 data-model.md): the
 * reminder of a debt ({@code DEBT}) or the due notice to the payer of an
 * expense ({@code PAYER_NOTICE}).
 */
public enum ReminderKind {
    DEBT,
    PAYER_NOTICE
}
