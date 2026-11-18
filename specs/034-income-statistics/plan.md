# Implementation Plan: Estadísticas y analíticas de ingresos personales

**Branch**: `feat/income-statistics` | **Date**: 2026-11-18 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/034-income-statistics/spec.md`

## Summary

Extender la pantalla `/statistics` de 032 con un bloque de ingresos personales, un bloque comparativo de ingresos contra gastos combinados y un carrusel de dos slides para los gastos personales y grupales. El backend expone un único `GET /api/stats/summary?year=&month=` que compone la agregación de gastos ya existente (032) con una agregación nueva de ingresos efectivizados (033), devuelve la comparación y los años unificados. El frontend reemplaza las dos tortas simultáneas por un carrusel accesible (swipe, flechas, puntos y teclado), agrega una dona comparativa y mantiene la torta de ingresos. Sin migraciones ni dependencias nuevas.

## Technical Context

**Language/Version**: Java 25 + Spring Boot 4.1 (backend); React 19 + Vite + React Router (frontend)

**Primary Dependencies**: Spring Data JPA (Hibernate), Spring Security (cookie `amano_auth`), `ExchangeRateProvider` existente (BCRA con caché LRU), React Router `useSearchParams`. Sin librerías nuevas de carrusel ni de gráficos.

**Storage**: PostgreSQL, tablas existentes `incomes`, `expenses`, `group_expenses`, `group_expense_shares` y `groups`. **Sin migraciones nuevas.**

**Testing**: JUnit + Spring Boot Test + Testcontainers (`*Test`, `*IT`); Vitest + React Testing Library (`*.test.jsx`). Cobertura ≥ 85 %.

**Target Platform**: Web (navegador) servida por Docker Compose

**Project Type**: Monorepo web (backend + frontend)

**Performance Goals**: Un pedido por período; consultas agregadas por categoría, moneda y fecha, y como máximo una cotización por par `(moneda, fecha)` del mes, casi siempre desde la caché. La pantalla responde en menos de 2 s con hasta 1000 movimientos (SC-008).

**Constraints**: Sin dependencias nuevas; sin cambios de esquema; sin romper `GET /api/expenses/statistics` ni `GET /api/balance`.

**Scale/Scope**: Movimientos de una persona en un mes (decenas a cientos de filas antes de agregar).

## Constitution Check

| Principio | Cumplimiento |
|-----------|--------------|
| I. Arquitectura | Nuevo paquete `statistics` como capa de composición; reutiliza `ExpenseStatisticsService` y la consulta de ingresos del paquete `income`. Frontend con componentes propios en `components/charts` y `components/`. |
| II. SDD | spec → plan → tasks antes de implementar; trazabilidad FR → tarea. |
| III. Incremental | US1 y US2 (P1) primero; US3–US5 después. El carrusel y los bloques se pueden validar por separado. |
| IV. Alcance | Solo lo acordado: dos slides de gastos, bloque de ingresos, dona comparativa y carrusel accesible. Sin autoplay, loop, año completo ni nuevas agrupaciones. |
| V. Pruebas | Unitarias, integración y frontend; regresión de 032/033; cobertura ≥ 85 % con `mvn -B verify` y `npm run test:coverage`. |
| VI. Reglas de negocio | Solo lectura. Ingresos efectivizados, gastos combinados de 032 y conversión por fecha. No altera saldos, deudas ni balance de 033. |
| VII. Contratos REST | Nuevo endpoint documentado en [contracts/statistics-summary-api.md](./contracts/statistics-summary-api.md); `/api/expenses/statistics` y `/api/balance` no cambian. |
| VIII. Validación | `year` y `month` obligatorios y validados en backend (400 con `properties.fields`); el frontend trata valores inválidos de la dirección como el período actual. |
| IX. Seguridad | La persona sale de la sesión; las consultas filtran por `user_id`. `/api/stats/**` se agrega a la regla de cuenta verificada de `SecurityConfig`. |
| X. Mantenibilidad | Se extrae la conversión compartida de 032 y se reutilizan `ExpenseStatisticsService`, `PieChart`, `expenseCatalog.js` y los selectores de período. |
| XI. Entorno | Sin dependencias ni migraciones; las verificaciones existentes siguen siendo las mismas. |
| XII. Revisión | El cambio se integra por PR a la rama del sprint. |

## Decisiones (research)

- **D1 – Un único endpoint para la pantalla**: `GET /api/stats/summary?year=&month=` en un nuevo `StatisticsSummaryController`, bajo `/api/stats/**`. Devuelve en una sola respuesta los slides de gastos, el bloque de ingresos, la comparación y los años unificados. Evita dos pedidos con monedas o períodos potencialmente distintos y deja la comparación y la unión de años en el backend, donde son testeables. `GET /api/expenses/statistics` (032) queda intacto para regresión y otros consumidores.
- **D2 – Composición, no duplicación**: `StatisticsSummaryService` llama a `ExpenseStatisticsService.statistics(...)` para obtener `personal`, `group`, `total`, `excludedCount` y los años de gastos, y a un nuevo `IncomeStatisticsService.statistics(...)` para el bloque de ingresos. La comparación se arma con esos dos resultados. No se reimplementan las reglas de gastos ni de grupos.
- **D3 – Ingresos efectivizados**: la consulta nueva filtra `income_date BETWEEN :from AND :to AND income_date <= :today`, con `today = LocalDate.now(ZoneOffset.UTC)`, igual que `IncomeResponse.isProjected` y el balance de 033. Los ingresos proyectados no se agregan ni se informan.
- **D4 – Agregación de ingresos en SQL**: `IncomeRepository.sumByCategoryCurrencyAndDate(userId, from, to, today)` agrupa por `(categoría, moneda, income_date)` con `SUM(amount)` y `COUNT(id)`, en un `IncomeCategorySubtotal`. La conversión se hace una vez por subtotal, con la cotización de su fecha.
- **D5 – Conversión compartida**: se extrae de `ExpenseStatisticsService` la lógica de conversión y caché por `(moneda, fecha)` a un `StatisticsConverter` en el paquete `statistics`, que recibe `(currency, date, amount, count)` y acumula la cantidad de movimientos excluidos. `ExpenseStatisticsService` lo reutiliza sin cambiar su comportamiento; los tests de 032 son la red de seguridad. Así el bloque de ingresos no duplica la regla de cotización faltante (FR-011, FR-024).
- **D6 – Moneda única**: `StatisticsSummaryService` toma la moneda del resultado de gastos (regla de 032: primera preferida por orden de `Currency`, ARS por defecto) y se la pasa a `IncomeStatisticsService`. Se verifica que ambas partes usen esa moneda; no hay dos cálculos independientes de moneda.
- **D7 – Comparación y redondeo**: el total de gastos es el `total` de 032 (personales + partes grupales, ya redondeado). El total de ingresos es el `total` del bloque de ingresos, ya redondeado. El balance se calcula en `BigDecimal` a partir de esos dos totales redondeados y se formatea a 2 decimales, para que la etiqueta cierre exactamente con lo que se muestra (FR-016, SC-004).
- **D8 – Años unificados**: la respuesta une los años de gastos que ya devuelve 032 con `IncomeRepository.findReceivedIncomeYears(userId, today)` (solo ingresos efectivizados), sin duplicados y en orden descendente. El frontend agrega el año actual, como en 032/031.
- **D9 – Contrato de respuesta**: `StatisticsSummaryResponse` contiene `year`, `month`, `currency`, `expenses` (`total`, `excludedCount`, `personal`, `group`), `income` (`total`, `excludedCount`, `categories`), `comparison` (`income`, `expenses`, `balance`) y `years`. Importes y porcentajes como cadenas con 2 y 1 decimales respectivamente. Ver contrato.
- **D10 – Seguridad**: se agrega `/api/stats/**` al `requestMatchers(...).access(verifiedAccount(...))` de `SecurityConfig`. El controlador toma la persona del `SecurityContext` y responde 401 sin sesión, como `ExpenseStatisticsController`. No hay parámetros de usuario.
- **D11 – Un solo pedido en el frontend**: `getStatisticsSummary({ year, month })` en `api/statisticsClient.js` devuelve `{ ok, statistics }`, `{ ok: false, status }` o `{ ok: false, network: true }`. La página deja de pedir `getExpenseStatistics` y muestra un único estado de carga/error; si falla el pedido no se muestran bloques parciales (FR-027).
- **D12 – Carrusel controlado y accesible**: `components/Carousel.jsx` recibe `slides`, `index`, `onIndexChange` y `ariaLabel`. El índice vive en `StatisticsPage`, fuera del componente de resultados, para conservar el slide al cambiar el período (FR-005) y volver al Slide 1 al recargar (FR-006). El carrusel usa `role="region"` con `aria-roledescription="carrusel"`, cada slide `role="group"` con `aria-roledescription="slide"` y `aria-label="1 de 2"`, puntos `<button>` con `aria-label` y `aria-current`, y botones "Anterior"/"Siguiente" deshabilitados en los extremos. No hay autoplay ni loop.
- **D13 – Swipe sin romper el scroll**: `touch-action: pan-y` en el contenedor; se escuchan eventos de puntero/touch y solo se cambia de slide cuando el desplazamiento horizontal supera un umbral (por ejemplo, 40 px) y es mayor que el vertical. Un gesto corto o vertical no cambia de slide ni llama a `preventDefault` (FR-031). Las flechas del teclado `ArrowLeft`/`ArrowRight` y los botones ofrecen la misma navegación.
- **D14 – Dona comparativa**: `components/charts/DonutChart.jsx` dibuja dos arcos SVG con radio interior sobre la geometría compartida de `PieChart` (se extrae `charts/geometry.js`). Cada porción es enfocable, tiene `aria-label` con importe y porcentaje, y la leyenda textual muestra ambos totales y el balance. Una sola porción con importe se dibuja como anillo completo; ambos totales en cero no muestran dona (FR-015, FR-016).
- **D15 – Colores de ingreso**: nuevas variables CSS `--color-ingreso-<código>` en `StatisticsPage.scss` para los temas claro y oscuro, y `incomeCategoryColor(code)` en `expenseCatalog.js`. La paleta es distinta de la de gastos y los nombres/importes/porcentajes de la lista mantienen la información sin depender del color (FR-022).
- **D16 – Reutilización de `Breakdown`**: el componente actual de torta + lista se parametriza con `options` y `colorOf`, para usarlo tanto en gastos como en ingresos sin duplicar la lista ni el detalle al pasar el mouse.
- **D17 – Estado en la dirección**: se conserva `?year=&month=` de 032, con `replace`; el slide no se escribe en la dirección. Un valor inválido se trata como el actual. El año actual lo decide el navegador.
- **D18 – Respuestas fuera de orden**: el componente de resultados se monta con `key` por período y descarta respuestas canceladas, como en 032 (D12). El cambio de slide no dispara pedidos.

## Project Structure

### Documentation (this feature)

```text
specs/034-income-statistics/
├── spec.md
├── plan.md
├── contracts/statistics-summary-api.md
├── checklists/requirements.md
└── tasks.md                      # siguiente paso
```

### Source Code (repository root)

```text
backend/src/main/java/com/amano/backend/
├── config/SecurityConfig.java                 # + /api/stats/** con cuenta verificada
├── statistics/
│   ├── StatisticsSummaryController.java       # NUEVO: GET /api/stats/summary
│   ├── StatisticsSummaryService.java          # NUEVO: composición, comparación y años
│   ├── StatisticsSummaryResponse.java         # NUEVO: expenses + income + comparison + years
│   ├── IncomeStatisticsService.java           # NUEVO: agregación y porcentajes de ingresos
│   ├── IncomeStatisticsResponse.java          # NUEVO: total, excludedCount, categories
│   ├── IncomeCategorySubtotal.java            # NUEVO: proyección de la consulta agregada
│   └── StatisticsConverter.java               # NUEVO: conversión y exclusiones compartidas
├── expense/ExpenseStatisticsService.java      # reutiliza StatisticsConverter
├── income/IncomeRepository.java               # + subtotales por categoría y años efectivizados

backend/src/test/java/com/amano/backend/
├── statistics/IncomeStatisticsServiceTest.java
├── statistics/StatisticsSummaryServiceTest.java
├── statistics/StatisticsSummaryIT.java
└── expense/ExpenseStatisticsServiceTest.java  # regresión tras extraer el conversor

frontend/src/
├── api/statisticsClient.js                    # NUEVO: getStatisticsSummary
├── api/statisticsClient.test.js               # NUEVO
├── components/Carousel.jsx                    # NUEVO: carrusel accesible con swipe
├── components/Carousel.scss                   # NUEVO
├── components/Carousel.test.jsx               # NUEVO
├── components/charts/DonutChart.jsx           # NUEVO: dona comparativa
├── components/charts/DonutChart.scss          # NUEVO
├── components/charts/DonutChart.test.jsx      # NUEVO
├── components/charts/geometry.js              # NUEVO: geometría compartida de arcos
├── components/charts/PieChart.jsx             # usa geometry.js
├── components/charts/PieChart.test.jsx        # regresión
├── pages/StatisticsPage.jsx                   # carrusel + ingresos + comparación
├── pages/StatisticsPage.scss                  # estilos del carrusel, dona y colores de ingreso
├── pages/StatisticsPage.test.jsx              # actualizado
├── validation/expenseCatalog.js               # + incomeCategoryColor
└── validation/expenseCatalog.test.js          # + color de ingreso
```

**Structure Decision**: monorepo web existente. El paquete `statistics` es una capa de composición de solo lectura: reutiliza la agregación de gastos de 032 y agrega la de ingresos de 033 sin modificar sus reglas de dominio. El carrusel y la dona son componentes de presentación reutilizables en `components/`.

## Riesgos

- **Refactor del conversor de 032**: extraer `StatisticsConverter` puede cambiar el comportamiento si se altera el redondeo o la caché. Mitigación: mover la lógica tal cual, cubrirla con los tests existentes de `ExpenseStatisticsServiceTest`/`ExpenseStatisticsIT` y agregar casos de cotización faltante.
- **Swipe en navegadores táctiles**: los eventos de puntero y touch varían; mitigación: umbral horizontal y `touch-action: pan-y`, con botones y teclado como alternativas siempre disponibles. Tests con eventos sintéticos y validación manual en móvil.
- **Dos fuentes de años**: si el endpoint de ingresos olvida filtrar proyectados, el selector mostraría años vacíos. Mitigación: consulta `findReceivedIncomeYears` con `today` y test de integración específico.
- **Diferencia con el balance de 033**: la comparación incluye gastos grupales y puede diferir del balance personal. Es una decisión explícita; la UI usa "Balance del período" para no confundirlo con "Balance neto" de 033.
- **Respuesta única**: si falla el pedido, no se muestran tortas parciales; es el comportamiento deseado (FR-027) y debe cubrirse con test de error.

## Complexity Tracking

Sin violaciones a justificar. El único refactor estructural (conversor compartido y geometría de gráficos) está justificado por la reutilización entre 032 y 034 y por evitar duplicar reglas de conversión.
