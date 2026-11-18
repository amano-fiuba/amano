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

## Fase 3: Frontend Carrusel y Gráficos

- [X] T012 [P] Crear `getStatisticsSummary({ year, month })` en frontend/src/api/statisticsClient.js con el patrón de `expenseClient.js`: `{ ok: true, statistics }` en 200, `{ ok: false, status }` en 4xx y `{ ok: false, network: true }` en error de red; exportar `STATISTICS_ERROR_MESSAGE = 'No pudimos cargar tus estadísticas. Intentá nuevamente'` (FR-027, plan D11)
- [X] T013 [P] Confirmar que el `PieChart` existente cubre la comparación de dos porciones (proporciones, círculo completo con una sola, detalle al enfocar o tocar y `aria-label`); mantener su geometría actual en `charts/geometry.js` y retirar `ringPath` y el código del gráfico anterior (FR-015, FR-016, plan D14)
- [X] T014 [US3] Definir la composición del bloque comparativo sin un gráfico nuevo: `PieChart` con dos porciones (Ingresos y Gastos), leyenda textual con ambos totales y balance destacado debajo del gráfico (FR-015, FR-016, plan D14)
- [X] T015 [US2] [P] Crear frontend/src/components/Carousel.jsx y Carousel.scss: `slides`, `index`, `onIndexChange` y `ariaLabel`; `role="region"` con `aria-roledescription="carrusel"`, cada slide `role="group"` con `aria-roledescription="slide"` y `aria-label="<n> de <total>"`; puntos `<button>` con `aria-label` y `aria-current`; botones "Anterior"/"Siguiente" deshabilitados en los extremos; teclas `ArrowLeft`/`ArrowRight`; sin autoplay ni loop (FR-002, FR-030, plan D12)
- [X] T016 [US2] Implementar el swipe en `Carousel`: eventos de puntero/touch, cambio solo si el desplazamiento horizontal supera el umbral (40 px) y es mayor que el vertical, `touch-action: pan-y` y sin `preventDefault` en gestos cortos o verticales, en frontend/src/components/Carousel.jsx (FR-031, plan D13)
- [X] T017 [P] Agregar `incomeCategoryColor(code)` y el catálogo `INCOME_CATEGORY_OPTIONS` ya existente a frontend/src/validation/expenseCatalog.js, devolviendo la variable CSS `var(--color-ingreso-<código>)`; las variables CSS se definen en StatisticsPage.scss (T022) para tema claro y oscuro (FR-022, plan D15)
- [X] T018 [US1] Parametrizar `Breakdown` (torta + lista) con `options`, `colorOf` y `titleId` para reutilizarlo en gastos e ingresos sin duplicar la lista ni el detalle, en frontend/src/pages/StatisticsPage.jsx (plan D16)
- [X] T019 [US2] Reescribir frontend/src/pages/StatisticsPage.jsx con el orden acordado: selectores de mes y año, bloque comparativo, carrusel de gastos (Slide 1 personales, Slide 2 grupales) y bloque de ingresos; el índice del carrusel vive en la página, se conserva al cambiar de período y vuelve a 0 al recargar; un solo pedido `getStatisticsSummary` con un único estado de carga/error (FR-001..FR-006, FR-018, FR-023, plan D11, D12, D18)
- [X] T020 [US3] Agregar a frontend/src/pages/StatisticsPage.jsx el bloque comparativo: torta de dos porciones (Ingresos y Gastos combinados) reutilizando `PieChart`, etiquetas "Ingresos"/"Gastos" con importe, balance del período destacado **debajo del gráfico** con signo explícito, ícono o texto para negativos, y aviso de moneda (FR-015..FR-019)
- [X] T021 [US4] Conservar y adaptar en frontend/src/pages/StatisticsPage.jsx los estados vacíos: por slide, sin ingresos efectivizados con enlace para registrar un ingreso, y período vacío sin carrusel, torta comparativa ni tortas; mantener los avisos de gastos excluidos y agregar el de ingresos excluidos por separado (FR-023, FR-024)
- [X] T022 [P] Extender frontend/src/pages/StatisticsPage.scss: carrusel de un slide visible con puntos y flechas, torta comparativa y balance debajo, variables CSS `--color-ingreso-<código>` distintas de las de gasto y legibles en tema claro y oscuro, apilado en pantallas angostas y transición breve desactivada con `prefers-reduced-motion` (FR-025, plan D15)

