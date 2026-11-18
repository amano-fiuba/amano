import { useRef } from 'react'
import './Carousel.scss'

const SWIPE_THRESHOLD = 40

/**
 * Accessible carousel with one slide visible at a time (specs/034-income-statistics,
 * plan D12, D13). Controlled: the page owns `index`. No autoplay and no loop; the
 * previous/next buttons, the dots and ArrowLeft/ArrowRight offer the same
 * navigation as the horizontal swipe, which only changes the slide when it passes
 * 40 px and is more horizontal than vertical, leaving vertical scroll untouched.
 *
 * slides: [{ key, label, content }]
 */
export default function Carousel({ slides, index, onIndexChange, ariaLabel }) {
  const start = useRef(null)
  const last = slides.length - 1

  const goTo = (next) => {
    if (next >= 0 && next <= last && next !== index) {
      onIndexChange(next)
    }
  }

  const onKeyDown = (event) => {
    if (event.key === 'ArrowLeft') {
      goTo(index - 1)
    } else if (event.key === 'ArrowRight') {
      goTo(index + 1)
    }
  }

  const onTouchStart = (event) => {
    const touch = event.touches?.[0]
    start.current = touch ? { x: touch.clientX, y: touch.clientY } : null
  }

  const onTouchEnd = (event) => {
    const touch = event.changedTouches?.[0]
    const origin = start.current
    start.current = null
    if (!touch || !origin) {
      return
    }
    const dx = touch.clientX - origin.x
    const dy = touch.clientY - origin.y
    if (Math.abs(dx) >= SWIPE_THRESHOLD && Math.abs(dx) > Math.abs(dy)) {
      goTo(dx < 0 ? index + 1 : index - 1)
    }
  }

  return (
    <section
      className="carousel"
      role="region"
      aria-roledescription="carrusel"
      aria-label={ariaLabel}
      onKeyDown={onKeyDown}
    >
      <div
        className="carousel__viewport"
        onTouchStart={onTouchStart}
        onTouchEnd={onTouchEnd}
        onTouchCancel={() => {
          start.current = null
        }}
      >
        <div className="carousel__track" style={{ transform: `translateX(-${index * 100}%)` }}>
          {slides.map((slide, position) => (
            <div
              key={slide.key}
              className="carousel__slide"
              role="group"
              aria-roledescription="slide"
              aria-label={`${position + 1} de ${slides.length}`}
              aria-hidden={position === index ? undefined : 'true'}
              inert={position === index ? undefined : true}
            >
              {slide.content}
            </div>
          ))}
        </div>
      </div>
      <div className="carousel__controls">
        <button type="button" onClick={() => goTo(index - 1)} disabled={index <= 0}>
          Anterior
        </button>
        <div className="carousel__dots">
          {slides.map((slide, position) => (
            <button
              key={slide.key}
              type="button"
              className="carousel__dot"
              aria-label={`Ir a ${slide.label} (${position + 1} de ${slides.length})`}
              aria-current={position === index ? 'true' : undefined}
              onClick={() => goTo(position)}
            />
          ))}
        </div>
        <button type="button" onClick={() => goTo(index + 1)} disabled={index >= last}>
          Siguiente
        </button>
      </div>
      <p className="carousel__status" aria-live="polite">
        {slides[index]?.label} ({index + 1} de {slides.length})
      </p>
    </section>
  )
}
