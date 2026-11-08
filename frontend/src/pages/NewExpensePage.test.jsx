import { act, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

vi.mock('../api/authClient', () => ({
  me: vi.fn(),
}))

import { me } from '../api/authClient'
import { AuthProvider } from '../auth/AuthContext'
import { AppRoutes } from '../App'
import NewExpensePage, { PROFILE_FALLBACK_NOTICE } from './NewExpensePage'
import { currentDateIn, deviceTimeZone } from '../hooks/useCurrentDate'

function renderPage() {
  return render(
    <MemoryRouter initialEntries={['/', '/expenses/new']} initialIndex={1}>
      <Routes>
        <Route path="/" element={<p>Pantalla principal</p>} />
        <Route path="/expenses/new" element={<NewExpensePage />} />
      </Routes>
    </MemoryRouter>,
  )
}

beforeEach(() => {
  me.mockReset()
  vi.unstubAllGlobals()
})

afterEach(() => {
  vi.useRealTimers()
  vi.unstubAllGlobals()
})

describe('NewExpensePage (T027/T031)', () => {
  it('precarga la moneda preferida del perfil y la fecha del día actual', async () => {
    me.mockResolvedValue({ ok: true, profile: { currencies: ['USD'] } })

    renderPage()

    await waitFor(() => expect(screen.getByLabelText('Moneda')).toHaveValue('USD'))
    expect(screen.getByLabelText('Fecha')).toHaveValue(currentDateIn(deviceTimeZone()))
  })

  it.each([
    ['perfil ausente', undefined],
    ['lista vacía', []],
    ['valor no soportado', ['XAU']],
  ])('usa ARS con %s', async (_label, currencies) => {
    me.mockResolvedValue({ ok: true, profile: { currencies } })

    renderPage()

    await waitFor(() => expect(me).toHaveBeenCalledTimes(1))
    expect(screen.getByLabelText('Moneda')).toHaveValue('ARS')
  })

  it('sin conexión usa ARS y muestra un aviso no bloqueante', async () => {
    me.mockResolvedValue({ ok: false, network: true })

    renderPage()

    expect(await screen.findByRole('status')).toHaveTextContent(PROFILE_FALLBACK_NOTICE)
    expect(screen.getByLabelText('Moneda')).toHaveValue('ARS')
    expect(screen.getByRole('button', { name: 'Guardar gasto' })).toBeInTheDocument()
  })

  it('actualiza la fecha al cruzar la medianoche solo si no fue modificada (US1-12)', async () => {
    vi.useFakeTimers()
    vi.setSystemTime(new Date('2026-10-21T15:00:00Z'))
    me.mockResolvedValue({ ok: true, profile: { currencies: ['USD'] } })
    const zone = deviceTimeZone()

    renderPage()
    await act(async () => {
      await Promise.resolve()
      await Promise.resolve()
    })

    const initialDate = currentDateIn(zone)
    expect(screen.getByLabelText('Fecha')).toHaveValue(initialDate)

    act(() => {
      vi.advanceTimersByTime(25 * 60 * 60 * 1000)
    })

    const nextDate = currentDateIn(zone)
    expect(nextDate).not.toBe(initialDate)
    expect(screen.getByLabelText('Fecha')).toHaveValue(nextDate)

    const dateInput = screen.getByLabelText('Fecha')
    fireEvent.change(dateInput, { target: { value: '01/01/2026' } })

    act(() => {
      vi.advanceTimersByTime(25 * 60 * 60 * 1000)
    })

    expect(screen.getByLabelText('Fecha')).toHaveValue('01/01/2026')
  })

  it('/expenses/new sin sesión redirige al login por ProtectedRoute (401)', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue({ ok: false, status: 401 }))

    render(
      <AuthProvider>
        <MemoryRouter initialEntries={['/expenses/new']}>
          <AppRoutes />
        </MemoryRouter>
      </AuthProvider>,
    )

    expect(
      await screen.findByRole('heading', { name: /iniciar sesión/i }),
    ).toBeInTheDocument()
    expect(me).not.toHaveBeenCalled()
  })
})
