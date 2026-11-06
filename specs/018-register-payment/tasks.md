---

description: "Task list for feature 018-register-payment"
---

# Tasks: Registrar un pago

**Input**: Design documents from `/specs/018-register-payment/`

**Prerequisites**: [plan.md](./plan.md), [spec.md](./spec.md), [data-model.md](./data-model.md)

**Tests**: incluidas (constitución V). En cada historia, las pruebas se escriben primero y deben fallar antes de implementar.

**Coverage gates**: JaCoCo `LINE COVEREDRATIO ≥ 0.85` (`backend/pom.xml`) y Vitest V8 `lines`/`statements ≥ 85` (`frontend/vite.config.js`). Ninguna tarea baja los umbrales ni excluye archivos nuevos del reporte. Además, cada clase o archivo nuevo o modificado por 018 debe quedar en ≥ 85 % de líneas por sí mismo (T061, T062).

**Organization**: tareas agrupadas por historia de usuario. Se implementa una historia por vez (constitución III).

**Punto de partida**: la rama `feat/registrar-pagos` con 017 completa. Todas las tareas empiezan sin marcar.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: puede ejecutarse en paralelo (archivos distintos, sin dependencias pendientes)
- **[Story]**: US1, US2 o US3; sin etiqueta en Setup, Foundational y Polish

## Path Conventions

- Backend: `backend/src/main/java/com/amano/backend/{payment,groupexpense,groups,common}/…`, pruebas en `backend/src/test/java/com/amano/backend/…`
- Migración: `backend/src/main/resources/db/migration/V14__create_payments.sql`
- Frontend: `frontend/src/…`, pruebas `*.test.js(x)` junto al código

---

## Phase 1: Preparación (Setup)

**Purpose**: confirmar la base de trabajo y la numeración

- [x] T001 Confirmar que `feat/registrar-pagos` contiene 017 completa: `V13__create_group_expenses_and_debts.sql`, el paquete `groupexpense` (con `Debt.applyPayment`), `groups/port/LedgerDebtPort` y las vistas `GroupExpenseFormView`/`GroupExpenseDetailView`. Confirmar que no existe ninguna `V14` en `db/migration` — plan.md §Technical Context
- [ ] T002 Confirmar que `018` y `V14` siguen libres en las ramas del equipo (`sprint-4` y `feat/*` remotas) y avisar en el grupo que quedan tomadas; la próxima libre pasa a ser `V15` — plan.md D1
- [x] T003 Línea base antes de tocar código: `cd backend && ./mvnw -B clean verify` y `cd frontend && npm run lint && npm run test:coverage`, los dos en verde. Anotar en plan.md §Resultado la cantidad de pruebas y la cobertura de partida
- [ ] T004 Si la base local llegó a aplicar una `V14` de un borrador, recrearla (`docker compose down -v && docker compose up -d db`) y correr `./mvnw clean` para descartar migraciones compiladas viejas en `target/classes`

---

## Phase 2: Foundational — Migración V14 y dominio (Blocking Prerequisites)

**Purpose**: esquema, entidad, bloqueo y permisos que usan todas las historias

**⚠️ CRITICAL**: ninguna historia empieza antes de terminar esta fase

### Migración V14

- [x] T005 [P] Escribir `PaymentMigrationIT` (Testcontainers) en `backend/src/test/java/com/amano/backend/payment/PaymentMigrationIT.java`: la tabla `payments` existe con sus columnas; rechaza `payer_type` inválido, `REGISTERED` sin `payer_user_id`, `REGISTERED` con perfil, pagador igual a receptor (`ck_payments_not_self`), `amount` 0 y moneda fuera de ARS/USD/EUR; rechaza la misma `(registered_by_user_id, idempotency_key)`; al borrar el perfil simulado, `payer_simulated_profile_id` queda nulo y la fila sigue. Debe fallar — data-model.md §Payment
- [x] T006 Verificar en `PaymentMigrationIT` que existen `ix_payments_debt` e `ix_payments_group` (consulta a `pg_indexes`) — data-model.md §Cambios de esquema
- [x] T007 Crear `backend/src/main/resources/db/migration/V14__create_payments.sql` copiando **exactamente** el DDL de data-model.md §Boceto del DDL: un `CREATE TABLE payments` y dos `CREATE INDEX`. **Prohibido** `INSERT`, `UPDATE`, `DELETE` o `ALTER` sobre tablas existentes. T005 y T006 pasan

