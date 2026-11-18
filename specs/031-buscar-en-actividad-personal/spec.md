# Feature Specification: Buscar en actividad personal

**Feature Branch**: `feat/buscar-en-act-personal`

**Created**: 2026-11-16

**Status**: Draft

**Input**: User description: "Buscar en actividad personal. Como usuario quiero buscar dentro de mi actividad para encontrar rápidamente un gasto o movimiento específico. Criterios de aceptación: el sistema debe permitir ingresar un texto de búsqueda; debe buscar coincidencias en la información disponible de la actividad del usuario; los resultados deben mostrar únicamente los elementos que coincidan con el criterio ingresado; debe permitir visualizar el detalle del resultado seleccionado; si no existen coincidencias, debe informar que no se encontraron resultados; el usuario debe poder limpiar la búsqueda y volver a visualizar toda su actividad."

## Relación con otras especificaciones

- **Extiende la pantalla "Actividad personal" (`/activity`)**, introducida junto con la pantalla principal (015): hasta ahora listaba los últimos 200 gastos personales de la persona, del más reciente al más antiguo, con sus conversiones a las monedas preferidas. Esta spec la convierte en una vista por año (por defecto, el actual) y, opcionalmente, por mes, con un buscador y paginación, y hace que cada fila abra el detalle del gasto. El endpoint que alimentaba esa lista (`GET /api/expenses`, últimos 200) queda sin uso y se elimina (sesión 4).
- **Reutiliza de 007 (registrar gasto) y 009 (gasto recurrente personal)**: los datos del gasto personal (monto, moneda, fecha, categoría, medio de pago, tipo, descripción y, si es recurrente, frecuencia, estado y próxima fecha) y los catálogos de categorías y medios de pago con sus nombres visibles en español.
- **Toma de 008 (búsqueda de usuarios)** las convenciones de búsqueda: se busca al dejar de escribir (o con Enter), la coincidencia es parcial y no distingue mayúsculas, se recortan los espacios al inicio y al final y se exige un mínimo de 2 caracteres.
- **Toma de la barra superior (015)** el aspecto del campo de búsqueda (lupa dentro del campo) y de "Mis grupos" (010) el aspecto de cada fila: tarjeta con borde y flecha.
- **No modifica** la tarjeta "Actividad personal" de la pantalla principal (015), que sigue mostrando los últimos 5 gastos sin buscador.

## Fuera de alcance

- Buscar en gastos de grupo, gastos con amigos, pagos o deudas: la actividad personal se limita a los gastos personales, como hoy.
- Buscar por monto, moneda o fecha exacta; filtros combinados por categoría o medio de pago (desplegables, chips) y orden configurable.
- Editar, eliminar, pausar o finalizar el gasto desde el detalle (pausar/finalizar sigue en "Gastos recurrentes", 009).
- Sugerencias o autocompletado.
- Elegir a mano la cantidad de gastos por página o el orden.
- Filtrar por rango de fechas arbitrario (desde/hasta) o por varios meses a la vez.
- Buscador en la tarjeta de la pantalla principal o en la barra superior.

## Clarificaciones

### Session 2026-11-16

