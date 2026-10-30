# Tasks: Enviar solicitud de amistad

**Input**: Design documents from `/specs/011-send-friend-request/`

**Prerequisites**: [plan.md](./plan.md), [spec.md](./spec.md), [research.md](./research.md), [data-model.md](./data-model.md), [contracts/friendship-requests-api.md](./contracts/friendship-requests-api.md), [quickstart.md](./quickstart.md)

**Tests**: incluidas. La constitución (principio V) exige pruebas automatizadas para toda
funcionalidad nueva, y [research.md](./research.md) §D12 define la estrategia: unitarios con
Mockito, integración con Testcontainers, Vitest en frontend y cobertura ≥ 85 % en ambos
módulos. En cada historia las pruebas se escriben **primero** y deben fallar antes de
implementar.

**Organization**: tareas agrupadas por historia de usuario para permitir implementación,
prueba y revisión independientes.

**Estado**: todas las tareas quedan pendientes `[ ]`.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: puede ejecutarse en paralelo (archivos distintos, sin dependencias pendientes)
- **[Story]**: US1, US2, US3 o US4; sin etiqueta en Setup, Foundational y Polish
- Todas las rutas son reales del repositorio

## Path Conventions

- Backend: `backend/src/main/java/com/amano/backend/…`, pruebas en `backend/src/test/java/…`
- Frontend: `frontend/src/…`, pruebas `*.test.jsx` / `*.test.js` colocadas junto al código
- Migración nueva: `backend/src/main/resources/db/migration/V6__create_friendship_requests.sql`

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: confirmar la rama, la línea base y crear la migración.

- [ ] T001 Confirmar que la rama activa es `feat/send-friend-request` y que no se requieren dependencias nuevas: `backend/pom.xml` y `frontend/package.json` sin cambios — plan.md §Technical Context
- [ ] T002 Ejecutar la línea base antes de tocar código: `cd backend && ./mvnw -B verify` y `cd frontend && npm ci && npm run lint && npm run test:coverage && npm run build` — quickstart.md §1
- [ ] T003 Crear `backend/src/main/resources/db/migration/V6__create_friendship_requests.sql` con la tabla `friendship_requests`, CHECK `ck_friendship_requests_not_self`, CHECK de estado, CHECK de `responded_at`, FKs a `users` con `ON DELETE CASCADE`, `uq_friendship_requests_pending_pair`, `uq_friendship_requests_accepted_pair` e `ix_friendship_requests_recipient_pending`; sin `UPDATE` ni `DELETE` — data-model.md §Cambios de esquema, FR-003/FR-005/FR-009

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: piezas compartidas por todas las historias. **Ninguna historia puede empezar hasta completar esta fase.**

- [ ] T004 [P] Crear `friendship/FriendshipRequestStatus.java` (`PENDING`, `ACCEPTED`, `REJECTED`) — data-model.md
- [ ] T005 [P] Crear `friendship/FriendshipUserSummary.java` con exactamente `Long id`, `String firstName`, `String lastName`, `String email`; sin CBU/CVU, monedas, contraseña ni avatar — FR-011, research.md §D11
- [ ] T006 Crear `friendship/FriendshipRequest.java` mapeada a `friendship_requests` con `id`, `senderId`, `recipientId`, `status` (`@Enumerated(STRING)`), `createdAt` y `respondedAt`; sin relaciones JPA — data-model.md §Mapeo JPA
- [ ] T007 Crear `friendship/FriendshipRequestRepository.java` con consultas `@Query` explícitas: `existsPendingBetween(a, b)` (una sola consulta con `LEAST`/`GREATEST` o con ambas direcciones), `existsAcceptedBetween(a, b)` y `findReceivedPending(recipientId)` ordenada por `createdAt DESC, id DESC` — FR-005/FR-006/FR-012, data-model.md
- [ ] T008 [P] Crear las excepciones `friendship/AlreadyFriendsException.java`, `friendship/PendingFriendshipRequestExistsException.java` y `friendship/FriendshipRecipientNotFoundException.java` — FR-005/FR-006/FR-007
- [ ] T009 Agregar a `common/ProblemDetailExceptionHandler.java` los handlers de `AlreadyFriendsException` y `PendingFriendshipRequestExistsException` (409) y `FriendshipRecipientNotFoundException` (404), con los mensajes exactos de la spec — FR-007/FR-017/FR-023, contracts §404/§409
- [ ] T010 [P] Crear `backend/src/test/java/com/amano/backend/friendship/FriendshipRequestSchemaIT.java` con Testcontainers: auto-relación rechazada, estado fuera del catálogo rechazado, `PENDING` con `responded_at` no nulo rechazado, dos `PENDING` por pareja (misma e inversa dirección) rechazadas, dos `ACCEPTED` por pareja rechazadas y `ON DELETE CASCADE` al borrar una cuenta — data-model.md, FR-009
- [ ] T011 Verificar que `/api/users/**` ya exige sesión verificada en `config/SecurityConfig.java`; no se modifica la configuración — FR-021/FR-022, research.md §D4

