# Feature Specification: Modificar perfil de usuario

**Feature Branch**: `feat/edit-user-profile`

**Created**: 2026-10-20

**Status**: Draft

**Input**: User story (Nro 5, estimación 2): "Como usuario registrado quiero modificar los datos de mi perfil para mantener actualizada la información asociada a mi cuenta. Puede completar los campos opcionales como apellido, avatar, cvu/cbu para transferir, preferencias de monedas. Puede modificar el nombre y la contraseña. No puede modificar el mail. Criterios de Aceptación: el sistema debe mostrar los datos actuales del usuario al ingresar a la opción de edición; debe permitir modificar los datos del perfil que sean editables; debe permitir completar los datos del perfil opcionales que estén vacíos; debe validar los campos obligatorios antes de guardar los cambios; no debe permitir editar el mail; debe permitir cancelar la edición sin guardar los cambios; al guardar información válida, debe actualizar los datos del perfil; debe informar al usuario cuando los cambios se hayan guardado correctamente; si existe un error de validación, debe informar el motivo y permitir corregir los datos; los cambios realizados deben visualizarse al volver a consultar el perfil."

## Relación con las especificaciones 001 y 002

Esta especificación ACTIVA la edición de perfil diferida por las especificaciones anteriores:

- **Activa el alcance diferido por FR-011 de 002** (pantalla de perfil de solo lectura): el perfil deja de ser únicamente de lectura y esta especificación incorpora la vía de edición. La pantalla de visualización de `002` se mantiene como lugar de consulta y punto de entrada a la edición.
- **Reutiliza sin cambios las reglas de campo de 001**: longitudes y recortes de nombre/apellido (FR-002/FR-003), formato del CBU/CVU (FR-009), política de contraseña (FR-006), reglas de avatar (FR-010) y selección de monedas (FR-011), más la validación en fronteras del servidor (FR-022).
- **Hereda el bloqueo de 003**: la edición exige cuenta con correo verificado y sesión válida (cookie `amano_auth`); no introduce tratamientos especiales para cuentas no verificadas.

Los identificadores FR-### y SC-### de esta especificación son propios de 005; toda referencia cruzada se cualifica con la especificación de origen.

Todo lo demás de `001`–`003` se mantiene sin cambios.

## Clarifications

### Session 2026-10-20

- Q: ¿El cambio de contraseña exige que el usuario acredite su contraseña actual? → A: Sí. El cambio solo se procesa si la contraseña actual ingresada coincide con la acreditada por la cuenta; de lo contrario la solicitud es rechazada y el cambio no se aplica (decisión del dueño del producto el 2026-10-20).
- Q: ¿Qué puede hacer el usuario con su avatar desde la edición del perfil? → A: Puede reemplazar la imagen subida por una nueva (con las mismas reglas de formato y tamaño de `001`) y puede eliminar la propia para volver al avatar predeterminado.
- Q: ¿Qué sucede con las monedas preferidas si el usuario las desmarca todas al editar? → A: Se guarda la lista vacía; la visualización aplica el default de `001` (FR-011/Q1): ARS como moneda efectiva.
- Q: ¿La edición cubre también el cambio de dirección de correo electrónico? → A: No. El correo es invariante de la cuenta: se muestra en la edición pero ningún control ni endpoint de esta feature permite modificarlo. Ningún flujo de cambio de correo (con o sin re-verificación) forma parte de esta especificación (queda para una historia futura).

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Editar los datos del perfil (Priority: P1) 🎯 MVP

Un usuario con sesión iniciada accede a la edición de su perfil. El sistema le muestra los datos actuales cargados desde el servidor (nombre, correo electrónico, apellido, CBU/CVU y monedas preferidas). Puede modificar el nombre, completar o corregir los campos opcionales (apellido, CBU/CVU) y ajustar sus monedas preferidas; el correo electrónico se muestra siempre deshabilitado para edición. Al guardar, el sistema valida los campos, actualiza los datos y confirma el éxito; los cambios quedan visibles al volver a consultar el perfil. También puede cancelar la edición y cerrar la pantalla sin que se guarde nada.

