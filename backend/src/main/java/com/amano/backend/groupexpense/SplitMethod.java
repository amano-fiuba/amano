package com.amano.backend.groupexpense;

/**
 * 022 D2/T066: how a group expense is split among its participants.
 * {@code EQUAL} is the 017 behavior and the default for existing and omitted
 * values; {@code CUSTOM} and {@code PERCENTAGE} are additive methods of 022.
 */
public enum SplitMethod {

    EQUAL,
    CUSTOM,
    PERCENTAGE
}
