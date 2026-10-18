# Tasks: Registro de usuario

**Input**: Design documents from `/specs/001-user-registration/`

**Prerequisites**: plan.md (required), spec.md (required for user stories), research.md, data-model.md, contracts/, quickstart.md

**Tests**: Incluidos por pedido explícito del usuario y por la constitución (Principio V): primero el test (se escribe y se verifica que FALLA), luego la implementación que lo hace pasar.

**Organization**: Tareas agrupadas por historia de usuario (US1→US2→US3), con etiquetas de módulo para reparto entre personas: **[BE]** backend Java, **[FE]** frontend React, **[INFRA]** Docker Compose / `.env` / configuración. `[P]` = paralelizable (archivos distintos, sin dependencias sobre tareas incompletas).

Referencias de contratos: [contracts/auth-api.md](contracts/auth-api.md), [contracts/users-api.md](contracts/users-api.md); modelo de datos: [data-model.md](data-model.md); escenarios: [quickstart.md](quickstart.md).

## Format: `[ID] [P?] [Story] Description`

- **[P]**: puede ejecutarse en paralelo (archivos distintos, sin dependencias)
- **[Story]**: historia de usuario (US1, US2, US3) — solo en fases de historia
- Etiquetas de módulo `[BE]` / `[FE]` / `[INFRA]` al inicio de la descripción
- Cada tarea referencia FR/SC de la spec y la ruta exacta del archivo

## Path Conventions

- Monorepo web existente: `backend/src/main/java/com/amano/backend/`, `backend/src/test/java/com/amano/backend/`, `backend/src/main/resources/`, `frontend/src/`, `frontend/public/`, `compose.yaml`, `.env.example`
- Tests frontend co-ubicados junto al componente (`*.test.jsx`) o carpeta `__tests__`

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Dependencias, variables de entorno y recursos comunes. Sin dependencias mutuas: todo lo marcado [P] puede arrancar en paralelo y repartirse entre personas.

- [x] T001 [P] **[BE]** Agregar a `backend/pom.xml` las dependencias `spring-boot-starter-security`, `spring-boot-starter-validation` y `io.jsonwebtoken:jjwt-api` + `jjwt-impl` + `jjwt-jackson` (scope runtime), sin fijar versiones ad hoc (gestiona el BOM del parent Spring Boot 4.1.1). Nota de implementación: `jjwt` no está gestionado por el BOM de Spring Boot; se importó `jjwt-bom:0.13.0` bajo `<dependencyManagement>` (única versión explícita, documentada). Cubre FR-007, FR-016 (decisión D1 de research.md).
- [x] T002 [P] **[FE]** Agregar `react-router` y `react-hook-form` a `frontend/package.json` (`npm install react-router react-hook-form`). Cubre FR-013, FR-016 (decisión D8 de research.md).
- [x] T003 [P] **[INFRA]** Incorporar en `compose.yaml` el passthrough de las variables `JWT_SECRET` y `JWT_EXPIRATION=86400` al servicio `backend` (sección `environment`). Cubre FR-016.
- [x] T004 [P] **[INFRA]** Agregar `JWT_SECRET` (placeholder de ejemplo local) y `JWT_EXPIRATION=86400` en `.env.example`, conservando las variables existentes; `.env` real queda fuera del versionado. Cubre FR-016 ( constitution XIII: sin secretos versionados).
- [x] T005 [P] **[FE]** Configurar el proxy de desarrollo en `frontend/vite.config.js`: `server.proxy` de `/api` → `http://localhost:8080` (sin reescritura de path), espejando el proxy de producción de `frontend/nginx.conf` para un origen único con cookies `SameSite=Strict`. Cubre FR-016 (decisión D9).
- [x] T006 [P] **[FE]** Crear el avatar predeterminado como recurso estático en `frontend/public/avatar-default.png` (PNG sencillo, ≤ 100 KB). Cubre FR-010, SC-002.
- [x] T007 **[INFRA]** Verificar y fijar los umbrales de cobertura (tarea explícita pedida por el usuario): confirmar la regla de JaCoCo BUNDLE/LINE ≥ 0.85 (executions `check` en fase `verify`) en `backend/pom.xml` y los `thresholds` de Vitest (`lines: 85, statements: 85`, provider v8) en `frontend/vite.config.js`; no se reducen umbrales ni se agregan exclusiones sin justificación documentada (Constitution V). Verificación realizada (2026-10-18): umbrales confirmados vigentes; `./mvnw -B -DskipTests compile` OK con las dependencias nuevas; `npm run test:coverage` OK (100 % ≥ 85 %). La corrida completa de `mvn verify` con ITs queda para los checkpoints de US y la fase 6 (T039). Cubre todos los SC a nivel de calidad.

