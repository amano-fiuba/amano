package com.amano.backend.groups;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Persistence for {@code group_permissions}: exactly one configuration row per
 * group (UNIQUE group_id in V9).
 */
public interface GroupPermissionsRepository extends JpaRepository<GroupPermissions, Long> {

    Optional<GroupPermissions> findByGroupId(Long groupId);
}
