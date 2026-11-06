import { act, render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import NotificationsMenu, {
  NOTIFICATIONS_ERROR_MESSAGE,
  NO_NOTIFICATIONS_MESSAGE,
} from './NotificationsMenu'

const { listReceivedFriendshipRequests } = vi.hoisted(() => ({
  listReceivedFriendshipRequests: vi.fn(),
}))

vi.mock('../api/friendsClient', () => ({
  FRIEND_REQUESTS_CHANGED_EVENT: 'friend-requests-changed',
  listReceivedFriendshipRequests,
}))

const REQUEST = { id: 1, sender: { firstName: 'Ana', lastName: 'Pérez' } }
const REQUEST_WITHOUT_LASTNAME = { id: 2, sender: { firstName: 'Juan', lastName: null } }

function renderMenu() {
  return render(
    <MemoryRouter>
      <NotificationsMenu />
      <p>Afuera</p>
    </MemoryRouter>,
  )
}

beforeEach(() => {
  listReceivedFriendshipRequests.mockReset()
  listReceivedFriendshipRequests.mockResolvedValue({ ok: true, requests: [] })
})

describe('NotificationsMenu (015, US2)', () => {
  it('muestra la foto de quien envía o el avatar por defecto', async () => {
    const user = userEvent.setup()
    listReceivedFriendshipRequests.mockResolvedValue({
      ok: true,
      requests: [
        { id: 1, sender: { firstName: 'Ana', lastName: null, avatarUrl: '/api/users/4/avatar' } },
        { id: 2, sender: { firstName: 'Bruno', lastName: null, avatarUrl: null } },
      ],
    })
    renderMenu()

    await user.click(await screen.findByRole('button', { name: /Notificaciones, 2 sin leer/ }))

    const [ana, bruno] = screen.getAllByRole('listitem')
    expect(ana.querySelector('img')).toHaveAttribute('src', '/api/users/4/avatar')
    expect(bruno.querySelector('img')).toHaveAttribute('src', '/avatar-default.png')
  })

  it('indica la cantidad y lista las solicitudes con acceso a Amigos', async () => {
    listReceivedFriendshipRequests.mockResolvedValue({
      ok: true,
      requests: [REQUEST, REQUEST_WITHOUT_LASTNAME],
    })
    const user = userEvent.setup()
    renderMenu()

    await user.click(await screen.findByRole('button', { name: 'Notificaciones, 2 sin leer' }))

    expect(await screen.findByText('Ana Pérez')).toBeInTheDocument()
    expect(screen.getByText('Juan')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Ver solicitudes' })).toHaveAttribute('href', '/friends')
  })

  it('muestra el singular y limita el indicador a 9+', async () => {
    listReceivedFriendshipRequests.mockResolvedValue({ ok: true, requests: [REQUEST] })
    const { unmount } = renderMenu()
    expect(
      await screen.findByRole('button', { name: 'Notificaciones, 1 sin leer' }),
    ).toBeInTheDocument()
    unmount()

    listReceivedFriendshipRequests.mockResolvedValue({
      ok: true,
      requests: Array.from({ length: 12 }, (_, id) => ({ ...REQUEST, id })),
    })
    renderMenu()
    expect(await screen.findByText('9+')).toBeInTheDocument()
  })

  it('sin solicitudes muestra el estado vacío', async () => {
    const user = userEvent.setup()
    renderMenu()

    await user.click(screen.getByRole('button', { name: 'Notificaciones' }))

    expect(await screen.findByText(NO_NOTIFICATIONS_MESSAGE)).toBeInTheDocument()
  })

  it('informa el error de carga sin romper la barra', async () => {
    listReceivedFriendshipRequests.mockResolvedValue({ ok: false, network: true })
    const user = userEvent.setup()
    renderMenu()

    await user.click(screen.getByRole('button', { name: 'Notificaciones' }))

    expect(await screen.findByRole('alert')).toHaveTextContent(NOTIFICATIONS_ERROR_MESSAGE)
  })

  it('se cierra con Escape y con un clic afuera', async () => {
    const user = userEvent.setup()
    renderMenu()
    const button = screen.getByRole('button', { name: 'Notificaciones' })

    await user.click(button)
    expect(button).toHaveAttribute('aria-expanded', 'true')
    await user.keyboard('{Escape}')
    expect(button).toHaveAttribute('aria-expanded', 'false')

    await user.click(button)
    await user.click(screen.getByText('Afuera'))
    expect(button).toHaveAttribute('aria-expanded', 'false')
  })

  it('se actualiza cuando se responde una solicitud en otra pantalla (FR-004)', async () => {
    listReceivedFriendshipRequests.mockResolvedValueOnce({ ok: true, requests: [REQUEST] })
    renderMenu()
    await screen.findByRole('button', { name: 'Notificaciones, 1 sin leer' })

    act(() => {
      window.dispatchEvent(new Event('friend-requests-changed'))
    })

    await waitFor(() =>
      expect(screen.getByRole('button', { name: 'Notificaciones' })).toBeInTheDocument(),
    )
  })
})
