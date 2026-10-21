---

description: "Task list for feature 007-register-expense"
---

# Tasks: Registro de gasto personal

**Input**: Design documents from `/specs/007-register-expense/`

**Prerequisites**: [plan.md](./plan.md), [spec.md](./spec.md), [research.md](./research.md), [data-model.md](./data-model.md), [contracts/expenses-api.md](./contracts/expenses-api.md), [quickstart.md](./quickstart.md)

**Tests**: incluidas. El plan y la constitución (V) exigen pruebas por comportamiento; cada
tarea de prueba se lista junto a la implementación que cubre (en TDD donde el orden importa).

**Organization**: tareas agrupadas por historia de usuario para permitir implementación,
prueba y revisión independientes.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: puede ejecutarse en paralelo (archivos distintos, sin dependencias pendientes)
- **[Story]**: US1, US2 o US3
- Todas las rutas son reales del repositorio

## Path Conventions

- Backend: `backend/src/main/java/com/amano/backend/…`, pruebas en `backend/src/test/java/…`
- Migraciones: `backend/src/main/resources/db/migration/…`
- Frontend: `frontend/src/…`, pruebas `*.test.jsx` / `*.test.js` colocadas junto al código

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: preparar la rama y verificar la línea base antes de tocar código.

- [ ] T001 Crear la rama `feat/register-expense` desde `sprint-1` (`git switch -c feat/register-expense sprint-1`). La rama actual es `sprint-1`; el spec/plan usan `feat/register-expense` (Constitución XII)
- [ ] T002 Verificar la línea base en verde antes de cambios: `cd backend && mvn -B verify` y `cd frontend && npm ci && npm run lint && npm run test:coverage && npm run build`

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: todo lo estrictamente compartido por las tres historias. **Ninguna historia puede
empezar hasta terminar esta fase** (Constitución III).

### Persistencia y dominio

- [ ] T003 [P] Crear la migración nueva [V4__create_expenses.sql](backend/src/main/resources/db/migration/V4__create_expenses.sql) con la tabla `expenses` exactamente como en data-model.md: `id BIGINT GENERATED ALWAYS AS IDENTITY PK`, `user_id BIGINT NOT NULL` + `fk_expenses_user` → `users(id) ON DELETE CASCADE`, `amount NUMERIC(11,2) NOT NULL` + `CHECK (amount >= 0.01)`, `currency VARCHAR(3) NOT NULL` + `CHECK (currency IN ('ARS','USD','EUR'))`, `expense_date DATE NOT NULL`, `category VARCHAR(20) NOT NULL` + CHECK de códigos, `expense_type VARCHAR(20) NOT NULL` + `CHECK (expense_type IN ('RECURRING','ONE_TIME'))`, `payment_method VARCHAR(20) NOT NULL` + CHECK de códigos, `description VARCHAR(200) NULL` + `CHECK (description IS NULL OR char_length(description) <= 200)`, `idempotency_key UUID NOT NULL`, `created_at TIMESTAMPTZ NOT NULL DEFAULT now()`, `CONSTRAINT uq_expenses_user_idempotency_key UNIQUE (user_id, idempotency_key)`. **No editar V1–V3.** Cubre FR-003, FR-006, FR-009–FR-012, FR-014, FR-023, FR-025, FR-027
- [ ] T004 Test de migración: crear [ExpenseSchemaIT.java](backend/src/test/java/com/amano/backend/expense/ExpenseSchemaIT.java) (Testcontainers + Spring Boot) que arranca el contexto (Flyway aplica V4) y verifica que `expenses` existe con el índice único `uq_expenses_user_idempotency_key` y los CHECK clave. Criterio: `cd backend && ./mvnw verify -Dit.test=ExpenseSchemaIT` en verde
- [ ] T005 [P] Crear los enums de catálogo en [ExpenseCategory.java](backend/src/main/java/com/amano/backend/expense/ExpenseCategory.java), [ExpenseType.java](backend/src/main/java/com/amano/backend/expense/ExpenseType.java) y [PaymentMethod.java](backend/src/main/java/com/amano/backend/expense/PaymentMethod.java) con los códigos exactos de data-model.md (`FOOD, TRANSPORT, HOUSING, SERVICES, HEALTH, EDUCATION, LEISURE, SHOPPING, PETS, OTHER`; `RECURRING, ONE_TIME`; `CASH, DEBIT_CARD, CREDIT_CARD, TRANSFER, VIRTUAL_WALLET, OTHER`). Reutilizar `com.amano.backend.user.Currency` para la moneda; no duplicarla. Cubre FR-010, FR-011
- [ ] T006 Crear la entidad [Expense.java](backend/src/main/java/com/amano/backend/expense/Expense.java) mapeada a `expenses` (depende de T005): `Long userId`, `BigDecimal amount` con `@Column(precision = 11, scale = 2)`, `Currency currency` con `CurrencyConversion`, `LocalDate expenseDate`, enums con `@Enumerated(EnumType.STRING)`, `String description` nulable, `UUID idempotencyKey`, `OffsetDateTime createdAt` con `@CreationTimestamp`. Debe pasar `ddl-auto=validate`. Cubre FR-003, FR-023, FR-025
- [ ] T007 Crear [ExpenseRepository.java](backend/src/main/java/com/amano/backend/expense/ExpenseRepository.java) (depende de T006) con `Optional<Expense> findByUserIdAndIdempotencyKey(Long userId, UUID idempotencyKey)` y `Optional<Expense> findByIdAndUserId(Long id, Long userId)`. Cubre FR-027

