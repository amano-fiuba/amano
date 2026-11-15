# Feature Specification: Whistle — aviso manual a un deudor

**Feature Branch**: `feat/whistle` (el trabajo se hizo en `feat/aviso-manual-saldo-pendiente`, mismo commit base que `sprint-5`; se renombra antes del PR, ver Q11)

**Created**: 2026-11-15

**Status**: Implementada en la rama, pendiente de revisión (quickstart manual T034 y revisión de accesibilidad T032 pendientes)

**Input**: Actividad del cronograma "Whistle" (`09 - Artefactos - Cronograma (1).pdf`): *"Enviar un aviso manual e instantáneo a un integrante con saldo pendiente"*. Fase Social, MVP 5, Sprint 5 (semana 12), responsable Nahuel, 2 story points.

## Relación con otras especificaciones

- **025 (recordatorios automáticos)**: Whistle es el complemento **manual** de los recordatorios programados. Reutiliza el canal de correo (`ReminderMailService`) y el renderizador de mensajes (`ReminderMessageBuilder`), con un asunto y un texto propios que lo distinguen del aviso automático. No crea, cancela ni modifica recordatorios programados de 025, y no cuenta como uno de ellos.
- **017, 018 y 022 (estados de deuda, pagos, gestión de deudas)**: Whistle solo **lee** la deuda (estado, saldo, acreedor y deudor). Nunca cambia su estado ni su saldo.
- **014 (historial de actividad del grupo)**: suma el evento `DEBT_WHISTLED`. La migración nueva amplía el catálogo cerrado de `event_type`, siguiendo el patrón de V20, V22, V28 y V36.
- **026 (modal base)**: la confirmación usa `ConfirmModal`. Se cierra con la × o con Escape y tiene un único botón de acción.
- **020 (abandonar grupo)**: un miembro con deudas abiertas no puede salir del grupo, así que el caso "deudor que ya no es miembro" es raro. Igual se define (FR-009).

Esta spec no modifica ninguna regla de las anteriores.

## Fuera de alcance

- Otros canales: notificaciones push, notificaciones dentro de la app, WhatsApp o SMS.
- Silbar fuera de un grupo: deudas directas entre amigos (027 US4) y deudas sin `group_id`.
- Que el acreedor redacte un texto libre en cada silbido.
- Silbar varias deudas o varios deudores con una sola acción.
- Que el deudor pueda bloquear o desactivar los silbidos.
- Mostrar de antemano, en la lista de deudas, cuándo se puede volver a silbar. El tiempo restante se informa solo al intentarlo (FR-007).
- Que los administradores silben en nombre de otro acreedor.
- Panel consolidado de deudas entre grupos. Es otra actividad del cronograma (Ivan).

## Clarificaciones

### Session 2026-11-15 — decisiones confirmadas por Nahuel ("aprobado")

Nahuel aprobó las once decisiones tal como están escritas, incluida Q3 con la propuesta original (perfiles simulados excluidos).

- **Q1. ¿Quién puede silbar y a quién?** → A (**confirmado por Nahuel el 2026-11-15**): solo el **acreedor** de una deuda abierta de un grupo, y solo al **deudor** de esa deuda. Los administradores y el resto de los miembros no pueden silbar deudas ajenas.
- **Q2. ¿Qué estados de deuda se pueden silbar?** → A (**confirmado por Nahuel el 2026-11-15**): `PENDING`, `PARTIALLY_PAID` y `OVERDUE`, es decir, todos los estados con saldo a pagar (`DebtStatus.isPayable()`). La propuesta original decía "pendiente o parcial"; se agrega `OVERDUE` porque una deuda vencida es el caso más natural para silbar. Quedan excluidos `PAYMENT_REPORTED`, porque el deudor ya informó un pago que espera validación, y `PAID`/`VERIFIED`, porque están saldadas.
- **Q3. ¿Se puede silbar a un perfil simulado?** → A propuesta (**confirmado por Nahuel el 2026-11-15, a pesar de la contradicción con el código**): no. El botón se muestra deshabilitado con un texto explicativo y el backend responde 422 si se intenta igual.
  - **Contradicción detectada:** la propuesta se apoya en que los perfiles simulados "no tienen correo", pero en el repositorio **sí lo tienen y es obligatorio**: `simulated_profiles.email VARCHAR(191) NOT NULL` (V8) y `SimulatedProfile.email` con `nullable = false`. Además, 025 **ya les envía** recordatorios automáticos por correo (`ReminderRecipients.emailOf`).
  - **Alternativa:** permitir silbar a perfiles simulados con su correo, igual que 025. Eso elimina el 422 y el estado deshabilitado (FR-004).
  - Esta spec queda escrita con la propuesta original. Si Nahuel elige la alternativa, se ajustan FR-004, US1 escenario 4, `contracts/`, `data-model.md` (`debtor_user_id` nullable más `debtor_simulated_profile_id`) y las tareas T007, T008, T010, T015 y T018.
