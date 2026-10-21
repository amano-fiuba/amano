# Implementation Plan: Registro de gasto personal

**Branch**: `feat/register-expense` | **Date**: 2026-10-21 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/007-register-expense/spec.md`

**Note**: Plan generado por `/speckit.plan`. Documenta el diseño técnico que satisfará la
especificación aprobada; no genera todavía las tareas de implementación (`/speckit.tasks`).

## Summary

Primera funcionalidad de gastos de la aplicación. Un usuario autenticado (sesión de 001/004)
accede a una ruta protegida `/expenses/new`, completa monto, moneda, fecha, categoría, tipo y
medio de pago (obligatorios) y una descripción opcional, y confirma. El frontend valida en
vivo, bloquea el envío mientras haya errores y muestra un estado "Guardando…"; el backend
revalida de forma independiente, normaliza y persiste un único gasto vinculado al usuario de
la sesión mediante `POST /api/expenses`. La operación es atómica y resistente a duplicados
accidentales gracias a una clave de idempotencia por operación (cabecera `Idempotency-Key` +
restricción de unicidad por usuario). El gasto es estrictamente personal: sin participantes,
divisiones, deudas, amigos ni grupos. Se reutilizan los mecanismos existentes (seguridad de
001/004, `ProblemDetailExceptionHandler`, `react-hook-form`, `TextField`, `authClient.me()`,
patrón de estados de página de 002/005); no se agregan dependencias.

## Technical Context

**Language/Version**: Java 25 (backend); JavaScript con React 19.2 (frontend).

**Primary Dependencies**: Spring Boot 4.1.1 (Web MVC, Security, Validation, Data JPA, Flyway),
Jackson 3 vía `tools.jackson` (nótese que el proyecto ya migró de `com.fasterxml`), BCrypt de
Spring Security; React 19.2, react-router 8.4, react-hook-form 7.90, Vite 8.3 (proxy `/api` →
`:8080`). **Sin dependencias nuevas.**

**Storage**: PostgreSQL con JPA (`ddl-auto=validate`) + Flyway. **Una migración nueva**:
`V4__create_expenses.sql` (tabla `expenses`, ver [data-model.md](./data-model.md)).

**Testing**: JUnit 5 + Mockito (`*Test`), Spring Boot Test + MockMvc + Testcontainers
PostgreSQL `postgres:18-alpine` (`*IT`), `SecurityConfigTest`; Vitest 5 + React Testing
Library (`*.test.jsx`, `*.test.js`); cobertura JaCoCo (`mvn -B verify`, umbral de línea 0.85
verificado en `pom.xml`) y Vitest v8 (`npm run test:coverage`, umbral 85 de líneas y
sentencias verificado en `vite.config.js`).

**Target Platform**: Servidor Linux vía Docker Compose; navegador web de escritorio y móvil
(responsive, sin app nativa).

**Project Type**: Aplicación web monorepo (backend REST + SPA frontend).

**Performance Goals**: No se fijan umbrales de rendimiento (spec, Out of Scope). No hay
objetivos de latencia ni de throughput en esta iteración.

**Constraints**: Cobertura ≥ 85 % en ambos módulos; sin dependencias nuevas; sin listado,
edición ni eliminación de gastos; sin mecanismos de autenticación nuevos; migraciones
integradas inmutables; mensajes en español; el backend es la fuente de verdad (la validación
de la interfaz es solo retroalimentación inmediata).

**Scale/Scope**: 1 endpoint nuevo (`POST /api/expenses`), 1 tabla nueva, 1 ruta protegida y 1
formulario nuevos; ~10 archivos nuevos y ~4 modificados entre backend y frontend (ver
Project Structure). El sistema operará a escala de una aplicación personal (un usuario,
decenas de gastos por mes); la columna de idempotencia y su índice único cubren el caso de
reintentos concurrentes a esa escala.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| # | Principio | Estado | Evidencia |
|---|-----------|--------|-----------|
| I | Arquitectura y separación de responsabilidades | PASS | Paquete nuevo `com.amano.backend.expense/` con la misma forma que `user/` (entidad, repositorio, DTOs, servicio, controlador); la validación/normalización vive en el servicio y en utilidades puras, no en el controlador (research.md §D9). Se reutilizan seguridad, `ProblemDetailExceptionHandler`, `FieldValidationException`, `Currency`, `TextField`, `react-hook-form` y el cliente `authClient.me()`. No se refactoriza código ajeno |
| II | Desarrollo guiado por especificaciones y trazabilidad | PASS | `spec.md` aprobado; este plan cita FR/SC por decisión y la tabla de trazabilidad de pruebas asocia cada FR/SC con su verificación; `data-model.md` y `contracts/` son la fuente técnica |
| III | Entrega incremental por historia | PASS | US1 (registro válido) es independiente y entregable; US2 (validación cliente) y US3 (identidad/sesión/duplicados) se apoyan sobre US1. Orden sugerido de implementación y dependencias en "Fases de implementación" |
| IV | Alcance controlado y fidelidad al backlog | PASS | Se respeta Out of Scope; las decisiones que la spec delegó al plan quedan en research.md con alternativas; las ambigüedades detectadas (redundancia de FR-012, interpretación estricta del formato de miles, códigos de idempotencia (replay 200 / payload distinto 422)) se documentan como supuestos explícitos, no se resuelven por inferencia silenciosa |
| V | Calidad, pruebas y cobertura | PASS | Estrategia por capas abajo; umbral 85 % verificado en `pom.xml` (JaCoCo LINE 0.85) y `vite.config.js` (lines/statements 85). Se incluyen regresiones de 001/004 por el cambio en `SecurityConfig` y en el manejador de errores |
| VI | Integridad de las reglas de negocio | PASS | Propiedad por sesión (FR-003/FR-005), gasto estrictamente personal (FR-006), catálogos cerrados validados en backend (FR-022); no se inventan invariantes financieras |
| VII | Contratos REST y compatibilidad | PASS | `contracts/expenses-api.md`; formato ProblemDetail RFC 7807 idéntico al vigente; el único cambio de contrato existente son matchers de seguridad (se amplía el ámbito verificado) y un handler nuevo, sin romper contratos de 001–006 |
| VIII | Validación y manejo de errores | PASS | Doble validación (interfaz + backend); 400 por campo; 401 coherente con 004; el manejador global no expone internos (SC-006). Se agregan dos handlers mínimos (`HttpMessageNotReadableException`, `MissingRequestHeaderException`) para que los errores de forma/malformación sean 400 y no 500 (research.md §D9) |
| IX | Seguridad y control de acceso | PASS | El creador se toma siempre del principal de la sesión; la ruta se incorpora al ámbito `verifiedAccount` (una cuenta pendiente no registra gastos); se rechazan intentos de fijar otro creador o de enviar participantes/divisiones (FR-005/FR-006) |
| X | Código mantenible y convenciones compartidas | PASS | Convenciones `*Test`/`*IT`/`*.test.jsx`, paquetes `com.amano.backend.*`, comentarios con trazabilidad FR, sin dependencias nuevas ni código de depuración. Utilidades puras reutilizables y testeables |
| XI | Entorno reproducible e integración continua | PASS | `.github/workflows/ci.yml` ejecuta `mvn -B verify` y `npm ci && npm run lint && npm run test:coverage && npm run build`; la migración nueva corre al arrancar. No se modifican umbrales ni la build |
| XII | Revisión por pares e integración controlada | PENDIENTE (posterior) | Corresponde al PR de `feat/register-expense` hacia `sprint-1`; no es un gate de planificación |
| XIII | Commits y trazabilidad | PENDIENTE (posterior) | Los commits los realiza el equipo; este plan no commitea |

**Gate resultado**: PASS. Las únicas observaciones se registran en Complexity Tracking y en
"Decisiones pendientes y supuestos".

**Re-evaluación post-diseño**: tras generar `research.md`, `data-model.md`, `contracts/` y
`quickstart.md`, el diseño no introduce nuevas violaciones ni desviaciones. Se confirman los
gates: un solo endpoint y una sola tabla; cambios en código compartido acotados y justificados
(Complexity Tracking); sin dependencias nuevas; cobertura y CI ya configurados y verificados en
el repositorio.

## Project Structure

### Documentation (this feature)

```text
specs/007-register-expense/
├── plan.md              # Este archivo
├── research.md          # Fase 0: decisiones técnicas y alternativas
├── data-model.md        # Fase 1: entidad, columnas, restricciones, reglas
├── quickstart.md        # Fase 1: guía de verificación ejecutable
├── contracts/
│   └── expenses-api.md  # Fase 1: contrato REST y errores
├── checklists/          # Ya existente (spec)
└── tasks.md             # Fase 2 (/speckit.tasks - NO se crea aquí)
```

### Source Code (repository root)

```text
backend/src/main/java/com/amano/backend/expense/
├── Expense.java                     # Entidad JPA (tabla expenses)
├── ExpenseRepository.java           # findByIdAndUserId / findByUserIdAndIdempotencyKey
├── ExpenseCategory.java             # Enum de catálogo estable (nombres en inglés)
├── ExpenseType.java                 # RECURRING | ONE_TIME
├── PaymentMethod.java               # CASH | DEBIT_CARD | ...
├── RegisterExpenseRequest.java      # DTO de entrada + @JsonAnySetter de reservados
├── ExpenseResponse.java             # DTO de salida (amount como String)
├── IdempotencyConflictException.java # clave reutilizada con payload distinto ⇒ 422
└── ExpenseService.java              # Validación/normalización + persistencia atómica
backend/src/main/java/com/amano/backend/expense/ExpenseController.java  # POST /api/expenses
backend/src/main/java/com/amano/backend/expense/ExpenseAmountNormalizer.java  # monto canónico
backend/src/main/java/com/amano/backend/expense/ExpenseDescriptionNormalizer.java # descripción
backend/src/main/java/com/amano/backend/expense/ExpenseDateValidator.java  # zona/tolerancia

