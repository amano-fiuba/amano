import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { EXPENSE_NETWORK_ERROR_MESSAGE, createExpense } from './expenseClient'

const PAYLOAD = {
  amount: '10.10',
  currency: 'ARS',
  date: '2026-10-21',
  category: 'FOOD',
  type: 'ONE_TIME',
  paymentMethod: 'CASH',
  description: 'Almuerzo',
  deviceTimeZone: 'America/Argentina/Buenos_Aires',
}

const KEY = '3f1c8a8e-6a4e-4f0e-9b1e-0d8f5f3c2a10'

function response({ ok = false, status = 200, body = {} } = {}) {
  return {
    ok,
    status,
    json: () => Promise.resolve(body),
  }
}

beforeEach(() => {
  vi.unstubAllGlobals()
})

afterEach(() => {
  vi.unstubAllGlobals()
})

describe('createExpense (T024)', () => {
  it('envía POST con cookie, cabecera de idempotencia y la zona horaria dentro del body', async () => {
    const fetchMock = vi.fn().mockResolvedValue(response({ ok: true, status: 201, body: { id: 1 } }))
    vi.stubGlobal('fetch', fetchMock)

    await createExpense(PAYLOAD, KEY)

    expect(fetchMock).toHaveBeenCalledTimes(1)
    const [url, options] = fetchMock.mock.calls[0]
    expect(url).toBe('/api/expenses')
    expect(options.method).toBe('POST')
    expect(options.credentials).toBe('include')
    expect(options.headers).toEqual({
      'Content-Type': 'application/json',
      'Idempotency-Key': KEY,
    })
    expect(JSON.parse(options.body)).toEqual(PAYLOAD)
    expect(JSON.parse(options.body).deviceTimeZone).toBe('America/Argentina/Buenos_Aires')
  })

  it('201 devuelve el gasto creado', async () => {
    const expense = { id: 42, amount: '10.10' }
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(
      response({ ok: true, status: 201, body: expense }),
    ))

    await expect(createExpense(PAYLOAD, KEY)).resolves.toEqual({ ok: true, expense })
  })

  it('200 trata el replay como éxito', async () => {
    const expense = { id: 42, amount: '10.10' }
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(
      response({ ok: true, status: 200, body: expense }),
    ))

    await expect(createExpense(PAYLOAD, KEY)).resolves.toEqual({ ok: true, expense })
  })

  it('400 devuelve status, detalle y mensajes por campo', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(response({
      status: 400,
      body: {
        detail: 'La solicitud contiene campos inválidos',
        properties: { fields: { amount: 'El monto es obligatorio' } },
      },
    })))

    await expect(createExpense(PAYLOAD, KEY)).resolves.toEqual({
      ok: false,
      status: 400,
      detail: 'La solicitud contiene campos inválidos',
      fields: { amount: 'El monto es obligatorio' },
    })
  })

  it('401 se informa con su status para que el gate redirija', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(response({ status: 401 })))

    await expect(createExpense(PAYLOAD, KEY)).resolves.toEqual({
      ok: false,
      status: 401,
      detail: '',
      fields: {},
    })
  })

  it('422 marca la clave reutilizada y conserva el detalle accionable', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(response({
      status: 422,
      body: {
        detail: 'La operación ya fue registrada con datos distintos. Reintentá el guardado.',
        properties: { code: 'IDEMPOTENCY_KEY_REUSED' },
      },
    })))

    await expect(createExpense(PAYLOAD, KEY)).resolves.toEqual({
      ok: false,
      status: 422,
      reusedKey: true,
      detail: 'La operación ya fue registrada con datos distintos. Reintentá el guardado.',
      code: 'IDEMPOTENCY_KEY_REUSED',
    })
  })

  it('5xx y fallos de red se informan como network con el mensaje fijo', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(response({ status: 500 })))
    await expect(createExpense(PAYLOAD, KEY)).resolves.toEqual({ ok: false, network: true })

    vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new Error('offline')))
    await expect(createExpense(PAYLOAD, KEY)).resolves.toEqual({ ok: false, network: true })
    expect(EXPENSE_NETWORK_ERROR_MESSAGE).toBe(
      'No se pudo guardar el gasto. Intenta nuevamente',
    )
  })

  it('ignora fields que no sean un objeto de mensajes (SC-006)', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(response({
      status: 400,
      body: { properties: { fields: 'detalle interno' } },
    })))

    await expect(createExpense(PAYLOAD, KEY)).resolves.toMatchObject({
      ok: false,
      status: 400,
      fields: {},
    })
  })

  it('un cuerpo de error no JSON no rompe el cliente', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue({
      ok: false,
      status: 400,
      json: () => Promise.reject(new Error('not json')),
    }))

    await expect(createExpense(PAYLOAD, KEY)).resolves.toEqual({
      ok: false,
      status: 400,
      detail: '',
      fields: {},
    })
  })
})