**Why this priority**: Es la ruta crítica de la historia (Nro 5): sin ella no existe ningún valor entregado. Cubre la inmensa mayoría de los criterios de aceptación (dato precargado, campos editables, opcionales vacíos, validación, mail no editable, cancelación, guardado con confirmación y persistencia visible).

**Independent Test**: Puede probarse iniciando sesión con un usuario registrado, entrando a la edición, verificando la precarga con los datos del servidor, modificando campos (incluidos los opcionales que estaban vacíos), guardando y confirmando el mensaje de éxito y la persistencia al reconsultar el perfil; y repitiendo con cancelación y con datos inválidos para ver los mensajes por campo.

**Acceptance Scenarios**:

1. **Given** un usuario con sesión iniciada, **When** accede a la edición de su perfil, **Then** el sistema muestra precargados los datos actuales del usuario tal como están guardados en el servidor (nombre, correo electrónico, apellido, CBU/CVU y monedas preferidas), en menos de 2 segundos.
2. **Given** el usuario en la edición, **When** modifica el nombre y el apellido con textos válidos (2–50 caracteres tras el recorte de espacios de `001`), **Then** el sistema acepta los cambios y es capaz de guardarlos.
3. **Given** un usuario cuyo perfil no registra apellido o CBU/CVU, **When** completa esos campos opcionales con valores válidos (apellido 2–50 caracteres; CBU/CVU de exactamente 22 dígitos tras la normalización de espacios y guiones de `001`) y guarda, **Then** el sistema los persiste y son visibles al volver a consultar el perfil.
4. **Given** el usuario en la edición, **When** desmarca todas las monedas y guarda, **Then** el sistema persiste la lista vacía y la visualización posterior muestra ARS como moneda efectiva (default de `001`, FR-011/Q1).
5. **Given** el usuario edita sus datos, **When** deja el nombre vacío o por debajo de 2 caracteres, o ingresa un CBU/CVU que no resulta en 22 dígitos numéricos, **Then** el sistema resalta el campo con un mensaje explicativo del motivo, no guarda y conserva el resto de los datos ya completados.
6. **Given** el usuario envía cambios con algún campo inválido, **When** el servidor rechaza la solicitud por validación, **Then** la pantalla informa el motivo por campo (formato RFC 7807 de `001`), conserva los datos escritos y permite corregir y volver a guardar.
7. **Given** el usuario guardó información válida, **When** el sistema completa la actualización, **Then** informa en pantalla que los cambios se guardaron correctamente y, al volver a consultar el perfil (pantalla de `002` o reingreso a la edición), los datos actualizados son los que se muestran.
8. **Given** el usuario en la edición con cambios sin guardar, **When** cancela la edición (o abandona la pantalla), **Then** no se guarda ningún cambio y la consulta posterior del perfil muestra los datos anteriores.

---

### User Story 2 - Modificar la contraseña (Priority: P2)

Desde la edición, el usuario puede cambiar su contraseña. El sistema exige acreditar la contraseña actual: solo procesa el cambio si es correcta y si la nueva contraseña cumple la política de `001` (8–64 caracteres, con al menos una mayúscula, una minúscula, un número y un carácter especial) y coincide con su confirmación. La sesión activa se mantiene tras el cambio.

**Why this priority**: La historia lo exige explícitamente ("puede modificar el nombre y la contraseña"). Depende de la pantalla de edición de US1 (a la que se llega) pero es valor autónomo y usa reglas íntegramente reutilizadas de `001`; su falla no impide el valor de US1.