backend/src/main/resources/db/migration/V4__create_expenses.sql   # NUEVA

backend/src/test/java/com/amano/backend/expense/
├── ExpenseAmountNormalizerTest.java
├── ExpenseDescriptionNormalizerTest.java
├── ExpenseDateValidatorTest.java
├── ExpenseSchemaIT.java
├── ExpenseServiceTest.java
└── RegisterExpenseIT.java

# Modificados (cambios mínimos):
backend/src/main/java/com/amano/backend/config/SecurityConfig.java          # ámbito verificado
backend/src/main/java/com/amano/backend/common/ProblemDetailExceptionHandler.java # 400 de forma/cabecera + 422 de idempotencia
backend/src/test/java/com/amano/backend/auth/SecurityConfigTest.java        # regresión + /api/expenses
backend/src/test/java/com/amano/backend/common/ProblemDetailExceptionHandlerTest.java # 400/422 + regresión

frontend/src/api/expenseClient.js               # createExpense(payload, idempotencyKey); deviceTimeZone dentro de payload
frontend/src/validation/expenseValidation.js    # parseo puro de monto, fecha, descripción
frontend/src/validation/expenseCatalog.js        # catálogos code → etiqueta (es)
frontend/src/pages/NewExpensePage.jsx            # ruta protegida /expenses/new
frontend/src/components/ExpenseForm.jsx          # formulario y estados
frontend/src/components/SelectField.jsx          # select accesible con label+error (nuevo)

