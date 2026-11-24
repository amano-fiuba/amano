# Feature Specification: Ajustes de interfaz sobre la renovación

**Feature Branch**: `fix/ui-again`

**Created**: 2026-11-08

**Status**: Implementada en la rama, pendiente de revisión

**Input**: User description: "Pulir la interfaz después de la renovación de 023: tomar la paleta del logo, unificar los diálogos de confirmación en un modal base, sumar buscadores y orden a las listas de amigos, solicitudes y grupos, agregar miembros a un grupo desde un modal con la lista de amigos, ordenar los botones Cancelar y Guardar, y ofrecer un acceso a la pantalla principal desde las pantallas secundarias. Al verificar el correo, llegar a la pantalla principal."

## Relación con otras especificaciones

Sigue la costumbre del repo (018 §Constitution Check, 021, 023): las specs anteriores no se editan y sus extensiones se documentan acá. **Donde esta spec y una anterior difieran, vale esta.**

| Spec | Punto | Qué decía | Qué vale ahora |
|---|---|---|---|
| 003 | FR-005 | Al verificar el correo se inicia sesión y se lleva al dashboard | Se lleva a `/` (pantalla principal) con un snackbar "Cuenta verificada" (FR-001, FR-002) |
| 015 | Clarifications, decisión de implementación | Tras verificar el correo se sigue llegando a `/groups`, que muestra la bienvenida | Se llega a `/`. `/groups` ya no muestra el mensaje de bienvenida (FR-001 a FR-003) |
| 023 | FR-004 y Clarifications "¿Para qué se usa el naranja del logo?" | El azul sigue siendo el color base de la interfaz | El color base es el turquesa/petróleo del logo. El naranja conserva su uso para interacción y detalles (FR-005, FR-006) |
| 023 | US5 escenario 1 y FR-023 | Crear grupo lista a los amigos con una casilla por cada uno | Cada amigo tiene un botón "Agregar"; los elegidos se marcan "Agregado" y se muestran como fichas, junto con los perfiles simulados (FR-032 a FR-034) |
| 023 | Fuera de alcance: "Cambiar la pantalla de agregar miembros a un grupo ya creado" | No se cambiaba | Se reemplaza por un modal con la lista completa de amigos (FR-027 a FR-031) |
| 010 | Agregar miembro a un grupo existente | Se busca entre los amigos con `GET /api/groups/{id}/candidates?q=` y se elige el tipo con un selector "Amigo registrado / Perfil simulado" | Se lista a todos los amigos con `GET /api/users/me/friends` y se filtra en el navegador. El perfil simulado se suma desde el pie del mismo modal (FR-027 a FR-031) |
| 010 | Eliminar miembro o perfil simulado | Confirmación dentro del menú del miembro | Modal de confirmación (FR-007 a FR-010, FR-023) |
| 016 | Diálogo "Eliminar amigo" | Diálogo propio con botones "Cancelar" y "Eliminar" | Usa el modal base: se cancela con la × o con Escape, sin botón "Cancelar" (FR-007 a FR-010). El botón de cada fila dice "Eliminar" y el éxito se avisa con un snackbar (FR-013) |
| 020 | Botón y modal de salida | "Abandonar grupo", al pie de la pantalla, modal "Abandonar el grupo" con "Cancelar" y "Confirmar" | "Salir", con ícono, en el encabezado del grupo; modal "Salir del grupo" con el modal base (FR-021, FR-022) |

El resto no cambia:

- **Backend**: esta spec no agrega ni modifica endpoints, DTOs ni migraciones. Todas las reglas de permisos, de amistad y de deudas se siguen validando en el servidor.
- **010 (`GET /api/groups/{id}/candidates`)**: el endpoint sigue existiendo, pero el frontend deja de usarlo. Su permanencia o deprecación queda fuera de esta spec.
- **013 (Lista de amigos)**: se usa `friendsSince` para el orden por fecha, sin cambios en la API.
- **014 (Historial de actividad)**: el historial sigue accesible desde "Ver todo" en la tarjeta de actividad del grupo.
- **015 (Pantalla principal)** y **023 (Renovación)**: el resto de sus reglas se mantiene.

Los identificadores FR-### y SC-### de esta especificación son propios de 026.

