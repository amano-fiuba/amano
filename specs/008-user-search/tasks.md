---

description: "Task list for feature 008-user-search"
---

# Tasks: Buscador de usuarios registrados

**Input**: Design documents from `/specs/008-user-search/`

**Prerequisites**: [plan.md](./plan.md), [spec.md](./spec.md), [research.md](./research.md), [data-model.md](./data-model.md), [contracts/users-search-api.md](./contracts/users-search-api.md), [quickstart.md](./quickstart.md)

**Tests**: incluidas. La constitución (principio V) exige pruebas automatizadas para toda
funcionalidad nueva, y [research.md](./research.md) §D7 define la estrategia: unitario del
servicio, IT con Testcontainers, Vitest en frontend y cobertura ≥ 85 % en ambos módulos. En
cada historia las pruebas se escriben **primero** y deben fallar antes de implementar.

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
- Sin migraciones ni dependencias nuevas

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: confirmar la rama, la línea base y que no hay cambios de esquema ni dependencias.

- [X] T001 Confirmar que la rama activa es `feat/user-search` y que no se requieren dependencias ni migraciones nuevas: `backend/pom.xml` y `frontend/package.json` sin cambios, y `backend/src/main/resources/db/migration/` sin un V5 — solo se lee la tabla `users` (plan.md §Technical Context, research.md §D8)
- [X] T002 Ejecutar la línea base en verde antes de tocar código: `cd backend && ./mvnw -B verify` y `cd frontend && npm ci && npm run lint && npm run test:coverage && npm run build`, siguiendo [quickstart.md](./quickstart.md) §Verificación automatizada

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: verificar que las piezas compartidas ya existen y que no requieren cambios. **Ninguna historia puede empezar hasta completar esta fase**.

- [X] T003 [P] Verificar en `backend/src/main/java/com/amano/backend/config/SecurityConfig.java` y `backend/src/test/java/com/amano/backend/auth/SecurityConfigTest.java` que `/api/users/**` ya exige sesión verificada; el endpoint `/api/users/search` hereda el 401 sin tocar la configuración — spec FR-011, research.md §D1
- [X] T004 [P] Verificar que `backend/src/main/java/com/amano/backend/common/FieldValidationException.java` y `backend/src/main/java/com/amano/backend/common/ProblemDetailExceptionHandler.java` cubren el error 400 con `properties.fields.q`; no se agregan handlers nuevos — spec FR-004, contracts §400
- [X] T005 [P] Verificar en `backend/src/main/resources/db/migration/V1__create_user_registration.sql` y `V2__email_verification.sql` que `users`, `email_verified_at`, `first_name`, `last_name` y `email` ya existen; no se crean tablas ni índices — data-model.md §Alcance de datos

**Checkpoint**: seguridad, errores y esquema confirmados; las historias pueden comenzar.

---

## Phase 3: User Story 1 - Buscar usuarios registrados y ver resultados (Priority: P1) 🎯 MVP

**Goal**: un usuario autenticado y verificado busca por nombre, apellido o correo (parcial,
sin distinguir mayúsculas, recortando espacios) y ve hasta 20 resultados ordenados por nombre
y apellido, con solo nombre, apellido, correo y avatar por defecto.

**Independent Test**: con una cuenta verificada y otras cuentas de prueba, cargar
`/users/search`, buscar un fragmento de nombre/apellido/correo en distinta capitalización y
verificar resultados, orden, límite, avatar por defecto, ausencia de datos privados y
exclusión de la propia cuenta.

### Tests for User Story 1 ⚠️ (primero, deben FALLAR)

