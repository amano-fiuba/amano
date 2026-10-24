import { render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router'
import { describe, expect, it } from 'vitest'
import SiteNav from './SiteNav'

function renderNav(entry = '/') {
  return render(
    <MemoryRouter initialEntries={[entry]}>
      <SiteNav />
    </MemoryRouter>,
  )
}

describe('SiteNav', () => {
  it('muestra los accesos al dashboard, al registro de gastos y al buscador', () => {
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
  })

  it('resalta el acceso de la ruta actual', () => {
    renderNav('/expenses/new')

    expect(screen.getByRole('link', { name: 'Registrar gasto' })).toHaveClass(
      'is-active',
    )
    expect(screen.getByRole('link', { name: 'Dashboard' })).not.toHaveClass(
      'is-active',
    )
  })

  it('resalta el buscador cuando se está en /users/search (008)', () => {
    renderNav('/users/search')

    expect(screen.getByRole('link', { name: 'Buscar usuarios' })).toHaveClass('is-active')
    expect(screen.getByRole('link', { name: 'Registrar gasto' })).not.toHaveClass('is-active')
  })
})
