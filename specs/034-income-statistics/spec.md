# Feature Specification: Estadísticas y analíticas de ingresos personales

**Feature Branch**: `feat/income-statistics`

**Created**: 2026-11-18

**Status**: Draft

**Input**: User description: "Incorporar visualizaciones y métricas de Ingresos Personales en la sección de estadísticas, permitiendo a la persona analizar la distribución de sus ingresos por categoría y comparar sus hábitos de ingreso vs. gasto. La interfaz usa un carrusel con swipe horizontal para los gastos, un bloque de ingresos y un bloque comparativo."

## Relación con otras especificaciones

- **Extiende la pantalla de estadísticas de 032** (`/statistics`): conserva la ruta, los selectores de mes y año, la moneda del cálculo y los gráficos de torta; reorganiza los gastos en un **carrusel de dos slides**, agrega el **bloque de Ingresos personales** y un **bloque comparativo** de ingresos contra gastos.
- **Reutiliza los ingresos personales de 033**: monto, moneda, fecha del ingreso, categoría (`SALARY`, `FREELANCE`, `SALE`, `REFUND`, `OTHER`) y carácter proyectado (fecha posterior a hoy en UTC). No se agregan campos ni migraciones.
- **Reutiliza de 032** la definición de gastos personales y de la parte de la persona en gastos grupales y con amigos, la regla de moneda del cálculo, la conversión con la cotización de la fecha del movimiento y la exclusión de importes sin cotización con su aviso.
- **Reutiliza de 031/033** los selectores de mes y año, la regla de que el mes y el año de un movimiento son los de su fecha, y el listado de años con movimientos.
- **Reutiliza el componente de torta** (`PieChart`) para las categorías y agrega un gráfico de dona para la comparación y un carrusel accesible para los gastos.
- **No modifica** el balance de 033 (`/api/balance`), la actividad personal, los saldos de grupo, los presupuestos grupales ni la propuesta de liquidación.

## Fuera de alcance

- Ingresos grupales, compartidos o con amigos: los ingresos siguen siendo exclusivamente personales.
- Comparación año contra año, evolución mensual, promedios, tendencias o proyecciones de ahorro.
- Otros períodos: año completo, rangos de fechas arbitrarios o varios meses a la vez.
- Otras agrupaciones de ingresos (por medio de cobro, por moneda, por descripción) y otros gráficos distintos de las tortas por categoría y la dona comparativa.
- Reproducción automática del carrusel, desplazamiento infinito, efectos 3D o transiciones llamativas.
- Editar, eliminar o reclasificar ingresos desde la pantalla de estadísticas.
- Abrir desde una categoría del gráfico la lista de sus ingresos.
- Exportar, compartir o imprimir las estadísticas.
- Metas de ahorro, presupuestos de ingreso o recomendaciones automáticas.
- Cambiar el cálculo del balance neto de 033: la comparación de esta pantalla es informativa y no reemplaza esa tarjeta.

## Clarificaciones

### Session 2026-11-18

- Q: ¿Qué gastos resta la comparación "ingresos vs. gastos"? → A: Los gastos personales **más** la parte de la persona en los gastos grupales y con amigos, es decir, el mismo total combinado que ya muestra 032.
- Q: ¿Los ingresos proyectados (fecha futura) cuentan en la torta y en la comparación del período? → A: No. Se excluyen hasta que su fecha llegue, igual que en el balance neto de 033; solo cuentan los ingresos efectivizados.
- Q: ¿Cómo se presenta la comparación ingresos vs. gastos? → A: Con un único gráfico comparativo de ingresos y gastos, con los totales como etiquetas; no se muestra un bloque separado de tres cifras.
- Q: ¿Qué tipo de gráfico comparativo se usa? → A: Una dona de dos porciones, una para ingresos y otra para gastos, con sus importes como etiquetas y el balance del período destacado.
- Q: ¿El selector de año incluye años que solo tienen ingresos proyectados? → A: No. Solo se ofrecen años con al menos un ingreso efectivizado, un gasto personal o una parte de gasto grupal; el año actual siempre aparece.
- Q: ¿Cómo se navega entre gastos personales y grupales? → A: Con un carrusel horizontal de dos slides, puntos indicadores y flechas accesibles; el bloque de ingresos y el comparativo son bloques fijos, no slides del carrusel.
- Q: ¿El carrusel vuelve al primer slide al cambiar de período? → A: No. Conserva el slide activo al cambiar mes o año; al recargar la pantalla vuelve al Slide 1 y el slide no forma parte de la dirección.