- **Q4. ¿Por qué canal se envía y con qué texto?** → A (**confirmado por Nahuel el 2026-11-15**):
  - Por correo, reutilizando `ReminderMailService.send` y un método nuevo `ReminderMessageBuilder.debtWhistle`.
  - Asunto: "{acreedor} te recuerda una deuda - {grupo}", distinto de "Recordatorio de deuda - {grupo}" de 025.
  - Cuerpo: aclara que el aviso lo envía el acreedor en persona e incluye grupo, concepto, saldo pendiente, fecha límite (si existe) y los datos del gasto que ya arma 025.
- **Q5. ¿Se reutiliza el mensaje personalizado de 025?** → A (**confirmado por Nahuel el 2026-11-15**): no. `groups.reminder_message` es una plantilla que los administradores configuran para los recordatorios **automáticos** del grupo. Usarla en un aviso que manda un acreedor en persona mezclaría las dos voces. El silbido usa siempre su texto fijo.
- **Q6. ¿Hay límite anti-spam?** → A (**confirmado por Nahuel el 2026-11-15**): como máximo **1 silbido por par acreedor–deudor por grupo cada 24 horas**, sin importar desde qué deuda se silbe.
  - Si se excede, el backend responde 429 con el tiempo restante (cabecera `Retry-After` y `properties.retryAfterSeconds`) y el frontend lo muestra.
  - Un envío que falla no consume el límite.
- **Q7. ¿Se persiste algo?** → A (**confirmado por Nahuel el 2026-11-15**): sí. Una tabla nueva `debt_whistles` en `V37`, creada solo con `CREATE TABLE` e índices, registra cada intento y permite aplicar el límite.
- **Q8. ¿Queda en el historial del grupo?** → A (**confirmado por Nahuel el 2026-11-15**): sí, como evento `DEBT_WHISTLED` a nombre del acreedor. La misma `V37` reemplaza el `CHECK` de `event_type` con `ALTER TABLE … DROP CONSTRAINT / ADD CONSTRAINT`, como V36.
- **Q9. ¿Dónde aparece el botón?** → A (**confirmado por Nahuel el 2026-11-15**): botón "Silbar" con confirmación (`ConfirmModal`) y aviso de éxito o error, en las dos vistas existentes que listan deudas:
  - la pestaña "Mis deudas pendientes" de `GroupDebtsView` (`MyDebtsPanel`);
  - la lista de deudas del detalle del gasto (`ExpenseDebtList` en `GroupExpenseDetailView`).
  - **Nota:** la propuesta mencionaba "el detalle de la deuda", pero esa pantalla **no existe**. Las deudas se ven en el detalle del gasto y la única ruta por deuda es la de "Registrar pago". Se interpreta como el detalle del gasto.
- **Q10. ¿Qué pasa si el servidor de correo falla?** → A (**confirmado por Nahuel el 2026-11-15**): el envío es sincrónico.
  - Si el correo no sale, se responde 503, no se registra el evento en el historial y el intento no consume el límite.
  - A diferencia de 025, no se notifica a los administradores: el propio acreedor ve el error y puede reintentar.
