# Contract: Expense Split, Edit, and Delete

**Feature**: Group Debt Management (022)
**Related Specs**: US1, US2, FR-001, FR-002, clarifications Q1 (rounding) and Q4 (edit/delete)
**Created**: 2026-11-07

## Purpose

Define the additive contract for:

1. choosing how a group expense is split: `EQUAL`, `CUSTOM` or `PERCENTAGE`;
2. editing a personal expense (no participants other than the payer);
3. deleting a personal expense.

The current 017 endpoints and responses keep working unchanged when `splitMethod` is omitted (it defaults to `EQUAL`). No existing route, field or response is removed or renamed; all changes are additive.

## Common conventions

- Authentication: `amano_auth` cookie. Without a session → `401`.
- Access: only active members of the group. Everybody else gets the neutral `404` `"No existe el grupo solicitado o no sos miembro"`.
- Amounts: canonical decimal text with a dot and two decimals (`"3333.33"`).
- Money math uses `BigDecimal` with scale 2 and `compareTo`; never floating point.
- Errors: `ProblemDetail`. Validation errors include `properties.fields` with one voseo message per field.
- `Idempotency-Key` is still required and valid only for `POST` (creation). `PATCH` and `DELETE` do not use an idempotency key.

---

## 1. Split method on creation (extended `POST`)

### `POST /api/groups/{groupId}/expenses`

The 017 payload keeps working as-is. Two optional additions are accepted:

- `splitMethod`: `EQUAL` | `CUSTOM` | `PERCENTAGE`. Omitted → `EQUAL`.
- per-participant `amount` (for `CUSTOM`) or `percentage` (for `PERCENTAGE`).

### Payload shape

```json
{
  "amount": "10000.00",
  "currency": "ARS",
  "date": "2026-11-05",
  "description": "Súper",
  "deviceTimeZone": "America/Argentina/Buenos_Aires",
  "payerUserId": 7,
  "splitMethod": "CUSTOM",
  "participants": [
    { "type": "REGISTERED", "userId": 7, "amount": "3000.00" },
    { "type": "REGISTERED", "userId": 15, "amount": "6000.00" },
    { "type": "SIMULATED", "simulatedProfileId": 4, "amount": "1000.00" }
  ]
}
```

For `PERCENTAGE`, replace `amount` with `percentage` (for example `"30"`, `"60"`, `"10"`). For `EQUAL`, participants carry no `amount` or `percentage`.

| Field | Type | Required | Notes |
|-------|------|----------|-------|
| `splitMethod` | text | No | `EQUAL` (default when omitted), `CUSTOM` or `PERCENTAGE` |
| `participants[].amount` | text | For `CUSTOM` | Canonical amount with two decimals; `> 0` |
| `participants[].percentage` | text/number | For `PERCENTAGE` | Decimal `> 0`; the sum of all participants must be exactly `100` |

### Rules

- The payer always participates and cannot be removed (017 FR-004).
- Every participant (including the payer) supplies its own `amount` or `percentage` in the corresponding method.
- If the participant list contains nobody other than the payer, the expense is **personal**: no debts are generated and `splitMethod` is not applied.
- The payer and all registered participants must be active members of the group; simulated participants must belong to the group (017 D11).
- Shared expenses require at least one participant other than the payer.
- `CUSTOM`: each participant amount must be `> 0`; the sum of all amounts must equal the expense total **exactly**.
- `PERCENTAGE`: each percentage must be `> 0`; the sum of all percentages must be exactly `100`.
- Applying `amount`/`percentage` to `EQUAL`, or mixing both per-participant fields, is rejected.
- Currency conversion (022 D8) is applied **once to the total** before splitting; `CUSTOM` and `PERCENTAGE` operate on the converted total, in the group currency.

### Rounding (Q1, applies to `PERCENTAGE`; `EQUAL` keeps the same rule)

Each non-payer share is calculated in cents **rounding down** (floor). The remainder, if any, is absorbed by the payer: the payer's share is `total - sum(other shares)`.

Reason: no non-payer participant ever pays more than their exact share because of rounding. The sum of the shares is always exactly the total.

Examples (payer last):

- `100.00` among 3 → `33.33 / 33.33 / 33.34` (payer absorbs the remaining cent).
- `0.05` among 3 → `0.01 / 0.01 / 0.03` (payer absorbs the remaining two cents).

`PERCENTAGE` example with total `100.00`: percentages `33.33 / 33.33 / 33.34` → non-payers `33.33 + 33.33`, payer `100.00 - 66.66 = 33.34`.

### Validation messages (voseo, `ProblemDetail.properties.fields`)

| Field / rule | Message |
|--------------|---------|
| `splitMethod` not one of the three values | `El método de reparto debe ser EQUAL, CUSTOM o PERCENTAGE` |
| `CUSTOM`, participant amount missing, non-positive or not canonical | `Cada importe personalizado tiene que ser mayor que 0` |
| `CUSTOM`, sum differs from the total | `Los importes personalizados deben sumar exactamente el total del gasto` |
| `PERCENTAGE`, participant percentage missing or `<= 0` | `Cada porcentaje tiene que ser mayor que 0` |
| `PERCENTAGE`, sum differs from 100 | `Los porcentajes deben sumar exactamente 100` |
| `EQUAL` with `amount`/`percentage` present | `El reparto en partes iguales no acepta importes ni porcentajes por participante` |
| `amount`/`percentage` sent together on the same participant | `Elegí importe o porcentaje, no ambos` |
| payer is not an active member | `Quien pagó tiene que ser miembro del grupo` (existing 017 message) |
| participant is not a group member | `Algún participante no pertenece al grupo` (existing 017 message) |
| `EQUAL`, total does not reach one cent per participant | `El monto no alcanza para dividirlo entre N participantes` (existing 017 message, `N` = participant count) |