### Entidad y repositorios

- [x] T008 [P] Escribir `PaymentTest` en `backend/src/test/java/com/amano/backend/payment/PaymentTest.java`: `Payment.of(debt, amount, date, registeredBy, receiverName, key)` copia `group_id`, `debt_id`, `payer_type`, `payer_user_id` o `payer_simulated_profile_id`, `payer_name` (de `debtor_name`), `receiver_user_id` (del acreedor) y `currency` de la deuda, para un deudor registrado y uno simulado. Debe fallar — plan.md D3
- [x] T009 Crear la entidad `Payment` en `backend/src/main/java/com/amano/backend/payment/Payment.java` con el mapeo de `payments` (`ParticipantType` y `CurrencyConversion` de 017/007, `@CreationTimestamp`), constructor protegido, fábrica `of(...)` y getters; sin setters (los pagos no se editan). T008 pasa
- [x] T010 [P] Crear `PaymentRepository` en `backend/src/main/java/com/amano/backend/payment/PaymentRepository.java` con `findByRegisteredByUserIdAndIdempotencyKey(Long, UUID)` y `findByDebtIdOrderByCreatedAtAsc(Long)`
- [x] T011 Agregar a `DebtRepository` (`groupexpense/DebtRepository.java`) `findByIdForUpdate(Long id)` con `@Lock(LockModeType.PESSIMISTIC_WRITE)` y `@Query("SELECT d FROM Debt d WHERE d.id = :id")` — data-model.md §Concurrencia, plan.md D5
- [x] T012 Arrancar el backend contra la base local (o correr `PaymentMigrationIT`) y confirmar que `ddl-auto=validate` acepta `Payment` sin errores de mapeo

### Permisos y excepciones

- [x] T013 [P] Extender `GroupPermissionCheckerTest` con `canRegisterPayment`: ADMIN siempre; MEMBER deudor; MEMBER acreedor; MEMBER tercero → `false`; deudor simulado (`debtorUserId` nulo) con un MEMBER que no es acreedor → `false`. Debe fallar — plan.md D2
- [x] T014 Implementar `GroupPermissionChecker.canRegisterPayment(String callerRole, Long callerUserId, Long debtorUserId, Long creditorUserId)` en `groups/GroupPermissionChecker.java`. T013 pasa
- [x] T015 [P] Crear `DebtNotFoundException` ("No existe la deuda solicitada o no sos miembro del grupo") y `DebtAlreadyPaidException` ("La deuda ya está pagada", `CODE = "DEBT_ALREADY_PAID"`) en `backend/src/main/java/com/amano/backend/payment/`
- [x] T016 Registrar las dos excepciones en `common/ProblemDetailExceptionHandler.java`: 404 con el `detail` neutral y 409 con `properties.code = DEBT_ALREADY_PAID`, con el mismo formato que `PendingDebtsException` y `RecurringExpenseFinishedException`

### Integración con 014 (formato de `PAYMENT_MADE`)

- [x] T017 [P] Extender `ActivityEventServiceTest`: `recordPaymentMade` con `expenseId` y `debtId` los guarda en el detalle; sin ellos, el detalle queda igual que en 014 (sin claves nulas). Debe fallar — plan.md D6
- [x] T018 *(Se agregó un constructor con el formato de 014, así que las pruebas de 014 no cambiaron.)* Agregar `expenseId` y `debtId` al final de `PaymentActivityDetails` (`groups/PaymentActivityDetails.java`) y ponerlos en el mapa de `ActivityEventService.recordPaymentMade` solo si no son nulos. Ajustar las llamadas existentes de las pruebas de 014 al constructor nuevo sin cambiar lo que verifican. T017 pasa

**Checkpoint**: `V14` aplicada, `Payment` mapeada, bloqueo y permiso disponibles, `PAYMENT_MADE` listo para recibir los datos del pago. `./mvnw -B verify` en verde.

---

