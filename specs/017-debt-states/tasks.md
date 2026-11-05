---

description: "Task list for feature 017-debt-states"
---

# Tasks: Crear estados de deuda

**Input**: Design documents from `/specs/017-debt-states/`

**Prerequisites**: [plan.md](./plan.md), [spec.md](./spec.md), [research.md](./research.md), [data-model.md](./data-model.md), [contracts/group-expenses-api.md](./contracts/group-expenses-api.md), [quickstart.md](./quickstart.md)

**Tests**: incluidas (constitución V). En cada historia, las pruebas se escriben primero y deben fallar antes de implementar.

**Coverage gates**: JaCoCo `LINE COVEREDRATIO ≥ 0.85` (`backend/pom.xml`) y Vitest V8 `lines`/`statements ≥ 85` (`frontend/vite.config.js`). Ninguna tarea baja los umbrales ni excluye archivos nuevos del reporte.

**Organization**: tareas agrupadas por historia de usuario. Se implementa una historia por vez (constitución III).

**Punto de partida**: la rama `feat-crear-estadoss-deudas` (desde `sprint-4`, `223a768`, más el merge de `feat/delete-friend`) no tiene nada de 017. Todas las tareas empiezan sin marcar (spec §Clarifications, sesión de la rama).

## Format: `[ID] [P?] [Story] Description`

- **[P]**: puede ejecutarse en paralelo (archivos distintos, sin dependencias pendientes)
- **[Story]**: US1, US2 o US3; sin etiqueta en Setup, Foundational y Polish

## Path Conventions

- Backend: `backend/src/main/java/com/amano/backend/{groupexpense,groups}/…`, pruebas en `backend/src/test/java/com/amano/backend/…`
- Migración: `backend/src/main/resources/db/migration/V13__create_group_expenses_and_debts.sql`
- Frontend: `frontend/src/…`, pruebas `*.test.js(x)` junto al código

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: confirmar la base de trabajo

- [ ] T001 Confirmar la rama `feat-crear-estadoss-deudas` desde `sprint-4` (`223a768`) con el merge de `feat/delete-friend` commiteado, V1–V12 en `db/migration` y sin `V13`. Correr `./mvnw clean` para descartar una `V13` compilada vieja en `target/classes` — quickstart.md §1
- [ ] T002 Confirmar que `017` y `V13` siguen libres en todas las ramas (`015-main-screen` y `016-delete-friend` no tienen migraciones) y avisar al equipo la reserva de `017`/`V13` y `018`/`V14` — research.md D1
- [ ] T003 Línea base antes de tocar código: `cd backend && ./mvnw -B clean verify` y `cd frontend && npm run lint && npm run test:coverage`, los dos en verde. Anotar en quickstart.md §Resultado la cantidad de pruebas y la cobertura de partida
- [ ] T004 Si la base local llegó a aplicar una `V13` de un borrador anterior, recrearla (`docker compose down -v && docker compose up -d db`) — quickstart.md §1

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: esquema, entidades y reglas puras que usan todas las historias, y la corrección aislada de `DebtPort`

**⚠️ CRITICAL**: ninguna historia empieza antes de terminar esta fase

### Migración y dominio

- [ ] T005 [P] `GroupExpenseMigrationIT` en `backend/src/test/java/com/amano/backend/groupexpense/`: las tres tablas existen; los CHECK rechazan estado incoherente con `paid_amount`, `paid_amount > amount`, participante `REGISTERED` sin `user_id`, deuda del pagador consigo mismo, moneda inválida y participante repetido; borrar un perfil simulado deja `simulated_profile_id` y `debtor_simulated_profile_id` en `NULL` — FR-011, data-model.md
- [ ] T006 Crear `V13__create_group_expenses_and_debts.sql` según el boceto de data-model.md §Cambios de esquema: 3 `CREATE TABLE` y 6 `CREATE INDEX`, sin `ALTER` sobre tablas existentes ni `INSERT`/`UPDATE`/`DELETE` — FR-011, FR-026
- [ ] T007 [P] `EqualSplitTest`: exacto, resto al pagador, resto de a un centavo en orden sin pagador, `base = 0` rechazado, suma exacta con montos grandes (999.999.999,99 entre 7) — FR-005, FR-006, SC-001, SC-002
- [ ] T008 [P] `DebtTest`: `DebtStatus.of` para los tres casos; `applyPayment` en `PENDING → PARTIALLY_PAID → PAID` y `PENDING → PAID`; rechaza monto ≤ 0, mayor al saldo y deuda `PAID` sin cambiar nada; `getOutstanding` — FR-011, FR-012
- [ ] T009 Crear `ParticipantType`, `DebtStatus` y `EqualSplit` en `groupexpense/` — research.md D4/D5
- [ ] T010 Crear las entidades `GroupExpense`, `GroupExpenseShare` y `Debt` (con `applyPayment` y `getOutstanding`) y sus tres repositorios; `ddl-auto=validate` pasa contra `V13` — data-model.md

