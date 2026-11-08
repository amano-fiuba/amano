# Implementation Plan: Formas de registrar un gasto

**Branch**: `feat/modales-pagos` | **Date**: 2026-11-08 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/027-expense-entry-modes/spec.md`

## Summary

"Registrar gasto" en el menú "+" pasa a abrir un submenú con tres flujos: personal, con amigo y grupal. La 027 **reutiliza** lo existente (research.md):

1. **Submenú** en `QuickActionsButton` y dos modales de selección (grupo y amigo) que llevan a rutas existentes o nuevas.
2. **Componentes comunes**: calendario propio (`DatePickerField`, sin dependencias nuevas) y selector de moneda con "Ver más…" que lee el catálogo del BCRA.
3. **Más monedas**: el enum `Currency` pasa de 3 a 32 monedas (las del BCRA que se pueden cotizar y son monedas reales; R1), con un endpoint `GET /api/currencies`. La conversión de 022 ya es genérica; solo se amplían los `CHECK` de la base. La moneda principal del grupo no cambia.
4. **Gasto de grupo**: categoría, tipo y medio de pago (columnas nullable, obligatorias en altas nuevas) y layout de altura fija con panel scrolleable.
5. **Gasto con amigo**: el formulario de grupo se extrae a `SharedExpenseForm`; el backend reutiliza `GroupExpense`/`Debt` con `group_id NULL` y `friend_user_id`, y `DirectDebtPort` recibe por fin su implementación real (bloquea eliminar la amistad, 016).
6. **Gasto personal**: estilo card sobre card, Cancelar/Guardar en el pie, nuevo calendario y monedas.

Fuera de alcance (spec): personalizar frecuencias de recurrentes; ver, editar, borrar y saldar gastos o deudas con amigo.

## Technical Context

**Language/Version**: Java 25 (backend); JavaScript con React 19.2 (frontend)

**Primary Dependencies**: Spring Boot 4.1.1, Maven; React, react-router, react-hook-form, Vite, Sass. **Sin dependencias nuevas**: el calendario es propio (R6) y el catálogo usa el `RestClient` ya configurado para el BCRA.

**Storage**: PostgreSQL con Flyway (`backend/src/main/resources/db/migration`). Una migración nueva (data-model.md); las integradas no se editan.

**Testing**: JUnit 5 + Mockito, Spring Boot Test + Testcontainers; Vitest + Testing Library + user-event. Umbrales JaCoCo y Vitest ≥ 85 %. Las pruebas nunca llaman al BCRA real.

**Target Platform**: Servidor Linux vía Docker Compose; navegador web desktop y móvil.

**Project Type**: Aplicación web monorepo (backend REST + frontend SPA).

**Performance Goals**: catálogo de monedas servido desde caché salvo el primer pedido del día (lo verifica T011); el submenú y el calendario responden sin pedidos de red.

**Constraints**: voseo; cambios de contrato aditivos salvo los tres campos obligatorios del gasto de grupo (justificado abajo); montos `BigDecimal` escala 2; etiquetas en español solo en el frontend; accesible por teclado (menú, calendario, diálogos).

**Scale/Scope** (estimado): backend ~6 clases nuevas (`CurrencyController`, proveedor y caché del catálogo, `FriendExpenseController`, `DebtDirectDebtAdapter`, request de gasto con amigo) y ~8 modificadas (`Currency`, `BcraExchangeRateClient`, `GroupExpense`, `Debt`, `GroupExpenseService`, `GroupExpenseResponse`, `CreateGroupExpenseRequest`, `UserAccountService`, `GroupService`); 1 migración. Frontend ~8 componentes nuevos y ~8 archivos modificados.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| # | Principio | Estado | Evidencia |
|---|-----------|--------|-----------|
| I | Arquitectura | PASS con nota | Reutiliza `groupexpense` (gasto, partes, deudas, idempotencia, reparto) para el gasto con amigo y el puerto `DirectDebtPort` de 016. Refactor necesario: extraer `SharedExpenseForm` de `GroupExpenseFormView` (R8) y un método común de alta en `GroupExpenseService`; ambos los exige FR-031 ("reutilizar formulario de grupos") |
| II | SDD y trazabilidad | PASS | Spec con aclaraciones; cada contrato cita FR; quickstart mapea escenarios a FR/SC |
| III | Entrega incremental | PASS | Orden por historia (abajo); cada una se puede revisar sola |
| IV | Alcance controlado | PASS con nota | Sin frecuencias ni gestión de deudas directas. Se amplían las preferencias de moneda del perfil porque lo pide FR-014. Moneda principal de grupo sin cambios |
| V | Calidad y cobertura | PASS | Unitarias + IT (Testcontainers) + Vitest; dobles para el BCRA; umbral 85 % |
| VI | Reglas de negocio | PASS con nota | Sin datos inventados: campos nuevos nullable para filas viejas; sin tasa inventada; gasto con amigo sin conversión. Invariante "toda consulta de deudas filtra por grupo" documentada y probada (R5) |
| VII | Contratos REST | PASS con nota | `POST /api/groups/{id}/expenses` pasa a exigir `category`, `type`, `paymentMethod`: incompatible para clientes viejos; el único cliente es el frontend del repo y se actualiza en el mismo PR. Resto aditivo |
| VIII | Validación y errores | PASS | `ProblemDetail`: 400 validación (mensajes de 007), 404 neutral para amigo, 422 idempotencia, 503 catálogo/cotización |
| IX | Seguridad | PASS | Solo amigos pueden registrar gastos entre sí (verificado en servidor); el `GET` de gasto con amigo solo para sus dos participantes |
| X | Mantenible | PASS | Enums en inglés, etiquetas en `expenseCatalog.js`; sin dependencias nuevas |
| XI | Entorno reproducible | PASS con nota | Propiedad nueva `amano.exchange.bcra.catalog-ttl` (default 24 h) en `.env.example`/Compose si se expone; número de migración a verificar al integrar con 025/026 |
| XII | Revisión por pares | PENDIENTE (posterior) | PR a `sprint-n` |
| XIII | Commits | PENDIENTE (posterior) | Conventional Commits |

**Gate (pre y post diseño)**: PASS con notas. No hay violaciones que requieran *Complexity Tracking*.

## Verificaciones previas (primera tarea)

1. Que `V27` siga libre en `main` y en las ramas de 025/026; si no, renumerar.
2. Que `debts.group_id` y `group_expenses.group_id` tengan o no FK a `groups` (afecta solo al texto de la migración).
3. Que ningún otro `Repository` haga consultas sobre `debts` o `group_expenses` sin filtrar por grupo (`grep` de `FROM Debt`, `FROM GroupExpense`, `findAll`).
4. Que el formulario de grupo no envíe ya `category` u otros campos (evitar el `400` por propiedad desconocida al migrar en partes).
5. Cómo `CreateGroupRequest`/`GroupService` parsean `primaryCurrency` para agregar la restricción a ARS/USD/EUR.

## Decisiones de diseño

**D1. Monedas: enum ampliado + catálogo en vivo** (R1–R3, [currencies-api](./contracts/currencies-api.md)). 32 constantes con `bcraCode()`; `GET /api/currencies` cruza `Maestros/Divisas` con el enum, caché 24 h, `503` sin caché. `BcraExchangeRateClient` busca por `bcraCode()`. `CHECK` de moneda ampliados salvo `groups.primary_currency`; `Currency.GROUP_PRIMARY` restringe la moneda principal en `GroupService.java:127-132` (verificado en T004: solo el alta la setea).

**D2. Campos de 007 en gasto compartido** (R4). Columnas nullable; el servicio las exige en altas (grupo y amigo) y en `PATCH` de gasto personal de grupo las acepta. `RECURRING` es informativo.

**D3. Gasto con amigo sobre el modelo de grupo** Sin conversión de moneda por FR-034. (R5, [shared-expenses-api](./contracts/shared-expenses-api.md)). `group_id NULL` + `friend_user_id`, CHECK de exclusión. Alta en `POST /api/users/me/friends/{friendId}/expenses`, verifica amistad (`FriendshipRequestRepository`/servicio de 013), participantes = {yo, amigo}, sin conversión. `DebtDirectDebtAdapter` implementa `DirectDebtPort` y desplaza a `NoOpDirectDebtPort` (que se elimina o queda `@ConditionalOnMissingBean`). El alta se hace con el mismo núcleo que grupos: `GroupExpenseService` extrae `register(context, ...)` donde `context` es grupo o amigo (moneda destino, validador de participantes, permisos).

**D4. Formulario compartido** (R8, [ui-flows](./contracts/ui-flows.md)). `SharedExpenseForm` recibe participantes, pagadores, moneda destino (o `null`), `showSharedToggle`, `lockParticipants` y `onSubmit`. `GroupExpenseFormView` y `FriendExpenseFormView` solo cargan datos y llaman a su API. Las pruebas actuales de `GroupExpenseFormView.test.jsx` deben seguir pasando sin cambios de comportamiento (salvo los campos nuevos).

**D5. Layout del reparto** (R9). Grid de dos columnas, panel derecho con `min-height: 0` y lista con `overflow-y: auto`; altura de fila común por variable CSS; acciones al pie fuera de las columnas.

**D6. Calendario, monedas y submenú** (R6, R7, R11). Componentes propios accesibles; `DatePickerField` y `CurrencySelect` se usan en los tres formularios; `CurrencyCatalogDialog` también en el perfil.

## Orden de implementación (por historia)

| Paso | Historia | Contenido | Depende de |
|---|---|---|---|
| 0 | — | Verificaciones previas | — |
| 1 | US1 (P1) | Submenú en `QuickActionsButton`, íconos; "Gasto personal" → `/expenses/new`; "con amigo" y "grupal" abren sus diálogos (ver pasos 4 y 5) | — |
| 2a | US2 (P1) | `DatePickerField`, estilo card sobre card, Cancelar/Guardar en `ExpenseForm` | — |
| 2b | US2 (P1) | Backend de monedas (enum, migración de `CHECK`, `/api/currencies`, perfil) + `CurrencySelect`/`CurrencyCatalogDialog` en el formulario personal y el perfil | — |
| 3 | US3 (P2) | Backend: columnas y validación de categoría/tipo/medio en gasto de grupo. Frontend: extraer `SharedExpenseForm`, campos nuevos, layout fijo/scroll, `DatePickerField` y `CurrencySelect`, `GroupChooserDialog` | 2a, 2b |
| 4 | US4 (P3) | Backend: `group_id` nullable, `friend_user_id`, alta y `GET` de gasto con amigo, `DebtDirectDebtAdapter`. Frontend: `FriendChooserDialog`, ruta y `FriendExpenseFormView` | 3 |

Hasta que existan los pasos 3 y 4, las opciones "Gasto grupal" y "Gasto con amigo" del submenú pueden quedar ocultas detrás de la implementación de su paso (no se publica una opción que no funciona).

## Pruebas

**Backend**

- `CurrencyTest`: `bcraCode`/`fromBcraCode` (MXN↔MXP), `GROUP_PRIMARY`.
- `BcraCurrencyCatalogClientTest` (doble de `RestClient`): filtra extintas/REF/XAU/XAG/XDR/CNH/VND, deduplica NIO, mapea MXP; `503` sin caché; sirve caché vencida si el BCRA falla.
- `BcraExchangeRateClientTest`: par BRL→ARS y MXN→USD.
- `GroupExpenseServiceTest`/IT: los tres campos obligatorios y sus mensajes; `frequency` rechazado; gastos viejos devuelven `null`.
- `FriendExpenseIT` (Testcontainers): alta 201/200 idempotente/422; no amigo 404; participantes y pagador inválidos 400; deuda `PENDING` con `group_id NULL`; sin conversión; `DELETE` de amistad bloqueado; saldos, deudas pendientes y vencimiento de grupo no incluyen la deuda directa.
- `UserAccountServiceTest`: preferencias con monedas nuevas; mensaje "Moneda no soportada".
- `GroupServiceTest`: moneda principal fuera de ARS/USD/EUR → 400.
- Migración: IT que aplica `V27` sobre datos existentes y verifica los `CHECK`.

**Frontend**

- `QuickActionsButton.test`: hover, clic, teclado, Escape, posicionamiento en ancho chico.
- `DatePickerField.test`: navegación de meses, `max`, teclado, tipeo manual.
- `CurrencySelect.test`/`CurrencyCatalogDialog.test`: orden de opciones, "Ver más…" sin cambiar valor hasta confirmar, búsqueda sin acentos, error + reintentar.
- `ExpenseForm.test`: sin "Gasto compartido", Cancelar con confirmación.
- `SharedExpenseForm.test`: campos nuevos obligatorios; modo amigo (sin interruptor, participantes fijos, sin cotización); clase de altura fija y lista scrolleable.
- `GroupChooserDialog.test`, `FriendChooserDialog.test`: selección única, estados vacíos.
- `GroupExpenseFormView.test` existente: sigue pasando.

## Project Structure

### Documentation (this feature)

```text
specs/027-expense-entry-modes/
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/
│   ├── currencies-api.md
│   ├── shared-expenses-api.md
│   └── ui-flows.md
├── checklists/requirements.md
└── tasks.md             # /speckit.tasks
```

### Source Code (repository root)

```text
backend/src/main/java/com/amano/backend/
├── user/
│   ├── Currency.java                       # MOD: 32 monedas, bcraCode, GROUP_PRIMARY
│   └── UserAccountService.java             # MOD: mensaje de moneda
├── currency/                               # NUEVO
│   ├── CurrencyController.java             # GET /api/currencies
│   ├── CurrencyCatalogProvider.java
│   ├── BcraCurrencyCatalogClient.java      # Maestros/Divisas
│   ├── CachingCurrencyCatalogProvider.java
│   └── CurrencyCatalogUnavailableException.java
├── groupexpense/
│   ├── GroupExpense.java                   # MOD: groupId nullable, friendUserId, category, expenseType, paymentMethod
│   ├── Debt.java                           # MOD: groupId nullable
│   ├── DebtRepository.java                 # MOD: existsOpenDirectBetween
│   ├── CreateGroupExpenseRequest.java      # MOD: category, type, paymentMethod
│   ├── GroupExpenseResponse.java           # MOD: category, type, paymentMethod, friend
│   ├── GroupExpenseService.java            # MOD: núcleo común de alta
│   ├── FriendExpenseController.java        # NUEVO
│   ├── DebtDirectDebtAdapter.java          # NUEVO: implementa friendship.DirectDebtPort
│   └── exchange/BcraExchangeRateClient.java# MOD: bcraCode
├── groups/GroupService.java                # MOD: valida GROUP_PRIMARY
├── friendship/NoOpDirectDebtPort.java      # MOD/DEL: reemplazado por el adaptador
└── common/ProblemDetailExceptionHandler.java # MOD: 503 CURRENCY_CATALOG_UNAVAILABLE

