# Specification Quality Checklist: Ver estadísticas de mis gastos

**Purpose**: Validate specification completeness and quality before proceeding to planning
**Created**: 2026-11-16
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
- [x] No implementation details leak into specification

## Notes

- Decisiones tomadas con el usuario (sesión 2026-11-16): período por selector de
  mes y año (por defecto el actual); suma en la primera moneda preferida.
- Sesión 2: se incluyen los gastos grupales (de grupo y con amigos) por la parte
  de la persona en el reparto, en una torta aparte de la de personales, con un
  total combinado arriba.
- FR-005 exige que el backend agregue los datos: es un criterio de aceptación de
  la historia, por eso figura aunque roza la implementación.
- Supuestos a validar:
  - "La primera" moneda preferida = la primera según el orden del catálogo
    (ARS, USD, EUR, …), porque las preferencias se guardan sin orden.
  - Los gastos sin cotización disponible se excluyen y se avisa su cantidad.
  - Conversión con la cotización a la fecha del gasto, como en la actividad.
  - El selector de mes no ofrece "Todos los meses" (el año completo queda fuera
    de alcance).
  - Gastos grupales anteriores a 027 (sin categoría) cuentan como "Otros".
  - En el plan hay que confirmar que una serie grupal recurrente (028) no se
    cuenta dos veces junto con sus repeticiones.
