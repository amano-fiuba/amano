import { render, screen, waitFor } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'

vi.mock('../../api/groupsClient', async (importOriginal) => ({
  ...await importOriginal(),
  getMembers: vi.fn(),
}))

import { getMembers } from '../../api/groupsClient'
import MemberListView from './MemberListView'

beforeEach(() => {
  getMembers.mockReset()
})

const MEMBERS = [
  { userId: 7, name: 'Ana López', avatar: null, role: 'ADMIN' },
  { userId: 15, name: 'Carla Gómez', avatar: null, role: 'MEMBER' },
  { profileId: 33, name: 'Juan Simulado', avatar: null, role: 'MEMBER' },
]

describe('MemberListView (T079/T077, US6)', () => {
  it('renderiza a todos los miembros activos con perfil simulado incluido', async () => {
    getMembers.mockResolvedValue({ ok: true, members: MEMBERS })

    render(<MemberListView groupId={3} currentUserId={7} />)

    expect(await screen.findByText('Ana López')).toBeInTheDocument()
    expect(screen.getByText('Carla Gómez')).toBeInTheDocument()
    expect(screen.getByText('Juan Simulado')).toBeInTheDocument()
  })

  it('distingue al usuario actual con "Vos"', async () => {
    getMembers.mockResolvedValue({ ok: true, members: MEMBERS })

    render(<MemberListView groupId={3} currentUserId={7} />)

    await waitFor(() => expect(screen.getByTestId('member-current')).toHaveTextContent(/Ana López/))
    expect(screen.getByText(/Vos/)).toBeInTheDocument()
    expect(screen.queryByTestId('member-current')).not.toHaveTextContent('Carla Gómez')
  })

  it('usa el avatar gris por defecto cuando no hay foto (mecanismo existente)', async () => {
    getMembers.mockResolvedValue({ ok: true, members: MEMBERS })

    render(<MemberListView groupId={3} currentUserId={7} />)

    const avatars = await screen.findAllByAltText('')
    avatars.forEach((img) => expect(img).toHaveAttribute('src', '/avatar-default.png'))
  })

  it('renderiza el rol de cada miembro', async () => {
    getMembers.mockResolvedValue({ ok: true, members: MEMBERS })

    render(<MemberListView groupId={3} currentUserId={7} />)

    await waitFor(() => expect(screen.getByTestId('member-role-7')).toHaveTextContent(/Administrador/))
    expect(screen.getByTestId('member-role-15')).toHaveTextContent(/Miembro/)
  })

  it('muestra el error de conexión cuando getMembers falla', async () => {
    getMembers.mockResolvedValue({ ok: false, network: true })

    render(<MemberListView groupId={3} currentUserId={7} />)

    expect(await screen.findByRole('alert')).toHaveTextContent(
      /No se pudo conectar con el servidor/,
    )
  })
})