- [X] T006 [P] [US1] Crear `backend/src/test/java/com/amano/backend/user/UserSearchServiceTest.java` (Mockito): `search` recorta y pasa el término en minúsculas (`Locale.ROOT`), usa `PageRequest.of(0, 20)`, propaga el id de la sesión y mapea `User` a `UserSearchResult` con solo `id`, `firstName`, `lastName` y `email`; lista vacía cuando el repositorio no devuelve filas — FR-002, FR-003, FR-006, data-model.md §DTO de salida
- [X] T007 [P] [US1] Crear `backend/src/test/java/com/amano/backend/user/UserSearchIT.java` (MockMvc + Testcontainers PostgreSQL): registrar cuentas y marcarlas verificadas por SQL; buscar parcialmente por nombre, apellido y correo, con mayúsculas/minúsculas y espacios externos; verificar hasta 20 resultados ordenados por nombre y apellido, exclusión de la propia cuenta, solo cuentas verificadas y respuesta con exactamente `id`, `firstName`, `lastName`, `email` (sin `cbuCvu`, `currencies`, `passwordHash` ni otros datos) — FR-002, FR-003, FR-005, FR-006, FR-007, SC-002, SC-003, SC-005
- [X] T008 [P] [US1] Crear `frontend/src/api/usersClient.test.js`: `searchUsers(q)` llama a `GET /api/users/search?q=…` con `encodeURIComponent` y `credentials: 'include'`; 200 devuelve `{ok:true, users}` con el arreglo recibido — contracts §Contrato del frontend, research.md §D5
- [X] T009 [P] [US1] Crear `frontend/src/pages/UsersSearchPage.test.jsx` (mock de `usersClient`): la pantalla muestra campo y botón "Buscar"; al enviar un término válido llama a `searchUsers` y renderiza cada resultado con nombre, apellido (si es `null`, solo el nombre), correo y `<img src="/avatar-default.png">`; no muestra `id`, CBU/CVU, monedas ni contraseña — FR-001, FR-007, FR-008, data-model.md §DTO de salida
- [X] T010 [P] [US1] Actualizar `frontend/src/components/SiteNav.test.jsx`: el enlace "Buscar usuarios" apunta a `/users/search` y se resalta como activo en esa ruta — FR-001, research.md §D6
- [X] T011 [P] [US1] Actualizar `frontend/src/App.test.jsx`: con sesión, `/users/search` renderiza la pantalla protegida de búsqueda; sin sesión, redirige a la pantalla de login — FR-001, FR-011, research.md §D6

### Implementation for User Story 1

- [X] T012 [US1] Crear `backend/src/main/java/com/amano/backend/user/UserSearchResult.java` como record con exactamente `Long id`, `String firstName`, `String lastName`, `String email`; sin CBU/CVU, monedas, contraseña ni avatar — data-model.md §DTO de salida, plan.md §D4
- [X] T013 [US1] Agregar a `backend/src/main/java/com/amano/backend/user/UserRepository.java` la consulta de búsqueda con `lower(...)` + `locate(...)` sobre nombre, apellido (con `coalesce`) y correo, filtro `emailVerifiedAt IS NOT NULL`, `id <> :sessionId`, orden por `lower(firstName)`, `lower(lastName)`, `id` y `Pageable` — spec FR-002, FR-005, FR-006, research.md §D2
- [X] T014 [US1] Crear `backend/src/main/java/com/amano/backend/user/UserSearchService.java` con `search(Long sessionUserId, String q)` `@Transactional(readOnly = true)`: recorta el término, lo pasa a minúsculas (`Locale.ROOT`), solicita `PageRequest.of(0, 20)` y mapea las entidades a `UserSearchResult` (la validación de mínimo 2 caracteres se agrega en US2) — FR-002, FR-003, FR-006, research.md §D3
- [X] T015 [US1] Agregar `GET /api/users/search?q=` a `backend/src/main/java/com/amano/backend/user/UserController.java`: `@RequestParam(name = "q", required = false)`, obtiene el principal de la sesión con el patrón `currentUserId()`, delega en `UserSearchService` y responde `200` con la lista; si el principal es `null`, responde el 401 existente — contracts §200, plan.md §D1
- [X] T016 [US1] Crear `frontend/src/api/usersClient.js` siguiendo el patrón de `frontend/src/api/authClient.js` y `frontend/src/api/expenseClient.js`: `searchUsers(q)` con URL codificada, `credentials: 'include'`, `{ok:true, users}` en 2xx y parseo RFC 7807 para 4xx (el detalle de red se completa en US4) — contracts §Contrato del frontend, research.md §D5
- [X] T017 [US1] Crear `frontend/src/pages/UsersSearchPage.jsx`: formulario con `TextField` y botón "Buscar"; al enviar, llama a `searchUsers`, muestra la lista con nombre, apellido (si es `null`, solo el nombre), correo y `/avatar-default.png`; sin mensajes de mínimo/red todavía (se agregan en US2/US4) — FR-001, FR-007, FR-008, plan.md §D5
- [X] T018 [US1] Registrar la ruta protegida `/users/search` en `frontend/src/App.jsx` con `ProtectedRoute` y `UsersSearchPage`, sin alterar las rutas existentes — FR-001, FR-011, plan.md §D7
- [X] T019 [US1] Agregar el enlace "Buscar usuarios" a `/users/search` en `frontend/src/components/SiteNav.jsx`, respetando las clases de navegación existentes — FR-001, plan.md §D7
- [X] T020 [US1] Verificación de la historia: `cd backend && ./mvnw -B -Dtest=UserSearchServiceTest test` y `cd backend && ./mvnw -B -Dit.test=UserSearchIT verify`; `cd frontend && npx vitest run src/api/usersClient.test.js src/pages/UsersSearchPage.test.jsx src/components/SiteNav.test.jsx src/App.test.jsx`, con todas las pruebas de US1 en verde

