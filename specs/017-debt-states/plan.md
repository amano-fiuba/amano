# Implementation Plan: Crear estados de deuda

**Branch**: `feat-crear-estadoss-deudas` | **Date**: 2026-11-05 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/017-debt-states/spec.md`

## Summary

La tarea "Crear estados de deuda" (Sprint 4, 2 SP) necesita deudas, y hoy no existen gastos de grupo. Por eso 017 entrega tres historias:

- **US1**: un gasto de grupo mínimo (`POST /api/groups/{id}/expenses`) en tablas propias (`V13`). Se divide en partes iguales en centavos: el resto lo absorbe el pagador si participa, o se reparte de a un centavo en el orden de la solicitud. Genera una deuda `PENDING` por cada participante distinto del pagador y registra `EXPENSE_CREATED` con el enganche de 014. Los estados `PENDING`/`PARTIALLY_PAID`/`PAID` quedan atados al monto pagado por un CHECK, y `Debt.applyPayment` queda lista para 018.
- **US2**: `LedgerDebtPort` reemplaza a `NoOpDebtPort`, y `DebtPort` se separa por tipo de participante para corregir la confusión de ids de 010. Abandonar o quitar a alguien con deudas sin saldar (como deudor o acreedor) lanza `PendingDebtsException`.
- **US3**: formulario "Nuevo gasto" con vista previa de partes, detalle del gasto con el estado de cada deuda y enlace desde el historial.

Las reglas de entrada, la idempotencia y los errores se reutilizan de 007 y 010. No se agregan dependencias.

## Technical Context

**Language/Version**: Java 25 (backend); JavaScript con React 19.2 (frontend)

**Primary Dependencies**: Spring Boot 4.1.1 (Web MVC, Data JPA, Security, Jackson 3), Flyway; React 19.2, react-router 8.4, Vite 8.3. **Sin dependencias nuevas.**

**Storage**: PostgreSQL. Una migración: `V13__create_group_expenses_and_debts.sql` (3 tablas, 6 índices, solo `CREATE`). `ddl-auto=validate`.

**Testing**: JUnit 5 + Mockito (`EqualSplitTest`, `DebtTest`, `GroupExpenseServiceTest`, `GroupPermissionCheckerTest` y ajustes de 010), Spring Boot Test + Testcontainers (`GroupExpenseMigrationIT`, `GroupExpenseControllerIT`, `LedgerDebtPortIT`, `GroupDebtBlockingIT`), Vitest + Testing Library (`splitEqually`, `debtStatusLabels`, `expensePermissions`, `groupExpensesClient`, los cuatro componentes nuevos, las dos vistas nuevas y ajustes de `GroupInfoView`, `GroupHistoryView` y `App`). Umbrales: JaCoCo `LINE COVEREDRATIO ≥ 0.85` (`backend/pom.xml`) y Vitest V8 `lines`/`statements ≥ 85` (`frontend/vite.config.js`). Ninguno se baja.

**Target Platform**: Servidor Linux vía Docker Compose; navegador web desktop y móvil.

**Project Type**: Aplicación web monorepo (backend REST + frontend SPA).

**Performance Goals**: alta de gasto en una transacción con n + (n − 1) + 2 inserts. `DebtPort` resuelto con un `exists` sobre índice parcial. SC-004 (< 1 min de punta a punta para la persona).

**Constraints**: no tocar `expenses` ni 007/009; sin `UPDATE`/`DELETE` en la migración; estados coherentes por CHECK; mensajes de 007 y 010 sin cambios; voseo; cobertura ≥ 85 %.

**Scale/Scope**: 1 migración; backend con 1 paquete nuevo (~14 clases), 1 clase nueva en `groups/port`, 1 eliminada y 4 modificadas; frontend con 10 archivos nuevos (más sus pruebas) y 4 modificados.

**Punto de partida**: la rama `feat-crear-estadoss-deudas` sale de `sprint-4` (`223a768`, con 015-main-screen) más el merge de `feat/delete-friend`. No tiene nada de 017: todo se implementa desde cero. El frontend de US3 que existió en otra copia de trabajo se perdió, y se rediseña en §Diseño del frontend.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| # | Principio | Estado | Evidencia |
|---|-----------|--------|-----------|
| I | Arquitectura y separación de responsabilidades | PASS | Paquete nuevo `groupexpense`. El puerto sigue el patrón de `AcceptedFriendshipPort`. Se reutilizan normalizadores de 007, `GroupPermissionChecker`, `ActivityEventService` y el handler de errores (research.md D2/D3). Los cambios en `groups/` se limitan al puerto y a sus tres llamadas |
| II | Desarrollo guiado por especificaciones | PASS | `specs/017-debt-states/` completa antes del código. La referencia rota de 014 a `ESTADO_ACTUAL.md` se aclara en la spec |
| III | Entrega incremental por historia | PASS | US1 → US2 → US3, con checkpoints. US2 depende de US1 (necesita deudas) y US3 depende de US1 (necesita el contrato) |
| IV | Alcance controlado | PASS | El gasto de grupo mínimo fue pedido explícitamente por el responsable. Sección "Fuera de alcance" en la spec. Sin listados, pagos ni división no igualitaria |
| VI | Integridad de las reglas de negocio | PASS con nota | Redondeo (D4) y estados/transiciones (D5) son propuestas delegadas por el responsable y quedan documentadas para revisión en el PR. Bloqueo por deudas (deudor o acreedor) y casillas de V9: confirmados en la clarificación del 2026-11-05 |
| V | Calidad, pruebas y cobertura | PASS | Unitarias, integración con Testcontainers (incluida la migración) y Vitest; umbral 85 % |
| VII | Contratos REST y compatibilidad | PASS | `contracts/group-expenses-api.md`. Endpoints nuevos; los de 010 cambian solo de comportamiento (403 ya documentado). `GET /api/groups/{id}` no cambia |
| VIII | Validación y manejo de errores | PASS | 400 por campo, 403/404/422 existentes; transacción única sin estados parciales |
| IX | Seguridad y control de acceso | PASS | Membresía activa y casillas decididas en `GroupPermissionChecker`; el creador es siempre el usuario de la sesión; la UI no sustituye al backend |
| X | Código mantenible y convenciones | PASS | Nombres en inglés, `*Test`/`*IT`, `*.test.js(x)`. Se elimina `NoOpDebtPort` en lugar de dejarlo muerto |
| XI | Entorno reproducible | PASS | Sin propiedades ni dependencias nuevas. `V13` corre en el arranque y en los IT |
| XII | Revisión por pares | PENDIENTE (posterior) | PR hacia `sprint-4` aún no abierto |
| XIII | Commits y trazabilidad | PENDIENTE (posterior) | Los commits los realiza el equipo |

**Gate resultado**: PASS. Re-check post-diseño: PASS (data-model y contrato no agregan desvíos).

## Project Structure

### Documentation (this feature)

```text
specs/017-debt-states/
├── spec.md
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/group-expenses-api.md
├── checklists/requirements.md
└── tasks.md
```

### Source Code (repository root)

```text
backend/src/main/resources/db/migration/
└── V13__create_group_expenses_and_debts.sql     # A