## Supuestos

- **Orden de la pantalla**: selectores de mes y año, bloque comparativo del período, carrusel de gastos y bloque de ingresos.
- **Carrusel de gastos**: dos slides, "Gastos personales por categoría" y "Gastos grupales por categoría". Se muestra un solo slide a la vez. Tiene puntos indicadores, botones "Anterior" y "Siguiente" y admite swipe horizontal. No se reproduce automáticamente ni se envuelve: en el primer slide "Anterior" está deshabilitado y en el último "Siguiente" está deshabilitado.
- **Slide activo**: al entrar a la pantalla o recargarla se abre el Slide 1. Cambiar mes o año conserva el slide activo. Cambiar de slide no agrega entradas al historial ni modifica la dirección.
- **Bloque de ingresos**: es un bloque independiente con una sola torta, "Ingresos personales por categoría". No es un slide del carrusel y no tiene controles de navegación propios.
- **Bloque comparativo**: dona de dos porciones (ingresos y gastos combinados) con los totales como etiquetas y el balance del período destacado. Es visible siempre que el período tenga al menos un ingreso o gasto incluido.
- Los totales de cada slide y del bloque de ingresos siguen visibles en su propio bloque.
- "Mes/año" significa un mes concreto dentro de un año, igual que en 032; no se agrega un selector de año completo.
- La moneda del cálculo es la misma que usan las estadísticas de gastos y el balance: la primera moneda preferida según el orden del catálogo, o ARS si no hay ninguna. Una sola moneda para ingresos, gastos y comparación.
- Solo cuentan los **ingresos efectivizados**: los ingresos con fecha posterior a hoy (UTC) son proyectados y quedan fuera de la torta, del total de ingresos y de la comparación hasta que su fecha llegue, como en el balance de 033. Un mes futuro puede mostrarse sin ingresos aunque tenga cobros agendados.
- Cada ingreso se convierte a la moneda del cálculo con la cotización de su fecha de ingreso. Un ingreso cuya cotización no se pueda obtener se excluye de los importes y de la comparación, y la pantalla informa cuántos quedaron fuera, con el mismo criterio que 032. No se usa la cotización de hoy ni se responde con un error total por un ingreso inconvertible.
- El porcentaje de cada categoría de ingreso se calcula sobre el total de ingresos incluidos del período; los importes se redondean a dos decimales y los porcentajes a un decimal, con el formato de montos de la aplicación.
- Solo aparecen las categorías de ingreso con importe mayor que cero en el período; el catálogo completo no se muestra con ceros.
- Las categorías de ingreso tienen un color fijo por categoría, igual en cualquier período y tema, distinto del color de las categorías de gasto cuando compartan pantalla; la información nunca depende solo del color.
- Los años del selector incluyen el año actual y todo año con al menos un gasto personal, una parte de gasto grupal o un **ingreso efectivizado** de la persona, en orden descendente. Un año con solo ingresos proyectados no se ofrece, salvo que sea el año actual.
- El período elegido forma parte de la dirección de la pantalla, así que recargar o compartir el enlace lo conserva; el slide activo no forma parte de la dirección.
- El orden de las categorías en cada torta y en su lista es por importe descendente y, a igual importe, por el orden del catálogo de categorías correspondiente.
- Los importes y porcentajes se muestran con el mismo formato que en las estadísticas de gastos; la pantalla indica la moneda del cálculo.
- La comparación es informativa y de solo lectura: no crea movimientos, no ajusta saldos y no modifica el balance de 033.
- Se respeta `prefers-reduced-motion`: el cambio de slide no necesita animación para ser comprensible.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Ver en qué categorías ingresé este mes (Priority: P1)