**Checkpoint**: US1 funcional y probable de forma independiente (MVP).

---

## Phase 4: User Story 2 - Aviso cuando el término es demasiado corto (Priority: P2)

**Goal**: con menos de 2 caracteres después de recortar espacios, no se ejecuta ninguna
búsqueda y se muestra el aviso "Ingresá al menos 2 caracteres para buscar"; con exactamente
2 caracteres la búsqueda se habilita.

**Independent Test**: escribir 0, 1 o solo espacios y comprobar que no hay llamada al
backend/cliente y aparece el aviso; escribir 2 caracteres y comprobar que la búsqueda se
ejecuta.

### Tests for User Story 2 ⚠️ (primero, deben FALLAR)

- [X] T021 [P] [US2] Ampliar `backend/src/test/java/com/amano/backend/user/UserSearchServiceTest.java`: `null`, vacío, solo espacios y 1 carácter tras recorte lanzan `FieldValidationException` con `fields.q` = "Ingresá al menos 2 caracteres para buscar" y **nunca** invocan el repositorio; exactamente 2 caracteres sí ejecuta la búsqueda — FR-004, SC-006, contracts §400
- [X] T022 [P] [US2] Ampliar `backend/src/test/java/com/amano/backend/user/UserSearchIT.java`: `q` ausente, vacío, solo espacios o de 1 carácter → `400` con `title "Validación fallida"`, `detail "La solicitud contiene campos inválidos"` y `properties.fields.q` exacto; no se devuelven resultados — FR-004, SC-006, contracts §400
- [X] T023 [P] [US2] Ampliar `frontend/src/pages/UsersSearchPage.test.jsx`: con 0, 1 o solo espacios, al intentar buscar no se llama a `searchUsers`, se muestra el aviso exacto y se limpian resultados previos; con 2 caracteres sí se llama — FR-004, SC-006, plan.md §D3
- [X] T024 [P] [US2] Ampliar `frontend/src/api/usersClient.test.js`: un `400` con ProblemDetail devuelve `{ok:false, status:400, detail, fields:{q:…}}`; un cuerpo 4xx no JSON no lanza excepción y deja `detail:''`, `fields:{}` — contracts §400, contracts §Contrato del frontend

### Implementation for User Story 2

- [X] T025 [US2] Agregar a `backend/src/main/java/com/amano/backend/user/UserSearchService.java` la validación de mínimo 2 caracteres después del recorte, con `FieldValidationException(Map.of("q", "Ingresá al menos 2 caracteres para buscar"))` y el máximo de 100 caracteres con `fields.q` = "La búsqueda no puede superar los 100 caracteres" (FR-019) antes de consultar el repositorio — FR-004, research.md §D3
- [X] T026 [US2] Completar en `frontend/src/api/usersClient.js` el parseo de errores 4xx (`detail` y `properties.fields`) con la convención de `authClient.js`, sin lanzar excepción ante un cuerpo inválido — contracts §400
- [X] T027 [US2] Completar en `frontend/src/pages/UsersSearchPage.jsx` la regla de mínimo 2 caracteres: recorte del término, aviso exacto, ausencia de llamada y limpieza de resultados previos cuando no se busca — FR-004, SC-006
- [X] T028 [US2] Verificación de la historia: `cd backend && ./mvnw -B -Dtest=UserSearchServiceTest test` y `./mvnw -B -Dit.test=UserSearchIT verify`; `cd frontend && npx vitest run src/api/usersClient.test.js src/pages/UsersSearchPage.test.jsx` en verde

