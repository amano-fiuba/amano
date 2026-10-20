# Research: Inicio de sesión de usuario (004)

**Fecha**: 2026-10-20 | **Rama**: `feat/user-login` | **Spec**: [spec.md](./spec.md)

**Propósito**: documentar las decisiones ya tomadas en la implementación existente. No quedaron `NEEDS CLARIFICATION` en el Technical Context del plan: la descripción de entrada fijó stack, archivos y comportamiento.

## D1 — Mecanismo de sesión: reutilizar la cookie `amano_auth` (JWT) de 001

- **Decisión**: el login exitoso emite la misma cookie de sesión `amano_auth` definida en 001 (JWT firmado, HttpOnly, SameSite=Strict, `Max-Age` = `JWT_EXPIRATION`, `Secure` solo en HTTPS), construida con `AuthCookie.sessionCookie(...)` y `JwtService.issueToken(userId)`. El cuerpo de la respuesta tiene la forma de `GET /api/users/me` (`UserAccountResponse`).
- **Rationale**: 001 ya define el contrato de sesión y `JwtCookieAuthenticationFilter` ya lee esa cookie; el gate `verifiedAccount` de 003 y todas las rutas protegidas dependen de ella. Emitir otro mecanismo rompería 001/002/003.
- **Alternativas consideradas**: nueva cookie propia de login (innecesaria, duplicaría contrato); JWT en `Authorization` header (rompería el gate existente de cookie y el proxy de Vite).

## D2 — Rechazo indistinguible de credenciales: mismo 401 + hash dummy

- **Decisión**: cuenta inexistente y contraseña incorrecta lanzan la misma `InvalidCredentialsException` → 401 `{"title":"No autorizado","detail":"El correo electrónico o la contraseña son incorrectos"}` sin `properties` y sin cookie. Cuando la cuenta no existe, igualmente se ejecuta `passwordEncoder.matches(password, dummyHash)` con un hash BCrypt generado en el constructor (`amano-login-timing-equalizer`) para igualar el costo computacional.
- **Rationale**: FR-005/SC-002 de la spec prohíben revelar si el correo está registrado, por contenido y por tiempo de respuesta; BCrypt es intencionalmente lento, por lo que saltarlo delataría la rama.
- **Alternativas consideradas**: responder 404 para correo inexistente (filtra existencia); responder siempre 200 genérico (empeora UX y no es verificable contra el contrato); hash dummy con otro algoritmo (distinto costo → timing distinto).

## D3 — Orden de validación: campos → credenciales → estado de verificación

- **Decisión**: `LoginService.authenticate` valida en este orden: (1) campos vacíos → `FieldValidationException` (400 por campo, sin consulta a BD); (2) lookup + comparación BCrypt → `InvalidCredentialsException` (401); (3) `emailVerifiedAt == null` → `UnverifiedAccountException` (403 con `properties.action = RESEND_VERIFICATION`).
- **Rationale**: conserva FR-014 de 003 (solo se revela el estado de verificación a quien ya conoce la contraseña) y evita consultas de BD con datos incompletos.
- **Alternativas consideradas**: verificar primero el estado (revelaría cuentas no verificadas a terceros); validar formato de correo en servidor (decidido como fuera de alcance: formato inválido cae como cuenta inexistente → 401 genérico).

## D4 — Forma de errores: ProblemDetail RFC 7807 existente

- **Decisión**: reutilizar `ProblemDetailExceptionHandler` sin modificar su estructura: 400 con `properties.fields` (campo → mensaje), 401 con `title`/`detail`, 403 con `properties.action`. Nuevos solo los mensajes puntuales (`InvalidCredentialsException.MESSAGE`).
- **Rationale**: 001/003 ya consumen este formato (contratos `auth-api.md` de 001 y `email-verification-api.md` de 003); el frontend parsea `detail`, `properties.fields` y `properties.action` de forma genérica en `authClient`.
- **Alternativas consideradas**: cuerpo de error propio para login (rompería el parser común de `authClient` y la consistencia exigida por el principio VII).

## D5 — Cierre de sesión: endpoint público e idempotente

