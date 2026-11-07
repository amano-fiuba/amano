package com.amano.backend.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import com.amano.backend.config.JwtService;

import jakarta.servlet.http.Cookie;
import tools.jackson.databind.ObjectMapper;

/**
 * 018 T037–T040 (US2, FR-013/FR-014): once a debt is PAID it stops blocking
 * leaving the group and removing members or simulated profiles; a partial
 * payment does not. The 017 {@code LedgerDebtPort} is unchanged.
 */
@SpringBootTest(properties = {
        "jwt.secret=2b7e151628aed2a6abf7158809cf4f3c2b7e151628aed2a6abf7158809cf4f3c"
})
@AutoConfigureMockMvc
@Testcontainers
class PaymentUnblocksLeaveIT {

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

    private PaymentScenario s;

    @BeforeEach
    void buildGroup() throws Exception {
        s = new PaymentScenario(mockMvc, jdbc, jwtService, objectMapper);
    }

    private ResultActions leave(Cookie session) throws Exception {
        return mockMvc.perform(post("/api/groups/" + s.groupId + "/leave").cookie(session));
    }

    private boolean isActiveMember(long userId) {
        return jdbc.queryForObject("""
                SELECT COUNT(*) FROM group_members WHERE group_id = ? AND user_id = ? AND left_at IS NULL
                """, Integer.class, s.groupId, userId) == 1;
    }

    @Test
    void aPartialPaymentKeepsTheDebtorBlockedAndTheBalanceUnblocksHer() throws Exception {
        leave(s.juanSession).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.properties.code").value("PENDING_DEBTS"));

        s.pay(s.juanSession, s.juanDebt, "1000.00").andExpect(status().isCreated());
        leave(s.juanSession).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.properties.code").value("PENDING_DEBTS"));
        assertThat(isActiveMember(s.juan)).isTrue();

        s.pay(s.juanSession, s.juanDebt, "2333.33").andExpect(status().isCreated());
        leave(s.juanSession).andExpect(status().isOk());
        assertThat(isActiveMember(s.juan)).isFalse();
    }

    @Test
    void theCreditorStaysBlockedWhileAnyOfHerDebtsIsOpen() throws Exception {
        mockMvc.perform(post("/api/groups/" + s.groupId + "/members/" + s.juan + "/admin").cookie(s.anaSession))
                .andExpect(status().isOk());
        mockMvc.perform(delete("/api/groups/" + s.groupId + "/members/" + s.ana + "/admin").cookie(s.juanSession))
                .andExpect(status().isOk());

        s.pay(s.juanSession, s.juanDebt, "3333.33").andExpect(status().isCreated());
        mockMvc.perform(delete("/api/groups/" + s.groupId + "/members/" + s.ana).cookie(s.juanSession))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.properties.code").value("PENDING_DEBTS"));

        s.pay(s.juanSession, s.abuelaDebt, "3333.33").andExpect(status().isCreated());
        mockMvc.perform(delete("/api/groups/" + s.groupId + "/members/" + s.ana).cookie(s.juanSession))
                .andExpect(status().is2xxSuccessful());
        assertThat(isActiveMember(s.ana)).isFalse();
    }

    @Test
    void aDebtorWithOnePaidAndOneOpenDebtIsStillBlocked() throws Exception {
        mockMvc.perform(post("/api/groups/" + s.groupId + "/expenses").cookie(s.anaSession)
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"amount": "100.00", "currency": "ARS", "date": "2026-11-05",
                         "payerUserId": %d, "participants": [{"type": "REGISTERED", "userId": %d}]}
                        """.formatted(s.ana, s.juan)))
                .andExpect(status().isCreated());
        s.pay(s.juanSession, s.juanDebt, "3333.33").andExpect(status().isCreated());

        leave(s.juanSession).andExpect(status().isForbidden());
        assertThat(isActiveMember(s.juan)).isTrue();
    }

    @Test
    void aVerifiedDebtDoesNotBlockLeaving() throws Exception {
        s.requireValidation();
        long paymentId = s.registerReported(s.juanSession, s.juanDebt, "3333.33");

        leave(s.juanSession).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.properties.code").value("PENDING_DEBTS"));

        s.confirmPayment(s.anaSession, paymentId).andExpect(status().isOk());

        leave(s.juanSession).andExpect(status().isOk());
        assertThat(isActiveMember(s.juan)).isFalse();
    }

    @Test
    void aSimulatedProfileWithItsDebtPaidCanBeRemovedAndThePaymentKeepsItsName() throws Exception {
        mockMvc.perform(delete("/api/groups/" + s.groupId + "/simulated-profiles/" + s.abuela).cookie(s.anaSession))
                .andExpect(status().isForbidden());

        s.pay(s.anaSession, s.abuelaDebt, "3333.33").andExpect(status().isCreated());
        mockMvc.perform(delete("/api/groups/" + s.groupId + "/simulated-profiles/" + s.abuela).cookie(s.anaSession))
                .andExpect(status().is2xxSuccessful());

        var row = jdbc.queryForMap(
                "SELECT payer_simulated_profile_id, payer_name FROM payments WHERE debt_id = ?", s.abuelaDebt);
        assertThat(row.get("payer_simulated_profile_id")).isNull();
        assertThat(row.get("payer_name")).isEqualTo("Abuela");
        mockMvc.perform(get("/api/groups/" + s.groupId + "/activity").param("eventType", "PAYMENT_MADE")
                .cookie(s.anaSession))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].details.fromName").value("Abuela"));
    }
}
