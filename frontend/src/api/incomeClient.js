// API client for the personal incomes and the personal balance
// (specs/032-income-management, contracts/personal-incomes-api.md). The idempotency
// key identifies one save operation and travels as a header; the device time zone is
// part of the payload. Incomes are immutable: there is deliberately no function to
// edit or delete one.
import { problemOf, safeFields } from './problemDetail'

export const INCOME_NETWORK_ERROR_MESSAGE =
  'No se pudo guardar el ingreso. Intenta nuevamente'
export const BALANCE_ERROR_MESSAGE =
  'No pudimos calcular tu balance. Intentá nuevamente'

/**
 * POST /api/incomes: { ok: true, income } on 201/200 (a replay of the same key and
 * payload), { ok: false, status, fields, detail } on other 4xx,
 * { ok: false, status: 422, reusedKey: true, detail } when the key was reused with a
 * different payload, and { ok: false, network: true } on 5xx or network failures
 * (same convention as createExpense).
 */
export async function createIncome(payload, idempotencyKey) {
  try {
    const response = await fetch('/api/incomes', {
      method: 'POST',
      credentials: 'include',
      headers: {
        'Content-Type': 'application/json',
        'Idempotency-Key': idempotencyKey,
      },
      body: JSON.stringify(payload),
    })
    if (response.ok) {
      return { ok: true, income: await response.json() }
    }
    if (response.status >= 500) {
      return { ok: false, network: true }
    }
    const problem = await problemOf(response)
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

/**
 * GET /api/balance?year=&month=&from=&to= (every parameter optional): { ok: true,
 * balance } on 200 (`projectedIncome` may be absent), { ok: false, status, fields } on
 * 4xx (400 invalid period, 401 no session) and { ok: false, network: true } on 5xx
 * (503 when a quote is missing) or network failures.
 */
export async function getBalance({ year, month, from, to } = {}) {
  const params = new URLSearchParams()
  const optional = { year, month, from, to }
  for (const [name, value] of Object.entries(optional)) {
    if (value !== null && value !== undefined && value !== '') {
      params.set(name, String(value))
    }
  }
  const query = params.toString()
  try {
    const response = await fetch(`/api/balance${query ? `?${query}` : ''}`, {
      credentials: 'include',
    })
    if (response.ok) {
      return { ok: true, balance: await response.json() }
    }
    if (response.status >= 500) {
      return { ok: false, network: true }
    }
    const problem = await problemOf(response)
    return { ok: false, status: response.status, fields: safeFields(problem.properties?.fields) }
  } catch {
    return { ok: false, network: true }
  }
}