**Checkpoint**: dependencias resueltas (`./mvnw`, `npm ci`); variables de sesión en `.env.example` + compose; archivos de cobertura congelados.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Migración, entidades, seguridad, errores y ruteo compartidos. ⚠️ CRITICAL: ninguna historia puede implementarse hasta completar esta fase.

- [x] T008 **[BE]** Crear la migración `backend/src/main/resources/db/migration/V1__create_user_registration.sql` con las tablas y CHECKs exactos de data-model.md: `users` (`first_name` VARCHAR(50) con CHECK `char_length BETWEEN 2 AND 50`; `last_name` NULLABLE con el mismo CHECK cuando no es null; `email` VARCHAR(255) con `CREATE UNIQUE INDEX uq_users_email`; `password_hash` VARCHAR(255); `cbu` VARCHAR(22) NULL con CHECK `cbu ~ '^[0-9]{22}$'`; `terms_accepted_at`, `created_at`, `updated_at` TIMESTAMPTZ NOT NULL), `user_avatars` (`user_id` PK+FK ON DELETE CASCADE; `content_type` NOT NULL; `data` BYTEA NOT NULL CHECK `octet_length(data) <= 2097152`; `created_at`), `user_currencies` (PK compuesta `user_id`+`currency_code`, FK CASCADE, CHECK código en {ARS, USD, EUR}). Nota: se agregó defensa en profundidad `ck_user_avatars_content_type` (CHECK `content_type IN ('image/jpeg','image/png')`), coherente con la fila de content_type del data-model. Verificada con Testcontainers (BackendApplicationIT: "Successfully applied 1 migration ... version v1"). Cubre FR-002, FR-003, FR-004, FR-005, FR-006, FR-009, FR-010, FR-011, FR-012 y SC-004 (adopta las decisiones Q1–Q4 de Clarifications).
- [x] T009 [P] **[BE]** Crear entidades JPA y repositorios en `backend/src/main/java/com/amano/backend/user/`: `User.java` (mapea data-model.md; sin lógica de negocio), `UserAvatar.java` (@Entity sobre `user_avatars`; PK `id` = FK `user_id`, con asociación @OneToOne read-only a `User`), preferencia de monedas como `@ElementCollection` de enum `Currency` con `CurrencyConversion.java` (enum↔código via @Convert), `UserRepository.java` (con `Optional<User> findByEmail(String email)`), `UserAvatarRepository.java`. Validado por Hibernate `ddl-auto=validate` en el IT de Testcontainers. Cubre FR-002, FR-003, FR-004, FR-006, FR-009, FR-010, FR-011.
- [x] T010 [P] **[BE]** Crear `backend/src/main/java/com/amano/backend/common/ProblemDetailExceptionHandler.java` (global @RestControllerAdvice según contracts/auth-api.md §Errores): `MethodArgumentNotValidException`/`HandlerMethodValidationException` → 400 con `properties.fields` (mensaje por campo en español, FR-018), `DataIntegrityViolationException` → 409 "El correo electrónico ya se encuentra registrado" (FR-005, SC-004), `MaxUploadSizeExceededException` → 400/413 con mensaje de tamaño (FR-010); 500 genérico sin detalles internos. Nota de implementación: además, `NoHandlerFoundException`/`NoResourceFoundException` → 404 ProblemDetail propio (`Recurso no encontrado`) para que el handler genérico 500 no absorba los 404 del framework (verificado por `registerEndpointIsReachableWithoutSession` de T012). Cubre FR-005, FR-017, FR-018, FR-019.
- [x] T011 **[BE]** Configurar seguridad y sesión en `backend/src/main/java/com/amano/backend/config/`: `SecurityConfig.java` (FilterChain: CSRF deshabilitado conscientemente, ver plan.md; `POST /api/auth/register` permitAll; `/api/users/**` autenticado; 401 ProblemDetail sin sesión — implementado como `UnauthorizedEntryPoint` interno; filtro de autenticación por cookie — `JwtCookieAuthenticationFilter` nuevo), `JwtProperties.java` (record @ConfigurationProperties: `jwt.secret=${JWT_SECRET}` fail-fast si falta o < 32 bytes; `jwt.expiration` con @DurationUnit(SECONDS)), `JwtService.java` (jjwt HS256, claims `sub`/`iat`/`exp`) y `AuthCookie.java` (Set-Cookie `amano_auth`, HttpOnly, SameSite=Strict, Max-Age=86400, Secure según HTTPS); bean `BCryptPasswordEncoder`. Colateral documentado: `HealthControllerTest` ahora @Import de la SecurityConfig real (la starter-security está en el classpath) y `BackendApplicationIT` requiere `jwt.secret` de prueba. Cubre FR-007, FR-016 y SC-005 (la ocultación visual del campo FR-008 es responsabilidad del frontend: T022/T029).
- [x] T012 **[BE]** Escribir primero (red ⇒ deben fallar) los tests de sesión (depende de T011: requieren `SecurityConfig`/`JwtService` existentes, no paralelizable): `backend/src/test/java/com/amano/backend/auth/JwtServiceTest.java` (firma/vía/claims y expiración de 86400) y `backend/src/test/java/com/amano/backend/auth/SecurityConfigTest.java` (`/api/users/me` sin cookie → 401 ProblemDetail; `/api/auth/register` permitAll). Cubre FR-007, FR-016, SC-005.
- [x] T013 [P] **[FE]** Test-first de validaciones compartidas: escribir `frontend/src/validation/registerValidation.test.js` con todos los casos de reglas del spec y verificar que falla: nombre 2–50 tras trim (FR-002/Q4); apellido opcional 2–50 (FR-003/Q4); formato email + trim + case-insensitivo (FR-004); contraseña 8–64 con ≥1 mayúscula/minúscula/dígito/especial (FR-006 + Q2 max 64); CBU normalizado (espacios/guiones → 22 dígitos, Q3); imagen jpg/jpeg/png ≤ 2 MB (FR-010); mensajes en español.
- [x] T014 **[FE]** Implementar `frontend/src/validation/registerValidation.js` (funciones puras exportadas) para que la suite T013 pase (24/24 en verde). Cubre los mismos FR del T013.
- [x] T015 **[FE]** Preparar el ruteo y sesión en `frontend/src/main.jsx`, `frontend/src/App.jsx`, `frontend/src/auth/AuthContext.jsx` y `frontend/src/auth/ProtectedRoute.jsx`: AuthContext obtiene la sesión vía `GET /api/users/me` con `fetch` directo (credentials: include) mientras el cliente de API de US1 aún no existe; rutas protegidas `/dashboard` (creado en US1) con redirect a `/register` ante 401. Notas: `main.jsx` no requirió cambios (App monta router + AuthProvider); `App` exporta `AppRoutes` para pruebas con `MemoryRouter`; `/register` y `/dashboard` quedan con placeholders vacíos de US1 (T023/T024); se agregaron `AuthContext.test.jsx` y `ProtectedRoute.test.jsx` para cubrir el código nuevo (lint clean, cobertura 98,7 % ≥ 85 %). Cubre FR-016, FR-021, SC-002.