Lucía entra a "Estadísticas" desde la barra superior y ve el bloque "Ingresos personales por categoría". Muestra el total de ingresos del mes actual en su moneda y una torta con la distribución por categoría; al lado, la lista "Sueldo", "Freelance" y "Venta" con su importe y su porcentaje, de la mayor a la menor. Así descubre que el sueldo representa el 80 % de lo que cobró este mes.

**Why this priority**: es el núcleo de la funcionalidad: ver la distribución de los ingresos personales por categoría con su proporción e importe.

**Independent Test**: con ingresos del mes en tres categorías y en ARS, abrir la pantalla y verificar el bloque de ingresos con sus porciones, importes, porcentajes y total.

**Acceptance Scenarios**:

1. **Given** una persona con sesión iniciada y cuenta verificada, **When** abre "Estadísticas", **Then** ve el bloque "Ingresos personales por categoría" del mes y el año actuales con su total y la lista de categorías.
2. **Given** ingresos del mes en ARS: Sueldo 800.000, Freelance 150.000 y Venta 50.000, **When** abre la pantalla, **Then** la torta de ingresos tiene tres porciones proporcionales, el total es "ARS 1.000.000,00" y la lista muestra "Sueldo ARS 800.000,00 · 80,0 %", "Freelance ARS 150.000,00 · 15,0 %" y "Venta ARS 50.000,00 · 5,0 %", en ese orden.
3. **Given** un ingreso de la categoría "Otros" sin descripción, **When** se calculan las estadísticas, **Then** se muestra como "Otros" con su importe y porcentaje.
4. **Given** el gráfico de ingresos, **When** la persona apunta o toca una porción, **Then** ve el nombre de la categoría, su importe y su porcentaje.
5. **Given** la torta y la lista de ingresos, **When** se muestran, **Then** cada categoría tiene el mismo color en el gráfico y en la lista, y la información se puede leer sin depender del color (nombre, importe y porcentaje en texto).
6. **Given** un ingreso de otra persona con fecha en el mismo mes, **When** se calculan las estadísticas, **Then** no se cuenta.
7. **Given** un ingreso con fecha posterior a hoy en el período elegido, **When** se calculan las estadísticas, **Then** no se cuenta en la torta, ni en el total de ingresos, ni en la comparación.
8. **Given** una sola categoría con ingresos en el período, **When** se ve la torta, **Then** es un círculo completo con esa categoría al 100,0 %.

---

### User Story 2 - Deslizar entre gastos personales y grupales sin perder el resto (Priority: P1)

Martín está revisando sus finanzas del mes. En el bloque de gastos ve el Slide 1, "Gastos personales por categoría", y desliza el dedo hacia la izquierda para pasar al Slide 2, "Gastos grupales por categoría". Los puntos indican en qué slide está y las flechas permiten el mismo recorrido con teclado o mouse. El bloque de ingresos y la comparación siguen visibles sin cambiar.

**Why this priority**: el carrusel es la forma acordada de incorporar los ingresos sin saturar la pantalla con tres tortas simultáneas y sin perder las dos vistas existentes.

**Independent Test**: con gastos personales y grupales en el mismo mes, deslizar, usar las flechas y el teclado para cambiar de slide, y verificar que solo cambia el slide de gastos, que el período se conserva y que ingresos y comparación no cambian.

**Acceptance Scenarios**:

1. **Given** la pantalla de estadísticas, **When** se abre, **Then** el carrusel de gastos muestra el Slide 1 "Gastos personales por categoría", con dos puntos indicadores y las flechas "Anterior" y "Siguiente", y "Anterior" está deshabilitada.
2. **Given** el Slide 1 visible, **When** la persona desliza horizontalmente hacia la izquierda, **Then** se muestra el Slide 2 "Gastos grupales por categoría" y el segundo punto queda activo.
3. **Given** el Slide 2 visible, **When** pulsa "Anterior" o desliza hacia la derecha, **Then** vuelve al Slide 1.
4. **Given** el carrusel enfocado, **When** la persona usa las teclas de flecha izquierda y derecha, **Then** cambia de slide y el cambio se anuncia.
5. **Given** el Slide 1 visible, **When** la persona intenta retroceder más allá del primer slide, **Then** no cambia de slide y "Anterior" sigue deshabilitada; lo mismo ocurre hacia adelante en el Slide 2.
6. **Given** un slide visible, **When** la persona cambia de mes o de año, **Then** el slide activo se conserva y sus datos se recalculan para el nuevo período.
7. **Given** el carrusel de gastos, **When** la persona cambia de slide, **Then** el bloque de ingresos, el bloque comparativo y la dirección de la pantalla no cambian.
8. **Given** la pantalla con cualquier slide, **When** se recarga, **Then** el carrusel vuelve al Slide 1 y el período se conserva.
9. **Given** un lector de pantalla, **When** recorre el carrusel, **Then** oye el nombre del slide activo, su posición ("1 de 2" o "2 de 2"), los totales y la lista de categorías.
10. **Given** una pantalla angosta, **When** se desliza sobre el carrusel, **Then** el gesto cambia de slide sin desplazar horizontalmente toda la página y sin interferir con el desplazamiento vertical.

---

### User Story 3 - Comparar ingresos y gastos del mes (Priority: P2)

Lucía quiere saber si este mes gastó más de lo que cobró. En el bloque comparativo ve una dona de dos porciones, una de ingresos y otra de gastos combinados, con sus totales como etiquetas y el balance del período destacado. Al cambiar de mes o de slide, la comparación se mantiene o se recalcula según corresponda.

**Why this priority**: es el valor diferencial de la feature ("comparar sus hábitos de ingreso vs. gasto"), pero la distribución por categoría ya entrega valor sin la comparación.

**Independent Test**: con ingresos y gastos personales y grupales conocidos en el mes, verificar que la dona muestra ambos totales y que el balance resta el total combinado de gastos; cambiar de mes y comprobar el recálculo.

**Acceptance Scenarios**:

1. **Given** ingresos incluidos por 1.000.000, gastos personales por 300.000 y una parte de gastos grupales por 100.000 en el período, **When** se abre la pantalla, **Then** la dona tiene una porción de ingresos y otra de gastos proporcionales, etiqueta "Ingresos ARS 1.000.000,00" y "Gastos ARS 400.000,00", y destaca "Balance del período +ARS 600.000,00".
2. **Given** gastos incluidos mayores que los ingresos, **When** se muestra la dona, **Then** el balance del período es negativo y se distingue con signo explícito y con un ícono o texto además del color.
3. **Given** un mes sin ingresos, **When** se muestra la dona, **Then** los ingresos son "ARS 0,00" y el balance del período es el negativo de los gastos incluidos.
4. **Given** un mes sin gastos, **When** se muestra la dona, **Then** los gastos son "ARS 0,00" y el balance del período coincide con los ingresos incluidos.
5. **Given** la comparación visible, **When** la persona cambia de slide en el carrusel de gastos, **Then** la dona no cambia (es del período, no del slide).
6. **Given** la comparación visible, **When** la persona cambia de mes o de año, **Then** la dona se recalcula para el nuevo período.
7. **Given** la comparación, **When** se muestra, **Then** indica la moneda del cálculo y no se presentan como definitivos importes que hayan quedado fuera por falta de cotización.

---

### User Story 4 - Cambiar de período y ver estados vacíos (Priority: P2)

Lucía quiere revisar los ingresos de octubre. Elige el mes en el selector y ve las estadísticas de octubre; cambia el año y conserva el mes. Si en un mes no registró ingresos, el bloque de ingresos se lo dice con una invitación a registrar el primero, sin mostrar una torta vacía ni un total engañoso.

**Why this priority**: el período es un criterio de aceptación explícito y los estados vacíos evitan interpretar una pantalla sin datos como un error.

