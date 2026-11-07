# Feature Specification: Recordatorios automáticos de deudas pendientes

**Feature Branch**: `feat/envio-recordatorio-deudas`

**Created**: 2026-11-07

**Status**: Draft

**Input**: User description: "Recordatorios automáticos de deudas pendientes en un grupo: el sistema envía por correo electrónico recordatorios a los miembros con deudas pendientes antes del vencimiento. El Administrador del grupo puede personalizar el mensaje y el tiempo de envío; si no lo hace, se usan valores predeterminados."

## Relación con otras especificaciones

- **017/022 (deudas y estados)**: los recordatorios usan los estados de deuda existentes (Pendiente, Pago informado, Pagado, Verificado, Vencido) y el vencimiento por grupo definido en 022 (fecha límite = fecha del gasto originario + días de vencimiento del grupo). Esta spec no crea estados ni transiciones nuevas. 017 dejó explícitamente los recordatorios y su configuración fuera de su alcance.
- **018/022 (pagos y verificación)**: si una deuda se salda o se verifica antes de la fecha programada, el recordatorio se cancela. No se modifican los flujos de pago ni de validación.
- **014 (historial de actividad)**: el envío de cada recordatorio se registra como evento del historial del grupo, igual que los eventos de gasto registrado o deuda vencida.
- **003/006 (correo electrónico)**: se reutiliza la infraestructura de envío de correo existente (verificación de cuenta y recuperación de contraseña); no se agrega un canal nuevo.
- **010 (grupos)**: la configuración de recordatorios pertenece a la configuración del grupo y solo el Administrador puede modificarla, con los permisos existentes.

Los identificadores FR-### y SC-### de esta especificación son propios de la 025.

## Fuera de alcance

- Recordatorios por canales distintos al correo electrónico (notificaciones dentro de la app, push, SMS).
- Múltiples recordatorios por deuda (recordatorios escalonados, reintentos de envío).
- Configuración de recordatorios por miembro individual.
- Recordatorios para deudas directas entre amigos sin grupo (016).
- Modificar los estados, transiciones o el proceso de vencimiento de deudas de 017/022.
- La configuración de los días de vencimiento del grupo (022); esta spec solo configura cuándo y con qué mensaje se recuerda una deuda que ya tiene fecha límite.
- La personalización del texto del aviso al pagador (usa un texto predeterminado propio).

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Enviar recordatorio automático de deudas (Priority: P1)

Juan debe $ 3.333,33 en el grupo "Depto" y la deuda tiene fecha límite de pago. Sin que nadie configure nada de recordatorios, el sistema le envía un correo 5 días antes del vencimiento (tiempo predeterminado), con el mensaje predeterminado que identifica el grupo, el concepto, el monto y la fecha límite. El envío queda registrado en el historial de actividad del grupo. Ana, cuya deuda ya fue saldada y verificada, no recibe nada.

**Why this priority**: Es el valor central de la feature y debe funcionar por sí sola con la configuración predeterminada (mensaje y tiempo de envío), sin que el Administrador configure nada. Las demás historias se apoyan en este mecanismo: la 2 y la 4 lo personalizan y la 3 lo extiende al pagador del gasto.

**Independent Test**: Con un grupo que ya tiene días de vencimiento configurados (022) y un gasto que genera deudas, sin tocar ninguna configuración de recordatorios, cada deudor con deuda sin saldar recibe exactamente un correo en la fecha calculada (5 días antes del vencimiento), con grupo, concepto, monto y fecha límite; los miembros con deuda saldada no reciben nada y el envío aparece en el historial del grupo.

**Acceptance Scenarios**:

