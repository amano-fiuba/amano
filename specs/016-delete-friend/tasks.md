# Tasks: Eliminar amigo

**Input**: Design documents from `/specs/016-delete-friend/`

**Prerequisites**: [plan.md](./plan.md), [spec.md](./spec.md), [research.md](./research.md), [data-model.md](./data-model.md), [contracts/delete-friend-api.md](./contracts/delete-friend-api.md), [quickstart.md](./quickstart.md)

**Tests**: incluidas (constitución V, research.md D7). En cada historia las pruebas se escriben primero.

**Organization**: tareas agrupadas por historia de usuario.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: puede ejecutarse en paralelo (archivos distintos, sin dependencias pendientes)
- **[Story]**: US1, US2 o US3; sin etiqueta en Setup, Foundational y Polish

## Path Conventions

- Backend: `backend/src/main/java/com/amano/backend/friendship/…`, pruebas en `backend/src/test/java/com/amano/backend/friendship/…`
- Frontend: `frontend/src/…`, pruebas `*.test.js(x)` junto al código
- Sin migraciones

---

## Phase 1: Setup

- [ ] T001 Confirmar la rama `feat/delete-friend` desde `sprint-4`, con 011, 012 y 013 integradas (`V12__create_friendship_requests.sql` presente) y sin dependencias nuevas — plan.md §Technical Context

---

## Phase 2: Foundational

- [ ] T002 [P] Crear `friendship/DirectDebtPort.java` con `hasPendingDirectDebt(Long userId, Long friendId)` — data-model.md, FR-016
- [ ] T003 [P] Crear `friendship/NoOpDirectDebtPort.java` (`@Component`, siempre `false`) documentando que el futuro módulo de deudas lo reemplaza — research.md D2, FR-016
- [ ] T004 [P] Crear `friendship/FriendNotFoundException.java` con `"No sos amigo de ese usuario"` y `friendship/PendingDirectDebtException.java` con el mensaje de deuda directa — FR-013, FR-025
- [ ] T005 Mapear las nuevas excepciones en `common/ProblemDetailExceptionHandler.java`: `FriendNotFoundException` → `404`, `PendingDirectDebtException` → `403` con `code = PENDING_DIRECT_DEBT` — research.md D4, FR-013, FR-025

**Checkpoint**: puerto, stub y errores disponibles; las historias pueden implementarse en orden.

---

## Phase 3: User Story 1 - Eliminar un amigo con confirmación (Priority: P1) 🎯 MVP

**Goal**: eliminar la amistad `ACCEPTED` con confirmación modal, actualización de la lista y confirmación de éxito.

### Tests for User Story 1

- [ ] T006 [P] [US1] Crear `FriendServiceTest.java`: eliminación exitosa borra la fila de la pareja y devuelve éxito; 404 cuando el borrado afecta 0 filas; el usuario sale de la sesión — FR-002, FR-007, FR-025, FR-030
- [ ] T007 [P] [US1] Crear `DeleteFriendIT.java`: amistad aceptada se elimina y deja de aparecer para ambas direcciones; `204`; solicitudes `PENDING`/`REJECTED` y de terceros intactas — FR-007, FR-009, SC-002
- [ ] T008 [P] [US1] Ampliar `frontend/src/api/friendsClient.test.js` con `deleteFriend`: URL, método `DELETE`, `credentials`, 204, 4xx, 5xx y red — contracts §Contrato del frontend
- [ ] T009 [P] [US1] Crear `frontend/src/components/DeleteFriendDialog.test.jsx`: muestra el nombre, foco inicial en "Cancelar", Escape, cancelar sin petición, confirmar con "Eliminando…" — FR-004..FR-006
- [ ] T010 [P] [US1] Ampliar `frontend/src/components/FriendsList.test.jsx`: el botón "Eliminar amigo" abre el diálogo y confirmar retira la tarjeta y muestra "Amigo eliminado correctamente" — FR-001, FR-008, SC-001

### Implementation for User Story 1

- [ ] T011 [US1] Agregar `deleteAcceptedBetween(a, b)` a `friendship/FriendshipRequestRepository.java` como `@Modifying` filtrado por `ACCEPTED` y pareja, devolviendo filas afectadas — research.md D6
- [ ] T012 [US1] Implementar `FriendService.deleteFriend(userId, friendIdRaw)` con validación de id, borrado condicional y `FriendNotFoundException` cuando afecta 0 filas — FR-007, FR-023, FR-030
- [ ] T013 [US1] Agregar `DELETE /api/users/me/friends/{friendId}` a `friendship/FriendController.java`, con `friendId` como `String` y `204` sin cuerpo — FR-023, contracts §DELETE
- [ ] T014 [US1] Agregar `deleteFriend()` y `DELETE_FRIEND_NETWORK_ERROR_MESSAGE` a `frontend/src/api/friendsClient.js` — contracts
- [ ] T015 [US1] Crear `frontend/src/components/DeleteFriendDialog.jsx` con el patrón accesible de `RemoveMemberDialog` — research.md D5, FR-004..FR-006
- [ ] T016 [US1] Integrar en `frontend/src/components/FriendsList.jsx` el botón "Eliminar amigo", la apertura del diálogo, la confirmación, la retirada de la tarjeta y el aviso de éxito — FR-001, FR-003, FR-006..FR-008
- [ ] T017 [US1] Agregar estilos del botón y del diálogo a `frontend/src/index.scss` — FR-001

**Checkpoint**: US1 funcional (MVP): eliminar con confirmación y éxito.

---

## Phase 4: User Story 2 - Deuda directa pendiente (Priority: P1)