**Independent Test**: con ingresos en distintos meses y años, cambiar los selectores y comprobar que cada bloque muestra solo el período elegido y su mensaje de vacío cuando corresponde.

**Acceptance Scenarios**:

1. **Given** ingresos en octubre y noviembre del año actual, **When** elige "Octubre", **Then** el bloque de ingresos y la dona comparativa muestran solo los movimientos de octubre.
2. **Given** "Octubre" elegido, **When** cambia el año a 2025, **Then** el mes y el slide activo se conservan y se ven las estadísticas de octubre de 2025.
3. **Given** años con gastos, años con ingresos efectivizados y años con solo ingresos proyectados, **When** abre el selector de año, **Then** ve el año actual y todos los años con al menos un gasto personal, una parte grupal o un ingreso efectivizado, en orden descendente y sin repetir, sin los años que solo tienen ingresos proyectados.
4. **Given** un mes sin ingresos efectivizados pero con gastos, **When** se abre la pantalla, **Then** el bloque de ingresos muestra "No tuviste ingresos efectivizados en marzo de 2026." con un enlace para registrar un ingreso, sin torta ni total de ingresos.
5. **Given** un mes sin gastos personales pero con gastos grupales, **When** se ve el Slide 1, **Then** muestra su mensaje de vacío; el Slide 2 sigue mostrando su torta y se puede alcanzar deslizando o con las flechas.
6. **Given** un mes sin ingresos ni gastos, **When** se abre la pantalla, **Then** se muestra un mensaje de período vacío con enlaces para registrar un gasto o un ingreso, sin carrusel, dona, tortas ni totales.
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

- **Mes sin ingresos**: el bloque de ingresos muestra su mensaje de vacío con enlace para registrar un ingreso; la dona comparativa sigue mostrando gastos y un balance del período negativo si los hay.
- **Mes sin ingresos ni gastos**: mensaje de período vacío con ambos enlaces; no se muestran carrusel, dona ni tortas.
- **Un slide de gastos sin datos**: el carrusel sigue siendo navegable y ese slide muestra su mensaje de vacío; el otro slide conserva su torta.
- **Una sola categoría de ingreso**: círculo completo con esa categoría al 100,0 %.
- **Categoría de ingreso con importe muy chico** (por ejemplo, 0,3 %): aparece en la lista con su importe y porcentaje; en el gráfico puede ser una porción casi invisible.
- **Ingreso proyectado** (fecha futura): no cuenta en la torta, el total de ingresos ni la comparación del período hasta que su fecha llegue. Si el mes futuro no tiene otros ingresos, el bloque muestra su mensaje de vacío aunque existan cobros agendados.
- **Ingreso con cotización faltante**: se excluye de la torta y de la comparación, y se informa la cantidad; el resto de la pantalla se muestra normalmente.
- **Todos los ingresos del mes sin cotización**: el bloque de ingresos muestra el aviso de excluidos y su mensaje de vacío; la comparación usa solo los importes incluidos.
- **Dona con un solo tipo con importe**: si hay ingresos y no gastos (o al revés), la dona es un círculo completo de la porción con importe y la otra se etiqueta "ARS 0,00".
- **Dona con ambos totales en cero**: no se muestra dona ni balance; rige el mensaje de período vacío.
- **Importes muy dispares**: la porción menor puede ser casi invisible, pero las etiquetas y la leyenda muestran ambos totales y el balance.
- **Carrusel en los extremos**: no hay desplazamiento circular; "Anterior" está deshabilitado en el Slide 1 y "Siguiente" en el Slide 2.
- **Swipe corto o vertical**: un gesto por debajo del umbral o predominantemente vertical no cambia de slide ni bloquea el desplazamiento vertical de la página.
- **Cambio rápido de slide o de período**: solo se muestra el slide y los datos de la última selección; las respuestas viejas se descartan.
- **Recarga o enlace compartido**: conserva el período; el carrusel vuelve al Slide 1.
- **Año con ingresos efectivizados pero sin gastos** (o al revés): el año aparece igualmente en el selector.
- **Año con solo ingresos proyectados**: no aparece en el selector, salvo que sea el año actual; si se llega por un enlace con ese año, el bloque de ingresos muestra su estado vacío.
- **Mes o año inválido en la dirección** (por ejemplo, `month=13`, `year=20x5`): se toman el mes y el año actuales.
- **Error de red o del servidor**: mensaje fijo "No pudimos cargar tus estadísticas. Intentá nuevamente" con un botón "Reintentar", sin datos viejos ni parciales. Si falla cualquiera de las consultas que alimentan la pantalla, no se muestran bloques parciales.
- **Sesión expirada o cuenta no verificada**: responde como el resto de las pantallas protegidas (redirección al acceso / 401).
- **Pantalla angosta (celular)**: el carrusel ocupa el ancho disponible, muestra un slide por vez, la torta y su lista se apilan en una columna y la dona comparativa también.
- **Lectura con lector de pantalla**: el carrusel, la torta, la lista y la dona exponen etiquetas textuales; el slide activo, los totales y el signo del balance no se comunican solo por color o posición.
- **Movimiento reducido**: con `prefers-reduced-motion`, el cambio de slide es inmediato o con una transición mínima y sigue siendo comprensible.