**Checkpoint**: migración aplicada por Flyway al boot; seguridad y manejo de errores de la API listos; validaciones compartidas probadas; router/protecciones colocados. Las historias pueden comenzar.

---

## Phase 3: User Story 1 — Registro básico con datos obligatorios (Priority: P1) 🎯 MVP

**Goal**: Un nuevo usuario completa los campos obligatorios (FR-001), queda en sesión automática y llega al dashboard.

**Independent Test**: `docker compose up` + `http://localhost:8081/register`, completar campos obligatorios (incluido consentimiento de T&C) → dashboard con el perfil visible. Ver quickstart.md §2. Entrega por sí sola el valor mínimo: acceso a la plataforma (SC-001, SC-002).

### Tests for User Story 1 (⚠️ escribir primero y verlos FALLAR)

- [ ] T016 [P] [US1] **[BE]** Escribir `backend/src/test/java/com/amano/backend/auth/RegistrationIT.java` (Testcontainers, PostgreSQL real; Constitution V) replicando contracts/auth-api.md: `POST /api/auth/register` multipart → 201 con cuerpo de perfil SIN password (`currencies` devuelta según el contrato — vacía cuando no se envían y el cliente aplica ARS por defecto), `Set-Cookie amano_auth` (HttpOnly, SameSite=Strict, Max-Age=86400) y `GET /api/users/me` reutilizando la cookie; 409 exacto con "El correo electrónico ya se encuentra registrado" ante correo registrado (incluye mismas variaciones de mayúsculas/espacios); 400 ProblemDetail ante campos inválidos o `acceptTerms != "true"` con `properties.fields` por campo; `GET /api/users/me` sin cookie → 401. Cubre FR-001, FR-002, FR-004, FR-005, FR-006, FR-012, FR-014, FR-016, FR-018, FR-021, FR-022, SC-001, SC-002, SC-004.
- [ ] T017 [P] [US1] **[FE]** Escribir `frontend/src/pages/RegisterPage.test.jsx` antes del render: botón se deshabilita al iniciar el envío y solo se emite una petición (FR-015); submit válido (fetch mockeado 201) → navega a dashboard y expone la sesión (FR-016); 409 → muestra "El correo electrónico ya se encuentra registrado" conservando los valores completados (FR-018); fetch que rechaza (sin red) → muestra "No se pudo completar el registro. Intenta nuevamente" y se rehabilita el botón (FR-019). Mock del módulo `frontend/src/api/authClient.js` (estilizado por contracts/auth-api.md).