## Clarifications

### Session 2026-11-08

- Q: ¿Por qué se cambia el azul por turquesa? → A: Para que la base tome los colores del logo, igual que el naranja de 023. Se ajustan los dos temas y las sombras se tiñen de petróleo.
- Q: ¿Los diálogos de confirmación siguen teniendo "Cancelar"? → A: No. Todos usan un modal base con una × para cerrar y un único botón de confirmación. Escape también cierra, salvo durante la operación.
- Q: ¿Los buscadores de las listas consultan al servidor? → A: No. Filtran en el navegador la lista ya cargada, por nombre, apellido o correo (grupos: por nombre), sin distinguir mayúsculas ni acentos.
- Q: ¿Qué pasa si el buscador de amigos o solicitudes no encuentra a nadie? → A: Muestra un mensaje y un link "Buscar usuarios" con el término ya cargado.
- Q: ¿Cómo se agrega un miembro a un grupo existente? → A: Desde un modal "Agregar amigos a {grupo}" con todos los amigos. Quienes ya son miembros figuran sombreados con "Agregado". El modal queda abierto para seguir agregando.
- Q: ¿Se puede buscar en el modal a alguien que no es amigo? → A: No, como hasta ahora. Si nadie coincide se informa; si no hay amigos se ofrece buscar usuarios.
- Q: ¿Dónde van Cancelar y Guardar? → A: Cancelar a la izquierda y Guardar (o Crear) a la derecha, con textos cortos.
- Q: ¿Por qué editar los datos del grupo no tiene Cancelar? → A: Porque se abre como "Configuración": el botón de la cabecera pasa a "Cerrar" y cierra el panel.
- Q: ¿Por qué el buscador global muestra 3 resultados por sección? → A: Para que el panel entre sin scroll. Los links "Ver más" llevan a la búsqueda completa.
- Q: ¿La búsqueda de usuarios sigue teniendo botón? → A: No. Busca mientras se escribe, a partir de 2 caracteres. Enter sigue buscando en el acto.

## Fuera de alcance

- Cambios de backend, incluida la eliminación de `GET /api/groups/{id}/candidates`.
- Paginar o buscar en el servidor las listas de amigos, solicitudes y grupos.
- Guardar el orden o el filtro elegido entre visitas.
- Sumar desde el modal de un grupo a personas que no son amigas.
- Cambiar la tarjeta "Solicitudes de amistad" de la pantalla principal.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Llegar a la pantalla principal (Priority: P1)

Una persona verifica su correo o está en una pantalla secundaria y llega a la pantalla principal sin rodeos.

**Why this priority**: La pantalla principal de 015 es el punto de partida; mandar a `/groups` tras la verificación y no ofrecer una vuelta directa la dejaba de lado.

**Independent Test**: Abrir un enlace de verificación válido y llegar a `/` con el snackbar. Desde Amigos, Mis grupos y Ajustes, usar "Ir a Pantalla principal".

**Acceptance Scenarios**:

1. **Given** un enlace de verificación válido, **When** se abre, **Then** se inicia sesión, se llega a `/` y se ve el snackbar "Cuenta verificada".
2. **Given** una sesión, **When** se abre `/groups`, **Then** no se ve el mensaje de bienvenida de la verificación.
3. **Given** Actividad, Amigos, Mi perfil, Gastos recurrentes, Ajustes, Buscar usuarios o Mis grupos, **When** se usa "Ir a Pantalla principal", **Then** se llega a `/`.
4. **Given** una sesión en una pantalla protegida (por ejemplo `/settings`), **When** se cierra sesión y se vuelve a iniciar, **Then** se llega a `/` y no a la pantalla en la que estaba.

---

### User Story 2 - Confirmar acciones de la misma forma en toda la app (Priority: P1)

Una persona elimina un amigo, elimina un miembro o sale de un grupo y siempre ve el mismo tipo de modal.

**Why this priority**: Había tres diálogos distintos y uno de ellos quedaba dentro del menú del miembro.

**Independent Test**: Abrir cada uno de los tres modales, cerrarlo con × y con Escape, y confirmarlo.

**Acceptance Scenarios**:

1. **Given** cualquiera de los tres modales, **When** se abre, **Then** el foco queda en la ×.
2. **Given** un modal abierto, **When** se aprieta Escape o la ×, **Then** se cierra sin hacer nada.
3. **Given** una operación en curso, **When** se aprieta Escape o la ×, **Then** el modal no se cierra.
4. **Given** un error del servidor, **When** se confirma, **Then** el modal sigue abierto y muestra el error.
5. **Given** un administrador como destino de "Eliminar miembro", **When** se abre el modal, **Then** se ve el aviso y no hay botón de confirmación.
6. **Given** la última persona administradora, **When** abre "Salir del grupo", **Then** ve el aviso y no hay botón de confirmación.

---

### User Story 3 - Encontrar rápido en mis listas (Priority: P1)

Una persona con muchos amigos, solicitudes o grupos filtra la lista y la ordena.

**Why this priority**: Las listas crecían sin forma de encontrar a alguien más que recorriéndolas.

**Independent Test**: Con varios amigos y grupos, filtrar por una parte del nombre con y sin acentos, y cambiar el orden.

**Acceptance Scenarios**:

1. **Given** la pantalla Amigos, **When** se abre, **Then** se ven en dos columnas "Mis amigos" y "Solicitudes pendientes", cada una con su buscador.
2. **Given** amigos o solicitudes, **When** se escribe parte de un nombre, apellido o correo, **Then** solo quedan las coincidencias, sin distinguir mayúsculas ni acentos.
3. **Given** un filtro sin coincidencias en amigos o solicitudes, **When** se mira la lista, **Then** se ve un mensaje y "Buscar usuarios" lleva a `/users/search?q={término}`.
4. **Given** Mis amigos, **When** se elige un orden, **Then** se ordena por nombre A–Z, Z–A, fecha más reciente o más antigua. Los amigos sin fecha quedan al final.
5. **Given** Mis grupos, **When** se escribe parte de un nombre, **Then** solo quedan esos grupos; sin coincidencias se ve "Ningún grupo coincide con tu búsqueda".
6. **Given** Mis grupos, **When** se elige un orden, **Then** se ordena por actividad más reciente (por defecto) o más antigua, o por nombre A–Z o Z–A.
7. **Given** `/groups?q=texto`, **When** se abre, **Then** el buscador ya trae ese texto.

---

### User Story 4 - Buscar personas y grupos más rápido (Priority: P2)

Una persona busca desde la barra o desde "Buscar usuarios" sin tener que apretar un botón.

**Why this priority**: El panel del buscador global se cortaba y la búsqueda de usuarios exigía un clic extra.

**Independent Test**: Escribir en la barra un término con más de 3 personas y más de 3 grupos; escribir en "Buscar usuarios" y esperar los resultados.

**Acceptance Scenarios**:

1. **Given** más de 3 personas que coinciden, **When** se mira el buscador global, **Then** se ven 3 y "Ver más en personas" lleva a la búsqueda de usuarios con el término.
2. **Given** más de 3 grupos que coinciden, **When** se mira el buscador global, **Then** se ven 3 y "Ver más en grupos" lleva a `/groups?q={término}`.
3. **Given** "Buscar usuarios", **When** se escriben al menos 2 caracteres, **Then** se busca tras una pausa de 300 ms.
4. **Given** menos de 2 caracteres, **When** se borra el término, **Then** los resultados se vacían y no se consulta al servidor.
5. **Given** un término ya enviado con Enter, **When** pasa la pausa, **Then** no se repite la búsqueda.

---

### User Story 5 - Agregar amigos a un grupo existente (Priority: P1)

Una persona con permiso de invitar abre "Agregar miembro" y suma a varios amigos de la lista, sin escribir nombres ni correos.

**Why this priority**: Había que buscar a cada amigo por texto y elegir antes el tipo de miembro.

**Independent Test**: En un grupo con un amigo ya miembro, abrir el modal, ver a ese amigo como "Agregado", agregar a dos más y sumar un perfil simulado sin cerrar el modal.

**Acceptance Scenarios**:

1. **Given** permiso de invitar, **When** se aprieta "Agregar miembro", **Then** se abre el modal "Agregar amigos a {grupo}" con todos los amigos, su foto, nombre y correo.
2. **Given** un amigo que ya es miembro, **When** se mira la lista, **Then** su fila está sombreada con "Agregado" y sin botón.
3. **Given** un amigo que no es miembro, **When** se aprieta "Agregar", **Then** se agrega, su fila pasa a "Agregado", se ve el snackbar "Miembro agregado: {nombre}" y el modal sigue abierto.
4. **Given** un alta en curso, **When** se mira la lista, **Then** el botón dice "Agregando…" y los demás "Agregar" quedan deshabilitados.
5. **Given** el buscador del modal, **When** nadie coincide, **Then** se ve "Ningún amigo coincide con tu búsqueda".
6. **Given** una persona sin amigos, **When** abre el modal, **Then** se ve el aviso y un link a "Buscar usuarios".
7. **Given** un error al cargar los amigos, **When** se abre el modal, **Then** se informa y se puede reintentar.
8. **Given** el pie del modal, **When** se elige "Agregar perfil simulado", **Then** se ve el formulario de nombre y correo, y al enviarlo se suma el perfil.
9. **Given** un alta rechazada por el servidor, **When** se intenta, **Then** el modal muestra el motivo.

---

### User Story 6 - Elegir personas al crear un grupo (Priority: P2)

Una persona arma un grupo nuevo y ve en fichas a todas las personas que eligió.

**Why this priority**: Las casillas y la lista aparte de perfiles simulados no dejaban ver de un vistazo quién iba a estar en el grupo.

**Independent Test**: Agregar dos amigos y un perfil simulado, quitar uno desde su ficha y crear el grupo.

**Acceptance Scenarios**:

1. **Given** la lista de amigos, **When** se aprieta "Agregar" en un amigo, **Then** su fila pasa a "Agregado" y aparece su ficha con foto y nombre.
2. **Given** un perfil simulado agregado, **When** se mira la lista de fichas, **Then** aparece junto a los amigos, con borde punteado.
3. **Given** una ficha, **When** se aprieta su ×, **Then** la persona deja de estar elegida.
4. **Given** un nombre vacío o ninguna persona elegida, **When** se mira el pie, **Then** "Crear" está deshabilitado.
5. **Given** un nombre y un correo escritos sin tocar "Agregar perfil", **When** se mira el pie, **Then** "Crear" está habilitado y ese perfil se suma al crear, como en 023.
6. **Given** el formulario, **When** se aprieta "Cancelar", **Then** se vuelve a `/groups` sin crear nada.

---

### User Story 7 - Editar y configurar el grupo desde la cabecera (Priority: P2)

Una persona administradora edita los datos o la configuración del grupo sin que cambie la cabecera.

**Why this priority**: Editar reemplazaba la cabecera por el formulario y tenía otro patrón que "Configuración".

**Independent Test**: Abrir "Editar", guardar; abrir "Configuración", guardar permisos.

**Acceptance Scenarios**:

1. **Given** permiso de editar, **When** se aprieta "Editar", **Then** la cabecera sigue igual, el botón pasa a "Cerrar" y el formulario se abre en un panel debajo.
2. **Given** el panel de edición, **When** se aprieta "Guardar", **Then** se guardan los datos, se actualiza la cabecera y el panel se cierra.
3. **Given** el panel de edición, **When** se aprieta "Cerrar", **Then** el panel se cierra sin guardar.
4. **Given** "Configuración", **When** se guardan los permisos, **Then** el panel se cierra y el grupo se vuelve a consultar.
5. **Given** la cabecera, **When** se mira, **Then** "Editar" y "Configuración" quedan arriba a la derecha y "Salir" abajo a la derecha.
6. **Given** el menú de un miembro abierto, **When** se hace scroll o cambia el tamaño de la ventana, **Then** el menú se cierra.

---

### User Story 8 - Botones de formulario predecibles (Priority: P3)

Una persona edita su perfil, cambia la contraseña o crea un grupo y encuentra Cancelar y Guardar siempre en el mismo lugar.

**Why this priority**: Cada formulario tenía su orden y sus textos.

**Independent Test**: Abrir los tres formularios y ver el mismo orden.

**Acceptance Scenarios**:

