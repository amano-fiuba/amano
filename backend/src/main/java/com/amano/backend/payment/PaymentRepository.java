package com.amano.backend.payment;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

/** Persistence of {@link Payment} (018 data-model.md). */
public interface PaymentRepository extends JpaRepository<Payment, Long> {

    /**
     * 022 US5: debt of a payment without locking, so the decision flow can
     * take the debt lock first (the same order used by registration) and then
     * reload the payment.
     */
    @Query("SELECT p.debtId FROM Payment p WHERE p.id = :id")
    Optional<Long> findDebtIdById(@Param("id") Long id);

    /** Payment with a pessimistic write lock, after the debt is locked. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Payment p WHERE p.id = :id")
    Optional<Payment> findByIdForUpdate(@Param("id") Long id);

    /** The payment of a {@code (registered by, Idempotency-Key)} pair, if any (FR-008). */
    Optional<Payment> findByRegisteredByUserIdAndIdempotencyKey(Long registeredByUserId, UUID idempotencyKey);

    /** Payments of a debt in registration order ({@code ix_payments_debt}). */
    List<Payment> findByDebtIdOrderByCreatedAtAsc(Long debtId);

    /**
     * The payment with a given key on a debt (022 D6): reusing the key returns
     * the existing result instead of inserting a duplicate.
     */
    Optional<Payment> findByDebtIdAndIdempotencyKey(Long debtId, UUID idempotencyKey);
}
