# Implementation Plan: Gestión y cálculo de deudas de grupo

**Branch**: `feat/group-debt-management` | **Date**: 2026-11-06 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/022-group-debt-management/spec.md`

## Summary

La 022 completa el núcleo del producto: a partir de los gastos compartidos de un grupo, calcular quién le debe a quién, proponer una liquidación con pocas transferencias y registrar y validar los pagos. **No parte de cero**: según research.md, el módulo `groupexpense` ya tiene gasto, reparto (`EqualSplit`), deuda (`Debt`), pago (`Payment`), claves de idempotencia y el enum `Currency`. El plan es por tanto una **extensión** de ese módulo:

1. Saldos netos por miembro derivados de las deudas pendientes (sin tabla de saldos), con la suma del grupo siempre en cero.
2. Liquidación como módulo de dominio puro con algoritmo voraz (a lo sumo n-1 transferencias), de solo lectura.
3. Máquina de estados de deuda de 6 estados (`PARTIALLY_PAID` se conserva solo para lectura de datos de la 018), con confirmación o rechazo de pagos por un administrador según la casilla "Pagos requieren validación".
4. Solo pagos completos, con bloqueo de fila para que dos pagos simultáneos no puedan saldar la misma deuda.
5. Vencimiento automático mediante una tarea programada idempotente, con umbral configurable por grupo.
6. Conversión de moneda con cotización oficial del BCRA de la fecha del gasto, detrás de un adaptador, sin inventar nunca una tasa.
7. Integración con la 020: no se puede abandonar un grupo con deudas pendientes.

Este plan **prevalece** sobre research.md, data-model.md, contracts/ y tasks.md donde se contradigan; la sección *Reconciliación de artefactos* lista cada diferencia.

## Technical Context

**Language/Version**: Java 25 (backend); JavaScript con React 19.2 (frontend)

**Primary Dependencies**: Spring Boot 4.1.1; Maven; React, react-router, Vite. **Sin dependencias nuevas**: el cliente del BCRA usa el `RestClient` de Spring (con timeouts y reintentos), la tarea programada usa `@Scheduled` y la caché es en memoria acotada (sin Caffeine ni Redis). Las pruebas de propiedades usan JUnit 5 con generadores de semilla fija (ver *Pruebas*).

**Storage**: PostgreSQL con migraciones Flyway en `backend/src/main/resources/db/migration`. Hay migraciones nuevas (ver *Migraciones*); las ya integradas no se editan.

**Testing**: JUnit 5 + Mockito, Spring Boot Test + Testcontainers, Vitest + Testing Library. Umbrales JaCoCo y Vitest ≥ 85 %; no se bajan. CI: `mvn -B verify` y `npm ci && npm run lint && npm run test:coverage && npm run build`. Las pruebas nunca llaman a la API real del BCRA: el proveedor de cotizaciones se reemplaza por un doble.

**Target Platform**: Servidor Linux vía Docker Compose; navegador web desktop y móvil.

**Project Type**: Aplicación web monorepo (backend REST + frontend SPA).

**Constraints**: mensajes de 010 sin cambios; cambios de contrato solo aditivos; voseo; montos con `BigDecimal` (escala 2, columnas `precision = 11, scale = 2`), sin punto flotante; reloj inyectable (`Clock`) para todo cálculo de fechas; `BCRA_BASE_URL` por variable de entorno (la API del BCRA no requiere credenciales).

**Scale/Scope** (estimado): backend con unas 12 clases nuevas (cálculo de saldos, planificador de liquidación, adaptador y caché de cotizaciones, tarea de vencimiento, servicios, controladores y DTOs) y 6 clases modificadas (`Debt`, `DebtStatus`, `GroupExpense`, `Payment`, `GroupExpenseService`, `GroupService.leaveGroup` de la 020), más 3 migraciones; frontend con 1 página y unos 7 componentes nuevos y 4 archivos modificados.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| # | Principio | Estado | Evidencia |
|---|-----------|--------|-----------|
| I | Arquitectura | PASS | Extiende `groupexpense`; el dominio nuevo (saldos, liquidación) es puro y sin dependencias de Spring ni JPA; sin capas nuevas |
| II | Desarrollo guiado por especificaciones | PASS con nota | Hay que reconciliar los artefactos con este plan antes de `/speckit.tasks` |
| III | Entrega incremental | PASS con nota | Orden por historia: saldos → liquidación → estados y pagos → vencimiento → moneda; tasks.md debe reordenarse (hoy casi todo está en "Foundational") |
| IV | Alcance controlado | PASS con nota | Fuera: recordatorios, Whistle, estadísticas, amistades, administración de grupos. Toca comportamiento de la 018 (solo pagos completos) y de la 020 (bloqueo por deuda); ambos cambios quedan explícitos |
| V | Calidad y cobertura | PASS | Unitarias, propiedades, IT con Testcontainers y Vitest; umbral 85 % |
| VI | Integridad de reglas | PASS con nota | Nunca se inventa una cotización; una deuda solo se salda una vez (bloqueo de fila más índice parcial único); la suma de saldos es cero. La regla de solo pagos completos cambia a la 018 y debe revisarse en el PR |
| VII | Contratos REST | PASS con nota | Campos y endpoints aditivos; los estados nuevos aparecen en respuestas existentes y el frontend se actualiza en el mismo release |
| VIII | Validación y errores | PASS | `ProblemDetail`: 400 validación, 403 sin permiso, 404 neutral, 409 transición inválida, 503 cotización no disponible |
| IX | Seguridad | PASS | El servidor decide siempre; solo miembros ven saldos; confirmar o rechazar pagos es solo para administradores; no hay credenciales del BCRA y la URL base va por variable de entorno |
| X | Código mantenible | PASS | Nombres de enum en inglés como el código existente; etiquetas en español solo en el frontend |
| XI | Entorno reproducible | PASS con nota | Propiedades nuevas (`amano.exchange.bcra.*`, programación) en `.env.example` y Docker Compose; migraciones versionadas |
| XII | Revisión por pares | PENDIENTE (posterior) | PR aún no abierto |
| XIII | Commits y trazabilidad | PENDIENTE (posterior) | Conventional Commits al implementar |

**Gate resultado**: PASS con notas. Las decisiones pendientes de abajo bloquean `/speckit.tasks`, no el plan.

## Verificaciones previas (primera tarea del feature)

research.md describe el código existente, pero no lo prueba. Antes de implementar hay que confirmar cada punto; si alguno es falso, se corrige el plan:

1. Existen `Debt`, `DebtStatus` (PENDING, PARTIALLY_PAID, PAID), `Payment` con `of()` e idempotencia, `EqualSplit` (solo partes iguales; `CUSTOM` y `PERCENTAGE` son aditivos de la 022) y `ActivityEventService`.
2. El grupo ya guarda **moneda principal** y la casilla "Pagos requieren validación" (015). Si falta la moneda principal, se agrega en migración.
3. Cómo persiste el estado de deuda (texto o enum ordinal, y si hay restricción `CHECK`) y si existen filas `PARTIALLY_PAID`.
4. Si existen gastos o deudas en una moneda distinta a la del grupo (no se pueden convertir retroactivamente sin tasa).
5. Si `@EnableScheduling` ya está activo y cómo se configura la zona horaria.
6. Qué hacen hoy la edición y el borrado de gastos y el pago parcial (018).
7. Confirmar con una consulta real a la API pública del BCRA la forma de la respuesta de Estadísticas Cambiarias v1.0 y qué campo de cotización se usa (USD y EUR contra ARS).

## Decisiones de diseño

**D1. Reutilizar antes que crear.** Las historias 1 y 2 (registrar gasto y reparto) y gran parte de la 6 (registrar pago) ya existen; las tareas deben ser extensiones puntuales (redondeo, conversión, estados), no reimplementaciones.

**D2. Dinero, reparto y redondeo.** `BigDecimal` con escala 2, comparación con `compareTo`. El reparto admite `splitMethod`: `EQUAL` (por defecto, comportamiento de la 017), `CUSTOM` (importes por participante que suman exactamente el total) y `PERCENTAGE` (porcentajes por participante que suman exactamente 100, cada uno `> 0`). En `EQUAL` y `PERCENTAGE`, cada parte se calcula en centavos hacia abajo (piso) y el **pagador absorbe el resto** (clarificación Q1): su parte es el total menos la suma de las demás, de modo que ningún participante no pagador pague de más por redondeo. La misma regla produce 100,00 entre 3 → 33,33 / 33,33 / 33,34 y 0,05 entre 3 → 0,01 / 0,01 / 0,03 con el pagador. `CUSTOM` no redondea: exige suma exacta. La conversión de moneda se aplica una sola vez al total antes de repartir (D8). Contrato: `contracts/expense-split-and-edit.md`.

**D3. Estados de deuda.** Se conservan los nombres en inglés del código: `PENDING` (Pendiente), `PARTIALLY_PAID` (Pago parcial, solo lectura), `PAYMENT_REPORTED` (Pago informado), `PAID` (Pagado), `VERIFIED` (Verificado), `OVERDUE` (Vencido). `PENDING`, `PARTIALLY_PAID` y `PAID` ya existen, por lo que no hace falta renombrar datos. `PARTIALLY_PAID` se conserva únicamente para poder leer deudas de la 018; la 022 nunca lo produce.

| Desde | Hacia | Condición |
|-------|-------|-----------|
| (alta) | `PENDING` | Toda deuda nueva |
| `PENDING`, `OVERDUE` o `PARTIALLY_PAID` | `PAID` | Se registra un pago por el saldo restante completo y el grupo **no** requiere validación |
| `PENDING`, `OVERDUE` o `PARTIALLY_PAID` | `PAYMENT_REPORTED` | Se registra un pago por el saldo restante completo y el grupo requiere validación |
| `PAYMENT_REPORTED` | `VERIFIED` | Un administrador confirma el pago |
| `PAYMENT_REPORTED` | `PENDING` | Un administrador rechaza el pago |
| `PENDING` | `OVERDUE` | La tarea programada detecta la deuda vencida (D7); `PARTIALLY_PAID` no pasa a `OVERDUE` |

`PAID` y `VERIFIED` son estados finales y equivalentes para el saldo (Q7). `PAYMENT_REPORTED` no se considera saldado hasta que un administrador lo confirme. El historial conserva el camino recorrido.

La restricción `ck_debts_status` de V13 se elimina y se recrea con los seis valores; `ck_debts_status_matches_paid` también se recrea para admitir los estados nuevos sin romper las filas `PARTIALLY_PAID` existentes.

**D4. Saldos derivados.** No hay tabla de saldos. Una deuda está **pendiente de saldar** en `PENDING`, `PAYMENT_REPORTED` y `OVERDUE`; está **saldada** en `PAID` y `VERIFIED`. Como solo se aceptan pagos completos, `paidAmount` vale 0 o el monto total, y la deuda pendiente aporta `amount − paidAmount` con el `getOutstanding()` existente. El saldo neto de un miembro es la suma de lo que le deben menos la suma de lo que debe, dentro del grupo, incluidas las personas simuladas. La suma de saldos del grupo es siempre 0. El cálculo es una función pura (`BalanceCalculator`) que recibe deudas y devuelve saldos; se evalúa en cada consulta.

**D5. Liquidación.** `SettlementPlanner` es puro y recibe los saldos netos. Mientras haya saldos distintos de cero, toma el mayor acreedor y el mayor deudor (desempate por identificador, para que el resultado sea reproducible), transfiere el menor de los dos montos y repite. Cada paso deja al menos a un miembro en cero y el último deja a dos, por lo que hay **a lo sumo n-1 transferencias** (n = miembros con saldo distinto de cero). El mínimo absoluto es un problema NP-difícil y no se busca. Invariantes: el total transferido es la suma de los saldos positivos (no la suma bruta de deudas, que es mayor cuando hay deudas cruzadas), aplicar la propuesta deja a todos en cero y el cálculo no modifica gastos ni deudas.

**D6. Pagos y concurrencia.** Se **extiende la entidad y la tabla existentes** (`com.amano.backend.payment.Payment`, tabla `payments`, V14) con `ALTER TABLE`; no se crea otra clase ni otra tabla. La 018 sigue siendo dueña del flujo de registro. Las columnas nuevas son `status` (`PENDING`, `REPORTED`, `CONFIRMED`, `REJECTED`), `decided_by_user_id`, `decided_at` y `legacy`. Los pagos ya existentes se rellenan con `status = 'CONFIRMED'` y `legacy = TRUE`; los pagos nuevos son `legacy = FALSE`. Un índice único parcial sobre `payments(debt_id)` para pagos activos (`status IN ('REPORTED', 'CONFIRMED')`) **excluye los legacy**, de modo que no choca con los pagos parciales históricos de la 018 ni con los que esa feature genere mientras siga vigente su semántica anterior.

Registrar, confirmar y rechazar pagos corren en una transacción con **bloqueo pesimista de la fila de la deuda** (`PESSIMISTIC_WRITE`). La misma clave de idempotencia devuelve el pago existente (patrón actual); una clave distinta sobre una deuda ya informada o saldada responde 409. Un pago menor al saldo restante se rechaza (400): solo se aceptan pagos completos. Al confirmar un pago `REPORTED`, la deuda pasa a `VERIFIED`; al rechazarlo, el pago queda `REJECTED` y la deuda vuelve a `PENDING` (o a `OVERDUE` en la próxima ejecución de D7 si ya estaba vencida).

**D7. Vencimiento.** El grupo tiene `debtDueDays` (entero, nulo por defecto; nulo significa que nunca vence). Una tarea `@Scheduled` diaria, con `Clock` inyectado, pasa a `OVERDUE` las deudas `PENDING` cuya fecha de gasto más `debtDueDays` ya pasó. Es idempotente (actualización masiva con condición de estado) y segura con varias instancias. Una deuda vencida **sigue siendo saldo** y se puede pagar. Un rechazo devuelve la deuda a `PENDING`, y si ya está vencida la próxima ejecución la vuelve a `OVERDUE`.

**D8. Cotizaciones.** `ExchangeRateProvider` es una interfaz de dominio, con `BcraExchangeRateClient` (infraestructura) detrás y una caché decoradora en memoria acotada, por (par, fecha). El cliente usa el `RestClient` de Spring, con timeouts y reintentos, sin dependencias nuevas. La API pública del BCRA, Estadísticas Cambiarias v1.0 (`/estadisticascambiarias/v1.0/Cotizaciones?fecha=YYYY-MM-DD` y `/Cotizaciones/{moneda}?fechadesde=...&fechahasta=...`), no requiere credenciales ni registro y devuelve la cotización de cada moneda contra el peso: USD y EUR contra ARS salen directo; USD↔EUR se calcula cruzando por ARS con `BigDecimal`, sin punto flotante. Si la fecha no tiene cotización (fin de semana o feriado), se usa la del último día hábil anterior, hasta 7 días hacia atrás; si no hay ninguna, el gasto se rechaza con 503 y **no se guarda**. Una cotización histórica no cambia, así que no hay TTL ni "tasa vieja". La conversión se aplica **una sola vez al total del gasto**; el reparto y las deudas se calculan sobre el monto convertido, en la moneda del grupo. En el gasto se guardan moneda y monto originales, monto convertido, tasa, fecha y fuente de la tasa: `rate_source = 'BCRA'` y `rate_date` con la fecha de la cotización efectivamente usada, no la del gasto. **No existe tasa por defecto ni carga manual de la tasa**: no se registra con la moneda original como alternativa. Misma moneda: tasa 1 y sin llamada externa.

**D9. Edición y borrado de gastos.** Un gasto con participantes además del pagador es inmutable: `PATCH` responde 409 "No se permite editar un gasto que ya tiene participantes" y `DELETE` responde 409 "No se puede eliminar un gasto con deudas asociadas" (o "No se permite eliminar un gasto que ya tiene participantes" en el caso defensivo sin deudas). Un gasto personal (sin participantes además del pagador) se edita con `PATCH /api/groups/{groupId}/expenses/{expenseId}` (`amount`, `currency`, `date`, `description`; el pagador y los participantes no cambian) y se elimina con `DELETE /api/groups/{groupId}/expenses/{expenseId}` con `204`, solo si no tiene deudas. Los saldos son derivados (D4), así que no se recalcula ni reescribe ninguna tabla de saldos. Los endpoints son aditivos: la 017 no cambia de forma. Quién puede editar/borrar (creador, pagador o admin) queda como decisión pendiente. Contrato: `contracts/expense-split-and-edit.md` §2–§3.

**D10. Integración con la 020.** `GroupService.leaveGroup` consulta a `BalanceQuery.hasOutstandingBalance(groupId, member)`: si el miembro figura como deudor o acreedor en una deuda pendiente de saldar, no puede abandonar (409 con motivo). El mensaje se agrega sin modificar los de 010.

**D11. Gastos pagados por otro miembro.** Cualquier miembro activo del grupo puede registrar un gasto cuyo pagador sea otro miembro cuando la casilla `members_can_generate_expenses_for_others` del grupo está activa. La casilla se conserva como permiso por grupo (no se elimina) y su validación en el servidor sigue vigente: si está en `false`, el miembro recibe 403 con el mensaje "Solo podés registrar gastos pagados por vos". Los grupos nuevos arrancan con la casilla en `true` (default en la creación del grupo y migración V20, la siguiente versión libre, que solo cambia el DEFAULT de la columna); los grupos existentes mantienen su valor. La validación de membresía también es del servidor: el pagador y todos los participantes deben ser miembros activos del grupo (los participantes también pueden ser personas simuladas del grupo). Se guardan por separado `createdByUserId` (quien registró) y `payerUserId` (quien pagó); las deudas se generan contra el pagador y el historial `EXPENSE_CREATED` muestra a ambos. El endpoint `POST /api/groups/{id}/expenses` ya acepta `payerUserId` distinto del principal autenticado y ya persiste ambos campos; falta cambiar el default de la casilla para grupos nuevos y agregar el pagador al historial.

## Decisiones pendientes (bloquean `/speckit.tasks`; resolver con `/speckit.clarify`)

1. **¿Cómo se paga una transferencia propuesta?** La liquidación puede proponer "A le paga a C" cuando A solo le debe a B y B le debe a C; esa transferencia no coincide con ninguna deuda y el pago se registra por deuda. Opciones: (a) la propuesta es solo orientativa y cada pago se registra sobre una deuda directa; (b) registrar una transferencia propuesta salda las deudas de origen mediante neteo. Sin esta decisión, la propuesta no se puede ejecutar dentro de la app.
2. **Personas simuladas.** FR-003 y SC-001 las incluyen en los saldos, pero la clarificación Q6 dice que "no tienen obligación financiera". Hay que decidir si entran en la propuesta como origen o destino (marcadas como informativas), quién registra sus pagos y si pueden figurar como pagador de un gasto (hoy el acreedor es siempre un usuario registrado).
3. **Alcance del vencimiento.** La clarificación dice umbral por grupo, pero también que solo vencen los gastos recurrentes. Este plan implementa el umbral por grupo para cualquier gasto; hay que confirmar si se restringe a recurrentes y qué mensaje ve el usuario.
4. **"Pendiente" incluye "Vencido".** Se interpreta que se puede pagar una deuda `OVERDUE`; la spec dice solo "pendiente" y sería absurdo impedirlo. Confirmar.
5. **Pagos parciales ya existentes (018).** Resuelto: `PARTIALLY_PAID` y los pagos parciales históricos se conservan para lectura; se marcan `legacy` y quedan fuera del índice único parcial. La 022 solo acepta pagos por el saldo restante completo.
6. **Cotización no disponible.** Se rechaza el gasto (D8). Confirmar que esa es la política, en vez de permitir cargar la tasa a mano.

## Project Structure

### Documentation (this feature)

```text
specs/022-group-debt-management/
├── spec.md, plan.md, research.md, data-model.md, quickstart.md, tasks.md
├── contracts/
│   ├── liquidation-proposal.md      # a corregir (ver Reconciliación)
│   ├── currency-conversion.md       # a corregir
│   ├── balances-and-debts.md        # nuevo: saldos, deudas pendientes, resumen entre grupos
│   └── payment-validation.md        # nuevo: registrar, confirmar y rechazar pagos
└── checklists/requirements.md
```

### Source Code (repository root)

```text
backend/src/main/java/com/amano/backend/groupexpense/
├── Debt.java, DebtStatus.java, GroupExpense.java   # modificados
├── SplitMethod.java                # nuevo: EQUAL, CUSTOM, PERCENTAGE (D2)
├── GroupExpenseService.java        # modificado: reparto (D2), conversión (D8), edición/borrado (D9)
├── balance/                        # nuevo, dominio puro
│   ├── MemberRef.java, MemberBalance.java
│   └── BalanceCalculator.java
├── settlement/                     # nuevo, dominio puro
│   ├── Transfer.java
│   └── SettlementPlanner.java
├── payment/                        # nuevo
│   └── PaymentValidationService.java   # registrar, confirmar, rechazar (D6)
├── exchange/                       # nuevo
│   ├── ExchangeRateProvider.java       # interfaz de dominio
│   ├── BcraExchangeRateClient.java     # infraestructura (RestClient)
│   └── CachingExchangeRateProvider.java
├── overdue/
│   └── OverdueDebtJob.java         # nuevo (D7)
└── api/                            # controladores y DTOs nuevos, según convención existente