### Fix aislado de `DebtPort` (se commitea aparte, antes de US1)

- [ ] T011 [P] `MemberRemovalServiceTest` y `GroupLeaveServiceTest` usan `hasPendingDebtsForUser`/`hasPendingDebtsForSimulatedProfile`. Pruebas nuevas: `removeSimulatedProfile` consulta el perfil y nunca un usuario con el mismo id, y `removeMember` y `leaveGroup` consultan al usuario — FR-018, research.md D7
- [ ] T012 `DebtPort` pasa a `hasPendingDebtsForUser(groupId, userId)` y `hasPendingDebtsForSimulatedProfile(groupId, profileId)`. `NoOpDebtPort` implementa los dos (sigue en `false` hasta US2). `GroupService.leaveGroup`, `GroupMemberService.removeMember` y `removeSimulatedProfile` llaman al método correcto — FR-018, research.md D7

**Checkpoint**: `GroupExpenseMigrationIT`, `EqualSplitTest`, `DebtTest` y las pruebas de 010 en verde con `ddl-auto=validate`.

---

## Phase 3: User Story 1 - Registrar un gasto de grupo y generar sus deudas (Priority: P1) 🎯 MVP

**Goal**: `POST` y `GET /api/groups/{id}/expenses…` crean y muestran el gasto, sus partes, sus deudas `PENDING` y el evento del historial; el detalle del grupo informa la cantidad real de gastos.

**Independent Test**: con un grupo de tres participantes, el `POST` devuelve 201 con partes que suman el total y dos deudas `PENDING`, y el historial tiene `EXPENSE_CREATED` (spec US1).

### Tests for User Story 1 ⚠️

> **NOTE: escribir estas pruebas primero y verificar que fallen**

- [ ] T013 [P] [US1] `GroupPermissionCheckerTest`: `canCreateExpenses` y `canExpenseForOthers` para ADMIN, MEMBER y cada valor de las dos casillas — FR-007
- [ ] T014 [P] [US1] `GroupExpenseServiceTest`: validaciones de monto, fecha y descripción con mensajes de 007; pagador no miembro o ex miembro; participantes vacíos, solo el pagador, repetidos, ajenos o ex miembros; monto insuficiente; casillas; idempotencia (misma clave → existente, otro cuerpo → `IdempotencyConflictException`); partes y deudas generadas con nombres copiados; `recordExpenseCreated` invocado con los datos del gasto — FR-002..FR-010, FR-014, FR-015
- [ ] T015 [P] [US1] `GroupExpenseControllerIT`: 201 con `Location` y cuerpo del contrato (escenarios 1–3 de US1); 200 en reenvío; 400 por campo; 400 sin `Idempotency-Key`; 403 por cada casilla; 404 para no miembro y para gasto de otro grupo; 422 por clave reutilizada; 401 sin sesión; una sola entrada `EXPENSE_CREATED` en `/activity` aunque haya reenvío; rollback completo si falla una parte — contrato, FR-013, SC-005
- [ ] T016 [P] [US1] `expensesCount`: `GroupServiceTest` (el detalle usa el conteo del repositorio) y `GroupExpenseControllerIT` (`GET /api/groups/{id}` devuelve 0 sin gastos y 2 después de registrar dos) — FR-027

### Implementation for User Story 1