- Q: ¿Qué es la "actividad" donde se busca? → A: Solo los gastos personales que ya muestra `/activity`; "movimiento" se entiende como sinónimo de gasto personal.
- Q: ¿En qué datos se buscan coincidencias? → A: Descripción, categoría y medio de pago (por su nombre visible en español).
- Q: ¿Sobre qué conjunto se busca? → A: Sobre todos los gastos de la persona, no solo los 200 cargados. El usuario elige el año con un selector: por defecto, el año actual; puede elegir otro año o "Todos los años".
- Q: ¿El selector de año filtra también la lista sin búsqueda? → A: ~~No: aplica solo a la búsqueda~~ (revisado en la sesión 3).
- Q: ¿Cuándo se ejecuta la búsqueda? → A: ~~Al pulsar "Buscar" o Enter~~ (revisado en la sesión 2 de la misma fecha).
- Q (sesión 2): ¿Cómo se dispara la búsqueda y qué controles tiene el buscador? → A: Sin botón "Buscar": con 2 o más caracteres, mientras la persona escribe se muestra "Buscando…" y se consulta cuando deja de escribir (pausa breve); Enter consulta en el acto. Cambiar el año vuelve a consultar de inmediato. El campo lleva la lupa y el texto "Buscar por categoría, descripción, medio de pago". ~~"Limpiar" solo se ve cuando hay una búsqueda vigente~~ (reemplazado por la x del campo en la sesión 3).
- Q (sesión 3): ¿El año filtra aunque no haya texto? → A: Sí. Cambiar el año, con o sin texto, consulta de inmediato y muestra los gastos de ese año (o de todos con "Todos los años"). Se entra filtrado por el año actual, con el selector en ese año: lo que dice el selector es lo que se ve.
- Q (sesión 3): ¿Cómo se limpia la búsqueda? → A: Con la x del propio campo de búsqueda; el botón "Limpiar" se elimina. Borrar el texto conserva el año elegido y muestra todos sus gastos.
- Q (sesión 2): ¿Cómo se ve cada gasto de la actividad? → A: Como las filas de "Mis grupos": una tarjeta con borde y una flecha a la derecha.
- Q (sesión 4): ¿Cuántos gastos se muestran? → A: Ya no hay tope de 200: la lista se pagina. La cantidad por página se adapta al alto de la pantalla (las tarjetas que entran sin scroll, entre 5 y 20) y se recalcula al cambiar el tamaño de la ventana. Si hay más de una página aparece la paginación con números (1, 2, 3…), "Anterior" y "Siguiente".
- Q (sesión 4): ¿Se puede filtrar por mes? → A: Sí. Entre la barra de búsqueda y el año se agrega un selector de mes con "Todos los meses" (por defecto) y enero a diciembre. Con "Todos los años" el selector de mes se deshabilita y queda en "Todos los meses". La pantalla se ensancha si hace falta para que entren los tres controles.
- Q (sesión 4): ¿Qué pasa con `GET /api/expenses`? → A: Se elimina, junto con su cliente en el frontend: ninguna pantalla lo usa desde que la actividad consulta `/api/expenses/search`.
- Q: ¿Cómo se comparan los textos? → A: Coincidencia parcial, sin distinguir mayúsculas ni tildes, recortando espacios, con un mínimo de 2 caracteres.
- Q: ¿Cómo se ve el detalle? → A: Una pantalla propia del gasto personal, con un "Volver" que regresa a la actividad conservando la búsqueda.

## Supuestos

- El año de un gasto es el de su **fecha del gasto** (la que eligió la persona al registrarlo), no la fecha de registro en el sistema.
- El selector ofrece el año actual (siempre, aunque no tenga gastos), cada año anterior en el que la persona tenga al menos un gasto, de más reciente a más antiguo, y "Todos los años".
- Los resultados conservan el orden de la actividad: primero el último registrado. Se reparten en páginas; la cantidad por página depende del alto de la pantalla (entre 5 y 20).
- El mes de un gasto, como el año, es el de su fecha del gasto. El selector de mes ofrece siempre los 12 meses, tengan gastos o no.
- Un gasto coincide si el texto aparece en **cualquiera** de los tres campos; no se separa el texto en palabras ("cafe juan" se busca como una sola frase).
- La categoría y el medio de pago se comparan con su nombre visible en español (ej. "alimentos", "efectivo"), no con el código interno.
- Las series recurrentes y sus ocurrencias generadas aparecen en la búsqueda igual que en la lista actual, porque son gastos personales.
- La búsqueda vigente (texto, año, mes y página) forma parte de la dirección de la pantalla, de modo que "Volver" desde el detalle, el botón Atrás del navegador o recargar la página la conservan. Si al recargar la pantalla tiene otro alto, la página indica la misma posición aproximada con la nueva cantidad por página.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Buscar un gasto en mi actividad (Priority: P1)

Lucía quiere encontrar cuánto pagó en la farmacia este año. Entra a "Actividad
personal", escribe "farma" y pulsa "Buscar". La lista pasa a mostrar solo los
gastos del año actual cuya descripción, categoría o medio de pago contiene ese
texto, sin importar mayúsculas ni tildes. Si busca "xyz", ve "No se encontraron
resultados". Al borrar el texto con la x del campo, vuelve a ver toda su actividad del año.

**Why this priority**: es el núcleo de la historia: ingresar un texto, ver solo lo
que coincide, saber cuándo no hay nada y volver a la actividad del año.

**Independent Test**: con gastos cuya descripción, categoría y medio de pago son
distintos, buscar un texto que coincida con uno solo de cada campo y verificar que
aparecen únicamente esos gastos; buscar un texto inexistente y ver el mensaje de
sin resultados; limpiar y verificar que vuelve la lista completa.

**Acceptance Scenarios**:

1. **Given** la pantalla "Actividad personal", **When** la persona la abre, **Then** ve un campo de búsqueda con lupa y el texto "Buscar por categoría, descripción, medio de pago", un selector de mes en "Todos los meses", un selector de año con el año actual elegido y debajo la primera página de sus gastos del año actual, cada gasto como una tarjeta con flecha.
2. **Given** gastos del año actual con descripciones "Farmacia centro" y "Supermercado", **When** escribe "farma", **Then** mientras escribe ve "Buscando…" y, cuando deja de escribir, ve solo "Farmacia centro".
3. **Given** un gasto de categoría "Alimentos" sin descripción y otro de categoría "Transporte", **When** busca "alim", **Then** ve solo el de categoría "Alimentos".
4. **Given** un gasto pagado en "Efectivo" y otro con "Tarjeta de débito", **When** busca "efectivo", **Then** ve solo el pagado en efectivo.
5. **Given** un gasto con descripción "Café con Juan", **When** busca "CAFE", "café" o "  cafe  ", **Then** en los tres casos lo encuentra.
6. **Given** una búsqueda sin coincidencias, **When** termina, **Then** la pantalla muestra "No se encontraron resultados" y no muestra ningún gasto.
7. **Given** una búsqueda con resultados o sin ellos, **When** borra el texto con la x del campo (o a mano), **Then** el campo queda vacío, el año elegido se conserva y se ven de inmediato todos los gastos de ese año.
8. **Given** el campo con un solo carácter (tras recortar espacios), **When** deja de escribir o pulsa Enter, **Then** no se ejecuta ninguna búsqueda, se muestra "Ingresá al menos 2 caracteres para buscar" y se ven todos los gastos del año elegido.

---

### User Story 2 - Acotar o ampliar por año y mes (Priority: P2)

Martín no encuentra el pago del seguro del auto porque fue el año pasado. Cambia el
selector de año a 2025 (o a "Todos los años") y vuelve a buscar: ahora aparece.

**Why this priority**: la búsqueda en el año actual ya resuelve el caso más común;
ampliar el rango cubre los gastos más viejos, que la lista de 200 no alcanza.

**Independent Test**: con un gasto de un año anterior que coincide con el texto,
buscar con el año actual (no aparece), con ese año (aparece) y con "Todos los años"
(aparece junto con los del año actual que coincidan).

**Acceptance Scenarios**:

1. **Given** gastos con fecha en 2026 y 2024 y ninguno en 2025, **When** abre el selector de año, **Then** ve "2026", "2024" y "Todos los años", en ese orden.
2. **Given** un gasto "Seguro auto" con fecha de 2025, **When** busca "seguro" con el año 2026, **Then** no aparece; **When** cambia el año a 2025, **Then** se vuelve a consultar sin más acciones y aparece.
3. **Given** gastos que coinciden en varios años, **When** busca con "Todos los años", **Then** aparecen todos los que coinciden, sin importar el año.
4. **Given** una persona sin gastos en el año actual, **When** abre el selector, **Then** el año actual sigue presente y elegido por defecto.
5. **Given** una búsqueda vigente, **When** cambia solo el año, **Then** se vuelve a consultar de inmediato con el mismo texto y el año nuevo.
6. **Given** el campo vacío, **When** elige otro año (o "Todos los años"), **Then** se consulta de inmediato y se ven todos los gastos de ese año (o de todos), sin escribir nada.
7. **Given** gastos de marzo y de abril de 2026, **When** elige el mes "Marzo" con el año 2026, **Then** se consulta de inmediato y se ven solo los de marzo de 2026; con texto, solo los de marzo que coinciden.
8. **Given** el mes "Marzo" elegido, **When** cambia el año a 2025, **Then** el mes se conserva y se ven los gastos de marzo de 2025.
9. **Given** el mes "Marzo" elegido, **When** elige "Todos los años", **Then** el selector de mes vuelve a "Todos los meses", queda deshabilitado y se ven los gastos de todos los años.
10. **Given** un mes sin gastos y sin texto, **When** se consulta, **Then** se muestra "No registraste gastos en marzo de 2026." con el enlace para registrar uno.

---

### User Story 4 - Recorrer la actividad por páginas (Priority: P2)

Lucía tiene 60 gastos este año. En su computadora ve, por ejemplo, 9 tarjetas que
entran en la pantalla sin hacer scroll y debajo la paginación "Anterior 1 2 3 … 7
Siguiente". Al tocar "2" ve los 9 siguientes. En el celular entran menos tarjetas
por página y hay más páginas.