- **Decisión**: `POST /api/auth/logout` agregado a la lista `permitAll` de `SecurityConfig`, responde **204** con `Set-Cookie` de `AuthCookie.expiredCookie(...)` (mismo nombre/atributos, valor vacío, `Max-Age=0`). El frontend siempre limpia el estado local y navega a `/login`, incluso si la llamada de red falla.
- **Rationale**: con sesiones stateless no hay estado servidor que destruir; eximirlo de autenticación hace el cierre idempotente y operable desde cualquier estado (incluida una sesión ya vencida), cumpliendo FR-010/SC-005.
- **Alternativas consideradas**: logout autenticado con `401` cuando no hay sesión (rompería la idempotencia pedida); blacklist de tokens (alcance nuevo, requiere persistencia).

## D6 — Redirección de rutas protegidas: `/login` con destino en estado del router

- **Decisión**: `ProtectedRoute` redirige a `<Navigate to="/login" replace state={{ from: location }} />` (antes `/register`); `LoginPage` lee `location.state?.from?.pathname` y vuelve ahí tras el login, con fallback a `/dashboard`. Si ya hay sesión activa, `/login` redirige al mismo destino sin renderizar el formulario (render `null` mientras se resuelve la sesión, para evitar destello).
- **Rationale**: FR-007/FR-011; conserva el patrón de "destino retenido por el gate" ya usado por 001/002, solo cambia el endpoint del gate.
- **Alternativas consideradas**: query param `?next=` (estado del router ya es el mecanismo en uso y no expone la ruta en la URL); recordar en almacenamiento local (alcance nuevo).

## D7 — Frontend: formulario controlado con `react-hook-form` y mensajes centralizados

- **Decisión**: `LoginForm` usa `react-hook-form` (`mode: 'onBlur'`), normaliza el correo con `normalizeEmail` (trim + minúsculas) antes de enviar, deshabilita el botón con `isSubmitting`, muestra errores por campo (cliente y `properties.fields` del servidor), limpia contraseña y conserva el correo en 401, y en 403 con `RESEND_VERIFICATION` renderiza el aviso + `ResendCountdown` de 003. Los mensajes compartidos viven como constantes (`BAD_CREDENTIALS_MESSAGE`, `UNVERIFIED_MESSAGE`, `LOGIN_NETWORK_ERROR_MESSAGE`) para que cliente y servidor no diverjan.
- **Rationale**: `react-hook-form` ya se usa en `RegisterForm` (001) y `ResendCountdown`/`TextField`/`validateEmail` son componentes y utilidades existentes reutilizables sin duplicación (principio I y X).
- **Alternativas consideradas**: control manual del estado (ya resuelto por la dependencia existente); ocultar botón de reenvío propio (duplicaría la lógica de 5 min de 003).

## D8 — Estrategia de pruebas

- **Decisión**: pirámide existente del proyecto: unitario `LoginServiceTest` (todas las ramas sin BD, con `BCryptPasswordEncoder` de costo bajo), integración `LoginIT`/`LogoutIT` con Testcontainers PostgreSQL (contrato completo: cookie, headers, cuerpos), `SecurityConfigTest` para visibilidad pública de los endpoints, y Vitest en frontend (`LoginPage.test.jsx` es la suite principal, más `authClient.test.js`, `DashboardPage.test.jsx`, `ProtectedRoute.test.jsx`, `App.test.jsx`).
- **Rationale**: exige la constitución (principio V, cobertura ≥ 85 % con `mvn verify` y `npm run test:coverage`); coincide con la estructura `*Test`/`*IT`/`*.test.jsx` ya adoptada.
- **Alternativas consideradas**: tests E2E con navegador (no existen en el proyecto; fuera de convención).

## D9 — Sin cambios de datos ni de dependencias

- **Decisión**: ninguna migración Flyway ni dependencia nueva; login solo consulta `UserRepository.findByEmail` sobre el esquema existente.
- **Rationale**: lo fija la descripción de entrada; el esquema de usuarios de 001/003 ya contiene `email`, `password_hash` y `email_verified_at`.
- **Alternativas consideradas**: tabla de sesiones/intentos de login (bloqueo por intentos está fuera de alcance).
