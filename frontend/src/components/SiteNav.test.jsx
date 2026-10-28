import { render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import SiteNav from './SiteNav'

const { useAuth } = vi.hoisted(() => ({ useAuth: vi.fn() }))

vi.mock('../auth/AuthContext', () => ({ useAuth }))

beforeEach(() => {
  useAuth.mockReturnValue({ user: null, loading: false })
})

function renderNav(entry = '/') {
  return render(
    <MemoryRouter initialEntries={[entry]}>
      <SiteNav />
    </MemoryRouter>,
  )
}

describe('SiteNav', () => {
  it('muestra la marca y los accesos públicos para visitantes', () => {
    renderNav()

    expect(
      screen.getByRole('link', { name: /a mano, ir al inicio/i }),
    ).toHaveAttribute('href', '/')
    expect(
      screen.getByRole('navigation', { name: 'Navegación principal' }),
    ).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Inicio' })).toHaveAttribute('href', '/')
    expect(screen.getByRole('link', { name: 'Iniciar sesión' })).toHaveAttribute(
      'href',
      '/login',
    )
    expect(screen.getByRole('link', { name: 'Registrarse' })).toHaveAttribute(
      'href',
      '/register',
    )
    expect(screen.queryByRole('link', { name: 'Dashboard' })).not.toBeInTheDocument()
  })

  it('muestra los accesos de la app con sesión iniciada', () => {
    useAuth.mockReturnValue({ user: { id: 7 }, loading: false })
    renderNav()

    expect(
      screen.getByRole('navigation', { name: 'Navegación principal' }),
    ).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Dashboard' })).toHaveAttribute(
      'href',
      '/dashboard',
    )
    expect(screen.getByRole('link', { name: 'Registrar gasto' })).toHaveAttribute(
      'href',
      '/expenses/new',
    )
    expect(screen.getByRole('link', { name: 'Buscar usuarios' })).toHaveAttribute(
      'href',
      '/users/search',
    )
    expect(screen.getByRole('link', { name: 'Gastos recurrentes' })).toHaveAttribute(
      'href',
      '/expenses/recurring',
    )
    expect(
      screen.queryByRole('link', { name: 'Registrarse' }),
    ).not.toBeInTheDocument()
  })

  it('resalta el acceso de la ruta actual', () => {
    useAuth.mockReturnValue({ user: { id: 7 }, loading: false })
    renderNav('/expenses/new')

    expect(screen.getByRole('link', { name: 'Registrar gasto' })).toHaveClass(
      'is-active',
    )
    expect(screen.getByRole('link', { name: 'Dashboard' })).not.toHaveClass(
      'is-active',
    )
  })

  it('resalta el buscador cuando se está en /users/search (008)', () => {
    useAuth.mockReturnValue({ user: { id: 7 }, loading: false })
    renderNav('/users/search')

    expect(screen.getByRole('link', { name: 'Buscar usuarios' })).toHaveClass('is-active')
    expect(screen.getByRole('link', { name: 'Registrar gasto' })).not.toHaveClass('is-active')
  })

  it('resalta los gastos recurrentes cuando se está en /expenses/recurring (009)', () => {
    useAuth.mockReturnValue({ user: { id: 7 }, loading: false })
    renderNav('/expenses/recurring')

    expect(screen.getByRole('link', { name: 'Gastos recurrentes' })).toHaveClass('is-active')
    expect(screen.getByRole('link', { name: 'Registrar gasto' })).not.toHaveClass('is-active')
  })

  it('resalta el inicio en la landing pública', () => {
    renderNav('/')

    expect(screen.getByRole('link', { name: 'Inicio' })).toHaveClass('is-active')
    expect(screen.getByRole('link', { name: 'Iniciar sesión' })).not.toHaveClass(
      'is-active',
    )
  })
})
