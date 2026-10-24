# Implementation Plan: Buscador de usuarios registrados

**Branch**: `feat/user-search` | **Date**: 2026-10-24 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/008-user-search/spec.md`

## Summary

Agregar un buscador de cuentas verificadas para usuarios autenticados y verificados. El
backend expone `GET /api/users/search?q=` dentro del ámbito ya protegido `/api/users/**`
por `SecurityConfig`, consulta la tabla `users` existente con coincidencia parcial e
insensible a mayúsculas en nombre, apellido o correo, excluye al propio usuario, limita a
20 resultados ordenados por nombre y apellido y devuelve un DTO mínimo (`id`, `firstName`,
`lastName`, `email`). El frontend agrega un cliente API, una página protegida accesible
desde `SiteNav`, reutiliza `TextField` y muestra el avatar por defecto para todos los
resultados. No hay migraciones, dependencias nuevas ni cambios de esquema.

## Technical Context

**Language/Version**: Java 25 (backend); JavaScript/JSX con React 19.2 y React Router 8.4
(frontend).

**Primary Dependencies**: Spring Boot 4.1.1 (Web MVC, Data JPA, Security), Maven,
PostgreSQL 18, Flyway; React 19.2, Vite 8, Vitest 5, React Testing Library.

**Storage**: PostgreSQL existente. Solo se lee la tabla `users`; no se crean tablas,
columnas, índices ni migraciones.

**Testing**: JUnit 5 + Mockito (unitario del servicio), MockMvc + Testcontainers
PostgreSQL (`UserSearchIT`), Vitest + React Testing Library (cliente, página, navegación y
ruta protegida). Cobertura mínima del 85 % con JaCoCo (`mvn verify`) y Vitest
(`npm run test:coverage`).

**Target Platform**: Aplicación web en monorepo (`backend/` + `frontend/`), desplegada con
Docker Compose y proxificada por `/api/*`.

**Project Type**: Web service + SPA.

**Performance Goals**: Una búsqueda válida responde y muestra resultados en menos de 2
segundos en condiciones normales; como máximo 20 filas por consulta.

**Constraints**: Sin dependencias ni migraciones nuevas; sin paginación; sin datos
privados en la respuesta; sin avatares ajenos; sin acciones de amistad o grupos. El acceso
debe quedar cubierto por la regla de cuenta verificada ya aplicada a `/api/users/**`
(sin sesión o cuenta no verificada → 401).

**Scale/Scope**: Una pantalla nueva, un endpoint GET, un método de repositorio, un servicio,
un DTO y pruebas asociadas. Los datos son las cuentas ya registradas en `users`.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principio | Cumplimiento en este plan |
|---|---|
| I. Arquitectura y separación de responsabilidades | Se reutilizan `UserController`, `UserRepository`, `SecurityConfig`, el manejo de errores existente y los patrones de clientes/páginas del frontend. No se crean módulos ni mecanismos paralelos. |
| II. SDD y trazabilidad | Cada requisito del spec se mapea a componentes y pruebas; el plan y los contratos documentan el comportamiento antes de implementar. |
| III. Entrega incremental | La feature se puede implementar y verificar en una sola historia funcional sin dependencias pendientes de otras ramas; las pruebas se ejecutan por capas. |
| IV. Alcance controlado | No se agregan amistades, grupos, paginación, avatares ajenos, dependencias ni migraciones. |
| V. Calidad y cobertura | Se planifican prueba unitaria del servicio, IT del endpoint, pruebas Vitest de cliente/página/nav/ruta y verificación de cobertura ≥85 % en ambos módulos. |
| VI. Integridad de reglas de negocio | Solo cuentas verificadas, exclusión del propio usuario, mínimo de 2 caracteres, límite de 20 y orden por nombre/apellido se implementan en el backend como fuente de verdad. |
| VII. Contratos REST y compatibilidad | Se documenta el contrato de `GET /api/users/search` y se mantienen los endpoints existentes; el DTO no altera contratos previos. |
| VIII. Validación y manejo de errores | Se usa `FieldValidationException` y `ProblemDetailExceptionHandler`; los errores no exponen detalles internos. La UI valida y muestra mensajes fijos. |
| IX. Seguridad y control de acceso | El endpoint queda bajo `/api/users/**`; `SecurityConfig` ya exige sesión verificada. La autorización no depende del frontend. |
| X. Código mantenible | Se siguen convenciones de paquetes, DTOs, clientes API, componentes y nombres de pruebas existentes; no se duplican utilidades. |
| XI. Entorno reproducible | Los comandos de verificación son los del repositorio: `mvn -B verify` y `npm ci && npm run lint && npm run test:coverage && npm run build`. |
| XII. Revisión e integración | El trabajo se integra mediante PR desde `feat/user-search` hacia la rama de sprint correspondiente, con revisiones y CI. |
| XIII. Commits y trazabilidad | Los cambios se registrarán con Conventional Commits y referencia a la feature/tareas cuando corresponda. |

**Resultado**: PASS. No hay violaciones que justificar.

## Project Structure

### Documentation (this feature)

```text
specs/008-user-search/
├── plan.md              # Este archivo
├── research.md          # Decisiones técnicas (Phase 0)
├── data-model.md        # Entidades, DTO y reglas (Phase 1)
├── quickstart.md        # Guía de validación (Phase 1)
├── contracts/
│   └── users-search-api.md
├── checklists/
│   └── requirements.md
└── tasks.md             # Se genera con /speckit.tasks
```

### Source Code (repository root)

```text
backend/
├── src/main/java/com/amano/backend/user/
│   ├── UserController.java          # Modificar: GET /api/users/search
│   ├── UserRepository.java          # Modificar: consulta acotada de búsqueda
│   ├── UserSearchService.java       # Nuevo: normalización, validación y mapeo
│   └── UserSearchResult.java        # Nuevo: DTO id, firstName, lastName, email
├── src/main/java/com/amano/backend/config/SecurityConfig.java
│                                    # Sin cambios: /api/users/** ya exige cuenta verificada
└── src/test/java/com/amano/backend/user/
    ├── UserSearchServiceTest.java   # Nuevo: unitario con Mockito
    └── UserSearchIT.java            # Nuevo: IT con Testcontainers

frontend/
├── src/api/
│   ├── usersClient.js               # Nuevo: cliente de búsqueda
│   └── usersClient.test.js          # Nuevo
├── src/pages/
│   ├── UsersSearchPage.jsx          # Nuevo: pantalla protegida
│   └── UsersSearchPage.test.jsx     # Nuevo
├── src/components/
│   ├── SiteNav.jsx                  # Modificar: enlace "Buscar usuarios"
│   └── SiteNav.test.jsx             # Modificar: cobertura del enlace
└── src/App.jsx                      # Modificar: ruta protegida /users/search
```

**Structure Decision**: se mantiene la estructura existente del monorepo. Backend: nuevo
servicio y DTO dentro del paquete `user`, ampliación del controlador y repositorio ya
existentes. Frontend: cliente en `api/`, página en `pages/`, reutilización de `TextField`,
`ProtectedRoute` y `SiteNav`. No se crean capas ni carpetas nuevas.

## Design Decisions

- **D1 — Endpoint**: `GET /api/users/search?q=`. Se elige una ruta específica de dos
  segmentos, sin colisión con `/api/users/me` ni con `/api/users/{id}/avatar`. Queda
  automáticamente bajo la regla de cuenta verificada de `SecurityConfig`.
- **D2 — Consulta**: `UserRepository` recibe un término ya normalizado y ejecuta una
  consulta JPQL con `lower(...)` y `locate(...)` (coincidencia parcial literal, sin
  comodines interpretables), filtrando `emailVerifiedAt IS NOT NULL` y `id <> :sessionId`,
  ordenando por `lower(firstName)`, `lower(lastName)` e `id` como desempate estable, con
  `PageRequest.of(0, 20)`.
- **D3 — Validación**: `UserSearchService` recorta el término y exige al menos 2 caracteres
  después del recorte; si no, lanza `FieldValidationException` con `fields.q`. El frontend
  evita la llamada en ese caso y muestra el mismo aviso.   Si el término supera 100 caracteres tras el recorte, también lanza `FieldValidationException` con `fields.q` (400).
- **D4 — DTO**: `UserSearchResult(Long id, String firstName, String lastName, String email)`
  exclusivamente. No incluye CBU/CVU, monedas, contraseña, avatar ni datos derivados. El
  `id` no se muestra en la lista, pero queda disponible para acciones futuras fuera de
  alcance.
- **D5 — Frontend**: `usersClient.searchUsers(q)` devuelve `{ ok, users }`, errores 4xx
  parseados como ProblemDetail (`status`, `detail`, `fields`) y `{ ok: false, network: true }`
  para red/5xx. La página usa un formulario con `TextField` y botón "Buscar"; maneja aviso
  de mínimo, lista, "No se encontraron usuarios" y mensaje fijo de reintento. Las respuestas
  fuera de orden se descartan para mostrar solo el último término buscado.   Si la respuesta es 401 (sesión vencida o cuenta no verificada), la página limpia la sesión con `setUser(null)` y `ProtectedRoute` redirige a `/login`, sin dejar resultados visibles.
- **D6 — Avatar**: todos los resultados usan `/avatar-default.png`; no se invoca el endpoint
  de avatar ajeno.
- **D7 — Navegación**: enlace "Buscar usuarios" en `SiteNav` hacia `/users/search`; ruta
  envuelta en `ProtectedRoute`.
- **D8 — Pruebas**: unitario del servicio, IT con PostgreSQL real y Vitest para cliente,
  página, navegación y ruta protegida.

## Testing Strategy

- **Backend unitario (`UserSearchServiceTest`)**: recorte y normalización del término,
  rechazo de menos de 2 caracteres sin consultar el repositorio, mapeo al DTO mínimo,
  paso del término normalizado y límite de 20 resultados.
- **Backend integración (`UserSearchIT`)**: cuentas verificadas y no verificadas en
  PostgreSQL real; búsqueda parcial por nombre, apellido y correo; insensibilidad a
  mayúsculas; exclusión del propio usuario; orden y tope de 20; respuesta sin datos
  privados; `q` inválido → 400 con `properties.fields.q`; sin sesión y cuenta no
  verificada → 401.
- **Frontend (`usersClient.test.js`, `UsersSearchPage.test.jsx`, `SiteNav.test.jsx`,
  `App.test.jsx`)**: construcción de URL con `encodeURIComponent`, éxito/4xx/5xx/red,
  aviso de mínimo sin llamada, render de resultados con avatar por defecto y campos
  permitidos, mensaje sin resultados, mensaje fijo de error de red, enlace de navegación y
  redirección de la ruta protegida sin sesión.
- **Cobertura**: `mvn -B verify` (JaCoCo ≥85 %) en `backend/` y
  `npm run test:coverage` (Vitest ≥85 %) en `frontend/`.

## Risks and Mitigations

- **Riesgo**: que la búsqueda interprete `%` o `_` como comodines. **Mitigación**: usar
  `locate` sobre el término normalizado, sin patrones LIKE construidos con entrada del
  usuario.
- **Riesgo**: respuestas fuera de orden al tipear/buscar rápido. **Mitigación**: descartar
  respuestas obsoletas en la página.
- **Riesgo**: exponer datos privados en el DTO. **Mitigación**: DTO cerrado a cuatro campos
  y prueba de contrato que verifica campos prohibidos.
- **Riesgo**: que el frontend sea la única barrera de acceso. **Mitigación**: el endpoint
  permanece bajo `/api/users/**`, protegido por `SecurityConfig`; el 401 se prueba en IT.

## Post-Design Constitution Check

Tras generar `research.md`, `data-model.md`, `contracts/users-search-api.md` y
`quickstart.md`, se re-evalúan los gates: el diseño mantiene el endpoint bajo el ámbito
verificado existente, no crea tablas ni dependencias, expone solo cuatro campos del DTO y
planifica pruebas de servicio, contrato HTTP, UI y cobertura. **Resultado: PASS**, sin
violaciones ni trabajo de complejidad adicional.

## Complexity Tracking

No se registran violaciones: no hay proyectos, capas, dependencias ni cambios de esquema
nuevos.