**Independent Test**: Puede probarse iniciando sesión, ingresando el flujo de cambio de contraseña, cambiando con la contraseña actual correcta y validando el inicio de sesión con la nueva; y repitiendo con la contraseña actual incorrecta y con contraseñas nuevas que no cumplen la política, verificando los rechazos y que la contraseña anterior siga siendo válida.

**Acceptance Scenarios**:

1. **Given** un usuario con sesión iniciada, **When** ingresa su contraseña actual correcta y una nueva que cumple la política de `001` (junto con su confirmación) y confirma, **Then** el sistema actualiza la credencial, informa el cambio con éxito y la sesión se mantiene activa en la misma pantalla.
2. **Given** el usuario en el cambio de contraseña, **When** ingresa una contraseña actual que no coincide con la acreditada por la cuenta, **Then** el sistema rechaza el cambio con el mensaje "La contraseña actual no es correcta" y la contraseña anterior sigue siendo la válida.
3. **Given** el usuario ingresa una contraseña nueva que no cumple la política de `001` (o cuya confirmación no coincide), **When** confirma el cambio, **Then** el sistema resalta el campo con el mensaje explicativo correspondiente y no modifica la credencial.
4. **Given** el cambio de contraseña se concretó, **When** el usuario consulta el perfil, **Then** el campo de contraseña continúa presentándose enmascarado con su leyenda (la regla de solo visualización de `002` no cambia) y la credencial nueva es la que permite autenticar en el próximo inicio de sesión.

---

### User Story 3 - Gestionar el avatar (reemplazar y eliminar) (Priority: P3)

Desde la edición, el usuario puede reemplazar la imagen de avatar que subió por una nueva (con el mismo formato y límite de tamaño aceptados en el registro de `001`: JPG, JPEG o PNG de hasta 2 MB, validados por contenido real) y puede eliminar su avatar propio, volviendo a la representación predeterminada. Si no tenía avatar propio, puede subir uno por primera vez.

**Why this priority**: La historia menciona al avatar entre los campos opcionales. En los casos donde la edición ya existe (US1) es un agregado pequeño, pero depende de la infraestructura de avatares de `001` y no incluye ningún comportamiento de la ruta crítica.

**Independent Test**: Puede probarse con un usuario que sube un avatar al editar y verifica que la imagen nueva es la que se sirve; que elimina su avatar y que la consulta del perfil muestra el predeterminado; y que sube un archivo inválido (formato distinto o > 2 MB) y el sistema lo rechaza sin corromper el avatar vigente.

**Acceptance Scenarios**:

1. **Given** un usuario con sesión iniciada, **When** sube una nueva imagen de avatar válida desde la edición, **Then** el sistema la hace su avatar vigente: la consulta del perfil sirve la imagen nueva (el anterior deja de estar disponible).
2. **Given** un usuario con avatar propio, **When** elimina su avatar desde la edición, **Then** el sistema quita la imagen y la visualización vuelve al avatar predeterminado (`hasCustomAvatar=false`, fallback `/avatar-default.png` de `002`).
3. **Given** un usuario sin avatar propio, **When** sube una imagen válida desde la edición, **Then** el sistema crea su avatar: la consulta lo muestra con `hasCustomAvatar=true`.
4. **Given** el usuario intenta subir un archivo con formato distinto de JPG/JPEG/PNG o mayor a 2 MB, **When** el sistema lo valida en vivo, **Then** resalta el campo con el mensaje de `001` y el avatar vigente no se altera hasta corregir la imagen.

---

### User Story 4 - Aislamiento, acceso protegido y estados de fallo (Priority: P4)

La edición del perfil opera siempre sobre el usuario de la sesión (principal): ningún endpoint de edición acepta operar sobre un tercero. Solo un usuario con sesión válida y correo verificado accede a la edición; sin sesión hay redirección al flujo de acceso. Ante fallos de red o del servidor durante el guardado, la pantalla informa el error conservando los datos escritos sin aplicar cambios. La modificación del correo electrónico es imposible por diseño: ni la interfaz ni la API la permiten.

