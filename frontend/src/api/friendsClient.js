// API client for the friendship request endpoints (features 011 and 012,
// contracts/friendship-requests-api.md). Same convention as
// authClient/usersClient/expenseClient: never throws, always returns
// { ok: true, ... } or { ok: false, ... }.

export const SEND_NETWORK_ERROR_MESSAGE =
  'No se pudo enviar la solicitud. Intentá nuevamente'
export const RECEIVED_NETWORK_ERROR_MESSAGE =
  'No se pudieron cargar tus solicitudes. Intentá nuevamente'
export const RESPOND_NETWORK_ERROR_MESSAGE =
  'No se pudo responder la solicitud. Intentá nuevamente'

async function parseProblem(response) {
  try {
    const problem = await response.json()
    return {
      detail: problem.detail ?? '',
      fields: problem.properties?.fields ?? {},
    }
  } catch {
    return { detail: '', fields: {} }
  }
}

/**
 * Sends a friendship request to the given account. Returns
 * { ok: true, request } on 201; 4xx as { ok: false, status, detail, fields };
 * network failures and 5xx as { ok: false, network: true }.
 */
export async function sendFriendshipRequest(recipientId) {
  try {
    const response = await fetch('/api/users/me/friendship-requests', {
      method: 'POST',
      credentials: 'include',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ recipientId }),
    })
    if (response.ok) {
      return { ok: true, request: await response.json() }
    }
    if (response.status >= 500) {
      return { ok: false, network: true }
    }
    const problem = await parseProblem(response)
    return { ok: false, status: response.status, ...problem }
  } catch {
    return { ok: false, network: true }
  }
}

/**
 * Lists the pending requests received by the session user. Returns
 * { ok: true, requests } on 200; 4xx as { ok: false, status, detail };
 * network failures and 5xx as { ok: false, network: true }.
 */
export async function listReceivedFriendshipRequests() {
  try {
    const response = await fetch('/api/users/me/friendship-requests/received', {
      credentials: 'include',
    })
    if (response.ok) {
      return { ok: true, requests: await response.json() }
    }
    if (response.status >= 500) {
      return { ok: false, network: true }
    }
    const problem = await parseProblem(response)
    return { ok: false, status: response.status, detail: problem.detail }
  } catch {
    return { ok: false, network: true }
  }
}

/**
 * Answers a pending friendship request as the recipient (feature 012).
 * `action` is 'ACCEPT' or 'REJECT'; the outcome is the response object on
 * 200 ({ ok: true, request }); 4xx as { ok: false, status, detail, fields };
 * network failures and 5xx as { ok: false, network: true }.
 */
export async function respondFriendshipRequest(id, action) {
  try {
    const response = await fetch(`/api/users/me/friendship-requests/${id}`, {
      method: 'PUT',
      credentials: 'include',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ action }),
    })
    if (response.ok) {
      return { ok: true, request: await response.json() }
    }
    if (response.status >= 500) {
      return { ok: false, network: true }
    }
    const problem = await parseProblem(response)
    return { ok: false, status: response.status, ...problem }
  } catch {
    return { ok: false, network: true }
  }
}
