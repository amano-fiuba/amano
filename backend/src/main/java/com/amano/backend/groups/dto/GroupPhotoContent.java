package com.amano.backend.groups.dto;

/**
 * Served bytes of GET /api/groups/{id}/photo (US8): the TRUE content type
 * derived from the magic bytes and the binary payload.
 */
public record GroupPhotoContent(String contentType, byte[] data) {
}
