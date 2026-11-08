# Research: Formas de registrar un gasto (027)

**Fecha**: 2026-11-08 | **Spec**: [spec.md](./spec.md) | **Plan**: [plan.md](./plan.md)

Cada sección sigue el formato *Decisión / Motivo / Alternativas*. Lo que se afirma del código se verificó leyendo el repo en la rama `feat/modales-pagos` (commit `1d56a13`).

## Estado actual del código (verificado)

| Tema | Qué hay hoy | Dónde |
|---|---|---|
| Menú "+" | `QuickActionsButton` con tres enlaces planos; "Registrar gasto" va a `/expenses/new` | `frontend/src/components/QuickActionsButton.jsx` |
| Gasto personal | Página `NewExpensePage` + `ExpenseForm` (react-hook-form, una columna, `<input type="date">`, select de moneda con 3 opciones, select de frecuencia) | `frontend/src/pages/NewExpensePage.jsx`, `frontend/src/components/ExpenseForm.jsx` |
| Gasto de grupo | `GroupExpenseFormView` (881 líneas): columna izquierda `group-card group-expense__main` + panel `group-expense__split` con interruptor "Gasto compartido", tres métodos y filas por miembro | `frontend/src/pages/groups/GroupExpenseFormView.jsx`, `groups.scss` |
| Selector de amigos | `FriendPicker` (lista con búsqueda sin acentos, multi-selección para agregar miembros) | `frontend/src/components/groups/FriendPicker.jsx` |
| Monedas | `enum Currency { ARS, USD, EUR }` usado en ~40 clases (gastos, deudas, pagos, grupos, usuario). `CHECK (... IN ('ARS','USD','EUR'))` en `expenses`, `group_expenses`, `debts`, `payments`, `user_currencies` y `groups.primary_currency` | `backend/.../user/Currency.java`, migraciones V1, V4, V13, V14, V17 |
| BCRA | `BcraExchangeRateClient` consulta `GET /estadisticascambiarias/v1.0/Cotizaciones?fecha=` y cruza contra ARS; caché LRU en `CachingExchangeRateProvider` | `backend/.../groupexpense/exchange/` |
| Gasto de grupo (backend) | `GroupExpense` sin categoría, tipo ni medio de pago; `group_id NOT NULL`. `Debt.group_id NOT NULL` | `backend/.../groupexpense/` |
| Deuda directa | `DirectDebtPort` con implementación `NoOpDirectDebtPort` (siempre `false`); `FriendService` ya la consulta antes de eliminar una amistad | `backend/.../friendship/` |
| Catálogos | `ExpenseCategory`, `ExpenseType` (`RECURRING`, `ONE_TIME`), `PaymentMethod` del 007, con etiquetas en `frontend/src/validation/expenseCatalog.js` | `backend/.../expense/` |
| Dependencias frontend | React 19, react-hook-form, react-router. No hay librería de calendario | `frontend/package.json` |

## R1. Catálogo de monedas del BCRA

Consultas reales hechas el 2026-11-08:

- `GET https://api.bcra.gob.ar/estadisticascambiarias/v1.0/Maestros/Divisas` devuelve 44 entradas `{codigo, denominacion}` (NIO aparece dos veces).
- `GET .../Cotizaciones` (último día hábil, 2026-11-05) devuelve `tipoCotizacion` (ARS por unidad) para 39 de ellas.

El catálogo crudo **no se puede ofrecer tal cual**:

| Código(s) | Problema | Tratamiento |
|---|---|---|
| ATS, DEM, ESP, ITL | Monedas extintas (reemplazadas por el euro); sin cotización | Excluidas |
| VEB | Bolívar viejo, reemplazado | Excluido |
| REF | "Dólar referencia Com. 3500": es un tipo de cambio, no una moneda | Excluido |
| XAG, XAU | Plata y oro (onza troy); cotización 0 | Excluidos |
| XDR | Derechos especiales de giro del FMI; no es moneda de pago | Excluido |
| CNH | Yuan offshore, duplica a CNY | Excluido (queda CNY) |
| VND | Cotizado "c/1.000 unidades": `tipoCotizacion` es por cada 1000 VND | Excluido (evita un factor de escala especial) |
| NIO | Aparece dos veces en Maestros | Se deduplica |
| MXP | Código pre-1993; el ISO 4217 vigente es MXN | Se guarda `MXN` y se mapea a `MXP` al hablar con el BCRA |