- [ ] T017 [US1] `GroupPermissionChecker.canCreateExpenses` y `canExpenseForOthers` — research.md D8
- [ ] T018 [US1] `CreateGroupExpenseRequest` (rechaza propiedades desconocidas) y `GroupExpenseResponse` (partes y deudas ordenadas por `position`, `outstanding` calculado) — contrato
- [ ] T019 [US1] `GroupExpenseService.create` en el orden de plan.md §Flujo del backend: membresía (404 neutral), idempotencia, casillas (403), normalizadores de 007, pagador y participantes, `EqualSplit`, partes, deudas, `recordExpenseCreated` y resolución de carrera como `ExpenseService` — FR-001..FR-015
- [ ] T020 [US1] `GroupExpenseService.get` con el 404 neutral, también para un gasto de otro grupo — FR-020
- [ ] T021 [US1] `GroupExpenseController`: `POST /api/groups/{id}/expenses` (201/200 + `Location`, `Idempotency-Key`) y `GET /api/groups/{id}/expenses/{expenseId}` — contrato
- [ ] T022 [US1] Mensajes nuevos de 403 y 400 ("Solo podés registrar gastos pagados por vos", "Elegí al menos un participante además de quien pagó", etc.) como constantes, en español con voseo — contrato
- [ ] T023 [US1] `GroupRepository.countGroupExpenses` (JPQL sobre `GroupExpense`), `GroupService.getGroup` lo usa para `expensesCount`, y se actualiza el Javadoc de `GroupDetailResponse` — FR-027, research.md D8
- [ ] T024 [US1] Verificación de la historia: `./mvnw -B -Dtest='EqualSplitTest,DebtTest,GroupExpenseServiceTest,GroupPermissionCheckerTest,GroupServiceTest' test` y `./mvnw -B -Dit.test='GroupExpenseMigrationIT,GroupExpenseControllerIT' verify` en verde. En `target/site/jacoco/index.html`, el paquete `groupexpense` queda ≥ 85 % de líneas

**Checkpoint**: US1 funcional por API (MVP). Las deudas existen pero todavía no bloquean bajas (`NoOpDebtPort` sigue activo hasta US2).

---

## Phase 4: User Story 2 - Bloquear bajas con deudas sin saldar (Priority: P1)

**Goal**: `LedgerDebtPort` reemplaza a `NoOpDebtPort`; abandonar o quitar a alguien con deudas sin saldar responde 403 `PENDING_DEBTS`.

**Independent Test**: después de crear un gasto con deudas, abandonar o quitar al deudor, al acreedor o al perfil simulado responde 403; en grupos sin deudas todo sigue como en 010 (spec US2).

**Depende de**: US1 (necesita deudas reales para las pruebas de integración) y del fix T011/T012.

### Tests for User Story 2 ⚠️

- [ ] T025 [P] [US2] `LedgerDebtPortIT` (Testcontainers; la regla vive en las consultas de `DebtRepository`): usuario deudor y acreedor con `PENDING`/`PARTIALLY_PAID` → `true`; solo `PAID` → `false`; otro grupo → `false`; perfil simulado deudor → `true`; usuario y perfil con el mismo id no se confunden — FR-017, FR-018
- [ ] T026 [P] [US2] `GroupDebtBlockingIT`: escenarios 1–7 de US2 de punta a punta (abandonar, quitar miembro, quitar perfil; deudor y acreedor; ids coincidentes; `PAID` y otro grupo no bloquean); al bloquear no cambia nada — FR-019, SC-003

### Implementation for User Story 2

- [ ] T027 [US2] `DebtRepository.existsOpenForUser(groupId, userId)` (deudor o acreedor) y `existsOpenForSimulatedProfile(groupId, profileId)` sobre los índices parciales — research.md D7, plan.md §Bloqueo de bajas
- [ ] T028 [US2] Crear `LedgerDebtPort` (`@Component`, en `groups/port/`) y eliminar `NoOpDebtPort` — FR-016
- [ ] T029 [US2] Actualizar los Javadoc y comentarios que mencionan el stub (`PendingDebtsException`, `LeaveGroupIT`, `MemberRemovalIT`) — FR-019
- [ ] T030 [US2] Verificación de la historia: `LedgerDebtPortIT`, `GroupDebtBlockingIT` y todas las suites de 010 (`LeaveGroupIT`, `MemberRemovalIT`, `GroupRealFriendshipIT`, etc.) en verde; `grep -rn NoOpDebtPort backend/src` vacío

**Checkpoint**: US1 y US2 funcionan; las suites de 010 siguen en verde.

---

## Phase 5: User Story 3 - Formulario "Nuevo gasto" y detalle con estados (Priority: P2)

**Goal**: registrar un gasto desde la UI, ver sus deudas con estado y llegar al detalle desde la pantalla del grupo y el historial (plan.md §Diseño del frontend).

