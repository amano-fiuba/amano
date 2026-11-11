# Contrato REST: Gastos de grupo y deudas

**Feature**: `017-debt-states` | **Spec**: [spec.md](../spec.md) | **Plan**: [plan.md](../plan.md)

Endpoints nuevos. Además, 017 cambia el **comportamiento** (no la forma) de tres endpoints de 010: ver "Endpoints de 010 afectados".

Convenciones comunes (004/007/010):

- **Autenticación**: cookie `amano_auth`. Sin sesión → `401`.
- **Acceso**: solo miembros activos del grupo. Para el resto, `404` neutral con "No existe el grupo solicitado o no sos miembro".
- **Errores**: `ProblemDetail`. Los de validación traen `properties.fields` con un mensaje en español por campo.
- **Montos**: texto decimal canónico con punto y dos decimales (`"3333.33"`).

## `POST /api/groups/{id}/expenses`

Registra un gasto de grupo dividido en partes iguales y crea sus deudas (FR-001..FR-015).

### Cabeceras

| Cabecera | Obligatoria | Notas |
|----------|-------------|-------|
| `Idempotency-Key` | Sí | UUID. Inválida → `400` en `idempotencyKey` (mensaje de 007) |
| `Content-Type` | Sí | `application/json` |

### Cuerpo

```json
{
  "amount": "10000.00",
  "currency": "ARS",
  "date": "2026-11-05",
  "description": "Súper",
  "deviceTimeZone": "America/Argentina/Buenos_Aires",
  "payerUserId": 7,
  "participants": [
    { "type": "REGISTERED", "userId": 7 },
    { "type": "REGISTERED", "userId": 15 },
    { "type": "SIMULATED", "simulatedProfileId": 4 }
  ]
}
```

| Campo | Tipo | Obligatorio | Validación (mensaje) |
|-------|------|-------------|----------------------|
| `amount` | texto | Sí | Reglas y mensajes de `ExpenseAmountNormalizer` (007). Además, al menos 0,01 por participante: "El monto no alcanza para dividirlo entre {n} participantes" |
| `currency` | texto | Sí | `ARS`, `USD` o `EUR` (mensajes de 007) |
| `date` | `YYYY-MM-DD` | Sí | `ExpenseDateValidator` (007) |
| `description` | texto | No | `ExpenseDescriptionNormalizer` (007), hasta 200 caracteres |
| `deviceTimeZone` | texto | No | Como en 007 |
| `payerUserId` | número | Sí | Miembro registrado activo: "Quien pagó tiene que ser miembro del grupo" |
| `participants` | arreglo | Sí, ≥ 1 | Ver abajo |

Cada participante:

| Campo | Cuándo |
|-------|--------|
| `type` | Siempre: `REGISTERED` o `SIMULATED` |
| `userId` | `REGISTERED`: miembro registrado activo del grupo |
| `simulatedProfileId` | `SIMULATED`: perfil simulado del grupo |

Errores en `participants` (`properties.fields.participants`):

- Vacío o solo el pagador: "Elegí al menos un participante además de quien pagó".
- Repetido: "Hay participantes repetidos".
- No pertenece al grupo, ex miembro o tipo inválido: "Algún participante no pertenece al grupo".

Las propiedades desconocidas del cuerpo se rechazan con `400` en ese mismo campo ("No se permite informar este campo", el mensaje de 007 para propiedades reservadas).

### Respuesta `201 Created` (nuevo) o `200 OK` (reenvío con la misma clave)

Cabecera `Location: /api/groups/{id}/expenses/{expenseId}`. El cuerpo es el mismo que el de `GET` (abajo).

### Otros códigos

| Caso | Código | `properties` |
|------|--------|--------------|
| Validación | `400` | `fields` |
| MEMBER con `members_cannot_make_expenses = true` | `403` | Mensaje "No podés registrar gastos en este grupo" |
| MEMBER con pagador ≠ sí mismo y `members_can_generate_expenses_for_others = false` | `403` | Mensaje "Solo podés registrar gastos pagados por vos" |
| No miembro o grupo inexistente | `404` | — |
| Misma `Idempotency-Key` con otro cuerpo | `422` | `code: IDEMPOTENCY_KEY_REUSED` |

