# Feature Specification: Verificación de correo electrónico

**Feature Branch**: `feat/email-verification`

**Created**: 2026-10-19

**Status**: Draft

**Input**: User description: "Verificación de correo electrónico: el registro deja de iniciar sesión y la cuenta queda 'no verificada' (bloqueada) hasta confirmar la casilla de correo mediante un enlace con token de un solo uso y validez de 24 horas, con reenvío limitado a uno cada 5 minutos por dirección y eliminación automática de cuentas no verificadas a los 3 días."

## Relación con la especificación 001 (Registro de usuario)

Esta especificación MODIFICA el comportamiento de la especificación 001 (Registro de usuario):

- **Reemplaza FR-016 de 001** (inicio de sesión automático tras el registro): el registro ya no inicia sesión ni lleva al dashboard; la cuenta queda "no verificada" y el usuario es redirigido a la pantalla "Revisa tu correo".
- **Reemplaza SC-002 de 001** (acceso inmediato al perfil tras registrarse): el acceso inmediato ahora ocurre únicamente tras confirmar el correo.
- **Modifica el alcance de FR-005 de 001** ("correo ya registrado → error"): la regla de correo duplicado aplica solo a cuentas **verificadas**; para direcciones con cuenta **no verificada** rige el flujo de esta especificación (reemplazo de la cuenta pendiente, ver Casos borde).

Los identificadores FR-### y SC-### de esta especificación son propios de 003 y no coinciden necesariamente en número con los de 001; toda referencia cruzada se cualifica con la especificación de origen (por ejemplo, "FR-016 de 001").

Todo lo demás de 001 (validaciones del formulario, campos, políticas de contraseña, avatares, CBU/CVU, monedas) se mantiene sin cambios.

## Clarifications

### Session 2026-10-19

- Q: Si un usuario hace clic en el enlace de verificación desde un navegador o dispositivo distinto del que usó para registrarse, ¿el sistema debe iniciar sesión automáticamente en ese dispositivo igualmente? (FR-005) → A: Sí, el enlace es portador de la sesión: donde sea que se abra, verifica la cuenta, inicia sesión en ese dispositivo y lleva al dashboard.
- Q: Si un usuario vuelve a registrar un correo que ya tiene una cuenta no verificada mientras el límite de 5 minutos por reenvío está vigente, ¿el sistema envía el correo de verificación de todos modos o respeta el límite? (FR-015) → A: Respeta el límite: se reemplazan los datos y el token, pero el correo no se envía mientras el límite siga vigente; la pantalla "Revisa tu correo" muestra la cuenta regresiva con el tiempo restante.
- Q: Si alguien pidió un reenvío para una dirección que aún no tiene cuenta y, antes de que venzan los 5 minutos, el usuario se registra con esa dirección, ¿el correo del primer registro se envía igualmente o queda bloqueado por el límite vigente? (FR-011) → A: El correo del primer registro se envía siempre de inmediato, sin verse afectado por un límite de reenvío previo; la cuenta regresiva solo aplica a los reenvíos.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Registro con cuenta no verificada y pantalla "Revisa tu correo" (Priority: P1)

Un usuario nuevo completa el formulario de registro con las validaciones de 001 y lo envía. El sistema crea la cuenta en estado "no verificada", **no** inicia sesión y **no** lo lleva al dashboard: lo redirige a la pantalla "Revisa tu correo", que indica que se envió un enlace de verificación a la dirección ingresada y ofrece un botón de reenvío. La dirección de correo ingresada se conserva en la pantalla aunque el usuario recargue la página. En paralelo, el sistema envía de forma asíncrona un correo con un enlace que contiene un token de un solo uso y validez de 24 horas.

**Why this priority**: Es el cambio fundante de la funcionalidad y su valor de negocio principal: sin cuenta en estado "no verificada" y sin el canal de verificación por correo, no existe ninguna de las demás historias (confirmación, reenvío, bloqueo). También es la que modifica el comportamiento de 001.

**Independent Test**: Puede probarse completamente enviando el formulario de registro con datos válidos y observando: creación de la cuenta en estado "no verificada", ausencia de sesión iniciada, redirección a "Revisa tu correo" con el correo visible, y recepción del correo de verificación con el enlace.

**Acceptance Scenarios**:

1. **Given** un usuario nuevo envía el formulario de registro con datos válidos según 001, **When** el sistema procesa el registro, **Then** la cuenta se crea como "no verificada", no se inicia sesión, el usuario es redirigido a la pantalla "Revisa tu correo" y el correo de verificación se emite en menos de 5 segundos.
2. **Given** el usuario está en la pantalla "Revisa tu correo", **When** recarga la página, **Then** la pantalla conserva y muestra la dirección de correo ingresada en el registro y el botón de reenvío.
3. **Given** el servidor de correo falla durante el envío, **When** el registro se procesa, **Then** la cuenta se crea igual y el usuario siempre ve la pantalla "Revisa tu correo", desde la cual puede reintentar con el botón de reenvío.
4. **Given** un usuario envía el formulario con validaciones de 001 incumplidas (formato de correo, política de contraseña, sin aceptar términos, etc.), **When** el sistema valida la solicitud, **Then** se comporta según 001 (resalta campos, no crea ninguna cuenta) y no se envía ningún correo de verificación.
5. **Given** el usuario abre la aplicación en otro dispositivo o cierra la sesión del navegador, **When** intenta volver a la pantalla "Revisa tu correo", **Then** la pantalla sigue siendo accesible con el correo y el botón de reenvío correspondientes.

---

### User Story 2 - Confirmación del enlace de verificación (Priority: P2)

El usuario abre el correo y hace clic en el enlace de verificación. Con un token válido, el sistema marca la cuenta como "verificada", inicia sesión automáticamente y lo lleva al dashboard con un mensaje de bienvenida. Si el enlace está vencido (más de 24 horas), ya fue utilizado o es inválido/alterado, el sistema muestra mensajes específicos que orientan al usuario sobre qué hacer a continuación.

**Why this priority**: Es la culminación del flujo principal: sin confirmación el registro no produce ningún usuario habilitado. El valor de negocio (cuentas con correo confiable) se concreta únicamente en esta historia.

**Independent Test**: Puede probarse abriendo el enlace recibido en el correo con un token vigente y verificando la transición a estado "verificada", el inicio de sesión y la llegada al dashboard; y repitiendo con enlaces vencidos, reutilizados y alterados para verificar los mensajes de error.

**Acceptance Scenarios**:

1. **Given** una cuenta no verificada con token vigente (emitido hace menos de 24 horas y sin usar), **When** el usuario abre el enlace de verificación, **Then** el sistema marca la cuenta como "verificada", inicia sesión automáticamente y lleva al usuario al dashboard con un mensaje de bienvenida en menos de 2 segundos.
2. **Given** una cuenta no verificada registrada desde un dispositivo, **When** el usuario abre el enlace de verificación desde otro navegador o dispositivo (por ejemplo, desde el cliente de correo del teléfono), **Then** el sistema verifica la cuenta e inicia sesión en el dispositivo donde el enlace fue abierto, llevándolo al dashboard con el mensaje de bienvenida.
3. **Given** un enlace con token emitido hace más de 24 horas, **When** el usuario abre el enlace, **Then** el sistema muestra una pantalla que explica que el enlace expiró y lo lleva a la pantalla de reenvío, donde puede ingresar su correo electrónico.
4. **Given** un enlace cuyo token ya fue utilizado para verificar la cuenta, **When** el usuario vuelve a abrir el enlace, **Then** el sistema muestra "Este enlace de verificación ya ha sido utilizado. Por favor, inicia sesión".
5. **Given** un enlace con token inválido o alterado, **When** el usuario lo abre, **Then** el sistema muestra "El enlace de verificación no es válido".
6. **Given** una cuenta no verificada que ya tiene un token emitido, **When** se emite un token nuevo (por reenvío o por nuevo registro), **Then** el token anterior queda invalidado y el enlace anterior deja de verificar la cuenta.

---

### User Story 3 - Bloqueo de acceso para cuentas no verificadas (Priority: P3)

Una cuenta que no confirmó su correo no puede acceder a ningún recurso protegido de la plataforma (dashboard, perfil, avatar, grupos, etc.). El inicio de sesión real es una funcionalidad de otro integrante del equipo y está fuera de esta especificación; aquí se define la regla que debe respetar (una cuenta no verificada no puede iniciar sesión y recibe el mensaje "Tu cuenta aún no ha sido verificada. Por favor, revisa tu correo electrónico", más un indicador legible por máquina para que el inicio de sesión real ofrezca el enlace de reenvío; la interfaz de login real queda fuera de alcance) y se valida con un inicio de sesión simulado, provisorio, a reemplazar por el real.

