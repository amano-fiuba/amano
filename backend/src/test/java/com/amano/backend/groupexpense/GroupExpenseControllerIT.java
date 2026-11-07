package com.amano.backend.groupexpense;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDate;
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
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.util.AopTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import com.amano.backend.config.JwtService;
import com.amano.backend.groupexpense.exchange.BcraExchangeRateClient;
import com.amano.backend.groupexpense.exchange.CachingExchangeRateProvider;
import com.amano.backend.groupexpense.exchange.ExchangeRateProvider;
import com.amano.backend.groupexpense.exchange.ExchangeRateQuote;
import com.amano.backend.groupexpense.exchange.ExchangeRateUnavailableException;
import com.amano.backend.groups.ActivityEventService;
import com.amano.backend.groups.ExpenseActivityDetails;
import com.amano.backend.user.Currency;

import jakarta.servlet.http.Cookie;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * 017 T015/T016 (US1): HTTP contract of {@code POST /api/groups/{id}/expenses}
 * and {@code GET /api/groups/{id}/expenses/{expenseId}}
 * (contracts/group-expenses-api.md), the EXPENSE_CREATED history entry, the
 * single transaction (FR-013) and the real {@code expensesCount} of the group
 * detail (FR-027). Every test builds its own group: Ana (ADMIN, payer), Juan
 * (MEMBER) and the simulated profile Abuela.
 */
@SpringBootTest(properties = {
        "jwt.secret=2b7e151628aed2a6abf7158809cf4f3c2b7e151628aed2a6abf7158809cf4f3c"
})
@AutoConfigureMockMvc
@Testcontainers
class GroupExpenseControllerIT {

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

    @MockitoSpyBean
    private ActivityEventService activityEventService;

    @MockitoBean
    private BcraExchangeRateClient bcraExchangeRateClient;

    @Autowired
    private ExchangeRateProvider exchangeRateProvider;

    private long ana;
    private long juan;
    private long outsider;
    private Cookie anaSession;
    private Cookie juanSession;
    private Cookie outsiderSession;
    private long groupId;
    private long abuela;