- **Q11. ¿Qué rama se usa?** → A (**confirmado por Nahuel el 2026-11-15**): la propuesta pide `feat/whistle` desde `sprint-5`, pero la rama local actual es `feat/aviso-manual-saldo-pendiente`, que apunta al mismo commit que `sprint-5` (`5c8d74d`). Se trabaja en la rama actual y se renombra a `feat/whistle` antes de abrir el PR (el comando queda sugerido, no se ejecuta).

## Supuestos

- "Integrante con saldo pendiente" se interpreta como el deudor de una deuda abierta de un grupo. Quien silba es la persona a la que se le debe (Q1).
- "Instantáneo" significa que el correo se envía en el momento de confirmar, no en el próximo ciclo del job de 025.
- El correo del deudor nunca se muestra al acreedor: solo lo usa el servidor.
- Los 2 story points del cronograma cubren el flujo básico. Si el equipo considera que el límite anti-spam o el historial lo exceden, se pueden recortar en la aprobación (ver US2 y US3).

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Silbar a un deudor (Priority: P1) 🎯 MVP

Ana pagó la cena del grupo "Viaje" y Juan le debe $8.000 desde hace dos semanas.
En "Mis deudas pendientes" ve la fila "Juan te debe" y pulsa "Silbar". El
sistema le pide confirmación y, al aceptar, Juan recibe en ese momento un correo
de Ana recordándole la deuda. Ana ve "Le avisamos a Juan".

**Why this priority**: es la actividad del cronograma completa: un aviso manual e
instantáneo al deudor.

**Independent Test**: con una deuda `PENDING` de un usuario registrado, silbar
como acreedor y verificar que llega un único correo al deudor con el asunto y los
datos de la deuda, sin que cambie el estado de la deuda.

**Acceptance Scenarios**:

1. **Given** una deuda abierta (`PENDING`, `PARTIALLY_PAID` u `OVERDUE`) en la que el usuario es el acreedor y el deudor es un usuario registrado y miembro activo, **When** la ve en "Mis deudas pendientes" o en el detalle del gasto, **Then** aparece el botón "Silbar" junto a la deuda.
2. **Given** el botón "Silbar", **When** el acreedor lo pulsa, **Then** se abre una confirmación que dice a quién se le va a avisar, por qué monto y concepto, y que solo se puede silbar una vez cada 24 horas. **When** la cierra con la × o con Escape, **Then** no se envía nada.
3. **Given** la confirmación, **When** el acreedor confirma, **Then** el deudor recibe en ese momento un correo con el asunto "{acreedor} te recuerda una deuda - {grupo}" y los datos de la deuda, el acreedor ve "Le avisamos a {deudor}" y el estado y el saldo de la deuda no cambian.
4. **Given** una deuda abierta cuyo deudor es un perfil simulado, **When** el acreedor la ve, **Then** el botón "Silbar" aparece deshabilitado con el texto "Los perfiles simulados no reciben silbidos". **When** se llama al API igual, **Then** responde 422 sin enviar nada (**sujeto a Q3**).
5. **Given** una deuda en la que el usuario es el deudor, o en la que no participa, **When** la ve, **Then** no aparece el botón "Silbar". **When** se llama al API igual, **Then** responde 403 sin enviar nada.
6. **Given** una deuda `PAYMENT_REPORTED`, `PAID` o `VERIFIED`, **When** el acreedor la ve, **Then** no aparece el botón. **When** se llama al API igual, **Then** responde 409 sin enviar nada.

---

### User Story 2 - Límite de un silbido por día (Priority: P1)

Ana ya le silbó a Juan a la mañana. A la tarde lo intenta de nuevo, desde otra
deuda de Juan en el mismo grupo. El sistema no manda otro correo y le avisa que
va a poder volver a silbarle en 17 h 40 min.

**Why this priority**: sin límite, el botón se convierte en una herramienta de
acoso. Es lo que hace aceptable un aviso instantáneo hecho por una persona.