### Implementation for User Story 1

- [ ] T018 [US1] **[BE]** Implementar `backend/src/main/java/com/amano/backend/auth/RegistrationService.java` (transaccional, atómico: FR-019; nada se persiste si falla): normaliza `email` con `.trim().toLowerCase()` (FR-004) y recorta nombre/apellido antes de validar y guardar (FR-002/FR-003, Q4); valida política de contraseña 8–64 (FR-006 + Q2); exige `acceptTerms=true` (FR-012); hashea con `BCryptPasswordEncoder` (FR-007); aplica default de monedas `ARS` solo en lectura, no persiste fila por vacío (FR-011, Q1); CBU normalizado espacios/guiones antes de validar 22 dígitos (FR-009, Q3); captura UNIQUE email → 409 vía T010 (FR-005).
- [ ] T019 [US1] **[BE]** Implementar `backend/src/main/java/com/amano/backend/auth/AuthController.java`: `POST /api/auth/register` multipart según contract (partes `firstName`, `lastName`, `email`, `password`, `confirmPassword`, `acceptTerms`, `currencies`, `cbuCvu` y `avatar` opcional), Bean Validation del DTO `RegisterRequest.java`, delegación a `RegistrationService`, respuesta 201 con `Location: /api/users/me` + Set-Cookie vía `AuthCookie` + cuerpo sin credenciales. Cubre FR-001, FR-014, FR-016, FR-021.
- [ ] T020 [US1] [P] **[BE]** Implementar `backend/src/main/java/com/amano/backend/user/UserController.java` + `UserMapper.java` con el endpoint autenticado `GET /api/users/me` según contracts/users-api.md: dueño de la sesión (cookie), cuerpo con `id`, `firstName`, `lastName`, `email`, `currencies` (si vacío, el frontend interpreta ARS), `cbuCvu` del propio usuario (FR-009), `hasCustomAvatar`. Sin credenciales (FR-007). Cubre FR-009, FR-021, SC-002.
- [ ] T021 [US1] [P] **[FE]** Implementar `frontend/src/api/authClient.js`: `register(formData)` compone el multipart y llama `fetch('/api/auth/register', {method:'POST', credentials:'include'})`; `me()` con credenciales; parser de ProblemDetail que devuelve `{status, detail, fields}`; error de red o 5xx mapea al mensaje "No se pudo completar el registro. Intenta nuevamente" (FR-019). Cubre FR-016, FR-018, FR-019.
- [ ] T022 [US1] **[FE]** Implementar `frontend/src/components/RegisterForm.jsx` con `react-hook-form` (`mode:'onBlur'` y `onChange` para coincidencia de contraseña/CBU — FR-013): campos obligatorios `firstName`, `email`, `password`, `confirmPassword`, `acceptTerms` (FR-001, FR-012); reglas vía `registerValidation.js` (T014); deshabilitar el botón inmediatamente al enviar (FR-015); mensajes por campo desde `ProblemDetail.fields` y mensaje de red/fallo (FR-018, FR-019) sin limpiar los datos ya ingresados; contraseña oculta por defecto (FR-008).
- [ ] T023 [US1] **[FE]** Implementar `frontend/src/pages/RegisterPage.jsx`: montar `RegisterForm`, al 201 navegar a `<Dashboard/>` y poblar `AuthContext` con el perfil (login automático, FR-016); placeholder local de estados de carga. SC-001 (flujo completo < 1 min).
- [ ] T024 [US1] [P] **[FE]** Implementar `frontend/src/pages/DashboardPage.jsx` (pantalla protegida de llegada): consumir la sesión de `AuthContext` (y refrescar con `me()`), renderizando nombre, email, monedas y avatar (`avatarUrl` si `hasCustomAvatar`, si no `/avatar-default.png`). Cubre FR-016, FR-021, SC-002 (endpoint ya disponible desde T020).
- [ ] T025 [US1] **[BE]** Ejecutar la suite completa del backend en verde: `cd backend && ./mvnw -B verify` con los IT de T016 pasando (Testcontainers valida la migración de T008, unicidad de email y sesión); corregir lo que falte en `RegistrationService`/`AuthController` hasta verde. Cubre los mismos FR/SC que T016 (cierre del MVP).