## Requirements *(mandatory)*

### Functional Requirements

**Acceso, carrusel y período (US1, US2, US4)**

- **FR-001**: La pantalla "Estadísticas" existente MUST incorporar un bloque de **Ingresos personales por categoría** además del carrusel de gastos y del bloque comparativo, accesible para toda persona con sesión iniciada y cuenta verificada.
- **FR-002**: La pantalla MUST organizar los gastos en un **carrusel horizontal de dos slides**: Slide 1 "Gastos personales por categoría" y Slide 2 "Gastos grupales por categoría". MUST mostrar un solo slide a la vez, puntos indicadores, botones "Anterior" y "Siguiente", y MUST permitir cambiar de slide con gesto de swipe horizontal, con los botones y con teclado. El carrusel MUST NOT reproducirse automáticamente ni envolverse: en el primer slide "Anterior" está deshabilitado y en el último "Siguiente" está deshabilitado.
- **FR-003**: Cambiar de slide MUST conservar el mes y el año elegidos y MUST NOT modificar el bloque de ingresos, el bloque comparativo ni la dirección de la pantalla.
- **FR-004**: La pantalla MUST conservar los selectores de mes (enero a diciembre) y de año, con el mes y el año actuales por defecto. El selector de año MUST ofrecer el año actual y cada año con al menos un gasto personal, una parte de gasto grupal o un **ingreso efectivizado** de la persona, en orden descendente y sin repetir; un año con solo ingresos proyectados MUST NOT ofrecerse, salvo que sea el año actual.
- **FR-005**: Cambiar el mes o el año MUST recalcular de inmediato el slide activo, el bloque de ingresos y el bloque comparativo; el mes y el slide activo MUST conservarse al cambiar de año.
- **FR-006**: El período elegido MUST conservarse en la dirección de la pantalla (recargar o compartir el enlace muestra el mismo período); un mes o un año inválido MUST tratarse como el actual. El slide activo MUST NOT formar parte de la dirección: al recargar se abre el Slide 1, y cambiar de slide o de período MUST NOT agregar entradas al historial del navegador.
- **FR-030**: El carrusel MUST exponer el slide activo con su nombre y posición ("1 de 2" o "2 de 2"), los puntos MUST indicar cuál está activo con una etiqueta accesible, y los controles MUST tener nombres accesibles ("Anterior", "Siguiente", "Ir a Gastos personales por categoría", "Ir a Gastos grupales por categoría").

**Cálculo de ingresos (backend)**

