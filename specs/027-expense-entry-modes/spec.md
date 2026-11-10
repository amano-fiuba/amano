# Feature Specification: Formas de registrar un gasto

**Feature Branch**: `feat/modales-pagos`

**Created**: 2026-11-08

**Status**: Implementada en la rama `feat/modales-pagos` (US1–US4), pendiente de prueba manual y revisión

**Input**: User description: "Al presionar 'Registrar un gasto' en el frontpage se abre un submenú (estilo 'Insertar > Elementos de creación' de Google Docs) con 3 opciones: Gasto personal, Gasto con amigo y Gasto grupal. El gasto personal reutiliza el modal actual con correcciones (calendario para la fecha, monedas del usuario + 'Ver más' con monedas del BCRA, rango inicio/fin para gastos frecuentes, botones Cancelar/Guardar, fondo celeste sobre blanco, sin 'Gasto compartido'). El gasto grupal agrega tipo de gasto, medio de pago y categoría, iguala el tamaño de las filas de los tres modos de reparto y fija la altura del modal según la columna izquierda, con el panel derecho scrolleable. El gasto con amigo primero pide elegir un amigo y luego reutiliza el formulario grupal."

**Aclaraciones (2026-11-08)**: (1) se aceptan todas las monedas del BCRA y en grupos se convierten a la moneda del grupo; (2) el rango inicio/fin de los gastos frecuentes sale de esta spec y lo tratará otra; (3) los gastos con amigo crean deudas directas, pero verlas y saldarlas queda para otra spec.

## Relación con otras especificaciones

Sigue la costumbre del repo (018, 021, 023, 024): las specs anteriores no se editan y sus cambios se documentan acá. **Donde esta spec y una anterior difieran, vale esta.**

| Spec | Punto | Qué decía | Qué vale ahora |
|---|---|---|---|
| 015 | US4, botón "+" | "Registrar gasto" lleva directo al formulario de gasto personal | "Registrar gasto" abre un submenú con tres opciones (FR-001 a FR-004) |
| 007 | Campo fecha | Campo de fecha nativo del navegador | Campo con ícono de calendario que abre un mini calendario navegable por meses (FR-010) |
| 007 / 002 / 022 | Moneda | Solo ARS, USD y EUR | Primero las monedas del usuario y luego "Ver más" con el catálogo del BCRA; se acepta cualquier moneda del catálogo, también en gastos de grupo (FR-011 a FR-014) |
| 016 | Deuda directa | El módulo de deudas entre amigos no existe; nunca hay deudas directas | Un gasto con amigo genera una deuda directa que bloquea eliminar la amistad (FR-032) |
| 007 | Formulario | Una columna, sin "Cancelar" y sin el fondo de los formularios de grupo | Panel celeste sobre fondo blanco, "Cancelar" abajo a la izquierda y "Guardar" abajo a la derecha (FR-018, FR-019) |
| 017 / 022 / 024 | Gasto de grupo | Sin categoría, tipo de gasto ni medio de pago | Se agregan los tres campos (FR-020) |
| 024 | Panel de reparto | Las filas de cada modo tienen alturas distintas y el panel crece sin límite | Filas de igual altura en los tres modos; altura del formulario fijada por la columna izquierda y panel derecho scrolleable (FR-022 a FR-024) |

El resto no cambia: las reglas de reparto y redondeo de 022/024, el mecanismo de conversión de moneda de 022 (ahora aplicado a más monedas), la validación de montos y descripción de 007 y todo el comportamiento de los gastos recurrentes de 009 (frecuencias semanal, mensual y anual, sin fecha de fin, pausa y reanudación). La personalización de frecuencias queda para otra spec.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Elegir qué tipo de gasto registrar (Priority: P1)

Desde el frontpage, el usuario presiona "Registrar gasto" y, en lugar de ir directo a un formulario, ve un submenú con tres opciones: "Gasto personal", "Gasto con amigo" y "Gasto grupal". Elige una y se abre el flujo correspondiente.

**Why this priority**: es la puerta de entrada a los tres flujos; sin ella ninguno de los demás es accesible desde el frontpage.

