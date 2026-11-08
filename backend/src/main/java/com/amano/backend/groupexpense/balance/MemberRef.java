package com.amano.backend.groupexpense.balance;

import java.util.Objects;

import com.amano.backend.groupexpense.ParticipantType;

/**
 * Identity of a member in a balance (022 D4): a registered user or a simulated
 * profile. The type is part of the identity because both use numeric ids from
 * different tables; a simulated profile with id 7 is not user 7.
 */
public record MemberRef(ParticipantType type, Long id) implements Comparable<MemberRef> {

    public MemberRef {
        Objects.requireNonNull(type, "El tipo de miembro es obligatorio");
        Objects.requireNonNull(id, "El identificador del miembro es obligatorio");
        if (id <= 0) {
            throw new IllegalArgumentException("El identificador del miembro debe ser positivo");
        }
    }

    public static MemberRef registered(Long userId) {
        return new MemberRef(ParticipantType.REGISTERED, userId);
    }

    public static MemberRef simulated(Long simulatedProfileId) {
        return new MemberRef(ParticipantType.SIMULATED, simulatedProfileId);
    }

    public boolean isSimulated() {
        return type == ParticipantType.SIMULATED;
    }

    /** Stable order (REGISTERED before SIMULATED, then id) for reproducible results. */
    @Override
    public int compareTo(MemberRef other) {
        int byType = type.compareTo(other.type);
        return byType != 0 ? byType : id.compareTo(other.id);
    }
}
