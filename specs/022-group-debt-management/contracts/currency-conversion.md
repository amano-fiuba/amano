# Contract: Currency Conversion

**Feature**: Group Debt Management (022)
**Related Specs**: FR-009, User Story 9, Assumptions in spec.md
**Created**: 2026-11-06
**Updated**: 2026-11-07 (aligned with the implementation: `ExchangeRateProvider`, in-memory LRU cache, `SAME_CURRENCY`, preview endpoint)

## Purpose

Define how amounts are converted when an expense is in a currency different from the group's primary currency. Both the original amount and its converted equivalent are kept and shown, using the official BCRA exchange rate of the expense date. The conversion is applied **once, to the total**, when the expense is saved.

## Internal contract (there is no public conversion endpoint)

The conversion is applied by the backend when an expense of a group is saved; the only public read is the quote preview documented at the end of this file. The internal contract is `ExchangeRateProvider`:

```
ExchangeRateQuote getQuote(Currency from, Currency to, LocalDate date)
```

| Field of `ExchangeRateQuote` | Type | Description |
|------------------------------|------|-------------|
| `rate` | `BigDecimal` | Rate `from` → `to`, scale 8 (`1` for the same currency) |
| `rateDate` | `LocalDate` | Date of the quote actually used (may be an earlier business day) |
| `rateSource` | `String` | `BCRA`, or `SAME_CURRENCY` when no conversion applies |

`date` must not be in the future. When no quote can be resolved the provider throws `ExchangeRateUnavailableException`, mapped to `503`.

What the expense stores and returns (`GroupExpenseResponse`, additive): `amount` and `currency` (original, always kept), `convertedAmount` and `convertedCurrency` (group currency), `exchangeRate`, `rateDate` and `rateSource`.

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

### 2. Failure Flow (Service Unavailable)

1. A quote already in the cache for `(from, to, date)` is returned without calling the BCRA.
2. Otherwise the BCRA is called with timeouts and a bounded number of attempts (`amano.exchange.bcra.*`), walking back up to 7 days for a business day.
3. If the BCRA does not answer or has no quote within the last 7 days → **the expense is not saved**: `503` with the message `"No se pudo obtener la cotización; reintente después o registre en moneda del grupo"` (`properties.code = EXCHANGE_RATE_UNAVAILABLE`). Nothing is written (no expense, share, debt or history event).
4. **No default or memo rate** is used; the expense cannot be registered without a valid rate.

### 3. Same Currency

If `from == to`:
- `exchangeRate` = 1
- `convertedAmount` = `amount`
- `rateSource` = `"SAME_CURRENCY"` and `rateDate` = the expense date
- the provider and the BCRA are **not** called

## Rate Caching Strategy

| Aspect | Design |
|--------|--------|
| **Cache Key** | `(fromCurrency, toCurrency, date)`; a quote resolved from an earlier business day is also stored under that effective date |
| **No TTL** | Historical rates for a given date are deterministic and never stale |
| **Storage** | In-process map (`CachingExchangeRateProvider`); no external cache |
| **Eviction** | LRU when the cache exceeds `amano.exchange.bcra.cache-max-size` (1000 by default) |
| **Cache Miss** | Fetch from the BCRA; on success, store in the cache |
| **Rate Determinism** | Same pair and date always give the same rate |

## Example

```
Group in ARS; expense of 50.00 USD dated 2026-01-15.

BCRA rate USD→ARS on 2026-01-15: 18.50

GroupExpenseResponse (relevant fields):
  amount=50.00, currency=USD,
  convertedAmount=925.00, convertedCurrency=ARS,
  exchangeRate=18.50000000, rateDate=2026-01-15, rateSource=BCRA
```

If 2026-01-15 is a holiday and the last business day was 2026-01-13, `rateDate` is
`2026-01-13`: the date of the quote actually used.

## Behavior Rules