**Decisión**: `Currency` sigue siendo un `enum` y se amplía con las 32 monedas resultantes (ARS, AUD, AWG, BOB, BRL, CAD, CHF, CLP, CNY, COP, CZK, DKK, EUR, GBP, HKD, ILS, INR, JPY, MXN, NIO, NOK, NZD, PEN, PYG, RSD, RUB, SEK, SGD, TRY, USD, UYU, ZAR). Cada constante sabe su código BCRA (`bcraCode`, igual al nombre salvo MXN→MXP). Un endpoint nuevo `GET /api/currencies` consulta `Maestros/Divisas` (con caché de 24 h), lo cruza con el enum y devuelve lo que ambos conocen. Si el BCRA no responde y no hay caché, responde `503` (edge case de la spec: "Ver más" muestra error y reintento).

**Motivo**: el enum está en ~40 clases y en todas las columnas `@Enumerated`/`CurrencyConversion`; reemplazarlo por un string libre obligaría a revalidar en cada punto y abre la puerta a guardar códigos que el BCRA no cotiza. Ampliar el enum es un cambio aditivo y localizado. Consultar `Maestros/Divisas` cumple el pedido de que "Ver más" use la API del BCRA, y el cruce con el enum garantiza que todo lo que se ofrece se puede guardar y convertir.

**Alternativas**:
- *Código como `String` validado contra el catálogo en vivo*: descartada; si el BCRA cae no se podría validar ni un gasto en ARS, y un gasto guardado podría quedar con una moneda que luego deja de existir en el catálogo.
- *Lista estática sin consultar al BCRA*: descartada; contradice el pedido explícito del usuario.
- *Incluir VND con factor 1000*: descartada por ahora; exige un caso especial en el cálculo de tasas por una moneda de uso marginal.

## R2. Conversión con más monedas

`BcraExchangeRateClient.computeRate` ya cruza cualquier par contra ARS usando `arsPerUnit.get(currency.name())`. **Decisión**: cambiar esa búsqueda a `currency.bcraCode()` (para MXN) y no tocar nada más. Las reglas de 022 (escala 8, `HALF_UP`, retroceso de hasta 7 días hábiles, `503` sin tasa inventada) siguen iguales.

**Motivo**: el algoritmo ya es genérico; el único supuesto atado a 3 monedas era el `CHECK` de la base.

## R3. Restricciones de la base

**Decisión**: una migración reemplaza los `CHECK` de moneda de `expenses`, `group_expenses`, `debts`, `payments` y `user_currencies` por uno con las 32 monedas. `groups.primary_currency` **no cambia** (sigue ARS/USD/EUR).

**Motivo**: la spec amplía las monedas de los gastos y de las preferencias (FR-014), no la moneda principal del grupo, que es una configuración de 010/022 fuera de alcance. Como el enum se comparte, `GroupService` debe validar explícitamente que la moneda principal sea ARS, USD o EUR (hoy lo garantiza el enum por accidente).

**Alternativas**: quitar los `CHECK` y confiar solo en el enum. Descartada: el repo mantiene la integridad también en la base (Principio VI).

## R4. Campos de 007 en el gasto de grupo

**Decisión**: tres columnas nuevas en `group_expenses`: `category`, `expense_type`, `payment_method` (`VARCHAR`, nullable, con `CHECK` sobre los mismos valores que `expenses`). El backend las exige en altas nuevas (400 si faltan) y las deja `NULL` en los gastos anteriores. Se reutilizan los enums `ExpenseCategory`, `ExpenseType` y `PaymentMethod` del paquete `expense` y sus mensajes de error del 007.

