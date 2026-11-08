import { afterEach, describe, expect, it, vi } from 'vitest'
import { listCurrencies } from './currencyClient'

function response({ ok = false, status = 200, body = {} } = {}) {
  return { ok, status, json: () => Promise.resolve(body) }
}

afterEach(() => {
  vi.unstubAllGlobals()
})

describe('listCurrencies (027, T030)', () => {
  it('pide el catálogo con credenciales y devuelve las monedas', async () => {
    const fetchMock = vi.fn().mockResolvedValue(
      response({ ok: true, body: { currencies: [{ code: 'BRL', bcraName: 'REAL' }] } }),
    )
    vi.stubGlobal('fetch', fetchMock)

    const result = await listCurrencies()

    expect(fetchMock).toHaveBeenCalledWith('/api/currencies', { credentials: 'include' })
    expect(result).toEqual({ ok: true, currencies: [{ code: 'BRL', bcraName: 'REAL' }] })
  })

  it('una respuesta sin lista devuelve una lista vacía', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(response({ ok: true, body: {} })))

    expect(await listCurrencies()).toEqual({ ok: true, currencies: [] })
  })

  it('503 significa catálogo no disponible', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(response({ status: 503 })))

    expect(await listCurrencies()).toEqual({ ok: false, unavailable: true })
  })

  it('otro 5xx o un error de red es un error de red', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(response({ status: 500 })))
    expect(await listCurrencies()).toEqual({ ok: false, network: true })

    vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new TypeError('offline')))
    expect(await listCurrencies()).toEqual({ ok: false, network: true })
  })

  it('otros errores devuelven su estado', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(response({ status: 401 })))

    expect(await listCurrencies()).toEqual({ ok: false, status: 401 })
  })
})
