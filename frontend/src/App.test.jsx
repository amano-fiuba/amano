import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import App, { AppRoutes } from './App'
import { AuthProvider } from './auth/AuthContext'
import { INVALID_LINK_MESSAGE } from './pages/VerifyEmailPage'

// AuthProvider consulta /api/users/me al montar; en pruebas offline devuelve 401.
beforeEach(() => {
  vi.unstubAllGlobals()
  vi.stubGlobal('fetch', vi.fn().mockResolvedValue({ ok: false, status: 401 }))
})

afterEach(() => {
  vi.unstubAllGlobals()
})

describe('App (T015)', () => {
  it('incrementa el contador al hacer clic en el botón (landing en /)', async () => {
    const user = userEvent.setup()
    render(<App />)

    await user.click(screen.getByRole('button', { name: /count is 0/i }))

    expect(
      screen.getByRole('button', { name: /count is 1/i }),
    ).toBeInTheDocument()
  })
})

describe('App rutas (T005/T009, specs/002-user-profile)', () => {
  it('/profile sin sesión redirige al flujo de registro (FR-008)', async () => {
    render(
      <AuthProvider>
        <MemoryRouter initialEntries={['/profile']}>
          <AppRoutes />
        </MemoryRouter>
      </AuthProvider>,
    )

    expect(await screen.findByText(/crear\s+tu\s+cuenta/i)).toBeInTheDocument()
    // FR-008: no hay destello de datos del perfil.
    expect(screen.queryByTestId('profile-email')).not.toBeInTheDocument()
  })

  it('/profile con sesión renderiza la pantalla de perfil (FR-001)', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue({
        ok: true,
        status: 200,
        json: () =>
          Promise.resolve({
            id: 7,
            firstName: 'Ana',
            lastName: 'Pérez',
            email: 'ana.perez@example.com',
            currencies: [],
            cbuCvu: null,
            hasCustomAvatar: false,
          }),
      }),
    )
    render(
      <AuthProvider>
        <MemoryRouter initialEntries={['/profile']}>
          <AppRoutes />
        </MemoryRouter>
      </AuthProvider>,
    )

    expect(await screen.findByRole('heading', { name: /mi perfil/i })).toBeInTheDocument()
  })
})

describe('App rutas de verificación (T035, specs/003-email-verification)', () => {
  it('/verify-email sin token es una ruta pública que muestra el enlace inválido (FR-008)', async () => {
    render(
      <AuthProvider>
        <MemoryRouter initialEntries={['/verify-email']}>
          <AppRoutes />
        </MemoryRouter>
      </AuthProvider>,
    )

    expect(await screen.findByText(INVALID_LINK_MESSAGE)).toBeInTheDocument()
  })

  it('/verify-email/expired es una ruta pública con la pantalla de vencido (FR-006)', async () => {
    render(
      <AuthProvider>
        <MemoryRouter initialEntries={['/verify-email/expired']}>
          <AppRoutes />
        </MemoryRouter>
      </AuthProvider>,
    )

    expect(
      await screen.findByRole('heading', { name: /el enlace expiró/i }),
    ).toBeInTheDocument()
  })
})
