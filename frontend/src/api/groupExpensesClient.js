// API client for group expenses and their debts (specs/017-debt-states,
// contracts/group-expenses-api.md). Same convention as expenseClient: it never
// throws; 5xx and network failures are reported as { ok: false, network: true }.

export const GROUP_EXPENSE_NETWORK_ERROR_MESSAGE =
  'No se pudo registrar el gasto. Intentá nuevamente'
export const GROUP_EXPENSE_LOAD_ERROR_MESSAGE =
  'No se pudo cargar el gasto. Intentá nuevamente'

function safeFields(fields) {
  if (!fields || typeof fields !== 'object' || Array.isArray(fields)) {
    return {}
  }
  return Object.fromEntries(
    Object.entries(fields).filter(([, message]) => typeof message === 'string'),
  )
}

async function problemOf(response) {
  try {
    const body = await response.json()
    return body && typeof body === 'object' ? body : {}
  } catch {
    return {}
  }
}

async function failure(response) {
  const problem = await problemOf(response)
  return {
    ok: false,
    status: response.status,
    detail: typeof problem.detail === 'string' ? problem.detail : '',
    fields: safeFields(problem.properties?.fields),
    code: problem.properties?.code ?? null,
  }
}

/**
 * POST /api/groups/{id}/expenses. Returns { ok: true, expense, created }
 * (created = 201, false on an idempotent replay with 200), { ok: false, status,
 * detail, fields, code } on 4xx, or { ok: false, network: true }.
 */
export async function createGroupExpense(groupId, payload, idempotencyKey) {
  try {
    const response = await fetch(`/api/groups/${groupId}/expenses`, {
      method: 'POST',
      credentials: 'include',
      headers: {
        'Content-Type': 'application/json',
        'Idempotency-Key': idempotencyKey,
      },
      body: JSON.stringify(payload),
    })
    if (response.ok) {
      return { ok: true, expense: await response.json(), created: response.status === 201 }
    }
    if (response.status >= 500) {
      return { ok: false, network: true }
    }
    return failure(response)
  } catch {
    return { ok: false, network: true }
  }
}

/** GET /api/groups/{id}/expenses/{expenseId}: { ok: true, expense } or a failure as above. */
export async function getGroupExpense(groupId, expenseId) {
  try {
    const response = await fetch(`/api/groups/${groupId}/expenses/${expenseId}`, {
      method: 'GET',
      credentials: 'include',
    })
    if (response.ok) {
      return { ok: true, expense: await response.json() }
    }
    if (response.status >= 500) {
      return { ok: false, network: true }
    }
    return failure(response)
  } catch {
    return { ok: false, network: true }
  }
}
