import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import QuickActionsButton from './QuickActionsButton'

const { useAuth } = vi.hoisted(() => ({ useAuth: vi.fn() }))

vi.mock('../auth/AuthContext', () => ({ useAuth }))

function renderButton() {
  return render(
    <MemoryRouter>
      <QuickActionsButton />
    </MemoryRouter>,
  )
}

beforeEach(() => {
  useAuth.mockReturnValue({ user: { id: 7 } })
})

describe('QuickActionsButton (015, US4)', () => {
  it('sin sesión no se muestra', () => {
    useAuth.mockReturnValue({ user: null })
    renderButton()

    expect(screen.queryByRole('button')).not.toBeInTheDocument()
  })

  it('despliega las acciones de creación', async () => {
    const user = userEvent.setup()
    renderButton()

    await user.click(screen.getByRole('button', { name: 'Acciones rápidas' }))

    expect(screen.getByRole('link', { name: 'Crear grupo' })).toHaveAttribute('href', '/groups/new')
    expect(screen.getByRole('link', { name: 'Registrar gasto' })).toHaveAttribute('href', '/expenses/new')
    expect(screen.getByRole('link', { name: 'Gastos recurrentes' })).toHaveAttribute(
      'href',
      '/expenses/recurring',
    )
  })

  it('se cierra al elegir una opción', async () => {
    const user = userEvent.setup()
    renderButton()

    await user.click(screen.getByRole('button', { name: 'Acciones rápidas' }))
    await user.click(screen.getByRole('link', { name: 'Crear grupo' }))

    expect(screen.queryByRole('link', { name: 'Crear grupo' })).not.toBeInTheDocument()
  })
})