**Goal**: informar y bloquear la eliminación cuando existe deuda directa pendiente; no bloquear por deudas de grupo.

### Tests for User Story 2

- [ ] T018 [P] [US2] Ampliar `FriendServiceTest.java`: elegibilidad `canDelete: false` con deuda directa; `deleteFriend` lanza `PendingDirectDebtException` y no invoca el borrado; deuda consultada con la pareja correcta; deuda saldada habilita — FR-011..FR-015, FR-016
- [ ] T019 [P] [US2] Ampliar `DeleteFriendIT.java`: con el stub `NoOpDirectDebtPort` la eliminación procede; documento de que el bloqueo real se cubre con mock hasta que exista el módulo — FR-014, FR-016
- [ ] T020 [P] [US2] Ampliar `frontend/src/api/friendsClient.test.js` con `checkFriendDeletion`: 200 `canDelete true/false`, 4xx, 5xx y red — contracts §Contrato del frontend
- [ ] T021 [P] [US2] Ampliar `FriendsList.test.jsx`: elegibilidad negativa muestra el aviso de deuda y no abre el diálogo; `403` al confirmar conserva la tarjeta y muestra el motivo — FR-004, FR-012, FR-027

### Implementation for User Story 2

- [ ] T022 [US2] Implementar `FriendService.checkDeletionEligibility(userId, friendIdRaw)` (amistad + `DirectDebtPort`) y el DTO `DeletionEligibilityResponse` — FR-003, FR-011, FR-022
- [ ] T023 [US2] Agregar `GET /api/users/me/friends/{friendId}/deletion-eligibility` a `FriendController` — FR-022, contracts §GET
- [ ] T024 [US2] Validar la deuda directa al inicio de `deleteFriend` y lanzar `PendingDirectDebtException` — FR-012, FR-013
- [ ] T025 [US2] Agregar `checkFriendDeletion()` a `frontend/src/api/friendsClient.js` — contracts
- [ ] T026 [US2] En `FriendsList.jsx`, consultar elegibilidad al pulsar "Eliminar amigo", abrir el diálogo solo si `canDelete` es `true` y mostrar el aviso bloqueante en caso contrario — FR-003, FR-004

**Checkpoint**: la deuda directa bloquea; las deudas de grupo no.

---

## Phase 5: User Story 3 - Efectos, acceso y errores (Priority: P2)

**Goal**: simetría, grupos y gastos intactos, re-agregar, validación de entrada, 401/404/400, concurrencia y errores de interfaz.

### Tests for User Story 3

- [ ] T027 [P] [US3] Ampliar `DeleteFriendIT.java`: `400` id no numérico/cero/negativo; `401` sin sesión y cuenta no verificada; `404` no amigo y auto-id; re-agregar con nueva solicitud aceptada; grupos y gastos previos intactos — FR-009, FR-010, FR-017..FR-021, FR-024, FR-025
- [ ] T028 [P] [US3] Ampliar `FriendServiceTest.java`: dos eliminaciones sobre la misma amistad → una éxito y una `FriendNotFoundException`; validación antes de consultar — FR-024, FR-030, SC-007
- [ ] T029 [P] [US3] Ampliar `FriendsList.test.jsx`: `404` retira la tarjeta y muestra "No sos amigo de ese usuario"; error de red mantiene la tarjeta y muestra el mensaje exacto; `401` cierra sesión — FR-026, FR-028, FR-029
- [ ] T030 [P] [US3] Agregar a `FriendsList.test.jsx` la persistencia de la confirmación de éxito al eliminar el último amigo y el estado vacío de 013 — FR-031

### Implementation for User Story 3

- [ ] T031 [US3] En `FriendsList.jsx`, manejar `404`, `403`, red y `401` según el contrato, manteniendo o retirando la tarjeta y conservando el aviso de éxito — FR-026..FR-031
- [ ] T032 [US3] Verificar que la eliminación no toca `expenses`, `group_members`, `group_activity` ni `groups` y que 010 sigue usando `AcceptedFriendshipPort` sin cambios — FR-014, FR-017..FR-019

**Checkpoint**: todas las historias funcionan y resisten errores y concurrencia.

---

## Phase 6: Polish & Cross-Cutting Concerns

- [ ] T033 [P] Revisar que el contrato de `GET /api/users/me/friends` de 013 no cambió y que 008/010/011/012 siguen en verde — FR-032
- [ ] T034 [P] Revisar que ninguna respuesta ni pantalla expone datos privados ni detalles internos — FR-033
- [ ] T035 **Validación manual (persona usuaria)**: escenarios A–I de quickstart.md §2
- [ ] T036 Automatizadas: `cd backend && ./mvnw -B verify` y `cd frontend && npm run lint && npm run test:coverage && npm run build` en verde con cobertura ≥ 85 % — constitución V/XI
- [ ] T037 Commits con Conventional Commits (`docs:` spec 016, `feat:` eliminar amigo, `test:`) — constitución XIII
- [ ] T038 PR de `feat/delete-friend` hacia `sprint-4` — constitución XII

---

## Dependencies & Execution Order

- Setup → Foundational → US1 → US2 → US3 → Polish.
- US2 depende de la elegibilidad y de la eliminación de US1 para mostrar el bloqueo antes del diálogo.
- US3 refina el manejo de errores de US1/US2.
- Backend y frontend de cada historia pueden avanzar en paralelo una vez fijado el contrato.

## Notes

- La persona sale siempre de la sesión; el amigo sale de la ruta.
- No se crean migraciones ni se escriben datos fuera de `friendship_requests`.
- El bloqueo por deuda directa se prueba con mock hasta que exista el módulo real de deudas.
- Los commits y el PR los realiza el equipo (T037/T038).
