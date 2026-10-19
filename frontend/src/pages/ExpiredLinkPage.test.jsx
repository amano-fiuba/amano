import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router'
import { describe, expect, it } from 'vitest'
import ExpiredLinkPage from './ExpiredLinkPage'

// Expired-link screen (task T030, TDD: written BEFORE the implementation):
// FR-006 — explains that the link expired and leads to the resend screen, where
// the user types the address (the request itself is wired in US4, task T053).
describe('ExpiredLinkPage (T030)', () => {
  it('explica que el enlace expiró y ofrece el formulario de reenvío (FR-006)', () => {
    render(
      <MemoryRouter>
        <ExpiredLinkPage />
      </MemoryRouter>,
    )

    expect(
      screen.getByRole('heading', { name: /el enlace expiró/i }),
    ).toBeInTheDocument()
    expect(screen.getByText(/enlace de verificación ha expirado/i)).toBeInTheDocument()
    expect(screen.getByLabelText(/correo electrónico/i)).toBeInTheDocument()
    expect(
      screen.getByRole('button', { name: /reenviar correo de verificación/i }),
    ).toBeEnabled()
  })

  it('el envío sigue en la pantalla: el wiring del reenvío llega en US4', async () => {
    const user = userEvent.setup()
    render(
      <MemoryRouter>
        <ExpiredLinkPage />
      </MemoryRouter>,
    )

    await user.type(screen.getByLabelText(/correo electrónico/i), 'ana.perez@example.com')
    await user.click(screen.getByRole('button', { name: /reenviar correo de verificación/i }))

    expect(
      screen.getByRole('heading', { name: /el enlace expiró/i }),
    ).toBeInTheDocument()
  })
})
