import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { createIncome, getBalance } from './incomeClient'

const PAYLOAD = {
  amount: '850000.00',
  currency: 'ARS',
  date: '2026-11-17',
  category: 'SALARY',
  paymentMethod: 'TRANSFER',
  description: 'Sueldo',
  deviceTimeZone: 'America/Argentina/Buenos_Aires',
}
const KEY = '3f1c8a8e-6a4e-4f0e-9b1e-0d8f5f3c2a10'

function response({ ok = false, status = 200, body = {} } = {}) {
  return { ok, status, json: () => Promise.resolve(body) }
}

beforeEach(() => {
  vi.unstubAllGlobals()
})

afterEach(() => {
  vi.unstubAllGlobals()
})

describe('createIncome (032)', () => {
  it('envía POST con cookie, cabecera de idempotencia y el payload en el body', async () => {
    const fetchMock = vi.fn().mockResolvedValue(response({ ok: true, status: 201, body: { id: 1 } }))
    vi.stubGlobal('fetch', fetchMock)

    const result = await createIncome(PAYLOAD, KEY)

    expect(fetchMock).toHaveBeenCalledWith('/api/incomes', {
      method: 'POST',
      credentials: 'include',
      headers: { 'Content-Type': 'application/json', 'Idempotency-Key': KEY },
      body: JSON.stringify(PAYLOAD),
    })
    expect(result).toEqual({ ok: true, income: { id: 1 } })
  })

  it('un reintento idéntico (200) también es un éxito', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(response({ ok: true, status: 200, body: { id: 1 } })))

    expect(await createIncome(PAYLOAD, KEY)).toEqual({ ok: true, income: { id: 1 } })
  })

  it('devuelve los errores por campo de un 400 y descarta los que no son texto', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(
        response({
          status: 400,
          body: {
            detail: 'Datos inválidos',
            properties: { fields: { amount: 'El monto debe ser mayor a cero', extra: 3 } },
          },
        }),
      ),
    )

    expect(await createIncome(PAYLOAD, KEY)).toEqual({
      ok: false,
      status: 400,
      detail: 'Datos inválidos',
      fields: { amount: 'El monto debe ser mayor a cero' },
    })
  })

  it('un 422 indica que la clave se reutilizó con otro cuerpo', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(
        response({
          status: 422,
          body: { detail: 'Ya registrada', properties: { code: 'IDEMPOTENCY_KEY_REUSED' } },
        }),
      ),
    )

    expect(await createIncome(PAYLOAD, KEY)).toEqual({
      ok: false,
      status: 422,
      reusedKey: true,
      detail: 'Ya registrada',
      code: 'IDEMPOTENCY_KEY_REUSED',
    })
  })

  it('un 401 sin cuerpo devuelve el estado y campos vacíos', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue({
        ok: false,
        status: 401,
        json: () => Promise.reject(new Error('sin cuerpo')),
      }),
    )

    expect(await createIncome(PAYLOAD, KEY)).toEqual({
      ok: false,
      status: 401,
      detail: '',
      fields: {},
    })
  })

  it('un 5xx o un fallo de red es un error de red', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(response({ status: 503 })))
    expect(await createIncome(PAYLOAD, KEY)).toEqual({ ok: false, network: true })

    vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new Error('offline')))
    expect(await createIncome(PAYLOAD, KEY)).toEqual({ ok: false, network: true })
  })
})

describe('getBalance (032)', () => {
  const BALANCE = { currency: 'ARS', income: '10.00', expenses: '4.00', net: '6.00' }

  it('pide el balance sin parámetros cuando no hay período', async () => {
    const fetchMock = vi.fn().mockResolvedValue(response({ ok: true, body: BALANCE }))
    vi.stubGlobal('fetch', fetchMock)

    const result = await getBalance()

    expect(fetchMock).toHaveBeenCalledWith('/api/balance', { credentials: 'include' })
    expect(result).toEqual({ ok: true, balance: BALANCE })
  })

  it('envía solo los parámetros informados', async () => {
    const fetchMock = vi.fn().mockResolvedValue(response({ ok: true, body: BALANCE }))
    vi.stubGlobal('fetch', fetchMock)

    await getBalance({ year: '2026', month: '3', from: '', to: null })

    expect(fetchMock.mock.calls[0][0]).toBe('/api/balance?year=2026&month=3')
  })

  it('devuelve el estado y los campos de un 4xx', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(
        response({ status: 400, body: { properties: { fields: { month: 'El mes no es válido' } } } }),
      ),
    )

    expect(await getBalance({ month: '13' })).toEqual({
      ok: false,
      status: 400,
      fields: { month: 'El mes no es válido' },
    })
  })

  it('un 503 (cotización no disponible) y un fallo de red son errores de red', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(response({ status: 503 })))
    expect(await getBalance()).toEqual({ ok: false, network: true })

    vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new Error('offline')))
    expect(await getBalance()).toEqual({ ok: false, network: true })
  })
})
