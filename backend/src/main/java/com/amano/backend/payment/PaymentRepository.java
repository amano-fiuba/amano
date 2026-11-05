package com.amano.backend.payment;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

/** Persistence of {@link Payment} (018 data-model.md). */
public interface PaymentRepository extends JpaRepository<Payment, Long> {

    /** The payment of a {@code (registered by, Idempotency-Key)} pair, if any (FR-008). */
    Optional<Payment> findByRegisteredByUserIdAndIdempotencyKey(Long registeredByUserId, UUID idempotencyKey);

    /** Payments of a debt in registration order ({@code ix_payments_debt}). */
    List<Payment> findByDebtIdOrderByCreatedAtAsc(Long debtId);
}