**Checkpoint**: Hibernate valida la entidad contra `V6` (`ddl-auto=validate`); la base aplica los invariantes; las historias pueden comenzar.

---

## Phase 3: User Story 1 - Enviar una solicitud desde el buscador (Priority: P1) 🎯 MVP

**Goal**: el botón "Agregar" de 008 envía una solicitud y esta queda persistida `PENDING`, con confirmación visual.

**Independent Test**: con dos cuentas verificadas, buscar a la segunda desde la primera, pulsar "Agregar" y verificar "Solicitud de amistad enviada", el botón "Solicitud enviada" deshabilitado y una fila `PENDING` con emisor y destinatario correctos.

### Tests for User Story 1 ⚠️ (primero, deben FALLAR)

- [ ] T012 [P] [US1] Crear `friendship/FriendshipRequestServiceTest.java` (Mockito): `send` válido persiste `PENDING` con el emisor de la sesión, `createdAt` y `respondedAt` nulo, y devuelve el resumen del destinatario; no se usa ningún id de emisor recibido por parámetro — FR-002/FR-003
- [ ] T013 [P] [US1] Crear `friendship/FriendshipRequestIT.java` (MockMvc + Testcontainers): `POST /api/users/me/friendship-requests` con una cuenta verificada responde `201`, cuerpo con `status: "PENDING"`, `recipient` y sin datos privados; la fila existe en la base con el emisor de la sesión — FR-002/FR-003, contracts §201
- [ ] T014 [P] [US1] Crear `frontend/src/api/friendsClient.test.js` con el caso de `sendFriendshipRequest`: método `POST`, URL correcta, `credentials: 'include'`, cuerpo `{ recipientId }` y `{ ok: true, request }` en 201 — contracts §Contrato del frontend
- [ ] T015 [P] [US1] Ampliar `frontend/src/pages/UsersSearchPage.test.jsx`: el botón "Agregar" está habilitado; al pulsarlo llama a `sendFriendshipRequest` con el id de la fila, muestra "Enviando…" y luego "Solicitud enviada" deshabilitado con el aviso "Solicitud de amistad enviada"; las demás filas no cambian de estado — FR-001/FR-002/FR-016

### Implementation for User Story 1

- [ ] T016 [US1] Crear `friendship/SendFriendshipRequestRequest.java` con `Long recipientId` — contracts §Cuerpo
- [ ] T017 [US1] Crear `friendship/FriendshipRequestResponse.java` (`id`, `status`, `createdAt`, `recipient`) y el mapeo desde `FriendshipRequest` + `User` — contracts §201
- [ ] T018 [US1] Crear `friendship/FriendshipRequestService.java` con `send(senderId, recipientId)` `@Transactional`: resuelve el destinatario, inserta la solicitud `PENDING` y devuelve la respuesta; las validaciones de negocio se completan en US2 — FR-002/FR-003, research.md §D3
- [ ] T019 [US1] Crear `friendship/FriendshipRequestController.java` con `POST /api/users/me/friendship-requests`: obtiene el principal de la sesión con el patrón `currentUserId()` y delega en el servicio — FR-002/FR-022, contracts §POST
- [ ] T020 [US1] Agregar `sendFriendshipRequest(recipientId)` a `frontend/src/api/friendsClient.js` con la convención de `usersClient` (nunca lanza; 2xx/4xx/red) — contracts §Cliente
- [ ] T021 [US1] En `frontend/src/pages/UsersSearchPage.jsx`: habilitar "Agregar", enviar con el `id` de la fila, estado de carga por fila, estado "Solicitud enviada" y aviso de éxito; eliminar la nota "próximamente" — FR-001/FR-016, research.md §D9
- [ ] T022 [US1] Verificación de la historia: `cd backend && ./mvnw -B -Dtest=FriendshipRequestServiceTest test && ./mvnw -B -Dit.test=FriendshipRequestIT verify`; `cd frontend && npx vitest run src/api/friendsClient.test.js src/pages/UsersSearchPage.test.jsx` en verde

