# Feature Specification: Inicio de sesión de usuario

**Feature Branch**: `feat/user-login`

**Created**: 2026-10-20

**Status**: Draft

**Input**: User description: "Inicio de sesion de usuario (login) con correo y contraseña."

## Relación con otras especificaciones

- **Reemplaza el login simulado provisorio de 003 (Verificación de correo electrónico)**: el inicio de sesión simulado (`SimulatedLoginController`, ya eliminado) es sustituido por el login real. Se conserva la regla **FR-014 de 003**: una cuenta no verificada con contraseña correcta es rechazada con el mensaje "Tu cuenta aún no ha sido verificada. Por favor, revisa tu correo electrónico" y con el indicador legible por máquina `properties.action = "RESEND_VERIFICATION"`, y la pantalla de login ofrece el botón de reenvío de verificación de 003.
- **Modifica la redirección de rutas protegidas de 001 (Registro de usuario)**: el gate de rutas protegidas (`ProtectedRoute`) ahora redirige a `/login` en lugar de `/register`, conservando la ruta intentada para volver a ella tras el inicio de sesión exitoso.
- **Reutiliza de 001**: la cookie de sesión `amano_auth`, el perfil de usuario y el dashboard como destino por defecto.
- **Reutiliza de 003**: el flujo y el botón de reenvío de correo de verificación.

Los identificadores FR-### y SC-### de esta especificación son propios de 004 y no coinciden necesariamente en número con los de 001 ni de 003.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Acceder con correo y contraseña (Priority: P1)

Una persona con cuenta verificada ingresa a la pantalla `/login`, escribe su correo electrónico y su contraseña (oculta por defecto, con opción de mostrarla) y pulsa "Iniciar sesión". El sistema la autentica, establece su sesión y la lleva al dashboard o, si llegó intentando abrir una ruta protegida, a esa misma ruta. Si ya tenía una sesión activa y entra a `/login`, se la redirige directamente al destino sin mostrar el formulario.

**Why this priority**: es el valor central de la feature: sin acceso real no hay forma de entrar a las rutas protegidas de 001/002 una vez eliminado el login simulado.

**Independent Test**: con una cuenta creada y verificada, completar el formulario en `/login` autentica a la persona, establece la sesión y la coloca en el destino esperado (dashboard o ruta protegida intentada).

**Acceptance Scenarios**:

1. **Given** una cuenta verificada con correo y contraseña conocidos, **When** se ingresan credenciales válidas en `/login` y se envía el formulario, **Then** se establece la sesión y se navega al dashboard.
2. **Given** una persona sin sesión que intentó abrir `/profile` y fue redirigida a `/login`, **When** se autentica correctamente, **Then** vuelve a `/profile` en lugar del dashboard.
3. **Given** una sesión ya activa, **When** se navega a `/login`, **Then** se redirige al destino (dashboard o ruta intentada) sin mostrar el formulario.
4. **Given** el formulario de `/login`, **When** está en proceso de envío, **Then** el botón de envío queda deshabilitado y muestra estado de progreso ("Ingresando…") hasta que la respuesta llegue.
5. **Given** la pantalla de login, **When** se observa el campo de contraseña, **Then** la contraseña aparece oculta y un control permite mostrarla y volver a ocultarla.
6. **Given** la pantalla de login sin sesión, **When** se pulsa el enlace de registro, **Then** se navega a `/register`.

---

### User Story 2 - Rechazo seguro de credenciales inválidas o campos vacíos (Priority: P2)

Si faltan campos o las credenciales no corresponden a ninguna cuenta verificada, el sistema informa el error sin revelar si la cuenta existe: los campos vacíos reciben un error por campo, y cualquier intento fallido recibe el mismo mensaje genérico. La contraseña escrita se limpia y el correo se conserva para corregirlo.

**Why this priority**: protege la privacidad de las cuentas (no se puede descubrir qué correos están registrados) y da al usuario una guía clara de corrección; es condición para poder confiar en el login.

**Independent Test**: enviar el formulario vacío y luego con credenciales inexistentes/incorrectas; verificar los errores por campo y un único mensaje idéntico para cualquier credencial inválida.

**Acceptance Scenarios**:

