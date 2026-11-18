package com.amano.backend.income;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.EnumSet;

import org.junit.jupiter.api.Test;

/** Spanish labels and text folding of the income search (specs/033-income-management, FR-008). */
class IncomeSearchLabelsTest {

    @Test
    void everyCategoryHasItsSpanishLabel() {
        assertThat(IncomeSearchLabels.CATEGORY_LABELS.keySet())
                .containsExactlyInAnyOrderElementsOf(EnumSet.allOf(IncomeCategory.class));
        assertThat(IncomeSearchLabels.CATEGORY_LABELS)
                .containsEntry(IncomeCategory.SALARY, "Sueldo")
                .containsEntry(IncomeCategory.FREELANCE, "Freelance")
                .containsEntry(IncomeCategory.SALE, "Venta")
                .containsEntry(IncomeCategory.REFUND, "Reembolso")
                .containsEntry(IncomeCategory.OTHER, "Otros");
    }

    @Test
    void normalizeLowercasesAndStripsAccents() {
        assertThat(IncomeSearchLabels.normalize("CAFÉ Ñandú")).isEqualTo("cafe nandu");
        assertThat(IncomeSearchLabels.normalize("sueldo")).isEqualTo("sueldo");
    }

    @Test
    void matchesCategoriesByTheirNormalizedLabelFragment() {
        assertThat(IncomeSearchLabels.categoriesMatching("sue")).containsExactly(IncomeCategory.SALARY);
        assertThat(IncomeSearchLabels.categoriesMatching("reembol"))
                .containsExactly(IncomeCategory.REFUND);
        assertThat(IncomeSearchLabels.categoriesMatching("lance"))
                .containsExactly(IncomeCategory.FREELANCE);
        assertThat(IncomeSearchLabels.categoriesMatching("re"))
                .containsExactlyInAnyOrder(IncomeCategory.FREELANCE, IncomeCategory.REFUND);
        assertThat(IncomeSearchLabels.categoriesMatching("ven")).containsExactly(IncomeCategory.SALE);
    }

    @Test
    void aTermThatIsNotInAnyLabelMatchesNothing() {
        assertThat(IncomeSearchLabels.categoriesMatching("xyz")).isEmpty();
        // The payment method is not searched: "transferencia" is not a category label.
        assertThat(IncomeSearchLabels.categoriesMatching("transferencia")).isEmpty();
    }
}