**Independent Test**: con el backend simulado en Vitest, completar el formulario, ver la vista previa, enviar y ver el detalle con deudas "Pendiente"; probar errores 400/403/404/401/red (spec US3).

**Depende de**: US1 (contrato). Puede hacerse en paralelo con US2.

### Tests for User Story 3 ⚠️

- [ ] T031 [P] [US3] `frontend/src/pages/groups/splitEqually.test.js`: los mismos casos que `EqualSplitTest`, más `null` con monto inválido — FR-022, research.md D4
- [ ] T032 [P] [US3] `debtStatusLabels.test.js` (tres etiquetas, saldo formateado, estado desconocido) y `expensePermissions.test.js` (ADMIN; MEMBER con cada combinación de casillas; `group` nulo) — FR-007, FR-023
- [ ] T033 [P] [US3] `frontend/src/api/groupExpensesClient.test.js`: cabecera `Idempotency-Key`, cuerpo, `credentials: 'include'`, 201 vs 200 (`created`), 400 con `fields`, 403, 404, 401, 5xx y red como `network` — contrato
- [ ] T034 [P] [US3] `components/groups/DebtStatusBadge.test.jsx`, `ExpenseDebtList.test.jsx` y `ExpenseSharePreview.test.jsx`: estados con su clase y su texto, frase deudor → acreedor en orden, "(pagó)", notas de resto, estados vacíos — FR-022, FR-023
- [ ] T035 [P] [US3] `components/groups/GroupExpensesCard.test.jsx`: textos de cantidad (0, 1, N), "Nuevo gasto" visible u oculto según permisos, enlace con `?filter=EXPENSES` — FR-021
- [ ] T036 [P] [US3] `pages/groups/GroupExpenseFormView.test.jsx`: carga, pagador preseleccionado, todos los participantes marcados, vista previa reactiva con resto al pagador y sin pagador, pagador fijo para MEMBER sin casilla, pantalla de sin permiso, 400 por campo sin perder datos, 403, 401, red con reintento y la misma clave, snackbar y navegación al detalle en 201 — FR-021, FR-022, FR-025
- [ ] T037 [P] [US3] `pages/groups/GroupExpenseDetailView.test.jsx`: encabezado, partes en orden, deudas "Pendiente", "Pago parcial · faltan $ X" y "Pagada", perfil borrado con nombre copiado, 404, 401, red con reintento — FR-023, FR-025
- [ ] T038 [P] [US3] Ampliar `GroupInfoView.test.jsx` (aparece `GroupExpensesCard`), `GroupHistoryView.test.jsx` ("Ver gasto" con y sin `expenseId`; `?filter=EXPENSES` aplicado; valor desconocido → "Toda la actividad") y `App.test.jsx` (las dos rutas son protegidas) — FR-021, FR-024

### Implementation for User Story 3

- [ ] T039 [US3] `pages/groups/splitEqually.js` (centavos enteros, misma regla que `EqualSplit`) — research.md D4
- [ ] T040 [US3] `pages/groups/debtStatusLabels.js` y `pages/groups/expensePermissions.js` — plan.md §Módulos sin interfaz
- [ ] T041 [US3] `api/groupExpensesClient.js`: `createGroupExpense(groupId, payload, idempotencyKey)`, `getGroupExpense(groupId, expenseId)` y los dos mensajes fijos — contrato
- [ ] T042 [US3] `components/groups/DebtStatusBadge.jsx`, `ExpenseSharePreview.jsx` y `ExpenseDebtList.jsx` — plan.md §Componentes
- [ ] T043 [US3] `components/groups/GroupExpensesCard.jsx` y montarla en `GroupInfoView.jsx` entre la tarjeta de miembros y la de acciones — FR-021
- [ ] T044 [US3] `pages/groups/GroupExpenseFormView.jsx`: `react-hook-form` y validaciones de `validation/expenseValidation.js`, claves `u:`/`s:`, manejo de la `Idempotency-Key` como `ExpenseForm`, `useSnackbar` — FR-022, FR-025
- [ ] T045 [US3] `pages/groups/GroupExpenseDetailView.jsx` con `ExpenseDebtList` y el enlace "Volver al grupo" — FR-020, FR-023, FR-025
- [ ] T046 [US3] Rutas `/groups/:id/expenses/new` y `/groups/:id/expenses/:expenseId` en `App.jsx` (con `ProtectedRoute`); en `GroupHistoryView.jsx`, `?filter=` con `useSearchParams` y el enlace "Ver gasto" — FR-021, FR-024
- [ ] T047 [US3] Estilos `.group-expense*` en `frontend/src/index.scss` con las variables existentes — plan.md §Cambios en vistas existentes
- [ ] T048 [US3] Verificación de la historia: `npx vitest run src/pages/groups src/components/groups src/api/groupExpensesClient.test.js src/App.test.jsx` en verde

