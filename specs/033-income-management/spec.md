# Feature Specification: Ingresos personales y balance neto

**Feature Branch**: `feat/income-management`

**Created**: 2026-11-17

**Status**: Draft

**Input**: User description: "Registro e Integración de Ingresos Personales (Personal Income Management): hoy la aplicación gestiona solo egresos. Se requiere registrar ingresos personales (sueldos, freelance, ventas ocasionales, reembolsos) para calcular el balance neto y la capacidad de ahorro de la persona, únicamente en el ámbito personal."

## Relación con otras especificaciones

- **Extiende "Actividad personal" (`/activity`, 015 y 031)**: reutiliza el buscador, los selectores de mes y año, la paginación y la pantalla de detalle. La actividad pasa a tener dos vistas, **Gastos** (la actual, sin cambios) e **Ingresos**.
- **Reutiliza de 007 (registrar gasto)** las convenciones de alta: cabecera `Idempotency-Key`, validación de monto y fecha, descripción normalizada, errores en ProblemDetail en español.
- **Reutiliza de 027/022** el catálogo de monedas (`/api/currencies`) y las cotizaciones a ARS (`/api/currencies/quote`), y de la cuenta (`UserAccountResponse.currencies`) la moneda principal de la persona (la primera de su lista; ARS si la lista está vacía).
- **Reutiliza de 031** las reglas de búsqueda: coincidencia parcial sin distinguir mayúsculas ni tildes, mínimo 2 caracteres, año y mes por fecha del movimiento.
- **No modifica** nada de grupos: saldos (`/api/groups/{id}/balances`), presupuestos grupales ni propuesta de liquidación (`/api/groups/{id}/settlement-proposal`) permanecen idénticos.

## Fuera de alcance

- Ingresos en grupos o con amigos; cualquier efecto sobre saldos compartidos, presupuestos grupales o simplificación de deudas.
- Integraciones bancarias o sincronización automática de ingresos en este sprint.
- Generación automática de ingresos recurrentes (el "sueldo" se carga a mano cada vez que se cobra; no hay series, pausas ni finalización como en 009).
- Presupuestos o metas de ahorro; el balance neto es informativo.
- Edición o eliminación de ingresos o gastos registrados (los registros son inmutables para preservar el historial y la consistencia del balance).
- Importar/exportar ingresos, adjuntar comprobantes, impuestos y retenciones.
- Selectores de rango de fechas "desde/hasta" en la pantalla (el rango existe solo en la API).
- Mezclar ingresos y gastos en una sola lista cronológica (cada vista lista un solo tipo).

## Clarificaciones

### Session 2026-11-17

- Q: Al reenviar un alta con la misma `Idempotency-Key` y datos distintos, ¿qué código devuelve? → A: `422`, igual que el alta de gastos; la moneda no soportada pasa a `400` con `fields.currency`.
- Q: ¿Se pueden editar o eliminar ingresos? → A: No. Los ingresos son inmutables una vez registrados, igual que los gastos personales: no hay `PATCH`, `PUT` ni `DELETE`. Un error de carga no se corrige desde la aplicación en esta versión.
- Q: ¿Tabla propia o discriminador en `expenses`? → A: Tabla propia `incomes`. Mezclar tipos en `expenses` obligaría a filtrar ingresos en gastos de grupo, deudas y recurrentes (riesgo de regresión, principio I); ver `data-model.md`.
- Q: ¿Qué es un ingreso "recurrente"? → A: Solo una categoría de uso frecuente (Sueldo): se carga manualmente cada vez. No se automatiza.
- Q: ¿Los ingresos con fecha futura cuentan en el balance? → A: Se aceptan y se marcan como **proyectados**; no suman al balance hasta que su fecha llega (ver FR-009).
- Q: ¿En qué moneda se calcula el balance? → A: En la moneda principal de la persona; cada movimiento se convierte con la cotización de su propia fecha (la de ese día), por lo que el balance de un período cerrado no cambia con el tiempo.
- Q: ¿Qué cotización se usa para convertir cada movimiento en otra moneda al calcular el balance? → A: La de la fecha de cada movimiento, como en el formulario de gastos.
- Q: ¿Qué campos pide el formulario de ingreso además de monto, moneda, fecha y categoría? → A: Descripción opcional y un **medio de cobro obligatorio** (Efectivo, Transferencia, Billetera virtual, Otro). Sin tipo ni frecuencia.
- Q: ¿La pantalla ofrece filtro de rango "desde/hasta"? → A: No. Solo la API admite `from`/`to`; la pantalla filtra por texto, año y mes, como en gastos.
- Q: ¿El balance incluye gastos recurrentes aún no ocurridos? → A: No. Solo cuentan los gastos ya registrados en el período; las ocurrencias futuras de una serie recurrente no se restan ni se informan.
- Q: ¿Cuál es el período del balance? → A: El que esté elegido en año y mes de la actividad (por defecto, el año actual; "Todos los años" permitido).