**Checkpoint**: MVP funcional: registro con datos obligatorios → sesión automática → dashboard con perfil; demoable y validable independientemente (quickstart §2).

---

## Phase 4: User Story 2 — Validaciones en vivo y manejo de errores de envío (Priority: P2)

**Goal**: Toda validación ocurre en vivo (< 2 s), sin limpiar datos; sin dobles envíos; fallos (validación, 409, red, 5xx) rehabilitan el botón.

**Independent Test**: quickstart.md §3 y §4: correo duplicado con otra escritura → 409 con el mensaje y datos intactos; en vivo: formatos inválidos al perder foco; doble clic → una sola petición; sin red → mensaje + rehabilitación; abandono sin enviar → nada persiste (SC-006).

### Tests for User Story 2 (⚠️ escribir primero)

- [ ] T026 [P] [US2] **[BE]** Escribir `backend/src/test/java/com/amano/backend/auth/ProblemDetailExceptionHandlerTest.java` ANTES de los ajustes finales: 400 con mensajes exactos por campo en español (FR-018); 409 ProblemDetail "El correo electrónico ya se encuentra registrado" (FR-005/SC-004) proveniente del índice UNIQUE; 400/413 al exceder el límite de multipart (FR-010); 500 sin stacktrace ni SQL (Constitution VIII). Cubre también FR-017 (botón re-habilitado es comportamiento cliente, pero la respuesta otorga esa info).
- [ ] T027 [P] [US2] **[FE]** Escribir `frontend/src/components/RegisterForm.validation.test.jsx` ANTES de los refinamientos con `fakeTimers` de Vitest: mensajes en vivo (blur/change) aparecen en < 2 s sin limpiar el resto del formulario (FR-013/FR-018, SC-003); CBU con espacios/guiones se normaliza y valida 22 dígitos (Q3); nombre/apellido se trimean antes de medir (Q4); doble submit rápido ⇒ una sola llamada al cliente (FR-015); ante 400/409/fallo de red el botón se rehabilita de inmediato (FR-017); si el usuario abandona sin enviar nada persiste (no llamada de persist, FR-020/SC-006).

### Implementation for User Story 2

- [ ] T028 [US2] **[BE]** Agregar a `backend/src/main/resources/application.properties` los límites `spring.servlet.multipart.max-file-size=2MB` y `spring.servlet.multipart.max-request-size=3MB`, y alinear `ProblemDetailExceptionHandler` con el mapeo 400/413 definido en los tests de T026 (frontend ya valida en vivo, FR-013 — la validación de UI no sustituye el backend, FR-022). Cubre FR-010, FR-018, FR-022.
- [ ] T029 [US2] [P] **[FE]** Implementar `frontend/src/components/TextField.jsx` (componente reutilizable): label, input con `type=password` oculto por defecto (FR-008), estado de error con `aria-invalid` + `role=alert` (accesible), resaltado visual del campo invalidado (FR-018), id/name por campo. [P] respecto de T030 (archivos distintos).
- [ ] T030 [US2] **[FE]** Refactorizar `frontend/src/components/RegisterForm.jsx` para usar `TextField` (T029) y consolidar la validación en vivo con `registerValidation.js` (T014): coincidencia de contraseñas en vivo, mensajes por campo, rehabilitación del botón ante cualquier error (FR-017), no se limpian datos validados (FR-018). Dejar la suite del T027 en verde.
- [ ] T031 [US2] **[BE]** Ejecutar `cd backend && ./mvnw -B test` con la suite del T026 en verde y re-verificar los IT de T016 (regresión: el caso duplicado/remoto sigue pasando) `[recién tras T026 y T028]`. Cubre los requisitos del T026 (FR-005, FR-010, FR-018, FR-022, SC-004).

