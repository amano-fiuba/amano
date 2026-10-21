# Feature Specification: Recuperación de contraseña

**Feature Branch**: `feat/recuperar-contra`

**Created**: 2026-10-21

**Status**: Draft

**Input**: User description: "Recuperación de contraseña: desde el login, la persona pide un enlace por correo sin que la aplicación revele si el correo tiene cuenta; el enlace (de un solo uso y con vencimiento) abre una pantalla donde elige una nueva contraseña, que reemplaza a la anterior, y luego inicia sesión con ella."

## Relación con otras especificaciones

- **Reutiliza de 001 (Registro de usuario)**: la normalización del correo (recorte de espacios y minúsculas), la política de contraseñas (8–64 caracteres con mayúscula, minúscula, número y símbolo) con sus mensajes exactos y el almacenamiento de la contraseña como hash BCrypt.
- **Reutiliza de 003 (Verificación de correo electrónico)**: el envío de correo asíncrono por SMTP (configuración `spring.mail.*` y `APP_BASE_URL`), el criterio de tokens de un solo uso guardados únicamente como hash SHA-256 y la respuesta neutra que no revela la existencia de cuentas. El estado "verificada" de 003 condiciona quién puede recibir el enlace.
- **Extiende 004 (Inicio de sesión)**: la pantalla `/login` gana el enlace "¿Olvidaste tu contraseña?" y un aviso de confirmación tras restablecer la contraseña. El login en sí no cambia: la nueva contraseña se usa con el mismo `POST /api/auth/login`.
- **Comparte con 005 (Edición de perfil)**: la política de contraseñas centralizada en `PasswordPolicy`, que 005 introduce en su rama y que esta feature incorpora con contenido idéntico para no divergir (ver plan.md).

Los identificadores FR-### y SC-### de esta especificación son propios de 006 y no coinciden necesariamente en número con los de 001, 003, 004 ni 005.

## Clarifications

### Session 2026-10-21

- Q: ¿Quién recibe el correo de recuperación: cualquier cuenta existente o solo las verificadas? → A: Solo las cuentas **verificadas**. Una cuenta pendiente no puede iniciar sesión aunque cambie la contraseña (FR-014 de 003); su camino es el reenvío de verificación de 003. La respuesta es idéntica en todos los casos.
- Q: ¿Cuánto dura el enlace de recuperación? → A: **1 hora**, configurable (`password-reset.token-expiration`). Es más corto que las 24 h de la verificación de 003 porque el enlace otorga control sobre una cuenta ya activa.
- Q: Si se pide un enlace nuevo mientras otro sigue vigente, ¿siguen valiendo los dos? → A: No. Solo vale el **último**: emitir uno nuevo invalida los anteriores.
- Q: Si el formulario de nueva contraseña tiene errores de campo, ¿el enlace se consume? → A: No. El enlace solo se consume con un cambio exitoso; ante errores de campo sigue siendo válido para reintentar.
- Q: ¿Qué pasa con el token una vez usado? → A: Se **eliminan** de la base todos los tokens de recuperación de la cuenta (el canjeado y cualquier otro ya invalidado), de modo que no quede ninguno reutilizable.
- Q: Tras cambiar la contraseña, ¿se inicia sesión automáticamente? → A: No. Se redirige a `/login` con un aviso de confirmación y la persona inicia sesión con la nueva contraseña.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Pedir un enlace de recuperación (Priority: P1)

Una persona que olvidó su contraseña pulsa "¿Olvidaste tu contraseña?" en `/login`, llega a `/forgot-password`, escribe su correo y pulsa "Enviar enlace". La pantalla muestra "Revisa tu bandeja de entrada" junto con un mensaje neutro, haya o no una cuenta con ese correo. Si la cuenta existe y está verificada, recibe un correo con un enlace válido por 1 hora.

**Why this priority**: es la puerta de entrada del flujo: sin el enlace no hay forma de recuperar la cuenta, y la respuesta neutra es la condición de seguridad que protege la privacidad de los correos registrados.

