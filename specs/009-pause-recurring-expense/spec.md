# Feature Specification: Pausar o finalizar un gasto recurrente

**Feature Branch**: `feat/definir-tipo-gasto`

**Created**: 2026-10-24

**Status**: Draft

**Input**: User description: "Pausar o frenar un gasto recurrente: pausar o finalizar la generación automática periódica de un gasto. Definir cómo se modela el estado de un gasto recurrente (ACTIVE, PAUSED, FINISHED); si la entidad de gastos no tiene lógica de recurrencia, definirla junto con su estado."

## Relación con otras especificaciones

- **Extiende 007 (Registro de gasto personal)**: la entidad `Expense`, la tabla `expenses`, `POST /api/expenses` y el formulario de nuevo gasto ya existen. En 007 "Recurrente" es **solo una clasificación** (FR-010 de 007: "MUST NOT generar gastos futuros ni exigir una frecuencia"). Esta especificación **reemplaza esa regla para los gastos recurrentes nuevos**: un gasto "Recurrente" pasa a exigir una frecuencia y a generar sus repeticiones de forma automática, porque sin generación periódica no hay nada que pausar ni finalizar.
- **Conserva de 007**: todas las demás validaciones del formulario y del backend (monto, moneda, fecha no futura, categoría, medio de pago, descripción), la clave de idempotencia por operación, la sesión verificada y el formato de errores.
- **No modifica 008 (Buscador de usuarios)** ni las features de cuenta (001–006).
- Los gastos **"Recurrente" registrados antes de esta feature** (sin frecuencia) se conservan tal cual, como clasificación: no se les inventa una frecuencia ni generan repeticiones (ver FR-013).

Los identificadores FR-### y SC-### de esta especificación son propios de 009; toda referencia a otra spec se cualifica (por ejemplo, "FR-010 de 007").

## Clarifications

### Session 2026-10-24

- Q: La tarea pide pausar la "generación automática periódica", pero hoy un gasto "Recurrente" no genera nada. ¿Se implementa la recurrencia o solo el estado? → A: Se implementa la recurrencia mínima necesaria (frecuencia + generación automática) junto con el estado, como lo prevé el pedido.
- Q: ¿Qué frecuencias se ofrecen? → A: Semanal, Mensual y Anual.
- Q: Si el gasto se registra con una fecha pasada (por ejemplo, el alquiler del 1° de enero registrado en octubre), ¿se generan las repeticiones de los meses intermedios? → A: No. La generación nunca es retroactiva: la primera repetición es la primera fecha de la serie posterior al día del registro.
- Q: Al reanudar una serie pausada, ¿se generan las repeticiones que se "perdieron" durante la pausa? → A: No. Pausar significa que esas repeticiones no corresponden; al reanudar, la serie continúa desde la próxima fecha de la serie que sea hoy o posterior.
- Q: ¿Finalizar es reversible? → A: No. Una serie finalizada no puede reanudarse; para retomarla se registra un gasto recurrente nuevo. Los gastos ya generados se conservan.
- Q: ¿Dónde se pausa o finaliza? → A: En una pantalla nueva "Gastos recurrentes", accesible desde la barra de navegación, que lista las series del usuario con su estado y su próxima fecha.
- Q: Pedir el mismo estado que ya tiene la serie (por ejemplo, pausar una serie pausada por un doble clic), ¿es un error? → A: No: se responde como éxito sin cambios. Solo es error intentar modificar una serie finalizada.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Registrar un gasto recurrente con frecuencia (Priority: P1)

Al elegir el tipo "Recurrente" en el formulario de nuevo gasto, aparece el campo obligatorio "Frecuencia" (Semanal, Mensual o Anual). Al guardar, el gasto queda registrado como la primera repetición de una serie **activa** y el sistema calcula cuándo será la próxima.

**Why this priority**: es el prerrequisito de toda la feature: sin una serie con frecuencia no existe generación periódica que pausar o finalizar.

**Independent Test**: registrar un gasto "Recurrente" mensual con fecha de hoy y verificar que queda activo con la próxima fecha dentro de un mes; registrar uno "Único" y verificar que no pide ni acepta frecuencia.

**Acceptance Scenarios**:

