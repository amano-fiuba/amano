# Feature Specification: Calendario de vencimientos de recurrentes de grupo

**Feature Branch**: `feat/recurrence-due-dates`

**Created**: 2026-11-08

**Status**: Draft

**Input**: User description: "toma la spec 028 para realizar la siguiente user story: el gasto recurrente de grupo registrado hoy cuenta como primer pago, la primera ocurrencia vence como un gasto normal y las ocurrencias siguientes vencen en su propia fecha, con recordatorios calculados desde ese vencimiento."

## Relación con otras especificaciones

- Extiende **Spec 028 (Gastos compartidos recurrentes)**: conserva la creación de la serie, la primera ocurrencia y la generación de ocurrencias siguientes.
- Extiende **Spec 022 (Deudas y vencimientos)**: la primera ocurrencia hereda exactamente la misma regla de vencimiento que un gasto no recurrente del grupo.
- Extiende **Spec 025 (Recordatorios automáticos)**: el recordatorio se calcula siempre desde la fecha de vencimiento vigente de cada ocurrencia, sin crear recordatorios extra por cada ocurrencia de la serie.

En caso de conflicto con 028 sobre el vencimiento o la fecha de recordatorio de una ocurrencia, vale esta especificación.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Primer pago de la recurrencia y vencimiento de cada período (Priority: P1)

Un miembro registra hoy un gasto recurrente en un grupo. Ese mismo gasto funciona como primer pago y no se genera una duplicación para hoy. La primera ocurrencia se recuerda y vence como un gasto normal del grupo, con su plazo habitual según la configuración de días para el vencimiento. Cuando llega el siguiente período, se genera una nueva ocurrencia con su propia fecha, sus propias deudas y su propio vencimiento, y el recordatorio se calcula desde esa fecha.

**Why this priority**: Es el comportamiento central del calendario completo de la recurrencia de gastos: sin este flujo, el primer pago puede omitirse, duplicarse o vencerse sin recordatorio o con una forma de vencimiento distinta de la esperada.

**Independent Test**: Con un grupo con 7 días de plazo para pagar y un miembro con deuda pendiente, registrar hoy un gasto recurrente, generar el segundo período un mes después y verificar los recordatorios y vencimientos históricos de ambas ocurrencias.

**Acceptance Scenarios**:

1. **Given** un usuario registra hoy un gasto de grupo recurrente, **When** guarda el gasto, **Then** el gasto creado es la primera ocurrencia, con fecha de hoy, y no se genera otra ocurrencia adicional para hoy.

2. **Given** el grupo tiene un plazo de 7 días para pagar, **When** se crea la primera ocurrencia después del 08/11/2026, **Then** las deudas de la primera ocurrencia vencen el 15/11/2026, igual que un gasto normal.

3. **Given** la configuración de recordatorios es de 2 días antes del vencimiento, **When** llega el 13/11/2026 (viernes), **Then** los deudores con deuda pendiente reciben el recordatorio de la primera ocurrencia.

4. **Given** la frecuencia es mensual y la primera ocurrencia fue el 08/11/2026, **When** llega el 08/12/2026, **Then** se genera la segunda ocurrencia con esa fecha, sus propias deudas y su propio vencimiento.

5. **Given** la segunda ocurrencia es del 08/12/2026, **When** se calcula su vencimiento, **Then** vence el 08/12/2026, porque las ocurrencias generadas vencen en su propia fecha y no se les aplica otra vez el plazo por defecto del grupo.

6. **Given** la segunda ocurrencia vence el 08/12/2026 y la configuración de recordatorios es de 2 días antes, **When** llega el 06/12/2026, **Then** los deudores con deuda pendiente reciben el recordatorio de la segunda ocurrencia.

7. **Given** una ocurrencia sin deudas pendientes o ya saldadas, **When** llega la fecha de envío, **Then** no se envía recordatorio, según las reglas de recordatorios vigentes.

---

### User Story 2 - Regla común para cualquier ocurrencia recurrente (Priority: P1)

Las ocurrencias generadas después de la primera comparten una misma regla independiente de la frecuencia: cada una vence en su propia fecha y el recordatorio se envía 2 días antes. El usuario no tiene que indicar de nuevo el plazo ni ajustar manualmente una fecha de vencimiento por período. 

**Why this priority**: Deja la regla explícita para cualquier frecuencia, no solo para el ejemplo mensual, y evita leer como una excepción puntual.

**Independent Test**: Con una recurrencia semanal y el mismo plazo de 7 días para la primera ocurrencia, generar una segunda y una tercera ocurrencia y verificar que ambas vencen el día exacto de su fecha de gasto.

