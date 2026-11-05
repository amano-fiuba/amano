import { afterEach, describe, expect, it, vi } from 'vitest'
import { PAYMENT_NETWORK_ERROR_MESSAGE, registerPayment } from './paymentsClient'

// 018 T043: client of POST /api/payments (plan.md §Contrato REST).

const PAYMENT = { id: 54, debtId: 31, amount: '1000.00', debt: { status: 'PARTIALLY_PAID' } }
const PAYLOAD = { debtId: 31, amount: '1000.00', date: '2026-11-05' }

function respond(status, body) {
  return vi.fn().mockResolvedValue({
    ok: status >= 200 && status < 300,
    status,
    json: () => (body instanceof Error ? Promise.reject(body) : Promise.resolve(body)),
  })
}

afterEach(() => {
  vi.unstubAllGlobals()
})

describe('registerPayment (018)', () => {
  it('201 devuelve el pago creado y envía la clave, el cuerpo y la cookie', async () => {
    vi.stubGlobal('fetch', respond(201, PAYMENT))

    expect(await registerPayment(PAYLOAD, 'key-1')).toEqual({ ok: true, payment: PAYMENT, created: true })
    expect(fetch).toHaveBeenCalledWith('/api/payments', {
      method: 'POST',
      credentials: 'include',
      headers: { 'Content-Type': 'application/json', 'Idempotency-Key': 'key-1' },
      body: JSON.stringify(PAYLOAD),
    })
  })

  it('200 es un reenvío idempotente', async () => {
    vi.stubGlobal('fetch', respond(200, PAYMENT))
    expect(await registerPayment(PAYLOAD, 'key-1')).toEqual({ ok: true, payment: PAYMENT, created: false })
  })

  it('400 trae los mensajes por campo y descarta los que no son texto', async () => {
    vi.stubGlobal('fetch', respond(400, {
      detail: 'Datos inválidos',
      properties: { fields: { amount: 'El pago no puede superar el saldo de $ 2.333,33', date: 3 } },
    }))
    expect(await registerPayment(PAYLOAD, 'key-1')).toEqual({
      ok: false,
      status: 400,
      detail: 'Datos inválidos',
      fields: { amount: 'El pago no puede superar el saldo de $ 2.333,33' },
      code: null,
    })
  })

  it.each([
    [403, { detail: 'Solo quien debe, quien cobra o un administrador pueden registrar este pago' }, null],
    [404, { detail: 'No existe la deuda solicitada o no sos miembro del grupo' }, null],
    [409, { detail: 'La deuda ya está pagada', properties: { code: 'DEBT_ALREADY_PAID' } }, 'DEBT_ALREADY_PAID'],
    [422, { detail: 'Clave reutilizada', properties: { code: 'IDEMPOTENCY_KEY_REUSED' } }, 'IDEMPOTENCY_KEY_REUSED'],
  ])('%i devuelve el estado, el detalle y el código', async (status, body, code) => {
    vi.stubGlobal('fetch', respond(status, body))
    const result = await registerPayment(PAYLOAD, 'key-1')
    expect(result).toMatchObject({ ok: false, status, detail: body.detail, fields: {}, code })
  })

  it('401 o un cuerpo ilegible no rompen el cliente', async () => {
    vi.stubGlobal('fetch', respond(401, new Error('not json')))
    expect(await registerPayment(PAYLOAD, 'key-1')).toEqual({
      ok: false, status: 401, detail: '', fields: {}, code: null,
    })
    vi.stubGlobal('fetch', respond(400, null))
    expect(await registerPayment(PAYLOAD, 'key-1')).toMatchObject({ ok: false, status: 400, fields: {} })
    vi.stubGlobal('fetch', respond(400, { properties: { fields: ['amount'] } }))
    expect(await registerPayment(PAYLOAD, 'key-1')).toMatchObject({ fields: {} })
  })

  it('5xx y errores de red se informan como red', async () => {
    vi.stubGlobal('fetch', respond(503, {}))
    expect(await registerPayment(PAYLOAD, 'key-1')).toEqual({ ok: false, network: true })
    vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new TypeError('offline')))
    expect(await registerPayment(PAYLOAD, 'key-1')).toEqual({ ok: false, network: true })
    expect(PAYMENT_NETWORK_ERROR_MESSAGE).toBe('No se pudo registrar el pago. Intentá nuevamente')
  })
})
