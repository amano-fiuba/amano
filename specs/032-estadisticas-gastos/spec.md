# Feature Specification: Ver estadísticas de mis gastos

**Feature Branch**: `feat/estadisticas-gastos`

**Created**: 2026-11-16

**Status**: Draft

**Input**: User story Nro 44 (estimación 3): "Como usuario quiero visualizar estadísticas sobre mis gastos para analizar cómo distribuyo mi dinero y conocer mis principales categorías de gasto. Criterios de aceptación: el sistema debe permitir acceder a una sección de estadísticas personales; debe mostrar la distribución de los gastos por categoría; debe representar la distribución mediante un gráfico de torta; el gráfico debe mostrar la proporción correspondiente a cada categoría; debe permitir identificar el importe asociado a cada categoría; las estadísticas deben basarse en los gastos registrados por el usuario; debe actualizar las estadísticas mes a mes; el backend procesará y agregará los datos de los gastos antes de enviarlos a la interfaz gráfica; las estadísticas calcularán la proporción porcentual y el monto absoluto por categoría sobre el total de gastos del período; si hay gastos en diferentes monedas, el cálculo convertirá previamente cada gasto a la moneda de preferencia del usuario/grupo antes de realizar la suma."

## Relación con otras especificaciones

- **Gastos personales, de 007 (registrar gasto) y 009 (gasto recurrente personal)**: monto, moneda, fecha del gasto y categoría. Cada serie recurrente cuenta como su primera repetición y cada repetición generada es un gasto más, igual que en la actividad personal; no hay doble conteo.
- **Gastos grupales, de los gastos de grupo (010 en adelante, con categoría desde 027, reparto desde 022/024 y recurrencia desde 028) y de los gastos con amigos (027)**: categoría, fecha del gasto, moneda del reparto y la **parte** que le corresponde a la persona en el reparto.
- **Toma de 031 (buscar en actividad personal)** los selectores de mes y año (mismo aspecto) y la regla de que el mes y el año de un gasto son los de su **fecha del gasto**.
- **Toma de 002/015 (monedas preferidas)** la moneda de la persona (ARS si no configuró ninguna) y de la conversión de la actividad personal la cotización del BCRA a la fecha del gasto.
- **Agrega una entrada "Estadísticas"** a la barra superior (015), junto a "Actividad personal".

## Fuera de alcance

- Deudas y pagos: no son gastos. Tampoco cuenta lo que la persona pagó por la parte de otros.
- Otros períodos: el año completo, rangos de fechas arbitrarios o varios meses a la vez.
- Otras agrupaciones (por medio de pago, por tipo, por grupo) y otros gráficos (barras, evolución mensual, comparación con el mes anterior).
- Filtrar por un grupo o un amigo en particular.
- Elegir en pantalla la moneda del cálculo o ver los totales en varias monedas a la vez.
- Abrir desde una categoría del gráfico la lista de sus gastos.
- Exportar o compartir las estadísticas.
- Ordenar las monedas preferidas en el perfil.

## Clarificaciones

### Session 2026-11-16

- Q: ¿Cómo se elige el período ("mes a mes")? → A: Con un selector de mes y un selector de año, como en la actividad personal. Por defecto, el mes y el año actuales.
- Q: Si la persona tiene varias monedas preferidas, ¿en cuál se suma? → A: En la primera.
- Q: ¿Entran los gastos de grupo ("moneda de preferencia del usuario/grupo")? → A: ~~No, solo los gastos personales~~ (revisado en la sesión 2).
- Q (sesión 2): ¿Se muestran los gastos grupales? ¿Cómo se distinguen de los personales? → A: Sí. Dos tortas: una para los gastos personales y otra para los grupales, cada una con su lista de categorías, su total y sus porcentajes. Arriba, el total combinado del mes.
- Q (sesión 2): De un gasto grupal, ¿qué monto cuenta? → A: La parte que le corresponde a la persona según el reparto, la haya pagado ella o no.
- Q (sesión 2): ¿Los gastos con amigos (sin grupo) cuentan? → A: Sí, como grupales, con el mismo criterio (la parte de la persona).

## Supuestos

