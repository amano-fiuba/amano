# Feature Specification: Registro de usuario

**Feature Branch**: `feat/user-registration`

**Created**: 2026-10-18

**Status**: Draft

**Input**: User description: "A mano es una aplicación web y móvil para la gestión de gastos personales y compartidos. Para acceder a las funcionalidades de la plataforma, crear grupos y gestionar deudas o pagos, los nuevos usuarios requieren crear una cuenta de forma segura e identificar su perfil dentro del sistema..."

## Clarifications

### Session 2026-10-18

- Q: ¿La preferencia de monedas permite elegir varias monedas a la vez o solo una moneda preferida? (FR-011) → A: Selección múltiple — el usuario puede marcar cualquier subconjunto de ARS, USD y EUR; si no marca ninguno, se asigna ARS por defecto.
- Q: ¿Qué longitud máxima debe aceptar la contraseña? (FR-006) → A: Máximo 64 caracteres — las contraseñas más largas se rechazan con mensaje explicativo.
- Q: ¿Cómo debe tratarse un CBU/CVU ingresado con espacios o guiones separadores durante la validación en vivo? (FR-009) → A: Normalización automática — se eliminan los espacios y guiones ingresados y se validan los 22 dígitos resultantes.
- Q: ¿Cómo debe tratarse los espacios en blanco al inicio o al final del nombre y del apellido al validar la longitud de 2 a 50 caracteres? (FR-002 / FR-003) → A: Se recortan automáticamente antes de validar; la longitud se mide sobre el texto recortado y el texto recortado es el que se guarda.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Registro básico con datos obligatorios (Priority: P1)

Un nuevo usuario ingresa a la pantalla de Registro de la aplicación, completa los campos obligatorios (nombre, correo electrónico, contraseña, confirmación de contraseña y aceptación de Términos y Condiciones y Política de Privacidad) y presiona el botón de registro. El sistema valida la información, confirma la disponibilidad del correo electrónico, crea la cuenta, inicia sesión automáticamente y redirige al usuario al dashboard principal.

**Why this priority**: Es la ruta crítica de la funcionalidad: sin una cuenta registrada no existe acceso a ninguna otra capacidad de la plataforma (gastos personales, grupos, deudas ni pagos). Todo lo demás de esta feature es un refinamiento opcional de esta historia.

**Independent Test**: Puede probarse completamente cargando la pantalla de Registro, completando solo los campos obligatorios con datos válidos y confirmando. Entrega un usuario con cuenta activa, sesión iniciada y acceso al dashboard.

**Acceptance Scenarios**:

1. **Given** un usuario nuevo en la pantalla de Registro, **When** completa nombre (2 a 50 caracteres), correo electrónico válido, contraseña que cumple la política, coincidencia de la confirmación y acepta los Términos y Condiciones, y presiona el botón de registro, **Then** el sistema deshabilita el botón, valida los datos, confirma la disponibilidad del correo, crea la cuenta, inicia la sesión automáticamente y redirige al dashboard principal.
2. **Given** un usuario en la pantalla de Registro cuyo correo electrónico ya existe en el sistema (comparación insensible a mayúsculas/minúsculas y sin espacios), **When** presiona el botón de registro, **Then** se muestra el mensaje "El correo electrónico ya se encuentra registrado", no se crea ninguna cuenta y el botón se habilita nuevamente.
3. **Given** un usuario que prefiere escribir su correo con mayúsculas o espacios en blanco, **When** envía el formulario (por ejemplo, "  Usuario@Ejemplo.COM "), **Then** el sistema lo normaliza (elimina espacios al inicio y al final, trata mayúsculas/minúsculas de forma insensible) y esa dirección no puede registrarse ni volver a registrarse con otra variante de escritura.
4. **Given** un usuario que no acepta la casilla de Términos y Condiciones y Política de Privacidad, **When** presiona el botón de registro, **Then** el registro no se procesa, la casilla y/o los campos incompletos se resaltan con mensajes explicativos en menos de 2 segundos y los datos ya completados se conservan.

---

### User Story 2 - Validaciones en vivo y manejo de errores de envío (Priority: P2)

Mientras el usuario completa el formulario, el sistema valida los formatos sobre la marcha (al escribir o al perder el foco del campo) y gestiona de forma controlada los errores al enviar: validación integral, correo duplicado, doble envío y fallos de red o servidor, con feedback inmediato sin limpiar los datos ya ingresados.

**Why this priority**: Mejora sustancial la calidad y solidez del flujo de registro (menos frustración, menos intentos fallidos) pero no bloquea el valor mínimo: un usuario con validaciones "todo al final" igualmente logra registrarse.

