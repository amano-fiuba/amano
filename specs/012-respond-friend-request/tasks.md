# Tasks: Aceptar y rechazar solicitudes de amistad

**Input**: Design documents from `/specs/012-respond-friend-request/`

**Prerequisites**: [plan.md](./plan.md), [spec.md](./spec.md), [research.md](./research.md), [data-model.md](./data-model.md), [contracts/friendship-requests-api.md](./contracts/friendship-requests-api.md), [quickstart.md](./quickstart.md)

**Tests**: incluidas. La constitución (principio V) exige pruebas automatizadas para toda
funcionalidad nueva, y [research.md](./research.md) §D11 define la estrategia: unitarios con
Mockito, integración con Testcontainers, Vitest en frontend y cobertura ≥ 85 % en ambos
módulos. En cada historia las pruebas se escriben **primero** y deben fallar antes de
implementar.

**Organization**: tareas agrupadas por historia de usuario para permitir implementación,
prueba y revisión independientes.

**Estado**: todas las tareas quedan pendientes `[ ]`.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: puede ejecutarse en paralelo (archivos distintos, sin dependencias pendientes)
- **[Story]**: US1, US2 o US3; sin etiqueta en Setup, Foundational y Polish
- Todas las rutas son reales del repositorio

## Path Conventions

- Backend: `backend/src/main/java/com/amano/backend/…`, pruebas en `backend/src/test/java/…`
- Frontend: `frontend/src/…`, pruebas `*.test.jsx` / `*.test.js` colocadas junto al código
- Migración: **sin migración nueva**; se reutiliza el esquema de `V12__create_friendship_requests.sql`

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: confirmar la rama, la línea base y la ausencia de cambios de esquema.

- [ ] T001 Confirmar que la rama activa es `feat/respond-friend-request` y que no se requieren dependencias nuevas ni migraciones: `backend/pom.xml`, `frontend/package.json` y `db/migration` sin cambios — plan.md §Technical Context
- [ ] T002 Ejecutar la línea base antes de tocar código: `cd backend && ./mvnw -B verify` y `cd frontend && npm ci && npm run lint && npm run test:coverage && npm run build`, ambos en verde — quickstart.md §1

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: piezas compartidas por las dos acciones (aceptar y rechazar). **Ninguna historia puede empezar hasta completar esta fase.**

- [ ] T003 [P] Crear `friendship/FriendshipRequestAction.java` con exactamente `ACCEPT` y `REJECT` — data-model.md §FriendshipRequestAction
- [ ] T004 [P] Crear las excepciones `friendship/FriendshipRequestNotFoundException.java` (404, mensaje neutro "No se encontró la solicitud de amistad") y `friendship/FriendshipRequestAlreadyRespondedException.java` (409, "Esta solicitud de amistad ya fue respondida") — FR-007/FR-008/FR-009
- [ ] T005 Agregar a `common/ProblemDetailExceptionHandler.java` los handlers de las excepciones de T004 con los mensajes exactos; 404 no revela existencia, pertenencia ni estado — FR-008/FR-009/FR-020, contracts §404/§409
- [ ] T006 Agregar a `friendship/FriendshipRequestRepository.java` la consulta `@Modifying @Query respondPending(requestId, recipientId, newStatus, respondedAt)` con la condición `id + recipient_id + status = 'PENDING'`, devolviendo la cantidad de filas afectadas — FR-011, data-model.md §Reglas de negocio, research.md §D5
- [ ] T007 Verificar que `/api/users/**` sigue exigiendo sesión verificada en `config/SecurityConfig.java`; no se modifica la configuración — FR-018/FR-019

**Checkpoint**: las transiciones illegales son imposibles en la base; el servicio puede escribir estados finales con coherencia garantizada; las historias pueden comenzar.

---

## Phase 3: User Story 1 - Aceptar una solicitud de amistad recibida (Priority: P1) 🎯 MVP

**Goal**: "Aceptar" en la fila de la bandeja registra la amistad (`ACCEPTED` + `responded_at`), retira la fila y confirma exactamente.

**Independent Test**: enviar una solicitud de A a B, iniciar como B, pulsar "Aceptar" y verificar la confirmación "Solicitud de amistad aceptada", la fila retirada y una fila `ACCEPTED` con fecha de respuesta.

### Tests for User Story 1 ⚠️ (primero, deben FALLAR)

