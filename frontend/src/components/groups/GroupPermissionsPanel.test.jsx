import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'

vi.mock('../../api/groupsClient', async (importOriginal) => ({
  ...await importOriginal(),
  updateGroup: vi.fn(),
}))

import { updateGroup } from '../../api/groupsClient'
import GroupPermissionsPanel from './GroupPermissionsPanel'

const GROUP = { id: 42, name: 'Amigos para viajar' }

const BASE_PERMISSIONS = {
  membersCannotMakeExpenses: false,
  membersCannotInvite: false,
  membersCannotEditSettings: false,
  membersCanManageSimulatedProfiles: false,
  membersCanGenerateExpensesForOthers: false,
  paymentsRequireValidation: false,
}

beforeEach(() => {
  updateGroup.mockReset()
})

describe('GroupPermissionsPanel (T036/T040)', () => {
  it('muestra las 6 casillas con los valores actuales', () => {
    render(
      <GroupPermissionsPanel
        group={GROUP}
        permissions={{ ...BASE_PERMISSIONS, membersCannotInvite: true }}
      />,
    )

    expect(screen.getByLabelText('No podrán hacer gastos')).not.toBeChecked()
    expect(screen.getByLabelText('No podrán invitar gente')).toBeChecked()
    expect(screen.getByLabelText('No podrán editar ajustes del grupo')).not.toBeChecked()
    expect(screen.getByLabelText('Pueden administrar perfiles simulados')).not.toBeChecked()
    expect(screen.getByLabelText('Pueden generar gastos en nombre de otras personas')).not.toBeChecked()
    expect(screen.getByLabelText('Los pagos requieren validación')).not.toBeChecked()
  })

  it('envía los cambios de permisos y confirma en la UI', async () => {
    updateGroup.mockResolvedValue({ ok: true, group: { ...GROUP, permissions: BASE_PERMISSIONS } })

    render(
      <GroupPermissionsPanel group={GROUP} permissions={{ ...BASE_PERMISSIONS }} />,
    )

    fireEvent.click(screen.getByLabelText('No podrán hacer gastos'))
    fireEvent.click(screen.getByRole('button', { name: /Guardar/i }))

    await waitFor(() =>
      expect(updateGroup).toHaveBeenCalledWith(42, {
        permissions: { ...BASE_PERMISSIONS, membersCannotMakeExpenses: true },
      }),
    )
    expect(await screen.findByRole('status')).toHaveTextContent(/Permisos actualizados/i)
  })

  it('muestra el motivo del rechazo cuando el guardado falla', async () => {
    updateGroup.mockResolvedValue({
      ok: false,
      status: 403,
      detail: 'No podés editar los ajustes de este grupo',
    })

    render(
      <GroupPermissionsPanel group={GROUP} permissions={{ ...BASE_PERMISSIONS }} />,
    )

    fireEvent.click(screen.getByRole('button', { name: /Guardar/i }))

    expect(await screen.findByRole('alert')).toHaveTextContent(
      /No podés editar los ajustes de este grupo/i,
    )
  })
})