backend/src/main/java/com/amano/backend/payment/
└── Payment.java, PaymentStatus.java   # extendidos: status, decisión, legacy (D6)

backend/src/main/java/com/amano/backend/groups/
└── Group.java, GroupService.java     # Group extendido; leaveGroup consulta saldo pendiente (D10)

backend/src/main/resources/db/migration/    # V17, V18, V19 y V21 nuevas (V20 ya integrada)
backend/src/test/java/com/amano/backend/groupexpense/   # pruebas espejo

frontend/src/
├── pages/groups/GroupDebtsView.jsx                 # nuevo: pestañas Saldos | Mis deudas | Liquidación | Pagos por validar
├── components/debts/                               # nuevo
│   ├── BalanceList.jsx, DebtList.jsx, DebtDetailModal.jsx
│   ├── RegisterPaymentModal.jsx, SettlementProposal.jsx
│   ├── PaymentValidationList.jsx, ConvertedAmount.jsx
│   └── debtStatusLabels.js         # etiquetas en español de los 5 estados
└── (modificados) formulario y detalle de gasto, selector de reparto (`EQUAL`/`CUSTOM`/`PERCENTAGE`), helpers `splitCustom.js`, configuración de grupo (debtDueDays), aviso al abandonar grupo
```

**Structure Decision**: Se mantiene el monorepo existente (backend REST + frontend SPA); sin proyectos nuevos. El dominio nuevo vive en subpaquetes de `groupexpense`. Las rutas exactas de los archivos existentes se confirman en la verificación previa.

## API (aditiva; rutas finales alineadas con los controladores existentes)

| Endpoint | Quién | Notas |
|----------|-------|-------|
| `POST /groups/{groupId}/expenses` | Miembro | Existente; admite `splitMethod` (`EQUAL` por defecto, `CUSTOM`, `PERCENTAGE`) y la respuesta suma monto convertido, tasa, fuente y `splitMethod` |
| `PATCH /groups/{groupId}/expenses/{expenseId}` | Miembro | Nuevo y aditivo; solo gastos personales (sin participantes además del pagador); 409 si tiene participantes |
| `DELETE /groups/{groupId}/expenses/{expenseId}` | Miembro | Nuevo y aditivo; solo gastos personales sin deudas; 409 si tiene deudas |
| `GET /groups/{groupId}/balances` | Miembro | Saldo neto por miembro |
| `GET /groups/{groupId}/settlement-proposal` | Miembro | Solo lectura; reemplaza el `POST` con cuerpo del contrato actual |
| `GET /groups/{groupId}/debts/pending` | Miembro | Mis deudas, con origen y detalle |
| `GET /users/me/debts` | Usuario | Resumen por grupo; el total entre grupos es informativo (Q5) |
| `POST /groups/{groupId}/debts/{debtId}/payments` | Deudor involucrado | Existente; extiende estados y validación (D6) |
| `GET /groups/{groupId}/payments/pending-validation` | Administrador | Cola de pagos informados |
| `POST /groups/{groupId}/payments/{paymentId}/confirm` y `/reject` | Administrador | 403 si no es administrador |
| Ajustes del grupo | Administrador | Campo aditivo `debtDueDays` |

## Migraciones

Son cuatro migraciones nuevas, versionadas e inmutables una vez integradas:

1. **V17 — `groups`**: `primary_currency VARCHAR(3) NOT NULL DEFAULT 'ARS'` (los grupos existentes quedan en ARS; el valor es explícito y no se infiere de otra tabla) y `debt_due_days INTEGER NULL` (NULL = nunca vence; la columna se agrega una sola vez). Se agregan los `CHECK` de moneda y de `debt_due_days >= 0`.
2. **V18 — `debts` y `payments`**: se elimina y recrea `ck_debts_status` con `PENDING`, `PARTIALLY_PAID`, `PAYMENT_REPORTED`, `PAID`, `VERIFIED`, `OVERDUE`; se recrea `ck_debts_status_matches_paid` para que `PAYMENT_REPORTED` y `VERIFIED` no violen la restricción y para conservar las filas `PARTIALLY_PAID`; se recrean los índices parciales `ix_debts_open_*` excluyendo `PAID` y `VERIFIED`. Sobre `payments` se agregan por `ALTER TABLE` las columnas `status`, `decided_by_user_id`, `decided_at` y `legacy`; se rellenan las filas existentes con `status = 'CONFIRMED'` y `legacy = TRUE`; se crea el índice único parcial `uq_payments_active_debt ON payments (debt_id) WHERE legacy = FALSE AND status IN ('REPORTED', 'CONFIRMED')`.
3. **V19 — `group_expenses`**: `converted_amount`, `exchange_rate` (escala 8), `rate_date` y `rate_source`; se rellena `converted_amount = amount`, `exchange_rate = 1`, `rate_date = expense_date` y `rate_source = 'SAME_CURRENCY'` donde la moneda del gasto coincide con la del grupo.
4. **V21 — `group_expenses`**: `split_method VARCHAR(10) NOT NULL DEFAULT 'EQUAL'` con `CHECK (split_method IN ('EQUAL', 'CUSTOM', 'PERCENTAGE'))`; los gastos existentes quedan en `EQUAL`. No se edita V20 ni ninguna migración integrada.

No se edita V13 ni V14 (ya integradas).

## Diseño del frontend

- `GroupDebtsView` muestra cuatro pestañas; "Pagos por validar" solo para administradores.
- Los estados se muestran siempre con `debtStatusLabels.js`, nunca con el valor del enum.
- `ConvertedAmount` muestra "$50 USD · $925 ARS" cuando la moneda del gasto difiere de la del grupo.
- La propuesta de liquidación indica quién transfiere, a quién y cuánto; si una parte es persona simulada se rotula como informativa (sujeto a la decisión 2).
- Diseño responsive: las pestañas colapsan a lista en móvil.

## Pruebas

- **Unitarias (puras)**: `BalanceCalculator`, `SettlementPlanner`, reparto `EQUAL`/`CUSTOM`/`PERCENTAGE` (suma exacta, piso al centavo y resto al pagador) y máquina de estados.
- **Propiedades**, con JUnit 5 y generadores de semilla fija (se imprime la semilla al fallar): suma de saldos igual a cero; total transferido igual a la suma de saldos positivos; transferencias ≤ n-1; aplicar la propuesta deja todos los saldos en cero; las partes de un reparto suman el total; la propuesta no modifica deudas.
- **Integración (Testcontainers)**: gasto → deudas → saldos; gasto `CUSTOM`/`PERCENTAGE` con shares que suman el total; edición de gasto personal y 409 con participantes; borrado de gasto personal con 204 y 409 con deudas; pago con y sin validación; confirmación y rechazo; **dos pagos concurrentes sobre la misma deuda dejan exactamente uno aplicado**; tarea de vencimiento ejecutada dos veces con el mismo resultado; abandono de grupo bloqueado por deuda; conversión con proveedor simulado y falla del proveedor.
- **Frontend (Vitest)**: pestañas, modales de pago y validación, etiquetas de estado, monto convertido, selector de reparto y validación de sumas `CUSTOM`/`PERCENTAGE`.

## Reconciliación de artefactos (corregir antes de `/speckit.tasks`)

| Artefacto | Problema | Corrección |
|-----------|----------|------------|
| research.md §4–5 | `VENCIDO` solo desde `PAGO_INFORMADO` o `PAGADO` (al revés: una deuda saldada no vence) y vencimiento por gasto recurrente | D3 y D7 |
| research.md §3 | Llama "set cover" al problema NP-difícil de minimizar transferencias | Decir solo NP-difícil |
| research.md §7 y currency-conversion.md | Tasa "memo-default", TTL de 24 h y "tasa vieja"; dos comportamientos contradictorios ante falla | D8 |
| research.md §9 | Sugiere `junit-quickcheck` (dependencia nueva) | Generadores propios, sin dependencia |
| data-model.md | `recurrent` y `vencimientoDias` en `GroupExpense` (duplica la 009 y contradice el umbral por grupo); dice que `PAGO_INFORMADO` equivale a `PAGADO` para el saldo; nombres de estado en español; falta la entidad `Payment` y los campos de `Group`; el VO de monto convertido no tiene dueño | D3, D4, D7, D8 y *Migraciones* |
| liquidation-proposal.md | Incluye deudas `PAGADO` y excluye `VENCIDO`; invariante `totalTransferred = totalOriginalDebt` (falso con deudas cruzadas); excluye personas simuladas aunque tienen saldo; ejemplo con la cuenta rehecha a mitad del texto; `includeSummary` entre grupos no aplica | D4, D5 y decisión 2 |
| tasks.md | T004 duplicada; subtareas con formato roto; T017 prueba una propiedad incorrecta; US8 y US9 dependen de sí mismas; casi no hay tareas de frontend; faltan tareas para inmutabilidad de gastos (D9), abandono de grupo (D10), confirmar y rechazar pagos, tarea de vencimiento, `debtDueDays` y migraciones; la tabla de cobertura y las métricas aparecen duplicadas; FR-010 apunta a tareas que no lo cubren | Regenerar con `/speckit.tasks` |
| spec.md | SC-003 con "95 %" sin sustento; texto de plantilla `[Describe this user journey...]` en las 9 historias; etiqueta `</feature-specification>` suelta; FR-004 dice "mínimo" y SC-002 "≤ n-1" | Limpiar; usar "reducido" en FR-004 |
| quickstart.md | Pide Java 17+ y PostgreSQL 15+ (el proyecto usa Java 25); el escenario de pago parcial y los de cotización dependen de D6 y D8 | Alinear versiones y escenarios |

## Validación manual

Ver [quickstart.md](./quickstart.md), una vez reconciliado.

## Complexity Tracking

Sin desvíos.