**Independent Test**: abrir el menú "+", posar el cursor o tocar "Registrar gasto" y comprobar que aparecen las tres opciones y que cada una lleva a su flujo.

**Acceptance Scenarios**:

1. **Given** un usuario autenticado en el frontpage, **When** abre el menú de acciones rápidas y pasa el cursor sobre "Registrar gasto" (o lo presiona), **Then** se despliega a su lado un submenú con "Gasto personal", "Gasto con amigo" y "Gasto grupal", cada una con su ícono.
2. **Given** el submenú abierto, **When** el usuario elige "Gasto personal", **Then** se abre el formulario de gasto personal.
3. **Given** el submenú abierto, **When** el usuario hace clic fuera o presiona Escape, **Then** el menú y el submenú se cierran sin abrir ningún formulario.
4. **Given** un dispositivo táctil o el uso de teclado, **When** el usuario selecciona "Registrar gasto", **Then** el submenú se abre igual (no depende solo del hover) y se puede navegar con flechas y Enter.

---

### User Story 2 - Registrar un gasto personal con el formulario corregido (Priority: P1)

El usuario registra un gasto propio en un formulario con el mismo estilo que el de grupos (panel celeste sobre fondo blanco), elige la fecha en un mini calendario, elige la moneda entre las suyas o busca otra en "Ver más", y puede cancelar o guardar desde los botones del pie.

**Why this priority**: es el flujo más usado y hoy tiene los problemas de usabilidad señalados.

**Independent Test**: registrar un gasto personal único eligiendo la fecha desde el calendario y una moneda desde "Ver más"; el gasto queda guardado con esos datos.

**Acceptance Scenarios**:

1. **Given** el formulario de gasto personal abierto, **When** se muestra, **Then** tiene un panel celeste sobre fondo blanco (igual que el de grupos), no muestra el interruptor "Gasto compartido" ni el panel de reparto, y muestra "Cancelar" abajo a la izquierda y "Guardar" abajo a la derecha.
2. **Given** el campo Fecha, **When** el usuario presiona el ícono de calendario a la derecha del campo, **Then** se abre un mini calendario en el mes de la fecha cargada, con flechas para ir al mes anterior y al siguiente; al elegir un día, el campo toma esa fecha y el calendario se cierra.
3. **Given** el selector de Moneda, **When** el usuario lo abre, **Then** ve primero sus monedas preferidas y al final la opción "Ver más".
4. **Given** el usuario elige "Ver más", **When** se abre el modal de monedas, **Then** puede buscar por código o nombre en el catálogo de monedas del BCRA, elegir una y volver al formulario con esa moneda seleccionada.
5. **Given** el usuario presiona "Cancelar", **When** el formulario tiene datos cargados, **Then** se pide confirmación antes de descartarlos; si está vacío, se cierra directamente.

---

### User Story 3 - Registrar un gasto grupal completo (Priority: P2)

El usuario elige "Gasto grupal", selecciona el grupo y completa el formulario de grupo, que ahora incluye categoría, tipo de gasto y medio de pago. El panel de reparto tiene filas del mismo tamaño en los tres modos y, si hay muchos miembros, se scrollea dentro de una altura fija.

**Why this priority**: completa datos que hoy faltan en los gastos de grupo y arregla el layout, pero el flujo grupal ya existe.

**Independent Test**: registrar un gasto en un grupo de 10 miembros con categoría, tipo y medio de pago; alternar entre los tres modos de reparto sin que cambie la altura de las filas ni la del formulario.

**Acceptance Scenarios**:

1. **Given** el usuario elige "Gasto grupal" desde el submenú, **When** pertenece a uno o más grupos, **Then** se le pide elegir el grupo y luego se abre el formulario de gasto de ese grupo.
2. **Given** el formulario de grupo, **When** se muestra, **Then** incluye Categoría, Tipo de gasto y Medio de pago con las mismas opciones que el gasto personal, y Fecha y Moneda con el mismo comportamiento que en US2.
3. **Given** el panel de reparto, **When** el usuario cambia entre "Partes iguales", "Personalizado" y "Porcentajes", **Then** cada fila de miembro ocupa la misma altura en los tres modos.
4. **Given** un grupo con más miembros de los que entran, **When** se muestra el panel derecho, **Then** el formulario mantiene la altura de la columna izquierda y el panel derecho se scrollea internamente.
5. **Given** el usuario no pertenece a ningún grupo, **When** elige "Gasto grupal", **Then** ve un mensaje que lo indica y un acceso a "Crear grupo".