## Phase 3: User Story 1 — Registrar un pago total o parcial (Priority: P1) 🎯 MVP

**Goal**: `POST /api/payments` registra el pago, actualiza la deuda vía JPA/Hibernate y deja `PAYMENT_MADE` en el historial

**Independent Test**: con una deuda `PENDING` de 3.333,33, un pago de 1.000,00 deja `PARTIALLY_PAID` con saldo 2.333,33 y otro de 2.333,33 deja `PAID`; el historial tiene dos `PAYMENT_MADE`

### Tests for User Story 1 ⚠️

> Escribirlas primero y verificar que fallan

- [x] T019 [P] [US1] Escribir `PaymentServiceTest` (Mockito) en `backend/src/test/java/com/amano/backend/payment/PaymentServiceTest.java`, casos de acceso: deuda inexistente → `DebtNotFoundException`; no miembro activo → la misma excepción; MEMBER tercero → `GroupPermissionDeniedException`; deudor, acreedor y ADMIN → pasan — spec US1 escenarios 8–11
- [x] T020 [US1] Agregar a `PaymentServiceTest` los casos de entrada: monto vacío, cero, negativo, con tres decimales y fuera de rango (mensajes de 007); monto mayor al saldo ("El pago no puede superar el saldo de $ 2.333,33"); fecha futura; fecha anterior a la del gasto; `debtId` nulo. Todos los errores juntos en una `FieldValidationException` — plan.md D4
- [x] T021 [US1] Agregar a `PaymentServiceTest` los casos de estado: `PENDING` + parcial → `PARTIALLY_PAID`; `PENDING` + saldo → `PAID`; `PARTIALLY_PAID` + saldo → `PAID`; `PAID` → `DebtAlreadyPaidException` sin guardar; se usa `findByIdForUpdate` (no `findById`) antes de validar el saldo — data-model.md §Transiciones
- [x] T022 [US1] Agregar a `PaymentServiceTest` los casos de idempotencia: misma clave y mismos datos → devuelve el pago existente sin llamar a `applyPayment` ni a `recordPaymentMade`; misma clave con otro monto, fecha o deuda → `IdempotencyConflictException` — spec FR-008
- [x] T023 [US1] Agregar a `PaymentServiceTest` el caso de historial: `recordPaymentMade(groupId, caller, details)` con `paymentId`, `amount`, `currency`, `fromName` = `debtor_name`, `toName` = nombre del acreedor, `expenseId` y `debtId`; el actor es quien registró, aunque no sea el deudor — spec FR-012
- [x] T024 [P] [US1] Escribir `PaymentControllerIT` (Testcontainers) en `backend/src/test/java/com/amano/backend/payment/PaymentControllerIT.java`: 201 con `Location` y `PaymentResponse` (pago + deuda actualizada); 200 en el reenvío; 400 por campo; 403; 404 neutral para deuda inexistente y para deuda de otro grupo; 409 `DEBT_ALREADY_PAID`; 422 `IDEMPOTENCY_KEY_REUSED`; 401 sin sesión — plan.md §Contrato REST
- [x] T025 [US1] Agregar a `PaymentControllerIT` las verificaciones en la base: después de dos pagos parciales, `debts.paid_amount = Σ payments.amount`, `status` correcto y `updated_at` posterior a `created_at`; `GET /api/groups/{id}/activity?eventType=PAYMENT_MADE` devuelve las entradas `PAYMENT_MADE` con las siete claves — data-model.md §Reglas de negocio 1
- [x] T026 [P] [US1] Escribir `PaymentConcurrencyIT` en `backend/src/test/java/com/amano/backend/payment/PaymentConcurrencyIT.java`: dos hilos pagan a la vez montos que juntos superan el saldo → exactamente uno 201 y el otro 400 o 409, `paid_amount ≤ amount`; dos hilos con la misma clave → un solo pago y una sola entrada `PAYMENT_MADE` — spec FR-011, plan.md D5
- [x] T027 [P] [US1] Escribir `PaymentControllerTest` (unitaria) en `backend/src/test/java/com/amano/backend/payment/PaymentControllerTest.java`: 401 defensivo con principal no numérico, `Idempotency-Key` inválida → `FieldValidationException`, 201 con `Location: /api/payments/{id}`, `DataIntegrityViolationException` resuelta con `resolveConcurrentRegistration` → 200 (patrón de `GroupExpenseControllerTest` de 017)