**Why this priority**: Es la garantía de seguridad de toda la funcionalidad: sin el bloqueo, la verificación sería cosmética. Se prioriza después de la historia 2 porque su valor se demuestra con cuentas creadas y aún no verificadas, que la historia 1 produce.

**Independent Test**: Puede probarse intentando iniciar sesión (simulado) y accediendo a recursos protegidos con una cuenta recién registrada y no verificada, verificando el rechazo con el mensaje definido; y repitiendo el mismo intento tras verificar la cuenta para confirmar el acceso.

**Acceptance Scenarios**:

1. **Given** una cuenta creada y no verificada, **When** se intenta iniciar sesión con ella (mediante el inicio de sesión simulado), **Then** el intento es rechazado con el mensaje "Tu cuenta aún no ha sido verificada. Por favor, revisa tu correo electrónico" y la respuesta incluye un indicador legible por máquina para que la feature de login real ofrezca el enlace de reenvío (la interfaz de login queda fuera de alcance).
2. **Given** una sesión obtenida antes de verificar (por ejemplo, simulada directamente), **When** se intenta acceder a cualquier recurso protegido (dashboard, perfil, avatar, grupos) con una cuenta no verificada, **Then** el acceso es denegado.
3. **Given** una cuenta verificada, **When** se intenta iniciar sesión con ella, **Then** el acceso no es rechazado por el motivo de verificación (el resto de reglas del inicio de sesión real quedan fuera de esta especificación).

---

### User Story 4 - Reenvío del correo con límite de frecuencia y respuesta neutra (Priority: P4)

Desde la pantalla "Revisa tu correo" el usuario puede pedir un nuevo correo de verificación. El botón está siempre habilitado; al hacer clic, el sistema responde siempre con el mismo mensaje neutro ("Si el correo existe y no ha sido verificado, se enviará un nuevo enlace de confirmación"), envía el correo si corresponde, invalida el token anterior y emite uno nuevo, y muestra una cuenta regresiva de 5 minutos visible en pantalla, calculada con el tiempo restante que devuelve el servidor. Como máximo se permite un reenvío cada 5 minutos por dirección de correo, exista o no una cuenta asociada, esté o no verificada: la respuesta y el temporizador son idénticos en todos los casos, preservando la privacidad.

**Why this priority**: Es el mecanismo de recuperación del flujo principal para quien no recibe el correo. Su valor depende de la pantalla de la historia 1 y del token de la historia 2, pero no de la historia 3.

**Independent Test**: Puede probarse haciendo clic en "Reenviar correo de verificación", verificando el mensaje neutro, la recepción del correo (para una cuenta pendiente), la invalidación del token anterior y la cuenta regresiva de 5 minutos; y repitiendo con direcciones inexistentes o ya verificadas para confirmar respuestas idénticas.

**Acceptance Scenarios**:

1. **Given** una cuenta no verificada, **When** el usuario hace clic en "Reenviar correo de verificación", **Then** el sistema responde con el mensaje neutro, emite un correo nuevo en menos de 5 segundos con un token que invalida al anterior y muestra una cuenta regresiva de 5 minutos, con el botón aún habilitado.
2. **Given** el límite de reenvío está vigente, **When** el usuario recarga la página y vuelve a hacer clic en "Reenviar", **Then** el sistema no envía nada, responde con el mismo mensaje neutro y la pantalla muestra la cuenta regresiva con el tiempo restante real devuelto por el servidor, con el botón habilitado.
3. **Given** el usuario hace clic en "Reenviar" para una dirección que no tiene cuenta asociada, **When** el sistema procesa la solicitud, **Then** no envía ningún correo y responde con el mismo mensaje neutro y el mismo comportamiento de temporizador que para una cuenta pendiente.
4. **Given** una dirección con cuenta ya verificada, **When** el usuario hace clic en "Reenviar", **Then** no se envía ningún correo y la respuesta y el temporizador son idénticos a los de una cuenta pendiente.
5. **Given** un límite vigente por haber hecho clic hace menos de 5 minutos, **When** vence el plazo, **Then** la cuenta regresiva llega a cero y un nuevo clic procesa un reenvío real.
6. **Given** una cuenta no verificada creada hace instantes (correo del primer registro), **When** el usuario hace su primer clic en "Reenviar", **Then** el reenvío se procesa (el correo del registro inicial no consume el límite).