**Independent Test**: Puede probarse de forma independiente intentando registros inválidos (formato de correo erróneo, contraseñas que no coinciden, CBU con 21 dígitos, avatar de 3 MB, doble clic, conexión de red interrumpida) y observando los mensajes, la conservación de los datos y la re-habilitación del botón.

**Acceptance Scenarios**:

1. **Given** un usuario completa el formulario con algún dato inválido (formato de correo, longitud de nombre/apellido fuera de 2-50, contraseña que no cumple la política, contraseñas no coincidentes, CBU/CVU con cantidad de dígitos distinta de 22, avatar en formato no permitido o mayor a 2 MB), **When** el campo pierde el foco (o mientras escribe), **Then** el sistema resalta el campo con un mensaje explicativo en menos de 2 segundos sin limpiar los datos ya completados.
2. **Given** un usuario que presiona el botón de registro múltiples veces consecutivas, **When** el sistema está procesando la primera solicitud, **Then** el botón queda deshabilitado inmediatamente y únicamente la primera solicitud se procesa.
3. **Given** un registro que falla por validación, correo duplicado o error de red, **When** el sistema detecta el fallo, **Then** el botón de registro se habilita nuevamente de inmediato para permitir corregir datos o reintentar.
4. **Given** un usuario que envía el formulario y ocurre un error de conexión o del servidor, **When** el sistema detecta el fallo, **Then** muestra el mensaje "No se pudo completar el registro. Intenta nuevamente", conserva la información ingresada en el formulario y habilita nuevamente el botón.
5. **Given** un usuario que cierra la aplicación o navega hacia otra pantalla antes de confirmar el formulario, **When** abandona el proceso, **Then** no se guarda ninguno de los datos ingresados y no se crea ninguna cuenta.

---

### User Story 3 - Datos opcionales de perfil (apellido, avatar, CBU/CVU y monedas) (Priority: P3)

El usuario puede, de forma opcional, completar datos adicionales de perfil durante el registro: apellido, avatar (imagen), CBU/CVU y preferencia de monedas. Si no proporciona ninguno, el sistema asigna valores predeterminados (avatar por defecto, moneda ARS) y la cuenta se registra igualmente.

**Why this priority**: Enriquece el perfil y prepara funcionalidades futuras (transferencias con CBU/CVU, visualización de montos en la moneda preferida) pero no es necesario para que un usuario acceda y empiece a usar la plataforma.

**Independent Test**: Puede probarse registrando usuarios con distintos subconjuntos de datos opcionales (apellido solo, avatar solo, CBU/CVU solo, monedas ARS/USD/EUR, ninguno) y verificando los valores resultantes en el perfil después del registro, incluidos los valores predeterminados.

**Acceptance Scenarios**:

1. **Given** un usuario que no carga archivo de avatar, **When** completa el registro, **Then** el sistema le asigna un avatar predeterminado.
2. **Given** un usuario que carga un archivo de imagen en formato JPG, JPEG o PNG de hasta 2 MB, **When** envía el formulario, **Then** el sistema acepta el archivo y lo asocia a su perfil.
3. **Given** un usuario que no selecciona ninguna moneda, **When** completa el registro, **Then** el sistema le asigna ARS como moneda por defecto.
4. **Given** un usuario que selecciona una o más de las monedas disponibles (ARS, USD y EUR), **When** completa el registro, **Then** el sistema guarda su preferencia de monedas.
5. **Given** un usuario que ingresa un CBU/CVU válido de 22 dígitos, **When** completa el registro, **Then** el sistema lo guarda y queda visible únicamente para el propio usuario en su perfil (la visibilidad hacia otros usuarios se define en la funcionalidad de gestión de grupos).

---

### Edge Cases

