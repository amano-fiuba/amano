# Feature Specification: Panel de reparto del gasto de grupo

**Feature Branch**: `feat/group-debt-management`

**Created**: 2026-11-08

**Status**: Implementada en la rama, pendiente de revisión

**Input**: User description: "Mejorar cómo se divide un gasto entre personas (partes iguales, importes personalizados, porcentajes). La elección de con quiénes se comparte va en un lugar a la derecha del formulario principal. Si se elige porcentajes aparece un slider con el % de cada persona; si se elige personalizado, una celda donde se escribe el monto de cada uno."

## Relación con otras especificaciones

Sigue la costumbre del repo (018, 021, 023): las specs anteriores no se editan y sus cambios se documentan acá. **Donde esta spec y una anterior difieran, vale esta.**

| Spec | Punto | Qué decía | Qué vale ahora |
|---|---|---|---|
| 022 | T072, selector de reparto | Un desplegable "¿Cómo se reparte?" con "En partes iguales", "Importes personalizados" y "Porcentajes" | Tres botones: "Partes iguales", "Personalizado" y "Porcentajes" (FR-004) |
| 022 | T072, porcentajes | Un campo de texto por participante | Un slider y un campo de texto por participante, que editan el mismo valor (FR-008) |
| 022 | T072, vista previa | Una lista aparte con la parte de cada participante (`ExpenseSharePreview`) | El monto de cada participante va en su propia fila (FR-010 a FR-012) |
| 022 | T077, gasto personal | Casilla "Es un gasto personal (sin deudas con nadie)", destildada por defecto | Interruptor "Gasto compartido", encendido por defecto. Apagado, el gasto es personal (FR-002, FR-003) |
| 017 | Formulario "Nuevo gasto" | Una sola columna | Dos columnas en escritorio: datos del gasto y panel de reparto (FR-001) |

El resto no cambia:

- **022**: las reglas de reparto, el redondeo (piso para quienes no pagaron y resto para quien pagó), la conversión de moneda y los mensajes de error siguen iguales. El backend no se toca.
- **022 T077**: la edición de un gasto personal sigue en una sola columna y sin panel de reparto.

Los identificadores FR-### y SC-### de esta especificación son propios de 024.

## Clarifications

### Session 2026-11-08

- Q: ¿Dónde se elige con quiénes se comparte? → A: En un panel a la derecha del formulario principal. En pantallas angostas el panel va debajo.
- Q: Con porcentajes, ¿los sliders se reacomodan solos para sumar 100? → A: No. Cada slider es independiente y un indicador muestra lo que falta o lo que sobra. Es simple y predecible, y es lo que ya hacían los campos de texto. Con 4 o más personas, que los valores se muevan solos confunde.
- Q: ¿El interruptor "Gasto compartido" arranca encendido? → A: Sí, porque es un formulario de grupo.
- Q: Al destildar a alguien en Personalizado o Porcentajes, ¿su campo queda en 0 o desaparece? → A: Desaparece.
- Q: Si la moneda del gasto es distinta de la del grupo, ¿en qué moneda se muestra el monto de cada persona? → A: En las dos.
- Q: ¿Spec nueva o ajuste? → A: Ajuste dentro de la rama `feat/group-debt-management`, documentado en esta spec.
- Q: ¿Hay un atajo para completar lo que falta? → A: Sí, "Repartir lo que falta en partes iguales".
- Q: ¿Qué textos de ayuda se sacan para que el panel ocupe menos? → A: "Quien pagó siempre participa del reparto" pasa a un tooltip; "Ingresá el monto para ver cuánto le toca a cada uno" se elimina; "¿Cómo se reparte?" y "Participantes" quedan solo para lectores de pantalla.
- Q: ¿Cómo se muestra el nombre de cada persona? → A: Solo el nombre de pila, sin apellido ni "(perfil simulado)". El nombre completo queda en el tooltip y para lectores de pantalla.
- Q: ¿Cómo se marca a quien pagó? → A: Con un ícono de billete en lugar del texto "(pagó)".

## Fuera de alcance

- Sliders que se reacomodan solos para sumar 100.
- Cambiar reglas de reparto, redondeo o conversión.
- Cambios de backend o de contrato.
- Usar el panel para editar un gasto ya registrado.
- Quitar `ExpenseSharePreview`, que ya no se usa en el formulario (ver tasks.md).

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Repartir un gasto en un panel propio (Priority: P1)