**Why this priority**: Refina la solidez y la seguridad de lo anterior (privacidad del CBU/CVU y del dato personal, resiliencia, invariante del mail). Un usuario con sesión válida, red estable y datos correctos ya obtiene el valor completo con US1–US3.

**Independent Test**: Puede probarse ejecutando las consultas de guardado de la edición sin sesión (expectativa: 401 y redirección), con fallos de red o del servidor (expectativa: mensaje sin aplicar cambios ni limpiar datos), verificando que ningún parámetro de la API altera datos de otro usuario, y que el campo de correo queda deshabilitado y la API ignora o rechaza cualquier intento de modificarlo.

**Acceptance Scenarios**:

1. **Given** el usuario A con sesión iniciada, **When** edita y guarda su perfil, **Then** solo se modifican los datos de A: ningún intento (incluido un cliente manipulado o una solicitud fabricada) altera los datos de B.
2. **Given** el usuario en la edición, **When** la sesión expira (cookie vencida) y guarda, **Then** el sistema responde con 401, no aplica ningún cambio, conserva el estado de la pantalla a nivel de lo editable por el usuario y redirige al flujo de acceso.
3. **Given** el usuario guarda cambios y ocurre un error de red o del servidor, **When** el sistema detecta el fallo, **Then** muestra el mensaje "No se pudo completar la actualización. Intenta nuevamente" (u otro general de `001` si aplica), conserva los datos escritos en el formulario y habilita nuevamente el botón para reintentar.
4. **Given** el usuario en la edición, **When** intenta modificar el correo electrónico, **Then** la interfaz no lo permite (campo deshabilitado, de solo lectura) y, aun cuando la solicitud lo incluya, el servidor no lo cambia: el correo persistente es siempre el mismo.

---

### Edge Cases

