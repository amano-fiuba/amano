import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { MemoryRouter, Route, Routes } from 'react-router'
import { AuthProvider } from '../auth/AuthContext'
import RegisterPage from './RegisterPage'
import DashboardPage from '../pages/DashboardPage'
import ProtectedRoute from '../auth/ProtectedRoute'

// The auth client is task T021: mocked here so the page contract (T017) is
// defined first, with shapes styled by contracts/auth-api.md. The Network
// message constant must exist for the FR-019 branch of RegisterForm.
vi.mock('../api/authClient', () => ({
  NETWORK_ERROR_MESSAGE: 'No se pudo completar el registro. Intenta nuevamente',
  register: vi.fn(),
  me: vi.fn(),
}))

const authClient = await import('../api/authClient')

beforeEach(() => {
  vi.unstubAllGlobals()
  vi.stubGlobal('fetch', vi.fn().mockResolvedValue({ ok: false, status: 401 }))
  authClient.me.mockResolvedValue({ ok: false, status: 401 })
})

afterEach(() => {
  vi.restoreAllMocks()
  vi.unstubAllGlobals()
})

function setupApp() {
  return render(
    <AuthProvider>
      <MemoryRouter initialEntries={['/register']}>
        <Routes>
          <Route path="/register" element={<RegisterPage />} />
          <Route
            path="/dashboard"
            element={
              <ProtectedRoute>
                <DashboardPage />
              </ProtectedRoute>
            }
          />
        </Routes>
      </MemoryRouter>
    </AuthProvider>,
  )
}

async function fillForm(user) {
  await user.type(screen.getByLabelText('Nombre'), 'Ana')
  await user.type(screen.getByLabelText('Correo electrónico'), 'ana.perez@example.com')
  await user.type(screen.getByLabelText('Contraseña'), 'Password123!')
  await user.type(screen.getByLabelText('Confirmar contraseña'), 'Password123!')
  await user.click(screen.getByLabelText(/Términos y Condiciones/i))
}

describe('RegisterPage (US1/US2 behaviors required by T017)', () => {
  it('envío válido → navegación al dashboard con la sesión poblada (FR-016)', async () => {
    const user = userEvent.setup()
    authClient.me.mockResolvedValue({ ok: false, status: 401 })
    authClient.register.mockResolvedValue({
      ok: true,
      profile: {
        id: 1,
        firstName: 'Ana',
        lastName: null,
        email: 'ana.perez@example.com',
        currencies: [],
        cbuCvu: null,
        hasCustomAvatar: false,
      },
    })
    setupApp()

    await fillForm(user)
    await user.click(screen.getByRole('button', { name: /crear cuenta/i }))

    expect(await screen.findByText(/bienvenida/i)).toBeInTheDocument()
    expect(screen.getByTestId('dashboard-email')).toHaveTextContent('ana.perez@example.com')
    expect(screen.getByRole('img', { name: '@avatar' })).toHaveAttribute(
      'src',
      '/avatar-default.png',
    )
    expect(screen.getByTestId('dashboard-currencies')).toHaveTextContent('Monedas: ARS')
  })

  it('409 → muestra el mensaje exacto y conserva lo completado (FR-018)', async () => {
    const user = userEvent.setup()
    authClient.register.mockResolvedValue({
      ok: false,
      status: 409,
      detail: 'El correo electrónico ya se encuentra registrado',
      fields: { email: 'El correo electrónico ya se encuentra registrado' },
    })
    setupApp()

    await fillForm(user)
    await user.click(screen.getByRole('button', { name: /crear cuenta/i }))

    expect(
      await screen.findByText('El correo electrónico ya se encuentra registrado'),
    ).toBeInTheDocument()
    expect(screen.getByLabelText('Correo electrónico')).toHaveDisplayValue(
      'ana.perez@example.com',
    )
    expect(authClient.register).toHaveBeenCalledTimes(1)
  })

  it('fallo de red → mensaje fijo, datos intactos y botón rehabilitado (FR-019/FR-017)', async () => {
    const user = userEvent.setup()
    authClient.register.mockResolvedValue({ ok: false, network: true })
    setupApp()

    await fillForm(user)
    await user.click(screen.getByRole('button', { name: /crear cuenta/i }))

    expect(await screen.findByText("No se pudo completar el registro. Intenta nuevamente")).toBeInTheDocument()
    expect(screen.getByLabelText('Correo electrónico')).toHaveDisplayValue(
      'ana.perez@example.com',
    )
    expect(screen.getByRole('button', { name: /crear cuenta/i })).toBeEnabled()
  })

  it('400 sin properties.fields muestra el detail genérico (FR-018)', async () => {
    const user = userEvent.setup()
    authClient.register.mockResolvedValue({
      ok: false,
      status: 400,
      detail: 'La solicitud contiene campos inválidos',
      fields: {},
    })
    setupApp()

    await fillForm(user)
    await user.click(screen.getByRole('button', { name: /crear cuenta/i }))

    expect(
      await screen.findByText('La solicitud contiene campos inválidos'),
    ).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /crear cuenta/i })).toBeEnabled()
  })

  it('mientras se envía el botón está deshabilitado y solo hay una petición (FR-015)', async () => {
    const user = userEvent.setup()
    let releaseResponse
    authClient.register.mockImplementation(
      () =>
        new Promise((resolve) => {
          releaseResponse = () => resolve({ ok: false, network: true })
        }),
    )
    setupApp()

    await fillForm(user)
    await user.click(screen.getByRole('button', { name: /crear cuenta/i }))

    const button = screen.getByRole('button', { name: /crear cuenta/i })
    expect(button).toBeDisabled()

    releaseResponse()
    expect(await screen.findByText("No se pudo completar el registro. Intenta nuevamente")).toBeInTheDocument()
    expect(button).toBeEnabled()
    expect(authClient.register).toHaveBeenCalledTimes(1)
  })
})
