# Tasks: Visualización de perfil de usuario

**Input**: Design documents from `/specs/002-user-profile/`

**Prerequisites**: plan.md (required), spec.md (required for user stories), data-model.md, contracts/users-api.md, quickstart.md

**Tests**: explícitamente solicitados y **OBLIGATORIOS** (Constitution V del repo): cada historia comienza por sus tests co-ubicados, que deben escribirse primero y verse FALLAR antes de la implementación.

**Organization**: agrupado por User Story para implementación y testeo independientes.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: puede correr en paralelo (archivos distintos, sin dependencias)
- **[Story]**: historia a la que pertenece la tarea (US1, US2, US3)
- Incluye paths exactos en las descripciones

## Path Conventions

- Frontend: `frontend/src/` (tests co-ubicados junto al código)
- Backend: SIN cambios en esta feature (ver [data-model.md](data-model.md) — no-op, y [contracts/users-api.md](contracts/users-api.md) — reuso de `001`)

---

## Phase 1: Setup

**Purpose**: verificar el punto de partida antes de tocar código

- [x] T001 [Setup] Verificar línea base verde en frontend: `npm run test` (66/66) y `npm run lint` (0 errores; 1 warning preexistente en RegisterForm) sin regresiones. Nota: Node local debió actualizarse a v24 (`~/.local/opt`, Vite 8 requiere ≥20.19) y `npm ci` reejecutado con él para el binding nativo de rolldown

**Checkpoint**: línea base estable; el trabajo por historias puede arrancar

---

## Phase 2: User Story 1 - Visualizar mi perfil (Priority: P1) 🎯 MVP

**Goal**: pantalla `/profile` protegida que muestra nombre, email, contraseña enmascarada, apellido, avatar, CBU/CVU y monedas (con reglas de opción/default)

**Independent Test**: con sesión activa, `/profile` renderiza cada campo con su valor registrado o su representación prevista ("No informado", default ARS, avatar default)

### Tests for User Story 1 ⚠️ (escribir primero y verlos FALLAR)

- [x] T002 [P] [US1] Test co-ubicado `frontend/src/pages/ProfilePage.test.jsx`: mock de `me()` (`frontend/src/api/authClient.js`) devolviendo perfil completo → verifica render de nombre, email, contraseña enmascarada con leyenda "Por seguridad, la contraseña no se muestra", apellido, CBU/CVU, monedas y avatar propio (cubre FR-002 y FR-003). Nota: el mock es del módulo authClient (no del fetch global): React ejecuta efectos de hijos antes que los del padre, por lo que el orden de fetchs globales no es determinista
- [x] T003 [P] [US1] Test en el mismo archivo: perfil sin `lastName`, sin `cbuCvu`, `currencies: []` y `hasCustomAvatar: false` → verifica "No informado" en apellido y CBU/CVU, moneda `ARS`, avatar `/avatar-default.png` (cubre FR-004, FR-005 y FR-006)

### Implementation for User Story 1

- [x] T004 [US1] Crear `frontend/src/pages/ProfilePage.jsx`: pantalla de solo lectura que consulta `me()` al montar (patrón `DashboardPage` con refresh explícito), estados loading / datos / error, reglas de render del contrato (`specs/002-user-profile/contracts/users-api.md`), sin ningún control de edición (cubre FR-001, FR-002, FR-003, FR-004, FR-005, FR-006 y FR-011). Nota: `.catch` defensivo en el efecto para Fallos que el cliente propaga como rechazo
- [x] T005 [US1] Registrar ruta protegida `/profile` en `frontend/src/App.jsx` (elemento envuelto en `frontend/src/auth/ProtectedRoute.jsx`) y agregar test de routing en `frontend/src/App.test.jsx` que verifique el render de la página en esa ruta (cubre FR-001 y FR-008). Nota: los tests usan `AppRoutes` + `MemoryRouter` (el `<App />` por defecto trae su propio BrowserRouter)

**Checkpoint**: con sesión, `/profile` muestra el MVP completo y la ruta es alcanzable; `npm run test` verde

---

## Phase 3: User Story 2 - Aislamiento de datos (Priority: P2)

**Goal**: garantizar que la pantalla muestra únicamente la información del usuario en sesión

**Independent Test**: con sesión de un usuario A mockeada, el DOM de `/profile` no contiene ningún dato de otro usuario B; el único origen de datos es la respuesta de `me()` de la sesión

### Tests for User Story 2 ⚠️

- [x] T006 [P] [US2] Test en `frontend/src/pages/ProfilePage.test.jsx`: mock de `me()` con datos de A y afirmar que el texto renderizado coincide exactamente con los valores de A y no contiene los de un usuario B esperado (cubre FR-007)

### Implementation for User Story 2

- [x] T007 [US2] Agregar entrada de navegación "Mi perfil" en `frontend/src/pages/DashboardPage.jsx` (link a `/profile`, alcanzable en ≤2 interacciones, SC-001) y extender su test co-ubicado `frontend/src/pages/DashboardPage.test.jsx` (cubre FR-001). Nota: los tests del dashboard se envuelven en `MemoryRouter` por el nuevo `Link`

**Checkpoint**: el perfil siempre refleja la sesión activa y es alcanzable desde el dashboard

---

## Phase 4: User Story 3 - Acceso protegido y estados de fallo (Priority: P3)

**Goal**: fallo de red/servidor con mensaje sin datos ficticios; redirección sin sesión (heredada del gate); fallback de avatar ante 404

**Independent Test**: simulando red caída la pantalla muestra error sin datos; visitando `/profile` sin sesión hay redirección; ante avatar inexistente se muestra el default

### Tests for User Story 3 ⚠️