- **Doble envío de guardar**: ante clics consecutivos, el sistema deshabilita el botón durante el procesamiento y procesa únicamente la primera solicitud (patrón FR-015 de `001`).
- **Abandono con cambios sin guardar** (navegación hacia atrás, cierre de pestaña, cancelación, envío fallido que no se reintenta): no se persiste ningún cambio; la cuenta conserva los valores anteriores en el 100 % de los abandonos sin confirmar (SC-006 de `001` y esta misma regla aplicada a la edición de perfil).
- **Actualización concurrente de la sesión de otro dispositivo**: los datos de perfil se leen frescos del servidor al abrir la edición; el último guardado sobre un campo es el que persiste (read-modify-write por pantalla; no se exige resolución de conflictos de edición simultánea en esta feature — ver Assumptions).
- **Contraseña actual incorrecta**: rechazo con mensaje específico, sin bloquear la cuenta (no se cuenta como intento fallido de login distinto del ya existente en el flujo de autenticación) y sin errores que contaminen la sesión activa.
- **Contraseña nueva idéntica a la actual**: está permitida; el sistema valida solo la política y la acreditación de la contraseña actual (sin regla de historia de contraseñas en esta feature).
- **CBU/CVU con espacios o guiones**: se normalizan automáticamente antes de validar los 22 dígitos resultantes (patrón FR-009 de `001`); la forma normalizada es la que se guarda.
- **Nombre o apellido fuera del rango 2–50 tras el recorte, o recortados vacíos**: rechazo del campo con mensaje explicativo; los datos ya escritos se conservan.
- **Monedas con valores inválidos o repetidos**: la API válida solo acepta elementos del conjunto {ARS, USD, EUR}; el servidor normaliza el conjunto y descarta duplicados/valores desconocidos (o rechaza con 400 — ver contracts; decisión de tasks).
- **Vencimiento de sesión a mitad de la edición**: al guardar responde 401; la UI no inventa éxito; nada se guarda.
- **Avatar inválido (formato o tamaño)**: rechazo en vivo y en el servidor (consistente con `001`); el avatar vigente se mantiene intacto.
- **El usuario reenvía datos idénticos al valor previo** (guardar sin cambios): el sistema los procesa como actualización válida y confirma el éxito (no revela si hubo diferencias).

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: El sistema MUST ofrecer la edición de perfil a los usuarios con sesión iniciada y correo verificado, alcanzable desde la pantalla de visualización de `002` en ≤ 1 interacción adicional, mediante una ruta protegida (`ProfileEditPage`).
- **FR-002**: Al ingresar a la edición, el sistema MUST precargar los datos actuales del usuario, consultados frescos del servidor (`GET /api/users/me`), sin leer de ningún caché de usuario (estados loading / datos / error, con mensajes de `002` ante fallos).
- **FR-003**: El sistema MUST permitir modificar el nombre aplicando las reglas de `001` (FR-002): obligatorio, 2–50 caracteres tras eliminar espacios al inicio y al final; el texto recortado es el que se guarda.
- **FR-004**: El sistema MUST permitir completar o modificar el apellido (opcional, 2–50 caracteres tras el recorte de FR-003 de `001`) y el CBU/CVU (opcional, 22 dígitos numéricos tras eliminar espacios y guiones separadores, FR-009 de `001`), incluyendo limpiarlos (volver a vacíos).
- **FR-005**: El sistema MUST permitir modificar las preferencias de monedas como subconjunto de {ARS, USD, EUR} (reglas de FR-011 de `001`); la lista vacía queda permitida y la visualización muestra ARS como efectiva (default FR-011/Q1 de `001`).
- **FR-006**: El sistema MUST validar los campos antes de guardar —obligatoriedad del nombre y formatos de apellido/CBU/CVU/monedas— con validación en vivo (al escribir o al perder el foco, patrón FR-013 de `001`) y, obligatoriamente, en el servidor al procesar el guardado (validación en fronteras, FR-022 de `001`).
- **FR-007**: El sistema NO MUST permitir editar el correo electrónico: la edición lo muestra siempre deshabilitado/solo lectura y los endpoints de edición de esta feature MUST ignorar (o rechazar, decisión de tasks) cualquier valor de correo distinto al de la sesión; ningún flujo de cambio de correo forma parte de esta feature.
- **FR-008**: El sistema MUST proporcionar el botón "Cancelar cambios", que MUST abandonar la edición y regresar a la visualización del perfil sin guardar ningún cambio; al abandonar la pantalla sin confirmar, no MUST persistirse nada (comportamiento análogo a FR-020 de `001`). El botón "Volver atrás" ya no existe: su rol de regreso sin guardar queda cubierto por "Cancelar cambios".
- **FR-009**: Al guardar datos válidos, el sistema MUST persistir la actualización vía API nueva (`PUT /api/users/me` y los endpoints de avatar/contraseña de esta feature, ver contracts/users-api.md) sobre el usuario de la sesión (principal), MUST regresar automáticamente a la pantalla de visualización del perfil de `002` y MUST mostrar allí, en un snackbar, el mensaje "Los cambios se guardaron correctamente" una única vez (aviso one-shot: no MUST reaparecer al refrescar la página ni en ingresos posteriores a la visualización sin un nuevo guardado).
- **FR-010**: Con error de validación (cliente o servidor), el sistema MUST informar el motivo claro por campo (mensajes en español, formato ProblemDetail RFC 7807 con `properties.fields` de `001`), MUST resaltar los campos afectados en menos de 2 segundos, MUST conservar los datos ya escritos y MUST habilitar nuevamente el botón de guardado para permitir corregir.
- **FR-011**: El sistema MUST administrar el estado de carga y NO MUST permitir doble procesamiento: botón deshabilitado durante el envío; ante cualquier error (validación, red, servidor), botón habilitado nuevamente de inmediato (FR-015/FR-017 de `001`). Además, "Guardar cambios" MUST presentarse deshabilitado (estado visual gris, sin interacción) mientras no existan modificaciones pendientes de confirmar —al menos un campo del formulario distinto de la precarga, monedas que difieran de las iniciales (comparadas como conjunto, sin importar el orden) o un avatar pendiente de subir/eliminar—, y MUST habilitarse apenas el usuario introduzca alguna de estas modificaciones (si las revierte todas, vuelve a deshabilitarse); "Cancelar cambios" MUST estar siempre habilitado.
- **FR-012**: Tras un guardado exitoso, los datos actualizados MUST ser los que muestren la visualización del perfil (pantalla de `002`) y la propia edición en un ingreso posterior (la edición siempre lee datos frescos al ingresar).
- **FR-013**: El sistema MUST ofrecer el cambio de contraseña que exige (a) la contraseña actual (acreditación 1:1 contra la guardada) y (b) la nueva con la política de `001` (8–64, ≥1 mayúscula, ≥1 minúscula, ≥1 dígito, ≥1 carácter especial), más el campo de confirmación que debe coincidir. La sesión activa MUST persistir tras el cambio.
- **FR-014**: Con la contraseña actual incorrecta, el sistema MUST rechazar el cambio de contraseña con el mensaje "La contraseña actual no es correcta" y no MUST modificar la credencial.
- **FR-015**: El sistema MUST permitir incorporar un avatar nuevo al usuario desde la edición con las reglas de FR-010 de `001` (JPG, JPEG o PNG, ≤ 2 MB, validación de magic bytes en el servidor) y el patrón de reemplazo: la imagen nueva sustituye a la vigente. La elección del archivo se retiene como cambio **pendiente** (validación en vivo de formato/tamaño en el cliente, sin contactar al backend) y solo se aplica —vía `PUT` del avatar— al presionar "Guardar cambios" (FR-009); "Cancelar cambios" (FR-008) la descarta sin efecto.
- **FR-016**: El sistema MUST permitir eliminar el avatar propio del usuario desde la edición; al eliminar, la cuenta vuelve al estado "sin avatar propio" y la visualización muestra el avatar predeterminado (`hasCustomAvatar=false`, FR-005 de `002`). Si hay avatar propio, el botón "Eliminar avatar" está presente; al presionarlo se marca la eliminación como cambio **pendiente** (el preview muestra el default y el botón queda deshabilitado, sin contactar al backend ni regresar a un estado "deshacer"); se confirma vía `DELETE` del avatar al presionar "Guardar cambios" (FR-009). "Cancelar cambios" (FR-008) es la única vía de anular la marca (descarta también las demás ediciones pendientes). Si no hay avatar propio, el botón no se presenta.
- **FR-017**: La edición MUST operar siempre sobre el usuario de la sesión (principal): los endpoints de esta feature MUST NOT aceptar un identificador de usuario distinto; en ningún caso la solicitud puede alterar datos de otro usuario.
- **FR-018**: Sin sesión válida (cookie ausente/inválida/expirada) o con cuenta no verificada, el sistema MUST aplicar los gates existentes (redirección al flujo de acceso / bloqueo de recursos protegidos de `003`) sin exponer la edición ni persistir nada.
- **FR-019**: Ante error de red o del servidor durante el guardado, el sistema MUST mostrar el mensaje genérico correspondiente de `001` ("No se pudo completar la actualización. Intenta nuevamente"), MUST conservar los datos escritos y MUST NO aplicar cambios parciales.
- **FR-020**: El backend de esta feature MUST exponer los endpoints nuevos de edición documentados en [contracts/users-api.md](contracts/users-api.md) reutilizando el esquema y los validadores de `001` (sin migraciones nuevas; ver [data-model.md](data-model.md)); la API pública de mutación de esta feature MUST NOT exponer ninguna vía para cambiar el correo.