- **"La primera" moneda preferida** es la primera según el orden del catálogo de monedas (Peso argentino, Dólar estadounidense, Euro y luego el resto), que es el orden en que la app ya lista las conversiones. Hoy las monedas preferidas se guardan sin orden, así que no existe "la primera que eligió la persona". Sin monedas preferidas se usa ARS. La misma moneda se usa para las dos tortas y el total combinado.
- La conversión usa la cotización del BCRA **a la fecha del gasto**, igual que las conversiones de la actividad personal. Un importe ya en la moneda del cálculo no se convierte.
- La parte de la persona en un gasto grupal está expresada en la moneda en que se repartió el gasto (la del grupo); se convierte desde esa moneda.
- Un gasto grupal en el que la persona no tiene parte (por ejemplo, pagó solo por otros) no cuenta en sus estadísticas.
- Los gastos grupales recurrentes cuentan como los personales: cada repetición con fecha en el mes cuenta una vez.
- El mes y el año de un gasto son los de su fecha del gasto, no los de su registro.
- Los porcentajes se muestran con un decimal; por el redondeo, la suma de los porcentajes mostrados puede diferir levemente de 100 %. Los montos se muestran con dos decimales, con el formato de montos de la app.
- Los porcentajes de cada torta se calculan sobre el total de esa torta (personal o grupal), no sobre el total combinado.
- Solo aparecen las categorías con gastos en el período; las categorías sin gastos no se muestran (ni con 0 %).
- El período elegido forma parte de la dirección de la pantalla, así que recargar o compartir el enlace lo conserva.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Ver en qué categorías gasté este mes (Priority: P1)

Lucía entra a "Estadísticas" desde la barra superior. Ve el mes actual: arriba, el
total del mes en su moneda (ARS); debajo, dos tortas, "Personales" y "Grupales",
cada una con su total y, al lado, la lista de categorías con su importe y su
porcentaje, de la que más gastó a la que menos. Así sabe que "Vivienda" se lleva el
45 % de sus gastos personales y que en los grupos gasta sobre todo en "Alimentos".

**Why this priority**: es el núcleo de la historia: acceder a la sección y ver la
distribución por categoría con proporción e importe.

**Independent Test**: con gastos personales del mes en tres categorías y gastos
grupales en dos, todos en ARS, abrir la pantalla y verificar las porciones, los
importes, los porcentajes y los totales de cada torta y el total combinado.

**Acceptance Scenarios**:

1. **Given** la persona con sesión iniciada, **When** abre "Estadísticas" desde la barra superior, **Then** ve la pantalla con los selectores de mes y año en el mes y el año actuales.
2. **Given** gastos personales del mes en ARS: Vivienda 45.000, Alimentos 35.000 y Transporte 20.000, **When** abre la pantalla, **Then** la torta "Personales" tiene tres porciones proporcionales, el total "ARS 100.000,00" y la lista "Vivienda ARS 45.000,00 · 45,0 %", "Alimentos ARS 35.000,00 · 35,0 %", "Transporte ARS 20.000,00 · 20,0 %", en ese orden.
3. **Given** además un gasto de grupo de Alimentos de ARS 90.000 repartido en partes iguales entre 3 personas, y un gasto con un amigo de Ocio de ARS 60.000 repartido en partes iguales entre 2, **When** abre la pantalla, **Then** la torta "Grupales" muestra Ocio ARS 30.000,00 · 50,0 % y Alimentos ARS 30.000,00 · 50,0 % con total ARS 60.000,00, y el total del mes es ARS 160.000,00.
4. **Given** un gasto de grupo que pagó otra persona y en el que Lucía tiene una parte de ARS 10.000, **When** abre la pantalla, **Then** esos ARS 10.000 cuentan en su torta grupal.
5. **Given** un gasto de grupo de ARS 30.000 que pagó Lucía, repartido entre ella (ARS 10.000) y dos personas más, **When** abre la pantalla, **Then** solo cuentan sus ARS 10.000, no los 30.000.
6. **Given** el gráfico, **When** la persona apunta o toca una porción, **Then** ve el nombre de la categoría, su importe y su porcentaje.
7. **Given** las dos tortas, **When** se muestran, **Then** cada categoría tiene el mismo color en las dos tortas y en las listas, y la información se puede leer sin depender del color (nombre, importe y porcentaje en texto).
8. **Given** gastos de otra persona o gastos de grupos de los que Lucía no participa en el mismo mes, **When** se calculan las estadísticas, **Then** no se cuentan.

