# Recordatorios automáticos de deudas Checklist: Calidad de requisitos

**Purpose**: Revisar que los requisitos de la 025 sean completos, claros, consistentes y verificables antes de implementar, con foco en programación/estados/idempotencia, configuración/permisos y contenido/fallos de entrega.
**Created**: 2026-11-07
**Feature**: [spec.md](../spec.md)

**Note**: Este checklist lo genera `/speckit.checklist` a partir del contexto de la feature. No verifica implementación: evalúa la calidad de los requisitos.
**Review Ownership**: Es un artefacto de revisión propiedad del revisor. Marcar `[x]` solo cuando el revisor determine que el criterio de calidad de requisitos está satisfecho.
**Marker Semantics**: `[x]` significa que el criterio fue revisado y satisfecho en cuanto a calidad de requisitos. No significa que la implementación esté terminada.

## Completitud de requisitos

- [ ] CHK001 ¿Están enumerados con precisión los estados de deuda que habilitan el recordatorio y los que lo excluyen? [Completeness, Spec §FR-006, Spec §FR-011]
- [ ] CHK002 ¿Se especifica el cálculo de la fecha límite y de la fecha de envío, incluido el caso de grupo sin días de vencimiento? [Completeness, Spec §FR-007, Spec §Edge Cases]
- [ ] CHK003 ¿Está definido qué ocurre si la fecha de envío ya pasó pero la fecha límite aún no? [Completeness, Spec §FR-009]
- [ ] CHK004 ¿Se especifican todos los disparadores de cancelación y reprogramación de un recordatorio programado? [Completeness, Spec §FR-003, Spec §FR-011, Spec §Edge Cases]
- [ ] CHK005 ¿Están documentados los estados del recordatorio y sus transiciones válidas? [Completeness, Spec §Key Entities, Plan §D3]
- [ ] CHK006 ¿Se establece explícitamente la unicidad por deuda y por gasto? [Completeness, Spec §FR-010, Spec §FR-018]
- [ ] CHK007 ¿Están completos los campos configurables, sus valores predeterminados y la lista fija de tiempos? [Completeness, Spec §FR-001, Spec §FR-002, Spec §FR-005]
- [ ] CHK008 ¿Están enumerados todos los destinatarios posibles, incluidos los perfiles simulados? [Completeness, Spec §FR-008, Spec §Clarifications]
- [ ] CHK009 ¿Se especifica el contenido obligatorio de los correos de recordatorio y de aviso al pagador? [Completeness, Spec §FR-012, Spec §FR-016, Spec §FR-017]
- [ ] CHK010 ¿Están definidos el manejo de fallos de entrega, la notificación al administrador y la exclusión del historial? [Completeness, Spec §FR-014]
- [ ] CHK011 ¿Están definidos la interfaz del Administrador, las validaciones y los errores del formulario? [Completeness, Spec §FR-019, Spec §FR-005]

## Claridad de requisitos

- [ ] CHK012 ¿Es inequívoco que el tiempo de envío se expresa en días antes del vencimiento y no como fecha absoluta? [Clarity, Spec §FR-002]
- [ ] CHK013 ¿Están definidos sin ambigüedad "deuda pendiente" y "deuda saldada" en términos de los estados existentes? [Clarity, Spec §FR-006, Spec §FR-011]
- [ ] CHK014 ¿Está claro el tratamiento de una deuda en "Pago informado" tanto para el recordatorio como para el aviso al pagador? [Clarity, Spec §FR-012, Spec §Clarifications]
- [ ] CHK015 ¿Se define "fecha límite de pago" por referencia explícita a la regla de 022? [Clarity, Spec §FR-007, Spec §Assumptions]
- [ ] CHK016 ¿Está cuantificado "cuánto falta para el vencimiento" en el aviso al pagador? [Clarity, Spec §FR-016, Plan §D14]
- [ ] CHK017 ¿Están precisados el límite de 500 caracteres y el efecto de un mensaje en blanco? [Clarity, Spec §FR-005]
- [ ] CHK018 ¿Es claro que se envía un correo por deuda y que no se consolidan varias deudas? [Clarity, Spec §FR-010, Spec §Assumptions]
- [ ] CHK019 ¿Es inequívoco quién recibe el aviso de fallo cuando el grupo tiene más de un administrador? [Ambiguity, Spec §FR-014]

## Consistencia de requisitos