**Why this priority**: sin tope de 200, la lista puede ser larga; la paginación la
mantiene manejable y permite llegar a cualquier gasto.

**Independent Test**: con más gastos de los que entran en una página, verificar
la cantidad por página según el alto de la ventana, navegar entre páginas, volver
del detalle a la misma página y cambiar el texto, el mes o el año (vuelve a la 1).

**Acceptance Scenarios**:

1. **Given** más gastos de los que entran en una página, **When** se muestra la lista, **Then** se ven tantas tarjetas como entran en el alto disponible (entre 5 y 20) y debajo la paginación con los números de página, "Anterior" y "Siguiente".
2. **Given** todos los gastos entran en una página, **When** se muestra la lista, **Then** no se muestra paginación.
3. **Given** la página 1, **When** toca "2" o "Siguiente", **Then** ve la página 2, la página actual queda marcada y la lista vuelve a su inicio. En la primera página "Anterior" está deshabilitado; en la última, "Siguiente".
4. **Given** muchas páginas, **When** se muestra la paginación, **Then** siempre se ven la primera, la última, la actual y sus vecinas, y los saltos se indican con "…".
5. **Given** la página 3, **When** cambia el texto, el mes o el año, **Then** vuelve a la página 1 del nuevo criterio.
6. **Given** la página 3, **When** abre un gasto y pulsa "Volver", **Then** regresa a la página 3 con el mismo criterio.
7. **Given** la lista paginada, **When** cambia el alto de la ventana, **Then** la cantidad por página se recalcula y se muestra la página que contiene el primer gasto que estaba viendo.
8. **Given** una búsqueda con texto, **When** termina, **Then** el contador "N resultados" indica el total de coincidencias, no solo las de la página.

---

### User Story 3 - Ver el detalle de un gasto (Priority: P1)

Desde los resultados (o desde la lista completa), Lucía toca un gasto y ve su
detalle: monto, moneda y conversiones, fecha, categoría, medio de pago, tipo,
descripción y, si es recurrente, frecuencia, estado y próxima fecha. Al pulsar
"Volver" regresa a la actividad con la misma búsqueda y los mismos resultados.

**Why this priority**: es un criterio de aceptación explícito y hoy no existe una
vista de detalle de gasto personal.

**Independent Test**: buscar, abrir un resultado, verificar todos sus datos,
volver y verificar que el texto, el año y los resultados se conservan; abrir por
URL el detalle de un gasto de otra persona y verificar que no se muestra.

**Acceptance Scenarios**:

1. **Given** una lista de actividad (con o sin búsqueda), **When** la persona toca un gasto, **Then** se abre su detalle.
2. **Given** el detalle de un gasto único, **When** se muestra, **Then** incluye monto, moneda, conversiones a las monedas preferidas (si existen), fecha del gasto, categoría, medio de pago, tipo y descripción (o la indicación de que no tiene).
3. **Given** el detalle de un gasto recurrente, **When** se muestra, **Then** además incluye la frecuencia, el estado (Activo, Pausado o Finalizado) y, si está activo, la próxima fecha.
4. **Given** el detalle abierto desde una búsqueda, **When** pulsa "Volver" o el botón Atrás del navegador, **Then** regresa a la actividad con el mismo texto, el mismo año y los mismos resultados.
5. **Given** el identificador de un gasto que no existe o que pertenece a otra persona, **When** se intenta abrir su detalle, **Then** se muestra "No encontramos ese gasto" con un enlace para volver a la actividad, sin revelar si el gasto existe.

---

### Edge Cases

