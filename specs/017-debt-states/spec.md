# Feature Specification: Crear estados de deuda

**Feature Branch**: `feat-crear-estadoss-deudas`

**Created**: 2026-11-05

**Status**: Draft (diseño listo, implementación pendiente en esta rama)

**Input**: User description: "Crear estados de deuda (Sprint 4, 2 SP, responsable Nahuel). Gestionar los diferentes estados de obligación de pago entre usuarios. Como hoy no existen gastos de grupo, la spec incluye un gasto de grupo mínimo (pagador, participantes, división en partes iguales con redondeo explícito); al crearlo se genera una deuda por participante distinto del pagador. Reemplazar `NoOpDebtPort`, registrar `EXPENSE_CREATED` en el historial de 014 y dejar el modelo preparado para los pagos (018)."

## Relación con otras especificaciones

- **010 (Gestión de grupos)**: 010 dejó el puerto `DebtPort` con un stub (`NoOpDebtPort`) que siempre responde "sin deudas", y la excepción `PendingDebtsException` (403, código `PENDING_DEBTS`) para quitar miembros y abandonar el grupo. 017 **reemplaza el stub** por una implementación real y **corrige su firma**: hoy `removeSimulatedProfile` pasa el id del perfil simulado como si fuera un `userId` (research.md D7). También empieza a aplicar dos casillas de `V9` que 010 guardaba sin efecto: `members_cannot_make_expenses` y `members_can_generate_expenses_for_others` (FR-007).
- **014 (Historial de actividad)**: 014 dejó el método `ActivityEventService.recordExpenseCreated` sin usar. 017 es el primer consumidor: cada gasto de grupo registra `EXPENSE_CREATED` en la misma transacción. La spec de 014 justifica la falta de gastos de grupo citando "`ESTADO_ACTUAL.md` §4.1", pero **ese archivo no existe** en el repositorio ni en ninguna rama. La referencia está rota. Como las specs anteriores no se editan, la aclaración queda acá: el estado del proyecto se registra en `ESTADO_DEL_PROYECTO.md` (raíz).
- **015 (Pantalla principal)**: "Grupos recientes" ordena por `lastActivityAt`, que sale del máximo `created_at` de `group_activity`. Como cada gasto registra `EXPENSE_CREATED`, el grupo sube en esa lista sin cambios en 015. El header y los snackbars de 015 se reutilizan en US3 (`useSnackbar`).
- **016 (Eliminar amigo)**: 016 define su propio puerto `DirectDebtPort` (con `NoOpDirectDebtPort`) para **deudas directas entre amigos, sin grupo**. Las deudas de 017 son siempre **de grupo**, así que 017 **no** reemplaza ese stub ni cambia 016. Eliminar a un amigo con el que hay deudas de grupo sin saldar sigue permitido: 016 aclara que los grupos previos se mantienen.
- **007/009 (Gastos personales)**: no se modifican. El gasto de grupo **no** es una fila de `expenses`: vive en tablas propias (research.md D2) y reutiliza las reglas de monto, fecha y descripción de 007.
- **018 (Registrar un pago, próxima spec)**: 017 deja el modelo de deuda con monto pagado, estados y transiciones definidos y probados, pero **no registra pagos** ni expone endpoints de pago.

Los identificadores FR-### y SC-### de esta especificación son propios de 017.

## Clarifications

### Session 2026-11-05

