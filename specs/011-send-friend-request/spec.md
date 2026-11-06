# Feature Specification: Enviar solicitud de amistad

**Feature Branch**: `feat/send-friend-request`

**Created**: 2026-10-30

**Status**: Draft

**Input**: User description: "Nro: 26. Título: Enviar solicitud de amistad. Como usuario quiero enviar una solicitud de amistad a otro usuario existente para poder incorporarlo a mi red de amigos, compartir gastos e incluirlo a grupos. Criterios de aceptación: el sistema debe permitir buscar usuarios registrados en la plataforma; el sistema debe permitir enviar una solicitud de amistad a un usuario existente; el sistema no debe permitir enviar una solicitud de amistad al propio usuario; el sistema no debe permitir enviar una nueva solicitud si ya existe una solicitud pendiente entre ambos usuarios; el sistema no debe permitir enviar una solicitud si los usuarios ya son amigos; el usuario destinatario debe recibir una notificación de que tiene una nueva solicitud de amistad; la solicitud debe quedar registrada con estado Pendiente hasta que el destinatario la acepte o rechace."

## Relación con otras especificaciones

- **Reutiliza 008 (Buscador de usuarios)**: la búsqueda ya existe y es el punto de entrada. Los resultados exponen `id`, nombre, apellido y correo; esta feature usa el `id` para enviar la solicitud y habilita el botón "Agregar" que 008 dejó deshabilitado. El contrato de búsqueda no cambia.
- **Reutiliza 001/003/004 (cuenta, verificación de correo y sesión)**: la cookie `amano_auth` identifica al emisor y la regla de cuenta verificada se hereda al exponer el endpoint bajo `/api/users/**`.
- **Introduce el dominio de amistades**: crea la entidad conceptual de solicitud de amistad y su persistencia. **No modifica** el comportamiento de las specs anteriores.
- **Aceptar y rechazar solicitudes quedan fuera de alcance**: la solicitud se crea y permanece `PENDIENTE`; la respuesta del destinatario y la lista de amigos serán historias posteriores. Esta spec sí define el estado `ACEPTADA` como la representación conceptual de una amistad, porque el criterio "no enviar si ya son amigos" debe poder evaluarse.
- **Sustituye el "próximamente" de 008** solo en la interacción de envío: la nota "Las solicitudes de amistad estarán disponibles próximamente" y el botón deshabilitado se reemplazan por la acción real.

Los identificadores FR-### y SC-### de esta especificación son propios de 011 y no coinciden necesariamente en número con los de otras specs.

## Clarifications

### Session 2026-10-30

- Q: ¿Cómo se materializa la notificación al destinatario? → A: Con una pantalla nueva `Amigos` que contiene una sección "Solicitudes" con las solicitudes pendientes recibidas. No se envían correos.
- Q: ¿Aceptar y rechazar forman parte de esta historia? → A: No. Solo se envía y se consultan las pendientes recibidas. Aceptar/rechazar será otra historia; el estado se modela para soportarlo después.
- Q: ¿Cómo se representa la amistad para validar "ya son amigos"? → A: La amistad es una solicitud en estado aceptado. El detalle del esquema se define en `data-model.md`; no se crea una tabla de amigos separada en 011.
- Q: ¿Desde dónde se envía? → A: Habilitando el botón "Agregar" de los resultados del buscador (008).
- Q: Si A ya tiene una solicitud pendiente hacia B, ¿B puede enviarle otra a A? → A: No. Se rechaza como duplicada: hay una solicitud pendiente entre ambos, sin importar la dirección.
- Q: ¿Dónde ve el destinatario sus solicitudes? → A: Dentro de la pantalla `Amigos`, en una sección "Solicitudes" con las pendientes recibidas. La visualización de la lista de amigos queda para otra historia.
- Q: Numeración → A: `011-send-friend-request`, acordado con el equipo el 2026-10-30.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Enviar una solicitud de amistad desde el buscador (Priority: P1)

Una persona autenticada y verificada busca a otra cuenta desde "Buscar usuarios", ve el resultado y pulsa "Agregar". La solicitud se envía, se registra como pendiente y la interfaz confirma "Solicitud de amistad enviada"; el botón de ese resultado pasa a "Solicitud enviada" y queda deshabilitado mientras la pantalla siga abierta.

**Why this priority**: Es el valor central de la historia: sin el envío y la persistencia no existe ninguna relación que el destinatario pueda ver.

