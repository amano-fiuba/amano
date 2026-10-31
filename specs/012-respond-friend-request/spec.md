# Feature Specification: Aceptar y rechazar solicitudes de amistad

**Feature Branch**: `feat/respond-friend-request`

**Created**: 2026-10-30

**Status**: Draft

**Input**: User description: "Nro: 27. Título: Aceptar solicitud de amistad. Como usuario quiero aceptar solicitudes de amistad recibidas para controlar quién forma parte de mi lista de amigos. Criterios de aceptación: el sistema debe permitir visualizar las solicitudes de amistad pendientes; el sistema debe permitir aceptar una solicitud de amistad; al aceptar una solicitud, ambos usuarios deben quedar registrados como amigos; una solicitud aceptada no debe continuar apareciendo como pendiente; el sistema debe informar al usuario cuando la acción se haya realizado correctamente; luego de aceptada la solicitud, ambos usuarios deben poder visualizarse en su lista de amigos. Nro: 29. Título: Rechazar solicitud de amistad. Como usuario quiero rechazar solicitudes de amistad recibidas para controlar quién forma parte de mi lista de amigos. Criterios de aceptación: el sistema debe permitir visualizar las solicitudes de amistad pendientes; el sistema debe permitir rechazar una solicitud de amistad; al rechazar una solicitud, los usuarios no deben quedar registrados como amigos; una solicitud aceptada o rechazada no debe continuar apareciendo como pendiente; el sistema debe informar al usuario cuando la acción se haya realizado correctamente; al rechazar la solicitud, los usuarios no se ven en su lista de amigos."

## Relación con otras especificaciones

- **Continúa 011 (Enviar solicitud de amistad)**: la entidad `friendship_requests` ya existe con los estados `PENDIENTE`, `ACEPTADA` y `RECHAZADA`, la columna `responded_at` y la bandeja "Solicitudes" de la pantalla "Amigos". Esta feature añade la respuesta del destinatario y **no crea ni modifica ninguna tabla**; el estado `PENDIENTE` pasa a ser transitorio por primera vez.
- **La bandeja de 011 gana acciones**: cada fila de la sección "Solicitudes" muestra los botones "Aceptar" y "Rechazar" que 011 dejó fuera de alcance; el listado, el orden, los datos mostrados y el mensaje de estado vacío no cambian.
- **Reutiliza 001/003/004 (cuenta, verificación de correo y sesión)**: la cookie `amano_auth` identifica a la persona que responde y la regla de cuenta verificada se hereda al exponer la operación bajo `/api/users/**`.
- **La visualización de la lista de amigos queda fuera de alcance**: el criterio "ambos usuarios deben poder visualizarse en su lista de amigos" se satisface hoy con el estado persistido (una solicitud aceptada); la vista de la lista es una historia posterior acordada (spec separada) y **no** se implementa acá. Por la misma razón, "al rechazar la solicitud, los usuarios no se ven en su lista de amigos" se cumple en la relación: no queda ninguna amistad registrada.
- **El emisor no recibe notificaciones**: la historia 26 (011) aprobó la notificación in-app para la destinataria; el aviso de que su solicitud fue aceptada o rechazada, por correo o en tiempo real, queda fuera de alcance.
- **No modifica el envío ni la búsqueda**: el contrato de `POST /api/users/me/friendship-requests`, de `GET /api/users/me/friendship-requests/received` y de `GET /api/users/search` de 011/008 no cambia.

Los identificadores FR-### y SC-### de esta especificación son propios de 012 y no coinciden necesariamente en número con los de otras specs.

## Clarifications

### Session 2026-10-30

