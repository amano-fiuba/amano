# Feature Specification: Visualización de perfil de usuario

**Feature Branch**: `feat/user-profile`

**Created**: 2026-10-19

**Status**: Draft

**Input**: User story: "Como usuario registrado quiero visualizar mi perfil para consultar la información asociada a mi cuenta. Visualizar los campos obligatorios nombre, mail y contraseña. Los campos opcionales: apellido, avatar, cbu/cvu para transferir, preferencias de monedas. Criterios de Aceptación: el sistema debe permitir al usuario acceder a su perfil; debe mostrar los datos personales registrados del usuario; debe mostrar únicamente la información correspondiente al usuario que inició sesión; debe proporcionar una opción para modificar los datos del perfil."

## Clarifications

### Session 2026-10-19

- Q: ¿Cómo se "visualiza la contraseña" si el spec `001` (FR-007/SC-005) exige que nunca sea visible ni recuperable en texto claro? → A: El perfil muestra el campo de contraseña **enmascarado** (`●●●●●●●●`) acompañado de la leyenda "Por seguridad, la contraseña no se muestra". El valor real nunca llega al cliente: el backend no lo expone en ningún endpoint.
- Q: El criterio de aceptación pide "una opción para modificar los datos del perfil". ¿Qué entra en esta feature? → A: Esta feature es **solo visualización** (decisión del dueño del producto el 2026-10-19). La modificación de datos del perfil se difiere a una historia futura; esta feature no agrega ningún control de edición (ni botón placeholder).
- Q: ¿Se puede editar el correo electrónico desde el perfil? → A: No, esta feature es de solo lectura; el email se muestra pero no es editable.
- Q: ¿Cómo se muestran los campos opcionales que el usuario no completó (apellido, CBU/CVU)? → A: Se muestra el texto "No informado" en lugar del valor.
- Q: ¿Puede el usuario ver el perfil de otros usuarios en esta feature? → A: No. El perfil siempre refleja únicamente al usuario en sesión; la visualización de perfiles de terceros (ej. en grupos) queda para funcionalidades futuras.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Visualizar mi perfil (Priority: P1)

Un usuario con sesión iniciada accede a la pantalla "Mi perfil" y consulta la información asociada a su cuenta: los campos obligatorios registrados (nombre, correo electrónico y contraseña presentada de forma enmascarada) y los campos opcionales que completó en el registro (apellido, avatar, CBU/CVU y preferencias de monedas).

**Why this priority**: Es la ruta crítica de la funcionalidad: cumple el propósito principal de la historia (consultar la información de la cuenta). Sin esta pantalla, ningún otro escenario de la feature tiene valor.

**Independent Test**: Puede probarse completamente iniciando sesión con un usuario registrado, navegando a "Mi perfil" y verificando que cada campo —obligatorio u opcional, presente o ausente— se muestra con su valor registrado o con su representación prevista (enmascarado, "No informado", valor predeterminado).

**Acceptance Scenarios**:

1. **Given** un usuario con sesión iniciada que completó en el registro nombre, correo y contraseña, **When** accede a "Mi perfil", **Then** el sistema muestra su nombre, su correo electrónico y el campo de contraseña enmascarado con la leyenda "Por seguridad, la contraseña no se muestra".
2. **Given** un usuario con sesión iniciada que registró apellido, avatar, CBU/CVU y monedas preferidas, **When** accede a "Mi perfil", **Then** el sistema muestra cada campo con el valor que guardó: su apellido, su imagen de avatar, su CBU/CVU de 22 dígitos y sus monedas preferidas.
3. **Given** un usuario con sesión iniciada que no registró algún campo opcional, **When** accede a "Mi perfil", **Then** el sistema muestra "No informado" en apellido y en CBU/CVU, el avatar predeterminado si no subió imagen, y ARS como moneda si no eligió ninguna.

---

### User Story 2 - Aislamiento de datos: solo mi información (Priority: P2)

El perfil muestra únicamente la información correspondiente al usuario que inició sesión. Ningún otro perfil es accesible desde esta pantalla, ni siquiera manipulando la URL con el identificador de otro usuario.

