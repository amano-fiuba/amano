# Contract: Correo del aviso y evento de historial (030)

**Feature**: specs/030-whistle | **Date**: 2026-11-15

## 1. Correo (`ReminderMessageBuilder.debtWhistle`)

Texto plano, español con voseo, enviado con `ReminderMailService.send` (025).

**Asunto**: `{acreedor} te recuerda una deuda - {grupo}`

**Cuerpo**:

```text
Hola {deudor}:

{acreedor} te recuerda que en el grupo «{grupo}» tenés una deuda pendiente
de {saldo} {moneda} por «{concepto}»[, con fecha límite {dd/MM/yyyy}].
Categoría: {categoría}
Tipo de gasto: {tipo}
Medio de pago: {medio}

Este aviso lo envió {acreedor} desde A mano.
```

- `{saldo}` es `amount − paid_amount`, con 2 decimales, mediante `ReminderMessageBuilder.money`.
- `{concepto}` usa `ReminderMessageBuilder.concept`, que devuelve "Gasto del grupo" si está vacío.
- La fecha límite se incluye solo si el gasto tiene `due_date`.
- Las líneas de categoría, tipo y medio de pago salen de `appendExpenseDetails`, con las mismas reglas de 025 y 028.
- **No** se usa `groups.reminder_message` (spec Q5).
- **Sujeto a Q3**: si se habilita avisar a perfiles simulados, el mismo correo va a `SimulatedProfile.email`.

## 2. Evento `DEBT_WHISTLED`

`GET /api/groups/{id}/activity` (contrato de 014) devuelve el evento nuevo con:

```json
{
  "eventType": "DEBT_WHISTLED",
  "actor": { "id": 7, "name": "Ana Gómez" },
  "details": {
    "whistleId": 15,
    "debtId": 42,
    "expenseId": 9,
    "amount": "8000.00",
    "currency": "ARS",
    "fromName": "Juan Pérez",
    "toName": "Ana Gómez",
    "description": "Cena"
  }
}
```

`frontend/src/pages/groups/activityDescriptions.js` lo describe como:

> {actor} le avisó a {fromName} por {amount} {currency} ({description})

Si falta `description`, se omite el paréntesis.

Los consumidores que no conocen el tipo nuevo deben usar su descripción por defecto. Es un cambio aditivo: nadie hace `switch` exhaustivo sin un `default`, y se verifica en T027.
