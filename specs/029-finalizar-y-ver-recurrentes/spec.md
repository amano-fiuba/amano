# Feature Specification: Finalizar y ver gastos recurrentes de grupo

**Feature Branch**: `feat/finalizar-ver-recurrentes`

**Created**: 2026-11-13

**Status**: Draft

**Input**: User description: "Extiende la spec 028 con dos historias: (1) Pausar o frenar un gasto recurrente — pausar, reanudar o finalizar la generación automática de un gasto recurrente, con confirmación previa y conservando los gastos ya generados; (2) Ver gastos recurrentes — visualizar los gastos recurrentes con su importe, categoría o grupo asociado, cuánto se gastó, el próximo vencimiento y el acceso al detalle para conocer su origen."

## Relación con otras especificaciones

- **Extiende 028 (gastos compartidos recurrentes)**: agrega el estado **Finalizado** (028 solo contempla pausar/reactivar y cambiar frecuencia, y declara "finalizar una serie desde la UI" fuera de alcance), exige confirmación antes de pausar o finalizar, y completa la vista de gastos recurrentes con el acumulado gastado, el próximo vencimiento y el origen de cada gasto.
- **Conserva de 028**: el catálogo de frecuencias y la aritmética de fechas ancladas (reutilizada de 009), la generación diaria idempotente con ventana de anticipación de 7 días, las reglas de vencimiento (FR-016 de 028) y recordatorios (025), los permisos de modificación de series (FR-021 de 028) y el acceso a la pantalla de gastos recurrentes desde la sección "Gastos" del grupo (FR-020 de 028).
- **009 (pausar gasto recurrente personal)**: precedentes adoptados para las series de grupo — finalizar es definitivo, pausar no genera retroactivamente, pedir el estado que la serie ya tiene es éxito sin cambios y solo es error modificar una finalizada.
- **022 (deudas y vencimientos)**: las ocurrencias generadas antes de pausar o finalizar conservan sus deudas y su flujo de pago, saldo y recordatorio.

Donde esta spec y una anterior difieran, vale esta.

## Fuera de alcance

- Gastos recurrentes personales y con amigo: su pausa y finalización ya están cubiertas por 009; esta spec solo trata series de grupo.
- Editar el monto u otros datos de una serie, eliminar series y programar una fecha de fin (la frecuencia ya puede cambiarse según 028).
- Pausar o finalizar desde otras pantallas distintas de la de gastos recurrentes del grupo.
- Notificaciones al pausar, reanudar o finalizar una serie.

## Supuestos

- Las historias se aplican a las series de gastos recurrentes de grupo de 028; la vista de gastos recurrentes es la de un grupo.
- "Cuánto gasté" se interpreta como el **acumulado de la serie**: la suma de la primera ocurrencia y de las ocurrencias ya generadas, expresada en la moneda de la serie.
- El importe de cada gasto recurrente es el monto de la ocurrencia (el gasto original o una repetición generada); el acumulado puede diferir del monto por ocurrencia.
- Finalizar se ofrece para series Activas y Pausadas (precedente de 009); los criterios de la historia piden explícitamente finalizar una activa.
- La confirmación es un diálogo previo: para pausar y reanudar pide una confirmación simple; para finalizar advierte explícitamente que es definitivo.
- Los saldos que se actualizan son los del grupo (totales y deudas), que ya reaccionan al registro de un gasto; el requisito asegura que las ocurrencias generadas no sean una excepción.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Pausar y reanudar una serie con confirmación (Priority: P1)

Ana tiene activa la serie "Alquiler" del grupo "Depto" y no va a pagar más por un tiempo.
Entra a "Gastos recurrentes", elige la serie y pulsa "Pausar"; el sistema pide
confirmación y, al aceptarla, la marca claramente como Pausada y deja de generar
ocurrencias. Meses después la reanuda: vuelve a estar Activa y continúa generando
las próximas ocurrencias según la periodicidad configurada, sin recuperar las que
transcurrieron durante la pausa. Todo lo ya generado queda intacto.

**Why this priority**: es el control operativo central que la historia pide: frenar
la generación sin perder la serie ni lo ya registrado.

**Independent Test**: pausar una serie activa con confirmación, verificar que la
generación no crea nada mientras esté pausada, reanudarla y verificar que la
próxima fecha queda en el futuro según la frecuencia y que los gastos anteriores
siguen registrados.

**Acceptance Scenarios**:

1. **Given** una serie Activa en la pantalla de gastos recurrentes, **When** el creador, el pagador o un administrador pulsa "Pausar", **Then** el sistema pide confirmación; **When** cancela, **Then** no cambia nada; **When** confirma, **Then** la serie queda Pausada, se indica claramente su nuevo estado y se muestra "El gasto recurrente se pausó".
2. **Given** una serie Pausada cuya próxima fecha ya venció, **When** corre la generación diaria, **Then** no se crea ninguna ocurrencia para esa serie mientras permanezca pausada.
3. **Given** una serie Pausada, **When** se la reanuda, **Then** queda Activa, su próxima fecha se recalcula con la frecuencia configurada como la primera fecha igual o posterior a hoy y no se generan ocurrencias de los períodos transcurridos durante la pausa.
4. **Given** la serie reanudada, **When** llega su próxima fecha dentro de la ventana de generación, **Then** se generan las próximas ocurrencias según la periodicidad configurada, con sus deudas y vencimientos como en 028.
5. **Given** una serie con ocurrencias ya generadas, **When** se la pausa y se la reanuda, **Then** el gasto original y todas las ocurrencias generadas antes de pausar se conservan con sus deudas.
6. **Given** un miembro que no creó la serie, no es el pagador ni administrador, **When** intenta pausarla o reanudarla, **Then** recibe el mismo rechazo por permisos que en 028 y nada cambia.

---

### User Story 2 - Finalizar una serie (Priority: P1)

Juan decide que la serie "Cuota gimnasio" del grupo no seguirá nunca más. En la
lista de gastos recurrentes pulsa "Finalizar"; el sistema advierte que es
definitivo y pide confirmación. Al confirmar, la serie queda Finalizada: no
genera más ocurrencias, pierde su próxima fecha y ya no puede reanudarse. Los
gastos ya generados, con sus deudas, siguen disponibles para el flujo normal.

**Why this priority**: es la mitad de la historia que 028 dejó fuera de alcance;
cierra el ciclo de vida de la serie de forma definitiva y explícita.

**Independent Test**: finalizar una serie activa o pausada con confirmación,
ejecutar la generación y verificar que no se genera nada; intentar reanudarla y
verificar el rechazo; verificar que las ocurrencias anteriores se conservan.

**Acceptance Scenarios**:

1. **Given** una serie Activa o Pausada, **When** se pulsa "Finalizar", **Then** el sistema pide confirmación advirtiendo que es definitivo y que no se generarán más gastos de esa serie; **When** se cancela, **Then** no cambia nada.
2. **Given** la confirmación, **When** se confirma, **Then** la serie queda Finalizada, pierde su próxima fecha, ya no ofrece acciones y se muestra "El gasto recurrente se finalizó".
3. **Given** una serie Finalizada, **When** corre la generación diaria, **Then** no se registra ninguna ocurrencia para esa serie.
4. **Given** una serie Finalizada, **When** alguien intenta pausarla, reanudarla o cambiarle la frecuencia, **Then** se rechaza con "El gasto recurrente ya está finalizado y no puede modificarse" y la serie no cambia.
5. **Given** una serie Finalizada con ocurrencias y deudas previas, **When** se consulta el grupo, **Then** el gasto original y las ocurrencias generadas se conservan con sus deudas y siguen disponibles para pagos, saldos y recordatorios.

---

### User Story 3 - Ver los gastos recurrentes con importes, vencimiento y origen (Priority: P2)

Un miembro abre "Gastos recurrentes" del grupo y ve cada serie con su concepto,
el importe por ocurrencia, la moneda, la categoría, la frecuencia, el estado, la
próxima fecha, el próximo vencimiento y cuánto se gastó acumulado en la serie.
Cuando se registra una nueva ocurrencia, los saldos del grupo y el acumulado se
actualizan sin ninguna acción manual. Desde el detalle de cualquier gasto de la
serie —el original o una repetición— puede conocer su origen: de qué serie proviene
y si fue generado automáticamente por el sistema.

**Why this priority**: completa el valor de la historia "ver gastos recurrentes":
saber cuánto se gastó, cuándo vence lo próximo y de dónde salió cada gasto.

**Independent Test**: con una serie de dos ocurrencias generadas, abrir la lista y
verificar monto, categoría, acumulado, próxima fecha y vencimiento; abrir el
detalle de una ocurrencia generada y verificar que muestra su origen y su serie.

**Acceptance Scenarios**:

1. **Given** la pantalla de gastos recurrentes de un grupo con series, **When** cualquier miembro activo la abre, **Then** ve de cada serie el concepto, el importe por ocurrencia, la moneda, la categoría y el grupo asociado, la frecuencia, el estado, la próxima fecha y el acumulado gastado de la serie.
2. **Given** una serie activa, **When** se muestra su fila, **Then** se indica el próximo vencimiento según las reglas de 028 (la primera ocurrencia usa el plazo vigente del grupo; las generadas vencen en su propia fecha); una serie Pausada o Finalizada no muestra próxima fecha ni vencimiento.
3. **Given** una serie con dos ocurrencias de $10.000 cada una, **When** se muestra, **Then** el importe por ocurrencia es $10.000 y el acumulado gastado es $20.000; una serie sin ocurrencias generadas muestra un acumulado de $0.
4. **Given** el job que registra una nueva ocurrencia, **When** la ocurrencia queda creada, **Then** los saldos y totales del grupo y el acumulado de la serie la reflejan sin que ningún miembro tenga que recargar o recalcular manualmente.
5. **Given** una ocurrencia generada por el job, **When** el usuario abre su detalle, **Then** ve que proviene de la serie correspondiente y que fue creada por "Sistema", junto con sus datos (monto, moneda, categoría, pagador, reparto y deudas).
6. **Given** el gasto original de una serie, **When** se abre su detalle, **Then** se identifica como el gasto que dio origen a la serie recurrente.

---

### Edge Cases

- **Mismo estado** (pausar una serie ya pausada, reanudar una activa, finalizar una finalizada por doble clic): pedir el estado que la serie ya tiene responde éxito sin cambios, salvo modificar una Finalizada, que siempre se rechaza.
- **Finalizar mientras hay una ocurrencia en la ventana de anticipación ya generada**: la ocurrencia ya creada se conserva; la serie no genera más.
- **Detalle de una ocurrencia cuya serie fue finalizada o pausada**: el detalle sigue mostrando el origen y la serie; finalizar o pausar no rompe el vínculo.
- **Acumulado y moneda**: una serie en una moneda distinta a la del grupo muestra su acumulado en la moneda de la serie, con los datos de conversión que conserva de 028.
- **Serie pausada con próxima fecha conservada**: mientras está pausada, la fila muestra su próxima fecha guardada pero no genera; al reanudar se recalcula.
- **Serie finalizada en la lista**: sigue visible con su estado Finalizado y su acumulado histórico, sin acciones.
- **Permisos**: solo creador, pagador o administrador pueden pausar, reanudar o finalizar; el resto de los miembros puede ver la lista y los detalles, y sus intentos de modificación se rechazan sin cambios.
- **Sesión expirada, error de red o del servidor**: al cambiar de estado, se informa el error y la serie conserva su estado anterior en pantalla; al cargar la lista, se ofrece reintentar, sin éxitos falsos.
- **Serie antigua `RECURRING` sin frecuencia** (previa a 028): no aparece en gastos recurrentes ni genera ocurrencias, como define 028.

## Requirements *(mandatory)*

### Functional Requirements

**Ciclo de vida de la serie (US1, US2)**

- **FR-001**: Una serie de grupo MUST tener uno de tres estados: Activo, Pausado o Finalizado. Solo las series Activas generan ocurrencias. Este requisito agrega Finalizado al modelo de 028.
- **FR-002**: Pausar una serie Activa MUST pedir confirmación previa y, al confirmarse, cambiar el estado a Pausado conservando su próxima fecha. Mientras esté Pausada, la generación MUST NOT crear ocurrencias para esa serie.
- **FR-003**: Reanudar una serie Pausada MUST cambiar su estado a Activo y MUST recalcular su próxima fecha como la primera fecha de la frecuencia configurada igual o posterior a hoy; las fechas transcurridas durante la pausa MUST NOT generarse retroactivamente.
- **FR-004**: Finalizar MUST estar disponible para series Activas y Pausadas y MUST pedir confirmación previa advirtiendo explícitamente que es definitivo.
- **FR-005**: Al finalizar, la serie MUST cambiar a Finalizado, MUST dejar de tener próxima fecha y MUST NOT generar ocurrencias. Una serie Finalizada MUST NOT poder pausarse, reanudarse ni cambiar su frecuencia: los intentos MUST rechazarse con "El gasto recurrente ya está finalizado y no puede modificarse".
- **FR-006**: Pausar, reanudar o finalizar una serie MUST NOT alterar el gasto original ni las ocurrencias y deudas ya generadas: MUST conservarse con sus datos, vencimientos y flujo de pagos y recordatorios.
- **FR-007**: Las transiciones válidas MUST ser Activo → Pausado, Pausado → Activo, Activo → Finalizado y Pausado → Finalizado. Pedir el estado que la serie ya tiene MUST responder éxito sin cambios.
- **FR-008**: Solo el creador de la serie, el pagador o un administrador del grupo MUST poder pausarla, reanudarla o finalizarla; cualquier otro miembro MUST recibir el mismo rechazo por permisos que define 028 (FR-021).