**Checkpoint**: US1 + US2 operan de forma robusta: feedback en vivo < 2 s (SC-003), sin dobles envíos ni persistencias accidentales (SC-005, SC-006).

---

## Phase 5: User Story 3 — Datos opcionales de perfil (Priority: P3)

**Goal**: Apellido opcional, avatar JPG/JPEG/PNG ≤ 2 MB (o default), CBU/CVU 22 dígitos privado y monedas ARS/USD/EUR multi (default ARS).

**Independent Test**: quickstart.md §5: registro con y sin datos opcionales; binario servido por `GET /api/users/{id}/avatar` con `Content-Type` exacto y `X-Content-Type-Options: nosniff`; default `/avatar-default.png` sin fila en BD; CBU normalizado.

### Tests for User Story 3 (⚠️ escribir primero)

- [ ] T032 [P] [US3] **[BE]** Escribir `backend/src/test/java/com/amano/backend/auth/RegistrationProfileIT.java` (Testcontainers) ANTES del código de avatar: con JPG real ≤ 2 MB → 201 `hasCustomAvatar:true`, `GET /api/users/{id}/avatar` 200 con `Content-Type: image/jpeg` y `nosniff` (FR-010, D6); con PNG real → `image/png`; sin avatar → 201 `hasCustomAvatar:false` y `/avatar` 404 (default en frontend); archivo con MIME declarado mentiroso (magic bytes distintos) → 400 con mensaje de formato (FR-010/FR-022, D6); avatar > 2 MB → 400/413 con mensaje de tamaño; `currencies` ["ARS","USD","EUR"] persistidas y devueltas (Q1), CBU con guiones/espacios → fila con 22 dígitos (Q3); `/api/users/me` sin sesión → 401 (CBU privado FR-009). Cubre FR-003, FR-009, FR-010, FR-011, FR-021, FR-022 y los cinco escenarios de US3.

### Implementation for User Story 3

- [ ] T033 [US3] **[BE]** Implementar la validación de imagen y la persistencia del avatar: nuevo `backend/src/main/java/com/amano/backend/user/AvatarValidator.java` (inspección de MIME real + magic bytes JPEG FF D8 FF / PNG 89 50 4E 47, rechazo 400 con mensaje de formato; tamaño > 2 MB rechazado) y extender `backend/src/main/java/com/amano/backend/auth/RegistrationService.java` para guardar `user_avatars` (`content_type`/`data`) asociado LAZY sin cargar binarios en lecturas habituales (D5). Cubre FR-010, FR-022.
- [ ] T034 [US3] **[BE]** Extender `backend/src/main/java/com/amano/backend/user/UserController.java` con `GET /api/users/{id}/avatar` según contracts/users-api.md: autenticado, 200 binario con `Content-Type` exacto + `X-Content-Type-Options: nosniff` + `Cache-Control: private, max-age=3600`; 404 sin avatar (el frontend usará el default); completar la persistencia de `currencies` multi y `cbu` normalizado en el flujo del `RegistrationService` (no retiro las validaciones del T018, solo sumo partes opcionales). Cubre FR-003, FR-009, FR-010, FR-011.
- [ ] T035 [P] [US3] **[FE]** Escribir antes de la implementación `frontend/src/components/AvatarUploader.test.jsx` y `frontend/src/components/CurrencySelector.test.jsx`: al elegir imagen, validación en vivo de formato/tamaño con mensajes en español (FR-010/FR-013); multiselección de ARS/USD/EUR, y vacío ⇒ no persiste ninguna (default ARS en lectura, Q1/FR-011); campos opcionales eliminados no fuerzan bloqueo del envío (el resto de la US1 sigue habilitada).
- [ ] T036 [US3] **[FE]** Implementar `frontend/src/components/AvatarUploader.jsx` y `frontend/src/components/CurrencySelector.jsx` para pasar la suite del T035, y conectarlos en `frontend/src/components/RegisterForm.jsx` como campos opcionales (los vacíos nunca bloquean el envío del resto de los campos, FR-003, FR-009, FR-010, FR-011); el default del avatar se resuelve en la vista (`/avatar-default.png` cuando `hasCustomAvatar:false`, decisión D5).
- [ ] T037 [US3] [P] **[FE]** Refinar `frontend/src/pages/DashboardPage.jsx` con los datos opcionales concretos: apellido si existe (FR-003), CBU/CVU del propio usuario (FR-009), monedas con default (FR-011/Q1), avatar real (`GET /api/users/{id}/avatar` solicitado como `<img src>` con cookies) o `/avatar-default.png`. Cubre FR-021, SC-002.
- [ ] T038 [US3] **[BE]** Ejecutar `cd backend && ./mvnw -B verify` con el IT del T032 en verde (y los demás ITs), corrigiendo `AvatarValidator`/`RegistrationService`/`UserController` hasta la suite completa en verde. Cubre los mismos FR/SC que T032 (cierre de US3).

