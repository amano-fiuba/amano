# Feature Specification: Ver listado de amigos

**Feature Branch**: `feat/visualizar-listado-amigos`

**Created**: 2026-11-01

**Status**: Implemented (pendiente validación manual)

**Input**: User description: "Ver listado de amigos (Sprint 3, 1 SP, responsable Nahuel). Visualizar el catálogo completo de contactos y amigos de la red."

## Relación con otras especificaciones

- **Reutiliza 011 (Enviar solicitud de amistad)**: la amistad es una solicitud en estado `ACCEPTED` de la tabla `friendship_requests` (`V12`). No se crea una tabla de amigos ni una migración nueva.
- **Reutiliza 012 (Aceptar y rechazar solicitudes)**: la aceptación es la única forma de crear una amistad. 012 dejó explícitamente "la lista de amigos visualizada para la historia posterior": esta es esa historia.
- **Extiende la pantalla "Amigos" de 011/012**: se agrega la sección "Mis amigos" en `/friends`, encima de la sección "Solicitudes", que no cambia de comportamiento salvo que, al aceptar una solicitud, la lista de amigos se actualiza.
- **Reutiliza 001/003/004**: la sesión (`amano_auth`) identifica a la persona y la regla de cuenta verificada se hereda al exponer el endpoint bajo `/api/users/**`.
- **No modifica 010 (Gestión de grupos)**: el puerto `FriendshipPort` de grupos sigue resuelto por `NoOpFriendshipPort`. **(Actualización 2026-11-04: resuelto en el fix de 010, `AcceptedFriendshipPort` reutiliza `findFriendsOf` de esta feature.)** Conectarlo con las amistades reales cambia el comportamiento de "Agregar miembros" y todas las pruebas de grupos, por lo que queda como historia aparte.

Los identificadores FR-### y SC-### de esta especificación son propios de 013.

## Clarifications

### Session 2026-11-01

- Q: ¿Qué se considera "amigo"? → A: Toda cuenta con la que exista una solicitud `ACCEPTED`, sin importar quién la envió.
- Q: ¿Dónde se ve la lista? → A: En la pantalla existente "Amigos" (`/friends`), en una sección "Mis amigos" ubicada antes de "Solicitudes".
- Q: ¿Qué datos se muestran por amigo? → A: Nombre, apellido, correo, avatar por defecto y la fecha desde la que son amigos (fecha de aceptación). Ningún dato privado (CBU/CVU, monedas, contraseña).
- Q: ¿Orden? → A: Alfabético por nombre y luego apellido, sin distinguir mayúsculas, con los apellidos vacíos al final y el id como desempate; el mismo criterio que el buscador de 008.
- Q: ¿Hay búsqueda, paginación o acción de eliminar amigo? → A: No. "Catálogo completo" sin paginación; eliminar amistad no está en el cronograma.
- Q: ¿Se integra con grupos? → A: No en 013 (ver Relación con otras especificaciones).
- Q: Numeración → A: `013-friends-list`; 011 y 012 ya están en `sprint-3`.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Ver mis amigos (Priority: P1) 🎯 MVP

Una persona autenticada y verificada abre "Amigos" desde la barra de navegación y ve la sección "Mis amigos" con todas las cuentas con las que tiene una amistad, en orden alfabético, cada una con nombre, apellido, correo, avatar por defecto y la fecha desde la que son amigos, más un contador total.

**Why this priority**: Es el valor completo de la historia: sin esta lista no hay forma de consultar la red de contactos.

**Independent Test**: Con tres cuentas verificadas, crear amistades aceptadas en ambas direcciones, abrir "Amigos" con la cuenta central y comprobar que aparecen exactamente esas amistades, con sus datos y en orden alfabético.

**Acceptance Scenarios**:

1. **Given** una persona con amistades aceptadas, **When** abre "Amigos", **Then** la sección "Mis amigos" muestra cada amigo con nombre, apellido, correo, avatar por defecto y "Amigos desde DD/MM/AAAA".
2. **Given** amistades donde la persona fue emisora y otras donde fue destinataria, **When** se lista, **Then** aparecen todas, mostrando siempre los datos de la otra cuenta.
3. **Given** varios amigos, **When** se lista, **Then** se ordenan por nombre y apellido sin distinguir mayúsculas, con apellido vacío al final.
4. **Given** una persona con N amigos, **When** se lista, **Then** se muestra el contador "1 amigo" o "N amigos".
5. **Given** un amigo sin apellido, **When** se lista, **Then** se muestra solo el nombre, sin "null" ni espacios sobrantes.