backend/src/main/java/com/amano/backend/groupexpense/
├── GroupExpense.java                  # A: entidad
├── GroupExpenseShare.java             # A: entidad
├── Debt.java                          # A: entidad + applyPayment
├── DebtStatus.java                    # A: enum + of(amount, paid)
├── ParticipantType.java               # A: enum
├── EqualSplit.java                    # A: división en centavos (D4)
├── GroupExpenseRepository.java        # A
├── GroupExpenseShareRepository.java   # A
├── DebtRepository.java                # A: exists de D7
├── GroupExpenseService.java           # A
├── GroupExpenseController.java        # A
├── CreateGroupExpenseRequest.java     # A
└── GroupExpenseResponse.java          # A

backend/src/main/java/com/amano/backend/groups/
├── port/DebtPort.java                 # M: dos métodos por tipo
├── port/LedgerDebtPort.java           # A
├── port/NoOpDebtPort.java             # D (US2; antes M por el fix T012)
├── GroupRepository.java               # M: countGroupExpenses (FR-027)
├── dto/GroupDetailResponse.java       # M: Javadoc de expensesCount
├── GroupPermissionChecker.java        # M: canCreateExpenses, canExpenseForOthers
├── GroupService.java                  # M: leaveGroup → hasPendingDebtsForUser; expensesCount real
└── GroupMemberService.java            # M: removeMember / removeSimulatedProfile

