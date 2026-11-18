---

description: "Lista de tareas de la feature 033: ingresos personales y balance neto"
---

# Tasks: Ingresos personales y balance neto

**Input**: Documentos de diseño en `/specs/033-income-management/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/personal-incomes-api.md, quickstart.md

**Tests**: se incluyen porque la constitución (principio V) los exige: cobertura de líneas ≥ 85 % en backend (JaCoCo, `mvn verify`) y frontend (`npm run test:coverage`).

**Organization**: las tareas se agrupan por historia de usuario (spec.md). Dentro de cada fase el orden es DB → backend core → endpoints → frontend → tests. Cada fase es un incremento verificable (principio III).

## Format: `[ID] [P?] [Story] Description`

- **[P]**: se puede hacer en paralelo (archivos distintos, sin dependencia de tareas incompletas)
- **[Story]**: US1 a US5 de spec.md
- Rutas relativas a la raíz del repositorio. `BE` = `backend/src/main/java/com/amano/backend`, `BT` = `backend/src/test/java/com/amano/backend`, `FE` = `frontend/src`.

## Regla de inmutabilidad (aplica a todas las fases)

NO hay tareas de endpoints ni de interfaz para editar (`PATCH`/`PUT`) ni eliminar (`DELETE`) ingresos. Solo se crean y se consultan. Las tareas de US3 verifican su ausencia.

---

## Phase 1: Setup

**Purpose**: línea base antes de tocar código

- [x] T001 [P] Ejecutar `mvn -B verify` en `backend/` y anotar el resultado y la cobertura de línea base (debe estar en verde antes de empezar)
- [x] T002 [P] Ejecutar `npm run lint && npm run test:coverage` en `frontend/` y anotar el resultado y la cobertura de línea base

---

## Phase 2: Foundational (bloquea todas las historias)

**Purpose**: base de datos, entidad, seguridad y piezas compartidas del frontend

### Base de datos y backend core

- [x] T003 Crear `backend/src/main/resources/db/migration/V38__create_incomes.sql` con el `CREATE TABLE incomes` y el índice `ix_incomes_user_date` copiados de `plan.md` → "Esquema de base de datos y migración". Restricciones literales: `amount NUMERIC(11,2) NOT NULL` con `CHECK (amount >= 0.01)`; `currency VARCHAR(3) NOT NULL` con CHECK en las 32 monedas de V29; `income_date DATE NOT NULL`; `category VARCHAR(20) NOT NULL` con CHECK `('SALARY','FREELANCE','SALE','REFUND','OTHER')`; `payment_method VARCHAR(20) NOT NULL` con CHECK `('CASH','TRANSFER','VIRTUAL_WALLET','OTHER')`; `description VARCHAR(200) NULL` con CHECK `char_length(description) <= 200`; `idempotency_key UUID NOT NULL`; `created_at TIMESTAMPTZ NOT NULL DEFAULT now()`; `UNIQUE (user_id, idempotency_key)`; FK a `users(id)` `ON DELETE CASCADE`. Solo `CREATE` (sin `DROP`/`DELETE`)
- [x] T004 [P] Crear los enums `IncomeCategory` (`SALARY, FREELANCE, SALE, REFUND, OTHER`) e `IncomePaymentMethod` (`CASH, TRANSFER, VIRTUAL_WALLET, OTHER`) en `BE/income/IncomeCategory.java` e `BE/income/IncomePaymentMethod.java`, con los mismos códigos que los CHECK de T003
- [x] T005 Crear la entidad JPA `Income` en `BE/income/Income.java` mapeada a `incomes` (columnas de data-model.md: `userId`, `amount` BigDecimal, `currency` `Currency`, `incomeDate` LocalDate, `category`, `paymentMethod`, `description`, `idempotencyKey` UUID, `createdAt`); solo getters públicos (sin `updated_at` ni setters de edición, los setters son package-private para el servicio). Depende de T003 y T004
- [x] T006 Crear `BE/income/IncomeRepository.java` (`JpaRepository<Income, Long>` + `JpaSpecificationExecutor<Income>`) con `findByUserIdAndIdempotencyKey(Long, UUID)`, `findByIdAndUserId(Long, Long)` y una consulta `findIncomeYears(userId)` (años distintos de `income_date`, más reciente primero), siguiendo `ExpenseRepository`
- [x] T007 [P] Crear `BT/income/IncomeSchemaIT.java` (base real, como `ExpenseSchemaIT`): la migración V38 aplica y la base rechaza `amount = 0`, categoría o medio de cobro fuera del catálogo, moneda fuera de las 32, descripción de 201 caracteres, y un segundo `(user_id, idempotency_key)` repetido

### Seguridad y errores

- [x] T008 Agregar `/api/incomes/**` y `/api/balance/**` al `requestMatchers(...).access(verifiedAccount(userRepository))` de `BE/config/SecurityConfig.java` (hoy solo cubre users, expenses, groups, payments y currencies)
- [x] T009 [P] Crear `BE/income/IncomeNotFoundException.java` y su handler en `BE/common/ProblemDetailExceptionHandler.java`: `404` ProblemDetail con título "Ingreso no encontrado" y detalle "No encontramos ese ingreso" (mismo cuerpo para inexistente, ajeno o id no numérico)

### Piezas compartidas del frontend

- [x] T010 [P] Crear `FE/api/problemDetail.js` exportando `problemOf` y `safeFields` (hoy privados en `FE/api/expenseClient.js`) y hacer que `FE/api/expenseClient.js` los importe; `FE/api/expenseClient.test.js` sigue pasando sin cambios
- [x] T011 [P] Agregar a `FE/validation/expenseCatalog.js` `INCOME_CATEGORY_OPTIONS` (`SALARY` Sueldo, `FREELANCE` Freelance, `SALE` Venta, `REFUND` Reembolso, `OTHER` Otros) e `INCOME_PAYMENT_METHOD_OPTIONS` (`CASH` Efectivo, `TRANSFER` Transferencia, `VIRTUAL_WALLET` Billetera virtual, `OTHER` Otro), y cubrirlos en `FE/validation/expenseCatalog.test.js` (códigos exactos, igual que los CHECK de T003)

**Checkpoint**: `mvn -B verify` y `npm run test:coverage` siguen en verde; la tabla `incomes` existe.

---

## Phase 3: User Story 1 - Registrar un ingreso y ver el balance neto (Priority: P1) 🎯 MVP

**Goal**: la persona registra un ingreso (con `Idempotency-Key`) y ve el balance neto del período actualizado.

**Independent Test**: con gastos cargados, `POST /api/incomes` devuelve `201`; repetir la misma llamada devuelve `200` sin duplicar; `GET /api/balance` devuelve `net = income − expenses`; en la interfaz, "Nuevo ingreso" guarda y la tarjeta muestra el nuevo balance.

### Backend core

- [x] T012 [P] [US1] Crear `BE/income/RegisterIncomeRequest.java` con campos String `amount`, `currency`, `date`, `category`, `paymentMethod`, `description`, `deviceTimeZone` y el mismo manejo de propiedades desconocidas/reservadas que `BE/expense/RegisterExpenseRequest.java`
- [x] T013 [P] [US1] Crear `BE/income/IncomeResponse.java` (record `@JsonInclude(NON_NULL)`: `id`, `amount`, `currency`, `date`, `category`, `paymentMethod`, `description`, `projected`, `createdAt`, `conversions`; `Conversion(currency, amount)`) con `from(Income, List<Conversion>)` donde `projected = incomeDate > hoy (UTC)`
- [x] T014 [US1] Crear `BE/income/IncomeService.java` con `registerIncome(userId, idempotencyKey, request)` y `resolveConcurrentRegistration(...)` replicando `BE/expense/ExpenseService.java`: validación acumulada en un solo paso (`FieldValidationException`) reutilizando `ExpenseAmountNormalizer`, `ExpenseDescriptionNormalizer` y `ExpenseClassification.parseCatalog`; moneda inválida → `fields.currency`; fecha obligatoria, `yyyy-MM-dd`, no posterior a hoy + 1 año en la zona `deviceTimeZone` (UTC si falta o es inválida); reintento con el mismo cuerpo normalizado → `Registration(created=false)`; otro cuerpo → `IdempotencyConflictException` (`422`). Depende de T005, T006, T012, T013
- [x] T015 [US1] Crear `BE/income/IncomeController.java` con **solo** `POST /api/incomes` (`Idempotency-Key` obligatoria y UUID → `400` si no, `201` creado / `200` reintento, `DataIntegrityViolationException` → `resolveConcurrentRegistration`), copiando el patrón de `ExpenseController.create`; el dueño sale de la sesión (`401` sin sesión). Depende de T014
- [x] T016 [US1] Crear `BE/income/IncomePeriod.java`: record + validador compartido de `year` (1–9999), `month` (1–12, requiere `year`), `from`/`to` (`yyyy-MM-dd`, `from` ≤ `to`) con los mensajes de contracts/personal-incomes-api.md (`El año no es válido`, `El mes no es válido`, `Elegí un año para filtrar por mes`, `La fecha no es válida`, `La fecha desde no puede ser posterior a la fecha hasta`); lo usan balance y búsqueda
- [x] T017 [US1] Agregar a `BE/expense/ExpenseRepository.java` una consulta agregada aditiva `sumByCurrencyAndDate(userId, from, to)` que devuelve `(currency, expense_date, suma)` de los gastos personales ya registrados en el período (no modifica las consultas existentes)
- [x] T018 [US1] Agregar a `BE/income/IncomeRepository.java` la consulta agregada `sumByCurrencyAndDate(userId, from, to)` análoga (`(currency, income_date, suma)`) para ingresos; depende de T006

### Balance

- [x] T019 [P] [US1] Crear `BE/balance/BalanceResponse.java` (record `currency`, `income`, `projectedIncome`, `expenses`, `net` como strings de 2 decimales; `projectedIncome` es nullable y se omite del JSON con `@JsonInclude(NON_NULL)` cuando no hay cotización ni de su fecha ni de hoy)
- [x] T020 [US1] Crear `BE/balance/BalanceService.java`: moneda principal = ARS→USD→EUR→otra de `User.getCurrencies()` (ARS si vacío, misma regla que `preferredCurrency()` del frontend); agrupa por `(moneda, fecha)` (T017, T018) y separa ingresos con fecha ≤ hoy (UTC) de los de fecha > hoy (UTC). Ingresos efectivizados y gastos: `ExchangeRateProvider.getQuote(moneda, principal, fecha)` (sin cotización si ya es la principal) y una `ExchangeRateUnavailableException` se propaga (→ `503`, sin balance parcial). Ingresos proyectados: se convierten con la cotización de su fecha y, si no existe (lo normal en una fecha futura), con la cotización de **hoy** (UTC) como fallback; si tampoco hay cotización de hoy, `projectedIncome` se omite (`null`) y el balance responde `200`: el `503` solo se propaga por ingresos efectivizados y gastos. Redondeo `HALF_UP` a 2 decimales; `net = income − expenses` (los proyectados no entran). Depende de T016–T019
- [x] T021 [US1] Crear `BE/balance/BalanceController.java` con **solo** `GET /api/balance?year&month&from&to` (valida con `IncomePeriod`, `400` con `properties.fields`; sin sesión `401`). Depende de T020

### Backend tests

- [x] T022 [P] [US1] Crear `BT/income/IncomeServiceTest.java`: alta válida, descripción solo espacios = ausente, monto `0`/negativo/3 decimales/sobre el tope, moneda inválida, fecha vacía/mal formada/más de 1 año adelante, categoría y medio de cobro inválidos, reintento idéntico, clave reutilizada con otro cuerpo
- [x] T023 [P] [US1] Crear `BT/income/RegisterIncomeIT.java`: `POST` → `201`, reintento → `200` mismo `id`, otro cuerpo → `422` con `properties.code = IDEMPOTENCY_KEY_REUSED`, sin cabecera/UUID inválido → `400`, `400` con `properties.fields.*` por campo, `401` sin sesión y `401` con cuenta no verificada
- [x] T024 [P] [US1] Crear `BT/balance/BalanceServiceTest.java`: neto positivo y negativo, sin ingresos, sin gastos, regla de moneda principal (lista vacía, solo BRL, ARS+USD), ingresos proyectados fuera del neto, conversión con cotización de la fecha, cotización faltante → excepción, redondeo; ingreso proyectado en USD: usa la cotización de hoy cuando no hay una de su fecha futura, y si tampoco hay cotización de hoy `projectedIncome` queda `null` sin fallar el balance; cotización faltante de un gasto o ingreso efectivizado sí falla
- [x] T025 [US1] Crear `BT/balance/BalanceIT.java`: `GET /api/balance` con ingresos y gastos, filtros `year`/`month`/`from`/`to` (incl. `400`), `401` (sin sesión y cuenta no verificada) y `503` ante cotización no disponible. Depende de T021

### Frontend

- [x] T026 [P] [US1] Crear `FE/api/incomeClient.js` con `createIncome(payload, idempotencyKey)` (header `Idempotency-Key`, misma forma de retorno que `createExpense`: `{ok, income}`; `422` → `reusedKey`; `fields` desde `properties.fields`; 5xx/red → `network`) y `getBalance({year, month, from, to})`; usa `FE/api/problemDetail.js`. **Sin** funciones de edición ni borrado
- [x] T027 [P] [US1] Agregar a `FE/validation/expenseValidation.js` `validateIncomeDate` (formato `dd/mm/aaaa` existente; error `INCOME_DATE_TOO_FAR` = "La fecha no puede ser más de un año posterior a hoy" si pasa de hoy + 1 año) y probarlo en `FE/validation/expenseValidation.test.js`
- [x] T028 [P] [US1] Agregar la prop `title` a `FE/components/ExpenseSummaryDialog.jsx` (por defecto `SUMMARY_TITLE`, "Confirmá el gasto") sin cambiar el uso actual
- [x] T029 [US1] En `FE/components/ExpenseForm.jsx` agregar la prop `kind` (`'EXPENSE'` por defecto) y un mapa de textos y nombres de campo por `kind` (mensaje de éxito "El ingreso se guardó correctamente", confirmación de descarte "¿Descartar el ingreso? Se perderán los datos cargados", título del resumen "Confirmá el ingreso" vía la prop `title` de T028, `FIELD_NAMES`). Con `kind='EXPENSE'` no cambia nada y los tests existentes de `FE/components/ExpenseForm.test.jsx` siguen verdes. Depende de T028
- [x] T030 [US1] En `FE/components/ExpenseForm.jsx` con `kind='INCOME'`: categorías de `INCOME_CATEGORY_OPTIONS`, campo "Medio de cobro" obligatorio con `INCOME_PAYMENT_METHOD_OPTIONS` (mensaje "El medio de cobro es obligatorio"), ocultar Tipo y Frecuencia, y filas del resumen con "Medio de cobro" y sin Tipo/Frecuencia. Depende de T011, T029
- [x] T031 [US1] En `FE/components/ExpenseForm.jsx` con `kind='INCOME'`: quitar el `max` de `DatePickerField`, validar la fecha con `validateIncomeDate` y armar el payload `{amount, currency, date, category, paymentMethod, description?, deviceTimeZone}` (sin `type` ni `frequency`). Depende de T027, T030
- [x] T032 [US1] En `FE/components/ExpenseForm.jsx` con `kind='INCOME'`: enviar con `createIncome`, conservando la `Idempotency-Key` (se genera al confirmar, se mantiene ante fallos, se renueva tras éxito) y el manejo de errores existente (red/5xx, `401` sesión expirada, `422` clave reutilizada, `properties.fields` → `setError` junto a cada campo). Depende de T026, T031
- [x] T033 [US1] En `FE/components/ExpenseForm.jsx` agregar la prop opcional `onSaved(result)`: tras un guardado exitoso se muestra el snackbar y se invoca `onSaved`; sin la prop el comportamiento de hoy (reiniciar el formulario) no cambia, así que gastos sigue igual. Depende de T032
- [x] T034 [P] [US1] Crear `FE/hooks/usePersonalFormDefaults.js` (moneda preferida vía `me()` + `preferredCurrency`, lista de monedas preferidas, aviso `PROFILE_FALLBACK_NOTICE`, fecha de hoy con `useCurrentDate`) y hacer que `FE/pages/NewExpensePage.jsx` lo use sin cambiar su comportamiento
- [x] T035 [US1] Crear `FE/pages/NewIncomePage.jsx` ("Nuevo ingreso", `ExpenseForm kind="INCOME"`, `usePersonalFormDefaults`, cancelar vuelve como en gastos) que pasa `onSaved` para que, tras guardar con éxito, navegue a `/activity` con `navigate('/activity', { state: { refreshBalance: Date.now() } })` (el snackbar de éxito es global y sobrevive a la navegación). Registrar la ruta protegida `/incomes/new` en `FE/App.jsx`. Depende de T033 y T034
- [x] T036 [P] [US1] Crear `FE/components/BalanceCard.jsx`: tres cifras (Ingresos, Gastos, **Balance neto** con signo explícito y color + ícono distinto para negativo), estado de carga y de error con "Reintentar" cuando `getBalance` falla (incluye `503`), props `year`, `month`, `refreshKey`. Depende de T026. Sin caché propio: cada montaje vuelve a pedir el balance, y también cuando cambian `year`, `month` o `refreshKey`
- [x] T037 [US1] Modificar `FE/pages/ActivityPage.jsx`: mostrar `BalanceCard` sobre el buscador con el año/mes elegidos, pasarle `refreshKey={location.state?.refreshBalance}` (valor que deja `NewIncomePage` al redirigir tras guardar, de modo que la tarjeta recalcule de inmediato y nunca muestre un valor viejo), y agregar el botón "Nuevo ingreso" que enlaza a `/incomes/new` (el control Gastos | Ingresos llega en US2). Depende de T035, T036

### Frontend tests

- [x] T038 [P] [US1] Crear `FE/api/incomeClient.test.js`: `createIncome` (header `Idempotency-Key`, `201`/`200`, `400` con `fields`, `422` → `reusedKey`, 5xx y red → `network`) y `getBalance` (query string, `503`, `401`)
- [x] T039 [P] [US1] Ampliar `FE/components/ExpenseForm.test.jsx` con `kind="INCOME"`: categorías y medio de cobro de ingresos, sin Tipo/Frecuencia, campos obligatorios, monto ≤ 0, fecha más de 1 año adelante, resumen "Confirmá el ingreso", éxito reinicia el formulario, `properties.fields` aparecen junto a cada campo, `422` muestra el mensaje y renueva la clave; y que el flujo `kind="EXPENSE"` no cambió
- [x] T040 [P] [US1] Crear `FE/pages/NewIncomePage.test.jsx` (título, moneda preferida, aviso de perfil, cancelar) y `FE/components/BalanceCard.test.jsx` (cifras, neto negativo, carga, error con reintento); y que al guardar con éxito `NewIncomePage` navega a `/activity` con `state.refreshBalance`
- [x] T041 [US1] Actualizar `FE/pages/ActivityPage.test.jsx`: la tarjeta de balance se muestra y el enlace "Nuevo ingreso" apunta a `/incomes/new`; al llegar con `location.state.refreshBalance` la tarjeta vuelve a pedir `GET /api/balance` y muestra el valor nuevo (no el anterior)

**Checkpoint**: US1 funciona de punta a punta y se puede demostrar sola (MVP).

---

## Phase 4: User Story 5 - Seguridad y errores (Priority: P1)

**Goal**: ningún ingreso ajeno es accesible o distinguible; los errores son los del contrato.

**Independent Test**: dos cuentas A y B; B no puede ver ingresos de A (`404` idéntico al de un id inexistente); `401` sin sesión o con cuenta no verificada; grupos sin cambios.

- [x] T042 [US5] Agregar `getIncome(userId, incomeId)` a `BE/income/IncomeService.java` (`findByIdAndUserId` → `IncomeNotFoundException`) y `GET /api/incomes/{id}` a `BE/income/IncomeController.java` con el id como `String` (no numérico → el mismo `404`). Depende de T009, T015
- [x] T043 [P] [US5] Crear `BT/income/IncomeAccessIT.java`: ingreso ajeno, inexistente y `/api/incomes/abc` devuelven `404` con cuerpo idéntico; sin sesión `401`; cuenta no verificada `401` (la regla de cuenta verificada responde 401, como en gastos) en `POST`, `GET` y `/api/balance`; cada campo faltante del alta (`amount`, `currency`, `date`, `category`, `paymentMethod`) da `400` con su campo
- [x] T044 [P] [US5] Crear `BT/income/IncomeGroupIsolationIT.java`: tras crear ingresos del usuario, `GET /api/groups/{id}/balances` y `GET /api/groups/{id}/settlement-proposal` devuelven exactamente lo mismo que antes (SC-006, FR-014)
- [x] T045 [P] [US5] Agregar `getIncome(id)` a `FE/api/incomeClient.js` (`200` → `{ok, income}`, `404`/`401` → `{ok:false, status}`, 5xx/red → `network`) y su prueba en `FE/api/incomeClient.test.js`

**Checkpoint**: US1 + US5 listas; la feature es segura para integrar.

---

## Phase 5: User Story 2 - Consultar, buscar y filtrar ingresos (Priority: P1)

**Goal**: ver, buscar y filtrar ingresos con la misma experiencia que gastos, y abrir su detalle de solo lectura.

**Independent Test**: con ingresos de distintos años, categorías y descripciones, la vista "Ingresos" lista, busca por texto, filtra por año y mes, pagina y abre el detalle.

### Backend

- [x] T046 [P] [US2] Crear `BE/income/IncomeSearchLabels.java`: etiquetas en español de `IncomeCategory` (Sueldo, Freelance, Venta, Reembolso, Otros) y normalización sin tildes ni mayúsculas, siguiendo `BE/expense/ExpenseSearchLabels.java`
- [x] T047 [P] [US2] Crear `BE/income/IncomeSearchResponse.java` (`incomes`, `page`, `size`, `totalElements`, `totalPages`) y `IncomeYearsResponse(List<Integer> years)`
- [x] T048 [US2] Agregar `withConversions(userId, incomes)` a `BE/income/IncomeService.java` (equivalente en las monedas preferidas, ARS si no hay; una cotización faltante omite esa conversión, igual que `ExpenseService.withConversions`) y usarlo en `getIncome` (T042). Depende de T042
- [x] T049 [US2] Crear `BE/income/IncomeSearchService.java`: valida `q` (2–100 caracteres tras recortar, `%` y `_` literales), `IncomePeriod`, `page` ≥ 1 y `size` 1–50 (por defecto 20) con los mensajes de contracts/personal-incomes-api.md; busca en descripción y etiqueta de categoría sin tildes (`translate`, sin extensión `unaccent`, como `ExpenseSearchService`); orden `income_date DESC, id DESC`; página fuera de rango devuelve la última; `years(userId)`. Depende de T006, T016, T046–T048
- [x] T050 [US2] Agregar `GET /api/incomes/search` y `GET /api/incomes/years` a `BE/income/IncomeController.java`. Depende de T049
- [x] T051 [P] [US2] Crear `BT/income/IncomeSearchLabelsTest.java` y `BT/income/IncomeSearchServiceTest.java`: normalización, `q` de 1 y de 101 caracteres, `%`/`_` literales, mes sin año, `from` > `to`, intersección rango + año/mes, paginación y página fuera de rango
- [x] T052 [P] [US2] Crear `BT/income/IncomeSearchIT.java`: búsqueda por descripción y por categoría ("proyec", "sueldo"), filtros de año/mes/rango, orden, paginación, `years`, conversiones en la respuesta, solo ingresos propios, `400` con `properties.fields`

### Frontend

- [x] T053 [P] [US2] Agregar `searchIncomes({term, year, month, page, size})` y `listIncomeYears()` a `FE/api/incomeClient.js` (misma forma de retorno que `searchExpenses` y `listExpenseYears`) y probarlos en `FE/api/incomeClient.test.js`
- [x] T054 [US2] Crear `FE/pages/IncomeDetailPage.jsx` (`/incomes/:id`): monto y moneda, equivalentes, fecha, categoría, medio de cobro, descripción ("Sin descripción" si falta), insignia "Proyectado" si corresponde, `404` → "No encontramos ese ingreso", error con mensaje, "Volver" a `state.from` o `/activity`; **sin** botones de editar ni eliminar. Registrar la ruta protegida en `FE/App.jsx`. Depende de T045
- [x] T055 [US2] Refactor sin cambio de comportamiento: extraer de `FE/pages/ActivityPage.jsx` a `FE/components/activity/` solo lo que la vista de ingresos también necesita (`SearchFilters` con buscador, mes y año; `Pagination`; helpers `pagesOf`, `pageSizeFor`, `rowHeightOf`). Justificación en `plan.md` → "Justificación de la extracción en ActivityPage". `FE/pages/ActivityPage.test.jsx` debe pasar **sin ninguna modificación** antes de seguir
- [x] T056 [US2] En `FE/pages/ActivityPage.jsx` agregar el control segmentado **Gastos | Ingresos** con el parámetro `view=incomes` en la URL (por defecto Gastos; se conserva junto con `q`, `year`, `month`, `page` y al limpiar la búsqueda) y hacer que el botón primario sea "Nuevo gasto" o "Nuevo ingreso" según la vista. Depende de T037, T055
- [x] T057 [US2] Crear `FE/components/activity/IncomeResults.jsx`: consulta con `searchIncomes`, filas-tarjeta con flecha hacia `/incomes/:id` (descripción o categoría, fecha, monto y moneda original), insignia "Proyectado", "Buscando…", error, "No se encontraron resultados" y vacío con el enlace "Registrar un ingreso". Depende de T053, T054, T055
- [x] T058 [US2] Conectar `IncomeResults` en la vista Ingresos de `FE/pages/ActivityPage.jsx`: años de `listIncomeYears` más el año actual, selector de mes deshabilitado con "Todos los años", paginación compartida; `BalanceCard` sigue visible en ambas vistas. Depende de T056, T057
- [x] T059 [P] [US2] Crear `FE/pages/IncomeDetailPage.test.jsx`: datos, proyectado, "Sin descripción", `404`, error, "Volver" conserva la búsqueda
- [x] T060 [US2] Ampliar `FE/pages/ActivityPage.test.jsx`: cambio de pestaña conserva texto/año/mes en la URL, búsqueda de ingresos, sin resultados, paginación, filas con enlace, botón "Nuevo ingreso" solo en la vista Ingresos, la vista Gastos sigue pasando

**Checkpoint**: US1, US5 y US2 funcionan; la actividad personal muestra gastos e ingresos.

---

## Phase 6: User Story 4 - Ingresos en otras monedas y proyectados (Priority: P2)

**Goal**: ingresos en moneda extranjera con su equivalente y cobros futuros como "Proyectado" sin alterar el balance actual.

**Independent Test**: ingreso de 100 USD → equivalente en la moneda principal y aporte al balance; ingreso con fecha futura → "Proyectado", no suma al neto hasta su fecha.

- [x] T061 [P] [US4] Crear `BT/income/IncomeFutureDateIT.java`: fecha hoy + 1 año aceptada, hoy + 1 año + 1 día → `400` en `fields.date`, `projected=true` para fecha futura y `false` para hoy
- [x] T062 [P] [US4] Crear `BT/balance/BalanceMultiCurrencyIT.java`: ingreso en USD convertido con la cotización de su fecha, gasto en otra moneda, cuenta con moneda principal distinta de ARS, ingreso proyectado suma en `projectedIncome` y no en `net`, y un ingreso que cruza a "efectivizado" cuando su fecha llega, ingreso proyectado en USD sin cotización de su fecha futura usa la cotización de hoy (fallback) y suma a `projectedIncome`; sin cotización de hoy tampoco, `projectedIncome` se omite y el balance responde `200`; solo la cotización faltante de un gasto o de un ingreso efectivizado produce `503`
- [x] T063 [US4] En `FE/components/ExpenseForm.jsx` (`kind="INCOME"`): si la fecha es posterior a hoy mostrar `<p role="status">Se contará en el balance a partir de esa fecha</p>`; no pedir cotización para fechas futuras (el efecto de estimado ya exige `quoteDate.iso <= todayIso`: mantenerlo y cubrirlo). Depende de T029
- [x] T064 [US4] En `FE/components/BalanceCard.jsx` agregar la línea secundaria "Proyectado: +X" cuando `projectedIncome` > 0; en `FE/pages/ActivityPage.jsx` mostrar el equivalente `≈ MONEDA monto` de `conversions` en las filas de ingresos en otra moneda. Depende de T036, T055
- [x] T065 [P] [US4] Ampliar `FE/components/ExpenseForm.test.jsx` (aviso con fecha futura, sin aviso con hoy, sin estimado con fecha futura), `FE/components/BalanceCard.test.jsx` (línea "Proyectado") y `FE/pages/ActivityPage.test.jsx` (equivalente y "Proyectado" en la lista)

**Checkpoint**: monedas y proyectados verificados.

---

## Phase 7: User Story 3 - Registros inmutables e idempotentes (Priority: P2)

**Goal**: garantizar que los ingresos no se pueden editar ni eliminar y que el reintento no duplica.

**Independent Test**: dos altas con la misma clave dejan un solo ingreso (también en paralelo); `PATCH`/`PUT`/`DELETE` responden `405`; la interfaz no ofrece acciones de edición o borrado.

- [x] T066 [P] [US3] Crear `BT/income/IncomeImmutabilityIT.java`: `PATCH`, `PUT` y `DELETE` sobre `/api/incomes/{id}` y sobre `/api/incomes` → `405` ProblemDetail; el ingreso queda intacto; además un test de reflexión que comprueba que `IncomeController` no declara `@PatchMapping`, `@PutMapping` ni `@DeleteMapping`
- [x] T067 [P] [US3] Crear `BT/income/IncomeConcurrentRegistrationIT.java`: dos `POST` simultáneos con la misma clave y cuerpo producen una sola fila (una respuesta `201` y otra `200`), y con cuerpos distintos una `422`
- [x] T068 [P] [US3] Agregar en `FE/api/incomeClient.test.js` una prueba de que el módulo no exporta funciones de actualización ni borrado, y en `FE/pages/IncomeDetailPage.test.jsx` y `FE/pages/ActivityPage.test.jsx` que no hay botones "Editar" ni "Eliminar" de ingresos
- [x] T069 [P] [US3] Ampliar `FE/components/ExpenseForm.test.jsx`: un fallo de red conserva la misma `Idempotency-Key` en el reintento y un éxito la renueva (modo `INCOME`)

**Checkpoint**: la regla de inmutabilidad y la idempotencia quedan demostradas.

---

## Phase 8: Polish & Cross-Cutting

- [x] T070 Ejecutar `mvn -B verify` en `backend/` y confirmar verde con cobertura de líneas ≥ 85 % (JaCoCo); informar el porcentaje real
- [x] T071 Ejecutar `npm run lint && npm run test:coverage && npm run build` en `frontend/` y confirmar verde con cobertura ≥ 85 %; informar el porcentaje real
- [x] T072 Recorrer `specs/033-income-management/quickstart.md` (curl y pantallas) contra el entorno local y anotar el resultado de cada escenario
- [x] T073 Revisar el diff: sin código de depuración, imports sin uso ni cambios en `/api/expenses/**`, `/api/groups/**` ni en tablas existentes; confirmar con `git diff --stat` que solo hay archivos de la feature y que no hay `PATCH`/`PUT`/`DELETE` nuevos
- [x] T074 Actualizar los casilleros de `specs/033-income-management/checklists/requirements.md` si algún requisito cambió durante la implementación, y marcar las tareas completadas en este archivo

---

## Dependencies & Execution Order

- **Fase 1** → **Fase 2** (bloquea todo). Dentro de Fase 2: T003 → T005 → T006 → T007; T004 antes de T005; T008–T011 son independientes entre sí.
- **US1 (Fase 3)** depende de la Fase 2. Orden interno: T012/T013 → T014 → T015; T016–T019 → T020 → T021; T026–T028 antes de T029; T029 → T030 → T031 → T032 → T033 (mismo archivo, secuencial); T033 y T034 → T035; T036 → T037 (T037 también depende de T035 por el estado de navegación).
- **US5 (Fase 4)** depende de T015 y T009; sus tareas no dependen de la UI.
- **US2 (Fase 5)** depende de US1 y T042 (US5); T054 depende de T045; T055 (refactor) va primero y T056–T058 siguen en orden (mismo archivo).
- **US4 (Fase 6)** depende de US1 (formulario, balance) y US2 (lista).
- **US3 (Fase 7)** depende de US1 y US5 (verifica lo ya construido); puede hacerse en paralelo con US4.
- **Polish (Fase 8)** al final.

### Parallel Opportunities

- T001 ∥ T002; T004 ∥ T009 ∥ T010 ∥ T011.
- En US1: T012 ∥ T013 ∥ T019; las pruebas T022 ∥ T023 ∥ T024; en frontend T026 ∥ T027 ∥ T028 ∥ T034 ∥ T036 (T029–T033 son secuenciales: mismo archivo).
- US5: T043 ∥ T044 ∥ T045.
- US2: T046 ∥ T047; pruebas T051 ∥ T052; T053 ∥ T059.
- US4: T061 ∥ T062; US3: T066–T069 son independientes.
- Backend y frontend de una misma historia pueden avanzar en paralelo una vez fijado el contrato.

## Implementation Strategy

1. **MVP**: Fases 1–3 (US1) → validar con `quickstart.md` escenarios 1–2 y 7.
2. **Seguridad antes de integrar**: Fase 4 (US5).
3. **Incremental**: US2 → US4 → US3, cada una con su checkpoint y su propio PR/commit revisable (principio III).
4. Cerrar con la Fase 8 y el PR hacia `sprint-n` (principio XII).

## Notes

- Ninguna tarea agrega edición ni eliminación de ingresos ni modifica las rutas de gastos o grupos.
- Convención de commits: un commit por tarea o grupo lógico; verificar que la suite pase antes de cada checkpoint.
