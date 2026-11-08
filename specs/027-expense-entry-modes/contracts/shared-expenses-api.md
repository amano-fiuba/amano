# Contract: Gasto de grupo con datos de 007 y gasto con amigo (027)

**Requisitos**: FR-020, FR-030 a FR-033 | **Research**: R4, R5 | **Base**: [017 group-expenses-api](../../017-debt-states/contracts/group-expenses-api.md) con los agregados de 022/024

## `POST /api/groups/{id}/expenses` (modificado, aditivo)

Cuerpo: el de 017/022 más tres propiedades **obligatorias**:

| Propiedad | Tipo | Valores | Error si falta o es inválida |
|---|---|---|---|
| `category` | string | Códigos de `ExpenseCategory` (007) | `400` en `category`, mensaje de 007 |
| `type` | string | `ONE_TIME`, `RECURRING` | `400` en `type`, mensaje de 007 |
| `paymentMethod` | string | Códigos de `PaymentMethod` (007) | `400` en `paymentMethod`, mensaje de 007 |

`frequency` sigue siendo una propiedad desconocida (`400` "No se permite informar este campo"): un gasto compartido `RECURRING` es solo informativo.

**Compatibilidad**: es un cambio que exige los campos nuevos. El único cliente es el frontend del repo, que se actualiza en el mismo release (Principio VII). Se documenta en el PR.

Respuesta (`GroupExpenseResponse`) suma `category`, `type`, `paymentMethod` (pueden ser `null` en gastos anteriores a 027) y `friend` (`null` en gastos de grupo).

`PATCH` de gasto personal (022 T077): acepta además `category`, `type` y `paymentMethod`.

## `POST /api/users/me/friends/{friendId}/expenses` (nuevo)

Registra un gasto compartido entre el usuario autenticado y un amigo.

- **Autenticación**: cookie `amano_auth`. Sin sesión → `401`.
- **Cabecera** `Idempotency-Key`: obligatoria, igual que en grupos.

### Cuerpo

```json
{
  "amount": "10000.00",
  "currency": "ARS",
  "date": "2026-11-08",
  "description": "Cena",
  "deviceTimeZone": "America/Argentina/Buenos_Aires",
  "category": "FOOD",
  "type": "ONE_TIME",
  "paymentMethod": "DEBIT_CARD",
  "payerUserId": 7,
  "splitMethod": "EQUAL",
  "participants": [
    { "type": "REGISTERED", "userId": 7 },
    { "type": "REGISTERED", "userId": 12 }
  ]
}
```

Reglas (además de las de monto, fecha, descripción, moneda y reparto de 007/022/024):

| Regla | Error |
|---|---|
| `friendId` no es amigo del usuario (incluye que haya dejado de serlo, FR-033) | `404` neutral "No existe el amigo solicitado" |
| `payerUserId` distinto del usuario y del amigo | `400` en `payerUserId` |
| `participants` distinto de exactamente {usuario, amigo}, o con `SIMULATED` | `400` en `participants` |
| `category`, `type`, `paymentMethod` | Igual que en grupos |

Moneda: **no hay conversión**. La deuda queda en la moneda del gasto; la respuesta trae `rateSource = "SAME_CURRENCY"`, `exchangeRate = "1"` y `convertedAmount = amount`.

Efecto: se crea el gasto (`groupId = null`, `friend` = el amigo), sus dos partes y, para quien no pagó, una deuda `PENDING` hacia quien pagó. Desde ese momento `GET /api/users/me/friends/{friendId}/deletion-eligibility` informa la deuda directa y `DELETE /api/users/me/friends/{friendId}` responde el bloqueo por deuda directa ya definido en 016 (`PendingDirectDebtException`).

### Respuesta `201 Created` / `200 OK` (reenvío con la misma clave)

`GroupExpenseResponse` con `groupId: null` y:

```json
"friend": { "id": 12, "firstName": "Manu", "lastName": "Pato", "avatarUrl": "..." }
```

`Location: /api/users/me/friends/{friendId}/expenses/{expenseId}`.

### Otros códigos

| Caso | Código |
|---|---|
| Validación | `400` con `fields` |
| No amigo / usuario inexistente | `404` neutral |
| Misma `Idempotency-Key` con otro cuerpo | `422` `IDEMPOTENCY_KEY_REUSED` |

## `GET /api/users/me/friends/{friendId}/expenses/{expenseId}` (nuevo)

Devuelve el gasto con amigo (misma forma que arriba) si el usuario es el creador o el amigo; si no, `404` neutral. Lo usa la pantalla de confirmación después de guardar. Listar, editar, borrar y pagar gastos con amigo queda fuera de esta spec.

## Endpoints existentes que no deben cambiar de comportamiento

Las deudas directas (`group_id = null`) **no** aparecen en: saldos y liquidación del grupo, deudas pendientes del grupo, pagos y validación de pagos, vencimiento automático, bloqueo de abandonar grupo (020) e historial de grupo (014).