1. **Given** el formulario de nuevo gasto, **When** se elige el tipo "Recurrente", **Then** aparece el campo "Frecuencia" con las opciones Semanal, Mensual y Anual; **When** se elige "Único", **Then** el campo no se muestra.
2. **Given** un gasto "Recurrente" sin frecuencia, **When** se intenta guardar, **Then** se muestra "La frecuencia es obligatoria para un gasto recurrente" y no se guarda nada.
3. **Given** un gasto "Recurrente" mensual con fecha 24/10/2026 registrado el 24/10/2026, **When** se guarda, **Then** el gasto queda como serie activa con próxima fecha 24/11/2026.
4. **Given** un gasto "Recurrente" mensual con fecha 01/01/2026 registrado el 24/10/2026, **When** se guarda, **Then** la próxima fecha es 01/11/2026 y no se generan repeticiones de los meses anteriores.
5. **Given** una petición manipulada con tipo "Único" y una frecuencia, **When** llega al servidor, **Then** se rechaza con "Un gasto único no puede tener frecuencia" sin guardar.

---

### User Story 2 - Generación automática de las repeticiones (Priority: P1)

Todos los días el sistema revisa las series activas y, para cada una cuya próxima fecha ya llegó, registra automáticamente el gasto correspondiente a esa fecha (mismos datos que el original) y avanza la próxima fecha.

**Why this priority**: es la "generación automática periódica" que la tarea pide controlar.

**Independent Test**: con una serie activa cuya próxima fecha es hoy, ejecutar la generación y verificar que aparece un gasto nuevo con la fecha de hoy y los datos del original, y que la próxima fecha avanzó un período.

**Acceptance Scenarios**:

1. **Given** una serie activa con próxima fecha hoy, **When** corre la generación diaria, **Then** se registra un gasto con fecha de hoy, mismo monto, moneda, categoría, medio de pago, descripción y dueño, de tipo "Recurrente" y vinculado a la serie; la próxima fecha avanza un período.
2. **Given** una serie mensual iniciada el 31/01, **When** se generan sus repeticiones, **Then** las fechas son 28/02 (o 29/02 en año bisiesto), 31/03, 30/04, 31/05… (cada mes toma el día de inicio o el último día del mes).
3. **Given** que la generación no corrió durante varios días (por ejemplo, servidor apagado) y una serie activa quedó con fechas vencidas, **When** vuelve a correr, **Then** se registran todas las repeticiones vencidas, cada una con su fecha.
4. **Given** que la generación corre dos veces el mismo día, **When** termina la segunda, **Then** no hay repeticiones duplicadas.

---

### User Story 3 - Ver mis gastos recurrentes y pausarlos o reanudarlos (Priority: P1)

Desde "Gastos recurrentes" en la barra de navegación, la persona ve sus series con monto, categoría, descripción, frecuencia, estado y próxima fecha. Una serie activa ofrece "Pausar"; una pausada ofrece "Reanudar". Mientras está pausada, no se generan repeticiones.

**Why this priority**: es el núcleo de la tarea asignada ("pausar la generación automática periódica").

**Independent Test**: pausar una serie activa, ejecutar la generación con su próxima fecha vencida y verificar que no se generó nada; reanudarla y verificar que la próxima fecha es hoy o posterior.

**Acceptance Scenarios**:

1. **Given** una serie activa, **When** se pulsa "Pausar", **Then** el botón muestra el estado de carga, la serie pasa a "Pausado", se muestra "El gasto recurrente se pausó" y ahora ofrece "Reanudar" y "Finalizar".
2. **Given** una serie pausada con su próxima fecha vencida, **When** corre la generación, **Then** no se registra ningún gasto para esa serie.
3. **Given** una serie mensual iniciada el 05/08 y pausada desde septiembre, **When** se reanuda el 24/10, **Then** pasa a "Activo" con próxima fecha 05/11 (no se generan las de septiembre ni octubre) y se muestra "El gasto recurrente se reanudó".
4. **Given** una serie mensual iniciada el 24/09, pausada, **When** se reanuda el 24/10, **Then** la próxima fecha es 24/10 (hoy) y la próxima generación registra ese gasto.
5. **Given** una persona sin series, **When** abre "Gastos recurrentes", **Then** ve "Todavía no tenés gastos recurrentes".

---

### User Story 4 - Finalizar un gasto recurrente (Priority: P1)

Una serie activa o pausada ofrece "Finalizar". Tras confirmar, la serie pasa a "Finalizado": no genera más repeticiones, no tiene próxima fecha y no puede reanudarse. Los gastos ya registrados se conservan.

**Why this priority**: es la segunda mitad de la tarea ("frenar/finalizar la generación").

**Independent Test**: finalizar una serie, ejecutar la generación y verificar que no genera nada; intentar reanudarla y verificar el rechazo.

**Acceptance Scenarios**:

