import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'

vi.mock('../../api/groupsClient', async (importOriginal) => ({
  ...await importOriginal(),
  searchCandidates: vi.fn(),
  addMember: vi.fn(),
}))

import { addMember, searchCandidates } from '../../api/groupsClient'
import AddMemberView from './AddMemberView'

const GROUP = { id: 42, name: 'Amigos para viajar' }

beforeEach(() => {
  searchCandidates.mockReset()
  addMember.mockReset()
})

describe('AddMemberView (T049/T043)', () => {
  it('la búsqueda se limita a los amigos del caller', async () => {
    searchCandidates.mockResolvedValue({
      ok: true,
      content: [{ id: 15, firstName: 'Beto', lastName: 'Pérez', email: 'beto@example.com' }],
    })

    render(<AddMemberView group={GROUP} permissions={{ membersCannotInvite: false }} />)

    fireEvent.change(screen.getByLabelText('Buscar entre tus amigos'), {
      target: { value: 'bet' },
    })
    fireEvent.click(screen.getByRole('button', { name: 'Buscar' }))
    expect(await screen.findAllByText(/beto@example\.com/)).not.toHaveLength(0)
    expect(searchCandidates).toHaveBeenCalledWith(42, 'bet')
  })

  it('envía el alta del amigo registrado y muestra el éxito', async () => {
    searchCandidates.mockResolvedValue({
      ok: true,
      content: [{ id: 15, firstName: 'Beto', lastName: 'Pérez', email: 'beto@example.com' }],
    })
    addMember.mockResolvedValue({
      ok: true,
      member: { id: 15, type: 'REGISTERED', name: 'Beto', email: 'beto@example.com', role: 'MEMBER' },
    })

    render(<AddMemberView group={GROUP} permissions={{ membersCannotInvite: false }} />)

    fireEvent.change(screen.getByLabelText('Buscar entre tus amigos'), { target: { value: 'bet' } })
    fireEvent.click(screen.getByRole('button', { name: 'Buscar' }))
    fireEvent.click(await screen.findByRole('button', { name: /Agregar a beto@example\.com/i }))

    await waitFor(() =>
      expect(addMember).toHaveBeenCalledWith(42, {
        memberType: 'REGISTERED',
        identifier: 'beto@example.com',
      }),
    )
    expect(await screen.findByRole('status')).toHaveTextContent(/Miembro agregado/i)
  })

  it('valida el formulario del perfil simulado antes de llamar a la API', async () => {
    render(<AddMemberView group={GROUP} permissions={{ membersCannotInvite: false }} />)

    fireEvent.click(screen.getByRole('radio', { name: /Perfil simulado/i }))
    fireEvent.click(screen.getByRole('button', { name: /Agregar perfil simulado/i }))

    expect(await screen.findByText(/Ingresá nombre y correo/i)).toBeInTheDocument()
    expect(addMember).not.toHaveBeenCalled()

    fireEvent.change(screen.getByLabelText('Nombre del perfil simulado'), {
      target: { value: 'Juan Simulado' },
    })
    fireEvent.change(screen.getByLabelText('Correo del perfil simulado'), {
      target: { value: 'simulado@ejemplo.com' },
    })
    fireEvent.click(screen.getByRole('button', { name: /Agregar perfil simulado/i }))

    await waitFor(() =>
      expect(addMember).toHaveBeenCalledWith(42, {
        memberType: 'SIMULATED',
        identifier: 'simulado@ejemplo.com',
        name: 'Juan Simulado',
      }),
    )
  })

  it('muestra el motivo de rechazo cuando la persona ya es miembro', async () => {
    searchCandidates.mockResolvedValue({
      ok: true,
      content: [{ id: 15, firstName: 'Beto', lastName: 'Pérez', email: 'beto@example.com' }],
    })
    addMember.mockResolvedValue({
      ok: false,
      status: 400,
      detail: 'Esa persona ya es miembro del grupo',
    })

    render(<AddMemberView group={GROUP} permissions={{ membersCannotInvite: false }} />)

    fireEvent.change(screen.getByLabelText('Buscar entre tus amigos'), { target: { value: 'bet' } })
    fireEvent.click(screen.getByRole('button', { name: 'Buscar' }))
    fireEvent.click(await screen.findByRole('button', { name: /Agregar a beto@example\.com/i }))

    expect(await screen.findByRole('alert')).toHaveTextContent(/ya es miembro del grupo/i)
  })

  it('avisa con onAdded tras un alta exitosa, registrada o simulada (fix 2026-11-04)', async () => {
    const onAdded = vi.fn()
    searchCandidates.mockResolvedValue({
      ok: true,
      content: [{ id: 15, firstName: 'Beto', lastName: 'Pérez', email: 'beto@example.com' }],
    })
    addMember.mockResolvedValue({ ok: true, member: { id: 15 } })

    render(<AddMemberView group={GROUP} permissions={{}} onAdded={onAdded} />)

    fireEvent.change(screen.getByLabelText('Buscar entre tus amigos'), { target: { value: 'bet' } })
    fireEvent.click(screen.getByRole('button', { name: 'Buscar' }))
    fireEvent.click(await screen.findByRole('button', { name: /Agregar a beto@example\.com/i }))
    await waitFor(() => expect(onAdded).toHaveBeenCalledTimes(1))

    fireEvent.click(screen.getByRole('radio', { name: /Perfil simulado/i }))
    fireEvent.change(screen.getByLabelText('Nombre del perfil simulado'), { target: { value: 'Abuela' } })
    fireEvent.change(screen.getByLabelText('Correo del perfil simulado'), { target: { value: 'abuela@example.com' } })
    fireEvent.click(screen.getByRole('button', { name: /Agregar perfil simulado/i }))
    await waitFor(() => expect(onAdded).toHaveBeenCalledTimes(2))

    // a rejected alta does not notify
    addMember.mockResolvedValueOnce({ ok: false, status: 400, detail: 'Esa persona ya es miembro del grupo' })
    fireEvent.change(screen.getByLabelText('Nombre del perfil simulado'), { target: { value: 'Abuela' } })
    fireEvent.change(screen.getByLabelText('Correo del perfil simulado'), { target: { value: 'abuela@example.com' } })
    fireEvent.click(screen.getByRole('button', { name: /Agregar perfil simulado/i }))
    expect(await screen.findByTestId('add-member-error')).toBeInTheDocument()
    expect(onAdded).toHaveBeenCalledTimes(2)
  })
})
