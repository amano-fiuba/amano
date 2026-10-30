# Implementation Plan: Enviar solicitud de amistad

**Branch**: `feat/send-friend-request` | **Date**: 2026-10-30 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/011-send-friend-request/spec.md`

## Summary

La historia 26 pide enviar una solicitud de amistad a otra cuenta y que el destinatario
reciba una notificación. La búsqueda ya existe (008) y el botón "Agregar" está deshabilitado;
esta feature lo habilita y agrega el dominio mínimo de amistades. **US1**: el botón envía
`POST /api/users/me/friendship-requests` con el `id` del resultado y la solicitud queda
`PENDING`. **US2**: el backend rechaza auto-solicitudes, pendientes en cualquier dirección,
amistades existentes y destinatarios inexistentes/no verificados. **US3**: una pantalla
protegida "Amigos" (`/friends`, en `SiteNav`) muestra la sección "Solicitudes" con las
pendientes recibidas; es la notificación in-app aprobada. **US4**: regla de cuenta
verificada, protección contra envíos concurrentes mediante índice único parcial y manejo de
errores. Una migración nueva (`V6`) crea solo `friendship_requests`; sin dependencias nuevas
ni cambios en `users`. Aceptar/rechazar y la lista de amigos quedan fuera de alcance.

## Technical Context

**Language/Version**: Java 25 (backend); JavaScript con React 19.2 (frontend)

**Primary Dependencies**: Spring Boot 4.1.1 (Web MVC, Data JPA, Flyway, Security); React
19.2, react-router 8.4, Vite 8.3. **Sin dependencias nuevas.**

**Storage**: PostgreSQL + Flyway. `V6__create_friendship_requests.sql` crea una tabla, tres
restricciones, dos índices únicos parciales y un índice de bandeja. Sin `UPDATE` ni `DELETE`.

**Testing**: JUnit 5 + Mockito (`FriendshipRequestServiceTest`, `FriendshipRequestValidationTest`),
Spring Boot Test + Testcontainers (`FriendshipRequestIT`, `FriendshipRequestSchemaIT`),
Vitest + Testing Library (`FriendsPage.test.jsx`, `friendsClient.test.js` y ampliaciones de
`UsersSearchPage.test.jsx`, `SiteNav.test.jsx`, `App.test.jsx`). Cobertura JaCoCo y Vitest
≥ 85 %.

**Target Platform**: Servidor Linux vía Docker Compose; navegador web desktop y móvil.

**Project Type**: Aplicación web monorepo (backend REST + frontend SPA).

**Performance Goals**: la bandeja usa un índice parcial por destinatario y estado; enviar una
solicitud es una escritura puntual con dos verificaciones indexadas. SC-001/SC-008 (< 2 s)
no requieren optimización adicional.

**Constraints**: no modificar el contrato de búsqueda de 008; no crear ni alterar filas de
`users`; sesión verificada; mensajes en voseo; cobertura ≥ 85 %; aceptar/rechazar fuera de
alcance.

**Scale/Scope**: 1 migración, 2 endpoints nuevos, 1 pantalla nueva, 1 pantalla modificada,
~10 archivos backend nuevos, ~4 archivos frontend nuevos y ~8 modificados (ver Project
Structure).

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| # | Principio | Estado | Evidencia |
|---|-----------|--------|-----------|
| I | Arquitectura y separación de responsabilidades | PASS | Dominio nuevo en `friendship/`; reutiliza `UserRepository`, `SecurityConfig`, `ProblemDetailExceptionHandler`, `ProtectedRoute`, `SiteNav`, `TextField`/estilos de 008. Sin refactors ajenos |
| II | Desarrollo guiado por especificaciones | PASS | `specs/011-send-friend-request/` completa; FR citados en contrato, tareas y pruebas |
| III | Entrega incremental por historia | PASS | US1 → US2 → US3 → US4 con checkpoint por historia (tasks.md); US2 refuerza US1, US3 es independiente de la UI de envío |
| IV | Alcance controlado | PASS | Solo envío + bandeja de pendientes. Aceptar/rechazar, lista de amigos, correos, push, grupos y compartir gastos quedan fuera (spec §Assumptions) |
| V | Calidad, pruebas y cobertura | PASS | Unitarios, integración con Testcontainers y Vitest; `mvn -B verify` y `npm run test:coverage` con umbral 85 % |
| VI | Integridad de las reglas de negocio | PASS | Invariantes en servicio + índices únicos parciales por pareja (pendiente/aceptada); no se inventan transiciones de aceptación |
| VII | Contratos REST y compatibilidad | PASS | `contracts/friendship-requests-api.md`; `GET /api/users/search` intacto; endpoints nuevos bajo `/api/users/**` |
| VIII | Validación y manejo de errores | PASS | Validación de `recipientId` en backend; 400/404/409 con ProblemDetail; la restricción de base respalda la carrera |
| IX | Seguridad y control de acceso | PASS | Emisor y destinatario salen de la sesión; `/api/users/**` exige cuenta verificada; no se confía en el cliente |
| X | Código mantenible y convenciones | PASS | Paquete `friendship/`, nombres en inglés, pruebas `*Test`/`*IT` y `*.test.jsx`; lint sin advertencias nuevas |
| XI | Entorno reproducible | PASS | Migración Flyway automática; sin propiedades ni dependencias nuevas |
| XII | Revisión por pares | PENDIENTE (posterior) | PR aún no abierto |
| XIII | Commits y trazabilidad | PENDIENTE (posterior) | Los commits los realiza el equipo |

**Gate resultado**: PASS. US3 es la interpretación aprobada de "notificación" y el alcance
excluye explícitamente aceptar/rechazar (spec §Clarifications, research.md §D3/§D8).

## Project Structure

### Documentation (this feature)

```text
specs/011-send-friend-request/
├── plan.md                                  # Este archivo (/speckit.plan)
├── research.md                              # Fase 0
├── data-model.md                            # Fase 1
├── quickstart.md                            # Fase 1
├── contracts/
│   └── friendship-requests-api.md           # Fase 1
├── checklists/
│   └── requirements.md                      # /speckit.specify
└── tasks.md                                 # Fase 2 (/speckit.tasks)
```

### Source Code (repository root)

```text
backend/src/main/
├── resources/db/migration/V6__create_friendship_requests.sql   # A: tabla, CHECKs, FK, índices
└── java/com/amano/backend/
    ├── common/ProblemDetailExceptionHandler.java               # M: handlers 404 y 409
    └── friendship/
        ├── FriendshipRequestStatus.java                        # A: PENDING/ACCEPTED/REJECTED
        ├── FriendshipRequest.java                              # A: entidad JPA
        ├── FriendshipRequestRepository.java                    # A: consultas de pareja y bandeja
        ├── FriendshipRequestService.java                       # A: envío + listado recibido
        ├── FriendshipRequestController.java                    # A: POST y GET recibidos
        ├── SendFriendshipRequestRequest.java                   # A: { recipientId }
        ├── FriendshipRequestResponse.java                      # A: salida del envío
        ├── ReceivedFriendshipRequestResponse.java              # A: elemento de la bandeja
        ├── FriendshipUserSummary.java                          # A: id, nombre, apellido, email
        ├── AlreadyFriendsException.java                        # A: 409 "Ya son amigos"
        ├── PendingFriendshipRequestExistsException.java        # A: 409 duplicado
        └── FriendshipRecipientNotFoundException.java           # A: 404 neutro

backend/src/test/java/com/amano/backend/friendship/
├── FriendshipRequestServiceTest.java                           # A: unitarios de reglas
├── FriendshipRequestIT.java                                    # A: flujo HTTP completo
└── FriendshipRequestSchemaIT.java                              # A: restricciones de V6

frontend/src/
├── api/friendsClient.js                    # A: sendFriendshipRequest, listReceivedFriendshipRequests
├── pages/FriendsPage.jsx                   # A: /friends con sección "Solicitudes"
├── pages/UsersSearchPage.jsx               # M: habilitar "Agregar" y enviar
├── components/SiteNav.jsx                  # M: acceso "Amigos"
├── App.jsx                                 # M: ruta protegida /friends
├── index.scss                              # M: estilos de la bandeja
├── api/friendsClient.test.js               # A
├── pages/FriendsPage.test.jsx              # A
├── pages/UsersSearchPage.test.jsx          # M: envío, éxito, conflictos, red, 401
├── components/SiteNav.test.jsx             # M: acceso "Amigos"
└── App.test.jsx                            # M: ruta protegida
```

**Structure Decision**: monorepo existente; backend nuevo en el paquete de dominio
`friendship/` y frontend en `pages/`, `api/` y `components/`, siguiendo los patrones de
`expense/` y de la pantalla de búsqueda de 008.

## Complexity Tracking

| Elemento | Detalle | Justificación / acción |
|----------|---------|------------------------|
| Tabla nueva | `friendship_requests` con estados y dos índices únicos parciales | La relación no cabe en `users`; el estado es parte del criterio de aceptación y prepara la historia de aceptación/rechazo |
| Dos índices por pareja | `LEAST/GREATEST` para `PENDING` y `ACCEPTED` | Garantizan el invariante incluso con peticiones concurrentes; el segundo queda listo para la historia futura |
| Estado local en el buscador | Tras recargar, "Agregar" reaparece y el backend responde 409 | Evita modificar el contrato de 008; se documenta como comportamiento esperado (spec §Edge Cases, research.md §D9) |
| Amistad sin tabla propia | Una solicitud `ACCEPTED` representa la amistad | Acordado con el equipo; si la amistad necesita atributos propios, se agrega una migración futura |
| Fuera de alcance | Aceptar/rechazar, lista de amigos, correos, push, grupos y compartir gastos | La historia 26 solo pide enviar y notificar (aprobado en §Clarifications) |