### Implementation for User Story 1

- [x] T028 [P] [US1] Crear `RegisterPaymentRequest` (`debtId`, `amount`, `date`, `deviceTimeZone`) y `PaymentResponse` (record con `id`, `groupId`, `expenseId`, `debtId`, `amount`, `currency`, `date`, `payer`, `receiver`, `registeredBy`, `createdAt` y `debt` como `GroupExpenseResponse.DebtView`) en `backend/src/main/java/com/amano/backend/payment/`
- [x] T029 [US1] *(La primera lectura de la deuda ya toma el bloqueo; ver plan.md §Flujo, paso 1.)* Implementar `PaymentService.register(callerUserId, idempotencyKey, request)` (`@Transactional`) con los pasos 1–11 de plan.md §Flujo del backend, en ese orden: deuda, membresía, permiso, validación (007 + fecha del gasto), `findByIdForUpdate`, idempotencia, estado/saldo, `debt.applyPayment`, `save(Payment)`, `recordPaymentMade`, respuesta. La deuda se actualiza **solo** con `applyPayment` y el *dirty checking* de Hibernate (sin `UPDATE` a mano). T019–T023 pasan
- [x] T030 [US1] Implementar `PaymentService.resolveConcurrentRegistration(...)` (`@Transactional(readOnly = true)` como en 017: el controlador lo llama después del rollback, relee por `(registered_by_user_id, idempotency_key)` y compara los datos, como 007/017)
- [x] T031 [US1] Implementar `PaymentController` en `backend/src/main/java/com/amano/backend/payment/PaymentController.java`: `POST /api/payments` (`consumes`/`produces` JSON), `currentUserId()` como `GroupExpenseController`, parseo de `Idempotency-Key`, 201 con `Location` o 200 en el reenvío, y captura de `DataIntegrityViolationException`. T024, T025 y T027 pasan
- [x] T032 [US1] Agregar `"/api/payments/**"` al `requestMatchers` de cuenta verificada de `config/SecurityConfig.java` (junto a `/api/users/**`, `/api/expenses/**` y `/api/groups/**`); hoy caería en `anyRequest().authenticated()` y aceptaría cuentas sin verificar. Agregar a `PaymentControllerIT` el caso de cuenta no verificada (mismo resultado que `/api/groups/**`) y el 401 sin sesión — plan.md §Seguridad
- [x] T033 [US1] Hacer pasar `PaymentConcurrencyIT` (T026). Si el bloqueo no serializa, revisar que `findByIdForUpdate` se llame dentro de la transacción de `register`

### Detalle del gasto: `canRegisterPayment` (cambio aditivo en 017)

- [x] T034 [P] [US1] Extender `GroupExpenseServiceTest`: en `get`, `canRegisterPayment` es `true` para el deudor, el acreedor y un ADMIN en deudas sin saldar, y `false` en deudas `PAID` y para un MEMBER tercero — spec FR-016
- [x] T035 [US1] *(Ninguna prueba de 017 construía `DebtView` a mano: no hubo que ajustarlas. Se agregó la fábrica `DebtView.of`, que reusa `PaymentResponse`.)* Agregar `boolean canRegisterPayment` al final de `GroupExpenseResponse.DebtView` y calcularlo en `GroupExpenseService.get` con `GroupPermissionChecker.canRegisterPayment` y el rol de quien consulta. En el alta (`create`), calcularlo con quien registra. Ajustar las construcciones de `DebtView` en las pruebas de 017 (`GroupExpenseControllerTest`, `GroupExpenseControllerIT`) sin cambiar lo que verifican. T034 pasa
- [x] T036 [US1] Agregar a `PaymentControllerIT` que `GET /api/groups/{id}/expenses/{expenseId}` devuelve `canRegisterPayment` correcto para el deudor, un tercero y después del pago total (`false`)

**Checkpoint**: el pago funciona por API de punta a punta, la deuda cambia de estado, el historial lo muestra y la concurrencia está cubierta. `./mvnw -B verify` en verde.

