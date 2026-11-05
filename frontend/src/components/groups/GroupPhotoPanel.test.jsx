import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'

vi.mock('../../api/groupsClient', async (importOriginal) => ({
  ...await importOriginal(),
  uploadGroupPhoto: vi.fn(),
}))

import { uploadGroupPhoto } from '../../api/groupsClient'
import GroupPhotoPanel from './GroupPhotoPanel'

beforeEach(() => {
  uploadGroupPhoto.mockReset()
})

function fileWith(payload, type = 'image/png', name = 'logo.png') {
  return new File([payload], name, { type })
}

const PNG = new Uint8Array([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a])

describe('GroupPhotoPanel (T086/T083, US8)', () => {
  it('sin foto muestra el avatar gris por defecto (mecanismo existente)', () => {
    render(<GroupPhotoPanel groupId={3} currentPhoto={null} isAdmin />)

    const img = screen.getByAltText('')
    expect(img).toHaveAttribute('src', '/avatar-default.png')
  })

  it('con foto muestra la imagen del grupo', () => {
    render(
      <GroupPhotoPanel
        groupId={3}
        currentPhoto="/api/groups/3/photo?v=abc"
        isAdmin
      />,
    )

    expect(screen.getByAltText('')).toHaveAttribute('src', '/api/groups/3/photo?v=abc')
  })

  it('manda el archivo al endpoint y muestra el éxito', async () => {
    uploadGroupPhoto.mockResolvedValue({ ok: true, photo: '/api/groups/3/photo?v=nueva' })

    render(<GroupPhotoPanel groupId={3} currentPhoto={null} isAdmin />)

    const input = screen.getByLabelText(/Cambiar foto del grupo/i)
    await waitFor(() => expect(input).toBeInTheDocument())
    fireEvent.change(input, { target: { files: [fileWith(PNG)] } })

    await waitFor(() => expect(uploadGroupPhoto).toHaveBeenCalled())
    expect(await screen.findByRole('status')).toHaveTextContent(/La foto del grupo se actualizó/i)
    expect(screen.getByAltText('')).toHaveAttribute('src', '/api/groups/3/photo?v=nueva')
  })

  it('valida el tipo en el cliente y no llama a la API con archivos inválidos', async () => {
    render(<GroupPhotoPanel groupId={3} currentPhoto="/api/groups/3/photo?v=x" isAdmin />)

    const gifBytes = new Uint8Array([0x47, 0x49, 0x46, 0x38]) // "GIF8"
    fireEvent.change(screen.getByLabelText(/Cambiar foto del grupo/i), {
      target: { files: [fileWith(gifBytes, 'image/gif', 'anim.gif')] },
    })

    expect(await screen.findByRole('alert')).toHaveTextContent(
      /El archivo debe ser una imagen JPG o PNG válida/,
    )
    expect(uploadGroupPhoto).not.toHaveBeenCalled()
    expect(screen.getByAltText('')).toHaveAttribute('src', '/api/groups/3/photo?v=x') // conservada
  })

  it('renderiza el rechazo server-side y conserva la foto anterior', async () => {
    uploadGroupPhoto.mockResolvedValue({
      ok: false,
      status: 400,
      detail: 'El archivo debe ser una imagen JPG o PNG válida',
    })

    render(
      <GroupPhotoPanel
        groupId={3}
        currentPhoto="/api/groups/3/photo?v=old"
        isAdmin
      />,
    )

    fireEvent.change(screen.getByLabelText(/Cambiar foto del grupo/i), {
      target: { files: [fileWith(PNG)] },
    })

    expect(await screen.findByTestId('group-photo-error')).toHaveTextContent(/JPG o PNG/)
    expect(screen.getByAltText('')).toHaveAttribute('src', '/api/groups/3/photo?v=old') // conservada
  })

  it('quien no es admin solo ve la foto, sin controles ni avisos', () => {
    render(<GroupPhotoPanel groupId={3} currentPhoto={null} isAdmin={false} />)

    expect(screen.getByAltText('')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /Cambiar foto/i })).not.toBeInTheDocument()
    expect(screen.queryByLabelText(/Cambiar foto del grupo/i)).not.toBeInTheDocument()
    expect(screen.queryByRole('alert')).not.toBeInTheDocument()
  })
})