**Independent Test**: con una cuenta verificada, pedir el enlace desde `/forgot-password` y comprobar que llega un correo con un enlace `/reset-password?token=...`; repetir con un correo inexistente y comprobar que la pantalla y la respuesta son idénticas y que no se envía nada.

**Acceptance Scenarios**:

1. **Given** una cuenta verificada con `ana@example.com`, **When** se solicita la recuperación con `"  Ana@Example.COM "`, **Then** la respuesta es el mensaje neutro y `ana@example.com` recibe un correo con el enlace `${APP_BASE_URL}/reset-password?token=<token>`, válido durante 1 hora.
2. **Given** un correo sin cuenta, **When** se solicita la recuperación, **Then** la respuesta es idéntica (mismo código y mismo cuerpo) y no se envía ningún correo.
3. **Given** una cuenta pendiente de verificación, **When** se solicita la recuperación, **Then** la respuesta es idéntica y no se envía ningún correo.
4. **Given** un enlace de recuperación vigente, **When** se pide uno nuevo, **Then** el anterior queda invalidado y solo el último es utilizable.
5. **Given** la pantalla de login, **When** se pulsa "¿Olvidaste tu contraseña?", **Then** se navega a `/forgot-password`.
6. **Given** el formulario de `/forgot-password` en envío, **When** se espera la respuesta, **Then** el botón queda deshabilitado con el texto "Enviando…" y, al llegar la respuesta, se reemplaza el formulario por "Revisa tu bandeja de entrada" y el mensaje neutro.

---

### User Story 2 - Elegir una nueva contraseña con el enlace (Priority: P1)

Al abrir el enlace del correo, la persona llega a `/reset-password?token=...`, escribe la nueva contraseña dos veces y pulsa "Guardar contraseña". Si el enlace es válido y la contraseña cumple la política, la contraseña se reemplaza, el enlace deja de servir y la persona llega a `/login` con el aviso "Tu contraseña fue actualizada. Ya puedes iniciar sesión". Si el enlace no es válido o venció, la pantalla lo informa y ofrece pedir uno nuevo.

**Why this priority**: completa el valor de la feature: sin esta pantalla el enlace del correo no sirve. Comparte prioridad con US1 porque ninguna de las dos entrega valor sola.

**Independent Test**: con un token obtenido por US1, cambiar la contraseña en `/reset-password` y verificar que el login funciona con la nueva, falla con la anterior y que el mismo enlace ya no puede reutilizarse.

**Acceptance Scenarios**:

1. **Given** un enlace vigente, **When** se ingresa una contraseña que cumple la política de 001 y se confirma igual, **Then** la contraseña se reemplaza, se eliminan todos los tokens de recuperación de la cuenta y se navega a `/login` con el aviso "Tu contraseña fue actualizada. Ya puedes iniciar sesión".
2. **Given** la contraseña ya cambiada, **When** se inicia sesión, **Then** la nueva contraseña funciona y la anterior recibe el 401 genérico de 004.
3. **Given** un enlace ya canjeado, **When** se vuelve a usar, **Then** la respuesta es "El enlace para restablecer la contraseña no es válido" y la contraseña no cambia.
4. **Given** un enlace reemplazado por otro más nuevo (US1 escenario 4), **When** se usa, **Then** la respuesta es la misma de enlace no válido.
5. **Given** un enlace vencido, **When** se usa, **Then** la respuesta es "El enlace para restablecer la contraseña ha expirado", el token se elimina, la contraseña no cambia y la pantalla ofrece "Solicitar un nuevo enlace".
6. **Given** un enlace vigente, **When** la contraseña no cumple la política o la confirmación no coincide, **Then** se informa el error en el campo correspondiente y el enlace **sigue siendo válido** para reintentar.
7. **Given** una URL sin parámetro `token`, **When** se abre `/reset-password`, **Then** la pantalla muestra el enlace como no válido sin contactar al servidor.
8. **Given** el formulario en envío, **When** se espera la respuesta, **Then** el botón queda deshabilitado con el texto "Guardando…".