**Checkpoint**: las tres historias funcionan y resisten errores.

---

## Phase 6: Polish & Cross-Cutting Concerns

- [ ] T049 [P] Revisión de la migración y del alcance: `grep -nEi '\b(UPDATE|DELETE|INSERT|DROP|TRUNCATE)\b' V13__*.sql` solo encuentra `ON DELETE SET NULL`; `expenses` y los endpoints de 007/009 no cambiaron; `git diff sprint-4 -- specs/` solo muestra `specs/017-debt-states/` (y `016-delete-friend` del merge) — FR-026, specs anteriores inmutables
- [ ] T050 [P] Revisar que ninguna respuesta nueva exponga CBU/CVU, correo, contraseña ni datos de otros grupos, y que el 404 no revele si el gasto existe — FR-009, FR-020
- [ ] T051 Backend completo: `cd backend && ./mvnw -B clean verify` en verde, con el gate de JaCoCo cumplido (≥ 85 % de líneas). Anotar la cobertura total y la de `groupexpense` en quickstart.md §Resultado — constitución V/XI
- [ ] T052 Frontend completo: `cd frontend && npm run lint && npm run test:coverage && npm run build` en verde, con el gate de Vitest cumplido (≥ 85 % de líneas y sentencias). Anotar la cobertura en quickstart.md §Resultado — constitución V/XI
- [ ] T053 **Validación manual (persona usuaria)**: escenarios A–I de quickstart.md §2 con base, backend y frontend levantados; registrar el resultado en quickstart.md §Resultado
- [ ] T054 Commits con Conventional Commits, en este orden: `docs:` spec 017, `fix:` puerto de deudas (T011/T012), `feat:` gasto de grupo y deudas (US1), `feat:` bloqueo por deudas (US2), `feat:` pantalla de gastos (US3), `test:` si queda algo suelto. Después, PR de `feat-crear-estadoss-deudas` hacia `sprint-4` — constitución XII/XIII

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)** → **Foundational (Phase 2)** → historias.
- **US1 (Phase 3)**: depende de Foundational.
- **US2 (Phase 4)**: depende de US1 (las pruebas de integración necesitan gastos y deudas reales) y del fix T011/T012.
- **US3 (Phase 5)**: depende del contrato de US1. Puede hacerse en paralelo con US2.
- **Polish (Phase 6)**: después de las tres historias.

### Within Each User Story

- Pruebas primero (deben fallar), después reglas puras, cliente o servicio, componentes y vistas.
- Terminar y validar una historia antes de pasar a la siguiente (constitución III).

### Parallel Opportunities

- T005, T007, T008 y T011 en paralelo (archivos distintos).
- Las pruebas [P] de cada historia en paralelo.
- US2 (backend) y US3 (frontend) en paralelo una vez cerrada US1.
- En US3, T039–T042 no dependen entre sí salvo `ExpenseSharePreview`, que usa `splitEqually`.

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Phase 1 + Phase 2.
2. Phase 3 (US1).
3. **STOP and VALIDATE**: `GroupExpenseControllerIT` y el escenario A del quickstart por API.

### Incremental Delivery

1. Foundational → US1 (gastos y deudas por API).
2. US2 (bloqueos reales; cierra la deuda técnica del stub de 010).
3. US3 (pantalla).
4. Polish, PR a `sprint-4`.

---

## Notes

- Los pagos (018) usarán `Debt.applyPayment`; 017 no expone endpoints de pago.
- `NoOpDirectDebtPort` de 016 (deudas directas sin grupo) no se toca: no es parte de 017 (spec §Relación con otras especificaciones).
- `expensesCount` real: decidido en la revisión del 2026-11-05 (FR-027, T016/T023).
- El fix de `DebtPort` (T011/T012) se commitea aparte, antes que el resto (spec §Clarifications).
- El contrato de 010 no se edita: la referencia al 403 real queda en `contracts/group-expenses-api.md` de 017.
- Los commits y el PR los realiza el equipo (T054).
