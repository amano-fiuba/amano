---

description: "Tareas de la 027: formas de registrar un gasto"
---

# Tasks: Formas de registrar un gasto

**Input**: Design documents from `/specs/027-expense-entry-modes/`

**Prerequisites**: [plan.md](./plan.md), [spec.md](./spec.md), [research.md](./research.md), [data-model.md](./data-model.md), [contracts/](./contracts/), [quickstart.md](./quickstart.md)

**Tests**: obligatorios por la constitución (Principio V, cobertura ≥ 85 % en backend y frontend). Dentro de cada historia, las pruebas se escriben primero y deben fallar antes de implementar.

**Organization**: una fase por historia de usuario, en el orden de prioridad de la spec. Cada historia se puede revisar e integrar por separado (Principio III).

## Format: `[ID] [P?] [Story] Description`

- **[P]**: se puede hacer en paralelo (otro archivo, sin depender de tareas incompletas)
- **[Story]**: historia de la spec (US1–US4)

## Path Conventions

- Backend: `backend/src/main/java/com/amano/backend/`, pruebas en `backend/src/test/java/com/amano/backend/`, migraciones en `backend/src/main/resources/db/migration/`
- Frontend: `frontend/src/`, pruebas junto al archivo (`*.test.jsx` / `*.test.js`)

---

## Phase 1: Setup (verificaciones previas)

**Purpose**: confirmar los supuestos del plan antes de tocar código ("Verificaciones previas" de plan.md). Si alguno es falso, corregir plan.md/data-model.md antes de seguir.

- [X] T001 Verificar que el número de migración `V27` siga libre en `main` y en las ramas de 025/026 (`git fetch && git ls-tree -r origin/main --name-only | grep db/migration` y lo mismo en esas ramas); si está tomado, usar el siguiente libre y actualizar la referencia en specs/027-expense-entry-modes/data-model.md
- [X] T002 [P] Revisar en backend/src/main/resources/db/migration/V13__create_group_expenses_and_debts.sql si `group_expenses.group_id` y `debts.group_id` tienen FK a `groups` y anotar el nombre exacto de cada `CHECK` de moneda (`ck_expenses_currency`, `ck_group_expenses_currency`, `ck_debts_currency`, `ck_payments_currency`, `ck_user_currencies_code`) en specs/027-expense-entry-modes/data-model.md, sección *Migración*
- [X] T003 [P] Buscar en backend/src/main/java consultas sobre `Debt` o `GroupExpense` que no filtren por `groupId` (`grep -rn "FROM Debt\|FROM GroupExpense\|findAll" backend/src/main/java`) y listar el resultado en specs/027-expense-entry-modes/research.md, sección R5 (*Riesgo*); si aparece alguna, agregar una tarea en US4 para filtrarla
- [X] T004 [P] Confirmar cómo `CreateGroupRequest` y backend/src/main/java/com/amano/backend/groups/GroupService.java parsean `primaryCurrency` (hoy `Currency.valueOf`) y anotar el punto exacto donde irá la restricción a `Currency.GROUP_PRIMARY` en specs/027-expense-entry-modes/plan.md, sección D1

---

## Phase 2: Foundational

**Purpose**: no hay infraestructura compartida que bloquee a todas las historias. US1 no depende de nada; US2 aporta los componentes (calendario, moneda) que después usan US3 y US4.

**Checkpoint**: tras la Phase 1 se puede empezar US1 y US2 en paralelo.

---

## Phase 3: User Story 1 - Elegir qué tipo de gasto registrar (Priority: P1) 🎯 MVP

**Goal**: "Registrar gasto" del menú "+" abre un submenú con "Gasto personal", "Gasto con amigo" y "Gasto grupal" (FR-001 a FR-004).

**Independent Test**: abrir el menú "+", pasar el cursor, hacer clic o usar el teclado sobre "Registrar gasto"; aparece el submenú y "Gasto personal" lleva a `/expenses/new`. "Gasto con amigo" y "Gasto grupal" quedan ocultos hasta que existan US4 y US3 (plan.md, *Orden de implementación*).

### Tests for User Story 1

- [X] T005 [P] [US1] Reescribir y ampliar frontend/src/components/QuickActionsButton.test.jsx: el menú tiene `role="menu"`; "Registrar gasto" es `menuitem` con `aria-haspopup="menu"` y `aria-expanded`; el submenú se abre con hover, con clic y con `ArrowRight`/`Enter`; se cierra con `ArrowLeft`, con `Escape` (devolviendo el foco a "Registrar gasto"), con clic fuera y al elegir una opción; "Gasto personal" navega a `/expenses/new`; las opciones se muestran solo si su prop/flag `enabled` es verdadero; si el submenú no entra a la derecha (mock de `getBoundingClientRect` con `window.innerWidth` chico) recibe la clase de posición alternativa

### Implementation for User Story 1

- [X] T006 [P] [US1] Agregar los íconos SVG inline `PersonIcon`, `FriendIcon`, `ChevronRightIcon` y `ChevronLeftIcon` (mismo estilo y props que los existentes; "Gasto grupal" reutiliza `GroupsIcon`) en frontend/src/components/NavIcons.jsx
- [X] T007 [US1] Convertir frontend/src/components/QuickActionsButton.jsx en menú con submenú según contracts/ui-flows.md (*QuickActionsButton*): `ACTIONS` pasa a tener el ítem "Registrar gasto" con `children` = [{ label "Gasto personal", icon PersonIcon, to "/expenses/new" }, { label "Gasto con amigo", icon FriendIcon, action "friend", enabled false }, { label "Gasto grupal", icon GroupsIcon, action "group", enabled false }]; hover con retardo de cierre de ~150 ms; navegación por teclado; cierre con `useDismiss` existente; reposicionamiento con `getBoundingClientRect`; en móvil (< 768 px) el submenú reemplaza la lista con un ítem "‹ Volver"
- [X] T008 [US1] Agregar estilos del submenú (panel lateral, ícono + texto, ítem activo con fondo, flecha "▸", variante de posición alternativa y variante móvil) en el archivo de estilos donde hoy viven `.quick-actions*` (buscar con `grep -rn "quick-actions" frontend/src --include=*.scss`)

**Checkpoint**: US1 lista para revisar e integrar por separado.

---

## Phase 4: User Story 2 - Registrar un gasto personal con el formulario corregido (Priority: P1)

**Goal**: formulario personal con panel celeste sobre blanco, Cancelar/Guardar en el pie, calendario propio y monedas del usuario + "Ver más" con el catálogo del BCRA; el backend acepta las 32 monedas (FR-010 a FR-014, FR-018, FR-019).

