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

/**
 * PUT /api/groups/{id}: partial update of group settings (name, description,
 * permission casillas). Authz is server-side (GroupPermissionChecker); the
 * client only renders reasons.
 */
export async function updateGroup(groupId, payload) {
  try {
    const response = await fetch(`/api/groups/${groupId}`, {
      method: 'PUT',
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
        fields: Object.keys(fields).length ? { ...fields } : {},
        detail: body?.detail || CANNOT_CREATE_MESSAGE,
      }
    }
    return { ok: true, group: await response.json() }
  } catch {
    return { ok: false, network: true }
  }
}

/**
 * GET /api/groups/{id}/candidates?q=: only the caller's friends (server-side
 * restriction; never the whole user base).
 */
export async function searchCandidates(groupId, query) {
  try {
    const response = await fetch(
      `/api/groups/${groupId}/candidates?q=${encodeURIComponent(query)}`,
      { method: 'GET', credentials: 'include' },
    )
    if (!response.ok) {
      const body = await response.json().catch(() => null)
      return { ok: false, status: response.status, detail: body?.detail, fields: fieldsFrom(body?.detail) }
    }
    const data = await response.json()
    return { ok: true, content: Array.isArray(data.content) ? data.content : [] }
  } catch {
    return { ok: false, network: true }
  }
}

/**
 * POST /api/groups/{id}/members: registered friend (identifier = email) or
 * simulated profile (identifier + name). Reasons (ya es miembro, no es amigo,
 * casilla de invitación) come in `detail` for the UI.
 */
export async function addMember(groupId, payload) {
  try {
    const response = await fetch(`/api/groups/${groupId}/members`, {
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
        fields,
        detail: body?.detail,
      }
    }
    return { ok: true, member: (await response.json()).member }
  } catch {
    return { ok: false, network: true }
  }
}

/**
 * GET /api/groups/{id}/activity: paginated descending history (todos los
 * miembros). Gastos y pagos son puntos de enganche que aparecerán cuando
 * exista la integración de gastos.
 */
export async function getActivity(groupId, { page = 1, size = 20, eventType } = {}) {
  try {
    const params = new URLSearchParams({ page: String(page), size: String(size) })
    if (eventType) {
      params.set('eventType', eventType)
    }
    const response = await fetch(`/api/groups/${groupId}/activity?${params.toString()}`, {
      method: 'GET',
      credentials: 'include',
    })
    if (!response.ok) {
      const body = await response.json().catch(() => null)
      return { ok: false, status: response.status, detail: body?.detail }
    }
    return { ok: true, ...(await response.json()) }
  } catch {
    return { ok: false, network: true }
  }
}

export const GROUPS_NETWORK_ERROR_MESSAGE =
  'No se pudo conectar con el servidor. Intenta nuevamente'
