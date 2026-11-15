# Research: Whistle — aviso manual a un deudor

**Feature**: specs/030-whistle | **Date**: 2026-11-15

Investigación sobre el código existente para resolver las decisiones técnicas de la spec 030. Las decisiones de negocio (Q1 a Q11 de la spec) siguen **a confirmar por Nahuel**. Las de este documento son técnicas y dependen de esas.

## D0 — Numeración y fuentes verificadas

- `specs/` termina en `029-finalizar-y-ver-recurrentes`, así que **`030` está libre**.
- `db/migration/` termina en `V36__group_activity_recurrence_events.sql`, así que **`V37` está libre**.
- Ninguna rama conocida localmente (`git branch -a`) tiene una spec `030` ni una migración `V37`. No se pudo hacer `git fetch` en la auditoría del 2026-11-15, así que hay que confirmar que nadie las reservó en una rama sin pushear.
- Cronograma vigente (`09 - Artefactos - Cronograma (1).pdf`): "Whistle", Social, MVP 5, responsable Nahuel, **2 SP en la semana 12 (Sprint 5)**.

## D1 — Endpoint y ubicación

**Decision**: `POST /api/groups/{groupId}/debts/{debtId}/whistle`, sin cuerpo, en un `DebtWhistleController` del subpaquete nuevo `com.amano.backend.groupexpense.whistle`.

**Rationale**: sigue la convención de rutas de deudas de grupo (`/api/groups/{groupId}/debts/pending`, `PendingDebtController`) y de pagos por deuda (`DebtPaymentController`). El caller sale del principal de la sesión, como en `PendingDebtController.currentUserId()`. El subpaquete queda junto a `reminder/`, que es el canal que se reutiliza.

**Alternatives considered**: (a) `POST /api/debts/{id}/whistle`: pierde el contexto de grupo que usan los 404 neutrales; (b) agregar el método a `PendingDebtController`: mezcla lectura con una acción que tiene efectos.

## D2 — Reutilización del correo de 025

**Decision**: inyectar `ReminderMailService` (sin cambios) y agregar `ReminderMessageBuilder.debtWhistle(groupName, creditorName, debtorName, concept, outstanding, currency, dueDate, expense)`, que devuelve un `Mail`. El destinatario se resuelve con `ReminderRecipients.emailOf(debt)`.

**Rationale**: `ReminderMailService.send` es sincrónico y propaga la excepción del SMTP, que es lo que necesita Q10 para responder 503. `ReminderMessageBuilder` ya tiene `concept()`, `money()` y `appendExpenseDetails()`. Un método nuevo los reutiliza sin duplicar código. El asunto "{acreedor} te recuerda una deuda - {grupo}" lo distingue del de 025 ("Recordatorio de deuda - {grupo}").

**Mensaje personalizado (Q5)**: `Group.getReminderMessage()` no se usa, porque es la plantilla de los administradores para los recordatorios automáticos (025).

**Alternatives considered**: (a) un `WhistleMailService` propio: duplica el armado del `MimeMessage`; (b) reutilizar `debtReminder(...)` tal cual: el asunto y el texto serían idénticos al aviso automático, lo que contradice la propuesta.

## D3 — Límite de 24 h y concurrencia (patrón de reclamo de 025)

**Decision**:

1. **Transacción 1:** bloquear la fila de membresía del acreedor (`GroupMemberRepository.findForUpdate(groupId, creditorId)` con `@Lock(PESSIMISTIC_WRITE)`, el mismo mecanismo que `DebtRepository`); revalidar las reglas (D4); contar los intentos `SENDING` o `SENT` del par (grupo, acreedor, deudor) con `created_at > now − 24h`. Si hay alguno, responder **429** con `retryAfterSeconds = ceil(último.created_at + 24h − now)`. Si no, insertar una fila `SENDING` y confirmar la transacción.
2. **Fuera de la transacción:** `ReminderMailService.send(...)`.
3. **Transacción 2:** si el envío salió bien, marcar la fila `SENT` (`sent_at = now`) y registrar `DEBT_WHISTLED`. Si falló, marcarla `FAILED` y responder **503**.

**Rationale**:

- El bloqueo sobre la membresía del acreedor serializa sus avisos dentro del grupo. Dos confirmaciones simultáneas no pueden ver las dos "0 intentos" (FR-010, SC-003).
- No se puede bloquear la fila del deudor, porque un perfil simulado no tiene fila en `group_members`, y bloquear una sola deuda no alcanza: el límite es por par.
- No se envía correo dentro de una transacción (mismo criterio que `ReminderDispatcher`).
- `FAILED` no cuenta para el límite (FR-008).

**Caso borde**: si el proceso muere entre (1) y (3), la fila queda `SENDING` y bloquea el par durante 24 h. Es conservador frente al spam y aceptable. No se agrega un job de limpieza.

**Alternatives considered**: (a) `pg_advisory_xact_lock(hash)`: SQL nativo, menos legible y sin precedente en el repo; (b) un índice único por ventana: PostgreSQL no admite unicidad por rango de tiempo sin una columna de "día", y la ventana es móvil, no por día calendario; (c) enviar el correo dentro de la transacción: retiene el bloqueo durante el SMTP y deja un éxito falso si el commit falla después del envío.

## D4 — Reglas de elegibilidad y orden de validación

**Decision**: el servicio valida en este orden. La primera regla que falla define la respuesta.

