# Tasks: Recuperación de contraseña

**Input**: Design documents from `/specs/006-password-recovery/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/password-recovery-api.md, quickstart.md

**Tests**: Incluidos porque la constitución (principio V) exige pruebas automatizadas para toda funcionalidad nueva o modificada, y research.md §D12 define la estrategia (unitarios sin base, Testcontainers + GreenMail en `*IT`, Vitest en frontend; cobertura ≥ 85 % en ambos módulos).

**Organization**: Tareas agrupadas por historia de usuario para permitir implementación y prueba independientes.

**Estado**: Toda la implementación y sus pruebas están en el árbol de trabajo de `feat/recuperar-contra` (sin commitear; `mvn -B verify` y `npm run test:coverage` en verde). Las tareas de código figuran como `[X]`; quedan pendientes `[ ]` la validación manual de US2, los commits y el PR.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Paralelizable (archivos distintos, sin dependencias entre tareas incompletas)
- **[Story]**: Historia de usuario (US1–US2); sin etiqueta en Setup, Foundational y Polish
- Cada tarea incluye la ruta exacta del archivo y la trazabilidad a FR/SC/D de research.md

## Phase 1: Setup (Infraestructura compartida)

**Purpose**: Esquema y configuración que necesitan ambas historias; no contiene lógica.

- [X] T001 Crear `backend/src/main/resources/db/migration/V3__password_reset_tokens.sql`: tabla `password_reset_tokens` (`id` identity, `user_id` FK `ON DELETE CASCADE`, `token_hash VARCHAR(64) UNIQUE`, `created_at DEFAULT now()`, `expires_at` con `CHECK (expires_at > created_at)`, `used_at` nullable) e índice `ix_password_reset_tokens_user_id`; sin `DELETE` (regla del equipo). Verificar que `V3` no colisiona con la rama de 005 — FR-005, FR-006, FR-007, data-model.md §Cambios de esquema
- [X] T002 [P] Agregar `password-reset.token-expiration=3600` a `backend/src/main/resources/application.properties` y crear el record `backend/src/main/java/com/amano/backend/passwordreset/PasswordResetProperties.java` (`@ConfigurationProperties(prefix = "password-reset")`, `@DurationUnit(SECONDS)`, default 3600, falla si la duración no es positiva) — FR-006, research.md §D2
- [X] T003 Registrar `PasswordResetProperties` en `@EnableConfigurationProperties` de `backend/src/main/java/com/amano/backend/BackendApplication.java`, junto a `VerificationProperties` — research.md §D2

---

## Phase 2: Foundational (Prerrequisitos bloqueantes)

**Purpose**: Persistencia del token, compartida por la emisión (US1) y el canje (US2).

**⚠️ CRITICAL**: Ninguna historia puede empezar hasta completar esta fase.

- [X] T004 Crear la entidad `backend/src/main/java/com/amano/backend/passwordreset/PasswordResetToken.java` mapeando `password_reset_tokens` (`@ManyToOne(fetch = LAZY)` a `User`, `tokenHash` no actualizable de 64, `@CreationTimestamp createdAt`, `expiresAt`, `usedAt`), solo mapeo, sin reglas — data-model.md §PasswordResetToken
- [X] T005 Crear `backend/src/main/java/com/amano/backend/passwordreset/PasswordResetTokenRepository.java` con la consulta derivada `findByUserIdAndUsedAtIsNull(Long)` (las de US2 se agregan en T021) — FR-007

**Checkpoint**: Hibernate valida la entidad contra `V3` al arrancar (`ddl-auto=validate`); las historias pueden comenzar.

---

## Phase 3: User Story 1 - Pedir un enlace de recuperación (Priority: P1) 🎯 MVP

**Goal**: Desde `/login`, la persona llega a `/forgot-password`, ingresa su correo y ve "Revisa tu bandeja de entrada" con un mensaje neutro; si la cuenta existe y está verificada, recibe un enlace de un solo uso válido por 1 hora.

**Independent Test**: Cuenta verificada → llega un correo con `/reset-password?token=...` y en la base solo está el hash; correo inexistente, cuenta pendiente y cuerpo vacío → respuesta idéntica y ningún correo; dos pedidos seguidos → el primero queda invalidado.

### Tests for User Story 1 ⚠️ (primero, deben FALLAR)

- [X] T006 [P] [US1] Crear `backend/src/test/java/com/amano/backend/passwordreset/PasswordResetServiceTest.java` (repositorios y correo mockeados, SHA-256 independiente del de producción): mensaje neutro exacto; cuenta verificada con `"  Ana@Example.COM "` → token de 43 caracteres Base64URL enviado por correo cuyo SHA-256 es el `tokenHash` guardado, `usedAt` nulo y vencimiento a 1 h; los tokens vigentes previos quedan con `usedAt`; dos pedidos dan tokens distintos; cuenta inexistente, pendiente o correo `null` → sin token ni correo; verificada e inexistente responden igual — FR-002..FR-007, research.md §D1, §D3, §D5
- [X] T007 [P] [US1] Crear `backend/src/test/java/com/amano/backend/passwordreset/PasswordResetMailServiceTest.java` (`JavaMailSender` mockeado): asunto "Restablece tu contraseña - A Mano", destinatario, "Hola Ana", enlace `${base}/reset-password?token=...` y "1 hora"; una `MailSendException` no se propaga — FR-008, research.md §D4
- [X] T008 [P] [US1] Crear `backend/src/test/java/com/amano/backend/passwordreset/ForgotPasswordIT.java` (Testcontainers PostgreSQL + GreenMail en proceso; nunca Gmail): cuenta registrada y verificada recibe el correo y el hash del token del enlace está vigente en `password_reset_tokens` (y el valor en claro no); un segundo pedido marca el primer token con `used_at`; las respuestas para cuenta verificada, correo inexistente y `{}` son idénticas byte a byte y el inexistente no recibe correo — FR-002, FR-004, FR-005, FR-007, FR-009, SC-002, SC-007
- [X] T009 [P] [US1] Agregar a `backend/src/test/java/com/amano/backend/auth/SecurityConfigTest.java` que `POST /api/auth/forgot-password` es accesible sin sesión (404 en el slice = no bloqueado por seguridad) — FR-001
- [X] T010 [P] [US1] Crear `frontend/src/pages/ForgotPasswordPage.test.jsx` (cliente mockeado): formulario y enlace a `/login`; correo vacío → "El correo electrónico es obligatorio" sin llamada; formato inválido → sin llamada; botón "Enviando…" deshabilitado durante la espera; 200 → correo normalizado, "Revisa tu bandeja de entrada" y mensaje del servidor, formulario oculto; red → mensaje fijo y botón habilitado — FR-002, FR-003, contracts §Comportamiento del cliente
- [X] T011 [P] [US1] Ampliar `frontend/src/api/authClient.test.js` con `forgotPassword`: 200 → `{ok:true, message}` con `POST /api/auth/forgot-password`, JSON y `credentials:'include'`; 5xx y rechazo de `fetch` → `{ok:false, network:true}`; texto exacto de `FORGOT_PASSWORD_NETWORK_ERROR_MESSAGE` — contracts §Comportamiento del cliente
- [X] T012 [P] [US1] Ampliar `frontend/src/pages/LoginPage.test.jsx`: existe el enlace "¿Olvidaste tu contraseña?" a `/forgot-password` — FR-001

### Implementation for User Story 1

- [X] T013 [US1] Crear `backend/src/main/java/com/amano/backend/passwordreset/PasswordResetMailService.java`: `@Async sendPasswordResetEmail(User, String)` con `JavaMailSender`, `From` solo si `spring.mail.username` no está vacío, cuerpo en español con el enlace `${app.base-url}/reset-password?token=...` y "válido durante 1 hora", y captura de `MessagingException | RuntimeException` con un warning sin token — FR-008, research.md §D4
- [X] T014 [US1] Crear `backend/src/main/java/com/amano/backend/passwordreset/PasswordResetService.java` con `NEUTRAL_MESSAGE` y `@Transactional requestReset(String)`: normaliza (trim + `toLowerCase(Locale.ROOT)`), filtra cuentas con `emailVerifiedAt != null`, invalida los tokens vigentes, genera 32 bytes de `SecureRandom` en Base64URL, guarda `hash()` SHA-256 y `expiresAt = now + tokenExpiration`, dispara el correo y devuelve siempre el mensaje neutro — FR-002..FR-007, research.md §D1, §D3, §D5
- [X] T015 [US1] Crear `backend/src/main/java/com/amano/backend/passwordreset/PasswordResetController.java` con `POST /api/auth/forgot-password` (`@RequestBody(required = false)`, record `ForgotPasswordRequest(email)`, siempre `200 {"message": ...}`) — FR-001, FR-002, contracts §forgot-password
- [X] T016 [US1] Agregar `/api/auth/forgot-password` al `permitAll` de `backend/src/main/java/com/amano/backend/config/SecurityConfig.java` y documentarlo en su Javadoc — FR-001
- [X] T017 [US1] Agregar a `frontend/src/api/authClient.js` `FORGOT_PASSWORD_NETWORK_ERROR_MESSAGE` y `forgotPassword(email)`: 2xx → `{ok:true, message}`; cualquier otro resultado o fallo de red → `{ok:false, network:true}` (el endpoint nunca da 4xx con significado) — contracts §Comportamiento del cliente, research.md §D11
- [X] T018 [US1] Crear `frontend/src/pages/ForgotPasswordPage.jsx` con `react-hook-form` (`mode: 'onBlur'`), `TextField` de correo con `validateEmail`, envío con `normalizeEmail`, botón "Enviando…"/"Enviar enlace" según `isSubmitting`, sección `role="status"` con `CHECK_INBOX_MESSAGE` ("Revisa tu bandeja de entrada") y el mensaje del servidor, error de red en `data-testid="forgot-password-error"` y enlace "Volver a iniciar sesión" — FR-001, FR-003, research.md §D11
- [X] T019 [US1] Registrar `<Route path="/forgot-password">` en `frontend/src/App.jsx` (pública) y agregar el enlace "¿Olvidaste tu contraseña?" en `frontend/src/pages/LoginPage.jsx` — FR-001

**Checkpoint**: US1 completa y validada manualmente con Gmail real (quickstart §3 A). El correo llega; el enlace todavía apunta a una pantalla inexistente hasta US2.

---

## Phase 4: User Story 2 - Elegir una nueva contraseña con el enlace (Priority: P1)

**Goal**: El enlace abre `/reset-password?token=...`; con un token vigente y una contraseña que cumple la política, la contraseña se reemplaza (BCrypt), se eliminan todos los tokens de la cuenta y la persona llega a `/login` con el aviso de confirmación. Los enlaces no válidos o vencidos se rechazan con la opción de pedir uno nuevo; los errores de campo no consumen el enlace.

**Independent Test**: Token de US1 → cambio exitoso; login con la nueva 200 y con la anterior 401; el mismo enlace después da 400; vencido da 410 y se borra; errores de campo dan 400 por campo y el enlace sigue sirviendo.

### Tests for User Story 2 ⚠️ (primero, deben FALLAR)

- [X] T020 [P] [US2] Ampliar `backend/src/test/java/com/amano/backend/passwordreset/PasswordResetServiceTest.java` (con `PasswordEncoder` mockeado): token vigente → `passwordHash` = encode(nueva) y `deleteByUserId`; mensaje de éxito exacto; token inexistente y `null` → `PasswordResetTokenInvalidException` con el mensaje exacto y sin cambios; token con `usedAt` → no válido; vencido → `PasswordResetTokenExpiredException`, `delete(token)` y contraseña intacta; contraseña corta, sin símbolo o vacía y confirmación distinta o `null` → `FieldValidationException` con los mensajes de 001 y el token sin tocar — FR-011..FR-015, research.md §D7, §D8
- [X] T021 [P] [US2] Crear `backend/src/test/java/com/amano/backend/passwordreset/ResetPasswordIT.java` (Testcontainers + GreenMail; el token se obtiene del correo de `/forgot-password`): cambio 200, cero tokens de la cuenta, hash `$2…` en `users.password_hash`, login nuevo 200 / anterior 401 y reuso 400; enlace reemplazado 400 y el más nuevo 200 (borra también el reemplazado); vencido (ventana movida al pasado respetando el `CHECK`) 410, token borrado y login anterior 200; errores de campo 400 con `properties.fields.password`/`confirmPassword`, el token sobrevive y luego el cambio funciona; token inventado y `{}` 400 "Enlace no válido" — FR-010..FR-015, SC-004, SC-005, SC-006
- [X] T022 [P] [US2] Agregar a `backend/src/test/java/com/amano/backend/auth/SecurityConfigTest.java` que `POST /api/auth/reset-password` es accesible sin sesión — FR-010
- [X] T023 [P] [US2] Crear `frontend/src/pages/ResetPasswordPage.test.jsx` (cliente mockeado): dos campos ocultos; sin token → enlace no válido con "Solicitar un nuevo enlace" a `/forgot-password` y sin llamada; campos vacíos, política incumplida y confirmación distinta → sin llamada; "Guardando…" deshabilitado durante la espera; 200 → `resetPassword(token, pass, pass)` y llegada a `/login` con `state.notice`; 410 → vencido y sin formulario; 400 sin campos → no válido; 400 con campos → mensaje del servidor y formulario conservado; red → mensaje fijo y botón habilitado — FR-016, contracts §Comportamiento del cliente
- [X] T024 [P] [US2] Ampliar `frontend/src/api/authClient.test.js` con `resetPassword`: 200 → `{ok:true, message}` con `POST /api/auth/reset-password` y el JSON `{token, password, confirmPassword}`; 4xx → `{ok:false, status, detail, fields}`; 5xx y fallo de red → `{network:true}`; texto exacto de `RESET_PASSWORD_NETWORK_ERROR_MESSAGE` — contracts §Comportamiento del cliente
- [X] T025 [P] [US2] Ampliar `frontend/src/pages/LoginPage.test.jsx`: con `state.notice` se muestra el aviso en `data-testid="login-notice"` — FR-016, research.md §D10

### Implementation for User Story 2

- [X] T026 [US2] Crear `backend/src/main/java/com/amano/backend/user/PasswordPolicy.java` como copia **idéntica** del archivo de `origin/feat/edit-profile` (005), sin modificar `RegistrationService` — FR-014, research.md §D9
- [X] T027 [P] [US2] Crear `backend/src/main/java/com/amano/backend/common/PasswordResetTokenInvalidException.java` ("El enlace para restablecer la contraseña no es válido") y `PasswordResetTokenExpiredException.java` ("El enlace para restablecer la contraseña ha expirado"), y sus handlers en `backend/src/main/java/com/amano/backend/common/ProblemDetailExceptionHandler.java` (400 "Enlace no válido" y 410 "Enlace expirado") — FR-012, FR-013, contracts §reset-password
- [X] T028 [US2] Agregar a `backend/src/main/java/com/amano/backend/passwordreset/PasswordResetTokenRepository.java` `findByTokenHash(String)` y `deleteByUserId(Long)` — FR-011, FR-015
- [X] T029 [US2] Agregar a `backend/src/main/java/com/amano/backend/passwordreset/PasswordResetService.java` el `PasswordEncoder` en el constructor, `RESET_SUCCESS_MESSAGE` y `@Transactional(noRollbackFor = PasswordResetTokenExpiredException.class) resetPassword(token, password, confirmPassword)`: busca por hash (token `null` = cadena vacía), filtra `usedAt == null`, borra y rechaza el vencido, valida con `PasswordPolicy` + confirmación (errores juntos en `FieldValidationException`), y en el éxito `setPasswordHash(encode)` + `deleteByUserId` — FR-011..FR-015, research.md §D7, §D8
- [X] T030 [US2] Agregar `POST /api/auth/reset-password` a `backend/src/main/java/com/amano/backend/passwordreset/PasswordResetController.java` (record `ResetPasswordRequest(token, password, confirmPassword)`, cuerpo ausente = todos `null`, `200 {"message": ...}` sin cookie) y sumarlo al `permitAll` de `backend/src/main/java/com/amano/backend/config/SecurityConfig.java` — FR-010, FR-015, research.md §D10
- [X] T031 [US2] Agregar a `frontend/src/api/authClient.js` `RESET_PASSWORD_NETWORK_ERROR_MESSAGE` y `resetPassword(token, password, confirmPassword)`: 2xx → `{ok:true, message}`; ≥500 o fallo de red → `{ok:false, network:true}`; otro → `parseProblem` en `{ok:false, status, detail, fields}` — contracts §Comportamiento del cliente
- [X] T032 [US2] Crear `frontend/src/pages/ResetPasswordPage.jsx`: token desde `useSearchParams`; sin token, 410 o 400 sin campos → sección `data-testid="reset-password-dead-link"` con el mensaje exacto y "Solicitar un nuevo enlace"; formulario `react-hook-form` con "Nueva contraseña" (`validatePassword`, `deps: ['confirmPassword']`) y "Confirmar contraseña" (`validate: (value, formValues) => validateConfirmPassword(...)`), botón "Guardando…"/"Guardar contraseña"; 400 con campos → `setError`; red → mensaje fijo; 200 → `navigate('/login', { replace: true, state: { notice } })` — FR-014, FR-016, research.md §D10, §D11
- [X] T033 [US2] Registrar `<Route path="/reset-password">` en `frontend/src/App.jsx` y mostrar `location.state?.notice` en `role="status"` (`data-testid="login-notice"`) en `frontend/src/pages/LoginPage.jsx` — FR-016, research.md §D10

**Checkpoint**: Ambas historias funcionan de punta a punta: pedir el enlace → abrirlo → nueva contraseña → login.

---

## Phase 5: Polish & Cross-Cutting Concerns

**Purpose**: Regresiones, artefactos y salida hacia Pull Request.

- [X] T034 [P] Verificar regresiones de 001/003/004: `RegistrationIT`, `EmailVerificationIT`, `EmailResendIT`, `LoginIT`, `LogoutIT`, `SecurityConfigTest` y las suites de frontend existentes siguen en verde — constitución V
- [X] T035 Ejecutar la verificación automatizada completa: `cd backend && mvn -B verify` (BUILD SUCCESS, JaCoCo ≥ 85 %) y `cd frontend && npm ci && npm run lint && npm run test:coverage && npm run build` (143 tests, 95,54 % de líneas; sin advertencias nuevas de lint) — constitución V, XI
- [X] T036 Estandarizar `specs/006-password-recovery/` con la estructura de Spec Kit de 003/004 (`spec.md`, `plan.md`, `research.md`, `data-model.md`, `quickstart.md`, `contracts/password-recovery-api.md`, `checklists/requirements.md`, `tasks.md`) y revisar su coherencia con el código — constitución II
- [ ] T037 Validar manualmente US2 con el entorno local y correo real según `specs/006-password-recovery/quickstart.md` §3 escenarios D–K y registrar el resultado en su sección "Resultado de la validación" — constitución V, DoD
- [ ] T038 Commitear los cambios en commits acotados con Conventional Commits (p. ej. `docs:` spec 006, `feat:` solicitud del enlace, `feat:` canje y nueva contraseña, `test:` si se separan) — constitución XIII
- [ ] T039 Abrir el Pull Request de `feat/recuperar-contra` hacia la rama de sprint describiendo los dos endpoints, la migración `V3`, la copia idéntica de `PasswordPolicy` de 005 (y la necesidad de verificarla al mergear) y la trazabilidad a `specs/006-password-recovery/`; CI en verde — constitución XII

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: Sin dependencias. T002 en paralelo con T001; T003 depende de T002.
- **Foundational (Phase 2)**: Depende de T001 (la entidad debe validar contra `V3`). **Bloquea ambas historias.**
- **US1 (Phase 3)**: Depende de Foundational.
- **US2 (Phase 4)**: Depende de US1: necesita tokens emitidos (`ResetPasswordIT` los obtiene vía `/forgot-password`) y amplía `PasswordResetService`, `PasswordResetController`, `PasswordResetTokenRepository`, `authClient.js`, `LoginPage.jsx` y `App.jsx`.
- **Polish (Phase 5)**: Depende de ambas historias; T037–T039 al final, en ese orden.

### User Story Dependencies

- **US1 (P1)**: Tras Foundational; sin dependencias de otras historias. MVP: el correo llega aunque la pantalla de destino aún no exista.
- **US2 (P1)**: Tras US1. Misma prioridad porque ninguna entrega valor completo sola, pero US1 va primero por dependencia técnica.

### Within Each User Story

- Tests primero: deben compilar y FALLAR antes de implementar (en esta rama se escribieron antes de cada paso de implementación).
- Backend: migración/propiedades → entidad/repositorio → servicio de correo → servicio → controlador → seguridad.
- Frontend: cliente → página → rutas y enlace desde el login.

### Parallel Opportunities

- Setup: T001 y T002.
- US1: los 7 tasks de tests (T006–T012) en paralelo; T013 (correo) y T017 (cliente) en paralelo entre sí.
- US2: los 6 tasks de tests (T020–T025) en paralelo; T026 y T027 en paralelo; el frontend (T031–T033) en paralelo con el backend (T028–T030) una vez fijado el contrato.

---

## Parallel Example: User Story 2

```bash
# Lanzar juntos los tests de US2 (archivos distintos):
Task: "T020 Ampliar PasswordResetServiceTest.java (canje)"
Task: "T021 Crear ResetPasswordIT.java"
Task: "T023 Crear ResetPasswordPage.test.jsx"
Task: "T024 Ampliar authClient.test.js (resetPassword)"

