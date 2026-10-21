import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { AuthProvider } from '../auth/AuthContext'

// Edición del perfil (specs/005-edit-user-profile, T007–T009/T016/T018):
// precarga fresca vía me() (FR-002), correo siempre deshabilitado (FR-007),
// validación en vivo + servidor por campo (FR-006/FR-010), cancelación sin
// guardar (FR-008), éxito con persistencia visible (FR-009/FR-012), avatar
// reemplazar/eliminar (FR-015/FR-016) y fallo de red sin cambios (FR-019).
//
// Mock del módulo del cliente (no del fetch global): el bootstrap del
// AuthProvider usa el fetch real stubbeado, la pantalla usa el mock explícito
// (mismo patrón que ProfilePage.test.jsx).
const PERFIL_COMPLETO = {
  id: 7,
  firstName: 'Ana',
  lastName: 'Pérez',
  email: 'ana.perez@example.com',
  currencies: ['ARS', 'USD'],
  cbuCvu: '0000003100010000000001',
  hasCustomAvatar: true,
  avatarUrl: '/api/users/7/avatar?v=3',
}

const PERFIL_EDICION_ACTUALIZADO = {
  ...PERFIL_COMPLETO,
  firstName: 'Anabel',
  lastName: 'Gómez',
  currencies: ['ARS', 'USD', 'EUR'],
  cbuCvu: '2850591001234567890123',
}

const PERFIL_MINIMO = {
  id: 7,
  firstName: 'Beto',
  lastName: null,
  email: 'beto@example.com',
  currencies: [],
  cbuCvu: null,
  hasCustomAvatar: false,
}

vi.mock('../api/authClient', () => ({
  UPDATE_NETWORK_ERROR_MESSAGE:
    'No se pudo completar la actualización. Intenta nuevamente',
  me: vi.fn(),
  updateProfile: vi.fn(),
  updateAvatar: vi.fn(),
  deleteAvatar: vi.fn(),
}))

import { deleteAvatar, me, updateAvatar, updateProfile } from '../api/authClient'
import ProfileEditPage from './ProfileEditPage'

beforeEach(() => {
  vi.unstubAllGlobals()
  me.mockReset()
  updateProfile.mockReset()
  updateAvatar.mockReset()
  deleteAvatar.mockReset()
})

afterEach(() => {
  vi.unstubAllGlobals()
})

function stubSesion(perfil) {
  const response = {
    ok: perfil !== null,
    status: perfil === null ? 401 : 200,
    json: () => Promise.resolve(perfil),
  }
  vi.stubGlobal('fetch', vi.fn().mockResolvedValue(response))
}

function renderEdit(initial = '/profile/edit') {
  return render(
    <AuthProvider>
      <MemoryRouter initialEntries={[initial]}>
        <Routes>
          <Route path="/profile/edit" element={<ProfileEditPage />} />
          <Route path="/profile" element={<div>Pantalla de visualización</div>} />
        </Routes>
      </MemoryRouter>
    </AuthProvider>,
  )
}

async function editarCompletamente(user) {
  // userEvent contra los campos precargados: se limpian antes de escribir
  // porque type() solo añade caracteres. Primero se espera la precarga
  // (FR-002): el reset() de react-hook-form puede llegar después del primer
  // render y pisaría lo tipeado (carrera Observable en jsdom).
  await waitFor(() => {
    expect(screen.getByLabelText('Nombre')).toHaveValue('Ana')
    expect(screen.getByLabelText('Apellido')).toHaveValue('Pérez')
  })
  const nombre = screen.getByLabelText('Nombre')
  await user.clear(nombre)
  await user.type(nombre, 'Anabel')
  const apellido = screen.getByLabelText('Apellido')
  await user.clear(apellido)
  await user.type(apellido, 'Gómez')
  const cbu = screen.getByLabelText('CBU/CVU')
  await user.clear(cbu)
  await user.type(cbu, '285-05910-0123 4567890123')
  return user.click(screen.getByRole('checkbox', { name: /euro/i }))
}

