import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'

vi.mock('../../api/groupsClient', async (importOriginal) => ({
  ...await importOriginal(),
  getReminderSettings: vi.fn(),
  updateReminderSettings: vi.fn(),
}))

import { getReminderSettings, updateReminderSettings } from '../../api/groupsClient'
import { SnackbarProvider } from '../Snackbar'
import GroupReminderSettingsPanel from './GroupReminderSettingsPanel'

const SETTINGS = { sendDays: 5, message: null, defaultSendDays: 5, allowedSendDays: [7, 5, 3, 1] }

function renderPanel(props = {}) {
  return render(
    <SnackbarProvider>
      <GroupReminderSettingsPanel groupId={42} isAdmin {...props} />
    </SnackbarProvider>,
  )
}

beforeEach(() => {
  getReminderSettings.mockReset()
  updateReminderSettings.mockReset()
  getReminderSettings.mockResolvedValue({ ok: true, settings: SETTINGS })
})

describe('GroupReminderSettingsPanel (T024/T035)', () => {
  it('no se muestra para quien no es administrador', () => {
    const { container } = renderPanel({ isAdmin: false })

    expect(container).toBeEmptyDOMElement()
    expect(getReminderSettings).not.toHaveBeenCalled()
  })

  it('ofrece 7, 5, 3, 2 y 1 días y carga la configuración actual', async () => {
    getReminderSettings.mockResolvedValue({
      ok: true,
      settings: { ...SETTINGS, sendDays: 3, message: 'Pagá cuando puedas' },
    })

    renderPanel()

    expect(await screen.findByLabelText('Cuándo enviar el recordatorio')).toHaveValue('3')
    const options = screen.getAllByRole('option')
    expect(options.map((option) => option.textContent)).toEqual([
      '7 días antes', '5 días antes', '3 días antes', '2 días antes', '1 día antes',
    ])
    expect(screen.getByLabelText('Mensaje personalizado (opcional)')).toHaveValue('Pagá cuando puedas')
  })

  it('guarda los cambios y confirma con un snackbar', async () => {
    updateReminderSettings.mockResolvedValue({ ok: true, settings: { ...SETTINGS, sendDays: 7 } })

    renderPanel()
    fireEvent.change(await screen.findByLabelText('Cuándo enviar el recordatorio'), {
      target: { value: '7' },
    })
    fireEvent.click(screen.getByRole('button', { name: /Guardar recordatorios/i }))

    await waitFor(() => expect(updateReminderSettings).toHaveBeenCalledWith(42, {
      sendDays: 7,
      message: '',
    }))
    expect(await screen.findByRole('status')).toHaveTextContent(/Recordatorios actualizados/i)
  })

  it('guarda un mensaje en blanco y lo muestra vacío al recargar', async () => {
    getReminderSettings.mockResolvedValue({ ok: true, settings: { ...SETTINGS, message: 'Hola' } })
    updateReminderSettings.mockResolvedValue({ ok: true, settings: { ...SETTINGS, message: null } })

    renderPanel()
    const textarea = await screen.findByLabelText('Mensaje personalizado (opcional)')
    expect(textarea).toHaveValue('Hola')

    fireEvent.change(textarea, { target: { value: '   ' } })
    fireEvent.click(screen.getByRole('button', { name: /Guardar recordatorios/i }))

    await waitFor(() => expect(updateReminderSettings).toHaveBeenCalledWith(42, {
      sendDays: 5,
      message: '   ',
    }))
    await waitFor(() => expect(textarea).toHaveValue(''))
  })

  it('muestra el error de validación del backend junto al campo', async () => {
    updateReminderSettings.mockResolvedValue({
      ok: false,
      status: 400,
      detail: 'Revisá los datos',
      fields: { message: 'El mensaje no puede superar los 500 caracteres' },
    })

    renderPanel()
    fireEvent.click(await screen.findByRole('button', { name: /Guardar recordatorios/i }))

    expect(await screen.findByText('El mensaje no puede superar los 500 caracteres')).toBeInTheDocument()
    expect(screen.getByTestId('reminder-settings-error')).toHaveTextContent(/Revisá los datos/i)
  })

  it('limita el mensaje a 500 caracteres y muestra el contador y la ayuda', async () => {
    renderPanel()

    const textarea = await screen.findByLabelText('Mensaje personalizado (opcional)')
    expect(textarea).toHaveAttribute('maxLength', '500')
    expect(screen.getByTestId('reminder-message-counter')).toHaveTextContent('0/500')
    expect(screen.getByText(/Si lo dejás vacío se usa el mensaje predeterminado/i)).toBeInTheDocument()

    fireEvent.change(textarea, { target: { value: 'Hola' } })
    expect(screen.getByTestId('reminder-message-counter')).toHaveTextContent('4/500')
  })
})
