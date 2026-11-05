# Feature Specification: Registrar un pago

**Feature Branch**: `feat/registrar-pagos`

**Created**: 2026-11-05

**Status**: Draft (diseño listo, implementación pendiente en esta rama)

**Input**: User description: "Registrar un pago (Sprint 4, 2 SP, responsable Nahuel). Permitir a un usuario registrar un pago para saldar, total o parcialmente, una deuda generada en la spec 017. Al registrarse, el backend actualiza el estado de la deuda (`PARTIALLY_PAID` o `PAID`) y emite el evento `PAYMENT_MADE` de la spec 014 para que aparezca en el historial del grupo. Endpoint `POST /api/payments`. Migración `V14__create_payments.sql` con la tabla `payments` (pagador, receptor, monto, fecha, `group_id` y `debt_id`), solo `CREATE`/`ALTER`/`INDEX`."

## Relación con otras especificaciones

- **017 (Crear estados de deuda)**: 017 creó las deudas con `paid_amount`, `status` y el CHECK que los ata, y dejó probada `Debt.applyPayment` sin exponerla (017 FR-012, SC-006). 018 es su **primer consumidor**: cada pago llama a `applyPayment` y Hibernate persiste el nuevo estado. 018 **no cambia el esquema** de `debts`. Sí extiende dos piezas de código de 017: la respuesta del detalle del gasto suma `canRegisterPayment` por deuda (FR-016), y `ExpenseDebtList` muestra la acción "Registrar pago" (FR-017).
- **017 US2 (bloqueo de bajas)**: `LedgerDebtPort` ya considera "sin saldar" solo a `PENDING` y `PARTIALLY_PAID`. Cuando un pago deja una deuda en `PAID`, la persona **deja de estar bloqueada** para abandonar el grupo o ser quitada, sin tocar el puerto (US2 de esta spec). Es el caso que motivó la tarea: una integrante con deudas no podía salir del grupo porque no había forma de saldarlas.
- **014 (Historial de actividad)**: 014 dejó `ActivityEventService.recordPaymentMade` sin usar, con el formato `paymentId`, `amount`, `currency`, `fromName`, `toName`, y la pantalla ya muestra "{actor} registró un pago de $ X de {from} a {to}" con el filtro "Pagos". 018 es el primer consumidor. Además **agrega** dos claves opcionales al detalle, `expenseId` y `debtId`, para que la entrada enlace al gasto (FR-012). Las entradas que no las traen se siguen mostrando igual: el cambio es compatible y la spec de 014 no se edita.
- **010 (Gestión de grupos)**: se reutilizan la membresía activa, el 404 neutral, `GroupPermissionChecker`, `GroupPermissionDeniedException` (403) y `PendingDebtsException`. La casilla `payments_require_validation` (V9) **sigue sin efecto** en 018 (§Clarifications).
- **007 (Registrar gasto)**: se reutilizan las reglas y mensajes de monto (`ExpenseAmountNormalizer`) y fecha (`ExpenseDateValidator` con `deviceTimeZone`), la cabecera `Idempotency-Key` y `IdempotencyConflictException` (422).
- **015 (Pantalla principal)**: los snackbars (`useSnackbar`) se reutilizan para "Pago registrado". "Grupos recientes" ordena por la última actividad, así que un pago sube al grupo en esa lista sin cambios en 015.
- **016 (Eliminar amigo)**: no se modifica. Sus deudas directas (`DirectDebtPort`) son otra cosa: 018 solo registra pagos de deudas **de grupo**.

Los identificadores FR-### y SC-### de esta especificación son propios de 018.

## Clarifications

### Session 2026-11-05