---

### Edge Cases

- **Registro con un correo ya registrado y no verificada**: el sistema no crea otra cuenta; reemplaza los datos de la cuenta pendiente por los del nuevo formulario (incluida la contraseña), invalida el token anterior y emite uno nuevo. El envío del correo respeta el límite de reenvío: si el límite está vigente (por ejemplo, el tercero que registró previamente el correo acaba de pedir un reenvío), el correo no se envía hasta que venza el plazo y la pantalla muestra la cuenta regresiva; si no hay límite vigente, el correo se envía de inmediato. El usuario ve la pantalla "Revisa tu correo", igual que en un registro normal. Esto evita que un tercero que registró previamente ese correo con otra contraseña conserve el control de la cuenta.
- **Registro con un correo ya registrado y verificada**: se mantiene el comportamiento de 001 ("El correo electrónico ya se encuentra registrado", sin crear cuenta).
- **Enlace con token vencido (más de 24 h)**: pantalla que explica que el enlace expiró y lleva a la pantalla de reenvío, donde el usuario ingresa su correo. La pantalla puede, opcionalmente, sugerir registrarse de nuevo si no llega ningún correo.
- **Enlace con token vencido cuya cuenta ya fue eliminada (más de 3 días)**: el reenvío no envía nada y la respuesta neutra sigue siendo la misma.
- **Enlace con token ya utilizado**: "Este enlace de verificación ya ha sido utilizado. Por favor, inicia sesión".
- **Enlace con token inválido o alterado**: "El enlace de verificación no es válido".
- **Reenvío para cuenta inexistente o ya verificada**: no se envía ningún correo; la respuesta y el temporizador son idénticos a los de una cuenta pendiente.
- **Registro de una dirección con límite de reenvío vigente (sin cuenta asociada)**: el registro se procesa normalmente y el correo del primer registro se envía de inmediato, sin verse afectado por el límite; la cuenta regresiva solo aplica a los reenvíos posteriores.
- **Falla del servidor de correo**: la cuenta se crea igual. Como el envío es asíncrono, el usuario siempre ve la pantalla "Revisa tu correo" y puede reenviar si no le llega.
- **Intento de acceso con cuenta no verificada**: rechazado según la regla del inicio de sesión simulado (US3).
- **Doble clic o envíos concurrentes en el botón de reenvío**: solo se procesa la primera solicitud; el comportamiento visible es el mismo (mensaje neutro y cuenta regresiva).
- **Recarga de la pantalla de reenvío durante un límite vigente**: la cuenta regresiva muestra el tiempo restante correcto (calculado con independencia de la sesión o el dispositivo del usuario).
- **La cuenta no verificada alcanza los 3 días desde la emisión de su último token sin verificarse**: la cuenta se elimina automáticamente, el correo queda liberado y el enlace anterior queda inválido; el usuario deberá registrarse de nuevo completando todos los datos.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: El sistema MUST crear la cuenta en estado "no verificada" cuando el formulario de registro (con las validaciones de 001) es válido, MUST iniciar sesión únicamente después de la confirmación del correo y MUST redirigir al usuario a la pantalla "Revisa tu correo" en lugar del dashboard. (Reemplaza FR-016 de 001.)
- **FR-002**: La pantalla "Revisa tu correo" MUST informar que se envió un enlace de verificación a la dirección ingresada, MUST ofrecer un botón "Reenviar correo de verificación" y MUST conservar y mostrar la dirección de correo ingresada aunque el usuario recargue la página.
- **FR-003**: El sistema MUST enviar el correo de verificación de forma asíncrona, sin hacer esperar al usuario; el enlace MUST contener un token de un solo uso con validez de 24 horas desde su emisión.
- **FR-004**: Una cuenta no verificada NO MUST poder acceder a ningún recurso protegido (dashboard, perfil, avatar, grupos, etc.). La exigencia MUST aplicarse en el servidor sobre todo endpoint protegido, presente o futuro, de modo que cualquier ruta nueva bajo un ámbito autenticado herede la regla sin necesidad de enumerarla.
- **FR-005**: Al abrir un enlace con token válido (no vencido, no utilizado y vigente), el sistema MUST marcar la cuenta como "verificada", MUST iniciar sesión automáticamente en el dispositivo o navegador donde el enlace fue abierto (el enlace es portador de la sesión, incluso si el registro se hizo en otro dispositivo) y MUST llevar al usuario al dashboard con un mensaje de bienvenida.
- **FR-006**: Ante un token vencido (más de 24 horas), el sistema MUST mostrar una pantalla que explica que el enlace expiró y MUST llevar al usuario a la pantalla de reenvío, donde puede ingresar su correo electrónico.
- **FR-007**: Ante un token ya utilizado, el sistema MUST mostrar "Este enlace de verificación ya ha sido utilizado. Por favor, inicia sesión".
- **FR-008**: Ante un token inválido o alterado, el sistema MUST mostrar "El enlace de verificación no es válido".
- **FR-009**: Cada emisión de token nuevo (por reenvío o por registro sobre cuenta pendiente) MUST invalidar el token anterior de esa cuenta.
- **FR-010**: El sistema MUST permitir solicitar un reenvío desde la pantalla "Revisa tu correo" o desde la pantalla de reenvío accesible con un enlace vencido; al cargar la pantalla, incluida una recarga, el botón MUST estar siempre habilitado, y tras una solicitud de reenvío la pantalla MUST mostrar una cuenta regresiva de 5 minutos visible, calculada con el tiempo restante devuelto por el servidor (fuente de verdad).
- **FR-011**: El sistema MUST aplicar un límite de máximo un reenvío cada 5 minutos por dirección de correo, exista o no una cuenta asociada; el plazo MUST empezar a contar cuando el usuario hace clic en "reenviar". El correo enviado en el primer registro NO MUST contar para este límite y NO MUST verse afectado por un límite de reenvío vigente: el correo del primer registro MUST enviarse siempre de inmediato. El límite MUST aplicar únicamente a los reenvíos solicitados.
- **FR-012**: El sistema MUST responder a toda solicitud de reenvío con el mensaje neutro "Si el correo existe y no ha sido verificado, se enviará un nuevo enlace de confirmación", más el tiempo de espera restante cuando haya un límite vigente; la respuesta MUST ser idéntica exista o no la cuenta, esté o no verificada.
- **FR-013**: Cuando el límite de reenvío sigue vigente (por ejemplo, al recargar la página o al hacer clic durante el plazo), el sistema NO MUST enviar ningún correo; el botón permanece habilitado y la pantalla MUST mostrar la cuenta regresiva con el tiempo restante real devuelto por el servidor (fuente de verdad).
- **FR-014**: El sistema MUST rechazar el inicio de sesión de una cuenta no verificada con el mensaje "Tu cuenta aún no ha sido verificada. Por favor, revisa tu correo electrónico" y MUST incluir en la respuesta un indicador legible por máquina para que la feature real de inicio de sesión ofrezca el enlace de reenvío; la interfaz de login queda fuera de alcance de esta especificación. Para validar esta regla se utiliza un inicio de sesión simulado, provisorio, a reemplazar por el real.
- **FR-015**: Ante un registro con un correo ya registrado y **no verificada**, el sistema NO MUST crear otra cuenta: MUST reemplazar los datos de la cuenta pendiente por los del nuevo formulario (incluida la contraseña), MUST invalidar el token anterior y MUST emitir uno nuevo. El envío del correo MUST respetar el límite de reenvío: si el límite está vigente, el correo NO MUST enviarse hasta que venza el plazo y la pantalla "Revisa tu correo" MUST mostrar la cuenta regresiva con el tiempo restante; si no hay límite vigente, el correo MUST enviarse de inmediato.
- **FR-016**: Ante un registro con un correo ya registrado y **verificada**, el sistema MUST mantener el comportamiento de 001 (mensaje "El correo electrónico ya se encuentra registrado", sin crear cuenta).
- **FR-017**: Si pasan 3 días desde la emisión del último token sin que la cuenta sea verificada, el sistema MUST eliminar la cuenta automáticamente y MUST liberar la dirección de correo para un nuevo registro completo.
- **FR-018**: Tras la eliminación automática de una cuenta, un reenvío para esa dirección NO MUST enviar ningún correo y MUST responder con el mismo mensaje neutro; los enlaces de verificación anteriores a la eliminación MUST responder como token inválido.
- **FR-019**: Ante una falla del servidor de correo, el sistema MUST crear la cuenta igual y MUST mostrar la pantalla "Revisa tu correo", permitiendo reintentar mediante el reenvío; no MUST exponer detalles internos del fallo.
- **FR-020**: Todas las validaciones del formulario de registro definidas en 001 MUST mantenerse sin cambios; esta especificación solo cambia el resultado del registro válido.
- **FR-021**: Ningún token de verificación MUST poder reutilizarse ni usarse pasadas las 24 horas desde su emisión. Esta regla no introduce comportamiento nuevo: es una referencia explícita a FR-003 (y respalda SC-003).

