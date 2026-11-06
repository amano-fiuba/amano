# Contract: Currency Conversion

**Feature**: Group Debt Management (022)
**Related Specs**: FR-009, User Story 9, Assumptions in spec.md
**Created**: 2026-11-06

## Purpose

Define the contract for converting amounts between currencies when an expense is in a currency different from the group's primary currency. Shows both the original amount and its converted equivalent, using the exchange rate from the transaction date.

## Request

### `CurrencyConversionRequest`

| Field | Type | Description | Required |
|-------|------|-------------|----------|
| `amount` | `BigDecimal` | The amount to convert | Yes; > 0 |
| `fromCurrency` | `Currency` | The currency of the amount | Yes |
| `toCurrency` | `Currency` | The target currency (usually the group's primary currency) | Yes |
| `date` | `LocalDate` | The transaction date for the exchange rate | Yes; not future |

## Response

### `CurrencyConversionResponse`

| Field | Type | Description |
|-------|------|-------------|
| `amount` | `BigDecimal` | The original amount (same as request) |
| `fromCurrency` | `Currency` | The original currency |
| `toCurrency` | `Currency` | The target currency |
| `convertedAmount` | `BigDecimal` | The amount in the target currency |
| `exchangeRate` | `BigDecimal` | The rate applied (fromCurrency → toCurrency) |
| `date` | `LocalDate` | The date the rate was effective from |
| `rateSource` | `String` | Identifier of the rate source (e.g., "OANDA-cache", "default-memo") |

## Exchange Rate Logic

### 1. Primary Flow (OANDA Adaptor)

1. Look up cached rate for `(fromCurrency, toCurrency, date)` key
2. If cache hit → use cached rate
3. If cache miss → fetch from OANDA API
4. Store result in cache with TTL of 24 hours
5. Return conversion

### 2. Fallback Flow (Service Unavailable)

If OANDA service does not respond after retries:

1. Check if there's a cached rate (even if stale, > 0 hours old)
2. If cached rate exists → use it with a warning note in `rateSource = "OANDA-cache-stale"`
3. If no cache → use group's default/memo rate
   - Document the default rate in the group configuration
   - Set `rateSource = "memo-default"`
   - Log the fallback for operator awareness

### 3. Same Currency

If `fromCurrency == toCurrency`:
- `exchangeRate` = 1
- `convertedAmount` = `amount`
- `rateSource` = "same-currency"

## Rate Caching Strategy

| Aspect | Design |
|--------|--------|
| **Cache Key** | `"{fromCurrency}-{toCurrency}-{date}"` (e.g., "USD-ARS-2026-01-15") |
| **TTL** | 24 hours (daily update sufficient for most use cases) |
| **Storage** | In-memory map (Caffeine) or Redis (if already project-configured) |
| **Eviction** | LRU (Least Recently Used) when cache exceeds configured size (e.g., 1000 entries) |
| **Cache Miss** | Fetch from external service; on success, store in cache |
| **Stale Data** | Rates ≥ 24h old should be recomputed; UI may show "rate updated" indicator |

## Example

```
Input: CurrencyConversionRequest(amount=50, fromCurrency=USD, toCurrency=ARS, date=2026-01-15)

OANDA rate USD→ARS on 2026-01-15: 18.50

Output: CurrencyConversionResponse(
  amount=50,
  fromCurrency=USD,
  toCurrency=ARS,
  convertedAmount=925.00,
  exchangeRate=18.50,
  date=2026-01-15,
  rateSource="OANDA"
)
```

## Behavior Rules

1. **Original amount always shown**: The `amount` field always reflects the original user-entered value.
2. **Converted amount always computed**: Even if the group's primary currency matches the expense currency, the converted amount is shown (equals the original).
3. **Rate source transparency**: The `rateSource` field tells the user whether the rate came from the live service, cache, or default.
4. **No floating point**: All monetary values use `BigDecimal` with 2 decimal places.
5. **Rate persistence**: The applied rate is stored with the expense (per the money representation design decision: "guardar siempre moneda original, monto original, monto convertido y tasa aplicada").

## Error Handling

| Scenario | Response |
|----------|----------|
| `amount <= 0` | Error: "El monto debe ser mayor que cero" |
| `fromCurrency == null` or `toCurrency == null` | Error: "La moneda es obligatoria" |
| `date` is future | Error: "No se permiten fechas futuras para la cotización" |
| OANDA service timeout after retries | Use cached rate if available; else error: "No se pudo obtener la cotización. Se muestra el monto original." |
| Cache empty and service unavailable | Use memo-default rate; `rateSource = "memo-default"`; log warning |

## Integration with Expense Registration

When registering a gasto with a currency different from the group's primary:

1. If `expense.currency != group.primaryCurrency`:
   - Fetch conversion rate for `expense.date`
   - Store `montoConvertido` and `tasaAplicada` with the expense
   - Display both original and converted amounts in UI
2. If `expense.currency == group.primaryCurrency`:
   - `convertedAmount` = `amount`
   - `exchangeRate` = 1
   - No external API call needed