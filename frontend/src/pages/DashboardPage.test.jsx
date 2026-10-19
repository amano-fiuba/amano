import { render, screen } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { MemoryRouter } from 'react-router'
import { AuthProvider } from '../auth/AuthContext'
import DashboardPage from './DashboardPage'

// Dashboard contract (T024; FR-016, FR-021, SC-002): the profile comes through
// the session store (AuthProvider fetches /api/users/me directly) and the
// dashboard refresh also goes through /api/users/me (FR-021); the default
// display currency is ARS (FR-011/Q1).
const PROFILE = {
  id: 7,
  firstName: 'Ana',
  lastName: null,
  email: 'ana.perez@example.com',
  currencies: [],
  cbuCvu: null,
  hasCustomAvatar: false,
}

beforeEach(() => {
  vi.unstubAllGlobals()
})

afterEach(() => {
  vi.unstubAllGlobals()
})

function renderDashboard(meProfile = PROFILE, initialEntries = ['/dashboard']) {
  vi.stubGlobal(
    'fetch',
    vi.fn().mockResolvedValue({
      ok: meProfile !== null,
      status: meProfile === null ? 401 : 200,
      json: () => Promise.resolve(meProfile),
    }),
  )
  return render(
    <AuthProvider>
      <MemoryRouter initialEntries={initialEntries}>
        <DashboardPage />
      </MemoryRouter>
    </AuthProvider>,
  )
}

describe('DashboardPage (T024)', () => {
  it('sin sesión (401) no renderiza el perfil', async () => {
    const { container } = renderDashboard(null)
    await new Promise((resolve) => setTimeout(resolve, 0))

    expect(container.firstChild).toBeNull()
  })

  it('renderiza nombre, email, avatar por defecto y monedas default ARS (FR-011)', async () => {
    renderDashboard(PROFILE)

    expect(await screen.findByText(/bienvenida, ana/i)).toBeInTheDocument()
    expect(screen.getByTestId('dashboard-email')).toHaveTextContent('ana.perez@example.com')
    expect(screen.getByRole('img', { name: '@avatar' })).toHaveAttribute(
      'src',
      '/avatar-default.png',
    )
    expect(screen.getByTestId('dashboard-currencies')).toHaveTextContent('Monedas: ARS')
  })

  it('con avatar propio usa el avatarUrl del contrato; monedas persistidas se muestran', async () => {
    renderDashboard({
      ...PROFILE,
      hasCustomAvatar: true,
      avatarUrl: '/api/users/7/avatar',
      currencies: ['USD', 'EUR'],
    })

    expect(await screen.findByText(/bienvenida, ana/i)).toBeInTheDocument()
    expect(screen.getByRole('img', { name: '@avatar' })).toHaveAttribute(
      'src',
      '/api/users/7/avatar',
    )
    expect(screen.getByTestId('dashboard-currencies')).toHaveTextContent('Monedas: USD, EUR')
  })

  it('refresca el perfil vía GET /api/users/me al montar (FR-021)', async () => {
    renderDashboard({ ...PROFILE, firstName: 'Anabel' })

    expect(await screen.findByText(/bienvenida, anabel/i)).toBeInTheDocument()
    // Both the AuthContext bootstrapping and the refresh go through the same contract.
    expect(globalThis.fetch).toHaveBeenCalledWith('/api/users/me', { credentials: 'include' })
  })

  it('expone acceso a su perfil en ≤2 interacciones (T007, SC-001)', async () => {
    renderDashboard(PROFILE)

    const link = await screen.findByRole('link', { name: /mi perfil/i })
    expect(link).toHaveAttribute('href', '/profile')
  })

  it('sin venir de la verificación mantiene el saludo y no muestra bienvenida (regresión T036)', async () => {
    renderDashboard(PROFILE)

    expect(await screen.findByText(/bienvenida, ana/i)).toBeInTheDocument()
    expect(screen.queryByTestId('dashboard-verified-welcome')).not.toBeInTheDocument()
  })

  it('al llegar desde el canje muestra la bienvenida de cuenta verificada (FR-005/T036)', async () => {
    renderDashboard(PROFILE, [
      { pathname: '/dashboard', state: { verifiedWelcome: true } },
    ])

    expect(
      await screen.findByTestId('dashboard-verified-welcome'),
    ).toHaveTextContent(/verificada correctamente/i)
    expect(screen.getByText(/bienvenida, ana/i)).toBeInTheDocument()
  })
})
