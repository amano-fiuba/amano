---
description: "Task list for Estadísticas y analíticas de ingresos personales"
---

# Tasks: Estadísticas y analíticas de ingresos personales

**Input**: Design documents from `/specs/034-income-statistics/`

**Prerequisites**: plan.md, spec.md, contracts/statistics-summary-api.md

**Tests**: La implementación se valida primero manualmente (T023) y después se escriben las pruebas automatizadas de la Fase 4. Son obligatorias para cerrar la historia (constitución V, cobertura ≥ 85 %, SC-007).

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Puede ejecutarse en paralelo (archivos distintos, sin dependencias pendientes)
- **[Story]**: Historia a la que pertenece (US1..US5)

## Path Conventions

- Backend: `backend/src/main/java/com/amano/backend/`, tests en `backend/src/test/java/com/amano/backend/`
- Frontend: `frontend/src/`, tests junto al archivo probado (`*.test.jsx`, `*.test.js`)

---

## Fase 1: Setup y DTOs

- [X] T001 [P] Crear `IncomeCategorySubtotal` (`IncomeCategory category`, `Currency currency`, `LocalDate date`, `BigDecimal amount`, `Long count`) en backend/src/main/java/com/amano/backend/statistics/IncomeCategorySubtotal.java, proyección de la consulta agregada de ingresos (plan D4)
- [X] T002 [P] Crear `IncomeStatisticsResponse` con `total` (String), `excludedCount` (long) y `categories[{ category, amount, percentage }]`, importes con dos decimales y porcentajes con uno, en backend/src/main/java/com/amano/backend/statistics/IncomeStatisticsResponse.java (FR-007, FR-012, plan D9)
- [X] T003 [P] Crear `StatisticsSummaryResponse` con `year`, `month`, `currency`, `expenses` como un `ExpensesSummary` propio y desacoplado de `ExpenseStatisticsResponse`, con exactamente los campos del contrato (`total`, `excludedCount`, `personal` y `group`, reutilizando `ExpenseStatisticsResponse.Breakdown` para los dos últimos), `income`, `comparison{ income, expenses, balance }` y `years`, en backend/src/main/java/com/amano/backend/statistics/StatisticsSummaryResponse.java (FR-015..FR-017, plan D9)
- [X] T004 [P] Verificar contra [contracts/statistics-summary-api.md](../contracts/statistics-summary-api.md) que los nombres y formatos de T002 y T003 coinciden con el contrato antes de implementar los servicios (constitución VII)

---

## Fase 2: Backend stats de ingresos

- [X] T005 [P] Agregar a `IncomeRepository` `sumByCategoryCurrencyAndDate(userId, from, to, today)`: `SUM(amount)` y `COUNT(id)` agrupados por `(categoría, moneda, income_date)`, filtrando `incomeDate` entre `from` y `to` y `incomeDate <= today`, con proyección a `IncomeCategorySubtotal`, en backend/src/main/java/com/amano/backend/income/IncomeRepository.java (FR-008, plan D3, D4)
- [X] T006 Agregar a `IncomeRepository` `findReceivedIncomeYears(userId, today)`: años distintos por fecha del ingreso con al menos un ingreso no proyectado, descendente, en backend/src/main/java/com/amano/backend/income/IncomeRepository.java (FR-004, plan D8)
- [X] T007 [P] Extraer de `ExpenseStatisticsService` la conversión y la caché por `(moneda, fecha)` a `StatisticsConverter` (`convert(currency, date, amount, count)` y contador de excluidos) en backend/src/main/java/com/amano/backend/statistics/StatisticsConverter.java, y hacer que `ExpenseStatisticsService` lo use sin cambiar su comportamiento, en backend/src/main/java/com/amano/backend/expense/ExpenseStatisticsService.java (FR-010, FR-011, plan D5)
- [X] T008 [US1] Crear `IncomeStatisticsService.statistics(userId, YearMonth period, Currency target)`: valida que la moneda del cálculo sea la recibida, convierte cada subtotal con la cotización de su fecha, suma por categoría, calcula total y porcentajes sobre el total incluido, redondea `HALF_UP` al final, filtra categorías con importe mayor que cero y ordena por importe descendente y luego por `IncomeCategory.ordinal()`, en backend/src/main/java/com/amano/backend/statistics/IncomeStatisticsService.java (FR-008..FR-013, plan D3..D6)
- [X] T009 [US1] Crear `StatisticsSummaryService.summary(userId, rawYear, rawMonth)`: delega en `ExpenseStatisticsService.statistics(...)` para validar el período y obtener gastos y moneda; calcula el bloque de ingresos con `IncomeStatisticsService`; arma `comparison` con los totales ya redondeados (balance = ingresos − gastos) y une `expense.years()` con `findReceivedIncomeYears`, sin duplicados y descendente, en backend/src/main/java/com/amano/backend/statistics/StatisticsSummaryService.java (FR-004, FR-007, FR-015..FR-018, plan D2, D6..D8)
- [X] T010 [US1] Crear `StatisticsSummaryController` con `GET /api/stats/summary?year=&month=`: persona desde `SecurityContext`, sin parámetros de usuario, 401 sin sesión como `ExpenseStatisticsController`, y errores 400 mapeados por `ProblemDetailExceptionHandler`, en backend/src/main/java/com/amano/backend/statistics/StatisticsSummaryController.java (FR-014, FR-026, plan D1, D10)
- [X] T011 [US1] Agregar `/api/stats/**` a los `requestMatchers(...).access(verifiedAccount(...))` de `SecurityConfig`, sin tocar las reglas existentes, en backend/src/main/java/com/amano/backend/config/SecurityConfig.java (FR-026, plan D10)