**Vista de gastos recurrentes (US3)**

- **FR-009**: La pantalla de gastos recurrentes del grupo MUST listar las series con concepto, importe por ocurrencia, moneda, categoría, grupo asociado, frecuencia, estado y acumulado gastado de la serie.
- **FR-010**: Para una serie Activa, la vista MUST mostrar su próxima fecha y el próximo vencimiento de su próxima ocurrencia, calculado según las reglas de vencimiento de 028 (FR-016); una serie Pausada muestra su próxima fecha guardada sin vencimiento vigente y una Finalizada no muestra ninguna.
- **FR-011**: El acumulado gastado de una serie MUST ser la suma del gasto original y de sus ocurrencias generadas, en la moneda de la serie; una serie sin ocurrencias generadas MUST mostrar un acumulado de $0.
- **FR-012**: Cuando el job registre una nueva ocurrencia, los saldos, totales del grupo y el acumulado de la serie MUST reflejarla sin acción manual de ningún miembro.
- **FR-013**: El usuario MUST poder acceder al detalle de cualquier gasto de una serie (original u ocurrencia generada) para conocer su origen: la serie a la que pertenece y si fue creado por el sistema o por una persona, junto con sus datos (monto, moneda, categoría, pagador, reparto y deudas).
- **FR-014**: La interfaz MUST indicar claramente el estado de cada serie (Activo, Pausado, Finalizado) y, tras pausar, reanudar o finalizar, MUST mostrar la confirmación correspondiente ("El gasto recurrente se pausó", "…se reanudó", "…se finalizó") sin recargar la página.

**Compatibilidad, seguridad y errores**

- **FR-015**: Las reglas de permisos, vencimientos, recordatorios, generación idempotente y ventana de anticipación de 028 MUST seguir aplicándose sin cambios para todo lo no modificado por esta spec.
- **FR-016**: Todas las operaciones MUST requerir una sesión de una cuenta verificada y MUST responder errores en el formato del proyecto, en español y sin detalles internos.

### Key Entities *(include if feature involves data)*

- **GroupExpense (serie)**: gasto compartido con `expense_type = RECURRING`, frecuencia y estado de ciclo de vida (Activo, Pausado, Finalizado); tiene próxima fecha mientras no esté finalizada. Esta spec agrega el estado Finalizado a lo definido en 028.
- **GroupExpense (ocurrencia)**: gasto compartido generado por el job con `recurrence_source_id` apuntando a la serie; el gasto original es la primera ocurrencia. Es la base del detalle de origen (FR-013).
- **GroupExpenseShare**: partes de cada ocurrencia, copiadas de la serie.
- **Debt**: deudas `PENDING` de cada ocurrencia, conservadas aunque la serie se pause o finalice (FR-006).
- **Acumulado de la serie**: valor calculado como la suma de las ocurrencias de la serie en la moneda de la serie (FR-011).

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: El 0 % de las series Pausadas o Finalizadas genera ocurrencias, verificable ejecutando la generación con fechas vencidas.
- **SC-002**: El 100 % de los intentos de pausar, reanudar o cambiar la frecuencia de una serie Finalizada es rechazado, y el 0 % de las series finalizadas vuelve a generar.
- **SC-003**: Un miembro autorizado pausa, reanuda o finaliza una serie en menos de 10 segundos desde que abre la pantalla, pasando siempre por la confirmación, y ve el nuevo estado sin recargar la página.
- **SC-004**: Tras registrarse una ocurrencia, el acumulado de la serie y los saldos del grupo la reflejan sin ninguna acción manual, en el primer vistazo a la pantalla.
- **SC-005**: Desde el detalle de cualquier gasto de una serie, el usuario identifica su origen (serie de la que proviene y si lo creó el sistema) en un solo paso.
- **SC-006**: El 100 % de las pausas, reanudaciones y finalizaciones conservan íntegras las ocurrencias y deudas ya generadas.
