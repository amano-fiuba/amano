# Research: Recuperación de contraseña (006)

**Fecha**: 2026-10-21 | **Rama**: `feat/recuperar-contra` | **Spec**: [spec.md](./spec.md)

**Propósito**: documentar las decisiones tomadas en la implementación. No quedaron `NEEDS CLARIFICATION` en el Technical Context del plan: el pedido fijó endpoints, pantallas y flujo, y los puntos abiertos se resolvieron con los criterios ya establecidos por 001/003/004 (registrados en las Clarifications de la spec).

## D1 — Token: aleatorio criptográfico, guardado como hash SHA-256

- **Decisión**: 32 bytes de `SecureRandom` en Base64URL sin relleno (43 caracteres); en `password_reset_tokens.token_hash` se guarda solo `sha256_hex(token)`. El canje busca por hash.
- **Rationale**: mismo criterio que D2 de 003. Con 256 bits de entropía no hace falta un hash lento: SHA-256 alcanza para que una filtración de la base no permita canjear enlaces, y permite búsqueda directa por índice único. El valor en claro solo viaja en el correo.
- **Alternativas consideradas**: guardar el token en claro (lo sugería el pedido original como "string del token"; descartado porque una lectura de la base daría control de cualquier cuenta con enlace vigente); BCrypt del token (impide la búsqueda por índice y no aporta frente a 256 bits aleatorios); JWT firmado como enlace (no permite invalidar al emitir uno nuevo ni al canjear sin estado adicional).

## D2 — Vencimiento de 1 hora, configurable

- **Decisión**: `password-reset.token-expiration=3600` (segundos) en `application.properties`, leído por el record `PasswordResetProperties` (`@ConfigurationProperties`, `@DurationUnit(SECONDS)`, validación de duración positiva), registrado en `BackendApplication` junto a `VerificationProperties`.
- **Rationale**: un enlace de recuperación entrega control de una cuenta ya activa, por lo que conviene una ventana corta; 1 hora es el valor habitual y deja margen para demoras del correo. El patrón de propiedades replica el de 003 y permite acortarlo en tests sin tocar lógica.
- **Alternativas consideradas**: 24 h como la verificación de 003 (ventana de exposición innecesariamente larga); 15 minutos (demasiado ajustado ante demoras de Gmail).

## D3 — Respuesta neutra siempre 200, correo solo a cuentas verificadas

- **Decisión**: `POST /api/auth/forgot-password` responde siempre `200 {"message": "Si el correo existe, recibirás un enlace para restablecer tu contraseña"}`, incluso con cuerpo vacío o correo inválido. Solo una cuenta existente y verificada recibe token y correo.
- **Rationale**: lo exige el pedido y replica el contrato del reenvío de 003 (SC-005 de 003). Una cuenta pendiente no podría iniciar sesión aunque cambiara la contraseña (FR-014 de 003), así que enviarle el enlace confundiría; su camino es el reenvío de verificación.
- **Alternativas consideradas**: 400 para correo con formato inválido (permitiría distinguir casos y no aporta: el cliente ya valida el formato); enviar también a cuentas pendientes (no sirve para entrar).

## D4 — Envío asíncrono con un servicio de correo propio

- **Decisión**: `PasswordResetMailService.sendPasswordResetEmail` es `@Async`, usa el `JavaMailSender` ya configurado (`spring.mail.*`, `APP_BASE_URL`), agrega `From` solo si hay `MAIL_USERNAME` y captura cualquier falla registrando un warning sin token ni credenciales. Asunto: "Restablece tu contraseña - A Mano".
- **Rationale**: mismo patrón que `VerificationMailService` (D1 de 003). Además del desacople con el SMTP, el envío asíncrono hace que la respuesta no tarde más cuando el correo existe, lo que evita distinguir casos por tiempo.
- **Alternativas consideradas**: reutilizar `VerificationMailService` con otro método (mezclaría dos features en una clase y su asunto/plantilla); envío síncrono (filtra existencia por latencia y bloquea la respuesta ante un SMTP lento).