**Independent Test**: Con dos cuentas verificadas, buscar a la segunda desde la primera, pulsar "Agregar" y comprobar el mensaje de éxito, el cambio de estado del botón y la existencia de una solicitud `PENDIENTE` con emisor y destinatario correctos.

**Acceptance Scenarios**:

1. **Given** una persona autenticada y verificada en los resultados de "Buscar usuarios", **When** pulsa "Agregar" en la fila de otra cuenta verificada, **Then** el botón muestra "Enviando…" y luego "Solicitud enviada" deshabilitado, y aparece "Solicitud de amistad enviada".
2. **Given** el envío exitoso, **When** se consulta la persistencia, **Then** existe una única solicitud con ese emisor, ese destinatario, estado `PENDIENTE` y fecha de creación.
3. **Given** una persona con la pantalla de búsqueda abierta, **When** envía solicitudes a varias cuentas, **Then** cada fila gestiona su propio estado y un envío no altera las demás.
4. **Given** una solicitud ya enviada desde la pantalla actual, **When** se vuelve a pulsar su botón, **Then** no se ejecuta un segundo envío.

---

### User Story 2 - Reglas de negocio al enviar (Priority: P1)

El sistema rechaza los envíos inválidos: hacia la propia cuenta, cuando ya existe una solicitud pendiente entre ambos usuarios en cualquier dirección, cuando ambos ya son amigos, y cuando el destinatario no existe o no está verificado. En todos los casos no se crea ninguna solicitud y se informa el motivo con un mensaje claro.

**Why this priority**: Evita relaciones duplicadas, inconsistentes o no deseadas; es condición de integridad del dominio y parte explícita de los criterios de aceptación.

**Independent Test**: Con cuentas preparadas, intentar cada caso (auto-solicitud, pendiente en el mismo sentido, pendiente en sentido inverso, amistad existente, destinatario inexistente o no verificado) y verificar que no se crea ninguna fila y que aparece el mensaje correspondiente.

**Acceptance Scenarios**:

1. **Given** una petición de envío cuyo destinatario es la propia persona que la envía, **When** se procesa, **Then** se rechaza con 400 y "No podés enviarte una solicitud de amistad a vos mismo" sin crear nada.
2. **Given** una solicitud `PENDIENTE` del emisor hacia el destinatario, **When** el emisor intenta enviar otra al mismo destinatario, **Then** se rechaza con 409 y "Ya existe una solicitud de amistad pendiente entre ambos usuarios".
3. **Given** una solicitud `PENDIENTE` del destinatario hacia el emisor (dirección inversa), **When** el emisor intenta enviarla, **Then** se rechaza con el mismo 409 y el mismo mensaje del escenario anterior.
4. **Given** una amistad existente entre ambos usuarios (solicitud en estado aceptado), **When** cualquiera intenta enviar una nueva solicitud, **Then** se rechaza con 409 y "Ya son amigos".
5. **Given** un destinatario inexistente o una cuenta no verificada, **When** se intenta enviar la solicitud, **Then** se responde 404 con "No se encontró el usuario" y no se revela si la cuenta existe.
6. **Given** un identificador de destinatario ausente o inválido, **When** se procesa la petición, **Then** se responde 400 con un error de campo sobre `recipientId` y no se consulta ni escribe la relación.

---

### User Story 3 - Ver las solicitudes pendientes recibidas (Priority: P2)

Desde la nueva pantalla "Amigos" de la barra de navegación, la persona destinataria ve la sección "Solicitudes" con las solicitudes pendientes que recibió, ordenadas de la más reciente a la más antigua, cada una con el nombre, apellido, correo y foto del emisor (o avatar por defecto si no tiene) y una indicación de que está pendiente. Si no tiene ninguna, ve un mensaje de estado vacío.

**Why this priority**: Es la "notificación" que pide el criterio de aceptación. El envío ya aporta el valor principal, pero sin esta bandeja el destinatario no se enteraría.

**Independent Test**: Enviar solicitudes desde una cuenta a otra, iniciar sesión como destinataria, abrir "Amigos" y comprobar que la sección "Solicitudes" lista exactamente las pendientes recibidas, con los datos permitidos y en el orden definido; comprobar también el estado vacío.

**Acceptance Scenarios**:

1. **Given** una persona con solicitudes pendientes recibidas, **When** abre "Amigos", **Then** la sección "Solicitudes" muestra cada una con nombre, apellido, correo y foto del emisor, o avatar por defecto si no tiene.
2. **Given** varias solicitudes pendientes recibidas, **When** se listan, **Then** aparecen de la más reciente a la más antigua.
3. **Given** una persona sin solicitudes pendientes recibidas, **When** abre "Amigos", **Then** ve "No tenés solicitudes de amistad pendientes".
4. **Given** que la persona es emisora de una solicitud pero no destinataria, **When** abre "Amigos", **Then** esa solicitud no aparece en su sección "Solicitudes".
5. **Given** una persona sin sesión, **When** intenta abrir "Amigos", **Then** se la redirige al flujo de acceso sin mostrar datos.

---

### User Story 4 - Acceso protegido, concurrencia y errores (Priority: P2)

Solo una persona autenticada con cuenta verificada puede enviar solicitudes o ver su bandeja. Aunque el mismo envío se dispare dos veces por un doble clic, dos pestañas o dos peticiones concurrentes, no puede quedar más de una solicitud pendiente entre la misma pareja. Los fallos de red y de sesión se informan sin mostrar éxitos falsos.

**Why this priority**: Protege la privacidad y la integridad ante usos reales; el flujo feliz y las validaciones básicas ya entregan el valor principal.

**Independent Test**: Solicitar los endpoints sin sesión y con cuenta no verificada; disparar dos envíos simultáneos entre la misma pareja y verificar una sola fila; simular caída de red y sesión expirada en la interfaz.

**Acceptance Scenarios**:

1. **Given** una petición de envío o de listado sin sesión válida, **When** se procesa, **Then** el sistema responde 401 y no devuelve ni modifica datos.
2. **Given** una cuenta no verificada, **When** intenta enviar una solicitud o abrir "Amigos", **Then** recibe 401 y no accede a la funcionalidad.
3. **Given** dos envíos concurrentes de la misma persona al mismo destinatario, **When** ambos llegan al servidor, **Then** se crea como máximo una solicitud pendiente y el segundo intento informa el conflicto.
4. **Given** un error de red o del servidor al enviar, **When** finaliza el intento, **Then** se muestra "No se pudo enviar la solicitud. Intentá nuevamente" y no se marca el botón como enviado.
5. **Given** un error de red o del servidor al cargar "Amigos", **When** finaliza el intento, **Then** se muestra "No se pudieron cargar tus solicitudes. Intentá nuevamente" con opción de reintentar.
6. **Given** una sesión expirada durante el envío o al cargar la bandeja, **When** el servidor responde 401, **Then** se cierra la sesión local y se redirige al acceso sin dejar datos visibles.

---

### Edge Cases

- **Doble clic en "Agregar"**: el botón se deshabilita mientras el envío está en curso; si aun así llegan dos peticiones, la restricción de unicidad deja una sola.
- **Solicitud inversa pendiente**: si B ya se la envió a A, A no puede enviarle otra; se trata como duplicada (409).
- **Solicitud previa rechazada**: una solicitud rechazada es histórica y no bloquea un envío nuevo; se permite crear otra siempre que no haya una pendiente ni una amistad.
- **Solicitud previa aceptada**: equivale a amistad; no se permite enviar otra (409).
- **Destinatario eliminado entre la búsqueda y el envío**: 404 "No se encontró el usuario".
- **Destinatario no verificado**: no es descubrible por la búsqueda de 008 y, por API directa, responde igual que inexistente (404).
- **Auto-solicitud por API manipulada**: la propia búsqueda excluye la cuenta, pero el backend igualmente rechaza el envío con 400.
- **La fila buscada ya no está visible**: el envío usa el `id` del resultado; si la cuenta dejó de cumplir los requisitos, se informa el error sin crear la solicitud.
- **Recarga de la pantalla de búsqueda después de enviar**: la búsqueda de 008 no devuelve el estado de la relación, por lo que "Agregar" vuelve a mostrarse; al pulsarlo, el backend rechaza el duplicado con el mensaje correspondiente. No se inventa un estado local persistente.
- **Muchas solicitudes pendientes recibidas**: se listan todas, de la más reciente a la más antigua; no hay paginación en 011.
- **Emisor sin apellido** (apellido opcional en 001): en la bandeja se muestra solo el nombre, sin texto vacío ni "null".
- **Sesión expirada durante la bandeja o un envío**: 401, cierre de sesión local y redirección al acceso.
- **Error de red durante una carga con datos previos**: no se conservan datos obsoletos; se muestra el mensaje fijo de reintento.