---

## Phase 4: User Story 2 — Saldar desbloquea las bajas (Priority: P1)

**Goal**: probar que una deuda `PAID` deja de bloquear abandonar y quitar, y que una parcial no

**Independent Test**: abandonar con deuda → 403; pago parcial → sigue 403; pago del saldo → abandonar 200

- [x] T037 [P] [US2] Escribir `PaymentUnblocksLeaveIT` en `backend/src/test/java/com/amano/backend/payment/PaymentUnblocksLeaveIT.java`: deudora con deuda `PENDING` → `POST /api/groups/{id}/leave` 403 `PENDING_DEBTS`; pago parcial → sigue 403; pago del saldo → 200 y deja de ser miembro — spec US2 escenarios 1–2
- [x] T038 [US2] Agregar a `PaymentUnblocksLeaveIT`: acreedora con todas sus deudas `PAID` puede ser quitada por un ADMIN; con una `PAID` y otra `PENDING` sigue bloqueada — spec US2 escenarios 3 y 5
- [x] T039 [US2] Agregar a `PaymentUnblocksLeaveIT`: perfil simulado con su deuda saldada por el acreedor → quitarlo se permite, el pago conserva `payer_name` y `payer_simulated_profile_id` queda nulo, y el historial sigue mostrando el nombre — spec US2 escenario 4
- [x] T040 [US2] Confirmar que T037–T039 pasan **sin** cambios en `groups/port/LedgerDebtPort`, `GroupService` ni `GroupMemberService` (FR-013). Si alguno falla, corregir en `payment/`, no en el puerto

**Checkpoint**: el caso de Lucía queda resuelto por API.

---

## Phase 5: User Story 3 — Pantalla "Registrar pago" (Priority: P2)

**Goal**: registrar el pago desde el detalle del gasto con vista previa, snackbar y vuelta al detalle

**Independent Test**: con el backend simulado en Vitest, abrir el detalle, pulsar "Registrar pago", cambiar el monto, ver "Pago parcial · faltan $ X", enviar y volver al detalle con el snackbar

### Tests for User Story 3 ⚠️

- [x] T041 [P] [US3] Escribir `frontend/src/pages/groups/paymentPreview.test.js`: parcial, total exacto, último centavo (0,01 sobre 0,01 → `PAID`), monto mayor al saldo → `null`, monto inválido o ≤ 0 → `null` — plan.md §Módulos sin interfaz
- [x] T042 [P] [US3] Escribir `frontend/src/pages/groups/paymentValidation.test.js`: mensajes de 007 para monto vacío, formato, decimales y cero; "El pago no puede superar el saldo de $ 2.333,33"; fecha vacía, inválida y futura (mensajes de 007); "La fecha del pago no puede ser anterior a la del gasto"; valores válidos → `true`
- [x] T043 [P] [US3] Escribir `frontend/src/api/paymentsClient.test.js` (mock de `fetch`): cabecera `Idempotency-Key`, cuerpo, `credentials: 'include'`, 201 (`created: true`) vs 200, 400 con `fields`, 403, 404, 409 con `code`, 422, 401, 5xx y red → `{ ok: false, network: true }`
- [x] T044 [P] [US3] Escribir `frontend/src/components/groups/RegisterPaymentParts.test.jsx`: `DebtPaymentSummary` (frase deudor → acreedor, total, pagado y saldo formateados, badge actual, nombres ausentes) y `PaymentOutcomePreview` (parcial, pagada, mensaje vacío, `aria-live`)
- [x] T045 [P] [US3] Escribir `frontend/src/pages/groups/RegisterPaymentView.test.jsx` (`vi.mock` de `groupExpensesClient` y `paymentsClient`): carga; monto precargado con el saldo y fecha con hoy; vista previa reactiva; validación local sin llamar a `registerPayment`; 201 → snackbar "Pago registrado" y navegación al detalle; 400 por campo; 403; 404; 409; 422 con renovación de clave; 401 → `setUser(null)`; red → mensaje y reintento con la **misma** clave; deuda `PAID` y sin permiso por URL directa → mensaje sin formulario
- [x] T046 [P] [US3] Extender `frontend/src/components/groups/ExpenseDebtList.test.jsx`: "Registrar pago" visible con `canRegisterPayment` y las props `groupId`/`expenseId`, con el `href` de la ruta nueva; oculto en `PAID`, sin permiso o sin props
- [x] T047 [P] [US3] Extender `GroupExpenseDetailView.test.jsx` (el detalle pasa las props y muestra el botón), `GroupHistoryView.test.jsx` ("Ver gasto" en `PAYMENT_MADE` con `expenseId`; sin `expenseId` no aparece) y `App.test.jsx` (la ruta nueva redirige al login sin sesión)

