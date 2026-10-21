package com.amano.backend.user;

import com.amano.backend.common.FieldValidationException;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

/**
 * Avatar format/size gate (task T033; FR-010, FR-022, decision D6): the check
 * is content-based — the declared MIME part is never trusted by itself; the
 * real JPEG (FF D8 FF) or PNG (89 50 4E 47) magic bytes are inspected. Size is
 * limited to 2 MB (data-model CHECK octet_length(data) <= 2097152 backstop).
 * An absent or empty part means "no avatar": optional (FR-003), never blocks.
 */
@Component
public class AvatarValidator {

    public static final int MAX_BYTES = 2 * 1024 * 1024;
    public static final String FORMAT_MESSAGE = "El archivo debe ser una imagen JPG o PNG válida";
    public static final String SIZE_MESSAGE = "El archivo es demasiado grande (máximo 2 MB)";

    private static final byte[] JPEG_MAGIC = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
    private static final byte[] PNG_MAGIC = {(byte) 0x89, 0x50, 0x4E, 0x47};

    /**
     * Non-throwing check used inside the service's unified field-error pass:
     * null when the (optional) avatar part is acceptable, the Spanish message
     * otherwise (FR-010/FR-018). Oversize is NOT returned: per the contract
     * (auth-api.md) it must be 413 "Payload demasiado grande", so it throws
     * {@link MaxUploadSizeExceededException} — the very exception Tomcat raises
     * when the container limit trips in real deployments, mapped by the T026
     * handler to the contract's response.
     */
    public String validationError(MultipartFile avatar) {
        if (isEmpty(avatar)) {
            return null; // optional part (FR-003): without file there is nothing to check
        }
        if (avatar.getSize() > MAX_BYTES) {
            throw new MaxUploadSizeExceededException(MAX_BYTES);
        }
        byte[] bytes = getBytesSafe(avatar);
        if (startsWith(bytes, JPEG_MAGIC) || startsWith(bytes, PNG_MAGIC)) {
            return null;
        }
        return FORMAT_MESSAGE;
    }

    /** Throwing variant for standalone use (same rules). */
    public void validate(MultipartFile avatar) {
        String error = validationError(avatar);
        if (error != null) {
            throw new FieldValidationException(java.util.Map.of("avatar", error));
        }
    }

    /**
     * True MIME derived from the magic bytes (D6: stored content type must
     * correspond to the validated content), or null when the format is invalid.
     */
    public String realContentType(MultipartFile avatar) {
        if (isEmpty(avatar)) {
            return null;
        }
        byte[] bytes = getBytesSafe(avatar);
        if (startsWith(bytes, JPEG_MAGIC)) {
            return "image/jpeg";
        }
        if (startsWith(bytes, PNG_MAGIC)) {
            return "image/png";
        }
        return null;
    }

    /** MultipartFile.getBytes() reads the part's bytes; IO failures are fatal. */
    private static byte[] getBytesSafe(MultipartFile avatar) {
        try {
            return avatar.getBytes();
        }
        catch (java.io.IOException exception) {
            throw new java.io.UncheckedIOException(exception);
        }
    }

    static boolean isEmpty(MultipartFile avatar) {
        return avatar == null || avatar.isEmpty();
    }

    private static boolean startsWith(byte[] bytes, byte[] prefix) {
        if (bytes == null || bytes.length < prefix.length) {
            return false;
        }
        for (int index = 0; index < prefix.length; index++) {
            if (bytes[index] != prefix[index]) {
                return false;
            }
        }
        return true;
    }
}
