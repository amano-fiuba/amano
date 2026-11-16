# Implementation Plan: Ver estadísticas de mis gastos

**Branch**: `feat/estadisticas-gastos` | **Date**: 2026-11-16 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/032-estadisticas-gastos/spec.md`

## Summary

Agregar una pantalla `/statistics` con selectores de mes y año y dos gráficos de
torta por categoría: gastos personales y gastos grupales (de grupo y con amigos,
por la parte de la persona). El backend expone `GET /api/expenses/statistics`, que
agrega en SQL por categoría, moneda y fecha, convierte cada subtotal a la moneda
del cálculo con la cotización del BCRA de esa fecha y devuelve importes,
porcentajes, totales y los años con gastos. Sin migraciones. El gráfico se dibuja
con SVG propio, sin dependencias nuevas.

## Technical Context

**Language/Version**: Java 25 + Spring Boot 4.1 (backend); React 19 + Vite + React Router (frontend)

**Primary Dependencies**: Spring Data JPA (Hibernate), Spring Security (cookie `amano_auth`), `ExchangeRateProvider` existente (BCRA con caché LRU), React Router `useSearchParams`

**Storage**: PostgreSQL, tablas existentes `expenses`, `group_expenses`, `group_expense_shares` y `groups`. **Sin migraciones nuevas.**

**Testing**: JUnit + Spring Boot Test + Testcontainers (`*Test`, `*IT`); Vitest + React Testing Library (`*.test.jsx`). Cobertura ≥ 85 %.

**Target Platform**: Web (navegador) servida por Docker Compose

**Project Type**: Monorepo web (backend + frontend)

**Performance Goals**: Por pedido, tres consultas agregadas (personales, grupales, años) y como máximo una cotización por par (moneda, fecha) distinto del mes, casi siempre servida desde la caché.

**Constraints**: Sin dependencias nuevas (ni librería de gráficos); sin cambios de esquema.

**Scale/Scope**: Gastos de una persona en un mes (decenas a cientos de filas antes de agregar).

## Constitution Check

| Principio | Cumplimiento |
|-----------|--------------|
| I. Arquitectura | Servicio y controlador propios en el paquete `expense`, que lee los gastos grupales con una consulta de solo lectura; pantalla nueva en `pages/`. |
| II. SDD | spec → plan → tasks antes de implementar. |
| III. Incremental | Una historia del backlog; US1/US2 (P1) antes que US3 (P2). |
| IV. Alcance | Solo lo acordado: mes y año, primera moneda, personales + grupales por la parte propia, dos tortas. |
| V. Pruebas | Fase de tests al final, después de la validación manual, como en 031; cobertura ≥ 85 %. |
| VI. Reglas de negocio | Solo lectura; no altera gastos, repartos ni deudas. |
| VII. Contratos REST | Endpoint nuevo documentado en [contracts/expense-statistics-api.md](./contracts/expense-statistics-api.md). |
| VIII. Validación | `year` y `month` obligatorios y validados en el backend (400 con `properties.fields`). |
| IX. Seguridad | La persona sale de la sesión; las consultas filtran por `expenses.user_id` y `group_expense_shares.user_id`; ruta bajo `/api/expenses/**`, que ya exige cuenta verificada. |
| X. Mantenibilidad | Reutiliza `ExchangeRateProvider`, los selectores de mes/año de 031 y `expenseCatalog.js`. |

## Decisiones (research)

- **D1 – Endpoint**: `GET /api/expenses/statistics?year=&month=` en un `ExpenseStatisticsController` nuevo. Queda bajo `/api/expenses/**`, así que hereda la regla de cuenta verificada de `SecurityConfig` sin tocarla. La respuesta incluye `years` para no hacer un segundo pedido.
- **D2 – Agregación en SQL, conversión en Java**: la cotización depende de la fecha, así que se agrega por `(categoría, moneda, fecha)` con `SUM(importe)` y `COUNT(*)`, y cada subtotal se convierte una sola vez. Esto reduce las filas a lo sumo a categorías × monedas × días del mes, y el frontend nunca recibe gastos individuales (FR-005).
  - Personales (JPQL en `ExpenseRepository`): `SELECT category, currency, expenseDate, SUM(amount), COUNT(id) FROM Expense WHERE userId = :userId AND expenseDate BETWEEN :from AND :to GROUP BY category, currency, expenseDate`.
  - Grupales (consulta nativa en un repositorio de solo lectura del paquete `expense`): `group_expense_shares s JOIN group_expenses e ON e.id = s.expense_id LEFT JOIN groups g ON g.id = e.group_id WHERE s.participant_type = 'REGISTERED' AND s.user_id = :userId AND e.expense_date BETWEEN :from AND :to`, agrupando por `COALESCE(e.category, 'OTHER')`, la moneda de la parte (D3) y `e.expense_date`, con `SUM(s.amount)` y `COUNT(*)`.
- **D3 – Moneda de la parte grupal**: la parte se reparte desde `converted_amount`, que está en la moneda del grupo (`groups.primary_currency`, fija desde la creación del grupo: solo `createGroup` la asigna). En un gasto con amigo (`group_id IS NULL`) la "moneda del grupo" es la del gasto (`createWithFriend` usa `normalized.currency()`). Excepción: los gastos de grupo en otra moneda anteriores a 022 tienen `converted_amount IS NULL` (V19 solo rellenó los de la misma moneda) y sus partes quedaron en la moneda del gasto. Por eso la moneda de la parte es `CASE WHEN e.converted_amount IS NULL THEN e.currency ELSE COALESCE(g.primary_currency, e.currency) END`.
- **D4 – Series recurrentes**: en 009 y 028 la serie es la primera repetición y cada repetición generada es otra fila con su propia fecha y, en grupos, con sus propias partes copiadas. Sumar todas las filas del mes no cuenta nada dos veces. Las repeticiones grupales se generan hasta 7 días antes (028 FR-006), así que pueden caer en el mes siguiente; se cuentan por su fecha.
- **D5 – Moneda del cálculo**: la primera de `User.currencies` según el orden de `Currency.values()` (ARS, USD, EUR, …), igual que el orden de `conversionsOf` en `ExpenseService`; ARS si no hay preferencias. `User.currencies` es un `Set` sin orden, así que "la primera" solo puede definirse por catálogo.
- **D6 – Conversión y cotización faltante**: por cada subtotal en otra moneda se pide `ExchangeRateProvider.getQuote(moneda, monedaCálculo, fecha)`, que ya cae al último día hábil (hasta 7 días antes) y pasa por `CachingExchangeRateProvider`. Si lanza `ExchangeRateUnavailableException`, el subtotal se descarta y su `COUNT` se suma a `excludedCount` (FR-010). Las cotizaciones se piden una vez por par `(moneda, fecha)` dentro del pedido.
- **D7 – Redondeo**: los subtotales convertidos se suman sin redondear. Al final, los importes se redondean a 2 decimales y los porcentajes a 1 decimal, con `HALF_UP` (el modo que usa el resto del backend). El porcentaje se calcula sobre los importes sin redondear. El total combinado es la suma de los totales del tipo ya redondeados, para que en pantalla cierre con lo que se ve.
- **D8 – Orden y filtro**: categorías con importe > 0, ordenadas por importe descendente y luego por `ExpenseCategory.ordinal()`. Una parte grupal mide al menos 0,01 (`ck_group_expense_shares_amount`), así que no aparecen partes nulas.
- **D9 – Años**: `UNION` de `EXTRACT(YEAR FROM expense_date)` de los gastos personales y de los gastos grupales con parte de la persona, en orden descendente. El frontend agrega el año actual, como en 031 (D6).
- **D10 – Validación**: `year` (1..9999) y `month` (1..12) obligatorios; ausentes o inválidos → `FieldValidationException` (400) con los mensajes de 031 ("El año no es válido", "El mes no es válido").
- **D11 – Estado en la dirección**: `/statistics?year=2026&month=10`. Se omiten si coinciden con el mes y el año actuales. Cambiar un selector actualiza la dirección con `replace` (FR-004). Un valor inválido en la dirección se trata como el actual. El mes y el año actuales los decide el navegador (zona de la persona).
- **D12 – Respuestas fuera de orden**: como en 031 (D9), el componente de resultados se monta con `key` = período y descarta la respuesta si se desmontó.
- **D13 – Gráfico de torta en SVG propio** (`components/charts/PieChart.jsx`): sin librería (no hay ninguna en `package.json` y una dependencia por dos tortas no se justifica). Cada porción es un `<path>` de arco calculado a partir de los porcentajes; una sola categoría es un `<circle>` completo. Cada porción es enfocable (`tabIndex=0`), y al pasar el mouse, enfocarla o tocarla muestra debajo de la torta "Categoría · importe · porcentaje" (FR-015). El `<svg>` tiene `role="img"` y un `aria-label` con el resumen; la lista de categorías al lado siempre da la información en texto (FR-016).
- **D14 – Colores por categoría**: un mapa fijo `categoría → color` (las 10 categorías del catálogo) en `validation/expenseCatalog.js` o un módulo vecino, igual en las dos tortas y en cualquier mes. La paleta se elige en la implementación para que las 10 sean distinguibles y tengan contraste suficiente sobre el fondo en los dos temas de la app (claro y oscuro, `ThemeToggle`).
- **D15 – Acceso**: entrada "Estadísticas" en `AppHeader` junto a "Actividad personal", con un ícono nuevo `StatisticsIcon` en `NavIcons.jsx` (torta simple), y ruta protegida `/statistics` en `App.jsx`, igual que `/activity`.
- **D16 – Etiquetas y montos**: nombres de categoría con `labelOf(CATEGORY_OPTIONS, …)`, montos con `formatAmount` y nombre de la moneda con el catálogo de monedas de `expenseCatalog.js` ("Importes en Peso argentino (ARS)").

## Project Structure

### Documentation (this feature)

```text
specs/032-estadisticas-gastos/
├── spec.md
├── plan.md
├── contracts/expense-statistics-api.md
├── tasks.md                      # siguiente paso
└── checklists/requirements.md
```

### Source Code (repository root)

```text
backend/src/main/java/com/amano/backend/expense/
├── ExpenseStatisticsController.java    # NUEVO: GET /api/expenses/statistics
├── ExpenseStatisticsService.java       # NUEVO: validación, moneda, conversión, totales y porcentajes
├── ExpenseStatisticsResponse.java      # NUEVO: { year, month, currency, total, excludedCount, personal, group, years }
├── SharedExpenseStatisticsRepository.java  # NUEVO: consultas nativas de partes grupales y años
└── ExpenseRepository.java              # + subtotales personales por categoría, moneda y fecha

frontend/src/
├── api/expenseClient.js                # + getExpenseStatistics({ year, month })
├── components/charts/PieChart.jsx      # NUEVO: torta SVG accesible
├── components/NavIcons.jsx             # + StatisticsIcon
├── components/AppHeader.jsx            # + entrada "Estadísticas"
├── pages/StatisticsPage.jsx            # NUEVO: selectores, total, dos tortas con su lista, vacíos y error
├── pages/StatisticsPage.scss           # NUEVO: dos columnas en escritorio, una en celular
├── validation/expenseCatalog.js        # + colores por categoría
└── App.jsx                             # ruta /statistics
```

**Structure Decision**: monorepo web existente; no se crean módulos nuevos. El servicio vive en `expense` porque las estadísticas son de "mis gastos"; lee los gastos grupales con una consulta propia y no depende de los servicios de `groupexpense`.

## Riesgos

- **Cotizaciones del BCRA**: un mes con muchas monedas y fechas distintas puede pedir varias cotizaciones la primera vez. La caché LRU existente lo amortigua; si el BCRA no responde, esos subtotales quedan excluidos y se avisan (no falla toda la pantalla).
- **Diferencias con la actividad**: la actividad convierte y redondea cada gasto por separado; las estadísticas convierten subtotales diarios y redondean al final. La diferencia es de centavos y queda dentro de SC-002.

## Complexity Tracking

Sin violaciones a justificar.