1. **Given** editar perfil, cambiar contraseña o crear grupo, **When** se mira el pie, **Then** Cancelar está a la izquierda y Guardar o Crear a la derecha.
2. **Given** cambiar contraseña, **When** se aprieta "Cancelar" o "← Mi perfil", **Then** se vuelve a `/profile`.
3. **Given** editar perfil sin cambios, **When** se mira el pie, **Then** "Guardar" sigue deshabilitado, como en 005.

---

### Edge Cases

- Un filtro que se borra vuelve a mostrar la lista completa, en el orden elegido.
- Un amigo sin `friendsSince` queda al final en los dos órdenes por fecha.
- Si falla la consulta de miembros al abrir el modal de un grupo, la lista de amigos se muestra igual y el servidor rechaza a quien ya sea miembro.
- Un perfil simulado con nombre o correo vacío no se agrega y el modal informa el motivo.
- Un perfil simulado con correo repetido se rechaza al crear el grupo, como en 023.
- En el buscador global, los links "Ver más" solo aparecen cuando hay más de 3 coincidencias en esa sección.
- El menú de un miembro se posiciona fijo respecto del botón, para no quedar cortado por la tarjeta.

## Requirements *(mandatory)*

### Functional Requirements

**Pantalla principal**

- **FR-001**: Tras verificar el correo, la app MUST llevar a `/` reemplazando la entrada del historial.
- **FR-002**: La verificación MUST avisarse con un snackbar "Cuenta verificada".
- **FR-003**: `/groups` MUST NOT mostrar el mensaje de bienvenida de la verificación.
- **FR-004**: Actividad, Amigos, Mi perfil, Gastos recurrentes, Ajustes, Buscar usuarios y Mis grupos MUST mostrar el link "Ir a Pantalla principal".
- **FR-004a**: Cerrar sesión MUST limpiar la sesión y navegar a `/login` en una misma actualización, para que el siguiente login lleve a `/` y no a la pantalla protegida anterior.

**Paleta**

- **FR-005**: El color base de la interfaz MUST ser el turquesa/petróleo del logo, en modo claro y oscuro.
- **FR-006**: Las sombras MUST usar `--color-sombra`, definida en cada tema. El naranja MUST mantener los usos de 023 (FR-002, FR-003 y FR-005 de 023).

**Modal base**

- **FR-007**: "Eliminar amigo", "Eliminar miembro", "Eliminar perfil simulado" y "Salir del grupo" MUST usar el mismo modal, con título, ×, contenido y un único botón de confirmación.
- **FR-008**: El modal MUST poner el foco en la × al abrirse y MUST cerrarse con la × o con Escape.
- **FR-009**: Mientras la operación está en curso, el modal MUST NOT cerrarse.
- **FR-010**: Un error MUST mostrarse dentro del modal sin cerrarlo.

**Buscadores y orden en listas**

- **FR-011**: Mis amigos y Solicitudes pendientes MUST tener un buscador cada una, que filtre por nombre, apellido o correo sin distinguir mayúsculas ni acentos.
- **FR-012**: Sin coincidencias, MUST mostrarse un mensaje y un link a `/users/search?q={término}`.
- **FR-013**: Mis amigos MUST poder ordenarse por nombre A–Z (por defecto) o Z–A, o por fecha de amistad más reciente o más antigua. Eliminar un amigo MUST avisarse con un snackbar.
- **FR-014**: Mis grupos MUST tener un buscador por nombre, sin distinguir mayúsculas ni acentos, que tome su valor inicial de `?q=`.
- **FR-015**: Mis grupos MUST poder ordenarse por actividad más reciente (por defecto) o más antigua, o por nombre A–Z o Z–A.
- **FR-016**: Todos estos filtros MUST resolverse en el navegador, sin consultas nuevas al servidor.

**Buscador global y búsqueda de usuarios**

- **FR-017**: El buscador global MUST mostrar hasta 3 personas y 3 grupos.
- **FR-018**: Con más de 3 coincidencias en una sección, MUST mostrar "Ver más en personas" o "Ver más en grupos", que llevan a la búsqueda de usuarios o a `/groups?q={término}`.
- **FR-019**: "Buscar usuarios" MUST buscar mientras se escribe, tras 300 ms sin cambios y con al menos 2 caracteres, y MUST NOT tener botón "Buscar".
- **FR-020**: Con menos de 2 caracteres, MUST vaciar los resultados y descartar la respuesta de una búsqueda anterior.

