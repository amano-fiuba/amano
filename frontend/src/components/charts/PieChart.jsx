import { useState } from 'react'
import './PieChart.scss'

const SIZE = 200
const CENTER = SIZE / 2
const RADIUS = 92

function pointAt(angle) {
  return [CENTER + RADIUS * Math.cos(angle), CENTER + RADIUS * Math.sin(angle)]
}

/** SVG path of the slice between two angles (radians, 0 = right, clockwise). */
function slicePath(start, end) {
  const [x1, y1] = pointAt(start)
  const [x2, y2] = pointAt(end)
  const largeArc = end - start > Math.PI ? 1 : 0
  return `M ${CENTER} ${CENTER} L ${x1} ${y1} A ${RADIUS} ${RADIUS} 0 ${largeArc} 1 ${x2} ${y2} Z`
}

/**
 * Pie chart drawn with plain SVG (specs/032-estadisticas-gastos, plan D13). One
 * slice per item, proportional to `value`, starting at the top and going
 * clockwise in the given order; a single item is a full circle. Each slice is
 * focusable, and hovering, focusing or tapping it shows its `detail` below the
 * chart. Slices are separated by a thin gap in the surface color, and the
 * category list next to the chart carries the same information as text, so
 * nothing depends on color alone (FR-016).
 *
 * items: [{ key, label, color, value, detail }]
 */
export default function PieChart({ items, label }) {
  const [active, setActive] = useState(null)
  const total = items.reduce((sum, item) => sum + item.value, 0)
  const activeItem = items.find((item) => item.key === active)

  const sweeps = items.map((item) => (total > 0 ? (item.value / total) * 2 * Math.PI : 0))
  const slices = items.map((item, index) => {
    const start = -Math.PI / 2 + sweeps.slice(0, index).reduce((sum, sweep) => sum + sweep, 0)
    return { ...item, start, end: start + sweeps[index] }
  })

  const sliceProps = (slice) => ({
    className: slice.key === active ? 'pie-chart__slice is-active' : 'pie-chart__slice',
    fill: slice.color,
    tabIndex: 0,
    role: 'img',
    'aria-label': slice.detail,
    onMouseEnter: () => setActive(slice.key),
    onMouseLeave: () => setActive(null),
    onFocus: () => setActive(slice.key),
    onBlur: () => setActive(null),
    onClick: () => setActive(slice.key),
  })

  return (
    <figure className="pie-chart">
      <svg viewBox={`0 0 ${SIZE} ${SIZE}`} role="group" aria-label={label}>
        {slices.length === 1 ? (
          <circle cx={CENTER} cy={CENTER} r={RADIUS} {...sliceProps(slices[0])} />
        ) : (
          slices
            .filter((slice) => slice.end > slice.start)
            .map((slice) => (
              <path key={slice.key} d={slicePath(slice.start, slice.end)} {...sliceProps(slice)} />
            ))
        )}
      </svg>
      <figcaption className="pie-chart__detail" aria-live="polite">
        {activeItem ? activeItem.detail : 'Pasá el cursor o tocá una porción para ver su detalle'}
      </figcaption>
    </figure>
  )
}