1. **Original amount always shown**: The `amount` field always reflects the original user-entered value.
2. **Converted amount always computed**: Even if the group's primary currency matches the expense currency, the converted amount is shown (equals the original).
3. **Rate source transparency**: `rateSource` is `BCRA` for an official quote and `SAME_CURRENCY` when no conversion was needed; `rateDate` tells which day's quote was used.
4. **No floating point**: All monetary values use `BigDecimal` with 2 decimal places.
5. **Rate persistence**: The applied rate is stored with the expense (per the money representation design decision: "guardar siempre moneda original, monto original, monto convertido y tasa aplicada").
6. **If the BCRA API is unavailable or has no quote within 7 days → expense not saved (503)**: no default/memo fallback; the user must register in the group's primary currency or retry later.

## Integration with Expense Registration

When registering a gasto with a currency different from the group's primary:

1. If `expense.currency != group.primaryCurrency`:
   - Fetch the rate for `expense.date` via `ExchangeRateProvider.getQuote()`
   - If 503 returned (no rate available) → **reject expense registration** with error message "No se pudo obtener la cotización; reintente después o registre en moneda del grupo"
   - If rate obtained → store `montoConvertido` and `tasaAplicada` with the expense
   - Display both original and converted amounts in UI

2. If `expense.currency == group.primaryCurrency`:
   - `convertedAmount` = `amount`
   - `exchangeRate` = 1
   - No external API call needed
   - `convertedAmount` = `amount`, `exchangeRate` = 1, `rateSource` = "SAME_CURRENCY"

---

## Exchange Quote Preview (read-only)

Lets the expense form show an estimated converted amount before saving (US9, T069/T070). It reuses `ExchangeRateProvider` and its cache; it contains **no conversion logic of its own** (the client multiplies amount × rate) and **writes nothing**. The quote that is stored is the one resolved when the expense is saved (D8), so the final rate may differ from the previewed one.

### `GET /api/groups/{groupId}/exchange-quote?currency=USD&date=2026-01-18`

| Parameter | Type | Required | Notes |
|-----------|------|----------|-------|
| `groupId` (path) | number | Yes | Group of the caller |
| `currency` | text | Yes | `ARS`, `USD` or `EUR` (case-insensitive); the currency of the expense |
| `date` | text | Yes | `aaaa-mm-dd`; not in the future (same tolerance as the expense date) |

The target currency is always the group's `primaryCurrency`.

### Response `200`

```json
{
  "rate": 1250.50000000,
  "rateDate": "2026-01-16",
  "rateSource": "BCRA",
  "groupCurrency": "ARS"
}
```

| Field | Type | Description |
|-------|------|-------------|
| `rate` | number | Rate `currency` → group currency, scale 8 |
| `rateDate` | text | Effective date of the quote (may be an earlier business day, up to 7 days back) |
| `rateSource` | text | `BCRA`; `SAME_CURRENCY` when `currency` equals the group currency |
| `groupCurrency` | text | The group's primary currency |

Same currency → `rate` `1.00000000`, `rateDate` equal to the requested `date`, `rateSource` `SAME_CURRENCY`, and the provider is **not** called.

### Errors

| Status | When | Body |
|--------|------|------|
| `400` | Missing/invalid `currency` (`"La moneda no es válida"` / `"La moneda es obligatoria"`), missing/bad-format `date` (`"La fecha debe tener el formato aaaa-mm-dd"`) or future `date` (`"La fecha no puede ser posterior a hoy"`) | `ProblemDetail` with `properties.fields.currency` / `properties.fields.date` |
| `401` | No session | `ProblemDetail` |
| `404` | Not an active member of the group, or the group does not exist (neutral) | `"No existe el grupo solicitado o no sos miembro"` |
| `503` | No quote available (same message and `properties.code = EXCHANGE_RATE_UNAVAILABLE` as when saving an expense) | `"No se pudo obtener la cotización; reintente después o registre en moneda del grupo"` |

Frontend behavior (T070): the form asks only when the currency differs from the group's and the amount and date are valid, when one of the three changes (400 ms debounce); a failure never blocks saving.

