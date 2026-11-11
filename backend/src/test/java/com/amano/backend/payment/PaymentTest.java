package com.amano.backend.payment;

import static com.amano.backend.payment.PaymentFixtures.ABUELA;
import static com.amano.backend.payment.PaymentFixtures.ANA;
import static com.amano.backend.payment.PaymentFixtures.GROUP_ID;
import static com.amano.backend.payment.PaymentFixtures.JUAN;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.amano.backend.groupexpense.ParticipantType;
import com.amano.backend.user.Currency;

/**
 * 018 T008 (plan.md D3, data-model.md regla 3): a payment copies payer,
 * receiver, group and currency from its debt; the client never chooses them.
 */
class PaymentTest {

    private static final UUID KEY = UUID.fromString("5a1e0c3b-4d2f-4e6a-9b8c-7d6e5f4a3b21");

    @Test
    void aPaymentOfARegisteredDebtorCopiesTheDebt() {
        Payment payment = Payment.of(PaymentFixtures.juanDebt(), new BigDecimal("1000.00"),
                LocalDate.of(2026, 11, 5), ANA, "Ana López", KEY);

        assertThat(payment.getGroupId()).isEqualTo(GROUP_ID);
        assertThat(payment.getDebtId()).isEqualTo(31L);
        assertThat(payment.getPayerType()).isEqualTo(ParticipantType.REGISTERED);
        assertThat(payment.getPayerUserId()).isEqualTo(JUAN);
        assertThat(payment.getPayerSimulatedProfileId()).isNull();
        assertThat(payment.getPayerName()).isEqualTo("Juan Pérez");
        assertThat(payment.getReceiverUserId()).isEqualTo(ANA);
        assertThat(payment.getReceiverName()).isEqualTo("Ana López");
        assertThat(payment.getRegisteredByUserId()).isEqualTo(ANA);
        assertThat(payment.getAmount()).isEqualByComparingTo("1000.00");
        assertThat(payment.getCurrency()).isEqualTo(Currency.ARS);
        assertThat(payment.getPaymentDate()).isEqualTo(LocalDate.of(2026, 11, 5));
        assertThat(payment.getIdempotencyKey()).isEqualTo(KEY);
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.CONFIRMED);
        assertThat(payment.isLegacy()).isFalse();
        assertThat(payment.getDecidedByUserId()).isNull();
        assertThat(payment.getDecidedAt()).isNull();
        assertThat(payment.getId()).isNull();
        assertThat(payment.getCreatedAt()).isNull();
    }

    @Test
    void theLegacyFlagCanBeSetExplicitly() {
        Payment payment = Payment.of(PaymentFixtures.juanDebt(), new BigDecimal("1000.00"),
                LocalDate.of(2026, 11, 5), ANA, "Ana López", KEY, true);

        assertThat(payment.isLegacy()).isTrue();
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.CONFIRMED);
    }

    @Test
    void theStateCanBeReportedForThe022FullPaymentFlow() {
        Payment payment = Payment.of(PaymentFixtures.juanDebt(), new BigDecimal("3333.33"),
                LocalDate.of(2026, 11, 7), ANA, "Ana López", KEY, false, PaymentStatus.REPORTED);

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.REPORTED);
        assertThat(payment.isLegacy()).isFalse();
    }

    @Test
    void aPaymentOfASimulatedDebtorKeepsTheProfile() {
        Payment payment = Payment.of(PaymentFixtures.abuelaDebt(), new BigDecimal("3333.33"),
                LocalDate.of(2026, 11, 5), ANA, "Ana López", KEY);

        assertThat(payment.getPayerType()).isEqualTo(ParticipantType.SIMULATED);
        assertThat(payment.getPayerUserId()).isNull();
        assertThat(payment.getPayerSimulatedProfileId()).isEqualTo(ABUELA);
        assertThat(payment.getPayerName()).isEqualTo("Abuela");
    }

    @Test
    void aReportedPaymentCanBeConfirmedWithTheAdministratorAndInstant() {
        Payment payment = reported();
        OffsetDateTime at = OffsetDateTime.parse("2026-11-07T12:30:00-03:00");

        payment.confirmBy(ANA, at);

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.CONFIRMED);
        assertThat(payment.getDecidedByUserId()).isEqualTo(ANA);
        assertThat(payment.getDecidedAt()).isEqualTo(at);
    }

    @Test
    void aReportedPaymentCanBeRejectedWithTheAdministratorAndInstant() {
        Payment payment = reported();
        OffsetDateTime at = OffsetDateTime.parse("2026-11-07T12:30:00-03:00");

        payment.rejectBy(ANA, at);

        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.REJECTED);
        assertThat(payment.getDecidedByUserId()).isEqualTo(ANA);
        assertThat(payment.getDecidedAt()).isEqualTo(at);
    }

    /**
     * Rewritten for partial payments (022 T085): {@code legacy} is only an origin marker now, so a
     * REPORTED payment written by the inherited 018 flow is decided like any other; what still
     * blocks a decision is the status.
     */
    @Test
    void onlyReportedPaymentsAcceptADecisionWhateverTheirOrigin() {
        Payment confirmed = Payment.of(PaymentFixtures.juanDebt(), new BigDecimal("3333.33"),
                LocalDate.of(2026, 11, 7), ANA, "Ana López", KEY, false, PaymentStatus.CONFIRMED);
        Payment legacyConfirmed = Payment.of(PaymentFixtures.juanDebt(), new BigDecimal("1000.00"),
                LocalDate.of(2026, 11, 5), ANA, "Ana López", KEY, true, PaymentStatus.CONFIRMED);
        OffsetDateTime at = OffsetDateTime.parse("2026-11-07T12:30:00-03:00");

        assertThatThrownBy(() -> confirmed.confirmBy(ANA, at)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> legacyConfirmed.rejectBy(ANA, at)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> reported().confirmBy(null, at)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> reported().confirmBy(ANA, null)).isInstanceOf(IllegalArgumentException.class);

        Payment legacyReported = Payment.of(PaymentFixtures.juanDebt(), new BigDecimal("1000.00"),
                LocalDate.of(2026, 11, 5), ANA, "Ana López", KEY, true, PaymentStatus.REPORTED);
        legacyReported.confirmBy(ANA, at);
        assertThat(legacyReported.getStatus()).isEqualTo(PaymentStatus.CONFIRMED);
    }

    private static Payment reported() {
        return Payment.of(PaymentFixtures.juanDebt(), new BigDecimal("3333.33"),
                LocalDate.of(2026, 11, 7), ANA, "Ana López", KEY, false, PaymentStatus.REPORTED);
    }
}
