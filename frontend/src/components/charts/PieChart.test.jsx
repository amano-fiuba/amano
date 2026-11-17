import { fireEvent, render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'
import PieChart from './PieChart'

const HINT = 'Pasá el cursor o tocá una porción para ver su detalle'

const ITEMS = [
  { key: 'HOUSING', label: 'Vivienda', color: 'red', value: 50, detail: 'Vivienda · ARS 50,00 · 50,0 %' },
  { key: 'FOOD', label: 'Alimentos', color: 'blue', value: 30, detail: 'Alimentos · ARS 30,00 · 30,0 %' },
  { key: 'PETS', label: 'Mascotas', color: 'green', value: 20, detail: 'Mascotas · ARS 20,00 · 20,0 %' },
]

function renderChart(items = ITEMS) {
  return render(<PieChart items={items} label="Personales" />)
}

const slice = (name) => screen.getByRole('img', { name })

describe('PieChart (032)', () => {
  it('dibuja una porción por categoría con su color dentro de un grupo rotulado', () => {
    const { container } = renderChart()

    expect(screen.getByRole('group', { name: 'Personales' })).toBeInTheDocument()
    const paths = container.querySelectorAll('path')
    expect(paths).toHaveLength(3)
    expect([...paths].map((path) => path.getAttribute('fill'))).toEqual(['red', 'blue', 'green'])
    expect(slice(ITEMS[0].detail)).toHaveAttribute('tabindex', '0')
    expect(screen.getByText(HINT)).toBeInTheDocument()
  })

  it('empieza arriba y la primera porción de la mitad termina abajo', () => {
    const { container } = renderChart()

    // 50 % from the top (100, 8) clockwise to the bottom (100, 192), small arc.
    expect(container.querySelector('path').getAttribute('d')).toBe(
      'M 100 100 L 100 8 A 92 92 0 0 1 100 192 Z',
    )
  })

  it('usa el arco largo para una porción de más de la mitad', () => {
    const { container } = renderChart([
      { ...ITEMS[0], value: 75 },
      { ...ITEMS[1], value: 25 },
    ])

    expect(container.querySelector('path').getAttribute('d')).toContain(' 0 1 1 ')
  })

  it('con una sola categoría dibuja un círculo completo', () => {
    const { container } = renderChart([ITEMS[0]])

    expect(container.querySelector('path')).toBeNull()
    expect(container.querySelector('circle')).toHaveAttribute('fill', 'red')
    expect(slice(ITEMS[0].detail)).toBeInTheDocument()
  })

  it('omite las porciones sin valor', () => {
    const { container } = renderChart([ITEMS[0], { ...ITEMS[1], value: 0 }])

    expect(container.querySelectorAll('path')).toHaveLength(1)
  })

  it('muestra el detalle al pasar el mouse y lo quita al salir', () => {
    renderChart()

    fireEvent.mouseEnter(slice(ITEMS[1].detail))
    expect(screen.getByText(ITEMS[1].detail)).toBeInTheDocument()
    expect(slice(ITEMS[1].detail)).toHaveClass('is-active')

    fireEvent.mouseLeave(slice(ITEMS[1].detail))
    expect(screen.getByText(HINT)).toBeInTheDocument()
  })

  it('muestra el detalle al enfocar con el teclado o tocar una porción', async () => {
    const user = userEvent.setup()
    renderChart()

    await user.tab()
    expect(screen.getByText(ITEMS[0].detail)).toBeInTheDocument()
    await user.tab()
    expect(screen.getByText(ITEMS[1].detail)).toBeInTheDocument()
    await user.tab()
    await user.tab()
    expect(screen.getByText(HINT)).toBeInTheDocument()

    fireEvent.click(slice(ITEMS[2].detail))
    expect(screen.getByText(ITEMS[2].detail)).toBeInTheDocument()
  })
})