1. **Given** un grupo con días de vencimiento configurados y una deuda pendiente de Juan con fecha límite D, **When** llega la fecha D − 5 días (tiempo predeterminado), **Then** Juan recibe exactamente un correo de recordatorio con el nombre del grupo, el concepto, el monto y la fecha límite D.
2. **Given** un grupo sin ninguna configuración de recordatorios, **When** el sistema genera recordatorios, **Then** se aplican el tiempo predeterminado (5 días antes) y el mensaje predeterminado.
3. **Given** una deuda de Ana que fue pagada y verificada antes de la fecha de envío, **When** llega la fecha de envío, **Then** Ana no recibe ningún recordatorio.
4. **Given** una deuda saldada como Pagado (grupo sin validación de pagos) antes de la fecha de envío, **When** llega la fecha de envío, **Then** el miembro no recibe recordatorio.
5. **Given** un recordatorio enviado, **When** se consulta el historial de actividad del grupo, **Then** aparece el envío del recordatorio asociado al grupo.
6. **Given** un miembro con dos deudas pendientes con la misma fecha límite, **When** llega la fecha de envío, **Then** recibe un recordatorio por cada deuda (único por deuda, sin reenvíos posteriores).
7. **Given** una deuda de un grupo sin días de vencimiento configurados (sin fecha límite), **When** se evalúan los recordatorios, **Then** esa deuda no genera recordatorio y no se reporta error.
8. **Given** una deuda cuyo recordatorio ya fue enviado, **When** se vuelve a evaluar al día siguiente, **Then** no se envía un segundo correo por esa deuda.
9. **Given** una deuda pendiente de un perfil simulado del grupo con correo cargado, **When** llega la fecha de envío, **Then** el perfil simulado recibe el recordatorio en ese correo.
10. **Given** una deuda en "Pago informado" (grupo con validación de pagos) cuya fecha de envío llegó, **When** se generan los recordatorios, **Then** el deudor recibe el recordatorio con la indicación de que su pago está informado y pendiente de verificación ("pagado a verificar").

---

### User Story 2 - Personalizar tiempo de envío (Priority: P2)

El Administrador del grupo "Depto" elige, entre las opciones fijas (7, 5, 3 o 1 días antes del vencimiento), el momento en que se envían todos los recordatorios automáticos del grupo. El sistema calcula la fecha de envío a partir de la fecha límite de pago de cada deuda y envía automáticamente en esa fecha. Puede cambiar la configuración más adelante; el cambio aplica a los recordatorios futuros.

**Why this priority**: Sobre la base del envío automático (US1), permite al Administrador elegir el momento más adecuado para su grupo. No es necesaria para que los recordatorios funcionen: sin personalización rige el tiempo predeterminado.

**Independent Test**: Con los recordatorios ya funcionando (US1), el Administrador cambia el tiempo de envío de 5 a 7 días; la próxima deuda con fecha límite D programa su recordatorio para D − 7. Un recordatorio ya enviado no cambia, y uno programado aún no enviado se reprograma.

**Acceptance Scenarios**:

1. **Given** el Administrador en la configuración del grupo, **When** selecciona "7 días antes" y guarda, **Then** la configuración queda registrada y aplica a todos los recordatorios automáticos del grupo.
2. **Given** la configuración en "7 días antes" y una deuda nueva con fecha límite D, **When** se programa el recordatorio, **Then** la fecha de envío es D − 7.
3. **Given** un recordatorio programado para D − 5 aún no enviado, **When** el Administrador cambia a "3 días antes", **Then** el recordatorio se reprograma para D − 3.
4. **Given** un recordatorio ya enviado, **When** el Administrador cambia la configuración de tiempo, **Then** ese envío no cambia ni se reenvía.
5. **Given** una deuda verificada antes de la fecha programada, **When** llega la fecha programada, **Then** no se envía el recordatorio.
6. **Given** un grupo sin tiempo personalizado, **When** se generan recordatorios, **Then** se usa el tiempo predeterminado (5 días antes).
7. **Given** un miembro que no es Administrador del grupo, **When** intenta cambiar el tiempo de envío, **Then** la operación es rechazada.
8. **Given** una deuda creada a 2 días de su vencimiento con el tiempo configurado en 5 días, **When** se evalúa al día siguiente y el vencimiento aún no llegó, **Then** el recordatorio se envía en esa evaluación.

---

### User Story 3 - Avisar al pagador que se acerca el vencimiento (Priority: P2)

Ana pagó el gasto "Súper" del grupo "Depto" y Juan y Abuela le deben sus partes. En la misma fecha de envío de los recordatorios (5 días antes con la configuración predeterminada), Ana recibe un aviso: indica cuánto falta para el vencimiento del gasto y le dice si ella también tiene deudas pendientes en el grupo (con el monto total) o si no debe nada, según el caso.

**Why this priority**: Quien pagó quiere saber que se acerca la fecha en que debería recuperar su dinero y si, a la vez, también tiene deudas propias por pagar. Se apoya en el mecanismo de US1 (misma fecha de envío y misma configuración de tiempo) y aporta valor independiente sin requerir personalización.

