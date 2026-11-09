# Feature Specification: Gastos compartidos recurrentes

**Feature Branch**: `feat/envio-recordatorio-deudas`

**Created**: 2026-11-08

**Status**: Implementada, pendiente de prueba manual

**Input**: User description: "Quiero que registrar un gasto recurrente en un grupo permita elegir la frecuencia, igual que en el registro de gasto personal recurrente, para que el gasto se repita y se generen las deudas de cada período (y así probar los recordatorios de 025)."

## Relación con otras especificaciones

- **009 (gastos personales recurrentes)**: se reutilizan el catálogo de frecuencias (`WEEKLY`, `MONTHLY`, `YEARLY`), la semántica de fechas ancladas al inicio y la generación diaria idempotente de ocurrencias.
- **017 (gastos de grupo)**: la serie conserva las reglas de creación del gasto compartido (pagador, participantes, reparto, conversión e idempotencia).
- **022 (deudas y vencimientos)**: cada ocurrencia genera sus propias deudas y, por lo tanto, su propio vencimiento según `debt_due_days` del grupo.
- **025 (recordatorios)**: los recordatorios se calculan por deuda; las deudas de cada ocurrencia reciben recordatorios sin cambios adicionales.
- **027 (formas de registrar un gasto)**: antes, `RECURRING` en un gasto compartido era solo informativo. Esta spec cambia ese comportamiento solo para gastos de grupo; un gasto con amigo sigue sin repetirse.

Donde esta spec y una anterior difieran, vale esta.

## Fuera de alcance

- Pausar, reanudar o finalizar una serie de gastos compartidos desde la UI.
- Editar la frecuencia de una serie ya creada.
- Recurrencia de gastos con amigo.
- Recalcular cotizaciones históricas para cada ocurrencia: la ocurrencia conserva los datos de conversión de la serie.
- Generar ocurrencias anteriores a la fecha de registro de la serie.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Registrar un gasto de grupo recurrente con frecuencia (Priority: P1)

Ana registra un gasto en el grupo "Depto", marca el tipo "Recurrente" y elige "Mensual". El sistema guarda el gasto como serie activa y, cuando llega la próxima fecha mensual, crea automáticamente la siguiente ocurrencia con el mismo reparto, pagador, categoría y medio de pago, generando las deudas de ese nuevo período. Cada ocurrencia queda disponible para el flujo de deudas y recordatorios de 022/025.

**Why this priority**: es el objetivo central: que el gasto recurrente de grupo se comporte como el gasto personal recurrente y permita probar recordatorios sobre períodos futuros.

**Independent Test**: registrar un gasto mensual de $10.000 pagado por Ana con Juan como participante; al llegar la fecha del mes siguiente, existe una nueva ocurrencia de $10.000, con la misma división, una deuda `PENDING` de Juan y el evento de gasto registrado en el historial.

**Acceptance Scenarios**:

1. **Given** el formulario de gasto de un grupo, **When** el usuario elige tipo "Recurrente", **Then** aparece el selector "Frecuencia" con las opciones "Semanal", "Mensual" y "Anual".
2. **Given** el formulario con tipo "Recurrente", **When** el usuario intenta guardar sin elegir frecuencia, **Then** ve "La frecuencia es obligatoria" y no se guarda nada.
3. **Given** el formulario con tipo "Único", **When** se muestra, **Then** no aparece el selector "Frecuencia" y el backend rechaza una frecuencia enviada.
4. **Given** un alta de grupo con tipo "Recurrente" y frecuencia "Mensual", **When** se guarda, **Then** responde `201`, la fila queda con `recurrence_frequency = MONTHLY`, `recurrence_status = ACTIVE` y `recurrence_next_date` igual al primer período posterior a la fecha del gasto.
5. **Given** una serie activa cuya próxima fecha es D, **When** corre el job con fecha D o posterior, **Then** se crea una ocurrencia con fecha D, el mismo monto, moneda, pagador, participantes, reparto, categoría, tipo, medio de pago y descripción, y se enlaza con `recurrence_source_id`.
6. **Given** la ocurrencia creada, **When** se consultan sus deudas, **Then** tiene deudas `PENDING` por cada participante distinto del pagador, con la misma división de la serie.
7. **Given** una ocurrencia creada, **When** se consulta el historial, **Then** aparece un evento `EXPENSE_CREATED` para esa ocurrencia.
8. **Given** el job ejecutado dos veces para la misma fecha, **When** se vuelve a ejecutar, **Then** no crea duplicados: hay como máximo una ocurrencia por serie y fecha.
9. **Given** una serie atrasada varios períodos, **When** corre el job, **Then** genera una ocurrencia por cada fecha vencida hasta hoy, sin superar el límite de seguridad de 1000 por serie y ejecución.
10. **Given** una ocurrencia con deudas y un grupo con `debt_due_days` configurado, **When** corre 025, **Then** calcula los recordatorios usando la fecha de la ocurrencia, sin cambios adicionales.

