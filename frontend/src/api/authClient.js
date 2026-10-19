// API client for the auth endpoints (task T021, contracts/auth-api.md).
// Sends/receives the HttpOnly session cookie via credentials: 'include'.

// FR-019: fixed message for network failures or 5xx responses (US2 covers the
// live behavior; far endpoints of this feature reuse it).
export const NETWORK_ERROR_MESSAGE =
  'No se pudo completar el registro. Intenta nuevamente'

/** RFC 7807 shape: { detail, properties: { fields } } (auth-api "Errores"). */
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
 * Single atomic multipart submit of the registration form.
 * Returns { ok: true, email } on 201 (feature 002: no profile, no session),
 * otherwise { ok: false, status, detail, fields } or { ok: false, network: true }.
 */
export async function register(formData) {
  try {
    const response = await fetch('/api/auth/register', {
      method: 'POST',
      credentials: 'include',
      body: formData,
    })
    if (response.ok) {
      const body = await response.json()
      return { ok: true, email: body.email }
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

/** GET /api/users/me with the session cookie; { ok, profile } or { ok: false }. */
export async function me() {
  try {
    const response = await fetch('/api/users/me', { credentials: 'include' })
    if (!response.ok) {
      return { ok: false, status: response.status }
    }
    return { ok: true, profile: await response.json() }
  } catch {
    return { ok: false, network: true }
  }
}