- Q: Si no hay gastos de grupo, ¿cómo se generan deudas? → A: 017 incluye un gasto de grupo mínimo: pagador, participantes, división en partes iguales y redondeo explícito.
- Q: ¿Quién puede ser pagador? → A: Un miembro registrado y activo del grupo. Un perfil simulado no puede pagar (no tiene cuenta para cobrar).
- Q: ¿Quiénes pueden participar? → A: Miembros registrados activos y perfiles simulados del grupo. El pagador puede estar o no entre los participantes.
- Q: ¿A quién va el resto del redondeo? → A: Si el pagador participa, el pagador absorbe el resto. Si no participa, el resto se reparte de a un centavo entre los primeros participantes según el orden de la solicitud (research.md D4).
- Q: ¿Qué deudas bloquean quitar a un miembro o abandonar el grupo? → A: Las que estén sin saldar (`PENDING` o `PARTIALLY_PAID`), **tanto si la persona debe como si le deben**. Para un perfil simulado, solo como deudor (nunca es acreedor).
- Q: ¿Se aplican las casillas de `V9`? → A: Sí, las dos. Con `members_cannot_make_expenses` activada, un MEMBER no puede crear gastos. Con `members_can_generate_expenses_for_others` desactivada (el valor por defecto), un MEMBER solo puede ponerse a sí mismo como pagador. El ADMIN siempre puede todo.
- Q: ¿Qué interfaz entra? → A: El formulario "Nuevo gasto" en el grupo y el detalle del gasto con cada deuda y su estado. Los listados de deudas ("Ver deudas pendientes") son de Sprint 5.
- Q: ¿Qué estados tiene una deuda? → A: `PENDING`, `PARTIALLY_PAID` y `PAID`, derivados del monto pagado (research.md D5). En 017 todas las deudas nacen `PENDING`.
- Q: Numeración → A: `017-debt-states`, migración `V13` (una sola). `V14` queda libre para 018.
- Q: ¿Por qué 017 y no 015? → A: La spec se escribió como `015-debt-states` en `feat/estados-deuda`. Mientras tanto se mergeó `015-main-screen` a `sprint-4` (PR #34) y `016-delete-friend` quedó tomada en `feat/delete-friend`. Se renumeró a `017` (y los pagos de 016 a 018) en `feat-crear-estadoss-deudas`, que sale de `sprint-4`. `V13` no cambia: ninguna otra rama la usa.

### Session 2026-11-05 (revisión)

- Q: ¿Se aprueban las decisiones de research.md? → A: D1–D6 y D8 quedan aprobadas como están.
- Q: ¿`expensesCount` del detalle del grupo sigue en 0? → A: No. Pasa a devolver la cantidad real de gastos del grupo. El contrato de 010 no cambia de forma (FR-027).
- Q: ¿Mínimo de participantes? → A: Se mantiene: al menos un participante además del pagador (FR-004).
- Q: 010 (Assumptions) define las deudas pendientes como "saldos netos entre el usuario y otros miembros del grupo". ¿Qué regla usa el puerto real? → A: **Cualquier deuda sin saldar** (`PENDING` o `PARTIALLY_PAID`) de ese grupo, como deudor o acreedor, sin compensar deudas cruzadas. Esta spec reemplaza explícitamente esa Assumption de 010, que no se edita (specs anteriores inmutables). El cálculo de saldos netos queda para "Cálculo de deudas" (FR-017, research.md D7).
- Q: ¿La corrección de `DebtPort` (research.md D7) va con US2? → A: No. Se hace antes de US1, aislada, para commitearla aparte. `NoOpDebtPort` implementa los dos métodos nuevos hasta que US2 lo reemplace.

### Session 2026-11-05 (rama `feat-crear-estadoss-deudas`)

- Q: ¿Qué pasa con el código hecho en `feat/estados-deuda`? → A: Esta rama sale de `sprint-4` y no lo tiene. La implementación se rehace acá siguiendo esta spec, con todas las tareas desmarcadas. El código de `feat/estados-deuda` sirve como referencia, pero no se da por hecho.
- Q: ¿Dónde se ven las deudas desde el grupo? → A: En el detalle de cada gasto (`/groups/:id/expenses/:expenseId`), al que se llega después de registrarlo y desde "Ver gasto" en el historial. La pantalla del grupo suma una tarjeta "Gastos" con el botón "Nuevo gasto" y el acceso al historial filtrado por gastos. Un listado de deudas del grupo necesita un endpoint de listado y es la tarea "Ver deudas pendientes" (Sprint 5): queda fuera de alcance.
- Q: ¿Cómo se diseña la interfaz? → A: Con componentes chicos y reutilizables (`DebtStatusBadge`, `ExpenseSharePreview`, `ExpenseDebtList`) que después puede usar "Ver deudas pendientes". El diseño está en plan.md §Diseño del frontend.

## Fuera de alcance

- División no igualitaria (porcentajes, montos fijos, partes).
- Cálculo y simplificación de deudas entre miembros (matriz "quién le debe a quién", liquidación). Es la tarea "Cálculo de deudas" (S4, TBD) y "Liquidación de deudas" (S5).
- Recordatorios y su configuración (tareas de Maycol y "Personalizar tiempo de envío").
- Pagos: registrarlos, validarlos (`payments_require_validation`) y cambiar el estado de una deuda desde la API (spec 018).
- Editar o borrar gastos de grupo, y cancelar o condonar deudas.
- Listados de gastos o deudas del grupo o de la persona ("Ver deudas pendientes", S5).
- Categoría, medio de pago y recurrencia en gastos de grupo.
- Conversión entre monedas.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Registrar un gasto de grupo y generar sus deudas (Priority: P1) 🎯 MVP

Ana, miembro del grupo "Depto", registra que pagó $ 10.000 del súper, dividido en partes iguales entre ella, Juan y el perfil simulado "Abuela". El sistema guarda el gasto, calcula la parte de cada uno y crea una deuda `PENDING` de Juan hacia Ana y otra de Abuela hacia Ana, cada una por $ 3.333,33. Ana absorbe el centavo que sobra ($ 3.333,34). El historial muestra "Ana López registró un gasto de $ 10.000,00 «Súper»".

**Why this priority**: Sin gasto de grupo no hay deudas, y sin deudas la tarea no tiene contenido. Es la base de 018, del cálculo de deudas y de los recordatorios.

**Independent Test**: Con un grupo de tres participantes, `POST /api/groups/{id}/expenses` devuelve 201 con las tres partes y las dos deudas `PENDING`. La suma de las partes es igual al total, y el historial tiene la entrada `EXPENSE_CREATED`.

**Acceptance Scenarios**:

1. **Given** un grupo con Ana, Juan y el perfil simulado Abuela, **When** Ana registra $ 10.000,00 pagados por ella entre los tres, **Then** se crea el gasto con partes de 3.333,34 (Ana), 3.333,33 (Juan) y 3.333,33 (Abuela), y dos deudas `PENDING` hacia Ana por 3.333,33.
2. **Given** el mismo grupo, **When** Ana registra $ 100,00 pagados por ella entre Juan y Abuela (sin participar), **Then** las partes son 50,00 y 50,00, y hay dos deudas de 50,00 hacia Ana.
3. **Given** que Ana no participa y el monto no se divide exacto ($ 100,00 entre tres personas que no son ella), **When** se registra, **Then** las partes son 33,34, 33,33 y 33,33, en el orden de la solicitud.
4. **Given** un gasto registrado, **When** se consulta el historial del grupo, **Then** aparece una entrada `EXPENSE_CREATED` con id, monto, moneda y descripción del gasto.
5. **Given** un participante repetido, un participante que no pertenece al grupo, un ex miembro o un pagador que no es miembro activo, **When** se intenta registrar, **Then** responde 400 con el campo afectado y no se guarda nada.
6. **Given** que el único participante es el pagador, **When** se intenta registrar, **Then** responde 400 en `participants` ("Elegí al menos un participante además de quien pagó").
7. **Given** un monto que no alcanza un centavo por participante (por ejemplo $ 0,02 entre tres), **When** se intenta registrar, **Then** responde 400 en `amount`.
8. **Given** un monto, fecha o descripción inválidos según las reglas de 007, **When** se intenta registrar, **Then** responde 400 con el mismo mensaje que 007.
9. **Given** la misma solicitud reenviada con la misma `Idempotency-Key`, **When** llega dos veces, **Then** se crea un solo gasto y la segunda respuesta devuelve el mismo gasto con 200.
10. **Given** una persona que no es miembro activo del grupo, **When** intenta registrar o consultar un gasto, **Then** recibe el 404 neutral de 010.

---

### User Story 2 - Bloquear bajas con deudas sin saldar (Priority: P1)

Juan le debe $ 3.333,33 a Ana. Si Juan intenta abandonar el grupo, el sistema lo bloquea con "No podés abandonar el grupo: tenés saldos pendientes con otros miembros". Si un ADMIN intenta quitar a Juan, a Ana (acreedora) o al perfil simulado Abuela (deudora), también se bloquea con el mensaje de 010.

**Why this priority**: Es el comportamiento que 010 prometió y que hoy no funciona por el stub. Sin este bloqueo, una baja deja deudas huérfanas.

**Independent Test**: Crear un gasto que genere deudas. Abandonar o quitar al deudor, al acreedor y al perfil simulado devuelve 403 `PENDING_DEBTS`. En un grupo sin deudas, las mismas operaciones siguen funcionando como en 010.

**Acceptance Scenarios**:

1. **Given** que Juan tiene una deuda `PENDING` hacia Ana, **When** Juan abandona el grupo, **Then** responde 403 con el código `PENDING_DEBTS` y Juan sigue siendo miembro.
2. **Given** la misma deuda, **When** un ADMIN quita a Juan, **Then** responde 403 `PENDING_DEBTS`.
3. **Given** la misma deuda, **When** Ana (acreedora, no ADMIN) abandona el grupo o un ADMIN la quita, **Then** responde 403 `PENDING_DEBTS`.
4. **Given** que el perfil simulado Abuela tiene una deuda `PENDING`, **When** se lo quita, **Then** responde 403 `PENDING_DEBTS` y el perfil no se borra.
5. **Given** un perfil simulado con id 7 sin deudas y un usuario con id 7 con deudas en el mismo grupo, **When** se quita el perfil, **Then** se permite (las deudas no se confunden por id).
6. **Given** que todas las deudas de una persona en el grupo están `PAID`, **When** abandona o se la quita, **Then** se permite.
7. **Given** deudas de Juan en **otro** grupo, **When** Juan abandona este grupo sin deudas en él, **Then** se permite.

---

### User Story 3 - Formulario "Nuevo gasto" y detalle con estados (Priority: P2)

Desde la pantalla del grupo, Ana pulsa "Nuevo gasto", completa monto, moneda, fecha, descripción, quién pagó y quiénes participan, y ve la parte de cada uno antes de confirmar. Al guardar, ve el detalle del gasto: total, pagador, partes, y cada deuda con su estado ("Pendiente", "Pago parcial" o "Pagada") y lo que falta pagar. Desde el historial, la entrada del gasto lleva a ese detalle.

**Why this priority**: Hace usable la US1 sin herramientas externas. El valor de dominio (gasto y deudas) ya lo entregan US1 y US2.

**Independent Test**: Con el backend simulado en Vitest, completar el formulario, ver la vista previa de partes con el resto asignado, enviar y llegar al detalle con las deudas `Pendiente`. Probar los errores 400, 403, 404 y de red.

**Acceptance Scenarios**:

1. **Given** la pantalla del grupo, **When** Ana pulsa "Nuevo gasto", **Then** llega a `/groups/:id/expenses/new` con su nombre preseleccionado como pagador y todos los miembros y perfiles simulados como participantes.
2. **Given** un monto y participantes elegidos, **When** cambia el monto o la selección, **Then** la vista previa muestra la parte de cada uno con el mismo redondeo que el backend.
3. **Given** un MEMBER sin permiso para elegir otro pagador, **When** abre el formulario, **Then** el pagador queda fijo en sí mismo. Si el grupo no permite gastos a los MEMBER, el botón "Nuevo gasto" no se muestra y la API responde 403 igual.
4. **Given** un envío correcto, **When** el backend responde 201, **Then** se navega a `/groups/:id/expenses/:expenseId` con el detalle y las deudas en "Pendiente".
5. **Given** un error 400, **When** responde el backend, **Then** cada mensaje aparece junto a su campo y el formulario conserva lo cargado.
6. **Given** un error de red o 5xx, **When** se envía, **Then** se muestra "No se pudo registrar el gasto. Intentá nuevamente". Reintentar usa la misma `Idempotency-Key`.
7. **Given** una entrada `EXPENSE_CREATED` en el historial con `expenseId`, **When** se pulsa "Ver gasto", **Then** se abre el detalle.
8. **Given** una deuda `PARTIALLY_PAID` o `PAID` (cargada por pruebas, ya que 017 no registra pagos), **When** se muestra el detalle, **Then** se ve "Pago parcial · faltan $ X" o "Pagada".
9. **Given** una sesión expirada (401), **When** carga el formulario o el detalle, **Then** se cierra la sesión local.

---

### Edge Cases

- **Resto con el pagador participando**: el resto (de 0 a n − 1 centavos) siempre se suma a la parte del pagador, así que ninguna deuda supera la división exacta.
- **Resto sin el pagador**: el resto va de a un centavo a los primeros participantes en el orden de la solicitud. Dos solicitudes iguales producen las mismas partes.
- **Monto mínimo**: el total debe permitir al menos 0,01 por participante (total en centavos ≥ cantidad de participantes).
- **Perfil simulado quitado después**: la deuda conserva el nombre copiado y no se rompe (patrón de preservación de 010/014). Solo puede quitarse con sus deudas saldadas.
- **Pagador que deja de ser miembro**: no puede pasar mientras tenga deudas a cobrar sin saldar (US2).
- **Miembro que se fue y volvió**: puede participar de gastos nuevos. Sus deudas viejas siguen vinculadas a su usuario.
- **Concurrencia**: el gasto, sus partes, sus deudas y el evento del historial se guardan en una sola transacción. Si algo falla, no queda nada.
- **Moneda**: el gasto y sus deudas usan la misma moneda (ARS, USD o EUR). No hay conversión.
- **Monto máximo**: el mismo de 007 (999.999.999,99).

## Requirements *(mandatory)*

### Functional Requirements

**Gasto de grupo (US1)**

- **FR-001**: El sistema MUST permitir registrar un gasto de grupo con monto, moneda, fecha, descripción opcional, pagador y participantes, mediante `POST /api/groups/{id}/expenses`.
- **FR-002**: Monto, fecha y descripción MUST validarse con las reglas y mensajes de 007: monto canónico con dos decimales como máximo, entre 0,01 y 999.999.999,99; fecha no posterior a hoy en la zona del dispositivo; descripción de hasta 200 caracteres.
- **FR-003**: El pagador MUST ser un miembro registrado activo del grupo.
- **FR-004**: Cada participante MUST ser un miembro registrado activo o un perfil simulado del grupo, sin repetidos. MUST haber al menos un participante distinto del pagador.
- **FR-005**: El monto MUST dividirse en partes iguales en centavos. Si el pagador participa, el resto MUST sumarse a su parte. Si no participa, el resto MUST repartirse de a un centavo entre los primeros participantes según el orden de la solicitud. La suma de las partes MUST ser igual al total.
- **FR-006**: El total MUST permitir al menos 0,01 por participante. En otro caso, MUST responder 400 en `amount`.
- **FR-007**: Un MEMBER MUST NOT registrar gastos si `members_cannot_make_expenses` está activada. Un MEMBER MUST NOT elegir un pagador distinto de sí mismo si `members_can_generate_expenses_for_others` está desactivada. Un ADMIN siempre puede. La decisión MUST tomarse en `GroupPermissionChecker` y responder 403 con el formato de 010.
- **FR-008**: La creación MUST ser idempotente con la cabecera `Idempotency-Key` (UUID), como en 007: misma clave y mismo creador devuelven el gasto existente con 200, y clave repetida con datos distintos responde 422 `IDEMPOTENCY_KEY_REUSED`.
- **FR-009**: Solo un miembro activo MUST poder registrar o consultar gastos del grupo. Para el resto, MUST responder el 404 neutral de 010.

**Deudas y estados (US1)**

- **FR-010**: Al crear el gasto, el sistema MUST crear una deuda por cada participante distinto del pagador, con monto igual a su parte, acreedor igual al pagador, la moneda del gasto, monto pagado 0 y estado `PENDING`.
- **FR-011**: Los estados de deuda MUST ser `PENDING` (pagado = 0), `PARTIALLY_PAID` (0 < pagado < monto) y `PAID` (pagado = monto). El estado MUST ser coherente con el monto pagado, y la base de datos MUST impedir combinaciones inválidas.
- **FR-012**: Las transiciones válidas MUST ser solo `PENDING → PARTIALLY_PAID`, `PENDING → PAID` y `PARTIALLY_PAID → PAID`. No se vuelve atrás y el monto pagado nunca supera el monto. 017 MUST dejar la operación de dominio que aplica un pago probada, sin exponerla por la API (018 la usará).
- **FR-013**: El gasto, sus partes, sus deudas y el evento del historial MUST guardarse en una sola transacción.
- **FR-014**: Cada parte y cada deuda MUST guardar una copia del nombre visible del participante al momento del gasto.

**Historial (US1)**

- **FR-015**: Cada gasto MUST registrar `EXPENSE_CREATED` mediante `ActivityEventService.recordExpenseCreated` (014), con el id, monto, moneda y descripción del gasto, y el creador como actor.

**Bloqueo de bajas (US2)**

- **FR-016**: `NoOpDebtPort` MUST reemplazarse por una implementación real basada en las deudas del grupo.
- **FR-017**: Un miembro registrado MUST considerarse con deudas pendientes en un grupo cuando tiene al menos una deuda de ese grupo `PENDING` o `PARTIALLY_PAID` como deudor **o** como acreedor.
- **FR-018**: Un perfil simulado MUST considerarse con deudas pendientes cuando tiene al menos una deuda de su grupo `PENDING` o `PARTIALLY_PAID` como deudor. La consulta MUST distinguir perfiles simulados de usuarios aunque compartan el número de id.
- **FR-019**: Abandonar el grupo, quitar un miembro y quitar un perfil simulado MUST lanzar `PendingDebtsException` (403 `PENDING_DEBTS`, mensajes de 010) cuando se cumple FR-017 o FR-018, sin modificar nada.

**Consulta y pantalla (US3)**

- **FR-020**: `GET /api/groups/{id}/expenses/{expenseId}` MUST devolver el gasto con sus partes y sus deudas, cada deuda con estado, monto, monto pagado y saldo. Un gasto de otro grupo MUST responder 404.
- **FR-021**: La pantalla del grupo MUST mostrar una tarjeta "Gastos" con la cantidad de gastos (`expensesCount`) y un acceso al historial filtrado por gastos. La tarjeta MUST ofrecer "Nuevo gasto" cuando el rol y las casillas lo permitan.
- **FR-022**: El formulario MUST mostrar la vista previa de partes con la misma regla de FR-005, preseleccionar al usuario como pagador, fijar el pagador cuando FR-007 lo exija y mostrar los errores 400 junto a cada campo.
- **FR-023**: El detalle MUST mostrar cada deuda con su estado en español ("Pendiente", "Pago parcial", "Pagada") y el saldo cuando corresponda.
- **FR-024**: La entrada `EXPENSE_CREATED` del historial MUST enlazar al detalle cuando trae `expenseId`. El historial MUST aceptar `?filter=EXPENSES` para abrirse con el filtro "Gastos" ya aplicado. Un valor desconocido MUST tratarse como "Toda la actividad".
- **FR-025**: Formulario y detalle MUST manejar carga, error de red con reintento, 401 (cierra sesión) y 404 neutral.

**Alcance**

- **FR-026**: La feature MUST NOT modificar la tabla `expenses` ni los endpoints de 007/009, registrar pagos, permitir editar o borrar gastos, ni modificar filas existentes. La migración MUST usar solo `CREATE` y `ALTER`/`INDEX`.

**Detalle del grupo (US1, revisión del 2026-11-05)**

- **FR-027**: `GET /api/groups/{id}` MUST informar en `expensesCount` la cantidad real de gastos de grupo registrados en el grupo (antes, siempre 0).

### Key Entities *(include if feature involves data)*

- **Gasto de grupo**: pertenece a un grupo. Tiene creador, pagador (miembro registrado), monto, moneda, fecha, descripción opcional y clave de idempotencia. No se edita ni se borra.
- **Parte del gasto**: una por participante. Indica si es un usuario o un perfil simulado, guarda una copia del nombre y el monto de la parte. La suma de las partes es el total.
- **Deuda**: una por parte de un participante distinto del pagador. Tiene deudor (usuario o perfil simulado), acreedor (el pagador), monto, monto pagado, moneda y estado.
- **Estado de deuda**: `PENDING`, `PARTIALLY_PAID` o `PAID`, siempre coherente con el monto pagado.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: En el 100 % de los gastos, la suma de las partes es exactamente el total, sin diferencias de centavos.
- **SC-002**: Ninguna deuda generada supera la división exacta en más de 0,01.
- **SC-003**: El 100 % de las bajas o salidas de personas con deudas sin saldar queda bloqueado, y el 100 % de las personas sin deudas sigue pudiendo irse.
- **SC-004**: Una persona registra un gasto de grupo de tres participantes en menos de 1 minuto desde la pantalla del grupo.
- **SC-005**: Cada gasto aparece en el historial del grupo en el mismo instante en que se crea (misma transacción).
- **SC-006**: 018 puede registrar un pago sin migraciones nuevas sobre deudas: los estados y transiciones ya existen y están probados.

## Assumptions

- La sesión (`amano_auth`), la membresía activa, el 404 neutral y el formato `ProblemDetail` son los de 004 y 010.
- Las amistades no intervienen: solo pueden participar quienes ya son miembros del grupo.
- Las monedas admitidas son las de `user.Currency` (ARS, USD y EUR), igual que el CHECK de `V4`.
- La fecha se valida con `deviceTimeZone`, como en 007.
- Los textos se escriben en español con voseo.
- No se agregan dependencias nuevas.
- `payments_require_validation` (V9) se aplica recién en 018.