- Q: ¿Aceptar y rechazar son dos features o una? → A: Una sola spec (012) que cubre las historias 27 y 29: la respuesta del destinatario con sus dos destinos posibles.
- Q: ¿La visualización de la lista de amigos se incluye acá? → A: No. El registro de la amistad (solicitud en estado aceptado) es parte de 012; su visualización es una spec posterior separada, acordada con el equipo.
- Q: ¿Cómo se expone la respuesta? → A: Operación única sobre la solicitud: `PUT /api/users/me/friendship-requests/{id}` con la acción en el cuerpo.
- Q: ¿Se notifica al emisor el resultado? → A: No. Sin correos ni notificaciones en tiempo real; el emisor solo percibirá el cambio cuando exista una vista que exponga la relación (historia posterior).
- Q: ¿Qué pasa si la solicitud ya fue respondida? → A: Se rechaza la operación; la interfaz retira la fila de pendientes, porque ya no lo está.
- Q: ¿Un rechazo impide que se vuelva a enviar una solicitud entre ambas personas? → A: No. Un rechazo es histórico y no bloquea un envío nuevo; la regla ya existe en 011.
- Q: Numeración → A: `012-respond-friend-request`, acordado con el equipo el 2026-10-30 (`010` está en curso con otro integrante).

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Aceptar una solicitud de amistad recibida (Priority: P1)

Una persona autenticada y verificada abre "Amigos", ve en la sección "Solicitudes" una solicitud pendiente recibida y pulsa "Aceptar". Los botones de la fila se deshabilitan mientras responde; al confirmarse, la amistad queda registrada, la fila se retira de los pendientes y aparece la confirmación "Solicitud de amistad aceptada".

**Why this priority**: Es la mitad del valor central de la historia: sin la aceptación no existe la amistad que el criterio 27 exige registrar.

**Independent Test**: Con dos cuentas verificadas, enviar una solicitud de A a B, iniciar sesión como B, pulsar "Aceptar" en la fila de A y comprobar la confirmación, la desaparición de la fila y una fila `ACCEPTED` con la fecha de respuesta en la persistencia.

**Acceptance Scenarios**:

1. **Given** una persona destinataria con solicitudes pendientes en "Amigos", **When** pulsa "Aceptar" en una fila, **Then** los botones se deshabilitan con "Aceptando…", la fila se retira de la lista y aparece "Solicitud de amistad aceptada".
2. **Given** la aceptación exitosa, **When** se consulta la persistencia, **Then** la solicitud tiene estado `ACCEPTED`, `responded_at` no nulo y conserva su emisor y destinataria originales.
3. **Given** una solicitud aceptada, **When** el estado es revisado como amistad, **Then** ambos usuarios aparecen registrados como amigos (relación por solicitud aceptada), aunque la vista de la lista pertenezca a otra historia.
4. **Given** una persona que respondió varias solicitudes, **When** vuelve a cargar "Amigos", **Then** ninguna de las respondidas aparece en su lista de solicitudes pendientes.

---

### User Story 2 - Rechazar una solicitud de amistad recibida (Priority: P1)

Con las condiciones de la US1, la persona destinataria pulsa "Rechazar" en vez de "Aceptar". La solicitud queda registrada como rechazada, no se crea amistad alguna, la fila se retira de los pendientes y aparece la confirmación "Solicitud de amistad rechazada".

**Why this priority**: Es la otra mitad del valor central y parte explícita de la historia 29: controlar quién forma parte de la lista incluye poder decir que no.

**Independent Test**: Con dos cuentas verificadas, enviar una solicitud de A a B, iniciar como B, pulsar "Rechazar" y comprobar la confirmación, la desaparición de la fila, una fila `REJECTED` con fecha de respuesta y la ausencia de cualquier relación de amistad.

**Acceptance Scenarios**:

1. **Given** una persona destinataria con solicitudes pendientes en "Amigos", **When** pulsa "Rechazar" en una fila, **Then** los botones se deshabilitan con "Rechazando…", la fila se retira de la lista y aparece "Solicitud de amistad rechazada".
2. **Given** el rechazo exitoso, **When** se consulta la persistencia, **Then** la solicitud tiene estado `REJECTED`, `responded_at` no nulo y no existe ninguna fila `ACCEPTED` entre ambas cuentas.
3. **Given** una solicitud rechazada, **When** se evalúa la amistad entre ambas cuentas, **Then** no existe ninguna amistad registrada y, en consecuencia, nadie aparece en una lista de amigos por esa relación.
4. **Given** una solicitud rechazada, **When** el anterior emisor busca a la destinataria e intenta enviar una nueva solicitud, **Then** el envío es aceptado porque un rechazo es histórico y no bloquea nada (regla de 011, aquí sin cambios).

---