---

### Edge Cases

- **Correo no normalizado**: `"  Ana@Example.COM "` corresponde a la cuenta `ana@example.com` (cliente y servidor normalizan).
- **Cuerpo ausente, correo vacío o con formato inválido en la solicitud**: el servidor responde el mismo mensaje neutro (no hay error que distinga casos); el cliente valida el formato antes de enviar con "Ingresá un correo electrónico válido".
- **Falla del servidor de correo**: se registra una advertencia sin el token ni credenciales y la respuesta sigue siendo la neutra.
- **Tiempo de respuesta**: el envío del correo es asíncrono, de modo que la respuesta no tarda más cuando el correo existe.
- **Token ausente, manipulado o inventado en el canje**: mismo error de enlace no válido; no revela si la cuenta existe.
- **Orden de validación en el canje**: primero el enlace (no válido o vencido) y después los campos. Un enlace muerto nunca le pide a la persona que corrija la contraseña.
- **Error de red o servidor caído**: en `/forgot-password`, "No se pudo enviar la solicitud. Intenta nuevamente"; en `/reset-password`, "No se pudo actualizar la contraseña. Intenta nuevamente". En ambos casos el formulario se conserva y el botón se rehabilita.
- **Doble envío**: mientras la solicitud está en curso el botón permanece deshabilitado.
- **Sesiones abiertas en otros dispositivos**: siguen vigentes hasta su vencimiento tras el cambio (fuera de alcance, ver Assumptions).

## Requirements *(mandatory)*

### Functional Requirements

**US1 — Solicitud del enlace**

- **FR-001**: El sistema MUST exponer una operación pública (sin sesión) para solicitar el enlace de recuperación que reciba el correo electrónico; la pantalla de login MUST enlazar a la pantalla de solicitud `/forgot-password`.
- **FR-002**: La operación de solicitud MUST responder siempre con el mismo resultado exitoso y el mensaje "Si el correo existe, recibirás un enlace para restablecer tu contraseña", byte a byte idéntico exista o no la cuenta, esté o no verificada, e incluso con correo vacío o ausente.
- **FR-003**: El sistema MUST normalizar el correo (recorte de espacios y minúsculas) en cliente y servidor antes de buscar la cuenta, igual que en 001.
- **FR-004**: Solo una cuenta existente y **verificada** (003) MUST recibir el correo; para cuentas inexistentes o pendientes MUST NOT emitirse token ni enviarse correo.
- **FR-005**: El token MUST generarse con un generador aleatorio criptográficamente seguro (256 bits, codificación Base64URL sin relleno) y MUST persistirse únicamente su hash SHA-256; el valor en claro solo viaja dentro del enlace del correo.
- **FR-006**: El token MUST vencer 1 hora después de emitido; la duración MUST ser configurable.
- **FR-007**: Cada cuenta MUST tener como máximo un token vigente: emitir uno nuevo MUST invalidar los anteriores en la misma operación.
- **FR-008**: El correo MUST enviarse de forma asíncrona reutilizando la configuración SMTP existente; su falla MUST registrarse sin token ni credenciales y MUST NOT alterar la respuesta.
- **FR-009**: Las pruebas automatizadas MUST usar un servidor SMTP en proceso (GreenMail) y MUST NOT contactar un proveedor de correo real.

**US2 — Canje del enlace y nueva contraseña**