- [ ] T008 [P] [US1] Ampliar `friendship/FriendshipRequestServiceTest.java` (Mockito): `respond` con `action = ACCEPT` sobre una `PENDING` de la sesión ejecuta exactamente un `UPDATE` condicional hacia el estado `ACCEPTED` con `now()`, y devuelve `status: ACCEPTED`, `respondedAt` y el resumen del emisor — FR-002, data-model.md §DTOs
- [ ] T009 [P] [US1] Ampliar `friendship/FriendshipRequestIT.java` (MockMvc + Testcontainers): `PUT /api/users/me/friendship-requests/{id}` con `{ "action": "ACCEPT" }` y la destinataria de la sesión responde `200` con el cuerpo del contrato; la fila queda `ACCEPTED` con `responded_at`; no aparece en `GET .../received`; sin datos privados en el cuerpo — FR-002/FR-003, contracts §200
- [ ] T010 [P] [US1] Ampliar `frontend/src/api/friendsClient.test.js` con `respondFriendshipRequest`: método `PUT`, URL con el id, `credentials: 'include'`, cuerpo `{ action }` y `{ ok: true, request }` en 200 — contracts §Contrato del frontend
- [ ] T011 [P] [US1] Ampliar `frontend/src/pages/FriendsPage.test.jsx`: la fila muestra los botones "Aceptar" y "Rechazar"; al pulsar "Aceptar" los dos se deshabilitan con "Aceptando…"; con éxito la fila se retira, aparece exactamente "Solicitud de amistad aceptada" y las demás filas no cambian — FR-001/FR-003/FR-012/FR-013

### Implementation for User Story 1

- [ ] T012 [US1] Crear `friendship/RespondFriendshipRequestRequest.java` con validación estricta de `action` (solo `ACCEPT`/`REJECT`; cualquier otro valor lanza `FieldValidationException` con `properties.fields.action` "La acción no es válida") — FR-010, contracts §400
- [ ] T013 [US1] Crear `friendship/AnsweredFriendshipRequestResponse.java` (`id`, `status`, `createdAt`, `respondedAt`, `sender`) y su mapeo desde la fila respondida — contracts §200
- [ ] T014 [US1] Agregar `respond(responderId, requestId, action)` a `friendship/FriendshipRequestService.java` `@Transactional`: valida `requestId > 0` (error `properties.fields.id` "La solicitud no es válida"), ejecuta el `UPDATE` condicional de T006 y, si no afecta filas, relee la fila para distinguir 404 de 409 (implementación completa de reglas se refuerza en US3) — FR-002/FR-007/FR-008/FR-010, data-model.md §Reglas de negocio
- [ ] T015 [US1] Agregar `PUT /api/users/me/friendship-requests/{id}` a `friendship/FriendshipRequestController.java`: id desde la ruta, acción desde el cuerpo, respondedor con el patrón `currentUserId()` y delegación en el servicio — FR-007/FR-019, contracts §PUT
- [ ] T016 [US1] Agregar `respondFriendshipRequest(id, action)` a `frontend/src/api/friendsClient.js` con la convención existente y el mensaje fijo `RESPOND_NETWORK_ERROR_MESSAGE` — contracts §Cliente
- [ ] T017 [US1] En `frontend/src/pages/FriendsPage.jsx`: botones por fila con estado de carga específico, retiro de la fila al confirmar y confirmación exacta de éxito; estilos reutilizando clases y variables de `index.scss` — FR-001/FR-012/FR-013, research.md §D8
- [ ] T018 [US1] Verificación de la historia: `cd backend && ./mvnw -B -Dtest=FriendshipRequestServiceTest test && ./mvnw -B -Dit.test=FriendshipRequestIT verify`; `cd frontend && npx vitest run src/api/friendsClient.test.js src/pages/FriendsPage.test.jsx` en verde

**Checkpoint**: US1 probable de forma independiente (MVP): la amistad ya se puede registrar.

---

## Phase 4: User Story 2 - Rechazar una solicitud de amistad recibida (Priority: P1)

**Goal**: "Rechazar" registra `REJECTED` + `responded_at` sin crear amistad, retira la fila y confirma exactamente; los reenvíos siguen siendo posibles.

**Independent Test**: enviar una solicitud de A a B, iniciar como B, pulsar "Rechazar" y verificar la confirmación "Solicitud de amistad rechazada", la fila retirada, la fila `REJECTED` con fecha de respuesta, la ausencia de `ACCEPTED` y que A puede volver a enviar a B.