---

## Fase 3: Frontend barra comparativa, columnas y gráficos

- [X] T012 [P] Crear `getStatisticsSummary({ year, month })` en frontend/src/api/statisticsClient.js con el patrón de `expenseClient.js`: `{ ok: true, statistics }` en 200, `{ ok: false, status }` en 4xx y `{ ok: false, network: true }` en error de red; exportar `STATISTICS_ERROR_MESSAGE = 'No pudimos cargar tus estadísticas. Intentá nuevamente'` (FR-027, plan D11)
- [X] T013 [P] Mantener la geometría de `PieChart` en `charts/geometry.js` (`SIZE`, `CENTER`, `RADIUS`, `pointAt`, `slicePath`) y agregar a `PieChart` un tooltip sobre la porción activa (`role="tooltip"`, centroide) con el `detail` del ítem (nombre, monto y porcentaje), quitando el texto de ayuda y el detalle fijo debajo del gráfico; el nombre accesible de la porción conserva nombre, monto y porcentaje (FR-021, plan D14)
- [X] T014 [US3] Definir la composición de la barra comparativa sin gráfico de torta: segmento verde de ingresos y rojo de gastos, porcentaje dentro de cada segmento, totales y balance debajo (FR-015, FR-016, plan D14)
- [X] T015 [US2] Retirar `Carousel.jsx`, `Carousel.scss` y `Carousel.test.jsx` (reemplazados por pestañas, FR-002, plan D12)
- [X] T016 [US2] Implementar en la columna de gastos de `StatisticsPage` las pestañas "Personales"/"Grupales": `role="tablist"`/`tab`/`tabpanel`, `aria-selected`, `aria-controls`, flechas izquierda y derecha sin dar la vuelta y foco móvil (FR-002, FR-030, plan D12)
- [X] T017 [P] Agregar `incomeCategoryColor(code)` y el catálogo `INCOME_CATEGORY_OPTIONS` ya existente a frontend/src/validation/expenseCatalog.js, devolviendo la variable CSS `var(--color-ingreso-<código>)`; las variables CSS se definen en StatisticsPage.scss (T022) para tema claro y oscuro (FR-022, plan D15)
- [X] T018 [US1] Parametrizar `Breakdown` (torta + lista) con `options`, `colorOf` y `titleId` para reutilizarlo en gastos e ingresos sin duplicar la lista ni el detalle, en frontend/src/pages/StatisticsPage.jsx (plan D16)
- [X] T019 [US2] Reescribir frontend/src/pages/StatisticsPage.jsx con el orden acordado: selectores de mes y año, barra comparativa y dos columnas (ingresos a la izquierda; gastos a la derecha con pestañas Personales y Grupales); el índice de la pestaña vive en la página, se conserva al cambiar de período y vuelve a "Personales" al recargar; un solo pedido `getStatisticsSummary` con un único estado de carga/error (FR-001..FR-006, FR-018, FR-023, plan D11, D12, D18)
- [X] T020 [US3] Agregar a frontend/src/pages/StatisticsPage.jsx la barra comparativa horizontal (ingresos en verde, gastos en rojo, porcentaje relativo dentro de la barra, etiqueta accesible) y debajo los totales de ingresos y de gastos y el balance del período con signo explícito y texto para negativos, más el aviso de moneda (FR-015..FR-019)
- [X] T021 [US4] Conservar y adaptar en frontend/src/pages/StatisticsPage.jsx los estados vacíos: por pestaña, sin ingresos efectivizados con enlace para registrar un ingreso, sin gastos con enlace para registrar un gasto (sin pestañas) y período vacío sin barra ni columnas; mantener los avisos de gastos excluidos y los de ingresos excluidos por separado (FR-023, FR-024)
- [X] T022 [P] Extender frontend/src/pages/StatisticsPage.scss y PieChart.scss: barra comparativa, grilla de dos columnas apilable en pantallas angostas, pestañas, tooltip de la torta y variables CSS `--color-ingreso-<código>` distintas de las de gasto y legibles en tema claro y oscuro (FR-025, plan D15)

