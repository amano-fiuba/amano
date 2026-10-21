package com.amano.backend.expense;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import com.amano.backend.common.FieldValidationException;

/** Unicode trimming, CRLF normalization and the 200-code-point limit (FR-012/FR-017). */
class ExpenseDescriptionNormalizerTest {

    @Test
    void trimsUnicodeWhitespaceIncludingNonBreakingSpaces() {
        assertThat(ExpenseDescriptionNormalizer.normalize("\u00A0 hola \u202F")).isEqualTo("hola");
        assertThat(ExpenseDescriptionNormalizer.normalize("\u2007hola\u2007")).isEqualTo("hola");
        assertThat(ExpenseDescriptionNormalizer.normalize("  hola  ")).isEqualTo("hola");
    }

    @Test
    void normalizesCrLfAndCrToLf() {
        assertThat(ExpenseDescriptionNormalizer.normalize("a\r\nb\rc")).isEqualTo("a\nb\nc");
    }

    @Test
    void keepsInnerTabsAndNewlinesWithoutCollapsing() {
        assertThat(ExpenseDescriptionNormalizer.normalize("  a\tb\nc   d  "))
                .isEqualTo("a\tb\nc   d");
    }

    @Test
    void emptyOrWhitespaceOnlyBecomesNull() {
        assertThat(ExpenseDescriptionNormalizer.normalize(null)).isNull();
        assertThat(ExpenseDescriptionNormalizer.normalize("")).isNull();
        assertThat(ExpenseDescriptionNormalizer.normalize(" \t\n\u00A0\u202F ")).isNull();
        assertThat(ExpenseDescriptionNormalizer.normalize("\r\n")).isNull();
    }

    @Test
    void accepts199And200CodePointsAndRejects201() {
        assertThat(ExpenseDescriptionNormalizer.validationError("x".repeat(199))).isNull();
        assertThat(ExpenseDescriptionNormalizer.validationError("x".repeat(200))).isNull();
        assertThat(ExpenseDescriptionNormalizer.validationError("x".repeat(201)))
                .isEqualTo(ExpenseDescriptionNormalizer.LENGTH_MESSAGE);
    }

    @Test
    void countsCodePointsNotUtf16Units() {
        String emoji = "\uD83D\uDE00"; // one code point, two UTF-16 units
        assertThat(ExpenseDescriptionNormalizer.validationError(emoji.repeat(200))).isNull();
        assertThat(ExpenseDescriptionNormalizer.validationError(emoji.repeat(201)))
                .isEqualTo(ExpenseDescriptionNormalizer.LENGTH_MESSAGE);
    }

    @Test
    void checkedReturnsTheNormalizedValueOrThrows() {
        assertThat(ExpenseDescriptionNormalizer.checked("  hola  ")).isEqualTo("hola");
        assertThatThrownBy(() -> ExpenseDescriptionNormalizer.checked("x".repeat(201)))
                .isInstanceOf(FieldValidationException.class)
                .satisfies(exception -> assertThat(
                        ((FieldValidationException) exception).getFields())
                        .containsEntry("description", ExpenseDescriptionNormalizer.LENGTH_MESSAGE));
    }
}