---

### User Story 4 - Registrar un gasto con un amigo (Priority: P3)

El usuario elige "Gasto con amigo", selecciona un único amigo de su lista y completa el mismo formulario que el de grupo, con él y ese amigo como únicos participantes.

**Why this priority**: es un flujo nuevo que depende del formulario de grupo corregido (US3).

**Independent Test**: elegir un amigo, registrar un gasto de $10.000 en partes iguales pagado por el usuario y comprobar que el amigo queda debiendo $5.000.

**Acceptance Scenarios**:

1. **Given** el usuario elige "Gasto con amigo", **When** se abre el selector, **Then** ve su lista de amigos con buscador y puede elegir exactamente uno.
2. **Given** un amigo elegido, **When** confirma, **Then** se abre el formulario de gasto compartido con el usuario y ese amigo como participantes, ambos seleccionables como "¿Quién pagó?".
3. **Given** el usuario no tiene amigos, **When** elige "Gasto con amigo", **Then** ve un mensaje que lo indica y un acceso para buscar usuarios.
4. **Given** el formulario de gasto con amigo, **When** el usuario presiona "Cancelar", **Then** vuelve sin crear nada.
5. **Given** un gasto con amigo guardado con una deuda pendiente, **When** el usuario intenta eliminar a ese amigo, **Then** la eliminación se bloquea como define 016 para las deudas directas.

---

### Edge Cases

- El catálogo del BCRA no responde: "Ver más" muestra un error con opción de reintentar; las monedas del usuario siguen disponibles.
- El usuario no tiene monedas preferidas: se muestra ARS como única moneda propia (regla de 002/007).
- Se elige una moneda de "Ver más" que no es preferida del usuario: queda seleccionada para ese gasto sin modificar sus preferencias.
- Gasto grupal en una moneda distinta de la del grupo sin cotización disponible: se aplica el error de conversión ya definido en 022.
- Se envía un código de moneda que no está en el catálogo del BCRA: el gasto se rechaza con el error de moneda de 007.
- El submenú no entra a la derecha del menú (pantalla angosta o móvil): se despliega debajo o en su lugar, sin salirse de la pantalla.
- El amigo elegido deja de ser amigo antes de guardar: el guardado falla con un mensaje y no se crea el gasto.
- Teclear la fecha a mano sigue permitido; una fecha inválida muestra el error de 007.

## Requirements *(mandatory)*

### Functional Requirements

**Submenú de registro**

- **FR-001**: La opción "Registrar gasto" del menú de acciones rápidas MUST abrir un submenú lateral con tres opciones, en este orden: "Gasto personal", "Gasto con amigo" y "Gasto grupal", cada una con un ícono. Como las historias se integran por separado, cada opción aparece cuando su flujo está integrado ("Gasto personal" con US1, "Gasto grupal" con US3, "Gasto con amigo" con US4); nunca se muestra una opción que no funciona.
- **FR-002**: El submenú MUST abrirse con hover, clic/toque y teclado, y cerrarse con Escape, clic fuera o al elegir una opción.
- **FR-003**: El submenú MUST reposicionarse para no salirse de la pantalla en anchos chicos.
- **FR-004**: Cada opción MUST abrir su flujo: formulario personal (US2), selector de amigo (US4) o selector de grupo (US3).

**Campos comunes (personal, grupal y con amigo)**