**Why this priority**: Protege la privacidad del dato personal y del CBU/CVU (dato financiero), requisito explícito de la historia. No es la ruta crítica (sin esta garantía US1 seguiría funcionando), pero es condición de calidad del valor entregado.

**Independent Test**: Puede probarse registrando dos usuarios distintos e iniciando sesión con uno de ellos: el perfil muestra exclusivamente los datos de la sesión activa y los intentos de acceso al avatar bajo un id ajeno no exponen información.

**Acceptance Scenarios**:

1. **Given** dos usuarios registrados con datos distintos y el usuario A con sesión iniciada, **When** A accede a "Mi perfil", **Then** se muestran únicamente los datos registrados por A; los datos de B no aparecen en ningún campo.
2. **Given** el usuario A con sesión iniciada, **When** intenta acceder a los datos de un id de usuario distinto al propio (por ejemplo, la URL del avatar de B), **Then** el sistema no expone la información de B.

---

### User Story 3 - Acceso protegido y estados de fallo (Priority: P3)

Solo un usuario con sesión válida puede ver la pantalla de perfil. Sin sesión, el sistema redirige al flujo de acceso preservando el destino ante eventuales opciones de retorno. Ante errores de red o del servidor, la pantalla muestra un mensaje sin inventar datos. El avatar propio se muestra si existe; si no existe (o falla su carga), se muestra el avatar predeterminado.

**Why this priority**: Refina la solidez del acceso (privacidad y resiliencia), pero un usuario con sesión válida y red estable ya obtiene el valor completo con la US1.

**Independent Test**: Puede probarse accediendo a "Mi perfil" sin cookie de sesión (expectativa: redirección), simulando errores de red durante la consulta (expectativa: mensaje de error sin datos ficticios) y comparando usuarios con y sin avatar propio (expectativa: imagen propia o predeterminada).

**Acceptance Scenarios**:

1. **Given** un usuario sin sesión iniciada, **When** intenta acceder a "Mi perfil", **Then** el sistema redirige al flujo de registro/acceso sin mostrar ningún dato del perfil.
2. **Given** un usuario con sesión iniciada, **When** la consulta del perfil falla por error de red o del servidor, **Then** la pantalla muestra un mensaje de error y no renderiza datos incompletos ni ficticios.
3. **Given** un usuario con sesión iniciada y avatar propio, **When** accede a "Mi perfil", **Then** se muestra su imagen cargada; sin avatar propio o si la imagen falla al cargar, **Then** se muestra el avatar predeterminado.

---

### Edge Cases

