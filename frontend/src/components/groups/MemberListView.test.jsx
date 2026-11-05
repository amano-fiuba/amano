import { fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'

vi.mock('../../api/groupsClient', async (importOriginal) => ({
  ...await importOriginal(),
  getMembers: vi.fn(),
  grantAdminRole: vi.fn(),
  removeSimulatedProfile: vi.fn(),
  removeMember: vi.fn(),
}))

import { getMembers, grantAdminRole, removeMember, removeSimulatedProfile } from '../../api/groupsClient'
import MemberListView from './MemberListView'

beforeEach(() => {
  getMembers.mockReset()
  grantAdminRole.mockReset()
  removeSimulatedProfile.mockReset()
  removeMember.mockReset()
})

const MEMBERS = [
  { userId: 7, name: 'Ana López', avatar: null, role: 'ADMIN' },
  { userId: 15, name: 'Carla Gómez', avatar: null, role: 'MEMBER' },
  { userId: null, simulatedProfileId: 33, name: 'Juan Simulado', avatar: null, role: 'MEMBER' },
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

    await waitFor(() =>
      expect(screen.getByTestId('member-role-user-7')).toHaveTextContent(/Administrador/),
    )
    expect(screen.getByTestId('member-role-user-15')).toHaveTextContent(/Miembro/)
    expect(screen.getByTestId('member-role-sim-33')).toHaveTextContent(/Miembro/)
  })

  it('sin ser admin no muestra las acciones por fila', async () => {
    getMembers.mockResolvedValue({ ok: true, members: MEMBERS })

    render(<MemberListView groupId={3} currentUserId={7} isAdmin={false} />)

    await waitFor(() => expect(screen.getByText('Juan Simulado')).toBeInTheDocument())
    expect(screen.queryByTestId('grant-admin-user-15')).not.toBeInTheDocument()
    expect(screen.queryByTestId('revoke-admin-user-7')).not.toBeInTheDocument()
  })

  it('siendo admin muestra "Hacer admin" en miembros y elimina perfiles simulados', async () => {
    getMembers.mockResolvedValue({ ok: true, members: MEMBERS })

    render(<MemberListView groupId={3} currentUserId={7} isAdmin />)

    expect(await screen.findByTestId('grant-admin-user-15')).toHaveTextContent(/Hacer admin/)
    expect(screen.queryByTestId('grant-admin-sim-33')).not.toBeInTheDocument()
    expect(screen.getAllByRole('button', { name: /Eliminar/i }).length).toBeLessThan(10)
  })

  it('muestra el error de conexión cuando getMembers falla', async () => {
    getMembers.mockResolvedValue({ ok: false, network: true })

    render(<MemberListView groupId={3} currentUserId={7} />)

    expect(await screen.findByRole('alert')).toHaveTextContent(
      /No se pudo conectar con el servidor/,
    )
  })

  // ---- fix 2026-11-04: perfiles simulados y actualización automática ----

  it('a un perfil simulado no le ofrece acciones de rol, solo eliminarlo', async () => {
    getMembers.mockResolvedValue({ ok: true, members: MEMBERS })

    render(<MemberListView groupId={3} currentUserId={7} isAdmin />)

    const row = await screen.findByTestId('member-row-sim-33')
    expect(row).toHaveTextContent('Juan Simulado')
    expect(row.querySelector('[data-testid^="grant-admin"]')).toBeNull()
    expect(row.querySelector('[data-testid^="revoke-admin"]')).toBeNull()
    expect(row).toHaveTextContent('Eliminar perfil simulado')
    expect(row).not.toHaveTextContent('Hacer admin')
  })

  it('elimina un perfil simulado con su id y la lista se actualiza sola', async () => {
    const onChanged = vi.fn()
    getMembers
      .mockResolvedValueOnce({ ok: true, members: MEMBERS })
      .mockResolvedValueOnce({ ok: true, members: MEMBERS.slice(0, 2) })
    removeSimulatedProfile.mockResolvedValue({ ok: true, removed: true })

    render(<MemberListView groupId={3} currentUserId={7} isAdmin onChanged={onChanged} />)

    const row = await screen.findByTestId('member-row-sim-33')
    fireEvent.click(within(row).getByRole('button', { name: 'Eliminar perfil simulado' }))
    fireEvent.click(within(row).getByRole('button', { name: 'Confirmar' }))

    await waitFor(() => expect(screen.queryByText('Juan Simulado')).not.toBeInTheDocument())
    expect(removeSimulatedProfile).toHaveBeenCalledWith(3, 33)
    expect(removeMember).not.toHaveBeenCalled()
    expect(getMembers).toHaveBeenCalledTimes(2)
    expect(onChanged).toHaveBeenCalledTimes(1)
    expect(screen.getByTestId('snackbar')).toHaveTextContent('Juan Simulado ya no forma parte del grupo')
  })

  it('al eliminar a un miembro registrado la lista se actualiza sola', async () => {
    getMembers
      .mockResolvedValueOnce({ ok: true, members: MEMBERS })
      .mockResolvedValueOnce({ ok: true, members: [MEMBERS[0], MEMBERS[2]] })
    removeMember.mockResolvedValue({ ok: true, removed: true })

    render(<MemberListView groupId={3} currentUserId={7} isAdmin />)

    const row = await screen.findByTestId('member-row-user-15')
    fireEvent.click(within(row).getByRole('button', { name: 'Eliminar miembro' }))
    fireEvent.click(within(row).getByRole('button', { name: 'Confirmar' }))

    await waitFor(() => expect(screen.queryByText('Carla Gómez')).not.toBeInTheDocument())
    expect(removeMember).toHaveBeenCalledWith(3, 15)
  })

  it('al cambiar un rol avisa al padre y vuelve a consultar', async () => {
    const onChanged = vi.fn()
    getMembers.mockResolvedValue({ ok: true, members: MEMBERS })
    grantAdminRole.mockResolvedValue({ ok: true })

    render(<MemberListView groupId={3} currentUserId={7} isAdmin onChanged={onChanged} />)

    fireEvent.click(await screen.findByTestId('grant-admin-user-15'))

    await waitFor(() => expect(onChanged).toHaveBeenCalledTimes(1))
    expect(grantAdminRole).toHaveBeenCalledWith(3, 15)
    expect(getMembers).toHaveBeenCalledTimes(2)
  })

  it('vuelve a consultar cuando cambia reloadKey (por ejemplo, tras un alta)', async () => {
    getMembers
      .mockResolvedValueOnce({ ok: true, members: MEMBERS.slice(0, 1) })
      .mockResolvedValueOnce({ ok: true, members: MEMBERS })

    const { rerender } = render(<MemberListView groupId={3} currentUserId={7} reloadKey={0} />)
    expect(await screen.findByText('Ana López')).toBeInTheDocument()
    expect(screen.queryByText('Juan Simulado')).not.toBeInTheDocument()

    rerender(<MemberListView groupId={3} currentUserId={7} reloadKey={1} />)

    expect(await screen.findByText('Juan Simulado')).toBeInTheDocument()
  })

  it('una respuesta vieja no pisa a la más reciente', async () => {
    let resolveFirst
    getMembers
      .mockReturnValueOnce(new Promise((resolve) => { resolveFirst = resolve }))
      .mockResolvedValueOnce({ ok: true, members: MEMBERS })

    const { rerender } = render(<MemberListView groupId={3} reloadKey={0} />)
    rerender(<MemberListView groupId={3} reloadKey={1} />)
    expect(await screen.findByText('Juan Simulado')).toBeInTheDocument()

    resolveFirst({ ok: true, members: [] })
    await new Promise((resolve) => setTimeout(resolve, 0))
    expect(screen.getByText('Juan Simulado')).toBeInTheDocument()
  })
})
