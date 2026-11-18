# Feature Specification: Estadísticas y analíticas de ingresos personales

**Feature Branch**: `feat/income-statistics`

**Created**: 2026-11-18

**Status**: Draft

**Input**: User description: "Incorporar visualizaciones y métricas de Ingresos Personales en la sección de estadísticas, permitiendo a la persona analizar la distribución de sus ingresos por categoría y comparar sus hábitos de ingreso vs. gasto. La interfaz muestra una barra horizontal comparativa de ingresos y gastos y dos columnas paralelas: ingresos personales y gastos (personales o grupales) por categoría."

## Relación con otras especificaciones

- **Extiende la pantalla de estadísticas de 032** (`/statistics`): conserva la ruta, los selectores de mes y año, la moneda del cálculo y los gráficos de torta; reorganiza la pantalla en **dos columnas** (ingresos a la izquierda, gastos a la derecha con pestañas Personales y Grupales), agrega el **bloque de Ingresos personales** y una **barra comparativa** de ingresos contra gastos.
- **Reutiliza los ingresos personales de 033**: monto, moneda, fecha del ingreso, categoría (`SALARY`, `FREELANCE`, `SALE`, `REFUND`, `OTHER`) y carácter proyectado (fecha posterior a hoy en UTC). No se agregan campos ni migraciones.
- **Reutiliza de 032** la definición de gastos personales y de la parte de la persona en gastos grupales y con amigos, la regla de moneda del cálculo, la conversión con la cotización de la fecha del movimiento y la exclusión de importes sin cotización con su aviso.
- **Reutiliza de 031/033** los selectores de mes y año, la regla de que el mes y el año de un movimiento son los de su fecha, y el listado de años con movimientos.
- **Reutiliza el componente de torta** (`PieChart`) para las categorías de ingresos y de gastos, y agrega una barra comparativa horizontal sin gráfico de torta. El detalle de cada porción se muestra en un tooltip al apuntar o tocar, sin texto fijo debajo del gráfico.
- **No modifica** el balance de 033 (`/api/balance`), la actividad personal, los saldos de grupo, los presupuestos grupales ni la propuesta de liquidación.

## Fuera de alcance

- Ingresos grupales, compartidos o con amigos: los ingresos siguen siendo exclusivamente personales.
- Comparación año contra año, evolución mensual, promedios, tendencias o proyecciones de ahorro.
- Otros períodos: año completo, rangos de fechas arbitrarios o varios meses a la vez.
- Otras agrupaciones de ingresos (por medio de cobro, por moneda, por descripción) y otros gráficos distintos de las tortas por categoría y la barra comparativa.
- Carruseles, gestos de swipe, reproducción automática o transiciones llamativas.
- Editar, eliminar o reclasificar ingresos desde la pantalla de estadísticas.
- Abrir desde una categoría del gráfico la lista de sus ingresos.
- Exportar, compartir o imprimir las estadísticas.
- Metas de ahorro, presupuestos de ingreso o recomendaciones automáticas.
- Cambiar el cálculo del balance neto de 033: la comparación de esta pantalla es informativa y no reemplaza esa tarjeta.

## Clarificaciones

### Session 2026-11-18

- Q: ¿Qué gastos resta la comparación "ingresos vs. gastos"? → A: Los gastos personales **más** la parte de la persona en los gastos grupales y con amigos, es decir, el mismo total combinado que ya muestra 032.
- Q: ¿Los ingresos proyectados (fecha futura) cuentan en la torta y en la comparación del período? → A: No. Se excluyen hasta que su fecha llegue, igual que en el balance neto de 033; solo cuentan los ingresos efectivizados.
- Q: ¿Cómo se presenta la comparación ingresos vs. gastos? → A: Con una barra horizontal segmentada, ingresos en verde y gastos en rojo, con el porcentaje relativo de cada uno dentro de la barra y, debajo, los totales de ingresos y de gastos y el balance neto del período.
- Q: ¿Qué tipo de gráfico comparativo se usa? → A: Una barra horizontal segmentada simple; ya no se usa una dona ni una torta comparativa.
- Q: ¿El selector de año incluye años que solo tienen ingresos proyectados? → A: No. Solo se ofrecen años con al menos un ingreso efectivizado, un gasto personal o una parte de gasto grupal; el año actual siempre aparece.
- Q: ¿Cómo se organiza la pantalla y cómo se navega entre gastos personales y grupales? → A: En dos columnas paralelas: a la izquierda los ingresos personales por categoría (torta y lista) y a la derecha los gastos por categoría (torta y lista), con dos pestañas "Personales" y "Grupales" para elegir qué gastos se ven. Reemplaza al carrusel de dos slides.
- Q: ¿La pestaña de gastos vuelve a Personales al cambiar de período? → A: No. Conserva la pestaña activa al cambiar mes o año; al recargar la pantalla vuelve a Personales y la pestaña no forma parte de la dirección.
- Q: ¿Qué muestra el detalle de una porción de torta? → A: Un tooltip al apuntar con el cursor o tocar una porción, con el nombre de la categoría, su monto y su porcentaje; no se escribe texto de ayuda ni detalle fijo debajo del gráfico.

