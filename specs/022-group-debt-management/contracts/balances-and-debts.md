# Contract: Balances and Debts

**Feature**: Group Debt Management (022)
**Related Specs**: SC-001, FR-003
**Created**: 2026-11-06

## Purpose

Define the contract for querying group member balances and pending debts. Balances are derived purely from debt records (source of truth); no separate balance table exists.

## Endpoints

### `GET /groups/{groupId}/balances`

**Description**: Get net balance for each group member, including simulated persons.

**Response**: `List<MemberBalance>`

| Field | Type | Description |
|-------|------|-------------|
| `memberId` | `Long` | Member identifier (user id or simulated profile id) |
| `memberName` | `String` | Display name of the member |
| `netBalance` | `BigDecimal` | Net amount: positive = is owed, negative = owes, zero = settled. Always expressed in the group's primary currency. |
| `memberType` | `ParticipantType` | REGISTERED or SIMULATED |

**Guarantee**: `Σ(netBalance for all members) == 0` (SC-001)

### `GET /groups/{groupId}/debts/pending`

**Description**: Get all pending (non-settled) debts in the group, with origin details.

**Response**: `List<PendingDebtDetail>`

| Field | Type | Description |
|-------|------|-------------|
| `debtId` | `Long` | Debt identifier |
| `debtorName` | `String` | Name of who owes |
| `creditorName` | `String` | Name of who is owed |
| `amount` | `BigDecimal` | Total amount owed |
| `paidAmount` | `BigDecimal` | Amount already paid |
| `outstanding` | `BigDecimal` | `amount - paidAmount` (what's still pending) |
| `status` | `DebtStatus` | Current debt state |
| `expenseId` | `Long` | Origin expense |
| `currency` | `Currency` | Debt currency |

**Inclusion rule**: Only debts with status `PENDIENTE` or `PAGO_INFORMADO` are included. Debts in `PAGADO`, `VERIFICADO`, or `VENCIDO` states are excluded from "pending" list (but VENCIDO debts still appear in balance calculations).

## Balance Calculation (Pure Function)

The group balance calculation is a pure function that receives the list of all debts and returns member balances:

```
Function computeGroupBalances(debts, groupMembers):
    Initialize map<memberId, BigDecimal> balances = zero
    
    For each debt in debts:
        If debt.status in {PENDIENTE, PAGO_INFORMADO, VENCIDO}:
            // Debtor owes amount - paidAmount
            balances.get(debt.debtorUserId) = balances.get(debt.debtorUserId).subtract(debt.getOutstanding())
            // Creditor is owed amount - paidAmount
            balances.get(debt.creditorUserId) = balances.get(debt.creditorUserId).add(debt.getOutstanding())
        // Debts in PAGADO/VERIFICADO are settled; no contribution to balance
    
    // Include simulated persons (they have debts but no user account;
    // their debts contribute to balance via debtorSimulatedProfileId)
    For each debt with debtorType == SIMULATED:
        balances.get(debt.debtorSimulatedProfileId) = 
            balances.get(debt.debtorSimulatedProfileId).subtract(debt.getOutstanding())
    
    Return map as List<MemberBalance>
```

**Invariants**:
- The sum of all `netBalance` values is exactly 0
- Simulated persons contribute to balances via their debt records
- `PAGADO` and `VERIFICADO` debts contribute 0 to net balance
- `VENCIDO` debts still contribute `amount - paidAmount` to net balance (they are still outstanding for payment purposes)

## Member Balance Representation

| memberType | memberId source | netBalance meaning |
|------------|-----------------|-------------------|
| REGISTERED | user table primary key | positive = member is owed money; negative = member owes money; 0 = fully settled |
| SIMULATED | simulated_profile table primary key | positive = simulated person is "owed" (net credit from group); negative = simulated person "owes" (net debt to group) |