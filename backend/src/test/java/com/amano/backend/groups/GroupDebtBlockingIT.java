package com.amano.backend.groups;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.support.TransactionTemplate;

import com.amano.backend.config.JwtService;
import com.amano.backend.groupexpense.Debt;
import com.amano.backend.groupexpense.DebtRepository;
import com.amano.backend.support.AbstractIntegrationIT;

import jakarta.servlet.http.Cookie;
import tools.jackson.databind.ObjectMapper;

/**
 * 017 T026 (US2, FR-019, SC-003), end to end: a group expense creates real
 * debts and, with {@link com.amano.backend.groups.port.LedgerDebtPort}, leaving
 * the group or removing a member or simulated profile with unsettled debts
 * answers 403 {@code PENDING_DEBTS} with the 010 messages and changes nothing.
 * Once the debts are PAID (applied with {@link Debt#applyPayment}, the domain
 * operation 018 will use) the same operations are allowed.
 *
 * <p>Group of every test: Ana (ADMIN, payer), Juan (MEMBER) and the simulated
 * profile Abuela; one expense of $ 90 among the three leaves Juan and Abuela
 * owing $ 30 each to Ana.
 */
class GroupDebtBlockingIT extends AbstractIntegrationIT {

    private static final String LEAVE_MESSAGE =
            "No podés abandonar el grupo: tenés saldos pendientes con otros miembros";
    private static final AtomicInteger SEQUENCE = new AtomicInteger();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private DebtRepository debtRepository;

    @Autowired
    private TransactionTemplate transactionTemplate;

    private int n;
    private long ana;
    private long juan;
    private Cookie anaSession;
    private Cookie juanSession;
    private long groupId;
    private long abuela;

    @BeforeEach
    void buildGroup() throws Exception {
        n = SEQUENCE.incrementAndGet();
        ana = user("Ana", "ana" + n + "@blocking.dev");
        juan = user("Juan", "juan" + n + "@blocking.dev");
        jdbc.update("""
                INSERT INTO friendship_requests (sender_id, recipient_id, status, responded_at)
                VALUES (?, ?, 'ACCEPTED', now())
                """, ana, juan);
        anaSession = new Cookie("amano_auth", jwtService.issueToken(ana));
        juanSession = new Cookie("amano_auth", jwtService.issueToken(juan));
        groupId = createGroupWithJuan("Depto");
        mockMvc.perform(post("/api/groups/" + groupId + "/members").cookie(anaSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"memberType\": \"SIMULATED\", \"identifier\": \"abuela" + n + "@blocking.dev\","
                        + " \"name\": \"Abuela\"}"))
                .andExpect(status().isCreated());
        abuela = jdbc.queryForObject("SELECT id FROM simulated_profiles WHERE group_id = ?", Long.class, groupId);
    }

    private long user(String first, String email) {
        return jdbc.queryForObject("""
                INSERT INTO users (first_name, email, password_hash, terms_accepted_at, email_verified_at)
                VALUES (?, ?, 'x', now(), now()) RETURNING id
                """, Long.class, first, email);
    }

