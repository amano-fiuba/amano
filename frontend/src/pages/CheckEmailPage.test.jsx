import { render, screen } from '@testing-library/react'
import { afterEach, describe, expect, it } from 'vitest'
import CheckEmailPage, { CHECK_EMAIL_STORAGE_KEY } from './CheckEmailPage'

// "Revisa tu correo" contract (task T023, feature 003): FR-002 (the address is
// restored from sessionStorage, so a reload keeps showing it) and FR-010 (the
// resend button is always available; its request lands in US4).
afterEach(() => {
  sessionStorage.clear()
})

describe('CheckEmailPage (T023)', () => {
  it('muestra la dirección guardada y el botón de reenvío habilitado (FR-002/FR-010)', () => {
    sessionStorage.setItem(CHECK_EMAIL_STORAGE_KEY, 'ana.perez@example.com')

    render(<CheckEmailPage />)

    expect(screen.getByRole('heading', { name: 'Revisa tu correo' })).toBeInTheDocument()
    expect(screen.getByText(/ana\.perez@example\.com/)).toBeInTheDocument()
    expect(
      screen.getByRole('button', { name: /reenviar correo de verificación/i }),
    ).toBeEnabled()
  })

  it('sin dirección guardada muestra el mensaje genérico y no rompe', () => {
    render(<CheckEmailPage />)

    expect(
      screen.getByText(/Enviamos un enlace de verificación a tu correo electrónico/i),
    ).toBeInTheDocument()
    expect(
      screen.getByRole('button', { name: /reenviar correo de verificación/i }),
    ).toBeEnabled()
  })
})
