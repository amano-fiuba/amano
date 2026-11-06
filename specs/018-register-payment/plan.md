# Implementation Plan: Registrar un pago

**Branch**: `feat/registrar-pagos` | **Date**: 2026-11-05 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/018-register-payment/spec.md`

## Summary

La tarea "Registrar un pago" (Sprint 4, 2 SP) cierra el ciclo que abrió 017: las deudas existen y tienen estados, pero nada las salda. 018 entrega tres historias:

- **US1**: `POST /api/payments` registra un pago total o parcial de una deuda en la tabla nueva `payments` (`V14`). Copia pagador, receptor, grupo y moneda de la deuda, aplica `Debt.applyPayment` (017) bajo un bloqueo pesimista y deja que Hibernate persista `PARTIALLY_PAID` o `PAID`. En la misma transacción registra `PAYMENT_MADE` con el enganche de 014.
- **US2**: como `LedgerDebtPort` (017) ya ignora las deudas `PAID`, saldar desbloquea abandonar el grupo y quitar miembros o perfiles simulados. No hay código nuevo en `groups/`: se prueba de punta a punta.
- **US3**: botón "Registrar pago" en cada deuda del detalle del gasto, pantalla con resumen, monto precargado con el saldo, vista previa del estado resultante, snackbar y vuelta al detalle. "Ver gasto" en las entradas `PAYMENT_MADE` del historial.

Las reglas de monto y fecha, la idempotencia, los errores y los snackbars se reutilizan de 007, 010, 015 y 017. No se agregan dependencias.

## Technical Context

**Language/Version**: Java 25 (backend); JavaScript con React 19.2 (frontend)

**Primary Dependencies**: Spring Boot 4.1.1 (Web MVC, Data JPA, Security, Jackson 3), Flyway; React 19.2, react-router 8.4, react-hook-form, Vite. **Sin dependencias nuevas.**

**Storage**: PostgreSQL. Una migración: `V14__create_payments.sql` (1 tabla, 2 índices, solo `CREATE`). `ddl-auto=validate`. `debts` (V13) no cambia de esquema; sus filas las actualiza Hibernate en tiempo de ejecución.

**Testing**: JUnit 5 + Mockito (`PaymentTest`, `PaymentServiceTest`, `PaymentControllerTest`, ajustes de `GroupPermissionCheckerTest`, `ActivityEventServiceTest` y `GroupExpenseServiceTest`), Spring Boot Test + Testcontainers (`PaymentMigrationIT`, `PaymentControllerIT`, `PaymentConcurrencyIT`, `PaymentUnblocksLeaveIT`), Vitest + Testing Library (`paymentPreview`, `paymentValidation`, `paymentsClient`, `DebtPaymentSummary`, `PaymentOutcomePreview`, `RegisterPaymentView` y ajustes de `ExpenseDebtList`, `GroupHistoryView` y `App`). Umbrales: JaCoCo `LINE COVEREDRATIO ≥ 0.85` (`backend/pom.xml`) y Vitest V8 `lines`/`statements ≥ 85` (`frontend/vite.config.js`). Ninguno se baja ni se excluyen archivos nuevos.

**Target Platform**: Servidor Linux vía Docker Compose; navegador web desktop y móvil.

**Project Type**: Aplicación web monorepo (backend REST + frontend SPA).

**Performance Goals**: un pago es una transacción con 1 `SELECT … FOR UPDATE`, 1 `INSERT` en `payments`, 1 `UPDATE` en `debts` y 1 `INSERT` en `group_activity`. SC-004 (< 30 s de punta a punta para la persona).

**Constraints**: no cambiar el esquema de `debts` ni de otras tablas; sin `INSERT`/`UPDATE`/`DELETE` en la migración; mensajes de 007 y 010 sin cambios; formato de `PAYMENT_MADE` de 014 compatible hacia atrás; voseo; cobertura ≥ 85 %.

**Scale/Scope**: 1 migración; backend con 1 paquete nuevo (`payment`, 8 clases) y 8 clases modificadas; frontend con 7 archivos nuevos (más sus pruebas) y 5 modificados.

**Punto de partida**: la rama `feat/registrar-pagos` tiene que incluir 017 completa (V13, `groupexpense`, `LedgerDebtPort`, detalle del gasto). T001 lo verifica antes de tocar código.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| # | Principio | Estado | Evidencia |
|---|-----------|--------|-----------|
| I | Arquitectura y separación de responsabilidades | PASS | Paquete nuevo `payment`. Usa el repositorio de deudas y las piezas de `groups` (membresía, `GroupPermissionChecker`, `ActivityEventService`) como hace `groupexpense`. `groups/` solo suma un método al checker (D2) |
| II | Desarrollo guiado por especificaciones | PASS | `specs/018-register-payment/` completa antes del código. 014 y 017 no se editan: sus extensiones se documentan acá |
| III | Entrega incremental por historia | PASS | US1 → US2 → US3, con checkpoints. US2 y US3 dependen del contrato de US1 |
| IV | Alcance controlado | PASS | Sección "Fuera de alcance" en la spec: sin validación de pagos, anulaciones, listados ni liquidación |
| VI | Integridad de las reglas de negocio | PASS con nota | Quién puede registrar (D2), fecha mínima (D4) y `payments_require_validation` sin efecto (D7) son propuestas delegadas al responsable y quedan para revisión en el PR |
| V | Calidad, pruebas y cobertura | PASS | Unitarias, integración con Testcontainers (incluidas la migración y la concurrencia) y Vitest; umbral 85 % |
| VII | Contratos REST y compatibilidad | PASS | Endpoint nuevo (§Contrato REST). El detalle del gasto de 017 suma un campo (`canRegisterPayment`) y `PAYMENT_MADE` suma dos claves opcionales: cambios aditivos |
| VIII | Validación y manejo de errores | PASS | 400 por campo, 403/404/409/422 con `ProblemDetail`; transacción única sin estados parciales |
| IX | Seguridad y control de acceso | PASS | Pagador y receptor salen de la deuda, nunca del cliente; permiso decidido en el backend; 404 neutral sin filtrar la existencia de deudas de otros grupos; `/api/payments/**` exige cuenta verificada como `/api/groups/**` (§Seguridad) |
| X | Código mantenible y convenciones | PASS | Nombres en inglés, `*Test`/`*IT`, `*.test.js(x)` |
| XI | Entorno reproducible | PASS | Sin propiedades ni dependencias nuevas. `V14` corre en el arranque y en los IT |
| XII | Revisión por pares | PENDIENTE (posterior) | PR hacia `sprint-4` aún no abierto |
| XIII | Commits y trazabilidad | PENDIENTE (posterior) | Los commits los realiza el equipo |

**Gate resultado**: PASS. Re-check post-diseño: PASS (data-model y contrato no agregan desvíos).

## Decisiones (Phase 0)

No quedaron `NEEDS CLARIFICATION`. Las decisiones de producto están en las Clarifications de la spec; las técnicas, acá.

### D1 — Numeración `018`, una sola migración `V14`

- **Decisión**: carpeta `specs/018-register-payment`, migración `V14__create_payments.sql`.
- **Rationale**: 017 reservó `018`/`V14` para los pagos (017 research.md D1; `ESTADO_DEL_PROYECTO.md` §3). T002 vuelve a verificar que nadie los haya tomado en otra rama.

### D2 — Quién puede registrar: deudor, acreedor o ADMIN

- **Decisión**: `GroupPermissionChecker.canRegisterPayment(callerRole, callerUserId, debtorUserId, creditorUserId)` devuelve `true` si `callerRole = ADMIN`, o si `callerUserId` es el deudor (registrado) o el acreedor.
- **Rationale**: el deudor registra lo que pagó, el acreedor lo que cobró. Sin el ADMIN, las deudas de un perfil simulado cuyo acreedor dejó de usar la app quedarían imposibles de saldar y bloquearían las bajas para siempre.
- **Alternativas consideradas**: solo el deudor (deja trabados los perfiles simulados); solo el acreedor (es más seguro, pero obliga a que el acreedor esté presente para que el deudor pueda irse); una casilla nueva en V9 (requiere `ALTER` sobre `group_permissions` y una UI de ajustes, fuera de 2 SP).

### D3 — Pagador y receptor se copian de la deuda

- **Decisión**: el cuerpo trae solo `debtId`, `amount`, `date` y `deviceTimeZone`. `payer_*`, `receiver_user_id`, `group_id` y `currency` salen de la deuda; `payer_name` de `debts.debtor_name`; `receiver_name` de `ActivityEventService.displayName(acreedor)`.
- **Rationale**: un pago solo tiene sentido entre el deudor y el acreedor de esa deuda. Aceptar `payerId`/`receiverId` del cliente abre la puerta a pagos cruzados y obliga a validarlos contra la deuda de todos modos.

### D4 — Validación del monto y la fecha

- **Monto**: `ExpenseAmountNormalizer` (007) para el formato y el rango; después del bloqueo, `amount ≤ outstanding`. Mensaje: "El pago no puede superar el saldo de $ {saldo}" (saldo con el formato argentino, `$ 2.333,33`).
- **Fecha**: `ExpenseDateValidator` (007) con `deviceTimeZone`; además, `date ≥ group_expenses.expense_date` de la deuda. Mensaje: "La fecha del pago no puede ser anterior a la del gasto".
- **Rationale**: los mensajes de 007 ya los conoce la persona y el frontend ya los tiene en `validation/expenseValidation.js`. La fecha mínima evita pagos "antes" de que exista la deuda, que harían inconsistente el historial.

### D5 — Concurrencia con bloqueo pesimista

- **Decisión**: `DebtRepository.findByIdForUpdate(id)` con `@Lock(LockModeType.PESSIMISTIC_WRITE)`. El servicio la usa en la **primera** lectura de la deuda, antes de la membresía, la idempotencia y el saldo (ajuste de la implementación: ver §Flujo, paso 1).
- **Rationale**: la carrera real (deudor y acreedor registrando el mismo pago a la vez) sumaría de más. `SELECT … FOR UPDATE` serializa solo los pagos de **esa** deuda, no requiere cambios de esquema y deja el CHECK `ck_debts_paid_range` como última barrera.
- **Alternativas consideradas**: `@Version` en `Debt` (necesita `ALTER TABLE debts ADD COLUMN version` y reintentos); `UPDATE … WHERE paid_amount + :x <= amount` a mano (saltea `applyPayment` y la regla de 017 de que el dominio mueve el estado).

### D6 — `PAYMENT_MADE` con `expenseId` y `debtId`

- **Decisión**: `PaymentActivityDetails` suma `expenseId` y `debtId` al final del record. `recordPaymentMade` los pone en el mapa **solo si vienen** (no escribe `null`), así las entradas existentes y las pruebas de 014 no cambian.
- **Rationale**: el historial ya enlaza los gastos ("Ver gasto", 017 FR-024). Con `expenseId` el pago enlaza al mismo detalle, donde se ve la deuda actualizada. Es un cambio aditivo del formato de 014, documentado acá porque las specs anteriores no se editan.

### D7 — `payments_require_validation` sin efecto en 018

- **Decisión**: el pago se aplica siempre en el momento, con la casilla activada o no. La casilla se sigue guardando y mostrando en los ajustes de 010.
- **Rationale**: pedido explícito ("al registrarse el pago, el backend deberá actualizar el estado de la deuda"). Validar pagos requiere estados de pago, una bandeja para el acreedor y notificaciones.
- **Riesgo**: un ADMIN puede activar la casilla y esperar que tenga efecto. Se documenta en la spec (§Fuera de alcance, §Assumptions) y en el PR. La spec posterior puede sumar `payments.status` con un `ALTER … ADD COLUMN … DEFAULT 'CONFIRMED'` (data-model.md §Payment).

### D8 — Acceso desde el detalle del gasto, con permiso calculado por el backend

- **Decisión**: el detalle del gasto (017) informa `canRegisterPayment` por deuda y `ExpenseDebtList` muestra "Registrar pago" cuando es `true`. La pantalla de pago vive en `/groups/:id/expenses/:expenseId/debts/:debtId/payment` y lee la deuda del mismo `GET` del gasto: **no hace falta un endpoint nuevo de lectura**.
- **Rationale**: el detalle del gasto no carga el rol del usuario en el grupo; calcular el permiso en el backend evita un pedido extra y duplicar la regla D2 en el frontend. Es el único lugar donde hoy se ven deudas (el listado del grupo es de S5).

## Project Structure

### Documentation (this feature)

```text
specs/018-register-payment/
├── spec.md
├── plan.md          # incluye decisiones (D1–D8), contrato REST y validación manual
├── data-model.md
└── tasks.md
```

### Source Code (repository root)

```text
backend/src/main/resources/db/migration/
└── V14__create_payments.sql                    # A

backend/src/main/java/com/amano/backend/payment/
├── Payment.java                                # A: entidad + fábrica Payment.of(...)
├── PaymentRepository.java                      # A
├── PaymentService.java                         # A
├── PaymentController.java                      # A: POST /api/payments
├── RegisterPaymentRequest.java                 # A
├── PaymentResponse.java                        # A: pago + deuda actualizada
├── DebtNotFoundException.java                  # A: 404 neutral
└── DebtAlreadyPaidException.java               # A: 409 DEBT_ALREADY_PAID

backend/src/main/java/com/amano/backend/
├── groupexpense/DebtRepository.java            # M: findByIdForUpdate (PESSIMISTIC_WRITE)
├── groupexpense/GroupExpenseResponse.java      # M: DebtView.canRegisterPayment
├── groupexpense/GroupExpenseService.java       # M: get() calcula canRegisterPayment
├── groups/GroupPermissionChecker.java          # M: canRegisterPayment
├── groups/PaymentActivityDetails.java          # M: expenseId, debtId opcionales
├── groups/ActivityEventService.java            # M: recordPaymentMade agrega las claves si vienen
├── common/ProblemDetailExceptionHandler.java   # M: 404 deuda, 409 DEBT_ALREADY_PAID
└── config/SecurityConfig.java                  # M: /api/payments/** exige cuenta verificada

backend/src/test/java/com/amano/backend/
├── payment/PaymentTest.java, PaymentServiceTest.java, PaymentControllerTest.java       # A
├── payment/PaymentMigrationIT.java, PaymentControllerIT.java, PaymentConcurrencyIT.java # A
├── payment/PaymentUnblocksLeaveIT.java                                                 # A (US2)
└── groups/(GroupPermissionCheckerTest, ActivityEventServiceTest), groupexpense/GroupExpenseServiceTest  # M

frontend/src/
├── api/paymentsClient.js (+ .test.js)                         # A
├── pages/groups/paymentPreview.js (+ .test.js)                # A: estado resultante en centavos
├── pages/groups/paymentValidation.js (+ .test.js)             # A: monto ≤ saldo, fecha (mensajes de 007)
├── pages/groups/RegisterPaymentView.jsx (+ .test.jsx)         # A: /groups/:id/expenses/:expenseId/debts/:debtId/payment
├── components/groups/DebtPaymentSummary.jsx                   # A
├── components/groups/PaymentOutcomePreview.jsx                # A
├── components/groups/RegisterPaymentParts.test.jsx            # A: pruebas de los dos componentes
├── components/groups/ExpenseDebtList.jsx (+ .test.jsx)        # M: acción "Registrar pago"
├── pages/groups/GroupExpenseDetailView.jsx (+ .test.jsx)      # M: pasa groupId/expenseId a la lista
├── pages/groups/GroupHistoryView.jsx (+ .test.jsx)            # M: "Ver gasto" también en PAYMENT_MADE
├── App.jsx (+ App.test.jsx)                                   # M: ruta protegida nueva
└── pages/groups/groups.scss                                   # M: estilos .group-payment*
```

**Structure Decision**: aplicación web existente. El pago es un dominio nuevo con su propio paquete de backend (`payment`), igual que `groupexpense` en 017. En el frontend, la vista va en `pages/groups/` y las piezas reutilizables en `components/groups/`.

## Contrato REST

Convenciones comunes (004/007/010/017): cookie `amano_auth` (sin sesión → 401); errores `ProblemDetail` con `properties.fields` por campo; montos como texto decimal canónico con punto y dos decimales.

### `POST /api/payments`

Registra un pago de una deuda de grupo (FR-001..FR-012, FR-015).

**Cabeceras**

| Cabecera | Obligatoria | Notas |
|----------|-------------|-------|
| `Idempotency-Key` | Sí | UUID. Falta o inválida → 400 en `idempotencyKey` (mensaje de 007) |
| `Content-Type` | Sí | `application/json` |

**Cuerpo**

```json
{
  "debtId": 31,
  "amount": "1000.00",
  "date": "2026-11-05",
  "deviceTimeZone": "America/Argentina/Buenos_Aires"
}
```

| Campo | Tipo | Obligatorio | Validación (mensaje) |
|-------|------|-------------|----------------------|
| `debtId` | número | Sí | Falta → 400 en `debtId` ("Indicá la deuda a pagar"). Inexistente o de un grupo ajeno → 404 neutral |
| `amount` | texto | Sí | `ExpenseAmountNormalizer` (007). Mayor al saldo → "El pago no puede superar el saldo de $ {saldo}" |
| `date` | `YYYY-MM-DD` | Sí | `ExpenseDateValidator` (007). Anterior al gasto → "La fecha del pago no puede ser anterior a la del gasto" |
| `deviceTimeZone` | texto | No | Como en 007 |

**Respuestas**

| Código | Cuándo | Cuerpo |
|--------|--------|--------|
| `201 Created` | Pago nuevo. `Location: /api/payments/{id}` | `PaymentResponse` |
| `200 OK` | Reenvío con la misma clave y los mismos datos | El `PaymentResponse` original, con la deuda en su estado **actual** |
| `400` | Campos inválidos | `ProblemDetail` con `properties.fields` (`debtId`, `amount`, `date`, `idempotencyKey`) |
| `401` | Sin sesión | — |
| `403` | Miembro sin permiso (D2) | `detail`: "Solo quien debe, quien cobra o un administrador pueden registrar este pago" |
| `404` | Deuda inexistente o grupo ajeno | `detail`: "No existe la deuda solicitada o no sos miembro del grupo" |
| `409` | Deuda `PAID` | `detail`: "La deuda ya está pagada", `properties.code`: `DEBT_ALREADY_PAID` |
| `422` | Clave reutilizada con datos distintos | `properties.code`: `IDEMPOTENCY_KEY_REUSED` (007) |

**`PaymentResponse`**

```json
{
  "id": 54,
  "groupId": 3,
  "expenseId": 12,
  "debtId": 31,
  "amount": "1000.00",
  "currency": "ARS",
  "date": "2026-11-05",
  "payer": { "type": "REGISTERED", "userId": 15, "simulatedProfileId": null, "name": "Juan Pérez" },
  "receiver": { "userId": 7, "name": "Ana López" },
  "registeredBy": { "userId": 15, "name": "Juan Pérez" },
  "createdAt": "2026-11-05T18:40:00Z",
  "debt": {
    "id": 31,
    "debtor": { "type": "REGISTERED", "userId": 15, "simulatedProfileId": null, "name": "Juan Pérez" },
    "creditor": { "userId": 7, "name": "Ana López" },
    "amount": "3333.33",
    "paidAmount": "1000.00",
    "outstanding": "2333.33",
    "currency": "ARS",
    "status": "PARTIALLY_PAID",
    "canRegisterPayment": true
  }
}
```

`debt` reutiliza `GroupExpenseResponse.DebtView` (017) para que el frontend use el mismo formato en el detalle y en el pago.

### `GET /api/groups/{id}/expenses/{expenseId}` (017, cambio aditivo)

Cada elemento de `debts` suma `canRegisterPayment` (boolean, FR-016): `status ≠ PAID` y `canRegisterPayment(rol, sesión, deudor, acreedor)` (D2). El resto del contrato de 017 no cambia.

### `PAYMENT_MADE` en `GET /api/groups/{id}/activity` (014, cambio aditivo; filtro `?eventType=PAYMENT_MADE`)

```json
{
  "eventType": "PAYMENT_MADE",
  "actor": { "userId": 15, "name": "Juan Pérez" },
  "details": {
    "paymentId": 54, "amount": "1000.00", "currency": "ARS",
    "fromName": "Juan Pérez", "toName": "Ana López",
    "expenseId": 12, "debtId": 31
  }
}
```

`expenseId` y `debtId` son opcionales (D6).

## Seguridad

- `/api/payments/**` se agrega al `requestMatchers(...).access(verifiedAccount(userRepository))` de `config/SecurityConfig.java`, junto a `/api/users/**`, `/api/expenses/**` y `/api/groups/**`. Sin ese cambio caería en `anyRequest().authenticated()` y aceptaría cuentas sin verificar el correo (003), a diferencia del resto de los endpoints de grupo.
- El controlador mantiene el 401 defensivo de 010/017 (`currentUserId()` nulo).
- Pagador, receptor, grupo y moneda nunca se leen del cuerpo (D3).
- El 404 de una deuda ajena es idéntico al de una inexistente (paso 2 de §Flujo).

## Flujo del backend

### Registro de un pago (`POST /api/payments`, US1)

`PaymentController.register` toma el usuario de la sesión (`currentUserId()`, patrón de 010/017) y la cabecera `Idempotency-Key`, y delega en `PaymentService.register`, que es `@Transactional` y sigue este orden:

1. **Deuda (con bloqueo)**: `DebtRepository.findByIdForUpdate(debtId)` (`SELECT … FOR UPDATE`, D5). Si no existe, `DebtNotFoundException` (404 neutral). Si falta `debtId`, 400 en `debtId`. Desde acá la deuda está fija hasta el `COMMIT`. *Ajuste de la implementación (2026-11-05)*: el diseño original leía la deuda sin bloqueo y la bloqueaba en el paso 5, pero Hibernate devuelve la instancia ya cargada en el contexto de persistencia sin refrescarla, así que el saldo podía quedar viejo. Por eso la primera lectura toma el bloqueo.
2. **Membresía**: `GroupMemberRepository` confirma que quien registra es miembro activo de `debt.groupId`. Si no, el **mismo** `DebtNotFoundException` (no se distingue "no existe" de "no sos miembro").
3. **Permiso**: `GroupPermissionChecker.canRegisterPayment(role, caller, debt.debtorUserId, debt.creditorUserId)` (D2). Si es `false`, `GroupPermissionDeniedException` (403 de 010).
4. **Entrada**: `ExpenseAmountNormalizer` y `ExpenseDateValidator` (con `deviceTimeZone`) de 007, más la fecha mínima del gasto (`GroupExpenseRepository.findById(debt.expenseId).expenseDate`). Todos los errores se juntan en una `FieldValidationException` (400 con `fields`).
5. *(Paso integrado al 1: ver el ajuste de arriba.)*
6. **Idempotencia**: busca `(registered_by_user_id, idempotency_key)`. Si existe y `(debtId, amount, date)` coinciden, devuelve ese pago con 200 y la deuda actual. Si no coinciden, `IdempotencyConflictException` (422, de 007). Va después del bloqueo para que dos envíos iguales queden serializados.
7. **Estado**: si `debt.status = PAID`, `DebtAlreadyPaidException` (409). Si `amount > debt.outstanding`, 400 en `amount` (D4).
8. **Aplicación**: `debt.applyPayment(amount)` (017). La entidad queda modificada en el contexto de persistencia; Hibernate emite el `UPDATE debts SET paid_amount, status, updated_at` al hacer *flush*.
9. **Persistencia**: `PaymentRepository.save(Payment.of(debt, amount, date, caller, receiverName, key))`.
10. **Historial**: `ActivityEventService.recordPaymentMade(groupId, caller, new PaymentActivityDetails(payment.id, amount, currency, payer_name, receiver_name, expenseId, debtId))`. Es `MANDATORY`: corre en la misma transacción. Si falla, se revierte todo (FR-010).
11. **Respuesta**: `PaymentResponse` con la deuda ya actualizada (`DebtView` con `canRegisterPayment` recalculado).
12. **Carrera de idempotencia**: si igual choca el `UNIQUE` de `payments`, `PaymentController` captura `DataIntegrityViolationException` y llama a `resolveConcurrentRegistration`, que relee el ganador en una transacción nueva (patrón de 007/017).

```text
POST /api/payments ──▶ PaymentController ──▶ PaymentService.register  (@Transactional)
                                                │
          1 findByIdForUpdate ── 2 membresía ── 3 canRegisterPayment ── 4 validar (007)
                                                │
          6 idempotencia ── 7 PAID? / saldo
                                                │
          8 Debt.applyPayment ── 9 save(Payment) ── 10 recordPaymentMade (MANDATORY)
                                                │
                                         COMMIT: INSERT payments, UPDATE debts, INSERT group_activity
```

### Desbloqueo de bajas (US2)

No hay cambios de código. `GroupService.leaveGroup`, `GroupMemberService.removeMember` y `removeSimulatedProfile` siguen llamando a `LedgerDebtPort`, que cuenta solo `status <> 'PAID'` (017). En cuanto el paso 8 deja la deuda `PAID` y la transacción confirma, la persona deja de estar bloqueada. `PaymentUnblocksLeaveIT` lo prueba de punta a punta (pago parcial sigue bloqueando; pago del saldo desbloquea).

### Detalle del gasto (017, cambio aditivo)

`GroupExpenseService.get` ya resuelve la membresía y el rol de quien consulta. Por cada deuda calcula `canRegisterPayment = status ≠ PAID && checker.canRegisterPayment(role, caller, debtorUserId, creditorUserId)` y lo pasa a `DebtView`. Las pruebas de 017 que construyen `DebtView` se ajustan al campo nuevo.

## Diseño del frontend (US3)

### Rutas y navegación

| Ruta | Componente | Protección | Desde dónde se llega |
|------|------------|-----------|----------------------|
| `/groups/:id/expenses/:expenseId/debts/:debtId/payment` | `RegisterPaymentView` | `ProtectedRoute` | Botón "Registrar pago" de `ExpenseDebtList` en el detalle del gasto |
| `/groups/:id/expenses/:expenseId` | `GroupExpenseDetailView` (017, M) | ya protegida | Después de registrar (201/200), desde "Volver al gasto" y desde "Ver gasto" en `PAYMENT_MADE` |

La ruta se agrega en `App.jsx` junto a las de gastos de 017.

### Árbol de componentes

```text
GroupExpenseDetailView (M)
└── ExpenseDebtList (M)           props: debts, groupId?, expenseId?
    └── fila de deuda
        ├── DebtStatusBadge (017)
        └── <Link> Registrar pago   (solo si groupId, expenseId y debt.canRegisterPayment)

RegisterPaymentView               useParams(id, expenseId, debtId) · useAuth() · useSnackbar() · useNavigate()
├── DebtPaymentSummary            props: debt
│   ├── "{debtor.name} le paga a {creditor.name}"
│   ├── Total · Pagado · Saldo    (formatAmount de 007)
│   └── DebtStatusBadge (017)     estado actual
├── formulario (react-hook-form)
│   ├── TextField "Monto"         precargado con debt.outstanding (formato argentino)
│   └── TextField "Fecha"         precargado con hoy (useCurrentDate)
├── PaymentOutcomePreview         props: debt, amount
│   └── "Después de este pago la deuda queda:" + DebtStatusBadge (estado resultante)
└── SubmitButton "Registrar pago" · <Link> Cancelar (vuelve al gasto)
```

### Módulos sin interfaz

| Módulo | Firma | Comportamiento |
|--------|-------|----------------|
| `paymentPreview.js` | `previewPayment(debt, amountCanonical) → { status, paidAmount, outstanding } \| null` | Usa `toCents`/`fromCents` de `splitEqually.js` (017). Devuelve `null` si el monto no es válido, es ≤ 0 o supera el saldo. `status` es `PAID` si el saldo queda en 0 y `PARTIALLY_PAID` si no (misma regla que `DebtStatus.of`) |
| `paymentValidation.js` | `validatePaymentAmount(raw, outstanding) → true \| string` y `validatePaymentDate(raw, todayIso, expenseDateIso) → true \| string` | Reutiliza `parseArgentineAmount`, `parseArgentineDate` y las constantes `AMOUNT_*` y `DATE_*` de `validation/expenseValidation.js` (007). Agrega `PAYMENT_EXCEEDS_OUTSTANDING(saldo)` y `PAYMENT_BEFORE_EXPENSE`, con los mismos textos del contrato |
| `paymentsClient.js` | `registerPayment(payload, idempotencyKey)` | Convención de `groupExpensesClient` (017): nunca lanza. `{ ok: true, payment, created }` (`created` = 201), `{ ok: false, status, detail, fields, code }` o `{ ok: false, network: true }` para 5xx y red. `credentials: 'include'`. Exporta `PAYMENT_NETWORK_ERROR_MESSAGE` = "No se pudo registrar el pago. Intentá nuevamente" |

### Componentes

**`RegisterPaymentView`** (en `pages/groups/`)
- **Carga**: `getGroupExpense(id, expenseId)` (017, se reutiliza) y busca la deuda con `debtId`. Mientras carga, "Cargando…". Gasto 404 o deuda que no está en el gasto → "No existe la deuda solicitada o no sos miembro del grupo". 401 → `setUser(null)`. Error de red → `GROUP_EXPENSE_LOAD_ERROR_MESSAGE` con "Reintentar".
- **Sin formulario**: deuda `PAID` → "La deuda ya está pagada"; `!canRegisterPayment` → "No podés registrar pagos de esta deuda". Los dos con el enlace "Volver al gasto" (cubre el acceso por URL directa).
- **Estado**: `react-hook-form` con `amount` (por defecto, el saldo en formato argentino: `2.333,33`) y `date` (por defecto, hoy, como `ExpenseForm`). Aparte, `idempotencyKey`.
- **Validación en el cliente**: `validatePaymentAmount` y `validatePaymentDate` en `register(..., { validate })`. Los errores se muestran junto al campo, como en 007/017.
- **Vista previa**: `useWatch` sobre `amount` → `parseArgentineAmount` → `PaymentOutcomePreview`.
- **Envío**: genera la `Idempotency-Key` con `crypto.randomUUID()` la primera vez y la conserva en los reintentos tras un error de red; la descarta en 400, 409 y 422 (como 017).
  - 201/200: `showSnackbar('Pago registrado')` (`const showSnackbar = useSnackbar()`) y `navigate('/groups/:id/expenses/:expenseId')`. El detalle vuelve a pedir el gasto y muestra la deuda actualizada.
  - 400: `setError` por campo (`amount`, `date`); `debtId` o `idempotencyKey` van como error general.
  - 403: el `detail` del servidor arriba del formulario.
  - 404: el mensaje neutral, sin formulario.
  - 409: "La deuda ya está pagada" con "Volver al gasto".
  - 422: "Ya se registró un pago con estos datos. Volvé al gasto para ver el estado actual" y se renueva la clave.
  - 401: `setUser(null)`.
  - Red o 5xx: `PAYMENT_NETWORK_ERROR_MESSAGE`.
- **Botón**: deshabilitado mientras envía.

**`DebtPaymentSummary`** (en `components/groups/`)
- Encabezado "{debtor.name} le paga a {creditor.name}" (con "Alguien"/"alguien" si falta un nombre, como `ExpenseDebtList`).
- Tres datos: Total (`amount`), Pagado (`paidAmount`) y Saldo (`outstanding`), con `formatAmount`, y el `DebtStatusBadge` del estado actual.

**`PaymentOutcomePreview`** (en `components/groups/`)
- Con `previewPayment(debt, amount)` no nulo: "Después de este pago la deuda queda:" y un `DebtStatusBadge` con el estado y el saldo resultantes ("Pagada" o "Pago parcial · faltan $ X").
- Con `null`: "Ingresá un monto válido para ver cómo queda la deuda".
- `aria-live="polite"` para que el cambio se anuncie a lectores de pantalla.

**`ExpenseDebtList`** (017, M)
- Dos props opcionales nuevas: `groupId` y `expenseId`. Si vienen y `debt.canRegisterPayment` es `true`, la fila suma el `<Link className="group-action">` "Registrar pago" a la ruta nueva.
- Sin esas props (uso futuro en "Ver deudas pendientes", S5) la lista se comporta igual que en 017.

### Cambios en vistas existentes

- **`GroupExpenseDetailView`** (017): pasa `groupId` y `expenseId` a `ExpenseDebtList`. Como vuelve a pedir el gasto al montarse, después del pago muestra el estado nuevo sin estado compartido.
- **`GroupHistoryView`** (014/017): el enlace "Ver gasto" se muestra en `EXPENSE_CREATED` **y** en `PAYMENT_MADE` cuando `details.expenseId` existe. `activityDescriptions.js` no cambia: la frase del pago ya existe.
- **`App.jsx`**: la ruta nueva.
- **`groups.scss`**: bloque `.group-payment` (resumen en tres columnas que pasan a una en móvil, vista previa) con las variables del rediseño de #37 (`--color-borde`, `--color-texto-suave`, `--color-acento-oscuro`). Sin colores nuevos.

### Reutilización

| Pieza | De | Uso en 018 |
|-------|----|-----------|
| `useSnackbar` | 015 | "Pago registrado" |
| `parseArgentineAmount`, `parseArgentineDate`, `AMOUNT_*`, `DATE_*` | 007 | Validación del formulario |
| `formatAmount` | 007 | Montos del resumen y mensaje de tope |
| `useCurrentDate`, `deviceTimeZone` | 007 | Fecha por defecto y cuerpo del `POST` |
| `TextField`, `SubmitButton` | 001/007 | Campos y botón |
| `getGroupExpense`, `GROUP_EXPENSE_LOAD_ERROR_MESSAGE` | 017 | Carga de la deuda |
| `DebtStatusBadge`, `debtStatusLabel` | 017 | Estado actual y resultante |
| `toCents`, `fromCents` | 017 | Vista previa en centavos |

### Pruebas del frontend

| Archivo | Casos principales |
|---------|-------------------|
| `paymentPreview.test.js` | Parcial, total exacto, último centavo, monto mayor al saldo → `null`, monto inválido → `null` |
| `paymentValidation.test.js` | Mensajes de 007 (vacío, formato, decimales, cero), tope del saldo con su texto, fecha futura, fecha anterior al gasto, fecha válida |
| `paymentsClient.test.js` | Cabecera `Idempotency-Key`, cuerpo, `credentials`, 201 vs 200, 400 con `fields`, 403, 404, 409 con `code`, 422, 401, 5xx y red |
| `RegisterPaymentParts.test.jsx` | `DebtPaymentSummary` (nombres, tres montos, nombres ausentes) y `PaymentOutcomePreview` (parcial, pagada, vacío) |
| `RegisterPaymentView.test.jsx` | Carga, valores precargados, vista previa reactiva, validación local sin `fetch`, 201 con snackbar y navegación, 400 por campo, 403, 404, 409, 422, 401, red con reintento y misma clave, deuda `PAID` y sin permiso por URL directa |
| `ExpenseDebtList.test.jsx` (M) | Botón visible con `canRegisterPayment`, oculto en `PAID`, sin permiso o sin props |
| `GroupExpenseDetailView.test.jsx` (M) | El detalle pasa las props y muestra el botón |
| `GroupHistoryView.test.jsx` (M) | "Ver gasto" en `PAYMENT_MADE` con y sin `expenseId` |
| `App.test.jsx` (M) | La ruta nueva está protegida |

Los mocks siguen el patrón de 010/014/017: `vi.mock` del módulo cliente, no del `fetch` global (salvo en `paymentsClient.test.js`).

## Pruebas del backend

| Prueba | Tipo | Qué cubre |
|--------|------|-----------|
| `payment/PaymentTest` | unitaria | `Payment.of` copia pagador (registrado y simulado), receptor, grupo y moneda de la deuda |
| `payment/PaymentServiceTest` | unitaria (Mockito) | Orden de §Flujo: 404 sin deuda y sin membresía, 403 por rol, 400 por monto/fecha/tope, 409 `PAID`, idempotencia 200/422, `applyPayment` llamado, `recordPaymentMade` con las siete claves |
| `payment/PaymentControllerTest` | unitaria | 401 defensivo, clave inválida, 201 con `Location`, carrera resuelta con `resolveConcurrentRegistration` (patrón de `GroupExpenseControllerTest`) |
| `payment/PaymentMigrationIT` | Testcontainers | CHECK de pagador, `ck_payments_not_self`, monto y moneda, `UNIQUE` de idempotencia, `SET NULL` al borrar el perfil simulado |
| `payment/PaymentControllerIT` | Testcontainers | Contrato HTTP completo: 201/200/400/403/404/409/422, deuda actualizada en la base, `Σ pagos = paid_amount`, entrada `PAYMENT_MADE` en `/activity` |
| `payment/PaymentConcurrencyIT` | Testcontainers | Dos hilos pagan a la vez más que el saldo: uno 201 y otro 400/409; misma clave en paralelo: un solo pago |
| `payment/PaymentUnblocksLeaveIT` | Testcontainers | US2: parcial sigue en 403 `PENDING_DEBTS`; saldo → abandonar 200, quitar miembro y quitar perfil simulado permitidos |
| `groups/GroupPermissionCheckerTest` (M) | unitaria | `canRegisterPayment`: ADMIN, deudor, acreedor, tercero, deudor simulado (`debtorUserId` nulo) |
| `groups/ActivityEventServiceTest` (M) | unitaria | `recordPaymentMade` con y sin `expenseId`/`debtId` |
| `groupexpense/GroupExpenseServiceTest` (M) | unitaria | `canRegisterPayment` en el detalle por rol y estado |

## Validación manual

Con la cuenta **A** (Ana, ADMIN), la cuenta **B** (Juan, MEMBER), la cuenta **C** (Lucía, MEMBER) y el perfil simulado "Abuela" en el grupo "Depto". Antes: `docker compose up -d db`, backend y frontend como en 017 quickstart.md §1. Si la base local tiene una `V14` de un borrador, recrearla.

- **A. Pago parcial**: Ana registra $ 9.000 "Súper" entre Ana, Juan y Lucía (3.000 cada uno). Con B, en el detalle, "Registrar pago" en su deuda → monto 1.000,00 → la vista previa dice "Pago parcial · faltan $ 2.000,00" → guardar. Snackbar "Pago registrado"; el detalle muestra "Pago parcial · faltan $ 2.000,00".
- **B. Pago total**: B vuelve a "Registrar pago"; el monto viene con 2.000,00 → guardar. La deuda queda "Pagada" y el botón desaparece.
- **C. Historial**: "Historial de actividad" → filtro "Pagos": dos entradas "Juan Pérez registró un pago de $ … de Juan Pérez a Ana López". "Ver gasto" abre el detalle.
- **D. Desbloqueo (caso Lucía)**: con C, "Abandonar grupo" → bloqueado por saldos pendientes. Ana (acreedora) registra el pago total de la deuda de Lucía. Con C, "Abandonar grupo" → sale del grupo.
- **E. Perfil simulado**: Ana registra un gasto con "Abuela". Juan (MEMBER, no es parte) no ve "Registrar pago" en esa deuda. Ana sí; la salda. Un ADMIN quita a "Abuela" → se permite y el historial sigue mostrando "de Abuela a Ana López".
- **F. Errores**: monto mayor al saldo → error junto al monto sin enviar; fecha de mañana → error en la fecha; fecha anterior al gasto → "La fecha del pago no puede ser anterior a la del gasto". Con el backend apagado, "Registrar pago" → "No se pudo registrar el pago. Intentá nuevamente"; levantar el backend y reintentar → un solo pago.
- **G. Doble toque**: abrir el formulario en dos pestañas con el saldo completo y guardar en las dos → una registra; la otra muestra "La deuda ya está pagada".
- **H. URL directa**: entrar a la ruta de pago de una deuda `PAID` → "La deuda ya está pagada" y "Volver al gasto". Con B, entrar a la de una deuda de la que no es parte → "No podés registrar pagos de esta deuda".
- **I. Casilla de validación**: activar "Los pagos requieren validación" y registrar un pago → se aplica en el momento (D7, comportamiento esperado en 018).

## Complexity Tracking

| Elemento | Detalle | Justificación / acción |
|----------|---------|------------------------|
| Bloqueo pesimista | `SELECT … FOR UPDATE` en cada pago | Única forma de impedir pagos de más sin tocar el esquema de `debts` (D5). Afecta solo a pagos de la misma deuda |
| Extensión de contratos de 014 y 017 | `expenseId`/`debtId` en `PAYMENT_MADE`; `canRegisterPayment` en el detalle | Cambios aditivos y compatibles (D6, D8); las specs anteriores no se editan, el cambio queda documentado acá |
| Casilla `payments_require_validation` sin efecto | Se guarda pero no cambia el flujo | Pedido explícito de aplicar el pago en el momento; validación fuera de 2 SP (D7). Riesgo documentado para el PR |
| Validaciones replicadas en el frontend | Tope del saldo y fecha mínima | Evitan un viaje al servidor; el backend decide igual. Mismos textos en ambos lados |

## Resultado

- **Línea base (T003, 05/11, `feat/registrar-pagos` con 017)**: backend 335 unitarias + 234 de integración, JaCoCo 94,8 % de líneas; frontend 685 pruebas, V8 93,9 % de líneas y 93,2 % de sentencias. Sin `V14` en `db/migration` (T001).
- **Automatizada (T057/T058, 05/11)**:
  - Backend `./mvnw -B clean verify`: **375 unitarias + 257 de integración**, 0 fallos, BUILD SUCCESS, "All coverage checks have been met". **JaCoCo 95,1 %** de líneas en total; paquete `payment` 100 %. Clases modificadas: `GroupExpenseService`, `GroupExpenseResponse`, `GroupPermissionChecker`, `PaymentActivityDetails`, `ActivityEventService` y `SecurityConfig` al 100 %; `ProblemDetailExceptionHandler` 89,8 %.
  - Frontend `npm run lint && npx vitest run --coverage && npm run build`: 0 errores de lint (2 warnings previos de `RegisterForm` y `ChangePasswordPage`), **730 pruebas** en 68 archivos, **V8 94,2 % de líneas y 93,5 % de sentencias**, build OK. Archivos nuevos y modificados de US3 al 100 % de líneas.
  - Migración (T060): la búsqueda de `UPDATE|DELETE|INSERT|DROP|TRUNCATE|ALTER` en `V14__create_payments.sql` solo encuentra `ON DELETE SET NULL`.
- **Desvíos respecto del diseño (T066)**: el bloqueo pesimista se toma en la primera lectura de la deuda (§Flujo, paso 1); `PaymentActivityDetails` conserva un constructor con el formato de 014; `GroupExpenseResponse.DebtView.of` centraliza la vista de la deuda para el detalle y el pago.
- **Manual (T064/T065)**: pendiente.