## `GET /api/groups/{id}/expenses/{expenseId}`

Detalle de un gasto con sus partes y deudas (FR-020).

### Respuesta `200 OK`

```json
{
  "id": 12,
  "groupId": 3,
  "amount": "10000.00",
  "currency": "ARS",
  "date": "2026-11-05",
  "description": "Súper",
  "payer": { "userId": 7, "name": "Ana López" },
  "createdBy": { "userId": 7, "name": "Ana López" },
  "createdAt": "2026-11-05T15:20:00Z",
  "shares": [
    { "position": 0, "type": "REGISTERED", "userId": 7, "simulatedProfileId": null, "name": "Ana López", "amount": "3333.34", "isPayer": true },
    { "position": 1, "type": "REGISTERED", "userId": 15, "simulatedProfileId": null, "name": "Juan Pérez", "amount": "3333.33", "isPayer": false },
    { "position": 2, "type": "SIMULATED", "userId": null, "simulatedProfileId": 4, "name": "Abuela", "amount": "3333.33", "isPayer": false }
  ],
  "debts": [
    {
      "id": 31,
      "debtor": { "type": "REGISTERED", "userId": 15, "simulatedProfileId": null, "name": "Juan Pérez" },
      "creditor": { "userId": 7, "name": "Ana López" },
      "amount": "3333.33",
      "paidAmount": "0.00",
      "outstanding": "3333.33",
      "currency": "ARS",
      "status": "PENDING"
    },
    {
      "id": 32,
      "debtor": { "type": "SIMULATED", "userId": null, "simulatedProfileId": 4, "name": "Abuela" },
      "creditor": { "userId": 7, "name": "Ana López" },
      "amount": "3333.33",
      "paidAmount": "0.00",
      "outstanding": "3333.33",
      "currency": "ARS",
      "status": "PENDING"
    }
  ]
}
```

- `shares` se ordena por `position`, y `debts` por el `position` de su parte.
- `name` es la copia guardada al momento del gasto. `simulatedProfileId` puede ser `null` si el perfil se borró.
- `status` ∈ `PENDING`, `PARTIALLY_PAID`, `PAID` (data-model.md §Estados).

### Otros códigos

| Caso | Código |
|------|--------|
| No miembro, grupo inexistente o gasto de otro grupo | `404` neutral |

## Endpoints de 010 afectados (sin cambio de forma)

Desde 017, el `403 PENDING_DEBTS` que 010 ya documentaba se produce de verdad:

| Endpoint | Se bloquea cuando… | Mensaje (de 010) |
|----------|--------------------|------------------|
| `POST /api/groups/{id}/leave` | El usuario tiene deudas sin saldar en el grupo como deudor o acreedor | "No podés abandonar el grupo: tenés saldos pendientes con otros miembros" |
| `DELETE /api/groups/{id}/members/{userId}` | Ídem para el miembro quitado | "No se permite la baja: la persona tiene deudas pendientes con otros miembros del grupo" |
| `DELETE /api/groups/{id}/simulated-profiles/{profileId}` | El perfil tiene deudas sin saldar como deudor | Ídem |

Respuesta: `403`, `title` "Operación bloqueada por deudas pendientes", `properties.code` `PENDING_DEBTS` (handler existente).

`GET /api/groups/{id}` no cambia de forma. Desde 017, `expensesCount` informa la cantidad real de gastos del grupo (FR-027), cuando antes era siempre 0. La UI deduce los permisos de gasto de `currentUserRole` y `permissions` (research.md D8).

## Historial (014)

Cada `POST` exitoso que crea un gasto agrega una entrada `EXPENSE_CREATED` a `GET /api/groups/{id}/activity`, con el formato de 014 (`expenseId`, `amount`, `currency`, `description`) y el creador como `actor`. Un reenvío idempotente (200) no agrega otra.