## Supuestos

- **Orden de la pantalla**: selectores de mes y año, barra comparativa del período y dos columnas: ingresos a la izquierda y gastos a la derecha.
- **Columna de gastos**: dos pestañas, "Personales" y "Grupales", y se muestra una sola a la vez, con su torta y su total, sin lista de categorías. Las pestañas se manejan con mouse, toque y teclado (flechas izquierda y derecha); no hay autoplay ni gestos especiales.
- **Pestaña activa**: al entrar a la pantalla o recargarla se abre "Personales". Cambiar mes o año conserva la pestaña activa. Cambiar de pestaña no agrega entradas al historial ni modifica la dirección.
- **Columna de ingresos**: un bloque independiente con una sola torta, "Ingresos personales por categoría", sin pestañas.
- **Barra comparativa**: barra horizontal segmentada con una parte verde (ingresos) y una roja (gastos combinados), cada una con su porcentaje relativo dentro de la barra, y debajo los totales de ingresos y de gastos y el balance neto del período (ingresos menos gastos). Es visible siempre que el período tenga al menos un ingreso o gasto incluido.
- Los totales de cada tipo de gasto y del bloque de ingresos siguen visibles en su propio bloque.
- "Mes/año" significa un mes concreto dentro de un año, igual que en 032; no se agrega un selector de año completo.
- La moneda del cálculo es la misma que usan las estadísticas de gastos y el balance: la primera moneda preferida según el orden del catálogo, o ARS si no hay ninguna. Una sola moneda para ingresos, gastos y comparación.
- Solo cuentan los **ingresos efectivizados**: los ingresos con fecha posterior a hoy (UTC) son proyectados y quedan fuera de la torta, del total de ingresos y de la comparación hasta que su fecha llegue, como en el balance de 033. Un mes futuro puede mostrarse sin ingresos aunque tenga cobros agendados.
- Cada ingreso se convierte a la moneda del cálculo con la cotización de su fecha de ingreso. Un ingreso cuya cotización no se pueda obtener se excluye de los importes y de la comparación, y la pantalla informa cuántos quedaron fuera, con el mismo criterio que 032. No se usa la cotización de hoy ni se responde con un error total por un ingreso inconvertible.
- El porcentaje de cada categoría de ingreso se calcula sobre el total de ingresos incluidos del período; los importes se redondean a dos decimales y los porcentajes a un decimal, con el formato de montos de la aplicación.
- Solo aparecen las categorías de ingreso con importe mayor que cero en el período; el catálogo completo no se muestra con ceros.
- Las categorías de ingreso tienen un color fijo por categoría, igual en cualquier período y tema, distinto del color de las categorías de gasto cuando compartan pantalla; la información nunca depende solo del color.
- Los años del selector incluyen el año actual y todo año con al menos un gasto personal, una parte de gasto grupal o un **ingreso efectivizado** de la persona, en orden descendente. Un año con solo ingresos proyectados no se ofrece, salvo que sea el año actual.
- El período elegido forma parte de la dirección de la pantalla, así que recargar o compartir el enlace lo conserva; la pestaña activa no forma parte de la dirección.
- El orden de las categorías en cada torta y en su lista es por importe descendente y, a igual importe, por el orden del catálogo de categorías correspondiente.
- Los importes y porcentajes se muestran con el mismo formato que en las estadísticas de gastos; la pantalla indica la moneda del cálculo.
- La comparación es informativa y de solo lectura: no crea movimientos, no ajusta saldos y no modifica el balance de 033.
- Cambiar de pestaña no necesita animación para ser comprensible.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Ver en qué categorías ingresé este mes (Priority: P1)

Lucía entra a "Estadísticas" desde la barra superior y ve, en la columna izquierda, el bloque "Ingresos personales por categoría". Muestra el total de ingresos del mes actual en su moneda y una torta con la distribución por categoría; al lado, la lista "Sueldo", "Freelance" y "Venta" con su importe y su porcentaje, de la mayor a la menor. Así descubre que el sueldo representa el 80 % de lo que cobró este mes.