**Independent Test**: silbar dos veces seguidas al mismo deudor en el mismo grupo
(una desde cada deuda) y verificar un solo correo y un 429 con el tiempo restante.
Adelantar el reloj 24 h y verificar que se puede volver a silbar.

**Acceptance Scenarios**:

1. **Given** un silbido enviado de Ana a Juan en el grupo "Viaje" hace menos de 24 h, **When** Ana vuelve a silbar a Juan en ese grupo, por la misma deuda o por otra, **Then** no se envía ningún correo, el API responde 429 con el tiempo restante y la interfaz muestra "Ya le silbaste a Juan hace poco. Vas a poder volver a hacerlo en {tiempo}".
2. **Given** el mismo caso, **When** Ana silba a Juan por una deuda de **otro** grupo, o a otro deudor del mismo grupo, **Then** el silbido se envía normalmente: el límite es por par acreedor–deudor y por grupo.
3. **Given** un silbido enviado hace 24 h o más, **When** el acreedor vuelve a silbar, **Then** se envía normalmente.
4. **Given** un intento cuyo correo no pudo enviarse (503), **When** el acreedor reintenta enseguida, **Then** el reintento no está bloqueado por el límite.
5. **Given** dos confirmaciones simultáneas del mismo acreedor al mismo deudor (por ejemplo, un doble clic o dos pestañas), **When** llegan al servidor, **Then** se envía un solo correo y la otra recibe 429.

---

### User Story 3 - Silbidos en el historial del grupo (Priority: P2)

Cualquier miembro de "Viaje" abre el historial del grupo y ve "Ana le silbó a
Juan por $8.000 (Cena)".

**Why this priority**: da trazabilidad, igual que los recordatorios de 025, pero
la feature funciona sin esto.

**Independent Test**: silbar y verificar que el historial del grupo muestra un
evento `DEBT_WHISTLED` a nombre del acreedor. Provocar una falla de correo y
verificar que no aparece ningún evento.

**Acceptance Scenarios**:

1. **Given** un silbido enviado con éxito, **When** cualquier miembro activo abre el historial del grupo, **Then** ve un evento a nombre del acreedor con el deudor, el monto pendiente, la moneda y el concepto.
2. **Given** un intento que falló (503) o que fue rechazado (403, 409, 422 o 429), **When** se consulta el historial, **Then** no hay ningún evento de ese intento.

---

### Edge Cases

- **La deuda se pagó entre que se cargó la pantalla y se confirmó el silbido**: el servidor revalida el estado y responde 409 con "La deuda ya no tiene saldo pendiente". La interfaz muestra el mensaje y recarga la lista.
- **La deuda está esperando validación de un pago** (`PAYMENT_REPORTED`): no se puede silbar (409). El deudor ya hizo su parte.
- **El deudor ya no es miembro activo del grupo** (salió o fue eliminado, aunque 020 lo impide si tiene deudas abiertas): 409 con "{deudor} ya no forma parte del grupo". No se envía nada.
- **El acreedor ya no es miembro activo del grupo**: 404 neutral, igual que el resto de los endpoints de grupo.
- **El deudor es un perfil simulado**: 422 y botón deshabilitado (**sujeto a Q3**).
- **El deudor es un perfil simulado eliminado** (`debtor_simulated_profile_id` nulo): se aplica la misma regla que a cualquier simulado.
- **Se alcanzó el límite**: 429 con `Retry-After` y `properties.retryAfterSeconds`. La interfaz lo muestra en horas y minutos, redondeando hacia arriba al minuto.
- **El servidor de correo falla o no responde**: 503 con "No pudimos enviar el aviso. Probá de nuevo en unos minutos". No se registra evento y el intento no consume el límite.
- **Doble clic o dos pestañas**: un solo correo (US2, escenario 5).
- **Deuda inexistente o de otro grupo**: 404 neutral, sin revelar si existe.
- **Sesión expirada**: 401. La interfaz cierra la sesión como en el resto de la app.
- **Error de red al confirmar**: se informa el error y se puede reintentar. No se muestra un éxito falso.
- **Deuda directa entre amigos** (sin grupo, 027 US4): no se puede silbar, porque no hay ruta de grupo. Queda fuera de alcance.
- **Correo del deudor no disponible** (cuenta inexistente): se trata como falla de envío (503) y no se registra nada.