### User Story 3 - Integridad de la respuesta: acceso, concurrencia y errores (Priority: P2)

Solo la destinataria de la solicitud puede responderla, y solo mientras está pendiente. Si dos respuestas simultáneas llegan a la misma solicitud, una sola transición tiene éxito y la otra informa el conflicto sin alterar nada. Los intentos inválidos —solicitud inexistente, ajena, ya respondida o acción inválida— se rechazan con mensajes claros, y los fallos de red y de sesión no muestran éxitos falsos.

**Why this priority**: Protege la privacidad y la integridad del ciclo de vida; el flujo feliz de las US1/US2 ya entrega el valor principal.

**Independent Test**: Responder por API con la cuenta equivocada, con una solicitud inexistente, ya respondida y con una acción inválida; disparar dos respuestas simultáneas sobre la misma solicitud; simular caída de red y sesión expirada en la interfaz.

**Acceptance Scenarios**:

1. **Given** una petición de respuesta cuya persona autenticada NO es la destinataria de la solicitud, **When** se procesa, **Then** se responde 404 con "No se encontró la solicitud de amistad" sin revelar si la solicitud existe ni su estado.
2. **Given** una petición sobre un identificador de solicitud inexistente, **When** se procesa, **Then** se responde 404 con el mismo mensaje neutro y sin cambios de datos.
3. **Given** una solicitud ya aceptada o ya rechazada, **When** se intenta responder nuevamente, **Then** se responde 409 con "Esta solicitud de amistad ya fue respondida" y su estado permanece sin cambios.
4. **Given** una acción distinta de aceptar o rechazar (por ejemplo, un valor desconocido o vacío), **When** se procesa, **Then** se responde 400 con un error de campo sobre `action` y la solicitud no se modifica.
5. **Given** dos respuestas simultáneas (misma o distinta acción) sobre una misma solicitud pendiente, **When** ambas llegan al servidor, **Then** exactamente una tiene éxito y deja su estado final; la otra responde 409 y no produce una segunda transición.
6. **Given** una petición de respuesta sin sesión válida o con cuenta no verificada, **When** se procesa, **Then** se responde 401 y no se devuelve ni modifica dato alguno.
7. **Given** un error de red o del servidor durante la respuesta, **When** finaliza el intento, **Then** se muestra "No se pudo responder la solicitud. Intentá nuevamente", la fila conserva sus acciones y no se da por realizada la operación.
8. **Given** una sesión expirada durante la respuesta, **When** el servidor responde 401, **Then** se cierra la sesión local y se redirige al flujo de acceso, sin dejar datos ni confirmaciones falsas.

---

### Edge Cases

- **Doble clic en "Aceptar" o "Rechazar"**: los botones se deshabilitan mientras la respuesta está en curso; si aun así llegan dos peticiones, la actualización condicional garantiza una única transición y el segundo intento recibe 409.
- **Dos acciones distintas al mismo tiempo** (un "Aceptar" y un "Rechazar" casi simultáneos, por ejemplo desde dos pestañas): exactamente una transición gana; la otra 409. Nunca quedan filas con ambos estados ni `responded_at` inconsistente.
- **Responder la propia solicitud enviada** (quien envió intenta aceptar o rechazar la suya): 404 neutro; solo la destinataria del registro puede responder.
- **Solicitud del emisor eliminado**: la fila se borró por la cascada de 011; responde 404 neutro y tampoco figurará más en la bandeja.
- **Solicitud ya aceptada**: 409 "Esta solicitud de amistad ya fue respondida"; la amistad no se duplica y el índice único de aceptadas de 011 sigue sosteniendo el invariante.
- **Solicitud ya rechazada**: mismo 409; una respuesta posterior sobre un rechazo no cambia nada.
- **Acción por API manipulada** (valor distinto de aceptar/rechazar, mayúsculas no normalizadas u otro texto): 400 con error de campo; sin contacto con la persistencia.
- **Identificador no numérico en la ruta**: 400 sin acceso a datos.
- **Fila respondida por otra vía durante la sesión actual**: la interfaz retira la fila con el mensaje del 409 y refresca su estado local sin recargar la pantalla.
- **Última solicitud respondida de la bandeja**: la confirmación de éxito permanece visible; el mensaje "No tenés solicitudes de amistad pendientes" corresponde únicamente a una carga inicial sin pendientes.
- **Sesión expirada durante la respuesta**: 401, cierre de sesión local y redirección al acceso.
- **Error de red durante la respuesta**: la fila permanece con sus botones reactivos; no se conservan estados de carga colgados ni marcas optimistas.

