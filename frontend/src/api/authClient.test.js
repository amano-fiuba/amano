import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import { NETWORK_ERROR_MESSAGE, me, register, resendVerification } from './authClient'

beforeEach(() => {
  vi.unstubAllGlobals()
})

afterEach(() => {
  vi.unstubAllGlobals()
})

describe('register(endpoint: POST /api/auth/register, contracts/email-verification-api.md)', () => {
  // Feature 003 (task T013): the 201 body is {email} — no profile, no session cookie.
  it('201 devuelve ok con el correo normalizado (sin perfil)', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue({
        ok: true,
        json: () => Promise.resolve({ email: 'ana.perez@example.com' }),
      }),
    )

    const formData = new FormData()
    const result = await register(formData)

    expect(result).toEqual({ ok: true, email: 'ana.perez@example.com' })
    expect(fetch).toHaveBeenCalledWith('/api/auth/register', {
      method: 'POST',
      credentials: 'include',
      body: formData,
    })
  })

  it('4xx parsea el ProblemDetail (detail + properties.fields)', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue({
        ok: false,
        status: 400,
        json: () =>
          Promise.resolve({
            detail: 'La solicitud contiene campos inválidos',
            properties: { fields: { email: 'El correo...' } },
          }),
      }),
    )

    const result = await register(new FormData())

    expect(result).toEqual({
      ok: false,
      status: 400,
      detail: 'La solicitud contiene campos inválidos',
      fields: { email: 'El correo...' },
    })
  })

  it('409 sin properties devuelve detalle vacío y fields vacíos', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue({
        ok: false,
        status: 409,
        json: () => Promise.resolve({ detail: 'El correo electrónico ya se encuentra registrado' }),
      }),
    )

    const result = await register(new FormData())

    expect(result).toEqual({
      ok: false,
      status: 409,
      detail: 'El correo electrónico ya se encuentra registrado',
      fields: {},
    })
  })

  it('5xx se tratata como fallo de red (FR-019)', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue({ ok: false, status: 500, json: () => Promise.resolve({}) }),
    )

    const result = await register(new FormData())

    expect(result).toEqual({ ok: false, network: true })
  })

  it('fallo de red devuelve network:true', async () => {
    vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new Error('offline')))

    const result = await register(new FormData())

    expect(result).toEqual({ ok: false, network: true })
  })
})

describe('me(endpoint: GET /api/users/me, contracts/users-api.md)', () => {
  it('200 devuelve ok con el perfil', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue({
        ok: true,
        json: () => Promise.resolve({ id: 7, firstName: 'Ana' }),
      }),
    )

    const result = await me()

    expect(result).toEqual({ ok: true, profile: { id: 7, firstName: 'Ana' } })
    expect(fetch).toHaveBeenCalledWith('/api/users/me', { credentials: 'include' })
  })

  it('401 devuelve ok:false con el estado', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue({ ok: false, status: 401 }))

    expect(await me()).toEqual({ ok: false, status: 401 })
  })

  it('fallo de red devuelve ok:false + network flag', async () => {
    vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new Error('offline')))

    const result = await me()

    expect(result.ok).toBe(false)
    expect(result.network).toBe(true)
  })
})

describe('NETWORK_ERROR_MESSAGE (FR-019, contrato)', () => {
  it('es el mensaje fijo contratado', () => {
    expect(NETWORK_ERROR_MESSAGE).toBe(
      'No se pudo completar el registro. Intenta nuevamente',
    )
  })
})

describe('resendVerification(endpoint: POST /api/auth/resend-verification, contracts)', () => {
  const NEUTRAL_MESSAGE =
    'Si el correo existe y no ha sido verificado, se enviará un nuevo enlace de confirmación'

  // Feature 003 (task T050): the 200 body is {message, remainingSeconds}.
  it('200 devuelve ok con el mensaje neutro y el tiempo restante', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue({
        ok: true,
        json: () => Promise.resolve({ message: NEUTRAL_MESSAGE, remainingSeconds: 127 }),
      }),
    )

    const result = await resendVerification('ana.perez@example.com')

    expect(result).toEqual({ ok: true, message: NEUTRAL_MESSAGE, remainingSeconds: 127 })
    expect(fetch).toHaveBeenCalledWith('/api/auth/resend-verification', {
      method: 'POST',
      credentials: 'include',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ email: 'ana.perez@example.com' }),
    })
  })

  it('fallo de red devuelve ok:false + network', async () => {
    vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new Error('offline')))

    expect(await resendVerification('ana@example.com')).toEqual({ ok: false, network: true })
  })
})
