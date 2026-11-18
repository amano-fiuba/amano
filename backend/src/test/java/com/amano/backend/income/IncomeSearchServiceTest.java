package com.amano.backend.income;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import com.amano.backend.common.FieldValidationException;
import com.amano.backend.user.Currency;

/** Criteria validation and paging of the income search (specs/033-income-management, FR-008). */
class IncomeSearchServiceTest {

    private static final Long USER_ID = 5L;

    private IncomeRepository repository;
    private IncomeService incomeService;
    private IncomeSearchService service;

    @BeforeEach
    void setUp() {
        repository = mock(IncomeRepository.class);
        incomeService = mock(IncomeService.class);
        service = new IncomeSearchService(repository, incomeService);
        when(incomeService.withConversions(anyLong(), any())).thenReturn(List.of());
    }

    private static IncomeSearchService.Query query(String term, String year, String month,
            String from, String to, String page, String size) {
        return new IncomeSearchService.Query(term, year, month, from, to, page, size);
    }

    private static IncomeSearchService.Query none() {
        return query(null, null, null, null, null, null, null);
    }

    private static FieldValidationException invalid(IncomeSearchService.Query query) {
        try {
            IncomeSearchService.validate(query);
        }
        catch (FieldValidationException expected) {
            return expected;
        }
        throw new AssertionError("expected a validation error");
    }

    private static Income income(String amount) {
        Income income = new Income();
        income.setUserId(USER_ID);
        income.setAmount(new BigDecimal(amount));
        income.setCurrency(Currency.ARS);
        income.setIncomeDate(LocalDate.of(2026, 3, 10));
        income.setCategory(IncomeCategory.SALARY);
        income.setPaymentMethod(IncomePaymentMethod.CASH);
        return income;
    }

    @Test
    void withoutCriteriaUsesTheDefaults() {
        IncomeSearchService.Criteria criteria = IncomeSearchService.validate(none());

        assertThat(criteria.normalizedTerm()).isNull();
        assertThat(criteria.page()).isEqualTo(1);
        assertThat(criteria.size()).isEqualTo(20);
        assertThat(criteria.period().start()).isEqualTo(LocalDate.of(1, 1, 1));
    }

    @Test
    void trimsAndNormalizesTheTermAndBlankMeansNoTerm() {
        assertThat(IncomeSearchService.validate(
                query("  CAFÉ  ", null, null, null, null, null, null)).normalizedTerm())
                .isEqualTo("cafe");
        assertThat(IncomeSearchService.validate(
                query("   ", null, null, null, null, null, null)).normalizedTerm()).isNull();
    }

    @Test
    void rejectsATermOfOneOrMoreThanOneHundredCharacters() {
        assertThat(invalid(query("a", null, null, null, null, null, null)).getFields())
                .containsEntry("q", "Ingresá al menos 2 caracteres para buscar");
        assertThat(invalid(query("a".repeat(101), null, null, null, null, null, null)).getFields())
                .containsEntry("q", "La búsqueda admite hasta 100 caracteres");
        IncomeSearchService.validate(query("a".repeat(100), null, null, null, null, null, null));
    }

    @Test
    void rejectsAMonthWithoutYearAndAnInvertedRangeAndKeepsEveryError() {
        FieldValidationException error = invalid(
                query("x", null, "5", "2026-03-10", "2026-03-01", "0", "51"));

        assertThat(error.getFields()).containsOnlyKeys("q", "month", "from", "page", "size");
    }

    @Test
    void rejectsAnInvalidYearPageAndSize() {
        assertThat(invalid(query(null, "99999", null, null, null, null, null)).getFields())
                .containsEntry("year", "El año no es válido");
        assertThat(invalid(query(null, null, null, null, null, "abc", null)).getFields())
                .containsEntry("page", "La página no es válida");
        assertThat(invalid(query(null, null, null, null, null, null, "0")).getFields())
                .containsEntry("size", "El tamaño de página no es válido");
        assertThat(IncomeSearchService.validate(
                query(null, null, null, null, null, "3", "50")).size()).isEqualTo(50);
    }

    @Test
    void returnsTheRequestedPageWithItsTotals() {
        Pageable asked = PageRequest.of(1, 5);
        when(repository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(income("1.00")), asked, 11));

        IncomeSearchResponse response = service.search(USER_ID,
                query(null, null, null, null, null, "2", "5"));

        assertThat(response.page()).isEqualTo(2);
        assertThat(response.size()).isEqualTo(5);
        assertThat(response.totalElements()).isEqualTo(11);
        assertThat(response.totalPages()).isEqualTo(3);
        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(repository).findAll(any(Specification.class), pageable.capture());
        assertThat(pageable.getValue().getPageNumber()).isEqualTo(1);
        assertThat(pageable.getValue().getSort().toString())
                .isEqualTo("incomeDate: DESC,id: DESC");
    }

    @Test
    void aPagePastTheLastOneAnswersTheLastOne() {
        Page<Income> empty = new PageImpl<>(List.of(), PageRequest.of(9, 5), 7);
        Page<Income> last = new PageImpl<>(List.of(income("2.00")), PageRequest.of(1, 5), 7);
        when(repository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(empty, last);

        IncomeSearchResponse response = service.search(USER_ID,
                query(null, null, null, null, null, "10", "5"));

        assertThat(response.page()).isEqualTo(2);
        verify(repository, times(2)).findAll(any(Specification.class), any(Pageable.class));
    }

    @Test
    void anEmptyResultIsPageOneWithZeroTotals() {
        when(repository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        IncomeSearchResponse response = service.search(USER_ID, none());

        assertThat(response.incomes()).isEmpty();
        assertThat(response.page()).isEqualTo(1);
        assertThat(response.totalElements()).isZero();
        assertThat(response.totalPages()).isZero();
    }

    @Test
    void anInvalidQueryNeverReachesTheRepository() {
        assertThatThrownBy(() -> service.search(USER_ID,
                query("a", null, null, null, null, null, null)))
                .isInstanceOf(FieldValidationException.class);
        verify(repository, times(0)).findAll(any(Specification.class), any(Pageable.class));
    }

    @Test
    void listsTheYearsOfTheUser() {
        when(repository.findIncomeYears(USER_ID)).thenReturn(List.of(2026, 2024));

        assertThat(service.years(USER_ID)).containsExactly(2026, 2024);
    }
}
