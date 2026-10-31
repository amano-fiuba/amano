import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'

import GroupDescriptionPanel from './GroupDescriptionPanel'

describe('GroupDescriptionPanel (T081/T080, US7)', () => {
  it('muestra la descripción cuando el grupo tiene una', () => {
    render(<GroupDescriptionPanel description="Gastos compartidos del depto" />)

    const panel = screen.getByTestId('group-description')
    expect(panel).toHaveTextContent('Gastos compartidos del depto')
  })

  it('muestra que el grupo no tiene descripción cuando es NULL', () => {
    render(<GroupDescriptionPanel description={null} />)

    expect(screen.getByTestId('group-description')).toHaveTextContent(
      /Este grupo no tiene descripción/,
    )
  })

  it('trata la cadena vacía como "sin descripción" (normalización de la UI)', () => {
    render(<GroupDescriptionPanel description="" />)

    expect(screen.getByTestId('group-description')).toHaveTextContent(
      /Este grupo no tiene descripción/,
    )
  })

  it('soporta valores multilinea sin perder el contenido', () => {
    const { container } = render(<GroupDescriptionPanel description={'Línea 1\nLínea 2'} />)

    const panel = screen.getByTestId('group-description')
    expect(panel).toHaveTextContent('Línea 1')
    expect(panel).toHaveTextContent('Línea 2')
    expect(container.querySelectorAll('br')).toHaveLength(1) // el salto se conserva
  })
})