### Key Entities *(include if feature involves data)*

- **Cuenta de Usuario** (existente, con estado nuevo): además de los datos de 001, tiene un estado de verificación del correo ("verificada" o "no verificada") y la referencia al último token de verificación emitido. Solo las cuentas "verificadas" pueden autenticarse y acceder a recursos protegidos.
- **Token de Verificación**: credencial de un solo uso, ligada a una cuenta y a su dirección de correo, con fecha de emisión y validez de 24 horas; cada emisión nueva invalida a la anterior. Su uso exitoso cambia el estado de la cuenta a "verificada".
- **Control de Frecuencia de Reenvío**: registro por dirección de correo (exista o no la cuenta asociada) con el momento del último clic en "reenviar", que sostiene el límite de un reenvío cada 5 minutos y permite calcular el tiempo restante tras recargas o desde otros dispositivos.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: El correo de verificación se emite en menos de 5 segundos tras el registro o el reenvío, en el 100 % de los casos en que corresponde enviarlo.
- **SC-002**: Ninguna cuenta no verificada accede a recursos protegidos: el 100 % de los intentos de inicio de sesión o de acceso con cuentas no verificadas es rechazado.
- **SC-003**: Ningún token puede reutilizarse ni usarse pasadas 24 horas desde su emisión: el 100 % de los intentos con tokens usados, vencidos o alterados es rechazado con el mensaje correspondiente.
- **SC-004**: No se envía más de un correo de reenvío cada 5 minutos por dirección de correo, incluso ante clics repetidos, recargas de página o uso de distintos dispositivos.
- **SC-005**: La respuesta a una solicitud de reenvío es idéntica en todos los casos (cuenta inexistente, verificada, no verificada o con límite vigente), salvo el tiempo restante cuando hay un límite vigente.
- **SC-006**: Tras recargar la pantalla de reenvío durante un límite vigente, el temporizador muestra el tiempo restante correcto en el 100 % de las recargas.
- **SC-007**: Una cuenta no verificada se elimina a los 3 días de emitido su último token y su dirección de correo queda disponible para un nuevo registro, que exige completar todos los datos.
- **SC-008**: La confirmación de un token válido y la solicitud de reenvío responden en menos de 2 segundos.

