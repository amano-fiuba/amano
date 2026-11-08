# Feature Specification: Group Debt Management

**Feature Branch**: `[feat/group-debt-management]`

**Created**: 2026-11-06

**Status**: Draft

**Input**: User description: "Gestión y cálculo de deudas de grupo para 'A mano', una aplicación web y móvil de gestión de gastos personales y compartidos."

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Registrar un gasto compartido (Priority: P1)

Un miembro del grupo registra un gasto compartido indicando monto, moneda, fecha, quién pagó y con quiénes se comparte. La descripción es opcional. Quien pagó siempre forma parte del reparto y no puede quitarse. Sin participantes, el gasto es personal y no genera deudas.

**Why this priority**: Esta es la funcionalidad básica del sistema; sin registrar gastos, no es posible calcular deudas. Es el punto de entrada para todas las demás funcionalidades.

**Independent Test**: Given a group with members, when an administrator registers a shared expense with valid parameters, then the expense is stored and visible to all group participants, and debts are recalculated accordingly.

**Acceptance Scenarios**:
1. Given a group expense with amount > 0, when registered by a participating member, then the system stores the expense and assigns it to the group.
2. Given an expense with no participants, when registered, then it is marked as personal and does not generate debts.
3. Given a registered expense, when viewed by any group member, then the expense details (monto, moneda, quién pagó, participantes) are displayed correctly.

### User Story 2 - Definir cómo se reparte el gasto (Priority: P1)

El registrador del gasto define el método de reparto: partes iguales (por defecto), importes personalizados (la suma debe coincidir con el total) o porcentajes. En el caso de porcentajes, la suma de todos los porcentajes debe ser 100%.

**Why this priority**: El método de reparto determina cómo se calculan las deudas de cada participante. Sin esta definición, el sistema no sabe cómo asignar el monto entre los miembros.

**Independent Test**: Given an expense registered with a specific reparto method (equal, custom, or percentage), when the debt calculation is triggered, then each participant's share matches the selected method and the total equals the expense amount.

**Acceptance Scenarios**:
1. Given an expense with reparto "partes iguales", when debts are calculated, then each participant receives an equal share of the total amount.
2. Given an expense with reparto "importes personalizados", when the sum of custom shares equals the total, then each participant's share matches the specified importes; otherwise, the system rejects the registration.
3. Given an expense with reparto "porcentajes", when the sum of percentages is 100%, then each participant's share is calculated as (percentage/100) × total; if the sum is not 100%, the system rejects the registration.

### User Story 3 - Ver cálculo automático de deudas (Priority: P1)

El sistema calcula automáticamente, dentro de cada grupo, cuánto debe y cuánto debe recibir cada miembro (incluyendo personas simuladas) a partir de los gastos y pagos registrados. Los saldos se recalculan cuando se registra un gasto o un pago. Solo cuentan los gastos y participantes del grupo en cuestión.

**Why this priority**: El cálculo automático es el corazón del producto; los usuarios confían en que los saldos son consistentes y actualizados sin necesidad de hacer cuentas manuales.

**Independent Test**: Given a group with multiple registered expenses and payments, when the balance calculation is triggered, then every member's net balance is correct and the sum of all balances in the group equals zero.

**Acceptance Scenarios**:
1. Given a group with registered expenses where different members paid, when balances are recalculated, then each member's net amount owed/receivable reflects their net contribution.
2. Given a payment is registered against a debt, when balances are recalculated, then the payment is deducted from the corresponding debt and the updated balances reflect the payment.
3. Given a group with simulated persons (without accounts), when balances are calculated, then simulated persons are included in the calculations but do not appear in payment requests (they have no account).

### User Story 4 - Simplificar deudas (liquidación) (Priority: P2)

El sistema compensa deudas cruzadas y propone el conjunto reducido de transferencias (quién transfiere, a quién y cuánto) que deja a todos saldados. El total transferido debe coincidir con el total necesario para saldar y la propuesta no modifica el historial de gastos originales.