### Utilidades puras del backend (TDD: prueba primero)

- [ ] T008 [P] Implementar [ExpenseAmountNormalizer.java](backend/src/main/java/com/amano/backend/expense/ExpenseAmountNormalizer.java) y su prueba [ExpenseAmountNormalizerTest.java](backend/src/test/java/com/amano/backend/expense/ExpenseAmountNormalizerTest.java): parsea el string canónico con `new BigDecimal`, rechaza no numérico, `scale() > 2`, `<= 0`, y fuera de `0.01`–`999999999.99`, sin redondear; compara con `compareTo`. Incluye la batería SC-004 (`0,01`, `0,10`, `0,99`, `1,00`, `1,10`, `10,10`, `100,01`, `1.234,56`, `999.999,99`, `123.456.789,01`, `999.999.999,98`, `999.999.999,99`) en ARS, USD y EUR. Cubre FR-013, FR-014, FR-022, FR-023, SC-004
- [ ] T009 [P] Implementar [ExpenseDescriptionNormalizer.java](backend/src/main/java/com/amano/backend/expense/ExpenseDescriptionNormalizer.java) y su prueba [ExpenseDescriptionNormalizerTest.java](backend/src/test/java/com/amano/backend/expense/ExpenseDescriptionNormalizerTest.java): recorte de espacios en blanco Unicode con predicado por code points (`Character.isWhitespace || Character.isSpaceChar`, incluye NBSP U+00A0, U+2007, U+202F), normalización CRLF/CR→LF, conservar tabs/saltos internos sin colapsar, vacío ⇒ `null`, máximo 200 code points contados después del recorte/normalización (199/200/201). Cubre FR-012, FR-017, FR-022
- [ ] T010 [P] Implementar [ExpenseDateValidator.java](backend/src/main/java/com/amano/backend/expense/ExpenseDateValidator.java) y su prueba [ExpenseDateValidatorTest.java](backend/src/test/java/com/amano/backend/expense/ExpenseDateValidatorTest.java): `LocalDate.now(zona)` con zona IANA; rechaza fechas posteriores a hoy; zona ausente o inválida (`ZoneId.of` en try/catch sobre `DateTimeException`/`NullPointerException`) aplica tolerancia de un día (`LocalDate.now(UTC).plusDays(1)`); nunca expone la excepción. Casos: hoy, pasado, futuro, zona adelantada, zona ausente, zona inválida, borde de tolerancia. Cubre FR-009, FR-016, FR-022, SC-011

### Seguridad y manejo de errores (cambios mínimos, con regresión)

- [ ] T011 Modificar [SecurityConfig.java](backend/src/main/java/com/amano/backend/config/SecurityConfig.java): ampliar el matcher verificado a `"/api/users/**", "/api/expenses/**"` con `verifiedAccount(userRepository)`; no tocar los `permitAll` ni los matchers existentes. Cubre FR-001, FR-002, FR-003
- [ ] T012 [P] Extender [SecurityConfigTest.java](backend/src/test/java/com/amano/backend/auth/SecurityConfigTest.java): `/api/expenses` sin cookie ⇒ 401; con cuenta pendiente ⇒ 401; con cuenta verificada pasa la autorización. Regresión: verificar que `/api/users/**` y las rutas públicas de 001/004 no cambian de comportamiento (regresión de 001 y 004)
- [ ] T013 Modificar [ProblemDetailExceptionHandler.java](backend/src/main/java/com/amano/backend/common/ProblemDetailExceptionHandler.java): agregar handlers que devuelvan `400` con mensaje genérico en español para `HttpMessageNotReadableException` (JSON malformado) y `MissingRequestHeaderException` (falta `Idempotency-Key`), sin exponer detalle interno. No alterar los handlers existentes. Cubre FR-026, SC-006
- [ ] T014 [P] Extender [ProblemDetailExceptionHandlerTest.java](backend/src/test/java/com/amano/backend/common/ProblemDetailExceptionHandlerTest.java): ambos casos devuelven 400 con cuerpo ProblemDetail seguro (sin stacktrace, clases, SQL ni datos sensibles); regresión de los handlers de 001/004/005 intactos. Cubre FR-026, SC-006

