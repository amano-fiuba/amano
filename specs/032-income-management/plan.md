# Implementation Plan: Ingresos personales y balance neto

**Branch**: `feat/income-management` | **Date**: 2026-11-17 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/032-income-management/spec.md`

## Summary

Se agregan ingresos personales **inmutables** (solo crear y consultar) y un **balance neto** (ingresos efectivizados − gastos registrados, en la moneda principal, con la cotización de la fecha de cada movimiento). Backend: módulo `income` nuevo, paralelo a `expense`, con tabla propia `incomes` (migración `V38`), idempotencia por `(user_id, idempotency_key)` embebida en la tabla —igual que `expenses`— y los mismos mecanismos de errores `ProblemDetail`. Frontend: la pantalla `/activity` gana el control **Gastos | Ingresos** y una tarjeta de balance; el formulario de alta existente (`ExpenseForm`) se parametriza con `kind` (`EXPENSE` | `INCOME`) y se agrega una página de detalle de solo lectura. No se toca ningún endpoint, tabla ni lógica de gastos de grupo.

## Technical Context

**Language/Version**: Java 25 (Spring Boot 4.1.1), JavaScript (React) en el frontend

**Primary Dependencies**: Spring Web, Spring Data JPA, Flyway, Spring Security (cookie JWT `amano_auth`), JaCoCo; frontend: React, react-router, react-hook-form, Vitest. Sin dependencias nuevas.

**Storage**: PostgreSQL; migraciones Flyway en `backend/src/main/resources/db/migration` (última: `V37`). Regla del equipo: solo `CREATE`, sin `DELETE`/`DROP` de datos.

**Testing**: JUnit (`*Test`, `*IT` con la base real como `ExpenseSearchIT`/`RegisterExpenseIT`), `mvn verify` con JaCoCo; Vitest con `npm run test:coverage`.

**Target Platform**: Linux server (Docker Compose), navegador web

**Project Type**: Web application (backend + frontend)

**Performance Goals**: búsqueda ≤ 2 s con 1000 ingresos (SC-005); balance con cotizaciones agrupadas por `(moneda, fecha)`, no por movimiento.

**Constraints**: contratos REST bajo `/api` sin `/v1`; errores `ProblemDetail` en español; cobertura de líneas ≥ 85 % en ambos módulos; sin cambios incompatibles en `/api/expenses/**` ni `/api/groups/**`.

**Scale/Scope**: personas con cientos de movimientos al año; 1 tabla nueva, 4 endpoints de lectura/escritura de ingresos + 1 de balance; ~8 componentes/páginas del frontend tocados o nuevos.

Todos los puntos de contexto se resolvieron con el código existente (ver [research.md](./research.md)); no quedan `NEEDS CLARIFICATION`.

## Constitution Check

*GATE: pasa antes de la fase 0 y se re-evalúa tras el diseño.*

| Principio | Evaluación |
|-----------|------------|
| I. Arquitectura y separación | Módulo `income` paralelo a `expense`; se reutilizan `ExchangeRateProvider`, `ProblemDetailExceptionHandler`, `FieldValidationException`, normalizadores de monto y descripción. Sin refactor de `expense`. ✅ |
| II. SDD y trazabilidad | spec → clarify → plan; las tareas (`/speckit.tasks`) apuntarán a FR y escenarios. ✅ |
| III. Entrega incremental | Historias implementables por separado (ver "Orden de entrega"). ✅ |
| IV. Alcance | Sin edición, borrado, recurrencia automática, rango en pantalla ni grupos (fuera de alcance de la spec). ✅ |
| V. Calidad y cobertura | Pruebas unit/IT en backend y Vitest en frontend; umbral 85 % (JaCoCo en `mvn verify`, `npm run test:coverage`). ✅ |
| VI. Reglas de negocio | Reglas de la spec reflejadas en el servicio; ningún invariante nuevo no acordado. ✅ |
| VII. Contratos REST | [contracts/personal-incomes-api.md](./contracts/personal-incomes-api.md) es la fuente; cliente y servidor se alinean con él. ✅ |
| VIII. Validación y errores | Validación en backend (fuente de verdad) y en el formulario; errores por campo. ✅ |
| IX. Seguridad | **Hallazgo**: `SecurityConfig` exige cuenta verificada solo para `/api/users|expenses|groups|payments|currencies/**`; hay que agregar `/api/incomes/**` y `/api/balance/**` o quedarían accesibles a cuentas no verificadas (FR-013/SC-003). Dueño siempre desde la sesión. ✅ con ese cambio. |
| X. Mantenibilidad | Sin dependencias nuevas; se extraen helpers compartidos del cliente (`problemOf`, `safeFields`) en vez de duplicarlos. ✅ |
| XI/XII. CI y PRs | PR de feature hacia `sprint-n`; sin cambios de pipeline. ✅ |

**Post-diseño**: sin violaciones; no se completa "Complexity Tracking".

## Decisiones de arquitectura

1. **Tabla propia `incomes`** (no discriminador en `expenses`): evita tocar búsqueda 031, recurrentes y grupos. (research R1)
2. **Idempotencia embebida** en `incomes(user_id, idempotency_key)` con `UNIQUE`, igual que `expenses`; reintento con el mismo cuerpo = `200` con el mismo ingreso, otro cuerpo = `422 IDEMPOTENCY_KEY_REUSED`, carrera resuelta por el índice único (`resolveConcurrentRegistration`). **Corrige** `data-model.md` anterior, que preveía una tabla aparte. (R2)
3. **Inmutabilidad**: `IncomeController` solo mapea `POST /api/incomes`, `GET /api/incomes/search`, `GET /api/incomes/years`, `GET /api/incomes/{id}` y `GET /api/balance`. Cualquier otro método sobre esas rutas ya produce `405` por `handleMethodNotSupported` (mismo mecanismo que `GET /api/expenses` tras 031). La entidad no expone setters públicos de edición. (R3)
4. **Proyectado derivado** de `income_date > hoy`; sin columna de estado. "Hoy" se evalúa en **UTC**, con corte a la medianoche UTC, en lecturas y balance; en el alta se valida el tope de 1 año con la zona del dispositivo (`deviceTimeZone`), igual que gastos. (R5)
5. **Balance**: consulta agregada por `(moneda, fecha)` para ingresos efectivizados, ingresos proyectados y gastos; cada grupo se convierte con `ExchangeRateProvider.getQuote(moneda, principal, fecha)`. Ingresos efectivizados y gastos: si falta una cotización, `503` sin balance parcial. Ingresos **proyectados** (fecha futura): se convierten con la cotización de su fecha y, como casi nunca existe para una fecha futura, con la cotización de **hoy** como fallback; si tampoco hay cotización de hoy, `projectedIncome` se omite y el balance responde `200` (el `503` nunca lo causa un ingreso proyectado). (R4)
6. **Moneda principal**: primera de `UserAccountResponse.currencies` por prioridad ARS→USD→EUR→otra, ARS por defecto; es la misma regla que `preferredCurrency()` del frontend. (R6)
7. **Frontend**: el "modal de creación" es en realidad la página `/expenses/new` (`NewExpensePage` + `ExpenseForm`) con `ExpenseSummaryDialog` como confirmación. Se reutilizan parametrizados por `kind`; el detalle es una página de solo lectura. Tras guardar un ingreso, `NewIncomePage` redirige a `/activity` con `state.refreshBalance` y `BalanceCard` (sin caché) vuelve a pedir el balance, de modo que el neto se recalcula de inmediato. (R7)
8. **Orden por defecto**: los ingresos se listan por **fecha del ingreso descendente** (desempate `id` descendente); a diferencia de gastos, que ordena por registro. Orden fijo, no configurable.
9. **Extracción acotada en `ActivityPage`**: ver "Justificación de la extracción en ActivityPage".

## Justificación de la extracción en ActivityPage

La constitución (principios I y IV) prohíbe refactors ajenos al alcance sin justificación. Esta extracción se justifica porque la vista de ingresos debe tener **exactamente** el mismo buscador, selectores de mes y año y paginación que gastos (FR-015), y `ActivityPage.jsx` tiene 532 líneas con esas piezas definidas como funciones internas (`pagesOf`, `pageSizeFor`, `rowHeightOf`, `Pagination`, filtros). Alternativas rechazadas: copiar el archivo (dos implementaciones divergentes del mismo comportamiento, contra el principio X) o reescribir la pantalla. Límites: se extrae **solo** lo que ambas vistas usan; la lógica de gastos (`ExpenseItem`, `ActivityResults`) no se toca; el paso es un refactor puro (T055) que debe dejar `ActivityPage.test.jsx` en verde **sin modificarlo**, y recién después se agrega el control Gastos | Ingresos (T056).

## Supuestos de diseño

- **`projected` en UTC**: la condición `projected` (`income_date > hoy`) se evalúa en UTC, con corte a la medianoche UTC, tanto en las respuestas como en el balance. Cerca de esa hora una persona en otra zona puede ver un ingreso del día como proyectado o efectivizado unas horas antes o después; es una limitación aceptada (`research.md` R5).
- **Orden por defecto**: el listado de ingresos se ordena por fecha del ingreso descendente, con desempate por `id` descendente. No hay otro orden configurable.

## Esquema de base de datos y migración

Archivo: `backend/src/main/resources/db/migration/V38__create_incomes.sql`

```sql
-- V38__create_incomes.sql
-- Feature 032-income-management (specs/032-income-management/data-model.md).
-- Ingresos personales inmutables: sin UPDATE/DELETE desde la aplicación.
-- Sin DELETE (regla del equipo): solo CREATE TABLE.

CREATE TABLE incomes (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id         BIGINT NOT NULL,
    amount          NUMERIC(11,2) NOT NULL,
    currency        VARCHAR(3) NOT NULL,
    income_date     DATE NOT NULL,
    category        VARCHAR(20) NOT NULL,
    payment_method  VARCHAR(20) NOT NULL,
    description     VARCHAR(200) NULL,
    idempotency_key UUID NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT fk_incomes_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT ck_incomes_amount CHECK (amount >= 0.01),
    CONSTRAINT ck_incomes_currency CHECK (currency IN (
        'ARS', 'USD', 'EUR', 'AUD', 'AWG', 'BOB', 'BRL', 'CAD', 'CHF', 'CLP', 'CNY',
        'COP', 'CZK', 'DKK', 'GBP', 'HKD', 'ILS', 'INR', 'JPY', 'MXN', 'NIO', 'NOK',
        'NZD', 'PEN', 'PYG', 'RSD', 'RUB', 'SEK', 'SGD', 'TRY', 'UYU', 'ZAR')),
    CONSTRAINT ck_incomes_category CHECK (category IN
        ('SALARY','FREELANCE','SALE','REFUND','OTHER')),
    CONSTRAINT ck_incomes_payment_method CHECK (payment_method IN
        ('CASH','TRANSFER','VIRTUAL_WALLET','OTHER')),
    CONSTRAINT ck_incomes_description CHECK (description IS NULL OR char_length(description) <= 200),
    CONSTRAINT uq_incomes_user_idempotency_key UNIQUE (user_id, idempotency_key)
);

CREATE INDEX ix_incomes_user_date ON incomes (user_id, income_date DESC, id DESC);
```

El `UNIQUE (user_id, idempotency_key)` es el respaldo de concurrencia. `ddl-auto=validate`: la entidad `Income` debe coincidir con esta tabla. Detalle en [data-model.md](./data-model.md).

## Contrato de API REST

Fuente de verdad: [contracts/personal-incomes-api.md](./contracts/personal-incomes-api.md). Todas las rutas bajo `/api`, cookie `amano_auth`, cuenta verificada.

**DTOs (Java records, paquete `com.amano.backend.income`)**

```java
// Request del alta (los campos llegan como String y se validan como en RegisterExpenseRequest)
public class RegisterIncomeRequest {
    String amount;          // decimal > 0, ≤ 2 decimales, ≤ 999.999.999,99
    String currency;        // enum Currency (32)
    String date;            // yyyy-MM-dd, hasta hoy + 1 año
    String category;        // IncomeCategory
    String paymentMethod;   // IncomePaymentMethod
    String description;     // opcional, ≤ 200 puntos de código
    String deviceTimeZone;  // opcional, IANA; UTC si falta o es inválida
}

@JsonInclude(NON_NULL)
public record IncomeResponse(Long id, String amount, String currency, String date,
        String category, String paymentMethod, String description, boolean projected,
        OffsetDateTime createdAt, List<Conversion> conversions) {
    public record Conversion(String currency, String amount) {}
}

public record IncomeSearchResponse(List<IncomeResponse> incomes, int page, int size,
        long totalElements, int totalPages) {}

public record IncomeYearsResponse(List<Integer> years) {}

@JsonInclude(NON_NULL)
public record BalanceResponse(String currency, String income,
        String projectedIncome /* null si no hay cotización de su fecha ni de hoy */,
        String expenses, String net) {}