## Requirements *(mandatory)*

### Functional Requirements

**Envío (US1, US2, US4)**

- **FR-001**: El sistema MUST habilitar el botón "Agregar" de cada resultado del buscador de 008 y MUST NOT mostrar la nota de "próximamente" de 008.
- **FR-002**: Al pulsar "Agregar", el sistema MUST enviar una solicitud de amistad al usuario de esa fila usando el identificador del resultado; la persona emisora MUST identificarse exclusivamente por su sesión, nunca por un dato del cuerpo de la petición.
- **FR-003**: Toda solicitud válida MUST persistirse con emisor, destinatario, estado `PENDIENTE` y fecha de creación, y MUST permanecer en ese estado hasta que otra historia implemente la aceptación o el rechazo.
- **FR-004**: El sistema MUST rechazar con 400 el envío cuando el destinatario sea la propia persona y MUST NOT crear ninguna solicitud.
- **FR-005**: El sistema MUST rechazar con 409 cuando ya exista una solicitud `PENDIENTE` entre ambos usuarios en cualquier dirección y MUST NOT crear una segunda.
- **FR-006**: El sistema MUST rechazar con 409 cuando ambos usuarios ya sean amigos (solicitud en estado aceptado) y MUST NOT crear una nueva solicitud.
- **FR-007**: El sistema MUST rechazar con 404 cuando el destinatario no exista o su cuenta no esté verificada, con un mensaje neutro que no revele cuál de los dos casos ocurre.
- **FR-008**: El sistema MUST validar el identificador del destinatario en el backend: ausente, no numérico o no positivo MUST producir 400 con un error de campo, sin consultar ni escribir relaciones.
- **FR-009**: El sistema MUST garantizar, incluso ante peticiones concurrentes, que exista como máximo una solicitud `PENDIENTE` por pareja de usuarios sin importar la dirección.

**Notificación y bandeja (US3)**

- **FR-010**: El sistema MUST ofrecer una pantalla protegida "Amigos", accesible desde la barra de navegación, con una sección "Solicitudes" que liste las solicitudes pendientes recibidas por la persona autenticada.
- **FR-011**: La sección "Solicitudes" MUST mostrar, por cada solicitud, únicamente nombre, apellido, correo electrónico y foto del emisor (o avatar por defecto si no tiene), más una indicación de estado pendiente; MUST NOT mostrar CBU/CVU, monedas, contraseña ni otros datos privados.
- **FR-012**: Las solicitudes recibidas MUST listarse de la más reciente a la más antigua y MUST limitarse a las que tienen a la persona autenticada como destinataria y estado `PENDIENTE`.
- **FR-013**: Cuando no haya solicitudes pendientes recibidas, la sección MUST mostrar exactamente "No tenés solicitudes de amistad pendientes".
- **FR-014**: La notificación de una nueva solicitud MUST ser exclusivamente dentro de la aplicación mediante esta bandeja; el envío de correos y las notificaciones en tiempo real quedan fuera de alcance.
- **FR-015**: En 011 la pantalla "Amigos" MUST limitarse a la sección "Solicitudes"; la lista de amigos y las acciones de aceptar o rechazar quedan fuera de alcance y MUST NOT mostrarse como acciones operativas.

**Interfaz y errores (US1, US3, US4)**

- **FR-016**: Durante el envío, el botón de la fila MUST mostrar un estado de carga y quedar deshabilitado; con éxito MUST mostrar "Solicitud enviada" deshabilitado y el mensaje exacto "Solicitud de amistad enviada".
- **FR-017**: Ante el rechazo por auto-solicitud, duplicado o amistad existente, la interfaz MUST mostrar el mensaje exacto del motivo, sin marcar la solicitud como enviada.
- **FR-018**: Ante un error de red o del servidor al enviar, la interfaz MUST mostrar exactamente "No se pudo enviar la solicitud. Intentá nuevamente" y MUST NOT marcar la fila como enviada.
- **FR-019**: Al cargar la bandeja, la interfaz MUST distinguir carga, lista, vacío y error; ante error de red o del servidor MUST mostrar exactamente "No se pudieron cargar tus solicitudes. Intentá nuevamente" con opción de reintentar y MUST NOT mostrar datos obsoletos.
- **FR-020**: Ante un 401, la interfaz MUST cerrar la sesión local y redirigir al flujo de acceso, sin mostrar datos ni confirmaciones falsas.