---

### User Story 2 - Cambiar de mes (Priority: P1)

Martín quiere comparar con el mes pasado. Elige "Octubre" en el selector de mes y
ve las estadísticas de octubre; cambia el año a 2025 y ve octubre de 2025.

**Why this priority**: "actualizar las estadísticas mes a mes" es un criterio de
aceptación explícito.

**Independent Test**: con gastos en octubre y noviembre del año actual y en
octubre de 2025, cambiar mes y año y verificar que cada vez se ven solo los del
período elegido.

**Acceptance Scenarios**:

1. **Given** gastos en octubre y noviembre del año actual, **When** elige "Octubre", **Then** se consulta de inmediato y las dos tortas muestran solo los gastos de octubre.
2. **Given** "Octubre" elegido, **When** cambia el año a 2025, **Then** el mes se conserva y se ven las estadísticas de octubre de 2025.
3. **Given** gastos (personales o grupales) en 2026 y 2024 y ninguno en 2025, **When** abre el selector de año, **Then** ve "2026" y "2024", en ese orden (el año actual siempre aparece, tenga gastos o no).
4. **Given** un mes sin gastos personales ni grupales, **When** se elige, **Then** se muestra "No registraste gastos en marzo de 2026." con un enlace para registrar un gasto, sin tortas ni totales.
5. **Given** un mes con gastos personales y sin grupales, **When** se elige, **Then** se ve la torta personal y, en lugar de la grupal, "No tuviste gastos grupales en marzo de 2026."; a la inversa, "No registraste gastos personales en marzo de 2026."
6. **Given** un período elegido, **When** recarga la página, **Then** se ve el mismo período.
7. **Given** un nuevo gasto registrado (personal o grupal) en el mes elegido, **When** vuelve a abrir las estadísticas de ese mes, **Then** el gasto ya está incluido.

---

### User Story 3 - Sumar gastos en distintas monedas (Priority: P2)

Lucía tiene ARS como moneda preferida. Este mes pagó un hotel en USD y su grupo de
viaje reparte los gastos en EUR. En las estadísticas, el hotel y su parte de los
gastos del viaje se suman convertidos a pesos con la cotización del día de cada
gasto, y la pantalla aclara que los importes están expresados en ARS.

**Why this priority**: sin conversión, sumar monedas distintas da un total sin
sentido; es un criterio de aceptación explícito, pero el caso común es una sola
moneda.

**Independent Test**: con gastos del mes en ARS y en USD (personales) y una parte
grupal en EUR, con cotizaciones conocidas para sus fechas, verificar los importes
por categoría y los totales convertidos.

**Acceptance Scenarios**:

1. **Given** moneda preferida ARS, un gasto personal de ARS 10.000 en Alimentos y uno de USD 100 en Ocio con cotización 1.000 ARS por USD a su fecha, **When** abre las estadísticas, **Then** la torta personal muestra Ocio ARS 100.000,00 (90,9 %), Alimentos ARS 10.000,00 (9,1 %) y el total ARS 110.000,00.
2. **Given** moneda preferida ARS y una parte grupal de EUR 50 en Alimentos con cotización 1.200 ARS por EUR a la fecha del gasto, **When** abre las estadísticas, **Then** esa parte cuenta como ARS 60.000,00 en la torta grupal.
3. **Given** monedas preferidas USD y EUR, **When** abre las estadísticas, **Then** todos los importes y totales se expresan en USD (la primera según el orden del catálogo).
4. **Given** ninguna moneda preferida configurada, **When** abre las estadísticas, **Then** todos los importes se expresan en ARS.
5. **Given** cualquier período, **When** se muestran las estadísticas, **Then** la pantalla indica la moneda del cálculo (ej. "Importes en Peso argentino (ARS)").
6. **Given** un gasto (personal o grupal) en una moneda cuya cotización no se pudo obtener para su fecha, **When** se calculan las estadísticas, **Then** ese gasto no se suma y la pantalla avisa "1 gasto no se pudo convertir a ARS y no está incluido." (o "N gastos…").

---

### Edge Cases