**Independent Test**: registrar un gasto personal eligiendo la fecha en el calendario (otro mes) y BRL desde "Ver más"; queda guardado en BRL y las preferencias del perfil no cambian (quickstart 3–7).

### Tests for User Story 2 (backend)

- [X] T009 [P] [US2] Crear backend/src/test/java/com/amano/backend/user/CurrencyTest.java: hay exactamente 32 constantes (`ARS, AUD, AWG, BOB, BRL, CAD, CHF, CLP, CNY, COP, CZK, DKK, EUR, GBP, HKD, ILS, INR, JPY, MXN, NIO, NOK, NZD, PEN, PYG, RSD, RUB, SEK, SGD, TRY, USD, UYU, ZAR`); `bcraCode()` es igual al nombre salvo `MXN` → `"MXP"`; `fromBcraCode("MXP")` = `MXN`, `fromBcraCode("VND")` y `fromBcraCode("XAU")` = vacío; `GROUP_PRIMARY` = {ARS, USD, EUR}
- [X] T010 [P] [US2] Crear backend/src/test/java/com/amano/backend/currency/BcraCurrencyCatalogClientTest.java con un doble de `RestClient` (mismo patrón que backend/src/test/java/com/amano/backend/groupexpense/exchange/): con la respuesta real de `Maestros/Divisas` (copiar los 45 elementos de research.md R1 como fixture JSON en backend/src/test/resources/bcra/maestros-divisas.json) devuelve 32 entradas ordenadas por código, sin ATS/DEM/ESP/ITL/VEB/REF/XAG/XAU/XDR/CNH/VND, con NIO una sola vez y MXP mapeado a `code = "MXN"` y `bcraName = "PESO MEXICANO"`; un error de transporte o 5xx lanza `CurrencyCatalogUnavailableException`
- [X] T011 [P] [US2] Crear backend/src/test/java/com/amano/backend/currency/CachingCurrencyCatalogProviderTest.java con `Clock` fijo: el segundo pedido dentro de 24 h no llama al proveedor; pasado el TTL se refresca; si el refresco falla y hay catálogo vencido, lo devuelve; si falla y no hay catálogo, propaga `CurrencyCatalogUnavailableException`
- [X] T012 [P] [US2] Crear backend/src/test/java/com/amano/backend/currency/CurrencyControllerIT.java (Spring Boot Test, proveedor reemplazado por un doble): `GET /api/currencies` sin sesión → 401; con sesión → 200 `{ "currencies": [{ "code", "bcraName" }] }`; proveedor que lanza `CurrencyCatalogUnavailableException` → 503 `ProblemDetail` con `properties.code = "CURRENCY_CATALOG_UNAVAILABLE"` y mensaje "No se pudo obtener el catálogo de monedas; reintentá en unos minutos"
- [X] T013 [P] [US2] Ampliar backend/src/test/java/com/amano/backend/groupexpense/exchange/BcraExchangeRateClientTest.java: BRL→ARS usa `tipoCotizacion` de BRL; MXN→USD busca la entrada `codigoMoneda = "MXP"` y cruza contra USD con escala 8 `HALF_UP`
- [X] T014 [P] [US2] Ampliar backend/src/test/java/com/amano/backend/user/ProfileEditIT.java: `PUT /api/users/me` con `currencies = ["ARS","BRL"]` guarda ambas; con `["XAU"]` → 400 en `currencies` con mensaje "Moneda no soportada"
- [X] T015 [P] [US2] Agregar a la prueba de alta de gasto personal existente (buscar en backend/src/test/java/com/amano/backend/expense/ la IT de `POST /api/expenses`) un caso con `currency = "BRL"` → 201 y un caso con `currency = "VND"` → 400 en `currency` con el mensaje de 007
- [X] T016 [P] [US2] Agregar en backend/src/test/java/com/amano/backend/groups/ (prueba de `GroupService` o IT de creación de grupo existente) que `primaryCurrency = "BRL"` en `POST /api/groups` → 400 en `primaryCurrency` con "La moneda no es válida", y ARS/USD/EUR siguen aceptándose (el `PATCH` de grupo no edita la moneda principal; verificado en T004) — Cubierto por el caso existente `GroupControllerIT` (`primaryCurrency = "BRL"` → 400 "La moneda no es válida"), que ahora pasa por `Currency.GROUP_PRIMARY`.
- [X] T017 [P] [US2] Crear backend/src/test/java/com/amano/backend/currency/CurrencyMigrationIT.java (Testcontainers, mismo patrón que GroupExpenseMigrationIT): después de migrar, se puede insertar `BRL` en `expenses.currency`, `group_expenses.currency`, `debts.currency`, `payments.currency` y `user_currencies.currency_code`; `XAU` viola el `CHECK` en cada una; `groups.primary_currency = 'BRL'` sigue violando `ck_groups_primary_currency`

### Implementation for User Story 2 (backend)