### Key Entities *(include if feature involves data)*

No se crean entidades ni tablas nuevas: la feature MUTA las entidades de `001-user-registration` ([data-model.md](../001-user-registration/data-model.md)); el esquema y las migraciones de `001` (V1) quedan sin cambios.

- **Cuenta de Usuario** (mutación): `first_name` (obligatorio) y `last_name` (opcional) reciben nuevos valores con las reglas de `001`; el hash de la credencial se sustituye solo cuando el cambio de contraseña se concreta (FR-013/FR-014); el correo y la aceptación legal son invariantes de esta feature.
- **CBU/CVU y Preferencia de Monedas** (mutación): `cbu` y `user_currencies` se actualizan según FR-004/FR-005; vacíos se representan como en `001` (sin valor ⇒ "No informado" / ARS en la visualización).
- **Avatar** (upsert/delete): el reemplazo (FR-015) sustituye el binario y tipo MIME de la fila en `user_avatars` (o crea la fila si no existe); la eliminación (FR-016) quita la fila. La visualización de `002` (`GET /api/users/{id}/avatar`) sirve la imagen vigente.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Al ingresar a la edición, los datos actuales se precargan con lo último guardado del servidor en menos de 2 segundos en condiciones normales de red (y ante fallo, mensaje en menos de 2 segundos, patrón SC-004 de `002`).
- **SC-002**: El 100 % de los guardados exitosos persisten los cambios y los muestran en la visualización del perfil al reconsultar (criterio de aceptación "los cambios realizados deben visualizarse al volver a consultar el perfil").
- **SC-003**: El 100 % de los intentos de cambiar el correo electrónico fracasan sin modificar el dato persistido, sea por la UI deshabilitada o por la API que lo ignora/rechaza.
- **SC-004**: El 100 % de los envíos con errores de validación informan el motivo por campo en menos de 2 segundos, conservan los datos escritos y habilitan el botón para corregir.
- **SC-005**: La acreditación de la contraseña actual es requisito del 100 % de los cambios de contraseña concretados; 0 cambios se procesan con la contraseña actual incorrecta.
- **SC-006**: El 100 % de los abandonos/cancelaciones de la edición sin confirmar conlleva 0 cambios persistidos.
- **SC-007**: El 100 % de las mutaciones aprobadas operan sobre el usuario de la sesión; ningún guardado de la edición altera datos de otro usuario.
- **SC-008**: El reemplazo y la eliminación del avatar dejan el estado consistente con la visualización (imagen vigente servida; eliminación ⇒ default) en el 100 % de los casos.