- **FR-010**: El campo Fecha MUST tener un ícono de calendario a la derecha que abra un mini calendario con navegación mes a mes, marque el día de hoy y el día seleccionado, y respete las restricciones de fecha de 007. Escribir la fecha a mano MUST seguir funcionando.
- **FR-011**: El selector de Moneda MUST listar primero las monedas preferidas del usuario y al final una opción "Ver más".
- **FR-012**: "Ver más" MUST abrir un modal con el catálogo de monedas que publica el BCRA, con búsqueda por código o nombre.
- **FR-013**: La moneda elegida en "Ver más" MUST quedar seleccionada solo para ese gasto, sin cambiar las preferencias del usuario.
- **FR-014**: El sistema MUST aceptar y guardar gastos personales, de grupo y con amigo en cualquier moneda del catálogo del BCRA. El catálogo ofrecido son las monedas del BCRA **vigentes y con cotización** (32 al 2026-11-08); se excluyen las que no cumplen eso (ver Supuestos). En gastos de grupo, una moneda distinta de la del grupo MUST convertirse a la moneda del grupo con la cotización del BCRA, con las mismas reglas y errores de 022. Las preferencias de moneda del usuario (002) MUST poder elegirse del mismo catálogo.

**Gasto personal**

- **FR-035**: Cuando la moneda elegida en el formulario de gasto personal no es ARS y el monto y la fecha son válidos, el formulario MUST mostrar una vista previa del monto estimado en pesos con la cotización oficial del BCRA de esa fecha, como el formulario de grupos (022). Es solo informativa: el gasto personal se guarda en su moneda, sin convertir. Si la cotización no está disponible, se avisa y el guardado no se bloquea. *(Agregado el 2026-11-08 a pedido del usuario.)*
- **FR-036**: En el alta de los tres formularios (personal, grupal y con amigo), "Guardar" MUST abrir un resumen final del gasto (monto, moneda, fecha, clasificación, descripción y, en grupo o con amigo, con quién, quién pagó, el reparto y la parte de cada uno) con "Confirmar" y "Volver"; el gasto se guarda solo al confirmar y "Volver" conserva lo cargado. La edición de un gasto de grupo no lo usa. *(Agregado el 2026-11-08 a pedido del usuario.)*
- **FR-018**: El formulario de gasto personal MUST usar el mismo estilo que el de grupos: panel celeste sobre fondo blanco.
- **FR-019**: El formulario de gasto personal MUST mostrar "Cancelar" abajo a la izquierda y "Guardar" abajo a la derecha, y MUST NOT mostrar el interruptor "Gasto compartido" ni el panel de reparto. "Cancelar" con datos cargados MUST pedir confirmación.

**Gasto grupal**

- **FR-020**: El formulario de gasto grupal MUST incluir Categoría, Tipo de gasto y Medio de pago, con los mismos catálogos y validaciones que el gasto personal, y guardarlos con el gasto. La edición de un gasto personal de grupo (022) MUST permitir cambiarlos.
- **FR-021**: Al elegir "Gasto grupal" desde el submenú, el sistema MUST pedir primero el grupo entre los grupos activos del usuario. Desde la pantalla de un grupo, el formulario MUST abrirse con ese grupo sin preguntar.
- **FR-022**: Cada fila de miembro del panel de reparto MUST ocupar la misma altura en "Partes iguales", "Personalizado" y "Porcentajes".
- **FR-023**: En escritorio, la altura del formulario MUST estar dada por la columna izquierda (datos del gasto); el panel derecho MUST ocupar como máximo esa altura y scrollear internamente si su contenido la supera.
- **FR-024**: Los botones "Cancelar" y "Guardar" MUST seguir visibles aunque el panel derecho scrollee.

**Gasto con amigo**

- **FR-030**: "Gasto con amigo" MUST abrir primero un modal con la lista de amigos del usuario, con búsqueda, que permita elegir exactamente uno.
- **FR-031**: Tras elegir el amigo, MUST abrirse el mismo formulario que el de grupo (con los campos de FR-020 y el panel de reparto), con el usuario y el amigo como únicos participantes y ambos elegibles como pagador.
- **FR-032**: El gasto con amigo MUST generar la deuda correspondiente entre ambos según el reparto elegido, y esa deuda MUST ser la "deuda directa" que bloquea eliminar la amistad mientras esté pendiente (016). Ver y saldar estas deudas queda fuera de esta spec.
- **FR-033**: Si el amigo deja de serlo antes de guardar, el guardado MUST fallar con un mensaje y no crear el gasto.
- **FR-034**: Un gasto con amigo MUST NOT convertirse de moneda: como no hay moneda de grupo, la deuda queda en la moneda del gasto.

### Key Entities

