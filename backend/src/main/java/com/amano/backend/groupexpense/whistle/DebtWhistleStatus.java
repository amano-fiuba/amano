package com.amano.backend.groupexpense.whistle;

/**
 * Lifecycle of a whistle attempt (030 data-model.md): {@code SENDING} while the
 * mail is being delivered, then {@code SENT} or {@code FAILED}. Only
 * {@code SENDING} and {@code SENT} count for the 24 h limit (FR-007/FR-008).
 */
public enum DebtWhistleStatus {
    SENDING,
    SENT,
    FAILED
}
