# Contract: Liquidation Proposal

**Feature**: Group Debt Management (022)
**Related Specs**: FR-004, SC-002
**Created**: 2026-11-06

## Purpose

Define the contract for the advisory debt liquidation proposal of a group. The proposal compensates cross-debts and suggests a **reduced** set of transfers that settles all members: the total transferred equals the sum of the positive net balances and the count of transfers is ≤ (members with non-zero balance − 1). It is read-only: it never creates payments or changes debts.

**Updated**: 2026-11-07 (aligned with the implementation: per-currency proposals, member types and `informative` marker).

## Request

`GET /api/groups/{groupId}/settlement-proposal` — no body and no parameters. Only active members of the group (neutral `404` `"No existe el grupo solicitado o no sos miembro"` for the rest, `401` without session). A call records a `LIQUIDATION_PROPOSED` history event, at most one per group and day.

## Response

### `SettlementProposalResponse`

| Field | Type | Description |
|-------|------|-------------|
| `groupId` | `Long` | The group |
| `primaryCurrency` | `Currency` | The group's primary currency |
| `proposals` | `List<CurrencyProposal>` | One proposal per currency that has non-zero balances |

### `CurrencyProposal`

| Field | Type | Description |
|-------|------|-------------|
| `currency` | `Currency` | Currency of these transfers (amounts of different currencies are never added together) |
| `transfers` | `List<TransferView>` | Proposed transfers, in a stable order |
| `summary` | `TransferSummary` | Informational totals |

### `TransferView`

| Field | Type | Description |
|-------|------|-------------|
| `fromMemberId` / `fromMemberName` / `fromMemberType` | `Long` / `String` / `REGISTERED` or `SIMULATED` | Who transfers |
| `toMemberId` / `toMemberName` / `toMemberType` | `Long` / `String` / `REGISTERED` or `SIMULATED` | Who receives |
| `amount` | `BigDecimal` | Amount to transfer (always > 0, scale 2) |
| `informative` | `boolean` | `true` when a side is a simulated profile (it has no account to execute the transfer) |

### `TransferSummary`

| Field | Type | Description |
|-------|------|-------------|
| `totalTransferred` | `BigDecimal` | Sum of all transfer amounts |
| `totalNetBalances` | `BigDecimal` | Sum of the positive net balances (what has to move to settle the group) |
| `membersSettled` | `Integer` | Number of members with a non-zero balance that the proposal leaves at zero |
| `transferCount` | `Integer` | Number of transfers (guaranteed ≤ `nonZeroMembers − 1`) |

## Guarantees

1. **Balance consistency**: after applying all transfers, every member's balance is zero (and the balances of a currency add up to zero).
2. **Transfer count**: the number of transfers is ≤ (members with non-zero balance − 1).
3. **Total**: `totalTransferred == totalNetBalances` (the sum of the positive balances, **not** the gross sum of debts, which is larger when there are cross-debts).
4. **No history modification**: the proposal does not modify expenses, debts or payments.
5. **Not the absolute minimum**: the minimum number of transfers is an NP-hard problem and is not searched; the greedy algorithm gives the practical bound n − 1.

## Behavior rules

1. **Members**: registered members and simulated profiles of the group are included in the calculation.
2. **Debts included**: the four open states that still count in the balance — `PENDING`, `PARTIALLY_PAID`, `PAYMENT_REPORTED` and `OVERDUE`. `PAID` and `VERIFIED` (settled) are never included. `PAYMENT_REPORTED` is included because it is not settled until an administrator confirms. Each debt enters with its outstanding balance (`amount − paidAmount`), so confirmed partial payments reduce the proposed transfers.
3. **Simulated persons** are included with their balance. A transfer that involves a simulated profile is returned with `informative = true` (the group can use it to settle the balance, but the simulated person cannot execute it; an administrator registers the payments of simulated profiles).
4. **Read-only and advisory**: payments are registered on direct debts, not on proposed transfers.
5. **Currencies**: there is one proposal per debt currency; nothing is converted. The converted amount shown elsewhere in the UI is informational.
6. **Determinism**: at each step the planner takes the largest creditor and the largest debtor (ties by member id), transfers the smaller of the two amounts and repeats.

## Example

```
Group: Alice, Bob, Claire (ARS)
Debts: Alice owes Bob 10.00, Bob owes Claire 5.00, Claire owes Alice 3.00

Net balances: Alice −7.00, Bob +5.00, Claire +2.00   (sum 0)

Greedy: largest creditor Bob (+5.00) and largest debtor Alice (−7.00) → Alice pays 5.00 to Bob (Alice −2.00).
        Largest creditor Claire (+2.00) and debtor Alice (−2.00) → Alice pays 2.00 to Claire.

{
  "groupId": 1,
  "primaryCurrency": "ARS",
  "proposals": [{
    "currency": "ARS",
    "transfers": [
      { "fromMemberId": 1, "fromMemberName": "Alice", "fromMemberType": "REGISTERED",
        "toMemberId": 2, "toMemberName": "Bob", "toMemberType": "REGISTERED",
        "amount": 5.00, "informative": false },
      { "fromMemberId": 1, "fromMemberName": "Alice", "fromMemberType": "REGISTERED",
        "toMemberId": 3, "toMemberName": "Claire", "toMemberType": "REGISTERED",
        "amount": 2.00, "informative": false }
    ],
    "summary": { "totalTransferred": 7.00, "totalNetBalances": 7.00, "membersSettled": 3, "transferCount": 2 }
  }]
}
```

Check: 2 transfers ≤ 3 − 1; total 7.00 = 5.00 + 2.00 (sum of positive balances); gross debts were 18.00.
