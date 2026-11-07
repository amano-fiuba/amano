package com.amano.backend.groupexpense.reminder;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.amano.backend.groupexpense.DebtStatus;
import com.amano.backend.user.Currency;

/**
 * Pure renderer of the reminder mails (025 contracts/reminder-delivery-and-activity.md
 * §5). Plain text, Spanish voseo. It only formats data; resolving recipients
 * and sending is the job of {@link ReminderRecipients} and
 * {@link ReminderMailService}.
 */
@Component
public class ReminderMessageBuilder {

    public static final String DEFAULT_CONCEPT = "Gasto del grupo";
    public static final String PAYMENT_REPORTED_LEGEND =
            "Tu pago está informado y pendiente de verificación (pagado a verificar).";

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    /** Subject and body of a mail, ready to be sent. */
    public record Mail(String subject, String body) {
    }

    public Mail debtReminder(String groupName, String debtorName, String concept, BigDecimal amount,
            Currency currency, LocalDate dueDate, DebtStatus status, String customMessage) {
        String subject = "Recordatorio de deuda - " + groupName;
        String conceptText = concept(concept);
        String money = money(amount, currency);
        String due = dueDate.format(DATE_FORMAT);
        boolean reported = status == DebtStatus.PAYMENT_REPORTED;
        StringBuilder body = new StringBuilder();
        if (customMessage != null && !customMessage.isBlank()) {
            body.append(customMessage.trim()).append("\n\n")
                    .append("Grupo: ").append(groupName).append('\n')
                    .append("Concepto: ").append(conceptText).append('\n')
                    .append("Monto: ").append(money).append('\n')
                    .append("Fecha límite: ").append(due).append('\n');
            if (reported) {
                body.append('\n').append(PAYMENT_REPORTED_LEGEND).append('\n');
            }
        }
        else {
            body.append("Hola ").append(debtorName).append(":\n\n")
                    .append("Te recordamos que en el grupo «").append(groupName)
                    .append("» tenés una deuda pendiente\n")
                    .append("de ").append(money).append(" por «").append(conceptText)
                    .append("», con fecha límite ").append(due).append(".\n");
            if (reported) {
                body.append('\n').append(PAYMENT_REPORTED_LEGEND).append('\n');
            }
        }
        return new Mail(subject, body.toString());
    }

    public Mail payerNotice(String groupName, String payerName, String concept, LocalDate dueDate,
            long daysRemaining, List<Map<String, String>> pendingTotals) {
        String subject = "Aviso de vencimiento - " + groupName;
        String conceptText = concept(concept);
        StringBuilder body = new StringBuilder();
        body.append("Hola ").append(payerName).append(":\n\n")
                .append("En el grupo «").append(groupName).append("», el gasto «")
                .append(conceptText).append("» tiene fecha límite ")
                .append(dueDate.format(DATE_FORMAT)).append(": ")
                .append(daysRemaining).append(daysRemaining == 1 ? " día restante" : " días restantes")
                .append(" para el vencimiento.\n");
        if (pendingTotals == null || pendingTotals.isEmpty()) {
            body.append("\nNo tenés deudas pendientes en el grupo.\n");
        }
        else {
            body.append('\n');
            for (Map<String, String> total : pendingTotals) {
                body.append("También tenés deudas pendientes en el grupo por ")
                        .append(total.get("amount")).append(' ').append(total.get("currency"))
                        .append(".\n");
            }
        }
        return new Mail(subject, body.toString());
    }

    public Mail failureNotice(String groupName, String type, String recipientName,
            String recipientEmail, LocalDate dueDate, BigDecimal amount, Currency currency) {
        String subject = "No se pudo enviar un recordatorio - " + groupName;
        String body = """
                Hola:

                No se pudo enviar el recordatorio de %s del grupo «%s»
                a %s (%s).

                Fecha límite: %s.
                Monto: %s.
                No se reintentará el envío.
                """.formatted(type, groupName, recipientName, recipientEmail,
                dueDate.format(DATE_FORMAT), money(amount, currency));
        return new Mail(subject, body);
    }

    static String concept(String concept) {
        return concept == null || concept.isBlank() ? DEFAULT_CONCEPT : concept.trim();
    }

    static String money(BigDecimal amount, Currency currency) {
        BigDecimal value = amount == null ? BigDecimal.ZERO : amount;
        return value.setScale(2, RoundingMode.HALF_UP).toPlainString() + " " + currency.name();
    }
}