### Utilidades puras del frontend

- [ ] T015 [P] Crear [expenseCatalog.js](frontend/src/validation/expenseCatalog.js) con los códigos y etiquetas en español (moneda, categoría, tipo, medio de pago) y su prueba [expenseCatalog.test.js](frontend/src/validation/expenseCatalog.test.js) que fija la lista exacta de códigos esperada (consistencia con el backend). Cubre FR-010, FR-011
- [ ] T016 Crear [expenseValidation.js](frontend/src/validation/expenseValidation.js) con `parseArgentineAmount(raw)` (coma decimal, punto/espacio como miles, `1.234`→1234, `1,234` rechazado por 3 decimales, `1234.56` rechazado, combinaciones malformadas rechazadas, rango `0,01`–`999.999.999,99`, sin `Number`/`parseFloat`, canónico `entero.centavos`) y `preferredCurrency(currencies)` (orden ARS→USD→EUR; ausente/vacía/inválida ⇒ ARS). Prueba [expenseValidation.test.js](frontend/src/validation/expenseValidation.test.js) con todos los ejemplos del spec y SC-004. Cubre FR-011, FR-013, FR-014, SC-004
- [ ] T017 Extender [expenseValidation.js](frontend/src/validation/expenseValidation.js) con `parseArgentineDate(texto)`: acepta `dd/mm/aaaa` y `d/m/aaaa`, normaliza a dos dígitos, rechaza formato no reconocido y fechas inexistentes (`31/02`) sin corrección/rollover (validación por round-trip UTC), canónico `yyyy-MM-dd`. Ampliar [expenseValidation.test.js](frontend/src/validation/expenseValidation.test.js). Cubre FR-016, SC-011
- [ ] T018 Extender [expenseValidation.js](frontend/src/validation/expenseValidation.js) con `normalizeDescription(texto)` con la misma regla Unicode/CRLF y el límite de 200 code points que el backend. Ampliar [expenseValidation.test.js](frontend/src/validation/expenseValidation.test.js) con NBSP, tabs/saltos internos y 200/201. Cubre FR-012, FR-017

**Checkpoint**: fundaciones listas. Verificar `cd backend && mvn -B verify` y `cd frontend && npm run test:coverage` antes de empezar las historias.

---

## Phase 3: User Story 1 - Registrar un gasto personal válido (Priority: P1) 🎯 MVP

**Goal**: un usuario autenticado completa el formulario y el gasto se persiste vinculado
exclusivamente a él, con notificación de éxito y reseteo del formulario.

**Independent Test**: con sesión, cargar `/expenses/new`, completar solo campos obligatorios
válidos y confirmar; verificar notificación de éxito, reseteo de FR-032, permanencia en la
pantalla y una fila en `expenses` con el `user_id` de la sesión y el monto exacto.

### Backend

