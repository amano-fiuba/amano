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
  it('muestra los accesos al dashboard y al registro de gastos', () => {
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
})
