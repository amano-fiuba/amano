# Feature Specification: Pantalla principal con sesión y nueva navegación

**Feature Branch**: `feat/pantalla-principal`

**Created**: 2026-11-05

**Status**: Implemented (pendiente validación manual)

**Input**: User description: "Gran cambio de UI inspirado en el layout de una red social: barra superior con logo, buscador e íconos (perfil, amigos, notificaciones, FAQ, ajustes) y una pantalla principal con resumen de perfil, grupos recientes y actividad personal."

## Relación con otras especificaciones

- **Reemplaza la navegación con sesión de `SiteNav`** (pedido fuera de specs). Sin sesión, la barra y la landing no cambian.
- **Modifica 004 y 010**: después del login el destino por defecto pasa de `/groups` a `/`, y `/dashboard` redirige a `/`.
- **Extiende 010**: `GET /api/groups` agrega `lastActivityAt` (cambio aditivo).
- **Extiende 007**: nuevo endpoint `GET /api/expenses/recent`.
- **Reutiliza 008**: el buscador global usa `GET /api/users/search`, y `/users/search` acepta `?q=`.
- **Reutiliza 011/012/013**: solicitudes de amistad pendientes y sus respuestas.
- **Reutiliza 002/005**: datos del perfil propio de `GET /api/users/me`.

Los identificadores FR-### y SC-### de esta especificación son propios de 015.

## Clarifications

### Session 2026-11-05

- Q: ¿Qué se ve en `/`? → A: Sin sesión, la landing actual. Con sesión, la pantalla principal nueva.
- Q: ¿La barra nueva es global? → A: Sí, en todas las pantallas con sesión.
- Q: ¿Dónde quedan tema y cierre de sesión? → A: En una pantalla `/settings` mínima.
- Q: ¿Qué hace el acceso a FAQ? → A: Lleva a `/faq`, una pantalla "Próximamente".
- Q: ¿Qué muestra la campanita? → A: Por ahora solo solicitudes de amistad pendientes. Otros tipos de notificación se sumarán en el futuro.
- Q: ¿Qué busca el buscador? → A: Personas (buscador de 008) y solo los grupos de los que la persona es miembro.
- Q: ¿Cómo se eligen los grupos? → A: Los 5 con actividad más reciente, informada por el backend.
- Q: ¿Dónde están las acciones de crear? → A: En un botón "+" en la esquina inferior izquierda, con un desplegable. El resumen de grupos tiene aparte un acceso "Ir a Grupos".
- Q: ¿Qué es "Actividad personal"? → A: Los últimos gastos registrados por la persona.
- Q: ¿Qué muestra el resumen de perfil? → A: Foto, nombre, CBU, monedas y correo.
- Q: ¿Se mantiene la columna "Actividad reciente" del boceto? → A: No, se descarta. El espacio libre de la columna derecha muestra solicitudes pendientes.
- Q: ¿Cómo se ve el acceso a perfil en la barra? → A: Con la foto de perfil, o el avatar por defecto si no tiene una propia.
- Decisión de implementación: tras verificar el correo se sigue llegando a `/groups`, porque esa pantalla ya muestra la bienvenida con el primer paso (crear un grupo).

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Pantalla principal (Priority: P1) 🎯 MVP

Una persona con sesión entra a `/` y ve de un vistazo su perfil, sus grupos más activos, sus últimos gastos y sus solicitudes pendientes.

**Why this priority**: Es el nuevo punto de entrada de la aplicación.

**Independent Test**: Con una cuenta con grupos y gastos, entrar a `/` y comprobar cada sección. Cerrar sesión y comprobar que se ve la landing.

**Acceptance Scenarios**:

1. **Given** una persona sin sesión, **When** abre `/`, **Then** ve la landing actual.
2. **Given** una persona con sesión, **When** abre `/`, **Then** ve la pantalla principal, sin destello de la landing.
3. **Given** una persona que inicia sesión sin destino previo, **When** el login termina, **Then** llega a `/`.
4. **Given** una persona con sesión, **When** ve el resumen, **Then** se muestran foto, nombre, CBU/CVU, monedas y correo; un CBU ausente se muestra como "No informado" y sin monedas se muestra ARS.
5. **Given** más de 5 grupos, **When** ve "Grupos", **Then** ve los 5 de actividad más reciente, primero el más reciente, y "Ir a Grupos" lleva a `/groups`.
6. **Given** gastos registrados, **When** ve "Actividad personal", **Then** ve hasta 5 gastos, primero el último registrado, con categoría o descripción, monto, moneda y fecha.
7. **Given** una sección sin datos o con error de carga, **When** se muestra, **Then** se ve un estado vacío o un mensaje de error solo en esa sección, y las demás siguen funcionando.
8. **Given** solicitudes pendientes, **When** la persona acepta o rechaza una en la pantalla principal, **Then** la solicitud sale de la lista y la campanita se actualiza.

---

### User Story 2 - Barra superior global (Priority: P1)

Con sesión, cada pantalla muestra el logo, el buscador y los accesos a perfil, amigos, notificaciones, FAQ y ajustes.

**Why this priority**: Reemplaza la navegación actual; sin ella las pantallas quedan sin accesos.

**Independent Test**: Recorrer cada ícono y comprobar su destino.

**Acceptance Scenarios**:

