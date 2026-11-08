package com.amano.backend.groupexpense;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import com.amano.backend.config.JwtService;
import com.amano.backend.friendship.DirectDebtPort;
import com.amano.backend.groupexpense.exchange.BcraExchangeRateClient;
import com.amano.backend.groupexpense.overdue.OverdueDebtJob;

import jakarta.servlet.http.Cookie;
import tools.jackson.databind.ObjectMapper;

/**
 * 027 US4 (T063–T066, T091; contracts/shared-expenses-api.md): expenses shared with
 * a friend, their direct debt and its isolation from every group flow. Ana and Juan
 * are friends and also share a group; Otra is a stranger.
 */
@SpringBootTest(properties = {
        "jwt.secret=2b7e151628aed2a6abf7158809cf4f3c2b7e151628aed2a6abf7158809cf4f3c"
})
@AutoConfigureMockMvc
@Testcontainers
class FriendExpenseControllerIT {

    private static final AtomicInteger SEQUENCE = new AtomicInteger();

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18-alpine");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private DirectDebtPort directDebtPort;

    @Autowired
    private OverdueDebtJob overdueDebtJob;

    /** A friend expense never converts: any call to the BCRA is a failure. */
    @MockitoBean
    private BcraExchangeRateClient bcraExchangeRateClient;

    private long ana;
    private long juan;
    private long otra;
    private Cookie anaSession;
    private Cookie juanSession;
    private Cookie otraSession;
    private long groupId;