### Tests for User Story 2 ⚠️ (primero, deben FALLAR)

- [ ] T019 [P] [US2] Ampliar `friendship/FriendshipRequestServiceTest.java`: `respond` con `action = REJECT` produce exactamente un `UPDATE` condicional a `REJECTED` con `now()`; la respuesta refleja `status: REJECTED` y `respondedAt`; la transición no inserta filas ni crea amistades — FR-005
- [ ] T020 [P] [US2] Ampliar `friendship/FriendshipRequestIT.java`: `PUT` con `{ "action": "REJECT" }` responde `200` con `status: "REJECTED"`; la fila queda `REJECTED` con `responded_at`; no existe ninguna fila `ACCEPTED` entre la pareja; tras el rechazo, un `POST` de 011 entre las mismas personas se acepta (regla sin cambios) — FR-005/FR-006, contracts §200
- [ ] T021 [P] [US2] Ampliar `frontend/src/pages/FriendsPage.test.jsx`: pulsar "Rechazar" muestra "Rechazando…", retira la fila y muestra exactamente "Solicitud de amistad rechazada" — FR-004/FR-013

### Implementation for User Story 2

- [ ] T022 [US2] En `FriendshipRequestService.respond`, mapear la acción `REJECT` al estado final `REJECTED` y contrastar con la rama de `ACCEPT` (código de transición compartido, un solo `UPDATE`) — FR-005/FR-011
- [ ] T023 [US2] En `FriendsPage.jsx`, conectar el botón "Rechazar" al mismo flujo de T017 con su confirmación exacta, sin duplicar lógica de carga/retiro — FR-004/FR-013
- [ ] T024 [US2] Verificación de la historia: `cd backend && ./mvnw -B -Dtest=FriendshipRequestServiceTest test && ./mvnw -B -Dit.test=FriendshipRequestIT verify`; `cd frontend && npx vitest run src/pages/FriendsPage.test.jsx` en verde

**Checkpoint**: US1 + US2 completas; ambas respuestas están disponibles y son independientes entre filas.

---

## Phase 5: User Story 3 - Integridad de la respuesta: acceso, concurrencia y errores (Priority: P2)

**Goal**: solo la destinataria responde solicitudes pendientes; los intentos inválidos se rechazan sin tocar la base; la doble respuesta concurrente produce una única transición; la interfaz no muestra éxitos falsos.

**Independent Test**: responder con la cuenta equivocada, sobre un id inexistente, no numérico o ya respondido; con acción inválida; disparar dos respuestas simultáneas; simular caída de red y sesión expirada en la UI.

### Tests for User Story 3 ⚠️ (primero, deben FALLAR)

- [ ] T025 [P] [US3] Ampliar `friendship/FriendshipRequestServiceTest.java`: `requestId <= 0` → error de campo sobre `id`; `action` nulo/desconocido → error de campo sobre `action` sin ejecutar el `UPDATE` ni `findById`; update sin filas + fila inexistente o ajena → `FriendshipRequestNotFoundException`; update sin filas + fila final → `FriendshipRequestAlreadyRespondedException`; luego de 404/409 no se escribe nada — FR-008/FR-009/FR-010, research.md §D5/§D6
- [ ] T026 [P] [US3] Ampliar `friendship/FriendshipRequestIT.java`: `404` (id inexistente, solicitud propia del emisor y fila borrada por cascada) con el mismo cuerpo neutro; `409` sobre `ACCEPTED` y sobre `REJECTED` sin cambiar estado ni `responded_at`; `400` por acción inválida y por id no positivo, verificando que la cantidad de filas no cambie en cada rechazo — FR-008/FR-009/FR-010, contracts §400/§404/§409
- [ ] T027 [P] [US3] Ampliar `friendship/FriendshipRequestIT.java` con la prueba de carrera: dos respuestas simultáneas (mismas o distintas acciones) sobre la misma `PENDING` producen exactamente un 200 y un 409, con un único estado final y una única `responded_at` — FR-011, SC-005
- [ ] T028 [P] [US3] Ampliar `friendship/FriendshipRequestIT.java`: `401` sin sesión y con cuenta no verificada en el `PUT`; el `PUT` de la sesión equivocada no altera la fila — FR-007/FR-018, SC-006
- [ ] T029 [P] [US3] Ampliar `frontend/src/pages/FriendsPage.test.jsx`: 409 y 404 retiran la fila con el `detail` del servidor; error de red muestra exactamente "No se pudo responder la solicitud. Intentá nuevamente" dejando la fila reactiva y sin confirmación; la confirmación de éxito permanece visible con la lista vacía; el 401 limpia la sesión — FR-013..FR-017