**Why this priority**: es el núcleo de la funcionalidad: ver la distribución de los ingresos personales por categoría con su proporción e importe.

**Independent Test**: con ingresos del mes en tres categorías y en ARS, abrir la pantalla y verificar el bloque de ingresos con sus porciones, importes, porcentajes y total.

**Acceptance Scenarios**:

1. **Given** una persona con sesión iniciada y cuenta verificada, **When** abre "Estadísticas", **Then** ve el bloque "Ingresos personales por categoría" del mes y el año actuales con su total y la lista de categorías.
2. **Given** ingresos del mes en ARS: Sueldo 800.000, Freelance 150.000 y Venta 50.000, **When** abre la pantalla, **Then** la torta de ingresos tiene tres porciones proporcionales, el total es "ARS 1.000.000,00" y la lista muestra "Sueldo ARS 800.000,00 · 80,0 %", "Freelance ARS 150.000,00 · 15,0 %" y "Venta ARS 50.000,00 · 5,0 %", en ese orden.
3. **Given** un ingreso de la categoría "Otros" sin descripción, **When** se calculan las estadísticas, **Then** se muestra como "Otros" con su importe y porcentaje.
4. **Given** el gráfico de ingresos, **When** la persona apunta o toca una porción, **Then** ve un tooltip con el nombre de la categoría, su monto y su porcentaje, y deja de verlo al apartarse.
5. **Given** la torta de ingresos, **When** se muestra, **Then** cada categoría tiene su color fijo, el nombre, el monto y el porcentaje se leen en el tooltip, y la información no depende solo del color (nombre, monto y porcentaje en el tooltip y en el nombre accesible).
6. **Given** un ingreso de otra persona con fecha en el mismo mes, **When** se calculan las estadísticas, **Then** no se cuenta.
7. **Given** un ingreso con fecha posterior a hoy en el período elegido, **When** se calculan las estadísticas, **Then** no se cuenta en la torta, ni en el total de ingresos, ni en la comparación.
8. **Given** una sola categoría con ingresos en el período, **When** se ve la torta, **Then** es un círculo completo con esa categoría al 100,0 %.

---

### User Story 2 - Alternar entre gastos personales y grupales sin perder el resto (Priority: P1)

Martín está revisando sus finanzas del mes. En la columna de gastos ve la pestaña "Personales" con su torta, y pulsa "Grupales" para ver los gastos grupales. Los ingresos, a la izquierda, y la barra comparativa siguen visibles sin cambiar.

**Why this priority**: las pestañas son la forma acordada de incorporar los ingresos sin saturar la pantalla con tres tortas simultáneas y sin perder las dos vistas existentes.

**Independent Test**: con gastos personales y grupales en el mismo mes, cambiar de pestaña con el mouse y con el teclado, y verificar que solo cambia la columna de gastos, que el período se conserva y que ingresos y comparación no cambian.

**Acceptance Scenarios**:

1. **Given** la pantalla de estadísticas, **When** se abre, **Then** la columna de gastos muestra la pestaña "Personales" seleccionada con su torta, su total y su lista, y la pestaña "Grupales" disponible.
2. **Given** la pestaña "Personales", **When** la persona pulsa "Grupales", **Then** la columna muestra los gastos grupales y "Grupales" queda seleccionada.
3. **Given** la pestaña "Grupales", **When** pulsa "Personales", **Then** vuelve a los gastos personales.
4. **Given** una pestaña enfocada, **When** la persona usa las flechas izquierda y derecha, **Then** cambia de pestaña, sin salirse de las dos existentes.
5. **Given** una pestaña activa, **When** la persona cambia de mes o de año, **Then** la pestaña activa se conserva y sus datos se recalculan para el nuevo período.
6. **Given** la columna de gastos, **When** la persona cambia de pestaña, **Then** la columna de ingresos, la barra comparativa y la dirección de la pantalla no cambian.
7. **Given** la pantalla con cualquier pestaña, **When** se recarga, **Then** vuelve a "Personales" y el período se conserva.
8. **Given** un lector de pantalla, **When** recorre las pestañas, **Then** oye que son pestañas ("tab") con su nombre y cuál está seleccionada, y el panel muestra el total y la lista de categorías.
9. **Given** una pantalla angosta, **When** se ve la pantalla, **Then** las dos columnas se apilan (ingresos primero, gastos después) sin desplazamiento horizontal de la página.

---

### User Story 3 - Comparar ingresos y gastos del mes (Priority: P2)

