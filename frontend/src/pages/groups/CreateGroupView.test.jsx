import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

vi.mock('../../api/groupsClient', () => ({
  createGroup: vi.fn(),
}))

import { createGroup } from '../../api/groupsClient'
import CreateGroupView from './CreateGroupView'

beforeEach(() => {
  createGroup.mockReset()
  vi.unstubAllGlobals()
})

afterEach(() => {
  vi.unstubAllGlobals()
})

describe('CreateGroupView (T031/T025)', () => {
  it('valida que el nombre sea obligatorio antes de llamar a la API', async () => {
    render(<CreateGroupView />)

    fireEvent.click(screen.getByRole('button', { name: 'Crear grupo' }))

    expect(await screen.findByText(/El nombre del grupo es obligatorio/i)).toBeInTheDocument()
    expect(createGroup).not.toHaveBeenCalled()
  })

  it('crea el grupo con un amigo registrado', async () => {
    createGroup.mockResolvedValue({ ok: true, group: { id: 42, name: 'Amigos para viajar' } })

    render(<CreateGroupView />)
    fireEvent.change(screen.getByLabelText('Nombre del grupo'), { target: { value: 'Amigos para viajar' } })
    fireEvent.change(screen.getByLabelText('Correo del amigo'), {
      target: { value: 'friend@example.com' },
    })
    fireEvent.click(screen.getByRole('button', { name: 'Crear grupo' }))

    await waitFor(() => expect(createGroup).toHaveBeenCalledWith({
      name: 'Amigos para viajar',
      description: '',
      member: { memberType: 'REGISTERED', identifier: 'friend@example.com' },
    }))
    expect(await screen.findByRole('status')).toHaveTextContent(/Grupo creado/i)
  })

  it('pide nombre y correo para un perfil simulado', async () => {
    createGroup.mockResolvedValue({ ok: true, group: { id: 43, name: 'Proyecto X' } })

    render(<CreateGroupView />)
    fireEvent.change(screen.getByLabelText('Nombre del grupo'), { target: { value: 'Proyecto X' } })

    const simulatedTab = screen.getByRole('radio', { name: /Perfil simulado/i })
    fireEvent.click(simulatedTab)
    fireEvent.click(screen.getByRole('button', { name: 'Crear grupo' }))
    expect(await screen.findByText(/Ingresá nombre y correo del perfil simulado/i)).toBeInTheDocument()

    fireEvent.change(screen.getByLabelText('Nombre del perfil simulado'), { target: { value: 'Invitado' } })
    fireEvent.change(screen.getByLabelText('Correo del perfil simulado'), {
      target: { value: 'invitado@example.com' },
    })
    fireEvent.click(screen.getByRole('button', { name: 'Crear grupo' }))

    await waitFor(() => expect(createGroup).toHaveBeenCalledWith({
      name: 'Proyecto X',
      description: '',
      member: { memberType: 'SIMULATED', identifier: 'invitado@example.com', name: 'Invitado' },
    }))
    expect(await screen.findByRole('status')).toHaveTextContent(/Grupo creado/i)
  })

  it('muestra el error del servidor cuando no hay miembro adicional', async () => {
    createGroup.mockResolvedValue({
      ok: false,
      status: 400,
      fields: { member: 'Agregá al menos un miembro adicional (amigo o perfil simulado)' },
    })

    render(<CreateGroupView />)
    fireEvent.change(screen.getByLabelText('Nombre del grupo'), { target: { value: 'Solo yo' } })
    fireEvent.change(screen.getByLabelText('Correo del amigo'), {
      target: { value: 'friend@example.com' },
    })
    fireEvent.click(screen.getByRole('button', { name: 'Crear grupo' }))

    expect(await screen.findByText(/amigo o perfil simulado\)/i)).toBeInTheDocument()
  })
})