- ¿Qué sucede cuando el usuario intenta acceder al perfil sin sesión iniciada (cookie ausente, inválida o expirada)? Redirección al flujo de registro/acceso; no se muestran datos.
- ¿Qué sucede cuando la sesión expira mientras el usuario ve el perfil? La siguiente consulta falla con 401 y el usuario es redirigido; no quedan datos en pantalla.
- ¿Qué sucede cuando hay error de red o del servidor durante la consulta del perfil? Mensaje de error visible, sin datos en pantalla y sin romper la SPA.
- ¿Qué sucede cuando el perfil no tiene apellido o CBU/CVU? Se muestra "No informado" en cada campo ausente.
- ¿Qué sucede cuando el perfil no tiene avatar propio (o la imagen no cargó)? Se muestra `/avatar-default.png`.
- ¿Qué sucede cuando el perfil no tiene ninguna moneda seleccionada? Se muestra ARS como moneda efectiva (default del spec `001`, FR-011/Q1).
- ¿Qué sucede cuando el usuario intenta ver la información de otro usuario? No hay ruta que lo permita: la pantalla consulta siempre su propia sesión; la información de B nunca aparece en la sesión de A.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: El sistema MUST proporcionar una ruta "Mi perfil" accesible para el usuario con sesión iniciada, alcanzable desde la aplicación tras el registro o inicio de sesión.
- **FR-002**: El perfil MUST mostrar los datos obligatorios del usuario en sesión: nombre, correo electrónico y contraseña (esta última presentada de forma enmascarada, jamás en texto claro y con la leyenda "Por seguridad, la contraseña no se muestra").
- **FR-003**: El perfil MUST mostrar los datos opcionales del usuario en sesión: apellido, avatar, CBU/CVU y preferencias de monedas.
- **FR-004**: El sistema MUST mostrar "No informado" en lugar del valor de cada campo opcional de texto ausente (apellido, CBU/CVU). (Clarificación 2026-10-19.)
- **FR-005**: El sistema MUST mostrar el avatar del usuario: su imagen cargada cuando exista, o el avatar predeterminado cuando no exista o la imagen no pueda cargarse.
- **FR-006**: El perfil MUST mostrar como monedas preferidas las guardadas por el usuario; cuando no registró ninguna, MUST mostrar ARS (default del spec `001`, FR-011/Q1).
- **FR-007**: El perfil MUST mostrar únicamente la información del usuario que inició sesión; la pantalla consulta al servicio con la sesión activa y no admite el acceso a información de terceros.
- **FR-008**: El sistema MUST redirigir al flujo de registro/acceso cuando se intente acceder al perfil sin sesión iniciada, sin mostrar ningún dato.
- **FR-009**: Ante error de red o del servidor al consultar el perfil, el sistema MUST mostrar un mensaje de error y MUST NOT renderizar datos incompletos ni ficticios.
- **FR-010**: El sistema MUST reutilizar la infraestructura de `001-user-registration` (endpoints `GET /api/users/me` y `GET /api/users/{id}/avatar`, cookie de sesión `amano_auth`); esta feature MUST NOT introducir nuevos endpoints ni cambios de esquema (ver [contracts/users-api.md](contracts/users-api.md) y [data-model.md](data-model.md)).
- **FR-011**: El sistema MUST presentar la pantalla de perfil como de solo lectura: ningún control de esta pantalla modifica datos; la edición del perfil queda diferida a una historia futura (Clarificación 2026-10-19).
- **FR-012**: El sistema MUST mostrar en el dashboard el saludo "Te damos la bienvenida, {nombre}" utilizando únicamente el nombre del usuario de la sesión, sin el apellido. (Añadido por ajuste de UI 2026-10-26; el saludo anterior —"Bienvenida," con nombre y apellido— queda reemplazado.)

### Key Entities *(include if feature involves data)*

No se crean ni modifican entidades: la feature es de solo lectura. Se reutilizan las entidades de `001-user-registration` ([data-model.md](../001-user-registration/data-model.md)):

- **Cuenta de Usuario** (lectura): nombre, apellido, correo electrónico normalizado y único, credencial secreta (nunca expuesta), consentimiento legal.
- **Perfil** (lectura): avatar (con valor predeterminado cuando no se cargó), CBU/CVU (visible solo para el dueño) y preferencias de monedas.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Un usuario con sesión llega desde la aplicación a su perfil en 2 interacciones o menos y la pantalla muestra datos completos en menos de 2 segundos en condiciones normales de red.
- **SC-002**: El 100% de las visualizaciones del perfil muestran exclusivamente datos del usuario en sesión; en ningún caso un usuario ve campos de otro.
- **SC-003**: La contraseña nunca se muestra ni se envía en texto claro, ni en la pantalla, ni en la red a través de los endpoints usados por esta feature (100% de los casos).
- **SC-004**: En con problemas de red o expiración de sesión, la pantalla muestra el estado de fallo (mensaje o redirección) en menos de 2 segundos tras detectarlo.
- **SC-005**: Los campos opcionales ausentes se representan de forma consistente ("No informado" / avatar default / ARS) en el 100% de los casos.

## Assumptions

- Se reutiliza el sistema de autenticación de `001-user-registration` (cookie `amano_auth`, endpoint `GET /api/users/me` con principal = id de usuario); no se requiere modelo de acceso nuevo.
- El backend nunca transmite la contraseña ni su hash (garantía de FR-007 de `001`); el enmascaramiento del perfil es presentación pura de la SPA.
- La modificación de datos del perfil, el cambio de contraseña y la verificación de correo electrónico quedan explícitamente fuera del alcance de esta feature (se gestionarán en historias futuras).
- La visualización de perfiles de otros usuarios (ej. en la gestión de grupos) queda fuera del alcance de esta feature.
- El dashboard principal y su navegación hacia el perfil pertenecen a la plataforma provista; esta feature requiere únicamente que la pantalla sea alcanzable y verificable desde la perspectiva del usuario.
