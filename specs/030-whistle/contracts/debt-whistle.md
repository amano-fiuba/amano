# Contract: Silbar una deuda (030)

**Feature**: specs/030-whistle | **Date**: 2026-11-15

## `POST /api/groups/{groupId}/debts/{debtId}/whistle`

Endpoint nuevo y aditivo (FR-017). Requiere la cookie de sesión. No lleva cuerpo.

### 201 Created

```json
{
  "whistleId": 15,
  "debtId": 42,
  "debtorName": "Juan Pérez",
  "sentAt": "2026-11-15T14:03:12-03:00",
  "nextAvailableAt": "2026-11-16T14:03:10-03:00"
}
```

- `nextAvailableAt` = `created_at` del intento + 24 h. Es el momento a partir del cual el mismo par puede volver a silbar en el grupo.
- La respuesta **no** incluye el correo del deudor (FR-016).

### Errores (RFC 7807, `application/problem+json`)

| HTTP | `properties.code` | `detail` | Cuándo |
|------|-------------------|----------|--------|
| 401 | — | "Debés iniciar sesión para continuar" | Sin sesión (mismo cuerpo que el resto de los endpoints de grupo) |
| 404 | — | "No existe el grupo solicitado o no sos miembro" | Grupo inexistente, caller no es miembro activo, o la deuda no existe o es de otro grupo |
| 403 | `WHISTLE_NOT_CREDITOR` | "Solo quien recibe el pago puede silbar esta deuda" | El caller no es el acreedor |
| 409 | `DEBT_NOT_WHISTLEABLE` | "La deuda ya no tiene saldo pendiente" / "La deuda tiene un pago esperando validación" | Estado `PAID`, `VERIFIED` o `PAYMENT_REPORTED` |
| 409 | `DEBTOR_NOT_MEMBER` | "{deudor} ya no forma parte del grupo" | El deudor registrado no es miembro activo |
| 422 | `SIMULATED_DEBTOR` | "Los perfiles simulados no reciben silbidos" | El deudor es un perfil simulado (**sujeto a Q3**) |
| 429 | `WHISTLE_RATE_LIMITED` | "Ya le silbaste a {deudor} en las últimas 24 horas" | Hay un intento `SENDING` o `SENT` del par en las últimas 24 h |
| 503 | `WHISTLE_DELIVERY_FAILED` | "No pudimos enviar el aviso. Probá de nuevo en unos minutos" | Falla del SMTP o el deudor no tiene correo resoluble |

**Particularidades del 429** (primer 429 del proyecto):

- Cabecera `Retry-After: <segundos>`.
- Cuerpo con `"properties": { "code": "WHISTLE_RATE_LIMITED", "retryAfterSeconds": 63600 }`.

**Orden de evaluación**: 401 → 404 → 403 → 409 (estado) → 422 → 409 (deudor no miembro) → 429 → 503 (research D4).

**Efectos**:

| Respuesta | Fila en `debt_whistles` | Correo | Evento `DEBT_WHISTLED` |
|---|---|---|---|
| 201 | `SENT` | 1 | Sí |
| 503 | `FAILED` | Ninguno | No |
| Cualquier otro error | Ninguna | Ninguno | No |

En ningún caso cambia la deuda (FR-006).

## Cliente frontend

`whistleDebt(groupId, debtId)` en `frontend/src/api/groupExpensesClient.js`:

| Caso | Valor de retorno |
|---|---|
| Éxito | `{ ok: true, data: { whistleId, debtId, debtorName, sentAt, nextAvailableAt } }` |
| Error HTTP | `{ ok: false, status, detail, code, retryAfterSeconds }`. `retryAfterSeconds` sale de `properties.retryAfterSeconds` o, si falta, de la cabecera `Retry-After` |
| Error de red | `{ ok: false, network: true }` |