**Checkpoint**: las tres historias funcionan de forma independiente; los datos opcionales del perfil (avatar, CBU privado, monedas) están disponibles en el perfil tras el registro con sus defaults correctos.

---

## Phase 6: Verificación Final (Quickstart end-to-end)

**Purpose**: Validación integrada con Docker Compose + cierre de calidad constitucional.

- [ ] T039 **[BE]** Suite completo en verde: `cd backend && ./mvnw -B verify` (unitarias + ITs con Testcontainers + JaCoCo ≥ 85 % líneas, umbrales de T007 vigentes). Cubre todos los FR/SC del backend (Constitution V). NOTA: corrobora a nivel global lo ya validado por historia; los suites de historia ya corrieron verdes (T025, T031, T038).
- [ ] T040 [P] **[FE]** Suite completo en verde: `cd frontend && npm ci && npm run lint && npm run test && npm run test:coverage && npm run build` (Vitest ≥ 85 % líneas/statements con `@vitest/coverage-v8`, umbrales de T007; build Vite 8/Rolldown). Cubre todos los FR/SC del frontend (Constitution V).
- [ ] T041 **[INFRA]** Levantar todo con `docker compose up --build` y ejecutar los escenarios manuales de `specs/001-user-registration/quickstart.md` §1–§5: registro feliz → dashboard con perfil y cookie HttpOnly/SameSite=Strict visible en DevTools; correo duplicado en otra escritura → mensaje "El correo electrónico ya se encuentra registrado" en < 2 s; dashboard con avatar default / monedas ARS si no se cargaron; doble clic → una petición (Network abierta); CBU con separadores → 22 dígitos; verificar en logs del backend que NUNCA aparece ninguna contraseña ni hash (SC-005); verificar saludable (`db` healthy, `backend` `depends_on`). Cubre SC-001, SC-002, SC-003, SC-004, SC-006 y FR-015..FR-022.
- [ ] T042 [P] **[BE]** Auditoría de trazabilidad FR/SC: verificar que cada FR-001..FR-022 y cada SC-001..SC-007 esté cubierto por al menos un test automatizado o un escenario de quickstart; listar las evidencias (IDs de tests) en la descripción del PR. Cubre Constitution IX/XII.
- [ ] T043 **[INFRA]** Cierre constitucional: abrir Pull Request `feat/user-registration` → `sprint-n` (Constitution XII) con Conventional Commits (Constitution XIII); sin artefactos de debug ni secretos versionados (`JWT_SECRET` solo en `.env` no versionado y placeholder en `.env.example`); sin modificación de migraciones ya integradas. Cubre done/DoD.

---

## Dependencies & Execution Order

### Phase Dependencies

