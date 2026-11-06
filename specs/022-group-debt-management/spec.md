# Feature Specification: Group Debt Management

**Feature Branch**: `[feat/group-debt-management]`

**Created**: 2026-11-06

**Status**: Draft

**Input**: User description: "Gestión y cálculo de deudas de grupo para 'A mano', una aplicación web y móvil de gestión de gastos personales y compartidos."

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Registrar un gasto compartido (Priority: P1)
[Describe this user journey in plain language]
Un miembro del grupo registra un gasto compartido indicando monto, moneda, fecha, categoría, tipo (único o recurrente), medio de pago, quién pagó y con quiénes se comparte. La descripción es opcional. Quien pagó siempre forma parte del reparto y no puede quitarse. Sin participantes, el gasto es personal y no genera deudas.

**Why this priority**: Esta es la funcionalidad básica del sistema; sin registrar gastos, no es posible calcular deudas. Es el punto de entrada para todas las demás funcionalidades.

**Independent Test**: Given a group with members, when an administrator registers a shared expense with valid parameters, then the expense is stored and visible to all group participants, and debts are recalculated accordingly.

**Acceptance Scenarios**:
1. Given a group expense with amount > 0, when registered by a participating member, then the system stores the expense and assigns it to the group.
2. Given an expense with no participants, when registered, then it is marked as personal and does not generate debts.
3. Given a registered expense, when viewed by any group member, then the expense details (monto, moneda, quién pagó, participantes) are displayed correctly.

### User Story 2 - Definir cómo se reparte el gasto (Priority: P1)
[Describe this user journey in plain language]
El registrador del gasto define el método de reparto: partes iguales (por defecto), importes personalizados (la suma debe coincidir con el total) o porcentajes. En el caso de porcentajes, la suma de todos los porcentajes debe ser 100%.

**Why this priority**: El método de reparto determina cómo se calculan las deudas de cada participante. Sin esta definición, el sistema no sabe cómo asignar el monto entre los miembros.

**Independent Test**: Given an expense registered with a specific reparto method (equal, custom, or percentage), when the debt calculation is triggered, then each participant's share matches the selected method and the total equals the expense amount.

**Acceptance Scenarios**:
1. Given an expense with reparto "partes iguales", when debts are calculated, then each participant receives an equal share of the total amount.
2. Given an expense with reparto "importes personalizados", when the sum of custom shares equals the total, then each participant's share matches the specified importes; otherwise, the system rejects the registration.
3. Given an expense with reparto "porcentajes", when the sum of percentages is 100%, then each participant's share is calculated as (percentage/100) × total; if the sum is not 100%, the system rejects the registration.

### User Story 3 - Ver cálculo automático de deudas (Priority: P1)
[Describe this user journey in plain language]
El sistema calcula automáticamente, dentro de cada grupo, cuánto debe y cuánto debe recibir cada miembro (incluyendo personas simuladas) a partir de los gastos y pagos registrados. Los saldos se recalculan cuando se registra un gasto o un pago. Solo cuentan los gastos y participantes del grupo en cuestión.

**Why this priority**: El cálculo automático es el corazón del producto; los usuarios confían en que los saldos son consistentes y actualizados sin necesidad de hacer cuentas manuales.

**Independent Test**: Given a group with multiple registered expenses and payments, when the balance calculation is triggered, then every member's net balance is correct and the sum of all balances in the group equals zero.

**Acceptance Scenarios**:
1. Given a group with registered expenses where different members paid, when balances are recalculated, then each member's net amount owed/receivable reflects their net contribution.
2. Given a payment is registered against a debt, when balances are recalculated, then the payment is deducted from the corresponding debt and the updated balances reflect the payment.
3. Given a group with simulated persons (without accounts), when balances are calculated, then simulated persons are included in the calculations but do not appear in payment requests (they have no account).

### User Story 4 - Simplificar deudas (liquidación) (Priority: P2)
[Describe this user journey in plain language]
El sistema compensa deudas cruzadas y propone el conjunto mínimo de transferencias (quién transfiere, a quién y cuánto) que deja a todos saldados. El total transferido debe coincidir con el total necesario para saldar y la propuesta no modifica el historial de gastos originales.

**Why this priority**: La liquidación reduce la cantidad de transferencias necesarias para saldar el grupo, minimizando la fricción de cobrar. Los usuarios necesitan saber el número mínimo de movimientos.