**Independent Test**: Con los recordatorios funcionando (US1) y la configuración predeterminada, un gasto pagado por Ana con deudas pendientes y fecha límite D genera, en D − 5, un aviso a Ana con el tiempo restante para el vencimiento y su situación de deudas en el grupo; si todas las deudas del gasto están saldadas, Ana no recibe nada.

**Acceptance Scenarios**:

1. **Given** un gasto pagado por Ana con al menos una deuda pendiente y fecha límite D, **When** llega la fecha de envío (D − 5 con la configuración predeterminada), **Then** Ana recibe un aviso que indica cuánto falta para el vencimiento del gasto, identificando el grupo y el concepto.
2. **Given** Ana con deudas pendientes en el grupo, **When** recibe el aviso, **Then** el aviso indica que también debe, con el monto total pendiente.
3. **Given** Ana sin deudas pendientes en el grupo, **When** recibe el aviso, **Then** el aviso indica explícitamente que no debe nada.
4. **Given** un gasto cuyas deudas fueron todas saldadas o verificadas antes de la fecha de envío, **When** llega esa fecha, **Then** el pagador no recibe el aviso y los deudores no reciben recordatorios.
5. **Given** un aviso ya enviado para el gasto, **When** se vuelve a evaluar, **Then** no se envía un segundo aviso por ese gasto.
6. **Given** un aviso enviado, **When** se consulta el historial de actividad del grupo, **Then** el envío del aviso aparece registrado.
7. **Given** dos gastos pagados por Ana con la misma fecha límite, **When** llega la fecha de envío, **Then** Ana recibe un aviso por cada gasto.

---

### User Story 4 - Personalizar mensaje del recordatorio (Priority: P3)

El Administrador del grupo escribe un mensaje personalizado en un campo de texto de hasta 500 caracteres. El sistema valida la longitud antes de guardar. Los próximos recordatorios del grupo usan ese mensaje, que acompaña a los datos de la deuda (grupo, concepto, monto y fecha límite). Si no hay mensaje personalizado, se usa el predeterminado. El Administrador puede modificarlo cuando quiera o dejarlo en blanco para volver al predeterminado.

**Why this priority**: Personaliza la comunicación sobre un mecanismo ya funcional (US1). Sin ella, el recordatorio funciona igual con el mensaje predeterminado.

**Independent Test**: Con los recordatorios funcionando (US1), el Administrador guarda un mensaje de hasta 500 caracteres; el próximo recordatorio lo usa junto con los datos de la deuda. Un mensaje de más de 500 caracteres es rechazado al guardar.

**Acceptance Scenarios**:

1. **Given** el Administrador en la configuración del grupo, **When** guarda un mensaje de hasta 500 caracteres, **Then** la configuración queda registrada y los próximos recordatorios del grupo lo usan.
2. **Given** un mensaje personalizado guardado, **When** se envía el próximo recordatorio, **Then** el correo incluye el mensaje personalizado junto con el nombre del grupo, el concepto, el monto y la fecha límite.
3. **Given** un mensaje de más de 500 caracteres, **When** el Administrador intenta guardarlo, **Then** la operación es rechazada con un error de validación y la configuración anterior se conserva.
4. **Given** un mensaje en blanco, **When** el Administrador guarda, **Then** el grupo queda sin mensaje personalizado y se vuelve al mensaje predeterminado.
5. **Given** un recordatorio ya enviado, **When** el Administrador cambia el mensaje, **Then** ese envío ya realizado no cambia.
6. **Given** un miembro que no es Administrador del grupo, **When** intenta cambiar el mensaje, **Then** la operación es rechazada.
7. **Given** un miembro sin deudas pendientes, **When** se envían los recordatorios del grupo, **Then** no recibe ningún correo.

---

### Edge Cases

