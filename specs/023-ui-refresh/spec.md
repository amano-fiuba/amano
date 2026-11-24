# Feature Specification: Renovación de la interfaz

**Feature Branch**: `feat/teclas-y-patron-manos`

**Created**: 2026-11-06

**Status**: Implementada en la rama, pendiente de revisión

**Input**: User description: "Mantener la estética actual y sumarle identidad: botones en relieve, un patrón de fondo discreto con símbolos de la app y los colores del logo. Esquinas más rectas. Foto de quien envía una solicitud de amistad. Accesos a grupos y actividad personal en la barra superior, con pantalla propia de actividad. Pantalla de grupo que aproveche el ancho, con miembros, gastos y actividad a la vez, y lugar listo para mostrar saldos por miembro cuando el backend los calcule. Al crear un grupo, elegir a las personas de una lista de amigos en lugar de escribir el correo exacto, y poder sumar a más de una. El buscador debe mostrar las fotos de personas y grupos."

## Relación con otras especificaciones

Sigue la costumbre del repo (018 §Constitution Check, 021): las specs anteriores no se editan y sus extensiones se documentan acá. **Donde esta spec y una anterior difieran, vale esta.**

| Spec | Punto | Qué decía | Qué vale ahora |
|---|---|---|---|
| 002 | `contracts/users-api.md`, `GET /api/users/{id}/avatar` | Accesible para el dueño, sus amigos y quienes comparten un grupo activo | También para quien recibió una solicitud pendiente de esa cuenta, solo en ese sentido (FR-011, FR-012) |
| 011 | US3 y FR-011 | Cada solicitud muestra el avatar por defecto del emisor | Muestra su foto o, si no tiene, el avatar por defecto (FR-010, FR-013) |
| 008 | FR-007, FR-014, entidad "Resultado de Búsqueda" y `contracts/users-search-api.md` | Cada resultado muestra el avatar por defecto, sin datos de avatar | Suma `avatarUrl` si quien busca puede ver esa foto (FR-032 a FR-034) |
| 010 | US1 y `POST /api/groups` | Se crea con un único miembro adicional, indicado por correo | Se crea con uno o más miembros, elegidos de la lista de amigos o como perfiles simulados (FR-023 a FR-031). `member` sigue siendo válido |

El resto de las specs no cambia:

