# Specification Quality Checklist: Bandeja completa y configuración de notificaciones in-app

**Purpose**: Validar la completitud y calidad de la especificación antes de pasar al plan técnico
**Created**: 2026-11-23
**Feature**: [spec.md](../spec.md)

## Content Quality

- [x] No incluye detalles de implementación (lenguajes, frameworks) como decisiones propias de la spec: las cinco rutas REST y la tabla `user_notification_preferences` aparecen porque el backlog las exige literalmente y se tratan como restricciones de contrato, documentadas también en `plan.md` y `contracts/notifications-api.md`
- [x] Se centra en el valor para la persona y en las necesidades del negocio
- [x] Está redactada para partes interesadas no técnicas
- [x] Todas las secciones obligatorias están completas

## Requirement Completeness

- [x] No quedan marcadores [NEEDS CLARIFICATION]: las decisiones pendientes se resolvieron con supuestos documentados (límite del popover, defaults, destinos inexistentes, no duplicación de correos)
- [x] Los requisitos son verificables y no ambiguos (FR-001..FR-036 con acciones observables)
- [x] Los criterios de éxito son medibles (SC-001..SC-010 con tiempos, porcentajes y conteos)
- [x] Los criterios de éxito son agnósticos a la tecnología (ninguno nombra framework, lenguaje ni librería)
- [x] Todos los escenarios de aceptación están definidos (US1..US5 con `Given/When/Then`)
- [x] Los casos límite están identificados (sección Edge Cases y matriz con SMTP/BD caídos, IDs ajenos e inexistentes y destinos eliminados)
- [x] El alcance está claramente delimitado (sección "Fuera de alcance" y supuestos)
- [x] Las dependencias y los supuestos están identificados (sección "Relación con otras especificaciones" y "Supuestos")

## Feature Readiness

- [x] Todos los requisitos funcionales tienen criterios de aceptación claros (escenarios por historia y trazabilidad en `tasks.md`)
- [x] Los escenarios de usuario cubren los flujos principales (campana, bandeja, deep links, preferencias y resiliencia)
- [x] La feature cumple los resultados medibles definidos en Success Criteria
- [x] No se filtran detalles de implementación en la especificación más allá del contrato explícitamente solicitado

## Notes

- Decisiones registradas el 2026-11-23:
  1. El popover muestra hasta cinco avisos (no leídas primero) y el contador se topa en "9+" (FR-002, FR-003).
  2. In-App desactivado impide nuevas entradas sin borrar el historial; el canal de correo gobierna solo los correos de notificación nuevos; un tipo desactivado impide avisos en ambos canales (FR-025, FR-026).
  3. Los correos de negocio existentes (verificación, recuperación, recordatorios y silbatos) no se modifican ni se duplican (FR-026, plan D5).
  4. Los destinos son `/groups/{groupId}`, `/groups/{groupId}/debts?debt={debtId}`, `/activity?debt={debtId}` y `/friends`; si el destino ya no existe se marca como leída, se navega a la sección general y se muestra un aviso (FR-017, FR-019).
  5. El prefijo `/api/v1/notifications` se conserva aunque el resto del proyecto use `/api/...` sin versión, porque el backlog lo exige; se justifica en `plan.md` (Complexity Tracking).
  6. Los valores por defecto son todos activos y se crean la primera vez que se consultan o guardan las preferencias (FR-025).
- Validación ejecutada: FR-001..FR-036 y SC-001..SC-010 consecutivos, sin referencias a IDs inexistentes en spec/plan/tasks y sin placeholders pendientes.
- La especificación está lista para `/speckit.plan`. Artefactos de diseño generados y alineados el 2026-11-23: `plan.md` (arquitectura, constitution check, DoR), `research.md`, `data-model.md`, `quickstart.md`, `contracts/notifications-api.md` y `tasks.md` (44 tareas con pruebas unitarias/de integración por funcionalidad).
- Corrección de consistencia aplicada: las preferencias tienen **seis** booleanos (2 canales + 4 tipos de evento), no ocho; todos los artefactos usan ese valor.