### Implementation for User Story 3

- [ ] T030 [US3] En `FriendshipRequestService.respond`, ordenar las validaciones según data-model.md (id → action → base) y completar la relectura post-`UPDATE` que distingue 404 neutro de 409 ya respondida — FR-007..FR-010, data-model.md §Reglas de negocio
- [ ] T031 [US3] En `FriendsPage.jsx`, manejar 409/404 (retiro de fila + mensaje del servidor), error de red/5xx con `RESPOND_NETWORK_ERROR_MESSAGE` y fila reactiva, confirmación persistente con lista vacía y 401 con `setUser(null)` — FR-015..FR-017, contracts §Contrato del frontend
- [ ] T032 [US3] Verificación de la historia: `cd backend && ./mvnw -B -Dit.test=FriendshipRequestIT verify`; `cd frontend && npx vitest run src/pages/FriendsPage.test.jsx src/api/friendsClient.test.js` en verde

**Checkpoint**: todas las historias funcionan de forma independiente y resisten los casos de error previstos.

---

## Phase 6: Polish & Cross-Cutting Concerns

- [ ] T033 [P] Regresión de 011/008: `POST /api/users/me/friendship-requests`, `GET .../received` y `GET /api/users/search` responden exactamente igual que antes; el buscador y la navegación no cambian — spec §Assumptions, plan.md §Constraints
- [ ] T034 [P] Revisar que ninguna respuesta del `PUT` exponga CBU/CVU, monedas, contraseña, hash, avatar ajeno ni fecha de verificación, y que el `detail` de 404/409 no filtre el estado previo — FR-020, SC-007
- [ ] T035 Actualizar `quickstart.md` §Resultado de la validación con los resultados reales — constitución II
- [ ] T036 **Validación manual primero (persona usuaria)**: levantar base, backend y frontend; ejecutar los escenarios A–E de `quickstart.md` §2 y registrar observaciones — plan.md §Summary, indicación del equipo
- [ ] T037 **Automatizadas después de la validación visual**: `cd backend && ./mvnw -B verify` y `cd frontend && npm run lint && npm run test:coverage && npm run build`, ambos en verde y con cobertura ≥ 85 % — constitución V/XI
- [ ] T038 Commits con Conventional Commits (por ejemplo `docs:` spec 012, `feat:` respuesta de solicitudes, `test:`) — constitución XIII
- [ ] T039 PR de `feat/respond-friend-request` hacia `sprint-2` con la nota de sin migración y de que la lista de amigos queda para la historia posterior — constitución XII

---

## Dependencies & Execution Order

- **Setup → Foundational → US1 → US2 → US3 → Polish.**
- US2 reutiliza el endpoint y el flujo de US1 con la rama de `REJECT`; US3 refuerza reglas sobre el mismo código. US1 es la base indispensable de las demás.
- Dentro de cada historia: tests primero; backend (DTO → servicio → controlador) y frontend (cliente → pantalla) en paralelo una vez fijado el contrato.

### Parallel Opportunities

- T003/T004 en paralelo; T006 puede avanzar junto a las excepciones de T004.
- Los tests de cada historia (marcados [P]) en paralelo entre sí.
- Backend y frontend de cada historia en paralelo una vez fijado el contrato.

## Notes

- Mensajes exactos de la spec y el contrato: no reimprovisar textos ("La acción no es válida", "La solicitud no es válida", "No se encontró la solicitud de amistad", "Esta solicitud de amistad ya fue respondida", "Solicitud de amistad aceptada", "Solicitud de amistad rechazada", "No se pudo responder la solicitud. Intentá nuevamente").
- El respondedor SIEMPRE sale de la sesión; el id de la solicitud, de la ruta; la acción, del cuerpo.
- La única escritura válida es el `UPDATE` condicional de T006; jamás se realiza un flujo leer-modificar-escribir sobre la entidad.
- Sin migración nueva; si algo "necesitara" una, es señal de desvío del alcance.
- La validación visual de la persona usuaria (T036) ocurre **antes** de la corrida final de pruebas automatizadas (T037).
- Los commits y el PR los realiza el equipo (T038/T039).
