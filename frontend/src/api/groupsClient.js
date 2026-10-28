// API client for the groups endpoints (US1, contracts/api-contracts.md).
// Convention identical to authClient/expenseClient: { ok: true, ... } on 2xx,
// { ok: false, status, fields, detail } on 4xx, { ok: false, network: true }
// on 5xx or network failures.

const CANNOT_CREATE_MESSAGE = 'No se pudo crear el grupo. Intenta nuevamente'

function safeFields(fields) {
  if (!fields || typeof fields !== 'object' || Array.isArray(fields)) {
    return {}
  }
  return Object.fromEntries(
    Object.entries(fields)
      .map(([key, message]) => [key, typeof message === 'object' && message !== null ? (message && typeof message === 'string' ? message : JSON.stringify(message)) : message])
      .filter(([, message]) => typeof message === 'string'),
  )
}

function fieldsFrom(detail) {
  return { member: detail || CANNOT_CREATE_MESSAGE }
}

/**
 * POST /api/groups. Returns { ok: true, group } on 201, { ok: false, status,
 * fields, detail } on 4xx and { ok: false, network: true } on 5xx/network.
 */
export async function createGroup(payload) {
  try {
    const response = await fetch('/api/groups', {
      method: 'POST',
      credentials: 'include',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(payload),
    })
    if (!response.ok) {
      const body = await response.json().catch(() => null)
      const fields = body?.properties?.fields ? safeFields(body.properties.fields) : {}
      return {
        ok: false,
        status: response.status,
        fields: Object.keys(fields).length
          ? { ...fields }
          : { member: body?.detail || CANNOT_CREATE_MESSAGE },
        detail: body?.detail,
      }
    }
    return { ok: true, group: await response.json() }
  } catch {
    return { ok: false, network: true }
  }
}

/** GET /api/groups: the caller's active groups. */
export async function listGroups() {
  try {
    const response = await fetch('/api/groups', {
      method: 'GET',
      credentials: 'include',
    })
    if (!response.ok) {
      const body = await response.json().catch(() => null)
      return { ok: false, status: response.status, fields: fieldsFrom(body?.detail) }
    }
    const groups = await response.json()
    return { ok: true, groups: Array.isArray(groups) ? groups : [] }
  } catch {
    return { ok: false, network: true }
  }
}

/** GET /api/groups/{id}: the group detail for the caller. */
export async function getGroup(groupId) {
  try {
    const response = await fetch(`/api/groups/${groupId}`, {
      method: 'GET',
      credentials: 'include',
    })
    if (!response.ok) {
      const body = await response.json().catch(() => null)
      return { ok: false, status: response.status, fields: fieldsFrom(body?.detail) }
    }
    return { ok: true, group: await response.json() }
  } catch {
    return { ok: false, network: true }
  }
}

export const GROUPS_NETWORK_ERROR_MESSAGE =
  'No se pudo conectar con el servidor. Intenta nuevamente'
