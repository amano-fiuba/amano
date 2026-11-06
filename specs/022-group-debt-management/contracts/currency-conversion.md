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
| `rateSource` | `String` | Identifier of the rate source (e.g., "BCRA") |

## Exchange Rate Logic

### 1. Primary Flow (BCRA Adapter)

1. Look up cached rate for `(fromCurrency, toCurrency, date)` key
2. If cache hit → use cached rate (historical rates are deterministic, no staleness concern)
3. If cache miss → fetch from the public BCRA Estadísticas Cambiarias v1.0 API
4. Store result in cache for future requests with same par/date
5. Return conversion

`BcraExchangeRateClient` implements `ExchangeRateProvider` using Spring's `RestClient`, with
timeouts and retries and no new dependencies. The base URL comes from `BCRA_BASE_URL`
(default `https://api.bcra.gob.ar`); the API needs no credentials or registration.

### 1.1 Currency Pairs and Business Days

- The BCRA API returns each currency's quote against the peso: USD→ARS and EUR→ARS come
  directly from the response.
- USD↔EUR is crossed through ARS with `BigDecimal` (for example, USD→ARS divided by
  EUR→ARS), never floating point.
- If the requested date has no quote (weekend or holiday), use the most recent previous
  business day's quote, up to 7 days back.
- `rate_date` and the response `date` are the date of the quote actually used, not the
  expense date.

### 2. Fallback Flow (Service Unavailable)

If the BCRA API does not respond after retries:

1. **Check if there's a cached rate** (for the same par/date, even if previously stored)
2. If a cached rate exists → use it; `rateSource` stays `"BCRA"`
3. If no cache or no quote within the last 7 days → **expense not saved**: return error 503 with message "No se pudo obtener la cotización; registre el gasto en la moneda del grupo o reintente después"
4. **No default memo rate** is used; the expense simply cannot be registered without a valid rate

### 3. Same Currency

If `fromCurrency == toCurrency`:
- `exchangeRate` = 1
- `convertedAmount` = `amount`
- `rateSource` = "same-currency"

## Rate Caching Strategy

| Aspect | Design |
|--------|--------|
| **Cache Key** | `"{fromCurrency}-{toCurrency}-{date}"` (e.g., "USD-ARS-2026-01-15") |
| **No TTL** | Historical rates for a given date are deterministic and never stale; cache persists for session lifetime |
| **Storage** | In-memory map (Caffeine) or Redis (if already project-configured) |
| **Eviction** | LRU (Least Recently Used) when cache exceeds configured size (e.g., 1000 entries) |
| **Cache Miss** | Fetch from external service; on success, store in cache |
| **Rate Determinism** | Same par → same date always gives same rate; no "stale data" concept |

## Example

```
Input: CurrencyConversionRequest(amount=50, fromCurrency=USD, toCurrency=ARS, date=2026-01-15)

BCRA rate USD→ARS on 2026-01-15: 18.50

Output: CurrencyConversionResponse(
  amount=50,
  fromCurrency=USD,
  toCurrency=ARS,
  convertedAmount=925.00,
  exchangeRate=18.50,
  date=2026-01-15,
  rateSource="BCRA"
)
```

If 2026-01-15 is a holiday and the last business day was 2026-01-13, the response `date`
is `2026-01-13`: the date of the quote actually used.

## Behavior Rules

1. **Original amount always shown**: The `amount` field always reflects the original user-entered value.
2. **Converted amount always computed**: Even if the group's primary currency matches the expense currency, the converted amount is shown (equals the original).
3. **Rate source transparency**: The `rateSource` field tells the user whether the rate came from the live service or cache.
4. **No floating point**: All monetary values use `BigDecimal` with 2 decimal places.
5. **Rate persistence**: The applied rate is stored with the expense (per the money representation design decision: "guardar siempre moneda original, monto original, monto convertido y tasa aplicada").
6. **If the BCRA API is unavailable or has no quote within 7 days → expense not saved (503)**: no default/memo fallback; the user must register in the group's primary currency or retry later.

## Integration with Expense Registration

When registering a gasto with a currency different from the group's primary:

1. If `expense.currency != group.primaryCurrency`:
   - Fetch conversion rate for `expense.date` via `CurrencyConversionService.getRate()`
   - If 503 returned (no rate available) → **reject expense registration** with error message "No se pudo obtener la cotización; reintente después o registre en moneda del grupo"
   - If rate obtained → store `montoConvertido` and `tasaAplicada` with the expense
   - Display both original and converted amounts in UI

2. If `expense.currency == group.primaryCurrency`:
   - `convertedAmount` = `amount`
   - `exchangeRate` = 1
   - No external API call needed
   - `montoConvertido` = `amount`, `tasaAplicada` = 1, `rateSource` = "same-currency"