1. Sesión → **401** (en el controller).
2. El grupo existe y el caller es miembro activo → si no, **404** neutral (`GroupNotFoundException`, mensaje de `PendingDebtService.NOT_MEMBER`).
3. La deuda existe y `debt.groupId == groupId` → si no, **404** neutral.
4. `debt.creditorUserId == callerId` → si no, **403** "Solo quien recibe el pago puede avisar esta deuda".
5. `debt.status.isPayable()` (`PENDING`, `PARTIALLY_PAID`, `OVERDUE`) → si no, **409** `DEBT_NOT_WHISTLEABLE` "La deuda ya no tiene saldo pendiente" o, para `PAYMENT_REPORTED`, "La deuda tiene un pago esperando validación".
6. `debt.debtorType == SIMULATED` → **422** `SIMULATED_DEBTOR` "Los perfiles simulados no reciben avisos" (**sujeto a Q3**).
7. El deudor registrado es miembro activo del grupo → si no, **409** `DEBTOR_NOT_MEMBER`.
8. Límite (D3) → **429** `WHISTLE_RATE_LIMITED`.
9. Envío → **503** `WHISTLE_DELIVERY_FAILED` si el SMTP falla o no hay correo.

**Rationale**: los 404 van antes de cualquier regla para no revelar si existen deudas de otros grupos (mismo criterio que 022). El 403 va antes del estado, para que alguien que no es acreedor no pueda sondear estados. `isPayable()` ya existe en `DebtStatus` y coincide con Q2.

## D5 — Códigos HTTP y formato de error

**Decision**: excepciones propias en `whistle/` y handlers en `ProblemDetailExceptionHandler`, con `properties.code`. El 429 agrega la cabecera `Retry-After` y `properties.retryAfterSeconds`.

**Rationale**: el proyecto ya usa `HttpStatus.UNPROCESSABLE_CONTENT` (`handleIdempotencyConflict`), `CONFLICT` con `properties.code` (`RecurringExpenseFinishedException.CODE`) y `FORBIDDEN`. **No hay precedente de 429**: es el primero del proyecto, y por eso se documenta en `contracts/debt-whistle.md`. El 403 reutiliza `GroupPermissionDeniedException` si su mensaje y su handler alcanzan; si no, se crea una excepción propia (a resolver en T005).

## D6 — Evento de historial

**Decision**: `GroupActivity.GroupActivityType.DEBT_WHISTLED` y `ActivityEventService.recordDebtWhistled(groupId, actorUserId, whistleId, debtId, expenseId, outstanding, currency, debtorName, creditorName, concept)`, con `Propagation.MANDATORY` (se llama desde la transacción 2 de D3). El `CHECK` se amplía en V37 copiando la lista vigente de V36 y sumando `'DEBT_WHISTLED'`.

**Rationale**: es el mismo patrón que `recordDebtReminderSent`. Los detalles usan las claves `fromName`/`toName` que ya consume `activityDescriptions.js` para los eventos de deuda.

## D7 — Interfaz

**Decision**: un componente `WhistleButton({ groupId, debt, debtorName, amountText, currency, concept, simulated, onStale })` encapsula el botón, el `ConfirmModal`, la llamada a `whistleDebt` y los mensajes. Se monta en:

- `MyDebtsPanel`, cuando `debt.creditorUserId === currentUserId` y el estado es avisable (`isPayableDebtStatus`, que ya existe en `debtStatusLabels.js`). Simulado = `debt.debtorUserId == null`.
- `ExpenseDebtList`, cuando `debt.creditor.userId === currentUserId` (el `Person` de `DebtView` trae `userId`) y el estado es avisable. Simulado = `debt.debtor.type === 'SIMULATED'`.

`whistleDebt(groupId, debtId)` se agrega a `groupExpensesClient.js` siguiendo `problemOf` y `failure`, y devuelve `{ ok, status, detail, code, retryAfterSeconds, network }`.

**Rationale**: no hace falta cambiar ningún contrato de lectura. Los datos necesarios ya llegan en `PendingDebtResponse` (`debtorUserId`, `creditorUserId`, `status`) y en `DebtView` (`debtor.type`, `creditor.id`, `status`). Un solo componente evita duplicar la lógica en las dos vistas (constitución, principio X).

**Alternatives considered**: (a) agregar `canWhistle` o `nextWhistleAt` a las respuestas de lectura: requiere consultar `debt_whistles` en cada listado y se dejó fuera de alcance (spec); (b) una página propia: innecesaria para una acción de un clic.

## D8 — Pruebas

**Decision**:

- `DebtWhistleServiceTest` (Mockito + `Clock.fixed`, zona `America/Argentina/Buenos_Aires` como el resto): reglas de D4, ventana de 24 h en el límite exacto (23:59:59 bloquea, 24:00:00 permite), `FAILED` no cuenta, cálculo de `retryAfterSeconds`.
- `DebtWhistleIT` (Testcontainers + GreenMail + MockMvc, mismo setup que las IT de 025): flujo completo, un correo con el asunto esperado, 403/404/409/422/429/503 (503 con el SMTP de GreenMail detenido o con un `JavaMailSender` que falla), evento en el historial, deuda sin cambios y concurrencia con dos hilos (FR-010).
- Vitest: `WhistleButton.test.jsx`, extensiones de los tests de `MyDebtsPanel` (vía `GroupDebtsView.test.jsx`), `ExpenseDebtList.test.jsx`, `groupExpensesClient.test.js` y `activityDescriptions.test.js`.