- **Phase 1 (Setup)**: sin dependencias internas; T001–T006 paralelizables y repartibles; T007 al cierre.
- **Phase 2 (Foundational)**: depende de Phase 1. Orden: T008 → T009 (y T010 en paralelo con T009) → T011 (tras T001) → T012 (tras T011); [FE] T013 → T014, y T015 tras T002.
- **Phase 3 (US1)**: depende del checkpoint de Phase 2. Los tests T016/T017 corren en paralelo por módulo; luego implementaciones: backend T018 → T019 → (T020 en paralelo) y frontend T021 → T022 → T023/T024; cierre T025.
- **Phase 4 (US2)**: depende de US1 integrada (extiende `RegisterForm`/configuración multipart). Tests T026/T027 al inicio en paralelo; luego T028/T029/T030 y cierre T031.
- **Phase 5 (US3)**: Tests T032/T035 pueden redactarse en paralelo con US2 (archivos propios); la implementación (T033/T034/T036/T037/T038) requiere `RegistrationService` de US1 completado.
- **Phase 6 (Verificación)**: depende de todas las historias; T039/T040/T042 paralelizables (módulos distintos); T041 al final antes del PR; T043 último.

### User Story Dependencies

- **US1 (P1)**: tras Phase 2; independiente del resto.
- **US2 (P2)**: se apoya en el `RegisterForm` de US1 integrado (mismo archivo en FASES posteriores); independiente a nivel de pruebas (T027) pero su integración de UI (T030) depende de US1.
- **US3 (P3)**: requiere migración (T008) y `RegistrationService` (T018); sus tests (T032, T035) son paralelizables con US2.

### Within Each User Story

- Tests primero (con referencias FR/SC citadas en cada tarea de test): deben fallar antes de la implementación correspondiente.
- Backend: migración/entidades → entrada/validación/servicio → controller/cookie → endpoint → tests en verde.
- Frontend: validaciones compartidas → cliente API → form → página/dashboard → componentes opcionales.
- Historia completa ⇒ checkpoint independiente (quickstart correspondiente) antes de avanzar.

### Parallel Opportunities (reparto entre varias personas)

- Phase 1 completa es paralelizable (T001–T006); T007 al cierre por persona dedicada.
- Phase 2: T009 y T010 en paralelo; mapa [BE]/[FE] corre en flujos independientes.
- Por historia: los tests BE y FE (T016/T017, T026/T027, T032/T035) se escriben en paralelo.
- Por módulo con el contrato congelado: una persona [BE] avanza US1-backend mientras otra [FE] hace US1-frontend usando contracts/*.md como frontera.

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Phase 1 (Setup) — reparto en paralelo (T001–T006; T007 al cierre).
2. Phase 2 (Foundational) — obligatoria, bloquea las historias.
3. Phase 3 (US1) → **STOP y validar** con docker compose + quickstart §2.
4. US2 → PR a `sprint-n` (Constitution III): entrega revisable por historia.
5. US3 → validar → PR.
6. Phase 6: verificación end-to-end (quickstart) y PR de feature.

### Incremental Delivery

1. Phases 1–2 → fundación lista.
2. US1 → MVP demoable → PR.
3. US2 → robustez en vivo → PR.
4. US3 → datos opcionales → PR.
5. Phase 6 → validación integral y cierre constitucional.

### Parallel Team Strategy (reparto pedido por el usuario)

- **Persona A [BE]**: T001 → T008 → T009/T010 → T011/T012 → T016,T018–T020,T025 → T026,T028,T031 → T032,T033,T034,T038 → T039,T042.
- **Persona B [FE]**: T002/T005/T006 → T013–T015 → T017,T021–T024 → T027,T029,T030 → T035–T037 → T040.
- **Persona C [INFRA]**: T003/T004/T007 → soporte de compose → T041 → T043, y revisión de PRs de las personas A y B.
- Los checkpoints (fin de fase/historia) detienen el flujo de la persona correspondiente hasta validar en verde su parte.

---

## Notes

- Tests primero: la tarea de test debe fallar antes de implementarse el código que la hace pasar (constitution V).
- `[P]` = archivos distintos sin dependencias pendientes; solo esas tareas corren simultáneamente por personas distintas.
- Referencias canónicas: spec.md (FR-001..FR-022, SC-001..SC-007 y Q1–Q4 de Clarifications), plan.md y research.md (D1–D10), data-model.md (tablas y CHECKs), contracts/*.md (contratos congelados), quickstart.md (escenarios de validación).
- La integración sigue la constitución: historias una a una vía Pull Request a `sprint-n`, umbrales de cobertura ≥ 85 % vigentes (T007), Conventional Commits, sin secretos en el repositorio.