## Supuestos

- El medio de cobro es un catálogo cerrado, subconjunto del de medios de pago de gastos: Efectivo (`CASH`), Transferencia (`TRANSFER`), Billetera virtual (`VIRTUAL_WALLET`) y Otro (`OTHER`); las tarjetas no aplican a un cobro.
- Las categorías de ingreso son un catálogo cerrado: Sueldo, Freelance, Venta, Reembolso, Otros. Los códigos persistidos son estables en inglés y los nombres visibles en español viven en el frontend, igual que en gastos.
- El año y el mes de un ingreso son los de su **fecha del ingreso** (la que elige la persona), no la de registro.
- Un ingreso conserva su moneda original; el balance usa la conversión a la moneda principal. Los ingresos proyectados no se convierten para el neto (no entran en él); su total informativo usa la cotización de su fecha o, si no existe, la de hoy. Si la cotización no está disponible, el balance no se muestra (no se inventan valores).
- "Gastos" del balance son los gastos personales ya registrados (sin ocurrencias futuras de series recurrentes; no incluye gastos de grupo ni deudas con amigos), consistentes con lo que muestra "Actividad personal".
- La condición **proyectado** (fecha del ingreso posterior a hoy) se evalúa en **UTC**, con corte a la medianoche UTC; cerca de esa hora puede diferir unas horas del "hoy" local de la persona.
- El orden por defecto del listado de ingresos es por **fecha del ingreso descendente** (el más reciente primero), con desempate por último registrado; no es configurable.
- Las monedas aceptadas para un ingreso son las del catálogo `/api/currencies`; no se limita a las preferidas de la persona.
- El monto tiene como máximo 2 decimales y un tope igual al de gastos personales; la descripción admite hasta el mismo largo que en gastos.
- No hay límite de antigüedad para la fecha; la fecha futura se admite hasta 1 año adelante.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Registrar un ingreso y ver el balance neto (Priority: P1)

Lucía cobra su sueldo. Abre "Actividad personal", pulsa "Nuevo ingreso", completa monto 850000, fecha de hoy, categoría "Sueldo", medio de cobro "Transferencia" y descripción opcional, y guarda. El ingreso aparece en la vista "Ingresos" y la tarjeta de balance neto del período se actualiza al instante: ingresos − gastos.

**Why this priority**: sin poder registrar ingresos y ver el balance no existe la funcionalidad; es el MVP.

**Independent Test**: con gastos cargados en el mes, crear un ingreso y verificar que aparece en la lista y que el balance = ingresos − gastos del período, en la moneda principal.

**Acceptance Scenarios**:

1. **Given** una persona autenticada con cuenta verificada, **When** crea un ingreso válido con `Idempotency-Key`, **Then** recibe `201` con el ingreso y el balance del período lo incluye de inmediato.
2. **Given** un ingreso de categoría "Venta" sin descripción, **When** lo guarda, **Then** se registra (la descripción es opcional).
3. **Given** el mismo `POST` repetido con la misma `Idempotency-Key` y el mismo cuerpo, **When** se reenvía, **Then** no se duplica el ingreso y se devuelve el mismo resultado.
4. **Given** la misma `Idempotency-Key` con un cuerpo distinto, **When** se envía, **Then** recibe `422`, igual que en gastos.
5. **Given** un `POST` sin `Idempotency-Key` o con una que no es UUID, **When** se envía, **Then** recibe `400`.
6. **Given** ingresos 1000 y gastos 400 en el período, **When** ve la tarjeta, **Then** muestra Ingresos 1000, Gastos 400 y Balance neto +600; si los gastos superan los ingresos, el balance se muestra negativo y diferenciado.

---

### User Story 2 - Consultar, buscar y filtrar ingresos (Priority: P1)

Lucía quiere ver cuánto cobró por freelance este año. En la vista "Ingresos" busca "freelance" y filtra por año y mes; ve los resultados paginados y abre el detalle de uno.

**Why this priority**: sin consulta no se puede verificar ni corregir lo cargado; junto con la US1 forma el núcleo de uso.

