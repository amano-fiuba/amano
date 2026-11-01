import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import {
  RECEIVED_NETWORK_ERROR_MESSAGE,
  RESPOND_NETWORK_ERROR_MESSAGE,
  SEND_NETWORK_ERROR_MESSAGE,
  listReceivedFriendshipRequests,
  respondFriendshipRequest,
  sendFriendshipRequest,
} from './friendsClient'

beforeEach(() => {
  vi.unstubAllGlobals()
})

afterEach(() => {
  vi.unstubAllGlobals()
})

describe('sendFriendshipRequest(endpoint: POST /api/users/me/friendship-requests)', () => {
  it('201 devuelve ok con la solicitud y envía recipientId como JSON con cookie', async () => {
    const request = {
      id: 9,
      status: 'PENDING',
      createdAt: '2026-10-30T12:00:00Z',
      recipient: { id: 12, firstName: 'Bruno', lastName: null, email: 'bruno@example.com' },
    }
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue({ ok: true, status: 201, json: () => Promise.resolve(request) }),
    )

    const result = await sendFriendshipRequest(12)

    expect(result).toEqual({ ok: true, request })
    expect(fetch).toHaveBeenCalledWith('/api/users/me/friendship-requests', {
      method: 'POST',
      credentials: 'include',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ recipientId: 12 }),
    })
  })

  it('400 devuelve el detalle y los campos del ProblemDetail', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue({
        ok: false,
        status: 400,
        json: () =>
          Promise.resolve({
            detail: 'La solicitud contiene campos inválidos',
            properties: {
              fields: { recipientId: 'No podés enviarte una solicitud de amistad a vos mismo' },
            },
          }),
      }),
    )

    expect(await sendFriendshipRequest(1)).toEqual({
      ok: false,
      status: 400,
      detail: 'La solicitud contiene campos inválidos',
      fields: { recipientId: 'No podés enviarte una solicitud de amistad a vos mismo' },
    })
  })

  it('409 y 404 devuelven status y detalle para que la página muestre el mensaje', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue({
        ok: false,
        status: 409,
        json: () =>
          Promise.resolve({ detail: 'Ya existe una solicitud de amistad pendiente entre ambos usuarios' }),
      }),
    )
    expect(await sendFriendshipRequest(12)).toEqual({
      ok: false,
      status: 409,
      detail: 'Ya existe una solicitud de amistad pendiente entre ambos usuarios',
      fields: {},
    })

    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue({
        ok: false,
        status: 404,
        json: () => Promise.resolve({ detail: 'No se encontró el usuario' }),
      }),
    )
    expect(await sendFriendshipRequest(99)).toEqual({
      ok: false,
      status: 404,
      detail: 'No se encontró el usuario',
      fields: {},
    })
  })

  it('4xx con cuerpo no JSON no rompe', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue({
        ok: false,
        status: 400,
        json: () => Promise.reject(new Error('no json')),
      }),
    )

    expect(await sendFriendshipRequest(12)).toEqual({
      ok: false,
      status: 400,
      detail: '',
      fields: {},
    })
  })

  it('5xx y fallo de red se tratan como network', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue({ ok: false, status: 503 }))
    expect(await sendFriendshipRequest(12)).toEqual({ ok: false, network: true })

    vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new Error('offline')))
    expect(await sendFriendshipRequest(12)).toEqual({ ok: false, network: true })
  })

  it('SEND_NETWORK_ERROR_MESSAGE es el mensaje fijo de la spec', () => {
    expect(SEND_NETWORK_ERROR_MESSAGE).toBe(
      'No se pudo enviar la solicitud. Intentá nuevamente',
    )
  })
})