**Independent Test**: Given a group with complex cross-debts, when the liquidation is generated, then the proposed transfers settle all debts with the minimum number of transactions, and the total transferred equals the net amount needed to settle. The original expense history remains unchanged.

**Acceptance Scenarios**:
1. Given a group where Alice owes Bob $10 and Bob owes Alice $5, when liquidation is generated, then the proposal is a single transfer of $5 from Alice to Bob (not two separate transfers).
2. Given a group with liquidation proposed, when the transfers are executed, then all members end with zero net balance.
3. Given the liquidation proposal, when reviewed, then the original expense history is unchanged.

### User Story 5 - Gestionar estado de cada deuda (Priority: P2)
[Describe this user journey in plain language]
Cada deuda tiene un estado: Pendiente (estado inicial), Pago informado, Pagado, Verificado y Vencido. Los estados evolucionan conforme se registran y verifican pagos.

**Why this priority**: El seguimiento del estado de las deudas permite a los usuarios saber el estado actual de cada obligación y qué acciones aún deben realizar.

**Independent Test**: Given a newly created debt, when checked, then its state is "Pendiente". After registering an informed payment (without validation), when checked, then its state is "Pago informado". After an administrator validates, when checked, then its state is "Verificado". After confirming full payment, when checked, then its state is "Pagado".

**Acceptance Scenarios**:
1. Given a new debt, when viewed, then its initial state is "Pendiente".
2. Given a debt with state "Pago informado" in a group without "Pagos requiere validación", when the payment is registered, then the state changes to "Pagado".
3. Given a debt with state "Pago informado" in a group with "Pagos requiere validación", when an administrator confirms, then the state changes to "Verificado"; when rejected, the state remains "Pendiente".

### User Story 6 - Registrar pago de deuda pendiente (Priority: P2)
[Describe this user journey in plain language]
Registrar el pago de una deuda pendiente en la que el miembro está involucrado, dejando constancia en el historial. Si el grupo tiene activada "Pagos requieren validación", el pago queda como "Pago informado" hasta que un Administrador lo confirme (pasa a Verificado) o lo rechace (la deuda sigue Pendiente). Si está desactivada, el pago informado se considera confirmado (Pagado). No se puede registrar un pago sobre una deuda que no esté pendiente.

**Why this priority**: Registrar pagos es esencial para mantener los saldos actualizados y cerrar deudas. La lógica de validación asegura el control según la configuración del grupo.

**Independent Test**: Given a group member involved in a pending debt, when they register a payment, then the payment is recorded in the history and the debt state updates according to the group's "Pagos requiere validación" setting. Attempting to register a payment on a non-pending debt fails.

**Acceptance Scenarios**:
1. Given a pending debt in a group without "Pagos requiere validación", when a member registers payment, then the debt state becomes "Pagado" and is recorded in the group's activity history.
2. Given a pending debt in a group with "Pagos requiere validación", when a member registers payment, then the debt state becomes "Pago informado" and waits for administrator confirmation.
3. Given an administrator confirming a "Pago informado", when confirmed, then the debt state becomes "Verificado".
4. Given an administrator rejecting a "Pago informado", when rejected, then the debt state remains "Pendiente".
5. Given a debt that is not "Pendiente" (e.g., already "Pagado"), when attempting to register a payment, then the system rejects the operation.

### User Story 7 - Ver mis deudas pendientes (Priority: P3)
[Describe this user journey in plain language]
Ver cuánto debo, a quién (persona o grupo), con acceso al detalle y origen de cada deuda, descontando pagos ya realizados y verificados.

**Why this priority**: Los miembros necesitan ver su situación financiera dentro del grupo de forma clara y consolidada.

**Independent Test**: Given a group member, when they view their pending debts, then they see a list of all debts they're involved in that are not yet settled, with the amount, counterpart, and origin (which expense generated the debt), excluding payments already made or verified.

**Acceptance Scenarios**:
1. Given multiple debts in various states for a member, when they view pending debts, then only debts with state "Pendiente" or "Pago informado" (without validation) appear.
2. Given debts with recorded payments, when viewing pending debts, then payments already applied are deducted from the displayed amounts.
3. Given the detail view, when selecting a specific debt, then the origin (which expense generated it) is shown.

### User Story 8 - Mantener historial de actividad del grupo (Priority: P3)
[Describe this user journey in plain language]
Mantener un historial de actividad del grupo que incluya gastos y pagos, ordenados cronológicamente, para que los usuarios puedan consultar qué sucedió en el grupo.

