import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'

vi.mock('../../api/groupsClient', async (importOriginal) => ({
  ...await importOriginal(),
  removeMember: vi.fn(),
  removeSimulatedProfile: vi.fn(),
}))

import { removeMember, removeSimulatedProfile } from '../../api/groupsClient'
import RemoveMemberDialog from './RemoveMemberDialog'

const MEMBER = { userId: 15, name: 'Carla Pérez', role: 'MEMBER' }
const SIMULATED = { profileId: 33, name: 'Juan Simulado', email: 'simulado@amano.dev' }

beforeEach(() => {
  removeMember.mockReset()
  removeSimulatedProfile.mockReset()
})

describe('RemoveMemberDialog (T062/T057)', () => {
  it('pide confirmación antes de eliminar al miembro', async () => {
    removeMember.mockResolvedValue({ ok: true, removed: true })

    render(<RemoveMemberDialog groupId={42} member={MEMBER} />)

    fireEvent.click(screen.getByRole('button', { name: /Eliminar miembro/i }))
    expect(screen.getByRole('dialog', { name: /Eliminar a Carla Pérez/i })).toBeInTheDocument()
    expect(removeMember).not.toHaveBeenCalled()

    fireEvent.click(screen.getByRole('button', { name: /Confirmar/i }))
    await waitFor(() => expect(removeMember).toHaveBeenCalledWith(42, 15))
    expect(await screen.findByRole('status')).toHaveTextContent(/Miembro eliminado/i)
  })

  it('muestra la pista de revocar el rol primero para otro admin', async () => {
    render(<RemoveMemberDialog groupId={42} member={{ ...MEMBER, role: 'ADMIN' }} />)

    fireEvent.click(screen.getByRole('button', { name: /Eliminar miembro/i }))

    expect(
      await screen.findByText(/No podés eliminar directamente a otro administrador: primero quitale el rol/i),
    ).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /Confirmar/i })).not.toBeInTheDocument()
  })

  it('renderiza el motivo del rechazo server-side', async () => {
    removeMember.mockResolvedValue({
      ok: false,
      status: 403,
      detail: 'No podés eliminarla: tiene deudas pendientes con otros miembros del grupo',
    })

    render(<RemoveMemberDialog groupId={42} member={MEMBER} />)

    fireEvent.click(screen.getByRole('button', { name: /Eliminar miembro/i }))
    fireEvent.click(screen.getByRole('button', { name: /Confirmar/i }))

    expect(await screen.findByRole('alert')).toHaveTextContent(
      /tiene deudas pendientes/i,
    )
  })

  it('elimina un perfil simulado de forma física con la misma confirmación', async () => {
    removeSimulatedProfile.mockResolvedValue({ ok: true, removed: true })

    render(<RemoveMemberDialog groupId={42} simulatedProfile={SIMULATED} />)

    fireEvent.click(screen.getByRole('button', { name: /Eliminar perfil simulado/i }))
    fireEvent.click(screen.getByRole('button', { name: /Confirmar/i }))

    await waitFor(() => expect(removeSimulatedProfile).toHaveBeenCalledWith(42, 33))
    expect(await screen.findByRole('status')).toHaveTextContent(/Perfil simulado eliminado/i)
  })
})
