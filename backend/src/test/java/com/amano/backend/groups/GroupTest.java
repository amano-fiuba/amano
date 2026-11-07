package com.amano.backend.groups;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.amano.backend.user.Currency;

/** 022 T005/T006: group currency defaults to ARS until the create form exposes it, and the due days are optional. */
class GroupTest {

    @Test
    void aNewGroupDefaultsToArsAndNoDeadline() {
        Group group = new Group();

        assertThat(group.getPrimaryCurrency()).isEqualTo(Currency.ARS);
        assertThat(group.getDebtDueDays()).isNull();
    }

    @Test
    void currencyAndDeadlineCanBeChanged() {
        Group group = new Group();
        group.setPrimaryCurrency(Currency.USD);
        group.setDebtDueDays(30);

        assertThat(group.getPrimaryCurrency()).isEqualTo(Currency.USD);
        assertThat(group.getDebtDueDays()).isEqualTo(30);
    }
}