Una persona carga un gasto de grupo. A la izquierda completa monto, moneda, fecha, descripción y quién pagó; a la derecha decide con quiénes se comparte y cómo, viendo cuánto le toca a cada uno.

**Why this priority**: Antes la división quedaba al final de un formulario largo, y la vista previa estaba separada de los participantes.

**Independent Test**: En un grupo de 3 personas, cargar $100 en partes iguales. Cada fila muestra su parte y quien pagó absorbe el centavo.

**Acceptance Scenarios**:

1. **Given** un ancho de 820px o más, **When** se abre "Nuevo gasto", **Then** se ven dos columnas: datos del gasto y panel de reparto.
2. **Given** un ancho menor, **When** se abre "Nuevo gasto", **Then** el panel va debajo de los datos del gasto.
3. **Given** el formulario recién abierto, **When** se mira el panel, **Then** "Gasto compartido" está encendido, "Partes iguales" elegido y todos los miembros tildados.
4. **Given** "Gasto compartido" apagado, **When** se guarda, **Then** el gasto es personal, solo a nombre de quien pagó.
5. **Given** $100 entre 3 en partes iguales, **When** se mira cada fila, **Then** se ven $33,34 para quien pagó y $33,33 para los demás, con la nota del centavo.

---

### User Story 2 - Repartir por porcentajes con sliders (Priority: P1)

Una persona elige "Porcentajes" y ajusta el de cada participante con un slider o escribiéndolo, mientras ve cuánta plata representa y cuánto falta para llegar a 100.

**Why this priority**: Es el pedido central: el slider da una idea visual del reparto.

**Independent Test**: Con $1.000 entre 3, llevar el slider de una persona a 40: su campo muestra 40, su fila $400 y el indicador "Falta repartir 60 %".

**Acceptance Scenarios**:

1. **Given** el modo Porcentajes, **When** se mueve un slider, **Then** el campo de esa persona muestra el valor, de a 1 %.
2. **Given** el modo Porcentajes, **When** se escribe 33,33, **Then** se acepta con decimales y el slider se acerca a ese valor.
3. **Given** porcentajes incompletos, **When** se mira una fila con valor, **Then** ya muestra su monto estimado.
4. **Given** una suma distinta de 100, **When** se mira el indicador, **Then** dice cuánto falta o sobra y "Guardar gasto" está deshabilitado.
5. **Given** una suma de 100, **When** se mira el indicador, **Then** se pone verde y se puede guardar.
6. **Given** una persona destildada, **When** se mira su fila, **Then** no tiene slider ni campo.

---

### User Story 3 - Repartir con importes personalizados (Priority: P1)

Una persona elige "Personalizado" y escribe cuánto paga cada uno en la moneda del gasto.

**Why this priority**: Es el otro modo pedido; cambia de lugar respecto de 022, no de reglas.

**Independent Test**: Con $100 entre 3, escribir 30 y 50: el indicador dice "Faltan $ 20,00 ARS".

**Acceptance Scenarios**:

1. **Given** el modo Personalizado, **When** se mira cada fila incluida, **Then** tiene un campo de importe con la moneda del gasto.
2. **Given** importes que no suman el total, **When** se mira el indicador, **Then** dice cuánto falta o sobra y no se puede guardar.
3. **Given** una persona destildada, **When** se mira su fila, **Then** no tiene campo.

---

### User Story 4 - Completar lo que falta de un toque (Priority: P2)

Una persona cargó los valores de algunos participantes y reparte el resto en partes iguales con un botón.

**Why this priority**: Evita hacer cuentas a mano en el caso común "yo pongo la mitad y el resto se divide".

**Independent Test**: Con $1.000 entre 3 en porcentajes, escribir 50 para una persona y tocar el botón: las otras dos quedan en 25 y se puede guardar.

**Acceptance Scenarios**:

1. **Given** un faltante en Personalizado o Porcentajes, **When** se mira el indicador, **Then** aparece "Repartir lo que falta en partes iguales".
2. **Given** participantes sin valor, **When** se toca el botón, **Then** el faltante se reparte entre ellos en partes iguales.
3. **Given** que todos tienen valor, **When** se toca el botón, **Then** el faltante se suma en partes iguales a todos.
4. **Given** un faltante que no se divide justo, **When** se toca el botón, **Then** los centavos o centésimos de % que sobran van a los primeros de la lista ($100 entre 3 → 33,34 / 33,33 / 33,33).
5. **Given** que no falta nada, o que sobra, **When** se mira el indicador, **Then** el botón no aparece.

---

### User Story 5 - Ver el reparto en dos monedas (Priority: P2)

Una persona carga un gasto en una moneda distinta de la del grupo y ve, en cada fila, la parte en la moneda del gasto y su equivalente estimado en la del grupo.

**Why this priority**: Las deudas se generan en la moneda del grupo, pero el gasto se pensó en la otra.

**Independent Test**: USD 100 entre 2 con cotización 1.000 en partes iguales: cada fila muestra "$ 50,00 USD" y "≈ $ 50.000,00 ARS".

**Acceptance Scenarios**:

1. **Given** otra moneda y una cotización disponible, **When** se mira una fila en Partes iguales o Porcentajes, **Then** muestra el monto en la moneda del gasto y debajo "≈" en la del grupo.
2. **Given** el modo Personalizado en otra moneda, **When** se mira una fila, **Then** muestra solo el equivalente en la moneda del grupo, porque el importe ya está en el campo.
3. **Given** montos convertidos visibles, **When** se mira el panel, **Then** se aclara que son estimados en la moneda del grupo.

---

### User Story 6 - Un panel compacto (Priority: P2)

Una persona reparte un gasto en un grupo de 6 o más personas sin que el panel ocupe toda la pantalla, y con sliders que se puedan mover con comodidad.

**Why this priority**: La primera versión ocupaba dos líneas por persona y los montos grandes achicaban los sliders.

**Independent Test**: En un grupo de 6 personas, con $300.000 en porcentajes, cada persona ocupa una línea y los sliders miden lo mismo en todas las filas.

**Acceptance Scenarios**:

1. **Given** el modo Porcentajes o Personalizado, **When** se mira el panel, **Then** cada persona ocupa una línea: nombre, control y monto.
2. **Given** un monto grande, **When** aparece en una fila, **Then** la columna del monto no crece y el slider no se achica.
3. **Given** un monto que no entra en su columna, **When** se pasa el cursor, **Then** el tooltip muestra el valor completo.
4. **Given** un miembro "Manuel Pato Ibáñez", **When** se mira su fila, **Then** dice "Manuel" y el tooltip dice el nombre completo.
5. **Given** dos miembros con el mismo nombre de pila, **When** se mira el panel, **Then** a esos dos se les muestra el nombre completo.
6. **Given** quien pagó, **When** se mira su fila, **Then** tiene un ícono de billete con el tooltip "Pagó".
7. **Given** la casilla bloqueada de quien pagó en Personalizado o Porcentajes, **When** se pasa el cursor, **Then** el tooltip dice "Quien pagó siempre participa del reparto".
8. **Given** un ancho menor a 560px, **When** se mira una fila con control, **Then** el nombre va arriba y el control con el monto debajo.

---

### Edge Cases

- Con el monto vacío, Partes iguales y Porcentajes no muestran montos; Personalizado pide el monto antes de repartir.
- Los valores escritos se conservan al cambiar de modo y al volver.
- Mientras no hay montos en Personalizado con la moneda del grupo, la columna del monto no ocupa lugar.
- En un navegador, el slider va de a 1 % y el campo admite dos decimales: 25,5 % deja el slider en 26.
- Un faltante de 1 centavo entre 3 personas se le asigna solo a la primera sin valor.
- El botón de repartir no aparece si no hay participantes.
- Un nombre de un solo término ("camik") se muestra igual; un perfil simulado pierde "(perfil simulado)" a la vista pero no para lectores de pantalla.
- Con la cotización todavía cargando o en error, las filas muestran solo la moneda del gasto.

## Requirements *(mandatory)*

### Functional Requirements

**Disposición**