**Checkpoint**: US1 funcional y probable de forma independiente (MVP).

---

## Phase 4: User Story 2 - Reglas de negocio al enviar (Priority: P1)

**Goal**: rechazar auto-solicitudes, duplicados pendientes en cualquier dirección, amistades existentes y destinatarios inválidos, sin crear filas.

**Independent Test**: preparar cada caso y verificar el código HTTP, el mensaje exacto y la ausencia de una nueva fila.

### Tests for User Story 2 ⚠️ (primero, deben FALLAR)

- [ ] T023 [P] [US2] Ampliar `friendship/FriendshipRequestServiceTest.java`: `recipientId` nulo/`<= 0` → `FieldValidationException`; auto-solicitud → 400 con el mensaje exacto; destinatario inexistente o no verificado → `FriendshipRecipientNotFoundException`; pendiente en misma dirección y en dirección inversa → `PendingFriendshipRequestExistsException`; amistad `ACCEPTED` → `AlreadyFriendsException`; en todos los rechazos el repositorio **no** inserta — FR-004..FR-008
- [ ] T024 [P] [US2] Ampliar `friendship/FriendshipRequestIT.java`: casos HTTP 400 (auto-solicitud y `recipientId` inválido), 404 (inexistente y no verificado, mismo cuerpo), 409 (pendiente misma dirección, pendiente inversa y amistad), y que la cantidad de filas no cambie en cada rechazo — FR-004..FR-008, contracts §400/§404/§409
- [ ] T025 [P] [US2] Ampliar `frontend/src/pages/UsersSearchPage.test.jsx`: 409 duplicado, 409 amistad, 400 y 404 muestran el mensaje correspondiente y la fila **no** queda como "Solicitud enviada" — FR-017

### Implementation for User Story 2

- [ ] T026 [US2] Completar en `FriendshipRequestService.send` las validaciones en el orden de data-model.md §Reglas de negocio y lanzar las excepciones de T008 con los mensajes exactos — FR-004..FR-008
- [ ] T027 [US2] Ajustar `existsPendingBetween`/`existsAcceptedBetween` para cubrir ambas direcciones en una sola consulta y usarlas antes de insertar — FR-005/FR-006, research.md §D5
- [ ] T028 [US2] En `UsersSearchPage.jsx`, mostrar el `detail` del 409/404 y el error de campo del 400 al enviar, sin marcar la fila como enviada — FR-017, contracts §Contrato del frontend
- [ ] T029 [US2] Verificación de la historia: `cd backend && ./mvnw -B -Dtest=FriendshipRequestServiceTest test && ./mvnw -B -Dit.test=FriendshipRequestIT verify`; `cd frontend && npx vitest run src/pages/UsersSearchPage.test.jsx` en verde

**Checkpoint**: US1 + US2 completas; ninguna relación inválida o duplicada puede crearse.

---

## Phase 5: User Story 3 - Ver las solicitudes pendientes recibidas (Priority: P2)

**Goal**: pantalla "Amigos" con la sección "Solicitudes" que lista las pendientes recibidas, con estado vacío y datos mínimos.

**Independent Test**: enviar solicitudes desde A a B, iniciar como B, abrir "Amigos" y verificar la lista, el orden, la ausencia de solicitudes ajenas y el estado vacío con C.

### Tests for User Story 3 ⚠️ (primero, deben FALLAR)