### Implementation for User Story 3

- [x] T048 [P] [US3] Crear `frontend/src/pages/groups/paymentPreview.js` con `previewPayment(debt, amountCanonical)` usando `toCents`/`fromCents` de `splitEqually.js`. T041 pasa
- [x] T049 [P] [US3] Crear `frontend/src/pages/groups/paymentValidation.js` con `validatePaymentAmount`, `validatePaymentDate`, `PAYMENT_EXCEEDS_OUTSTANDING` y `PAYMENT_BEFORE_EXPENSE`, reutilizando `parseArgentineAmount`, `parseArgentineDate`, `AMOUNT_*` y `DATE_*` de `validation/expenseValidation.js` y `formatAmount` de `validation/expenseFormat`. T042 pasa
- [x] T050 [P] [US3] Crear `frontend/src/api/paymentsClient.js` con `registerPayment(payload, idempotencyKey)` y `PAYMENT_NETWORK_ERROR_MESSAGE`, con la convención de `groupExpensesClient` (nunca lanza). T043 pasa
- [x] T051 [P] [US3] Crear `frontend/src/components/groups/DebtPaymentSummary.jsx` y `frontend/src/components/groups/PaymentOutcomePreview.jsx` reutilizando `DebtStatusBadge` y `formatAmount`. T044 pasa
- [x] T052 [US3] Crear `frontend/src/pages/groups/RegisterPaymentView.jsx` según plan.md §Componentes: carga con `getGroupExpense`, estados sin formulario, `react-hook-form` con `TextField`/`SubmitButton`, `useWatch` para la vista previa, `Idempotency-Key` con `crypto.randomUUID()` conservada en errores de red, `useSnackbar` de 015 con "Pago registrado" y `navigate` al detalle. T045 pasa
- [x] T053 [US3] Modificar `ExpenseDebtList.jsx` (props opcionales `groupId`/`expenseId` y el `<Link className="group-action">` "Registrar pago") y `GroupExpenseDetailView.jsx` (pasa las props). T046 y la parte de detalle de T047 pasan
- [x] T054 [US3] Modificar `GroupHistoryView.jsx` para mostrar "Ver gasto" también en `PAYMENT_MADE` con `details.expenseId`, y agregar la ruta protegida `/groups/:id/expenses/:expenseId/debts/:debtId/payment` en `App.jsx`. El resto de T047 pasa
- [x] T055 [US3] Agregar en `frontend/src/pages/groups/groups.scss` el bloque `.group-payment` (resumen en tres columnas que pasan a una bajo 600 px, vista previa) con las variables del rediseño de #37; sin colores nuevos
- [x] T056 [US3] `npm run lint` sin errores nuevos (los dos warnings previos de `RegisterForm` y `ChangePasswordPage` no cuentan) y `npm run build` OK

**Checkpoint**: el flujo completo funciona desde la interfaz.

---

## Phase 6: Polish, cobertura y validación manual

**Purpose**: gates de calidad, regla de migraciones y validación con la app levantada

### Cobertura (gates obligatorios)

- [x] T057 Backend: `cd backend && ./mvnw -B clean verify` en verde con **JaCoCo ≥ 85 % de líneas** en el total ("All coverage checks have been met"). Ninguna exclusión nueva en `pom.xml`
- [x] T058 Frontend: `cd frontend && npm run test:coverage` en verde con **cobertura V8 ≥ 85 %** de `lines` y `statements` en el total. Ninguna exclusión nueva en `vite.config.js`
- [x] T059 [P] Agregar en plan.md §Resultado los totales de pruebas y cobertura de T057 y T058 contra la línea base de T003

