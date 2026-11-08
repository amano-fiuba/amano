import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'

vi.mock('../../api/groupsClient', async (importOriginal) => ({
  ...await importOriginal(),
  updateGroup: vi.fn(),
  uploadGroupPhoto: vi.fn(),
  deleteGroupPhoto: vi.fn(),
  getGroup: vi.fn(),
}))

import { deleteGroupPhoto, getGroup, updateGroup, uploadGroupPhoto } from '../../api/groupsClient'
import GroupEditForm, { DEBT_DUE_DAYS_INVALID } from './GroupEditForm'

const group = { id: 3, name: 'Viaje', description: 'Desc', photo: '/api/groups/3/photo?v=a' }

beforeEach(() => {
  vi.resetAllMocks()
  getGroup.mockResolvedValue({ ok: true, group: { ...group, name: 'Nuevo' } })
  updateGroup.mockResolvedValue({ ok: true, group })
  deleteGroupPhoto.mockResolvedValue({ ok: true })
  uploadGroupPhoto.mockResolvedValue({ ok: true })
})

describe('GroupEditForm', () => {
  it('guarda nombre y descripción', async () => {
    const onSaved = vi.fn()
    render(<GroupEditForm group={group} onSaved={onSaved} />)

    fireEvent.change(screen.getByLabelText('Nombre'), { target: { value: 'Nuevo' } })
    fireEvent.change(screen.getByLabelText('Descripción'), { target: { value: 'Otra' } })
    fireEvent.click(screen.getByRole('button', { name: 'Guardar' }))

    await waitFor(() => expect(onSaved).toHaveBeenCalled())
    expect(updateGroup).toHaveBeenCalledWith(3, { name: 'Nuevo', description: 'Otra' })
    expect(deleteGroupPhoto).not.toHaveBeenCalled()
  })

  it('elimina la foto solo al guardar', async () => {
    const onSaved = vi.fn()
    render(<GroupEditForm group={group} onSaved={onSaved} />)

    fireEvent.click(screen.getByTestId('group-photo-remove'))
    expect(deleteGroupPhoto).not.toHaveBeenCalled()
    expect(screen.getByAltText('')).toHaveAttribute('src', '/avatar-default.png')

    fireEvent.click(screen.getByRole('button', { name: 'Guardar' }))
    await waitFor(() => expect(deleteGroupPhoto).toHaveBeenCalledWith(3))
    expect(updateGroup).not.toHaveBeenCalled()
  })

  it('sube una foto nueva al guardar', async () => {
    URL.createObjectURL = vi.fn(() => 'blob:x')
    render(<GroupEditForm group={group} onSaved={() => {}} />)

    const file = new File(['x'], 'a.png', { type: 'image/png' })
    fireEvent.change(screen.getByTestId('group-photo-input'), { target: { files: [file] } })
    fireEvent.click(screen.getByRole('button', { name: 'Guardar' }))

    await waitFor(() => expect(uploadGroupPhoto).toHaveBeenCalledWith(3, file))
  })

  it('no tiene botón Cancelar: el panel se cierra desde la cabecera', () => {
    render(<GroupEditForm group={group} onSaved={() => {}} />)
    expect(screen.queryByRole('button', { name: 'Cancelar' })).not.toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Guardar' })).toBeInTheDocument()
  })

  it('rechaza un nombre vacío', () => {
    render(<GroupEditForm group={group} onSaved={() => {}} />)
    fireEvent.change(screen.getByLabelText('Nombre'), { target: { value: '  ' } })
    fireEvent.click(screen.getByRole('button', { name: 'Guardar' }))
    expect(screen.getByRole('alert')).toHaveTextContent(/obligatorio/)
    expect(updateGroup).not.toHaveBeenCalled()
  })

  // 022 T078: días para el vencimiento de las deudas
  describe('días para el vencimiento (022 T078)', () => {
    const admin = { ...group, currentUserRole: 'ADMIN', debtDueDays: 15 }
    const dueDays = () => screen.getByLabelText('Días para el vencimiento de las deudas')
    const save = () => fireEvent.click(screen.getByRole('button', { name: 'Guardar' }))

    it('muestra el valor actual y la ayuda, solo a administradores', () => {
      const { unmount } = render(<GroupEditForm group={admin} onSaved={() => {}} onCancel={() => {}} />)
      expect(dueDays()).toHaveValue('15')
      expect(screen.getByText('Dejalo vacío si las deudas nunca vencen')).toBeInTheDocument()
      unmount()

      render(<GroupEditForm group={{ ...admin, currentUserRole: 'MEMBER' }} onSaved={() => {}} onCancel={() => {}} />)
      expect(screen.queryByLabelText('Días para el vencimiento de las deudas')).not.toBeInTheDocument()
    })

    it('arranca vacío cuando las deudas nunca vencen', () => {
      render(<GroupEditForm group={{ ...admin, debtDueDays: null }} onSaved={() => {}} onCancel={() => {}} />)
      expect(dueDays()).toHaveValue('')
    })

    it('envía el entero con updateGroup', async () => {
      const onSaved = vi.fn()
      render(<GroupEditForm group={admin} onSaved={onSaved} onCancel={() => {}} />)
      fireEvent.change(dueDays(), { target: { value: '30' } })
      save()

      await waitFor(() => expect(onSaved).toHaveBeenCalled())
      expect(updateGroup).toHaveBeenCalledWith(3, { debtDueDays: 30 })
    })

    it('acepta 0 y vaciar el campo envía null (nunca vence)', async () => {
      const { unmount } = render(<GroupEditForm group={admin} onSaved={() => {}} onCancel={() => {}} />)
      fireEvent.change(dueDays(), { target: { value: '0' } })
      save()
      await waitFor(() => expect(updateGroup).toHaveBeenCalledWith(3, { debtDueDays: 0 }))
      unmount()

      updateGroup.mockClear()
      render(<GroupEditForm group={admin} onSaved={() => {}} onCancel={() => {}} />)
      fireEvent.change(dueDays(), { target: { value: '  ' } })
      save()
      await waitFor(() => expect(updateGroup).toHaveBeenCalledWith(3, { debtDueDays: null }))
    })

    it('si no cambia no lo envía', async () => {
      const onSaved = vi.fn()
      render(<GroupEditForm group={admin} onSaved={onSaved} onCancel={() => {}} />)
      fireEvent.change(screen.getByLabelText('Nombre'), { target: { value: 'Nuevo' } })
      save()

      await waitFor(() => expect(onSaved).toHaveBeenCalled())
      expect(updateGroup).toHaveBeenCalledWith(3, { name: 'Nuevo' })
    })

    it('rechaza negativos, decimales, texto y más de 3650 sin llamar a la API', () => {
      render(<GroupEditForm group={admin} onSaved={() => {}} onCancel={() => {}} />)
      for (const bad of ['-1', '2,5', 'abc', '3651']) {
        fireEvent.change(dueDays(), { target: { value: bad } })
        save()
        expect(screen.getByTestId('group-due-days-error')).toHaveTextContent(DEBT_DUE_DAYS_INVALID)
        expect(dueDays()).toHaveAttribute('aria-invalid', 'true')
      }
      expect(updateGroup).not.toHaveBeenCalled()
    })

    it('muestra el error de campo que devuelva el servidor', async () => {
      updateGroup.mockResolvedValue({
        ok: false,
        status: 400,
        fields: { debtDueDays: 'Los días para el vencimiento tienen que ser un número entero entre 0 y 3650' },
        detail: 'x',
      })
      render(<GroupEditForm group={admin} onSaved={() => {}} onCancel={() => {}} />)
      fireEvent.change(dueDays(), { target: { value: '9' } })
      save()

      expect(await screen.findByTestId('group-due-days-error')).toHaveTextContent('entre 0 y 3650')
      expect(screen.queryByTestId('group-edit-error')).not.toBeInTheDocument()
    })

    it('muestra el 403 del servidor en voseo', async () => {
      updateGroup.mockResolvedValue({
        ok: false,
        status: 403,
        fields: {},
        detail: 'Solo un administrador puede configurar el vencimiento de las deudas',
      })
      render(<GroupEditForm group={admin} onSaved={() => {}} onCancel={() => {}} />)
      fireEvent.change(dueDays(), { target: { value: '9' } })
      save()

      expect(await screen.findByTestId('group-edit-error'))
        .toHaveTextContent('Solo un administrador puede configurar el vencimiento de las deudas')
    })
  })
})