backend/src/main/resources/db/migration/V27__expense_entry_modes.sql   # NUEVO

frontend/src/
├── api/currencyClient.js                   # NUEVO
├── api/groupExpensesClient.js              # MOD: campos nuevos
├── api/friendExpensesClient.js             # NUEVO
├── components/
│   ├── QuickActionsButton.jsx              # MOD: submenú
│   ├── NavIcons.jsx                        # MOD: íconos
│   ├── DatePickerField.jsx (+ .scss)       # NUEVO
│   ├── CurrencySelect.jsx                  # NUEVO
│   ├── CurrencyCatalogDialog.jsx           # NUEVO
│   ├── CurrencySelector.jsx                # MOD: "Ver más…" en perfil
│   ├── ExpenseForm.jsx                     # MOD
│   ├── GroupChooserDialog.jsx              # NUEVO
│   ├── FriendChooserDialog.jsx             # NUEVO
│   └── expenses/SharedExpenseForm.jsx      # NUEVO (extraído de GroupExpenseFormView)
├── pages/
│   ├── NewExpensePage.jsx                  # MOD
│   ├── FriendExpenseFormView.jsx           # NUEVO
│   ├── groups/GroupExpenseFormView.jsx     # MOD: delega en SharedExpenseForm
│   └── groups/groups.scss                  # MOD: layout fijo/scroll, filas
├── validation/expenseCatalog.js            # MOD: etiquetas de 32 monedas
└── App.jsx                                 # MOD: ruta /friends/:friendId/expenses/new
```

**Structure Decision**: monorepo web existente (`backend/` + `frontend/`). El catálogo de monedas va en un paquete `currency` nuevo porque no es propio de grupos; el gasto con amigo vive en `groupexpense` porque reutiliza su modelo.

## Complexity Tracking

Sin violaciones de la constitución que justificar.
