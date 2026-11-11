# Implementation Plan: Eliminar amigo

**Branch**: `feat/delete-friend` | **Date**: 2026-11-05 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/016-delete-friend/spec.md`

## Summary

La historia "Eliminar amigo" agrega la baja de una amistad desde la sección "Mis amigos" de 013. Como una amistad es una solicitud `ACCEPTED` de `friendship_requests` (011/012), **US1** elimina físicamente esa fila y ambas personas dejan de figurar como amigas. **US2** agrega un pre-chequeo de deuda directa a través del puerto `DirectDebtPort` (con stub sin deuda, igual que `DebtPort` de 010) que bloquea la operación con `403` hasta saldar la deuda; las deudas de grupo no bloquean. **US3** completa el acceso verificado, la validación de entrada, el `404` neutro, la concurrencia y los errores de interfaz. **Sin migración**, sin cambios en grupos, gastos ni en el contrato de lista de 013.

## Technical Context

**Language/Version**: Java 25 (backend); JavaScript con React 19.2 (frontend)

**Primary Dependencies**: Spring Boot 4.1.1 (Web MVC, Data JPA, Security); React 19.2, react-router 8.4, Vite 8.3. **Sin dependencias nuevas.**

**Storage**: PostgreSQL. **Cero migraciones**; se borra una fila de `friendship_requests` (`V12`) y se leen `users`/amistades. No se tocan `expenses`, `group_members`, `group_activity` ni deudas futuras.

**Testing**: JUnit 5 + Mockito (`FriendServiceTest`, `DirectDebtPortTest` si aplica), Spring Boot Test + Testcontainers (`DeleteFriendIT`), Vitest + Testing Library (`FriendsList.test.jsx`, `DeleteFriendDialog.test.jsx`, ampliaciones de `friendsClient.test.js`). Cobertura JaCoCo y Vitest ≥ 85 %.

**Target Platform**: Servidor Linux vía Docker Compose; navegador web desktop y móvil.

**Project Type**: Aplicación web monorepo (backend REST + frontend SPA).

**Performance Goals**: consulta de elegibilidad y `DELETE` puntuales; volumen bajo por persona. SC-001 (< 2 s) sin optimización adicional.

**Constraints**: no cambiar el contrato de `GET /api/users/me/friends` (013); no tocar 008/010/011/012; sin migraciones; sesión verificada; sin datos privados; voseo; cobertura ≥ 85 %.

**Scale/Scope**: 0 migraciones; 2 endpoints backend (elegibilidad y eliminación) en el controlador de amigos; 1 puerto y 1 stub nuevos; 1 excepción de dominio nueva; 1 handler de errores modificado; 1 componente de diálogo nuevo y 2 archivos frontend modificados.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| # | Principio | Estado | Evidencia |
|---|-----------|--------|-----------|
| I | Arquitectura y separación de responsabilidades | PASS | Se extiende `friendship/` (`FriendController`/`FriendService`) y se reutiliza el patrón de puertos de 010; la UI sigue el patrón de `RemoveMemberDialog` de grupos |
| II | Desarrollo guiado por especificaciones | PASS | `specs/016-delete-friend/` completa; FR citados en contrato, tareas y pruebas |
| III | Entrega incremental por historia | PASS | US1 → US2 → US3 con checkpoints en tasks.md |
| IV | Alcance controlado | PASS | Solo eliminar amistad; sin historial de amistad, sin notificaciones, sin borrar gastos ni grupos, sin tocar 010 |
| V | Calidad, pruebas y cobertura | PASS | Unitarios, integración con Testcontainers y Vitest; umbral 85 % |
| VI | Integridad de las reglas de negocio | PASS | Borrado de la única fila `ACCEPTED` por pareja; deuda directa bloquea; deudas/grupos intactos |
| VII | Contratos REST y compatibilidad | PASS | `contracts/delete-friend-api.md`; endpoints aditivos, contrato de 013 sin cambios |
| VIII | Validación y manejo de errores | PASS | Id validado antes de consultar; `400/401/403/404` con RFC 7807; mensajes de UI fijos |
| IX | Seguridad y control de acceso | PASS | Sesión verificada bajo `/api/users/**`; el amigo sale de la ruta y nunca de un id emisor del cliente |
| X | Código mantenible y convenciones | PASS | Nombres en inglés, `*Test`/`*IT`, `*.test.jsx`, reutilización de componentes y estilos |
| XI | Entorno reproducible | PASS | Sin propiedades, dependencias ni migraciones nuevas |
| XII | Revisión por pares | PENDIENTE (posterior) | PR aún no abierto |
| XIII | Commits y trazabilidad | PENDIENTE (posterior) | Los commits los realiza el equipo |

**Gate resultado**: PASS.

## Project Structure

### Documentation (this feature)

```text
specs/016-delete-friend/
├── spec.md
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/delete-friend-api.md
├── checklists/requirements.md
└── tasks.md
```

### Source Code (repository root)

```text
backend/src/main/java/com/amano/backend/friendship/
├── FriendController.java             # M: GET elegibilidad + DELETE
├── FriendService.java                # M: elegibilidad y eliminación
├── FriendshipRequestRepository.java  # M: borrado condicional de la fila ACCEPTED
├── DirectDebtPort.java               # A: puerto de deuda directa (futuro módulo)
├── NoOpDirectDebtPort.java           # A: stub sin deuda (mientras no exista el módulo)
├── PendingDirectDebtException.java   # A: bloqueo 403 por deuda directa
└── FriendNotFoundException.java      # A: 404 neutro "No sos amigo de ese usuario"

backend/src/main/java/com/amano/backend/common/
└── ProblemDetailExceptionHandler.java # M: mapea las nuevas excepciones a 403/404

backend/src/test/java/com/amano/backend/friendship/
├── FriendServiceTest.java            # M/A: elegibilidad y eliminación
└── DeleteFriendIT.java               # A: contrato HTTP con Testcontainers

frontend/src/
├── api/friendsClient.js              # M: checkFriendDeletion + deleteFriend
├── api/friendsClient.test.js         # M
├── components/FriendsList.jsx        # M: acción "Eliminar amigo" y estados
├── components/FriendsList.test.jsx   # M
├── components/DeleteFriendDialog.jsx # A: diálogo de confirmación reutilizable
├── components/DeleteFriendDialog.test.jsx # A
└── index.scss                        # M: estilos del diálogo y la acción
```

**Structure Decision**: aplicación web (backend + frontend) existente; no se crean módulos ni paquetes nuevos. El puerto de deuda vive en `friendship/` porque es una necesidad de esta feature y todavía no existe un paquete de deudas.

## Phase 0 — Research (resumen)

Las decisiones están en [research.md](./research.md). No quedaron `NEEDS CLARIFICATION`.

- **D1**: borrado físico de la fila `ACCEPTED` (sin migración, sin historial de amistad).
- **D2**: `DirectDebtPort` + `NoOpDirectDebtPort`, análogo a `DebtPort`/`NoOpDebtPort` de 010.
- **D3**: pre-chequeo con `GET .../deletion-eligibility` y validación final en `DELETE`.
- **D4**: `403` con `ProblemDetail` y código `PENDING_DIRECT_DEBT`; `404` neutro para no-amigo.
- **D5**: diálogo modal reutilizando el patrón de `RemoveMemberDialog`.
- **D6**: pruebas unitarias, de integración y de UI; cobertura ≥ 85 %.

## Phase 1 — Design

### Backend

1. **`DirectDebtPort`** (nuevo): `boolean hasPendingDirectDebt(Long userId, Long friendId)`.
2. **`NoOpDirectDebtPort`** (nuevo, `@Component`): siempre `false`; documenta que el módulo real lo reemplazará.
3. **`FriendNotFoundException`** (nuevo): mensaje `"No sos amigo de ese usuario"`.
4. **`PendingDirectDebtException`** (nuevo): mensaje exacto de deuda directa; `ProblemDetailExceptionHandler` lo mapea a `403` con `code = PENDING_DIRECT_DEBT`.
5. **`FriendshipRequestRepository`**:
   - Reutiliza `existsBetween(a, b, ACCEPTED)` para la elegibilidad.
   - Agrega `deleteAcceptedBetween(a, b)` como `@Modifying` con `delete ... where status = ACCEPTED and ((sender/recipient) or (recipient/sender))`; devuelve la cantidad de filas.
6. **`FriendService`**:
   - `checkDeletionEligibility(userId, friendIdRaw)`: valida id, verifica amistad y consulta `DirectDebtPort`; devuelve `{ canDelete, reason }`.
   - `deleteFriend(userId, friendIdRaw)`: valida id, verifica deuda directa (403), ejecuta el borrado condicional y lanza `FriendNotFoundException` si afectó 0 filas (404).
   - `@Transactional` en la eliminación; `@Transactional(readOnly = true)` en la elegibilidad.
7. **`FriendController`**:
   - `GET /api/users/me/friends/{friendId}/deletion-eligibility` → `200` con `DeletionEligibilityResponse`.
   - `DELETE /api/users/me/friends/{friendId}` → `204`.
   - `friendId` como `String` en la ruta para responder `400` antes de consultar, siguiendo el patrón de 012.

### Frontend

1. **`friendsClient.js`**:
   - `checkFriendDeletion(friendId)`: `GET .../deletion-eligibility`; 2xx → `{ ok: true, canDelete, reason }`; 4xx → `{ ok: false, status, detail }`; 5xx/red → `{ ok: false, network: true }`.
   - `deleteFriend(friendId)`: `DELETE ...`; 204 → `{ ok: true }`; 4xx/5xx/red con la misma convención que el resto del cliente.
   - Constantes: `DELETE_FRIEND_NETWORK_ERROR_MESSAGE = 'No se pudo eliminar al amigo. Intentá nuevamente'`.
2. **`DeleteFriendDialog.jsx`**:
   - Props: `friend`, `isDeleting`, `error`, `onCancel`, `onConfirm`.
   - Rol `dialog`, título/descripción accesibles, foco inicial en "Cancelar", cierre con Escape solo si no está eliminando.
   - No conoce la API: la lógica vive en `FriendsList`.
3. **`FriendsList.jsx`**:
   - Botón "Eliminar amigo" en cada tarjeta.
   - Al pulsar: llama a `checkFriendDeletion`; si `canDelete === false`, muestra el aviso bloqueante; si es `true`, abre el diálogo.
   - Confirmar: llama a `deleteFriend`; en éxito retira la tarjeta y muestra "Amigo eliminado correctamente"; en `403` muestra el motivo sin retirar; en `404` retira y muestra el mensaje neutro; en red mantiene la tarjeta y muestra el error; en `401` cierra sesión.
   - Un `notice` local conserva la confirmación hasta la próxima acción o navegación.
4. **`index.scss`**: estilos del diálogo y del botón, reutilizando variables y clases existentes.

### Contratos

Ver [contracts/delete-friend-api.md](./contracts/delete-friend-api.md).

- `GET /api/users/me/friends/{friendId}/deletion-eligibility`
  - `200 { "canDelete": true }`
  - `200 { "canDelete": false, "reason": "..." }`
  - `400`, `401`, `404`
- `DELETE /api/users/me/friends/{friendId}`
  - `204` sin cuerpo
  - `400`, `401`, `403`, `404`

### Datos

Ver [data-model.md](./data-model.md). **Sin migración**: se borra una fila existente y se lee el modelo actual.

## Phase 2 — Tasks

El detalle está en [tasks.md](./tasks.md). Orden: Setup → Foundational → US1 → US2 → US3 → Polish.

## Riesgos y mitigaciones

| Riesgo | Mitigación |
|--------|------------|
| Borrar una fila `ACCEPTED` equivocada | `DELETE` condicional por pareja y estado, con cantidad de filas afectadas; 404 si es 0 |
| El módulo de deudas no existe | Puerto + stub; pruebas unitarias del bloqueo con mock; integración con stub sin deuda |
| Cambiar sin querer el contrato de 013 | Endpoints aditivos; `GET /api/users/me/friends` no se toca |
| Afectar grupos o gastos | La operación solo toca `friendship_requests`; pruebas de regresión de 010 y de historial |
| Doble eliminación / concurrencia | Borrado condicional atómico; una operación afecta 1 fila y la otra 0 → 404 |
| Confirmación de éxito falsa | La tarjeta solo se retira con `204` o `404`; los errores no muestran éxito |

## Complexity Tracking

| Elemento | Detalle | Justificación / acción |
|----------|---------|------------------------|
| Nuevo puerto con stub | `DirectDebtPort` no tiene módulo real todavía | Permite especificar y probar el bloqueo sin inventar el dominio de deudas (constitución VI) |
| Pre-chequeo + validación final | Dos endpoints | El pre-chequeo cumple el aviso antes de abrir el diálogo; el `DELETE` garantiza la regla aunque la deuda cambie |
| Borrado físico | No se conserva historial de amistad | La historia no lo pide y evita una migración; el historial de gastos vive fuera de la amistad |