- [ ] CHK020 ¿Son consistentes las reglas de elegibilidad entre FR-006, FR-009, FR-011 y los casos límite? [Consistency, Spec §FR-006, Spec §FR-009, Spec §FR-011]
- [ ] CHK021 ¿Son consistentes las reglas de fecha del recordatorio y del aviso al pagador? [Consistency, Spec §FR-018]
- [ ] CHK022 ¿Son consistentes las reglas de cancelación y no reenvío entre las cuatro historias? [Consistency, Spec §User Story 1, Spec §User Story 2, Spec §User Story 3, Spec §User Story 4]
- [ ] CHK023 ¿Coinciden los valores predeterminados de tiempo y mensaje entre requisitos, supuestos y criterios? [Consistency, Spec §FR-001, Spec §FR-002, Spec §Assumptions]
- [ ] CHK024 ¿Son consistentes los permisos de consulta y modificación de la configuración? [Consistency, Spec §FR-004]
- [ ] CHK025 ¿Se alinean los eventos de historial con el modelo y los filtros de la 014? [Consistency, Spec §FR-013, Spec §FR-018]

## Calidad de criterios de aceptación

- [ ] CHK026 ¿Los criterios SC-001, SC-002 y SC-003 son medibles sin depender de detalles de implementación? [Measurability, Spec §SC-001, Spec §SC-002, Spec §SC-003]
- [ ] CHK027 ¿Los criterios SC-004, SC-005, SC-006 y SC-007 son verificables de forma objetiva y distinguen envíos exitosos de fallos? [Measurability, Spec §SC-004, Spec §SC-005, Spec §SC-006, Spec §SC-007, Spec §FR-014]

## Cobertura de escenarios

- [ ] CHK028 ¿Están cubiertos los flujos alternativos de deuda creada después de la fecha de envío y de cambio de configuración? [Coverage, Spec §Edge Cases, Spec §FR-003]
- [ ] CHK029 ¿Están cubiertos los flujos de excepción y recuperación ante correo inválido o fallo de entrega? [Coverage, Spec §FR-014, Spec §Assumptions]
- [ ] CHK030 ¿Están especificados los requisitos no funcionales de evaluación diaria, zona horaria y granularidad por día? [Coverage, Spec §Assumptions]
- [ ] CHK031 ¿Están cubiertos los casos de pagador que también es deudor y de múltiples administradores? [Coverage, Spec §FR-017, Spec §FR-014]

## Cobertura de casos límite

- [ ] CHK032 ¿Se abordan los casos de grupo sin días de vencimiento, deuda vencida y deuda saldada antes del envío? [Edge Case, Spec §Edge Cases]
- [ ] CHK033 ¿Se abordan los cambios de configuración con recordatorios ya enviados y con programados aún no enviados? [Edge Case, Spec §FR-003]
- [ ] CHK034 ¿Se abordan múltiples deudas del mismo miembro y múltiples gastos del mismo pagador con la misma fecha límite? [Edge Case, Spec §Assumptions]
- [ ] CHK035 ¿Se abordan el correo inválido de un perfil simulado y la salida del grupo antes del envío? [Edge Case, Spec §Edge Cases, Spec §Clarifications]

## Dependencias y supuestos

- [ ] CHK036 ¿Están documentadas las dependencias con 022 (vencimiento), 003/006 (correo) y 014 (historial)? [Dependency, Spec §Relación con otras especificaciones]
- [ ] CHK037 ¿Están validados los supuestos de que el pagador siempre es un usuario registrado, de que un miembro con deudas no puede salir y de un correo por deuda? [Assumption, Spec §Assumptions]

## Ambigüedades y conflictos

- [ ] CHK038 ¿Está resuelto qué ocurre si el grupo no tiene administradores activos y cómo se interpreta "el Administrador" cuando hay varios? [Ambiguity, Gap, Spec §FR-014]
- [ ] CHK039 ¿Está definido el comportamiento del aviso al pagador cuando tiene deudas en más de una moneda? [Ambiguity, Spec §FR-017, Spec §Assumptions]
- [ ] CHK040 ¿Están definidos el concepto cuando el gasto no tiene descripción, el alcance del mensaje personalizado y el límite con los días de vencimiento de 022? [Ambiguity, Conflict, Spec §FR-012, Spec §Assumptions, Spec §Fuera de alcance]

## Notes

- Marcar `[x]` solo después de revisar y confirmar que el criterio de calidad de requisitos está satisfecho.
- Dejar sin marcar los ítems que todavía necesiten aclaración, corrección o evaluación del revisor.
- `checklists/requirements.md` tiene un ciclo de vida separado, mantenido por `/speckit.specify` y `/speckit.clarify`.
- Este checklist se generó para revisión del autor antes de implementar, con foco en programación/estados/idempotencia, configuración/permisos y contenido/fallos de entrega.