Lucía quiere saber si este mes gastó más de lo que cobró. En el bloque comparativo ve una barra horizontal con una parte verde de ingresos y una roja de gastos combinados, cada una con su porcentaje, y debajo los totales de ingresos y de gastos y el balance neto del período. Al cambiar de mes, la comparación se recalcula; al cambiar de pestaña, se mantiene.

**Why this priority**: es el valor diferencial de la feature ("comparar sus hábitos de ingreso vs. gasto"), pero la distribución por categoría ya entrega valor sin la comparación.

**Independent Test**: con ingresos y gastos personales y grupales conocidos en el mes, verificar que la barra muestra la proporción y ambos totales, que el balance resta el total combinado de gastos y que al cambiar de mes se recalcula.

**Acceptance Scenarios**:

1. **Given** ingresos incluidos por 1.000.000, gastos personales por 300.000 y una parte de gastos grupales por 100.000 en el período, **When** se abre la pantalla, **Then** la barra tiene una parte verde de ingresos (71,4 %) y una roja de gastos (28,6 %), con "Ingresos ARS 1.000.000,00", "Gastos ARS 400.000,00" y "Balance del período +ARS 600.000,00" debajo.
2. **Given** gastos incluidos mayores que los ingresos, **When** se muestra la barra, **Then** el balance del período es negativo y se distingue con signo explícito y con un texto además del color.
3. **Given** un mes sin ingresos, **When** se muestra la barra, **Then** los ingresos son "ARS 0,00", la barra es toda roja y el balance del período es el negativo de los gastos incluidos.
4. **Given** un mes sin gastos, **When** se muestra la barra, **Then** los gastos son "ARS 0,00", la barra es toda verde y el balance del período coincide con los ingresos incluidos.
5. **Given** la comparación visible, **When** la persona cambia de pestaña en la columna de gastos, **Then** la barra no cambia (es del período, no de la pestaña).
6. **Given** la comparación visible, **When** la persona cambia de mes o de año, **Then** la barra se recalcula para el nuevo período.
7. **Given** la comparación, **When** se muestra, **Then** indica la moneda del cálculo y no se presentan como definitivos importes que hayan quedado fuera por falta de cotización.

---

### User Story 4 - Cambiar de período y ver estados vacíos (Priority: P2)

Lucía quiere revisar los ingresos de octubre. Elige el mes en el selector y ve las estadísticas de octubre; cambia el año y conserva el mes. Si en un mes no registró ingresos, el bloque de ingresos se lo dice con una invitación a registrar el primero, sin mostrar una torta vacía ni un total engañoso.

**Why this priority**: el período es un criterio de aceptación explícito y los estados vacíos evitan interpretar una pantalla sin datos como un error.

**Independent Test**: con ingresos en distintos meses y años, cambiar los selectores y comprobar que cada bloque muestra solo el período elegido y su mensaje de vacío cuando corresponde.

**Acceptance Scenarios**:

1. **Given** ingresos en octubre y noviembre del año actual, **When** elige "Octubre", **Then** el bloque de ingresos y la barra comparativa muestran solo los movimientos de octubre.
2. **Given** "Octubre" elegido, **When** cambia el año a 2025, **Then** el mes y la pestaña activa se conservan y se ven las estadísticas de octubre de 2025.
3. **Given** años con gastos, años con ingresos efectivizados y años con solo ingresos proyectados, **When** abre el selector de año, **Then** ve el año actual y todos los años con al menos un gasto personal, una parte grupal o un ingreso efectivizado, en orden descendente y sin repetir, sin los años que solo tienen ingresos proyectados.
4. **Given** un mes sin ingresos efectivizados pero con gastos, **When** se abre la pantalla, **Then** el bloque de ingresos muestra "No tuviste ingresos efectivizados en marzo de 2026." con un enlace para registrar un ingreso, sin torta ni total de ingresos.
5. **Given** un mes sin gastos personales pero con gastos grupales, **When** se ve la pestaña "Personales", **Then** muestra su mensaje de vacío; la pestaña "Grupales" sigue mostrando su torta y se alcanza con un toque o con las flechas.
6. **Given** un mes sin ingresos ni gastos, **When** se abre la pantalla, **Then** se muestra un mensaje de período vacío con enlaces para registrar un gasto o un ingreso, sin barra comparativa, columnas, tortas ni totales.
7. **Given** un nuevo ingreso registrado en el mes elegido, **When** vuelve a abrir las estadísticas de ese mes, **Then** el ingreso ya está incluido.
8. **Given** un mes o un año inválido en la dirección, **When** se abre la pantalla, **Then** se usa el mes y el año actuales.