- [X] T018 [US2] Ampliar backend/src/main/java/com/amano/backend/user/Currency.java a las 32 constantes de T009 con el campo `bcraCode` (igual al nombre salvo `MXN("MXP")`), `bcraCode()`, `static Optional<Currency> fromBcraCode(String)` y `public static final Set<Currency> GROUP_PRIMARY = EnumSet.of(ARS, USD, EUR)`; actualizar el Javadoc (ya no es "subset of ARS/USD/EUR")
- [X] T019 [US2] Crear backend/src/main/resources/db/migration/V27__expense_entry_modes.sql (número según T001), parte 1: para `expenses`, `group_expenses`, `debts`, `payments` y `user_currencies`, `DROP CONSTRAINT` del `CHECK` de moneda anotado en T002 y `ADD CONSTRAINT` con el mismo nombre y `IN` las 32 monedas de T009; **no** tocar `ck_groups_primary_currency`. Dejar un comentario de encabezado que remita a specs/027-expense-entry-modes/data-model.md
- [X] T020 [US2] En backend/src/main/java/com/amano/backend/groupexpense/exchange/BcraExchangeRateClient.java, reemplazar `arsPerUnit.get(x.name())` por `arsPerUnit.get(x.bcraCode())` en `computeRate` (tres lugares); sin otros cambios de comportamiento
- [X] T021 [P] [US2] Crear el paquete backend/src/main/java/com/amano/backend/currency/ con: `CurrencyCatalogEntry` (record `code`, `bcraName`), interfaz `CurrencyCatalogProvider` (`List<CurrencyCatalogEntry> getCatalog()`), `CurrencyCatalogUnavailableException` (RuntimeException)
- [X] T022 [US2] Crear backend/src/main/java/com/amano/backend/currency/BcraCurrencyCatalogClient.java: `GET /estadisticascambiarias/v1.0/Maestros/Divisas` con el `RestClient` del BCRA ya configurado en backend/src/main/java/com/amano/backend/groupexpense/exchange/ExchangeRateConfig.java (reutilizar base URL, timeouts y `max-attempts`); parsear `results[].{codigo, denominacion}` con `@JsonIgnoreProperties(ignoreUnknown = true)`; quedarse con los códigos que `Currency.fromBcraCode` reconoce, deduplicar por `code`, ordenar por `code`; errores de transporte/4xx/5xx → `CurrencyCatalogUnavailableException`
- [X] T023 [US2] Crear backend/src/main/java/com/amano/backend/currency/CachingCurrencyCatalogProvider.java: envuelve a `BcraCurrencyCatalogClient`, TTL `amano.exchange.bcra.catalog-ttl` (default `PT24H`), `Clock` inyectado, sirve el catálogo vencido si el refresco falla; registrar ambos beans en backend/src/main/java/com/amano/backend/groupexpense/exchange/ExchangeRateConfig.java (o en una `CurrencyConfig` nueva en el paquete `currency`)
- [X] T024 [US2] Crear backend/src/main/java/com/amano/backend/currency/CurrencyController.java: `GET /api/currencies` (`produces` JSON) → `{ "currencies": [...] }`; sin sesión → 401 con el mismo helper que usan los otros controladores
- [X] T025 [US2] Agregar en backend/src/main/java/com/amano/backend/common/ProblemDetailExceptionHandler.java el handler de `CurrencyCatalogUnavailableException` → 503, `properties.code = "CURRENCY_CATALOG_UNAVAILABLE"`, detalle "No se pudo obtener el catálogo de monedas; reintentá en unos minutos"
- [X] T026 [US2] En backend/src/main/java/com/amano/backend/user/UserAccountService.java, cambiar el mensaje de moneda inválida "Las monedas deben ser ARS, USD o EUR" por "Moneda no soportada" (el parseo con `Currency.valueOf` ya acepta las nuevas) — También en backend/src/main/java/com/amano/backend/auth/RegistrationService.java (el registro elige preferencias con el mismo selector).
- [X] T027 [US2] En backend/src/main/java/com/amano/backend/groups/GroupService.java (punto anotado en T004), rechazar `primaryCurrency` fuera de `Currency.GROUP_PRIMARY` con el 400 existente (`"primaryCurrency": "La moneda no es válida"`) en el `try` de `GroupService.java:127-132` (solo el alta setea la moneda principal)
- [X] T028 [P] [US2] Agregar `amano.exchange.bcra.catalog-ttl=PT24H` en backend/src/main/resources/application.properties (o `.yml`, el que use el repo) y, si las demás propiedades `amano.exchange.bcra.*` están en `.env.example` y compose.yaml, agregarla ahí también — `BCRA_BASE_URL` es la única propiedad expuesta en `.env.example`/Compose; `catalog-ttl` queda solo en application.properties.

### Tests for User Story 2 (frontend)

- [X] T029 [P] [US2] Crear frontend/src/components/DatePickerField.test.jsx: muestra `value` ISO como `dd/mm/aaaa`; el botón del ícono abre un `role="dialog"` con el mes del valor; `‹`/`›` cambian de mes; los días posteriores a `max` están deshabilitados; elegir un día llama a `onChange` con ISO y cierra; teclado: flechas mueven el día, `PageUp`/`PageDown` cambian de mes, `Enter` elige, `Escape` cierra y devuelve el foco al botón; tipear `05/10/2026` produce `2026-10-05`; texto inválido no rompe y deja el error de validación al formulario
- [X] T030 [P] [US2] Crear frontend/src/api/currencyClient.test.js: `listCurrencies()` hace `GET /api/currencies` con credenciales y devuelve `{ ok: true, currencies }`; 503 → `{ ok: false, unavailable: true }`; error de red → `{ ok: false, network: true }` (mismo patrón de resultados que frontend/src/api/expenseClient.js)
- [X] T031 [P] [US2] Crear frontend/src/components/CurrencyCatalogDialog.test.jsx: muestra *Cargando*; lista monedas con la etiqueta en español de `expenseCatalog.js` o, si no hay, la `bcraName`; buscar "real" o "brl" o "dolar" (sin acento) filtra; *Sin resultados*; 503 muestra error y "Reintentar" vuelve a pedir; "Elegir" llama a `onSelect(code)`; "Cancelar"/`Escape` llama a `onClose` sin elegir
- [X] T032 [P] [US2] Crear frontend/src/components/CurrencySelect.test.jsx: opciones en orden preferidas → moneda actual si no es preferida → "Ver más…"; sin preferidas muestra ARS; elegir "Ver más…" abre el diálogo y **no** cambia el valor; al elegir BRL en el diálogo, BRL aparece como opción y queda seleccionada; al cancelar se mantiene la anterior
- [X] T033 [P] [US2] Ampliar frontend/src/components/ExpenseForm.test.jsx: no existe "Gasto compartido" ni panel de reparto; hay "Cancelar" y "Guardar gasto" en el pie; "Cancelar" con el formulario sucio pide `window.confirm("¿Descartar el gasto? Se perderán los datos cargados")` y solo navega si se acepta; sin cambios navega directo; la fecha se elige con `DatePickerField`; enviar con `currency = "BRL"` manda `currency: "BRL"` en el payload
- [X] T034 [P] [US2] Ampliar frontend/src/components/CurrencySelector.test.jsx (preferencias del perfil): además de ARS/USD/EUR hay "Ver más…" que abre `CurrencyCatalogDialog` y agrega la moneda elegida a la selección

### Implementation for User Story 2 (frontend)