**Independent Test**: con ingresos de distintos años, categorías y descripciones, aplicar texto, año y mes (y rango en la API) y comprobar que solo aparecen los coincidentes, paginados y ordenados.

**Acceptance Scenarios**:

1. **Given** la vista "Ingresos", **When** se abre, **Then** muestra los ingresos del año actual, por fecha del ingreso descendente (el más reciente primero), en tarjetas con flecha, con el mismo buscador y selectores de mes y año de gastos.
2. **Given** ingresos con descripciones "Proyecto web" y "Venta bici", **When** busca "proyec", **Then** ve solo el primero (coincidencia parcial, sin tildes ni mayúsculas; también contra el nombre de categoría en español).
3. **Given** una consulta a la API con `from` y `to`, **When** se ejecuta, **Then** devuelve solo ingresos con fecha dentro del rango (ambos extremos incluidos); la pantalla no ofrece este filtro.
4. **Given** un texto sin coincidencias, **When** busca, **Then** ve "No se encontraron resultados".
5. **Given** más ingresos que los que caben en una página, **When** navega, **Then** ve paginación con números, "Anterior" y "Siguiente".
6. **Given** el selector de año, **When** se abre, **Then** ofrece el año actual y los años con ingresos, y "Todos los años".
7. **Given** un ingreso de la lista, **When** lo selecciona, **Then** ve su detalle con monto, moneda, equivalente en moneda principal, fecha, categoría, medio de cobro, descripción, y estado "Proyectado" si corresponde.

---

### User Story 3 - Registros inmutables y registro idempotente (Priority: P2)

Lucía registra su sueldo y, por un problema de conexión, la aplicación reintenta el envío. El ingreso queda una sola vez. Una vez registrado, el ingreso no se puede editar ni eliminar, igual que sus gastos: el historial y el balance son confiables.

**Why this priority**: garantiza que el balance no se distorsione por duplicados ni por cambios posteriores; se apoya en US1 y US2.

**Independent Test**: enviar dos veces el mismo alta con la misma clave y verificar un solo ingreso; verificar que no existen operaciones de edición ni de borrado y que la interfaz no las ofrece.

**Acceptance Scenarios**:

1. **Given** un alta ya procesada, **When** se reenvía con la misma `Idempotency-Key` y el mismo cuerpo, **Then** no se crea otro ingreso y el balance no cambia.
2. **Given** un ingreso registrado, **When** se consulta con `GET`, **Then** devuelve siempre los mismos datos con los que se registró.
3. **Given** un ingreso registrado, **When** se intenta `PATCH`, `PUT` o `DELETE` sobre él, **Then** el sistema responde `405` y el ingreso no cambia.
4. **Given** la lista o el detalle de un ingreso, **When** la persona los mira, **Then** no ve acciones de editar ni de eliminar.

---

### User Story 4 - Ingresos en otras monedas y proyectados (Priority: P2)

Lucía cobra un trabajo en USD y tiene otro cobro programado para el mes próximo. Carga ambos: el de USD se muestra con su equivalente en su moneda principal y el futuro aparece como "Proyectado" sin alterar el balance actual.

**Why this priority**: refleja la realidad del usuario, pero el flujo básico funciona sin esto.

**Independent Test**: crear un ingreso en USD y comprobar el equivalente y su aporte al balance; crear uno con fecha futura y comprobar que figura como proyectado y no suma hasta su fecha.

**Acceptance Scenarios**:

1. **Given** una moneda principal ARS y un ingreso de 100 USD, **When** ve la lista, el detalle y el balance, **Then** el monto se muestra en USD con su equivalente en ARS y el balance usa ese equivalente.
2. **Given** un ingreso con fecha futura, **When** se registra, **Then** se acepta, se marca "Proyectado" y no se suma al balance mientras su fecha sea posterior a hoy.
3. **Given** la fecha de un ingreso proyectado llega, **When** se consulta el balance, **Then** pasa a considerarse efectivizado y suma, sin acción de la persona.
4. **Given** una cotización no disponible, **When** se pide el balance, **Then** se informa el error sin mostrar un balance parcial como si fuera total.
5. **Given** una moneda fuera del catálogo, **When** se registra, **Then** recibe `400` con `fields.currency`.

---

### User Story 5 - Seguridad y errores (Priority: P1)

Un usuario malicioso o un cliente defectuoso intenta datos inválidos o acceder a ingresos ajenos.

**Why this priority**: es una regla de integridad y privacidad financiera (principios VI y VII).

**Independent Test**: probar cada error del contrato y el acceso cruzado entre dos cuentas.

**Acceptance Scenarios**:

1. **Given** un monto ≤ 0, vacío o no numérico, **When** se envía, **Then** recibe `400` con `properties.fields.amount`.
2. **Given** faltan campos obligatorios (monto, moneda, fecha, categoría, medio de cobro), **When** se envía, **Then** recibe `400` indicando cada campo.
3. **Given** una categoría fuera del catálogo o una fecha inválida, **When** se envía, **Then** recibe `400`.
4. **Given** un ingreso de otra persona, **When** se pide `GET`, **Then** recibe `404` idéntico al de un id inexistente o no numérico (no revela existencia).
5. **Given** una solicitud sin sesión, **Then** recibe `401`; con cuenta no verificada también `401`, igual que el resto de la API personal (la regla de cuenta verificada de `/api/expenses`).
6. **Given** exceso de solicitudes, **Then** recibe `429`; ante indisponibilidad de cotizaciones, `503`.
7. **Given** cualquier ingreso personal, **When** se consultan balances, presupuestos y propuesta de liquidación de un grupo, **Then** los resultados son idénticos a los de antes de la funcionalidad.

---

### Edge Cases

- Monto con más de 2 decimales (se rechaza), monto cero o negativo (se rechaza), monto por encima del tope (se rechaza).
- Fecha más de 1 año en el futuro (se rechaza); fecha hoy cuenta como efectivizada.
- Rango con `desde` posterior a `hasta` (`400`); rango combinado con año/mes (se intersectan).
- Mes sin año (`400`, como en 031); "Todos los años" deshabilita el mes.
- Persona sin ingresos: lista vacía con invitación a registrar el primero; balance = −gastos.
- Persona sin gastos: balance = ingresos.
- Cambiar la moneda principal en la cuenta: los ingresos conservan su moneda original; el balance se recalcula en la nueva.
- Un error de carga (monto o fecha equivocados) no se puede corregir: queda registrado; la persona lo ve reflejado en el balance.
- Descripción solo espacios: se trata como vacía; se recortan espacios.
- Eliminación de la cuenta: los ingresos de la persona se eliminan con ella.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: La persona autenticada con cuenta verificada MUST poder crear un ingreso personal con monto, moneda, fecha, categoría, medio de cobro y descripción opcional.
- **FR-002**: El monto MUST ser decimal mayor que 0, con hasta 2 decimales y dentro del tope; en otro caso se rechaza con `400`.
- **FR-003**: La categoría MUST pertenecer al catálogo cerrado: Sueldo, Freelance, Venta, Reembolso, Otros. El medio de cobro es obligatorio y MUST pertenecer al catálogo cerrado: Efectivo, Transferencia, Billetera virtual, Otro.
- **FR-004**: La moneda MUST pertenecer al catálogo de monedas del sistema.
- **FR-005**: El alta MUST exigir la cabecera `Idempotency-Key` (UUID); reenviar la misma clave con el mismo cuerpo MUST devolver el mismo ingreso sin duplicarlo; con distinto cuerpo MUST devolver `422`, igual que el alta de gastos.
- **FR-006**: Los ingresos MUST ser inmutables una vez registrados: la persona solo puede crearlos y consultarlos; no existe edición ni eliminación y los métodos `PATCH`, `PUT` y `DELETE` sobre un ingreso MUST responder `405`.
- **FR-007**: Un ingreso MUST pertenecer a una sola persona; todo `GET` de un ingreso ajeno, inexistente o con id no numérico MUST responder el mismo `404` neutral.
- **FR-008**: El sistema MUST ofrecer búsqueda paginada de ingresos propios por texto (descripción y nombre de categoría), año, mes y rango de fechas, con las reglas de 031, orden por fecha del ingreso descendente y desempate por registro, y un listado de años con ingresos.
- **FR-009**: Un ingreso con fecha posterior a hoy MUST aceptarse, exponerse como **proyectado** y excluirse del balance hasta que su fecha llegue; el estado se deriva de la fecha, no se guarda.
- **FR-010**: El sistema MUST calcular el **balance neto personal** del período elegido como suma de ingresos efectivizados menos suma de gastos personales, ambos convertidos a la moneda principal, y devolver también los totales por separado y el total proyectado.
- **FR-011**: El alta de un ingreso MUST reflejarse en el balance en la siguiente consulta, sin pasos manuales.
- **FR-012**: La conversión MUST usar la cotización de la fecha de cada movimiento (cotizaciones del sistema, `/api/currencies/quote`); si no están disponibles MUST responder `503` en lugar de un balance incompleto.
- **FR-013**: Los errores MUST usar ProblemDetail en español: `400` (datos), `401` (sin sesión o cuenta no verificada, como en gastos), `404` neutral, `405` (método no permitido sobre un ingreso), `422` (reutilización de una `Idempotency-Key` con otro cuerpo), `429` y `503`. Una moneda fuera del catálogo es un error de datos (`400`, campo `currency`).
- **FR-014**: Los ingresos personales MUST NOT alterar saldos de grupo, presupuestos grupales, deudas ni propuestas de liquidación.
- **FR-015**: La actividad personal MUST ofrecer una vista "Ingresos" con el buscador, selectores y paginación de 031, y una tarjeta de balance neto visible en ambas vistas.
- **FR-016**: El detalle del ingreso MUST mostrar monto en su moneda, equivalente en moneda principal, fecha, categoría, medio de cobro, descripción y estado proyectado/efectivizado, sin acciones de editar ni eliminar.
- **FR-017**: La interfaz MUST NOT ofrecer acciones de edición ni eliminación de ingresos.
- **FR-018**: La vista "Gastos" y sus endpoints (`/api/expenses/**`) MUST mantener su comportamiento actual.
- **FR-019**: El contrato REST MUST colgar de `/api` sin prefijo de versión y autenticarse con la cookie `amano_auth` (ver `contracts/personal-incomes-api.md`).

