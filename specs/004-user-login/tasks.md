# Tasks: Inicio de sesión de usuario

**Input**: Design documents from `/specs/004-user-login/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/auth-api.md, quickstart.md

**Tests**: Incluidos porque la constitución (principio V) exige pruebas automatizadas para toda funcionalidad nueva o modificada, y research.md §D8 define la estrategia (unitario sin BD, Testcontainers `*IT`, Vitest en frontend; cobertura ≥ 85 % en ambos módulos).

**Organization**: Tareas agrupadas por historia de usuario para permitir implementación y prueba independientes.

**Estado**: Toda la implementación y sus pruebas ya están commiteadas en `feat/user-login` (commits `9af126a`…`b26ff62`, verificado en el árbol de trabajo). Las tareas de código figuran como `[X]`; solo quedan pendientes `[ ]` la limpieza de comentarios obsoletos, la verificación final previa al PR y la apertura del PR hacia `sprint-1`.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Paralelizable (archivos distintos, sin dependencias entre tareas incompletas)
- **[Story]**: Historia de usuario (US1–US4); sin etiqueta en Setup, Foundational y Polish
- Cada tarea incluye la ruta exacta del archivo y la trazabilidad a FR/SC/D de research.md

## Phase 1: Setup (Infraestructura compartida)

**Purpose**: Confirmar el arranque sin dependencias ni esquema nuevos; nada de esto toca lógica de historias.

- [X] T001 Verificar que la feature no requiere dependencias nuevas ni migraciones Flyway: `backend/pom.xml` y `frontend/package.json` sin cambios, y `backend/src/main/resources/db/migration/` sin V nuevo — el login solo consulta `UserRepository.findByEmail` sobre el esquema existente (data-model.md §Cambios de esquema, research.md §D9)

---

## Phase 2: Foundational (Prerrequisitos bloqueantes)

**Purpose**: Piezas compartidas por todas las historias: reemplazo del stub de 003, mensaje del 401 y cookie de expiración.

**⚠️ CRITICAL**: Ninguna historia puede empezar hasta completar esta fase.

- [X] T002 Eliminar el stub provisorio de 003: borrar `backend/src/main/java/com/amano/backend/verification/SimulatedLoginController.java` y su IT `backend/src/test/java/com/amano/backend/verification/SimulatedLoginIT.java` (renombrada/migrada a `auth/LoginIT.java` en US1), dejando la ruta `POST /api/auth/login` libre para el login real — FR-012, research.md §D3
- [X] T003 [P] Definir `MESSAGE = "El correo electrónico o la contraseña son incorrectos"` en `backend/src/main/java/com/amano/backend/common/InvalidCredentialsException.java` como el 401 único de credenciales inválidas (cuenta inexistente y contraseña incorrecta, indistinguibles) — FR-005, contracts §401, research.md §D2
- [X] T004 [P] Agregar `expiredCookie(boolean secure)` a `backend/src/main/java/com/amano/backend/config/AuthCookie.java`: mismo nombre `amano_auth`, path y atributos que la cookie de sesión, valor vacío y `Max-Age=0` para que el navegador la elimine de inmediato — FR-010, data-model.md §Sesión, research.md §D5

**Checkpoint**: Stub eliminado, mensaje del 401 y helper de cookie de cierre listos; las historias pueden comenzar.

---

## Phase 3: User Story 1 - Acceder con correo y contraseña (Priority: P1) 🎯 MVP

**Goal**: Login real end-to-end: pantalla `/login` con contraseña oculta y botón deshabilitado durante el envío, correo normalizado, sesión `amano_auth` + perfil, y vuelta a la ruta protegida intentada (o dashboard). `ProtectedRoute` redirige a `/login` en lugar de `/register`.

**Independent Test**: Cuenta verificada → formulario en `/login` autentica, establece la cookie `amano_auth` y llega al destino (dashboard o `state.from`); sin sesión, `/profile` redirige a `/login` y el login devuelve a `/profile`; sesión activa en `/login` redirige sin formulario.

### Tests for User Story 1 ⚠️ (primero, deben FALLAR)

- [X] T005 [P] [US1] Crear `backend/src/test/java/com/amano/backend/auth/LoginIT.java` (migra `SimulatedLoginIT`, `@SpringBootTest` + Testcontainers): credenciales correctas de cuenta verificada → 200 con `Set-Cookie` que contiene `amano_auth=`, `HttpOnly` y `SameSite=Strict`, perfil con la forma de `GET /api/users/me` sin `passwordHash`, y la cookie abre `GET /api/users/me`; correo ingresado como `"  Login.Verificada@Example.COM "` autentica a la cuenta normalizada — US1 escenarios 1 y 5, SC-001, research.md §D1
- [X] T006 [P] [US1] Crear `frontend/src/pages/LoginPage.test.jsx`: formulario con contraseña oculta y link a `/register`; envío 200 guarda la sesión y navega a `/dashboard` con el correo normalizado; con `state.from` vuelve a `/profile`; usuario ya logueado en `/login` se redirige sin ver el formulario — FR-001, FR-007, FR-008
- [X] T007 [P] [US1] Ampliar `frontend/src/api/authClient.test.js` con la sección de `login`: 200 → `{ok:true, profile}` con `POST /api/auth/login`, body JSON y `credentials:'include'` — contracts §Contrato de interfaz, research.md §D7
- [X] T008 [P] [US1] Actualizar `frontend/src/auth/ProtectedRoute.test.jsx`: sin sesión → redirección a `/login` (antes `/register`); con sesión → renderiza el contenido protegido — FR-011

### Implementation for User Story 1

- [X] T009 [US1] Crear `backend/src/main/java/com/amano/backend/auth/LoginRequest.java` como record `(String email, String password)` sin validación declarativa (la validación vive en el servicio) — contracts §Request
- [X] T010 [US1] Crear `backend/src/main/java/com/amano/backend/auth/LoginService.java` con `@Transactional(readOnly = true) authenticate(LoginRequest)`: normalización `trim()` + `toLowerCase(Locale.ROOT)` del correo, cuerpo nulo tratado como campos vacíos, y flujo en el orden campos → credenciales → estado de verificación (las ramas de 400/401/403 se completan en US2/US3 sobre este mismo archivo) — FR-002, data-model.md §User, research.md §D3
- [X] T011 [US1] Agregar `POST /api/auth/login` a `backend/src/main/java/com/amano/backend/auth/AuthController.java`: `@RequestBody(required = false) LoginRequest`; en éxito emite `JwtService.issueToken(userId)` + `AuthCookie.sessionCookie(jwt, expiration, isSecure())` en `Set-Cookie` y devuelve `UserMapper.summaryOf(userId)` con el shape de `GET /api/users/me`; errores 400/401/403 delegados al `ProblemDetailExceptionHandler` existente — contracts §200, research.md §D1, §D4
- [X] T012 [US1] Agregar `login(email, password)` a `frontend/src/api/authClient.js`: `POST /api/auth/login` JSON con `credentials:'include'`; 2xx → `{ok:true, profile}`; ≥500 o fallo de red → `{ok:false, network:true}`; otro → parsea RFC 7807 en `{ok:false, status, detail, fields: properties.fields ?? {}, action: properties.action ?? null}` — contracts §Contrato de interfaz, research.md §D4, §D7
- [X] T013 [US1] Crear `frontend/src/components/LoginForm.jsx` con `react-hook-form` (`mode: 'onBlur'`): campos correo (email, requerido, `validateEmail` → "Ingresá un correo electrónico válido") y contraseña (requerida, oculta por defecto con el toggle de `TextField`), normalización con `normalizeEmail` antes de enviar, botón deshabilitado durante `isSubmitting` con label "Ingresando…" → "Iniciar sesión" — FR-001, FR-002, FR-004, research.md §D7
- [X] T014 [US1] Crear `frontend/src/pages/LoginPage.jsx`: destino `location.state?.from?.pathname ?? '/dashboard'`, render `null` mientras carga la sesión (sin destello), `<Navigate replace>` al destino si ya hay sesión, `onLoggedIn` → `setUser` + `navigate(destination, {replace:true})`, título "Iniciar sesión" y enlace "¿No tienes cuenta? Regístrate" a `/register` — FR-001, FR-007, FR-008
- [X] T015 [US1] Registrar `<Route path="/login" element={<LoginPage />} />` en `frontend/src/App.jsx` (ruta pública) — FR-001
- [X] T016 [US1] Modificar `frontend/src/auth/ProtectedRoute.jsx`: `if (!user) return <Navigate to="/login" replace state={{ from: location }} />` (antes `/register`), conservando el render `null` durante la comprobación de sesión — FR-011, research.md §D6

**Checkpoint**: US1 completa: login end-to-end con cookie `amano_auth` y destino conservado. MVP demoable.

---

## Phase 4: User Story 2 - Rechazo seguro de credenciales inválidas o campos vacíos (Priority: P2)

**Goal**: Campos vacíos → error por campo en cliente y 400 por campo en servidor sin autenticar; cuenta inexistente o contraseña incorrecta → mismo 401 sin revelar existencia (con comparación BCrypt dummy para igualar tiempos), contraseña limpia y correo conservado en la interfaz; error de red con mensaje exacto.

**Independent Test**: Formulario vacío → errores por campo sin llamada al backend; curl con `{}` → 400 con `properties.fields`; correo inexistente y contraseña incorrecta → 401 idénticos byte a byte; sin backend → "No se pudo iniciar sesión. Intenta nuevamente".

### Tests for User Story 2 ⚠️ (primero, deben FALLAR)

- [X] T017 [P] [US2] Ampliar `backend/src/test/java/com/amano/backend/auth/LoginIT.java`: body `{"email":"   ","password":""}` → 400 `title "Validación fallida"` con `properties.fields.email` = "El correo electrónico es obligatorio" y `properties.fields.password` = "La contraseña es obligatoria"; cuenta inexistente y cuenta verificada con contraseña incorrecta → 401 idéntico (`title "No autorizado"`, detail exacto), sin cookie — FR-003, FR-005, SC-002, SC-004
- [X] T018 [P] [US2] Crear `backend/src/test/java/com/amano/backend/auth/LoginServiceTest.java` (unitario con `BCryptPasswordEncoder(4)`, repos mockeados): normalización trim+minúsculas antes del lookup; campos vacíos reportados juntos sin acceso a BD; cuenta inexistente → `InvalidCredentialsException` **y** `passwordEncoder.matches` ejecutado contra el hash dummy (igualamiento de tiempo); contraseña incorrecta → misma excepción; orden: estado de verificación solo se evalúa tras contraseña correcta — research.md §D2, §D3
- [X] T019 [P] [US2] Ampliar `frontend/src/pages/LoginPage.test.jsx`: campos vacíos → mensajes requeridos por campo sin invocar a la red; correo con formato inválido → "Ingresá un correo electrónico válido" y sin envío; 400 → mensaje del servidor aplicado por campo; 401 → `BAD_CREDENTIALS_MESSAGE` con `role="alert"`, contraseña vacía y correo conservado — FR-003, FR-004, FR-005
- [X] T020 [P] [US2] Ampliar `frontend/src/api/authClient.test.js`: 401 → `{status, detail, fields:{}, action:null}`; ≥500 y rechazo de `fetch` → `{network:true}`; asertar el texto exacto de `LOGIN_NETWORK_ERROR_MESSAGE`; cuerpo 4xx no JSON no lanza excepción — FR-009, contracts §Contrato de interfaz

### Implementation for User Story 2

- [X] T021 [US2] Completar en `backend/src/main/java/com/amano/backend/auth/LoginService.java` la validación de campos (email vacío tras normalizar y password nulo/vacío → `FieldValidationException` con los mensajes exactos, ambos juntos y sin consulta a BD) y las ramas de `InvalidCredentialsException`: lookup fallido ejecuta `passwordEncoder.matches(password, dummyHash)` (hash dummy `amano-login-timing-equalizer` generado en el constructor) antes de lanzar; password incorrecto sobre cuenta existente lanza la misma excepción — FR-003, FR-005, research.md §D2, §D3
- [X] T022 [US2] Exportar en `frontend/src/api/authClient.js` `LOGIN_NETWORK_ERROR_MESSAGE = 'No se pudo iniciar sesión. Intenta nuevamente'` y en `frontend/src/components/LoginForm.jsx` las constantes compartidas `BAD_CREDENTIALS_MESSAGE` / `UNVERIFIED_MESSAGE` con el texto exacto del servidor — FR-005, FR-009, research.md §D7
- [X] T023 [US2] Completar en `frontend/src/components/LoginForm.jsx` el manejo de resultados: 400 → `setError(field, {type:'server', message})` por `properties.fields`; 401 → `setValue('password','')`, correo intacto y `BAD_CREDENTIALS_MESSAGE` en `data-testid="login-error"`; red o ≥500 → `LOGIN_NETWORK_ERROR_MESSAGE` reutilizando el botón (ya habilitado por `isSubmitting`); otro detalle → `result.detail || LOGIN_NETWORK_ERROR_MESSAGE` — FR-005, FR-009, research.md §D7

**Checkpoint**: US1 + US2 completas: el rechazo es indistinguible, por campo y sin fugas de existencia de cuentas.

---

## Phase 5: User Story 3 - Cuenta no verificada con oferta de reenvío (Priority: P3)

**Goal**: Conservar FR-014 de 003 en el login real: contraseña correcta sobre cuenta no verificada → 403 con el mensaje de 003 y `properties.action = "RESEND_VERIFICATION"`, sin sesión, y la pantalla ofrece el botón de reenvío de 003; contraseña incorrecta → 401 genérico (el estado no se revela).

**Independent Test**: Cuenta pendiente + contraseña correcta → aviso de 003 con "Reenviar correo de verificación" y cuenta regresiva; misma cuenta con contraseña incorrecta → 401 genérico; el botón de reenvío funciona con el correo ingresado (contrato de 003 intacto).

### Tests for User Story 3 ⚠️ (primero, deben FALLAR)

- [X] T024 [P] [US3] Ampliar `backend/src/test/java/com/amano/backend/auth/LoginIT.java`: cuenta no verificada + contraseña correcta → 403 sin `Set-Cookie`, `title "Cuenta no verificada"`, `detail` exacto "Tu cuenta aún no ha sido verificada. Por favor, revisa tu correo electrónico" y `properties.action: "RESEND_VERIFICATION"`; cuenta no verificada + contraseña incorrecta → 401 con el mensaje genérico de credenciales — FR-006, SC-003, contracts §403
- [X] T025 [P] [US3] Ampliar `frontend/src/pages/LoginPage.test.jsx`: 403 con `RESEND_VERIFICATION` → sección `data-testid="login-unverified"` con `UNVERIFIED_MESSAGE` y `ResendCountdown`; el clic de reenvío se dispara con el correo normalizado y muestra la cuenta "05:00" — FR-006, US3 escenario 2
- [X] T026 [P] [US3] Ampliar `frontend/src/api/authClient.test.js`: el parseo del 403 expone `action: 'RESEND_VERIFICATION'` desde `properties.action` — contracts §Contrato de interfaz

### Implementation for User Story 3

- [X] T027 [US3] Completar en `backend/src/main/java/com/amano/backend/auth/LoginService.java` el chequeo final `user.getEmailVerifiedAt() == null` → `UnverifiedAccountException` (definido en 003, con el mensaje exacto), **después** de validar la contraseña para no revelar el estado — FR-006, research.md §D3
- [X] T028 [US3] Completar en `frontend/src/components/LoginForm.jsx` el manejo del 403 con `action === 'RESEND_VERIFICATION'`: `setUnverifiedEmail(emailNormalizado)` y render del aviso `UNVERIFIED_MESSAGE` junto a `frontend/src/components/ResendCountdown.jsx` (reutiliza el componente y `resendCountdown.js` de 003, con `key` por correo para reiniciar la cuenta regresiva) — FR-006, research.md §D7

**Checkpoint**: FR-014 de 003 preservada en el login real, con la oferta de reenvío funcionando.

---

## Phase 6: User Story 4 - Cerrar sesión (Priority: P4)

**Goal**: Botón "Cerrar sesión" en el dashboard que termina la sesión en el servidor (`POST /api/auth/logout` público e idempotente → 204 + cookie expirada) y navega a `/login` limpiando el estado local.

**Independent Test**: Sesión activa → clic en "Cerrar sesión" → cookie `amano_auth` eliminada (`Max-Age=0`), en `/login`, y `/dashboard` vuelve a redirigir; curl de logout sin sesión → siempre 204.

### Tests for User Story 4 ⚠️ (primero, deben FALLAR)

- [X] T029 [P] [US4] Crear `backend/src/test/java/com/amano/backend/auth/LogoutIT.java`: `POST /api/auth/logout` → 204 sin cuerpo, `Set-Cookie` con `amano_auth=`, `Max-Age=0` y `HttpOnly`; sin cookie previa también 204 (idempotente) — FR-010, SC-005, contracts §logout
- [X] T030 [P] [US4] Ampliar `frontend/src/pages/DashboardPage.test.jsx`: el botón "Cerrar sesión" invoca `fetch('/api/auth/logout', {method:'POST', credentials:'include'})` y navega a `/login` — FR-010, US4 escenario 1

### Implementation for User Story 4

- [X] T031 [US4] Agregar `POST /api/auth/logout` a `backend/src/main/java/com/amano/backend/auth/AuthController.java`: `AuthCookie.expiredCookie(httpRequest.isSecure())` en `Set-Cookie` y `204 No Content` sin cuerpo; público e idempotente (sin sesión es un 204 inofensivo) — FR-010, research.md §D5
- [X] T032 [US4] Agregar `logout()` a `frontend/src/api/authClient.js`: `POST /api/auth/logout` con `credentials:'include'`; devuelve `{ok}` y **nunca** lanza excepción (fallo de red → `{ok:false, network:true}`) — contracts §Contrato de interfaz
- [X] T033 [US4] Agregar el botón "Cerrar sesión" en `frontend/src/pages/DashboardPage.jsx` (nav, junto a "Mi perfil"): `await logout()`, `setUser(null)` y `navigate('/login', {replace:true})` — el estado local se limpia aunque el backend falle — FR-010, research.md §D5
- [X] T034 [US4] Agregar `/api/auth/logout` al `permitAll` de `backend/src/main/java/com/amano/backend/config/SecurityConfig.java` y actualizar las aserciones de visibilidad en `backend/src/test/java/com/amano/backend/auth/SecurityConfigTest.java` (`/api/auth/login` y `/api/auth/logout` accesibles sin sesión; sin cookie válida `/api/users/**` → 401) — contracts §Visibilidad, research.md §D5

**Checkpoint**: Todas las historias funcionales de forma independiente; ciclo de sesión completo (login → acceso → logout).

---

## Phase 7: Polish & Cross-Cutting Concerns

**Purpose**: Regresiones, artefactos y salida hacia Pull Request. Solo los últimos tres tasks quedan pendientes: limpieza de comentarios obsoletos, verificación final previa al PR y apertura del PR hacia `sprint-1`.

- [X] T035 [P] Actualizar `frontend/src/App.test.jsx`: `/profile` sin sesión redirige a la pantalla de login (título "iniciar sesión") y con sesión renderiza el perfil — regresión de 002, FR-011
- [X] T036 [P] Verificar regresiones de 001/003 sin cambios previstos: `RegistrationIT`, `RegistrationVerificationIT`, `EmailVerificationIT`, `EmailResendIT`, `SecurityConfigTest`, `JwtServiceTest`, `RegisterPage.test.jsx`, `CheckEmailPage*.test.jsx` y `ResendCountdown.test.jsx` siguen pasando tras el reemplazo del stub — DoD constitución V
- [X] T037 Revisar coherencia de artefactos SDD con lo implementado (`specs/004-user-login/{plan.md, research.md, data-model.md, contracts/auth-api.md}`) — sin divergencias que documentar — constitución II, DoD
- [X] T038 Corregir los comentarios obsoletos detectados en plan.md §Complexity Tracking (solo comentarios, sin cambios de comportamiento): `frontend/src/auth/ProtectedRoute.jsx` comenta aún "redirected to `/register`" pese a redigir a `/login`, y `backend/src/main/java/com/amano/backend/config/SecurityConfig.java` llama a `/api/auth/login` "provisional stub of feature 003" — constitución X
- [ ] T039 Ejecutar la verificación final previa al PR y dejarla en verde: guía `specs/004-user-login/quickstart.md` (escenarios manuales A–H y regresiones), `cd backend && ./mvnw -B verify` (JaCoCo LINE ≥ 85 %) y `cd frontend && npm ci && npm run lint && npm run test:coverage && npm run build` (V8 ≥ 85 %); sin borrar ni excluir pruebas para alcanzar el umbral — constitución V, XI
- [ ] T040 Abrir el Pull Request de `feat/user-login` hacia `sprint-1` describiendo el reemplazo del login simulado de 003, la conservación de FR-014, el cambio de redirección de `ProtectedRoute` respecto de 001 y la trazabilidad a `specs/004-user-login/`; CI en verde — constitución XII, XIII

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: Sin dependencias — arranca de inmediato.
- **Foundational (Phase 2)**: Arranca de inmediato (T003 y T004 en paralelo); T002 libera la ruta de login. **Bloquea todas las historias.**
- **US1 (Phase 3)**: Depende de Foundational (usa `AuthCookie.sessionCookie` existente; no necesita T004, que es de logout).
- **US2 (Phase 4)**: Depende de US1 (amplía `LoginService.java`, `LoginForm.jsx`, `authClient.js`, `LoginIT.java` y `LoginPage.test.jsx` de US1).
- **US3 (Phase 5)**: Depende de US1 (mismos archivos) y conserva el 401 de US2; independiente de US4.
- **US4 (Phase 6)**: Depende de Foundational (T004 `expiredCookie`) y de US1 (`LoginPage` como destino del logout); T034 toca `SecurityConfig.java`.
- **Polish (Phase 7)**: Depende de las historias deseadas; T038–T040 al final del todo.

### User Story Dependencies

- **US1 (P1)**: Tras Foundational — sin dependencias de otras historias. MVP.
- **US2 (P2)**: Tras US1 (hereda servicio, formulario, cliente y archivos de test).
- **US3 (P3)**: Tras US1 (y con el 401 de US2 ya establecido).
- **US4 (P4)**: Tras Foundational y US1 (destino `/login`); independiente de US2/US3.

### Within Each User Story

- Tests primero: deben compilar y FALLAR antes de implementar (TDD suave) — aquí ya pasan, dado que el código está commiteado.
- Servicio → endpoint → cliente → formulario → pantallas → rutas.
- Los archivos compartidos (`LoginService.java`, `LoginForm.jsx`, `authClient.js`, `LoginIT.java`) se amplían en la historia que introduce cada comportamiento, en el orden US1 → US2 → US3.

### Parallel Opportunities

- T001 y T003/T004 en paralelo (archivos distintos).
- US1: los 4 tasks de tests (T005–T008) corren en paralelo entre sí.
- US2: los 4 tasks de tests (T017–T020) en paralelo.
- US3: los 3 tasks de tests (T024–T026) en paralelo.
- US4: T029 y T030 en paralelo.
- Polish: T035 y T036 en paralelo; T038 es un solo archivo por vez pero ambos comentarios pueden corregirse en paralelo.
- US2 y US4 pueden avanzar en paralelo si se coordinan los archivos compartidos (`authClient.js`).

---

## Parallel Example: User Story 1

```bash
# Lanzar juntos los tests de US1 (archivos distintos):
Task: "T005 Crear LoginIT.java (escenarios 200 + normalización)"
Task: "T006 Crear LoginPage.test.jsx"
Task: "T007 Ampliar authClient.test.js (login)"
Task: "T008 Actualizar ProtectedRoute.test.jsx"

# Luego, implementación en orden de dependencia:
Task: "T009 LoginRequest.java → T010 LoginService.java → T011 AuthController.login"
Task: "T012 authClient.login → T013 LoginForm.jsx → T014 LoginPage.jsx → T015 App.jsx → T016 ProtectedRoute.jsx"
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Completar Phase 1: Setup (T001).
2. Completar Phase 2: Foundational (CRÍTICO — bloquea todo; T002 elimina el stub de 003).
3. Completar Phase 3: US1.
4. **PARAR Y VALIDAR**: login end-to-end en el navegador (quickstart §3 escenarios A, B, F) y `LoginIT` en verde.
5. Demo/entrega: las rutas protegidas vuelven a ser alcanzables con credenciales reales.

### Incremental Delivery

1. Setup + Foundational → base lista.
2. US1 → probar independiente → demo (MVP: acceso real con cookie).
3. US2 → probar independiente → demo (rechazo indistinguible y guía de corrección).
4. US3 → probar independiente → demo (FR-014 de 003 preservada con reenvío).
5. US4 → probar independiente → demo (ciclo de sesión completo).
6. Polish → quickstart + cobertura + artefactos + PR a `sprint-1`.

### Parallel Team Strategy

Con varios desarrolladores:

1. El equipo completa Setup + Foundational junto.
2. Con Foundational listo:
   - Dev A: US1 (bloqueante de US2/US3 por archivos compartidos).
   - Con US1 integrada: Dev A/B → US2 y US3 secuenciales sobre los mismos archivos; Dev C → US4 en paralelo (`AuthController.logout`, `authClient.logout`, dashboard, `SecurityConfig`).
3. Las historias se integran y prueban de forma independiente vía PR a `sprint-1`.

---

## Notes

- `[P]` = archivos distintos, sin dependencias; `[Story]` = trazabilidad US1–US4 (constitución II).
- Mensajes exactos en español definidos por la spec y `contracts/auth-api.md`: no reimprovisar textos ("El correo electrónico o la contraseña son incorrectos", mensajes de campo, mensaje de 003, "No se pudo iniciar sesión. Intenta nuevamente").
- El 401 debe ser idéntico para cuenta inexistente y contraseña incorrecta, **incluido el tiempo de respuesta** (hash dummy, research.md §D2) — SC-002.
- El 403 de cuenta no verificada solo se emite tras validar la contraseña (research.md §D3).
- Cookie de sesión y cierre: solo vía `AuthCookie` (contrato de 001); nada de tokens en `localStorage`.
- Sin migraciones ni dependencias nuevas (T001, research.md §D9).
- Commit después de cada task o grupo lógico con Conventional Commits (constitución XIII); PR hacia `sprint-1`.
- Detenerse en cualquier checkpoint para validar la historia de forma independiente.