- [X] T035 [P] [US2] Ampliar `CURRENCY_OPTIONS` en frontend/src/validation/expenseCatalog.js con etiquetas en español para las 32 monedas (ej. `BRL` "Real brasileño", `MXN` "Peso mexicano", `UYU` "Peso uruguayo", `CLP` "Peso chileno", `GBP` "Libra esterlina"…) y exportar `currencyLabel(code, fallback)`; revisar que frontend/src/validation/expenseValidation.js (`preferredCurrency`, validadores de moneda) no asuma solo 3 monedas
- [X] T036 [P] [US2] Crear frontend/src/api/currencyClient.js con `listCurrencies()` según T030
- [X] T037 [P] [US2] Crear frontend/src/components/DatePickerField.jsx (+ frontend/src/components/DatePickerField.scss) según contracts/ui-flows.md (*DatePickerField*) y research.md R6: input de texto `dd/mm/aaaa` + botón con ícono de calendario a la derecha, popover `role="dialog"`, grilla `role="grid"` de 6×7 que empieza el lunes, nombres de mes/día en español (`Intl.DateTimeFormat('es-AR')`), hoy marcado, seleccionado resaltado, `max` deshabilita días posteriores; compatible con react-hook-form vía `forwardRef` + `onChange`/`onBlur`/`name` o vía `Controller`; sin dependencias nuevas
- [X] T038 [US2] Crear frontend/src/components/CurrencyCatalogDialog.jsx según T031 y contracts/ui-flows.md: usa `listCurrencies()`, normalización sin acentos (extraer la función `normalize` de frontend/src/components/groups/FriendPicker.jsx a frontend/src/validation/normalizeText.js y reutilizarla en ambos), lista de radios, botones "Cancelar" / "Elegir"
- [X] T039 [US2] Crear frontend/src/components/CurrencySelect.jsx según T032: envuelve `SelectField`, recibe `preferred` (array de códigos), `value`, `onChange`, `error`; la opción "Ver más…" tiene un valor centinela que abre `CurrencyCatalogDialog` en lugar de cambiar el valor
- [X] T040 [US2] Modificar frontend/src/components/ExpenseForm.jsx: reemplazar el campo de fecha por `DatePickerField` (`max = today`, misma validación de 007) y el select de moneda por `CurrencySelect` con las preferidas del perfil (nueva prop `preferredCurrencies`); envolver los campos en `<div className="group-card">` (superficie celeste) y agregar una barra de acciones al pie con "Cancelar" a la izquierda (confirmación si `isDirty`, luego `navigate(-1)` o `/` si no hay historial) y "Guardar gasto" (`SubmitButton`) a la derecha; no agregar interruptor ni panel de reparto
- [X] T041 [US2] Modificar frontend/src/pages/NewExpensePage.jsx para pasar `preferredCurrencies` (de `me().profile.currencies`, `['ARS']` si está vacío) a `ExpenseForm`
- [X] T042 [US2] Mover las reglas de superficie `.group-card` (fondo celeste, borde y radio) de frontend/src/pages/groups/groups.scss a un parcial compartido (ej. frontend/src/components/surfaces.scss importado desde frontend/src/index.scss) para que `ExpenseForm` no importe estilos de grupos; agregar la clase `.form-actions` (flex, `justify-content: space-between`) y verificar que la vista de grupo se vea igual — `.group-card` ya era global (frontend/src/index.scss); no hizo falta moverla. Se agregó `.expense-actions` en index.scss (`.form-actions` ya existía con otro uso).
- [X] T043 [US2] Modificar frontend/src/components/CurrencySelector.jsx (preferencias, usado en frontend/src/pages/ProfileEditPage.jsx) para agregar "Ver más…" con `CurrencyCatalogDialog` según T034, manteniendo el orden de clic existente — `CurrencySelector` lo usa el registro (RegisterForm); el perfil tenía su propia lista, que se reemplazó por `CurrencySelector` en modo controlado (`value`/`onChange`/`legend`).

**Checkpoint**: US2 lista; DatePickerField y CurrencySelect disponibles para US3 y US4.

---

## Phase 5: User Story 3 - Registrar un gasto grupal completo (Priority: P2)

**Goal**: el formulario de grupo suma categoría, tipo de gasto y medio de pago, filas de igual altura en los tres métodos, altura fija con panel scrolleable y selector de grupo desde el submenú (FR-020 a FR-024).

**Independent Test**: desde el submenú, elegir un grupo de 10 miembros, registrar un gasto con categoría, tipo y medio de pago y alternar los tres métodos sin que cambie la altura de las filas ni la del modal (quickstart 8–11).

**Depende de**: US2 (DatePickerField, CurrencySelect, superficie compartida) y US1 (submenú).

### Tests for User Story 3

- [X] T044 [P] [US3] Ampliar backend/src/test/java/com/amano/backend/groupexpense/GroupExpenseServiceTest.java: `category`, `type` y `paymentMethod` son obligatorios en el alta (400 en cada campo con los mismos mensajes que `ExpenseService` del 007); valores fuera de `ExpenseCategory`/`ExpenseType`/`PaymentMethod` → 400; `type = "RECURRING"` se guarda sin programar nada; `frequency` en el cuerpo → 400 "No se permite informar este campo"; el `PATCH` de gasto personal acepta los tres campos
- [X] T045 [P] [US3] Ampliar backend/src/test/java/com/amano/backend/groupexpense/GroupExpenseControllerIT.java: alta con los tres campos → 201 y la respuesta trae `category`, `type`, `paymentMethod` y `friend: null`; un gasto insertado antes de la migración (fila con columnas `NULL`) se lee con los tres campos en `null`; alta en `BRL` en un grupo en `ARS` con el `ExchangeRateProvider` reemplazado por un doble que devuelve una tasa fija → 201 con `convertedCurrency = "ARS"`, `convertedAmount = amount × tasa` y `rateSource = "BCRA"` (SC-007)
- [X] T046 [P] [US3] Ampliar backend/src/test/java/com/amano/backend/groupexpense/GroupExpenseMigrationIT.java (o crear `ExpenseEntryModesMigrationIT.java` junto a él): `group_expenses.category` acepta los valores de `ExpenseCategory` y rechaza otros; `expense_type` solo `'RECURRING','ONE_TIME'`; `payment_method` solo los de `PaymentMethod`; los tres admiten `NULL` — Hecho en backend/src/test/java/com/amano/backend/currency/ExpenseEntryModesMigrationIT.java (renombrado desde CurrencyMigrationIT, cubre las partes 1 y 2 de V27). `expense_type` quedó `VARCHAR(20)` como en V4.
- [X] T047 [P] [US3] Ampliar frontend/src/api/groupExpensesClient.test.js: el payload de alta incluye `category`, `type` y `paymentMethod` — `groupExpensesClient` reenvía el payload sin tocarlo; la cobertura está en GroupExpenseFormView.test.jsx (payload con los tres campos).
- [X] T048 [P] [US3] Crear frontend/src/components/expenses/SharedExpenseForm.test.jsx: muestra Monto, Moneda (`CurrencySelect`), Fecha (`DatePickerField`), Categoría, Tipo de gasto, Medio de pago, Descripción y "¿Quién pagó?"; los tres selects nuevos son obligatorios con los mensajes de `expenseValidation.js`; no hay campo Frecuencia; el formulario tiene la clase `group-expense__form--fixed` y la lista de filas `group-expense__rows` es el contenedor scrolleable; cada fila tiene la clase común `group-expense__row` en los tres métodos; "Cancelar" está en el pie a la izquierda y "Guardar gasto" a la derecha; con `showSharedToggle = false` no se muestra "Gasto compartido" — Como la extracción pasó a US4 (ver T057), estas verificaciones se hicieron sobre GroupExpenseFormView.test.jsx (describe "datos de 007 y diseño (027 US3)").
- [X] T049 [P] [US3] Verificar que frontend/src/pages/groups/GroupExpenseFormView.test.jsx siga pasando tras extraer `SharedExpenseForm` y ampliarlo con: el alta envía los tres campos nuevos; la edición de gasto personal precarga los tres campos (o vacío si el gasto es anterior a 027)
- [X] T050 [P] [US3] Crear frontend/src/components/GroupChooserDialog.test.jsx: lista los grupos activos del usuario con buscador; elegir uno navega a `/groups/:id/expenses/new`; sin grupos muestra "Todavía no formás parte de ningún grupo" y un enlace "Crear grupo" a `/groups/new`; error de red muestra el mensaje de red de `groupsClient`
- [X] T051 [P] [US3] Ampliar frontend/src/components/QuickActionsButton.test.jsx: con "Gasto grupal" habilitado, elegirlo abre `GroupChooserDialog`