### Key Entities

- **Ingreso personal**: movimiento a favor de una persona. Atributos: dueño, monto, moneda, fecha del ingreso, categoría, medio de cobro, descripción opcional, fecha de registro. Estado derivado: proyectado (fecha futura) o efectivizado.
- **Categoría de ingreso**: catálogo cerrado (Sueldo, Freelance, Venta, Reembolso, Otros).
- **Balance neto personal**: resultado calculado (no persistido) para un período: ingresos efectivizados, ingresos proyectados, gastos y neto, en la moneda principal.
- **Clave de idempotencia de ingreso**: identifica un alta ya procesada para no duplicarla.

## UI/UX

- **Ubicación**: "Actividad personal" gana un control segmentado **Gastos | Ingresos** arriba de la lista; conserva la búsqueda y filtros en la dirección (año, mes, texto, página) como en 031. El botón primario cambia según la vista: "Nuevo gasto" / "Nuevo ingreso".
- **Tarjeta de balance neto**: sobre el buscador, siempre visible en ambas vistas, para el período elegido. Muestra tres cifras (Ingresos, Gastos, **Balance neto**) en la moneda principal; el neto es la cifra destacada, con signo explícito y color/ícono distintos para positivo y negativo (no solo color, por accesibilidad). Muestra una línea secundaria "Proyectado: +X" cuando hay ingresos futuros. Estado de carga y de error con reintento si no hay cotización.
- **Fila de ingreso**: tarjeta con borde y flecha (igual que gastos), con descripción o categoría, fecha, monto en moneda original y equivalente; insignia "Proyectado" para fechas futuras.
- **Formulario**: Reutiliza el componente DatePickerField utilizado en el registro de gastos para la selección de la fecha del movimiento, sugiriendo por defecto la fecha del día actual. A diferencia de gastos, en el modo ingresos no impone la restricción 'max' a la fecha de hoy, permitiendo seleccionar fechas futuras hasta 1 año adelante (FR-009). Si la fecha elegida es posterior a hoy, muestra el aviso de estado 'Se contará en el balance a partir de esa fecha'.
- **Vacíos**: sin ingresos, texto invitando a registrar el primero.
- **Responsive/accesibilidad**: la tarjeta se apila en una columna en pantallas angostas; controles con etiquetas y navegación por teclado.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Una persona registra un ingreso en menos de 30 segundos y ve el balance actualizado sin recargar.
- **SC-002**: El balance neto mostrado coincide con ingresos − gastos del período en el 100 % de los casos de prueba, incluyendo monedas mixtas.
- **SC-003**: El 100 % de los intentos de acceso a ingresos ajenos reciben una respuesta indistinguible de "no existe".
- **SC-004**: El 100 % de los reenvíos con la misma clave de idempotencia y el mismo cuerpo resultan en un único ingreso.
- **SC-005**: Una búsqueda sobre hasta 1000 ingresos muestra resultados en menos de 2 segundos desde la perspectiva de la persona.
- **SC-006**: Los saldos, presupuestos y propuestas de liquidación de grupos no cambian tras registrar ingresos (verificado por regresión).
- **SC-007**: Las suites de pruebas de backend y frontend siguen pasando con cobertura de líneas ≥ 85 %.
