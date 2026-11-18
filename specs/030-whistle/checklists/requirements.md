# Specification Quality Checklist: Whistle — aviso manual a un deudor

**Purpose**: Validate specification completeness and quality before proceeding to planning
**Created**: 2026-11-15
**Feature**: [spec.md](../spec.md)

## Content Quality

- [x] No implementation details (languages, frameworks, APIs)
- [x] Focused on user value and business needs
- [x] Written for non-technical stakeholders
- [x] All mandatory sections completed

## Requirement Completeness

- [x] No [NEEDS CLARIFICATION] markers remain
- [x] Requirements are testable and unambiguous
- [x] Success criteria are measurable
- [x] Success criteria are technology-agnostic (no implementation details)
- [x] All acceptance scenarios are defined
- [x] Edge cases are identified
- [x] Scope is clearly bounded
- [x] Dependencies and assumptions identified

## Feature Readiness

- [x] All functional requirements have clear acceptance criteria
- [x] User scenarios cover primary flows
- [x] Feature meets measurable outcomes defined in Success Criteria
- [ ] No implementation details leak into specification

## Notes

- **Actualización 2026-11-15:** Nahuel aprobó Q1 a Q11 tal como estaban escritas, así que el ítem de marcadores queda resuelto. El ítem de detalles de implementación sigue sin marcar, por el motivo de abajo.
- **Marcadores pendientes (histórico):** no hay `[NEEDS CLARIFICATION]` literales, pero las clarificaciones Q1 a Q11 están marcadas **"a confirmar por Nahuel"**. Hasta que se confirmen, la spec no cumple la DoR (constitución, punto 7), así que el ítem queda sin marcar.
- **Q3 (perfiles simulados) contradice el modelo de datos:**
  - `simulated_profiles.email` es `NOT NULL` (V8).
  - 025 ya les envía correos.
  - La decisión cambia FR-004, US1 escenario 4 y varias tareas.
- **Detalles de implementación en la spec:** la spec nombra componentes existentes (`ReminderMailService`, `ConfirmModal`, `GroupDebtsView`, `ExpenseDebtList`) y códigos HTTP. Se mantienen a pedido explícito de la propuesta, que fija la reutilización de 025 y las vistas. Por eso el último ítem de Feature Readiness queda sin marcar.
- **Story points:** el cronograma asigna 2 SP. US2 (límite) y US3 (historial) suman persistencia y una migración, lo que puede exceder la estimación. Si se decide recortar, US3 es la candidata, porque es P2.
- Ítems marcados `[x]`: reflejan la revisión de calidad de los requisitos y no implican implementación.
