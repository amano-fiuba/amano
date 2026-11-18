# Specification Quality Checklist: Estadísticas y analíticas de ingresos personales

**Purpose**: Validar la completitud y calidad de la especificación antes de pasar al plan técnico
**Created**: 2026-11-18
**Feature**: [spec.md](../spec.md)

## Content Quality

- [x] No incluye detalles de implementación (lenguajes, frameworks, endpoints concretos)
- [x] Se centra en el valor para la persona y en las necesidades del negocio
- [x] Está redactada para partes interesadas no técnicas
- [x] Todas las secciones obligatorias están completas

## Requirement Completeness

- [x] No quedan marcadores [NEEDS CLARIFICATION]
- [x] Los requisitos son verificables y no ambiguos
- [x] Los criterios de éxito son medibles
- [x] Los criterios de éxito son agnósticos a la tecnología
- [x] Todos los escenarios de aceptación están definidos
- [x] Los casos límite están identificados
- [x] El alcance está claramente delimitado
- [x] Las dependencias y los supuestos están identificados

## Feature Readiness

- [x] Todos los requisitos funcionales tienen criterios de aceptación claros
- [x] Los escenarios de usuario cubren los flujos principales
- [x] La feature cumple los resultados medibles definidos en los criterios de éxito
- [x] No se filtran detalles de implementación en la especificación

## Notes

- Clarificaciones resueltas el 2026-11-18:
  1. La comparación resta el total combinado de gastos de 032: gastos personales más la parte de gastos grupales y con amigos (FR-017, SC-004).
  2. Los ingresos proyectados (fecha futura en UTC) se excluyen de la torta, del total de ingresos y de la comparación hasta que su fecha llegue, igual que en el balance de 033 (FR-008).
  3. La comparación se muestra como una dona de dos porciones (ingresos y gastos) con los totales como etiquetas y el balance del período destacado (FR-015, FR-016).
  4. Los gastos se organizan en un carrusel de dos slides con swipe horizontal, puntos y flechas accesibles; el bloque de ingresos y la comparación son fijos (FR-002, FR-003, FR-030, FR-031).
  5. El slide activo no forma parte de la dirección, se conserva al cambiar de período y vuelve al Slide 1 al recargar (FR-005, FR-006).
  6. El selector de año no ofrece años con solo ingresos proyectados, salvo que sea el año actual (FR-004).
- La especificación está lista para `/speckit.plan`; `plan.md`, `contracts/statistics-summary-api.md` y `tasks.md` generados el 2026-11-18.
