package com.amano.backend.groupexpense.reminder;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.amano.backend.groupexpense.DebtStatus;
import com.amano.backend.user.Currency;

/** 025 T012/T028: pure rendering of the reminder and payer notice mails. */
class ReminderMessageBuilderTest {

    private static final LocalDate DUE_DATE = LocalDate.of(2026, 11, 20);

    private final ReminderMessageBuilder builder = new ReminderMessageBuilder();

    @Test
    void debtSubjectIncludesTheGroupName() {
        ReminderMessageBuilder.Mail mail = debtMail("Súper", DebtStatus.PENDING, null);
        assertThat(mail.subject()).isEqualTo("Recordatorio de deuda - Grupo Amigos");
    }

    @Test
    void defaultDebtBodyIncludesDebtorGroupAmountConceptAndDeadline() {
        ReminderMessageBuilder.Mail mail = debtMail("Súper", DebtStatus.PENDING, null);
        assertThat(mail.body())
                .contains("Hola Juan Pérez:")
                .contains("«Grupo Amigos»")
                .contains("3333.33 ARS")
                .contains("«Súper»")
                .contains("20/11/2026");
    }

    @Test
    void blankConceptFallsBackToTheDefault() {
        ReminderMessageBuilder.Mail mail = debtMail("  ", DebtStatus.PENDING, null);
        assertThat(mail.body()).contains("«Gasto del grupo»");
    }

    @Test
    void paymentReportedAddsTheVerificationLegend() {
        ReminderMessageBuilder.Mail mail = debtMail("Súper", DebtStatus.PAYMENT_REPORTED, null);
        assertThat(mail.body()).contains("pagado a verificar");
    }

    @Test
    void theDebtReminderDoesNotIncludeTheCreditor() {
        ReminderMessageBuilder.Mail mail = debtMail("Súper", DebtStatus.PENDING, null);
        assertThat(mail.body()).doesNotContain("Ana López");
    }

    @Test
    void customMessageAccompaniesTheDebtData() {
        ReminderMessageBuilder.Mail mail = debtMail("Súper", DebtStatus.PENDING, "Te esperamos");
        assertThat(mail.body())
                .contains("Te esperamos")
                .contains("Grupo: Grupo Amigos")
                .contains("Concepto: Súper")
                .contains("Monto: 3333.33 ARS")
                .contains("Fecha límite: 20/11/2026");
    }

    @Test
    void blankCustomMessageFallsBackToTheDefaultBody() {
        ReminderMessageBuilder.Mail mail = debtMail("Súper", DebtStatus.PENDING, "   ");
        assertThat(mail.body())
                .contains("Hola Juan Pérez:")
                .doesNotContain("Grupo: Grupo Amigos");
    }

    @Test
    void customMessageKeepsThePaymentReportedLegend() {
        ReminderMessageBuilder.Mail mail = debtMail("Súper", DebtStatus.PAYMENT_REPORTED, "Te esperamos");
        assertThat(mail.body()).contains("Te esperamos").contains("pagado a verificar");
    }

    @Test
    void payerSubjectIncludesTheGroupName() {
        ReminderMessageBuilder.Mail mail = payerMail(List.of());
        assertThat(mail.subject()).isEqualTo("Aviso de vencimiento - Grupo Amigos");
    }

    @Test
    void payerNoticeIncludesTheRemainingDays() {
        ReminderMessageBuilder.Mail mail = payerMail(List.of());
        assertThat(mail.body())
                .contains("Hola Ana López:")
                .contains("«Súper»")
                .contains("20/11/2026")
                .contains("5 días restantes");
    }

    @Test
    void payerWithoutDebtsIsToldExplicitly() {
        ReminderMessageBuilder.Mail mail = payerMail(List.of());
        assertThat(mail.body()).contains("No tenés deudas pendientes en el grupo.");
    }

    @Test
    void payerWithDebtsGetsOneLinePerCurrency() {
        ReminderMessageBuilder.Mail mail = payerMail(List.of(
                Map.of("currency", "ARS", "amount", "1200.00"),
                Map.of("currency", "USD", "amount", "10.00")));
        assertThat(mail.body())
                .contains("También tenés deudas pendientes en el grupo por 1200.00 ARS.")
                .contains("También tenés deudas pendientes en el grupo por 10.00 USD.");
    }

    @Test
    void zeroRemainingDaysIsRenderedAsSingularDay() {
        ReminderMessageBuilder.Mail mail = builder.payerNotice("Grupo Amigos", "Ana López", "Súper",
                DUE_DATE, 0, List.of());
        assertThat(mail.body()).contains("0 días restantes");
    }

    @Test
    void oneRemainingDayIsRenderedInSingular() {
        ReminderMessageBuilder.Mail mail = builder.payerNotice("Grupo Amigos", "Ana López", "Súper",
                DUE_DATE, 1, List.of());
        assertThat(mail.body()).contains("1 día restante");
    }

    @Test
    void failureNoticeExplainsThatThereIsNoRetry() {
        ReminderMessageBuilder.Mail mail = builder.failureNotice("Grupo Amigos", "deuda",
                "Juan Pérez", "juan@example.com", DUE_DATE, new BigDecimal("3333.33"), Currency.ARS);
        assertThat(mail.subject()).isEqualTo("No se pudo enviar un recordatorio - Grupo Amigos");
        assertThat(mail.body())
                .contains("No se pudo enviar el recordatorio de deuda")
                .contains("juan@example.com")
                .contains("20/11/2026")
                .contains("3333.33 ARS")
                .contains("No se reintentará el envío.");
    }

    private ReminderMessageBuilder.Mail debtMail(String concept, DebtStatus status, String customMessage) {
        return builder.debtReminder("Grupo Amigos", "Juan Pérez", concept,
                new BigDecimal("3333.33"), Currency.ARS, DUE_DATE, status, customMessage);
    }

    private ReminderMessageBuilder.Mail payerMail(List<Map<String, String>> pendingTotals) {
        return builder.payerNotice("Grupo Amigos", "Ana López", "Súper", DUE_DATE, 5, pendingTotals);
    }
}