**Checkpoint**: US1 + US2 completas; la búsqueda corta no consume backend y explica el mínimo.

---

## Phase 5: User Story 3 - Acceso protegido y resultados limitados a cuentas verificadas (Priority: P3)

**Goal**: solo una sesión válida de cuenta verificada accede al buscador; sin sesión o con
cuenta no verificada el endpoint responde 401, y las cuentas no verificadas nunca aparecen
en los resultados.

**Independent Test**: llamar `GET /api/users/search?q=an` sin cookie, con cookie inválida y
con cookie de cuenta pendiente (401); con cuenta verificada, comprobar que una cuenta
pendiente con datos coincidentes no aparece y que la propia cuenta tampoco.

### Tests for User Story 3 ⚠️ (primero, deben FALLAR)

- [X] T029 [P] [US3] Ampliar `backend/src/test/java/com/amano/backend/user/UserSearchIT.java`: sin cookie, cookie inválida y cookie de cuenta no verificada → `401` con ProblemDetail (`title "No autorizado"`, detail "Necesitas iniciar sesión para continuar") sin resultados; una cuenta no verificada cuyos datos coinciden con `q` no aparece en la respuesta de una sesión verificada — FR-011, SC-004, contracts §401
- [X] T030 [P] [US3] Ampliar `backend/src/test/java/com/amano/backend/auth/SecurityConfigTest.java`: `GET /api/users/search?q=an` sin sesión → 401; con cuenta pendiente → 401; con cuenta verificada la autorización deja pasar la solicitud (404 en el slice sin `UserController`) — FR-011, research.md §D1 (cubierto en UserSearchIT.withoutSessionOrWithUnverifiedAccountIsA401)
- [X] T031 [P] [US3] Ampliar `frontend/src/api/usersClient.test.js`: `401` devuelve `{ok:false, status:401}` para que el gate de la ruta redirija, sin mensaje de error de dominio — contracts §401, contracts §Contrato del frontend
- [X] T032 [P] [US3] Ampliar `frontend/src/App.test.jsx` si hace falta para dejar explícito que `/users/search` sin sesión redirige a `/login` y no renderiza el campo de búsqueda ni resultados — FR-011, SC-004

### Implementation for User Story 3

- [X] T033 [US3] Asegurar en `backend/src/main/java/com/amano/backend/user/UserController.java` que el endpoint de búsqueda responde el 401 existente cuando `currentUserId()` es `null`, y confirmar en `backend/src/main/java/com/amano/backend/user/UserRepository.java` los filtros `emailVerifiedAt IS NOT NULL` e `id <> :sessionId` — FR-005, FR-011, contracts §Autorización
- [X] T034 [US3] Verificación de la historia: `cd backend && ./mvnw -B -Dit.test=UserSearchIT,SecurityConfigTest verify` y `cd frontend && npx vitest run src/App.test.jsx src/api/usersClient.test.jsx` en verde, incluyendo la regresión de `/api/users/me` y `/api/expenses/**`

**Checkpoint**: US1–US3 completas; el buscador y el 401 heredado de 003 quedan verificados.

---

## Phase 6: User Story 4 - Estados sin resultados y de error de red (Priority: P4)

**Goal**: una búsqueda válida sin coincidencias muestra exactamente "No se encontraron
usuarios"; un fallo de red/5xx muestra el mensaje fijo "No se pudo completar la búsqueda.
Intenta nuevamente", sin resultados obsoletos y permitiendo reintentar.

**Independent Test**: buscar un término válido sin coincidencias y verificar el mensaje
exacto; simular 5xx y rechazo de `fetch` y verificar el mensaje fijo, la ausencia de
resultados previos y que un nuevo envío vuelve a consultar.

### Tests for User Story 4 ⚠️ (primero, deben FALLAR)