- **Deuda sin fecha límite**: el grupo no tiene días de vencimiento configurados (022) → la deuda no genera recordatorio ni error.
- **Fecha de envío ya pasada** (deuda creada con vencimiento más cercano que el tiempo configurado, o cambio de configuración sobre deudas existentes): el recordatorio se envía en la evaluación siguiente si la fecha límite aún no pasó; si la deuda ya está vencida, no se envía (el vencimiento ya queda registrado por el proceso existente de 022).
- **Cambio de configuración con recordatorios programados**: los aún no enviados se recalculan con la configuración vigente (misma regla de fecha pasada); los ya enviados no cambian.
- **Miembro que sale del grupo o deuda cancelada antes del envío**: la salida o remoción con deudas sin saldar está bloqueada (017); si la deuda se salda (paga o verifica) antes del envío, el recordatorio se cancela. El dominio actual no tiene cancelación ni condonación de deudas, por lo que "deuda cancelada" se cubre con saldarla.
- **Miembro sin correo válido o fallo de entrega**: el fallo queda registrado, no bloquea los demás recordatorios del grupo y no se reintenta (un único intento por deuda).
- **Mensaje vacío o que excede la longitud máxima**: vacío → vuelve al mensaje predeterminado; más de 500 caracteres → rechazado al guardar con error de validación.
- **Deuda en "Pago informado" al llegar la fecha de envío**: recibe el recordatorio (solo Pagado y Verificado excluyen), con la indicación "pagado a verificar"; si el pago se verifica después del envío, no hay reenvío.
- **Perfil simulado con deuda**: recibe el recordatorio en el correo cargado por el Administrador; si el correo es inválido o la entrega falla, aplica la regla de fallo de entrega (queda registrado, sin reintento y sin bloquear los demás envíos).
- **Gasto sin deudas pendientes al llegar la fecha de envío**: no se envía el aviso al pagador (ni recordatorios a los deudores).
- **Gasto de un grupo sin días de vencimiento**: no genera recordatorios ni aviso al pagador.

## Requirements *(mandatory)*

### Functional Requirements

**Configuración por grupo (US2 y US4)**

- **FR-001**: El sistema MUST mantener una única configuración de recordatorios por grupo, compuesta por el tiempo de envío y el mensaje. Mientras el Administrador no la personalice, MUST aplicarse la configuración predeterminada: 5 días antes del vencimiento y mensaje predeterminado.
- **FR-002**: El tiempo de envío MUST ser una única opción elegida de la lista fija de 7 días, 5 días, 3 días o 1 día antes del vencimiento (incluye como mínimo una semana y cinco días antes). La opción elegida aplica a todos los recordatorios automáticos del grupo.
- **FR-003**: La configuración MUST poder modificarse en cualquier momento. Un cambio MUST aplicar solo a los recordatorios futuros: los ya enviados no cambian y los programados aún no enviados se recalculan con la configuración vigente.
- **FR-004**: Solo el Administrador del grupo MUST poder consultar y modificar la configuración de recordatorios; la operación MUST ser rechazada para cualquier otro miembro.
- **FR-005**: El mensaje personalizado MUST ingresarse en un campo de texto con longitud máxima de 500 caracteres, validada antes de guardar: un mensaje que la excede MUST ser rechazado, y un mensaje en blanco MUST dejar al grupo sin mensaje personalizado (vuelve al predeterminado).

**Generación y envío (US1)**

- **FR-006**: Al generar los recordatorios, el sistema MUST identificar qué miembros del grupo tienen deudas pendientes de pago (no saldadas ni vencidas) con fecha límite definida. Solo las deudas saldadas (Pagado o Verificado) quedan excluidas; las deudas en "Pago informado" (pago reportado aún no verificado) MUST recibir el recordatorio hasta que se salden o verifiquen (aclaración del 2026-11-07).
- **FR-007**: La fecha de envío MUST calcularse como la fecha límite de pago de la deuda menos el tiempo de envío configurado. La fecha límite de pago es la fecha del gasto originario más los días de vencimiento del grupo (022); una deuda de un grupo sin días de vencimiento MUST NOT generar recordatorio.
- **FR-008**: El recordatorio MUST enviarse por correo electrónico al miembro deudor con deuda pendiente, en la fecha de envío calculada. Los destinatarios son todos los deudores con correo: miembros con cuenta y perfiles simulados (cuyo correo cargó el Administrador al agregarlos).
- **FR-009**: Si la fecha de envío calculada ya pasó y la fecha límite aún no pasó, el recordatorio MUST enviarse en la evaluación siguiente; si la deuda ya está vencida, MUST NOT enviarse.
- **FR-010**: MUST enviarse un único recordatorio por deuda según la configuración; el sistema MUST impedir reenvíos y duplicados para la misma deuda.
- **FR-011**: No MUST enviarse recordatorio por deudas saldadas (Pagado o Verificado). Si la deuda se salda o se verifica antes de la fecha programada, el recordatorio MUST cancelarse.
- **FR-012**: El correo MUST incluir la información suficiente para identificar la deuda y su grupo: nombre del grupo, concepto del gasto originario, monto y fecha límite de pago. El mensaje personalizado acompaña a esos datos; el mensaje predeterminado los incluye. Si la deuda está en "Pago informado" al momento del envío, el correo MUST indicarlo (pago informado, pendiente de verificación, estilo "pagado a verificar") en lugar de solicitar el pago como si no existiera (aclaración del 2026-11-07).
- **FR-013**: El envío de cada recordatorio MUST registrarse en el historial de actividad del grupo.
- **FR-014**: Un fallo de entrega o un correo electrónico inválido MUST quedar registrado sin impedir el envío de los demás recordatorios, y MUST NOT reintentarse.