### Status codes

| Situation | Status |
|-----------|--------|
| New expense created | `201` with `Location` |
| Idempotent replay (same key and payload) | `200` |
| Validation error | `400` with `properties.fields` |
| Payer not allowed when `members_can_generate_expenses_for_others = false` | `403` `"Solo podés registrar gastos pagados por vos"` |
| Non-member or expense of another group | neutral `404` |
| Same key with another payload | `422` (existing 017 behavior) |
| Quote unavailable for a foreign currency | `503` `"No se pudo obtener la cotización; reintente después o registre en moneda del grupo"` |

### Response (additive)

`GroupExpenseResponse` keeps every existing field and adds `splitMethod`. Each `shares[].amount` is the computed share; the original percentage is not required in the response.

---

## 2. Editing a personal expense

### `PATCH /api/groups/{groupId}/expenses/{expenseId}`

Partial update of a **personal** group expense (no participants other than the payer and no associated debts).

**Editable fields**

| Field | Notes |
|-------|-------|
| `amount` | Canonical amount `> 0`; revalidated with the 007 rules |
| `currency` | `ARS`/`USD`/`EUR`; if it differs from the group currency, conversion is recalculated (D8) |
| `date` | Not future (existing `ExpenseDateValidator`) |
| `description` | Optional; empty/omitted clears it |

**Not editable**: `payerUserId`, `participants`, `splitMethod`, `createdByUserId`, `idempotencyKey`.

**Request example**

```json
{
  "amount": "2500.00",
  "description": "Ajuste de monto"
}
```

- At least one editable field must be present; otherwise `400` `"Enviá al menos un campo para editar"`.
- If the expense has any participant other than the payer (or any associated debt), the request is rejected with `409`:

```json
{
  "title": "Conflicto",
  "status": 409,
  "detail": "No se permite editar un gasto que ya tiene participantes",
  "properties": { "code": "EXPENSE_HAS_PARTICIPANTS" }
}
```

- A failed currency conversion answers `503` and persists no change.
- Success answers `200` with the updated `GroupExpenseResponse`.
- Validation errors answer `400` with `properties.fields`; non-members answer the neutral `404`.

**Who may edit (proposed, pending confirmation)**: the member who registered the expense (`createdByUserId`), the payer (`payerUserId`), or a group administrator.

---

## 3. Deleting a personal expense

### `DELETE /api/groups/{groupId}/expenses/{expenseId}`

Deletes a **personal** group expense (no participants other than the payer) that has **no associated debts**.

- Success: `204 No Content`. The expense and its personal share are removed.
- If any debt references the expense (in any status, including settled), the request is rejected with `409`:

```json
{
  "title": "Conflicto",
  "status": 409,
  "detail": "No se puede eliminar un gasto con deudas asociadas",
  "properties": { "code": "EXPENSE_HAS_DEBTS" }
}
```

- If the expense has participants other than the payer but no debts (defensive case), it also answers `409` with `"No se permite eliminar un gasto que ya tiene participantes"`.
- Balances are derived from debts (`BalanceCalculator`, D4); there is no stored balance table to rewrite. After a successful personal-expense deletion the next balance query simply reflects the removed expense (which contributed no debts).
- Non-members answer the neutral `404`; anonymous users answer `401`.

**Who may delete (proposed, pending confirmation)**: the member who registered the expense (`createdByUserId`), the payer (`payerUserId`), or a group administrator.

---

## Compatibility and migration

- `POST /api/groups/{groupId}/expenses` and `GET /api/groups/{groupId}/expenses/{expenseId}` of 017 remain valid without changes when `splitMethod` is omitted.
- New request fields are optional; new response fields are additive.
- No existing migration is edited. A new migration `V21__add_group_expense_split_method.sql` adds:

```sql
ALTER TABLE group_expenses
    ADD COLUMN split_method VARCHAR(10) NOT NULL DEFAULT 'EQUAL';

ALTER TABLE group_expenses
    ADD CONSTRAINT ck_group_expenses_split_method
    CHECK (split_method IN ('EQUAL', 'CUSTOM', 'PERCENTAGE'));
```

Existing rows stay `EQUAL`, which matches the 017 behavior.

## Open product decisions (not defined by the spec)

These are intentionally **not** invented here and must be confirmed before implementing:

1. Who may edit/delete: the proposal above (creator, payer or group admin) is not stated by the spec.
2. Whether a simulated profile may carry a custom amount or a percentage of its own, or only participate in `EQUAL`.
3. Whether the 007 personal `Expense` (outside groups, without a `participants` concept) also gets edit/delete endpoints under this contract.
4. Whether the payer of a personal expense may be changed through `PATCH` (this contract says no).
5. Concurrency control for `PATCH`/`DELETE` (for example `If-Match`/ETag) is not defined by the spec.
