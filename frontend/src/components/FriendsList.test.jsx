import { act, render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { MemoryRouter, Route, Routes } from 'react-router'
import { AuthProvider } from '../auth/AuthContext'
import ProtectedRoute from '../auth/ProtectedRoute'
import FriendsList, {
  DELETE_SUCCESS_MESSAGE,
  NOT_FRIEND_MESSAGE,
  NO_FRIENDS_MESSAGE,
} from './FriendsList'

vi.mock('../api/friendsClient', () => ({
  FRIENDS_NETWORK_ERROR_MESSAGE: 'No se pudo cargar tu lista de amigos. Intentá nuevamente',
  DELETE_FRIEND_NETWORK_ERROR_MESSAGE: 'No se pudo eliminar al amigo. Intentá nuevamente',
  listFriends: vi.fn(),
  checkFriendDeletion: vi.fn(),
  deleteFriend: vi.fn(),
}))

const friendsClient = await import('../api/friendsClient')

const ME = { id: 1, firstName: 'Camila', email: 'camila@example.com', currencies: [] }
const ANA = {
  id: 12,
  firstName: 'Ana',
  lastName: 'Pérez',
  email: 'ana@example.com',
  // 02:00 UTC on 31/10 is still 30/10 in Argentina (UTC-3): the date is shown
  // in Argentina's timezone, not the browser's (research.md D6).
  friendsSince: '2026-10-31T02:00:00Z',
}
const BRUNO = {
  id: 8,
  firstName: 'Bruno',
  lastName: null,
  email: 'bruno@example.com',
  friendsSince: '2026-10-29T15:00:00Z',
}

beforeEach(() => {
  vi.unstubAllGlobals()
  vi.stubGlobal(
    'fetch',
    vi.fn().mockResolvedValue({ ok: true, status: 200, json: () => Promise.resolve(ME) }),
  )
  friendsClient.listFriends.mockReset()
  friendsClient.checkFriendDeletion.mockReset()
  friendsClient.deleteFriend.mockReset()
})

afterEach(() => {
  vi.unstubAllGlobals()
})

function ui(reloadKey = 0) {
  return (
    <AuthProvider>
      <MemoryRouter initialEntries={['/friends']}>
        <Routes>
          <Route
            path="/friends"
            element={
              <ProtectedRoute>
                <FriendsList reloadKey={reloadKey} />
              </ProtectedRoute>
            }
          />
          <Route path="/login" element={<div>PANTALLA LOGIN</div>} />
          <Route path="/users/search" element={<div>PANTALLA BUSCADOR</div>} />
        </Routes>
      </MemoryRouter>
    </AuthProvider>
  )
}

describe('FriendsList (feature 013)', () => {
  it('US1: muestra el título y el estado de carga mientras consulta', async () => {
    let resolveList
    friendsClient.listFriends.mockReturnValue(
      new Promise((resolve) => {
        resolveList = resolve
      }),
    )
    render(ui())

    expect(await screen.findByRole('heading', { name: 'Mis amigos' })).toBeInTheDocument()
    expect(screen.getByRole('status')).toHaveTextContent('Cargando amigos…')

    await act(async () => {
      resolveList({ ok: true, friends: [ANA] })
    })

    expect(screen.queryByText('Cargando amigos…')).not.toBeInTheDocument()
  })

  it('US1: lista cada amigo con nombre, correo, avatar por defecto y "Amigos desde"', async () => {
    friendsClient.listFriends.mockResolvedValue({ ok: true, friends: [ANA, BRUNO] })
    render(ui())

    const list = await screen.findByRole('list', { name: 'Lista de amigos' })
    const items = within(list).getAllByRole('listitem')
    expect(items).toHaveLength(2)

    const ana = within(items[0])
    expect(ana.getByText('Ana Pérez')).toBeInTheDocument()
    expect(ana.getByText('ana@example.com')).toBeInTheDocument()
    expect(ana.getByText('Amigos desde 30/10/2026')).toBeInTheDocument()
    expect(items[0].querySelector('img')).toHaveAttribute('src', '/avatar-default.png')

    // Missing last name: only the first name, never "null".
    const bruno = within(items[1])
    expect(bruno.getByText('Bruno')).toBeInTheDocument()
    expect(bruno.getByText('Amigos desde 29/10/2026')).toBeInTheDocument()
    expect(screen.queryByText(/null/)).not.toBeInTheDocument()
  })

  it('muestra la foto del amigo cuando tiene una', async () => {
    friendsClient.listFriends.mockResolvedValue({
      ok: true,
      friends: [{ ...ANA, avatarUrl: '/api/users/12/avatar' }, BRUNO],
    })
    render(ui())

    const list = await screen.findByRole('list', { name: 'Lista de amigos' })
    const [ana, bruno] = within(list).getAllByRole('listitem')
    expect(ana.querySelector('img')).toHaveAttribute('src', '/api/users/12/avatar')
    expect(bruno.querySelector('img')).toHaveAttribute('src', '/avatar-default.png')
  })

  it('US1: respeta el orden del servidor', async () => {
    friendsClient.listFriends.mockResolvedValue({ ok: true, friends: [BRUNO, ANA] })
    render(ui())

    const items = within(await screen.findByRole('list', { name: 'Lista de amigos' }))
      .getAllByRole('listitem')
    expect(items.map((item) => item.dataset.testid)).toEqual(['friend-8', 'friend-12'])
  })

  it('US1: el contador usa singular y plural', async () => {
    friendsClient.listFriends.mockResolvedValueOnce({ ok: true, friends: [ANA] })
    const { rerender } = render(ui(0))

    expect(await screen.findByText('1 amigo')).toBeInTheDocument()

    friendsClient.listFriends.mockResolvedValueOnce({ ok: true, friends: [ANA, BRUNO] })
    rerender(ui(1))

    expect(await screen.findByText('2 amigos')).toBeInTheDocument()
  })

  it('US1: omite la fecha si el servidor no envía una válida', async () => {
    friendsClient.listFriends.mockResolvedValue({
      ok: true,
      friends: [{ ...ANA, friendsSince: null }],
    })
    render(ui())

    expect(await screen.findByText('Ana Pérez')).toBeInTheDocument()
    expect(screen.queryByText(/Amigos desde/)).not.toBeInTheDocument()
  })

  it('US2: sin amigos muestra el vacío exacto y el acceso al buscador', async () => {
    friendsClient.listFriends.mockResolvedValue({ ok: true, friends: [] })
    const user = userEvent.setup()
    render(ui())

    expect(await screen.findByText(NO_FRIENDS_MESSAGE)).toBeInTheDocument()
    expect(NO_FRIENDS_MESSAGE).toBe('Todavía no tenés amigos')
    expect(screen.queryByRole('list', { name: 'Lista de amigos' })).not.toBeInTheDocument()
    expect(screen.queryByText(/^\d+ amigos?$/)).not.toBeInTheDocument()

    await user.click(screen.getByRole('link', { name: 'Buscar usuarios' }))
    expect(await screen.findByText('PANTALLA BUSCADOR')).toBeInTheDocument()
  })

  it('US3: error de red muestra el mensaje fijo y "Reintentar" vuelve a consultar', async () => {
    friendsClient.listFriends
      .mockResolvedValueOnce({ ok: false, network: true })
      .mockResolvedValueOnce({ ok: true, friends: [ANA] })
    const user = userEvent.setup()
    render(ui())

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'No se pudo cargar tu lista de amigos. Intentá nuevamente',
    )
    expect(screen.queryByRole('list', { name: 'Lista de amigos' })).not.toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: 'Reintentar' }))

    expect(await screen.findByText('Ana Pérez')).toBeInTheDocument()
    expect(screen.queryByRole('alert')).not.toBeInTheDocument()
    expect(friendsClient.listFriends).toHaveBeenCalledTimes(2)
  })

  it('US3: un rechazo inesperado o un 4xx distinto de 401 no deja datos obsoletos', async () => {
    friendsClient.listFriends
      .mockResolvedValueOnce({ ok: true, friends: [ANA] })
      .mockRejectedValueOnce(new Error('boom'))
      .mockResolvedValueOnce({ ok: false, status: 403, detail: 'Prohibido' })
    const { rerender } = render(ui(0))
    expect(await screen.findByText('Ana Pérez')).toBeInTheDocument()

    rerender(ui(1))
    expect(await screen.findByRole('alert')).toBeInTheDocument()
    expect(screen.queryByText('Ana Pérez')).not.toBeInTheDocument()

    rerender(ui(2))
    await act(async () => {})
    expect(screen.getByRole('alert')).toHaveTextContent(
      'No se pudo cargar tu lista de amigos. Intentá nuevamente',
    )
    expect(screen.queryByText('PANTALLA LOGIN')).not.toBeInTheDocument()
  })

  it('US3: 401 cierra la sesión y lleva al login', async () => {
    friendsClient.listFriends.mockResolvedValue({ ok: false, status: 401, detail: '' })
    render(ui())

    expect(await screen.findByText('PANTALLA LOGIN')).toBeInTheDocument()
  })

  it('US3: cambiar reloadKey vuelve a consultar y muestra al amigo nuevo', async () => {
    friendsClient.listFriends
      .mockResolvedValueOnce({ ok: true, friends: [] })
      .mockResolvedValueOnce({ ok: true, friends: [ANA] })
    const { rerender } = render(ui(0))
    expect(await screen.findByText(NO_FRIENDS_MESSAGE)).toBeInTheDocument()

    rerender(ui(1))

    expect(await screen.findByText('Ana Pérez')).toBeInTheDocument()
    expect(screen.queryByText(NO_FRIENDS_MESSAGE)).not.toBeInTheDocument()
    expect(friendsClient.listFriends).toHaveBeenCalledTimes(2)
  })

  it('US3: una respuesta vieja nunca pisa a una más nueva', async () => {
    let resolveOld
    friendsClient.listFriends
      .mockReturnValueOnce(
        new Promise((resolve) => {
          resolveOld = resolve
        }),
      )
      .mockResolvedValueOnce({ ok: true, friends: [BRUNO] })
    const { rerender } = render(ui(0))
    await screen.findByRole('heading', { name: 'Mis amigos' })

    rerender(ui(1))
    expect(await screen.findByText('Bruno')).toBeInTheDocument()

    await act(async () => {
      resolveOld({ ok: true, friends: [ANA] })
    })

    expect(screen.getByText('Bruno')).toBeInTheDocument()
    expect(screen.queryByText('Ana Pérez')).not.toBeInTheDocument()
  })

  it('US1 (016): eliminar un amigo pide confirmación y actualiza la lista', async () => {
    friendsClient.listFriends.mockResolvedValue({ ok: true, friends: [ANA] })
    friendsClient.checkFriendDeletion.mockResolvedValue({ ok: true, canDelete: true })
    friendsClient.deleteFriend.mockResolvedValue({ ok: true })
    const user = userEvent.setup()
    render(ui())

    const card = await screen.findByTestId('friend-12')
    await user.click(within(card).getByRole('button', { name: 'Eliminar amigo' }))

    expect(friendsClient.checkFriendDeletion).toHaveBeenCalledWith(12)
    expect(screen.getByRole('dialog', { name: 'Eliminar amigo' })).toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: 'Eliminar' }))

    expect(await screen.findByText(DELETE_SUCCESS_MESSAGE)).toBeInTheDocument()
    expect(friendsClient.deleteFriend).toHaveBeenCalledWith(12)
    expect(screen.queryByTestId('friend-12')).not.toBeInTheDocument()
    expect(screen.getByText(NO_FRIENDS_MESSAGE)).toBeInTheDocument()
  })

  it('US1 (016): cancelar el diálogo no elimina ni llama al backend', async () => {
    friendsClient.listFriends.mockResolvedValue({ ok: true, friends: [ANA] })
    friendsClient.checkFriendDeletion.mockResolvedValue({ ok: true, canDelete: true })
    const user = userEvent.setup()
    render(ui())

    const card = await screen.findByTestId('friend-12')
    await user.click(within(card).getByRole('button', { name: 'Eliminar amigo' }))
    await user.click(screen.getByRole('button', { name: 'Cerrar' }))

    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
    expect(friendsClient.deleteFriend).not.toHaveBeenCalled()
    expect(screen.getByTestId('friend-12')).toBeInTheDocument()
  })

  it('US2 (016): la deuda directa muestra el aviso y no abre el diálogo', async () => {
    friendsClient.listFriends.mockResolvedValue({ ok: true, friends: [ANA] })
    friendsClient.checkFriendDeletion.mockResolvedValue({
      ok: true,
      canDelete: false,
      reason: 'No podés eliminar a este amigo: tenés una deuda pendiente.',
    })
    const user = userEvent.setup()
    render(ui())

    const card = await screen.findByTestId('friend-12')
    await user.click(within(card).getByRole('button', { name: 'Eliminar amigo' }))

    expect(await screen.findByText(/tenés una deuda pendiente/)).toBeInTheDocument()
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
    expect(friendsClient.deleteFriend).not.toHaveBeenCalled()
  })

  it('US2 (016): un 403 al confirmar conserva la tarjeta y muestra el motivo', async () => {
    friendsClient.listFriends.mockResolvedValue({ ok: true, friends: [ANA] })
    friendsClient.checkFriendDeletion.mockResolvedValue({ ok: true, canDelete: true })
    friendsClient.deleteFriend.mockResolvedValue({
      ok: false,
      status: 403,
      detail: 'No podés eliminar a este amigo: tenés una deuda pendiente.',
    })
    const user = userEvent.setup()
    render(ui())

    const card = await screen.findByTestId('friend-12')
    await user.click(within(card).getByRole('button', { name: 'Eliminar amigo' }))
    await user.click(screen.getByRole('button', { name: 'Eliminar' }))

    expect(await screen.findByText(/tenés una deuda pendiente/)).toBeInTheDocument()
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
    expect(screen.getByTestId('friend-12')).toBeInTheDocument()
    expect(screen.queryByText(DELETE_SUCCESS_MESSAGE)).not.toBeInTheDocument()
  })

  it('US3 (016): un 404 retira la tarjeta obsoleta y muestra el mensaje neutro', async () => {
    friendsClient.listFriends.mockResolvedValue({ ok: true, friends: [ANA] })
    friendsClient.checkFriendDeletion.mockResolvedValue({ ok: true, canDelete: true })
    friendsClient.deleteFriend.mockResolvedValue({
      ok: false,
      status: 404,
      detail: NOT_FRIEND_MESSAGE,
    })
    const user = userEvent.setup()
    render(ui())

    const card = await screen.findByTestId('friend-12')
    await user.click(within(card).getByRole('button', { name: 'Eliminar amigo' }))
    await user.click(screen.getByRole('button', { name: 'Eliminar' }))

    expect(await screen.findByText(NOT_FRIEND_MESSAGE)).toBeInTheDocument()
    expect(screen.queryByTestId('friend-12')).not.toBeInTheDocument()
    expect(screen.queryByText(DELETE_SUCCESS_MESSAGE)).not.toBeInTheDocument()
  })

  it('US3 (016): un error de red mantiene la tarjeta y no confirma el éxito', async () => {
    friendsClient.listFriends.mockResolvedValue({ ok: true, friends: [ANA] })
    friendsClient.checkFriendDeletion.mockResolvedValue({ ok: true, canDelete: true })
    friendsClient.deleteFriend.mockResolvedValue({ ok: false, network: true })
    const user = userEvent.setup()
    render(ui())

    const card = await screen.findByTestId('friend-12')
    await user.click(within(card).getByRole('button', { name: 'Eliminar amigo' }))
    await user.click(screen.getByRole('button', { name: 'Eliminar' }))

    expect(
      await screen.findByText('No se pudo eliminar al amigo. Intentá nuevamente'),
    ).toBeInTheDocument()
    expect(screen.getByRole('dialog')).toBeInTheDocument()
    expect(screen.getByTestId('friend-12')).toBeInTheDocument()
    expect(screen.queryByText(DELETE_SUCCESS_MESSAGE)).not.toBeInTheDocument()
  })

  it('US3 (016): un 401 durante la elegibilidad cierra la sesión', async () => {
    friendsClient.listFriends.mockResolvedValue({ ok: true, friends: [ANA] })
    friendsClient.checkFriendDeletion.mockResolvedValue({ ok: false, status: 401 })
    const user = userEvent.setup()
    render(ui())

    const card = await screen.findByTestId('friend-12')
    await user.click(within(card).getByRole('button', { name: 'Eliminar amigo' }))

    expect(await screen.findByText('PANTALLA LOGIN')).toBeInTheDocument()
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
  })

  it('US3 (016): un error al consultar la elegibilidad no abre el diálogo', async () => {
    friendsClient.listFriends.mockResolvedValue({ ok: true, friends: [ANA] })
    friendsClient.checkFriendDeletion.mockResolvedValue({ ok: false, network: true })
    const user = userEvent.setup()
    render(ui())

    const card = await screen.findByTestId('friend-12')
    await user.click(within(card).getByRole('button', { name: 'Eliminar amigo' }))

    expect(
      await screen.findByText('No se pudo eliminar al amigo. Intentá nuevamente'),
    ).toBeInTheDocument()
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
    expect(friendsClient.deleteFriend).not.toHaveBeenCalled()
  })
})