describe('ProfileEditPage (US1: precarga)', () => {
  it('precarga los datos actuales y el correo queda deshabilitado (FR-002, FR-007)', async () => {
    stubSesion(PERFIL_COMPLETO)
    me.mockResolvedValue({ ok: true, profile: PERFIL_COMPLETO })
    renderEdit()

    const nombre = await screen.findByLabelText('Nombre')
    expect(nombre).toHaveValue('Ana')
    expect(screen.getByLabelText('Apellido')).toHaveValue('Pérez')
    expect(screen.getByLabelText('CBU/CVU')).toHaveValue('0000003100010000000001')
    // SC-003: el mail es invariante — visible, siempre no editable.
    expect(screen.getByLabelText('Correo electrónico')).toHaveValue('ana.perez@example.com')
    expect(screen.getByLabelText('Correo electrónico')).toBeDisabled()
    expect(screen.getByRole('checkbox', { name: /peso argentino/i })).toBeChecked()
    expect(screen.getByRole('checkbox', { name: /dólar/i })).toBeChecked()
    expect(screen.getByRole('checkbox', { name: /euro/i })).not.toBeChecked()
  })

  it('con opcionales ausentes precarga los campos vacíos y sin monedas (FR-002/FR-004, US1)', async () => {
    stubSesion(PERFIL_MINIMO)
    me.mockResolvedValue({ ok: true, profile: PERFIL_MINIMO })
    renderEdit()

    expect(await screen.findByLabelText('Nombre')).toHaveValue('Beto')
    expect(screen.getByLabelText('Apellido')).toHaveValue('')
    expect(screen.getByLabelText('CBU/CVU')).toHaveValue('')
    expect(screen.getByRole('checkbox', { name: /peso argentino/i })).not.toBeChecked()
    expect(screen.getByRole('checkbox', { name: /euro/i })).not.toBeChecked()
  })

  it('fallo de red al precargar muestra el mensaje sin datos (FR-002/FR-019)', async () => {
    stubSesion(PERFIL_COMPLETO)
    me.mockRejectedValue(new Error('network down'))
    renderEdit()

    expect(await screen.findByRole('alert')).toHaveTextContent(
      /no se pudo cargar el perfil/i,
    )
    expect(screen.queryByLabelText('Correo electrónico')).not.toBeInTheDocument()
  })
})

