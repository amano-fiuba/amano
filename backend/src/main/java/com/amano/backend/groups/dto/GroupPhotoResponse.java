package com.amano.backend.groups.dto;

/**
 * Response of {@code POST /api/groups/{id}/photo} (US8,
 * contracts/api-contracts.md): the URL reference stored in groups.photo and
 * the update timestamp.
 */
public record GroupPhotoResponse(String photo, java.time.OffsetDateTime updatedAt) {
}