- [ ] T019 [US1] Crear el DTO de entrada [RegisterExpenseRequest.java](backend/src/main/java/com/amano/backend/expense/RegisterExpenseRequest.java) como clase (no record) con `amount`, `currency`, `date`, `category`, `type`, `paymentMethod`, `description`, `deviceTimeZone` y un mapa capturado con `@JsonAnySetter` (`unknownProperties`). Cubre FR-007
- [ ] T020 [US1] Crear el DTO de salida [ExpenseResponse.java](backend/src/main/java/com/amano/backend/expense/ExpenseResponse.java) como record con `id`, `amount` (String canónico), `currency`, `date` (`yyyy-MM-dd`), `category`, `type`, `paymentMethod`, `description` (omitido si `null`) y `createdAt`; mapeo desde `Expense`. Cubre FR-025
- [ ] T021 [US1] Implementar [ExpenseService.java](backend/src/main/java/com/amano/backend/expense/ExpenseService.java) (depende de T006–T010) **y crear [ExpenseServiceTest.java](backend/src/test/java/com/amano/backend/expense/ExpenseServiceTest.java)**: `registerExpense(Long userId, UUID idempotencyKey, RegisterExpenseRequest)` `@Transactional` que normaliza con las utilidades; valida **ausencia/nulidad de todo campo obligatorio** (`amount`, `currency`, `date`, `category`, `type`, `paymentMethod`) como error de campo antes de cualquier `valueOf`/parseo, de modo que un valor ausente o `null` nunca produzca `NullPointerException` (`500`) sino `FieldValidationException` (`400`); valida catálogos con `valueOf` acumulando errores en `FieldValidationException`; consulta `findByUserIdAndIdempotencyKey` y, si existe (replay básico), devuelve el gasto sin insertar; si no, persiste con `user_id = userId` y devuelve la respuesta. Sin datos parciales. La prueba cubre catálogos manipulados y campos ausentes/nulos (uno por campo, y en combinación) ⇒ error por campo, un solo pase y `Mockito.verify(repository, never()).save(...)`. La comparación de payload y el `422` se agregan en US3 (T040). Cubre FR-011, FR-021, FR-022, FR-024, FR-025, FR-027, SC-002
- [ ] T022 [US1] Crear [ExpenseController.java](backend/src/main/java/com/amano/backend/expense/ExpenseController.java) con `POST /api/expenses`: lee `Idempotency-Key` (obligatoria, UUID), obtiene el `userId` de `SecurityContextHolder`, delega en el servicio y responde `201` en creación o `200` en replay; 401 si falta el principal (patrón `UserController`). Cubre FR-001, FR-003, FR-025, FR-027
- [ ] T023 [US1] Crear [RegisterExpenseIT.java](backend/src/test/java/com/amano/backend/expense/RegisterExpenseIT.java) (MockMvc + Testcontainers): creación válida vinculada al usuario de la sesión (`user_id` correcto, sin otros participantes), lectura exacta de la batería SC-004 en ARS/USD/EUR desde PostgreSQL, `401` sin cookie, `401` con cuenta pendiente, `201` con la representación esperada, validación de zona horaria informada (envío de `deviceTimeZone` de una zona adelantada con la fecha del día de esa zona ⇒ `201`; sin zona ⇒ tolerancia de un día), y rechazo de **catálogo manipulado** (`currency`/`category`/`type`/`paymentMethod` fuera de enum) ⇒ `400` sin persistencia. Cubre US1-1, US1-2, US1-3, US1-11, FR-003, FR-009, FR-011, FR-022, FR-025, SC-001, SC-002, SC-004

### Frontend