backend/src/test/java/com/amano/backend/
├── groupexpense/EqualSplitTest.java, DebtTest.java, GroupExpenseServiceTest.java, GroupExpenseControllerTest.java  # A
├── groupexpense/GroupExpenseMigrationIT.java, GroupExpenseControllerIT.java             # A
├── groups/port/LedgerDebtPortIT.java, groups/GroupDebtBlockingIT.java                 # A
└── groups/(GroupPermissionCheckerTest, GroupMemberServiceTest, MemberRemovalServiceTest, GroupLeaveServiceTest, GroupServiceTest)  # M/A

frontend/src/
├── api/groupExpensesClient.js (+ .test.js)                          # A
├── pages/groups/splitEqually.js (+ .test.js)                        # A: regla D4 en centavos
├── pages/groups/debtStatusLabels.js (+ .test.js)                    # A: etiquetas; el test cubre también permisos y claves
├── pages/groups/expensePermissions.js                               # A: rol + casillas → qué permite la UI
├── pages/groups/participantKeys.js                                  # A: claves u:/s: (pruebas en debtStatusLabels.test.js)
├── pages/groups/GroupExpenseFormView.jsx (+ .test.jsx)              # A: /groups/:id/expenses/new
├── pages/groups/GroupExpenseDetailView.jsx (+ .test.jsx)            # A: /groups/:id/expenses/:expenseId
├── components/groups/DebtStatusBadge.jsx                            # A
├── components/groups/ExpenseSharePreview.jsx                        # A
├── components/groups/ExpenseDebtList.jsx (+ .test.jsx)              # A: el test cubre los cuatro componentes
├── components/groups/GroupExpensesCard.jsx                          # A: tarjeta "Gastos" del grupo
├── pages/groups/GroupInfoView.jsx (+ .test.jsx)                     # M: monta GroupExpensesCard
├── pages/groups/GroupHistoryView.jsx (+ .test.jsx)                  # M: "Ver gasto" y ?filter=
├── App.jsx (+ App.test.jsx)                                         # M: dos rutas protegidas
└── index.scss                                                       # M: estilos .group-expense*
```

**Structure Decision**: aplicación web (backend + frontend) existente. Se agrega un paquete de backend (`groupexpense`) por ser un dominio nuevo. En el frontend, las vistas van en `pages/groups/` y las piezas reutilizables en `components/groups/`, como en 010.

## Flujo del backend

### Alta de un gasto (`POST /api/groups/{id}/expenses`, US1)

`GroupExpenseController.create` toma el usuario de la sesión (`currentUserId()`, patrón de 010) y la cabecera `Idempotency-Key`, y delega en `GroupExpenseService.create`, que es `@Transactional` y sigue este orden:

1. **Membresía**: `GroupMemberRepository` confirma que quien registra es miembro activo. Si no, `GroupNotFoundException` (404 neutral de 010).
2. **Permisos**: `GroupPermissionChecker.canCreateExpenses(role, permissions)` y, si `payerUserId` ≠ quien registra, `canExpenseForOthers(role, permissions)`. Si alguno falla, 403 con los mensajes del contrato. El ADMIN siempre pasa.
3. **Entrada**: `ExpenseAmountNormalizer`, `ExpenseDateValidator` (con `deviceTimeZone`) y `ExpenseDescriptionNormalizer` de 007, más la moneda. Todos los errores se juntan en una `FieldValidationException` (400 con `fields`).
4. **Pagador y participantes**: el pagador tiene que ser miembro registrado activo. Cada participante tiene que ser miembro activo o perfil simulado de **este** grupo, sin repetidos, y tiene que haber al menos uno distinto del pagador. Los nombres visibles se resuelven en este paso.
5. **Idempotencia**: busca `(created_by_user_id, idempotency_key)`. Si existe y el cuerpo normalizado es igual, devuelve ese gasto con 200. Si es distinto, `IdempotencyConflictException` (422, de 007). Va después de validar porque la comparación necesita el cuerpo normalizado (ajuste de la implementación, 2026-11-05).
6. **División**: `EqualSplit.split(total, n, payerIndex)` devuelve las partes en centavos (D4). Si `base = 0`, 400 en `amount` (se detecta en el paso 3 con `EqualSplit.fits`). La suma de las partes es siempre el total.
7. **Persistencia**: guarda `GroupExpense`, una `GroupExpenseShare` por participante (con `position` y nombre copiado) y una `Debt` `PENDING` por cada parte que no es del pagador (`creditor = payer`).
8. **Historial**: `ActivityEventService.recordExpenseCreated(groupId, actorUserId, ExpenseActivityDetails)` (con `expenseId`, `amount`, `currency` y `description`). Es `MANDATORY`, así que corre en la misma transacción. Si falla, se revierte todo (FR-013).
9. **Carrera de idempotencia**: si dos envíos con la misma clave llegan juntos, el `UNIQUE` de `group_expenses` hace fallar al segundo. `GroupExpenseController` captura `DataIntegrityViolationException` y llama a `resolveConcurrentRegistration`, que relee el ganador, igual que en 007.

La respuesta (`GroupExpenseResponse`) arma `shares` y `debts` ordenados por `position`, con `outstanding = amount − paidAmount`.

### Bloqueo de bajas (`LedgerDebtPort`, US2)

```text
GroupService.leaveGroup(groupId, userId)
GroupMemberService.removeMember(groupId, userId)            ──▶ DebtPort.hasPendingDebtsForUser(groupId, userId)
GroupMemberService.removeSimulatedProfile(groupId, profileId) ─▶ DebtPort.hasPendingDebtsForSimulatedProfile(groupId, profileId)
                                                                       │
                                                         LedgerDebtPort (@Component)
                                                                       │
                                     DebtRepository.existsOpenForUser / existsOpenForSimulatedProfile
                                                                       │
                                            true ──▶ throw PendingDebtsException (403 PENDING_DEBTS)
