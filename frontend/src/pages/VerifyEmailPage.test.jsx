import { render, screen } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import VerifyEmailPage, {
  INVALID_LINK_MESSAGE,
  USED_LINK_MESSAGE,
} from './VerifyEmailPage'

// The page only needs setUser from the session store (T029 contract).
const { setUser } = vi.hoisted(() => ({ setUser: vi.fn() }))
vi.mock('../auth/AuthContext', () => ({
  useAuth: () => ({ user: null, setUser, loading: false }),
}))

// US2 contract (task T029, TDD: written BEFORE the implementation): a valid
// token signs the user in and lands on the dashboard (FR-005); 400 shows the
// exact invalid message (FR-008); 409 the already-used one (FR-007); 410 is
// routed to the expired screen (FR-006); a missing token is invalid too.
const PROFILE = { id: 7, firstName: 'Ana', email: 'ana.perez@example.com' }

beforeEach(() => {
  vi.unstubAllGlobals()
  setUser.mockClear()
})

afterEach(() => {
  vi.unstubAllGlobals()
})

function stubFetch(status, body) {
  vi.stubGlobal(
    'fetch',
    vi.fn().mockResolvedValue({
      ok: status >= 200 && status < 300,
      status,
      json: () => Promise.resolve(body),
    }),
  )
}

function renderPage(initialEntry) {
  return render(
    <MemoryRouter initialEntries={[initialEntry]}>
      <Routes>
        <Route path="/verify-email" element={<VerifyEmailPage />} />
        <Route path="/verify-email/expired" element={<div>PANTALLA EXPIRADO</div>} />
        <Route path="/dashboard" element={<div>DASHBOARD</div>} />
      </Routes>
    </MemoryRouter>,
  )
}

describe('VerifyEmailPage (T029)', () => {
  it('token válido: setUser con el perfil y navegación al dashboard (FR-005)', async () => {
    stubFetch(200, PROFILE)

    renderPage('/verify-email?token=token-valido')

    expect(await screen.findByText('DASHBOARD')).toBeInTheDocument()
    expect(setUser).toHaveBeenCalledWith(PROFILE)
    expect(globalThis.fetch).toHaveBeenCalledWith(
      '/api/auth/verify-email',
      expect.objectContaining({ method: 'POST', credentials: 'include' }),
    )
  })

  it('400: muestra el mensaje exacto de enlace inválido (FR-008)', async () => {
    stubFetch(400, { detail: 'El enlace de verificación no es válido' })

    renderPage('/verify-email?token=alterado')

    expect(await screen.findByText(INVALID_LINK_MESSAGE)).toBeInTheDocument()
    expect(setUser).not.toHaveBeenCalled()
  })

  it('409: muestra el mensaje exacto de enlace ya utilizado (FR-007)', async () => {
    stubFetch(409, { detail: USED_LINK_MESSAGE })

    renderPage('/verify-email?token=usado')

    expect(await screen.findByText(USED_LINK_MESSAGE)).toBeInTheDocument()
    expect(setUser).not.toHaveBeenCalled()
  })

  it('410: navega a la pantalla de enlace vencido (FR-006)', async () => {
    stubFetch(410, { detail: 'El enlace de verificación ha expirado' })

    renderPage('/verify-email?token=vencido')

    expect(await screen.findByText('PANTALLA EXPIRADO')).toBeInTheDocument()
  })

  it('sin token en la URL: mensaje de enlace inválido y sin llamar al backend', async () => {
    stubFetch(200, PROFILE)

    renderPage('/verify-email')

    expect(await screen.findByText(INVALID_LINK_MESSAGE)).toBeInTheDocument()
    expect(globalThis.fetch).not.toHaveBeenCalled()
  })
})