## D5 — Un solo token vigente por cuenta

- **Decisión**: al emitir, los tokens con `used_at IS NULL` de la cuenta se marcan con `used_at = now()` en la misma transacción. Un token marcado se rechaza en el canje como no válido.
- **Rationale**: igual que FR-009 de 003: si la persona pide varios enlaces, solo el último funciona, lo que reduce la superficie si un correo viejo queda expuesto.
- **Alternativas consideradas**: varios tokens vigentes en paralelo (más superficie sin beneficio); borrar los anteriores al emitir (se conservan marcados para que el canje de un enlace viejo responda de forma consistente hasta el próximo cambio exitoso, que los elimina a todos).

## D6 — Sin límite de frecuencia en esta entrega

- **Decisión**: no se implementa un cooldown por dirección; cada solicitud para una cuenta verificada envía un correo.
- **Rationale**: el pedido no lo incluía y la feature no requiere tabla adicional. Queda documentado como fuera de alcance en la spec y como riesgo abajo.
- **Alternativas consideradas**: reutilizar `email_resend_limits` de 003 (mezcla dos límites con semánticas distintas en una sola clave); tabla propia por hash de correo, análoga a D4 de 003 (candidata para una historia futura).

## D7 — Canje: primero el token, después los campos; el token sobrevive a errores de campo

- **Decisión**: `resetPassword` valida en este orden: (1) token inexistente o invalidado → `PasswordResetTokenInvalidException` (400); (2) token vencido → se elimina y `PasswordResetTokenExpiredException` (410), con `@Transactional(noRollbackFor = PasswordResetTokenExpiredException.class)` para que el borrado se confirme; (3) política y confirmación → `FieldValidationException` (400 con `properties.fields`), sin tocar el token; (4) éxito.
- **Rationale**: con un enlace muerto no tiene sentido pedir que se corrija la contraseña, y un error de tipeo no debería obligar a pedir otro correo. El `noRollbackFor` replica la estrategia del canje vencido de 003.
- **Alternativas consideradas**: validar campos primero (la persona corregiría la contraseña para enterarse después de que el enlace venció); consumir el token ante cualquier intento (castiga errores de tipeo).

## D8 — Eliminar los tokens al canjear, en lugar de marcarlos

- **Decisión**: el canje exitoso ejecuta `deleteByUserId(userId)`: borra el token usado y cualquier otro de la cuenta (incluidos los invalidados por D5). El vencido también se borra (D7).
- **Rationale**: lo pidió el equipo ("eliminar el token de la BD para que no pueda reusarse"). Borrar todos, y no solo el usado, garantiza que no quede ningún token de esa cuenta; además mantiene la tabla chica sin una tarea de limpieza. La consecuencia es que un enlace ya canjeado responde "no válido" (400) y no un 409 "ya utilizado" como en 003: sin la fila no hay forma de distinguirlo, lo que además no revela nada.
- **Alternativas consideradas**: marcar `used_at` como en 003 (conserva historial pero deja filas que nadie lee y requeriría limpieza); borrar solo el token usado (dejaría filas invalidadas huérfanas).

## D9 — Política de contraseñas compartida con 005 (`PasswordPolicy`)

- **Decisión**: la validación usa `com.amano.backend.user.PasswordPolicy.validationError(password)`, copiada **byte a byte** del archivo que introduce la rama `feat/edit-profile` (005). `RegistrationService` no se modifica en esta rama.
- **Rationale**: evita una tercera copia de la regla (registro, edición de perfil, recuperación) y evita conflictos de merge: si ambas ramas agregan el mismo archivo con contenido idéntico, Git no marca conflicto. El refactor de `RegistrationService` para usarla pertenece a 005 (principio I: sin refactorizaciones ajenas al alcance).
- **Alternativas consideradas**: duplicar la regla como método privado del servicio (tercera copia que puede divergir); refactorizar `RegistrationService` aquí (pisaría el cambio de 005 y generaría conflicto).