- **FR-001**: "Nuevo gasto" MUST mostrar los datos del gasto y el panel de reparto en dos columnas desde 820px, y apilados por debajo de ese ancho. El panel MUST quedar visible al hacer scroll en escritorio.
- **FR-002**: El panel MUST empezar con un interruptor "Gasto compartido", encendido por defecto.
- **FR-003**: Con el interruptor apagado, el panel MUST ocultar el reparto, explicar que el gasto no genera deudas y enviarlo con quien pagó como único participante.
- **FR-004**: El método MUST elegirse con tres botones excluyentes: "Partes iguales", "Personalizado" y "Porcentajes", con "Partes iguales" elegido al abrir.

**Filas de participantes**

- **FR-005**: Cada miembro MUST tener una fila con una casilla. Las filas de quienes no participan MUST verse atenuadas y sin controles.
- **FR-006**: Cada fila MUST mostrar solo el nombre de pila, salvo que otro miembro tenga el mismo, y MUST ofrecer el nombre completo en un tooltip y a lectores de pantalla, junto con "(perfil simulado)" si corresponde.
- **FR-007**: La fila de quien pagó MUST mostrar un ícono de billete con el tooltip "Pagó", y lectores de pantalla MUST leer "(pagó)".
- **FR-008**: En Porcentajes, cada fila incluida MUST tener un slider de 0 a 100, de a 1 %, y un campo que admite dos decimales. Ambos MUST editar el mismo valor.
- **FR-009**: En Personalizado, cada fila incluida MUST tener un campo de importe en la moneda del gasto.

**Montos**

- **FR-010**: Cada fila incluida MUST mostrar su parte en la moneda del gasto en Partes iguales y Porcentajes, calculada con las mismas reglas que el backend.
- **FR-011**: Si la moneda del gasto difiere de la del grupo y hay cotización, cada fila MUST mostrar además su parte estimada en la moneda del grupo. En Personalizado MUST mostrar solo esa.
- **FR-012**: Mientras el reparto no cierra, las filas con valor MUST mostrar una estimación (piso al centavo) que se actualiza al escribir.
- **FR-013**: En Porcentajes, la columna del monto MUST tener ancho fijo. Un monto que no entra MUST cortarse con "…" y mostrarse completo en un tooltip.

**Indicador y atajo**

- **FR-014**: En Personalizado y Porcentajes, el panel MUST mostrar cuánto falta o sobra, con una barra de progreso en Porcentajes, y marcarse como completo cuando la suma cierra.
- **FR-015**: Mientras falte algo, el panel MUST ofrecer "Repartir lo que falta en partes iguales", que reparte el faltante entre quienes no tienen valor o, si todos tienen, entre todos. Las unidades sobrantes MUST ir a los primeros de la lista.
- **FR-016**: Guardar MUST seguir deshabilitado hasta que el reparto cierre, como en 022.

**Accesibilidad**

- **FR-017**: El grupo de métodos MUST llamarse "¿Cómo se reparte?" y la lista "Participantes" para lectores de pantalla, aunque esos textos no se vean.
- **FR-018**: El interruptor MUST exponerse con `role="switch"`, y el slider de cada persona MUST tener un nombre distinto de su campo ("Deslizar porcentaje de …" y "Porcentaje de …").
- **FR-019**: Las animaciones del interruptor y de la barra MUST desactivarse con `prefers-reduced-motion`.

### Key Entities

- **Esta feature no agrega tablas, migraciones ni cambios de contrato.** El pedido a `POST /api/groups/{id}/expenses` es el mismo de 022.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: En un grupo de 6 personas, en escritorio, cada participante ocupa una sola línea en los tres métodos.
- **SC-002**: Repartir "yo pongo la mitad y el resto en partes iguales" entre 4 personas requiere escribir un solo valor y un toque.
- **SC-003**: Con montos de hasta $ 99.999.999,99 los sliders miden lo mismo en todas las filas.
- **SC-004**: Ninguna pantalla del formulario tiene scroll horizontal a 360px.
- **SC-005**: Los montos de cada fila coinciden con los que guarda el backend, en las tres modalidades y en otra moneda.

## Assumptions

- El ancho fijo del monto (9,5rem) alcanza para la mayoría de los montos; los mayores se leen en el tooltip.
- El nombre de pila es lo que está antes del primer espacio del nombre visible del miembro.
- El ícono de billete sigue el estilo de los íconos de la barra (`NavIcons.jsx`).