## Requirements *(mandatory)*

### Functional Requirements

**Envío (US1)**

- **FR-001**: El acreedor de una deuda de grupo MUST poder enviar al deudor un aviso manual e inmediato ("silbido") por correo, previa confirmación.
- **FR-002**: Solo se MUST poder silbar una deuda cuyo estado tenga saldo a pagar: `PENDING`, `PARTIALLY_PAID` u `OVERDUE` (**Q2**). Las deudas `PAYMENT_REPORTED`, `PAID` y `VERIFIED` MUST rechazarse con 409.
- **FR-003**: Solo el acreedor de la deuda (`creditor_user_id`), siendo miembro activo del grupo, MUST poder silbarla. Cualquier otro miembro activo MUST recibir 403. Un no miembro, o una deuda que no pertenece al grupo de la ruta, MUST recibir el 404 neutral del proyecto (**Q1**).
- **FR-004**: Si el deudor es un perfil simulado, el servidor MUST responder 422 sin enviar nada, y la interfaz MUST mostrar el botón deshabilitado con el texto "Los perfiles simulados no reciben silbidos" (**Q3**: decisión confirmada aunque el modelo de datos permitiría enviarles correo).
- **FR-005**: El correo MUST enviarse con `ReminderMailService` y un asunto y un texto propios que lo distingan del recordatorio automático de 025:
  - Asunto: "{acreedor} te recuerda una deuda - {grupo}".
  - Contenido: el nombre del acreedor, el grupo, el concepto, el saldo pendiente con su moneda y la fecha límite si existe, más los datos del gasto que ya arma 025 (categoría, tipo y medio de pago).
  - No MUST usar el mensaje personalizado del grupo de 025 (**Q4 y Q5**).
- **FR-006**: Silbar MUST NOT cambiar el estado, el saldo ni los pagos de la deuda, ni los recordatorios programados de 025.

**Límite (US2)**

- **FR-007**: El sistema MUST permitir como máximo un silbido enviado o en curso por cada par (grupo, acreedor, deudor) en una ventana móvil de 24 horas, sin importar la deuda desde la que se silbe. Un intento adicional MUST responder 429 con la cabecera `Retry-After` (segundos) y `properties.retryAfterSeconds`, y sin enviar correo (**Q6**).
- **FR-008**: Un intento cuyo correo no se pudo enviar MUST NOT contar para el límite de FR-007.
- **FR-009**: Si el deudor registrado ya no es miembro activo del grupo, el servidor MUST responder 409 sin enviar nada.
- **FR-010**: Dos solicitudes simultáneas para el mismo par MUST producir como máximo un correo.

**Historial (US3)**

- **FR-011**: Cada silbido enviado con éxito MUST registrar un evento `DEBT_WHISTLED` en el historial del grupo a nombre del acreedor, con el deudor, el monto pendiente, la moneda y el concepto (**Q8**). Los intentos rechazados o fallidos MUST NOT registrar evento.
- **FR-012**: El historial MUST describir el evento como "{acreedor} le silbó a {deudor} por {monto} {moneda} ({concepto})".

**Interfaz**

- **FR-013**: El botón "Silbar" MUST aparecer junto a cada deuda silbable en la que el usuario sea el acreedor, en "Mis deudas pendientes" (`GroupDebtsView`) y en la lista de deudas del detalle del gasto (`GroupExpenseDetailView`). No MUST aparecer en deudas en las que el usuario no es acreedor (**Q9**).
- **FR-014**: La confirmación MUST usar el modal base (`ConfirmModal`): título "Silbar a {deudor}", un texto con el monto y el concepto, la aclaración del límite de 24 horas y un único botón "Silbar".
- **FR-015**: Tras el éxito, la interfaz MUST mostrar "Le avisamos a {deudor}" sin recargar la página. Ante un error, MUST mostrar el mensaje del servidor o uno equivalente en voseo, sin éxitos falsos:
  - 429: el tiempo restante en horas y minutos.
  - 409: el mensaje y la recarga de la lista.
  - 503: "No pudimos enviar el aviso. Probá de nuevo en unos minutos".
  - Error de red: mensaje y opción de reintentar.

