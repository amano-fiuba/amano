import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'

vi.mock('../../api/groupsClient', async (importOriginal) => ({
  ...await importOriginal(),
  leaveGroup: vi.fn(),
}))

import { leaveGroup } from '../../api/groupsClient'
import LeaveGroupPanel from './LeaveGroupPanel'

beforeEach(() => {
  leaveGroup.mockReset()
})

describe('LeaveGroupPanel (T068/T065)', () => {
  it('muestra la confirmación cuando el usuario no es el último admin', () => {
    render(<LeaveGroupPanel groupId={42} isLastAdmin={false} />)

    fireEvent.click(screen.getByRole('button', { name: /Abandonar grupo/i }))

    expect(screen.getByRole('dialog', { name: /Abandonar el grupo/i })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /Confirmar/i })).toBeInTheDocument()
    expect(leaveGroup).not.toHaveBeenCalled()
  })

  it('si el usuario es último admin muestra la guía de asignar otro admin y no sale', async () => {
    render(<LeaveGroupPanel groupId={42} isLastAdmin />)

    fireEvent.click(screen.getByRole('button', { name: /Abandonar grupo/i }))

    expect(
      await screen.findByText(/Sos el último administrador: asigná otro administrador antes de salir/i),
    ).toBeInTheDocument()
    expect(leaveGroup).not.toHaveBeenCalled()
  })

  it('abandona el grupo tras confirmar y muestra el éxito', async () => {
    leaveGroup.mockResolvedValue({ ok: true, groupLeft: true })

    render(<LeaveGroupPanel groupId={42} isLastAdmin={false} />)

    fireEvent.click(screen.getByRole('button', { name: /Abandonar grupo/i }))
    fireEvent.click(screen.getByRole('button', { name: /Confirmar/i }))

    await waitFor(() => expect(leaveGroup).toHaveBeenCalledWith(42))
    expect(await screen.findByRole('status')).toHaveTextContent(
      /Se abandonó el grupo/i,
    )
  })

  it('renderiza el motivo del rechazo server-side', async () => {
    leaveGroup.mockResolvedValue({
      ok: false,
      status: 403,
      detail: 'No podés abandonar el grupo: tenés saldos pendientes con otros miembros',
    })

    render(<LeaveGroupPanel groupId={42} isLastAdmin={false} />)

    fireEvent.click(screen.getByRole('button', { name: /Abandonar grupo/i }))
    fireEvent.click(screen.getByRole('button', { name: /Confirmar/i }))

    expect(await screen.findByRole('alert')).toHaveTextContent(/saldos pendientes/i)
  })
})