---

## Fase 4: Integración y Tests

- [x] T023 [US1] Validar manualmente con el usuario en Docker Compose los escenarios de US1–US5 y los casos límite, con datos de demo que incluyan ingresos efectivizados y proyectados en varias monedas, gastos personales, de grupo y con amigos, y meses con y sin movimientos (spec.md, plan.md)
- [X] T024 [P] Unit test de `IncomeStatisticsService`: solo ingresos efectivizados del período, conversión por fecha, moneda del cálculo ya convertida, cotización faltante y `excludedCount`, suma por categoría, porcentajes, redondeo, filtro de ceros, orden y desempate, en backend/src/test/java/com/amano/backend/statistics/IncomeStatisticsServiceTest.java (FR-008..FR-013)
- [X] T025 [P] Unit test de `StatisticsSummaryService`: composición de gastos e ingresos, comparación con totales redondeados, balance positivo/negativo/cero, unión de años sin duplicados y sin años con solo ingresos proyectados, en backend/src/test/java/com/amano/backend/statistics/StatisticsSummaryServiceTest.java (FR-004, FR-015..FR-018, SC-004)
- [X] T026 [P] Integration test (Testcontainers + MockMvc) de `GET /api/stats/summary`: gastos personales y partes grupales, ingresos de varias categorías y monedas, ingresos de otras personas, ingreso proyectado excluido, cotización faltante, años unificados, 400 por `year`/`month` y 401 sin sesión o cuenta no verificada, en backend/src/test/java/com/amano/backend/statistics/StatisticsSummaryIT.java (FR-004, FR-008..FR-014, FR-026, FR-027)
- [X] T027 [P] Regresión tras extraer `StatisticsConverter`: mismos importes, porcentajes, totales, años y `excludedCount` en `ExpenseStatisticsServiceTest`/`ExpenseStatisticsIT`; además, `GET /api/balance` (033) y los endpoints de saldos, presupuestos y propuestas de liquidación grupales devuelven exactamente los mismos resultados que antes, en backend/src/test/java/com/amano/backend/expense/ExpenseStatisticsServiceTest.java, backend/src/test/java/com/amano/backend/balance/BalanceIT.java, backend/src/test/java/com/amano/backend/groupexpense/GroupBalanceControllerIT.java y backend/src/test/java/com/amano/backend/groupexpense/GroupSettlementControllerIT.java (FR-028, SC-006)
- [X] T028 [P] Tests de `getStatisticsSummary` en frontend/src/api/statisticsClient.test.js: éxito, 400, 401 y error de red (FR-027, plan D11)
- [X] T029 [P] Tests de `PieChart` en frontend/src/components/charts/PieChart.test.jsx: tooltip al pasar el cursor, enfocar o tocar con nombre, monto y porcentaje, sin texto de ayuda ni leyenda debajo, dos porciones, círculo completo con una sola y `aria-label` por porción (FR-015, FR-021)
- [X] T030 [P] Tests de las pestañas en frontend/src/pages/StatisticsPage.test.jsx: "Personales" inicial, cambio con clic y con flechas, extremos sin vuelta, roles y `aria-selected`, pestaña conservada al cambiar el período (FR-002, FR-030, SC-009)
- [X] T031 [P] Tests de `incomeCategoryColor` y de los colores de ingreso en frontend/src/validation/expenseCatalog.test.js (FR-022, FR-028)
- [X] T032 [P] Actualizar frontend/src/pages/StatisticsPage.test.jsx: pedido único, período por defecto y en la dirección, cambio de mes y año conservando la pestaña, dos columnas, bloque de ingresos, barra comparativa con totales y balance, tooltip de las tortas, estados vacíos, avisos de excluidos, error con "Reintentar", sesión vencida y ausencia de bloques parciales (FR-001..FR-006, FR-021, FR-023..FR-027)