- [x] T008 [P] [US3] Test en `frontend/src/pages/ProfilePage.test.jsx`: mock de `me()` con resultado `ok:false` (network/error) → verifica texto de estado de error y ausencia de datos incompletos o placeholders (cubre FR-009). Cubierto con 2 variantes: rechazo del cliente y `result.network` resuelto
- [x] T009 [P] [US3] Test en `frontend/src/App.test.jsx` (o el existente de `ProtectedRoute`): ruta `/profile` sin usuario en contexto → redirección al flujo de registro/acceso preservando el destino (cubre FR-008). Nota: la redirección llega a la `/register` real (RegisterPage), por eso la aserción usa su heading "Crear tu cuenta"

### Implementation for User Story 3

- [x] T010 [US3] Cerrar implementación de estados en `frontend/src/pages/ProfilePage.jsx`: mensaje de error visible sin datos ni placeholders que parezcan datos reales y sin recarga automática de datos (cubre FR-009); el fallback de avatar ya cumple FR-005 vía contrato y el gate ya cumple FR-008. Nota: con sesión y fallo de red la pantalla muestra solo el mensaje; el 401 del refresco no rompe el render (el gate de la ruta redirige); 2 tests extra cubren limpieza del efecto al desmontar y 401 del refresco

**Checkpoint**: todas las historias funcionan de forma independiente

---

## Phase 5: Verificación Final

**Purpose**: gates de calidad y validación E2E

- [x] T011 [P] Ejecutar `npm run test:coverage` en `frontend/` (gate ≥85% líneas/statements) y corregir huecos de cobertura introducidos por la feature. Resultado: 94.62% líneas / 93.17% stmts global; ProfilePage 100% líneas con tests de ramas extra (respuesta tardía, result.network, 401 del refresco)
- [x] T012 [P] Ejecutar `npm run lint` en `frontend/` limpio (0 errores; persiste 1 warning preexistente de react-hooks en RegisterForm, fuera de esta feature); correr `mvn -B verify` en `backend/` como regresión: BUILD SUCCESS (12 tests, JaCoCo OK). Nota de entorno: JDK 25 local instalado en `~/.local/opt/jdk-25.0.4.1+1` (el sistema trae JDK 21)
- [x] T013 Ejecutar la validación manual de `specs/002-user-profile/quickstart.md` (E2E 1–5) con `docker compose up --build` y registrar resultados. Aceptado vía registro + curl a través del proxy :8081 (E2E 1: perfil completo con normalización de CBU y avatar PNG servido; E2E 2: sin opcionales; E2E 3: avatar ajeno 404; E2E 4: 401 sin cookie; E2E 5: parada del backend → fallo limpio, y la sesión sobrevive al reinicio). Contraseñas de prueba: ver /tmp/opencode/amano.env (externo al repo)
- [x] T014 Auditoría de trazabilidad FR/SC ↔ tests en esta misma checklist (FR-001..FR-011, SC-001..SC-005) y actualizar notas de tareas antes del PR a `sprint-1` (Conventional Commits). Trazabilidad: FR-001→T004/T005/T007; FR-002/FR-003→T002; FR-004/FR-005/FR-006→T003; FR-007→T006; FR-008→T005/T009; FR-009→T008/T010; FR-010→T012/T013 (sin cambios de backend); FR-011→T004 (pantalla sin controles de edición); SC-001→T007; SC-002→T006/T013; SC-003→T002/T013; SC-004→T008/T012/T013; SC-005→T003

---

## Phase 6: Post-plan fixes (FR-012)

- [x] T015 [P] [FE] Ajuste del saludo del dashboard (FR-012, pedido del usuario): en `frontend/src/pages/DashboardPage.jsx` el `h1` pasa de "Bienvenida, {nombre apellido}" a "Te damos la bienvenida, {nombre}" — solo el nombre, sin apellido (se elimina el cálculo `fullName`). Test co-ubicado extendido con asertos exactos (saludo exacto y apellido ausente del saludo).
- [x] T016 [FE] Verificación tras T015: `npm run lint` (0 errores) y `npx vitest run src/pages/DashboardPage.test.jsx` en verde (~1 min; suite completa diferida a cierre).

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: sin dependencias; T001 primero.
- **US1 (Phase 2)**: depende de Setup; tests T002/T003 FALLAN → T004, T005.
- **US2 (Phase 3)**: depende de US1 (la pantalla debe existir); T006 → T007.
- **US3 (Phase 4)**: depende de la pantalla; tests T008/T009 → T010.
- **Verificación (Phase 5)**: depende de todas las historias.

### User Story Dependencies

- **US1**: sin dependencias con otras historias (MVP).
- **US2**: extiende la pantalla de US1; independiente en lógica (solo afirma sobre el render).
- **US3**: integra con mecanismos existentes (gate, fallback); sin dependencia con US2.

### Parallel Opportunities

- T002 y T003 en paralelo (mismo archivo; escritura secuencial recomendada por conflicto de archivo; marcar [P] por contenido).
- T008 y T009 en paralelo (archivos distintos).
- T011/T012 en paralelo tras el cierre de historias.

## Implementation Strategy

- **MVP first**: Setup → US1 → validar checkpoint → luego US2 y US3.
- Cada checkpoint cierra con lint + tests verdes.
- Commit Conventional por tarea o grupo lógico; PR `feat/user-profile` → `sprint-1` después de T014.

## Notes

- Los tests se escriben antes de la implementación y deben verse FALLAR primero.
- Sin backend: cualquier necesidad detectada de nuevo endpoint es señal de desvío del contrato → revisar specs antes de codear.
- Evitar duplicación: reusar `me()`, `ProtectedRoute`, `useAuth` y el fallback de avatar del dashboard.