**Compatibilidad, seguridad y errores**

- **FR-016**: La operación MUST requerir la sesión de una cuenta verificada y MUST responder los errores en el formato RFC 7807 del proyecto, en español y sin detalles internos. El correo del deudor nunca MUST aparecer en respuestas al acreedor ni en mensajes de error.
- **FR-017**: Los cambios de contrato MUST ser aditivos: un endpoint nuevo y un tipo de evento nuevo. Los endpoints existentes no cambian.

### Non-Functional Requirements

- **NFR-001 (latencia)**: con el servidor de correo disponible, el acreedor ve el resultado en menos de 5 segundos desde que confirma. El envío es sincrónico (Q10).
- **NFR-002 (privacidad)**: el correo del deudor se usa solo del lado del servidor. Las respuestas y los mensajes de error no lo exponen (FR-016).
- **NFR-003 (consistencia)**: el límite se aplica en el servidor bajo concurrencia (FR-010). La interfaz no es la que lo garantiza.
- **NFR-004 (migraciones)**: la migración nueva usa solo `CREATE TABLE`, `CREATE INDEX` y `ALTER TABLE … DROP/ADD CONSTRAINT`. No incluye `UPDATE` ni `DELETE` de datos ni edita migraciones integradas (V1 a V36).
- **NFR-005 (calidad)**: cobertura de líneas ≥ 85 % en backend (JaCoCo, `mvn -B verify`) y frontend (V8, `npm run test:coverage`), sin bajar umbrales ni excluir código. Lint y build en verde.
- **NFR-006 (accesibilidad)**: el botón tiene `aria-label` con el nombre del deudor. El modal conserva el foco inicial, Escape y el resto del comportamiento de `ConfirmModal`. El estado deshabilitado de Q3 se acompaña de un texto visible, no solo de un `title`.
- **NFR-007 (idioma)**: los textos de la interfaz y del correo van en español, con voseo, igual que 025 y 026.

### Key Entities *(include if feature involves data)*

- **Debt** (existente, 017/022): la deuda silbada. Solo se lee: `creditor_user_id`, `debtor_type`, `debtor_user_id`, `status`, `amount - paid_amount` y `expense_id`.
- **DebtWhistle** (nuevo, tabla `debt_whistles`): un intento de silbido. Registra grupo, deuda (que puede quedar nula si la deuda se borra), acreedor, deudor, estado del intento (`SENDING`, `SENT`, `FAILED`) y fechas. Es la base del límite de FR-007.
- **GroupActivity** (existente, 014): evento nuevo `DEBT_WHISTLED` con actor = acreedor.
- **SimulatedProfile** (existente, 010): tiene correo obligatorio. Por Q3, se excluye de los silbidos.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Un acreedor silba a un deudor en menos de 10 segundos desde que abre "Mis deudas pendientes", pasando siempre por la confirmación.
- **SC-002**: El 100 % de los silbidos confirmados con el servidor de correo disponible entregan exactamente un correo al deudor (verificable con GreenMail).
- **SC-003**: El 0 % de los intentos que exceden el límite de 24 horas envía correo, incluidos los simultáneos.
- **SC-004**: El 0 % de los intentos de alguien que no es el acreedor, sobre deudas saldadas o con pago informado, o hacia perfiles simulados (sujeto a Q3), envía correo.
- **SC-005**: El 100 % de los silbidos enviados aparece en el historial del grupo, y el 0 % de los fallidos o rechazados.
- **SC-006**: Silbar no cambia el estado ni el saldo de ninguna deuda (0 cambios verificables antes y después).
