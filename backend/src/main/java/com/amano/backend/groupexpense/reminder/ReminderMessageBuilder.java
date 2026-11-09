package com.amano.backend.groupexpense.reminder;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.amano.backend.expense.ExpenseCategory;
import com.amano.backend.expense.ExpenseType;
import com.amano.backend.expense.PaymentMethod;
import com.amano.backend.groupexpense.DebtStatus;
import com.amano.backend.groupexpense.GroupExpense;
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

    private static final Map<ExpenseCategory, String> CATEGORY_LABELS = Map.of(
            ExpenseCategory.FOOD, "Alimentos",
            ExpenseCategory.TRANSPORT, "Transporte",
            ExpenseCategory.HOUSING, "Vivienda",
            ExpenseCategory.SERVICES, "Servicios",
            ExpenseCategory.HEALTH, "Salud",
            ExpenseCategory.EDUCATION, "Educación",
            ExpenseCategory.LEISURE, "Ocio",
            ExpenseCategory.SHOPPING, "Compras",
            ExpenseCategory.PETS, "Mascotas",
            ExpenseCategory.OTHER, "Otros");
    private static final Map<ExpenseType, String> TYPE_LABELS = Map.of(
            ExpenseType.RECURRING, "Recurrente",
            ExpenseType.ONE_TIME, "Único");
    private static final Map<PaymentMethod, String> PAYMENT_METHOD_LABELS = Map.of(
            PaymentMethod.CASH, "Efectivo",
            PaymentMethod.DEBIT_CARD, "Tarjeta de débito",
            PaymentMethod.CREDIT_CARD, "Tarjeta de crédito",
            PaymentMethod.TRANSFER, "Transferencia",
            PaymentMethod.VIRTUAL_WALLET, "Billetera virtual",
            PaymentMethod.OTHER, "Otro");

    /** Subject and body of a mail, ready to be sent. */
    public record Mail(String subject, String body) {
    }

    public Mail debtReminder(String groupName, String debtorName, String concept, BigDecimal amount,
            Currency currency, LocalDate dueDate, DebtStatus status, String customMessage,
            GroupExpense expense) {
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
            appendExpenseDetails(body, expense);
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
            appendExpenseDetails(body, expense);
            if (reported) {
                body.append('\n').append(PAYMENT_REPORTED_LEGEND).append('\n');
            }
        }
        return new Mail(subject, body.toString());
    }

    public Mail payerNotice(String groupName, String payerName, String concept, LocalDate dueDate,
            long daysRemaining, List<Map<String, String>> pendingTotals, GroupExpense expense) {
        String subject = "Aviso de vencimiento - " + groupName;
        String conceptText = concept(concept);
        StringBuilder body = new StringBuilder();
        body.append("Hola ").append(payerName).append(":\n\n")
                .append("En el grupo «").append(groupName).append("», el gasto «")
                .append(conceptText).append("» tiene fecha límite ")
                .append(dueDate.format(DATE_FORMAT)).append(": ")
                .append(daysRemaining).append(daysRemaining == 1 ? " día restante" : " días restantes")
                .append(" para el vencimiento.\n");
        appendExpenseDetails(body, expense);
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

    /**
     * 028: categoría, tipo y medio de pago del gasto. El medio de pago se omite
     * en las ocurrencias que genera el sistema ({@code recurrenceSourceId}).
     */
    private static void appendExpenseDetails(StringBuilder body, GroupExpense expense) {
        if (expense == null) {
            return;
        }
        String category = expense.getCategory() == null ? null : CATEGORY_LABELS.get(expense.getCategory());
        if (category != null) {
            body.append("Categoría: ").append(category).append('\n');
        }
        String type = expense.getExpenseType() == null ? null : TYPE_LABELS.get(expense.getExpenseType());
        if (type != null) {
            body.append("Tipo de gasto: ").append(type).append('\n');
        }
        String paymentMethod = expense.getPaymentMethod() == null
                ? null : PAYMENT_METHOD_LABELS.get(expense.getPaymentMethod());
        if (paymentMethod != null && expense.getRecurrenceSourceId() == null) {
            body.append("Medio de pago: ").append(paymentMethod).append('\n');
        }
    }

    static String concept(String concept) {
        return concept == null || concept.isBlank() ? DEFAULT_CONCEPT : concept.trim();
    }

    static String money(BigDecimal amount, Currency currency) {
        BigDecimal value = amount == null ? BigDecimal.ZERO : amount;
        return value.setScale(2, RoundingMode.HALF_UP).toPlainString() + " " + currency.name();
    }
}