- **010 (Gestión de grupos)**: además de lo indicado en la tabla, cambia la disposición de la pantalla de grupo. Las reglas de permisos no se tocan. Solo se puede agregar a amigos reales, como hasta ahora.
- **014 (Historial de actividad)**: se reutiliza `GET /api/groups/{id}/activity` para las tarjetas de gastos y actividad.
- **015 (Pantalla principal)**: se suma el link "Ver todo" en "Actividad personal". La pantalla de grupo adopta sus proporciones y sus tarjetas.
- **017 y 018 (Deudas y pagos)**: no se modifican. Esta spec deja lugar para los saldos por miembro, pero no los calcula (ver el [contrato](./contracts/ui-refresh-api.md#saldos-por-miembro-pendiente)).
- **020 (Abandonar grupo)** y **021 (Extensión de grupos y buscador)**: sus reglas no cambian. El modal de salida, el botón "Editar" y `friendshipStatus` quedan dentro de la pantalla nueva, con su estética.

Los identificadores FR-### y SC-### de esta especificación son propios de 023.

## Clarifications

### Session 2026-11-06

- Q: ¿Se cambia de estética? → A: No. Se comparó un conjunto de propuestas y se decidió mantener la actual, sumando teclas en relieve, un patrón y los colores del logo.
- Q: ¿Cuánto más rectas son las esquinas? → A: Tarjetas de 8px y controles de 6px. Los chips y los círculos de la barra se mantienen.
- Q: ¿Para qué se usa el naranja del logo? → A: Para interacción y detalles: hover, presionado, acceso activo de la barra, chip "Yo" y filas de grupo en hover. El azul sigue siendo el color base.
- Q: ¿Qué texto va sobre naranja? → A: Oscuro. El blanco queda por debajo de 3:1.
- Q: ¿El patrón es una imagen? → A: Es una máscara SVG, para tomar el color de una variable en cada tema. Los símbolos son apretón de manos, billetes, persona, tarjeta y familia.
- Q: ¿Quién ve la foto de quien envía una solicitud? → A: Solo quien la recibe. Enviar una solicitud no revela la foto de la otra persona.
- Q: ¿Qué hace el acceso de actividad personal? → A: Abre una pantalla nueva, `/activity`, con los últimos 200 gastos propios.
- Q: ¿Cómo se organiza la pantalla de grupo? → A: Encabezado a todo el ancho y tres columnas: miembros, gastos y actividad.
- Q: ¿Cómo se elige a las personas al crear un grupo? → A: De una lista de amigos con casillas, con un filtro por nombre para quien tiene muchos. No hace falta escribir ningún correo.
- Q: ¿Se puede crear un grupo solo con perfiles simulados? → A: Sí, como hasta ahora. Es la salida para quien todavía no tiene amigos.
- Q: ¿Hay que elegir entre amigos o perfiles simulados? → A: No. Ahora se pueden combinar.
- Q: ¿Se reemplaza `member` en `POST /api/groups`? → A: No. Se suma `members` y `member` sigue funcionando, para no romper el contrato de 010.
- Q: ¿Qué pasa después de crear el grupo? → A: Se abre el grupo. Antes la pantalla quedaba en el formulario, que era provisorio y con el que se podía crear el grupo dos veces.
- Q: ¿El buscador muestra la foto de cualquier persona? → A: No. Muestra la foto solo si quien busca ya puede verla: amistades, grupos activos en común y solicitudes recibidas. Al resto se le muestra el avatar por defecto, para no abrir las fotos a cualquier cuenta verificada.
- Q: ¿Quién implementa los saldos por miembro? → A: Otra persona del equipo, más adelante. Habrá conversión de cotizaciones, saldos cruzados, y los perfiles simulados participan. Esta spec solo deja el componente listo.

## Fuera de alcance

- Calcular saldos por miembro, compensar deudas cruzadas o convertir monedas.
- Decidir a qué pantalla lleva "Pagar" cuando un saldo neto agrupa varias deudas.
- Un endpoint que liste los gastos de un grupo: la tarjeta de gastos se arma con el historial.
- Incluir gastos de grupo y pagos en la actividad personal, o paginarla.
- Cambiar quién puede ver una foto de perfil: el buscador respeta la regla vigente.
- Mostrar la foto en la tarjeta "Solicitudes de amistad" de la pantalla principal.
- Cambiar la pantalla de agregar miembros a un grupo ya creado.
- Buscar personas que no son amigas desde la pantalla de crear grupo.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Una interfaz con identidad propia (Priority: P1)

Una persona usa la app y la reconoce como A mano: los botones tienen relieve y responden al tocarlos, el fondo muestra símbolos de la app sin molestar, y el naranja y el turquesa del logo aparecen en los detalles.

**Why this priority**: Es el objetivo central del cambio y alcanza a todas las pantallas.

**Independent Test**: En modo claro y oscuro, pasar el cursor y apretar un botón principal, uno secundario y el "+". El patrón se ve en el fondo y nunca sobre las tarjetas.

**Acceptance Scenarios**:

1. **Given** un botón principal, **When** se pasa el cursor, **Then** se eleva y su borde inferior se vuelve naranja.
2. **Given** un botón principal, **When** se aprieta, **Then** se hunde y se pinta de naranja con texto oscuro.
3. **Given** un botón de acción destructiva, **When** se aprieta, **Then** sigue en rojo.
4. **Given** el botón "+", **When** se aprieta o queda abierto, **Then** se ve naranja en ambos modos.
5. **Given** cualquier pantalla, **When** se abre, **Then** el patrón queda detrás del contenido y no recibe clics.
6. **Given** una persona con movimiento reducido activado, **When** usa un botón, **Then** no hay transiciones.

---

### User Story 2 - Reconocer quién envía una solicitud (Priority: P1)

Una persona recibe una solicitud de amistad y ve la foto de quien la envía, en la campanita y en la pantalla "Amigos".

**Why this priority**: Evita aceptar a alguien solo por el nombre.

**Independent Test**: Con una solicitud pendiente de una cuenta con foto, la bandeja devuelve `avatarUrl`, la imagen responde 200 para quien la recibe y 404 para quien la envió al pedir la foto de la otra persona.

**Acceptance Scenarios**:

1. **Given** una solicitud de alguien con foto, **When** se abre la campanita, **Then** se ve su foto.
2. **Given** una solicitud de alguien sin foto, **When** se abre la campanita, **Then** se ve el avatar por defecto.
3. **Given** una cuenta sin relación con otra, **When** pide su avatar, **Then** recibe 404.

---

### User Story 3 - Llegar más rápido a grupos y actividad (Priority: P2)

Una persona entra a sus grupos o a su actividad desde cualquier pantalla con la barra superior.

**Why this priority**: Son destinos frecuentes que hoy requieren pasar por Inicio.

**Independent Test**: Los accesos llevan a `/activity` y `/groups`. La pantalla de actividad muestra los gastos propios, de más nuevo a más viejo.

**Acceptance Scenarios**:

1. **Given** una sesión activa, **When** se mira la barra, **Then** el orden es perfil, actividad personal, grupos, amigos, notificaciones y ajustes.
2. **Given** gastos registrados, **When** se abre `/activity`, **Then** se listan con descripción o categoría, fecha y monto, sin gastos de otras cuentas.
3. **Given** ningún gasto, **When** se abre `/activity`, **Then** se ofrece registrar el primero.
4. **Given** un error de conexión, **When** se abre `/activity`, **Then** se informa el error.
5. **Given** una pantalla de 360px, **When** se abre la campanita, **Then** el panel entra completo.

---

### User Story 4 - Ver el grupo de un vistazo (Priority: P1)

Una persona abre un grupo y ve al mismo tiempo los miembros, los últimos gastos y la actividad reciente.

**Why this priority**: La pantalla anterior era una columna angosta que desaprovechaba el ancho.

**Independent Test**: Con un grupo con gastos, el detalle muestra las tres tarjetas en escritorio y las apila en celular.

**Acceptance Scenarios**:

1. **Given** un ancho mayor a 960px, **When** se abre un grupo, **Then** se ven tres columnas: miembros, gastos y actividad.
2. **Given** un ancho de hasta 960px, **When** se abre un grupo, **Then** las tarjetas se apilan en ese orden.
3. **Given** gastos registrados, **When** se mira "Gastos", **Then** se ven los últimos 5 con link a cada uno.
4. **Given** actividad registrada, **When** se mira "Actividad", **Then** se ven los últimos 8 eventos y un link al historial.
5. **Given** un error al cargar la actividad, **When** se abre el grupo, **Then** esa tarjeta lo informa y el resto sigue funcionando.
6. **Given** un alta de miembro, **When** se completa, **Then** las tarjetas se actualizan sin recargar.

---

### User Story 5 - Armar un grupo sin escribir correos (Priority: P1)

Una persona crea un grupo y elige a sus integrantes de la lista de sus amigos, tildando a todos los que quiera. Si tiene muchos, filtra por nombre. También puede sumar perfiles simulados.

**Why this priority**: Hoy hay que saber el correo exacto del amigo y solo se puede sumar a una persona, así que crear un grupo de varias personas exige varios pasos.

**Independent Test**: Con una cuenta con tres amigos, crear un grupo tildando a dos y sumando un perfil simulado. El grupo se abre con cuatro miembros y el historial registra las tres altas.

**Acceptance Scenarios**:

1. **Given** una persona con amigos, **When** abre "Crear grupo", **Then** ve la lista de sus amigos con foto, nombre y correo, y una casilla por cada uno.
2. **Given** la lista, **When** escribe parte de un nombre en el filtro, **Then** solo ve las coincidencias, sin distinguir mayúsculas ni acentos.
3. **Given** amigos tildados, **When** cambia el filtro, **Then** siguen tildados y el contador los sigue contando.
4. **Given** dos amigos tildados y un perfil simulado, **When** crea el grupo, **Then** el grupo tiene a todos y queda abierto.
5. **Given** ninguna persona elegida, **When** intenta crear el grupo, **Then** se le pide elegir al menos una y no se crea nada.
6. **Given** una persona sin amigos, **When** abre la pantalla, **Then** ve "Todavía no tenés amigos", un enlace para buscar personas y la opción de sumar un perfil simulado.
7. **Given** un error al cargar los amigos, **When** se abre la pantalla, **Then** puede reintentar o seguir con perfiles simulados.
8. **Given** un amigo que dejó de serlo después de abrir la pantalla, **When** crea el grupo, **Then** ve el motivo y no se crea nada.

---

### User Story 6 - Reconocer a quién encontré (Priority: P2)

Una persona busca en la barra o en la pantalla de búsqueda y distingue los resultados por su foto.

**Why this priority**: Dos personas o grupos con nombres parecidos se distinguen más rápido por la imagen.

**Independent Test**: Con un amigo con foto y una cuenta sin relación con foto, buscar a ambos: se ve la foto del amigo y el avatar por defecto de la otra cuenta. Buscar un grupo propio con foto y ver su imagen.

**Acceptance Scenarios**:

1. **Given** un amigo con foto, **When** lo busco, **Then** veo su foto en la barra y en la pantalla de búsqueda.
2. **Given** un compañero de un grupo activo o alguien que me envió una solicitud, **When** lo busco, **Then** veo su foto.
3. **Given** una cuenta sin relación, o a la que yo le envié una solicitud, **When** la busco, **Then** veo el avatar por defecto, aunque tenga foto.
4. **Given** un grupo con foto, **When** lo busco en la barra, **Then** veo su foto.
5. **Given** un grupo sin foto, **When** lo busco en la barra, **Then** veo su inicial, como en "Mis grupos".

---

### Edge Cases

- Una solicitud de una cuenta sin foto no incluye `avatarUrl`: se usa el avatar por defecto.
- Si la solicitud deja de estar pendiente y no hay otra relación, el avatar vuelve a responder 404.
- Un grupo sin gastos no consulta el historial de gastos.
- Con 8 o más eventos solo se ven los últimos 8; el resto queda en el historial.
- Una persona con más de 200 gastos no ve los anteriores en `/activity`.
- En pantallas de hasta 440px se oculta el texto "A mano" de la barra para que entren los accesos; el logo sigue siendo el link al inicio.
- Mientras se editan los datos del grupo, el formulario ocupa su propia fila en el encabezado.
- Si al crear el grupo quedaron un nombre y un correo escritos sin tocar "Agregar perfil", se suman al grupo. Si falta alguno de los dos, se avisa y no se crea nada.
- Un filtro sin coincidencias muestra un mensaje; las personas ya tildadas se conservan.
- Dos perfiles simulados con el mismo correo se rechazan, porque el correo es único dentro del grupo.
- Una lista que repite a un amigo lo agrega una sola vez.

## Requirements *(mandatory)*

### Functional Requirements

**Botones y colores**

- **FR-001**: Los botones principales y secundarios MUST tener relieve con borde inferior, elevarse en hover y hundirse al apretar.
- **FR-002**: Hover y presionado MUST usar el naranja del logo, salvo en acciones destructivas, que MUST seguir en rojo.
- **FR-003**: El texto sobre un fondo naranja sólido MUST ser oscuro.
- **FR-004**: El azul MUST seguir siendo el color base de la interfaz.
- **FR-005**: El acceso activo de la barra MUST marcarse con un aro naranja claro, y el chip "Yo" MUST usar un naranja suave.
- **FR-006**: Las animaciones de los botones MUST desactivarse con `prefers-reduced-motion`.

**Esquinas y patrón**

- **FR-007**: Las esquinas de la app MUST definirse con `--radio-tarjeta` (8px) y `--radio-control` (6px).
- **FR-008**: El fondo MUST mostrar `patron-fondo.svg` como máscara, con el color de `--color-patron`, en modo claro y oscuro.
- **FR-009**: El patrón MUST quedar detrás del contenido y MUST NOT recibir clics.

**Fotos en solicitudes**

- **FR-010**: `GET /api/users/me/friendship-requests/received` MUST incluir `sender.avatarUrl` cuando quien envía tiene foto.
- **FR-011**: `GET /api/users/{id}/avatar` MUST permitir la lectura a quien recibió una solicitud pendiente de esa cuenta.
- **FR-012**: Esa regla MUST aplicar en un solo sentido: enviar una solicitud MUST NOT dar acceso a la foto de quien la recibe.
- **FR-013**: La campanita y la pantalla "Amigos" MUST mostrar la foto, o el avatar por defecto si no hay.

**Barra y actividad personal**

- **FR-014**: La barra MUST incluir accesos a "Actividad personal" y "Mis grupos", en el orden de la User Story 3.
- **FR-015**: `GET /api/expenses` MUST devolver los últimos 200 gastos de la persona logueada, de más nuevo a más viejo, y 401 sin sesión.
- **FR-016**: `/activity` MUST ser una ruta protegida con estados de carga, vacío y error.
- **FR-017**: "Actividad personal" de la pantalla principal MUST enlazar a `/activity`.

**Pantalla de grupo**

- **FR-018**: El detalle MUST mostrar un encabezado a todo el ancho y las tarjetas de miembros, gastos y actividad.
- **FR-019**: Las tarjetas MUST apilarse en anchos de hasta 960px.
- **FR-020**: Gastos y actividad MUST obtenerse de `GET /api/groups/{id}/activity`, sin endpoints nuevos.
- **FR-021**: Un error en esas tarjetas MUST NOT impedir usar el resto de la pantalla.
- **FR-022**: Los miembros MUST poder mostrar saldos según el [contrato](./contracts/ui-refresh-api.md#saldos-por-miembro-pendiente), y MUST NOT mostrar nada de deudas mientras el backend no los envíe.

Los campos que debe enviar el backend para activar los saldos están en el [contrato](./contracts/ui-refresh-api.md#saldos-por-miembro-pendiente). La fila de la persona logueada nunca muestra saldo.

**Crear grupo**

- **FR-023**: La pantalla "Crear grupo" MUST listar los amigos de la persona, con foto, nombre, correo y una casilla, y MUST NOT pedir que escriba el correo de un amigo.
- **FR-024**: La pantalla MUST permitir filtrar la lista por nombre, apellido o correo, sin distinguir mayúsculas ni acentos. La selección MUST conservarse al filtrar.
- **FR-025**: La persona MUST poder elegir varios amigos y varios perfiles simulados, y combinarlos.
- **FR-026**: La pantalla MUST mostrar cuántas personas eligió, y MUST exigir al menos una antes de llamar a la API.
- **FR-027**: La lista MUST tener estados de carga, vacío y error con reintento. Sin amigos o con un error, MUST poder crearse el grupo con perfiles simulados.
- **FR-028**: `POST /api/groups` MUST aceptar `members`, una lista de miembros adicionales con la forma de `member`. Si llegan los dos, vale `members`. `member` MUST seguir funcionando.
- **FR-029**: La operación MUST validar todos los miembros antes de crear nada: un miembro inválido MUST dejar la base sin cambios. Un amigo repetido MUST agregarse una sola vez, y un correo de perfil simulado repetido MUST rechazarse.
- **FR-030**: Cada miembro MUST registrar su evento `MEMBER_ADDED`, marcado `atCreation`.
- **FR-031**: Al crear el grupo, la pantalla MUST abrirlo reemplazando la entrada del historial.

**Fotos en el buscador**

- **FR-032**: Cada resultado de `GET /api/users/search` MUST incluir `avatarUrl` solo si la cuenta tiene foto y quien busca puede verla según `GET /api/users/{id}/avatar`. En otro caso MUST ser nulo.
- **FR-033**: La visibilidad MUST resolverse para toda la página de resultados de una vez, no con una consulta por resultado.
- **FR-034**: El buscador de la barra y la pantalla de búsqueda MUST mostrar esa foto, o el avatar por defecto si no hay.
- **FR-035**: El buscador de la barra MUST mostrar la foto de cada grupo, o su inicial si no tiene.

### Key Entities *(include if feature involves data)*

- **FriendshipUserSummary**: suma `avatarUrl`, nulo si la cuenta no tiene foto.
- **Esta feature no agrega tablas ni migraciones.**

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Los textos sobre naranja alcanzan al menos 4,5:1 de contraste en ambos modos.
- **SC-002**: En un ancho de 1280px, miembros, gastos y actividad se ven sin cambiar de pantalla.
- **SC-003**: Ninguna pantalla revisada tiene scroll horizontal, incluso a 360px.
- **SC-004**: Activar los saldos no requiere cambios de frontend si el backend respeta el contrato.
- **SC-005**: Las esquinas de toda la app se ajustan cambiando dos variables.
- **SC-006**: Se puede crear un grupo con varios amigos sin escribir ningún correo.
- **SC-007**: Un rechazo de cualquier miembro no deja ninguna fila del grupo en la base.
- **SC-008**: Ninguna búsqueda revela la foto de una cuenta que quien busca no podría ver desde `GET /api/users/{id}/avatar`.

## Assumptions

- Los colores se tomaron del logo de forma aproximada y pueden ajustarse.
- El ícono de apretón de manos del patrón es de Lucide (licencia ISC), acreditado en `patron-fondo.svg`.
- El límite de 200 gastos alcanza para una primera versión de `/activity`.