---

### User Story 5 - Ingresos en distintas monedas (Priority: P2)

Lucía cobra en pesos y en dólares. En el bloque de ingresos, ambos se suman convertidos a su moneda de cálculo con la cotización del día de cada cobro, y la pantalla aclara en qué moneda están expresados los importes.

**Why this priority**: sin conversión, sumar monedas distintas daría un total sin sentido; el caso común es una sola moneda.

**Independent Test**: con ingresos del mes en ARS y en USD con cotizaciones conocidas, verificar el importe por categoría, el total y la comparación.

**Acceptance Scenarios**:

1. **Given** moneda de cálculo ARS, un ingreso de ARS 100.000 en Sueldo y uno de USD 100 en Freelance con cotización 1.000 ARS por USD a su fecha, **When** abre la pantalla, **Then** la torta de ingresos muestra Sueldo ARS 100.000,00 (50,0 %) y Freelance ARS 100.000,00 (50,0 %), con total ARS 200.000,00.
2. **Given** monedas preferidas USD y EUR, **When** abre las estadísticas, **Then** los ingresos, los gastos y la comparación se expresan en USD (la primera según el orden del catálogo).
3. **Given** ninguna moneda preferida configurada, **When** abre las estadísticas, **Then** todos los importes se expresan en ARS.
4. **Given** cualquier período, **When** se muestran los ingresos, **Then** la pantalla indica la moneda del cálculo (por ejemplo, "Importes en Peso argentino (ARS)").
5. **Given** un ingreso en una moneda cuya cotización no se pudo obtener para su fecha, **When** se calculan las estadísticas, **Then** ese ingreso no se suma y la pantalla avisa "1 ingreso no se pudo convertir a ARS y no está incluido." (o "N ingresos…").
6. **Given** ingresos y gastos en monedas distintas, **When** se muestra la comparación, **Then** los tres importes usan la misma moneda de cálculo y las conversiones de la fecha de cada movimiento.

---

### Edge Cases

- **Mes sin ingresos**: el bloque de ingresos muestra su mensaje de vacío con enlace para registrar un ingreso; la barra comparativa sigue mostrando gastos y un balance del período negativo si los hay.
- **Mes sin ingresos ni gastos**: mensaje de período vacío con ambos enlaces; no se muestran barra comparativa, columnas ni tortas.
- **Un tipo de gasto sin datos**: las pestañas siguen siendo navegables y esa pestaña muestra su mensaje de vacío; la otra conserva su torta. Sin gastos de ningún tipo, la columna de gastos muestra su mensaje de vacío con enlace para registrar un gasto y sin pestañas.
- **Una sola categoría de ingreso**: círculo completo con esa categoría al 100,0 %.
- **Categoría de ingreso con importe muy chico** (por ejemplo, 0,3 %): aparece en el tooltip con su monto; en el gráfico puede ser una porción casi invisible.
- **Ingreso proyectado** (fecha futura): no cuenta en la torta, el total de ingresos ni la comparación del período hasta que su fecha llegue. Si el mes futuro no tiene otros ingresos, el bloque muestra su mensaje de vacío aunque existan cobros agendados.
- **Ingreso con cotización faltante**: se excluye de la torta y de la comparación, y se informa la cantidad; el resto de la pantalla se muestra normalmente.
- **Todos los ingresos del mes sin cotización**: el bloque de ingresos muestra el aviso de excluidos y su mensaje de vacío; la comparación usa solo los importes incluidos.
- **Barra con un solo tipo con importe**: si hay ingresos y no gastos (o al revés), la barra es toda de un color con "100,0 %" y el otro total se muestra "ARS 0,00".
- **Barra con ambos totales en cero**: no se muestra la barra ni el balance; rige el mensaje de período vacío.
- **Importes muy dispares**: el segmento menor tiene un ancho mínimo para poder leerse, y los totales y el balance siguen visibles debajo de la barra.
- **Pestañas en los extremos**: las flechas del teclado no pasan de "Personales" ni de "Grupales" ni dan la vuelta.
- **Cambio rápido de pestaña o de período**: solo se muestran los datos de la última selección; las respuestas viejas se descartan.
- **Recarga o enlace compartido**: conserva el período; la pestaña vuelve a "Personales".
- **Año con ingresos efectivizados pero sin gastos** (o al revés): el año aparece igualmente en el selector.
- **Año con solo ingresos proyectados**: no aparece en el selector, salvo que sea el año actual; si se llega por un enlace con ese año, el bloque de ingresos muestra su estado vacío.
- **Mes o año inválido en la dirección** (por ejemplo, `month=13`, `year=20x5`): se toman el mes y el año actuales.
- **Error de red o del servidor**: mensaje fijo "No pudimos cargar tus estadísticas. Intentá nuevamente" con un botón "Reintentar", sin datos viejos ni parciales. Si falla cualquiera de las consultas que alimentan la pantalla, no se muestran bloques parciales.
- **Sesión expirada o cuenta no verificada**: responde como el resto de las pantallas protegidas (redirección al acceso / 401).
- **Pantalla angosta (celular)**: las dos columnas se apilan (ingresos y luego gastos), cada torta, y la barra comparativa ocupa el ancho disponible.
- **Lectura con lector de pantalla**: la barra, las pestañas, y las tortas exponen etiquetas textuales; los totales y el signo del balance no se comunican solo por color o posición.
- **Táctil**: tocar una porción de la torta muestra su tooltip; tocar otra o apartarse lo cambia o quita.

