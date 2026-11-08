// API client for GET /api/currencies (027, contracts/currencies-api.md): the BCRA
// catalog of the "Ver más" dialog. A 503 means the BCRA is down and nothing is cached.

/**
 * Returns { ok: true, currencies } on 200, { ok: false, unavailable: true } on
 * 503, { ok: false, network: true } on other 5xx or network failures and
 * { ok: false, status } on other errors (e.g. 401).
 */
export async function listCurrencies() {
  try {
    const response = await fetch('/api/currencies', { credentials: 'include' })
    if (response.ok) {
      const body = await response.json()
      return { ok: true, currencies: Array.isArray(body?.currencies) ? body.currencies : [] }
    }
    if (response.status === 503) {
      return { ok: false, unavailable: true }
    }
    if (response.status >= 500) {
      return { ok: false, network: true }
    }
    return { ok: false, status: response.status }
  } catch {
    return { ok: false, network: true }
  }
}
