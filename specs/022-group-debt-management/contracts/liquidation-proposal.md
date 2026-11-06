# Contract: Liquidation Proposal

**Feature**: Group Debt Management (022)
**Related Specs**: FR-004, SC-002
**Created**: 2026-11-06

## Purpose

Define the contract for generating a debt liquidation proposal within a group. The proposal compensates cross-debts and suggests the minimum set of transfers that settles all members, ensuring the total transferred equals the net amount needed and the count of transfers is ≤ (members with non-zero balance - 1).

## Request

### `LiquidationProposalRequest`

| Field | Type | Description | Required |
|-------|------|-------------|----------|
| `groupId` | `Long` | Identifier of the group to liquidate | Yes |

_No body parameters needed. This is a GET request: `GET /groups/{groupId}/settlement-proposal`_

## Response

### `LiquidationProposalResponse`

| Field | Type | Description |
|-------|------|-------------|
| `groupId` | `Long` | The group that was liquidated |
| `transfers` | `List<Transfer>` | The proposed transfers to settle all pending debts |
| `summary` | `TransferSummary` | Informational totals (only relevant if needed for display) |

### `Transfer`

| Field | Type | Description |
|-------|------|-------------|
| `fromMemberId` | `Long` | Member who transfers money |
| `toMemberId` | `Long` | Member who receives money |
| `amount` | `BigDecimal` | Amount to transfer (always > 0, 2 decimal places) |
| `originDebtIds` | `List<Long>` | Debt IDs that this transfer compensates |

### `TransferSummary`

| Field | Type | Description |
|-------|------|-------------|
| `totalTransferred` | `BigDecimal` | Sum of all transfer amounts |
| `totalNetBalances` | `BigDecimal` | Sum of positive net balances (what needs to be transferred to settle) |
| `membersSettled` | `Integer` | Number of members now at zero balance |
| `transferCount` | `Integer` | Number of transfers proposed (guaranteed ≤ `nonZeroMembers - 1`) |

## Guarantees (per Feature Specification)

1. **Balance Consistency**: After applying all transfers, the sum of all group members' balances equals zero.
2. **Transfer Count Guarantee**: The number of transfers proposed is ≤ (number of members with non-zero balance - 1).
3. **No History Modification**: The proposal does NOT modify the original expense history or debt records; it only proposes transfers.
4. **Minimum Absolute**: The absolute minimum number of transfers is NP-difficult (set cover problem); the greedy algorithm provides a practical upper bound of n-1.

## Behavior Rules

1. **Only group members** (registered or simulated) are included in the calculation.
2. **Only debts in states** that can be settled are included (PENDIENTE, PAGO_INFORMADO; VENCIDO debts are excluded from proposal but still appear as informational). ONLY debts that are NOT VENCIDO and NOT PAGADO/VERIFICADO are included.
3. **Simulated persons** are included in the balance calculation but cannot be `fromMemberId` or `toMemberId` in real transfers (they have no account for real transfers). They may appear as `fromMemberId` or `toMemberId` marked as `[informativo]`.
4. **The proposal is read-only**: It does not change any debt states, paid amounts, or expense records.
5. **Currency consistency**: All transfers are in the group's primary currency. If the group has multi-currency expenses, converted amounts are shown but transfers are in the group currency.

## Example

```
Group: 3 members (Alice, Bob, Claire)
Debts: Alice owes Bob $10, Bob owes Claire $5, Claire owes Alice $3

LiquidationProposalResponse:
{
  "transfers": [
    { "fromMemberId": "Alice", "toMemberId": "Bob", "amount": "7.00", "originDebtIds": ["debt_AliceBob"] },
    { "fromMemberId": "Claire", "toMemberId": "Bob", "amount": "2.00", "originDebtIds": ["debt_ClaireBob"] }
  ],
  "summary": {
    "totalTransferred": "9.00",
    "totalNetBalances": "9.00",
    "membersSettled": 3,
    "transferCount": 2
  }
}
```

**Verification**: After applying these transfers:
- Alice: -7 (to Bob) → net -7 (debitor)
- Bob: +7 (from Alice) + 2 (from Claire) → net +9 (creditor) ... wait, let me recalculate net balances:

Net balances before liquidation:
- Alice: -10 (owes Bob) + 3 (owed by Claire) = -7 (net debtor, owes 7)
- Bob: +10 (owed by Alice) - 5 (owes Claire) = +5 (net creditor, is owed 5)
- Claire: +5 (owed by Bob) - 3 (owed to Alice) = +2 (net creditor, is owed 2)
- Total: -7 + 5 + 2 = 0 ✓

Greedy algorithm:
1. Max positive: Bob (+5), Max negative: Alice (-7) → transfer min(5,7) = 5 from Alice to Bob → Alice: -2, Bob: 0
2. Max positive: Claire (+2), Max negative: Alice (-2) → transfer min(2,2) = 2 from Alice to Claire → Alice: 0, Claire: 0
3. Done.

Transfers: Alice→Bob $5, Alice→Claire $2. Total transferred: $7. Transfer count: 2 = 3 members - 1. ✓