---

### User Story 2 - Solo amistades reales y propias (Priority: P1)

La lista contiene únicamente amistades de la persona autenticada. Las solicitudes pendientes o rechazadas, y las amistades entre terceros, no aparecen.

**Why this priority**: Mostrar como amigo a alguien que no lo es, o a amigos de otras personas, rompe la privacidad y la confianza en la red.

**Independent Test**: Preparar solicitudes pendientes, rechazadas y amistades ajenas, y verificar que ninguna aparece en la lista.

**Acceptance Scenarios**:

1. **Given** una solicitud `PENDING` enviada o recibida, **When** se lista, **Then** esa cuenta no aparece en "Mis amigos".
2. **Given** una solicitud `REJECTED`, **When** se lista, **Then** esa cuenta no aparece.
3. **Given** una amistad entre otras dos cuentas, **When** se lista, **Then** no aparece.
4. **Given** una persona sin amistades, **When** abre "Amigos", **Then** ve "Todavía no tenés amigos" y un acceso a "Buscar usuarios".

---

### User Story 3 - Lista al día al aceptar, acceso protegido y errores (Priority: P2)

Al aceptar una solicitud desde la sección "Solicitudes", la nueva amistad aparece en "Mis amigos" sin recargar la página. Solo una sesión verificada accede a la lista, y los errores de red y de sesión se informan sin mostrar datos obsoletos.

**Why this priority**: Completa la experiencia; el listado básico ya entrega el valor principal.

**Independent Test**: Aceptar una solicitud y ver aparecer al amigo; llamar al endpoint sin sesión y con cuenta no verificada; simular errores de red y sesión expirada en la interfaz.

**Acceptance Scenarios**:

1. **Given** una solicitud pendiente, **When** la persona la acepta, **Then** la lista "Mis amigos" se vuelve a consultar y muestra a la nueva amistad.
2. **Given** una solicitud pendiente, **When** la persona la rechaza, **Then** la lista "Mis amigos" no cambia.
3. **Given** una petición sin sesión o de una cuenta no verificada, **When** se pide la lista, **Then** el sistema responde 401 sin datos.
4. **Given** un error de red o del servidor al cargar la lista, **When** finaliza el intento, **Then** se muestra "No se pudo cargar tu lista de amigos. Intentá nuevamente" con opción de reintentar y sin datos previos.
5. **Given** una sesión expirada al cargar la lista, **When** el servidor responde 401, **Then** se cierra la sesión local y se redirige al acceso.
6. **Given** un error al cargar la lista, **When** la sección "Solicitudes" carga bien, **Then** cada sección muestra su propio estado de forma independiente.

---

### Edge Cases

- **Amistad iniciada por cualquiera de los dos**: la consulta contempla ambas direcciones y devuelve siempre la otra cuenta.
- **Solicitud rechazada y luego aceptada**: la historia de rechazos no duplica a la persona; solo cuenta la fila `ACCEPTED`, única por pareja (`uq_friendship_requests_accepted_pair`).
- **Cuenta eliminada**: la FK con `ON DELETE CASCADE` borra la amistad, así que no aparece en la lista.
- **Muchos amigos**: se listan todos, sin paginación.
- **Nombres con mayúsculas y minúsculas mezcladas**: el orden no distingue mayúsculas.
- **Aceptar con la lista de amigos en error**: al aceptar se vuelve a consultar la lista; si la consulta tiene éxito, el error desaparece.
- **Doble carga simultánea** (montaje + aceptación): la respuesta de una consulta anterior nunca pisa a una más reciente.

## Requirements *(mandatory)*

### Functional Requirements

**Listado (US1, US2)**