    @BeforeEach
    void setUp() throws Exception {
        reset(bcraExchangeRateClient);
        int n = SEQUENCE.incrementAndGet();
        ana = user("Ana", "López", "ana" + n + "@friends.dev");
        juan = user("Juan", "Pérez", "juan" + n + "@friends.dev");
        otra = user("Otra", "Persona", "otra" + n + "@friends.dev");
        jdbc.update("""
                INSERT INTO friendship_requests (sender_id, recipient_id, status, responded_at)
                VALUES (?, ?, 'ACCEPTED', now())
                """, ana, juan);
        anaSession = new Cookie("amano_auth", jwtService.issueToken(ana));
        juanSession = new Cookie("amano_auth", jwtService.issueToken(juan));
        otraSession = new Cookie("amano_auth", jwtService.issueToken(otra));
        String created = mockMvc.perform(post("/api/groups").cookie(anaSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\": \"Depto\", \"member\": {\"memberType\": \"REGISTERED\","
                        + " \"identifier\": \"juan" + n + "@friends.dev\"}}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        groupId = objectMapper.readTree(created).get("id").asLong();
    }

    private long user(String first, String last, String email) {
        return jdbc.queryForObject("""
                INSERT INTO users (first_name, last_name, email, password_hash, terms_accepted_at, email_verified_at)
                VALUES (?, ?, ?, 'x', now(), now()) RETURNING id
                """, Long.class, first, last, email);
    }

    private String body(String amount, String currency, long payer, String participants) {
        return """
                {"amount": "%s", "currency": "%s", "date": "2026-11-05", "description": "Cena",
                 "deviceTimeZone": "America/Argentina/Buenos_Aires", "category": "FOOD", "type": "ONE_TIME",
                 "paymentMethod": "DEBIT_CARD", "payerUserId": %d, "splitMethod": "EQUAL",
                 "participants": %s}
                """.formatted(amount, currency, payer, participants);
    }

    private String pair() {
        return "[{\"type\": \"REGISTERED\", \"userId\": %d}, {\"type\": \"REGISTERED\", \"userId\": %d}]"
                .formatted(ana, juan);
    }

    private ResultActions postFriendExpense(Cookie session, long friendId, String key, String body) throws Exception {
        var request = post("/api/users/me/friends/" + friendId + "/expenses")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body);
        if (session != null) {
            request.cookie(session);
        }
        if (key != null) {
            request.header("Idempotency-Key", key);
        }
        return mockMvc.perform(request);
    }

    private long createDinner() throws Exception {
        String response = postFriendExpense(anaSession, juan, UUID.randomUUID().toString(),
                body("10000.00", "ARS", ana, pair()))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("id").asLong();
    }

    // ---- T063: creation ----

    @Test
    void withoutSessionIsUnauthorized() throws Exception {
        postFriendExpense(null, juan, UUID.randomUUID().toString(), body("10.00", "ARS", ana, pair()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void createsTheExpenseWithoutGroupAndWithoutConversion() throws Exception {
        String key = UUID.randomUUID().toString();
        String response = postFriendExpense(anaSession, juan, key, body("10000.00", "BRL", ana, pair()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.groupId").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.friend.userId").value(juan))
                .andExpect(jsonPath("$.friend.name").value("Juan Pérez"))
                .andExpect(jsonPath("$.currency").value("BRL"))
                .andExpect(jsonPath("$.rateSource").value("SAME_CURRENCY"))
                .andExpect(jsonPath("$.exchangeRate").value("1"))
                .andExpect(jsonPath("$.convertedAmount").value("10000.00"))
                .andExpect(jsonPath("$.category").value("FOOD"))
                .andExpect(jsonPath("$.paymentMethod").value("DEBIT_CARD"))
                .andExpect(jsonPath("$.shares.length()").value(2))
                .andExpect(jsonPath("$.debts[0].amount").value("5000.00"))
                .andExpect(jsonPath("$.debts[0].currency").value("BRL"))
                .andExpect(jsonPath("$.debts[0].canRegisterPayment").value(false))
                .andReturn().getResponse().getContentAsString();
        long id = objectMapper.readTree(response).get("id").asLong();
        verify(bcraExchangeRateClient, never()).getQuote(any(), any(), any());

        mockMvc.perform(get("/api/users/me/friends/" + juan + "/expenses/" + id).cookie(anaSession))
                .andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT friend_user_id FROM group_expenses WHERE id = ?", Long.class, id))
                .isEqualTo(juan);
    }

    @Test
    void theLocationPointsToTheFriendExpense() throws Exception {
        String key = UUID.randomUUID().toString();
        String response = postFriendExpense(anaSession, juan, key, body("10.00", "ARS", ana, pair()))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long id = objectMapper.readTree(response).get("id").asLong();

        postFriendExpense(anaSession, juan, key, body("10.00", "ARS", ana, pair()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id));
        postFriendExpense(anaSession, juan, key, body("99.00", "ARS", ana, pair()))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.properties.code").value("IDEMPOTENCY_KEY_REUSED"));
        postFriendExpense(anaSession, juan, UUID.randomUUID().toString(), body("10.00", "ARS", ana, pair()))
                .andExpect(header().string("Location",
                        org.hamcrest.Matchers.startsWith("/api/users/me/friends/" + juan + "/expenses/")));
    }

    @Test
    void theFriendCanBeThePayer() throws Exception {
        postFriendExpense(anaSession, juan, UUID.randomUUID().toString(), body("100.00", "ARS", juan, pair()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.payer.userId").value(juan))
                .andExpect(jsonPath("$.debts[0].debtor.userId").value(ana))
                .andExpect(jsonPath("$.debts[0].creditor.userId").value(juan));
    }

    @Test
    void aStrangerOrAMissingUserIsANeutral404() throws Exception {
        postFriendExpense(anaSession, otra, UUID.randomUUID().toString(), body("10.00", "ARS", ana,
                "[{\"type\": \"REGISTERED\", \"userId\": %d}, {\"type\": \"REGISTERED\", \"userId\": %d}]"
                        .formatted(ana, otra)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("No sos amigo de ese usuario"));
        postFriendExpense(anaSession, 999_999L, UUID.randomUUID().toString(), body("10.00", "ARS", ana, pair()))
                .andExpect(status().isNotFound());
        postFriendExpense(anaSession, ana, UUID.randomUUID().toString(), body("10.00", "ARS", ana, pair()))
                .andExpect(status().isNotFound());
    }

    @Test
    void payerAndParticipantsMustBeTheTwoFriends() throws Exception {
        postFriendExpense(anaSession, juan, UUID.randomUUID().toString(), body("10.00", "ARS", otra, pair()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.properties.fields.payerUserId")
                        .value(GroupExpenseService.FRIEND_PAYER_INVALID));
        postFriendExpense(anaSession, juan, UUID.randomUUID().toString(), body("10.00", "ARS", ana,
                "[{\"type\": \"REGISTERED\", \"userId\": %d}]".formatted(ana)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.properties.fields.participants")
                        .value(GroupExpenseService.FRIEND_PARTICIPANTS_INVALID));
        postFriendExpense(anaSession, juan, UUID.randomUUID().toString(), body("10.00", "ARS", ana,
                "[{\"type\": \"REGISTERED\", \"userId\": %d}, {\"type\": \"SIMULATED\", \"simulatedProfileId\": 1}]"
                        .formatted(ana)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.properties.fields.participants")
                        .value(GroupExpenseService.FRIEND_PARTICIPANTS_INVALID));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM group_expenses WHERE friend_user_id = ?",
                Integer.class, juan)).isZero();
    }

    @Test
    void theClassificationIsRequired() throws Exception {
        String withoutClassification = body("10.00", "ARS", ana, pair())
                .replace("\"category\": \"FOOD\", \"type\": \"ONE_TIME\",", "")
                .replace("\"paymentMethod\": \"DEBIT_CARD\", ", "");
        postFriendExpense(anaSession, juan, UUID.randomUUID().toString(), withoutClassification)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.properties.fields.category").value("La categoría es obligatoria"))
                .andExpect(jsonPath("$.properties.fields.type").value("El tipo de gasto es obligatorio"))
                .andExpect(jsonPath("$.properties.fields.paymentMethod").value("El medio de pago es obligatorio"));
    }

    // ---- T064: direct debt ----

    @Test
    void createsOnePendingDirectDebtThatBlocksDeletingTheFriendship() throws Exception {
        long id = createDinner();

        Map<String, Object> debt = jdbc.queryForMap("SELECT * FROM debts WHERE expense_id = ?", id);
        assertThat(debt.get("group_id")).isNull();
        assertThat(debt.get("status")).isEqualTo("PENDING");
        assertThat(((Number) debt.get("debtor_user_id")).longValue()).isEqualTo(juan);
        assertThat(((Number) debt.get("creditor_user_id")).longValue()).isEqualTo(ana);
        assertThat(debt.get("amount").toString()).isEqualTo("5000.00");

        assertThat(directDebtPort.hasPendingDirectDebt(ana, juan)).isTrue();
        assertThat(directDebtPort.hasPendingDirectDebt(juan, ana)).isTrue();
        assertThat(directDebtPort.hasPendingDirectDebt(ana, otra)).isFalse();

        mockMvc.perform(get("/api/users/me/friends/" + juan + "/deletion-eligibility").cookie(anaSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.canDelete").value(false));
        mockMvc.perform(delete("/api/users/me/friends/" + juan).cookie(anaSession))
                .andExpect(status().isForbidden());
    }

    @Test
    void aSettledDirectDebtNoLongerBlocks() throws Exception {
        long id = createDinner();
        jdbc.update("UPDATE debts SET status = 'PAID', paid_amount = amount WHERE expense_id = ?", id);

        assertThat(directDebtPort.hasPendingDirectDebt(ana, juan)).isFalse();
    }

    @Test
    void directDebtsStayOutOfEveryGroupFlow() throws Exception {
        String balancesBefore = mockMvc.perform(get("/api/groups/" + groupId + "/balances").cookie(anaSession))
                .andReturn().getResponse().getContentAsString();
        long id = createDinner();
        long debtId = jdbc.queryForObject("SELECT id FROM debts WHERE expense_id = ?", Long.class, id);

        mockMvc.perform(get("/api/groups/" + groupId + "/balances").cookie(anaSession))
                .andExpect(status().isOk())
                .andExpect(result -> assertThat(result.getResponse().getContentAsString()).isEqualTo(balancesBefore));
        mockMvc.perform(get("/api/groups/" + groupId + "/debts/pending").cookie(juanSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));

        // The overdue job only looks at groups: the direct debt stays PENDING.
        jdbc.update("UPDATE groups SET debt_due_days = 0 WHERE id = ?", groupId);
        overdueDebtJob.markOverdueDebts();
        assertThat(jdbc.queryForObject("SELECT status FROM debts WHERE id = ?", String.class, debtId))
                .isEqualTo("PENDING");

        // T091: paying it through the group routes is refused and changes nothing.
        mockMvc.perform(post("/api/groups/" + groupId + "/debts/" + debtId + "/payments").cookie(juanSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .content("{\"amount\": \"5000.00\"}"))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/payments").cookie(juanSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .content("{\"debtId\": %d, \"amount\": \"5000.00\"}".formatted(debtId)))
                .andExpect(status().isNotFound());
        assertThat(jdbc.queryForObject("SELECT paid_amount FROM debts WHERE id = ?", String.class, debtId))
                .isEqualTo("0.00");
    }

    @Test
    void aFriendExpenseKeyReusedInAGroupIsAConflictNotAnError() throws Exception {
        String key = UUID.randomUUID().toString();
        postFriendExpense(anaSession, juan, key, body("10.00", "ARS", ana, pair())).andExpect(status().isCreated());

        mockMvc.perform(post("/api/groups/" + groupId + "/expenses").cookie(anaSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", key)
                        .content(body("10.00", "ARS", ana, pair())))
                .andExpect(status().isUnprocessableContent());
    }

    // ---- T065: reading ----

    @Test
    void onlyTheTwoFriendsCanReadIt() throws Exception {
        long id = createDinner();

        mockMvc.perform(get("/api/users/me/friends/" + ana + "/expenses/" + id).cookie(juanSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.friend.userId").value(juan));
        mockMvc.perform(get("/api/users/me/friends/" + ana + "/expenses/" + id).cookie(otraSession))
                .andExpect(status().isNotFound());

        String groupExpense = mockMvc.perform(post("/api/groups/" + groupId + "/expenses").cookie(anaSession)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .content(body("10.00", "ARS", ana, pair())))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long groupExpenseId = objectMapper.readTree(groupExpense).get("id").asLong();
        mockMvc.perform(get("/api/users/me/friends/" + juan + "/expenses/" + groupExpenseId).cookie(anaSession))
                .andExpect(status().isNotFound());
    }
}
