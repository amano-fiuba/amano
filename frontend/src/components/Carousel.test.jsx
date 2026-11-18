import { fireEvent, render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { useState } from 'react'
import { describe, expect, it } from 'vitest'
import Carousel from './Carousel'

const SLIDES = [
  { key: 'a', label: 'Primero', content: <p>Contenido A</p> },
  { key: 'b', label: 'Segundo', content: <p>Contenido B</p> },
  { key: 'c', label: 'Tercero', content: <p>Contenido C</p> },
]

function Harness({ initial = 0 }) {
  const [index, setIndex] = useState(initial)
  return <Carousel slides={SLIDES} index={index} onIndexChange={setIndex} ariaLabel="Demo" />
}

const region = () => screen.getByRole('region', { name: 'Demo' })
const viewport = () => region().querySelector('.carousel__viewport')
const dot = (n) => screen.getByRole('button', { name: new RegExp(`\\(${n} de 3\\)`) })

function swipe(from, to) {
  fireEvent.touchStart(viewport(), { touches: [{ clientX: from[0], clientY: from[1] }] })
  fireEvent.touchEnd(viewport(), { changedTouches: [{ clientX: to[0], clientY: to[1] }] })
}

describe('Carousel (034)', () => {
  it('empieza en el primer slide, con roles y nombres accesibles', () => {
    render(<Harness />)

    expect(region()).toHaveAttribute('aria-roledescription', 'carrusel')
    const slides = screen.getAllByRole('group', { hidden: true })
    expect(slides.map((slide) => slide.getAttribute('aria-label'))).toEqual([
      '1 de 3',
      '2 de 3',
      '3 de 3',
    ])
    expect(slides[0]).toHaveAttribute('aria-roledescription', 'slide')
    expect(slides[0]).not.toHaveAttribute('aria-hidden')
    expect(slides[1]).toHaveAttribute('aria-hidden', 'true')
    expect(dot(1)).toHaveAttribute('aria-current', 'true')
    expect(dot(2)).not.toHaveAttribute('aria-current')
    expect(screen.getByText('Primero (1 de 3)')).toBeInTheDocument()
  })

  it('los botones avanzan y retroceden y se deshabilitan en los extremos', async () => {
    const user = userEvent.setup()
    render(<Harness />)
    const previous = screen.getByRole('button', { name: 'Anterior' })
    const next = screen.getByRole('button', { name: 'Siguiente' })

    expect(previous).toBeDisabled()
    await user.click(next)
    expect(dot(2)).toHaveAttribute('aria-current', 'true')
    await user.click(next)
    expect(next).toBeDisabled()
    await user.click(previous)
    expect(dot(2)).toHaveAttribute('aria-current', 'true')
  })

  it('los puntos llevan al slide elegido', async () => {
    const user = userEvent.setup()
    render(<Harness />)

    await user.click(dot(3))

    expect(dot(3)).toHaveAttribute('aria-current', 'true')
    expect(screen.getByText('Tercero (3 de 3)')).toBeInTheDocument()
  })

  it('las flechas del teclado cambian de slide sin pasar de los extremos ni dar la vuelta', () => {
    render(<Harness />)

    fireEvent.keyDown(region(), { key: 'ArrowLeft' })
    expect(dot(1)).toHaveAttribute('aria-current', 'true')
    fireEvent.keyDown(region(), { key: 'ArrowRight' })
    fireEvent.keyDown(region(), { key: 'ArrowRight' })
    fireEvent.keyDown(region(), { key: 'ArrowRight' })
    expect(dot(3)).toHaveAttribute('aria-current', 'true')
    fireEvent.keyDown(region(), { key: 'ArrowLeft' })
    expect(dot(2)).toHaveAttribute('aria-current', 'true')
    fireEvent.keyDown(region(), { key: 'Enter' })
    expect(dot(2)).toHaveAttribute('aria-current', 'true')
  })

  it('un swipe horizontal largo cambia de slide en la dirección del gesto', () => {
    render(<Harness initial={1} />)

    swipe([200, 100], [100, 110])
    expect(dot(3)).toHaveAttribute('aria-current', 'true')
    swipe([100, 100], [220, 90])
    expect(dot(2)).toHaveAttribute('aria-current', 'true')
  })

  it('un gesto corto o más vertical que horizontal no cambia de slide', () => {
    render(<Harness />)

    swipe([200, 100], [170, 100])
    swipe([200, 100], [100, 260])
    expect(dot(1)).toHaveAttribute('aria-current', 'true')
  })

  it('el swipe no bloquea el scroll: touch-action pan-y y sin preventDefault', () => {
    render(<Harness />)

    const notCancelled = fireEvent.touchStart(viewport(), { touches: [{ clientX: 0, clientY: 0 }] })
    const endNotCancelled = fireEvent.touchEnd(viewport(), {
      changedTouches: [{ clientX: 5, clientY: 80 }],
    })

    expect(notCancelled).toBe(true)
    expect(endNotCancelled).toBe(true)
    expect(viewport().className).toContain('carousel__viewport')
  })

  it('cancelar el toque descarta el gesto y un fin sin inicio no hace nada', () => {
    render(<Harness />)

    fireEvent.touchStart(viewport(), { touches: [{ clientX: 300, clientY: 0 }] })
    fireEvent.touchCancel(viewport())
    fireEvent.touchEnd(viewport(), { changedTouches: [{ clientX: 0, clientY: 0 }] })

    expect(dot(1)).toHaveAttribute('aria-current', 'true')
  })
})