    private long createGroupWithJuan(String name) throws Exception {
        String created = mockMvc.perform(post("/api/groups").cookie(anaSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\": \"" + name + "\", \"member\": {\"memberType\": \"REGISTERED\","
                        + " \"identifier\": \"juan" + n + "@blocking.dev\"}}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(created).get("id").asLong();
    }

    /** $ 90 paid by Ana among Ana, Juan and Abuela: Juan and Abuela owe $ 30 each. */
    private void registerExpense() throws Exception {
        mockMvc.perform(post("/api/groups/" + groupId + "/expenses").cookie(anaSession)
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"amount": "90.00", "currency": "ARS", "date": "2026-11-05", "category": "FOOD", "type": "ONE_TIME", "paymentMethod": "CASH", "payerUserId": %d,
                         "participants": [{"type": "REGISTERED", "userId": %d},
                                          {"type": "REGISTERED", "userId": %d},
                                          {"type": "SIMULATED", "simulatedProfileId": %d}]}
                        """.formatted(ana, ana, juan, abuela)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.debts.length()").value(2));
    }

    /** Pays {@code amount} of every debt of the group through the domain operation of 018. */
    private void payEveryDebt(String amount) {
        transactionTemplate.executeWithoutResult(status -> jdbc.queryForList(
                "SELECT id FROM debts WHERE group_id = ?", Long.class, groupId).forEach(id -> {
                    Debt debt = debtRepository.findById(id).orElseThrow();
                    debt.applyPayment(new BigDecimal(amount));
                    debtRepository.save(debt);
                }));
    }

    private ResultActions leave(Cookie session) throws Exception {
        return mockMvc.perform(post("/api/groups/" + groupId + "/leave").cookie(session));
    }

    private ResultActions removeMember(Cookie session, long userId) throws Exception {
        return mockMvc.perform(delete("/api/groups/" + groupId + "/members/" + userId).cookie(session));
    }

    private ResultActions removeAbuela() throws Exception {
        return mockMvc.perform(delete("/api/groups/" + groupId + "/simulated-profiles/" + abuela).cookie(anaSession));
    }

    private static void expectPendingDebts(ResultActions result, String message) throws Exception {
        result.andExpect(status().isForbidden())
                .andExpect(jsonPath("$.title").value("Operación bloqueada por deudas pendientes"))
                .andExpect(jsonPath("$.detail").value(message))
                .andExpect(jsonPath("$.properties.code").value("PENDING_DEBTS"));
    }

    private boolean isActiveMember(long userId) {
        return jdbc.queryForObject(
                "SELECT left_at IS NULL FROM group_members WHERE group_id = ? AND user_id = ?",
                Boolean.class, groupId, userId);
    }

    private int events(String type) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM group_activity WHERE group_id = ? AND event_type = ?",
                Integer.class, groupId, type);
    }

    private boolean abuelaExists() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM simulated_profiles WHERE id = ?", Integer.class, abuela) == 1;
    }

    @Test
    void theDebtorCannotLeaveTheGroup() throws Exception {
        registerExpense();

        expectPendingDebts(leave(juanSession), LEAVE_MESSAGE);

        assertThat(isActiveMember(juan)).isTrue();
        assertThat(events("MEMBER_LEFT")).isZero();
    }

    @Test
    void theDebtorCannotBeRemoved() throws Exception {
        registerExpense();

        expectPendingDebts(removeMember(anaSession, juan), PendingDebtsException.MESSAGE);

        assertThat(isActiveMember(juan)).isTrue();
        assertThat(events("MEMBER_REMOVED")).isZero();
    }

    @Test
    void aSimulatedProfileWithDebtsCannotBeRemoved() throws Exception {
        registerExpense();

        expectPendingDebts(removeAbuela(), PendingDebtsException.MESSAGE);

        assertThat(abuelaExists()).isTrue();
        assertThat(events("MEMBER_REMOVED")).isZero();
    }

    @Test
    void theCreditorCannotLeaveNorBeRemovedEither() throws Exception {
        registerExpense();
        // Juan becomes admin and takes the role away from Ana, so Ana is a plain MEMBER
        mockMvc.perform(post("/api/groups/" + groupId + "/members/" + juan + "/admin").cookie(anaSession))
                .andExpect(status().isOk());
        mockMvc.perform(delete("/api/groups/" + groupId + "/members/" + ana + "/admin").cookie(juanSession))
                .andExpect(status().isOk());

        expectPendingDebts(leave(anaSession), LEAVE_MESSAGE);
        expectPendingDebts(removeMember(juanSession, ana), PendingDebtsException.MESSAGE);

        assertThat(isActiveMember(ana)).isTrue();
    }

    @Test
    void aPartialPaymentStillBlocks() throws Exception {
        registerExpense();
        payEveryDebt("10.00");

        assertThat(jdbc.queryForList("SELECT DISTINCT status FROM debts WHERE group_id = ?", String.class, groupId))
                .containsExactly("PARTIALLY_PAID");
        expectPendingDebts(leave(juanSession), LEAVE_MESSAGE);
        expectPendingDebts(removeAbuela(), PendingDebtsException.MESSAGE);
    }

    @Test
    void onceEveryDebtIsPaidTheBajasAreAllowed() throws Exception {
        registerExpense();
        payEveryDebt("30.00");

        assertThat(jdbc.queryForList("SELECT DISTINCT status FROM debts WHERE group_id = ?", String.class, groupId))
                .containsExactly("PAID");
        removeAbuela().andExpect(status().isOk());
        leave(juanSession).andExpect(status().isOk());

        assertThat(abuelaExists()).isFalse();
        assertThat(isActiveMember(juan)).isFalse();
        // the paid debt of the deleted profile keeps its copied name (research.md D6)
        assertThat(jdbc.queryForList("SELECT debtor_name FROM debts WHERE group_id = ? AND debtor_type = 'SIMULATED'",
                String.class, groupId)).containsExactly("Abuela");
    }

    @Test
    void debtsInAnotherGroupDoNotBlockThisOne() throws Exception {
        registerExpense();
        long otherGroup = createGroupWithJuan("Viaje");

        mockMvc.perform(post("/api/groups/" + otherGroup + "/leave").cookie(juanSession))
                .andExpect(status().isOk());
        expectPendingDebts(leave(juanSession), LEAVE_MESSAGE);
    }

    @Test
    void withoutExpensesEverythingWorksAsIn010() throws Exception {
        removeAbuela().andExpect(status().isOk());
        leave(juanSession).andExpect(status().isOk());
    }
}