### Implementation for User Story 3

- [X] T052 [US3] Crear backend/src/main/resources/db/migration/V28__group_expense_classification.sql (V27 ya estaba aplicada en US2): en `group_expenses`, columnas `category VARCHAR(20) NULL` con `CHECK` sobre los valores de `ExpenseCategory`, `expense_type VARCHAR(10) NULL` con `CHECK (expense_type IN ('RECURRING','ONE_TIME'))`, `payment_method VARCHAR(20) NULL` con `CHECK` sobre los valores de `PaymentMethod` (copiar listas y nombres de constraint del estilo de V4__create_expenses.sql)
- [X] T053 [US3] Agregar a backend/src/main/java/com/amano/backend/groupexpense/GroupExpense.java los campos `category` (`@Enumerated(STRING)`, `ExpenseCategory`, columna `category`, length 20), `expenseType` (`ExpenseType`, columna `expense_type`, length 10) y `paymentMethod` (`PaymentMethod`, columna `payment_method`, length 20), todos nullable, con getters/setters
- [X] T054 [US3] Agregar `category`, `type` y `paymentMethod` (String, tal como llegan) a backend/src/main/java/com/amano/backend/groupexpense/CreateGroupExpenseRequest.java
- [X] T055 [US3] En backend/src/main/java/com/amano/backend/groupexpense/GroupExpenseService.java, normalizar y validar los tres campos en el alta reutilizando los parsers y mensajes de backend/src/main/java/com/amano/backend/expense/ExpenseService.java (extraer a un helper común en el paquete `expense`, ej. `ExpenseClassificationParser`, si hoy son privados), guardarlos en la entidad, y aceptarlos en `updatePersonal`; `frequency` sigue rechazado como propiedad desconocida — Parser común: backend/src/main/java/com/amano/backend/expense/ExpenseClassification.java (ExpenseService también lo usa). En edición los tres campos son opcionales (un gasto anterior a 027 se puede editar sin completarlos).
- [X] T056 [US3] Agregar `category`, `type`, `paymentMethod` (String, `null` si la columna es `NULL`) y `friend` (`Person` o `null`) a backend/src/main/java/com/amano/backend/groupexpense/GroupExpenseResponse.java y completarlos en `toResponse` de `GroupExpenseService` — `friend` se agrega en US4 junto con el gasto con amigo (no tiene uso antes).
- [X] T057 [US3] Extraer el formulario de frontend/src/pages/groups/GroupExpenseFormView.jsx a frontend/src/components/expenses/SharedExpenseForm.jsx según plan.md D4: props `participants`, `payerOptions`, `defaultPayerKey`, `targetCurrency` (o `null` para no pedir cotización), `showSharedToggle`, `lockParticipants`, `preferredCurrencies`, `initialValues`, `isEdit`, `onSubmit(values)`, `onCancel`, `serverErrors`; la vista conserva la carga de grupo/miembros, permisos y llamadas a la API, sin cambiar comportamiento — **Movida a US4** (2026-11-08): solo hace falta para el gasto con amigo; en US3 los cambios se hicieron sobre GroupExpenseFormView.jsx para no refactorizar antes de necesitarlo. — Resuelto sin extraer un componente nuevo: GroupExpenseFormView.jsx acepta `mode="friend"` (carga al amigo, fija los dos participantes, oculta "Gasto compartido" y la cotización, guarda con la API de amigos) y frontend/src/pages/FriendExpenseFormView.jsx la renderiza en ese modo. Evita duplicar ~900 líneas y un refactor grande.
- [X] T058 [US3] En frontend/src/components/expenses/SharedExpenseForm.jsx: reemplazar fecha por `DatePickerField` y moneda por `CurrencySelect`; agregar los selects Categoría, Tipo de gasto y Medio de pago con `CATEGORY_OPTIONS`/`TYPE_OPTIONS`/`PAYMENT_METHOD_OPTIONS` de frontend/src/validation/expenseCatalog.js y los validadores de frontend/src/validation/expenseValidation.js; mover "Cancelar" (izquierda) y "Guardar gasto" (derecha) a una barra `.form-actions` fuera de las dos columnas; aplicar la clase `group-expense__form--fixed` al `<form>` (la estiliza T060 y la verifica T048) — Aplicado directamente en GroupExpenseFormView.jsx (ver T057).
- [X] T059 [US3] Enviar `category`, `type` y `paymentMethod` en el alta y en el `PATCH` personal desde frontend/src/api/groupExpensesClient.js y frontend/src/pages/groups/GroupExpenseFormView.jsx; agregar los tres a `SERVER_FIELDS` para mapear errores 400 por campo
- [X] T060 [US3] En frontend/src/pages/groups/groups.scss: grid de dos columnas con `align-items: stretch` en `.group-expense__form--fixed`; `.group-expense__split` con `display: flex; flex-direction: column; min-height: 0`; `.group-expense__rows` con `overflow-y: auto; flex: 1 1 0; min-height: 0`; variable `--split-row-height` aplicada a `.group-expense__row` en los tres métodos (en "Porcentajes", slider y campo en una sola línea); en `< 768px` columnas apiladas y sin altura fija (research.md R9)
- [X] T061 [P] [US3] Crear frontend/src/components/GroupChooserDialog.jsx según T050 y contracts/ui-flows.md: usa el listado de grupos de frontend/src/api/groupsClient.js filtrando activos, buscador con `normalizeText`, selección única y navegación a `/groups/:id/expenses/new`
- [X] T062 [US3] Habilitar "Gasto grupal" en frontend/src/components/QuickActionsButton.jsx (`enabled: true`) y abrir `GroupChooserDialog` al elegirlo