## D10 — Sin sesión automática; aviso en `/login`

- **Decisión**: el 200 del canje solo trae `{"message": ...}`, sin `Set-Cookie`. El frontend muestra el mensaje en el snackbar global y navega a `/login`.
- **Rationale**: confirmar la nueva contraseña escribiéndola una vez más es la práctica habitual y no requiere que el canje conozca el mecanismo de sesión de 001. El paso por estado del router replica el patrón de `state.from` (D6 de 004) y `verifiedWelcome` (003).
- **Alternativas consideradas**: iniciar sesión directamente como el canje de verificación de 003 (acopla el canje a `AuthCookie`/`JwtService` y abre sesión en el dispositivo donde se abrió el correo, que puede no ser el de la persona); pantalla de éxito propia con temporizador de redirección (más estado y tests con temporizadores, sin beneficio).

## D11 — Frontend: páginas autónomas con `react-hook-form`

- **Decisión**: `ForgotPasswordPage` y `ResetPasswordPage` usan `react-hook-form` (`mode: 'onBlur'`), `TextField` y las validaciones de `registerValidation.js` (`validateEmail`, `normalizeEmail`, `validatePassword`, `validateConfirmPassword`). La confirmación se valida con `validate: (value, formValues) => ...` y `deps: ['confirmPassword']` en la contraseña, sin `watch`. `authClient.js` expone `forgotPassword` y `resetPassword` con la forma de resultado de las funciones existentes.
- **Rationale**: reutiliza las piezas de 001/004 (principio I). Evitar `watch` no suma otra advertencia `react-hooks/incompatible-library` como la que ya tiene `RegisterForm`.
- **Alternativas consideradas**: componente de formulario separado como `LoginForm` (cada pantalla tiene un solo formulario sin reutilización, no lo justifica); `watch` + `useEffect` como `RegisterForm` (agrega la advertencia de lint).

## D12 — Estrategia de pruebas

- **Decisión**: pirámide del proyecto. Unitarios sin base (`PasswordResetServiceTest` con repositorios, correo y `PasswordEncoder` mockeados y SHA-256 independiente del de producción; `PasswordResetMailServiceTest` con `JavaMailSender` mockeado), integración con Testcontainers PostgreSQL y GreenMail en proceso (`ForgotPasswordIT`, `ResetPasswordIT`, que obtiene el token como lo haría una persona: leyendo el enlace del correo), visibilidad pública en `SecurityConfigTest`, y Vitest en frontend (`ForgotPasswordPage.test.jsx`, `ResetPasswordPage.test.jsx`, `authClient.test.js`, `LoginPage.test.jsx`).
- **Rationale**: principio V de la constitución (cobertura ≥ 85 %) y regla del equipo de no usar Gmail real en tests (D9 de 003). Los tests se escribieron antes de la implementación de cada paso.
- **Alternativas consideradas**: tests E2E con navegador (no existen en el proyecto).

## Riesgos y mitigaciones

| Riesgo | Mitigación actual | Pendiente |
|--------|-------------------|-----------|
| Spam: alguien pide enlaces repetidamente para el correo de un tercero | Solo cuentas verificadas reciben correo; cada pedido invalida el anterior | Límite de frecuencia por dirección (D6) |
| Robo de sesión previo al cambio de contraseña | El cambio impide nuevos logins con la contraseña vieja | Revocación de JWT (p. ej. versión de credenciales en `users`) |
| Enlace filtrado (correo reenviado, historial) | 1 hora de validez, un solo uso, solo el último vale, hash en base | — |
| Divergencia de `PasswordPolicy` entre 005 y 006 antes del merge | Contenido idéntico al de 005 | Si 005 lo cambia antes de mergear, resolver el conflicto conservando una sola versión |
| Token en logs | Ningún log incluye el token ni el enlace (solo el destinatario) | — |