- **FR-001**: El sistema MUST ofrecer, dentro de la pantalla protegida "Amigos", una sección "Mis amigos" con todas las amistades de la persona autenticada.
- **FR-002**: Una amistad MUST ser una solicitud en estado `ACCEPTED` en la que la persona autenticada sea emisora o destinataria; la lista MUST mostrar siempre la otra cuenta.
- **FR-003**: La lista MUST excluir solicitudes `PENDING` y `REJECTED` y amistades en las que la persona autenticada no participe.
- **FR-004**: Por cada amigo, la respuesta y la pantalla MUST incluir únicamente id, nombre, apellido, correo y fecha desde la que son amigos (fecha de aceptación); MUST NOT incluir CBU/CVU, monedas, contraseña ni otros datos privados. La pantalla muestra el avatar por defecto.
- **FR-005**: La lista MUST ordenarse por nombre y luego apellido sin distinguir mayúsculas, con apellido vacío al final y el id como desempate.
- **FR-006**: La pantalla MUST mostrar el total de amigos ("1 amigo" / "N amigos").
- **FR-007**: Sin amistades, la sección MUST mostrar exactamente "Todavía no tenés amigos" y un enlace a "Buscar usuarios" (`/users/search`).
- **FR-008**: La lista MUST ser completa, sin paginación ni filtro.

**Actualización e interfaz (US3)**

- **FR-009**: Al aceptar una solicitud con éxito desde "Solicitudes", la interfaz MUST volver a consultar la lista de amigos; rechazar MUST NOT hacerlo.
- **FR-010**: Al cargar la lista, la interfaz MUST distinguir carga, lista, vacío y error, de forma independiente de la sección "Solicitudes".
- **FR-011**: Ante error de red o del servidor, la interfaz MUST mostrar exactamente "No se pudo cargar tu lista de amigos. Intentá nuevamente" con la opción "Reintentar" y MUST NOT mostrar datos obsoletos.
- **FR-012**: Ante un 401, la interfaz MUST cerrar la sesión local y redirigir al acceso.

**Acceso y alcance**

- **FR-013**: El endpoint MUST requerir una sesión válida de una cuenta verificada y MUST responder 401 en caso contrario; MUST vivir bajo `/api/users/**` para heredar la regla de 003.
- **FR-014**: La persona MUST identificarse solo por su sesión; el endpoint MUST NOT aceptar un id de usuario por parámetro.
- **FR-015**: La feature MUST NOT crear migraciones, modificar `friendship_requests`, eliminar amistades ni cambiar el comportamiento de grupos (010).

### Key Entities *(include if feature involves data)*

- **Amistad** (existente, conceptual): solicitud `ACCEPTED` entre dos cuentas; la fecha de aceptación (`responded_at`) es "amigos desde".
- **Amigo** (vista nueva, solo lectura): la otra cuenta de una amistad, con id, nombre, apellido, correo y fecha de amistad.
- **Cuenta de usuario** (existente, solo lectura): no se crean ni modifican cuentas.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Una persona abre "Amigos" y ve su lista completa en menos de 2 segundos en condiciones normales de red.
- **SC-002**: El 100 % de las amistades aceptadas de la persona aparece en la lista, sin importar quién envió la solicitud.
- **SC-003**: El 0 % de solicitudes pendientes, rechazadas o amistades de terceros aparece en la lista.
- **SC-004**: El 100 % de las peticiones sin sesión o con cuenta no verificada se rechaza con 401.
- **SC-005**: Ninguna respuesta ni pantalla expone CBU/CVU, monedas ni contraseña.
- **SC-006**: Tras aceptar una solicitud, la nueva amistad aparece en la lista en la misma pantalla, sin recarga manual.

## Assumptions

- La sesión, la cookie `amano_auth` y la regla de cuenta verificada son las de 001, 003 y 004.
- Las amistades solo se crean aceptando solicitudes (012); 013 no escribe datos.
- El volumen de amigos por persona es bajo (proyecto académico); una sola consulta sin paginación es suficiente y no se agregan índices.
- La fecha "Amigos desde" se muestra en formato `DD/MM/AAAA` en la zona horaria de Argentina.
- Eliminar una amistad, buscar dentro de la lista y la integración con grupos quedan fuera de alcance.
- Los textos se escriben en español con voseo.
- No se agregan dependencias nuevas.