```

**Endpoints y códigos**

| Método y ruta | Éxito | Errores |
|---------------|-------|---------|
| `POST /api/incomes` (`Idempotency-Key` UUID) | `201` nuevo · `200` reintento idéntico | `400` datos/clave · `401` · `422` clave reutilizada con otro cuerpo (`properties.code = IDEMPOTENCY_KEY_REUSED`) · `429` |
| `GET /api/incomes/{id}` | `200` | `401` · `404` neutral (inexistente, ajeno, no numérico) |
| `GET /api/incomes/search?q&year&month&from&to&page&size` | `200` | `400` (`properties.fields`) · `401` |
| `GET /api/incomes/years` | `200` | `401` |
| `GET /api/balance?year&month&from&to` | `200` | `400` · `401` · `503` cotización no disponible |
| `PATCH`/`PUT`/`DELETE /api/incomes/{id}`, y cualquier otro método sobre las rutas anteriores | — | `405` (no hay mapeo) |

Una moneda fuera del catálogo es `400` con `fields.currency`. Los gastos no cambian (`/api/expenses/**` es inmutable, sin `PATCH`/`PUT`/`DELETE` de edición o borrado; `PATCH /api/expenses/{id}/status` es el cambio de estado de series recurrentes de 009 y queda fuera de esta feature).

## Diagrama de secuencia: crear un ingreso personal

```mermaid
sequenceDiagram
    actor U as Persona
    participant F as Frontend (ExpenseForm kind=INCOME)
    participant C as IncomeController
    participant S as IncomeService
    participant R as IncomeRepository
    participant DB as PostgreSQL

    U->>F: Completa monto, moneda, fecha, categoría, medio de cobro
    F->>F: Valida (monto, fecha ≤ hoy+1 año); aviso si fecha futura
    U->>F: Guardar → resumen → Confirmar
    F->>F: Genera Idempotency-Key (UUID) si no hay una pendiente
    F->>C: POST /api/incomes + cookie amano_auth + Idempotency-Key
    C->>C: Sesión → userId (401 sin sesión o sin verificar, como gastos)
    C->>C: Parsea Idempotency-Key (400 si no es UUID)
    C->>S: registerIncome(userId, key, request)
    S->>S: Valida y normaliza todos los campos (400 con fields)
    S->>R: findByUserIdAndIdempotencyKey
    alt Ya existe con el mismo cuerpo
        R-->>S: ingreso previo
        S-->>C: Registration(created=false)
        C-->>F: 200 IncomeResponse (sin duplicar)
    else Ya existe con otro cuerpo
        S-->>C: IdempotencyConflictException
        C-->>F: 422 ProblemDetail IDEMPOTENCY_KEY_REUSED
    else No existe
        S->>R: save(Income)
        R->>DB: INSERT incomes
        alt Carrera: UNIQUE (user_id, idempotency_key)
            DB-->>C: DataIntegrityViolationException
            C->>S: resolveConcurrentRegistration → 200 o 422
        else OK
            S-->>C: Registration(created=true)
            C-->>F: 201 IncomeResponse
        end
    end
    F->>F: Éxito: snackbar global y renueva la clave
    F->>F: onSaved → navigate('/activity', state.refreshBalance)
    F->>C: GET /api/balance (BalanceCard se monta o cambia refreshKey; sin caché)
    C-->>F: 200 BalanceResponse → tarjeta recalculada
```

**Flujo del balance** (`GET /api/balance`): `BalanceService` toma la moneda principal de la cuenta, agrupa ingresos efectivizados, ingresos proyectados y gastos por `(moneda, fecha)` con filtros de período, convierte cada grupo con `ExchangeRateProvider.getQuote(moneda, principal, fecha)` (sin cotización si la moneda ya es la principal), suma con `BigDecimal` a 2 decimales (`HALF_UP`) y devuelve `net = income − expenses`. Una cotización faltante de un **gasto o de un ingreso efectivizado** lanza `ExchangeRateUnavailableException` → `503`. Para los grupos de **ingresos proyectados** (fecha > hoy UTC) se usa la cotización de hoy cuando no existe la de su fecha; si tampoco existe, `projectedIncome` queda `null` (se omite del JSON) y no afecta ni a `net` ni al código de respuesta.

## Módulos y componentes afectados

### Backend (`backend/src/main/java/com/amano/backend`)

| Cambio | Ruta |
|--------|------|
| Nuevo módulo | `income/`: `Income`, `IncomeCategory`, `IncomePaymentMethod`, `RegisterIncomeRequest`, `IncomeResponse`, `IncomeSearchResponse`, `IncomeRepository`, `IncomeService`, `IncomeSearchService`, `IncomeSearchLabels`, `IncomeNotFoundException`, `IncomeController` |
| Nuevo | `balance/`: `BalanceService`, `BalanceController`, `BalanceResponse` |
| Reutilizados sin cambios | `ExpenseAmountNormalizer`, `ExpenseDescriptionNormalizer`, `ExpenseClassification.parseCatalog`, `IdempotencyConflictException`, `FieldValidationException`, `ExchangeRateProvider` |
| Modificar | `config/SecurityConfig.java`: agregar `/api/incomes/**` y `/api/balance/**` al matcher `verifiedAccount`; `common/ProblemDetailExceptionHandler.java`: handler de `IncomeNotFoundException` → `404` neutral "Ingreso no encontrado" |
| Nueva migración | `resources/db/migration/V38__create_incomes.sql` |
| `ExpenseRepository` | agregar una consulta agregada de gastos por `(moneda, fecha)` y período (aditiva; no cambia las existentes) |
| Pruebas | `IncomeServiceTest`, `IncomeSearchServiceTest`, `IncomeSchemaIT`, `RegisterIncomeIT`, `IncomeSearchIT`, `BalanceIT`, `IncomeAccessIT` (ajeno → 404, sin verificar → 401, `PATCH/PUT/DELETE` → 405), regresión de grupos (SC-006) |

### Frontend (`frontend/src`)

| Cambio | Ruta |
|--------|------|
| Nuevo cliente | `api/incomeClient.js`: `createIncome` (misma forma de retorno que `createExpense`: `{ok, income}` / `{ok:false, status, fields, detail}` / `reusedKey` con `422` / `network`), `searchIncomes`, `listIncomeYears`, `getIncome`, `getBalance`. Sin `update` ni `delete` |
| Extraer | `api/problemDetail.js` con `problemOf` y `safeFields` (hoy privados en `expenseClient.js`); `expenseClient.js` los importa |
| Catálogos | `validation/expenseCatalog.js`: `INCOME_CATEGORY_OPTIONS` (Sueldo, Freelance, Venta, Reembolso, Otros) e `INCOME_PAYMENT_METHOD_OPTIONS` (Efectivo, Transferencia, Billetera virtual, Otro) |
| Validación | `validation/expenseValidation.js`: `INCOME_DATE_TOO_FAR` y validación de fecha de ingreso (hasta hoy + 1 año) |
| Formulario compartido | `components/ExpenseForm.jsx`: prop `kind` (`'EXPENSE'` por defecto). Para `INCOME`: categorías y "Medio de cobro" de ingresos; se ocultan Tipo y Frecuencia; sin `max` en `DatePickerField`; aviso `role="status"` "Se contará en el balance a partir de esa fecha" con fecha futura; sin estimado en pesos para fecha futura; usa `createIncome`; textos "Nuevo ingreso", "El ingreso se guardó correctamente", "¿Descartar el ingreso?..."; `FIELD_NAMES` según `kind`. Conserva `Idempotency-Key` (se genera al confirmar, se mantiene ante fallos, se renueva tras éxito) y el mapeo `properties.fields` → `setError`; prop opcional `onSaved(result)` que, si se pasa, se invoca tras el snackbar de éxito (sin ella el comportamiento actual de gastos no cambia). Se implementa en cinco pasos pequeños (T029–T033) |
| Diálogo | `components/ExpenseSummaryDialog.jsx`: prop `title` en lugar de `SUMMARY_TITLE` fijo ("Confirmá el ingreso") |
| Páginas | nueva `pages/NewIncomePage.jsx` (`/incomes/new`, pasa `onSaved` que redirige a `/activity` con `state: { refreshBalance: Date.now() }`); nueva `pages/IncomeDetailPage.jsx` (`/incomes/:id`, solo lectura, sin acciones, con "Volver" a `/activity`); `hooks/usePersonalFormDefaults.js` extrae de `NewExpensePage` la carga de moneda preferida y fecha |
| Actividad | `pages/ActivityPage.jsx`: control segmentado **Gastos \| Ingresos** (`?view=incomes` en la URL junto con `q`, `year`, `month`, `page`), botón "Nuevo gasto"/"Nuevo ingreso", lista de ingresos con insignia "Proyectado", vacíos propios; extraer a `components/activity/` solo el buscador/filtros y la paginación (cuatro pasos, T055–T058; ver justificación abajo) |
| Tarjeta | nuevo `components/BalanceCard.jsx`: Ingresos, Gastos, **Balance neto** (signo y color/ícono), línea "Proyectado", carga y error con reintento; visible en ambas vistas; sin caché: cada montaje y cada cambio de `year`, `month` o `refreshKey` (`location.state.refreshBalance`) vuelve a pedir `GET /api/balance` |
| Rutas | `App.jsx`: rutas protegidas `/incomes/new` y `/incomes/:id` |
| Pruebas | `incomeClient.test.js`, `ExpenseForm.test.jsx` (modo `INCOME`), `NewIncomePage.test.jsx`, `IncomeDetailPage.test.jsx`, `BalanceCard.test.jsx`, `ActivityPage.test.jsx` (vista Ingresos), ajustes en `ExpenseSummaryDialog` |

## Orden de entrega (principio III)

1. **US1 + US5 (núcleo)**: migración, alta, `GET {id}`, seguridad, balance básico, formulario `kind=INCOME`, tarjeta.
2. **US2**: búsqueda, años, vista Ingresos, detalle de solo lectura.
3. **US4**: monedas, proyectados, aviso de fecha futura, `503` del balance.
4. **US3**: pruebas de inmutabilidad (`405`) e idempotencia de extremo a extremo.

## Project Structure

### Documentation (this feature)

```text
specs/032-income-management/
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/
│   └── personal-incomes-api.md
├── checklists/
│   └── requirements.md
└── tasks.md             # lo genera /speckit.tasks
```

### Source Code (repository root)

```text
backend/
├── src/main/java/com/amano/backend/
│   ├── income/          # nuevo módulo
│   ├── balance/         # nuevo módulo
│   ├── config/SecurityConfig.java
│   └── common/ProblemDetailExceptionHandler.java
├── src/main/resources/db/migration/V38__create_incomes.sql
└── src/test/java/com/amano/backend/{income,balance}/

frontend/
└── src/
    ├── api/{incomeClient.js,problemDetail.js}
    ├── components/{ExpenseForm.jsx,ExpenseSummaryDialog.jsx,BalanceCard.jsx}
    ├── hooks/usePersonalFormDefaults.js
    ├── pages/{ActivityPage.jsx,NewIncomePage.jsx,IncomeDetailPage.jsx}
    ├── validation/{expenseCatalog.js,expenseValidation.js}
    └── App.jsx
```

**Structure Decision**: aplicación web existente (backend Spring + frontend React); un módulo de dominio nuevo por concepto, siguiendo el layout de `expense/`.

## Complexity Tracking

Sin violaciones de la constitución que justificar.