frontend/src/*.test.js(x):
├── src/validation/expenseValidation.test.js
├── src/validation/expenseCatalog.test.js
├── src/api/expenseClient.test.js
├── src/components/ExpenseForm.test.jsx
└── src/pages/NewExpensePage.test.jsx

# Modificados (cambios mínimos):
frontend/src/App.jsx                             # ruta protegida /expenses/new
```

**Structure Decision**: se conserva el monorepo existente (backend Maven + frontend Vite) y se
sigue el patrón de la feature 005: paquete de dominio propio por feature, DTOs simples
(`ExpenseResponse` como record; `RegisterExpenseRequest` como clase para soportar
`@JsonAnySetter`, ver research.md §D8), validación en el servicio, migración Flyway nueva y
utilidades puras del frontend en `src/validation/`, con pruebas colocadas junto al código.

## Estrategia de pruebas y trazabilidad

Toda prueba automatizada de la feature, con su FR/SC de origen. Convención de nombres del
repositorio: `*Test` (unitarias con Mockito), `*IT` (Testcontainers + MockMvc),
`*.test.jsx`/`*.test.js` (Vitest + RTL).

### Backend

| Archivo | Alcance | Trazabilidad |
|---|---|---|
| `ExpenseAmountNormalizerTest` | Monto canónico: dos decimales, cero/negativo, más de dos decimales, fuera de rango, escala, batería SC-004 en ARS/USD/EUR, sin redondeo | FR-013, FR-014, FR-022, FR-023, SC-004 |
| `ExpenseDescriptionNormalizerTest` | Recorte Unicode (incluye NBSP), CRLF/CR→LF, tabs/saltos internos conservados, 200/201, solo espacios/tabs/saltos ⇒ ausente, conteo por code points | FR-012, FR-017, FR-022 |
| `ExpenseDateValidatorTest` | Hoy, pasada, futura, zona adelantada, zona ausente, zona inválida, tolerancia de un día acotada | FR-009, FR-016, FR-022, SC-011 |
| `ExpenseServiceTest` | Catálogos manipulados, creador/participantes/divisiones prohibidos, normalización + un solo pase de validación, sin persistir ante error, idempotencia lógica (replay por clave) | FR-005, FR-006, FR-021, FR-024, FR-025, SC-001, SC-002, SC-008 |
| `RegisterExpenseIT` | Creación válida vinculada al usuario autenticado; rechazos sin persistir; lectura exacta de montos (SC-004) desde PostgreSQL; 401 sin sesión; idempotencia (replay con mismo payload tras persistencia, misma clave con payload distinto ⇒ 422 sin segundo gasto, doble envío concurrente con la misma clave, dos gastos idénticos en operaciones distintas con claves distintas); atomicidad; mensajes sin internos (SC-006); la migración V4 aplica limpia; la ruta exige cuenta verificada | FR-001–FR-006, FR-021–FR-032, SC-001, SC-002, SC-003, SC-004, SC-006, SC-007, SC-008 |
| `SecurityConfigTest` (regresión) | `/api/expenses` exige sesión verificada; ningún matcher previo cambia | FR-001, FR-002 |

### Frontend

| Archivo | Alcance | Trazabilidad |
|---|---|---|
| `expenseValidation.test.js` | `parseArgentineAmount` (todos los ejemplos del spec: `1.234`→1234, `1,234` rechazado, `1234.56` rechazado, `10,10` preservado, combinaciones malformadas, rango, cero, negativos, 3 decimales) sin `parseFloat`; `parseArgentineDate` (dd/mm/aaaa, d/m/aaaa normalizado, 31/02 rechazado sin corrección, formato no reconocido); `normalizeDescription` (trim Unicode, CRLF/CR, 200/201, ausente); `preferredCurrency` (orden ARS→USD→EUR, vacío/inválido→ARS) | FR-011, FR-013, FR-014, FR-016, FR-017 |
| `expenseCatalog.test.js` | Los códigos de moneda/categoría/medio de pago/tipo y sus etiquetas coinciden exactamente con los del backend | FR-010, FR-011 |
| `expenseClient.test.js` | Forma de la petición (campos, `Idempotency-Key`), éxito 201/200, 400 con `properties.fields`, 401, 5xx/red ⇒ `{ ok:false, network:true }`, nunca `parseFloat` | FR-019, FR-026, FR-027, FR-029 |
| `ExpenseForm.test.jsx` | Errores por campo, bloqueo/habilitación del envío, datos conservados, estado "Guardando…", doble clic, notificación con cierre a 5 s y manual, reseteo exacto de FR-032, errores del backend por campo, falla de red sin éxito falso, sesión expirada, accesibilidad (teclado, labels, `aria-describedby`), ausencia de campos de creador/participantes/divisiones | FR-007, FR-008, FR-013–FR-020, FR-027–FR-034, SC-005, SC-006, SC-007, SC-009, SC-010 |
| `NewExpensePage.test.jsx` | Precarga de fecha (zona del dispositivo), precarga de moneda (perfil presente/ausente/vacío/inválido/no consultable con aviso), actualización al cruzar medianoche con timers simulados, redirección 401 por `ProtectedRoute` | FR-001, FR-002, FR-009, FR-011, FR-012 |
| `App.test.jsx` (regresión) | `/expenses/new` protegida: sin sesión redirige a `/login` | FR-001, FR-002 |
| `AuthContext.test.jsx`, `ProfileEditPage.test.jsx` (regresión) | Los cambios de cliente/errores no rompen 001/004/005 | Constitución V |

## Fases de implementación (orden sugerido para `/speckit.tasks`)

1. **Fundaciones**: migración `V4`; enums de catálogo; entidad + repositorio; utilidades puras
   de normalización (monto, descripción, fecha) con `*Test` (TDD).
2. **US1 — registro válido**: `ExpenseService`, `ExpenseController`, contrato, ampliación de
   `SecurityConfig`; `RegisterExpenseIT`; ruta y formulario mínimos del frontend; notificación
   de éxito y reseteo (FR-031/FR-032).
3. **US2 — validación**: reglas de frontend, bloques y conservación de datos; errores por campo
   del backend; handlers 400 de forma/cabecera.
4. **US3 — identidad, sesión y duplicados**: rechazo de creador/participantes/divisiones;
   idempotencia (cabecera + unicidad) y pruebas de concurrencia; sesión expirada; fallas de
   red; estado "Guardando…".
5. **Accesibilidad y móvil**: `FR-033`/`FR-034`, pruebas de teclado/asociación y layout.

## Complexity Tracking

> Solo se registran desviaciones que requieren justificación.

| Desviación | Por qué es necesaria | Alternativa más simple rechazada |
|---|---|---|
| Tres cambios mínimos en código compartido (`SecurityConfig`, `ProblemDetailExceptionHandler`, handler de `422` de idempotencia) | La ruta nueva debe quedar en el ámbito verificado; los errores de forma/cabecera deben ser 400 sin filtrar internos; la decisión "misma clave con payload distinto ⇒ 422" necesita una excepción propia y su handler | Duplicar una cadena de seguridad solo para gastos (fragmentaría la seguridad); dejar 500 en JSON malformado (contradice VIII/SC-006); o devolver el gasto original ante payload distinto (ocultaría la edición del usuario) |
| Cabecera `Idempotency-Key` + columna/índice único | FR-027/SC-003 exigen evitar duplicados ante timeout con persistencia previa; el bloqueo de UI no cubre el reintento del cliente | Solo deshabilitar el botón (no cubre timeout), o deduplicar por campos (impediría registrar dos gastos idénticos deliberados, contra el Assumption del spec) |
| Comparación de payload sin columna extra | Distinguir "mismo payload" (200) de "payload distinto" (422) sin agregar hash/columna, usando los campos ya persistidos | Agregar una columna `payload_hash`: más esquema y migración sin mejorar la semántica a esta escala |
| DTO de monto como `String` canónico | Evita que Jackson o `Number`/`parseFloat` introduzcan error de punto flotante (FR-023/SC-004) | `BigDecimal` serializado como número JSON: `10,10` se convierte en `10.1` en JS y pierde la escala exigida |

Sin violaciones a principios. Las XIII y XII se cumplen al commitear/abrir el PR, fuera del
alcance del plan.

## Decisiones pendientes y supuestos explícitos

Estas decisiones **no** surgen confirmadas por la spec; se adoptan como supuestos y deben
confirmarse en revisión (Constitución IV). Detalle en [research.md](./research.md).

1. **Redundancia de FR-012 (recorte de descripción)**: FR-012 cita el criterio de 001
   (`String.trim()`, solo caracteres ≤ U+0020) y a la vez exige recortar *espacios en blanco
   Unicode*. Se adopta el criterio Unicode amplio (superset) y se interpreta "mismo criterio
   que 001" como "misma intención: recortar antes de medir y guardar el texto recortado". No se
   modifica 001 (fuera de alcance); se documenta la diferencia técnica.
2. **Formato de miles estricto**: la spec da ejemplos (`1.234` = 1234) pero no define
   exhaustivamente las "combinaciones malformadas". Se adopta una regla estricta y testeable
   (grupos de 3 con un solo tipo de separador) que rechaza `1.23.456` y la mezcla de `.` y
   espacio.
3. **Replay idempotente = 200**: el primer guardado responde 201; el reintento de la misma
   operación responde 200 con la misma representación, sin crear un segundo gasto.
4. **`Idempotency-Key` obligatoria**: ausente o inválida ⇒ 400. La spec exige la protección;
   hacerla opcional la desactivaría silenciosamente.
5. **`/api/expenses` exige cuenta verificada (decisión confirmada por el equipo)**: se homologa
   a `/api/users/**`. Evidencia del repo: `SecurityConfig.verifiedAccount(...)`
   ([SecurityConfig.java:89-102](backend/src/main/java/com/amano/backend/config/SecurityConfig.java))
   resuelve el ámbito a nivel servidor contra la base —exige `email_verified_at`, sin confiar en
   la cookie (Constitución IX)— y hoy cubre `/api/users/**`
   ([SecurityConfig.java:69](backend/src/main/java/com/amano/backend/config/SecurityConfig.java)),
   dejando el resto en `authenticated()`
   ([SecurityConfig.java:70](backend/src/main/java/com/amano/backend/config/SecurityConfig.java));
   T011 extiende el matcher a `"/api/expenses/**"`. El login ya rechaza cuentas no verificadas,
   por lo que es defensa en profundidad y no restringe a ningún usuario con sesión válida.
6. **`deviceTimeZone` viaja en el cuerpo** (dato de validación no persistido), no como cabecera.
7. **Conteo de la descripción por code points** (no unidades UTF-16 ni *chars* UTF-8), igual en
   frontend y backend.
8. **Sin endpoint de lectura**: el listado está fuera de alcance; las IT verifican la
   persistencia con SQL directo (patrón de `ProfileEditIT`).
9. **Misma `Idempotency-Key` con payload distinto ⇒ `422`** (decisión cerrada, ver
   [research.md](./research.md) §D1 y [contracts/expenses-api.md](./contracts/expenses-api.md)):
   la spec exige evitar duplicados pero no define este caso; se adopta la convención del
   borrador IETF (`422`, sin persistir). El frontend renueva la clave ante ese código. La
   carrera simultánea se resuelve esperando al ganador por el `UNIQUE` y comparando el payload
   (`200`/`422`), sin usar `409`.

## Verification (quickstart)

Comandos de verificación del repositorio (detalle y escenarios en
[quickstart.md](./quickstart.md)):

```bash
# Backend
cd backend && mvn -B verify

# Frontend
cd frontend && npm ci && npm run lint && npm run test:coverage && npm run build
```