describe('listReceivedFriendshipRequests(endpoint: GET /api/users/me/friendship-requests/received)', () => {
  it('200 devuelve ok con las solicitudes y usa la cookie de sesión', async () => {
    const requests = [
      {
        id: 5,
        status: 'PENDING',
        createdAt: '2026-10-30T12:00:00Z',
        sender: { id: 3, firstName: 'Ana', lastName: 'Pérez', email: 'ana@example.com' },
      },
    ]
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue({ ok: true, status: 200, json: () => Promise.resolve(requests) }),
    )

    expect(await listReceivedFriendshipRequests()).toEqual({ ok: true, requests })
    expect(fetch).toHaveBeenCalledWith('/api/users/me/friendship-requests/received', {
      credentials: 'include',
    })
  })

  it('401 devuelve el estado para que la página cierre la sesión', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue({
        ok: false,
        status: 401,
        json: () => Promise.resolve({ title: 'No autorizado' }),
      }),
    )

    expect(await listReceivedFriendshipRequests()).toEqual({
      ok: false,
      status: 401,
      detail: '',
    })
  })

  it('4xx devuelve el detalle del ProblemDetail; cuerpo inválido no rompe', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue({
        ok: false,
        status: 403,
        json: () => Promise.resolve({ detail: 'Cuenta no verificada' }),
      }),
    )
    expect(await listReceivedFriendshipRequests()).toEqual({
      ok: false,
      status: 403,
      detail: 'Cuenta no verificada',
    })

    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue({
        ok: false,
        status: 400,
        json: () => Promise.reject(new Error('no json')),
      }),
    )
    expect(await listReceivedFriendshipRequests()).toEqual({
      ok: false,
      status: 400,
      detail: '',
    })
  })

  it('5xx y fallo de red se tratan como network', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue({ ok: false, status: 500 }))
    expect(await listReceivedFriendshipRequests()).toEqual({ ok: false, network: true })

    vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new Error('offline')))
    expect(await listReceivedFriendshipRequests()).toEqual({ ok: false, network: true })
  })

  it('RECEIVED_NETWORK_ERROR_MESSAGE es el mensaje fijo de la spec', () => {
    expect(RECEIVED_NETWORK_ERROR_MESSAGE).toBe(
      'No se pudieron cargar tus solicitudes. Intentá nuevamente',
    )
  })
})

describe('respondFriendshipRequest (endpoint: PUT /api/users/me/friendship-requests/{id}, feature 012)', () => {
  it('200 devuelve ok con la solicitud respondida y envía los datos del contrato', async () => {
    const answered = {
      id: 40,
      status: 'ACCEPTED',
      createdAt: '2026-10-30T14:05:00Z',
      respondedAt: '2026-10-30T15:12:00Z',
      sender: { id: 31, firstName: 'Bruno', lastName: null, email: 'bruno@example.com' },
    }
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue({ ok: true, status: 200, json: () => Promise.resolve(answered) }),
    )

    const result = await respondFriendshipRequest(40, 'ACCEPT')

    expect(result).toEqual({ ok: true, request: answered })
    expect(fetch).toHaveBeenCalledWith('/api/users/me/friendship-requests/40', {
      method: 'PUT',
      credentials: 'include',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ action: 'ACCEPT' }),
    })
  })

  it('400 devuelve el detalle y los campos del ProblemDetail (acción o id inválido)', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue({
        ok: false,
        status: 400,
        json: () =>
          Promise.resolve({
            detail: 'La solicitud contiene campos inválidos',
            properties: { fields: { action: 'La acción no es válida' } },
          }),
      }),
    )

    expect(await respondFriendshipRequest(40, 'MAYBE')).toEqual({
      ok: false,
      status: 400,
      detail: 'La solicitud contiene campos inválidos',
      fields: { action: 'La acción no es válida' },
    })
  })

  it('409 y 404 devuelven status y detalle para que la página retire la fila', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue({
        ok: false,
        status: 409,
        json: () =>
          Promise.resolve({ detail: 'Esta solicitud de amistad ya fue respondida' }),
      }),
    )
    expect(await respondFriendshipRequest(40, 'ACCEPT')).toEqual({
      ok: false,
      status: 409,
      detail: 'Esta solicitud de amistad ya fue respondida',
      fields: {},
    })

    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue({
        ok: false,
        status: 404,
        json: () => Promise.resolve({ detail: 'No se encontró la solicitud de amistad' }),
      }),
    )
    expect(await respondFriendshipRequest(99, 'REJECT')).toEqual({
      ok: false,
      status: 404,
      detail: 'No se encontró la solicitud de amistad',
      fields: {},
    })
  })

  it('5xx y fallo de red se tratan como network', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue({ ok: false, status: 502 }))
    expect(await respondFriendshipRequest(40, 'ACCEPT')).toEqual({ ok: false, network: true })

    vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new Error('offline')))
    expect(await respondFriendshipRequest(40, 'REJECT')).toEqual({ ok: false, network: true })
  })

  it('RESPOND_NETWORK_ERROR_MESSAGE es el mensaje fijo de la spec', () => {
    expect(RESPOND_NETWORK_ERROR_MESSAGE).toBe(
      'No se pudo responder la solicitud. Intentá nuevamente',
    )
  })
})
