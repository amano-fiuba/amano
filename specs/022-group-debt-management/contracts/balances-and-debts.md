# Contract: Balances and Debts

**Feature**: Group Debt Management (022)
**Related Specs**: SC-001, FR-003, FR-007
**Created**: 2026-11-06

## Purpose

Define the contract for querying group member balances and pending debts. Balances are derived purely from debt records (source of truth); no separate balance table exists.

## Endpoints

### `GET /api/groups/{groupId}/balances`

**Description**: Get net balance for each group member, including simulated persons, grouped by currency so legacy debts in different currencies are never added together.

**Response**: `GroupBalancesResponse`

| Field | Type | Description |
|-------|------|-------------|
| `groupId` | `Long` | Group identifier |
| `primaryCurrency` | `Currency` | Primary currency of the group (`ARS`, `USD`, `EUR`) |
| `currencies` | `List<CurrencyBalances>` | Net balances per currency |

**`CurrencyBalances`**:

| Field | Type | Description |
|-------|------|-------------|
| `currency` | `Currency` | Currency of the balances |
| `total` | `BigDecimal` | Sum of all member net balances in this currency (always `0.00`) |
| `balances` | `List<MemberBalance>` | Net balance for each member |

**`MemberBalance`**:

| Field | Type | Description |
|-------|------|-------------|
| `memberId` | `Long` | Member identifier (user id or simulated profile id) |
| `memberName` | `String` | Display name of the member |
| `memberType` | `ParticipantType` | `REGISTERED` or `SIMULATED` |
| `netBalance` | `BigDecimal` | Net amount: positive = is owed, negative = owes, zero = settled |

**Guarantee**: `Σ(netBalance for all members) == 0` (SC-001)

### `GET /api/groups/{groupId}/debts/pending`

**Description**: Get the caller's open (non-settled) debts in the group, with origin and conversion details.

**Access & Visibility**:
- Requires an authenticated active member of the group; non-members receive the neutral 404 (`"No existe el grupo solicitado o no sos miembro"`).
- Each member sees the open debts in which they participate as **debtor** (`debtorUserId == callerId`) or **creditor** (`creditorUserId == callerId`). Because simulated profiles cannot hold accounts, their debts appear to the member who is their creditor.

**Response**: `List<PendingDebtResponse>`

| Field | Type | Description |
|-------|------|-------------|
| `debtId` | `Long` | Debt identifier |
| `debtorName` | `String` | Name of who owes (registered member or simulated profile) |
| `creditorName` | `String` | Name of who is owed |
| `amount` | `BigDecimal` | Total amount owed (scale 2) |
| `paidAmount` | `BigDecimal` | Amount already paid (scale 2) |
| `outstanding` | `BigDecimal` | `amount - paidAmount` (what is still pending, scale 2) |
| `status` | `DebtStatus` | Current debt state (`PENDING`, `PARTIALLY_PAID`, `PAYMENT_REPORTED`, `OVERDUE`) |
| `expenseId` | `Long` | Origin expense identifier |
| `shareId` | `Long` | Origin share identifier |
| `currency` | `Currency` | Original debt currency (`ARS`, `USD`, `EUR`) |
| `convertedAmount` | `BigDecimal` | Debt amount converted to the group's primary currency when the origin expense has a stored conversion; `null` otherwise |
| `convertedCurrency` | `Currency` | Group's primary currency when `convertedAmount` is present; `null` otherwise |
| `debtorUserId` | `Long` | User id of the debtor; `null` when the debtor is a simulated profile. Additive (T074): lets the UI tell whether the caller owes (`Le debés a …`) or is owed (`… te debe`) |
| `creditorUserId` | `Long` | User id of the creditor (always a registered member). Additive (T074) |

**Inclusion rule**: Only debts in the four states that still contribute to balances are included: `PENDING`, `PARTIALLY_PAID`, `PAYMENT_REPORTED`, and `OVERDUE`. Debts in settled states (`PAID` and `VERIFIED`) are excluded.

**Partial payments** (decision of 2026-11-07): every open debt contributes and shows its *outstanding balance* `amount − paidAmount`, so a confirmed partial payment lowers the debtor's and the creditor's balances by the amount paid (and the debt becomes `PARTIALLY_PAID`). A `PAYMENT_REPORTED` payment does not change `paidAmount` until it is confirmed, so it does not lower the balances yet.

## Balance Calculation (Pure Function)

The group balance calculation is a pure function (`BalanceCalculator`) that receives the list of all debts and returns member balances per currency:

```
Function calculate(debts):
    Initialize map<Currency, map<MemberRef, BigDecimal>> balances = zero
    
    For each debt in debts:
        If debt.status in {PENDING, PARTIALLY_PAID, PAYMENT_REPORTED, OVERDUE}:
            // Debtor (REGISTERED or SIMULATED) owes amount - paidAmount
            balances[debt.currency][debtorRef(debt)] -= debt.getOutstanding()
            // Creditor (REGISTERED) is owed amount - paidAmount
            balances[debt.currency][creditorRef(debt)] += debt.getOutstanding()
        // Debts in PAID or VERIFIED are settled; no contribution to balance
    
    Return balances
```

**Invariants**:
- The sum of all `netBalance` values is exactly `0.00`
- Simulated persons contribute to balances via their debt records (`debtorSimulatedProfileId`)
- `PAID` and `VERIFIED` debts contribute `0.00` to net balance
- `PENDING`, `PARTIALLY_PAID`, `PAYMENT_REPORTED`, and `OVERDUE` debts contribute `amount - paidAmount` (`getOutstanding()`) to net balance and appear in `GET /api/groups/{groupId}/debts/pending`

## Member Balance Representation

| memberType | memberId source | netBalance meaning |
|------------|-----------------|-------------------|
| `REGISTERED` | `users` table primary key | positive = member is owed money; negative = member owes money; `0.00` = fully settled |
| `SIMULATED` | `simulated_profiles` table primary key | positive = simulated person is owed (net credit); negative = simulated person owes (net debt) |