1. **Given** una persona con sesión, **When** presiona el logo, **Then** va a `/`.
2. **Given** una persona con sesión, **When** presiona perfil, amigos, FAQ o ajustes, **Then** va a `/profile`, `/friends`, `/faq` o `/settings`.
3. **Given** solicitudes pendientes, **When** se muestra la barra, **Then** la campanita indica la cantidad.
4. **Given** la campanita, **When** se presiona, **Then** se despliega la lista con un acceso a `/friends`; sin solicitudes muestra "No tenés notificaciones".
5. **Given** el desplegable abierto, **When** se presiona Escape o se hace clic afuera, **Then** se cierra.
6. **Given** una persona en `/settings`, **When** cambia el tema o cierra sesión, **Then** el comportamiento es el mismo que tenía la barra anterior.
7. **Given** una persona sin sesión, **When** ve la barra, **Then** se muestra la barra pública actual.

---

### User Story 3 - Buscador global (Priority: P2)

La persona escribe en la barra y encuentra personas y sus propios grupos.

**Why this priority**: Unifica dos búsquedas, pero existen caminos alternativos.

**Independent Test**: Escribir un término que coincida con una persona y un grupo propio, y abrir cada resultado.

**Acceptance Scenarios**:

1. **Given** menos de 2 caracteres, **When** se escribe, **Then** no se busca ni se muestra el desplegable.
2. **Given** un término válido, **When** termina de escribirse, **Then** se ven los resultados separados en "Personas" y "Grupos".
3. **Given** un grupo del que la persona no es miembro, **When** se busca su nombre, **Then** no aparece.
4. **Given** un grupo en los resultados, **When** se elige, **Then** va a `/groups/{id}`.
5. **Given** una persona en los resultados, o al presionar Enter, **When** se confirma, **Then** va a `/users/search?q={término}` con la búsqueda ya ejecutada, donde puede enviar la solicitud de amistad.
6. **Given** un error al buscar personas, **When** se muestran los resultados, **Then** se avisa del error y los grupos siguen visibles.

---

### User Story 4 - Botón "+" de acciones rápidas (Priority: P2)

Un botón fijo en la esquina inferior izquierda despliega las acciones de creación.

**Why this priority**: Conserva el acceso a acciones que dejan de estar en la barra.

**Independent Test**: Abrir el botón desde cualquier pantalla con sesión y usar cada opción.

**Acceptance Scenarios**:

1. **Given** una persona con sesión, **When** presiona "+", **Then** ve "Crear grupo", "Registrar gasto" y "Gastos recurrentes".
2. **Given** el menú abierto, **When** elige una opción, **Then** va a `/groups/new`, `/expenses/new` o `/expenses/recurring` y el menú se cierra.
3. **Given** una persona sin sesión, **When** ve cualquier pantalla, **Then** el botón no aparece.

### Edge Cases

- Una respuesta de búsqueda anterior no reemplaza a una más nueva.
- Un 401 en cualquier carga de la pantalla principal limpia la sesión.
- Un grupo sin eventos de actividad usa su fecha de creación como última actividad.
- Un error de carga en la campanita no bloquea la barra.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: `/` MUST mostrar la landing sin sesión y la pantalla principal con sesión, sin renderizar ninguna mientras se verifica la sesión.
- **FR-002**: El login sin destino previo y `/dashboard` MUST llevar a `/`.
- **FR-003**: La barra con sesión MUST reemplazar la navegación anterior en todas las pantallas; la barra sin sesión MUST no cambiar.
- **FR-004**: La campanita MUST mostrar solicitudes de amistad pendientes y actualizarse al responder una solicitud.
- **FR-005**: El buscador MUST usar el mínimo de 2 caracteres de 008, y buscar grupos solo entre los del usuario.
- **FR-006**: `/users/search` MUST ejecutar la búsqueda indicada por `?q=`.
- **FR-007**: `GET /api/groups` MUST incluir `lastActivityAt` para cada grupo, sin cambiar el orden ni los demás campos.
- **FR-008**: `GET /api/expenses/recent` MUST devolver los últimos 5 gastos propios por fecha de registro, y nunca gastos ajenos.
- **FR-009**: `/settings` MUST ofrecer el cambio de tema y el cierre de sesión; `/faq` MUST mostrar "Próximamente". Ambas son rutas protegidas.
- **FR-010**: El botón "+" MUST estar disponible en todas las pantallas con sesión.
- **FR-011**: La interfaz MUST funcionar en modo claro y oscuro y adaptarse a una sola columna en pantallas angostas.

### Key Entities

Sin entidades ni migraciones nuevas. `lastActivityAt` se deriva de `group_activity`.

## Success Criteria *(mandatory)*

- **SC-001**: Desde la pantalla principal se llega en un paso a perfil, amigos, grupos, FAQ, ajustes y a las tres acciones de creación.
- **SC-002**: Ningún dato de otra cuenta aparece en "Actividad personal", y ningún grupo ajeno aparece en el buscador.
- **SC-003**: Los tests de frontend y backend pasan, con cobertura mínima de líneas del 85 %.

## Out of Scope

- Sistema de notificaciones en backend, contenido del FAQ y otros ajustes.
- Saldos y deudas en la pantalla principal.
- Paginación de los últimos gastos y perfil público de otras personas.
- Rediseño de las demás pantallas.