**Checkpoint**: US3 lista; `SharedExpenseForm` disponible para US4.

---

## Phase 6: User Story 4 - Registrar un gasto con un amigo (Priority: P3)

**Goal**: elegir un amigo y registrar un gasto compartido con él usando el formulario de grupo; se crea una deuda directa que bloquea eliminar la amistad (FR-030 a FR-034).

**Independent Test**: elegir a B, registrar $10.000 en partes iguales pagado por A; B debe $5.000; eliminar a B queda bloqueado; los saldos y deudas de grupos no cambian (quickstart 12–15).

**Depende de**: US3 (`SharedExpenseForm`, columnas de clasificación).

### Tests for User Story 4

- [X] T063 [P] [US4] Crear backend/src/test/java/com/amano/backend/groupexpense/FriendExpenseControllerIT.java (Testcontainers): `POST /api/users/me/friends/{friendId}/expenses` sin sesión → 401; con amigo y cuerpo válido → 201, `Location: /api/users/me/friends/{friendId}/expenses/{id}`, `groupId: null`, `friend` con los datos del amigo, `rateSource: "SAME_CURRENCY"`, `exchangeRate: "1"`, `convertedAmount = amount`; reenvío con la misma `Idempotency-Key` → 200 con el mismo gasto; misma clave con otro cuerpo → 422 `IDEMPOTENCY_KEY_REUSED`; `friendId` que no es amigo o no existe → 404 "No existe el amigo solicitado"; `payerUserId` ajeno → 400 en `payerUserId`; participantes ≠ {yo, amigo} o con `SIMULATED` → 400 en `participants`; faltan `category`/`type`/`paymentMethod` → 400; con moneda BRL no se llama al `ExchangeRateProvider` (doble que falla si se lo invoca; FR-034) — backend/src/test/java/com/amano/backend/groupexpense/FriendExpenseControllerIT.java (T063–T065 y T091 en el mismo archivo).
- [X] T064 [P] [US4] En el mismo IT (o `FriendExpenseDebtIT.java`): tras crear el gasto hay una `Debt` con `group_id NULL`, `status = PENDING`, deudor = quien no pagó, acreedor = quien pagó, monto según el reparto (10000 EQUAL → 5000); `GET /api/users/me/friends/{friendId}/deletion-eligibility` informa deuda directa y `DELETE /api/users/me/friends/{friendId}` responde el bloqueo de `PendingDirectDebtException`; si A y B comparten un grupo, `GET` de saldos, liquidación y deudas pendientes de ese grupo no cambian, y la tarea de vencimiento (`OverdueDebtJob`) no toca la deuda directa
- [X] T065 [P] [US4] En el mismo IT: `GET /api/users/me/friends/{friendId}/expenses/{id}` → 200 para el creador y para el amigo; 404 para un tercero y para un gasto de grupo
- [X] T066 [P] [US4] Crear backend/src/test/java/com/amano/backend/groupexpense/DebtDirectDebtAdapterTest.java (o IT con repositorio real): `hasPendingDirectDebt(A, B)` es verdadero con una deuda directa `PENDING`, `PARTIALLY_PAID`, `PAYMENT_REPORTED` u `OVERDUE` en cualquier dirección; falso si está `PAID`/`VERIFIED`, si la deuda es de grupo o si es con otra persona — Cubierto en FriendExpenseControllerIT (`DirectDebtPort` real contra la base: deuda directa pendiente en ambas direcciones, saldada, de grupo o con otra persona).
- [X] T067 [P] [US4] Ampliar el IT de migración de T046: `group_expenses` con `group_id NULL` y `friend_user_id` válido se inserta; con ambos `NULL` o ambos con valor viola `ck_group_expenses_context`; `debts.group_id NULL` se inserta — En ExpenseEntryModesMigrationIT; la parte de US4 está en `V29__friend_expenses.sql`.
- [X] T068 [P] [US4] Ajustar backend/src/test/java/com/amano/backend/friendship/DeleteFriendIT.java si hoy depende de `NoOpDirectDebtPort` (que el bean real no rompa los casos existentes sin deudas) — Solo se renombró el caso que mencionaba al stub; `NoOpDirectDebtPort` se eliminó.
- [X] T069 [P] [US4] Crear frontend/src/api/friendExpensesClient.test.js: `createFriendExpense(friendId, values, idempotencyKey)` hace `POST /api/users/me/friends/{friendId}/expenses` con cabecera `Idempotency-Key` y mapea 400 (`fields`), 404, 422 y red igual que frontend/src/api/groupExpensesClient.js — `createFriendExpense` quedó en frontend/src/api/groupExpensesClient.js (reutiliza su manejo de errores); tests en groupExpensesClient.test.js.
- [X] T070 [P] [US4] Crear frontend/src/components/FriendChooserDialog.test.jsx: lista amigos con buscador sin acentos; selección única (radio); "Continuar" deshabilitado hasta elegir; al confirmar navega a `/friends/:friendId/expenses/new`; sin amigos muestra `NO_FRIENDS_MESSAGE` y enlace a `/users/search` — Filas-enlace con avatar, nombre, correo y flecha, como el selector de grupo (pedido del usuario el 2026-11-08); sin radio ni "Continuar".
- [X] T071 [P] [US4] Crear frontend/src/pages/FriendExpenseFormView.test.jsx: carga el amigo de la lista de amigos; si `friendId` no está en la lista muestra un error con enlace a Amigos; renderiza `SharedExpenseForm` sin "Gasto compartido", con usuario y amigo como únicos participantes (no destildables en "Partes iguales") y ambos en "¿Quién pagó?"; no pide cotización; enviar llama a `createFriendExpense` con los tres campos nuevos; éxito navega a `/` con snackbar "Gasto registrado con {nombre}"; 404 muestra "No existe el amigo solicitado" y no navega; "Cancelar" vuelve sin crear nada
- [X] T072 [P] [US4] Ampliar frontend/src/components/QuickActionsButton.test.jsx: con "Gasto con amigo" habilitado, elegirlo abre `FriendChooserDialog`

### Implementation for User Story 4