- **FR-010**: El sistema MUST exponer una operación pública (sin sesión) para canjear el enlace que reciba el token, la nueva contraseña y su confirmación; la pantalla `/reset-password` MUST leer el token del parámetro `token` de la URL.
- **FR-011**: El token recibido MUST hashearse y buscarse por su hash; es válido solo si existe, no fue invalidado (`used_at` vacío) y no venció.
- **FR-012**: Un token inexistente, manipulado, ya canjeado o reemplazado MUST rechazarse con "El enlace para restablecer la contraseña no es válido", sin revelar si la cuenta existe.
- **FR-013**: Un token vencido MUST rechazarse con "El enlace para restablecer la contraseña ha expirado" y MUST eliminarse de la base.
- **FR-014**: La nueva contraseña MUST cumplir la política de 001 (mensajes exactos de 001) y MUST coincidir con la confirmación ("Las contraseñas no coinciden"); si no, el rechazo MUST identificar cada campo afectado y el token MUST NOT consumirse. La validación del token MUST preceder a la de los campos.
- **FR-015**: En el éxito, la contraseña MUST guardarse como hash BCrypt y MUST eliminarse **todos** los tokens de recuperación de la cuenta en la misma transacción; la respuesta MUST incluir "Tu contraseña fue actualizada. Ya puedes iniciar sesión" y MUST NOT establecer sesión.
- **FR-016**: La interfaz MUST validar la política y la coincidencia antes de enviar, MUST deshabilitar el botón durante el envío, MUST mostrar los errores de enlace no válido o vencido con la opción "Solicitar un nuevo enlace", y en el éxito MUST navegar a `/login`, donde se muestra el aviso de confirmación.

### Key Entities *(include if feature involves data)*

- **Token de recuperación de contraseña**: credencial temporal de un solo uso asociada a una cuenta; se guarda solo su hash, tiene fecha de vencimiento y una marca de invalidación. Como máximo uno vigente por cuenta; se elimina al canjearse con éxito o al detectarse vencido.
- **Cuenta de usuario** (existente, 001/003): su correo normalizado identifica a quién enviar el enlace, su estado de verificación decide si se envía y su hash de contraseña es lo que el canje reemplaza.
- **Solicitud de recuperación** (no persistida): el correo ingresado; nunca se registra si correspondía a una cuenta.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: El 100 % de las solicitudes para una cuenta verificada genera un correo con un enlace utilizable, recibido en menos de 1 minuto con el servidor de correo operativo.
- **SC-002**: Una prueba ciega no puede distinguir, ni por el contenido ni por el código de la respuesta, si un correo tiene cuenta, está verificado o no existe: el 100 % de las respuestas de solicitud son idénticas.
- **SC-003**: Una persona completa el flujo (pedir el enlace, abrirlo, elegir contraseña e iniciar sesión) en menos de 3 minutos sin ayuda.
- **SC-004**: El 100 % de los enlaces canjeados, reemplazados o vencidos es rechazado; ningún enlace permite cambiar la contraseña dos veces.
- **SC-005**: Tras un cambio exitoso, el 100 % de los intentos de login con la contraseña anterior es rechazado y el 100 % con la nueva es aceptado.
- **SC-006**: El 100 % de los envíos con una contraseña que no cumple la política o con confirmación distinta muestra el error en el campo afectado y permite reintentar con el mismo enlace.
- **SC-007**: Ninguna base de datos ni registro (log) contiene un token de recuperación en claro.

## Assumptions

- Esta especificación documenta el comportamiento implementado en la rama `feat/recuperar-contra`; cualquier divergencia entre este documento y el código debe resolverse como defecto o como cambio de alcance explícito, nunca por inferencia.
- Los textos mostrados al usuario se escriben en español, en línea con el resto de la interfaz, y se usan de forma idéntica en cliente y servidor.
- La cuenta de correo remitente, el servidor SMTP y `APP_BASE_URL` son los configurados para 003; esta feature no los redefine.
- **Fuera de alcance**:
  - Límite de frecuencia de solicitudes por dirección (anti-spam, análogo al de 5 minutos del reenvío de 003): hoy cada solicitud para una cuenta verificada envía un correo.
  - Cerrar las sesiones abiertas en otros dispositivos al cambiar la contraseña: la sesión de 001 es un JWT sin estado en el servidor y no tiene mecanismo de revocación.
  - Notificar por correo que la contraseña fue cambiada.
  - Recuperación por otros medios (SMS, preguntas de seguridad) y recuperación de cuentas pendientes de verificación.
- La cobertura mínima del 85 % (backend y frontend) exigida por la constitución fue verificada con `mvn verify` (JaCoCo) y `npm run test:coverage` (Vitest).