**Motivo**: nullable evita inventar valores para datos históricos (Principio VI); la obligatoriedad se aplica en el servicio, igual que en 007. El "Recurrente" de un gasto compartido es informativo (supuesto de la spec): no se agrega `frequency` ni se programa nada.

**Alternativas**: `NOT NULL` con valores por defecto (`OTHER`, `ONE_TIME`, `OTHER`). Descartada: fabricaría datos que el usuario nunca eligió.

## R5. Gasto con amigo y deuda directa

**Decisión**: reutilizar `GroupExpense`, `GroupExpenseShare` y `Debt`. `group_expenses.group_id` y `debts.group_id` pasan a ser nullable y se agrega `group_expenses.friend_user_id` (el otro participante) con un `CHECK` de exclusión: exactamente uno de `group_id` o `friend_user_id` tiene valor. Un gasto con amigo es un gasto de grupo "sin grupo":

- Participantes: siempre dos usuarios registrados (el que registra y el amigo); no hay perfiles simulados.
- Reparto, redondeo e idempotencia: los mismos de 022/024, extraídos a un método común del servicio.
- Moneda: **sin conversión**. No hay moneda de grupo, así que la deuda queda en la moneda del gasto (`rateSource = SAME_CURRENCY`, tasa 1).
- Deuda: `Debt` con `group_id NULL`, estado inicial `PENDING`.
- `DirectDebtPort` recibe una implementación real (`DebtDirectDebtAdapter`) que reemplaza a `NoOpDirectDebtPort`: hay deuda directa si existe una `Debt` con `group_id IS NULL`, estado distinto de `PAID`/`VERIFIED` y el par (deudor, acreedor) es (usuario, amigo) en cualquier orden.

Todas las consultas existentes de `DebtRepository` y `OverdueDebtJob` filtran por `groupId = :groupId`, así que las deudas directas quedan fuera de saldos, liquidaciones, vencimientos, pagos y del bloqueo de abandonar grupo **sin cambios en esas consultas**. Verlas y saldarlas queda para otra spec (FR-032).

**Motivo**: la spec pide "reutilizar el formulario de grupos"; reutilizar también el modelo evita duplicar reparto, redondeo, idempotencia y la máquina de estados de deuda (Principio I). El puerto `DirectDebtPort` se diseñó en 016 justo para esto.

**Alternativas**:
- *Tablas nuevas `direct_expenses` / `direct_debts`*: descartada; duplica entidad, reparto y estados que después la spec de "saldar deudas directas" tendría que volver a unificar.
- *Un "grupo de 2" oculto por amistad*: descartada por el usuario en la aclaración P3.

**Verificación T003 (2026-11-08)**: todas las consultas JPQL de `DebtRepository` filtran por `groupId` salvo `findByIdForUpdate` (por id). Sus usuarios: `PaymentService` (busca membresía con `debt.getGroupId()` → con `NULL` no hay membresía y rechaza; correcto, se prueba en T091), `PaymentValidationService:92` y `PaymentDecisionService:133` (comparan con `.equals` → `NullPointerException` con `NULL`; se corrige en T090). `GroupExpenseService:454` (idempotencia) tiene el mismo problema (T090). `PendingDebtService` usa `findAllById` sobre deudas ya filtradas por grupo.

**Riesgo**: cualquier consulta futura sobre `debts` o `group_expenses` que no filtre por grupo verá también los gastos con amigo. Se documenta en el data-model y se cubre con una prueba de integración que verifica que saldos y deudas pendientes del grupo no cambian al crear un gasto con amigo.

## R6. Calendario de fecha

**Decisión**: componente propio `DatePickerField` (sin dependencias nuevas): input de texto `dd/mm/aaaa` que sigue aceptando tipeo, botón con ícono de calendario a la derecha y un popover con grilla de 6×7 días, flechas de mes anterior/siguiente, foco y navegación con teclado (flechas, Enter, Escape) y `aria` de diálogo. Respeta `max` = hoy (regla de fecha de 007). Se usa en los tres formularios.