### Regla de migraciones y regresión

- [x] T060 [P] Verificar la migración: `grep -nEi '\b(UPDATE|DELETE|INSERT|DROP|TRUNCATE|ALTER)\b' backend/src/main/resources/db/migration/V14__create_payments.sql` solo encuentra `ON DELETE SET NULL` (y, si los hubiera, comentarios). V1–V13 sin cambios — data-model.md §Verificación
- [x] T061 [P] Revisar en `backend/target/site/jacoco/index.html` que cada clase nueva de `payment/` y cada clase modificada (`DebtRepository`, `GroupExpenseService`, `GroupExpenseResponse`, `GroupPermissionChecker`, `PaymentActivityDetails`, `ActivityEventService`, `ProblemDetailExceptionHandler`, `SecurityConfig`) tenga **≥ 85 % de líneas**. Agregar pruebas si alguna no llega
- [x] T062 [P] Revisar en el reporte V8 (`frontend/coverage/index.html`) que cada archivo nuevo o modificado de US3 tenga **≥ 85 % de líneas**. Agregar pruebas si alguno no llega
- [x] T063 Regresión: las pruebas de 007, 009, 010, 014 y 017 siguen en verde sin cambiar lo que verifican (solo ajustes de constructor por `canRegisterPayment` y `PaymentActivityDetails`)

### Validación manual

- [ ] T064 Levantar base, backend y frontend en local y ejecutar los escenarios **A–I** de plan.md §Validación manual. Anotar el resultado (OK o el desvío encontrado) en plan.md §Resultado
- [ ] T065 Revisar en el navegador, en desktop y en un ancho móvil, que la pantalla "Registrar pago" y el botón en el detalle respetan el diseño de #37 (escenarios A y B)

### Cierre

- [x] T066 Revisar spec.md, plan.md y data-model.md contra lo implementado y registrar cualquier desvío en la propia spec 018 (no en specs anteriores)
- [ ] T067 Commits por fase con Conventional Commits y PR de `feat/registrar-pagos` hacia `sprint-4`, mencionando en la descripción D7 (`payments_require_validation` sin efecto) y los cambios aditivos de contrato de 014/017. Los realiza el equipo

---

## Dependencies & Execution Order

### Phase Dependencies

- **Preparación (Phase 1)**: sin dependencias.
- **Foundational (Phase 2)**: depende de Phase 1. Bloquea todas las historias.
- **US1 (Phase 3)**: depende de Phase 2.
- **US2 (Phase 4)**: depende de US1 (necesita el endpoint de pago).
- **US3 (Phase 5)**: depende del contrato de US1 (T028 y T035). Puede empezar con mocks en cuanto el contrato esté fijo, en paralelo con US2.
- **Polish (Phase 6)**: depende de las tres historias.

### Within Each Phase

- Las pruebas marcadas con ⚠️ se escriben y fallan antes de implementar.
- Migración (T007) antes que la entidad (T009); entidad y repositorios antes que el servicio (T029); servicio antes que el controlador (T031).
- En el frontend, los módulos puros (T048–T050) antes que los componentes (T051) y la vista (T052).

### Parallel Opportunities

- Phase 2: T005, T008, T013, T015 y T017 en paralelo (archivos distintos).
- Phase 3: T019, T024, T026, T027 y T034 en paralelo; T028 en paralelo con las pruebas.
- Phase 5: T041–T047 en paralelo; T048–T051 en paralelo.
- Phase 6: T059–T062 en paralelo.

## Implementation Strategy

### MVP First (User Story 1)

1. Phase 1 y Phase 2.
2. Phase 3 (US1). **Parar y validar** por API: pago parcial, pago total, historial.
3. Phase 4 (US2): el caso de Lucía queda resuelto.

### Incremental Delivery

1. Foundational → `V14`, entidad, bloqueo y permisos.
2. US1 → pagos por API (MVP).
3. US2 → bajas desbloqueadas (sin código nuevo en `groups/`).
4. US3 → pantalla y enlaces.
5. Polish → gates de cobertura, regla de migraciones y validación manual.