1. **Given** el formulario de login vacío, **When** se intenta enviar, **Then** cada campo vacío muestra su mensaje de campo obligatorio en el cliente y, si la solicitud llega al servidor, el servidor responde con un error por campo (400) sin realizar la autenticación.
2. **Given** una cuenta inexistente, **When** se envía un correo y contraseña arbitrarios, **Then** la respuesta es un 401 con el mensaje "El correo electrónico o la contraseña son incorrectos".
3. **Given** una cuenta existente con contraseña incorrecta, **When** se envía el login, **Then** la respuesta es idéntica a la del caso anterior (mismo 401, mismo mensaje), sin indicar que la cuenta existe.
4. **Given** un intento fallido con credenciales inválidas, **When** se muestra el error en la pantalla, **Then** la contraseña queda vacía y el correo permanece ingresado.
5. **Given** un correo con espacios y/o mayúsculas (por ejemplo, `"  Ana@Example.COM "`), **When** se intenta iniciar sesión, **Then** el correo se normaliza (recorte de espacios y minúsculas) y el inicio de sesión corresponde a la cuenta `ana@example.com`.

---

### User Story 3 - Cuenta no verificada con oferta de reenvío (Priority: P3)

Si las credenciales son correctas pero la cuenta aún no verificó su correo, el sistema rechaza el acceso con el mensaje de la feature 003 y ofrece en la misma pantalla el botón de reenvío del correo de verificación, respetando la regla FR-014 de 003.

**Why this priority**: conserva la regla de negocio de 003 (ninguna cuenta no verificada accede) y guía al usuario hacia la verificación; es secundaria respecto de poder iniciar sesión en sí.

**Independent Test**: con una cuenta creada y aún no verificada, autenticarse con su contraseña correcta y verificar el rechazo con el mensaje de 003 y la disponibilidad del botón de reenvío.

**Acceptance Scenarios**:

1. **Given** una cuenta no verificada con contraseña correcta, **When** se envía el login, **Then** la respuesta es un 403 con el mensaje "Tu cuenta aún no ha sido verificada. Por favor, revisa tu correo electrónico" y con `properties.action = "RESEND_VERIFICATION"`, y no se establece sesión.
2. **Given** el 403 anterior, **When** se muestra la pantalla de login, **Then** aparece el aviso de cuenta no verificada junto con el botón de reenvío de verificación de 003, operable con el correo ingresado.
3. **Given** una cuenta no verificada con contraseña incorrecta, **When** se envía el login, **Then** la respuesta es el mismo 401 genérico de credenciales inválidas (no se revela el estado de verificación).

---

### User Story 4 - Cerrar sesión (Priority: P4)

Desde el dashboard, la persona puede cerrar su sesión con un botón visible; el sistema le pide al servidor que expire la cookie de sesión y la devuelve a la pantalla de login.

**Why this priority**: complemento esperable de cualquier login; no bloquea el acceso pero completa el ciclo de sesión.

**Independent Test**: con una sesión activa, pulsar "Cerrar sesión" en el dashboard y verificar que la sesión terminó y que las rutas protegidas vuelven a pedir login.

**Acceptance Scenarios**:

1. **Given** una sesión activa en el dashboard, **When** se pulsa "Cerrar sesión", **Then** el cierre se confirma al servidor, la cookie de sesión expira y se navega a `/login`.
2. **Given** cualquier cliente, **When** se solicita el cierre de sesión (incluso sin sesión previa), **Then** la operación responde 204 y expira la cookie, siendo idempotente (repetirla no produce error).
3. **Given** una sesión cerrada, **When** se intenta acceder a una ruta protegida, **Then** el acceso es denegado y se redirige a `/login`.

---

### Edge Cases

