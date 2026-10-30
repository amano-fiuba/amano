import { act, render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { MemoryRouter, Route, Routes } from 'react-router'
import { AuthProvider } from '../auth/AuthContext'
import ProtectedRoute from '../auth/ProtectedRoute'
import FriendsPage, { NO_PENDING_REQUESTS_MESSAGE } from './FriendsPage'

vi.mock('../api/friendsClient', () => ({
  RECEIVED_NETWORK_ERROR_MESSAGE: 'No se pudieron cargar tus solicitudes. Intentá nuevamente',
  listReceivedFriendshipRequests: vi.fn(),
}))

const friendsClient = await import('../api/friendsClient')

const ME = { id: 1, firstName: 'Camila', email: 'camila@example.com', currencies: [] }
const ANA = {
  id: 5,
  status: 'PENDING',
  createdAt: '2026-10-30T12:00:00Z',
  sender: { id: 3, firstName: 'Ana', lastName: 'Pérez', email: 'ana@example.com' },
}
const JUAN = {
  id: 6,
  status: 'PENDING',
  createdAt: '2026-10-29T12:00:00Z',
  sender: { id: 4, firstName: 'Juan', lastName: null, email: 'juan@example.com' },
}

beforeEach(() => {
  vi.unstubAllGlobals()
  vi.stubGlobal(
    'fetch',
    vi.fn().mockResolvedValue({ ok: true, status: 200, json: () => Promise.resolve(ME) }),
  )
  friendsClient.listReceivedFriendshipRequests.mockReset()
})

afterEach(() => {
  vi.unstubAllGlobals()
})

function renderPage() {
  return render(
    <AuthProvider>
      <MemoryRouter initialEntries={['/friends']}>
        <Routes>
          <Route
            path="/friends"
            element={
              <ProtectedRoute>
                <FriendsPage />
              </ProtectedRoute>
            }
          />
          <Route path="/login" element={<div>PANTALLA LOGIN</div>} />
        </Routes>
      </MemoryRouter>
    </AuthProvider>,
  )
}

describe('FriendsPage (feature 011, US3)', () => {
  it('muestra el título y el estado de carga mientras consulta', async () => {
    let resolveList
    friendsClient.listReceivedFriendshipRequests.mockReturnValue(
      new Promise((resolve) => {
        resolveList = resolve
      }),
    )
    renderPage()

    expect(await screen.findByRole('heading', { name: 'Amigos' })).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: 'Solicitudes' })).toBeInTheDocument()
    expect(screen.getByRole('status')).toHaveTextContent('Cargando solicitudes…')

    await act(async () => {
      resolveList({ ok: true, requests: [] })
    })

    expect(screen.queryByText('Cargando solicitudes…')).not.toBeInTheDocument()
  })

  it('lista las solicitudes recibidas con nombre, correo, avatar por defecto y estado Pendiente', async () => {
    friendsClient.listReceivedFriendshipRequests.mockResolvedValue({
      ok: true,
      requests: [ANA, JUAN],
    })
    renderPage()

    expect(await screen.findByTestId('friend-request-5')).toBeInTheDocument()
    expect(screen.getByText('Ana Pérez')).toBeInTheDocument()
    expect(screen.getByText('ana@example.com')).toBeInTheDocument()
    expect(screen.getByText('Juan')).toBeInTheDocument()
    expect(screen.queryByText(/null/)).not.toBeInTheDocument()
    expect(screen.getByText('2 pendientes')).toBeInTheDocument()
    expect(screen.getAllByText('Pendiente')).toHaveLength(2)

    const list = screen.getByRole('list', { name: 'Solicitudes pendientes' })
    const avatars = list.querySelectorAll('img')
    expect(avatars).toHaveLength(2)
    avatars.forEach((img) => expect(img).toHaveAttribute('src', '/avatar-default.png'))

    const cards = list.querySelectorAll('li')
    expect(cards[0]).toHaveTextContent('Ana Pérez')
    expect(cards[1]).toHaveTextContent('Juan')
  })

  it('muestra el estado vacío exacto cuando no hay solicitudes pendientes', async () => {
    friendsClient.listReceivedFriendshipRequests.mockResolvedValue({ ok: true, requests: [] })
    renderPage()

    expect(await screen.findByText(NO_PENDING_REQUESTS_MESSAGE)).toHaveAttribute('role', 'status')
    expect(screen.queryByRole('list', { name: 'Solicitudes pendientes' })).not.toBeInTheDocument()
  })

  it('error de red muestra el mensaje fijo y permite reintentar', async () => {
    friendsClient.listReceivedFriendshipRequests
      .mockResolvedValueOnce({ ok: false, network: true })
      .mockResolvedValueOnce({ ok: true, requests: [ANA] })
    const user = userEvent.setup()
    renderPage()

    const alert = await screen.findByRole('alert')
    expect(alert).toHaveTextContent(
      'No se pudieron cargar tus solicitudes. Intentá nuevamente',
    )

    await user.click(screen.getByRole('button', { name: 'Reintentar' }))

    expect(await screen.findByText('Ana Pérez')).toBeInTheDocument()
    expect(friendsClient.listReceivedFriendshipRequests).toHaveBeenCalledTimes(2)
    expect(screen.queryByRole('alert')).not.toBeInTheDocument()
  })

  it('401 cierra la sesión y lleva al login', async () => {
    friendsClient.listReceivedFriendshipRequests.mockResolvedValue({
      ok: false,
      status: 401,
      detail: '',
    })
    renderPage()

    expect(await screen.findByText('PANTALLA LOGIN')).toBeInTheDocument()
  })
})
