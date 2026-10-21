// API client for POST /api/expenses (T024; contracts/expenses-api.md). The
// idempotency key identifies one save operation and travels as a header; the
// device time zone is part of the payload (D1/D5). Amounts arrive canonical.

export const EXPENSE_NETWORK_ERROR_MESSAGE =
  'No se pudo guardar el gasto. Intenta nuevamente'

function safeFields(fields) {
  if (!fields || typeof fields !== 'object' || Array.isArray(fields)) {
    return {}
  }
  return Object.fromEntries(
    Object.entries(fields).filter(([, message]) => typeof message === 'string'),
  )
}

/**
 * Returns { ok: true, expense } on 201/200, { ok: false, status, fields, detail }
 * on other 4xx, { ok: false, status: 422, reusedKey: true, detail } when the key
 * was reused with a different payload, and { ok: false, network: true } on 5xx
 * or network failures (same convention as authClient).
 */
export async function createExpense(payload, idempotencyKey) {
  try {
    const response = await fetch('/api/expenses', {
      method: 'POST',
      credentials: 'include',
      headers: {
        'Content-Type': 'application/json',
        'Idempotency-Key': idempotencyKey,
      },
      body: JSON.stringify(payload),
    })
    if (response.ok) {
      return { ok: true, expense: await response.json() }
    }
    if (response.status >= 500) {
      return { ok: false, network: true }
    }
    let problem = {}
    try {
      problem = await response.json()
    } catch {
      problem = {}
    }
    if (response.status === 422) {
      return {
        ok: false,
        status: 422,
        reusedKey: true,
        detail: problem.detail ?? '',
        code: problem.properties?.code ?? null,
      }
    }
    return {
      ok: false,
      status: response.status,
      detail: problem.detail ?? '',
      fields: safeFields(problem.properties?.fields),
    }
  } catch {
    return { ok: false, network: true }
  }
}