# Luego, backend y frontend en paralelo contra el contrato:
Task: "T026 PasswordPolicy → T027 excepciones → T028 repositorio → T029 servicio → T030 endpoint"
Task: "T031 authClient.resetPassword → T032 ResetPasswordPage.jsx → T033 ruta + aviso en LoginPage"
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Completar Phase 1 (migración y propiedades) y Phase 2 (entidad y repositorio).
2. Completar Phase 3: US1.
3. **PARAR Y VALIDAR**: pedir el enlace desde `/login` y comprobar que el correo llega (quickstart §3 A–C). Hecho por el equipo con Gmail real.

### Incremental Delivery

1. Setup + Foundational → esquema listo.
2. US1 → probar → validar manualmente (correo real).
3. US2 → probar → validar manualmente (quickstart §3 D–K).
4. Polish → artefactos SDD + verificación + commits + PR.

### Parallel Team Strategy

Con varios desarrolladores, una vez fijado `contracts/password-recovery-api.md`:

- Dev A: backend de la historia en curso (servicio, controlador, IT).
- Dev B: frontend de la historia en curso (cliente, página, tests) contra el cliente mockeado.
- Se integran en la misma rama y se valida con el quickstart.

---

## Notes

- `[P]` = archivos distintos, sin dependencias; `[Story]` = trazabilidad US1–US2 (constitución II).
- Mensajes exactos en español definidos por la spec y el contrato: no reimprovisar textos ("Si el correo existe, recibirás un enlace para restablecer tu contraseña", "El enlace para restablecer la contraseña no es válido", "…ha expirado", "Tu contraseña fue actualizada. Ya puedes iniciar sesión", mensajes de política de 001).
- La solicitud responde siempre lo mismo: nunca agregar un 400/404/429 a `/forgot-password` (FR-002, SC-002).
- El token en claro nunca se guarda ni se registra en logs (FR-005, SC-007).
- Pruebas de correo solo con GreenMail; nunca Gmail real (FR-009).
- `PasswordPolicy` debe seguir idéntico al de 005 hasta que se mergeen ambas ramas (research.md §D9).
- Los commits los realiza el equipo manualmente (T038).