- [ ] T024 [P] [US1] Crear [expenseClient.js](frontend/src/api/expenseClient.js) con `createExpense(payload, idempotencyKey)` (la firma recibe un único `payload`; `deviceTimeZone` viaja **dentro** del body, no como argumento aparte) que envía `POST /api/expenses` con `credentials:'include'`, `Content-Type: application/json` y la cabecera `Idempotency-Key`; retorna `{ ok:true, expense }` en `201`/`200`, `{ ok:false, status, fields, detail }` en 400, `{ ok:false, status:422, reusedKey:true, detail }` en 422 (el manejo de UI es T043) y `{ ok:false, network:true }` en 5xx/red (convención de `authClient`). Prueba [expenseClient.test.js](frontend/src/api/expenseClient.test.js): forma de la petición, cabecera, presencia de `deviceTimeZone` en el body, éxito, 400 por campo, 401, 422 y red. Cubre FR-019, FR-026, FR-027
- [ ] T025 [P] [US1] Crear [SelectField.jsx](frontend/src/components/SelectField.jsx) reutilizable (label asociada, `aria-invalid`, `aria-describedby`, mensaje con `role="alert"`, opciones desde `expenseCatalog.js`), siguiendo [TextField.jsx](frontend/src/components/TextField.jsx). Prueba [SelectField.test.jsx](frontend/src/components/SelectField.test.jsx). Cubre FR-008, FR-033
- [ ] T026 [US1] Crear el hook [useCurrentDate.js](frontend/src/pages/useCurrentDate.js) o `frontend/src/hooks/useCurrentDate.js`: devuelve el día actual en la zona del dispositivo (`Intl.DateTimeFormat().resolvedOptions().timeZone`), se actualiza con `setInterval` (30 s) y `visibilitychange`, y solo reemplaza el valor si el campo no fue modificado. Prueba con `vi.useFakeTimers()`. Cubre FR-009, US1-4, US1-12
- [ ] T027 [US1] Crear [NewExpensePage.jsx](frontend/src/pages/NewExpensePage.jsx): precarga la moneda con `me()` y `preferredCurrency` (perfil ausente/vacío/inválido ⇒ ARS; sin conexión ⇒ ARS + aviso no bloqueante), precarga la fecha con `useCurrentDate`, y compone el formulario. Cubre FR-001, FR-009, FR-011
- [ ] T028 [US1] Modificar [App.jsx](frontend/src/App.jsx): agregar la ruta protegida `/expenses/new` (`<ProtectedRoute><NewExpensePage /></ProtectedRoute>`), sin alterar las rutas existentes; extender [App.test.jsx](frontend/src/App.test.jsx) con el caso de `/expenses/new` protegida (sin sesión ⇒ redirección a `/login`). Cubre FR-001, FR-002
- [ ] T029 [US1] Crear [ExpenseForm.jsx](frontend/src/components/ExpenseForm.jsx): campos obligatorios (monto, moneda, fecha, categoría, tipo, medio de pago) y descripción opcional, envío vía `expenseClient.createExpense` con `deviceTimeZone` obtenido con `Intl.DateTimeFormat().resolvedOptions().timeZone` incluido en el payload, y con el ciclo de vida de la clave de idempotencia: se genera con `crypto.randomUUID()` al iniciar cada operación, se **conserva** ante un fallo (validación, red o `422`) para reintentar la misma operación y se **renueva** solo tras un guardado exitoso (junto con el reseteo de FR-032), botón deshabilitado con texto "Guardando…" mientras la operación está en curso, notificación de éxito con `role="status"` que se cierra a los 5 s o antes por botón, y reseteo exacto de FR-032 (monto y descripción vacíos; fecha del día; moneda según perfil; categoría/tipo/medio sin selección) manteniendo al usuario en la pantalla. Cubre FR-007, FR-008, FR-027 (estado), FR-030, FR-031, FR-032, US1-1, US1-14
- [ ] T030 [US1] Prueba [ExpenseForm.test.jsx](frontend/src/components/ExpenseForm.test.jsx) (éxito): envío válido, notificación de éxito, cierre automático a 5 s y cierre manual, reseteo exacto de FR-032 y permanencia en la pantalla. Cubre SC-007, US1-1
- [ ] T031 [US1] Prueba [NewExpensePage.test.jsx](frontend/src/pages/NewExpensePage.test.jsx): precarga de fecha y de moneda (perfil presente/ausente/vacío/inválido/no consultable con aviso), actualización por cruce de medianoche con timers simulados, y redirección 401 por `ProtectedRoute`. Cubre FR-009, FR-011, US1-4, US1-12, US1-15
- [ ] T032 [US1] Verificación de la historia: `cd backend && mvn -B verify` y `cd frontend && npm run lint && npm run test:coverage && npm run build`, con `RegisterExpenseIT`, `ExpenseForm.test.jsx` y `NewExpensePage.test.jsx` en verde

**Checkpoint**: US1 funcional y probable de forma independiente (MVP).

---

## Phase 4: User Story 2 - Validación de datos y bloqueo del envío (Priority: P2)

**Goal**: datos inválidos muestran mensajes por campo, se conserva lo ingresado y el envío se
bloquea hasta que todo sea válido.

**Independent Test**: enviar con cada campo obligatorio vacío, monto inválido (vacío/cero/
negativo/no numérico/3 decimales/punto decimal/fuera de rango), fecha inválida (`31/02`,
formato no reconocido, futura) y descripción de 201 caracteres; comprobar mensaje por campo,
envío bloqueado y cero persistencia.