describe('ProfileEditPage (US1: guardar, validar, cancelar)', () => {
  it('guarda datos válidos normalizados, confirma el éxito y aplica los datos frescos (FR-009, FR-012)', async () => {
    const user = userEvent.setup()
    stubSesion(PERFIL_COMPLETO)
    me.mockResolvedValue({ ok: true, profile: PERFIL_COMPLETO })
    updateProfile.mockResolvedValue({ ok: true, profile: PERFIL_EDICION_ACTUALIZADO })
    renderEdit()

    await editarCompletamente(user)
    await user.click(screen.getByRole('button', { name: /guardar cambios/i }))

    await waitFor(() => {
      expect(updateProfile).toHaveBeenCalledTimes(1)
    })
    const payload = updateProfile.mock.calls[0][0]
    expect(payload).toEqual({
      firstName: 'Anabel',
      lastName: 'Gómez',
      currencies: ['ARS', 'USD', 'EUR'],
      cbuCvu: '2850591001234567890123',
    })
    // FR-009: confirmación visible en pantalla.
    expect(await screen.findByRole('status')).toHaveTextContent(
      /los cambios se guardaron correctamente/i,
    )
    // FR-012: los datos frescos del servidor quedan visibles.
    expect(screen.getByLabelText('Nombre')).toHaveValue('Anabel')
    expect(screen.getByLabelText('Correo electrónico')).toHaveValue(
      'ana.perez@example.com', // SC-003: el mail sigue siendo el mismo
    )
  })

  it('error de validación del servidor informa por campo y conserva los datos (FR-010)', async () => {
    const user = userEvent.setup()
    stubSesion(PERFIL_COMPLETO)
    me.mockResolvedValue({ ok: true, profile: PERFIL_COMPLETO })
    updateProfile.mockResolvedValue({
      ok: false,
      status: 400,
      fields: { firstName: 'El nombre debe tener entre 2 y 50 caracteres' },
    })
    renderEdit()

    await editarCompletamente(user)
    await user.click(screen.getByRole('button', { name: /guardar cambios/i }))

    expect(await screen.findByText(/el nombre debe tener entre 2 y 50 caracteres/i))
      .toBeInTheDocument()
    // Los datos escritos se conservan para corregir sin repetir nada (crudos,
    // sin normalización: el trim y los 22 dígitos son del payload al enviar).
    expect(screen.getByLabelText('Apellido')).toHaveValue('Gómez')
    expect(screen.getByLabelText('CBU/CVU')).toHaveValue('285-05910-0123 4567890123')
    expect(screen.getByRole('checkbox', { name: /euro/i })).toBeChecked()
  })

  it('fallo de red al guardar muestra el mensaje fijo sin limpiar el formulario (FR-019)', async () => {
    const user = userEvent.setup()
    stubSesion(PERFIL_COMPLETO)
    me.mockResolvedValue({ ok: true, profile: PERFIL_COMPLETO })
    updateProfile.mockResolvedValue({ ok: false, network: true })
    renderEdit()

    await editarCompletamente(user)
    await user.click(screen.getByRole('button', { name: /guardar cambios/i }))

    expect(await screen.findByText(/no se pudo completar la actualización/i))
      .toBeInTheDocument()
    expect(screen.getByLabelText('Nombre')).toHaveValue('Anabel')
    expect(screen.queryByRole('status')).not.toBeInTheDocument()
  })

  it('cancelar vuelve a la visualización sin guardar nada (FR-008)', async () => {
    const user = userEvent.setup()
    stubSesion(PERFIL_COMPLETO)
    me.mockResolvedValue({ ok: true, profile: PERFIL_COMPLETO })
    renderEdit()

    await editarCompletamente(user)
    await user.click(screen.getByRole('button', { name: /cancelar/i }))

    expect(await screen.findByText('Pantalla de visualización')).toBeInTheDocument()
    expect(updateProfile).not.toHaveBeenCalled()
  })
})

function pngFile(name = 'nueva.png') {
  // PNG magic bytes (89 50 4E 47) reales para la validación por contenido.
  return new File(
    [new Uint8Array([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a])],
    name,
    { type: 'image/png' },
  )
}

function gifFile(name = 'trampa.gif') {
  return new File(
    [new Uint8Array([0x47, 0x49, 0x46, 0x38, 0x39])],
    name,
    { type: 'image/gif' },
  )
}