**Why this priority**: El historial brinda transparencia y permite a los usuarios revisar la historia de transacciones del grupo, resolviendo dudas sobre quién pagó qué y cuándo.

**Independent Test**: Given a group with registered expenses and payments, when the activity history is viewed, then all events are listed in reverse chronological order with sufficient detail to identify each transaction.

**Acceptance Scenarios**:
1. Given an expense registered on a specific date, when viewing the activity history, then the expense appears with its date, amount, and participants.
2. Given a payment registered on a specific date, when viewing the activity history, then the payment appears with its date, amount, and which debt was settled.
3. Given both expenses and payments, when viewing the history, then events are sorted by date in descending order (most recent first).

### User Story 9 - Operar en varias monedas (Priority: P2)
[Describe this user journey in plain language]
Operar en varias monedas (ARS, USD y EUR como mínimo): cuando un gasto está en una moneda distinta a la principal del grupo, se muestra el monto original y su equivalente convertido, usando la cotización de la fecha de la transacción.

**Why this priority**: Los usuarios viajan entre países y comparten gastos en divisas diferentes; mostrar tanto el monto original como el convertido brinda claridad.

**Independent Test**: Given a group with a primary currency (e.g., ARS) and an expense in USD, when the expense is displayed, then both the original USD amount and the equivalent in ARS (using the transaction date's exchange rate) are shown.

**Acceptance Scenarios**:
1. Given a group with primary currency ARS and an expense in USD 50 on 2026-01-15, when displayed, then the original $50 USD and the equivalent in ARS using the exchange rate from 2026-01-15 are shown.
2. Given a group with primary currency EUR and an expense in ARS, when displayed, then the original ARS amount and the equivalent in EUR using the transaction date's rate are shown.
3. Given expenses in different currencies within the same group, when viewing balances, then each expense shows its original currency and converted amount.

## Functional Requirements

### FR-001: Registrar gasto compartido
The system MUST allow registering a shared expense with the following parameters: monto (> 0), moneda, fecha (por defecto hoy), categoría, tipo (único o recurrente), medio de pago (efectivo, transferencia o débito), quién pagó y con quiénes se comparte. La descripción es opcional. Quien pagó siempre forma parte del reparto y no puede quitarse. Sin participantes, el gasto es personal y no genera deudas.

### FR-002: Definir método de reparto
The system MUST support defining cómo se reparte el gasto: partes iguales (por defecto), importes personalizados (la suma debe coincidir con el total) o porcentajes. En el caso de porcentajes, la suma de todos los porcentajes debe ser 100%.

### FR-003: Cálculo automático de saldos
The system MUST calculate automatically, within each group, cuánto debe y cuánto debe recibir cada miembro (incluyendo personas simuladas), a partir de los gastos y pagos registrados. Los saldos se recalculan cuando se registra un gasto o un pago. Solo cuentan los gastos y participantes del grupo en cuestión. La suma de todos los saldos del grupo es cero.

### FR-004: Liquidación de deudas (simplificación)
The system MUST simplify group debts (liquidación) compensando deudas cruzadas y proponiendo el conjunto mínimo de transferencias (quién transfiere, a quién y cuánto) que deja a todos saldados. El total transferido debe coincidir con el total necesario para saldar y la propuesta no modifica el historial de gastos originales. La cantidad de transferencias propuestas es menor o igual a la cantidad de miembros con saldo distinto de cero menos uno.

### FR-005: Estados de deuda
The system MUST manage the state of each debt with the following sequence: Pendiente (estado inicial) → Pago informado → Verificado (si requiere validación) / Pagado (si no requiere validación) → Vencido. Cada deuda comienza en estado Pendiente.

### FR-006: Registrar pago de deuda
The system MUST allow registering the payment of a pending debt in which the member is involved, leaving a record in the history. If the group has "Pagos requieren validación" activated, the payment remains as "Pago informado" until an Administrator confirms it (changes to Verificado) or rejects it (the debt remains Pendiente). If disabled, the informed payment is considered confirmed (Pagado). It is not possible to register a payment on a debt that is not pending.

### FR-007: Ver deudas pendientes
The system MUST show the member how much they owe, to whom (person or group), with access to the detail and origin of each debt, deducting payments already made and verified.

### FR-008: Historial de actividad del grupo
The system MUST maintain a group activity history that includes expenses and payments, ordered chronologically.

### FR-009: Soporte multi-moneda
The system MUST operate in multiple currencies (ARS, USD and EUR as minimum): when an expense is in a currency different from the group's main currency, both the original amount and its converted equivalent are shown, using the exchange rate from the transaction date.

### FR-010: Restricciones de membresía
The system MUST prevent a member with pending balance from leaving the group. Expenses can only be shared with people who are already friends of the user or members of the group, never with strangers. The exception are simulated persons, who have no account.

## Key Entities

- **Gasto**: Registro de un gasto compartido o personal. Atributos: monto, moneda, fecha, categoría, tipo (único/recurrente), medio de pago, pagador, participantes (opcional), descripción. Relacionado con un grupo y determina deudas.

- **Deuda**: Obligación que un miembro tiene con otro (o simulado) derivada de un gasto compartido. Atributos: monto, acreedor, deudor, estado (Pendiente, Pago informado, Pagado, Verificado, Vencido), origen (gasto relacionado), pagos registrados. Pertenece a un grupo.

- **Miembro del grupo**: Participante de un grupo. Puede ser una persona con cuenta o una persona simulada (sin cuenta, cargada por nombre y mail). Los miembros simulados participan en cálculos de deudas pero no pueden recibir pagos ni tienen cuentas.

- **Grupo**: Conjunto de miembros (personas con cuenta y/o simulados) con una moneda principal y configuración opcional ("Pagos requiere validación"). Los gastos y deudas se mantienen dentro del grupo y no se comparten con personas ajenas.

- **Persona simulada**: Alguien sin cuenta, cargado por nombre y mail, que participa de los gastos y del cálculo de deudas pero no usa la aplicación. Existe para que quienes no quieren registrarse, instalar ni aprender la app puedan ser incluidos igual.

- **Pago registrado**: Registro de que una deuda ha sido pagada. Atributos: fecha, monto, quién pagó, estado resultante (según configuración del grupo).

## Success Criteria

### SC-001: Saldos consistentes en el grupo
Dado un grupo con gastos de varios pagadores, cada miembro ve de forma consistente cuánto debe o debe recibir, y la suma de todos los saldos del grupo es cero.

**Verificación**: Para cualquier grupo, sumar los saldos netos de todos los miembros (incluyendo personas simuladas); el resultado debe ser exactamente 0.

### SC-002: Liquidación mínima de transferencias
La propuesta de liquidación deja a todos saldados con una cantidad de transferencias menor o igual a la cantidad de miembros con saldo distinto de cero menos uno.

**Verificación**: Contar el número de miembros con saldo neto distinto de cero (M); el número de transferencias propuestas por el sistema de liquidación debe ser ≤ M - 1.

### SC-003: Sin cálculos manuales necesarios
Nadie tiene que hacer cuentas manuales para saber quién le debe a quién.

**Verificación**: Después de registrar gastos y pagos, cada miembro puede abrir la aplicación y ver claramente cuánto debe y a quién, sin necesidad de calcular manualmente.

### SC-004: Pago informado reflejado en estado y historial
Un pago informado queda reflejado en el estado de la deuda y en el historial, con o sin validación según la configuración del grupo.

**Verificación**: Después de registrar un pago, consultar el estado de la deuda correspondiente muestra el estado correcto ("Pagado", "Pago informado" o "Verificado") y el historial de actividad refleja el pago registrado.

## Assumptions

- Los usuarios ya tienen cuentas, amistades y grupos configurados (la gestión de esas entidades es otra feature).
- Las personas simuladas son cargadas manualmente por nombre y mail y no tienen acceso a la aplicación.
- La cotización de divisas se obtiene de una fuente externa o se fija manualmente en el momento de la transacción; el sistema no sincroniza cotizaciones en tiempo real.
- Un "Administrador de grupo" es un miembro con permisos especiales para validar o rechazar pagos cuando la configuración "Pagos requiere validación" está activada.
- Los gastos recurrentes (tipo "recurrente") se manejan como gastos separados en cada ocasión; la simplificación de deudas aplica sobre el saldo actual después de cada registro.
- La moneda principal de un grupo se define cuando se crea el grupo y puede ser ARS, USD o EUR.
- Los porcentajes de reparto se ingresan como números enteros o decimales que suman 100 en total.
- Los medios de pago (efectivo, transferencia, débito) son solo etiquetas informativas y no afectan el cálculo de montos ni el estado de las deudas.
- Las categorías de gasto son libres o de un catálogo predefinido (por definir); no afectan los cálculos de deudas.
- Las fechas de las transacciones son fechas pasadas o presentes; no se permiten fechas futuras para gastos o pagos.
</feature-specification>