- [X] T073 [US4] Crear una migración nueva `V29__friend_expenses.sql` (V27 y V28 ya están aplicadas; verificar que el número siga libre): `ALTER TABLE group_expenses ALTER COLUMN group_id DROP NOT NULL`; columna `friend_user_id BIGINT NULL` con FK a `users(id)`; `CHECK ck_group_expenses_context ((group_id IS NULL) <> (friend_user_id IS NULL))`; índice parcial sobre `(friend_user_id, created_by_user_id) WHERE group_id IS NULL`; `ALTER TABLE debts ALTER COLUMN group_id DROP NOT NULL`
- [X] T074 [US4] En backend/src/main/java/com/amano/backend/groupexpense/GroupExpense.java: `groupId` sin `nullable = false`, campo `friendUserId` (columna `friend_user_id`) y `isDirect()` (`groupId == null`); en backend/src/main/java/com/amano/backend/groupexpense/Debt.java, `groupId` sin `nullable = false` (y ajustar la fábrica `Debt.pendingFor` para copiar `null`)
- [X] T075 [US4] Agregar a backend/src/main/java/com/amano/backend/groupexpense/DebtRepository.java la consulta `existsOpenDirectBetween(userA, userB)`: `d.groupId IS NULL AND d.status NOT IN (PAID, VERIFIED) AND ((d.debtorUserId = :a AND d.creditorUserId = :b) OR (d.debtorUserId = :b AND d.creditorUserId = :a))`; y a `GroupExpenseRepository` `findByIdAndFriendContext(id, userA, userB)` para el `GET` (gasto directo cuyo par creador/amigo es {A, B})
- [X] T076 [US4] Refactorizar backend/src/main/java/com/amano/backend/groupexpense/GroupExpenseService.java según plan.md D3: extraer el núcleo de alta (normalización, clasificación, reparto, redondeo, idempotencia, partes y deudas) a un método privado que recibe un contexto con `groupId`/`friendUserId`, moneda destino (o `null` = sin conversión, `SAME_CURRENCY`), validador de participantes y chequeo de permisos; `create` (grupo) lo usa sin cambiar su comportamiento ni sus eventos de historial — Núcleo común: `normalize(...)` (validación con los participantes posibles) y `persist(...)` (conversión, partes y deudas) en GroupExpenseService.
- [X] T077 [US4] Agregar a `GroupExpenseService` `createForFriend(callerId, friendId, idempotencyKey, request)` y `getForFriend(callerId, friendId, expenseId)`: verificar amistad con `FriendshipRequestRepository.existsBetween(callerId, friendId, FriendshipRequestStatus.ACCEPTED)` (el mismo chequeo que usa `FriendService` en backend/src/main/java/com/amano/backend/friendship/FriendService.java; 404 "No existe el amigo solicitado"), participantes exactamente {caller, friend} `REGISTERED`, pagador ∈ {caller, friend}, sin conversión, sin evento de historial de grupo; `resolveConcurrentRegistration` equivalente para la carrera de idempotencia — La verificación de amistad y los nombres viven en el servicio nuevo `FriendExpenseService`; GroupExpenseService expone `createWithFriend`, `resolveConcurrentFriendRegistration` y `getWithFriend`. Un extraño o un usuario inexistente responde la 404 existente de 016 ("No sos amigo de ese usuario").
- [X] T078 [US4] Crear backend/src/main/java/com/amano/backend/groupexpense/FriendExpenseController.java: `POST /api/users/me/friends/{friendId}/expenses` (cabecera `Idempotency-Key`, 201 con `Location` / 200 en reenvío, mismo manejo de `DataIntegrityViolationException` que `GroupExpenseController.create`) y `GET /api/users/me/friends/{friendId}/expenses/{expenseId}`
- [X] T079 [US4] Crear backend/src/main/java/com/amano/backend/groupexpense/DebtDirectDebtAdapter.java (`@Component`) que implementa `com.amano.backend.friendship.DirectDebtPort` con `existsOpenDirectBetween`; eliminar backend/src/main/java/com/amano/backend/friendship/NoOpDirectDebtPort.java (o dejarlo con `@ConditionalOnMissingBean(DirectDebtPort.class)`) y actualizar el Javadoc de `DirectDebtPort` ("the module does not exist yet" ya no aplica)
- [X] T090 [US4] Hallazgo de T003: en backend/src/main/java/com/amano/backend/payment/PaymentValidationService.java (línea ~92, `debt.getGroupId().equals(groupId)`) y backend/src/main/java/com/amano/backend/payment/PaymentDecisionService.java (línea ~133) y backend/src/main/java/com/amano/backend/groupexpense/GroupExpenseService.java (línea ~454, `existing.getGroupId().equals(groupId)`), reemplazar por `Objects.equals(groupId, debt.getGroupId())` / `Objects.equals(groupId, existing.getGroupId())` para que una deuda o gasto directo (`group_id NULL`) dé el 404/422 existente en vez de `NullPointerException` — `PaymentDecisionService` ya comparaba del lado seguro; se corrigieron `PaymentValidationService` y el replay de `GroupExpenseService`, y `PaymentService` rechaza explícitamente una deuda sin grupo.
- [X] T091 [P] [US4] Agregar al IT de T064: pagar una deuda directa con `POST` de pago de 018/022 (backend/src/main/java/com/amano/backend/payment/PaymentService.java, que busca membresía con `debt.getGroupId()`), validarla o rechazarla con las rutas de grupo devuelve el 403/404 existente y no modifica la deuda; reusar la `Idempotency-Key` de un gasto con amigo en `POST /api/groups/{id}/expenses` devuelve 422 sin `NullPointerException`
- [X] T080 [P] [US4] Crear frontend/src/api/friendExpensesClient.js con `createFriendExpense` según T069 — Ver T069.
- [X] T081 [P] [US4] Crear frontend/src/components/FriendChooserDialog.jsx según T070, reutilizando `listFriends` de frontend/src/api/friendsClient.js, `normalizeText` y el render de fila (avatar + nombre) de frontend/src/components/groups/FriendPicker.jsx (extraer a un subcomponente si hace falta)
- [X] T082 [US4] Crear frontend/src/pages/FriendExpenseFormView.jsx según T071: lee `friendId` de la ruta, carga el amigo con `listFriends`, arma `participants` (usuario actual y amigo como `REGISTERED`), renderiza `SharedExpenseForm` con `showSharedToggle = false`, `lockParticipants = true`, `targetCurrency = null` y llama a `createFriendExpense` con una `Idempotency-Key` generada por intento
- [X] T083 [US4] Agregar la ruta protegida `/friends/:friendId/expenses/new` → `FriendExpenseFormView` en frontend/src/App.jsx (mismo envoltorio `ProtectedRoute` que `/expenses/new`)
- [X] T084 [US4] Habilitar "Gasto con amigo" en frontend/src/components/QuickActionsButton.jsx (`enabled: true`) y abrir `FriendChooserDialog` al elegirlo