    @BeforeEach
    void buildGroup() throws Exception {
        if (exchangeRateProvider instanceof CachingExchangeRateProvider caching) {
            caching.clear();
        }
        reset(bcraExchangeRateClient);
        lenient().when(bcraExchangeRateClient.getQuote(any(), any(), any()))
                .thenReturn(new ExchangeRateQuote(
                        new BigDecimal("1.00000000"),
                        LocalDate.of(2026, 11, 5),
                        BcraExchangeRateClient.SOURCE_BCRA));
        int n = SEQUENCE.incrementAndGet();
        ana = user("Ana", "López", "ana" + n + "@expenses.dev");
        juan = user("Juan", "Pérez", "juan" + n + "@expenses.dev");
        outsider = user("Otra", "Persona", "otra" + n + "@expenses.dev");
        jdbc.update("""
                INSERT INTO friendship_requests (sender_id, recipient_id, status, responded_at)
                VALUES (?, ?, 'ACCEPTED', now())
                """, ana, juan);
        anaSession = new Cookie("amano_auth", jwtService.issueToken(ana));
        juanSession = new Cookie("amano_auth", jwtService.issueToken(juan));
        outsiderSession = new Cookie("amano_auth", jwtService.issueToken(outsider));

        String created = mockMvc.perform(post("/api/groups").cookie(anaSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\": \"Depto\", \"member\": {\"memberType\": \"REGISTERED\","
                        + " \"identifier\": \"juan" + n + "@expenses.dev\"}}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        groupId = objectMapper.readTree(created).get("id").asLong();
        mockMvc.perform(post("/api/groups/" + groupId + "/members").cookie(anaSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"memberType\": \"SIMULATED\", \"identifier\": \"abuela" + n + "@expenses.dev\","
                        + " \"name\": \"Abuela\"}"))
                .andExpect(status().isCreated());
        abuela = jdbc.queryForObject("SELECT id FROM simulated_profiles WHERE group_id = ?", Long.class, groupId);
    }

    private long user(String first, String last, String email) {
        return jdbc.queryForObject("""
                INSERT INTO users (first_name, last_name, email, password_hash, terms_accepted_at, email_verified_at)
                VALUES (?, ?, ?, 'x', now(), now()) RETURNING id
                """, Long.class, first, last, email);
    }

    private String threeWayBody(String amount) {
        return """
                {"amount": "%s", "currency": "ARS", "date": "2026-11-05", "description": "Súper",
                 "deviceTimeZone": "America/Argentina/Buenos_Aires", "payerUserId": %d,
                 "participants": [
                   {"type": "REGISTERED", "userId": %d},
                   {"type": "REGISTERED", "userId": %d},
                   {"type": "SIMULATED", "simulatedProfileId": %d}]}
                """.formatted(amount, ana, ana, juan, abuela);
    }

    private ResultActions postExpense(Cookie session, String key, String body) throws Exception {
        var request = post("/api/groups/" + groupId + "/expenses")
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

    /** Rows of {@code group_expenses} or {@code debts} of the current group. */
    private int count(String table) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM " + table + " WHERE group_id = ?", Integer.class, groupId);
    }

    private int expenseEvents() {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM group_activity WHERE group_id = ? AND event_type = 'EXPENSE_CREATED'",
                Integer.class, groupId);
    }

    @Test
    void createsTheExpenseWithItsSharesAndPendingDebts() throws Exception {
        String body = postExpense(anaSession, UUID.randomUUID().toString(), threeWayBody("10000.00"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location",
                        org.hamcrest.Matchers.matchesPattern("/api/groups/" + groupId + "/expenses/\\d+")))
                .andExpect(jsonPath("$.groupId").value(groupId))
                .andExpect(jsonPath("$.amount").value("10000.00"))
                .andExpect(jsonPath("$.currency").value("ARS"))
                .andExpect(jsonPath("$.date").value("2026-11-05"))
                .andExpect(jsonPath("$.description").value("Súper"))
                .andExpect(jsonPath("$.payer.userId").value(ana))
                .andExpect(jsonPath("$.payer.name").value("Ana López"))
                .andExpect(jsonPath("$.createdBy.userId").value(ana))
                .andExpect(jsonPath("$.createdAt").isNotEmpty())
                .andExpect(jsonPath("$.shares.length()").value(3))
                .andExpect(jsonPath("$.shares[0].amount").value("3333.34"))
                .andExpect(jsonPath("$.shares[0].isPayer").value(true))
                .andExpect(jsonPath("$.shares[1].name").value("Juan Pérez"))
                .andExpect(jsonPath("$.shares[1].amount").value("3333.33"))
                .andExpect(jsonPath("$.shares[2].type").value("SIMULATED"))
                .andExpect(jsonPath("$.shares[2].simulatedProfileId").value(abuela))
                .andExpect(jsonPath("$.shares[2].name").value("Abuela"))
                .andExpect(jsonPath("$.debts.length()").value(2))
                .andExpect(jsonPath("$.debts[0].debtor.userId").value(juan))
                .andExpect(jsonPath("$.debts[0].creditor.userId").value(ana))
                .andExpect(jsonPath("$.debts[0].amount").value("3333.33"))
                .andExpect(jsonPath("$.debts[0].paidAmount").value("0.00"))
                .andExpect(jsonPath("$.debts[0].outstanding").value("3333.33"))
                .andExpect(jsonPath("$.debts[0].status").value("PENDING"))
                .andExpect(jsonPath("$.debts[1].debtor.type").value("SIMULATED"))
                .andExpect(jsonPath("$.debts[1].debtor.name").value("Abuela"))
                .andReturn().getResponse().getContentAsString();

        JsonNode created = objectMapper.readTree(body);
        assertThat(jdbc.queryForObject("SELECT SUM(amount) FROM group_expense_shares WHERE expense_id = ?",
                java.math.BigDecimal.class, created.get("id").asLong())).isEqualByComparingTo("10000.00");
        assertThat(jdbc.queryForList("SELECT status FROM debts WHERE expense_id = ?", String.class,
                created.get("id").asLong())).containsOnly("PENDING").hasSize(2);
    }

    @Test
    void withoutThePayerTheRemainderGoesToTheFirstParticipant() throws Exception {
        String body = """
                {"amount": "100.00", "currency": "USD", "date": "2026-11-05", "payerUserId": %d,
                 "participants": [{"type": "SIMULATED", "simulatedProfileId": %d},
                                  {"type": "REGISTERED", "userId": %d}]}
                """.formatted(ana, abuela, juan);
        postExpense(anaSession, UUID.randomUUID().toString(), body)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.shares[0].amount").value("50.00"))
                .andExpect(jsonPath("$.shares[1].amount").value("50.00"))
                .andExpect(jsonPath("$.debts.length()").value(2))
                .andExpect(jsonPath("$.currency").value("USD"))
                .andExpect(jsonPath("$.convertedCurrency").value("ARS"))
                .andExpect(jsonPath("$.debts[0].currency").value("ARS"))
                .andExpect(jsonPath("$.description").doesNotExist());

        String odd = """
                {"amount": "0.05", "currency": "ARS", "date": "2026-11-05", "payerUserId": %d,
                 "participants": [{"type": "SIMULATED", "simulatedProfileId": %d},
                                  {"type": "REGISTERED", "userId": %d}]}
                """.formatted(ana, abuela, juan);
        postExpense(anaSession, UUID.randomUUID().toString(), odd)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.shares[0].amount").value("0.03"))
                .andExpect(jsonPath("$.shares[1].amount").value("0.02"));
    }

    @Test
    void theDetailReturnsTheSameRepresentation() throws Exception {
        String body = postExpense(anaSession, UUID.randomUUID().toString(), threeWayBody("10000.00"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long expenseId = objectMapper.readTree(body).get("id").asLong();

        mockMvc.perform(get("/api/groups/" + groupId + "/expenses/" + expenseId).cookie(juanSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(expenseId))
                .andExpect(jsonPath("$.shares.length()").value(3))
                .andExpect(jsonPath("$.debts[0].status").value("PENDING"));
    }

    @Test
    void theExpenseAppearsInTheHistoryOnceEvenWhenReplayed() throws Exception {
        String key = UUID.randomUUID().toString();
        String body = postExpense(anaSession, key, threeWayBody("10000.00"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long expenseId = objectMapper.readTree(body).get("id").asLong();

        postExpense(anaSession, key, threeWayBody("10000.00"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(expenseId));

        assertThat(count("group_expenses")).isEqualTo(1);
        assertThat(count("debts")).isEqualTo(2);
        assertThat(expenseEvents()).isEqualTo(1);
        mockMvc.perform(get("/api/groups/" + groupId + "/activity").cookie(anaSession)
                .param("eventType", "EXPENSE_CREATED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].actor.id").value(ana))
                .andExpect(jsonPath("$.content[0].details.expenseId").value(expenseId))
                .andExpect(jsonPath("$.content[0].details.amount").value("10000.00"))
                .andExpect(jsonPath("$.content[0].details.currency").value("ARS"))
                .andExpect(jsonPath("$.content[0].details.description").value("Súper"))
                .andExpect(jsonPath("$.content[0].details.payerUserId").value(ana))
                .andExpect(jsonPath("$.content[0].details.payerName").value("Ana López"));
    }

    @Test
    void theSameKeyWithAnotherBodyIsUnprocessable() throws Exception {
        String key = UUID.randomUUID().toString();
        postExpense(anaSession, key, threeWayBody("10000.00")).andExpect(status().isCreated());

        postExpense(anaSession, key, threeWayBody("20000.00"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.properties.code").value("IDEMPOTENCY_KEY_REUSED"));
        assertThat(count("group_expenses")).isEqualTo(1);
    }

    @Test
    void t066TheSplitMethodDefaultsToEqualForLegacyPayloads() throws Exception {
        String body = postExpense(anaSession, UUID.randomUUID().toString(), threeWayBody("10000.00"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.splitMethod").value("EQUAL"))
                .andReturn().getResponse().getContentAsString();
        long expenseId = objectMapper.readTree(body).get("id").asLong();

        assertThat(jdbc.queryForObject("SELECT split_method FROM group_expenses WHERE id = ?",
                String.class, expenseId)).isEqualTo("EQUAL");
        mockMvc.perform(get("/api/groups/" + groupId + "/expenses/" + expenseId).cookie(juanSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.splitMethod").value("EQUAL"));
    }

    @Test
    void t023CustomSplitPersistsTheRequestedShares() throws Exception {
        String custom = """
                {"amount": "100.00", "currency": "ARS", "date": "2026-11-05", "description": "Súper",
                 "deviceTimeZone": "America/Argentina/Buenos_Aires", "payerUserId": %d,
                 "splitMethod": "CUSTOM",
                 "participants": [
                   {"type": "REGISTERED", "userId": %d, "amount": "30.00"},
                   {"type": "REGISTERED", "userId": %d, "amount": "60.00"},
                   {"type": "SIMULATED", "simulatedProfileId": %d, "amount": "10.00"}]}
                """.formatted(ana, ana, juan, abuela);

        String body = postExpense(anaSession, UUID.randomUUID().toString(), custom)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.splitMethod").value("CUSTOM"))
                .andExpect(jsonPath("$.shares[0].amount").value("30.00"))
                .andExpect(jsonPath("$.shares[1].amount").value("60.00"))
                .andExpect(jsonPath("$.shares[2].amount").value("10.00"))
                .andExpect(jsonPath("$.debts.length()").value(2))
                .andExpect(jsonPath("$.debts[0].amount").value("60.00"))
                .andExpect(jsonPath("$.debts[1].amount").value("10.00"))
                .andReturn().getResponse().getContentAsString();

        long expenseId = objectMapper.readTree(body).get("id").asLong();
        assertThat(jdbc.queryForObject("SELECT SUM(amount) FROM group_expense_shares WHERE expense_id = ?",
                BigDecimal.class, expenseId)).isEqualByComparingTo("100.00");
        assertThat(jdbc.queryForObject("SELECT split_method FROM group_expenses WHERE id = ?",
                String.class, expenseId)).isEqualTo("CUSTOM");
    }

    @Test
    void t023CustomSplitMustSumTheConvertedTotal() throws Exception {
        String invalid = """
                {"amount": "100.00", "currency": "ARS", "date": "2026-11-05", "payerUserId": %d,
                 "splitMethod": "CUSTOM",
                 "participants": [
                   {"type": "REGISTERED", "userId": %d, "amount": "30.00"},
                   {"type": "REGISTERED", "userId": %d, "amount": "60.00"},
                   {"type": "SIMULATED", "simulatedProfileId": %d, "amount": "9.99"}]}
                """.formatted(ana, ana, juan, abuela);

        postExpense(anaSession, UUID.randomUUID().toString(), invalid)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.properties.fields.participants")
                        .value("Los importes personalizados deben sumar exactamente el total del gasto"));

        assertThat(count("group_expenses")).isZero();
        assertThat(count("debts")).isZero();
    }

    @Test
    void t024PercentageSplitFloorsNonPayersAndThePayerAbsorbsTheRemainder() throws Exception {
        String percentage = """
                {"amount": "0.05", "currency": "ARS", "date": "2026-11-05", "payerUserId": %d,
                 "splitMethod": "PERCENTAGE",
                 "participants": [
                   {"type": "REGISTERED", "userId": %d, "percentage": "33.33"},
                   {"type": "REGISTERED", "userId": %d, "percentage": "33.33"},
                   {"type": "SIMULATED", "simulatedProfileId": %d, "percentage": "33.34"}]}
                """.formatted(ana, ana, juan, abuela);

        String body = postExpense(anaSession, UUID.randomUUID().toString(), percentage)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.splitMethod").value("PERCENTAGE"))
                .andExpect(jsonPath("$.shares[0].amount").value("0.03"))
                .andExpect(jsonPath("$.shares[1].amount").value("0.01"))
                .andExpect(jsonPath("$.shares[2].amount").value("0.01"))
                .andExpect(jsonPath("$.debts[0].amount").value("0.01"))
                .andExpect(jsonPath("$.debts[1].amount").value("0.01"))
                .andReturn().getResponse().getContentAsString();

        long expenseId = objectMapper.readTree(body).get("id").asLong();
        assertThat(jdbc.queryForObject("SELECT SUM(amount) FROM group_expense_shares WHERE expense_id = ?",
                BigDecimal.class, expenseId)).isEqualByComparingTo("0.05");
    }

    @Test
    void t024SplitValidationMessagesAreVoseoAndRejectInvalidCombinations() throws Exception {
        String equalWithAmount = """
                {"amount": "100.00", "currency": "ARS", "date": "2026-11-05", "payerUserId": %d,
                 "splitMethod": "EQUAL",
                 "participants": [
                   {"type": "REGISTERED", "userId": %d, "amount": "50.00"},
                   {"type": "REGISTERED", "userId": %d},
                   {"type": "SIMULATED", "simulatedProfileId": %d}]}
                """.formatted(ana, ana, juan, abuela);
        postExpense(anaSession, UUID.randomUUID().toString(), equalWithAmount)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.properties.fields.participants")
                        .value("El reparto en partes iguales no acepta importes ni porcentajes por participante"));

        String unknownMethod = """
                {"amount": "100.00", "currency": "ARS", "date": "2026-11-05", "payerUserId": %d,
                 "splitMethod": "HALF",
                 "participants": [{"type": "REGISTERED", "userId": %d},
                                  {"type": "REGISTERED", "userId": %d},
                                  {"type": "SIMULATED", "simulatedProfileId": %d}]}
                """.formatted(ana, ana, juan, abuela);
        postExpense(anaSession, UUID.randomUUID().toString(), unknownMethod)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.properties.fields.splitMethod")
                        .value("El método de reparto debe ser EQUAL, CUSTOM o PERCENTAGE"));

        String zeroPercentage = """
                {"amount": "100.00", "currency": "ARS", "date": "2026-11-05", "payerUserId": %d,
                 "splitMethod": "PERCENTAGE",
                 "participants": [
                   {"type": "REGISTERED", "userId": %d, "percentage": "0"},
                   {"type": "REGISTERED", "userId": %d, "percentage": "50"},
                   {"type": "SIMULATED", "simulatedProfileId": %d, "percentage": "50"}]}
                """.formatted(ana, ana, juan, abuela);
        postExpense(anaSession, UUID.randomUUID().toString(), zeroPercentage)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.properties.fields.participants")
                        .value("Cada porcentaje tiene que ser mayor que 0"));

        String wrongPercentageSum = """
                {"amount": "100.00", "currency": "ARS", "date": "2026-11-05", "payerUserId": %d,
                 "splitMethod": "PERCENTAGE",
                 "participants": [
                   {"type": "REGISTERED", "userId": %d, "percentage": "33.33"},
                   {"type": "REGISTERED", "userId": %d, "percentage": "33.33"},
                   {"type": "SIMULATED", "simulatedProfileId": %d, "percentage": "33.33"}]}
                """.formatted(ana, ana, juan, abuela);
        postExpense(anaSession, UUID.randomUUID().toString(), wrongPercentageSum)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.properties.fields.participants")
                        .value("Los porcentajes deben sumar exactamente 100"));

        String mixed = """
                {"amount": "100.00", "currency": "ARS", "date": "2026-11-05", "payerUserId": %d,
                 "splitMethod": "CUSTOM",
                 "participants": [
                   {"type": "REGISTERED", "userId": %d, "amount": "30.00", "percentage": "30"},
                   {"type": "REGISTERED", "userId": %d, "amount": "30.00"},
                   {"type": "SIMULATED", "simulatedProfileId": %d, "amount": "40.00"}]}
                """.formatted(ana, ana, juan, abuela);
        postExpense(anaSession, UUID.randomUUID().toString(), mixed)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.properties.fields.participants")
                        .value("Elegí importe o porcentaje, no ambos"));

        String payerOutside = """
                {"amount": "100.00", "currency": "ARS", "date": "2026-11-05", "payerUserId": %d,
                 "splitMethod": "CUSTOM",
                 "participants": [
                   {"type": "REGISTERED", "userId": %d, "amount": "60.00"},
                   {"type": "SIMULATED", "simulatedProfileId": %d, "amount": "40.00"}]}
                """.formatted(ana, juan, abuela);
        postExpense(anaSession, UUID.randomUUID().toString(), payerOutside)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.properties.fields.participants")
                        .value("Quien pagó tiene que participar del reparto"));

        assertThat(count("group_expenses")).isZero();
        assertThat(expenseEvents()).isZero();
    }

    @Test
    void t023AndT024IdempotencyConsidersTheSplitMethodAndAmounts() throws Exception {
        String key = UUID.randomUUID().toString();
        String custom = """
                {"amount": "100.00", "currency": "ARS", "date": "2026-11-05", "payerUserId": %d,
                 "splitMethod": "CUSTOM",
                 "participants": [
                   {"type": "REGISTERED", "userId": %d, "amount": "30.00"},
                   {"type": "REGISTERED", "userId": %d, "amount": "60.00"},
                   {"type": "SIMULATED", "simulatedProfileId": %d, "amount": "10.00"}]}
                """.formatted(ana, ana, juan, abuela);
        postExpense(anaSession, key, custom).andExpect(status().isCreated());

        postExpense(anaSession, key, custom)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.splitMethod").value("CUSTOM"));
        postExpense(anaSession, key, threeWayBody("100.00"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.properties.code").value("IDEMPOTENCY_KEY_REUSED"));

        String otherAmounts = """
                {"amount": "100.00", "currency": "ARS", "date": "2026-11-05", "payerUserId": %d,
                 "splitMethod": "CUSTOM",
                 "participants": [
                   {"type": "REGISTERED", "userId": %d, "amount": "30.00"},
                   {"type": "REGISTERED", "userId": %d, "amount": "50.00"},
                   {"type": "SIMULATED", "simulatedProfileId": %d, "amount": "20.00"}]}
                """.formatted(ana, ana, juan, abuela);
        postExpense(anaSession, key, otherAmounts)
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.properties.code").value("IDEMPOTENCY_KEY_REUSED"));

        assertThat(count("group_expenses")).isEqualTo(1);
        assertThat(count("debts")).isEqualTo(2);
    }

    @Test
    void t023CustomSplitOperatesOnTheConvertedTotal() throws Exception {
        LocalDate date = LocalDate.of(2026, 1, 15);
        when(bcraExchangeRateClient.getQuote(Currency.USD, Currency.ARS, date))
                .thenReturn(new ExchangeRateQuote(
                        new BigDecimal("18.50000000"), date, BcraExchangeRateClient.SOURCE_BCRA));

        String usd = """
                {"amount": "50.00", "currency": "USD", "date": "2026-01-15", "payerUserId": %d,
                 "splitMethod": "CUSTOM",
                 "participants": [
                   {"type": "REGISTERED", "userId": %d, "amount": "25.00"},
                   {"type": "REGISTERED", "userId": %d, "amount": "25.00"}]}
                """.formatted(ana, ana, juan);

        postExpense(anaSession, UUID.randomUUID().toString(), usd)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.convertedAmount").value("925.00"))
                .andExpect(jsonPath("$.splitMethod").value("CUSTOM"))
                .andExpect(jsonPath("$.shares[0].amount").value("462.50"))
                .andExpect(jsonPath("$.shares[1].amount").value("462.50"))
                .andExpect(jsonPath("$.debts[0].amount").value("462.50"))
                .andExpect(jsonPath("$.debts[0].currency").value("ARS"));
    }

    @Test
    void invalidFieldsAnswer400PerFieldWithoutWriting() throws Exception {
        String body = """
                {"amount": "0.02", "currency": "XYZ", "date": "2999-01-01", "payerUserId": %d,
                 "participants": [{"type": "REGISTERED", "userId": %d},
                                  {"type": "REGISTERED", "userId": %d},
                                  {"type": "SIMULATED", "simulatedProfileId": %d}], "category": "FOOD"}
                """.formatted(ana, ana, juan, abuela);

        postExpense(anaSession, UUID.randomUUID().toString(), body)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.properties.fields.currency").value("La moneda no es válida"))
                .andExpect(jsonPath("$.properties.fields.date").value("La fecha no puede ser posterior a hoy"))
                .andExpect(jsonPath("$.properties.fields.category").value("No se permite informar este campo"));

        String tooSmall = threeWayBody("0.02");
        postExpense(anaSession, UUID.randomUUID().toString(), tooSmall)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.properties.fields.amount")
                        .value("El monto no alcanza para dividirlo entre 3 participantes"));

        String onlyPayer = """
                {"amount": "10.00", "currency": "ARS", "date": "2026-11-05", "payerUserId": %d,
                 "participants": [{"type": "REGISTERED", "userId": %d}]}
                """.formatted(ana, ana);
        postExpense(anaSession, UUID.randomUUID().toString(), onlyPayer)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.debts.length()").value(0));

        String outsiderParticipant = """
                {"amount": "10.00", "currency": "ARS", "date": "2026-11-05", "payerUserId": %d,
                 "participants": [{"type": "REGISTERED", "userId": %d}]}
                """.formatted(ana, outsider);
        postExpense(anaSession, UUID.randomUUID().toString(), outsiderParticipant)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.properties.fields.participants")
                        .value("Algún participante no pertenece al grupo"));

        assertThat(count("group_expenses")).isEqualTo(1);
        assertThat(expenseEvents()).isEqualTo(1);
    }

    @Test
    void theIdempotencyKeyIsRequiredAndMustBeAUuid() throws Exception {
        postExpense(anaSession, null, threeWayBody("10.00"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Falta la cabecera Idempotency-Key en la solicitud"));
        postExpense(anaSession, "no-es-uuid", threeWayBody("10.00"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.properties.fields.idempotencyKey")
                        .value("La cabecera Idempotency-Key debe ser un UUID válido"));
        assertThat(count("group_expenses")).isZero();
    }

    @Test
    void theV9CasillasAreEnforced() throws Exception {
        String juanPaysForAna = threeWayBody("10.00");
        postExpense(juanSession, UUID.randomUUID().toString(), juanPaysForAna)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value("Solo podés registrar gastos pagados por vos"));

        mockMvc.perform(put("/api/groups/" + groupId).cookie(anaSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"permissions\": {\"membersCanGenerateExpensesForOthers\": true}}"))
                .andExpect(status().isOk());
        postExpense(juanSession, UUID.randomUUID().toString(), juanPaysForAna)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.createdBy.userId").value(juan))
                .andExpect(jsonPath("$.payer.userId").value(ana));

        mockMvc.perform(get("/api/groups/" + groupId + "/activity").cookie(anaSession)
                .param("eventType", "EXPENSE_CREATED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].actor.id").value(juan))
                .andExpect(jsonPath("$.content[0].actor.firstName").value("Juan"))
                .andExpect(jsonPath("$.content[0].actor.lastName").value("Pérez"))
                .andExpect(jsonPath("$.content[0].details.payerUserId").value(ana))
                .andExpect(jsonPath("$.content[0].details.payerName").value("Ana López"));

        mockMvc.perform(put("/api/groups/" + groupId).cookie(anaSession)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"permissions\": {\"membersCannotMakeExpenses\": true}}"))
                .andExpect(status().isOk());
        String juanPays = """
                {"amount": "10.00", "currency": "ARS", "date": "2026-11-05", "payerUserId": %d,
                 "participants": [{"type": "REGISTERED", "userId": %d}, {"type": "REGISTERED", "userId": %d}]}
                """.formatted(juan, juan, ana);
        postExpense(juanSession, UUID.randomUUID().toString(), juanPays)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value("No podés registrar gastos en este grupo"));

        postExpense(anaSession, UUID.randomUUID().toString(), threeWayBody("10.00"))
                .andExpect(status().isCreated());
    }

    @Test
    void outsidersGetTheNeutralNotFoundAndAnonymousGet401() throws Exception {
        postExpense(outsiderSession, UUID.randomUUID().toString(), threeWayBody("10.00"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("No existe el grupo solicitado o no sos miembro"));
        postExpense(null, UUID.randomUUID().toString(), threeWayBody("10.00"))
                .andExpect(status().isUnauthorized());

        String body = postExpense(anaSession, UUID.randomUUID().toString(), threeWayBody("10.00"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long expenseId = objectMapper.readTree(body).get("id").asLong();
        mockMvc.perform(get("/api/groups/" + groupId + "/expenses/" + expenseId).cookie(outsiderSession))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/groups/" + groupId + "/expenses/" + expenseId))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void anExpenseOfAnotherGroupIsNotFound() throws Exception {
        String body = postExpense(anaSession, UUID.randomUUID().toString(), threeWayBody("10.00"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long expenseId = objectMapper.readTree(body).get("id").asLong();
        long otherGroup = groupId;

        buildGroup(); // Ana's second group; the expense belongs to the first one
        assertThat(groupId).isNotEqualTo(otherGroup);

        mockMvc.perform(get("/api/groups/" + groupId + "/expenses/" + expenseId).cookie(anaSession))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/groups/" + groupId + "/expenses/999999").cookie(anaSession))
                .andExpect(status().isNotFound());
    }

    @Test
    void aFailureAfterTheDebtsRollsBackEverything() throws Exception {
        // the spy sits behind the transactional proxy: stub the target, not the proxy
        ActivityEventService target = AopTestUtils.getUltimateTargetObject(activityEventService);
        doThrow(new IllegalStateException("history down"))
                .when(target).recordExpenseCreated(anyLong(), anyLong(), any(ExpenseActivityDetails.class));

        postExpense(anaSession, UUID.randomUUID().toString(), threeWayBody("10.00"))
                .andExpect(status().isInternalServerError());

        assertThat(count("group_expenses")).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM debts WHERE group_id = ?", Integer.class, groupId))
                .isZero();
        assertThat(expenseEvents()).isZero();
    }

    @Test
    void theGroupDetailCountsItsRealExpenses() throws Exception {
        mockMvc.perform(get("/api/groups/" + groupId).cookie(anaSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.expensesCount").value(0));

        postExpense(anaSession, UUID.randomUUID().toString(), threeWayBody("10.00")).andExpect(status().isCreated());
        postExpense(anaSession, UUID.randomUUID().toString(), threeWayBody("20.00")).andExpect(status().isCreated());

        mockMvc.perform(get("/api/groups/" + groupId).cookie(juanSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.expensesCount").value(2));
    }

    @Test
    void t051DifferentCurrencyConvertsOnceAndStoresOriginalConvertedAndRate() throws Exception {
        LocalDate sunday = LocalDate.of(2026, 1, 18);
        LocalDate friday = LocalDate.of(2026, 1, 16);
        when(bcraExchangeRateClient.getQuote(Currency.USD, Currency.ARS, sunday))
                .thenReturn(new ExchangeRateQuote(
                        new BigDecimal("18.50000000"), friday, BcraExchangeRateClient.SOURCE_BCRA));

        String usdBody = """
                {"amount": "50.00", "currency": "USD", "date": "2026-01-18", "description": "Hotel",
                 "deviceTimeZone": "America/Argentina/Buenos_Aires", "payerUserId": %d,
                 "participants": [
                   {"type": "REGISTERED", "userId": %d},
                   {"type": "REGISTERED", "userId": %d}]}
                """.formatted(ana, ana, juan);

        String response = postExpense(anaSession, UUID.randomUUID().toString(), usdBody)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.amount").value("50.00"))
                .andExpect(jsonPath("$.currency").value("USD"))
                .andExpect(jsonPath("$.convertedAmount").value("925.00"))
                .andExpect(jsonPath("$.convertedCurrency").value("ARS"))
                .andExpect(jsonPath("$.exchangeRate").value("18.5"))
                .andExpect(jsonPath("$.rateDate").value("2026-01-16"))
                .andExpect(jsonPath("$.rateSource").value("BCRA"))
                // Split and debts are calculated once over the converted amount (925.00 ARS)
                .andExpect(jsonPath("$.shares[0].amount").value("462.50"))
                .andExpect(jsonPath("$.shares[1].amount").value("462.50"))
                .andExpect(jsonPath("$.debts[0].amount").value("462.50"))
                .andExpect(jsonPath("$.debts[0].currency").value("ARS"))
                .andReturn().getResponse().getContentAsString();

        long expenseId = objectMapper.readTree(response).get("id").asLong();
        Map<String, Object> row = jdbc.queryForMap(
                "SELECT amount, currency, converted_amount, exchange_rate, rate_date, rate_source FROM group_expenses WHERE id = ?",
                expenseId);
        assertThat((BigDecimal) row.get("amount")).isEqualByComparingTo("50.00");
        assertThat(row.get("currency")).isEqualTo("USD");
        assertThat((BigDecimal) row.get("converted_amount")).isEqualByComparingTo("925.00");
        assertThat((BigDecimal) row.get("exchange_rate")).isEqualByComparingTo("18.50000000");
        assertThat(row.get("rate_date").toString()).isEqualTo("2026-01-16");
        assertThat(row.get("rate_source")).isEqualTo("BCRA");
    }

    @Test
    void t052SecondExpenseWithSameCurrencyAndDateUsesCachedQuoteWithoutSecondApiCall() throws Exception {
        LocalDate date = LocalDate.of(2026, 1, 15);
        when(bcraExchangeRateClient.getQuote(Currency.USD, Currency.ARS, date))
                .thenReturn(new ExchangeRateQuote(
                        new BigDecimal("18.50000000"), date, BcraExchangeRateClient.SOURCE_BCRA));

        String usdBody = """
                {"amount": "50.00", "currency": "USD", "date": "2026-01-15", "description": "Taxi",
                 "deviceTimeZone": "America/Argentina/Buenos_Aires", "payerUserId": %d,
                 "participants": [
                   {"type": "REGISTERED", "userId": %d},
                   {"type": "REGISTERED", "userId": %d}]}
                """.formatted(ana, ana, juan);

        postExpense(anaSession, UUID.randomUUID().toString(), usdBody).andExpect(status().isCreated());
        postExpense(anaSession, UUID.randomUUID().toString(), usdBody).andExpect(status().isCreated());

        assertThat(count("group_expenses")).isEqualTo(2);
        verify(bcraExchangeRateClient, times(1)).getQuote(Currency.USD, Currency.ARS, date);
    }

    @Test
    void t050AndT053WhenQuoteIsUnavailableAnswers503AndDoesNotSaveExpense() throws Exception {
        LocalDate date = LocalDate.of(2026, 1, 18);
        when(bcraExchangeRateClient.getQuote(Currency.USD, Currency.ARS, date))
                .thenThrow(new ExchangeRateUnavailableException());

        String usdBody = """
                {"amount": "50.00", "currency": "USD", "date": "2026-01-18", "description": "Cena",
                 "deviceTimeZone": "America/Argentina/Buenos_Aires", "payerUserId": %d,
                 "participants": [
                   {"type": "REGISTERED", "userId": %d},
                   {"type": "REGISTERED", "userId": %d}]}
                """.formatted(ana, ana, juan);

        postExpense(anaSession, UUID.randomUUID().toString(), usdBody)
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value(503))
                .andExpect(jsonPath("$.detail")
                        .value("No se pudo obtener la cotización; reintente después o registre en moneda del grupo"))
                .andExpect(jsonPath("$.properties.code").value("EXCHANGE_RATE_UNAVAILABLE"));

        assertThat(count("group_expenses")).isZero();
        assertThat(count("debts")).isZero();
        assertThat(expenseEvents()).isZero();
    }

    @Test
    void t054SameCurrencyUsesRateOneAndConvertedEqualsAmountWithoutCallingProvider() throws Exception {
        String body = postExpense(anaSession, UUID.randomUUID().toString(), threeWayBody("10000.00"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.amount").value("10000.00"))
                .andExpect(jsonPath("$.currency").value("ARS"))
                .andExpect(jsonPath("$.convertedAmount").value("10000.00"))
                .andExpect(jsonPath("$.convertedCurrency").value("ARS"))
                .andExpect(jsonPath("$.exchangeRate").value("1"))
                .andExpect(jsonPath("$.rateDate").value("2026-11-05"))
                .andExpect(jsonPath("$.rateSource").value("SAME_CURRENCY"))
                .andReturn().getResponse().getContentAsString();

        verify(bcraExchangeRateClient, never()).getQuote(any(), any(), any());

        long expenseId = objectMapper.readTree(body).get("id").asLong();
        Map<String, Object> row = jdbc.queryForMap(
                "SELECT amount, converted_amount, exchange_rate, rate_date, rate_source FROM group_expenses WHERE id = ?",
                expenseId);
        assertThat((BigDecimal) row.get("converted_amount")).isEqualByComparingTo("10000.00");
        assertThat((BigDecimal) row.get("exchange_rate")).isEqualByComparingTo("1.00000000");
        assertThat(row.get("rate_date").toString()).isEqualTo("2026-11-05");
        assertThat(row.get("rate_source")).isEqualTo("SAME_CURRENCY");
    }
}
