# Tasks: Verificación de correo electrónico

**Input**: Design documents from `/specs/003-email-verification/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/email-verification-api.md, quickstart.md

**Tests**: Incluidos porque la constitución (principio V) exige pruebas automatizadas para toda funcionalidad nueva o modificada, y research.md §5/D9/D10 define la estrategia (GreenMail para correo, nunca Gmail real; actualización de los tests de 001 afectados por el reemplazo de FR-016/SC-002).

**Organization**: Tareas agrupadas por historia de usuario para permitir implementación y prueba independientes.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Paralelizable (archivos distintos, sin dependencias entre tareas incompletas)
- **[Story]**: Historia de usuario (US1–US4); sin etiqueta en Setup, Foundational y Polish
- Cada tarea incluye la ruta exacta del archivo y la trazabilidad a FR/SC/D de research.md

## Phase 1: Setup (Infraestructura compartida)

**Purpose**: Dependencias y configuración de entorno del correo; nada de esto toca lógica de historias.

- [X] T001 Agregar `spring-boot-starter-mail` (scope compile) y, en scope test, `com.icegreen:greenmail` y `com.icegreen:greenmail-junit5` versión 2.1.14 a `backend/pom.xml` (research.md §1, D9)
- [X] T002 [P] Agregar placeholders de correo a `.env.example`: `MAIL_HOST`, `MAIL_PORT`, `MAIL_USERNAME`, `MAIL_PASSWORD` (contraseña de aplicación de Gmail; nota de que nunca se usa Gmail real en tests) y `APP_BASE_URL` (base del enlace del correo, con ejemplos dev/prod) — D8
- [X] T003 [P] Propagar las 5 variables de correo al servicio `backend` en `compose.yaml` (dev) y `compose.prod.yaml` (prod), siguiendo el patrón existente de `JWT_SECRET`/`JWT_EXPIRATION` — D8
- [X] T004 [P] Agregar a `backend/src/main/resources/application.properties`: `spring.mail.host/port/username/password` desde las env vars, `app.base-url=${APP_BASE_URL}` y los plazos de `VerificationProperties` con defaults (token 24 h = 86400 s, límite de reenvío 300 s, caducidad de cuenta 3 días, cron horario de limpieza) — D1, D8, contracts §"Plazos"

---

## Phase 2: Foundational (Prerrequisitos bloqueantes)

**Purpose**: Esquema y entidades base que TODAS las historias necesitan. La migración única V2 modela las tres piezas de data-model.md aunque sus consumidores lleguen en historias distintas (la tabla de límites queda huérfana hasta US4; `ddl-auto=validate` solo valida entidades existentes).

**⚠️ CRITICAL**: Ninguna historia puede empezar hasta completar esta fase.

- [X] T005 Crear `backend/src/main/resources/db/migration/V2__email_verification.sql` según data-model.md: `ALTER TABLE users ADD COLUMN email_verified_at TIMESTAMPTZ NULL;`, tabla `email_verification_tokens` (`id` IDENTITY PK, `user_id BIGINT NOT NULL` FK→`users(id)` ON DELETE CASCADE, `token_hash VARCHAR(64) NOT NULL UNIQUE`, `created_at TIMESTAMPTZ NOT NULL DEFAULT now()`, `expires_at TIMESTAMPTZ NOT NULL`, `used_at TIMESTAMPTZ NULL`, CHECK `expires_at > created_at`) y tabla `email_resend_limits` (`email_hash VARCHAR(64) NOT NULL` PK, `last_resend_at TIMESTAMPTZ NOT NULL`). **Sin DELETE** (regla del equipo) — data-model.md §Compatibilidad
- [X] T006 [P] Agregar campo `emailVerifiedAt` (`OffsetDateTime`, nullable, columna `email_verified_at`) a la entidad `backend/src/main/java/com/amano/backend/user/User.java` — FR-001, FR-004
- [X] T007 [P] Crear entidad `backend/src/main/java/com/amano/backend/verification/VerificationToken.java` (`user_id`, `token_hash`, `created_at`, `expires_at`, `used_at`; `@ManyToOne` LAZY hacia `User`) y repositorio `backend/src/main/java/com/amano/backend/verification/VerificationTokenRepository.java` (`JpaRepository` + `Optional<VerificationToken> findByTokenHash(String)` y `List<VerificationToken> findByUserIdAndUsedAtIsNull(Long userId)`) — data-model.md §email_verification_tokens
- [X] T008 [P] Crear `backend/src/main/java/com/amano/backend/verification/VerificationProperties.java` como `@ConfigurationProperties(prefix="verification")` record con `Duration tokenExpiration` (default 24 h), `Duration resendCooldown` (default 5 min), `Duration accountExpiration` (default 3 días), `String cronCleanup` y binding a las properties de T004 — D6, contracts §"Plazos"
- [X] T009 Agregar `@EnableAsync` y `@EnableScheduling` a `backend/src/main/java/com/amano/backend/BackendApplication.java` — D1, D6

**Checkpoint**: Esquema V2 aplicable, entidades validadas por Hibernate; las historias pueden comenzar.

---

## Phase 3: User Story 1 - Registro con cuenta no verificada y pantalla "Revisa tu correo" (Priority: P1) 🎯 MVP

**Goal**: El registro crea la cuenta "no verificada", no inicia sesión, redirige a `/check-email` y envía el correo con token de forma asíncrona (reemplaza FR-016/SC-002 de 001). Incluye el caso borde de re-registro sobre cuenta pendiente (FR-015, con envío inmediato mientras no exista límite — el límite se aplica en US4, FR-015/Q2).

**Independent Test**: Registrar con datos válidos → sin cookie, respuesta `{email}`, pantalla "Revisa tu correo" con el correo visible tras recarga, correo recibido en GreenMail con enlace de un solo uso; duplicado sobre verificada sigue dando 409.

### Tests for User Story 1 ⚠️ (primero, deben FALLAR)

- [X] T010 [P] [US1] Actualizar `backend/src/test/java/com/amano/backend/auth/RegistrationIT.java`: sin aserción de `Set-Cookie` en el 201, body ahora `{"email": "<normalizado>"}`, 409 solo para correo **verificada** (research.md §5) — contrato de contracts §"Cambio sobre contrato de 001"
- [X] T011 [P] [US1] Ampliar `backend/src/test/java/com/amano/backend/auth/RegistrationServiceTest.java`: mocks de `VerificationTokenRepository` y `VerificationMailService`; asertar que `register()` deja la cuenta con `emailVerifiedAt == null`, emite token (hash persistido, no el claro) y dispara el envío asíncrono; cubrir rama de re-registro sobre pendiente (reemplaza datos y contraseña, invalida token anterior) — FR-001, FR-009, FR-015, research.md §5
- [X] T012 [P] [US1] Actualizar `backend/src/test/java/com/amano/backend/user/UserEntitiesTest.java`: round-trip de `emailVerifiedAt` en `User` — research.md §5
- [X] T013 [P] [US1] Actualizar `frontend/src/api/authClient.test.js`: register ya no devuelve perfil con cookie; devuelve `{ok:true, email}`; asertar red/4xx restantes sin cambios — research.md §5, contrato contracts §"Cambio sobre contrato de 001"
- [X] T014 [P] [US1] Actualizar `frontend/src/pages/RegisterPage.test.jsx`: tras el envío exitoso se navega a `/check-email` **sin** `setUser` (ninguna sesión) — research.md §5, FR-001, SC-002
- [X] T015 [P] [US1] Actualizar `frontend/src/components/RegisterForm.validation.test.jsx`: el éxito dispara `onRegistered(email)` en lugar de `onRegistered(profile)` — research.md §5
- [X] T016 [P] [US1] Crear IT de flujo completo con GreenMail `backend/src/test/java/com/amano/backend/verification/RegistrationVerificationIT.java` (`@SpringBootTest` + Testcontainers + `GreenMailExtension` vía `@DynamicPropertySource` en `spring.mail.*`): registro → cuenta no verificada en BD, 0 cookies, correo recibido con asunto/cuerpo en español y enlace `${APP_BASE_URL}/verify-email?token=...`, token en claro NO presente en BD (solo hash), `GET /api/users/me` → 401 — FR-001, FR-003, FR-004, D2, D9
- [X] T017 [P] [US1] Crear test unitario `backend/src/test/java/com/amano/backend/verification/VerificationMailServiceTest.java` con `JavaMailSender` mockeado que lanza excepción al enviar: el fallo NO se propaga (el método `@Async` lo captura y lo registra con `log.warn`), la cuenta queda creada y el flujo de registro no depende del envío — FR-019, D1, D9

### Implementation for User Story 1

- [X] T018 [US1] Crear `backend/src/main/java/com/amano/backend/verification/VerificationTokenService.java`: `issueFor(User)` genera 32 bytes `SecureRandom` → Base64URL, persiste SHA-256 hex en `token_hash` con `expires_at = now + tokenExpiration`, invalida los vigentes previos de la cuenta (`used_at = now`) en la misma transacción; método para calcular el hash — FR-003, FR-009, FR-021, D2
- [X] T019 [US1] Crear `backend/src/main/java/com/amano/backend/verification/VerificationMailService.java`: método `@Async` que envía vía `JavaMailSender` el correo en español (asunto y cuerpo) con enlace `${app.baseUrl}/verify-email?token=<token en claro>`; captura y loguea fallos sin propagar (FR-019) — D1, D8
- [X] T020 [US1] Modificar `backend/src/main/java/com/amano/backend/auth/RegistrationService.java`: al registrar, crear la cuenta con `emailVerifiedAt = null`, emitir token (T018) y disparar el correo (T019) — **sin** emitir sesión; en el caso de correo ya registrado **no verificada**, reemplazar los datos de la cuenta pendiente por los del nuevo formulario (incluida la contraseña), invalidar el token anterior y emitir uno nuevo con envío inmediato (el chequeo de límite de reenvío se agrega en US4, FR-015/Q2); el correo del primer registro nunca consulta el límite (FR-011/Q3) — FR-001, FR-015, FR-020
- [X] T021 [US1] Modificar `backend/src/main/java/com/amano/backend/auth/AuthController.java`: `register()` deja de crear `AuthCookie`/`Set-Cookie` y responde `201` con body `{"email": "<normalizado>"}` (sin `Location`) — contrato contracts §"Cambio sobre contrato de 001"
- [X] T022 [US1] Modificar `frontend/src/api/authClient.js`: `register()` parsea el body `{email}` y lo devuelve en `{ok:true, email}`; sin cambios en manejo de errores — contracts §"Cambio sobre contrato de 001"
- [X] T023 [US1] Crear `frontend/src/pages/CheckEmailPage.jsx` y registrar la ruta `/check-email` en `frontend/src/App.jsx`: muestra "Revisa tu correo", el mensaje de que se envió un enlace de verificación, la dirección guardada en `sessionStorage` (clave por correo; se restaura al recargar) y el botón "Reenviar correo de verificación" (siempre habilitado — regla en FR-010; el manejo del clic se completa en US4) — FR-002, US1 escenarios 2 y 5
- [X] T024 [US1] Modificar `frontend/src/pages/RegisterPage.jsx`: en éxito, guardar el correo en `sessionStorage` y navegar a `/check-email` (replace), sin `setUser` — FR-001
- [X] T025 [US1] Modificar `frontend/src/components/RegisterForm.jsx`: `onRegistered` ahora recibe y propaga `email` en lugar del perfil — FR-001
- [X] T026 [US1] Actualizar `backend/src/test/java/com/amano/backend/auth/RegistrationProfileIT.java`: agregar helper de test que crea la cuenta **verificada** (insertar `email_verified_at` o canjear el token leído de GreenMail) antes de usar la cookie con `/me` — research.md §5

**Checkpoint**: US1 completa: registro no verificado end-to-end con correo real (GreenMail) y pantalla "Revisa tu correo". MVP demoable.

---

## Phase 4: User Story 2 - Confirmación del enlace de verificación (Priority: P2)

**Goal**: Canje del token vía POST (inmune a escáneres): token válido → verificada + sesión + dashboard con bienvenida; vencido/usado/inválido → pantallas y mensajes exactos (FR-005..FR-009, FR-021).

**Independent Test**: Abrir el enlace de GreenMail en el navegador → verifica, inicia sesión en ese dispositivo y llega al dashboard; reabrir el enlace → "ya ha sido utilizado"; enlace alterado → "no es válido"; token viejo → pantalla de expirado.

### Tests for User Story 2 ⚠️ (primero, deben FALLAR)

- [ ] T027 [P] [US2] Tests unitarios de canje en `backend/src/test/java/com/amano/backend/verification/EmailVerificationServiceTest.java`: token válido (marca `used_at`, `emailVerifiedAt` seteado, returns perfil), usado (409), vencido (410 + `used_at` seteado), inexistente/alterado (400); `JavaMailSender` y repos mockeados — FR-005..FR-009
- [ ] T028 [P] [US2] IT de contrato en `backend/src/test/java/com/amano/backend/verification/EmailVerificationIT.java` (GreenMail + Testcontainers): `POST /api/auth/verify-email` → 200 con `Set-Cookie amano_auth` idéntico al contrato de 001 y perfil en body; segundo canje → 409 sin cookie con el texto exacto "Este enlace de verificación ya ha sido utilizado. Por favor, inicia sesión"; token alterado → 400 "El enlace de verificación no es válido"; token con plazo acortado vencido → 410 "El enlace de verificación ha expirado"; segundo dispositivo (nueva sesión) → 200 (enlace portador de sesión, Q1) — contracts §`POST /api/auth/verify-email`, SC-003, SC-008
- [ ] T029 [P] [US2] Crear `frontend/src/pages/VerifyEmailPage.test.jsx`: éxito → `setUser` y navegación a `/dashboard`; 400 → mensaje "El enlace de verificación no es válido"; 409 → mensaje "ya ha sido utilizado… inicia sesión"; 410 → navegación a `/verify-email/expired`; sin token en la URL → mensaje de inválido — FR-005, FR-007, FR-008
- [ ] T030 [P] [US2] Crear `frontend/src/pages/ExpiredLinkPage.test.jsx`: muestra que el enlace expiró y conduce a la pantalla de reenvío (formulario visible; su wiring es US4) — FR-006

### Implementation for User Story 2

- [ ] T031 [US2] Crear `backend/src/main/java/com/amano/backend/verification/EmailVerificationService.java` con el método de canje: busca por `token_hash`, valida `used_at IS NULL` y `expires_at > now()` (vencido → setea `used_at` y lanza excepción de 410; usado → excepción de 409; no encontrado → excepción de 400), marca uso y cambia la cuenta a verificada en la misma transacción — FR-005, FR-021, D2
- [ ] T032 [US2] Crear `backend/src/main/java/com/amano/backend/verification/EmailVerificationController.java` con `POST /api/auth/verify-email` (`{"token": "..."}`) que, tras el canje exitoso, emite la sesión reutilizando `JwtService` y `AuthCookie` (cookie idéntica a la de 001) y devuelve el perfil con el shape de `GET /api/users/me` (usar `UserMapper`); agregar `/api/auth/verify-email` al `permitAll` de `backend/src/main/java/com/amano/backend/config/SecurityConfig.java` y actualizar las aserciones de rutas en `backend/src/test/java/com/amano/backend/auth/SecurityConfigTest.java` — contracts §`POST /api/auth/verify-email`, D7
- [ ] T033 [US2] Extender `backend/src/main/java/com/amano/backend/common/ProblemDetailExceptionHandler.java` con los mapeos de verificación: 409 (`title "Enlace ya utilizado"`, detalle exacto de FR-007), 410 (`title "Enlace expirado"`, detalle "El enlace de verificación ha expirado"), 400 (`title "Enlace no válido"`, detalle "El enlace de verificación no es válido"); sin filtrar detalles internos — contracts, FR-006..FR-008
- [ ] T034 [US2] Crear `frontend/src/pages/VerifyEmailPage.jsx`: lee `token` de la URL, ejecuta el POST de canje vía `frontend/src/api/authClient.js` (nuevo método `verifyEmail(token)`), en éxito `setUser` + navega a `/dashboard` (limpiando la URL de la barra de direcciones), y maneja 400/409/410 con los textos exactos — FR-005, D3
- [ ] T035 [US2] Crear `frontend/src/pages/ExpiredLinkPage.jsx` y registrar rutas `/verify-email` y `/verify-email/expired` en `frontend/src/App.jsx` (la SPA ya sirve rutas nuevas vía `try_files` de nginx, verificado en quickstart); smoke de rutas nuevas en `frontend/src/App.test.jsx` — FR-006
- [ ] T036 [US2] Modificar `frontend/src/pages/DashboardPage.jsx` para mostrar un mensaje de bienvenida de cuenta verificada cuando llega desde la verificación (por ejemplo vía `location.state`), manteniendo el saludo existente, y actualizar `frontend/src/pages/DashboardPage.test.jsx` (regresión del saludo existente + nuevo mensaje de bienvenida) — FR-005, flujo principal paso 5

**Checkpoint**: US1 + US2 completas: registro → correo → canje → sesión y dashboard funcionan de punta a punta.

---

## Phase 5: User Story 3 - Bloqueo de acceso para cuentas no verificadas (Priority: P3)

**Goal**: Ninguna cuenta no verificada accede a recursos protegidos ni inicia sesión; la regla se valida con el login simulado stub (FR-004, FR-014, SC-002). Backend solamente: la UI de login pertenece a la feature real de login (fuera de alcance), y el 403 le entrega el indicador `properties.action: "RESEND_VERIFICATION"` con el que ofrecerá el enlace de reenvío.

**Independent Test**: Con cuenta no verificada: `POST /api/auth/login` → 403 con el mensaje exacto, el indicador de reenvío y sin cookie; cualquier intento de `/api/users/**` → 401. Con cuenta verificada: login → 200 con cookie y perfil.

### Tests for User Story 3 ⚠️ (primero, deben FALLAR)

- [ ] T037 [P] [US3] Agregar a `backend/src/test/java/com/amano/backend/verification/EmailVerificationIT.java` (o nueva `UnverifiedAccessIT.java`): cookie emitida para cuenta no verificada (forjada en test) → `GET /api/users/me` responde 401 con el ProblemDetail "No autorizado" de 001; con cuenta verificada → 200 — FR-004, SC-002
- [ ] T038 [P] [US3] IT del stub en `backend/src/test/java/com/amano/backend/verification/SimulatedLoginIT.java`: credenciales válidas + verificada → 200 con `Set-Cookie` y perfil; credenciales válidas + no verificada → **403** sin cookie, `title "Cuenta no verificada"`, `detail` exacto "Tu cuenta aún no ha sido verificada. Por favor, revisa tu correo electrónico" e indicador `properties.action: "RESEND_VERIFICATION"`; credenciales inválidas o cuenta inexistente → 401 sin discernir existencia — FR-014, contracts §`POST /api/auth/login`
- [ ] T039 [P] [US3] Actualizar `backend/src/test/java/com/amano/backend/auth/SecurityConfigTest.java`: `/api/auth/login` en `permitAll`, y regla de cuenta verificada sobre `/api/users/**` — research.md §5

### Implementation for User Story 3

- [ ] T040 [US3] Implementar la exigencia de cuenta verificada para `/api/users/**` en `backend/src/main/java/com/amano/backend/config/SecurityConfig.java` mediante un `AuthorizationManager` (por ejemplo, un bean `verifiedAccount()`) que, para un principal autenticado, consulta `email_verified_at` vía `UserRepository` (no confiar solo en el frontend — constitución IX): cuenta no verificada → 401 con el ProblemDetail existente; cuenta verificada → permite. La comprobación vive en el servidor y hereda a cualquier endpoint protegido bajo ese ámbito (FR-004) — FR-004, SC-002
- [ ] T041 [US3] Crear `backend/src/main/java/com/amano/backend/verification/SimulatedLoginController.java` (`POST /api/auth/login`, `{"email","password"}`) **con cabecera Javadoc y anotación que lo marquen claramente como STUB PROVISORIO a reemplazar por la feature de login real** (D7): valida credenciales con `PasswordEncoder`, aplica FR-014 (403 con el mensaje exacto más `properties.action: "RESEND_VERIFICATION"` si no verificada, para que la feature real de login ofrezca el enlace de reenvío), emite cookie con `JwtService`/`AuthCookie` si verificada; agregar `/api/auth/login` al `permitAll` de `SecurityConfig` — contracts §`POST /api/auth/login`

**Checkpoint**: SC-002 garantizada en el servidor; la regla que consumirá la feature de login real está documentada, implementada como stub y probada.

---

## Phase 6: User Story 4 - Reenvío del correo con límite de frecuencia y respuesta neutra (Priority: P4)

**Goal**: Botón de reenvío con límite de 1 cada 5 min por dirección (existiendo o no la cuenta), respuesta siempre idéntica con `remainingSeconds`, cuenta regresiva persistente tras recargas (FR-010..FR-013, SC-004..SC-006), y aplicación del límite al re-registro sobre pendiente (FR-015/Q2).

**Independent Test**: Clic en "Reenviar" → mensaje neutro + correo nuevo (token invalida al anterior) + cuenta regresiva; recarga durante el límite → tiempo restante real; `curl` con correo inexistente y con verificada → respuestas idénticas sin envío.

### Tests for User Story 4 ⚠️ (primero, deben FALLAR)

- [ ] T042 [P] [US4] Tests unitarios en `backend/src/test/java/com/amano/backend/verification/EmailResendServiceTest.java`: sin límite + cuenta pendiente → emite token (invalida anterior) y envía; sin límite + inexistente o verificada → no envía pero registra el clic; límite vigente → no envía ni actualiza `last_resend_at` y devuelve el `remainingSeconds` correcto; mensaje neutro idéntico byte a byte en todos los casos — FR-011..FR-013, SC-005
- [ ] T043 [P] [US4] IT de contrato de reenvío en `backend/src/test/java/com/amano/backend/verification/EmailResendIT.java` (GreenMail): clic → 200 con el mensaje neutro exacto y `remainingSeconds` ∈ (0,300], correo recibido con token nuevo que invalida al anterior; segundo clic dentro del plazo → 200 idéntico, 0 correos nuevos; correo inexistente y correo verificada → 200 idénticos, 0 correos; SC-006: tras simular recarga, el `remainingSeconds` devuelto decrece correctamente — contracts §`POST /api/auth/resend-verification`, SC-004..SC-006
- [ ] T044 [P] [US4] Agregar a `backend/src/test/java/com/amano/backend/verification/RegistrationVerificationIT.java` los escenarios de las clarificaciones: Q2 — re-registro sobre pendiente con límite vigente → 201, datos reemplazados, token nuevo emitido pero **sin correo** hasta venza el plazo; Q3 — registro con dirección que tiene límite vigente de un reenvío previo sin cuenta → el correo del primer registro se envía igualmente — FR-015, FR-011, research.md §2 Q2/Q3
- [ ] T045 [P] [US4] Crear `frontend/src/components/ResendCountdown.test.jsx`: botón habilitado al montar y también tras recargar durante un límite vigente (el botón **nunca** se deshabilita); tras un clic la cuenta regresiva descendente se calcula desde el `remainingSeconds` del servidor (fuente de verdad), que tampoco envía nada cuando el límite sigue vigente; persistencia del `deadline` en `sessionStorage` (clave por correo) para restaurar la cuenta regresiva al recargar — FR-010, FR-013, SC-006
- [ ] T046 [P] [US4] Crear `frontend/src/pages/CheckEmailPage.resend.test.jsx`: clic → muestra el mensaje neutro "Si el correo existe y no ha sido verificado, se enviará un nuevo enlace de confirmación" y activa la cuenta regresiva; clic durante el límite (por ejemplo tras recarga) → el servidor responde con el tiempo restante y la pantalla lo refleja sin enviar — FR-012, FR-013

### Implementation for User Story 4

- [ ] T047 [P] [US4] Crear entidad `backend/src/main/java/com/amano/backend/verification/EmailResendLimit.java` (tabla `email_resend_limits`: `emailHash` PK, `lastResendAt`) y repositorio `EmailResendLimitRepository.java` — data-model.md §email_resend_limits
- [ ] T048 [US4] Crear `backend/src/main/java/com/amano/backend/verification/EmailResendService.java`: calcula `remainingSeconds` desde `last_resend_at` y `resendCooldown`; si el límite está vigente no envía ni actualiza; si no, registra el clic (`last_resend_at = now()`), y solo si existe cuenta **no verificada** emite token nuevo (reutilizando `VerificationTokenService`) y dispara el correo (`VerificationMailService`); respuesta siempre `{"message": "<mensaje neutro exacto>", "remainingSeconds": n}` — FR-011..FR-013, D4
- [ ] T049 [US4] Agregar `POST /api/auth/resend-verification` (`{"email": "..."}`) a `EmailVerificationController.java` (normalizar el correo como en 001 antes de hashear), agregar la ruta al `permitAll` de `SecurityConfig.java` y actualizar `SecurityConfigTest.java`; nunca devolver 400/404/429 en este endpoint — contracts §`POST /api/auth/resend-verification`
- [ ] T050 [US4] Agregar `resendVerification(email)` a `frontend/src/api/authClient.js` (o crear `frontend/src/api/verificationClient.js` si authClient queda demasiado grande) devolviendo `{message, remainingSeconds}` — contracts
- [ ] T051 [US4] Crear `frontend/src/components/ResendCountdown.jsx`: botón "Reenviar correo de verificación" siempre habilitado (también al cargar o recargar la pantalla); tras el clic muestra/actualiza la cuenta regresiva visible calculada desde `remainingSeconds` del servidor (fuente de verdad; si el límite sigue vigente no se envía nada), persistiendo el deadline en `sessionStorage` por correo (sobrevive recargas) — FR-010, FR-013, SC-006
- [ ] T052 [US4] Completar `frontend/src/pages/CheckEmailPage.jsx`: manejar el clic de reenvío con `ResendCountdown`, mostrar siempre el mensaje neutro y la cuenta regresiva con el tiempo restante real devuelto por el servidor — FR-012, SC-005
- [ ] T053 [US4] Completar el formulario de reenvío de `frontend/src/pages/ExpiredLinkPage.jsx` (ingreso de correo → `resendVerification` → mensaje neutro + cuenta regresiva) — FR-006, contrato §`POST /api/auth/resend-verification`
- [ ] T054 [US4] Modificar `backend/src/main/java/com/amano/backend/auth/RegistrationService.java` para que el envío del re-registro sobre cuenta pendiente consulte el límite de `EmailResendService` (Q2: con límite vigente, emitir token pero NO enviar hasta que venza; sin límite, enviar de inmediato) — FR-015, research.md §2 Q2

**Checkpoint**: Todas las historias funcionales de forma independiente; US4 además endurece el caso borde de re-registro de US1.

---

## Phase 7: Polish & Cross-Cutting Concerns

**Purpose**: Requisitos transversales y verificación final. El primer task es un requisito de la spec (FR-017/FR-018), no una mejora opcional.

- [ ] T055 Crear `backend/src/main/java/com/amano/backend/verification/UnverifiedAccountCleanupScheduler.java` (`@Scheduled` con el cron de `VerificationProperties`): elimina cuentas con `email_verified_at IS NULL` sin token vigente cuyo token más reciente tenga `created_at < now() - accountExpiration` (tokens en cascada; los límites de reenvío persisten); test unitario/IT con plazo acortado y datos de prueba — FR-017, FR-018, SC-007, D6
- [ ] T056 [P] Verificar regresiones sin cambios previstos: `backend/src/test/java/com/amano/backend/auth/JwtServiceTest.java`, `frontend/src/auth/AuthContext.test.jsx` y `frontend/src/auth/ProtectedRoute.test.jsx` siguen pasando (research.md §5); ajustar solo si el entorno lo exige
- [ ] T057 Ejecutar la guía de validación end-to-end de `specs/003-email-verification/quickstart.md` (Modo A o Modo B) y corregir desviaciones; documentar el resultado
- [ ] T058 Ejecutar las verificaciones obligatorias completas y dejarlas en verde: `cd backend && ./mvnw -B verify` (JaCoCo LINE ≥ 85 %) y `cd frontend && npm ci && npm run lint && npm run test:coverage && npm run build` (V8 lines/statements ≥ 85 %); sin borrar ni excluir pruebas para alcanzar el umbral — constitución V, XI
- [ ] T059 Revisar coherencia de artefactos SDD tras la implementación (`specs/003-email-verification/contracts/email-verification-api.md`, `data-model.md`, `research.md`): actualizar si el diseño final difirió, y marcar las tareas completadas en este `tasks.md` — constitución II, DoD

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: Sin dependencias — arranca de inmediato.
- **Foundational (Phase 2)**: Depende de Setup (T004 define las properties que lee T008). **Bloquea todas las historias.**
- **US1 (Phase 3)**: Depende de Foundational.
- **US2 (Phase 4)**: Depende de Foundational y de US1 (emite el token que US2 canjea; verifica la cuenta que US1 crea).
- **US3 (Phase 5)**: Depende de Foundational y de US1 (necesita cuentas no verificadas para probar el bloqueo); independiente de US2/US4.
- **US4 (Phase 6)**: Depende de Foundational, US1 (pantalla y servicio de token) y US2 (invalida tokens que US2 canjea); T054 toca `RegistrationService` de US1.
- **Polish (Phase 7)**: Depende de las historias deseadas; T055 solo requiere Foundational, pero se deja al final por ser un requisito transversal de ciclo de vida.

### User Story Dependencies

- **US1 (P1)**: Tras Foundational — sin dependencias de otras historias. MVP.
- **US2 (P2)**: Tras US1 (emisión de token).
- **US3 (P3)**: Tras US1 — no requiere US2 ni US4.
- **US4 (P4)**: Tras US1 y US2 (pantalla "Revisa tu correo" + ciclo de token completo).

### Within Each User Story

- Tests primero: deben compilar y FALLAR antes de implementar (TDD suave).
- Entidades → servicios → endpoints → frontend → integración.
- Los tests de 001 afectados (research.md §5) se actualizan en la historia que cambia el comportamiento correspondiente (contrato de register → US1; reglas de seguridad → US2/US3).

### Parallel Opportunities

- Toda la Phase 1 es paralela (T002–T004 en archivos distintos).
- Phase 2: T006, T007 y T008 en paralelo.
- US1: los 8 tasks de tests (T010–T017) corren en paralelo entre sí.
- US2 y US3 pueden avanzar en paralelo tras US1 (archivos compartidos mínimos: `SecurityConfig.java` — coordinar los `permitAll`).
- US4: T042–T046 (tests) en paralelo; T047 en paralelo con ellos.
- Polish: T056 en paralelo con T057.

---

## Parallel Example: User Story 1

```bash
# Lanzar juntos todos los tests de US1 (archivos distintos):
Task: "T010 Actualizar RegistrationIT.java"
Task: "T011 Ampliar RegistrationServiceTest.java"
Task: "T012 Actualizar UserEntitiesTest.java"
Task: "T013 Actualizar authClient.test.js"
Task: "T014 Actualizar RegisterPage.test.jsx"
Task: "T015 Actualizar RegisterForm.validation.test.jsx"
Task: "T016 Crear RegistrationVerificationIT.java (GreenMail)"
Task: "T017 Crear VerificationMailServiceTest.java (JavaMailSender lanza excepción)"

# Luego, implementación secuencial por dependencias:
Task: "T018 VerificationTokenService.java → T019 VerificationMailService.java → T020 RegistrationService.java"
Task: "T022 authClient.js → T023 CheckEmailPage.jsx → T024 RegisterPage.jsx → T025 RegisterForm.jsx"
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Completar Phase 1: Setup.
2. Completar Phase 2: Foundational (CRÍTICO — bloquea todo).
3. Completar Phase 3: US1.
4. **PARAR Y VALIDAR**: registrar end-to-end con GreenMail (quickstart §1 Modo B, §8).
5. Demo/entrega: la feature ya corta el registro fraudulento y produce cuentas con correo comprobable.

### Incremental Delivery

1. Setup + Foundational → base lista.
2. US1 → probar independiente → demo (MVP).
3. US2 → probar independiente → demo (verificación real de punta a punta).
4. US3 → probar independiente → demo (bloqueo garantizado; stub listo para la feature de login).
5. US4 → probar independiente → demo (recuperación de correo no recibido con privacidad).
6. Polish → quickstart completo + cobertura + artefactos.

### Parallel Team Strategy

Con varios desarrolladores:

1. El equipo completa Setup + Foundational junto.
2. Con Foundational listo:
   - Dev A: US1 (bloqueante de las demás — prioridad real).
   - Con US1 integrada: Dev A/B → US2 y US3 en paralelo (coordinando `SecurityConfig`); Dev C → US4 sobre US1/US2.
3. Las historias se integran y prueban de forma independiente vía PR a `sprint-n`.

---

## Notes

- `[P]` = archivos distintos, sin dependencias; `[Story]` = trazabilidad US1–US4 (constitución II).
- Mensajes exactos en español definidos por la spec y contracts: no reimprovisar textos.
- Gmail real **nunca** en pruebas (solo GreenMail) — regla del equipo, research.md D9.
- Token: valor en claro solo en el correo; en BD únicamente SHA-256 (D2).
- Commit después de cada task o grupo lógico con Conventional Commits (constitución XIII); PRs a `sprint-n`.
- Detenerse en cualquier checkpoint para validar la historia de forma independiente.
