package com.amano.backend.groupexpense;

/**
 * Kind of participant of a group expense (017 data-model.md): a registered
 * member ({@code users}) or a simulated profile ({@code simulated_profiles}).
 * Same values as the {@code type} key of the 014 history details.
 */
public enum ParticipantType {
    REGISTERED,
    SIMULATED
}