**Acceptance Scenarios**:

1. **Given** una serie recurrente y una ocurrencia generada automáticamente, **When** se define su vencimiento, **Then** corresponde a la fecha de la propia ocurrencia, sin añadirse el plazo por defecto del grupo otra vez.

2. **Given** la configuración de recordatorios de 2 días antes, **When** una ocurrencia generada tiene una fecha futura, **Then** el recordatorio llega 2 días antes de la fecha de esa ocurrencia.

3. **Given** la serie graba todas las ocurrencias ya generadas, **When** el usuario revisa los gastos del grupo, **Then** ve un único gasto por período y no duplicados ni ocurrencias posteriores inconsistentes.

### Edge Cases

- Un evento ocurre antes de la primera ocurrencia: no se genera otra ocurrencia extra del mismo día.
- El usuario registra el gasto con una fecha retroactiva: la primera ocurrencia usa esa fecha como gasto inicial, sin crear otra ocurrencia equivalente.
- Sin configuración de recordatorios: no se envían recordatorios, pero las deudas de cada ocurrencia mantienen su regla de vencimiento.
- No hay fecha de vencimiento del grupo: no se aplican recordatorios, pero se conserva la misma estructura de ocurrencias.
- El usuario cambia la configuración de recordatorios entre períodos: cada recordatorio usa la configuración vigente en su fecha de cálculo.
- La deuda se cancela antes de la fecha de envío: no se envía recordatorio.
- La primera ocurrencia se genera sobre una fecha en el pasado: se calcula con las mismas reglas de vencimiento que hoy, sin historial adicional.
- La segunda ocurrencia aparece en un día no ajustable al calendario del grupo: vence en su fecha propia, no en la fecha traducida al plazo por defecto del grupo.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: Un gasto recurrente de grupo registrado hoy debe contar como primera ocurrencia y no debe generar otra ocurrencia adicional del mismo día.
- **FR-002**: La primera ocurrencia de la recurrencia debe calcular su vencimiento con la misma regla que un gasto no recurrente del grupo.
- **FR-003**: Cada ocurrencia generada automáticamente debe crear sus propias deudas, independientes de la primera ocurrencia.
- **FR-004**: Cada ocurrencia generada automáticamente debe vencer en su propia fecha, sin volver a aplicar el plazo base del grupo.
- **FR-005**: Debe poder configurarse un recordatorio de 2 días antes del vencimiento para un grupo con gastos recurrentes.
- **FR-006**: Cada recordatorio de una ocurrencia debe calcularse desde la fecha de vencimiento vigente de esa ocurrencia.
- **FR-007**: No debe enviarse un recordatorio para deudas inexistentes, saldadas o canceladas.
- **FR-008**: Debe conservarse el histórico de ocurrencias y deudas por período, sin duplicar la primera ocurrencia.

### Key Entities *(include if feature involves data)*

- **Gasto recurrente de grupo**: gasto que abre una serie y funciona como primera ocurrencia con su propia fecha.
- **Ocurrencia generada**: gasto del período siguiente, con fecha propia y deudas propias.
- **Deuda**: obligación de un participante distinto del pagador en una ocurrencia, con su vencimiento según la regla de la ocurrencia.
- **Configuración de recordatorios**: información del grupo para decidir cuándo se envía recordatorio respecto del vencimiento, incluyendo opción de 2 días antes.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 100 % de las primeras ocurrencias del grupo recurrente tienen el mismo vencimiento que un gasto normal del grupo.
- **SC-002**: 100 % de las ocurrencias automáticas posteriores vencen en su propia fecha.
- **SC-003**: 100 % de los recordatorios de ocurrencias generadas llegan exactamente 2 días antes de la fecha de vencimiento de esa ocurrencia.
- **SC-004**: 100 % de las ocurrencias recurrentes producen exactamente una ocurrencia por período con deudas propias, sin duplicar el gasto del día de registro.

## Assumptions

- Las fechas usan formato dd/mm/yyyy.
- La recurrencia es mensual en el ejemplo base, pero la misma regla aplica a las demás frecuencias soportadas.
- La primera ocurrencia hereda la regla de vencimiento del grupo vigente en el momento del alta.
- Las ocurrencias automáticas heredan la fecha exacta del período, no un ajuste automático por feriados o fines de semana.
- La opción de 2 días antes del vencimiento es un ajuste general del grupo, no un valor específico para esta historia.
- El sistema ya provee la mecánica de serie, ocurrencias, deudas, vencimientos y recordatorios existente; esta feature ajusta la leyenda de la fecha en lugar de crear un mecanismo nuevo.