## Requirements *(mandatory)*

### Functional Requirements

**Acceso, columnas y período (US1, US2, US4)**

- **FR-001**: La pantalla "Estadísticas" existente MUST incorporar un bloque de **Ingresos personales por categoría** además de la columna de gastos y de la barra comparativa, accesible para toda persona con sesión iniciada y cuenta verificada.
- **FR-002**: La pantalla MUST organizarse en **dos columnas paralelas** bajo la barra comparativa: a la izquierda los ingresos personales por categoría y a la derecha los gastos por categoría. La columna de gastos MUST ofrecer dos pestañas, "Personales" y "Grupales", mostrar una sola a la vez y permitir cambiarlas con mouse, toque y teclado (flechas izquierda y derecha, sin dar la vuelta). En pantallas angostas las columnas MUST apilarse.
- **FR-003**: Cambiar de pestaña MUST conservar el mes y el año elegidos y MUST NOT modificar la columna de ingresos, la barra comparativa ni la dirección de la pantalla.
- **FR-004**: La pantalla MUST conservar los selectores de mes (enero a diciembre) y de año, con el mes y el año actuales por defecto. El selector de año MUST ofrecer el año actual y cada año con al menos un gasto personal, una parte de gasto grupal o un **ingreso efectivizado** de la persona, en orden descendente y sin repetir; un año con solo ingresos proyectados MUST NOT ofrecerse, salvo que sea el año actual.
- **FR-005**: Cambiar el mes o el año MUST recalcular de inmediato la pestaña activa, la columna de ingresos y la barra comparativa; el mes y la pestaña activa MUST conservarse al cambiar de año.
- **FR-006**: El período elegido MUST conservarse en la dirección de la pantalla (recargar o compartir el enlace muestra el mismo período); un mes o un año inválido MUST tratarse como el actual. La pestaña activa MUST NOT formar parte de la dirección: al recargar se abre "Personales", y cambiar de pestaña o de período MUST NOT agregar entradas al historial del navegador.
- **FR-030**: Las pestañas MUST exponer los roles `tablist`, `tab` y `tabpanel`, indicar cuál está seleccionada y tener nombres accesibles ("Personales", "Grupales"); el panel activo MUST estar asociado a su pestaña.

**Cálculo de ingresos (backend)**

- **FR-007**: Las estadísticas de ingresos MUST calcularse en el backend, que MUST devolver a la interfaz los datos ya agregados: la moneda del cálculo, el total de ingresos incluidos y, por cada categoría con ingresos, su importe y su porcentaje. La interfaz MUST NOT recibir la lista de ingresos individuales para calcularlos.
- **FR-008**: Los ingresos del cálculo MUST ser los ingresos personales **efectivizados** (fecha del ingreso no posterior a hoy en UTC) de la persona autenticada cuya fecha del ingreso pertenezca al mes y año elegidos, por su monto. Los ingresos proyectados (fecha posterior a hoy en UTC) MUST excluirse de la torta, del total de ingresos y de la comparación.
- **FR-009**: La moneda del cálculo MUST ser la primera moneda preferida de la persona según el orden del catálogo de monedas, o ARS si no tiene ninguna, y MUST ser la misma para la columna de gastos, el bloque de ingresos y la comparación.
- **FR-010**: Cada ingreso en otra moneda MUST convertirse a la moneda del cálculo con la cotización a la fecha del ingreso antes de sumarse. Un ingreso ya en la moneda del cálculo MUST sumarse sin conversión.
- **FR-011**: Si la cotización de un ingreso no puede obtenerse, ese ingreso MUST excluirse de todos los importes y porcentajes de ingresos y de la comparación, y la respuesta MUST indicar cuántos ingresos se excluyeron. El resto de la pantalla MUST mostrarse normalmente.
- **FR-012**: El importe de una categoría de ingreso MUST ser la suma de sus ingresos convertidos; el total de ingresos MUST ser la suma de sus categorías; el porcentaje de una categoría MUST ser su importe sobre el total de ingresos incluidos, por 100. Los importes MUST redondearse a dos decimales y los porcentajes a un decimal, sobre los valores sin redondear.
- **FR-013**: Las categorías de ingreso MUST devolverse ordenadas por importe descendente (a igual importe, en el orden del catálogo de categorías de ingreso) y solo las que tengan importe mayor que cero.
- **FR-014**: El backend MUST validar el mes (1 a 12) y el año (1 a 9999) recibidos y rechazar valores inválidos con errores en el formato del proyecto, en español y sin detalles internos.