**Why this priority**: La liquidación reduce la cantidad de transferencias necesarias para saldar el grupo, minimizando la fricción de cobrar. Los usuarios necesitan una cantidad reducida de movimientos: como máximo uno menos que los miembros con saldo distinto de cero (el mínimo absoluto no se busca, es un problema NP-difícil).

**Independent Test**: Given a group with complex cross-debts, when the liquidation is generated, then the proposed transfers settle all debts with at most M − 1 transactions (M = members with a non-zero balance), and the total transferred equals the net amount needed to settle. The original expense history remains unchanged.

**Acceptance Scenarios**:
1. Given a group where Alice owes Bob $10 and Bob owes Alice $5, when liquidation is generated, then the proposal is a single transfer of $5 from Alice to Bob (not two separate transfers).
2. Given a group with liquidation proposed, when the transfers are executed, then all members end with zero net balance.
3. Given the liquidation proposal, when reviewed, then the original expense history is unchanged.
4. Given a group where A owes B $100 and B owes A $50, when liquidation is generated, then the proposal is a single transfer of $50 from A to B (the cross-debts are compensated; the proposal never contains two opposite transfers between the same pair of members).
5. Given the same pair with several open debts in each direction (for example A owes B $60 and $40, B owes A $30 and $20), when liquidation is generated, then all of them are added up per direction and compensated into one transfer ($50 from A to B).
6. Given A owes B $50 and B owes A $50, when liquidation is generated, then the proposal has no transfer between them.
7. Given two groups with the same members, where in group 1 A owes B $100 and B owes A $50, and in group 2 C owes A $40, B owes A $25 and A owes C $20, when liquidation is generated for each group, then group 1 proposes one transfer of $50 from A to B, and group 2 proposes $20 from C to A and $25 from B to A. Debts are never compensated across groups: the A–B debts of group 1 do not offset the B–A debt of group 2.

### User Story 5 - Gestionar estado de cada deuda (Priority: P2)

Cada deuda tiene un estado: Pendiente (estado inicial), Pago parcial, Pago informado, Pagado, Verificado y Vencido. Los estados evolucionan conforme se registran y verifican pagos (totales o parciales).

**Why this priority**: El seguimiento del estado de las deudas permite a los usuarios saber el estado actual de cada obligación y qué acciones aún deben realizar.

**Independent Test**: Given a newly created debt, when checked, then its state is "Pendiente". After registering an informed payment (without validation), when checked, then its state is "Pago informado". After an administrator validates, when checked, then its state is "Verificado". After confirming a payment that covers the whole outstanding balance, when checked, then its state is "Pagado"; after a payment that leaves a balance, its state is "Pago parcial".

**Acceptance Scenarios**:
1. Given a new debt, when viewed, then its initial state is "Pendiente".
2. Given a debt with state "Pago informado" in a group without "Pagos requiere validación", when the payment is registered, then the state changes to "Pagado".
3. Given a debt with state "Pago informado" in a group with "Pagos requiere validación", when an administrator confirms, then the state changes to "Verificado" (or "Pago parcial" if the payment was partial and an outstanding balance remains); when rejected, the debt returns to its previous state ("Pendiente" or "Pago parcial").

### User Story 6 - Registrar pago de deuda pendiente (Priority: P2)

Registrar el pago de una deuda con saldo pendiente en la que el miembro está involucrado, dejando constancia en el historial. El pago puede ser **parcial o total**: el monto tiene que ser mayor que cero y no superar el saldo pendiente de la deuda (monto de la deuda menos lo ya pagado); por defecto se ofrece el saldo pendiente completo. Si el grupo tiene activada "Pagos requieren validación", el pago queda como "Pago informado" hasta que un Administrador lo confirme (se suma a lo pagado y la deuda pasa a Pago parcial si queda saldo o a Verificado si queda en cero) o lo rechace (la deuda vuelve a su estado anterior, Pendiente o Pago parcial). Si está desactivada, el pago se considera confirmado al instante: se suma a lo pagado y la deuda pasa a Pago parcial si queda saldo o a Pagado si queda en cero. Mientras una deuda tenga un pago informado esperando decisión, no se admite otro pago sobre ella. No se puede registrar un pago sobre una deuda saldada (Pagado o Verificado).