```

- **Paso previo (fix aislado, D7)**: hoy `DebtPort` tiene un único `hasPendingDebts(groupId, id)`, y `removeSimulatedProfile` le pasa el id del perfil como si fuera un `userId`. Primero se separa en dos métodos, `NoOpDebtPort` implementa los dos devolviendo `false` y se ajustan las tres llamadas y sus mocks. Esto se commitea solo, antes de US1.
- **`existsOpenForUser(groupId, userId)`**: `exists` sobre `debts` con `group_id = :groupId AND status <> 'PAID' AND (debtor_user_id = :userId OR creditor_user_id = :userId)`. Usa los índices parciales `ix_debts_open_debtor_user` e `ix_debts_open_creditor`.
- **`existsOpenForSimulatedProfile(groupId, profileId)`**: lo mismo con `debtor_simulated_profile_id = :profileId` (`ix_debts_open_debtor_profile`). Un perfil nunca es acreedor.
- **Dónde se lanza la excepción**: en los servicios de `groups/`, que ya tienen el `if (debtPort…) throw new PendingDebtsException(…)` con los mensajes de 010. `LedgerDebtPort` solo responde `true`/`false`, igual que `AcceptedFriendshipPort`.
- **Orden**: la consulta va **antes** de cualquier escritura (antes de marcar la baja o borrar el perfil), así que si se bloquea no cambia nada. Como `NoOpDebtPort` se borra, Spring inyecta `LedgerDebtPort` sin necesidad de `@Primary`.
- El handler existente (`ProblemDetailExceptionHandler`) ya traduce `PendingDebtsException` a 403 con `code: PENDING_DEBTS`. No se toca.

## Diseño del frontend (US3)

### Rutas y navegación

| Ruta | Componente | Protección | Desde dónde se llega |
|------|------------|-----------|----------------------|
| `/groups/:id/expenses/new` | `GroupExpenseFormView` | `ProtectedRoute` | Botón "Nuevo gasto" de `GroupExpensesCard` |
| `/groups/:id/expenses/:expenseId` | `GroupExpenseDetailView` | `ProtectedRoute` | Después de guardar (201/200) y desde "Ver gasto" del historial |
| `/groups/:id/history?filter=EXPENSES` | `GroupHistoryView` (M) | ya protegida | "Ver gastos en el historial" de `GroupExpensesCard` |

Las rutas se agregan en `App.jsx` junto a `/groups/:id/history`, con el mismo envoltorio que las demás rutas de grupo.

### Árbol de componentes

```text
GroupInfoView (M)
└── GroupExpensesCard            props: groupId, group (currentUserRole, permissions, expensesCount), currentUserId
    ├── "N gastos registrados"
    ├── <Link> Nuevo gasto         (solo si canCreateExpenses)
    └── <Link> Ver gastos en el historial