---

## Fase 4: Integración y Tests

- [ ] T023 [US1] Validar manualmente con el usuario en Docker Compose los escenarios de US1–US5 y los casos límite, con datos de demo que incluyan ingresos efectivizados y proyectados en varias monedas, gastos personales, de grupo y con amigos, y meses con y sin movimientos (spec.md, plan.md)
- [ ] T024 [P] Unit test de `IncomeStatisticsService`: solo ingresos efectivizados del período, conversión por fecha, moneda del cálculo ya convertida, cotización faltante y `excludedCount`, suma por categoría, porcentajes, redondeo, filtro de ceros, orden y desempate, en backend/src/test/java/com/amano/backend/statistics/IncomeStatisticsServiceTest.java (FR-008..FR-013)
- [ ] T025 [P] Unit test de `StatisticsSummaryService`: composición de gastos e ingresos, comparación con totales redondeados, balance positivo/negativo/cero, unión de años sin duplicados y sin años con solo ingresos proyectados, en backend/src/test/java/com/amano/backend/statistics/StatisticsSummaryServiceTest.java (FR-004, FR-015..FR-018, SC-004)
- [ ] T026 [P] Integration test (Testcontainers + MockMvc) de `GET /api/stats/summary`: gastos personales y partes grupales, ingresos de varias categorías y monedas, ingresos de otras personas, ingreso proyectado excluido, cotización faltante, años unificados, 400 por `year`/`month` y 401 sin sesión o cuenta no verificada, en backend/src/test/java/com/amano/backend/statistics/StatisticsSummaryIT.java (FR-004, FR-008..FR-014, FR-026, FR-027)
- [ ] T027 [P] Regresión tras extraer `StatisticsConverter`: mismos importes, porcentajes, totales, años y `excludedCount` en `ExpenseStatisticsServiceTest`/`ExpenseStatisticsIT`; además, `GET /api/balance` (033) y los endpoints de saldos, presupuestos y propuestas de liquidación grupales devuelven exactamente los mismos resultados que antes, en backend/src/test/java/com/amano/backend/expense/ExpenseStatisticsServiceTest.java, backend/src/test/java/com/amano/backend/balance/BalanceIT.java, backend/src/test/java/com/amano/backend/groupexpense/GroupBalanceControllerIT.java y backend/src/test/java/com/amano/backend/groupexpense/GroupSettlementControllerIT.java (FR-028, SC-006)
- [ ] T028 [P] Tests de `getStatisticsSummary` en frontend/src/api/statisticsClient.test.js: éxito, 400, 401 y error de red (FR-027, plan D11)
- [ ] T029 [P] Tests de `PieChart` aplicado a la comparación en frontend/src/components/charts/PieChart.test.jsx: dos porciones, círculo completo con una sola, ambos totales en cero sin gráfico, `aria-label` por porción y detalle con importe y porcentaje (FR-015, FR-016)
- [ ] T030 [P] Tests de frontend/src/components/Carousel.test.jsx: Slide 1 inicial, flechas y puntos, extremos deshabilitados, teclado, `aria-label` de posición, swipe horizontal, swipe corto/vertical sin cambio y sin bloquear el scroll, `prefers-reduced-motion` (FR-002, FR-030, FR-031, SC-009)
- [ ] T031 [P] Tests de `incomeCategoryColor` y de los colores de ingreso en frontend/src/validation/expenseCatalog.test.js (FR-022, FR-028)
- [ ] T032 [P] Actualizar frontend/src/pages/StatisticsPage.test.jsx: pedido único, período por defecto y en la dirección, cambio de mes y año conservando slide, carrusel con dos slides, bloque de ingresos, torta comparativa con balance debajo, estados vacíos, avisos de excluidos, error con "Reintentar", sesión vencida y ausencia de bloques parciales (FR-001..FR-006, FR-023..FR-027)