- **FR-007**: Las estadísticas de ingresos MUST calcularse en el backend, que MUST devolver a la interfaz los datos ya agregados: la moneda del cálculo, el total de ingresos incluidos y, por cada categoría con ingresos, su importe y su porcentaje. La interfaz MUST NOT recibir la lista de ingresos individuales para calcularlos.
- **FR-008**: Los ingresos del cálculo MUST ser los ingresos personales **efectivizados** (fecha del ingreso no posterior a hoy en UTC) de la persona autenticada cuya fecha del ingreso pertenezca al mes y año elegidos, por su monto. Los ingresos proyectados (fecha posterior a hoy en UTC) MUST excluirse de la torta, del total de ingresos y de la comparación.
- **FR-009**: La moneda del cálculo MUST ser la primera moneda preferida de la persona según el orden del catálogo de monedas, o ARS si no tiene ninguna, y MUST ser la misma para el carrusel de gastos, el bloque de ingresos y la comparación.
- **FR-010**: Cada ingreso en otra moneda MUST convertirse a la moneda del cálculo con la cotización a la fecha del ingreso antes de sumarse. Un ingreso ya en la moneda del cálculo MUST sumarse sin conversión.
- **FR-011**: Si la cotización de un ingreso no puede obtenerse, ese ingreso MUST excluirse de todos los importes y porcentajes de ingresos y de la comparación, y la respuesta MUST indicar cuántos ingresos se excluyeron. El resto de la pantalla MUST mostrarse normalmente.
- **FR-012**: El importe de una categoría de ingreso MUST ser la suma de sus ingresos convertidos; el total de ingresos MUST ser la suma de sus categorías; el porcentaje de una categoría MUST ser su importe sobre el total de ingresos incluidos, por 100. Los importes MUST redondearse a dos decimales y los porcentajes a un decimal, sobre los valores sin redondear.
- **FR-013**: Las categorías de ingreso MUST devolverse ordenadas por importe descendente (a igual importe, en el orden del catálogo de categorías de ingreso) y solo las que tengan importe mayor que cero.
- **FR-014**: El backend MUST validar el mes (1 a 12) y el año (1 a 9999) recibidos y rechazar valores inválidos con errores en el formato del proyecto, en español y sin detalles internos.

**Comparación ingresos vs. gastos (US3)**

- **FR-015**: La pantalla MUST mostrar un **bloque comparativo en forma de dona de dos porciones**, una para el total de ingresos incluidos y otra para el total de gastos incluidos, con ambos importes como etiquetas y el balance del período destacado, en la moneda del cálculo. La dona MUST poder interpretarse sin depender del color (etiquetas y leyenda textual).
- **FR-016**: El bloque comparativo MUST mostrar el **balance del período** (total de ingresos incluidos menos total de gastos incluidos) como etiqueta destacada, con signo explícito cuando sea positivo y cuando sea negativo y sin depender solo del color para distinguirlos. El balance MUST calcularse a partir de los totales redondeados que se muestran, para que la etiqueta coincida con ellos.
- **FR-017**: El total de gastos de la comparación MUST ser el total combinado de gastos de 032: los gastos personales por su monto completo más la parte de la persona en los gastos grupales y con amigos, con las mismas conversiones y exclusiones que los slides de gastos.
- **FR-018**: El bloque comparativo MUST ser del período y MUST permanecer igual al cambiar de slide; MUST recalcularse al cambiar de mes o de año.
- **FR-019**: El bloque comparativo MUST indicar la moneda del cálculo y MUST NOT presentar importes excluidos por falta de cotización como si estuvieran incluidos.

**Presentación (US1, US3, US5)**

