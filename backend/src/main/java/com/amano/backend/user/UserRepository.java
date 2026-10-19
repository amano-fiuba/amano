package com.amano.backend.user;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Persistence for {@code users}. The unique index {@code uq_users_email} (FR-005)
 * remains the race-proof backstop; service code resolves conflicts via
 * {@code DataIntegrityViolationException}.
 */
public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);
}
