# Research: Crear estados de deuda (017)

**Fecha**: 2026-11-05 | **Rama**: `feat-crear-estadoss-deudas` | **Spec**: [spec.md](./spec.md)

**Propósito**: registrar las decisiones técnicas y de dominio. No quedaron `NEEDS CLARIFICATION`. Las decisiones de producto están en las Clarifications de la spec. Las que propone esta research (D4, D5) quedan sujetas a la revisión del PR (constitución VI).

## D1 — Numeración `017`, una sola migración `V13`

- **Decisión**: carpeta `specs/017-debt-states`, migración `V13__create_group_expenses_and_debts.sql` con las tres tablas.
- **Rationale**: 001–014 y V1–V12 están en `main` (auditoría del 05/11 en `hecho-sprint.md` §8). Las tres tablas nacen juntas y se usan juntas: separarlas en `V13`/`V14` no aporta reversibilidad (Flyway no revierte) y consumiría el número que se reservó para los pagos (018 → `V14`).
- **Alternativas consideradas**: `V13` gasto y `V14` deudas. Se descartó porque obligaría a renumerar la reserva de 018 sin beneficio.
- **Renumeración (05/11)**: la carpeta original era `specs/015-debt-states` y los pagos tenían reservado 016. Se pasó a `017`/`018` porque `015-main-screen` (PR #34) y `016-delete-friend` ya usan esos números (`ESTADO_DEL_PROYECTO.md` §3). En esta rama, T001 y T002 vuelven a verificar la numeración antes de implementar. Las migraciones no cambian: `V13` para 017 y `V14` para 018.

## D2 — Tablas propias para el gasto de grupo, sin tocar `expenses`

- **Decisión**: nuevas tablas `group_expenses` y `group_expense_shares`. La tabla `expenses` (007/009) no se modifica.
- **Rationale**: `expenses` exige `category`, `payment_method` y `expense_type`, tiene cinco CHECK de recurrencia (V5) y una clave de idempotencia única por `user_id`, todo pensado para gastos personales. Agregarle `group_id` y pagador obligaría a relajar restricciones y a revisar cada consulta de 007/009 (`/api/expenses/recurring`, scheduler) para excluir los gastos de grupo. El riesgo de regresión es alto y viola el principio I (no refactorizar fuera de alcance).
- **Reutilización**: las reglas de entrada se toman tal cual de `expense/`: `ExpenseAmountNormalizer`, `ExpenseDateValidator` (con `deviceTimeZone`) y `ExpenseDescriptionNormalizer`, además de `user.Currency`/`CurrencyConversion`, la cabecera `Idempotency-Key` y `IdempotencyConflictException`. Los mensajes son idénticos a 007.
- **Alternativas consideradas**: (a) `ALTER TABLE expenses` con `group_id` nullable, descartada por lo anterior; (b) guardar los participantes como JSON en el gasto, descartada porque no se puede indexar ni referenciar desde las deudas.

## D3 — Paquete `groupexpense` y puerto en `groups/port`

- **Decisión**: un paquete nuevo `com.amano.backend.groupexpense` con el gasto, las partes, las deudas, el servicio y el controlador. La implementación real de `DebtPort` vive en `groups/port/LedgerDebtPort` y consulta el repositorio de deudas.
- **Rationale**: es el mismo patrón que `AcceptedFriendshipPort`, que vive en `groups/port/` y usa el repositorio de `friendship`. `groups` sigue dependiendo solo de su puerto, y `groupexpense` usa los repositorios y el `GroupPermissionChecker` de `groups` para validar membresía y casillas, como hace `GroupActivityService`.
- **Alternativas consideradas**: poner todo dentro de `groups/` (el paquete ya tiene 30 clases y mezclaría dos dominios); un paquete `debt/` separado del gasto (dos paquetes para tres entidades que se crean juntas).

## D4 — División en partes iguales y redondeo de centavos

- **Decisión**: se trabaja en centavos enteros. Con `T` = total en centavos y `n` = cantidad de participantes: `base = T / n` (división entera) y `resto = T mod n` (de 0 a n − 1).
  - **Si el pagador participa**: todas las partes valen `base` y la del pagador vale `base + resto`.
  - **Si el pagador no participa**: los primeros `resto` participantes, en el orden en que llegan en la solicitud, valen `base + 1`, y el resto `base`.
  - Si `base = 0` (total menor a un centavo por persona), se responde 400 en `amount`.
- **Rationale**:
  - Con el pagador participando, el centavo sobrante lo pone quien ya puso la plata: **ningún deudor paga más que la división exacta** (SC-002) y el resto no genera deuda. Es la regla más fácil de explicar ("el que pagó redondea").
  - Sin el pagador, el resto tiene que ir a algún deudor. Repartirlo de a un centavo minimiza la diferencia (como máximo 0,01). El orden de la solicitud lo hace determinista y la UI lo muestra en la vista previa antes de confirmar, así que nadie se sorprende.
  - La suma siempre es exacta (SC-001), y no se usa `BigDecimal.divide` con `RoundingMode`, que obliga a corregir el último centavo a mano.
- **Alternativas consideradas**: (a) resto siempre al primer participante (puede cargar hasta n − 1 centavos a una sola persona); (b) orden por id de usuario (determinista pero invisible para quien carga); (c) redondeo bancario por parte (la suma no cierra).
- **Frontend**: la vista previa replica la misma función (`splitEqually`), probada con los mismos casos que el backend.

## D5 — Estados de deuda y transiciones

- **Decisión**: `DebtStatus { PENDING, PARTIALLY_PAID, PAID }`, guardado en la columna `status` y siempre derivado de `paid_amount`:

| Estado | Condición |
|--------|-----------|
| `PENDING` | `paid_amount = 0` |
| `PARTIALLY_PAID` | `0 < paid_amount < amount` |
| `PAID` | `paid_amount = amount` |

  Transiciones válidas (solo hacia adelante):

```text
PENDING ──(pago parcial)──▶ PARTIALLY_PAID ──(pago del saldo)──▶ PAID
   └──────────────(pago total)──────────────────────────────────▲
```

- **Operación de dominio**: `Debt.applyPayment(BigDecimal amount)` valida monto > 0 y monto ≤ saldo, suma a `paid_amount` y recalcula el estado. Si la deuda está `PAID` o el pago supera el saldo, lanza una excepción y no cambia nada. En 017 solo la usan las pruebas. 018 la va a llamar al registrar un pago.
- **Integridad en la base**: `CHECK` que ata `status` a `paid_amount` (las tres filas de la tabla) y `CHECK (paid_amount BETWEEN 0 AND amount)`. Aunque alguien escriba la tabla a mano, no puede quedar `PAID` con saldo.
- **Rationale**:
  - Son los tres estados que nombra la tarea ("gestionar los estados de obligación de pago") y los que necesita "Registrar un pago" ("registrar el abono de una deuda pendiente y actualizar su estado").
  - `PARTIALLY_PAID` es necesario porque 018 puede admitir pagos parciales. Sin ese estado, una deuda con saldo parcial se vería igual que una sin pagos.
  - Guardar el estado (y no solo calcularlo) permite el índice parcial de D7 y consultas simples para el cálculo de deudas y los recordatorios. El CHECK impide que estado y monto se contradigan.
  - No hay transiciones hacia atrás porque revertir pagos no está en el backlog (principio VI: no inventar transiciones).
- **Alternativas consideradas**:
  - Solo `PENDING`/`PAID`: no representa pagos parciales.
  - Agregar `CANCELLED`: no hay tarea que cancele o condone deudas, y borrar o editar gastos está fuera de alcance. Si llega, se agrega con un `ALTER` del CHECK.
  - Estado de validación (`PENDING_VALIDATION`) para `payments_require_validation`: pertenece al pago (018), no a la deuda. Un pago sin validar no debería mover el saldo.

## D6 — Participantes: usuarios y perfiles simulados

- **Decisión**: cada parte y cada deuda guardan `participant_type` (`REGISTERED` | `SIMULATED`), `user_id` o `simulated_profile_id` (el que corresponda) y `participant_name` (copia del nombre visible). `simulated_profile_id` tiene FK con `ON DELETE SET NULL`.
- **Rationale**: 010 borra físicamente los perfiles simulados (`removeSimulatedProfile`). Con una FK estricta, borrar un perfil con deudas ya pagadas fallaría. Con `SET NULL` y la copia del nombre, el detalle del gasto sigue mostrando "Abuela", igual que el historial (patrón de preservación de 010/014, research 014 D4). Para usuarios, `group_members` nunca se borra (baja lógica), así que la FK a `users` es estricta.
- **Nombre visible**: `ActivityEventService.displayName(User)` para registrados (sin espacios sobrantes) y `SimulatedProfile.getName()` para simulados.
- **CHECK**: `REGISTERED` exige `user_id` no nulo y `simulated_profile_id` nulo. `SIMULATED` exige `user_id` nulo (el id del perfil puede quedar nulo después del borrado).

## D7 — `DebtPort` separado por tipo de participante

- **Decisión**: la interfaz pasa a tener dos métodos:
  - `hasPendingDebtsForUser(Long groupId, Long userId)`: deudas sin saldar donde el usuario es deudor **o** acreedor.
  - `hasPendingDebtsForSimulatedProfile(Long groupId, Long profileId)`: deudas sin saldar donde el perfil es deudor.
  
  Se elimina `hasPendingDebts(Long, Long)`. `GroupService.leaveGroup` y `GroupMemberService.removeMember` usan el primero, y `removeSimulatedProfile` usa el segundo.
- **Rationale**: hoy `removeSimulatedProfile` llama a `hasPendingDebts(groupId, profileId)` con el id de `simulated_profiles` en un parámetro llamado `userId`. Con el stub no se nota, pero con deudas reales un perfil con id 7 se bloquearía por las deudas del usuario 7 (o al revés). La corrección es necesaria para cumplir FR-018 y toca solo las tres llamadas existentes.
- **Consulta**: dos `exists` sobre `debts` filtrados por `group_id` y `status IN ('PENDING','PARTIALLY_PAID')`, apoyados en índices parciales `WHERE status <> 'PAID'` por deudor usuario, deudor perfil y acreedor.
- **Regla (override de 010)**: la Assumption de 010 habla de "saldos netos". 017 no compensa deudas cruzadas: cualquier deuda abierta bloquea (Clarifications del 2026-11-05). Motivos: calcular netos es la tarea "Cálculo de deudas" (fuera de alcance), y con saldos netos dos deudas cruzadas iguales dejarían irse a alguien con deudas registradas y sin pagar.
- **Implementación**: `LedgerDebtPort` (`@Component`) reemplaza a `NoOpDebtPort`, que se elimina. Las pruebas de 010 que mockean `DebtPort` se ajustan a los métodos nuevos. Los IT de 010 no necesitan cambios porque sin gastos no hay deudas.
- **Alternativas consideradas**: mantener un solo método con un parámetro de tipo (menos legible); dejar `NoOpDebtPort` para un perfil de pruebas (no lo usa nadie y confunde).

## D8 — Permisos con las casillas de `V9`

- **Decisión**: dos métodos nuevos en `GroupPermissionChecker`:
  - `canCreateExpenses(role, permissions)`: ADMIN siempre; MEMBER si `members_cannot_make_expenses = false`.
  - `canExpenseForOthers(role, permissions)`: ADMIN siempre; MEMBER si `members_can_generate_expenses_for_others = true`.
  
  El backend decide siempre con estos métodos. La pantalla no necesita campos nuevos: `GET /api/groups/{id}` ya devuelve `currentUserRole` y `permissions` (las casillas), y el frontend replica la misma regla para mostrar u ocultar "Nuevo gasto" y fijar el pagador. Si la UI se equivoca, la API responde 403 igual.
- **`expensesCount`** (decisión de la revisión del 2026-11-05): `GroupDetailResponse` lo informaba siempre en 0. Desde 017 devuelve la cantidad real de filas de `group_expenses` del grupo (FR-027). La forma del contrato de 010 no cambia. Se resuelve con una consulta JPQL en `GroupRepository` (`countGroupExpenses`), sin agregar dependencias al constructor de `GroupService`.
- **Rationale**: la constitución IX y 010 exigen un único punto de decisión. Las casillas existen desde 010 con valor por defecto `false`, así que **por defecto todos los MEMBER pueden crear gastos, pero solo a su nombre** como pagador. Es la interpretación literal de los nombres de las columnas y fue confirmada en la clarificación.
- **"Para otros"**: se interpreta como "elegir un pagador distinto de uno mismo", en línea con la etiqueta de la UI de 010 ("Pueden generar gastos en nombre de otras personas"). Incluir a otros como participantes es la esencia de un gasto compartido y no se restringe.

## D9 — Idempotencia igual que 007

- **Decisión**: cabecera obligatoria `Idempotency-Key` (UUID) y `UNIQUE (created_by_user_id, idempotency_key)` en `group_expenses`. La misma clave con la misma solicitud devuelve 200 con el gasto existente; con datos distintos responde 422 `IDEMPOTENCY_KEY_REUSED`. La carrera se resuelve igual que en `ExpenseService.resolveConcurrentRegistration`.
- **Rationale**: el formulario de 007 ya genera la clave y la reutiliza al reintentar. Un doble envío sin idempotencia duplicaría deudas, que es peor que duplicar un gasto personal.

## D10 — Contrato de la API

- **Decisión**: `POST /api/groups/{id}/expenses` (201/200) y `GET /api/groups/{id}/expenses/{expenseId}` (200). Bajo `/api/groups/{id}` como el resto de 010, en un controlador nuevo (`GroupExpenseController`). Montos como texto decimal (`"3333.33"`), como 007 y 014.
- **Rationale**: el detalle es necesario para la pantalla posterior al alta y para el enlace del historial (FR-020/FR-024). No hay listado (fuera de alcance).
- **Errores**: 400 `FieldValidationException` con `properties.fields` (`amount`, `currency`, `date`, `description`, `payerUserId`, `participants`, `idempotencyKey`), con los nombres de campo de 007; 403 `GroupPermissionDeniedException`; 404 `GroupNotFoundException` neutral; 422 de idempotencia. Todos ya existen en `ProblemDetailExceptionHandler`.

## D11 — Historial

- **Decisión**: `GroupExpenseService.create` llama a `activityEventService.recordExpenseCreated(groupId, callerId, new ExpenseActivityDetails(id, amount, currency, description))` dentro de su transacción.
- **Rationale**: es el enganche de 014 (FR-006 de 014). El actor es quien registra, aunque el pagador sea otro: el historial dice quién hizo la acción. La frase ya existe en `activityDescriptions.js`. 017 solo agrega el enlace "Ver gasto" cuando hay `expenseId`.

## D12 — Pantalla

- **Rutas nuevas**: `/groups/:id/expenses/new` (`GroupExpenseFormView`) y `/groups/:id/expenses/:expenseId` (`GroupExpenseDetailView`), protegidas como las de 010.
- **Formulario**: reutiliza el patrón de `NewExpensePage`/`ExpenseForm` de 007 (monto con notación argentina convertido a canónico, fecha, descripción, `Idempotency-Key` con `crypto.randomUUID()` conservada entre reintentos). Los participantes se toman de `getMembers(groupId)` (miembros activos y perfiles simulados).
- **Cliente**: `groupExpensesClient.js` con `createGroupExpense` y `getGroupExpense`, con el mismo manejo de errores que `groupsClient` (5xx como `network`, 401 aparte).
- **Textos**: "Nuevo gasto", "¿Quién pagó?", "¿Entre quiénes se divide?", "Le corresponde $ X a cada uno", estados "Pendiente", "Pago parcial · faltan $ X" y "Pagada".

## D13 — Estrategia de pruebas

- **Backend unitario (Mockito)**: `EqualSplitTest` (exacto, resto con y sin pagador, mínimo, orden), `DebtTest` (estados y transiciones de `applyPayment`, incluidos los rechazos), `GroupExpenseServiceTest` (validaciones, casillas, idempotencia, deudas generadas, evento registrado), `LedgerDebtPortIT` (Testcontainers: la regla vive en las consultas de `DebtRepository`, así que se prueba contra PostgreSQL real, incluidos los ids coincidentes forzados con `setval`), `GroupPermissionCheckerTest` (dos métodos nuevos) y ajustes de `GroupMemberServiceTest`, `MemberRemovalServiceTest` y `GroupLeaveServiceTest` a la nueva firma del puerto.
- **Backend integración (Testcontainers)**: `GroupExpenseMigrationIT` (CHECK de estado, partes y tipo de participante; `SET NULL` al borrar un perfil), `GroupExpenseControllerIT` (contrato completo: 201/200/400/403/404/422, historial) y `GroupDebtBlockingIT` (bloqueos de US2, incluido el caso de ids coincidentes).
- **Frontend (Vitest)**: `splitEqually.test.js`, `groupExpensesClient.test.js`, `GroupExpenseFormView.test.jsx`, `GroupExpenseDetailView.test.jsx` y ajustes en `GroupInfoView.test.jsx` y `GroupHistoryView.test.jsx`.
- Cobertura ≥ 85 % en ambos módulos (constitución V).