- **Mes sin gastos de ningún tipo**: "No registraste gastos en <mes> de <año>." con el enlace para registrar un gasto; no se muestran tortas ni totales.
- **Mes con gastos de un solo tipo**: se muestra esa torta y, en el lugar de la otra, su mensaje de vacío (US2-5); el total combinado es el de la torta que tiene gastos.
- **Una sola categoría en una torta**: la torta es un círculo completo con esa categoría al 100,0 %.
- **Categoría con importe muy chico** (ej. 0,3 %): igual aparece en la lista con su importe y su porcentaje; en el gráfico puede ser una porción casi invisible.
- **Gasto grupal sin parte de la persona** (pagó solo por otros): no cuenta.
- **Gasto grupal sin categoría** (registrado antes de la spec 027, que agregó la categoría): cuenta en la categoría "Otros".
- **Persona que dejó un grupo**: sus partes en los gastos de ese grupo siguen contando en los meses en que las tuvo.
- **Mes futuro**: se puede elegir. No se registran gastos con fecha futura, pero las repeticiones de gastos grupales recurrentes se generan hasta 7 días antes de su fecha (028); si una cae en el mes elegido, cuenta en ese mes. Sin ellas, se muestra el mensaje de mes vacío.
- **Ningún gasto convertible** (todos en monedas sin cotización): se muestra el aviso de gastos no incluidos y el mensaje de mes vacío en lugar de las tortas.
- **Mes o año inválido en la dirección** (ej. `month=13`, `year=20x5`): se toma el mes y el año actuales.
- **Gasto recurrente** (personal o grupal): cada repetición con fecha en el mes cuenta una vez; una serie pausada o finalizada sigue contando las repeticiones ya registradas.
- **Error de red o del servidor**: mensaje fijo "No pudimos cargar tus estadísticas. Intentá nuevamente" con un botón "Reintentar", sin datos viejos.
- **Respuestas fuera de orden** al cambiar rápido de mes: solo se muestra el resultado del último período pedido.
- **Sesión expirada o cuenta no verificada**: responde como el resto de las pantallas protegidas (redirección al acceso / 401).
- **Pantalla angosta (celular)**: las tortas van una debajo de la otra, cada una con su lista debajo, a todo el ancho.

## Requirements *(mandatory)*

### Functional Requirements

**Acceso y período (US1, US2)**

- **FR-001**: La app MUST ofrecer una pantalla "Estadísticas" accesible desde la barra superior para toda persona con sesión iniciada y cuenta verificada.
- **FR-002**: La pantalla MUST ofrecer un selector de mes (enero a diciembre) y un selector de año, con el mes y el año actuales elegidos por defecto. El selector de año MUST ofrecer el año actual y cada año con al menos un gasto personal de la persona o un gasto grupal en el que tenga parte, en orden descendente.
- **FR-003**: Cambiar el mes o el año MUST volver a calcular de inmediato las estadísticas del nuevo período. El mes elegido MUST conservarse al cambiar de año.
- **FR-004**: El período elegido MUST conservarse en la dirección de la pantalla (recargar o compartir el enlace muestra el mismo período); un mes o año inválido MUST tratarse como el actual. Cambiar el período MUST NOT agregar entradas al historial del navegador.

**Cálculo (backend)**

