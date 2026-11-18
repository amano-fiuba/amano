package com.amano.backend.groups.port;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.amano.backend.groupexpense.DebtRepository;

/**
 * Bug report 2026-11-05 ("PAID debts still block bajas"): the port answers
 * ONLY from the "open" queries of {@link DebtRepository} (status ≠ PAID, i.e.
 * PENDING or PARTIALLY_PAID) and asks the right one for each kind of
 * participant. The SQL side is proven by {@code LedgerDebtPortIT}.
 */
class LedgerDebtPortTest {

    private static final long GROUP_ID = 3L;

    private DebtRepository debtRepository;
    private LedgerDebtPort port;

    @BeforeEach
    void setUp() {
        debtRepository = mock(DebtRepository.class);
        port = new LedgerDebtPort(debtRepository);
    }

    @Test
    void aUserWhoseDebtsAreAllPaidIsNotBlocked() {
        when(debtRepository.existsOpenForUser(GROUP_ID, 15L)).thenReturn(false);

        assertThat(port.hasPendingDebtsForUser(GROUP_ID, 15L)).isFalse();
        verify(debtRepository).existsOpenForUser(GROUP_ID, 15L);
        verifyNoMoreInteractions(debtRepository);
    }

    @Test
    void aUserWithAnOpenDebtIsBlocked() {
        when(debtRepository.existsOpenForUser(GROUP_ID, 15L)).thenReturn(true);

        assertThat(port.hasPendingDebtsForUser(GROUP_ID, 15L)).isTrue();
    }

    @Test
    void aSimulatedProfileIsAskedWithItsOwnQuery() {
        when(debtRepository.existsOpenForSimulatedProfile(GROUP_ID, 4L)).thenReturn(false, true);

        assertThat(port.hasPendingDebtsForSimulatedProfile(GROUP_ID, 4L)).isFalse();
        assertThat(port.hasPendingDebtsForSimulatedProfile(GROUP_ID, 4L)).isTrue();
        verify(debtRepository, org.mockito.Mockito.times(2)).existsOpenForSimulatedProfile(GROUP_ID, 4L);
        verifyNoMoreInteractions(debtRepository);
    }
}