**Motivo**: el constitution prohíbe dependencias nuevas sin necesidad; un calendario mensual simple son unas 150 líneas y queda con el estilo del proyecto.

**Alternativas**: `<input type="date">` nativo (es lo que hay; su calendario depende del navegador y no se puede estilizar ni posicionar a la derecha de forma consistente); `react-day-picker` (dependencia nueva para un caso chico).

## R7. Submenú "Registrar gasto"

**Decisión**: `QuickActionsButton` pasa de lista de enlaces a menú con un ítem que abre submenú (`role="menu"`/`menuitem`, `aria-haspopup`, `aria-expanded`). El submenú se abre con hover (con un pequeño retardo de cierre para no perderlo al mover el mouse), con clic/toque y con flecha derecha; flecha izquierda o Escape lo cierra. Si no entra a la derecha, se muestra encima del menú principal. Opciones:

| Opción | Destino |
|---|---|
| Gasto personal | `/expenses/new` (sin cambios de ruta) |
| Gasto con amigo | Modal `FriendChooserDialog` → `/friends/:friendId/expenses/new` |
| Gasto grupal | Modal `GroupChooserDialog` → `/groups/:id/expenses/new` (ruta existente) |

**Motivo**: se reutilizan las rutas existentes; los selectores son modales porque son un paso intermedio corto (spec FR-021, FR-030).

## R8. Reutilizar el formulario de grupo para el gasto con amigo

**Decisión**: separar `GroupExpenseFormView` en:

- `SharedExpenseForm` (componente presentacional + lógica de reparto): recibe `participants`, `payerOptions`, `targetCurrency` (o `null` para no convertir), `onSubmit`, `isEdit`, etc.
- `GroupExpenseFormView`: carga grupo y miembros y llama a la API de grupos (comportamiento actual).
- `FriendExpenseFormView`: carga el amigo y llama a la API nueva.

**Motivo**: la vista actual mezcla carga de datos de grupo con el formulario; extraer el formulario es la forma mínima de reutilizarlo sin duplicar 800 líneas. Se justifica en el plan como refactor dentro del alcance (Principio I).

## R9. Layout del panel de reparto

**Decisión** (solo CSS en `groups.scss` + una clase):

- Altura de fila fija común para los tres métodos (`--split-row-height`), con el contenido extra de "Porcentajes" (slider + campo) en una sola línea en escritorio.
- `.group-expense__form` en grid de dos columnas con `align-items: stretch`; el panel derecho usa `min-height: 0` y su lista de filas `overflow-y: auto`, de modo que la altura la determina la columna izquierda.
- Barra de acciones (`Cancelar` / `Guardar gasto`) fuera de ambas columnas, al pie, siempre visible.
- En móvil (`< 768px`) se apilan las columnas y el panel no tiene altura máxima.

## R10. Estilo "card sobre card" del gasto personal

**Decisión**: `ExpenseForm` usa las mismas clases de superficie que el formulario de grupo (`group-card` dentro de `page`), moviendo esas clases de superficie a un archivo compartido si hace falta para no importar `groups.scss` desde el formulario personal.

## R11. Monedas en el frontend

**Decisión**:

- `CurrencySelect`: select con las monedas preferidas del usuario (de `me()`; ARS si no hay), la moneda actualmente elegida si no es preferida, un separador y "Ver más…". Elegir "Ver más…" abre `CurrencyCatalogDialog` y no cambia el valor hasta confirmar.
- `CurrencyCatalogDialog`: llama a `GET /api/currencies`, búsqueda por código o nombre (sin acentos), estados de carga, error con "Reintentar".
- Las etiquetas en español viven en `expenseCatalog.js` (Principio X). Si llega un código sin etiqueta, se muestra la `denominacion` del BCRA.
- `ProfileEditPage` / `CurrencySelector` (preferencias) suman "Ver más…" con el mismo diálogo (FR-014).
- `CreateGroupView` (moneda principal) sigue con ARS/USD/EUR.
