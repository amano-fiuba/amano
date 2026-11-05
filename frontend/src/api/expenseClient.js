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

// Feature 009 (specs/009-pause-recurring-expense/contracts/recurring-expenses-api.md).
export const RECURRING_LOAD_ERROR_MESSAGE =
  'No se pudieron cargar tus gastos recurrentes. Intentá nuevamente'
export const RECURRING_UPDATE_ERROR_MESSAGE =
  'No se pudo actualizar el gasto recurrente. Intentá nuevamente'

async function problemOf(response) {
  try {
    return await response.json()
  } catch {
    return {}
  }
}

/**
 * GET /api/expenses/recurring: { ok: true, expenses } on 200, { ok: false, status }
 * on 4xx (401 = no session) and { ok: false, network: true } on 5xx or network.
 */
export async function listRecurringExpenses() {
  try {
    const response = await fetch('/api/expenses/recurring', { credentials: 'include' })
    if (response.ok) {
      const body = await response.json()
      return { ok: true, expenses: Array.isArray(body) ? body : [] }
    }
    if (response.status >= 500) {
      return { ok: false, network: true }
    }
    return { ok: false, status: response.status }
  } catch {
    return { ok: false, network: true }
  }
}

export const RECENT_EXPENSES_ERROR_MESSAGE =
  'No se pudieron cargar tus últimos gastos. Intentá nuevamente'

/**
 * GET /api/expenses/recent (specs/015-main-screen): { ok: true, expenses } on
 * 200, { ok: false, status } on 4xx and { ok: false, network: true } on 5xx or
 * network failures.
 */
export async function listRecentExpenses() {
  try {
    const response = await fetch('/api/expenses/recent', { credentials: 'include' })
    if (response.ok) {
      const body = await response.json()
      return { ok: true, expenses: Array.isArray(body) ? body : [] }
    }
    if (response.status >= 500) {
      return { ok: false, network: true }
    }
    return { ok: false, status: response.status }
  } catch {
    return { ok: false, network: true }
  }
}

/**
 * PATCH /api/expenses/{id}/status with { status } (ACTIVE | PAUSED | FINISHED):
 * { ok: true, expense } on 200, { ok: false, status, detail, code } on 4xx (400
 * invalid status, 401 no session, 404 not a series of the user, 409 finished) and
 * { ok: false, network: true } on 5xx or network.
 */
export async function updateRecurringExpenseStatus(id, status) {
  try {
    const response = await fetch(`/api/expenses/${encodeURIComponent(id)}/status`, {
      method: 'PATCH',
      credentials: 'include',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ status }),
    })
    if (response.ok) {
      return { ok: true, expense: await response.json() }
    }
    if (response.status >= 500) {
      return { ok: false, network: true }
    }
    const problem = await problemOf(response)
    return {
      ok: false,
      status: response.status,
      detail: problem.detail ?? '',
      code: problem.properties?.code ?? null,
    }
  } catch {
    return { ok: false, network: true }
  }
}