- [ ] T030 [P] [US3] Ampliar `friendship/FriendshipRequestServiceTest.java`: `listReceivedPending` filtra por destinatario de la sesión y estado `PENDING`, ordena por fecha e id descendente y mapea solo el resumen del emisor — FR-010/FR-011/FR-012
- [ ] T031 [P] [US3] Ampliar `friendship/FriendshipRequestIT.java`: `GET /api/users/me/friendship-requests/received` devuelve solo las pendientes recibidas por la sesión, más recientes primero, sin solicitudes enviadas ni de terceros; arreglo vacío cuando no hay; respuesta sin datos privados — FR-010..FR-013, SC-004
- [ ] T032 [P] [US3] Ampliar `frontend/src/api/friendsClient.test.js` con `listReceivedFriendshipRequests`: URL, método, `credentials`, `{ ok: true, requests }` en 200 y mapeo de errores — contracts §Cliente
- [ ] T033 [P] [US3] Crear `frontend/src/pages/FriendsPage.test.jsx`: carga, lista con nombre/apellido/correo/avatar por defecto/etiqueta "Pendiente", apellido `null` sin "null", orden recibido, vacío exacto, error con reintento y 401 — FR-010..FR-013/FR-019/FR-020
- [ ] T034 [P] [US3] Ampliar `frontend/src/components/SiteNav.test.jsx` y `frontend/src/App.test.jsx`: el enlace "Amigos" apunta a `/friends`, se resalta activo y la ruta es protegida — FR-010

### Implementation for User Story 3

- [ ] T035 [US3] Crear `friendship/ReceivedFriendshipRequestResponse.java` (`id`, `status`, `createdAt`, `sender`) y su mapeo — contracts §200
- [ ] T036 [US3] Agregar `listReceivedPending(recipientId)` a `FriendshipRequestService` usando la consulta de T007 — FR-012
- [ ] T037 [US3] Agregar `GET /api/users/me/friendship-requests/received` a `FriendshipRequestController` — contracts §GET
- [ ] T038 [US3] Agregar `listReceivedFriendshipRequests()` a `frontend/src/api/friendsClient.js` con el mensaje fijo `RECEIVED_NETWORK_ERROR_MESSAGE` — contracts §Cliente
- [ ] T039 [US3] Crear `frontend/src/pages/FriendsPage.jsx` con la sección "Solicitudes" y sus estados; sin acciones "Aceptar"/"Rechazar" ni lista de amigos — FR-010..FR-015/FR-019/FR-020
- [ ] T040 [US3] Registrar `/friends` como ruta protegida en `frontend/src/App.jsx` y agregar el enlace "Amigos" en `frontend/src/components/SiteNav.jsx` — FR-010
- [ ] T041 [US3] Agregar los estilos de la bandeja a `frontend/src/index.scss`, reutilizando las clases y variables existentes — FR-010/FR-011
- [ ] T042 [US3] Verificación de la historia: `cd backend && ./mvnw -B -Dtest=FriendshipRequestServiceTest test && ./mvnw -B -Dit.test=FriendshipRequestIT verify`; `cd frontend && npx vitest run src/api/friendsClient.test.js src/pages/FriendsPage.test.jsx src/components/SiteNav.test.jsx src/App.test.jsx` en verde

**Checkpoint**: el destinatario recibe y puede ver sus solicitudes pendientes.

---

## Phase 6: User Story 4 - Acceso protegido, concurrencia y errores (Priority: P2)

**Goal**: solo cuentas verificadas acceden; dos envíos concurrentes no duplican; la interfaz no muestra éxitos falsos ante red, sesión o conflicto.

**Independent Test**: llamar a los endpoints sin sesión y con cuenta no verificada; disparar dos envíos simultáneos; simular red y 401 en la UI.

### Tests for User Story 4 ⚠️ (primero, deben FALLAR)

- [ ] T043 [P] [US4] Ampliar `friendship/FriendshipRequestIT.java`: `401` sin sesión y con cuenta no verificada en `POST` y `GET`; dos envíos concurrentes de la misma pareja producen una sola fila y uno de los dos responde 409 — FR-009/FR-021, SC-005/SC-006
- [ ] T044 [P] [US4] Ampliar `FriendshipRequestServiceTest` y/o agregar un test de carrera: `DataIntegrityViolationException` al insertar se traduce a `PendingFriendshipRequestExistsException` — FR-009, research.md §D10
- [ ] T045 [P] [US4] Ampliar `UsersSearchPage.test.jsx` y `FriendsPage.test.jsx`: error de red con los mensajes exactos, sin marcar enviado ni conservar datos obsoletos; 401 limpia la sesión; `FriendsPage` permite reintentar — FR-018/FR-019/FR-020

