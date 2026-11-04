package com.amano.backend.groups;

import org.springframework.data.jpa.repository.JpaRepository;

/** Persistence for {@code group_photos} (V11); the row exists only with a custom photo. */
public interface GroupPhotoRepository extends JpaRepository<GroupPhoto, Long> {
}