---

## Fase 5: Polish & Cobertura

- [ ] T033 Revisar accesibilidad de la pantalla completa: orden de foco, nombres accesibles de carrusel, puntos, flechas y porciones, anuncio del slide activo y del signo del balance, contraste de colores de ingreso en ambos temas y información nunca solo por color (FR-022, FR-025, FR-030, SC-009)
- [ ] T034 Validar el comportamiento táctil y responsive en pantalla angosta: swipe sin scroll horizontal de la página, scroll vertical intacto, un slide por vez, torta y lista apiladas y torta comparativa con su balance legibles (FR-025, FR-031)
- [ ] T035 Ejecutar `mvn -B verify` en backend/ y `npm run lint && npm run test:coverage && npm run build` en frontend/; corregir lo que falle e informar la cobertura alcanzada (≥ 85 %, SC-007). Incluir la verificación cronometrada de performance de SC-008: con 1000 ingresos y gastos en el período, la pantalla y sus gráficos deben mostrarse en menos de 2 s desde que se elige el período, sin recargar la página
- [ ] T036 Revisar la trazabilidad FR → tarea y la consistencia entre spec.md, plan.md, contracts/statistics-summary-api.md y este archivo (endpoint, nombres de campos, carrusel y fases); confirmar que la regresión incluye `GET /api/balance` (033) y los saldos, presupuestos y propuestas de liquidación grupales; actualizar `checklists/requirements.md` si cambió alguna clarificación (FR-028, SC-006, constitución II)

---

## Dependencies

- Fase 1 → Fase 2. T009 depende de T005–T008; T010 de T009; T011 se puede hacer en paralelo con T009.
- Fase 3: T012 depende de T003 (contrato); T013–T017 son independientes entre sí (T014 es una definición de diseño); T018–T021 modifican `StatisticsPage.jsx` y se ejecutan en orden; T022 depende de T019–T021.
- Fase 4 empieza con T023 (validación manual) después de T019–T022; T024–T032 dependen de T023 y del código que prueban.
- Fase 5 después de la Fase 4. T035 al final, después de T033 y T034.

## Parallel Opportunities

- T001–T004 (DTOs y contrato) entre sí.
- T005 y T007 en paralelo; T006 depende de T005 por editar el mismo `IncomeRepository.java`; T008 depende de T007.
- T012–T017 en paralelo con el backend de la Fase 2 (contrato ya fijado en T003/T004); T018–T021 son secuenciales por compartir `StatisticsPage.jsx`.
- T024–T032 entre sí, por archivos distintos, una vez completada T023.
- T033 y T034 en paralelo antes de T035.

## Implementation Strategy

1. **MVP (US1 + US2)**: T001–T022 con foco en el bloque de ingresos y el carrusel; permite validar la pantalla completa con la comparación básica.
2. **US3–US5**: comparación, períodos y monedas ya están cubiertos por T009, T018–T021 y T023; completar sus pruebas en T025, T029 y T032.
3. **Cierre**: pruebas automatizadas (T024–T032), accesibilidad/responsive (T033–T034), cobertura y trazabilidad (T035–T036).

## Notas

- No hay migraciones ni dependencias nuevas: todo se apoya en las tablas y componentes existentes (plan, constitución XI).
- `/api/expenses/statistics` y `/api/balance` no se modifican; la regresión de T027 y T031 es obligatoria (FR-028, SC-006).
- El endpoint `GET /api/stats/summary` es de solo lectura; ninguna tarea crea, edita o elimina movimientos (FR-029).
