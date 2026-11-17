# Research: Ingresos personales y balance neto (032)

Todas las decisiones salen de inspeccionar el código existente; no hubo incógnitas externas.

## R1. Tabla propia vs. discriminador en `expenses`
- **Decisión**: tabla `incomes` propia.
- **Razón**: `expenses` alimenta búsqueda 031, recurrentes (009), historial y resúmenes; un discriminador obligaría a filtrar en todas esas consultas y arriesga SC-006.
- **Alternativas**: columna `entry_type` en `expenses` (rechazada por regresión); tabla genérica `transactions` (rechazada por refactor fuera de alcance).

## R2. Idempotencia
- **Hallazgo**: `expenses` guarda `idempotency_key` en la propia fila con `UNIQUE (user_id, idempotency_key)`; `ExpenseService.registerExpense` repite la respuesta si el cuerpo normalizado coincide y lanza `IdempotencyConflictException` (→ `422`, `IDEMPOTENCY_KEY_REUSED`) si difiere; `ExpenseController` resuelve la carrera tras `DataIntegrityViolationException`.
- **Decisión**: replicar exactamente ese patrón en `incomes`. Reemplaza la tabla aparte del `data-model.md` previo.
- **Alternativa**: tabla de claves separada (más tablas sin beneficio).

## R3. Inmutabilidad y `405`
- **Hallazgo**: `ProblemDetailExceptionHandler.handleMethodNotSupported` ya devuelve `405` ProblemDetail para rutas sin el método mapeado.
- **Decisión**: no mapear `PATCH`/`PUT`/`DELETE`; la `405` sale gratis. Se prueba con un IT.
- **Nota**: `PATCH /api/expenses/{id}/status` (009) es una transición de estado de series recurrentes, no edición del gasto; no cambia.

## R4. Cálculo del balance
- **Hallazgo**: `ExpenseService.conversionsOf` ya usa `ExchangeRateProvider.getQuote(from, to, date)` y omite conversiones sin cotización.
- **Decisión**: para el balance, agrupar en SQL por `(currency, date)` y convertir cada grupo; una cotización faltante falla el balance (`503`) en lugar de omitirse, porque un balance parcial sería engañoso (FR-012). Redondeo `HALF_UP` a 2 decimales al convertir cada grupo; la suma es exacta.
- **Ingresos proyectados**: su fecha es futura y el proveedor casi nunca tiene cotización para ella; se usa la cotización de hoy como fallback y, si tampoco existe, `projectedIncome` se omite sin fallar el balance.
- **Alternativas**: convertir movimiento por movimiento (N cotizaciones, más lento); usar la cotización de hoy (rechazada en clarificación).

## R5. "Hoy" y fechas futuras
- **Hallazgo**: gastos validan la fecha con `deviceTimeZone` del cuerpo y una tolerancia de un día (UTC) si falta (`ExpenseDateValidator`).
- **Decisión**: el alta de ingresos acepta `deviceTimeZone` opcional y valida `date ≤ hoy + 1 año` con la misma lógica de zona. Las lecturas (`projected`, balance) usan la fecha UTC del servidor; el desvío máximo es de unas horas alrededor de la medianoche y se documenta como limitación aceptada.
- **Alternativa**: enviar la zona en cada `GET` (más superficie en el contrato para poca ganancia).

## R6. Moneda principal
- **Hallazgo**: `User.getCurrencies()` es un conjunto (vacío = ARS) y el frontend resuelve la preferida con `preferredCurrency()` (ARS → USD → EUR → otra).
- **Decisión**: el backend aplica la misma regla; es una función pura en `BalanceService`, probada contra los mismos casos del frontend.

## R7. Frontend: "modal de creación"
- **Hallazgo**: no existe un modal de alta; el alta es la página `/expenses/new` (`NewExpensePage` → `ExpenseForm`) y el modal es `ExpenseSummaryDialog` (confirmación). `ExpenseDetailPage` ya es de solo lectura.
- **Decisión**: parametrizar `ExpenseForm` con `kind`, y crear `NewIncomePage`/`IncomeDetailPage` finas. La pestaña activa en `/activity` decide a qué página lleva "Nuevo…".
- **Alternativa**: formulario nuevo para ingresos (duplica ~400 líneas de validación, idempotencia y cotización).

## R8. Orden de resultados y búsqueda
- **Decisión**: ingresos ordenados por `income_date DESC, id DESC` (más útil para cobros que el orden de registro); la búsqueda de texto reutiliza el patrón `translate` de `ExpenseSearchService` sobre descripción y nombre de categoría en español (`IncomeSearchLabels`), sin extensión `unaccent`.
- **Pendiente menor**: si el medio de cobro también debe buscarse; por defecto no (spec: descripción y categoría).

## R9. Seguridad (hallazgo)
- `SecurityConfig` aplica `verifiedAccount` solo a `/api/users|expenses|groups|payments|currencies/**`; las rutas nuevas caerían en `anyRequest().authenticated()` (sin exigir cuenta verificada). Se agregan `/api/incomes/**` y `/api/balance/**` al matcher verificado.