- **FR-005**: Las estadísticas MUST calcularse en el backend, que MUST devolver a la interfaz los datos ya agregados: la moneda del cálculo, el total combinado y, por separado para los gastos personales y para los grupales, su total y, por cada categoría con gastos, su importe y su porcentaje. La interfaz MUST NOT recibir la lista de gastos individuales para calcularlos.
- **FR-006**: Los **gastos personales** del cálculo MUST ser los de la persona autenticada cuya fecha del gasto pertenezca al mes y año elegidos, por su monto completo.
- **FR-007**: Los **gastos grupales** del cálculo MUST ser los gastos de grupo y los gastos con amigos cuya fecha del gasto pertenezca al mes y año elegidos y en los que la persona tenga parte en el reparto, por el importe de **su parte** (la haya pagado ella o no). Lo que la persona pagó por la parte de otros, las deudas y los pagos MUST NOT contarse.
- **FR-008**: La moneda del cálculo MUST ser la primera moneda preferida de la persona según el orden del catálogo de monedas, o ARS si no tiene ninguna, y MUST ser la misma para los dos tipos de gasto.
- **FR-009**: Cada importe en otra moneda (el monto de un gasto personal o la parte de un gasto grupal, en la moneda en que se repartió) MUST convertirse a la moneda del cálculo con la cotización a la fecha del gasto antes de sumarse. Un importe ya en la moneda del cálculo MUST sumarse sin conversión.
- **FR-010**: Si la cotización de un importe no puede obtenerse, ese gasto MUST excluirse de todos los importes y porcentajes, y la respuesta MUST indicar cuántos gastos se excluyeron.
- **FR-011**: Para cada tipo (personal y grupal), el importe de una categoría MUST ser la suma de sus importes convertidos; el total del tipo MUST ser la suma de sus categorías; el porcentaje de una categoría MUST ser su importe sobre el total de su tipo, por 100. El total combinado MUST ser la suma de los dos totales. Los importes MUST redondearse a dos decimales y los porcentajes a un decimal.
- **FR-012**: En cada tipo, las categorías MUST devolverse ordenadas por importe descendente (a igual importe, en el orden del catálogo de categorías) y solo las que tengan importe mayor que cero. Un gasto grupal sin categoría MUST contarse como "Otros".
- **FR-013**: El backend MUST validar el mes (1 a 12) y el año (1 a 9999) recibidos y rechazar valores inválidos con errores en el formato del proyecto, en español y sin detalles internos.

**Presentación (US1, US3)**

- **FR-014**: La pantalla MUST mostrar arriba el total combinado del mes y la moneda del cálculo (nombre y código), y debajo dos gráficos de torta, "Personales" y "Grupales", cada uno con su total y una porción por categoría proporcional a su porcentaje.
- **FR-015**: Junto a cada torta MUST mostrarse la lista de sus categorías en el mismo orden, cada una con su nombre en español, su importe con la moneda, su porcentaje y el color de su porción. Apuntar o tocar una porción MUST mostrar el nombre, el importe y el porcentaje de esa categoría.
- **FR-016**: Cada categoría MUST tener siempre el mismo color, en las dos tortas y en cualquier período, y la información MUST poder leerse sin depender del color.
- **FR-017**: Si el período no tiene gastos incluidos de ningún tipo, la pantalla MUST mostrar "No registraste gastos en <mes> de <año>." con un enlace para registrar un gasto, sin tortas ni totales. Si solo falta un tipo, en el lugar de su torta MUST mostrarse "No registraste gastos personales en <mes> de <año>." o "No tuviste gastos grupales en <mes> de <año>.".
- **FR-018**: Si hubo gastos excluidos por falta de cotización (FR-010), la pantalla MUST avisarlo con su cantidad.

**Seguridad y errores**

- **FR-019**: Las estadísticas MUST requerir una sesión de una cuenta verificada, igual que la actividad personal, y MUST incluir solo gastos propios y partes propias: nunca gastos de otras personas ni de grupos de los que la persona no participa.
- **FR-020**: Ante un error de red o del servidor, la pantalla MUST mostrar "No pudimos cargar tus estadísticas. Intentá nuevamente" y un botón "Reintentar", sin datos viejos ni parciales.

### Key Entities *(include if feature involves data)*

- **Expense (gasto personal)**: entidad existente; se leen monto, moneda, fecha del gasto y categoría. No se agregan campos.
- **Gasto grupal y su reparto** (gastos de grupo y con amigos): entidades existentes; se leen la categoría, la fecha del gasto, la moneda del reparto y la parte de la persona. No se agregan campos.
- **Estadísticas del período** (calculadas, no se guardan): mes, año, moneda del cálculo, total combinado, cantidad de gastos excluidos y, para personales y para grupales, su total y, por categoría, importe y porcentaje.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Una persona identifica su categoría de mayor gasto del mes, personal o grupal, en menos de 10 segundos desde que abre la pantalla.
- **SC-002**: Para cualquier período, cada total mostrado coincide con la suma de sus importes incluidos (montos personales y partes grupales), convertidos a la moneda del cálculo, con una diferencia máxima de 0,01 por redondeo.
- **SC-003**: El 0 % de los importes incluye gastos de otras personas, partes de otras personas o gastos de grupos de los que la persona no participa.
- **SC-004**: Cambiar de mes muestra las estadísticas del nuevo período sin recargar la página.
- **SC-005**: Los tests de frontend y backend pasan, con cobertura mínima de líneas del 85 %.