1. **Given** una serie activa o pausada, **When** se pulsa "Finalizar", **Then** se pide confirmación ("Finalizar es definitivo: no se generarán más gastos de esta serie"); **When** se cancela, **Then** no cambia nada.
2. **Given** la confirmación, **When** se confirma, **Then** la serie pasa a "Finalizado", pierde la próxima fecha, no muestra más acciones y se muestra "El gasto recurrente se finalizó".
3. **Given** una serie finalizada, **When** corre la generación, **Then** no se registra nada para esa serie.
4. **Given** una serie finalizada, **When** se intenta pausarla o reanudarla (por ejemplo, con una petición manual), **Then** se rechaza con "El gasto recurrente ya está finalizado y no puede modificarse".
5. **Given** una serie finalizada, **When** se consultan los datos, **Then** el gasto original y sus repeticiones ya generadas siguen registrados.

---

### Edge Cases

- **Mismo estado** (doble clic en "Pausar", reanudar algo activo, finalizar algo finalizado): éxito sin cambios.
- **Serie ajena, inexistente, gasto único, repetición generada o gasto recurrente anterior a 009**: el cambio de estado responde "No se encontró el gasto recurrente", sin revelar si existe ni de quién es.
- **Estado ausente o desconocido** en la petición: error de campo ("El estado es obligatorio" / "El estado no es válido").
- **Día del mes inexistente**: las series mensuales iniciadas el 29, 30 o 31 usan el último día de los meses más cortos; las anuales del 29/02 usan el 28/02 en años no bisiestos. La serie nunca "se corre": el mes siguiente vuelve al día de inicio.
- **Gasto recurrente de 007 sin frecuencia**: se sigue mostrando como clasificación donde corresponda, pero no aparece en "Gastos recurrentes" ni genera repeticiones.
- **Reintento idempotente del registro (007)**: la frecuencia forma parte de los datos comparados; repetir la operación con otra frecuencia es un conflicto de clave reutilizada.
- **Sesión expirada** al pausar/finalizar: se informa "Tu sesión expiró. Iniciá sesión nuevamente." con acceso al login.
- **Error de red o del servidor**: al cargar la lista, "No se pudieron cargar tus gastos recurrentes. Intentá nuevamente" con opción de reintentar; al cambiar el estado, "No se pudo actualizar el gasto recurrente. Intentá nuevamente" y la serie conserva su estado anterior en pantalla.
- **Conflicto** (la serie cambió en otra pestaña y ya está finalizada): se muestra el mensaje del servidor y se recarga la lista.

## Requirements *(mandatory)*

### Functional Requirements

**Recurrencia (US1, US2)**

- **FR-001**: Un gasto de tipo "Recurrente" registrado a partir de esta feature MUST indicar una frecuencia: Semanal, Mensual o Anual. El formulario MUST mostrar el campo "Frecuencia" solo cuando el tipo es "Recurrente" y MUST exigirlo en ese caso ("La frecuencia es obligatoria para un gasto recurrente"). Un valor fuera del catálogo MUST rechazarse ("La frecuencia no es válida").
- **FR-002**: Un gasto de tipo "Único" MUST NOT tener frecuencia; si la petición la informa, MUST rechazarse ("Un gasto único no puede tener frecuencia") sin guardar.
- **FR-003**: El gasto recurrente registrado MUST ser la primera repetición de una **serie** en estado Activo. Su próxima fecha MUST ser la primera fecha de la serie estrictamente posterior al día del registro; MUST NOT generarse repeticiones retroactivas.
- **FR-004**: Las fechas de la serie MUST calcularse siempre desde la fecha de inicio (inicio + n períodos), usando el último día del mes cuando el día de inicio no existe en ese mes, de modo que la serie no se desplace.
- **FR-005**: El sistema MUST ejecutar una generación automática diaria que, para cada serie Activa con próxima fecha igual o anterior a hoy (zona horaria configurable, por defecto `America/Argentina/Buenos_Aires`), registre un gasto por cada fecha vencida con el mismo dueño, monto, moneda, categoría, medio de pago y descripción, de tipo "Recurrente", vinculado a la serie, y avance la próxima fecha.
- **FR-006**: La generación MUST ser idempotente: MUST NOT existir dos repeticiones de la misma serie con la misma fecha, aunque la generación corra más de una vez.

**Estado (US3, US4)**

- **FR-007**: Una serie MUST tener uno de tres estados: Activo, Pausado o Finalizado. Solo las series Activas generan repeticiones.
- **FR-008**: Pausar MUST conservar la serie y su próxima fecha, sin generar nada mientras dure la pausa. Reanudar MUST fijar como próxima fecha la primera fecha de la serie igual o posterior a hoy; las fechas transcurridas durante la pausa MUST NOT generarse.
- **FR-009**: Finalizar MUST ser definitivo: la serie MUST dejar de generar, MUST perder su próxima fecha y MUST NOT poder volver a Activo ni a Pausado ("El gasto recurrente ya está finalizado y no puede modificarse"). El gasto original y las repeticiones ya generadas MUST conservarse.
- **FR-010**: Las transiciones válidas MUST ser Activo → Pausado, Pausado → Activo y Activo/Pausado → Finalizado. Pedir el estado que la serie ya tiene MUST responder éxito sin cambios.
- **FR-011**: Solo el dueño MUST poder ver o cambiar el estado de sus series. Una serie ajena o inexistente, un gasto único, una repetición generada o un gasto recurrente sin frecuencia MUST responder "No se encontró el gasto recurrente", sin revelar su existencia.

