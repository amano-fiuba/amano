// API client for payments of group debts (specs/018-register-payment, plan.md
// §Contrato REST). Same convention as groupExpensesClient (017): it never
// throws; 5xx and network failures are reported as { ok: false, network: true }.

export const PAYMENT_NETWORK_ERROR_MESSAGE = 'No se pudo registrar el pago. Intentá nuevamente'

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

/**
 * POST /api/payments. Returns { ok: true, payment, created } (created = 201,
 * false on an idempotent replay with 200), { ok: false, status, detail, fields,
 * code } on 4xx, or { ok: false, network: true }.
 */
export async function registerPayment(payload, idempotencyKey) {
  try {
    const response = await fetch('/api/payments', {
      method: 'POST',
      credentials: 'include',
      headers: {
        'Content-Type': 'application/json',
        'Idempotency-Key': idempotencyKey,
      },
      body: JSON.stringify(payload),
    })
    if (response.ok) {
      return { ok: true, payment: await response.json(), created: response.status === 201 }
    }
    if (response.status >= 500) {
      return { ok: false, network: true }
    }
    const problem = await problemOf(response)
    return {
      ok: false,
      status: response.status,
      detail: typeof problem.detail === 'string' ? problem.detail : '',
      fields: safeFields(problem.properties?.fields),
      code: problem.properties?.code ?? null,
    }
  } catch {
    return { ok: false, network: true }
  }
}