- **Texto vacío o solo espacios**: no hay filtro de texto; se ven todos los gastos del año elegido. **1 carácter**: no se busca, se muestra el aviso de mínimo 2 caracteres y se ven todos los gastos del año elegido.
- **Año sin gastos y sin texto**: se muestra "No registraste gastos en <año>." con el enlace para registrar uno; con "Todos los años" y ningún gasto, "Todavía no registraste gastos."
- **Caracteres especiales** (`%`, `_`, `'`, comillas): se buscan como texto literal, nunca como comodines ni rompen la búsqueda.
- **Texto muy largo**: se limita a 100 caracteres en el campo; el backend rechaza textos más largos con un error de validación en el formato del proyecto.
- **Gasto sin descripción**: puede encontrarse por su categoría o medio de pago; en el detalle se indica que no tiene descripción.
- **Muchos gastos**: no hay tope; se reparten en páginas (US4).
- **Página inexistente en la dirección** (ej. `page=99` con 3 páginas, o `page=abc`): se muestra la última página existente; un valor no numérico se trata como la página 1.
- **Pantalla muy baja o muy alta**: la cantidad por página nunca baja de 5 ni supera 20.
- **Mes con "Todos los años" en la dirección**: el mes se ignora.
- **Pantalla angosta (celular)**: el campo de búsqueda ocupa todo el ancho y los selectores de mes y año quedan debajo, uno al lado del otro.
- **Año sin coincidencias pero con coincidencias en otros años**: se muestra "No se encontraron resultados"; el selector queda visible para ampliar el rango.
- **Recarga de la página o enlace compartido con búsqueda**: se repite la búsqueda indicada en la dirección (texto, año, mes y página); un año inválido en la dirección se trata como el año actual, un mes inválido como "Todos los meses" y un texto inválido se ignora.
- **Error de red o del servidor al buscar**: se muestra un mensaje fijo de reintento con un botón "Reintentar", sin resultados viejos ni parciales.
- **Error al cargar el detalle**: se muestra un mensaje de reintento y el enlace para volver a la actividad.
- **Sesión expirada o cuenta no verificada**: la búsqueda y el detalle responden como el resto de las pantallas protegidas (redirección al acceso / 401).
- **Escritura rápida o respuestas fuera de orden**: se consulta una sola vez por pausa y solo se muestran los resultados de la última búsqueda pedida.
- **Historial del navegador**: escribir, cambiar el año o el mes y cambiar de página no agregan entradas al historial; Atrás desde el detalle vuelve a la búsqueda vigente.

## Requirements *(mandatory)*

### Functional Requirements

**Búsqueda (US1, US2)**

- **FR-001**: La pantalla "Actividad personal" MUST ofrecer un campo de texto de búsqueda (máximo 100 caracteres) con lupa y el texto "Buscar por categoría, descripción, medio de pago", un selector de mes y un selector de año, en ese orden. No hay botón "Buscar": con 2 o más caracteres, la búsqueda MUST ejecutarse cuando la persona deja de escribir (una consulta por pausa) o al pulsar Enter, y mientras escribe MUST mostrarse "Buscando…". Cambiar el año o el mes MUST volver a consultar de inmediato.
- **FR-002**: Antes de buscar, el texto MUST recortarse; si queda con menos de 2 caracteres, MUST NOT filtrarse por texto y la pantalla MUST mostrar todos los gastos del año elegido; con exactamente 1 carácter MUST mostrarse "Ingresá al menos 2 caracteres para buscar".
- **FR-003**: La búsqueda MUST abarcar únicamente los gastos personales de la persona autenticada y MUST encontrar los que contengan el texto en su descripción, en el nombre visible de su categoría o en el nombre visible de su medio de pago.
- **FR-004**: La comparación MUST ser parcial y no distinguir mayúsculas, minúsculas ni tildes. Los caracteres especiales MUST tratarse como texto literal.
- **FR-005**: La búsqueda MUST realizarse sobre todos los gastos de la persona que cumplan el año y el mes elegidos, sin tope de cantidad.
- **FR-006**: El selector de año MUST ofrecer el año actual, cada año anterior con al menos un gasto de la persona (por fecha del gasto) en orden descendente y "Todos los años". Por defecto MUST estar elegido el año actual.
- **FR-007**: Con un año elegido, solo MUST considerarse los gastos cuya fecha del gasto pertenezca a ese año (y, si hay un mes elegido, a ese mes de ese año); con "Todos los años", MUST considerarse todos.
- **FR-008**: Los resultados MUST mostrar únicamente los gastos que coincidan, con la misma presentación y el mismo orden de la lista de actividad (primero el último registrado), repartidos en páginas (FR-021).
- **FR-009**: Si no hay coincidencias, la pantalla MUST mostrar "No se encontraron resultados" y ningún gasto.
- **FR-010**: La búsqueda MUST poder limpiarse con la x del campo (no hay botón "Limpiar"). Al vaciar el texto, la pantalla MUST mostrar de inmediato todos los gastos del año elegido.
- **FR-011**: El texto, el año, el mes y la página de la búsqueda vigente MUST conservarse al volver desde el detalle, al usar Atrás o al recargar la página.
- **FR-012**: El selector de año MUST filtrar la actividad aunque no haya texto: al entrar se muestran los gastos del año actual y cambiar el año MUST consultar de inmediato y mostrar los gastos de ese año (o de todos con "Todos los años"). Lo que indica el selector MUST coincidir siempre con lo que se ve.
- **FR-020**: Entre el campo de búsqueda y el selector de año MUST haber un selector de mes con "Todos los meses" (por defecto) y los meses de enero a diciembre. Con "Todos los años" MUST estar deshabilitado y en "Todos los meses"; al pasar a "Todos los años" con un mes elegido, el mes MUST volver a "Todos los meses". Al cambiar de un año a otro, el mes elegido MUST conservarse.