## Requirements *(mandatory)*

### Functional Requirements

**Aceptación (US1)**

- **FR-001**: El sistema MUST ofrecer en cada fila de la sección "Solicitudes" la acción "Aceptar", disponible solo para la persona destinataria autenticada y verificada.
- **FR-002**: Al aceptar, la solicitud MUST pasar de `PENDIENTE` a `ACEPTADA` y MUST registrar la fecha de respuesta; ambos usuarios quedan registrados como amigos (una solicitud aceptada existe como la representación de la amistad, sin tabla propia).
- **FR-003**: Una solicitud aceptada MUST dejar de aparecer en la lista de solicitudes pendientes recibidas de la persona destinataria.

**Rechazo (US2)**

- **FR-004**: El sistema MUST ofrecer en cada fila la acción "Rechazar", en igualdad de condiciones de acceso que "Aceptar".
- **FR-005**: Al rechazar, la solicitud MUST pasar de `PENDIENTE` a `RECHAZADA` y MUST registrar la fecha de respuesta; los usuarios MUST NOT quedar registrados como amigos.
- **FR-006**: Una solicitud rechazada MUST dejar de aparecer en la lista de solicitudes pendientes recibidas y MUST NOT impedir un envío futuro entre ambas personas (regla existente de 011).

**Reglas de la respuesta (US3)**

- **FR-007**: El respondedor MUST identificarse exclusivamente por su sesión; el identificador de la solicitud sale de la ruta y la acción del cuerpo, nunca del emisor ni de la destinataria. Solo la destinataria del registro puede responder; en otro caso el sistema MUST responder 404 con un mensaje neutro.
- **FR-008**: El sistema MUST rechazar con 409 la respuesta sobre una solicitud que ya fue aceptada o rechazada, con un mensaje único sin revelar el resultado anterior, y MUST NOT cambiar su estado ni su fecha.
- **FR-009**: El sistema MUST rechazar con 404 las referencias a solicitudes inexistentes, con un mensaje neutro que no revele la existencia ni el estado de solicitudes ajenas.
- **FR-010**: El sistema MUST validar la acción indicada en el cuerpo: solo los valores de aceptación y rechazo son válidos; cualquier otro valor, vacío o ausente MUST producir 400 con un error de campo sobre `action`, sin consultar ni escribir la relación. El identificador de la ruta no numérico o no positivo MUST producir 400 sin acceso a datos.
- **FR-011**: El sistema MUST garantizar, incluso ante respuestas concurrentes, que una solicitud pase de `PENDIENTE` a exactamente un estado final y una única fecha de respuesta: la primera transición que se materializa registra el estado final y la fecha, y las restantes reciben 409.

**Interfaz y confirmación (US1, US2, US3)**

- **FR-012**: Durante una respuesta, los botones de la fila MUST deshabilitarse y mostrar la acción en curso ("Aceptando…" o "Rechazando…"), sin alterar las demás filas.
- **FR-013**: Con éxito, la fila MUST retirarse de la lista de pendientes y la interfaz MUST mostrar exactamente "Solicitud de amistad aceptada" o "Solicitud de amistad rechazada".
- **FR-014**: La confirmación de éxito MUST permanecer visible hasta que la persona navegue o accione otra solicitud, incluso si la lista queda sin filas.
- **FR-015**: Ante 409, la fila ya no pendiente MUST retirarse de la lista y la interfaz MUST mostrar el motivo informado por el servidor; ante 404 MUST mostrar "No se encontró la solicitud de amistad" y retirar la fila.
- **FR-016**: Ante un error de red o del servidor, la interfaz MUST mostrar exactamente "No se pudo responder la solicitud. Intentá nuevamente", la fila MUST permanecer con sus acciones reactivas y MUST NOT mostrar ninguna confirmación de éxito.
- **FR-017**: Ante un 401, la interfaz MUST cerrar la sesión local y redirigir al flujo de acceso, sin dejar datos ni confirmaciones falsas.

