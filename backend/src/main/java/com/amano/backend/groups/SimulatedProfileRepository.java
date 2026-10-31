package com.amano.backend.groups;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Persistence for {@code simulated_profiles}. Unique (group_id, email) is
 * backed by V8; a simulated profile is implicitly a member of its group and
 * its removal is a physical DELETE.
 */
public interface SimulatedProfileRepository extends JpaRepository<SimulatedProfile, Long> {

    boolean existsByGroupIdAndEmail(Long groupId, String email);

    Optional<SimulatedProfile> findByGroupIdAndEmail(Long groupId, String email);

    List<SimulatedProfile> findByGroupId(Long groupId);

    long countByGroupId(Long groupId);
}