### Implementation for User Story 4

- [ ] T046 [US4] En `FriendshipRequestService.send`, traducir `DataIntegrityViolationException` del índice único pendiente al 409 de duplicado — FR-009, research.md §D10
- [ ] T047 [US4] En `UsersSearchPage.jsx`, manejar 401 (`setUser(null)`), red/5xx con `SEND_NETWORK_ERROR_MESSAGE` y limpiar el estado de carga de la fila — FR-018/FR-020
- [ ] T048 [US4] En `FriendsPage.jsx`, manejar 401 (`setUser(null)`), red/5xx con `RECEIVED_NETWORK_ERROR_MESSAGE`, estado de reintento y ausencia de datos obsoletos — FR-019/FR-020
- [ ] T049 [US4] Verificación de la historia: `cd backend && ./mvnw -B -Dit.test=FriendshipRequestIT verify`; `cd frontend && npx vitest run src/pages/UsersSearchPage.test.jsx src/pages/FriendsPage.test.jsx` en verde

**Checkpoint**: todas las historias funcionan de forma independiente y resisten los casos de error previstos.

---

## Phase 7: Polish & Cross-Cutting Concerns

- [ ] T050 [P] Revisar que `GET /api/users/search` y su DTO no hayan cambiado (regresión de 008) y que `SiteNav`/rutas previas sigan funcionando — spec §Assumptions, plan.md §Complexity Tracking
- [ ] T051 [P] Revisar que ninguna respuesta ni pantalla exponga CBU/CVU, monedas, contraseña, hash, avatar ajeno ni fecha de verificación — FR-011, SC-007
- [ ] T052 Actualizar `quickstart.md` §Resultado de la validación con los resultados reales — constitución II
- [ ] T053 **Validación manual primero (persona usuaria)**: levantar base, backend y frontend; ejecutar los escenarios A–E de `quickstart.md` §2 y registrar observaciones — plan.md §Summary, indicación del equipo
- [ ] T054 **Automatizadas después de la validación visual**: `cd backend && ./mvnw -B verify` y `cd frontend && npm run lint && npm run test:coverage && npm run build`, ambos en verde y con cobertura ≥ 85 % — constitución V/XI
- [ ] T055 Commits con Conventional Commits (por ejemplo `docs:` spec 011, `feat:` envío de solicitudes, `feat:` pantalla Amigos, `test:`) — constitución XIII
- [ ] T056 PR de `feat/send-friend-request` hacia `sprint-2` con la migración V6, el contrato nuevo y la nota de que aceptar/rechazar queda fuera de alcance — constitución XII

---

## Dependencies & Execution Order

- **Setup → Foundational → US1 → US2 → US3 → US4 → Polish.**
- US2 refuerza las validaciones de US1 sobre el mismo endpoint. US3 solo depende de Foundational (puede desarrollarse en paralelo con US1/US2 si se preparan datos por SQL). US4 depende de US1 y US3 para cubrir ambos endpoints y ambas pantallas.
- Dentro de cada historia: tests primero; backend (servicio → DTO → controlador) y frontend (cliente → pantalla) en paralelo una vez fijado el contrato.

### Parallel Opportunities

- T004/T005/T008 en paralelo; T010 en paralelo con T006/T007.
- Los tests de cada historia (marcados [P]) en paralelo entre sí.
- Backend y frontend de cada historia en paralelo una vez fijado el contrato.
- US3 puede avanzar en paralelo con US1/US2 usando filas `PENDING` insertadas por SQL para la bandeja.

## Notes

- Mensajes exactos de la spec y el contrato: no reimprovisar textos.
- El emisor SIEMPRE sale de la sesión; nunca de un id del cuerpo o de la URL.
- La unicidad pendiente/aceptada vive en la base; el servicio solo la traduce a respuestas legibles.
- No se modifica el contrato de 008 ni se agregan campos de amistad a la búsqueda.
- En 011 no se muestran acciones de aceptar/rechazar ni una lista de amigos.
- La validación visual de la persona usuaria (T053) ocurre **antes** de la corrida final de pruebas automatizadas (T054).
- Los commits y el PR los realiza el equipo (T055/T056).