**Acceso y alcance**

- **FR-018**: La operación de respuesta MUST requerir una sesión válida de una cuenta verificada (regla de 003) y MUST responder 401 cuando falte la sesión o la cuenta no esté verificada.
- **FR-019**: La operación MUST exponerse bajo `/api/users/**` para heredar la regla de cuenta verificada sin duplicarla, siguiendo la estructura de rutas de 011.
- **FR-020**: Los errores MUST seguir el formato de errores del proyecto (RFC 7807), en español y sin detalles internos.
- **FR-021**: La feature MUST NOT implementar listado de amigos, notificaciones al emisor, correos, push, paginación, ni modificar el envío de solicitudes, la búsqueda de usuarios o los contratos existentes de 011/008.

### Key Entities *(include if feature involves data)*

- **Solicitud de amistad** (existente, de 011): relación dirigida entre una cuenta emisora y una destinataria con estado (`PENDIENTE`, `ACEPTADA`, `RECHAZADA`), fecha de creación y fecha de respuesta. En 011 solo se creaban y consultaban solicitudes `PENDIENTE`; esta feature introduce la transición desde `PENDING` hacia `ACCEPTED` o `REJECTED`. **No se agregan columnas ni tablas.**
- **Amistad** (existente, conceptual): relación no dirigida que existe cuando hay una solicitud `ACEPTADA` entre dos cuentas. Se crea por primera vez por esta historia, mediante la transición. La vista de la lista queda para otra spec.
- **Cuenta de usuario** (existente, solo lectura): la sesión resuelve a la persona que responde; el emisor y su resumen ya están persistidos en la solicitud. No se crean ni modifican cuentas.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Una persona destinataria acepta o rechaza una solicitud en menos de 2 segundos en condiciones normales de red y recibe la confirmación exacta.
- **SC-002**: El 100 % de las respuestas válidas queda persistido con el estado final correcto (`ACEPTADA` o `RECHAZADA`), la fecha de respuesta registrada y una única transición por solicitud.
- **SC-003**: El 100 % de los intentos inválidos (acción inválida, identificador inválido, solicitud inexistente, ajena o ya respondida) es rechazado sin crear ni modificar ninguna fila.
- **SC-004**: El 100 % de las solicitudes respondidas desaparece de la bandeja "Solicitudes" de la destinataria (aceptadas y rechazadas por igual), y ninguna queda con ambos estados o fecha inconsistente.
- **SC-005**: Ante cualquier cantidad de respuestas concurrentes sobre la misma solicitud, exactamente una transición tiene éxito y las restantes reciben 409 (0 estados mixtos, 0 fechas dobles).
- **SC-006**: El 100 % de las respuestas sin sesión o con cuenta no verificada es rechazado con 401 y no accede a datos.
- **SC-007**: Ninguna respuesta confirma éxito si hubo error de red o del servidor, y ninguna respuesta expone datos privados distintos de nombre, apellido y correo del emisor.

## Assumptions

- El dominio de amistad de 011 está completo para esta historia: estados, `responded_at`, la restricción de coherencia estado/fecha y los índices únicos de pareja ya existen; **no hay migración nueva**.
- Solo la persona destinataria de la solicitud puede responderla; el emisor no tiene operaciones sobre su solicitud en 012.
- La amistad registrada no se visualiza acá: la lista de amigos es una historia posterior acordada; el emisor tampoco recibe aviso del resultado.
- La búsqueda de 008 sigue sin exponer el estado de la relación; un emisor descubre el resultado del envío cuando exista una vista futura, no por esta historia.
- Un rechazo es histórico: no bloquea un envío posterior entre ambas personas.
- La pantalla "Amigos" de 011 conserva su listado, orden, datos mínimos y estado vacío; esta feature solo agrega las acciones y sus estados de carga/resultado.
- Los textos de la interfaz se escriben en español con voseo, en línea con el resto de la aplicación.
- No se agregan dependencias nuevas; se reutilizan los mecanismos de persistencia, validación, errores y sesión existentes.