- [X] T035 [P] [US4] Ampliar `backend/src/test/java/com/amano/backend/user/UserSearchIT.java`: una búsqueda válida sin coincidencias responde `200` con `[]` (sin error ni datos inventados) — FR-009, contracts §Respuesta 200
- [X] T036 [P] [US4] Ampliar `frontend/src/api/usersClient.test.js`: 5xx y rechazo de `fetch` devuelven `{ok:false, network:true}`; exportar y asertar el texto exacto de `SEARCH_NETWORK_ERROR_MESSAGE` = "No se pudo completar la búsqueda. Intenta nuevamente" — FR-010, contracts §Contrato del frontend
- [X] T037 [P] [US4] Ampliar `frontend/src/pages/UsersSearchPage.test.jsx`: sin coincidencias muestra exactamente "No se encontraron usuarios"; con `{ok:false, network:true}` muestra el mensaje fijo, no conserva resultados de una búsqueda anterior y un nuevo envío vuelve a llamar a `searchUsers` — FR-009, FR-010, SC-007

### Implementation for User Story 4

- [X] T038 [US4] Completar `frontend/src/api/usersClient.js` con el manejo de 5xx y fallos de `fetch` como `{ok:false, network:true}` y exportar `SEARCH_NETWORK_ERROR_MESSAGE` — FR-010, contracts §Contrato del frontend
- [X] T039 [US4] Completar `frontend/src/pages/UsersSearchPage.jsx`: estado sin resultados con el texto exacto, estado de error de red con el mensaje fijo, limpieza de resultados obsoletos y reintento mediante el mismo botón "Buscar" — FR-009, FR-010, SC-007, plan.md §D5
- [X] T040 [US4] Verificación de la historia: `cd backend && ./mvnw -B -Dit.test=UserSearchIT verify` y `cd frontend && npx vitest run src/api/usersClient.test.js src/pages/UsersSearchPage.test.jsx` en verde

**Checkpoint**: las cuatro historias funcionan y se prueban de forma independiente.

---

## Phase 7: Polish & Cross-Cutting Concerns

**Purpose**: regresiones, cobertura, validación manual, artefactos SDD y Pull Request.

- [X] T041 [P] Ejecutar la verificación completa del backend: `cd backend && ./mvnw -B verify`; confirmar JaCoCo ≥ 85 % de líneas sin reducir el umbral ni excluir código arbitrario — constitución V, XI
- [X] T042 [P] Ejecutar la verificación completa del frontend: `cd frontend && npm ci && npm run lint && npm run test:coverage && npm run build`; confirmar cobertura ≥ 85 % sin reducir umbrales ni excluir archivos arbitrarios — constitución V, XI
- [X] T043 [P] Ejecutar la validación de [quickstart.md](./quickstart.md) de punta a punta (búsqueda, mínimo de 2 caracteres, sin resultados, error de red, 401, límite de 20 y datos expuestos) y registrar el resultado — constitución II, DoD
- [X] T044 [P] Verificar regresiones relevantes: `backend/src/test/java/com/amano/backend/auth/SecurityConfigTest.java`, `LoginIT.java`, `backend/src/test/java/com/amano/backend/user/ProfileEditIT.java`, `backend/src/test/java/com/amano/backend/expense/RegisterExpenseIT.java`, `frontend/src/pages/ProfilePage.test.jsx` y `frontend/src/pages/NewExpensePage.test.jsx` siguen pasando — constitución V, DoD
- [X] T045 Actualizar los artefactos SDD si la implementación difiere del diseño: `specs/008-user-search/plan.md`, `research.md`, `data-model.md`, `contracts/users-search-api.md` y `checklists/requirements.md` — constitución II, DoD
- [ ] T046 Abrir el Pull Request de `feat/user-search` hacia la rama de sprint correspondiente, con resumen de FR/SC cubiertos, evidencia de `./mvnw -B verify` y `npm run test:coverage`, y sin información sensible ni artefactos de depuración — constitución XII, XIII

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: sin dependencias.
- **Foundational (Phase 2)**: depende de Setup; **bloquea** las cuatro historias.
- **User Stories (Phases 3–6)**: dependen de Foundational.
- **Polish (Phase 7)**: depende de que las historias deseadas estén completas.

### Within Foundational

- T003, T004 y T005 son verificaciones independientes [P]; no hay cambios de código.

### User Story Dependencies

- **US1 (P1)**: puede empezar tras Foundational. Es la base del backend, cliente, página,
  ruta y navegación. **MVP**.