## Assumptions

- Se reutiliza el sistema de autenticación de `001`/`003` (cookie `amano_auth`, endpoints `GET /api/users/me` y avatar, gates de sesión y de cuenta verificada); el cambio de contraseña no revoca sesiones activas (solo en el próximo inicio de sesión se usa la nueva).
- Las reglas de campo (longitud/recortes/formatos/política de contraseña/validación en fronteras) son las de `001`; esta especificación no las modifica ni las repite en detalle.
- No hay concurrencia de edición simultánea de la misma cuenta desde dos dispositivos en esta feature: el último guardado confirmado prevalece por campo (read-modify-write del cliente); la resolución de conflictos queda fuera de alcance.
- La carga de avatares reutiliza las reglas de formato/magic bytes/2 MB de `001`; la imagen enviada desde la edición reemplaza la fila existente (no se conserva historial de imágenes anteriores).
- El dashboard y la pantalla de visualización de `002` siguen provistos; esta feature solo requiere que la edición sea alcanzable desde ahí.
- La recuperación de contraseña (canales de correo), el cambio de dirección de correo, el 2FA y la autenticación por proveedores externos quedan fuera de alcance.
- Mensajes de la interfaz en español, coherentes con `001`–`002`.
- No hay datos en producción que migrar: los cambios de esta feature no requieren transformaciones sobre datos existentes.
