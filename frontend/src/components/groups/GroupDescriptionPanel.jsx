import { Fragment } from 'react'

const EMPTY_MESSAGE = 'Este grupo no tiene descripción'

/**
 * Group description (US7, T081): pure UI read of the description field that
 * GET /api/groups/{id} already returns (US1). A NULL or empty description is
 * reported as "no tiene descripción" (FR-024); multi-line values keep their
 * line breaks (white-space: pre-line via the CSS class).
 */
export default function GroupDescriptionPanel({ description = null }) {
  const hasDescription = typeof description === 'string' && description.trim().length > 0

  return (
    <section
      data-testid="group-description"
      className="group-description"
      aria-label="Descripción del grupo"
    >
      {hasDescription ? (
        <p className="group-description__text" data-testid="group-description-text">
          {description.split('\n').map((line, index) => (
            <Fragment key={index}>
              {index > 0 && <br />}
              {line}
            </Fragment>
          ))}
        </p>
      ) : (
        <p className="group-description__empty" data-testid="group-description-empty">
          {EMPTY_MESSAGE}
        </p>
      )}
    </section>
  )
}