**Acceso y alcance**

- **FR-021**: Todas las operaciones MUST requerir una sesión válida de una cuenta verificada (regla de 003) y MUST responder 401 cuando falte la sesión o la cuenta no esté verificada.
- **FR-022**: El backend MUST exponer las operaciones bajo `/api/users/**` para heredar la regla de cuenta verificada sin duplicarla.
- **FR-023**: Los errores MUST seguir el formato de errores del proyecto (RFC 7807), en español y sin detalles internos.
- **FR-024**: La feature MUST NOT implementar aceptación ni rechazo de solicitudes, ni listado de amigos, ni envío de correos, ni notificaciones en tiempo real, ni paginación.

### Key Entities *(include if feature involves data)*

- **Solicitud de amistad** (nueva): relación dirigida entre una cuenta emisora y una cuenta destinataria, con estado (`PENDIENTE`, `ACEPTADA`, `RECHAZADA`) y fecha de creación. En 011 solo se crean y consultan solicitudes `PENDIENTE`.
- **Amistad** (conceptual): relación no dirigida que existe cuando hay una solicitud `ACEPTADA` entre dos cuentas. En 011 no se crean amistades; el estado `ACEPTADA` queda modelado para que el criterio "ya son amigos" pueda evaluarse y para que la historia de aceptación lo utilice.
- **Cuenta de usuario** (existente, solo lectura): identificador, nombre, apellido, correo y estado de verificación. El emisor sale de la sesión y el destinatario de la solicitud. No se crean ni modifican cuentas.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Una persona autenticada y verificada envía una solicitud desde el buscador en menos de 2 segundos en condiciones normales de red y recibe la confirmación exacta.
- **SC-002**: El 100 % de los envíos válidos queda persistido como `PENDIENTE` con el emisor de la sesión y el destinatario elegido.
- **SC-003**: El 100 % de los intentos de auto-solicitud, duplicado pendiente (en cualquier dirección), amistad existente y destinatario inválido es rechazado sin crear ni modificar ninguna solicitud.
- **SC-004**: El 100 % de las solicitudes pendientes recibidas aparece en la sección "Solicitudes" de la persona destinataria, y ninguna aparece en la bandeja de personas ajenas.
- **SC-005**: El 100 % de las solicitudes sin sesión o con cuenta no verificada es rechazado con 401 y no accede a datos.
- **SC-006**: Ante cualquier cantidad de envíos concurrentes de la misma pareja, existe como máximo una solicitud `PENDIENTE` (0 duplicados).
- **SC-007**: Ninguna respuesta ni pantalla expone CBU/CVU, monedas, contraseña ni datos privados distintos de nombre, apellido y correo del emisor.
- **SC-008**: La bandeja de solicitudes es alcanzable desde la barra de navegación en 1 interacción y muestra su estado inicial en menos de 2 segundos.

## Assumptions

- La sesión, la cookie `amano_auth` y la regla de cuenta verificada son las de 001, 003 y 004; esta feature no las reimplementa.
- La búsqueda de 008 solo muestra cuentas verificadas y excluye la propia; el flujo normal no puede elegir un destinatario inválido, pero el backend igualmente lo valida.
- El estado de una relación no se incluye en la respuesta de búsqueda de 008; el botón "Agregar" vuelve a aparecer tras recargar y el backend informa el duplicado. Extender el contrato de búsqueda queda fuera de alcance.
- Una solicitud rechazada es histórica y no bloquea un envío posterior; solo una solicitud pendiente o una amistad existente lo hacen.
- La aceptación y el rechazo, la lista de amigos, los grupos, el compartir gastos y la inclusión de amigos en grupos quedan explícitamente fuera de alcance; esta historia solo habilita la relación pendiente que esas historias consumirán.
- La notificación es in-app y se materializa en la bandeja "Solicitudes" de la pantalla "Amigos"; no hay correo ni push.
- Los textos de la interfaz se escriben en español con voseo, en línea con el resto de la aplicación.
- No se agregan dependencias nuevas; se reutilizan los mecanismos de persistencia, validación y errores existentes.
