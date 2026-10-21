package com.amano.backend.expense;

import java.util.Map;

import com.amano.backend.common.FieldValidationException;

/**
 * Unicode-aware description normalization (FR-012/FR-017, research.md D5).
 * Trims leading/trailing code points that are Unicode whitespace (combining
 * {@link Character#isWhitespace(int)} and {@link Character#isSpaceChar(int)}, so
 * NBSP U+00A0, U+2007 and U+202F are included), normalizes CRLF/CR to LF and keeps
 * inner tabs/newlines without collapsing them. Empty after normalization means
 * "absent" ({@code null}). The 200 limit counts code points after normalization.
 */
public final class ExpenseDescriptionNormalizer {

    public static final int MAX_CODE_POINTS = 200;
    public static final String LENGTH_MESSAGE = "La descripción no puede superar los 200 caracteres";

    private ExpenseDescriptionNormalizer() {
    }

    /** Normalizes the text; {@code null} or blank input becomes {@code null} (FR-012). */
    public static String normalize(String raw) {
        if (raw == null) {
            return null;
        }
        String text = raw.replace("\r\n", "\n").replace('\r', '\n');
        int start = 0;
        int end = text.length();
        while (start < end) {
            int codePoint = text.codePointAt(start);
            if (!isWhitespace(codePoint)) {
                break;
            }
            start += Character.charCount(codePoint);
        }
        while (end > start) {
            int codePoint = text.codePointBefore(end);
            if (!isWhitespace(codePoint)) {
                break;
            }
            end -= Character.charCount(codePoint);
        }
        return start == end ? null : text.substring(start, end);
    }

    /** Null when the normalized description fits the limit, the Spanish message otherwise. */
    public static String validationError(String raw) {
        String normalized = normalize(raw);
        if (normalized != null
                && normalized.codePointCount(0, normalized.length()) > MAX_CODE_POINTS) {
            return LENGTH_MESSAGE;
        }
        return null;
    }

    /** Returns the normalized description, throwing the field error when it exceeds the limit. */
    public static String checked(String raw) {
        String error = validationError(raw);
        if (error != null) {
            throw new FieldValidationException(Map.of("description", error));
        }
        return normalize(raw);
    }

    private static boolean isWhitespace(int codePoint) {
        return Character.isWhitespace(codePoint) || Character.isSpaceChar(codePoint);
    }
}
