# Tasks: Abandonar grupo

**Input**: Design documents from `/specs/020-leave-group/`

**Prerequisites**: [plan.md](./plan.md), [spec.md](./spec.md), [research.md](./research.md), [data-model.md](./data-model.md), [contracts/leave-group-api.md](./contracts/leave-group-api.md), [quickstart.md](./quickstart.md)

**Tests**: incluidas (constitución V). En cada historia las pruebas se escriben primero.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: se puede ejecutar en paralelo
- **[Story]**: US1 a US4; sin etiqueta en Foundational y Polish
- T001 y T002 ya estaban publicadas en `551a2f0`

## Path Conventions

- Backend: `backend/src/main/java/com/amano/backend/groups/…`, pruebas en `backend/src/test/java/com/amano/backend/groups/…`
- Frontend: `frontend/src/…`, pruebas `*.test.jsx` junto al código
- Sin migraciones

---

## Phase 1: Foundational

- [x] T001 `GroupService.leaveGroup`: el último miembro hace baja lógica sin el bloqueo de último administrador — FR-011, FR-012
- [x] T002 `LeaveGroupResponse.groupDeleted` e `isLastMember` en `GroupDetailResponse` — FR-012, FR-014
- [x] T003 Agregar `isLastAdmin` a `dto/GroupDetailResponse.java` y calcularlo en `GroupService.toDetail` — research.md D4, FR-014
- [x] T004 Aclarar en el comentario de `GroupService.leaveGroup` que no se borra ningún registro — FR-013
- [x] T005 Actualizar `LeaveGroupIT.memberLeavesTheGroupAndItPersistsForTheOthers`, que todavía espera `LAST_ADMIN` cuando el administrador queda solo — research.md D8

**Checkpoint**: backend alineado con la spec.

---

## Phase 2: User Story 1 - Abandonar con otras personas (Priority: P1) 🎯 MVP

- [x] T006 [P] [US1] `LeaveGroupModal.test.jsx` (modo `confirm`): texto, Confirmar, Cancelar y equis; Cancelar y equis cierran sin llamar a la API — FR-001, FR-002
- [x] T007 [P] [US1] `LeaveGroupPanel.test.jsx`: confirmar navega a `/groups` con `replace`; el motivo del servidor se muestra en el modal — FR-006, FR-007
- [x] T008 [US1] Crear `components/groups/LeaveGroupModal.jsx` con el modo `confirm` y la equis — FR-001, FR-005
- [x] T009 [US1] Adaptar `components/groups/LeaveGroupPanel.jsx` para usar el modal — FR-002, FR-007

**Checkpoint**: abandonar con otros miembros funciona con el modal.

---

## Phase 3: User Story 2 - Único administrador (Priority: P1)

- [x] T010 [P] [US2] Prueba de integración: `isLastAdmin` verdadero solo para el único administrador activo, también con otros miembros — FR-014
- [x] T011 [P] [US2] `LeaveGroupIT`: único administrador con otros miembros recibe 403 `LAST_ADMIN` — FR-010
- [x] T012 [P] [US2] `LeaveGroupModal.test.jsx` (modo `last-admin`): texto exacto, sin "Confirmar" ni "Cancelar", solo la equis — FR-003
- [x] T013 [US2] `LeaveGroupModal.jsx`: modo `last-admin` — FR-003
- [x] T014 [US2] `GroupInfoView.jsx`: reemplazar la deducción sobre `memberCount` por `group.isLastAdmin` y actualizar `GroupInfoView.test.jsx`, que hoy prueba esa deducción con `memberCount: 1` — FR-008

- [x] T029 [US2] `MemberListView.jsx`: quitar el aviso de arriba del listado y su estilo `member-rows__hint`, con prueba en `MemberListView.test.jsx` — FR-016

**Checkpoint**: el único administrador ve el aviso sin botones antes de intentar salir, y solo en el modal.

---

## Phase 4: User Story 3 - Último miembro (Priority: P1)

- [x] T015 [P] [US3] `LeaveGroupIT`: el último miembro abandona con 200 y `groupDeleted = true`; luego `GET /api/groups/{id}` y repetir `leave` responden 404; `GET /api/groups` no lo lista — FR-011, FR-012, FR-015
- [x] T016 [P] [US3] `LeaveGroupIT`: tras abandonar, las filas del grupo siguen en la base y solo cambian `left_at` y `MEMBER_LEFT` — FR-013, SC-004
- [x] T017 [P] [US3] `LeaveGroupIT`: con saldos pendientes el último miembro recibe `PENDING_DEBTS` — FR-009
- [x] T018 [P] [US3] `LeaveGroupIT`: quedando solo con perfiles simulados, `isLastMember` es verdadero — FR-014
- [x] T019 [P] [US3] `LeaveGroupModal.test.jsx` (modo `last-member`): texto exacto, acciones y prioridad sobre `last-admin` — FR-004
- [x] T020 [US3] `LeaveGroupModal.jsx`: modo `last-member` — FR-004

**Checkpoint**: el último miembro sale, el grupo deja de mostrarse y los datos quedan.

---

## Phase 5: User Story 4 - Modal accesible (Priority: P2)

- [x] T021 [P] [US4] `LeaveGroupModal.test.jsx`: foco, `role="dialog"`, Escape, sin cierre con solicitud en curso — FR-005
- [x] T022 [P] [US4] `LeaveGroupPanel.test.jsx`: un solo envío con doble clic, reintento tras error de red, 401 cierra la sesión — FR-005, FR-006
- [x] T023 [US4] `LeaveGroupModal.jsx`: foco inicial, Escape y `busy` — FR-005
- [x] T024 [US4] `LeaveGroupPanel.jsx`: manejo de 401 y de red — FR-006
- [x] T025 [P] [US4] `groups.scss`: estilos del modal y la equis, responsive

---

## Phase 6: Polish

- [x] T026 Ejecutar `./mvnw verify` y `npx vitest run --coverage`; confirmar umbrales ≥ 85 %
- [x] T027 `npx eslint src` sin errores
- [ ] T028 Recorrer quickstart.md §2

---

## Dependencies & Execution Order

- Foundational → US1 → US2 → US3 → US4 → Polish.
- US2 y US3 dependen de T003 y T008.
- T005 conviene hacerlo primero: la prueba hoy no refleja el código publicado.

## Notes

- Los commits los realiza el equipo.