## Assumptions

- El formulario de registro, sus validaciones, campos y políticas son los de 001; esta especificación no los modifica y no los repite.
- El inicio de sesión real es provisto por otra funcionalidad del equipo; esta especificación solo define la regla a respetar (FR-014) y la valida con un inicio de sesión simulado provisorio.
- El límite de reenvío se administra por dirección de correo con independencia de la sesión, el navegador o el dispositivo del usuario, de modo que la cuenta regresiva sobrevive a recargas y cambios de dispositivo.
- La pantalla "Revisa tu correo" permanece accesible para la dirección pendiente sin requerir sesión iniciada, al menos durante la vigencia del estado "no verificada".
- La eliminación automática de cuentas no verificadas a los 3 días se ejecuta de forma periódica; la frecuencia exacta del proceso es una decisión técnica de la planificación.
- Un token de una cuenta eliminada responde como token inválido (FR-008).
- Los textos de mensaje mostrados al usuario se escriben en español, en línea con el resto de la interfaz.
- No hay datos en producción: las cuentas de prueba se descartan y se recrean; no se requiere migración de datos existentes.
- La verificación por código numérico, la recuperación de contraseña, el cambio de dirección de correo, el 2FA, el OAuth y la implementación real del inicio de sesión están fuera de alcance.