**Pantalla de grupo**

- **FR-021**: La cabecera del grupo MUST mostrar "Editar" y "Configuración" arriba a la derecha y "Salir" abajo a la derecha. El link al historial MUST quedar en "Ver todo" de la tarjeta de actividad.
- **FR-022**: El botón de salida MUST decir "Salir" y abrir el modal "Salir del grupo". Las reglas de 020 no cambian.
- **FR-023**: "Eliminar miembro" y "Eliminar perfil simulado" del menú del miembro MUST cerrar el menú y abrir el modal base.
- **FR-024**: "Editar" y "Configuración" MUST abrir su panel debajo de la cabecera y cambiar su texto a "Cerrar" mientras está abierto. La edición de datos MUST NOT tener botón "Cancelar".
- **FR-025**: Guardar los datos o los permisos MUST cerrar el panel. Guardar los permisos MUST volver a consultar el grupo.
- **FR-026**: El menú de un miembro MUST cerrarse al hacer scroll o al cambiar el tamaño de la ventana.

**Agregar miembros a un grupo existente**

- **FR-027**: "Agregar miembro" MUST abrir el modal "Agregar amigos a {grupo}", que lista a todos los amigos (`GET /api/users/me/friends`) con foto, nombre, correo y un buscador por nombre o correo.
- **FR-028**: Los amigos que ya son miembros (`GET /api/groups/{id}/members`) MUST figurar sombreados con "Agregado" y sin botón. Los demás MUST tener "Agregar".
- **FR-029**: Agregar a un amigo MUST usar el alta de 010, marcar su fila como "Agregado", mostrar el snackbar "Miembro agregado: {nombre}", actualizar la pantalla del grupo y dejar el modal abierto.
- **FR-030**: El modal MUST tener estados de carga, sin amigos (con link a "Buscar usuarios") y error con reintento.
- **FR-031**: El pie del modal MUST permitir sumar un perfil simulado con nombre y correo.

**Crear grupo**

- **FR-032**: Cada amigo de la lista MUST tener un botón "Agregar" que lo elige; una vez elegido, su fila MUST mostrar "Agregado".
- **FR-033**: Las personas elegidas, amigos y perfiles simulados, MUST mostrarse como fichas con foto y nombre, y una × para quitarlas. Los perfiles simulados MUST distinguirse con borde punteado y un texto para lectores de pantalla.
- **FR-034**: "Crear" MUST estar deshabilitado mientras falte el nombre o no haya ninguna persona elegida ni un perfil simulado completo escrito.

**Botones de formulario**

- **FR-035**: Editar perfil, cambiar contraseña y crear grupo MUST mostrar "Cancelar" a la izquierda y "Guardar" o "Crear" a la derecha.
- **FR-036**: En cambiar contraseña, "Cancelar" MUST volver a `/profile`, y la pantalla MUST mostrar arriba el link "← Mi perfil". En crear grupo, "Cancelar" MUST volver a `/groups`.

### Key Entities *(include if feature involves data)*

- **Esta feature no agrega entidades, tablas, migraciones ni campos de API.**

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Los cuatro diálogos de confirmación comparten un solo componente.
- **SC-002**: Se puede agregar a varios amigos a un grupo existente sin cerrar el modal ni escribir ningún correo.
- **SC-003**: Una persona encuentra a un amigo o un grupo por una parte del nombre, con o sin acentos, sin consultas nuevas al servidor.
- **SC-004**: Desde cualquiera de las pantallas de FR-004 se llega a `/` con un clic.
- **SC-005**: Los textos sobre los colores nuevos mantienen al menos 4,5:1 de contraste en ambos modos.
- **SC-006**: Ninguna pantalla revisada tiene scroll horizontal a 360px.

## Assumptions

- Las listas de amigos, solicitudes y grupos de una persona son lo bastante chicas como para filtrarlas en el navegador.
- Los colores se tomaron del logo de forma aproximada y pueden ajustarse.
- La pausa de 300 ms alcanza para no consultar al servidor en cada tecla.