**Why this priority**: Registrar pagos es esencial para mantener los saldos actualizados y cerrar deudas. La lógica de validación asegura el control según la configuración del grupo.

**Independent Test**: Given a group member involved in a debt with outstanding balance, when they register a payment greater than zero and not greater than the outstanding balance, then the payment is recorded in the history and the debt state updates according to the amount paid and the group's "Pagos requiere validación" setting. A payment above the outstanding balance, or any payment on a settled debt or on a debt that already has a payment waiting for validation, fails.

**Acceptance Scenarios**:
1. Given a pending debt in a group without "Pagos requiere validación", when a member registers a payment for the whole outstanding balance, then the debt state becomes "Pagado" and is recorded in the group's activity history.
2. Given a pending debt in a group without "Pagos requiere validación", when a member registers a payment smaller than the outstanding balance, then the payment is confirmed immediately, the amount paid increases, the debt state becomes "Pago parcial" and the outstanding balance decreases by that amount.
3. Given a pending debt in a group with "Pagos requiere validación", when a member registers a payment (partial or total), then the debt state becomes "Pago informado", the amount paid does not change yet and the payment waits for administrator confirmation; while it waits, another payment on the same debt is rejected.
4. Given an administrator confirming a "Pago informado", when confirmed, then the payment is added to the amount paid and the debt state becomes "Verificado" if nothing is left to pay, or "Pago parcial" if an outstanding balance remains.
5. Given an administrator rejecting a "Pago informado", when rejected, then the debt returns to its previous state ("Pendiente" or "Pago parcial") with the same outstanding balance and accepts a new payment.
6. Given a payment greater than the outstanding balance, or a payment of zero or less, when attempting to register it, then the system rejects the operation.
7. Given a debt that is settled ("Pagado" or "Verificado"), when attempting to register a payment, then the system rejects the operation.
8. Given an overdue debt ("Vencido"), when a partial payment is registered, then the debt becomes "Pago parcial" (confirmed decision, 2026-11-08); when the payment covers the whole balance it follows the rules above.

### User Story 7 - Ver mis deudas pendientes (Priority: P3)

Ver cuánto debo, a quién (persona o grupo), con acceso al detalle y origen de cada deuda, descontando pagos ya realizados y verificados.

**Why this priority**: Los miembros necesitan ver su situación financiera dentro del grupo de forma clara y consolidada.

**Independent Test**: Given a group member, when they view their pending debts, then they see a list of all debts they're involved in that are not yet settled, with the amount, counterpart, and origin (which expense generated the debt), excluding payments already made or verified.

**Acceptance Scenarios**:
1. Given multiple debts in various states for a member, when they view pending debts, then only open debts appear: "Pendiente", "Pago parcial", "Pago informado" (not settled until an administrator confirms) and "Vencido"; debts in "Pagado" or "Verificado" do not appear.
2. Given debts with recorded payments, when viewing pending debts, then payments already applied are deducted from the displayed amounts.
3. Given the detail view, when selecting a specific debt, then the origin (which expense generated it) is shown.

### User Story 8 - Mantener historial de actividad del grupo (Priority: P3)

Mantener un historial de actividad del grupo que incluya gastos y pagos, ordenados cronológicamente, para que los usuarios puedan consultar qué sucedió en el grupo.

**Why this priority**: El historial brinda transparencia y permite a los usuarios revisar la historia de transacciones del grupo, resolviendo dudas sobre quién pagó qué y cuándo.