- **Correo no normalizado**: `"  Ana@Example.COM "` debe autenticar a la misma cuenta que `ana@example.com` (normalización en cliente y en servidor).
- **Cuerpo de solicitud ausente o sin campos**: se responden los errores de campo correspondientes (correo y contraseña) sin realizar ninguna consulta de cuenta.
- **Cuenta inexistente vs. contraseña incorrecta**: ambas situaciones producen exactamente el mismo 401 con el mismo mensaje y sin datos adicionales; además, el costo de la comparación de contraseña se iguala (comparación contra un hash ficticio cuando la cuenta no existe) para no distinguirlas por el tiempo de respuesta.
- **No verificada + contraseña incorrecta**: responde 401 (no 403); el estado de verificación solo se revela a quien ya posee la contraseña correcta.
- **La respuesta de credenciales inválidas no incluye cookie de sesión**; la de cuenta no verificada tampoco.
- **Error de red o servidor caído** durante el envío: se muestra "No se pudo iniciar sesión. Intenta nuevamente" y el botón se rehabilita para reintentar.
- **Respuesta inesperada del servidor** (distinta de los casos previstos): se muestra el detalle del error si existe; si no, el mensaje genérico de error de red.
- **Doble envío**: mientras la solicitud está en curso el botón permanece deshabilitado, evitando logins duplicados.
- **Sesión activa + acceso a `/login`**: redirección al destino sin exponer el formulario (sin destello de contenido protegido).
- **Cierre de sesión repetido o sin sesión**: siempre responde 204 (idempotente).
- **Ruta protegida sin sesión**: redirige a `/login` conservando la ruta intentada (cambio respecto de 001, que redirigía a `/register`).

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: El sistema MUST ofrecer una pantalla de inicio de sesión en `/login` con campos de correo electrónico y contraseña; la contraseña MUST mostrarse oculta por defecto con una opción para mostrarla y ocultarla; el botón de envío MUST estar deshabilitado mientras la solicitud está en curso; la pantalla MUST incluir un enlace al registro (`/register`).
- **FR-002**: El sistema MUST normalizar el correo electrónico (recorte de espacios y conversión a minúsculas) tanto en el cliente como en el servidor antes de autenticar.
- **FR-003**: Para campos vacíos, el cliente MUST mostrar un error por campo sin enviar la solicitud, y el servidor MUST rechazar la solicitud con un error por campo (400) identificando correo y/o contraseña faltantes, sin realizar la autenticación.
- **FR-004**: El cliente MUST validar el formato del correo electrónico antes de enviar, mostrando el mensaje "Ingresá un correo electrónico válido".
- **FR-005**: Para cuenta inexistente o contraseña incorrecta, el sistema MUST responder siempre con el mismo 401 y el mensaje "El correo electrónico o la contraseña son incorrectos", MUST NOT revelar si la cuenta existe (incluye igualar el tiempo de respuesta con una comparación de contraseña contra un hash ficticio cuando la cuenta no existe) y MUST responder sin cookie de sesión; en la interfaz, tras el error, la contraseña MUST quedar vacía y el correo conservarse.
- **FR-006**: Para una cuenta no verificada con contraseña correcta, el sistema MUST responder 403 con el mensaje "Tu cuenta aún no ha sido verificada. Por favor, revisa tu correo electrónico" y con `properties.action = "RESEND_VERIFICATION"`, sin establecer sesión; la pantalla MUST ofrecer el botón de reenvío de verificación de la especificación 003. La verificación de contraseña MUST preceder a la de estado de verificación, de modo que una contraseña incorrecta responda el 401 genérico de FR-005.
- **FR-007**: Ante un inicio de sesión exitoso, el sistema MUST establecer la cookie de sesión `amano_auth` definida en 001 y MUST entregar el perfil del usuario; la interfaz MUST navegar a la ruta protegida que se intentó abrir previamente o, en su defecto, al dashboard.
- **FR-008**: Si un usuario con sesión activa accede a `/login`, el sistema MUST redirigirlo al destino correspondiente (ruta intentada o dashboard) sin mostrar el formulario.
- **FR-009**: Ante un error de red o de indisponibilidad del servidor durante el envío, la interfaz MUST mostrar "No se pudo iniciar sesión. Intenta nuevamente" y MUST rehabilitar el botón de envío.
- **FR-010**: El dashboard MUST ofrecer un botón "Cerrar sesión" que solicite al servidor la expiración de la cookie de sesión y navegue a `/login`; el cierre de sesión MUST estar expuesto como operación pública e idempotente que responde 204 y expira la cookie de sesión (funciona también sin sesión previa).
- **FR-011**: El gate de rutas protegidas MUST redirigir a `/login` (reemplazando la redirección a `/register` de 001) conservando la ruta intentada para usarla como destino tras el login exitoso.
- **FR-012**: El login real MUST reemplazar al inicio de sesión simulado provisorio de 003 (`SimulatedLoginController`, ya eliminado) manteniendo su regla FR-014: ninguna cuenta no verificada accede con credenciales correctas y el rechazo ofrece el reenvío de verificación.
- **FR-013**: Las respuestas de error MUST seguir el formato de error del proyecto (detalle legible para el usuario en español, sin exponer detalles internos, credenciales ni la existencia de cuentas) y los errores de campo MUST identificar el campo afectado.