**Interfaz (US3, US4)**

- **FR-012**: La interfaz MUST ofrecer una pantalla protegida "Gastos recurrentes", accesible desde la barra de navegación, que liste las series del usuario (más recientes primero) con monto, moneda, categoría, descripción, frecuencia, estado y próxima fecha (dd/mm/aaaa). Cada serie MUST ofrecer solo las acciones válidas para su estado: Activo → "Pausar" y "Finalizar"; Pausado → "Reanudar" y "Finalizar"; Finalizado → ninguna.
- **FR-013**: "Finalizar" MUST pedir confirmación antes de enviarse. Mientras un cambio está en curso, las acciones de esa serie MUST quedar deshabilitadas con estado de carga; al terminar con éxito MUST mostrarse "El gasto recurrente se pausó", "…se reanudó" o "…se finalizó" y la serie MUST reflejar su nuevo estado.
- **FR-014**: Ante sesión expirada, error de red o del servidor, o conflicto, la interfaz MUST mostrar el mensaje correspondiente (ver Edge Cases) sin mostrar un éxito falso.

**Compatibilidad y seguridad**

- **FR-015**: Los gastos "Recurrente" existentes sin frecuencia MUST conservarse sin cambios: MUST NOT asignárseles una frecuencia ni un estado, MUST NOT generar repeticiones y MUST NOT aparecer en "Gastos recurrentes".
- **FR-016**: Todas las operaciones MUST requerir una sesión de una cuenta verificada (regla de 003 para `/api/expenses/**`) y MUST responder errores en el formato del proyecto, en español y sin detalles internos.

### Key Entities *(include if feature involves data)*

- **Gasto (existente, 007)**: registro personal con monto, moneda, fecha, categoría, tipo, medio de pago y descripción. Esta feature le agrega, para los recurrentes, la frecuencia, el estado de la serie, la próxima fecha y el vínculo de una repetición con su serie.
- **Serie recurrente**: el gasto recurrente registrado por la persona; define la frecuencia y la fecha de inicio, tiene un estado (Activo, Pausado, Finalizado) y una próxima fecha mientras no está finalizada.
- **Repetición**: gasto registrado automáticamente a partir de una serie para una fecha de la serie; es un gasto común del mismo dueño, vinculado a su serie, que no tiene estado propio ni genera otros gastos.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: El 100 % de los gastos recurrentes nuevos queda registrado con una frecuencia y en estado Activo; el 0 % de los gastos únicos tiene frecuencia.
- **SC-002**: Para una serie Activa, la generación diaria registra exactamente una repetición por cada fecha vencida de la serie, con 0 duplicados aunque corra varias veces el mismo día.
- **SC-003**: El 0 % de las series Pausadas o Finalizadas genera repeticiones.
- **SC-004**: Una persona pausa, reanuda o finaliza una serie en menos de 10 segundos desde que abre "Gastos recurrentes", y ve el nuevo estado sin recargar la página.
- **SC-005**: El 100 % de los intentos de modificar una serie Finalizada es rechazado, y el 100 % de los intentos sobre series ajenas responde igual que una serie inexistente.
- **SC-006**: El 0 % de los gastos recurrentes de 007 sin frecuencia es alterado por la migración o por la generación.

## Assumptions

- Esta especificación documenta el comportamiento implementado en la rama `feat/definir-tipo-gasto`; cualquier divergencia con el código se resuelve como defecto o como cambio de alcance explícito.
- Los textos de la interfaz de gastos usan voseo, en línea con 007 ("Iniciá sesión", "Reintentá").
- La generación corre una vez por día, de madrugada (configurable); un gasto cuya fecha llega hoy puede aparecer recién después de esa corrida.
- **Fuera de alcance**: listado o historial completo de gastos, edición o eliminación de gastos o series, fecha de fin programada, otras frecuencias (diaria, quincenal, personalizada), notificaciones al generar, y gastos recurrentes compartidos o de grupo.
- La cobertura mínima del 85 % (backend y frontend) exigida por la constitución se verifica con `mvn verify` (JaCoCo) y `npm run test:coverage` (Vitest).