- **US2 (P2)**: depende de US1 (amplía `UserSearchService.java`, `usersClient.js`,
  `UsersSearchPage.jsx` y sus pruebas).
- **US3 (P3)**: depende de US1 (endpoint, repositorio y ruta protegida ya existentes) y puede
  ejecutarse después de US2 para evitar conflictos en `UserSearchIT.java` y
  `usersClient.test.js`.
- **US4 (P4)**: depende de US1 (cliente y página ya creados) y se ejecuta después de US2/US3
  porque amplía `UsersSearchPage.test.jsx`, `usersClient.test.js` y `UserSearchIT.java`.
- Por lo anterior, el orden recomendado es **US1 → US2 → US3 → US4**; los tests de cada
  historia se escriben y fallan antes de sus tareas de implementación.

### Within Each User Story

- Tests primero: deben fallar antes de implementar.
- Backend: DTO → repositorio → servicio → controlador.
- Frontend: cliente → página → ruta → navegación.
- La verificación de la historia cierra antes de pasar a la siguiente.

### Parallel Opportunities

- US1: T006–T011 pueden escribirse en paralelo (archivos distintos); luego T012/T013 en
  paralelo, T014 depende de ambos, T015 depende de T014; T016 y T017 pueden avanzar en
  paralelo una vez definido el contrato; T018 y T019 en paralelo.
- US2: T021–T024 en paralelo; T025–T027 en archivos distintos según dependencia.
- US3: T029–T032 en paralelo; T033 toca el controlador/repositorio ya existentes.
- US4: T035–T037 en paralelo; T038 y T039 en archivos distintos.
- Polish: T041, T042, T043 y T044 en paralelo; T045–T046 al final.

---

## Parallel Example: User Story 1

```bash
# Lanzar juntos los tests de US1 (archivos distintos):
Task: "T006 UserSearchServiceTest.java"
Task: "T007 UserSearchIT.java"
Task: "T008 usersClient.test.js"
Task: "T009 UsersSearchPage.test.jsx"
Task: "T010 SiteNav.test.jsx"
Task: "T011 App.test.jsx"

# Luego, implementación en orden de dependencia:
Task: "T012 UserSearchResult.java + T013 UserRepository.java"
Task: "T014 UserSearchService.java → T015 UserController.java"
Task: "T016 usersClient.js + T017 UsersSearchPage.jsx"
Task: "T018 App.jsx + T019 SiteNav.jsx"
```

## Parallel Example: User Story 2

```bash
# Tests en paralelo:
Task: "T021 UserSearchServiceTest.java (mínimo de 2)"
Task: "T022 UserSearchIT.java (400 por q corto)"
Task: "T023 UsersSearchPage.test.jsx (aviso sin llamada)"
Task: "T024 usersClient.test.js (parseo 400)"

# Implementación:
Task: "T025 UserSearchService.java → T027 UsersSearchPage.jsx"
Task: "T026 usersClient.js"
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Completar Phase 1: Setup (T001–T002).
2. Completar Phase 2: Foundational (T003–T005).
3. Completar Phase 3: US1 (T006–T020).
4. **PARAR Y VALIDAR**: búsqueda end-to-end con sesión verificada, resultados acotados,
   orden, límite, avatar por defecto y exclusión de la propia cuenta.
5. Demo/entrega: buscador mínimo operativo.

### Incremental Delivery

1. Foundational → US1 (MVP de búsqueda).
2. US2 → mínimo de 2 caracteres y aviso.
3. US3 → acceso 401 y resultados solo de cuentas verificadas.
4. US4 → sin resultados y error de red.
5. Polish → cobertura, quickstart, artefactos y PR.

### Notes

- `[P]` = archivos distintos, sin dependencias pendientes.
- Mensajes exactos: "Ingresá al menos 2 caracteres para buscar",
  "No se encontraron usuarios" y "No se pudo completar la búsqueda. Intenta nuevamente".
- DTO cerrado a `id`, `firstName`, `lastName`, `email`; la UI no muestra `id` ni datos
  privados.
- Todos los resultados usan `frontend/public/avatar-default.png` (referenciado como
  `/avatar-default.png`).
- No se agregan amistades, solicitudes, grupos, avatares ajenos, paginación, migraciones ni
  dependencias.
- No se editan migraciones existentes (V1–V4).
