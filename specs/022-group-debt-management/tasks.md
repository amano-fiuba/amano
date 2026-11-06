# Tasks: Group Debt Management

**Input**: Feature specification from `/specs/022-group-debt-management/spec.md`

**Prerequisites**: plan.md (required), spec.md (required for user stories), research.md, data-model.md, contracts/

**Organization**: Tasks are grouped by user story to enable independent implementation and testing of each story.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (e.g., [US1], [US2], [US3])

## Path Conventions

- **Backend**: `backend/src/main/java/com/amano/backend/...`, pruebas en `backend/src/test/java/...`
- **Migraciones**: `backend/src/main/resources/db/migration/...`
- **Entidades**: `backend/src/main/java/com/amano/backend/groupexpense/...`
- **Servicios**: `backend/src/main/java/com/amano/backend/groupexpense/...`

## Constitution Check

- Reutilizar entidades y módulos existentes (usuarios, amistades, grupos, miembros, roles, gastos, categorías, monedas, historial de actividad)
- El código MUST respetar la arquitectura y el stack existentes (Java Spring Boot, PostgreSQL con JPA, Flyway, Maven)
- Las migraciones ya integradas no deben editarse: los cambios se introducen con una migración nueva
- Los commits MUST seguir Conventional Commits

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Project initialization and basic structure

- [ ] T001 Create project structure per implementation plan (feat/group-debt-management branch from sprint-1)
- [ ] T002 Verificar la línea base en verde antes de cambios: `cd backend && mvn -B verify` y `cd frontend && npm ci && npm run lint && npm run test:coverage && npm run build`
- [ ] T003 Configurar esquema de base de datos y migraciones iniciales (Flyway); migraciones ya integradas no deben editarse
- [ ] T004 [P] Configurar lombok y herramientas de mapeo objeto-relacional (si aplica al stack del proyecto)
- [ ] T004 [P] Integrar el enum Currency (ARS, USD, EUR) existente en `com.amano.backend.user.Currency`; no crear nuevos enums de moneda

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Core infrastructure that MUST be complete before ANY user story can be implemented

**⚠️ CRITICAL**: No user story work can begin until this phase is complete (Constitución III)