**Comparación ingresos vs. gastos (US3)**

- **FR-015**: La pantalla MUST mostrar un **bloque comparativo en forma de barra horizontal segmentada**, con una parte verde para el total de ingresos incluidos y una roja para el total de gastos incluidos, proporcionales entre sí, con el **porcentaje relativo** de cada una dentro de la barra y, debajo, el total de ingresos, el total de gastos y el balance neto del período en la moneda del cálculo. El balance MUST ir alineado a la derecha y con el mismo tamaño de texto que los totales de ingresos y de gastos. No MUST usarse una dona ni una torta para la comparación. La comparación MUST poder interpretarse sin depender del color (porcentajes, nombres y montos en texto, y una etiqueta accesible de la barra).
- **FR-016**: El bloque comparativo MUST mostrar el **balance del período** (total de ingresos incluidos menos total de gastos incluidos) como etiqueta destacada, con signo explícito cuando sea positivo y cuando sea negativo y sin depender solo del color para distinguirlos. El balance MUST calcularse a partir de los totales redondeados que se muestran, para que la etiqueta coincida con ellos.
- **FR-017**: El total de gastos de la comparación MUST ser el total combinado de gastos de 032: los gastos personales por su monto completo más la parte de la persona en los gastos grupales y con amigos, con las mismas conversiones y exclusiones que las pestañas de gastos.
- **FR-018**: El bloque comparativo MUST ser del período y MUST permanecer igual al cambiar de pestaña; MUST recalcularse al cambiar de mes o de año.
- **FR-019**: El bloque comparativo MUST indicar la moneda del cálculo y MUST NOT presentar importes excluidos por falta de cotización como si estuvieran incluidos.

**Presentación (US1, US2, US3, US5)**

- **FR-020**: El bloque de ingresos y cada pestaña de gastos MUST mostrar una torta (`PieChart`) con una porción por categoría, proporcional a su porcentaje, sin texto sobre las porciones. MUST NOT repetir las categorías como lista de texto debajo ni junto al gráfico; el nombre, el monto y el porcentaje de cada categoría se leen en el tooltip y como nombre accesible de la porción. Cada columna MUST llevar la etiqueta "Personales": en gastos como pestaña, en ingresos como etiqueta fija.
- **FR-021**: Apuntar con el cursor, enfocar con el teclado o tocar una porción de una torta MUST mostrar un tooltip con el nombre de la categoría, su monto y su porcentaje, y quitarlo al apartarse. La torta MUST NOT escribir texto de ayuda ni detalle fijo debajo del gráfico; el detalle completo (nombre, monto y porcentaje) MUST estar disponible como nombre accesible de la porción y en la lista.
- **FR-022**: Cada categoría de ingreso MUST tener siempre el mismo color en cualquier período y tema, y la información MUST poder leerse sin depender del color (nombre, importe y porcentaje en texto).
- **FR-023**: Si el período no tiene ingresos efectivizados, el bloque de ingresos MUST mostrar "No tuviste ingresos efectivizados en <mes> de <año>." con un enlace para registrar un ingreso, sin torta ni total de ingresos. Cada pestaña de gastos MUST mostrar el mensaje de vacío correspondiente de 032 cuando su tipo no tenga gastos. Si no hay ingresos ni gastos, MUST mostrarse un mensaje de período vacío con enlaces para registrar un gasto o un ingreso, sin barra comparativa, columnas ni tortas.
- **FR-024**: Si hubo ingresos excluidos por falta de cotización (FR-011), la pantalla MUST avisarlo con su cantidad, de forma independiente del aviso de gastos excluidos de 032.
- **FR-025**: La pantalla MUST mantener la indicación de la moneda del cálculo, la navegación por teclado, las etiquetas accesibles y el diseño adaptable a pantallas angostas de 032.
**Seguridad, errores y regresión**