### User Story 2 - Mantener las reglas del gasto compartido (Priority: P2)

La serie recurrente no relaja las reglas del gasto de grupo: respeta permisos, participantes, reparto y moneda. Las ocurrencias se generan como sistema, sin pedir permisos otra vez, y conservan los datos con los que se creó la serie.

**Why this priority**: evita que la repetición automática saltee validaciones del flujo de gastos compartidos.

**Independent Test**: crear una serie con reparto personalizado y moneda distinta a la del grupo; la ocurrencia conserva las partes y la conversión de la serie, y un miembro sin permiso para crear gastos no puede crear la serie.

**Acceptance Scenarios**:

1. **Given** un grupo con permisos que impiden crear gastos, **When** un miembro intenta crear una serie, **Then** la operación se rechaza con el mismo error del alta de gasto de grupo.
2. **Given** una serie creada con reparto personalizado, **When** se genera una ocurrencia, **Then** sus partes y deudas respetan esos importes.
3. **Given** una serie en una moneda distinta a la del grupo, **When** se genera una ocurrencia, **Then** se copian el monto convertido, la cotización y la fuente registrados en la serie.
4. **Given** una serie cuyo único participante es el pagador, **When** se genera una ocurrencia, **Then** se crea el gasto sin deudas.
5. **Given** un gasto con amigo, **When** se envía una frecuencia, **Then** la API responde `400` y no se crea una serie.

### User Story 3 - Calendario de vencimientos y recordatorios (Priority: P1)

Un miembro registra hoy un gasto recurrente en un grupo. Ese gasto es la primera ocurrencia y no se genera otra para el mismo día. La primera ocurrencia vence como un gasto normal del grupo y sus recordatorios se calculan desde el vencimiento vigente; cada ocurrencia posterior se genera en su propia fecha, con sus deudas y su propio vencimiento.

**Why this priority**: cierra el calendario completo de la recurrencia: primer pago, vencimientos y recordatorios por período sin duplicar el gasto.

**Independent Test**: con un grupo con 7 días de plazo y recordatorio de 2 días antes, registrar hoy un gasto recurrente y validar el vencimiento y el recordatorio de la primera y segunda ocurrencia.

**Acceptance Scenarios**:

1. **Given** un usuario registra hoy un gasto de grupo recurrente, **When** guarda el gasto, **Then** el gasto creado es la primera ocurrencia, con fecha de hoy, y no se genera otra ocurrencia adicional para hoy.
2. **Given** el grupo tiene `debt_due_days = 7`, **When** se crea la primera ocurrencia, **Then** las deudas vencen el 15/11/2026, igual que en un gasto normal.
3. **Given** la configuración de recordatorios es "2 días antes", **When** llega el 13/11/2026 (viernes), **Then** los deudores con deuda pendiente reciben el recordatorio de la primera ocurrencia.
4. **Given** la frecuencia es mensual y la primera ocurrencia fue el 08/11/2026, **When** llega el 08/12/2026, **Then** se genera la segunda ocurrencia con esa fecha, sus propias deudas y su propio vencimiento.
5. **Given** la segunda ocurrencia es del 08/12/2026, **When** se calcula su vencimiento, **Then** vence el 08/12/2026, porque las ocurrencias generadas vencen en su propia fecha y no se les vuelve a sumar `debt_due_days`.
6. **Given** la segunda ocurrencia vence el 08/12/2026 y la configuración de recordatorios es "2 días antes", **When** llega el 06/12/2026, **Then** los deudores con deuda pendiente reciben el recordatorio de la segunda ocurrencia.
7. **Given** una ocurrencia sin deudas pendientes o ya saldadas, **When** llega la fecha de envío, **Then** no se envía recordatorio, según las reglas de 025.

### Edge Cases