describe('ProfileEditPage (US3: avatar)', () => {
  it('subir una imagen válida la hace el avatar vigente (preview actualizado)', async () => {
    const user = userEvent.setup()
    stubSesion(PERFIL_COMPLETO)
    me.mockResolvedValue({ ok: true, profile: PERFIL_COMPLETO })
    const avatarActualizado = {
      ...PERFIL_COMPLETO,
      hasCustomAvatar: true,
      // La respuesta del servidor lleva su propia versión de contenido (?v=):
      // distinta de la precargada, el preview refetchea siempre.
      avatarUrl: '/api/users/7/avatar?v=7',
    }
    updateAvatar.mockResolvedValue({ ok: true, profile: avatarActualizado })
    renderEdit()

    const input = await screen.findByLabelText(/avatar:/i)
    await user.upload(input, pngFile())

    const archivo = await waitFor(async () => {
      expect(updateAvatar).toHaveBeenCalledTimes(1)
      return updateAvatar.mock.calls[0][0]
    })
    // El cliente arma el FormData con el part "avatar": el mock captura el
    // archivo original tal como la página se lo entrega.
    expect(archivo).toBeInstanceOf(File)

    const preview = await screen.findByRole('img', { name: /avatar de ana/i })
    await waitFor(() => {
      // Tras el reemplazo la versión sube: ?v=1 invalida cualquier imagen
      // cacheada bajo la misma URL (specs/005, contenido mutable).
      expect(preview.getAttribute('src')).toBe('/api/users/7/avatar?v=7')
    })
  })

  it('rechaza en vivo archivos sin magic bytes JPG/PNG sin alterar el vigente', async () => {
    // applyAccept:false (config de setup, no de upload): accept= del input
    // filtra por MIME declarado, y acá justamente se prueba que un MIME
    // mentiroso ("image/gif" con bytes GIF) no pasa el control real.
    const user = userEvent.setup({ applyAccept: false })
    stubSesion(PERFIL_COMPLETO)
    me.mockResolvedValue({ ok: true, profile: PERFIL_COMPLETO })
    renderEdit()

    const input = await screen.findByLabelText(/avatar:/i)
    await user.upload(input, gifFile())

    expect(await screen.findByRole('alert')).toHaveTextContent(
      /el archivo debe ser una imagen jpg o png válida/i,
    )
    expect(updateAvatar).not.toHaveBeenCalled()
    // El avatar vigente sigue servido, con su versión de precarga intacta
    // (FR-010 de 001 aplicada a la edición).
    expect(screen.getByRole('img', { name: /avatar de ana/i }).getAttribute('src')).toBe(
      '/api/users/7/avatar?v=3',
    )
  })

  it('rechaza archivos mayores a 2 MB en vivo (FR-015)', async () => {
    const user = userEvent.setup()
    stubSesion(PERFIL_COMPLETO)
    me.mockResolvedValue({ ok: true, profile: PERFIL_COMPLETO })
    renderEdit()

    const bytes = new Uint8Array(2 * 1024 * 1024 + 1)
    bytes.set([0x89, 0x50, 0x4e, 0x47], 0)
    const grandes = new File([bytes], 'grande.png', { type: 'image/png' })
    const input = await screen.findByLabelText(/avatar:/i)
    await user.upload(input, grandes)

    expect(await screen.findByRole('alert')).toHaveTextContent(
      /el archivo es demasiado grande/i,
    )
    expect(updateAvatar).not.toHaveBeenCalled()
  })

  it('eliminar el avatar propio vuelve al predeterminado (FR-016)', async () => {
    const user = userEvent.setup()
    stubSesion(PERFIL_COMPLETO)
    me.mockResolvedValue({ ok: true, profile: PERFIL_COMPLETO })
    deleteAvatar.mockResolvedValue({
      ok: true,
      profile: { ...PERFIL_COMPLETO, hasCustomAvatar: false, avatarUrl: undefined },
    })
    renderEdit()

    expect(await screen.findByRole('img', { name: /avatar de ana/i })).toHaveAttribute(
      'src',
      '/api/users/7/avatar?v=3',
    )
    await user.click(screen.getByRole('button', { name: /eliminar avatar/i }))

    await waitFor(() => {
      expect(deleteAvatar).toHaveBeenCalledTimes(1)
    })
    await waitFor(() => {
      expect(screen.getByRole('img', { name: /avatar de ana/i }).getAttribute('src')).toBe(
        '/avatar-default.png',
      )
    })
    // Sin avatar propio no se ofrece eliminar de nuevo.
    expect(screen.queryByRole('button', { name: /eliminar avatar/i })).not.toBeInTheDocument()
  })

  it('sin avatar propio no se ofrece eliminar y al subirlo se muestra con preview', async () => {
    const user = userEvent.setup()
    stubSesion(PERFIL_MINIMO)
    me.mockResolvedValue({ ok: true, profile: PERFIL_MINIMO })
    updateAvatar.mockResolvedValue({
      ok: true,
      profile: { ...PERFIL_MINIMO, hasCustomAvatar: true, avatarUrl: '/api/users/7/avatar' },
    })
    renderEdit()

    const preview = await screen.findByAltText(/avatar de beto/i)
    expect(preview).toHaveAttribute('src', '/avatar-default.png')
    expect(screen.queryByRole('button', { name: /eliminar avatar/i })).not.toBeInTheDocument()

    await user.upload(screen.getByLabelText(/avatar:/i), pngFile())
    await waitFor(() => {
      expect(updateAvatar).toHaveBeenCalledTimes(1)
    })
  })
})