**Paginación (US4)**

- **FR-021**: La actividad MUST mostrarse por páginas. Si hay más de una página, debajo de la lista MUST mostrarse la paginación: "Anterior", los números de página y "Siguiente", con la página actual marcada; "Anterior" deshabilitado en la primera y "Siguiente" en la última. Con más de 7 páginas MUST mostrarse la primera, la última, la actual y sus vecinas, y "…" en los saltos. Con una sola página MUST NOT mostrarse.
- **FR-022**: La cantidad de gastos por página MUST calcularse según el alto disponible de la pantalla debajo de los filtros (las tarjetas que entran sin scroll), con un mínimo de 5 y un máximo de 20, y MUST recalcularse al cambiar el tamaño de la ventana, manteniendo a la vista el primer gasto que se estaba viendo.
- **FR-023**: Cambiar el texto, el año o el mes MUST volver a la página 1. Cambiar de página MUST llevar la vista al inicio de la lista. El contador "N resultados" (con texto) MUST indicar el total de coincidencias.

**Detalle (US3)**

- **FR-013**: Cada gasto de la actividad, con o sin búsqueda, MUST mostrarse como una tarjeta con flecha (como las filas de "Mis grupos"), con descripción (o categoría), monto, moneda, conversiones, fecha, categoría y medio de pago, y MUST poder abrirse para ver su detalle.
- **FR-014**: El detalle MUST mostrar monto, moneda, conversiones a las monedas preferidas (si existen), fecha del gasto, categoría, medio de pago, tipo y descripción (o "Sin descripción"); para un gasto recurrente, además frecuencia, estado y, si está activo, próxima fecha.
- **FR-015**: El detalle MUST ofrecer "Volver", que regresa a la actividad conservando la búsqueda vigente (FR-011).
- **FR-016**: Una persona MUST poder ver solo el detalle de sus propios gastos. Pedir el detalle de un gasto inexistente o ajeno MUST responder igual ("No encontramos ese gasto"), sin revelar su existencia.

**Seguridad y errores**

- **FR-017**: La búsqueda y el detalle MUST requerir una sesión de una cuenta verificada, igual que el resto de la actividad personal.
- **FR-018**: El backend MUST validar el texto (longitud) y el año recibidos y rechazar valores inválidos con errores en el formato del proyecto, en español y sin detalles internos.
- **FR-019**: Ante un error de red o del servidor, la pantalla MUST mostrar un mensaje fijo de reintento con un botón "Reintentar", sin resultados viejos ni parciales.

**Limpieza**

- **FR-024**: El endpoint `GET /api/expenses` (últimos 200 gastos) y su cliente en el frontend MUST eliminarse, porque ninguna pantalla los usa. `POST /api/expenses` no cambia.

### Key Entities *(include if feature involves data)*

- **Expense (gasto personal)**: entidad existente; la búsqueda lee su descripción, categoría, medio de pago, fecha del gasto y fecha de registro. No se agregan campos.
- **Criterio de búsqueda**: texto (2 a 100 caracteres tras recortar), año (un año concreto o "Todos los años"), mes (1 a 12 u opcional, solo con un año concreto) y página. No se persiste; vive en la dirección de la pantalla.
- **Años con actividad**: conjunto calculado de años (por fecha del gasto) en los que la persona tiene gastos, más el año actual; alimenta el selector.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Una persona encuentra un gasto conocido de su actividad en menos de 15 segundos desde que abre la pantalla.
- **SC-002**: El 100 % de los resultados contiene el texto buscado en la descripción, la categoría o el medio de pago, y el 0 % pertenece a otra persona.
- **SC-003**: Toda búsqueda sin coincidencias muestra el mensaje de sin resultados.
- **SC-004**: Tras borrar el texto, la lista es idéntica a la del año elegido sin búsqueda.
- **SC-005**: Al volver desde el detalle, el texto, el año y los resultados son los mismos en el 100 % de los casos.
- **SC-006**: Los tests de frontend y backend pasan, con cobertura mínima de líneas del 85 %.