- **FR-026**: Las estadísticas de ingresos MUST requerir una sesión de una cuenta verificada, igual que el resto de las pantallas protegidas, y MUST incluir solo ingresos propios: nunca ingresos de otras personas.
- **FR-027**: Ante un error de red o del servidor, la pantalla MUST mostrar "No pudimos cargar tus estadísticas. Intentá nuevamente" y un botón "Reintentar", sin datos viejos ni parciales.
- **FR-028**: La feature MUST NOT alterar los importes, porcentajes, totales, años ni mensajes de las estadísticas de gastos de 032, ni el balance de 033, ni los saldos, presupuestos o propuestas de liquidación de grupos.
- **FR-029**: La feature MUST NOT agregar, editar ni eliminar ingresos ni gastos; es una pantalla de solo lectura.

### Key Entities *(include if feature involves data)*

- **Ingreso personal**: entidad existente de 033; se leen monto, moneda, fecha del ingreso, categoría y carácter proyectado derivado de la fecha. No se agregan campos.
- **Categoría de ingreso**: catálogo cerrado existente (Sueldo, Freelance, Venta, Reembolso, Otros); los nombres visibles en español viven en el frontend.
- **Estadísticas de ingresos del período** (calculadas, no se guardan): mes, año, moneda del cálculo, total de ingresos incluidos, cantidad de ingresos excluidos y, por categoría, importe y porcentaje.
- **Comparación del período** (calculada, no se guarda): mes, año, moneda del cálculo, total de ingresos incluidos, total de gastos incluidos y balance del período.
- **Pestaña de gastos** (estado de interfaz, no se persiste): índice entre 0 y 1, con el nombre del tipo que muestra.

## UI/UX

- **Ubicación**: misma pantalla `/statistics` de 032. Orden vertical: selectores de mes y año, barra comparativa y dos columnas paralelas (ingresos a la izquierda, gastos a la derecha).
- **Barra comparativa**: barra horizontal segmentada (ingresos en verde, gastos en rojo) con el porcentaje de cada parte dentro de la barra; debajo, "Ingresos", "Gastos" y el **balance del período** destacado, con signo explícito y texto cuando es negativo. Es visible siempre que el período tenga movimientos incluidos.
- **Columna de ingresos**: torta sin texto sobre las porciones ni lista de categorías debajo, con colores propios de las categorías de ingreso.
- **Columna de gastos**: pestañas "Personales" y "Grupales"; el panel activo muestra su torta y su total. Los mensajes de vacío de 032 se conservan.
- **Torta**: sin texto de ayuda ni detalle fijo debajo; al apuntar, enfocar o tocar una porción aparece un tooltip con el nombre de la categoría y su monto.
- **Estados**: carga, vacío por bloque, vacío total, avisos de importes excluidos y error con reintento. Los mensajes de vacío de gastos se conservan.
- **Responsive/accesibilidad**: en pantallas angostas las columnas se apilan y la torta y su lista también; la barra ocupa el ancho disponible. La pestaña activa, los totales, los porcentajes y el signo del balance se comunican también de forma textual.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Una persona identifica su categoría de mayor ingreso del mes en menos de 10 segundos desde que abre la pantalla.
- **SC-002**: Para cualquier período, el total de ingresos mostrado coincide con la suma de sus importes incluidos, convertidos a la moneda del cálculo, con una diferencia máxima de 0,01 por redondeo.
- **SC-003**: El 0 % de los importes de ingresos incluye ingresos de otras personas.
- **SC-004**: El balance del período mostrado coincide con ingresos efectivizados incluidos menos gastos combinados incluidos (personales y parte de gastos grupales) del período en el 100 % de los casos de prueba.
- **SC-005**: Cambiar de pestaña, de mes o de año muestra los datos del nuevo período o pestaña sin recargar la página y sin perder el período; cambiar de pestaña no modifica la dirección.
- **SC-006**: Las estadísticas de gastos de 032 y el balance de 033 devuelven exactamente los mismos resultados que antes de la feature (verificado por regresión).
- **SC-007**: Los tests de frontend y backend pasan, con cobertura mínima de líneas del 85 %.
- **SC-008**: Con hasta 1000 ingresos y gastos en el período, la pantalla y sus gráficos se muestran en menos de 2 segundos desde que se elige el período, sin recargar la página.
- **SC-009**: Las dos pestañas de gastos se alcanzan con mouse, toque y teclado, y la pestaña activa se anuncia como seleccionada.
