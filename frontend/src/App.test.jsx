import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import App from './App'

// AuthProvider consulta /api/users/me al montar; en pruebas offline devuelve 401.
beforeEach(() => {
  vi.unstubAllGlobals()
  vi.stubGlobal('fetch', vi.fn().mockResolvedValue({ ok: false, status: 401 }))
})

afterEach(() => {
  vi.unstubAllGlobals()
})

describe('App (T015)', () => {
  it('incrementa el contador al hacer clic en el botón (landing en /)', async () => {
    const user = userEvent.setup()
    render(<App />)

    await user.click(screen.getByRole('button', { name: /count is 0/i }))

    expect(
      screen.getByRole('button', { name: /count is 1/i }),
    ).toBeInTheDocument()
  })
})
