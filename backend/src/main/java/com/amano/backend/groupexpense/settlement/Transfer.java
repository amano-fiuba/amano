package com.amano.backend.groupexpense.settlement;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

import com.amano.backend.groupexpense.balance.MemberRef;

/**
 * One transfer of a settlement proposal (022 D5): {@code from} pays
 * {@code amount} to {@code to}. It is a value object only; it never changes any
 * debt or expense.
 *
 * @param from   member that owes (negative net balance)
 * @param to     member that is owed (positive net balance)
 * @param amount positive amount with scale 2, expressed in the currency of the
 *               balance map
 */
public record Transfer(MemberRef from, MemberRef to, BigDecimal amount) {

    public Transfer {
        Objects.requireNonNull(from, "El miembro que paga es obligatorio");
        Objects.requireNonNull(to, "El miembro que cobra es obligatorio");
        Objects.requireNonNull(amount, "El monto de la transferencia es obligatorio");
        if (from.equals(to)) {
            throw new IllegalArgumentException("Una transferencia no puede tener el mismo origen y destino");
        }
        amount = amount.setScale(2, RoundingMode.HALF_UP);
        if (amount.signum() <= 0) {
            throw new IllegalArgumentException("El monto de la transferencia debe ser mayor que cero");
        }
    }
}