GroupExpenseFormView             useParams(id) · useAuth() · useSnackbar() · useNavigate()
├── campos: monto, moneda, fecha, descripción      (reglas de validation/expenseValidation.js)
├── SelectField "¿Quién pagó?"                      (fijo si !canExpenseForOthers)
├── lista de participantes con checkbox             (miembros + perfiles simulados de getMembers)
├── ExpenseSharePreview          props: amount, participants, payerKey
└── SubmitButton "Guardar gasto"

GroupExpenseDetailView           useParams(id, expenseId) · useAuth()
├── encabezado: descripción, total, fecha, "Pagó {payer.name}"
├── tabla de partes (en orden de position; "(pagó)" junto al pagador)
└── ExpenseDebtList              props: debts, currency
    └── DebtStatusBadge          props: status, outstanding, currency
```

### Módulos sin interfaz

| Módulo | Firma | Comportamiento |
|--------|-------|----------------|
| `splitEqually.js` | `splitEqually(amountCanonical, participantKeys, payerKey) → [{ key, cents, amount }] \| null` | Pasa a centavos enteros y aplica D4 igual que `EqualSplit`. Devuelve `null` si el monto no es válido o `base = 0`, y la vista previa no se muestra |
| `debtStatusLabels.js` | `debtStatusLabel(status, outstanding) → string` y `debtStatusModifier(status)` | `PENDING` → "Pendiente"; `PARTIALLY_PAID` → "Pago parcial · faltan $ X" (con `formatAmount` de 007); `PAID` → "Pagada"; desconocido → "Estado desconocido" |
| `expensePermissions.js` | `expensePermissions(group) → { canCreateExpenses, canExpenseForOthers }` | ADMIN: los dos en `true`. MEMBER: `!permissions.membersCannotMakeExpenses` y `permissions.membersCanGenerateExpensesForOthers`. Es solo para la UI: el backend decide igual (FR-007, D8) |
| `participantKeys.js` | `participantKey(member)`, `toParticipant(key)`, `userIdOf(key)` | Claves `u:{userId}` / `s:{profileId}` (D7) y su traducción al cuerpo del `POST` |
| `groupExpensesClient.js` | `createGroupExpense(groupId, payload, idempotencyKey)` y `getGroupExpense(groupId, expenseId)` | Convención de `expenseClient`: nunca lanza. Devuelve `{ ok: true, expense, created }` (`created` = 201), o `{ ok: false, status, detail, fields }`, o `{ ok: false, network: true }` para 5xx y fallos de red. Exporta `GROUP_EXPENSE_NETWORK_ERROR_MESSAGE` y `GROUP_EXPENSE_LOAD_ERROR_MESSAGE` |

### Componentes

**`GroupExpensesCard`** (en `components/groups/`)
- Muestra "Todavía no hay gastos" cuando `expensesCount = 0`, y si no, "1 gasto registrado" o "N gastos registrados".
- "Nuevo gasto" es un `<Link>` a `/groups/:id/expenses/new`, visible solo si `canCreateExpenses`.
- "Ver gastos en el historial" lleva a `/groups/:id/history?filter=EXPENSES`.
- Va entre la tarjeta de miembros y la de acciones de `GroupInfoView`. No hace pedidos propios: usa el `group` que ya cargó la vista.

**`GroupExpenseFormView`**
- **Carga**: en paralelo `getGroup(id)` (rol, casillas y nombre) y `getMembers(id)` (filas `GroupMemberView`: `userId` o `simulatedProfileId`, `name`, `role`). Mientras carga, muestra "Cargando…". Un 404 muestra el mensaje neutral de 010, un 401 llama a `setUser(null)` y un error de red ofrece "Reintentar".
- **Sin permiso**: si `!canCreateExpenses`, no muestra el formulario. Muestra "No podés registrar gastos en este grupo" y un enlace de vuelta al grupo (cubre el acceso por URL directa).
- **Estado**: `react-hook-form` como `ExpenseForm` para monto, moneda, fecha y descripción. Aparte: `payerKey` (por defecto, el usuario de la sesión), `selectedKeys` en el orden de la lista (por defecto, todos) e `idempotencyKey`.
- **Claves de participante**: `u:{userId}` y `s:{profileId}`, para que un usuario y un perfil con el mismo id no se confundan (D7).
- **Vista previa**: se recalcula con `useWatch` sobre el monto, `payerKey` y `selectedKeys` usando `splitEqually`. El orden de envío es el de la lista en pantalla, que es el mismo que usa la vista previa.
- **Envío**: genera la `Idempotency-Key` con `crypto.randomUUID()` la primera vez y la conserva en los reintentos tras un error de red. La descarta en un 400 o un 422, como `ExpenseForm`.
  - 201/200: `showSnackbar('Gasto registrado')` (`const showSnackbar = useSnackbar()`, como en `AddMemberView`) y `navigate('/groups/:id/expenses/:expenseId')`.
  - 400: `setError` por campo (`amount`, `date`, `description`, `currency`, `payerUserId`, `participants`), sin perder lo cargado.
  - 403: el `detail` del servidor arriba del formulario.
  - 401: `setUser(null)`.
  - Red o 5xx: `GROUP_EXPENSE_NETWORK_ERROR_MESSAGE`.
- **Botón**: deshabilitado mientras envía o si no hay al menos un participante distinto del pagador. El mensaje del backend se muestra igual si llega.

**`ExpenseSharePreview`**
- Recibe la salida de `splitEqually` y los nombres. Muestra una fila por participante con su parte y marca "(pagó)" en el pagador.
- Si el resto no es cero, agrega una nota: "Quien pagó absorbe los centavos que sobran" o "Los centavos que sobran se reparten en el orden de la lista".
- Si no hay división válida, no muestra filas: muestra "Completá el monto y elegí participantes para ver la división".

**`GroupExpenseDetailView`**
- Carga `getGroupExpense(id, expenseId)`. Mientras carga, "Cargando…". Un 404 muestra "No existe el gasto solicitado o no sos miembro del grupo", un 401 llama a `setUser(null)` y un error de red ofrece "Reintentar".
- Muestra el encabezado, la tabla de partes y `ExpenseDebtList`. Un enlace "Volver al grupo" lleva a `/groups/:id`.
- Los nombres son las copias guardadas, así que se ven aunque el perfil simulado ya no exista (`simulatedProfileId = null`).

**`ExpenseDebtList`**
- Una fila por deuda, en orden de `position`: "{debtor.name} le debe a {creditor.name}", el monto y `DebtStatusBadge`.
- Si no hay deudas (no puede pasar con FR-004, pero se cubre), muestra "Este gasto no generó deudas".
- No tiene acciones: registrar un pago es de 018. Está pensado para que "Ver deudas pendientes" (S5) lo reutilice con otra fuente de datos.

**`DebtStatusBadge`**
- Un `<span>` con `debtStatusLabel(...)` y la clase `group-expense__status--pending`, `--partial` o `--paid`. El texto describe el estado completo, así que no depende solo del color (accesibilidad).

### Cambios en vistas existentes

- **`GroupInfoView`**: monta `GroupExpensesCard` con el `group` ya cargado. No hace pedidos nuevos.
- **`GroupHistoryView`**:
  - Lee `?filter=` con `useSearchParams` para el filtro inicial. Si el valor no está en `ACTIVITY_FILTERS`, usa "Toda la actividad". Al cambiar el filtro, actualiza el parámetro.
  - En las entradas `EXPENSE_CREATED` con `details.expenseId`, agrega el enlace "Ver gasto" a `/groups/:id/expenses/:expenseId`.
  - `activityDescriptions.js` no cambia: la frase ya existe.
- **`App.jsx`**: las dos rutas nuevas.
- **`index.scss`**: bloque `.group-expense` (formulario, vista previa, tabla de partes, lista de deudas y badges) con las variables y clases de `.group-card` que ya existen. Sin colores nuevos fuera de la paleta.

### Pruebas del frontend

| Archivo | Casos principales |
|---------|-------------------|
| `splitEqually.test.js` | Los mismos casos que `EqualSplitTest`: exacto, resto al pagador, resto en orden sin pagador, `base = 0` → `null`, montos grandes |
| `debtStatusLabels.test.js` | Las tres etiquetas, saldo formateado, estado desconocido |
| `expensePermissions.test.js` | ADMIN; MEMBER con cada combinación de las dos casillas; `group` nulo |
| `groupExpensesClient.test.js` | Cabecera `Idempotency-Key`, cuerpo, `credentials: 'include'`, 201 vs 200 (`created`), 400 con `fields`, 403, 404, 401, 5xx y red |
| `GroupExpensesCard.test.jsx` | Textos de cantidad, botón visible u oculto según permisos, enlace con `?filter=EXPENSES` |
| `ExpenseSharePreview.test.jsx` | Filas y "(pagó)", nota de resto en los dos casos, estado vacío |
| `ExpenseDebtList.test.jsx` / `DebtStatusBadge.test.jsx` | Orden, frase deudor → acreedor, tres estados con su clase, lista vacía |
| `GroupExpenseFormView.test.jsx` | Carga, pagador preseleccionado, todos marcados, vista previa reactiva, pagador fijo, sin permiso, 400 por campo, 403, 401, red con reintento y misma clave, navegación y snackbar en 201 |
| `GroupExpenseDetailView.test.jsx` | Encabezado, partes en orden, deudas con los tres estados, perfil borrado, 404, 401, red con reintento |
| `GroupInfoView.test.jsx`, `GroupHistoryView.test.jsx`, `App.test.jsx` | La tarjeta aparece; "Ver gasto" con y sin `expenseId`; `?filter=EXPENSES` aplicado y valor inválido ignorado; rutas protegidas |

Los mocks siguen el patrón de las pruebas de 010/014: `vi.mock` del módulo cliente, no del `fetch` global (salvo en `groupExpensesClient.test.js`).

## Complexity Tracking

| Elemento | Detalle | Justificación / acción |
|----------|---------|------------------------|
| Gasto de grupo dentro de una tarea de "estados de deuda" | 017 crea el gasto mínimo además de las deudas | No hay otra fuente de deudas. Pedido explícito del responsable; alcance mínimo y lista de fuera de alcance (spec) |
| Cambio de firma de `DebtPort` (010) | Se reemplaza un método por dos | Corrige la confusión de ids de `removeSimulatedProfile` (research.md D7); toca 3 llamadas y sus mocks |
| `applyPayment` sin consumidor | No hay endpoint de pago en 017 | Es el "modelo preparado" que pide la tarea; probado por `DebtTest` y por el CHECK en `GroupExpenseMigrationIT`. Lo usa 018 |
| Estado guardado y derivado a la vez | `status` y `paid_amount` | El CHECK impide contradicciones; el estado guardado permite índices parciales (research.md D5) |
| Regla de redondeo replicada en el frontend | `EqualSplit` y `splitEqually` | La vista previa tiene que coincidir con el backend; mismos casos de prueba en ambos |