- ¿Qué sucede cuando el usuario abandona el proceso (cierra la app o navega a otra pantalla) antes de confirmar? No se guarda ningún dato ni se crea la cuenta.
- ¿Qué sucede cuando el usuario presiona el botón de registro repetidamente? El sistema deshabilita el botón y procesa únicamente la primera solicitud.
- ¿Qué sucede cuando hay un error de red o del servidor durante el envío? Se muestra "No se pudo completar el registro. Intenta nuevamente", se conservan los datos del formulario y el botón se habilita nuevamente en menos de 2 segundos.
- ¿Qué sucede cuando el correo electrónico difiere solo en mayúsculas/minúsculas o espacios respecto a uno ya registrado? El sistema lo normaliza (trim e insensible a mayúsculas/minúsculas) y lo rechaza como duplicado con el mensaje "El correo electrónico ya se encuentra registrado".
- ¿Qué sucede cuando el archivo de avatar excede 2 MB o tiene un formato distinto de JPG/JPEG/PNG? La validación en vivo lo resalta como inválido y el registro no se procesa hasta corregirlo.
- ¿Qué sucede cuando el CBU/CVU tiene 21 o 23 dígitos resultantes, o contiene caracteres no numéricos distintos de espacios y guiones? La normalización automática elimina espacios y guiones separadores; cualquier otra entrada inválida es rechazada por la validación en vivo por no resultar en exactamente 22 dígitos numéricos.
- ¿Qué sucede cuando el nombre o apellido tienen menos de 2 o más de 50 caracteres? El sistema resalta el campo sin limpiar los demás datos.
- ¿Qué sucede cuando el usuario omite campos obligatorios al confirmar el formulario? El sistema resalta los campos afectados con mensajes explicativos en menos de 2 segundos, sin limpiar los datos ya completados y sin procesar el registro.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: El sistema MUST permitir registrar una cuenta a partir de los datos obligatorios: nombre, correo electrónico, contraseña, confirmación de contraseña y aceptación de los Términos y Condiciones y la Política de Privacidad.
- **FR-002**: El sistema MUST validar que el nombre sea un texto obligatorio de 2 a 50 caracteres, tras recortar automáticamente los espacios en blanco al inicio y al final; el texto recortado es el que se almacena.
- **FR-003**: El sistema MUST aceptar un apellido opcional; si se ingresa, MUST recortar automáticamente sus espacios en blanco al inicio y al final (el texto recortado es el que se almacena) y validarlo como texto de 2 a 50 caracteres.
- **FR-004**: El sistema MUST validar que el correo electrónico tenga formato estándar de e-mail, y MUST procesarlo de forma insensible a mayúsculas/minúsculas eliminando automáticamente los espacios en blanco al inicio y al final.
- **FR-005**: El sistema MUST verificar, antes de crear la cuenta, la disponibilidad del correo electrónico normalizado y MUST rechazar el registro de direcciones que ya existan con el mensaje "El correo electrónico ya se encuentra registrado".
- **FR-006**: El sistema MUST requerir una contraseña de mínimo 8 y máximo 64 caracteres, con al menos una mayúscula, una minúscula, un número y un carácter especial, y MUST rechazar con un mensaje explicativo las contraseñas que no cumplan esa política o cuya confirmación no coincida.
- **FR-007**: El sistema MUST almacenar la contraseña de forma que no sea visible ni recuperable, ni por el equipo de desarrollo ni por otros usuarios (el sistema nunca debe poder mostrar o enviar la contraseña en texto claro).
- **FR-008**: El sistema MUST mostrar los campos de contraseña ocultos por defecto (sin texto visible) durante su ingreso.
- **FR-009**: El sistema MUST aceptar un CBU/CVU opcional que consista en exactamente 22 dígitos numéricos; si el usuario lo ingresa con espacios o guiones separadores, MUST normalizarlo automáticamente eliminando dichos caracteres antes de validar los 22 dígitos resultantes. Además, MUST restringir su visibilidad al propio usuario en su perfil; la visibilidad hacia otros usuarios se define en la funcionalidad de gestión de grupos (fuera de esta feature).
- **FR-010**: El sistema MUST aceptar un avatar opcional en formato JPG, JPEG o PNG con un tamaño máximo de 2 MB; si no se adjunta archivo, MUST asignar un avatar predeterminado.
- **FR-011**: El sistema MUST ofrecer la selección opcional y múltiple de monedas entre las disponibles (ARS, USD y EUR), permitiendo marcar cualquier subconjunto de ellas; si el usuario no elige ninguna, MUST asignar ARS por defecto.
- **FR-012**: El sistema MUST exigir la aceptación de la casilla "Acepto los Términos y Condiciones y la Política de Privacidad" para completar el registro.
- **FR-013**: El sistema MUST validar en vivo (mientras escribe o al perder el foco del campo) el formato de correo, la longitud de los campos de texto, la coincidencia de contraseñas, el formato del CBU/CVU (22 dígitos) y el tamaño y formato del archivo de avatar.
- **FR-014**: Al confirmar el formulario, el sistema MUST verificar los campos obligatorios incompletos, la aceptación de los términos y la disponibilidad del correo electrónico en el servidor.
- **FR-015**: Cuando el usuario presiona el botón de registro, el sistema MUST deshabilitar inmediatamente el botón para evitar doble procesamiento, y MUST procesar únicamente la primera solicitud ante envíos consecutivos.
- **FR-016**: Si la información es válida y el correo está disponible, el sistema MUST crear la cuenta, iniciar sesión automáticamente y redirigir al usuario al dashboard principal.
- **FR-017**: Ante cualquier fallo (validación, correo duplicado o error de red/servidor), el sistema MUST habilitar nuevamente el botón de registro de inmediato para permitir corregir datos o reintentar.
- **FR-018**: El sistema MUST resaltar los campos inválidos o incompletos con mensajes explicativos en menos de 2 segundos, sin limpiar los datos ya completados.
- **FR-019**: Ante error de conexión o del servidor al enviar la solicitud, el sistema MUST mostrar el mensaje "No se pudo completar el registro. Intenta nuevamente", conservar la información ingresada en el formulario y habilitar nuevamente el botón.
- **FR-020**: Si el usuario cierra la aplicación o navega hacia otra pantalla antes de confirmar, el sistema MUST no guardar ninguno de los datos ingresados.
- **FR-021**: Tras completar el registro, el usuario MUST poder consultar de inmediato en su perfil la información y preferencias que guardó (datos de perfil, avatar, CBU/CVU solo visible para él y monedas).
- **FR-022**: El sistema MUST validar los datos de entrada en sus fronteras: las validaciones de la interfaz de usuario no son el único control, y el servidor también debe rechazar datos inválidos o duplicados sin crear cuentas inconsistentes.
- **FR-023**: El sistema MUST indicar visualmente cuáles campos del formulario de registro son obligatorios mediante un asterisco (*) junto al nombre del campo, y MUST mostrar el mensaje "Los campos con (*) son obligatorios" antes del botón de registro. Los campos opcionales (apellido, CBU/CVU, avatar y monedas) MUST presentarse sin dicho asterisco y sin la marca "(opcional)" en su etiqueta: su carácter opcional queda implícito por la ausencia de asterisco y la leyenda de obligatorios.