---

## Fase 5: Polish & Cobertura

- [X] T033 Revisar accesibilidad de la pantalla completa: orden de foco, pestañas, nombres accesibles de la barra y de las porciones, signo del balance, contraste de colores de ingreso en ambos temas e información nunca solo por color (FR-022, FR-025, FR-030, SC-009)
- [ ] T034 Validar el comportamiento táctil y responsive en pantalla angosta: columnas apiladas sin scroll horizontal, tooltip al tocar una porción, torta y lista legibles y barra comparativa con su balance legible (FR-021, FR-025)
- [X] T035 Ejecutar `mvn -B verify` en backend/ y `npm run lint && npm run test:coverage && npm run build` en frontend/; corregir lo que falle e informar la cobertura alcanzada (≥ 85 %, SC-007). Incluir la verificación cronometrada de performance de SC-008: con 1000 ingresos y gastos en el período, la pantalla y sus gráficos deben mostrarse en menos de 2 s desde que se elige el período, sin recargar la página
- [X] T036 Revisar la trazabilidad FR → tarea y la consistencia entre spec.md, plan.md, contracts/statistics-summary-api.md y este archivo (endpoint, nombres de campos, columnas y fases); confirmar que la regresión incluye `GET /api/balance` (033) y los saldos, presupuestos y propuestas de liquidación grupales; actualizar `checklists/requirements.md` si cambió alguna clarificación (FR-028, SC-006, constitución II)

---

## Dependencies

- Fase 1 → Fase 2. T009 depende de T005–T008; T010 de T009; T011 se puede hacer en paralelo con T009.
- Fase 3: T012 depende de T003 (contrato); T013–T017 son independientes entre sí (T014 es una definición de diseño); T016 y T018–T021 modifican `StatisticsPage.jsx` y se ejecutan en orden; T022 depende de T019–T021.
- Fase 4 empieza con T023 (validación manual) después de T019–T022; T024–T032 dependen de T023 y del código que prueban.
- Fase 5 después de la Fase 4. T035 al final, después de T033 y T034.

## Parallel Opportunities

- T001–T004 (DTOs y contrato) entre sí.
- T005 y T007 en paralelo; T006 depende de T005 por editar el mismo `IncomeRepository.java`; T008 depende de T007.
- T012–T017 en paralelo con el backend de la Fase 2 (contrato ya fijado en T003/T004); T018–T021 son secuenciales por compartir `StatisticsPage.jsx`.
- T024–T032 entre sí, por archivos distintos, una vez completada T023.
- T033 y T034 en paralelo antes de T035.

## Implementation Strategy

1. **MVP (US1 + US2)**: T001–T022 con foco en el bloque de ingresos y las pestañas de gastos; permite validar la pantalla completa con la comparación básica.
2. **US3–US5**: comparación, períodos y monedas ya están cubiertos por T009, T018–T021 y T023; completar sus pruebas en T025, T029 y T032.
3. **Cierre**: pruebas automatizadas (T024–T032), accesibilidad/responsive (T033–T034), cobertura y trazabilidad (T035–T036).

## Notas

- No hay migraciones ni dependencias nuevas: todo se apoya en las tablas y componentes existentes (plan, constitución XI).
- `/api/expenses/statistics` y `/api/balance` no se modifican; la regresión de T027 y T031 es obligatoria (FR-028, SC-006).
- El endpoint `GET /api/stats/summary` es de solo lectura; ninguna tarea crea, edita o elimina movimientos (FR-029).
