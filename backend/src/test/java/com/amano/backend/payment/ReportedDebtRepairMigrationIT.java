package com.amano.backend.payment;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import com.amano.backend.config.JwtService;
import com.amano.backend.support.AbstractIntegrationIT;

import tools.jackson.databind.ObjectMapper;

/**
 * 022 T056: {@code V24__repair_reported_debts_paid_amount.sql} puts back the
 * paid amount of the debts that were left as {@code PAYMENT_REPORTED} with
 * {@code paid_amount = amount} by the previous behavior, so they count in the
 * balances again, and it leaves every other row alone.
 */
class ReportedDebtRepairMigrationIT extends AbstractIntegrationIT {

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

    private void runMigration() throws Exception {
        String sql = new String(new ClassPathResource("db/migration/V24__repair_reported_debts_paid_amount.sql")
                .getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        jdbc.execute(sql);
    }

    private BigDecimal paid(long debtId) {
        return jdbc.queryForObject("SELECT paid_amount FROM debts WHERE id = ?", BigDecimal.class, debtId);
    }

    @Test
    void aReportedDebtWrittenByTheOldBehaviorGetsItsPaidAmountBack() throws Exception {
        s.requireValidation();
        s.registerReported(s.juanSession, s.juanDebt, "3333.33");
        // New behavior: reporting leaves paid_amount untouched.
        assertThat(paid(s.juanDebt)).isEqualByComparingTo("0.00");
        // Shape written by the old behavior.
        jdbc.update("UPDATE debts SET paid_amount = amount WHERE id = ?", s.juanDebt);

        runMigration();

        assertThat(paid(s.juanDebt)).isEqualByComparingTo("0.00");
        assertThat(s.statusOf(s.juanDebt)).isEqualTo("PAYMENT_REPORTED");
    }

    @Test
    void aLegacyPartialPaymentIsRestoredAndOtherRowsAreUntouched() throws Exception {
        // The 018 partial payment is made before validation is required (T085: the inherited
        // endpoint now respects that setting as well).
        s.pay(s.juanSession, s.juanDebt, "1000.00");
        s.requireValidation();
        s.registerReported(s.juanSession, s.juanDebt, "2333.33");
        assertThat(paid(s.juanDebt)).isEqualByComparingTo("1000.00");
        jdbc.update("UPDATE debts SET paid_amount = amount WHERE id = ?", s.juanDebt);
        // A settled debt and a pending one must not change.
        long settled = s.abuelaDebt;
        jdbc.update("UPDATE debts SET paid_amount = amount, status = 'PAID' WHERE id = ?", settled);

        runMigration();

        assertThat(paid(s.juanDebt)).isEqualByComparingTo("1000.00");
        assertThat(paid(settled)).isEqualByComparingTo("3333.33");
        assertThat(s.statusOf(settled)).isEqualTo("PAID");

        // Running it again changes nothing (idempotent).
        runMigration();
        assertThat(paid(s.juanDebt)).isEqualByComparingTo("1000.00");
    }
}