### Key Entities *(include if feature involves data)*

- **Cuenta de Usuario**: Representa la identidad registrada de un usuario dentro de la plataforma. Atributos: nombre (obligatorio), apellido (opcional), correo electrónico normalizado y único (obligatorio), credencial secreta de acceso (nunca visible ni recuperable en texto claro), consentimiento legal de Términos y Condiciones y Política de Privacidad (obligatorio con fecha de aceptación).
- **Perfil**: Información opcional asociada a la cuenta: avatar (con valor predeterminado cuando no se carga), CBU/CVU (visible solo para el propio usuario) y selección de monedas preferidas (por defecto ARS).
- **Preferencia de Monedas**: Selección opcional y múltiple de la cuenta entre las monedas disponibles (ARS, USD y EUR), expresada como subconjunto de las monedas disponibles y no vacío; con ARS como valor predeterminado.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Un usuario nuevo puede completar el proceso de registro e ingresar al dashboard principal en menos de 1 minuto.
- **SC-002**: Tras registrarse, el usuario inicia sesión automáticamente y puede consultar de inmediato su información y preferencias guardadas en su perfil, sin acciones adicionales.
- **SC-003**: El sistema muestra los mensajes de error en pantalla y rehabilita el botón de acción en menos de 2 segundos tras detectar un fallo de validación o un problema de red.
- **SC-004**: Ninguna cuenta puede registrarse dos veces con el mismo correo electrónico, incluso cuando las escrituras difieren en mayúsculas/minúsculas o en espacios en blanco al inicio o al final.
- **SC-005**: Ninguna contraseña es visible ni recuperable en texto claro por el equipo de desarrollo ni por otros usuarios, en la interfaz, los registros o cualquier canal del sistema.
- **SC-006**: Un usuario abandona el formulario sin enviar y no queda ningún dato persistido ni cuenta creada (100 % de los casos de abandono sin procesar).
- **SC-007**: En condiciones normales de red, la tasa de registros completados con éxito en el primer intento es del 90 % o superior entre usuarios que ingresan datos válidos.

## Assumptions

- El usuario cuenta con conectividad a internet suficiente para comunicarse con el servidor durante el registro.
- La verificación o confirmación del correo electrónico (enlace o código), la recuperación de contraseña, el registro vía proveedores de terceros (OAuth), la verificación biométrica, el 2FA y la validación del CBU/CVU ante entidades bancarias están explícitamente fuera del alcance de esta feature.
- El reaprovechamiento o vinculación de perfiles simulados/invitados ya creados en un grupo queda fuera del alcance de esta feature.
- La casilla de Términos y Condiciones enlaza a los documentos legales vigentes ("Acepto los Términos y Condiciones y la Política de Privacidad"); su contenido y publicación se abordan en el proceso legal correspondiente.
- El CBU/CVU solo se valida por formato (22 dígitos numéricos) en esta feature; no se contrae ninguna verificación ante entidades bancarias.
- La confirmación de contraseña (contraseña y confirmación deben coincidir) es un campo de entrada obligatorio, adicional a la contraseña propiamente dicha.
- Un correo electrónico normalizado (sin espacios y comparación insensible a mayúsculas/minúsculas) constituye la identidad única de la cuenta a efectos de duplicados.
- El dashboard principal y la sesión automática posterior al registro son provistos por la plataforma; esta feature solo requiere que la redirección ocurra y sea verificable desde la perspectiva del usuario.