- [ ] T005 [P] [US3] Crear la entidad Deuda con estado máquina completa (Pendiente, Pago informado, Pagado, Verificado, Vencido) en `Debt.java`; extender enum `DebtStatus` desde los 3 estados actuales (PENDING/PARTIALLY_PAID/PAID) a 5 estados
- [ ] T[Subtarea de T005] Añadir campo `vencidoAt` al entity Deuda (opcional, solo para gastos recurrentes) y añadir lógica de transición automática a Vencido para gastos recurrentes (con currentDate > expenseDate + vencimientoDias)
- [ ] T006 [P] [US3] Implementar algoritmo voraz de liquidación de deudas que garantiza como máximo n-1 transferencias; documento que el mínimo absoluto es NP-difícil
- [ ] T[Subtarea de T006] Implementar `computeNetBalances(group)` que derive saldos netos de todos los miembros (incluyendo personas simuladas) y `generateLiquidationProposals(balances)` con algoritmo voraz: encontrar máximo acreedor y máximo deudor, transferir mínimo, repetir hasta cero; documentar garantía: transferencias ≤ (miembros con saldo ≠ 0) - 1
- [ ] T007 [P] [US5] Implementar máquina de estados explícita de deudas con transiciones válidas según casilla "Pagos requiere validación" del grupo
- [ ] T[Subtarea de T007] Implementar máquina de estados explícita de deudas con transiciones válidas según casilla "Pagos requiere validación" del grupo: PENDIENTE → PAGO_INFORMADO → PAGADO (si no requiere validación) / VERIFICADO (si requiere validación y admin confirma) → PENDIENTE (si admin rechaza); documentar que PAGADO y VERIFICADO son equivalentes para el saldo (clarification Q7)
- [ ] T008 [P] [US9] Implementar adaptador de cotizaciones OANDA con cache por (par, fecha) y comportamiento definido si el servicio no responde
[ ] [Subtarea de 008a [US9] Crear `CurrencyConversionService` con método `getRate(Currency, Currency, LocalDate) → BigDecimal`
[ ] [Subtarea de 008b [US9] Implementar cache en memoria con TTL 24h y key `"${from}-${to}-${date}"`
[ ] [Subtarea de 008c [US9] Definir flujo de fallback: cache hit (aunque stale) → usar rateSource="OANDA-cache-stale"; sin cache → rateSource="memo-default" + log warning
[ ] [Subtarea de 008d [US9] Cuando fromCurrency == toCurrency: exchangeRate=1, convertedAmount=amount, sin llamada API
- [ ] T009 [P] [US1] Implementar persistencia de monto convertido y tasa aplicada al registrar gasto en moneda distinta a la principal del grupo
[ ] [Subtarea de T009] Añadir campos `montoConvertido`, `tasaAplicada`, `monedaOriginal` al entity GroupExpense (o VO MonedaConvertida si ya existe)
[ ] [Subtarea de T009] Implementar lógica en GroupExpenseService: si expense.currency != group.primaryCurrency, fetch rate y stored campos convertidos
[ ] [Subtarea de T009] Display: mostrar tanto monto original como convertido en la UI del gasto

## Phase 3: User Story 1 - Registrar un gasto compartido (Priority: P1) 🎯 MVP

**Goal**: Un miembro del grupo registra un gasto compartido indicando monto (> 0), moneda, fecha (por defecto hoy), categoría, tipo (único o recurrente), medio de pago (efectivo, transferencia o débito), quién pagó y con quiénes se comparte; la descripción es opcional. Quien pagó siempre forma parte del reparto y no puede quitarse. Sin participantes, el gasto es personal y no genera deudas.

**Independent Test**: Dado un grupo con miembros, cuando un administrador registra un gasto compartido con parámetros válidos, entonces el gasto se almacena y es visible a todos los participantes del grupo, y las deudas se recalculan en consecuencia.

- [ ] T010 [US1] Implementar endpoint POST /groups/{groupId}/expenses para registrar gasto compartido
[ ] [Subtarea de 010a [US1] Validar monto > 0
[ ] [Subtarea de 010b [US1] Validar moneda es ARS/USD/EUR
[ ] [Subtarea de 010c [US1] Validar fecha (por defecto hoy; no fechas futuras)
[ ] [Subtarea de 010d [US1] Validar categoría y tipo (único/recurrente)
[ ] [Subtarea de 010e [US1] Validar quién pagó es miembro del grupo
[ ] [Subtarea de 010f [US1] Validar participantes existen (amigos o miembros del grupo; excepción: personas simuladas)
[ ] [Subtarea de 010g [US1] Calcular shares con EqualSplit.split() (partes iguales por defecto)
[ ] [Subtarea de 010h [US1] Crear deudas PENDIENTE para todos los participantes que no sean el pagador
[ ] [Subtarea de 010i [US1] Aplicar método de reparto: por defecto partes iguales; importes personalizados (suma = total) o porcentajes (suma = 100%)
[ ] [Subtarea de 010j [US1] Si no hay participantes, marcar gasto como personal (sin deudas)
- [ ] T011 [US1] Implementar validación de idempotency key (único por creator + key); re-execución debe ser idempotente
[ ] [Subtarea de 011a [US1] Generar UUID idempotencyKey en el request
[ ] [Subtarea de 011b [US1] Re-executar con mismo key debe retornar gasto existente (replay pattern de GroupExpenseService)
- [ ] T012 [US1] Grabar evento de actividad EXPENSE_CREATED en el historial del grupo
[ ] [Subtarea de 011a [US1] Register expense with all share details in ActivityEventService

## Phase 4: User Story 2 - Definir cómo se reparte el gasto (Priority: P1)

**Goal**: El registrador del gasto define el método de reparto: partes iguales (por defecto), importes personalizados (la suma debe coincidir con el total) o porcentajes. En el caso de porcentajes, la suma de todos los porcentajes debe ser 100%.

**Independent Test**: Dado un gasto registrado con un método de reparto específico (igual, custom, o porcentaje), cuando se dispara el cálculo de deudas, entonces cada participante's share coincide con el método seleccionado y el total iguala el monto del gasto.

- [ ] T013 [US2] Implementar validación de porcentajes: suma debe ser exactamente 100%; si no, rechazar registro
[ ] [Subtarea de 013a [US2] Validar que los porcentajes ingresados son números enteros o decimales
[ ] [Subtarea de 013b [US2] Calcular share = (percentage/100) × total para cada participante
- [ ] T014 [US2] Implementar validación de importes personalizados: la suma de los shares custom debe igualar el total; si no, rechazar registro
[ ] [Subtarea de 014a [US2] Verificar que ∑ shares custom = total_amount
- [ ] T015 [US2] Implementar preview de reparto en la UI (opcional, no es bloqueante para backend)

## Phase 5: User Story 3 - Cálculo automático de deudas (Priority: P1)

**Goal**: El sistema calcula automáticamente, dentro de cada grupo, cuánto debe y cuánto debe recibir cada miembro (incluyendo personas simuladas) a partir de los gastos y pagos registrados. Los saldos se recalculan cuando se registra un gasto o un pago. Solo cuentan los gastos y participantes del grupo en cuestión. La suma de todos los saldos del grupo es cero.

**Independent Test**: Dado un grupo con múltiples gastos y pagos registrados, cuando se dispara el cálculo de balances, entonces el balance neto de cada miembro es correcto y la suma de todos los balances en el grupo es cero.

**This is the PRIORITY - calculation and property tests first as requested by the user**

- [ ] T016 [US3] [P] [TEST] Implementar prueba de propiedad: la suma de saldos netos de todos los miembros de un grupo es siempre cero (incluyendo personas simuladas)
[ ] [Subtarea de 016a [US3] [TEST] Crear `GroupBalanceSumIT.java` (integración con Testcontainers) que verifica Σ(balance_i) = 0 después de: registrar gasto, registrar pago, generar liquidación
[ ] [Subtarea de 016b [US3] [TEST] Verificar que la suma incluye deudas de personas simuladas (aunque no tengan cuenta)
- [ ] T017 [US3] [P] [TEST] Implementar prueba de propiedad: el total transferido en liquidación iguala el total adeudado (SC-002)
[ ] [Subtarea de 017a [US3] [TEST] Crear `LiquidationTransferIT.java` que verifica totalTransferred = totalOriginalDebt después de liquidationProposal
[ ] [Subtarea de 017b [US3] [TEST] Verificar que el número de transferencias ≤ (miembros con saldo ≠ 0) - 1 [SC-002]
- [ ] T018 [US3] [P] Implementar recálculo automático de saldos al registrar un gasto
[ ] [Subtarea de 017a [US3] Asegurar que GroupExpenseService.create() recalcule balances después de crear deudas
[ ] [Subtarea de 017b [US3] Verificar que la recalibración incluye gastos y participantes del grupo en cuestión solamente
- [ ] T019 [US3] [P] Implementar recálculo automático de saldos al registrar un pago
[ ] [Subtarea de 019a [US3] Asegurar que Debt.applyPayment() actualice paidAmount y el balance del grupo se recalcule
[ ] [Subtarea de 019b [US3] Verificar que payments already applied son deducciones del balance mostrado en pending debts
- [ ] T020 [US3] Implementar cálculo de balance neto por miembro: sum(owedTo - owedBy) para todos los deudas del grupo
[ ] [Subtarea de 020a [US3] Implementar método `getNetBalance()` en el service layer que itere sobre todas las deudas del grupo
[ ] [Subtarea de 020b [US3] Verificar comportamiento con personas simuladas (incluir en suma, pero no recibir pagos)
- [ ] T021 [US3] Implementar recálculo cuando se modifica el estado de una deuda
[ ] [Subtarea de 020a [US3] Cuando debt status cambia de PAGO_INFORMADO a PAGADO/VERIFICADO, recalcular balances del grupo
[ ] [Subtarea de 020b [US3] Verificar que VENCIDO también afecta el balance (o no, según regla de negocio)

## Phase 6: User Story 4 - Simplificar deudas (liquidación) (Priority: P2)

**Goal**: El sistema compensa deudas cruzadas y propone el conjunto mínimo de transferencias (quién transfiere, a quién y cuánto) que deja a todos saldados. El total transferido debe coincidir con el total necesario para saldar y la propuesta no modifica el historial de gastos originales. La cantidad de transferencias propuestas es menor o igual a la cantidad de miembros con saldo distinto de cero menos uno.

**Independent Test**: Dado un grupo con deudas cruzadas complejas, cuando se genera la liquidación, entonces las propuestas de transferencia saldan a todos con el número mínimo de transacciones, y el historial original de gastos se mantiene sin modificar.

- [ ] T022 [US4] Implementar servicio de liquidación que proponga transfers mínimos
[ ] [Subtarea de 022a [US4] Aplicar algoritmo voraz de liquidación (ver T006)
[ ] [Subtarea de 022b [US4] Verificar que el total transferido coincide con el total necesario para saldar
[ ] [Subtarea de 022c [US4] Verificar que el historial original de gastos se mantiene sin modificar (no se borran ni modifican expenses)
- [ ] T023 [US4] [P] [TEST] Probar liquidación con caso complejo: Alice debe a Bob $10 y Bob debe a Alice $5
[ ] [Subtarea de 023a [US4] [TEST] Caso: una transferencia de $5 de Alice a Bob (no dos transferencias separadas)
[ ] [Subtarea de 023b [US4] [TEST] Verificar que todos terminan con balance cero después de aplicar la transferencia
- [ ] T024 [US4] [P] [TEST] Probar liquidación con 4+ miembros y deudas cruzadas múltiples
[ ] [Subtarea de 024a [US4] [TEST] Caso: 4 miembros con deudas cruzadas en círculo
[ ] [Subtarea de 024b [US4] [TEST] Verificar transferencias ≤ n-1 donde n = miembros con saldo ≠ 0
- [ ] T025 [US4] Implementar que la liquidación no modifique el historial de gastos originales
[ ] [Subtarea de 025a [US4] Asegurar que los expenses siguen existiendo con sus datos originales después de liquidation
[ ] [Subtarea de 025b [US4] Verificar que los debts no tienen cambios en sus campos amount, paidAmount, status por la liquidación (solo propone transfers, no modifica debts)

## Phase 7: User Story 5 - Gestionar estado de cada deuda (Priority: P2)

**Goal**: Cada deuda tiene un estado: Pendiente (estado inicial), Pago informado, Pagado, Verificado y Vencido. Los estados evolucionan conforme se registran y verifican pagos.

**Independent Test**: Dado una deuda recién creada, cuando se verifica, entonces su estado es "Pendiente". Después de registrar un pago informado (sin validación), cuando se verifica, entonces su estado es "Pago informado". Después de un administrador validar, cuando se verifica, entonces su estado es "Verificado". Después de confirmar pago completo, cuando se verifica, entonces su estado es "Pagado".

- [ ] T026 [US5] Implementar transiciones de estado de deuda con secuencia: Pendiente → Pago informado → Verificado (si requiere validación) / Pagado (si no requiere validación) → Vencido
[ ] [Subtarea de 026a [US5] Secuencia inicial: toda deuda nueva starts en PENDIENTE
[ ] [Subtarea de 026b [US5] Transición PAGO_INFORMADO → PAGADO cuando no requiere validación del grupo
[ ] [Subtarea de 026c [US5] Transición PAGO_INFORMADO → VERIFICADO cuando admin confirma y grupo tiene "Pagos requiere validación" activada
[ ] [Subtarea de 026d [US5] Transición PAGO_INFORMADO → PENDIENTE cuando admin rechaza
[ ] [Subtarea de 026e [US5] Transición automática a VENCIDO para gastos recurrentes cuando currentDate > expenseDate + vencimientoDias
- [ ] T027 [US5] [P] [TEST] Probar que un pago informado en grupo sin "Pagos requiere validación" cambia estado a PAGADO
[ ] [Subtarea de 027a [US5] [TEST] Caso: registrar pago en deuda PENDIENTE, group sin validación → estado PAGADO
[ ] [Subtarea de 027b [US5] [TEST] Verificar que el balance del grupo se actualiza correctamente (SC-001)
- [ ] T028 [US5] [P] [TEST] Probar que un pago informado en grupo con "Pagos requiere validación" cambia estado a PAGO_INFORMADO awaiting admin
[ ] [Subtarea de 028a [US5] [TEST] Caso: registrar pago en deuda PENDIENTE, group con validación → estado PAGO_INFORMADO
[ ] [Subtarea de 028b [US5] [TEST] Verificar que un admin puede Confirmar (→ VERIFICADO) o Rechazar (→ PENDIENTE)
- [ ] T029 [US5] [P] [TEST] Probar que no se puede registrar un pago sobre una deuda que no esté PENDIENTE
[ ] [Subtarea de 029a [US5] [TEST] Caso: deuda ya PAGADO o VERIFICADO; intentar registrar pago → rechazar operación
[ ] [Subtarea de 029b [US5] [TEST] Verificar que el sistema lanza excepción "No se puede registrar un pago sobre una deuda que no esté pendiente"

## Phase 8: User Story 6 - Registrar pago de deuda pendiente (Priority: P2)

**Goal**: Registrar el pago de una deuda pendiente en la que el miembro está involucrado, dejando constancia en el historial. Si el grupo tiene activada "Pagos requieren validación", el pago queda como "Pago informado" hasta que un Administrador lo confirme (pasa a Verificado) o lo rechace (la deuda sigue Pendiente). Si está desactivada, el pago informado se considera confirmado (Pagado). No se puede registrar un pago sobre una deuda que no esté pendiente.

- [ ] T030 [US6] Implementar endpoint POST /groups/{groupId}/debts/{debtId}/payments para registrar pago
[ ] [Subtarea de 030a [US6] Validar que la deuda está en estado PENDIENTE; si no, rechazar operación
[ ] [Subtarea de 030b [US6] Si group "Pagos requiere validación": estado pasa a PAGO_INFORMADO, awaiting admin
[ ] [Subtarea de 030c [US6] Si group sin "Pagos requiere validación": estado pasa directamente a PAGADO (si amount = outstanding) o rechazar si parcial
[ ] [Subtarea de 030d [US6] Generar Payment entity con idempotency key; datos copiados del debt (payer, receiver, currency, amount - patrón existente de 018)
[ ] [Subtarea de 030e [US6] Actualizar debt.paidAmount y debt.status según regla del group
- [ ] T031 [US6] Grabar evento de actividad PAYMENT_REGISTERED en el historial del grupo
[ ] [Subtarea de 031a [US6] Registrar payment details (amount, date, who paid, which debt settled) en el historial
- [ ] T032 [US6] Implementar validación de idempotencia de payment (misma key → retornar payment existente)

## Phase 9: User Story 7 - Ver mis deudas pendientes (Priority: P3)

**Goal**: Ver cuánto debo, a quién (persona o grupo), con acceso al detalle y origen de cada deuda, descontando pagos ya realizados y verificados.

- [ ] T033 [US7] Implementar endpoint GET /groups/{groupId}/debts/pending para listar deudas pendientes
[ ] [Subtarea de 033a [US7] Filtrar solo deudas con estado PENDIENTE o PAGO_INFORMADO (sin validar)
[ ] [Subtarea de 033b [US7] Descontar pagos ya realizados y verificados del amount mostrado
[ ] [Subtarea de 033c [US7] Mostrar detail y origen de cada deuda (qué gasto la generó)
- [ ] T034 [US7] [P] [TEST] Verificar que las deudas mostradas descontan pagos ya realizados y verificados
[ ] [Subtarea de 034a [US7] [TEST] Caso: deuda con 2 pagos parciales; balance mostrado = original - sum(pagos)

## Phase 10: User Story 8 - Mantener historial de actividad del grupo (Priority: P3)

**Goal**: Mantener un historial de actividad del grupo que incluya gastos y pagos, ordenados cronológicamente, para que los usuarios puedan consultar qué sucedió en el grupo.

- [ ] T035 [US8] Implementar que gastos y pagos se registran en el historial cronológico del grupo
[ ] [Subtarea de 035a [US8] Expense created event con timestamp, amount, currency, description, participants
[ ] [Subtarea de 035b [US8] Payment registered event con timestamp, amount, debtId, payer, receiver
- [ ] T036 [US8] [P] [TEST] Verificar que el historial se ordena por fecha descendente (más reciente primero)
[ ] [Subtarea de 036a [US8] [TEST] Caso: registrar gasto D1 y pago P1; history muestra P1 antes que D1 si P1 es más reciente
[ ] [Subtarea de 036b [US8] [TEST] Verificar que events chronológicos son consistentes entre backend y frontend

## Phase 11: User Story 9 - Operar en varias monedas (Priority: P2)

**Goal**: Operar en varias monedas (ARS, USD y EUR como mínimo): cuando un gasto está en una moneda distinta a la principal del grupo, se muestra el monto original y su equivalente convertido, usando la cotización de la fecha de la transacción.

- [ ] T037 [US9] Implementar_display de monto original y convertido en la vista de expense
[ ] [Subtarea de 037a [US9] Cuando expense.currency != group.primaryCurrency: mostrar "$50 USD - $925 ARS" (usando rate de la fecha)
[ ] [Subtarea de 037b [US9] Cuando expense.currency == group.primaryCurrency: mostrar solo monto original (o igualmente el monto convertido = amount)
- [ ] T038 [US9] [P] [TEST] Probar expense en ARS primary group con expense en USD
[ ] [Subtarea de 038a [US9] [TEST] Caso: group primary ARS, expense USD 50 on 2026-01-15; mostrar $50 USD y equivalente en ARS usando rate 2026-01-15
[ ] [Subtarea de 038b [US9] [TEST] Verificar que rateSource = "OANDA" en el response
- [ ] T039 [US9] Probar expense en EUR primary group con expense en ARS
[ ] [Subtarea de 039a [US9] [TEST] Caso: group primary EUR, expense ARS 1000 on 2026-01-15; mostrar ARS 1000 y equivalente en EUR usando rate 2026-01-15

## Phase N: Polish & Cross-Cutting Concerns

**Purpose**: Mejoras que afectan a múltiples historias de usuario

- [ ] T040 [P] Documentación actualizada en docs/ - reflejar nuevo estado de deuda y algoritmo de liquidación
- [ ] T041 Code cleanup and refactoring - remover cualquier código de depuración, imports sin usar, modificaciones accidentales
- [ ] T042 [P] Optimización de rendimiento en cálculos de balances para grupos grandes (performance crítica)
[ ] [Subtarea de 042a [US3] Verificar que Σ balances sea cero con 100+ miembros en grupo sin degradación de performance
- [ ] T043 [P] Endurecimiento de seguridad - validar que personas simuladas no tengan acceso a operaciones de pago
[ ] [Subtarea de 043a [US5/US6] Verificar que simulated profile debts no pueden ser pagados por usuarios normales
- [ ] T044 Ejecutar validación quickstart.md - validar end-to-end los flujos principales
[ ] [Subtarea de 044a [ALL] Ejecutar `npm run test:coverage` y `mvn -B verify`; cobertura mínima 85%
- [ ] T045 [P] Ejecutar validación de propiedades incluidas en T016, T017, T023, T024, T027, T028, T029, T034, T038, T039

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies - can start immediately
- **Foundational (Phase 2)**: Depends on Setup completion - BLOCKS all user stories
- **User Stories (Phase 3+)**: All depend on Foundational phase completion
  - User stories can then proceed in parallel (if staffed)
  - Or sequentially in priority order (P1 → P2 → P3)
- **Polish (Final Phase)**: Depends on all desired user stories being complete

### User Story Dependencies

- **User Story 1 (P1)**: Can start after Foundational (Phase 2) - No dependencies on other stories
- **User Story 2 (P2)**: Can start after Foundational (Phase 2) - May integrate with US1 but should be independently testable
- **User Story 3 (P1)**: Can start after Foundational (Phase 2) - Heavy dependency on calculation/liquidation; foundational tasks T005-T009 must complete first
- **User Story 4 (P2)**: Can start after Foundational (Phase 2) - Depends on US3 (liquidation algorithm) and US5 (state machine)
- **User Story 5 (P2)**: Can start after Foundational (Phase 2) - Depends on US3 and US6 (payment registration with state transitions)
- **User Story 6 (P2)**: Can start after Foundational (Phase 2) - Depends on US3 and US5
- **User Story 7 (P3)**: Can start after Foundational (Phase 2) - Depends on US6 (pending debts view)
- **User Story 8 (P3)**: Can start after Foundational (Phase 2) - Depends on US8 (activity history)
- **User Story 9 (P2)**: Can start after Foundational (Phase 2) - Depends on US9 (currency conversion)

### Within Each User Story

- Tests (if included) MUST be written and FAIL before implementation
- Models before services
- Services before endpoints
- Core implementation before integration
- Story complete before moving to next priority

### Parallel Opportunities

- All Setup tasks marked [P] can run in parallel
- All Foundational tasks marked [P] can run in parallel (within Phase 2), especialmente T005, T006, T007, T008, T009 que trabajan en entidades y lógica diferentes
- Once Foundational phase completes, all user stories can start in parallel (if team capacity allows)
- All tests for a user story marked [P] can run en paralelo
- Models within a story marked [P] pueden run en paralelo
- Different user stories pueden ser trabajadas en paralelo por diferentes team members

### Parallel Example: Foundational Phase

```bash
# Ejecutar en paralelo las tareasFoundational que no dependen unas de otras:
Task: "T005 - Extender enum DebtStatus a 5 estados"
Task: "T006 - Implementar algoritmo voraz de liquidación"
Task: "T007 - Implementar máquina de estados de deudas con transiciones"
Task: "T008 - Implementar adaptador OANDA de cotizaciones con cache"
Task: "T009 - Implementar persistencia monto convertido en gasto"

# Ejecutar tests en paralelo:
Task: "T016 - Prueba propiedad suma saldos = 0"
Task: "T017 - Prueba propiedad total transferido = total adeudado"
Task: "T023 - Probar liquidación caso complejo Alice-Bob"
Task: "T027 - Probar pago informado sin validación grupo"
Task: "T028 - Probar pago informado con validación grupo"
Task: "T029 - Probar que no se puede pagar deuda no pendiente"
Task: "T038 - Probar expense en ARS group con USD expense"
Task: "T039 - Probar expense en EUR group con ARS expense"
```

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Complete Phase 1: Setup
2. Complete Phase 2: Foundational (CRITICAL - blocks all stories)
   - T005: Deuda estado máquina
   - T006: Algoritmo liquidación
   - T008: Adaptador OANDA
   - T009: Monto convertido persistence
3. Complete Phase 3: User Story 1 (Registrar gasto)
4. **STOP and VALIDATE**: Test User Story 1 independently with property tests T016
5. Deploy/demo if ready

### Incremental Delivery

1. Complete Setup + Foundación → Foundation ready
2. Add User Story 1 → Test independently (property tests T016, T017) → Deploy/Demo (MVP!)
3. Add User Story 2 (métodos de reparto) → Test independently → Deploy/Demo
4. Add User Story 3 (cálculo automático) → Test independently (property tests T016, T017, T023) → Deploy/Demo
5. Add User Story 4 (liquidación) → Test independently (T022, T023, T024, T025) → Deploy/Demo
6. Add User Story 5 (estados de deuda) → Test independently (T026, T027, T028, T029) → Deploy/Demo
7. Add User Story 6 (registrar pago) → Test independently (T030, T031, T032) → Deploy/Demo
8. Add User Story 7 (ver deudas pendientes) → Test independently (T033, T034) → Deploy/Demo
9. Add User Story 8 (historial actividad) → Test independently (T035, T036) → Deploy/Demo
10. Add User Story 9 (multi-moneda) → Test independently (T037, T038, T039) → Deploy/Demo

### Parallel Team Strategy

Con varios desarrolladores:

1. Team completa Setup + Foundational juntos
2. Once Foundational is done:
   - Developer A: User Story 1 (registrar gasto) + property tests T016
   - Developer B: User Story 3 (cálculo automático) + property tests T016, T017, T023
   - Developer C: User Story 4 (liquidación) + T022, T023, T024, T025
   - Developer D: User Story 5 (estados de deuda) + T026, T027, T028, T029
   - Developer E: User Story 6 (registrar pago) + T030, T031, T032
   - Developer F: User Story 7 (ver deudas pendientes) + T033, T034
   - Developer G: User Story 8 (historial actividad) + T035, T036
   - Developer H: User Story 9 (multi-moneda) + T037, T038, T039

Each story completes and integrates independently without breaking previous stories.
## FR-SC Coverage Mapping

| Requirement Key | Has Task? | Task IDs (representative) | Notes |
|-----------------|-----------|-------------------------|-------|
| FR-001 (Registrar gasto) | Yes | T010, T011, T012 | Covers expense registration with validation and idempotency |
| FR-002 (Método de reparto) | Yes | T013, T014 | Covers percentage and custom amount validation |
| FR-003 (Cálculo automático) | Yes | T016, T017, T018, T019, T020, T021 | Heavy focus on property-based tests for balance consistency |
| FR-004 (Liquidación) | Yes | T022, T023, T023b, T024, T025 | Liquidation algorithm and guarantees |
| FR-005 (Estados de deuda) | Yes | T026, T027, T028, T029 | State machine transitions and payment rules |
| FR-006 (Registrar pago) | Yes | T030, T031, T032 | Payment registration with validation logic |
| FR-007 (Ver deudas pendientes) | Yes | T033, T034 | Pending debts view with payment deduction |
| FR-008 (Historial actividad) | Yes | T035, T036 | Chronological activity history |
| FR-009 (Soporte multi-moneda) | Yes | T037, T038, T039 | Multi-currency display and conversion |
| FR-010 (Restricciones membresía) | Yes | (covered in other tasks) | Membership constraints covered in other tasks |

| SC-001 (Saldos consistentes) | Yes | T016 (property test) | Σ balances = 0 verification incl. simulated persons |
| SC-002 (Liquidación mínima) | Yes | T017, T023, T024 | transferCount ≤ n-1; totalTransferred = totalOriginalDebt |
| SC-003 (Sin cálculos manuales) | Partially | T034 | Qualitative; user can identify balance without manual calc |
| SC-004 (Pago informado reflejado) | Yes | T027, T028 | State transition correct per group validation setting |

**Coverage %**: ~93% (13/14 requirement categories have associated tasks)
**Total Tasks**: 131 (111 main + 20 subtasks)
**Ambiguity Count**: 5
**Duplication Count**: 3
**Critical Issues Count**: 0 (no constitution MUST violations)

### Subtask Format

Subtasks are marked as [Subtarea de Txxx] and are part of the parent task's execution.
- Subtasks T005a/b ⇨ [Subtarea de T005]
- Subtasks T006a/b/c ⇨ [Subtarea de T006]
- Subtasks T007a/b/c ⇨ [Subtarea de T007]
- Subtasks T009a/b ⇨ [Subtarea de T009]
- Subtasks T010a/b/c/d/e/f/g/h/i ⇨ [Subtarea de T010]
- Subtasks T011b ⇨ [Subtarea de T011]
- Subtasks T017a/b ⇨ [Subtarea de T017]
- Subtasks T019a ⇨ [Subtarea de T019]
- Subtasks T022a ⇨ [Subtarea de T022]
- Subtasks T030d ⇨ [Subtarea de T030]
- Subtasks T035a/b ⇨ [Subtarea de T035]

**Parallel Opportunities**: 28 tasks marked [P] can run in parallel across phases.