- Q: ¿Qué se paga? → A: Una deuda concreta de 017, identificada por `debtId`. Un pago salda una sola deuda, en forma total o parcial. Pagar varias deudas con un solo monto o "liquidar" saldos netos es de "Liquidación de deudas" (S5).
- Q: ¿Endpoint? → A: `POST /api/payments` con `debtId` en el cuerpo (pedido del responsable). El grupo se deduce de la deuda; no hace falta en la URL.
- Q: ¿Quién paga y quién recibe? → A: Siempre el deudor y el acreedor de la deuda. El cliente **no** los envía: se copian de la deuda para que no se pueda registrar un pago entre personas equivocadas.
- Q: ¿Quién puede registrar el pago? → A: El **deudor** (si es un usuario registrado), el **acreedor** o un **ADMIN** del grupo. Un MEMBER que no es parte de la deuda recibe 403. Las deudas de un perfil simulado (por ejemplo, "Abuela") solo pueden registrarlas el acreedor o un ADMIN, porque el perfil no tiene cuenta.
- Q: ¿Se permiten pagos parciales? → A: Sí. El monto va de 0,01 hasta el saldo de la deuda. Un pago menor deja la deuda en `PARTIALLY_PAID`; uno igual al saldo, en `PAID`. Pagar de más responde 400.
- Q: ¿Moneda? → A: La de la deuda. No viaja en el cuerpo y no hay conversión.
- Q: ¿Qué fecha tiene el pago? → A: La que indica la persona (por defecto, hoy). No puede ser posterior a hoy (regla de 007) ni anterior a la fecha del gasto que originó la deuda.
- Q: ¿Se aplica la casilla "Los pagos requieren validación" (`payments_require_validation`, V9)? → A: **No en 018.** El responsable pidió que al registrarse el pago la deuda cambie de estado en el momento. Un flujo de validación (pago "pendiente de confirmar", confirmar o rechazar) necesita estados de pago, una pantalla para el acreedor y notificaciones, y no entra en 2 SP. La casilla se sigue guardando sin efecto, como desde 010, y queda documentada como deuda técnica para una spec posterior. El esquema lo deja preparado (data-model.md §Payment).
- Q: ¿Se puede anular, editar o borrar un pago? → A: No. Un error se corrige en una spec posterior (contra-pago o anulación). 018 no ofrece endpoints para eso.
- Q: ¿Desde dónde se registra en la interfaz? → A: Desde el **detalle del gasto** (017), en cada deuda sin saldar que la persona puede pagar, con el botón "Registrar pago". El listado de deudas del grupo ("Ver deudas pendientes") es de S5 y no existe todavía.
- Q: Numeración → A: `018-register-payment`, migración `V14` (reservada en 017 research.md D1 y en `ESTADO_DEL_PROYECTO.md`). Próxima libre: `V15`.

## Fuera de alcance

- Validación de pagos por el acreedor (`payments_require_validation`): estados de pago, confirmar o rechazar.
- Anular, editar o borrar pagos; condonar deudas.
- Pagar varias deudas con un solo pago, compensar deudas cruzadas o calcular saldos netos ("Cálculo de deudas", "Liquidación de deudas").
- Listados de pagos o de deudas del grupo o de la persona ("Ver deudas pendientes", S5).
- Medio de pago, comprobantes o notas en el pago.
- Recordatorios de deuda.
- Pagos de deudas directas entre amigos (016).
- Conversión entre monedas.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Registrar un pago total o parcial de una deuda (Priority: P1) 🎯 MVP

Juan le debe $ 3.333,33 a Ana por el súper. Le transfiere $ 1.000 y lo registra en la app: la deuda pasa a "Pago parcial · faltan $ 2.333,33". Una semana después le transfiere el resto y lo registra: la deuda pasa a "Pagada". Cada pago queda en el historial del grupo como "Juan Pérez registró un pago de $ 1.000,00 de Juan Pérez a Ana López".

**Why this priority**: Es el contenido de la tarea. Sin pagos, las deudas de 017 nunca cambian de estado y bloquean las bajas para siempre.

**Independent Test**: Con una deuda `PENDING` de 3.333,33, `POST /api/payments` con 1.000,00 devuelve 201 y la deuda en `PARTIALLY_PAID` con saldo 2.333,33. Un segundo pago de 2.333,33 devuelve 201 y la deuda en `PAID`. El historial tiene dos entradas `PAYMENT_MADE`.

**Acceptance Scenarios**:

1. **Given** una deuda `PENDING` de Juan hacia Ana por 3.333,33, **When** Juan registra un pago de 1.000,00, **Then** se crea el pago y la deuda queda `PARTIALLY_PAID` con `paidAmount` 1.000,00 y saldo 2.333,33.
2. **Given** esa deuda `PARTIALLY_PAID`, **When** Juan registra 2.333,33, **Then** la deuda queda `PAID` con saldo 0,00.
3. **Given** una deuda `PENDING`, **When** se registra un pago igual al saldo, **Then** pasa directo a `PAID`.
4. **Given** una deuda con saldo 2.333,33, **When** se intenta pagar 2.333,34, **Then** responde 400 en `amount` ("El pago no puede superar el saldo de $ 2.333,33") y no cambia nada.
5. **Given** una deuda `PAID`, **When** se intenta registrar otro pago, **Then** responde 409 `DEBT_ALREADY_PAID` y no cambia nada.
6. **Given** un monto inválido según 007 (vacío, cero, negativo, más de dos decimales o fuera de rango), **When** se intenta registrar, **Then** responde 400 en `amount` con el mismo mensaje que 007.
7. **Given** una fecha posterior a hoy o anterior a la fecha del gasto, **When** se intenta registrar, **Then** responde 400 en `date`.
8. **Given** Ana (acreedora) o un ADMIN del grupo, **When** registra el pago de Juan, **Then** se acepta igual: quien paga sigue siendo Juan y quien recibe sigue siendo Ana. El actor del historial es quien lo registró.
9. **Given** la deuda del perfil simulado "Abuela" hacia Ana, **When** Ana o un ADMIN registran un pago, **Then** se acepta y el pago figura como de "Abuela" a Ana.
10. **Given** un MEMBER que no es deudor ni acreedor de la deuda, **When** intenta registrar un pago, **Then** responde 403 ("Solo quien debe, quien cobra o un administrador pueden registrar este pago").
11. **Given** una persona que no es miembro activo del grupo de la deuda, o una deuda que no existe, **When** intenta registrar un pago, **Then** responde 404 neutral ("No existe la deuda solicitada o no sos miembro del grupo").
12. **Given** la misma solicitud reenviada con la misma `Idempotency-Key`, **When** llega dos veces, **Then** se crea un solo pago, la deuda se descuenta una sola vez y la segunda respuesta devuelve el mismo pago con 200. Con la misma clave y datos distintos responde 422 `IDEMPOTENCY_KEY_REUSED`.
13. **Given** dos pagos simultáneos que juntos superan el saldo, **When** llegan al mismo tiempo, **Then** se acepta uno y el otro responde 400 en `amount` (o 409 si la deuda quedó `PAID`). La deuda nunca queda pagada de más.
14. **Given** un pago registrado, **When** se consulta el historial del grupo, **Then** aparece una entrada `PAYMENT_MADE` con id del pago, monto, moneda, quién paga, quién recibe, `expenseId` y `debtId`.

---

### User Story 2 - Saldar las deudas desbloquea las bajas (Priority: P1)

Lucía tenía una deuda con Ana y no podía abandonar el grupo ("No podés abandonar el grupo: tenés saldos pendientes…"). Registra el pago total, la deuda queda "Pagada" y ahora puede abandonar el grupo. Lo mismo pasa si un ADMIN quiere quitarla, o quitar al perfil simulado "Abuela" una vez saldada su deuda.

**Why this priority**: Es la consecuencia que el equipo necesita para usar la app: sin esto, cualquier grupo con gastos queda trabado. No requiere código nuevo en `groups/` (lo resuelve el estado), pero se prueba de punta a punta.

**Independent Test**: Crear un gasto que genere una deuda de Lucía. Abandonar responde 403 `PENDING_DEBTS`. Registrar un pago parcial: sigue en 403. Registrar el resto: abandonar responde 200 como en 010.

**Acceptance Scenarios**:

