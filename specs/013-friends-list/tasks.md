# Tasks: Ver listado de amigos

**Input**: Design documents from `/specs/013-friends-list/`

**Prerequisites**: [plan.md](./plan.md), [spec.md](./spec.md), [research.md](./research.md), [data-model.md](./data-model.md), [contracts/friends-api.md](./contracts/friends-api.md), [quickstart.md](./quickstart.md)

**Tests**: incluidas (constitución V, research.md D9). En cada historia las pruebas se escriben primero.

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

- [x] T001 Confirmar la rama `feat/visualizar-listado-amigos` desde `sprint-3` con 011 y 012 (`V12__create_friendship_requests.sql` presente) y sin dependencias nuevas — plan.md §Technical Context

---

## Phase 2: Foundational

- [x] T002 Crear `friendship/FriendResponse.java` (`id`, `firstName`, `lastName`, `email`, `friendsSince`) — data-model.md, FR-004
- [x] T003 Agregar a `friendship/FriendshipRequestRepository.java` la consulta `findFriendsOf(userId)` (JPQL con `join User ... on` a la otra cuenta, `ACCEPTED`, participación de la sesión, orden de FR-005, proyección a `FriendResponse`) — research.md D3/D4, FR-002/FR-003/FR-005

**Checkpoint**: Hibernate valida la consulta al arrancar el contexto.

---

## Phase 3: User Story 1 - Ver mis amigos (Priority: P1) 🎯 MVP

**Goal**: endpoint y sección "Mis amigos" con datos, orden, fecha y contador.

### Tests for User Story 1

- [x] T004 [P] [US1] Crear `FriendServiceTest.java`: `listFriends` consulta con el id de la sesión y devuelve la lista del repositorio — FR-014
- [x] T005 [P] [US1] Crear `FriendListIT.java`: amistades en ambas direcciones devuelven la otra cuenta; orden alfabético sin distinguir mayúsculas con apellido nulo al final; `friendsSince` presente; sin datos privados — FR-002/FR-004/FR-005
- [x] T006 [P] [US1] Ampliar `frontend/src/api/friendsClient.test.js` con `listFriends`: URL, `credentials`, 200, 4xx, 5xx y red — contracts §Contrato del frontend
- [x] T007 [P] [US1] Crear `frontend/src/components/FriendsList.test.jsx`: carga, lista con nombre/correo/avatar/"Amigos desde DD/MM/AAAA", apellido nulo, contador singular/plural — FR-001/FR-004/FR-006

### Implementation for User Story 1

- [x] T008 [US1] Crear `friendship/FriendService.java` (`@Transactional(readOnly = true)`) — FR-001
- [x] T009 [US1] Crear `friendship/FriendController.java` con `GET /api/users/me/friends` y el patrón `currentUserId()`/401 de 011 — FR-013/FR-014, contracts §GET
- [x] T010 [US1] Agregar `listFriends()` y `FRIENDS_NETWORK_ERROR_MESSAGE` a `frontend/src/api/friendsClient.js` — contracts
- [x] T011 [US1] Crear `frontend/src/components/FriendsList.jsx` y renderizarla en `pages/FriendsPage.jsx` antes de "Solicitudes"; actualizar el texto introductorio — FR-001/FR-006
- [x] T012 [US1] Agregar estilos de la sección a `frontend/src/index.scss`, reutilizando los de `friend-request-card` — FR-001

**Checkpoint**: US1 funcional (MVP).

---

## Phase 4: User Story 2 - Solo amistades reales y propias (Priority: P1)

### Tests for User Story 2

- [x] T013 [P] [US2] Ampliar `FriendListIT.java`: `PENDING` (enviada y recibida), `REJECTED` y amistades de terceros no aparecen; lista vacía devuelve `[]` — FR-003, SC-003
- [x] T014 [P] [US2] Ampliar `FriendsList.test.jsx`: vacío exacto "Todavía no tenés amigos" con enlace a `/users/search` — FR-007

### Implementation for User Story 2

- [x] T015 [US2] Verificar que el filtro de T003 cubre FR-003 y agregar el estado vacío en `FriendsList.jsx` — FR-003/FR-007

**Checkpoint**: la lista solo muestra amistades propias.

---

## Phase 5: User Story 3 - Lista al día, acceso y errores (Priority: P2)

### Tests for User Story 3

- [x] T016 [P] [US3] Ampliar `FriendListIT.java`: 401 sin sesión y con cuenta no verificada — FR-013, SC-004
- [x] T017 [P] [US3] Ampliar `FriendsList.test.jsx`: error de red con mensaje exacto y "Reintentar" sin datos obsoletos; 401 cierra la sesión; cambio de `reloadKey` vuelve a consultar — FR-010/FR-011/FR-012
- [x] T018 [P] [US3] Ampliar `frontend/src/pages/FriendsPage.test.jsx`: aceptar con éxito recarga la lista y muestra al nuevo amigo; rechazar no la recarga — FR-009, SC-006

### Implementation for User Story 3

- [x] T019 [US3] En `FriendsList.jsx`, estados de error, reintento y 401 con descarte de respuestas viejas — FR-010..FR-012
- [x] T020 [US3] En `FriendsPage.jsx`, incrementar `friendsReloadKey` cuando una aceptación devuelve 200 — FR-009

**Checkpoint**: todas las historias funcionan y resisten errores.

---

## Phase 6: Polish & Cross-Cutting Concerns

- [x] T021 [P] Revisar que 008, 010, 011 y 012 no cambian (regresión: suites completas en verde) — FR-015
- [x] T022 [P] Revisar que ninguna respuesta ni pantalla expone datos privados — FR-004, SC-005
- [ ] T023 **Validación manual (persona usuaria)**: escenarios A–F de quickstart.md §2
- [x] T024 Automatizadas: `cd backend && ./mvnw -B verify` y `cd frontend && npm run lint && npm run test:coverage && npm run build` en verde con cobertura ≥ 85 % — constitución V/XI
- [ ] T025 Commits con Conventional Commits (`docs:` spec 013, `feat:` lista de amigos, `test:`) — constitución XIII
- [ ] T026 PR de `feat/visualizar-listado-amigos` hacia `sprint-3` — constitución XII

---

## Dependencies & Execution Order

- Setup → Foundational → US1 → US2 → US3 → Polish.
- US2 refuerza la consulta de US1; US3 depende de la sección de US1.
- Backend y frontend de cada historia en paralelo una vez fijado el contrato.

## Notes

- El usuario sale siempre de la sesión.
- No se escriben datos ni se crean migraciones.
- `NoOpFriendshipPort` (grupos) no se toca en 013.
- Los commits y el PR los realiza el equipo (T025/T026).