- [ ] T033 [P] [US2] **Ampliar** [ExpenseServiceTest.java](backend/src/test/java/com/amano/backend/expense/ExpenseServiceTest.java) (creado en US1, T021) (Mockito): validación de todos los campos en un solo pase; `Mockito.verify(repository, never()).save(...)` ante cualquier error; montos y descripción límite. Cubre FR-021, FR-022, FR-024, SC-002
- [ ] T034 [US2] Conectar en [ExpenseForm.jsx](frontend/src/components/ExpenseForm.jsx) las reglas de `expenseValidation.js` a react-hook-form (`mode: 'onBlur'`), pasando `error` por campo a `TextField`/`SelectField`, bloqueando el envío mientras haya errores, y conservando los datos tras un intento fallido. Cubre FR-013, FR-014, FR-015, FR-016, FR-017, FR-018, FR-020
- [ ] T035 [US2] Mapear los errores `400` del backend en [ExpenseForm.jsx](frontend/src/components/ExpenseForm.jsx): `properties.fields` ⇒ `setError` por campo (monto/fecha/categoría/tipo/medio/descripción) sin limpiar el formulario; `detail` genérico ⇒ mensaje de formulario. Cubre FR-019, FR-026, US3-6
- [ ] T036 [US2] Ampliar [ExpenseForm.test.jsx](frontend/src/components/ExpenseForm.test.jsx) (validación): un caso por campo obligatorio vacío, monto vacío/cero/negativo/no numérico/3 decimales/`1234.56`/fuera de rango, fecha vacía/`31/02`/futura, descripción de 201; en todos: mensaje por campo, envío bloqueado, datos conservados y sin llamada al cliente. Cubre FR-013–FR-020, US2-1 a US2-6, US2-10, US2-11, SC-002
- [ ] T037 [US2] Cubrir en [ExpenseForm.test.jsx](frontend/src/components/ExpenseForm.test.jsx) y [expenseClient.test.js](frontend/src/api/expenseClient.test.js) la matriz de errores de SC-006: campos vacíos, monto no numérico/cero/negativo/3 decimales/fuera de rango/punto decimal, fecha vacía/`31/02`/futura, descripción >200, catálogos manipulados, sesión expirada y falla de red; verificar que ningún mensaje de interfaz ni cuerpo de error contiene trazas, clases/paquetes, SQL, tokens, cookies, correo ni datos de otros usuarios. Cubre FR-019, FR-026, SC-006
- [ ] T038 [US2] Verificación de la historia: `cd backend && mvn -B verify` y `cd frontend && npm run lint && npm run test:coverage && npm run build`, con `ExpenseServiceTest` y los casos de validación de `ExpenseForm.test.jsx` en verde

**Checkpoint**: US1 y US2 funcionan y se prueban de forma independiente.

---

## Phase 5: User Story 3 - Protección de la identidad, sesión y resiliencia (Priority: P3)

**Goal**: el gasto solo puede pertenecer al usuario de la sesión; se evitan duplicados
accidentales (doble clic, timeout con persistencia previa, concurrencia); sesión expirada y
fallas de red no producen falsos éxitos ni datos parciales.

**Independent Test**: enviar un cuerpo con otro creador/participantes/divisiones (rechazo sin
persistencia); reintentar el mismo envío con la misma clave y el mismo payload (un solo gasto,
`200`); reintentar con la misma clave y el payload editado (`422`, un solo gasto); dos
solicitudes concurrentes con la misma clave (un solo gasto); dos gastos idénticos con claves
distintas (dos gastos); guardar sin sesión y con el servidor caído (mensaje claro, sin éxito,
datos conservados).