1. **Given** Lucía con una deuda `PENDING`, **When** registra un pago **parcial**, **Then** abandonar el grupo sigue respondiendo 403 `PENDING_DEBTS`.
2. **Given** la misma deuda, **When** se registra el pago del saldo y queda `PAID`, **Then** abandonar el grupo responde como en 010 (sale del grupo).
3. **Given** Ana como acreedora de varias deudas, **When** todas quedan `PAID`, **Then** un ADMIN puede quitarla (y, si no es la última ADMIN, abandonar).
4. **Given** el perfil simulado "Abuela" con su deuda `PAID`, **When** un ADMIN lo quita, **Then** se permite; el pago conserva el nombre "Abuela" y su `payer_simulated_profile_id` queda nulo.
5. **Given** una persona con una deuda `PAID` y otra `PENDING` en el mismo grupo, **When** intenta abandonar, **Then** sigue bloqueada hasta saldar la segunda.

---

### User Story 3 - Pantalla "Registrar pago" desde el detalle del gasto (Priority: P2)

En el detalle del gasto, cada deuda sin saldar que la persona puede pagar muestra el botón "Registrar pago". Al pulsarlo, ve quién le paga a quién, el total, lo ya pagado y el saldo; el monto viene cargado con el saldo (pago total) y la fecha con hoy. Puede cambiar el monto para un pago parcial y ve cómo quedará la deuda antes de confirmar. Al guardar, ve el snackbar "Pago registrado" y vuelve al detalle con la deuda actualizada.

**Why this priority**: Hace usable la US1 sin herramientas externas. El valor de dominio (pago, estado, historial, desbloqueo) ya lo entregan US1 y US2.

**Independent Test**: Con el backend simulado en Vitest, abrir el detalle de un gasto, pulsar "Registrar pago" en una deuda, cambiar el monto, ver la vista previa "Pago parcial · faltan $ X", enviar y volver al detalle con el snackbar. Probar 400, 403, 404, 409, 422, 401 y error de red.

**Acceptance Scenarios**:

1. **Given** el detalle de un gasto con una deuda `PENDING` en la que la persona es deudora, acreedora o ADMIN, **When** se muestra, **Then** la deuda tiene el botón "Registrar pago". Las deudas `PAID` y las que la persona no puede pagar no lo tienen.
2. **Given** el botón, **When** se pulsa, **Then** se llega a `/groups/:id/expenses/:expenseId/debts/:debtId/payment` con el resumen de la deuda, el monto cargado con el saldo y la fecha de hoy.
3. **Given** el formulario, **When** se cambia el monto, **Then** la vista previa muestra el estado resultante ("Pagada" o "Pago parcial · faltan $ X") con las mismas etiquetas que el detalle (017).
4. **Given** un monto mayor al saldo, cero, con más de dos decimales o una fecha futura, **When** se intenta enviar, **Then** el formulario muestra el error junto al campo sin llamar al backend, con los mensajes de 007 y "El pago no puede superar el saldo de $ X".
5. **Given** un envío correcto, **When** el backend responde 201 o 200, **Then** se muestra el snackbar "Pago registrado" y se navega al detalle del gasto, que muestra la deuda con su nuevo estado.
6. **Given** un error 400 del backend, **When** responde, **Then** cada mensaje aparece junto a su campo y el formulario conserva lo cargado.
7. **Given** un 409 (la deuda se saldó mientras tanto), **When** responde, **Then** se muestra "La deuda ya está pagada" con un enlace para volver al gasto.
8. **Given** un error de red o 5xx, **When** se envía, **Then** se muestra "No se pudo registrar el pago. Intentá nuevamente". Reintentar usa la misma `Idempotency-Key`.
9. **Given** una entrada `PAYMENT_MADE` del historial con `expenseId`, **When** se pulsa "Ver gasto", **Then** se abre el detalle del gasto.
10. **Given** una deuda ya `PAID` o una persona sin permiso, **When** entra por URL directa al formulario, **Then** ve "La deuda ya está pagada" o "No podés registrar pagos de esta deuda" y un enlace de vuelta, sin formulario.
11. **Given** una sesión expirada (401), **When** carga o envía el formulario, **Then** se cierra la sesión local.

---

### Edge Cases

