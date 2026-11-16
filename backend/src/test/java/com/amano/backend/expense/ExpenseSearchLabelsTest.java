package com.amano.backend.expense;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

import com.amano.backend.common.FieldValidationException;

/**
 * Pure unit tests of the 031 search helpers: normalization, label matching, the
 * LIKE escaping, the criteria validation and the mirror of the frontend catalog.
 */
class ExpenseSearchLabelsTest {

    private static ExpenseSearchService.Criteria validate(String term, String year) {
        return ExpenseSearchService.validate(new ExpenseSearchService.Query(term, year, null, null, null));
    }

    private static final Path FRONTEND_CATALOG =
            Path.of("..", "frontend", "src", "validation", "expenseCatalog.js");

    @Test
    void normalizeLowercasesAndRemovesAccents() {
        assertThat(ExpenseSearchLabels.normalize("CAFÉ Ñandú Ü")).isEqualTo("cafe nandu u");
    }

    @Test
    void categoriesMatchByPartialLabelIgnoringAccents() {
        assertThat(ExpenseSearchLabels.categoriesMatching("alim"))
                .containsExactly(ExpenseCategory.FOOD);
        assertThat(ExpenseSearchLabels.categoriesMatching("educacion"))
                .containsExactly(ExpenseCategory.EDUCATION);
        assertThat(ExpenseSearchLabels.categoriesMatching("zzz")).isEmpty();
    }

    @Test
    void paymentMethodsMatchByPartialLabel() {
        assertThat(ExpenseSearchLabels.paymentMethodsMatching("tarjeta"))
                .containsExactlyInAnyOrder(PaymentMethod.DEBIT_CARD, PaymentMethod.CREDIT_CARD);
        assertThat(ExpenseSearchLabels.paymentMethodsMatching("debito"))
                .containsExactly(PaymentMethod.DEBIT_CARD);
    }

    @Test
    void everyEnumConstantHasALabel() {
        assertThat(ExpenseSearchLabels.CATEGORY_LABELS).containsOnlyKeys(ExpenseCategory.values());
        assertThat(ExpenseSearchLabels.PAYMENT_METHOD_LABELS).containsOnlyKeys(PaymentMethod.values());
    }

    /** The backend mirror must say exactly what the UI shows (plan D3). */
    @Test
    void labelsMirrorTheFrontendCatalog() throws IOException {
        String catalog = Files.readString(FRONTEND_CATALOG);
        assertMirrors(catalog, "CATEGORY_OPTIONS", ExpenseSearchLabels.CATEGORY_LABELS);
        assertMirrors(catalog, "PAYMENT_METHOD_OPTIONS", ExpenseSearchLabels.PAYMENT_METHOD_LABELS);
    }

    private static void assertMirrors(String catalog, String constant, Map<? extends Enum<?>, String> labels) {
        Matcher block = Pattern.compile(constant + " = Object\\.freeze\\(\\[(.*?)\\]\\)", Pattern.DOTALL)
                .matcher(catalog);
        assertThat(block.find()).as(constant).isTrue();
        Matcher entry = Pattern.compile("code: '([A-Z_]+)', label: '([^']+)'").matcher(block.group(1));
        int count = 0;
        while (entry.find()) {
            String code = entry.group(1);
            String label = entry.group(2);
            assertThat(labels.entrySet())
                    .as(constant + " " + code)
                    .anyMatch(e -> e.getKey().name().equals(code) && e.getValue().equals(label));
            count++;
        }
        assertThat(count).isEqualTo(labels.size());
    }

    @Test
    void escapeLikeTreatsWildcardsLiterally() {
        assertThat(ExpenseSearchService.escapeLike("100%_a\\b")).isEqualTo("100\\%\\_a\\\\b");
    }

    @Test
    void plainHasTheSameLengthAsAccented() {
        assertThat(ExpenseSearchService.PLAIN).hasSameSizeAs(ExpenseSearchService.ACCENTED);
        assertThat(ExpenseSearchService.PLAIN).doesNotContain("á", "Ñ");
    }

    @Test
    void validateTrimsNormalizesAndAcceptsNoTerm() {
        assertThat(validate("  CAFÉ ", "2026"))
                .isEqualTo(new ExpenseSearchService.Criteria("cafe", 2026, null, 1, 20));
        assertThat(validate(null, null))
                .isEqualTo(new ExpenseSearchService.Criteria(null, null, null, 1, 20));
        assertThat(validate("   ", " "))
                .isEqualTo(new ExpenseSearchService.Criteria(null, null, null, 1, 20));
    }

    @Test
    void validateRejectsShortAndLongTerms() {
        assertThatThrownBy(() -> validate(" a ", null))
                .isInstanceOfSatisfying(FieldValidationException.class, e -> assertThat(e.getFields())
                        .containsEntry("q", ExpenseSearchService.TERM_TOO_SHORT));
        assertThatThrownBy(() -> validate("x".repeat(101), null))
                .isInstanceOfSatisfying(FieldValidationException.class, e -> assertThat(e.getFields())
                        .containsEntry("q", ExpenseSearchService.TERM_TOO_LONG));
        assertThat(validate("x".repeat(100), null).normalizedTerm()).hasSize(100);
    }

    @Test
    void validateRejectsInvalidYears() {
        for (String year : new String[] {"abc", "0", "10000", "-5"}) {
            assertThatThrownBy(() -> validate("ab", year))
                    .as(year)
                    .isInstanceOfSatisfying(FieldValidationException.class, e -> assertThat(e.getFields())
                            .containsEntry("year", ExpenseSearchService.YEAR_INVALID));
        }
    }
}
