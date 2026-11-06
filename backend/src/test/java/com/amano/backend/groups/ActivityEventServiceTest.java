package com.amano.backend.groups;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import com.amano.backend.user.User;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * 014 (T003, T015): the write side of the history serializes the details with
 * Jackson (valid JSON with quotes and accents, null keys omitted) and exposes
 * the expense/payment hooks with their validations.
 */
class ActivityEventServiceTest {

    private static final Long GROUP_ID = 42L;
    private static final Long ACTOR_ID = 7L;

    private final ObjectMapper objectMapper = JsonMapper.builder().build();
    private GroupActivityRepository repository;
    private ActivityEventService service;

    @BeforeEach
    void setUp() {
        repository = Mockito.mock(GroupActivityRepository.class);
        when(repository.save(any(GroupActivity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        service = new ActivityEventService(repository, objectMapper);
    }

    @Test
    void lastActivityOfNoGroupsDoesNotQueryTheDatabase() {
        assertThat(service.lastActivityByGroup(List.of())).isEmpty();
        verify(repository, never()).findLastActivityByGroupIds(any());
    }

    private GroupActivity saved() {
        ArgumentCaptor<GroupActivity> captor = ArgumentCaptor.forClass(GroupActivity.class);
        verify(repository).save(captor.capture());
        return captor.getValue();
    }

    @Test
    void namesWithQuotesBackslashesAndAccentsProduceValidJson() {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("type", "SIMULATED");
        details.put("name", "Abuela \"Tita\" \\ Ñandú");

        service.record(GROUP_ID, GroupActivity.GroupActivityType.MEMBER_ADDED, ACTOR_ID, details);

        GroupActivity event = saved();
        assertThat(event.getGroupId()).isEqualTo(GROUP_ID);
        assertThat(event.getEventType()).isEqualTo("MEMBER_ADDED");
        assertThat(event.getActorUserId()).isEqualTo(ACTOR_ID);
        JsonNode json = objectMapper.readTree(event.getDetailsJson());
        assertThat(json.get("name").asString()).isEqualTo("Abuela \"Tita\" \\ Ñandú");
    }

    @Test
    void nullValuesAreOmittedAndEmptyOrNullDetailsAreAnEmptyObject() {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("type", "REGISTERED");
        details.put("name", null);
        details.put("fields", List.of("permissions"));

        service.record(GROUP_ID, GroupActivity.GroupActivityType.MEMBER_REMOVED, ACTOR_ID, details);
        assertThat(saved().getDetailsJson()).isEqualTo("{\"type\":\"REGISTERED\",\"fields\":[\"permissions\"]}");

        Mockito.reset(repository);
        service.record(GROUP_ID, GroupActivity.GroupActivityType.MEMBER_LEFT, ACTOR_ID, null);
        assertThat(saved().getDetailsJson()).isEqualTo("{}");
    }

    @Test
    void registeredAndSimulatedMemberDetailsKeepTheVisibleName() {
        User user = new User();
        org.springframework.test.util.ReflectionTestUtils.setField(user, "id", 15L);
        user.setFirstName(" Juan ");
        user.setLastName("Pérez");
        user.setEmail("juan@example.com");
        assertThat(ActivityEventService.registeredMemberDetails(user)).containsExactly(
                Map.entry("type", "REGISTERED"), Map.entry("userId", 15L),
                Map.entry("name", "Juan Pérez"), Map.entry("email", "juan@example.com"));

        user.setLastName(null);
        assertThat(ActivityEventService.displayName(user)).isEqualTo("Juan");
        user.setFirstName(null);
        assertThat(ActivityEventService.displayName(user)).isNull();
        assertThat(ActivityEventService.displayName(null)).isNull();

        SimulatedProfile profile = new SimulatedProfile();
        org.springframework.test.util.ReflectionTestUtils.setField(profile, "id", 4L);
        profile.setName("Abuela");
        profile.setEmail("abuela@example.com");
        assertThat(ActivityEventService.simulatedMemberDetails(profile)).containsExactly(
                Map.entry("type", "SIMULATED"), Map.entry("simulatedProfileId", 4L),
                Map.entry("name", "Abuela"), Map.entry("email", "abuela@example.com"));
    }

    @Test
    void expenseHookRecordsTheDataModelFormat() {
        service.recordExpenseCreated(GROUP_ID, ACTOR_ID,
                new ExpenseActivityDetails(10L, new BigDecimal("1500.00"), " ars ", "  Súper  "));

        GroupActivity event = saved();
        assertThat(event.getEventType()).isEqualTo("EXPENSE_CREATED");
        JsonNode json = objectMapper.readTree(event.getDetailsJson());
        assertThat(json.get("expenseId").asLong()).isEqualTo(10L);
        assertThat(json.get("amount").asString()).isEqualTo("1500.00");
        assertThat(json.get("currency").asString()).isEqualTo("ARS");
        assertThat(json.get("description").asString()).isEqualTo("Súper");
    }

    @Test
    void expenseHookOmitsABlankDescription() {
        service.recordExpenseCreated(GROUP_ID, ACTOR_ID,
                new ExpenseActivityDetails(null, new BigDecimal("1E+3"), "USD", "   "));

        assertThat(saved().getDetailsJson()).isEqualTo("{\"amount\":\"1000\",\"currency\":\"USD\"}");
    }

    @Test
    void paymentHookRecordsTheDataModelFormat() {
        service.recordPaymentMade(GROUP_ID, ACTOR_ID,
                new PaymentActivityDetails(3L, new BigDecimal("500"), "ARS", "Juan Pérez", null));

        GroupActivity event = saved();
        assertThat(event.getEventType()).isEqualTo("PAYMENT_MADE");
        JsonNode json = objectMapper.readTree(event.getDetailsJson());
        assertThat(json.get("paymentId").asLong()).isEqualTo(3L);
        assertThat(json.get("amount").asString()).isEqualTo("500");
        assertThat(json.get("fromName").asString()).isEqualTo("Juan Pérez");
        assertThat(json.has("toName")).isFalse();
    }

    // 018 T017 (plan.md D6): additive expense and debt links.
    @Test
    void paymentHookAddsTheExpenseAndDebtWhenPresent() {
        service.recordPaymentMade(GROUP_ID, ACTOR_ID,
                new PaymentActivityDetails(54L, new BigDecimal("1000.00"), "ARS", "Juan Pérez", "Ana López", 12L, 31L));

        JsonNode json = objectMapper.readTree(saved().getDetailsJson());
        assertThat(json.get("expenseId").asLong()).isEqualTo(12L);
        assertThat(json.get("debtId").asLong()).isEqualTo(31L);
        assertThat(json.get("toName").asString()).isEqualTo("Ana López");
    }

    @Test
    void paymentHookWithoutLinksKeepsThe014Format() {
        service.recordPaymentMade(GROUP_ID, ACTOR_ID,
                new PaymentActivityDetails(3L, new BigDecimal("500"), "ARS", "Juan", "Ana"));

        assertThat(saved().getDetailsJson()).isEqualTo(
                "{\"paymentId\":3,\"amount\":\"500\",\"currency\":\"ARS\",\"fromName\":\"Juan\",\"toName\":\"Ana\"}");
    }

    @Test
    void hooksRejectMissingDataWithoutSaving() {
        assertThatThrownBy(() -> service.recordExpenseCreated(GROUP_ID, ACTOR_ID, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.recordPaymentMade(GROUP_ID, ACTOR_ID, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.recordExpenseCreated(GROUP_ID, ACTOR_ID,
                new ExpenseActivityDetails(1L, null, "ARS", null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("El monto debe ser mayor a cero");
        assertThatThrownBy(() -> service.recordExpenseCreated(GROUP_ID, ACTOR_ID,
                new ExpenseActivityDetails(1L, BigDecimal.ZERO, "ARS", null)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.recordPaymentMade(GROUP_ID, ACTOR_ID,
                new PaymentActivityDetails(1L, new BigDecimal("-1"), "ARS", null, null)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.recordPaymentMade(GROUP_ID, ACTOR_ID,
                new PaymentActivityDetails(1L, BigDecimal.TEN, "  ", null, null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("La moneda es obligatoria");
        assertThatThrownBy(() -> service.recordExpenseCreated(GROUP_ID, ACTOR_ID,
                new ExpenseActivityDetails(1L, BigDecimal.TEN, null, null)))
                .isInstanceOf(IllegalArgumentException.class);

        verify(repository, never()).save(any(GroupActivity.class));
    }
}