- **Pago del saldo exacto con centavos**: un saldo de 3.333,33 se salda con 3.333,33; no hay redondeo en los pagos (los montos ya son centavos exactos).
- **Último centavo**: un pago de 0,01 sobre un saldo de 0,01 deja la deuda `PAID`.
- **Perfil simulado quitado después de saldar**: el pago conserva `payer_name` y `payer_simulated_profile_id` queda nulo (`SET NULL`). El historial no se rompe.
- **Acreedor que cambió de nombre**: el pago guarda el nombre del momento (`receiver_name`); los pagos anteriores no cambian.
- **ADMIN que también es deudor**: puede registrar su propio pago (es deudor) y los de otros (es ADMIN). El actor del historial es siempre quien registró.
- **Persona que fue degradada de ADMIN a MEMBER**: solo conserva el permiso sobre las deudas en las que es deudora o acreedora.
- **Deuda de otro grupo**: si quien registra no es miembro activo de ese grupo, 404 neutral aunque la deuda exista (no se filtra su existencia).
- **Concurrencia**: el bloqueo pesimista sobre la deuda serializa los pagos de la misma deuda (data-model.md §Concurrencia). Pagos de deudas distintas no se esperan entre sí.
- **Fecha del gasto en otra zona horaria**: la fecha del pago se valida con `deviceTimeZone` igual que en 007; la comparación con la fecha del gasto es entre fechas (`DATE`), sin horas.
- **Monto máximo**: el de 007 (999.999.999,99), aunque en la práctica el tope real es el saldo.

## Requirements *(mandatory)*

### Functional Requirements

**Registro del pago (US1)**

- **FR-001**: El sistema MUST permitir registrar un pago de una deuda de grupo mediante `POST /api/payments` con `debtId`, `amount`, `date` y `deviceTimeZone` opcional.
- **FR-002**: Quien paga MUST ser el deudor de la deuda y quien recibe MUST ser el acreedor. El sistema MUST copiarlos de la deuda, junto con el grupo y la moneda; el cliente MUST NOT poder elegirlos.
- **FR-003**: Si la deuda no existe o quien registra no es miembro activo de su grupo, MUST responder el 404 neutral "No existe la deuda solicitada o no sos miembro del grupo".
- **FR-004**: Solo MUST poder registrar el pago el deudor (si es usuario registrado), el acreedor o un ADMIN del grupo. En otro caso, MUST responder 403 con el formato de 010. La decisión MUST tomarse en `GroupPermissionChecker`.
- **FR-005**: El monto MUST validarse con las reglas y mensajes de 007 (`ExpenseAmountNormalizer`) y MUST NOT superar el saldo de la deuda. En otro caso, MUST responder 400 en `amount`.
- **FR-006**: Si la deuda está `PAID`, MUST responder 409 con el código `DEBT_ALREADY_PAID` sin crear el pago.
- **FR-007**: La fecha MUST validarse con `ExpenseDateValidator` (007, no posterior a hoy en la zona del dispositivo) y MUST NOT ser anterior a la fecha del gasto de la deuda ("La fecha del pago no puede ser anterior a la del gasto").
- **FR-008**: El registro MUST ser idempotente con la cabecera `Idempotency-Key` (UUID), como en 007/017: misma clave y mismo usuario devuelven el pago existente con 200; clave repetida con datos distintos responde 422 `IDEMPOTENCY_KEY_REUSED`.

**Estado de la deuda (US1)**

- **FR-009**: Al registrar el pago, el sistema MUST aplicarlo a la deuda con `Debt.applyPayment` (017) y persistir `paid_amount`, `status` y `updated_at` mediante JPA/Hibernate. El estado resultante MUST ser `PARTIALLY_PAID` si queda saldo o `PAID` si no.
- **FR-010**: El pago, la actualización de la deuda y el evento del historial MUST guardarse en una sola transacción. Si algo falla, no MUST quedar nada.
- **FR-011**: Dos pagos concurrentes sobre la misma deuda MUST NOT superar su monto: el sistema MUST serializarlos con un bloqueo sobre la deuda y validar el saldo después de obtenerlo.

**Historial (US1)**

- **FR-012**: Cada pago MUST registrar `PAYMENT_MADE` mediante `ActivityEventService.recordPaymentMade` (014) con `paymentId`, `amount`, `currency`, `fromName` (deudor), `toName` (acreedor), `expenseId` y `debtId`, y quien registró como actor.

**Bajas (US2)**

