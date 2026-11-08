# Tasks: Panel de reparto del gasto de grupo

**Input**: Design documents from `/specs/024-split-panel/`

**Prerequisites**: [plan.md](./plan.md), [spec.md](./spec.md), [research.md](./research.md), [data-model.md](./data-model.md), [quickstart.md](./quickstart.md)

**Tests**: la implementación está en la rama `feat/group-debt-management`; las tareas abiertas cubren lo que quedó sin verificar.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: se puede ejecutar en paralelo
- **[Story]**: US1 a US6

---

## Phase 1: User Story 1 - Panel propio (Priority: P1)

- [x] T001 [US1] `GroupExpenseFormView.jsx`: dos columnas (datos del gasto y panel), clase `group-expense--wide` solo al crear — FR-001
- [x] T002 [US1] Interruptor "Gasto compartido" (`role="switch"`, encendido) en lugar de la casilla de gasto personal — FR-002, FR-003, FR-018
- [x] T003 [US1] Botones "Partes iguales", "Personalizado" y "Porcentajes" — FR-004, FR-017
- [x] T004 [US1] Una fila por miembro con casilla y monto por fila; nota del centavo en Partes iguales — FR-005, FR-010
- [x] T005 [P] [US1] Tests adaptados en `GroupExpenseFormView.test.jsx` y nuevo `el interruptor de gasto compartido arranca encendido` — FR-002 a FR-005

---

## Phase 2: User Story 2 - Porcentajes con sliders (Priority: P1)

- [x] T006 [US2] Slider de 0 a 100 de a 1 % y campo con decimales sobre el mismo valor — FR-008, FR-018
- [x] T007 [US2] Estimación en vivo por fila e indicador con barra de progreso — FR-012, FR-014
- [x] T008 [P] [US2] Tests: `el slider de porcentaje carga el valor del participante y el campo lo muestra`, `con porcentajes incompletos cada fila ya muestra su monto estimado`, `al sacar a alguien en porcentajes desaparecen su slider y su campo` — FR-008, FR-012

---

## Phase 3: User Story 3 - Importes personalizados (Priority: P1)

- [x] T009 [US3] Campo de importe por fila con la moneda del gasto — FR-009
- [x] T010 [P] [US3] Tests de 022 T072 adaptados a los botones de método — FR-009, FR-016

---

## Phase 4: User Story 4 - Repartir lo que falta (Priority: P2)

- [x] T011 [US4] `fillRemaining`: faltante en unidades enteras, entre quienes no tienen valor o entre todos, sobrantes a los primeros — FR-015
- [x] T012 [P] [US4] Tests: `repartir lo que falta completa en partes iguales los porcentajes vacíos`, `repartir lo que falta reparte los centavos sobrantes y, si todos tienen valor, suma a todos` — FR-015

---

## Phase 5: User Story 5 - Dos monedas (Priority: P2)

- [x] T013 [US5] Monto en la moneda del gasto y "≈" en la del grupo, con las reglas de `GroupExpenseService.sharesOf` — FR-010, FR-011
- [x] T014 [P] [US5] Tests: `en otra moneda cada fila muestra el monto en la moneda del gasto y en la del grupo` y el de 022 `en otra moneda mantiene la vista previa convertida…` adaptado — FR-011

---

## Phase 6: User Story 6 - Panel compacto (Priority: P2)

- [x] T015 [US6] Una línea por persona y textos de ayuda movidos a tooltip o a lectores de pantalla — FR-005, FR-017
- [x] T016 [US6] Columnas compartidas con `subgrid` y columna de monto fija en Porcentajes, con tooltip — FR-013
- [x] T017 [US6] Nombre de pila con nombre completo en tooltip y oculto visualmente; nombres repetidos completos — FR-006
- [x] T018 [US6] `PaidIcon` en `NavIcons.jsx` para quien pagó — FR-007
- [x] T019 [US6] Diseño apilado de las filas por debajo de 560px — US6 escenario 8
- [x] T020 [P] [US6] Tests: `muestra solo el nombre de pila y el nombre completo queda en el tooltip` y el tooltip de quien pagó — FR-006, FR-007

---

## Phase 7: Polish

- [ ] T021 Recorrer quickstart.md §2 en navegador, en modo claro y oscuro
- [ ] T022 [P] Decidir si se borra `ExpenseSharePreview.jsx` y su bloque en `ExpenseDebtList.test.jsx`: el formulario solo importa sus textos de la nota del centavo
- [ ] T023 [P] Investigar `GroupDebtsView.test.jsx` › `permite editar a un monto parcial…`: falla a veces en la suite completa y pasa sola (fuera de esta spec)
- [ ] T024 Correr `npm run test:coverage` y confirmar el umbral de 85 %

## Notes

- La spec 022 no se edita; `spec.md` indica qué puntos de T072 y T077 quedan reemplazados.
- Verificación al cierre: 970 tests en 79 archivos, lint sin errores (2 advertencias previas) y build OK.
- Los commits los realiza el equipo.