- [ ] T039 [US3] Rechazar en [ExpenseService.java](backend/src/main/java/com/amano/backend/expense/ExpenseService.java) las propiedades reservadas de `RegisterExpenseRequest.unknownProperties` (`userId`, `creatorId`, `creator`, `user`, `participants`, `participantIds`, `splits`, `divisions`, `shares`, `debts`, `friends`, `groups`) con error por campo (`400`, nada persistido). Extender [ExpenseServiceTest.java](backend/src/test/java/com/amano/backend/expense/ExpenseServiceTest.java). Cubre FR-004, FR-005, FR-006, US3-3, SC-001
- [ ] T040 [US3] Implementar la resolución de idempotencia en [ExpenseService.java](backend/src/main/java/com/amano/backend/expense/ExpenseService.java) y [ExpenseController.java](backend/src/main/java/com/amano/backend/expense/ExpenseController.java): al existir la clave, comparar el payload normalizado (`amount` con `compareTo`, `currency`, `expense_date`, `category`, `expense_type`, `payment_method`, `description` canónica) contra la fila persistida; coincidencia ⇒ `200` con el recurso original; diferencia ⇒ crear [IdempotencyConflictException.java](backend/src/main/java/com/amano/backend/expense/IdempotencyConflictException.java) y lanzarla (`422`, sin insertar). Para la carrera, capturar `DataIntegrityViolationException` fuera de la transacción revertida, releer al ganador por `(user_id, idempotency_key)` y aplicar la misma comparación (`200`/`422`); si no se encuentra, propagar error inesperado. Cubre FR-027, US3-4, US3-7, SC-003
- [ ] T041 [US3] Agregar en [ProblemDetailExceptionHandler.java](backend/src/main/java/com/amano/backend/common/ProblemDetailExceptionHandler.java) el handler de `IdempotencyConflictException` ⇒ `422 Unprocessable Content` con mensaje accionable en español y `properties.code = "IDEMPOTENCY_KEY_REUSED"`, sin detalle interno (no alterar los handlers existentes). Probar en [ProblemDetailExceptionHandlerTest.java](backend/src/test/java/com/amano/backend/common/ProblemDetailExceptionHandlerTest.java). Cubre FR-026, FR-027, SC-006
- [ ] T042 [US3] Ampliar [RegisterExpenseIT.java](backend/src/test/java/com/amano/backend/expense/RegisterExpenseIT.java) con idempotencia real: (a) reintento con la misma clave y el mismo payload tras persistencia previa ⇒ `200` y **una** fila; (b) misma clave con payload distinto ⇒ `422` y **una** fila (el original intacto); (c) dos envíos concurrentes con la misma clave y el mismo payload ⇒ una fila y respuestas `201`/`200`; (d) dos envíos concurrentes con la misma clave y distinto payload ⇒ una fila y respuestas `201`/`422`; (e) dos gastos idénticos con claves distintas ⇒ dos filas. Cubre FR-027, US3-4, US3-7, SC-003, SC-008
- [ ] T043 [US3] Manejar en [ExpenseForm.jsx](frontend/src/components/ExpenseForm.jsx) el `422` de [expenseClient.js](frontend/src/api/expenseClient.js): renovar la clave de idempotencia, informar que la operación anterior ya quedó registrada con datos distintos y habilitar un nuevo confirmar, conservando los datos. Prueba en [ExpenseForm.test.jsx](frontend/src/components/ExpenseForm.test.jsx). Cubre FR-027, US3-4
- [ ] T044 [US3] Implementar en [ExpenseForm.jsx](frontend/src/components/ExpenseForm.jsx) la protección de doble clic/reenvío: mantener el botón deshabilitado con "Guardando…" durante toda la operación (el ciclo de vida de la clave de idempotencia se define en T029). Prueba en [ExpenseForm.test.jsx](frontend/src/components/ExpenseForm.test.jsx) con doble clic. Cubre FR-027, US3-4
- [ ] T045 [US3] Manejar en [ExpenseForm.jsx](frontend/src/components/ExpenseForm.jsx) la sesión expirada/`401` al confirmar: no persistir, informar y reutilizar el login existente; conservar los datos solo mientras el formulario siga visible, sin borradores ni restauración posterior. Prueba en [ExpenseForm.test.jsx](frontend/src/components/ExpenseForm.test.jsx). Cubre FR-028, US3-2, US3-6
- [ ] T046 [US3] Manejar en [ExpenseForm.jsx](frontend/src/components/ExpenseForm.jsx) la falla de servidor/red: mensaje claro, sin notificación de éxito, sin datos parciales y con los datos conservados; prueba de que el backend no deja datos parciales ante fallo (verificar en [RegisterExpenseIT.java](backend/src/test/java/com/amano/backend/expense/RegisterExpenseIT.java)). Cubre FR-029, FR-030, SC-005, SC-008, US3-5
- [ ] T047 [US3] Verificar accesibilidad y móvil: recorrido solo con teclado, etiquetas asociadas, foco visible y errores asociados al campo (`aria-invalid`/`aria-describedby`) en [ExpenseForm.jsx](frontend/src/components/ExpenseForm.jsx) y [SelectField.jsx](frontend/src/components/SelectField.jsx); sin desplazamiento horizontal en viewport móvil. Pruebas en [ExpenseForm.test.jsx](frontend/src/components/ExpenseForm.test.jsx). Cubre FR-033, FR-034, SC-009, SC-010
- [ ] T048 [US3] Verificación de la historia: `cd backend && mvn -B verify` y `cd frontend && npm run lint && npm run test:coverage && npm run build`, con `RegisterExpenseIT` (idempotencia, incl. payload distinto ⇒ `422`, y concurrencia) y los casos de resiliencia de `ExpenseForm.test.jsx` en verde

**Checkpoint**: las tres historias funcionan y se prueban de forma independiente.

---

## Phase 6: Polish & Cross-Cutting Concerns

**Purpose**: cerrar calidad, artefactos SDD y PR (DoD).