**Independent Test**: Given a group with registered expenses and payments, when the activity history is viewed, then all events are listed in reverse chronological order with sufficient detail to identify each transaction.

**Acceptance Scenarios**:
1. Given an expense registered on a specific date, when viewing the activity history, then the expense appears with its date, amount, and participants.
2. Given a payment registered on a specific date, when viewing the activity history, then the payment appears with its date, amount, and which debt was settled.
3. Given both expenses and payments, when viewing the history, then events are sorted by date in descending order (most recent first).

### User Story 9 - Operar en varias monedas (Priority: P2)

Operar en varias monedas (ARS, USD y EUR como mínimo): cuando un gasto está en una moneda distinta a la principal del grupo, se muestra el monto original y su equivalente convertido, usando la cotización de la fecha de la transacción.

**Why this priority**: Los usuarios viajan entre países y comparten gastos en divisas diferentes; mostrar tanto el monto original como el convertido brinda claridad.

**Independent Test**: Given a group with a primary currency (e.g., ARS) and an expense in USD, when the expense is displayed, then both the original USD amount and the equivalent in ARS (using the transaction date's exchange rate) are shown.

**Acceptance Scenarios**:
1. Given a group with primary currency ARS and an expense in USD 50 on 2026-01-15, when displayed, then the original $50 USD and the equivalent in ARS using the exchange rate from 2026-01-15 are shown.
2. Given a group with primary currency EUR and an expense in ARS, when displayed, then the original ARS amount and the equivalent in EUR using the transaction date's rate are shown.
3. Given expenses in different currencies within the same group, when viewing balances, then each expense shows its original currency and converted amount.

## Functional Requirements

### FR-001: Registrar gasto compartido
The system MUST allow registering a shared expense with the following parameters: monto (> 0), moneda, fecha (por defecto hoy), quién pagó y con quiénes se comparte. La descripción es opcional. Quien pagó siempre forma parte del reparto y no puede quitarse. Sin participantes, el gasto es personal y no genera deudas.

### FR-002: Definir método de reparto
The system MUST support defining cómo se reparte el gasto: partes iguales (por defecto), importes personalizados (la suma debe coincidir con el total) o porcentajes. En el caso de porcentajes, la suma de todos los porcentajes debe ser 100%.

### FR-003: Cálculo automático de saldos
The system MUST calculate automatically, within each group, cuánto debe y cuánto debe recibir cada miembro (incluyendo personas simuladas), a partir de los gastos y pagos registrados. Los saldos se recalculan cuando se registra un gasto o un pago. Solo cuentan los gastos y participantes del grupo en cuestión. La suma de todos los saldos del grupo es cero.

### FR-004: Liquidación de deudas (simplificación)
The system MUST simplify group debts (liquidación) compensando deudas cruzadas y proponiendo el conjunto reducido de transferencias (quién transfiere, a quién y cuánto) que deja a todos saldados. El total transferido debe coincidir con el total necesario para saldar y la propuesta no modifica el historial de gastos originales. La cantidad de transferencias propuestas es menor o igual a la cantidad de miembros con saldo distinto de cero menos uno. Compensación entre dos miembros: si A le debe 100 a B y B le debe 50 a A, la propuesta es una sola transferencia de 50 de A a B; nunca se proponen dos transferencias opuestas entre el mismo par, y si las deudas se anulan exactamente no se propone ninguna. La compensación se hace por moneda (nunca se mezclan monedas) y sobre el saldo pendiente de cada deuda abierta. La compensación se hace siempre dentro de un mismo grupo, nunca entre grupos distintos. Es solo parte de la propuesta: las deudas originales no se modifican, y los pagos se siguen registrando sobre cada deuda directa (decisión (a) de la sesión de aclaraciones).

### FR-005: Estados de deuda
The system MUST manage the state of each debt with the following sequence: Pendiente (estado inicial) → Pago parcial (si se paga una parte) / Pago informado (si requiere validación) → Verificado (si requiere validación) / Pagado (si no requiere validación) → Vencido. Cada deuda comienza en estado Pendiente.

### FR-006: Registrar pago de deuda
The system MUST allow registering a partial or total payment of a debt with outstanding balance in which the member is involved, leaving a record in the history. The amount must be greater than zero and not greater than the outstanding balance (amount owed minus amount already paid). If the group has "Pagos requieren validación" activated, the payment remains as "Pago informado" without changing the amount paid until an Administrator confirms it (the amount is added and the debt becomes Verificado, or Pago parcial if a balance remains) or rejects it (the debt goes back to its previous state, Pendiente or Pago parcial). If disabled, the payment is confirmed at once: it is added to the amount paid and the debt becomes Pago parcial or Pagado. A debt can have only one payment waiting for validation at a time. It is not possible to register a payment on a settled debt.

### FR-007: Ver deudas pendientes
The system MUST show the member how much they owe, to whom (person or group), with access to the detail and origin of each debt, deducting payments already made and verified.

### FR-008: Historial de actividad del grupo
The system MUST maintain a group activity history that includes expenses and payments, ordered chronologically.

### FR-009: Soporte multi-moneda
The system MUST operate in multiple currencies (ARS, USD and EUR as minimum): when an expense is in a currency different from the group's main currency, both the original amount and its converted equivalent are shown, using the exchange rate from the transaction date.

### FR-010: Restricciones de membresía
The system MUST prevent a member with pending balance from leaving the group. Expenses can only be shared with people who are already friends of the user or members of the group, never with strangers. The exception are simulated persons, who have no account.

## Key Entities

- **Gasto**: Registro de un gasto compartido o personal. Atributos: monto, moneda, fecha, pagador, participantes (opcional), descripción. Relacionado con un grupo y determina deudas.

- **Deuda**: Obligación que un miembro tiene con otro (o simulado) derivada de un gasto compartido. Atributos: monto, acreedor, deudor, estado (Pendiente, Pago parcial, Pago informado, Pagado, Verificado, Vencido), origen (gasto relacionado), pagos registrados. Pertenece a un grupo.

- **Miembro del grupo**: Participante de un grupo. Puede ser una persona con cuenta o una persona simulada (sin cuenta, cargada por nombre y mail). Los miembros simulados participan en cálculos de deudas pero no pueden recibir pagos ni tienen cuentas.

- **Grupo**: Conjunto de miembros (personas con cuenta y/o simulados) con una moneda principal y configuración opcional ("Pagos requiere validación"). Los gastos y deudas se mantienen dentro del grupo y no se comparten con personas ajenas.

- **Persona simulada**: Alguien sin cuenta, cargado por nombre y mail, que participa de los gastos y del cálculo de deudas pero no usa la aplicación. Existe para que quienes no quieren registrarse, instalar ni aprender la app puedan ser incluidos igual.

- **Pago registrado**: Registro de que una deuda ha sido pagada, en forma total o parcial. Atributos: fecha, monto (mayor que cero y no superior al saldo pendiente), quién pagó, estado resultante (según configuración del grupo).

## Success Criteria

### SC-001: Saldos consistentes en el grupo
Dado un grupo con gastos de varios pagadores, cada miembro ve de forma consistente cuánto debe o debe recibir, y la suma de todos los saldos del grupo es cero.

**Verificación**: Para cualquier grupo, sumar los saldos netos de todos los miembros (incluyendo personas simuladas); el resultado debe ser exactamente 0.

### SC-002: Liquidación mínima de transferencias
La propuesta de liquidación deja a todos saldados con una cantidad de transferencias menor o igual a la cantidad de miembros con saldo distinto de cero menos uno.

**Verificación**: Contar el número de miembros con saldo neto distinto de cero (M); el número de transferencias propuestas por el sistema de liquidación debe ser ≤ M - 1.

### SC-003: Sin cálculos manuales necesarios
Ningún usuario debe hacer cuentas manuales para determinar quién le debe a quién en su grupo. Después de registrar cualquier combinación de gastos y pagos, la aplicación muestra claramente el balance neto de cada miembro (lo que debe y a quién) sin que el usuario necesite calcular manualmente. **Criterio de medición**: La aplicación muestra claramente el balance neto de cada miembro en una vista unica sin requerir cálculos manuales por parte del usuario.

### SC-004: Pago informado reflejado en estado y historial
Un pago informado queda reflejado en el estado de la deuda y en el historial de actividad del grupo, con o sin validación según la configuración "Pagos requiere validación" del grupo. **Criterio de medición**: Después de registrar un pago, consultar el estado de la deuda correspondiente muestra el estado correcto ("Pagado", "Pago informado" o "Verificado") y el historial de actividad refleja el pago registrado. La transición de estado es consistente independientemente de la configuración del grupo.


## Assumptions

- Los usuarios ya tienen cuentas, amistades y grupos configurados (la gestión de esas entidades es otra feature).
- Las personas simuladas son cargadas manualmente por nombre y mail y no tienen acceso a la aplicación.
- La fuente de cotizaciones es el tipo de cambio oficial del BCRA (API pública Estadísticas Cambiarias v1.0, sin credenciales). Este diseño reemplaza a OANDA, mencionado en el backlog original. Esta fuente está confirmada por el Product Owner. Se usa el **tipo de cambio oficial** del BCRA, no el dólar blue ni el MEP. No se fija la tasa manualmente ni se usa una tasa por defecto: si no hay cotización disponible (incluidos fines de semana y feriados, con hasta 7 días hacia atrás), el gasto se rechaza con 503 y no se guarda.
- Un "Administrador de grupo" es un miembro con permisos especiales para validar o rechazar pagos cuando la configuración "Pagos requiere validación" está activada.
- Los gastos recurrentes (spec 009) quedan fuera del alcance de la 022: el gasto compartido de grupo no captura tipo ni frecuencia. La simplificación de deudas aplica sobre el saldo actual después de cada registro.
- La moneda principal de un grupo se define cuando se crea el grupo y puede ser ARS, USD o EUR.
- Los porcentajes de reparto se ingresan como números enteros o decimales que suman 100 en total.
- Categoría, tipo (único o recurrente) y medio de pago de un gasto quedan **fuera del alcance** de la 022 (decisión del 2026-11-08): el gasto de grupo (017) no los captura y no se agregan en esta feature. Si más adelante se incorporan, no afectarán el cálculo de montos ni el estado de las deudas.
- Las fechas de las transacciones son fechas pasadas o presentes; no se permiten fechas futuras para gastos o pagos.

## Clarifications
**Session 2026-11-06**

- Q: Redondeo de centavos en repartos en partes iguales y quién absorbe la diferencia? → A: Cada parte se calcula en centavos hacia abajo (piso) y el resto, si lo hay, lo absorbe el pagador. La razón es que ningún participante no pagador pague de más por redondeo. Ejemplos con el pagador: 100,00 entre 3 → 33,33 / 33,33 / 33,34; 0,05 entre 3 → 0,01 / 0,01 / 0,03. La misma regla se aplica al reparto por porcentajes.
- Q: Origen de la fecha límite que determina el estado Vencido? → A: The deadline is configured per group by the administrator as a fixed number of days from the expense date; debts automatically transition to "Vencido" when the current date exceeds (expense date + days configured). If not configured, debts never automatically become "Vencido" (remain in their current state until manual transition). The threshold applies to any expense of the group (see the later clarification "Vencimiento: umbral por grupo o cualquier gasto?").
- Q: Si se admiten pagos parciales? → A: **Sí, se admiten** (decisión de producto del 2026-11-07, que reemplaza la anterior "solo pagos completos"). El monto del pago debe ser mayor que cero y no superar el saldo pendiente (monto de la deuda menos lo ya pagado); uno mayor se rechaza. Sin validación, el pago se confirma al instante y suma a lo pagado (Pago parcial si queda saldo, Pagado si queda en cero). Con validación, el pago queda informado sin tocar lo pagado; al confirmarlo se suma (Pago parcial o Verificado) y al rechazarlo la deuda vuelve a su estado anterior. Solo puede haber un pago por validar a la vez por deuda.
- Q: Qué ocurre con saldos y pagos ya registrados al editar o eliminar un gasto? → A: Editing an expense is not allowed once it has participants (only the description can be modified before registration; after registration, the expense is immutable). Deleting an expense is only allowed if it has no participants (personal expense) and no debts are associated; if the expense has associated debts, deletion is rejected.
- Q: Si las deudas de una misma persona se consolidan entre grupos distintos? → A: Debts are tracked per-group and displayed separately. A member sees their debts organized by group, with the total across all groups shown as a summary, but each debt is settled independently within its group. Payments are applied to specific debts in specific groups. The "total owed" summary is informational only and doesn't create a cross-group obligation.
- Q: ¿Quién puede cargar un gasto pagado por otro miembro y cómo se registra? → A: Cualquier miembro del grupo puede cargar un gasto pagado por otro miembro (decisión confirmada por el equipo, 2026-11-06). Reglas: (1) el pagador y todos los participantes deben ser miembros del grupo, y esta validación se hace en el servidor; (2) se guardan por separado el pagador (`payerUserId`) y quien registró el gasto (`createdByUserId`); (3) las deudas se generan contra el pagador; (4) el historial de actividad muestra a quien registró y al pagador. Los participantes también pueden ser personas simuladas del grupo, que siguen siendo informativas y no tienen obligación financiera. Esta respuesta reemplaza la anterior de esta misma sesión, que prohibía cargar gastos en nombre de otro.
- Q: Si Pagado y Verificado son equivalentes para el saldo? → A: Pagado y Verificado son equivalentes para el saldo: una vez que una deuda alcanza cualquiera de estos estados, el saldo correspondiente se considera saldado (cero) para todos los efectos del grupo. La diferencia semántica permanece en el historial (se registra el camino recorrido: Pendiente → Pago informado → Verificado/Pagado), pero no afecta los cálculos de deuda, liquidación, ni el reporte de saldos.

- Q: Pago de una transferencia propuesta cuando A le debe a B y B le debe a C, y se propone que A pague a C? → A: (a) la propuesta es solo orientativa y los pagos se registran sobre deudas directas
- Q: Personas simuladas en saldos y liquidación? → A: incluidas en saldos y propuesta como informativas; sus pagos los registra un administrador; el pagador siempre es un usuario registrado
- Q: Vencimiento: umbral por grupo o cualquier gasto? → A: cualquier gasto, con el umbral por grupo
- Q: Una deuda vencida se puede pagar? → A: Confirmado: una deuda en estado Vencido puede pagarse. Un pago total la deja Pagada (o Pago informado si el grupo valida); un pago parcial la pasa a Pago parcial (**decisión confirmada el 2026-11-08**; se descartó la alternativa de que siga Vencida con saldo parcial, que exigía cambiar las restricciones de la base de datos).
- Q: Pagos parciales: si la spec 018 permitió pagos parciales, ¿qué pasa ahora? → A: Los pagos parciales **siguen permitidos** (reemplaza la respuesta anterior "solo pagos completos"). El flujo de la 018 y el de la 022 quedan alineados: ambos suman al monto pagado de la deuda y producen los mismos estados; los pagos históricos de la 018 se conservan tal como están.
- Q: Si el servicio de cotizaciones no responde, ¿se rechaza el gasto? → A: Rechazar el gasto con un mensaje claro (texto vigente: "No se pudo obtener la cotización; reintente después o registre en moneda del grupo")