**Aviso al pagador (US3)**

- **FR-016**: En la fecha de envío calculada para las deudas de un gasto, el sistema MUST enviar por correo electrónico al miembro que pagó el gasto un aviso que indique cuánto falta para el vencimiento del gasto, siempre que el gasto tenga al menos una deuda pendiente de pago (no saldada ni vencida) en ese momento. El aviso MUST identificar el grupo y el concepto del gasto.
- **FR-017**: El aviso al pagador MUST indicar si el pagador tiene deudas pendientes en el grupo y, si las tiene, el monto total pendiente; si no tiene, MUST indicarlo explícitamente. La parte del pagador en su propio gasto no cuenta como deuda (quedó cubierta al pagarlo).
- **FR-018**: El aviso al pagador MUST seguir las mismas reglas que el recordatorio de deudas: único por gasto, sin reenvíos; mismas reglas de fecha (FR-007 y FR-009); sin envío si todas las deudas del gasto se saldan antes; registrado en el historial de actividad del grupo; y con fallos de entrega tratados según FR-014. Usa un texto predeterminado propio: el mensaje personalizado de FR-005 no le aplica.

**Interfaz del Administrador (US2 y US4)**

- **FR-019**: El Administrador MUST poder ver y editar la configuración de recordatorios desde la configuración del grupo, con las opciones de tiempo de FR-002 y el campo de texto de FR-005, y con los errores de validación informados en el formulario.

### Key Entities *(include if feature involves data)*

- **Configuración de recordatorios del grupo**: única por grupo. Tiene tiempo de envío (una opción de la lista fija; predeterminado: 5 días antes) y mensaje personalizado (hasta 500 caracteres, opcional). Solo el Administrador la modifica; los valores predeterminados aplican mientras no exista personalización.
- **Recordatorio de deuda**: uno por deuda. Tiene fecha de envío calculada, estado (programado, enviado, cancelado o fallido), la deuda y el grupo asociados, y el contenido con el que se envió o se enviaría (los ya enviados no cambian ante cambios de configuración).
- **Deuda** (existente, 017/022): obligación de un miembro (deudor) con otro (acreedor) nacida de un gasto de grupo, con monto, moneda y estado (Pendiente, Pago informado, Pagado, Verificado, Vencido). El recordatorio se asocia a la deuda y a su grupo.
- **Grupo** (existente, 010/022): define los días de vencimiento de las deudas que determinan la fecha límite de pago; sin días de vencimiento no hay fecha límite ni recordatorios.
- **Historial de actividad del grupo** (existente, 014): registra el envío de cada recordatorio como evento del grupo.
- **Aviso de vencimiento al pagador**: uno por gasto con fecha límite. Se envía al miembro que pagó el gasto en la misma fecha que los recordatorios de sus deudas; indica el tiempo restante para el vencimiento y si el pagador tiene deudas pendientes en el grupo. Comparte las reglas y estados del recordatorio (programado, enviado, cancelado, fallido).

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: El 100 % de las deudas elegibles recibe exactamente un recordatorio, en la fecha de envío calculada o en la evaluación inmediata siguiente si esa fecha ya pasó; cero reenvíos por deuda.
- **SC-002**: Cero recordatorios enviados por deudas saldadas o verificadas antes del envío.
- **SC-003**: El 100 % de los grupos tiene recordatorios operativos sin ninguna acción de configuración del Administrador (configuración predeterminada).
- **SC-004**: El 100 % de los correos de recordatorio identifica el grupo, el concepto, el monto y la fecha límite sin abrir la aplicación.
- **SC-005**: Un Administrador completa el cambio de tiempo de envío o del mensaje en menos de 1 minuto.
- **SC-006**: El 100 % de los recordatorios enviados queda registrado en el historial de actividad del grupo.
- **SC-007**: El 100 % de los gastos con al menos una deuda pendiente al llegar la fecha de envío genera exactamente un aviso al pagador, con el tiempo restante para el vencimiento y la situación de deudas del pagador en el grupo.