- [ ] T049 Verificar cobertura backend con la herramienta configurada: `cd backend && mvn -B verify`; confirmar JaCoCo ≥ 85 % de líneas (BUNDLE LINE 0.85) sin reducir el umbral ni excluir código arbitrario
- [ ] T050 [P] Verificar cobertura frontend con la herramienta configurada: `cd frontend && npm run test:coverage`; confirmar ≥ 85 % de líneas/sentencias sin reducir umbrales ni excluir código arbitrario (revisar que `vite.config.js` siga excluyendo solo `src/main.jsx`)
- [ ] T051 [P] Ejecutar la validación de [quickstart.md](specs/007-register-expense/quickstart.md) end-to-end (registro, validación, precisión, duplicados, identidad/sesión, errores, accesibilidad/móvil) y registrar el resultado
- [ ] T052 [P] Actualizar los artefactos SDD afectados si la implementación difiere del diseño: [plan.md](specs/007-register-expense/plan.md), [research.md](specs/007-register-expense/research.md), [data-model.md](specs/007-register-expense/data-model.md), [contracts/expenses-api.md](specs/007-register-expense/contracts/expenses-api.md) y checklists de [checklists/](specs/007-register-expense/checklists/)
- [ ] T053 Abrir el Pull Request de `feat/register-expense` hacia `sprint-1` (Constitución XII), con resumen de FR/SC cubiertos, evidencia de `mvn -B verify` y `npm run test:coverage`, y sin información sensible ni artefactos de depuración

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: sin dependencias.
- **Foundational (Phase 2)**: depende de Setup; **bloquea** las tres historias.
- **User Stories (Phases 3–5)**: dependen de Foundational.
- **Polish (Phase 6)**: depende de que las historias deseadas estén completas.

### Within Foundational

- T003 → T004 (la prueba consume la migración).
- T005 → T006 → T007.
- T008, T009, T010 son independientes entre sí [P] y respecto de T005–T007.
- T011 → T012; T013 → T014.
- T015 independiente [P]; T016 → T017 → T018 (mismo archivo, secuenciales).

### User Story Dependencies

- **US1 (P1)**: puede empezar tras Foundational. **MVP**.
- **US2 (P2)**: depende de US1 (reutiliza `ExpenseForm.jsx`) y de Foundational.
- **US3 (P3)**: depende de US1 (servicio/controlador/formulario) y de Foundational; además
  **depende de US2** en la superficie de `ExpenseForm.jsx`/`ExpenseForm.test.jsx` (ambas
  historias editan esos archivos intensivamente). No tiene bloqueos por decisiones: la decisión
  de T040/T041/T043 (payload distinto ⇒ `422`) quedó cerrada en [research.md](./research.md) §D1
  y [contracts/expenses-api.md](./contracts/expenses-api.md).
- Dentro de US3: T040 → T041 (el handler usa la excepción de T040) → T042; T039 es
  independiente; T043 → T044.
- Por lo anterior, **US2 y US3 se ejecutan de forma secuencial** (US2 antes que US3) para no
  romper la entrega incremental ni generar conflictos en los archivos compartidos; los
  subconjuntos backend de ambas pueden solaparse.

### Parallel Opportunities

- Foundational: T003, T005, T008, T009, T010, T011, T013, T015 (archivos distintos).
- US1: T024, T025 en paralelo; T026/T027/T028/T029 secuenciales sobre componentes
  compartidos.
- US2/US3: solo las pruebas [P] de archivos distintos y las tareas backend entre sí; las
  tareas que tocan `ExpenseForm.jsx`/`ExpenseForm.test.jsx` son secuenciales (US2 → US3).

---

## Parallel Example: Foundational

```bash
# En paralelo, archivos distintos:
Task: "T003 Migración V4__create_expenses.sql"
Task: "T005 Enums ExpenseCategory/ExpenseType/PaymentMethod"
Task: "T008 ExpenseAmountNormalizer + test"
Task: "T009 ExpenseDescriptionNormalizer + test"
Task: "T010 ExpenseDateValidator + test"
Task: "T015 expenseCatalog.js + test"
```

## Parallel Example: User Story 1

```bash
# En paralelo, archivos distintos:
Task: "T024 expenseClient.js + test"
Task: "T025 SelectField.jsx + test"
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Phase 1 (Setup) → Phase 2 (Foundational) → Phase 3 (US1).
2. **STOP and VALIDATE**: `RegisterExpenseIT`, `ExpenseForm.test.jsx`,
   `NewExpensePage.test.jsx` y `mvn -B verify`/`test:coverage` en verde.
3. Es el primer incremento entregable: un gasto personal se registra y persiste.

### Incremental Delivery

1. Foundational → US1 (MVP) → US2 (validación) → US3 (identidad/sesión/duplicados).
2. Cada historia se prueba de forma independiente y no rompe las anteriores
   (Constitución III).

### Notes

- [P] = archivos distintos, sin dependencias pendientes.
- Las tareas **no** incluyen listado, edición, eliminación, división de gastos, deudas, grupos,
  amigos, borradores, conversión de monedas ni mecanismos de autenticación nuevos (Out of
  Scope del spec).
- No queda ninguna tarea bloqueada: la decisión "misma clave con payload distinto ⇒ `422`" está
  cerrada en el contrato (research.md §D1) y se implementa en US3.
- No se editan las migraciones V1–V3 (Constitución, sección Bases de datos).