- **FR-020**: El bloque de ingresos MUST mostrar una torta con una porción por categoría, proporcional a su porcentaje, y junto a ella la lista de categorías en el mismo orden, cada una con su nombre en español, su importe con la moneda, su porcentaje y el color de su porción.
- **FR-021**: Apuntar o tocar una porción de la torta de ingresos MUST mostrar el nombre de la categoría, su importe y su porcentaje.
- **FR-022**: Cada categoría de ingreso MUST tener siempre el mismo color en cualquier período y tema, y la información MUST poder leerse sin depender del color (nombre, importe y porcentaje en texto).
- **FR-023**: Si el período no tiene ingresos efectivizados, el bloque de ingresos MUST mostrar "No tuviste ingresos efectivizados en <mes> de <año>." con un enlace para registrar un ingreso, sin torta ni total de ingresos. Cada slide de gastos MUST mostrar el mensaje de vacío correspondiente de 032 cuando su tipo no tenga gastos. Si no hay ingresos ni gastos, MUST mostrarse un mensaje de período vacío con enlaces para registrar un gasto o un ingreso, sin carrusel, dona ni tortas.
- **FR-024**: Si hubo ingresos excluidos por falta de cotización (FR-011), la pantalla MUST avisarlo con su cantidad, de forma independiente del aviso de gastos excluidos de 032.
- **FR-025**: La pantalla MUST mantener la indicación de la moneda del cálculo, la navegación por teclado, las etiquetas accesibles y el diseño adaptable a pantallas angostas de 032.
- **FR-031**: El gesto de swipe MUST reconocer el desplazamiento horizontal dominante sobre el carrusel, MUST NOT activarse con un desplazamiento vertical y MUST NOT bloquear el scroll vertical de la página. Un gesto por debajo del umbral de distancia o velocidad MUST NOT cambiar de slide.

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
- **Slide de gastos** (estado de interfaz, no se persiste): índice entre 0 y 1, con el nombre del tipo que muestra.

## UI/UX

- **Ubicación**: misma pantalla `/statistics` de 032. Orden vertical: selectores de mes y año, bloque comparativo, carrusel de gastos y bloque de ingresos.
- **Bloque comparativo**: dona de dos porciones (Ingresos y Gastos) con sus totales como etiquetas y una leyenda textual, más el **balance del período** destacado con signo explícito. Es visible siempre que el período tenga movimientos incluidos.
- **Carrusel de gastos**: un slide visible, "Gastos personales por categoría" o "Gastos grupales por categoría", cada uno con su torta, su total y su lista. Debajo, dos puntos indicadores; a los lados o debajo, botones "Anterior" y "Siguiente" con nombres accesibles. Swipe horizontal, teclado y mouse producen el mismo cambio. No hay autoplay ni loop.
- **Bloque de ingresos**: torta y lista de categorías con el mismo tratamiento visual que los slides de gastos, con colores propios de las categorías de ingreso. No tiene controles de carrusel.
- **Estados**: carga, vacío por bloque, vacío total, avisos de importes excluidos y error con reintento. Los mensajes de vacío de gastos se conservan.
- **Responsive/accesibilidad**: en pantallas angostas el carrusel ocupa el ancho disponible y muestra un slide por vez; la torta y su lista se apilan; la dona comparativa también. El slide activo, la posición, los totales y el signo del balance se comunican también de forma textual.
- **Movimiento**: transición breve entre slides, desactivada o reducida con `prefers-reduced-motion`.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Una persona identifica su categoría de mayor ingreso del mes en menos de 10 segundos desde que abre la pantalla.
- **SC-002**: Para cualquier período, el total de ingresos mostrado coincide con la suma de sus importes incluidos, convertidos a la moneda del cálculo, con una diferencia máxima de 0,01 por redondeo.
- **SC-003**: El 0 % de los importes de ingresos incluye ingresos de otras personas.
- **SC-004**: El balance del período mostrado coincide con ingresos efectivizados incluidos menos gastos combinados incluidos (personales y parte de gastos grupales) del período en el 100 % de los casos de prueba.
- **SC-005**: Cambiar de slide, de mes o de año muestra los datos del nuevo período o slide sin recargar la página y sin perder el período; cambiar de slide no modifica la dirección.
- **SC-006**: Las estadísticas de gastos de 032 y el balance de 033 devuelven exactamente los mismos resultados que antes de la feature (verificado por regresión).
- **SC-007**: Los tests de frontend y backend pasan, con cobertura mínima de líneas del 85 %.
- **SC-008**: Con hasta 1000 ingresos y gastos en el período, la pantalla y sus gráficos se muestran en menos de 2 segundos desde que se elige el período, sin recargar la página.
- **SC-009**: El 100 % de los slides del carrusel se alcanza con swipe, con los botones y con el teclado, y el slide activo se anuncia con su nombre y posición.
