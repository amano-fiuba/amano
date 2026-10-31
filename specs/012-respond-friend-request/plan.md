# Implementation Plan: Aceptar y rechazar solicitudes de amistad

**Branch**: `feat/respond-friend-request` | **Date**: 2026-10-30 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/012-respond-friend-request/spec.md`

## Summary

Las historias 27 y 29 piden que la destinataria pueda aceptar o rechazar las solicitudes pendientes recibidas. 011 dejó el dominio preparado (estados, `responded_at`, índices únicos de pareja) y la pantalla "Amigos" sin acciones. **US1**: la acción "Aceptar" ejecuta `PUT /api/users/me/friendship-requests/{id}` con `{ "action": "ACCEPT" }`; la fila pasa `PENDING → ACCEPTED` con `responded_at`, se retira de la bandeja y se confirma. **US2**: igual con "Rechazar" hacia `REJECTED`, sin crear amistad y sin bloquear reenvíos. **US3**: solo la destinataria responde (404 neutro en casos inexistente/ajena), 400 por acción o id inválido, 409 por ya respondida, una única transición ante respuestas concurrentes mediante `UPDATE` condicional, 401 heredado y manejo de errores en la interfaz. **Sin migración nueva** ni dependencias nuevas. La lista de amigos visualizada queda para la historia posterior; el emisor no recibe notificaciones.

## Technical Context

**Language/Version**: Java 25 (backend); JavaScript con React 19.2 (frontend)

**Primary Dependencies**: Spring Boot 4.1.1 (Web MVC, Data JPA, Flyway, Security); React 19.2, react-router 8.4, Vite 8.3. **Sin dependencias nuevas.**

**Storage**: PostgreSQL + Flyway. **Cero migraciones**: la historia opera sobre `friendship_requests` (`V6`, creada en 011). Única escritura del sistema en esta feature: la transición `PENDING → ACCEPTED | REJECTED` con `responded_at = now()` mediante `UPDATE` condicional; `ck_friendship_requests_responded` garantiza la coherencia estado/fecha.

**Testing**: JUnit 5 + Mockito (ampliación de `FriendshipRequestServiceTest`), Spring Boot Test + Testcontainers (ampliación de `FriendshipRequestIT`), Vitest + Testing Library (ampliaciones de `FriendsPage.test.jsx`, `friendsClient.test.js` y regresión de `UsersSearchPage.test.jsx`). Cobertura JaCoCo y Vitest ≥ 85 %.

**Target Platform**: Servidor Linux vía Docker Compose; navegador web desktop y móvil.

**Project Type**: Aplicación web monorepo (backend REST + frontend SPA).

**Performance Goals**: la respuesta es una única escritura condicionada por `id + recipient_id + status = 'PENDING'` con los índices de 011; no se agregan consultas por fila ni listados. SC-001 (< 2 s) no requiere optimización adicional.

**Constraints**: no tocar envío, bandeja ni búsqueda de 011/008; sin migraciones; sesión verificada; 404 neutro sin enumeración de solicitudes ajenas; mensajes en voseo; cobertura ≥ 85 %; lista de amigos y notificaciones al emisor fuera de alcance.

**Scale/Scope**: 0 migraciones, 1 endpoint nuevo, 3 archivos backend nuevos, ~4 modificados, 2 archivos frontend nuevos y ~3 modificados (ver Project Structure).

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| # | Principio | Estado | Evidencia |
|---|-----------|--------|-----------|
| I | Arquitectura y separación de responsabilidades | PASS | Extiende el dominio `friendship/` de 011 (servicio, repositorio, excepciones, DTOs); reutiliza `ProblemDetailExceptionHandler`, `SecurityConfig`, `FriendsPage`/`friendsClient`. Sin refactors ajenos |
| II | Desarrollo guiado por especificaciones | PASS | `specs/012-respond-friend-request/` completa; FR citados en contrato, tareas y pruebas |
| III | Entrega incremental por historia | PASS | US1 → US2 → US3 con checkpoint por historia (tasks.md); la base del `PUT` es común y las US quedan probables de forma independiente |
| IV | Alcance controlado | PASS | Solo responder. Lista de amigos, notificaciones al emisor, correos, push y cambios en envío/búsqueda quedan fuera (spec §Clarifications y §Assumptions) |
| V | Calidad, pruebas y cobertura | PASS | Tests primero por historia; integración con Testcontainers; `mvn -B verify` y `npm run test:coverage` con umbral 85 % |
| VI | Integridad de las reglas de negocio | PASS | Transición única atómica con `UPDATE` condicional + restricción `ck_friendship_requests_responded` + índice único de aceptadas; los estados finales son fijos |
| VII | Contratos REST y compatibilidad | PASS | `contracts/friendship-requests-api.md` extiende el contrato de 011 declarando explícitamente la invarianza de `POST`/`GET`; endpoint nuevo bajo `/api/users/**` |
| VIII | Validación y manejo de errores | PASS | Validación de `id` y `action` en backend; 400/401/404/409 con ProblemDetail; la base respalda la carrera |
| IX | Seguridad y control de acceso | PASS | Respondedor y destinataria verificados por sesión + condición del `UPDATE`; 404 neutro evita enumerar solicitudes ajenas |
| X | Código mantenible y convenciones | PASS | Nombres en inglés, paquete `friendship/` existente, pruebas `*Test`/`*IT` y `*.test.jsx`; lint sin advertencias nuevas |
| XI | Entorno reproducible | PASS | Sin propiedades ni dependencias nuevas; Flyway no cambia |
| XII | Revisión por pares | PENDIENTE (posterior) | PR aún no abierto |
| XIII | Commits y trazabilidad | PENDIENTE (posterior) | Los commits los realiza el equipo |

**Gate resultado**: PASS. El alcance acordado (las Clarifications de la spec) excluye la visualización de la lista de amigos (historia posterior) y las notificaciones al emisor.

## Project Structure

### Documentation (this feature)

```text
specs/012-respond-friend-request/
├── plan.md                                  # Este archivo
├── research.md                              # Fase 0
├── data-model.md                            # Fase 1
├── quickstart.md                            # Fase 1
├── contracts/
│   └── friendship-requests-api.md           # Fase 1 (extiende el de 011)
├── checklists/
│   └── requirements.md                      # Checklist de la spec
└── tasks.md                                 # Fase 2
```

### Source Code (repository root)

```text
backend/src/main/resources/db/migration/     # Sin cambios: no hay migración nueva

backend/src/main/java/com/amano/backend/
├── common/ProblemDetailExceptionHandler.java  # M: handlers 404 (FriendshipRequestNotFoundException) y 409 (FriendshipRequestAlreadyRespondedException)
└── friendship/
    ├── FriendshipRequestAction.java           # A: ACCEPT / REJECT
    ├── RespondFriendshipRequestRequest.java   # A: { action }
    ├── AnsweredFriendshipRequestResponse.java # A: salida del PUT
    ├── FriendshipRequestService.java          # M: respond(responderId, requestId, action)
    ├── FriendshipRequestRepository.java       # M: @Modifying respondPending(...) + relectura para distinguir 404/409
    ├── FriendshipRequestController.java       # M: PUT /api/users/me/friendship-requests/{id}
    ├── FriendshipRequestNotFoundException.java     # A: 404 neutro
    └── FriendshipRequestAlreadyRespondedException.java # A: 409 ya respondida

backend/src/test/java/com/amano/backend/friendship/
├── FriendshipRequestServiceTest.java          # M: reglas de la respuesta
└── FriendshipRequestIT.java                   # M: flujo HTTP, concurrencia y regresión de 011

frontend/src/
├── api/friendsClient.js                       # M: respondFriendshipRequest(id, action) + RESPOND_NETWORK_ERROR_MESSAGE
├── pages/FriendsPage.jsx                      # M: botones "Aceptar"/"Rechazar" con estados por fila
├── api/friendsClient.test.js                  # M: casos del respond
└── pages/FriendsPage.test.jsx                 # M: acciones, retiro de fila, confirmaciones, 409/404/red/401
```

**Structure Decision**: monorepo existente; solo se extiende el paquete `friendship/` y la pantalla "Amigos" de 011, sin tocar el buscador, `App.jsx`, `SiteNav` ni migraciones.

## Complexity Tracking

| Elemento | Detalle | Justificación / acción |
|----------|---------|------------------------|
| `PUT` no idempotente en el resultado observado | Repetir la llamada devuelve 409 | Comportamiento correcto según FR-008; documentado en el contrato para evitar sorpresas de integración |
| `UPDATE` condicional en el repositorio | `WHERE id AND recipient_id AND status = 'PENDING'`, con relectura para distinguir 404/409 | Es la única forma de garantizar una única transición sin bloqueos explícitos (research D5) |
| Sin migración | La base ya modela el ciclo de vida completo | 011 preparó `responded_at`, CHECK y índices a propósito (research D3); discrepancia de alcance por historia, no por diseño |
| Lista de amigos sin endpoints | El criterio "ambos deben quedar registrados como amigos" acaba en la fila `ACCEPTED` | La visualización fue apartada a una spec posterior por decisión del equipo (spec §Clarifications) |
| Fila retirada localmente sin recargar la bandeja | Retiro con 200/409/404 y confirmación persistente | La bandeja solo lista pendientes; recargarla borraría la confirmación exigida (research D8) |
