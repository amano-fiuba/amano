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
 * Returns { ok: true, email } on 201 (feature 003: no profile, no session),
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

/**
 * Redeems a verification token (feature 003, POST /api/auth/verify-email).
 * Returns { ok: true, profile } on 200 — the backend also sets the session
 * cookie, so the SPA only has to store the profile. 4xx come back as
 * { ok: false, status, detail } (400 invalid, 409 already used, 410 expired);
 * network failures and 5xx as { ok: false, network: true }.
 */
export async function verifyEmail(token) {
  try {
    const response = await fetch('/api/auth/verify-email', {
      method: 'POST',
      credentials: 'include',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ token }),
    })
    if (response.ok) {
      return { ok: true, profile: await response.json() }
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

/**
 * Requests a new verification email (feature 003, POST /api/auth/resend-verification).
 * The endpoint is always 200 with the neutral message and the remaining seconds of
 * the 5-minute limit (FR-012/SC-005), so a 4xx never carries domain meaning: only
 * network failures and 5xx come back as { ok: false, network: true }.
 */
export async function resendVerification(email) {
  try {
    const response = await fetch('/api/auth/resend-verification', {
      method: 'POST',
      credentials: 'include',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ email }),
    })
    if (response.ok) {
      const body = await response.json()
      return { ok: true, message: body.message, remainingSeconds: body.remainingSeconds }
    }
    return { ok: false, network: response.status >= 500 }
  } catch {
    return { ok: false, network: true }
  }
}

// Feature 004 (login): fixed message for network failures or 5xx on login.
export const LOGIN_NETWORK_ERROR_MESSAGE =
  'No se pudo iniciar sesión. Intenta nuevamente'

/**
 * Logs in (POST /api/auth/login). Returns { ok: true, profile } on 200 — the
 * backend also sets the HttpOnly session cookie, so the SPA only stores the
 * profile. 4xx come back as { ok: false, status, detail, fields, action }
 * (401 bad credentials, 403 unverified account with action
 * 'RESEND_VERIFICATION'); network failures and 5xx as { ok: false, network: true }.
 */
export async function login(email, password) {
  try {
    const response = await fetch('/api/auth/login', {
      method: 'POST',
      credentials: 'include',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ email, password }),
    })
    if (response.ok) {
      return { ok: true, profile: await response.json() }
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
    return {
      ok: false,
      status: response.status,
      detail: problem.detail ?? '',
      fields: problem.properties?.fields ?? {},
      action: problem.properties?.action ?? null,
    }
  } catch {
    return { ok: false, network: true }
  }
}
