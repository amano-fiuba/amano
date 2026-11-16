---
description: "Task list for Ver estadísticas de mis gastos"
---

# Tasks: Ver estadísticas de mis gastos

**Input**: Design documents from `/specs/032-estadisticas-gastos/`

**Prerequisites**: plan.md, spec.md, contracts/expense-statistics-api.md

**Tests**: Como en 031, la implementación se valida primero manualmente; las pruebas automatizadas (Fase 6) se escriben después de esa validación y son obligatorias para cerrar la historia (constitución V, cobertura ≥ 85 %).

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Puede ejecutarse en paralelo (archivos distintos, sin dependencias pendientes)
- **[Story]**: Historia a la que pertenece (US1, US2, US3)

---

## Phase 1: Foundational (backend compartido)

- [ ] T001 Agregar a `ExpenseRepository` la consulta de subtotales personales por categoría, moneda y fecha del gasto (`SUM(amount)`, `COUNT(id)`) entre dos fechas, filtrada por `userId`, en backend/src/main/java/com/amano/backend/expense/ExpenseRepository.java (FR-006, plan D2)
- [ ] T002 [P] Crear `SharedExpenseStatisticsRepository` con dos consultas nativas: subtotales de las partes de la persona (`participant_type = 'REGISTERED'`, `user_id`) por `COALESCE(e.category, 'OTHER')`, `COALESCE(g.primary_currency, e.currency)` y `e.expense_date` entre dos fechas; y los años con gastos personales o partes grupales (`UNION`, descendente), en backend/src/main/java/com/amano/backend/expense/SharedExpenseStatisticsRepository.java (FR-002, FR-007, FR-012, plan D2, D3, D9)
- [ ] T003 [P] Crear `ExpenseStatisticsResponse` (`year`, `month`, `currency`, `total`, `excludedCount`, `personal`, `group`, `years`; cada tipo con `total` y `categories[{category, amount, percentage}]`, importes y porcentajes como cadenas) en backend/src/main/java/com/amano/backend/expense/ExpenseStatisticsResponse.java (FR-005)

## Phase 2: US1 – Ver en qué categorías gasté este mes (P1) 🎯 MVP

- [ ] T004 [US1] Crear `ExpenseStatisticsService`: validación de `year` (1–9999) y `month` (1–12) obligatorios, rango del mes con `YearMonth`, moneda del cálculo (primera preferida por orden de `Currency`, ARS por defecto), suma por categoría de cada tipo, totales, porcentajes sobre el total del tipo, redondeo `HALF_UP` al final, orden por importe descendente y luego por `ordinal()`, y años, en backend/src/main/java/com/amano/backend/expense/ExpenseStatisticsService.java (FR-005..FR-008, FR-011..FR-013, plan D5, D7, D8, D10). En esta fase, sin conversión: solo subtotales en la moneda del cálculo (la conversión llega en T013)
- [ ] T005 [US1] Crear `ExpenseStatisticsController` con `GET /api/expenses/statistics` (persona desde la sesión, 401 sin sesión como `ExpenseController`) en backend/src/main/java/com/amano/backend/expense/ExpenseStatisticsController.java (FR-005, FR-019, plan D1)
- [ ] T006 [P] [US1] Agregar `getExpenseStatistics({ year, month })` y el mensaje "No pudimos cargar tus estadísticas. Intentá nuevamente" en frontend/src/api/expenseClient.js (FR-020)
- [ ] T007 [P] [US1] Agregar el color fijo de cada una de las 10 categorías en frontend/src/validation/expenseCatalog.js, distinguibles y con contraste en los temas claro y oscuro (FR-016, plan D14)
- [ ] T008 [P] [US1] Crear frontend/src/components/charts/PieChart.jsx: torta SVG con un arco por categoría (círculo completo si hay una sola), porciones enfocables, detalle "Categoría · importe · porcentaje" al pasar el mouse, enfocar o tocar, `role="img"` con `aria-label` de resumen (FR-013..FR-016, plan D13)
- [ ] T009 [US1] Crear frontend/src/pages/StatisticsPage.jsx: total combinado y moneda del cálculo ("Importes en Peso argentino (ARS)"), torta "Personales" y torta "Grupales" con su total y su lista (color, nombre, importe, porcentaje), mensajes de vacío general y por tipo con enlace para registrar un gasto, "Buscando…" y error con "Reintentar" (FR-014, FR-015, FR-017, FR-020)
- [ ] T010 [P] [US1] Crear frontend/src/pages/StatisticsPage.scss: torta y lista lado a lado en escritorio, una debajo de la otra en celular
- [ ] T011 [US1] Registrar la ruta protegida `/statistics` en frontend/src/App.jsx, agregar `StatisticsIcon` en frontend/src/components/NavIcons.jsx y la entrada "Estadísticas" junto a "Actividad personal" en frontend/src/components/AppHeader.jsx (FR-001, plan D15)

