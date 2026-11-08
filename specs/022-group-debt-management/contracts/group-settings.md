# Contract: Group Settings (primary currency and debt due days)

**Feature**: Group Debt Management (022)
**Related Specs**: FR-009 (primary currency), FR-005 / D7 (overdue debts), User Stories 1 and 9
**Created**: 2026-11-07

## Purpose

Document the two group-level fields that 022 adds to the existing group endpoints of 010 and 020. Both are **additive**: payloads and responses that do not mention them keep working unchanged.

| Field | Type | Meaning |
|-------|------|---------|
| `primaryCurrency` | `ARS` \| `USD` \| `EUR` | The group's currency: the currency of balances, the settlement proposal and the converted amounts. Defaults to `ARS` |
| `debtDueDays` | integer 0–3650 or `null` | Days after the expense date from which a still-`PENDING` debt becomes `OVERDUE`. `null` (or empty in the UI) = never overdue |

## `primaryCurrency`

### `POST /api/groups` (extended)

- Optional `primaryCurrency` in the body (case-insensitive, trimmed). Omitted or blank → `ARS`.
- Any value other than `ARS`, `USD` or `EUR` → `400` with `properties.fields.primaryCurrency = "La moneda no es válida"`; the group is **not** created.
- The `201` response carries `primaryCurrency`.

### `GET /api/groups/{groupId}` (extended)

- The detail carries `primaryCurrency` (existing groups are `ARS`, migration V17).
- The list `GET /api/groups` does not carry it.
- The currency is chosen at creation; the edit endpoint does not change it.

## `debtDueDays`

### `GET /api/groups/{groupId}` (extended)

The detail carries `debtDueDays` (`null` while not configured).

### `PUT /api/groups/{groupId}` (extended)

Partial update, like the rest of the endpoint: **an omitted key keeps the stored value**; an explicit `null` clears it.

```json
{ "debtDueDays": 15 }
```

| Rule | Result |
|------|--------|
| Integer between `0` and `3650` | Saved; the response is the updated group detail with `debtDueDays` |
| `null` | Saved as "never overdue" |
| Key omitted | Stored value unchanged |
| Negative integer, greater than `3650` | `400` with `properties.fields.debtDueDays = "Los días para el vencimiento tienen que ser un número entero entre 0 y 3650"`; the stored value is unchanged |
| Caller is an active member but **not an administrator** (even with "Pueden editar datos del grupo") | `403` `"Solo un administrador puede configurar el vencimiento de las deudas"`; nothing is saved |
| Non-member / unknown group | Neutral `404` `"No existe el grupo solicitado o no sos miembro"` |
| No session | `401` |

Only administrators may configure it (plan.md, "Ajustes del grupo").

### Effect

A scheduled job (`OVERDUE_CRON`, daily by default) moves to `OVERDUE` the `PENDING` debts of groups with `debtDueDays` whose expense date plus `debtDueDays` days has passed. It is idempotent, never touches `PARTIALLY_PAID`, `PAYMENT_REPORTED`, `PAID` or `VERIFIED`, and an `OVERDUE` debt still counts in the balances and can be paid. It records a `DEBT_OVERDUE` history event without an actor ("Sistema" in the UI).

### UI

`GroupEditForm` shows "Días para el vencimiento de las deudas" to administrators with the current value; an empty field means "never overdue" (`null`).