### Key Entities *(include if feature involves data)*

- **Credenciales de acceso**: correo electrónico (normalizado) y contraseña de una cuenta; la contraseña nunca se muestra ni se devuelve y solo se usa para la comparación de autenticación.
- **Sesión de usuario**: estado de autenticación establecido tras un login exitoso, representado por la cookie `amano_auth` de 001 y asociado al perfil del usuario; se crea con el login y se destruye con el cierre de sesión.
- **Estado de verificación de cuenta**: condición definida en 003 que condiciona el acceso: una cuenta no verificada con contraseña correcta es rechazada con el mensaje de 003 y el indicador `RESEND_VERIFICATION`.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Un usuario con cuenta verificada completa el inicio de sesión y llega a su destino (dashboard o ruta intentada) en el 100 % de los intentos con credenciales correctas, en menos de 10 segundos desde que abre `/login`.
- **SC-002**: El 100 % de los intentos con credenciales inválidas (cuenta inexistente o contraseña incorrecta) producen exactamente el mismo mensaje de error, y una prueba ciega no permite distinguir si la cuenta existe ni por el contenido de la respuesta ni por el tiempo de respuesta.
- **SC-003**: El 100 % de los intentos de cuentas no verificadas con contraseña correcta son rechazados (conservando FR-014 de 003) y presentan la opción de reenviar el correo de verificación.
- **SC-004**: El 100 % de los envíos con campos vacíos muestran un error identificando cada campo faltante, tanto en la interfaz como en la respuesta del servidor.
- **SC-005**: Tras cerrar sesión, el 100 % de los accesos a rutas protegidas vuelve a solicitar credenciales; repetir el cierre de sesión nunca produce un error visible.
- **SC-006**: Un usuario con sesión activa nunca ve el formulario de login: el 100 % de las visitas a `/login` con sesión lo redirigen al destino.
- **SC-007**: Ante una interrupción de red, el 100 % de los intentos muestra el mensaje "No se pudo iniciar sesión. Intenta nuevamente" y el usuario puede reintentar de inmediato.

## Assumptions

- Esta especificación documenta el comportamiento ya implementado en la rama `feat/user-login`; no se agrega alcance nuevo. Cualquier divergencia entre este documento y el código debe resolverse como defecto o como cambio de alcance explícito, nunca por inferencia.
- El mensaje real implementado para credenciales inválidas es "El correo electrónico o la contraseña son incorrectos" (variante "correo electrónico"), usado de forma idéntica en cliente y servidor.
- Los textos de mensaje mostrados al usuario se escriben en español, en línea con el resto de la interfaz.
- La cookie de sesión `amano_auth`, su duración y atributos, y el dashboard como destino por defecto siguen lo definido en 001; esta feature no los redefine.
- El botón, el mensaje y la ventana de espera del reenvío de verificación pertenecen a 003 y se reutilizan sin modificación.
- El registro de usuarios (`/register`) pertenece a 001/003; aquí solo se enlaza desde la pantalla de login.
- Fuera de alcance: recuperación de contraseña (otra tarea del cronograma), opción "recordarme", bloqueo por intentos fallidos, inicio de sesión con proveedores externos (OAuth) y autenticación en dos factores (2FA).
- No se exige verificación de formato de correo en el servidor más allá del carácter obligatorio: un correo con formato inválido se trata como cuenta inexistente (401 genérico).
- La verificación de cobertura y pruebas mínimas (85 %, backend y frontend) exigidas por la constitución se asume cumplida por las pruebas ya existentes de esta feature; su ejecución corresponde a las verificaciones previas al Pull Request.
- La cobertura mínima del 85 % (backend y frontend) exigida por la constitución fue verificada con `./mvnw verify` (JaCoCo) y `npm run test:coverage` (Vitest).