- **FR-013**: Una deuda `PAID` MUST dejar de contar como pendiente para abandonar el grupo, quitar un miembro o quitar un perfil simulado (comportamiento de `LedgerDebtPort` de 017, sin cambios).
- **FR-014**: Un pago parcial MUST NOT desbloquear las bajas.

**Respuesta (US1/US3)**

- **FR-015**: La respuesta de `POST /api/payments` MUST incluir el pago (id, monto, moneda, fecha, quién paga, quién recibe, quién registró) y la deuda actualizada con su estado, monto pagado y saldo.
- **FR-016**: `GET /api/groups/{id}/expenses/{expenseId}` (017) MUST informar, en cada deuda, `canRegisterPayment`: verdadero si la deuda no está `PAID` y el usuario de la sesión cumple FR-004.

**Pantalla (US3)**

- **FR-017**: El detalle del gasto MUST mostrar "Registrar pago" en cada deuda con `canRegisterPayment`, que lleva a `/groups/:id/expenses/:expenseId/debts/:debtId/payment`.
- **FR-018**: El formulario MUST mostrar el resumen de la deuda (quién le paga a quién, total, pagado y saldo), precargar el monto con el saldo y la fecha con hoy, y mostrar la vista previa del estado resultante con las etiquetas de 017.
- **FR-019**: El formulario MUST validar en el cliente el monto (mensajes de 007 y tope del saldo) y la fecha (mensajes de 007) antes de enviar, y mostrar los errores 400 del backend junto a cada campo.
- **FR-020**: Al registrar el pago, la pantalla MUST mostrar el snackbar "Pago registrado" (`useSnackbar` de 015) y volver al detalle del gasto.
- **FR-021**: El formulario MUST manejar carga, 401 (cierra sesión), 403, 404 neutral, 409, 422, error de red con reintento y la misma `Idempotency-Key` en los reintentos.
- **FR-022**: La entrada `PAYMENT_MADE` del historial MUST enlazar al detalle del gasto cuando trae `expenseId`. Las entradas sin `expenseId` MUST mostrarse como hasta ahora.

**Alcance**

- **FR-023**: La feature MUST NOT modificar el esquema de `debts` ni de ninguna tabla existente, ni anular, editar o borrar pagos. La migración MUST usar solo `CREATE`, `ALTER` o `INDEX`, sin `INSERT`, `UPDATE` ni `DELETE`.

### Key Entities *(include if feature involves data)*

- **Pago**: pertenece a una deuda y a su grupo. Tiene pagador (el deudor, usuario o perfil simulado), receptor (el acreedor), quien lo registró, monto, moneda, fecha y clave de idempotencia. Guarda copias de los nombres. No se edita ni se borra.
- **Deuda** (017): suma lo pagado y cambia de estado con cada pago. La suma de sus pagos es siempre igual a su monto pagado.
- **Evento `PAYMENT_MADE`** (014): entrada del historial del grupo que deja cada pago.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: En el 100 % de las deudas, la suma de sus pagos es exactamente igual a su monto pagado, y ninguna deuda queda con monto pagado mayor a su monto.
- **SC-002**: El 100 % de las deudas que quedan `PAID` dejan de bloquear las bajas, y el 100 % de las que quedan `PARTIALLY_PAID` siguen bloqueándolas.
- **SC-003**: Cada pago aparece en el historial del grupo en el mismo instante en que se registra (misma transacción).
- **SC-004**: Una persona registra el pago total de una deuda en menos de 30 segundos desde el detalle del gasto (dos toques y confirmar).
- **SC-005**: Reenviar el mismo pago (doble toque, reintento por red) nunca descuenta la deuda dos veces.

## Assumptions

- La sesión (`amano_auth`), la membresía activa, el 404 neutral y el formato `ProblemDetail` son los de 004 y 010.
- Las monedas admitidas son las de `user.Currency` (ARS, USD y EUR).
- La fecha se valida con `deviceTimeZone`, como en 007.
- El pago se registra en la app después de hacerse por fuera (efectivo, transferencia); la app no mueve dinero.
- Los textos se escriben en español con voseo.
- No se agregan dependencias nuevas.
- `payments_require_validation` (V9) sigue sin efecto hasta una spec posterior (§Clarifications).