## Phase 3: US2 – Cambiar de mes (P1)

- [ ] T012 [US2] Agregar a frontend/src/pages/StatisticsPage.jsx los selectores de mes (enero a diciembre) y año (actual + `years`, descendente), con el mes y el año actuales por defecto, período en la dirección (`?year=&month=`, omitidos si son los actuales, `replace`), valores inválidos tratados como el actual, el mes conservado al cambiar de año y descarte de respuestas viejas con `key` por período (FR-002..FR-004, plan D11, D12)

## Phase 4: US3 – Sumar gastos en distintas monedas (P2)

- [ ] T013 [US3] Convertir en `ExpenseStatisticsService` cada subtotal en otra moneda con `ExchangeRateProvider.getQuote(moneda, monedaCálculo, fecha)`, una vez por par (moneda, fecha); ante `ExchangeRateUnavailableException`, descartar el subtotal y sumar su cantidad a `excludedCount`, en backend/src/main/java/com/amano/backend/expense/ExpenseStatisticsService.java (FR-008..FR-010, plan D6)
- [ ] T014 [US3] Mostrar en frontend/src/pages/StatisticsPage.jsx el aviso "1 gasto no se pudo convertir a <moneda> y no está incluido." / "N gastos no se pudieron convertir…" cuando `excludedCount > 0` (FR-018)

## Phase 5: Validación manual

- [ ] T015 Validar con el usuario los escenarios de la spec en la app levantada con Docker Compose (US1, US2, US3 y casos límite), con datos de demo que incluyan gastos personales, de grupo y con amigos en varias monedas y meses

## Phase 6: Pruebas automatizadas (después de la validación manual)

- [ ] T016 [P] Unit test de `ExpenseStatisticsService` con repositorios y `ExchangeRateProvider` simulados: validación, moneda del cálculo (sin preferencias, varias preferencias), sumas, porcentajes, redondeo, orden y desempate, conversión, cotización faltante y `excludedCount`, en backend/src/test/java/com/amano/backend/expense/ExpenseStatisticsServiceTest.java
- [ ] T017 [P] Integration test (Testcontainers + MockMvc) de `GET /api/expenses/statistics`: personales del mes, partes grupales pagadas o no por la persona, gasto con amigo, gasto grupal sin categoría → `OTHER`, gasto de grupo sin parte propia, gastos de otras personas, series recurrentes sin doble conteo, años, 400 y 401, en backend/src/test/java/com/amano/backend/expense/ExpenseStatisticsIT.java
- [ ] T018 [P] Tests de `getExpenseStatistics` en frontend/src/api/expenseClient.test.js
- [ ] T019 [P] Tests de frontend/src/components/charts/PieChart.test.jsx: una porción por categoría, círculo completo con una sola, detalle al pasar el mouse, enfocar y tocar, `aria-label`
- [ ] T020 [P] Tests de frontend/src/pages/StatisticsPage.test.jsx: período por defecto, cambio de mes y año, dirección, valores inválidos, dos tortas y listas, vacío general y por tipo, aviso de excluidos, error y reintento, sesión vencida, entrada en la barra
- [ ] T021 Ejecutar `mvn -B verify` en backend/ y `npm run lint && npm run test:coverage && npm run build` en frontend/; informar cobertura (≥ 85 %)

## Dependencies

- Fase 1 → Fase 2. T004 depende de T001–T003; T005 de T004; T009 de T006–T008; T011 de T009.
- US2 (Fase 3) depende de T009. US3 (Fase 4) depende de T004 y T009.
- Fase 5 después de 2–4. Fase 6 después de 5; T021 al final.

## Parallel Opportunities

- T002 y T003 en paralelo con T001.
- Frontend T006–T008 y T010 en paralelo con el backend de la Fase 2.
- Todas las tareas de test T016–T020 entre sí.
