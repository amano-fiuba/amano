# Tasks: Renovación de la interfaz

**Input**: Design documents from `/specs/023-ui-refresh/`

**Prerequisites**: [plan.md](./plan.md), [spec.md](./spec.md), [research.md](./research.md), [data-model.md](./data-model.md), [contracts/ui-refresh-api.md](./contracts/ui-refresh-api.md), [quickstart.md](./quickstart.md)

**Tests**: la implementación está en la rama `feat/teclas-y-patron-manos`; las pruebas abiertas cubren lo que quedó sin verificar.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: se puede ejecutar en paralelo
- **[Story]**: US1 a US6

---

## Phase 1: User Story 1 - Identidad visual (Priority: P1)

- [x] T001 [US1] `detalles.scss`: teclas en relieve, con hover y presionado — FR-001, FR-006
- [x] T002 [US1] `detalles.scss`: naranja del logo, texto oscuro sobre naranja y rojo en acciones destructivas — FR-002 a FR-005
- [x] T003 [US1] `--radio-tarjeta` y `--radio-control` en `index.scss` y su uso en toda la app — FR-007
- [x] T004 [US1] `patron-fondo.svg` como máscara, con `--color-patron` por tema — FR-008, FR-009

---

## Phase 2: User Story 2 - Fotos en solicitudes (Priority: P1)

- [x] T005 [US2] `avatarUrl` en `FriendshipUserSummary` y `ReceivedFriendshipRequestResponse`; `findIdsWithAvatar` — FR-010
- [x] T006 [US2] `AvatarVisibility` y `existsBySenderIdAndRecipientIdAndStatus` — FR-011, FR-012
- [x] T007 [US2] `NotificationsMenu.jsx` y `FriendsPage.jsx` muestran la foto — FR-013
- [x] T008 [US2] Pruebas: `FriendListIT`, `NotificationsMenu.test.jsx`
- [ ] T009 [P] [US2] `FriendsPage.test.jsx`: la solicitud muestra `sender.avatarUrl`, y el avatar por defecto si no hay — FR-013

---

## Phase 3: User Story 3 - Barra y actividad personal (Priority: P2)

- [x] T010 [US3] `GET /api/expenses`, `ExpenseService.listHistory` y `findTop200ByUserIdOrderByCreatedAtDescIdDesc` — FR-015
- [x] T011 [US3] `AppHeader.jsx` y `NavIcons.jsx`: accesos, íconos y orden — FR-014
- [x] T012 [US3] `listExpenseHistory`, `ActivityPage.jsx` y ruta `/activity` — FR-016
- [x] T013 [US3] `PersonalActivity.jsx`: "Ver todo" — FR-017
- [x] T014 [US3] `AppHeader.scss`: panel de notificaciones y barra en pantallas angostas
- [x] T015 [US3] Pruebas: `RecurringExpenseIT`, `ActivityPage.test.jsx`, `SiteNav.test.jsx`
- [ ] T016 [P] [US3] Prueba de `PersonalActivity`: "Ver todo" enlaza a `/activity` — FR-017
- [ ] T017 [P] [US3] `RecurringExpenseIT`: `GET /api/expenses` no devuelve más de 200 gastos — FR-015

---

## Phase 4: User Story 4 - Grupo de un vistazo (Priority: P1)

- [x] T018 [US4] `GroupInfoView.jsx` y `groups.scss`: encabezado y `.group-board` en tres columnas — FR-018, FR-019
- [x] T019 [US4] `GroupFeed.jsx` y `GroupExpensesCard.jsx` sobre `GET /api/groups/{id}/activity` — FR-020, FR-021
- [x] T020 [US4] `MemberBalance.jsx` conectado a `MemberListView.jsx` — FR-022
- [x] T021 [US4] Pruebas: `GroupInfoView.test.jsx`, `MemberListView.test.jsx`

---

## Phase 5: User Story 5 - Crear grupo eligiendo amigos (Priority: P1)

- [x] T024 [US5] `CreateGroupRequest.members` y `GroupService.createGroup`: varios miembros, validación previa, repetidos — FR-028, FR-029, FR-030
- [x] T025 [P] [US5] `GroupServiceTest` y `GroupRealFriendshipIT`: varios miembros, `member` sigue válido, atomicidad, repetidos y eventos — FR-028 a FR-030, SC-007
- [x] T026 [US5] `FriendPicker.jsx`: lista, filtro, contador y estados de carga, vacío y error — FR-023, FR-024, FR-026, FR-027
- [x] T027 [US5] `CreateGroupView.jsx`: amigos y perfiles simulados combinados, y apertura del grupo creado — FR-025, FR-026, FR-031
- [x] T028 [P] [US5] `FriendPicker.test.jsx` y `CreateGroupView.test.jsx` — FR-023 a FR-027, FR-031

---

## Phase 6: User Story 6 - Fotos en el buscador (Priority: P2)

- [x] T029 [US6] `AvatarVisibility.visibleAmong` y `findActiveMatesAmong` — FR-032, FR-033
- [x] T030 [US6] `avatarUrl` en `UserSearchResult` y `UserSearchService` — FR-032
- [x] T031 [US6] `GlobalSearch.jsx` y `UsersSearchPage.jsx` muestran las fotos — FR-034, FR-035
- [x] T032 [P] [US6] Pruebas: `UserSearchServiceTest`, `UserSearchIT`, `GlobalSearch.test.jsx`, `UsersSearchPage.test.jsx` — FR-032 a FR-035, SC-008

---

## Phase 7: Polish

- [x] T022 Mezclar `sprint-4` y comprobar que 020 y 021 siguen vigentes — research.md D8
- [ ] T023 Recorrer quickstart.md §2

## Notes

- Las specs 002, 008, 010 y 011 no se editan; `spec.md` indica qué puntos quedan reemplazados.
- Calcular y enviar los saldos no es parte de esta spec.
- Los commits los realiza el equipo.