- **Gasto personal**: gasto de un solo usuario, con los atributos actuales (monto, moneda, fecha, categoría, tipo, frecuencia si es recurrente, medio de pago, descripción). Solo se amplía el catálogo de monedas.
- **Gasto de grupo**: gasto compartido dentro de un grupo. Suma categoría, tipo de gasto y medio de pago.
- **Gasto con amigo**: gasto compartido entre exactamente dos usuarios que son amigos, sin grupo. Tiene los mismos atributos que el gasto de grupo y genera una deuda directa.
- **Deuda directa**: lo que un amigo le debe al otro por un gasto con amigo.
- **Moneda del catálogo**: moneda publicada por el BCRA (código y nombre). Las preferidas del usuario son un subconjunto.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Desde el frontpage, el usuario llega al formulario de gasto personal en 3 clics o menos (2 si abre el submenú con hover) y al de gasto con amigo o grupal en 4 o menos, contando la elección del amigo o grupo.
- **SC-002**: El usuario elige una fecha de otro mes desde el calendario sin teclear, en menos de 10 segundos.
- **SC-003**: El usuario encuentra y elige una moneda no preferida desde "Ver más" en menos de 15 segundos.
- **SC-004**: Al alternar entre los tres modos de reparto, la altura de cada fila de miembro y la del formulario no cambian.
- **SC-005**: En un grupo de 20 miembros, el panel de reparto no supera la altura de la columna izquierda y los botones del pie quedan inmediatamente debajo de ambas columnas; con una pantalla de 1366×768 o mayor, se ven sin scrollear la página.
- **SC-006**: El 100 % de los gastos de grupo y con amigo creados después de esta spec guardan categoría, tipo de gasto y medio de pago.
- **SC-007**: Un gasto en una moneda del BCRA distinta de ARS, USD y EUR se guarda y, en un grupo, se convierte a la moneda del grupo sin intervención manual.

## Assumptions

- "Frontpage" es la pantalla principal autenticada (015) y "Registrar gasto" es la opción del botón de acciones rápidas "+".
- El aspecto del submenú toma como referencia el submenú "Elementos de creación" de Google Docs (panel lateral con ícono + texto), adaptado al estilo de A Mano.
- Los formularios de gasto (personal, grupal y con amigo) son páginas con ruta propia, como hoy (`/expenses/new`, `/groups/:id/expenses/new` y la nueva `/friends/:friendId/expenses/new`). Los modales se usan solo para pasos intermedios cortos: elegir grupo, elegir amigo y "Ver más" monedas. Decisión confirmada por el usuario el 2026-11-08.
- El formulario de grupo conserva el interruptor "Gasto compartido" de 024 (apagado = gasto personal del grupo); solo se quita del formulario personal.
- El tipo de gasto "Recurrente" en un gasto de grupo permite elegir frecuencia y genera las próximas ocurrencias (V32); en un gasto con amigo se guarda como dato informativo y no se repite.
- El selector de grupo muestra solo grupos activos de los que el usuario es miembro.
- El selector de amigo reutiliza la lista de amigos de 013.
- En móvil, las dos columnas del formulario de grupo se apilan y la regla de altura fija (FR-023) aplica solo en escritorio.
- Las deudas de gastos con amigo usan los mismos estados (pendiente, vencida, pagada) que las deudas de grupo (017).
- El catálogo del BCRA se consulta a través del backend (como la cotización de 022) y se puede cachear.
- Del catálogo del BCRA se excluyen: monedas extintas (ATS, DEM, ESP, ITL, VEB), lo que no es una moneda de pago (REF "dólar referencia", XAU oro, XAG plata, XDR derechos especiales de giro), el yuan offshore CNH (duplica a CNY) y el dong VND (el BCRA lo cotiza cada 1000 unidades). El peso mexicano, que el BCRA publica como MXP, se usa con su código vigente MXN.
- Fuera de alcance: personalizar la frecuencia de los gastos recurrentes (rangos de inicio y fin, intervalos libres); el selector de frecuencia queda como en 009 y lo tratará otra spec.
- Fuera de alcance: ver y saldar las deudas directas de un gasto con amigo; esta spec solo las crea.
