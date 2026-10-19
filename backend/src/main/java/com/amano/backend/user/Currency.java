package com.amano.backend.user;

/**
 * Currencies a user can prefer (Q1: multi-select, subset of ARS/USD/EUR).
 * 0 persisted rows means ARS by default at read time (FR-011).
 */
public enum Currency {
    ARS,
    USD,
    EUR
}