- El grupo no tiene `debt_due_days`: las ocurrencias se generan igual, pero 025 no crea recordatorios para sus deudas.
- La serie se crea con fecha de hoy: la primera ocurrencia es el siguiente período; no se genera una ocurrencia en la misma fecha de creación.
- El job no corre durante varios días: al volver a correr, recupera todas las fechas vencidas hasta hoy.
- La serie se creó antes de esta feature como `RECURRING` sin frecuencia: sigue siendo un gasto informativo y no genera ocurrencias.
- Un participante deja el grupo después de crear la serie: la ocurrencia conserva los participantes originales, porque replica la serie y no vuelve a validar la membresía.
- La fecha de la ocurrencia ya tiene una fila para esa serie (por ejemplo, tras un corte): el job la saltea y avanza la próxima fecha.
- El guardado de una serie falla después de crear el gasto: la transacción no deja series ni ocurrencias parciales.
- La frecuencia no está en el catálogo: se rechaza con el error de frecuencia y no se guarda nada.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: El formulario de gasto de grupo MUST ofrecer el selector de frecuencia cuando el tipo es "Recurrente", con las opciones Semanal, Mensual y Anual.
- **FR-002**: La frecuencia MUST ser obligatoria para `RECURRING` y MUST rechazarse para `ONE_TIME`.
- **FR-003**: Un gasto con amigo MUST rechazar `frequency`; la recurrencia compartida aplica solo a grupos.
- **FR-004**: El backend MUST persistir `recurrence_frequency`, `recurrence_status`, `recurrence_next_date` y `recurrence_source_id` en `group_expenses`.
- **FR-005**: Al crear una serie activa, `recurrence_next_date` MUST ser la primera fecha de la frecuencia estrictamente posterior a la fecha del gasto y a hoy, con la misma semántica que 009.
- **FR-006**: Un job diario MUST generar las ocurrencias cuyo `recurrence_next_date` sea menor o igual a hoy.
- **FR-007**: Cada ocurrencia MUST copiar monto, moneda, conversión, pagador, participantes, reparto, categoría, tipo, medio de pago y descripción de la serie.
- **FR-008**: Cada ocurrencia MUST crear sus propias deudas `PENDING` para los participantes distintos del pagador.
- **FR-009**: Cada ocurrencia MUST registrar el evento `EXPENSE_CREATED` en el historial del grupo.
- **FR-010**: La generación MUST ser idempotente: como máximo una ocurrencia por serie y fecha, incluso si el job corre dos veces o en varias instancias.
- **FR-011**: Si el job se atrasa, MUST generar una ocurrencia por cada fecha vencida hasta hoy, con un límite de seguridad de 1000 por serie y ejecución.
- **FR-012**: Una ocurrencia MUST avanzar `recurrence_next_date` de la serie al siguiente período.
- **FR-013**: La frecuencia MUST usar el mismo catálogo y la misma aritmética de fechas que los gastos personales recurrentes (anclada a la fecha de inicio).
- **FR-014**: La generación de ocurrencias NO MUST volver a validar permisos ni membresías: usa los datos congelados de la serie.
- **FR-015**: Las series existentes sin frecuencia MUST seguir siendo válidas y MUST NOT generar ocurrencias.
- **FR-016**: La primera ocurrencia MUST vencer según la regla vigente del grupo y las ocurrencias generadas MUST vencer en su propia fecha.
- **FR-017**: El recordatorio de cada ocurrencia MUST calcularse con la configuración vigente del grupo, incluyendo la opción de "2 días antes".
- **FR-018**: Una ocurrencia sin deudas pendientes o ya saldadas MUST NOT disparar recordatorio.

### Key Entities

- **GroupExpense (serie)**: gasto compartido con `expense_type = RECURRING`, frecuencia, estado `ACTIVE` y próxima fecha.
- **GroupExpense (ocurrencia)**: gasto compartido generado con `recurrence_source_id` apuntando a la serie y sin frecuencia propia.
- **GroupExpenseShare**: partes de la ocurrencia, copiadas de la serie.
- **Debt**: deuda `PENDING` por participante distinto del pagador, con el monto de su parte.
- **GroupActivity**: evento `EXPENSE_CREATED` de cada ocurrencia.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Un usuario puede registrar un gasto de grupo recurrente con frecuencia en una sola pasada del formulario.
- **SC-002**: Al menos el 95 % de las series activas generan su ocurrencia en la primera ejecución del job posterior a la fecha programada.
- **SC-003**: Ejecutar el job dos veces para la misma fecha no crea ocurrencias, deudas ni eventos duplicados.
- **SC-004**: Las deudas de cada ocurrencia quedan disponibles para los flujos de pago, saldo, vencimiento y recordatorios sin pasos manuales.