## Assumptions

- La fecha límite de pago de una deuda es la fecha del gasto originario más los días de vencimiento del grupo (definición de 022). Un grupo sin días de vencimiento no genera fecha límite y, por lo tanto, no genera recordatorios.
- Lista fija de tiempos de envío: 7, 5, 3 y 1 días antes (ajustable; como mínimo incluye una semana y cinco días). Tiempo predeterminado: 5 días antes. Las opciones se expresan en días (la entrada admite días u horas; se adoptan días).
- Se envía un correo por deuda: no se agrupan en un mismo correo varias deudas del mismo miembro.
- El mensaje personalizado acompaña a los datos de identificación (grupo, concepto, monto y fecha límite), que siempre están presentes; no los reemplaza.
- Un mensaje en blanco equivale a "sin mensaje personalizado" (se usa el predeterminado).
- La entrega se intenta una sola vez por recordatorio; los fallos se registran y no se reintentan.
- Los usuarios con cuenta tienen su correo verificado (003); los perfiles simulados tienen el correo cargado por quien los agregó al grupo y también reciben los recordatorios (aclaración del 2026-11-07).
- Un miembro con deudas sin saldar no puede abandonar el grupo ni ser removido (017), por lo que el destinatario sigue siendo miembro al momento del envío.
- La evaluación de recordatorios es periódica y diaria, como el proceso de vencimiento existente (022); la fecha de envío se interpreta a nivel de día, sin hora específica.
- Se reutiliza la infraestructura de envío de correo electrónico existente (verificación de cuenta y recuperación de contraseña); no se agrega una dependencia nueva.
- Solo el deudor recibe el recordatorio; el acreedor no recibe copia.
- Los textos para el usuario están en español con voseo (convención del proyecto).
- Configurar los días de vencimiento del grupo sigue siendo responsabilidad del Administrador en la configuración existente de 022; esta feature no lo altera.
- El aviso al pagador se envía por gasto: si un miembro pagó varios gastos con la misma fecha límite, recibe un aviso por cada uno (igual que los deudores reciben un correo por deuda).
- El "si debe o no" del aviso al pagador refiere a las deudas pendientes del pagador en ese grupo (se informa el monto total pendiente); su parte del propio gasto quedó cubierta al pagarlo y nunca es una deuda.
- El aviso al pagador usa un texto predeterminado propio; el mensaje personalizado de la configuración no le aplica.
- El pagador de un gasto siempre es un miembro con cuenta (017: los perfiles simulados no pueden pagar), por lo que el aviso al pagador no tiene el caso del correo de un perfil simulado.
- Para el aviso al pagador, las deudas en "Pago informado" cuentan como pendientes (no saldadas), igual que para los recordatorios: integran el monto total pendiente informado al pagador.

## Clarifications

**Session 2026-11-07**

- Q: ¿Los perfiles simulados (miembros sin cuenta) con deuda pendiente reciben el recordatorio por correo? → A: Sí. Todos los deudores con correo reciben el recordatorio, incluidos los perfiles simulados: el correo es la única vía por la que una persona simulada se entera de que debe, ya que no usa la aplicación. Un correo inválido o una entrega fallida siguen la regla general (se registra el fallo, sin reintento).
- Q: ¿Una deuda con pago informado (reportado pero aún no verificado) recibe el recordatorio? → A: Sí (opción A, refinada). En grupos sin validación de pagos, el pago pasa directo a Pagado y la deuda no recibe recordatorio. En grupos con validación, el pago debe estar Verificado para excluir el recordatorio; una deuda pagada pero no verificada ("Pago informado") recibe el correo igual, con una indicación del estilo "pagado a verificar" (pago informado, pendiente de verificación). Si se verifica después del envío, no hay reenvío.
- Q: Pedido adicional sobre el pagador del gasto → A: Se agrega la US3. En la misma fecha de envío de los recordatorios de un gasto, el miembro que lo pagó recibe un aviso que indica cuánto falta para el vencimiento del gasto y si él también tiene deudas pendientes en el grupo (monto total) o no, según el caso. El aviso es único por gasto, usa un texto predeterminado propio y sigue las mismas reglas de fechas, cancelación y fallos de entrega que los recordatorios.