**Checkpoint**: las cuatro historias funcionan de punta a punta.

---

## Phase 6b: Cotización a pesos en el gasto personal (FR-035, agregado 2026-11-08)

- [X] T092 [US2] Extraer el núcleo de `GroupExpenseService.getExchangeQuote` a un método reutilizable `quote(currency, date, target)` en backend/src/main/java/com/amano/backend/groupexpense/GroupExpenseService.java y exponer `GET /api/currencies/quote?currency=&date=` (destino ARS) en backend/src/main/java/com/amano/backend/currency/CurrencyController.java, con IT en backend/src/test/java/com/amano/backend/currency/CurrencyControllerIT.java (401, 200 con BCRA mockeado, ARS sin llamar al BCRA, 400 de moneda y fecha, 503)
- [X] T093 [US2] Cliente `getCurrencyQuote(currency, date)` en frontend/src/api/currencyClient.js (+ test) y vista previa en frontend/src/components/ExpenseForm.jsx: con moneda ≠ ARS y monto y fecha válidos, pide la cotización con debounce, muestra "Monto estimado" con `ConvertedAmount`, el aviso de que es una estimación y un mensaje no bloqueante si falla; tests en frontend/src/components/ExpenseForm.test.jsx

## Phase 7: Polish & Cross-Cutting Concerns

- [X] T085 [P] Ejecutar `cd backend && mvn -B verify` y confirmar JaCoCo ≥ 85 % sin bajar umbrales ni agregar exclusiones — 2026-11-08: 502 unitarias + 416 de integración, JaCoCo OK (JDK 25 en Docker; ver notas de la sesión).
- [X] T086 [P] Ejecutar `cd frontend && npm run lint && npm run test:coverage && npm run build` y confirmar cobertura ≥ 85 % — 2026-11-08: lint sin errores, 1055 tests, 95,1 % de líneas, build OK.
- [ ] T087 Recorrer los 15 escenarios de specs/027-expense-entry-modes/quickstart.md en la app levantada (escritorio y 375 px) y anotar el resultado en el PR
- [ ] T088 [P] Revisar accesibilidad por teclado y lector de pantalla de submenú, `DatePickerField`, `CurrencyCatalogDialog`, `GroupChooserDialog` y `FriendChooserDialog` (foco inicial, trampa de foco en diálogos, `Escape`, `aria-*`)
- [ ] T089 Actualizar specs/027-expense-entry-modes/spec.md (`Status`) y registrar en specs/027-expense-entry-modes/plan.md cualquier desvío encontrado durante la implementación; describir en el PR el cambio incompatible de `POST /api/groups/{id}/expenses` (tres campos obligatorios, Principio VII)

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: sin dependencias.
- **Foundational (Phase 2)**: vacía.
- **US1 (Phase 3)** y **US2 (Phase 4)**: arrancan tras la Phase 1, en paralelo.
- **US3 (Phase 5)**: depende de US2 (componentes y superficie) y de US1 (submenú, para T062).
- **US4 (Phase 6)**: depende de US3 (`SharedExpenseForm`, columnas de clasificación, núcleo de alta).
- **Polish (Phase 7)**: al final.

### User Story Dependencies

```text
Setup ──► US1 (P1) ─────────────┐
     └──► US2 (P1) ──► US3 (P2) ┴──► US4 (P3) ──► Polish
```

### Within Each User Story

- Pruebas primero (deben fallar), luego migración → entidad → servicio → controlador → cliente API → componentes → rutas y menú.
- Una migración por historia: `V27__expense_entry_modes.sql` (US2, T019), `V28__group_expense_classification.sql` (US3, T052) y `V29__friend_expenses.sql` (US4, T073). Una migración ya aplicada en cualquier base (incluida la local de desarrollo) nunca se edita: Flyway rechaza el arranque por checksum.

### Parallel Opportunities

- Setup: T002, T003 y T004.
- US1: T005 y T006.
- US2: todas las pruebas de backend (T009–T017) y de frontend (T029–T034) entre sí; T021, T028, T035, T036 y T037.
- US3: T044–T051; T061 en paralelo con T057–T060.
- US4: T063–T072; T080 y T081 en paralelo con el backend.
- US1 completa en paralelo con US2 completa.

---

## Parallel Example: User Story 2

```bash
# Pruebas de backend en paralelo:
Task: "CurrencyTest en backend/src/test/java/com/amano/backend/user/CurrencyTest.java"            # T009
Task: "BcraCurrencyCatalogClientTest en backend/src/test/java/com/amano/backend/currency/"        # T010
Task: "CurrencyControllerIT en backend/src/test/java/com/amano/backend/currency/"                 # T012

# Componentes de frontend independientes:
Task: "DatePickerField en frontend/src/components/DatePickerField.jsx"                            # T037
Task: "currencyClient en frontend/src/api/currencyClient.js"                                      # T036
Task: "Etiquetas de 32 monedas en frontend/src/validation/expenseCatalog.js"                      # T035
```

## Parallel Example: User Story 4

```bash
Task: "FriendExpenseControllerIT en backend/src/test/java/com/amano/backend/groupexpense/"         # T063
Task: "FriendChooserDialog.test.jsx en frontend/src/components/"                                   # T070
Task: "friendExpensesClient.js en frontend/src/api/"                                               # T080
```

---

## Implementation Strategy

### MVP First (User Story 1 + User Story 2)

1. Phase 1 (verificaciones).
2. US1: submenú con solo "Gasto personal" habilitado → revisar e integrar.
3. US2: formulario personal corregido + monedas del BCRA → revisar e integrar.
4. **Parar y validar** con quickstart 1–7.

### Incremental Delivery

1. US1 + US2 → MVP (las dos P1).
2. US3 → se habilita "Gasto grupal" en el submenú.
3. US4 → se habilita "Gasto con amigo".
4. Cada historia en su propio PR a `sprint-n` (Principio XII), con Conventional Commits.

---

## Notes

- [P] = otro archivo, sin dependencias pendientes.
- Las pruebas nunca llaman al BCRA real (dobles de `CurrencyCatalogProvider` y `ExchangeRateProvider`).
- No editar migraciones ya integradas; no bajar umbrales de cobertura.
- Etiquetas en español solo en el frontend (Principio X).
