# Implementation Plan: Inicio de sesión de usuario

**Branch**: `feat/user-login` | **Date**: 2026-10-20 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/004-user-login/spec.md`

**Note**: El código ya está implementado y testeado en la rama actual. Este plan **documenta la implementación existente**; no propone una nueva ni modifica código.

## Summary

Login real con correo y contraseña que reemplaza al `SimulatedLoginController` provisorio de 003: `LoginService` valida y normaliza la entrada, compara BCrypt (con hash dummy para igualar tiempos) y distingue tres rechazos (400 por campo, 401 genérico, 403 de cuenta no verificada con `RESEND_VERIFICATION` de 003); `AuthController` expone `POST /api/auth/login` (200 + cookie `amano_auth` + perfil) y `POST /api/auth/logout` (204 público e idempotente). En el frontend, la ruta `/login` (`LoginPage` + `LoginForm`) reutiliza `TextField`, `ResendCountdown` y las utilidades de validación de 001/003, `authClient` centraliza la llamada y el mensaje de error de red, y `ProtectedRoute` ahora redirige a `/login` conservando la ruta intentada. Sin migraciones ni dependencias nuevas.

## Technical Context

**Language/Version**: Java 25 (backend); JavaScript con React 19.2 (frontend)

**Primary Dependencies**: Spring Boot 4.1.1 (Web MVC, Security, Validation, Data JPA, Flyway, Mail), JJWT 0.13.0, BCrypt de Spring Security; React 19.2, react-router 8.4, react-hook-form 7.90, Vite 8.3 (proxy `/api` → `:8080`)

**Storage**: PostgreSQL con JPA + Flyway (esquema existente de 001/002/003; **sin migraciones nuevas** — no hay tablas nuevas: login solo lee `users`)

**Testing**: JUnit 5 + Spring Boot Test + Testcontainers (`LoginIT`, `LogoutIT`, `SecurityConfigTest`) y unitario `LoginServiceTest`; Vitest + React Testing Library en frontend (`LoginPage.test.jsx`, `authClient.test.js`, `DashboardPage.test.jsx`, `ProtectedRoute.test.jsx`, `App.test.jsx`); cobertura JaCoCo (`mvn verify`) y Vitest (`npm run test:coverage`), umbral 85 %

**Target Platform**: Servidor Linux vía Docker Compose; navegador web (desktop)

**Project Type**: Aplicación web monorepo (backend REST + frontend SPA)

**Performance Goals**: SC-001 login completo < 10 s; igualamiento de tiempo entre cuenta inexistente y contraseña incorrecta (comparación BCrypt dummy)

**Constraints**: Sin dependencias nuevas ni migraciones; cobertura ≥ 85 % en ambos módulos; mensajes en español; respuestas sin revelar existencia de cuentas ni detalles internos

**Scale/Scope**: 2 endpoints (`POST /api/auth/login`, `POST /api/auth/logout`), 1 pantalla nueva (`/login`), ~16 archivos modificados/agregados en backend y frontend según `git diff f6285f1..HEAD`

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| # | Principio | Estado | Evidencia |
|---|-----------|--------|-----------|
| I | Arquitectura y separación de responsabilidades | PASS | Reutiliza `AuthCookie`, `JwtService`, `ProblemDetailExceptionHandler`, `SecurityConfig` y los componentes frontend existentes (`TextField`, `ResendCountdown`, `authClient`); sin refactorizaciones ajenas al alcance |
| II | Desarrollo guiado por especificaciones | PASS | `specs/004-user-login/spec.md` con FR/SC trazables; plan documenta la implementación existente |
| III | Entrega incremental por historia | PASS | Historia única acotada (login) en rama propia `feat/user-login`, commits incrementales Conventional Commits (`feat`, `test`, `docs`) |
| IV | Alcance controlado y fidelidad al backlog | PASS | Sin alcance nuevo: fuera recuperación de contraseña, recordarme, bloqueo de intentos, OAuth, 2FA; sin dependencias ni migraciones |
| V | Calidad, pruebas y cobertura | PASS | `LoginServiceTest` (unit), `LoginIT`/`LogoutIT` (Testcontainers), tests de `LoginPage`, `authClient`, `DashboardPage`, `ProtectedRoute`, `App`; verificación con `mvn -B verify` y `npm run test:coverage` |
| VI | Integridad de las reglas de negocio | PASS | Conserva FR-014 de 003 (403 con `RESEND_VERIFICATION`; contraseña antes que el estado de verificación) |
| VII | Contratos REST y compatibilidad | PASS | Login/logout documentados en `contracts/auth-api.md` de esta feature; cookie `amano_auth` y perfil con forma de `GET /api/users/me` según contrato de 001; `logout` en lista `permitAll` |
| VIII | Validación y manejo de errores | PASS | Validación en ambos bordes (cliente y servidor), errores por campo 400, ProblemDetail RFC 7807, sin exponer internals ni existencia de cuentas |
| IX | Seguridad y control de acceso | PASS | Cookie `amano_auth` HttpOnly/SameSite=Strict (001); comparación BCrypt con hash dummy; gate `verifiedAccount` de 003 intacto; `logout` público e idempotente sin asumir sesión |
| X | Código mantenible y convenciones | PASS | Convenciones existentes (`com.amano.backend`, `*Test`/`*IT`, `*.test.jsx`); sin imports huérfanos ni código de depuración; un comentario obsoleto menor (ver Complexity Tracking) |
| XI | Entorno reproducible e integración continua | PASS | Sin cambios en build ni dependencias; verifica con los comandos ya configurados (`mvn -B verify`, `npm ci && npm run lint && npm run test:coverage && npm run build`) |
| XII | Revisión por pares e integración controlada | PENDIENTE (posterior) | PR de `feat/user-login` hacia `sprint-n`; aún no abierto — corresponde a la fase de entrega, no a este plan |
| XIII | Commits y trazabilidad | PASS | Historial `feat:`/`test:`/`docs:` descriptivo y acotado a la historia |

**Gate resultado**: PASS (principio XII pendiente por naturaleza: se cumple al abrir el PR, fuera del alcance de este plan). No hay violaciones que justificar en Complexity Tracking.

## Project Structure

### Documentation (this feature)

```text
specs/004-user-login/
├── plan.md              # Este archivo (/speckit.plan)
├── research.md          # Fase 0
├── data-model.md        # Fase 1
├── quickstart.md        # Fase 1
├── contracts/
│   └── auth-api.md      # Fase 1: contratos de login/logout
├── checklists/
│   └── requirements.md  # /speckit.specify
└── tasks.md             # Fase 2 (/speckit.tasks — no lo crea /speckit.plan)
```

### Source Code (repository root)

```text
backend/src/main/java/com/amano/backend/
├── auth/
│   ├── AuthController.java      # M: POST /api/auth/login (200+cookie+perfil), POST /api/auth/logout (204)
│   ├── LoginRequest.java        # A: record(email, password)
│   └── LoginService.java        # A: validación, normalización, BCrypt dummy, excepciones
├── common/
│   ├── InvalidCredentialsException.java  # M: MESSAGE 401 nuevo
│   ├── FieldValidationException.java     # existente (400 por campo)
│   ├── UnverifiedAccountException.java   # existente (403 de 003)
│   └── ProblemDetailExceptionHandler.java# existente: 400/401/403 con properties
└── config/
    ├── AuthCookie.java          # M: expiredCookie() para logout
    └── SecurityConfig.java      # M: permitAll incluye /api/auth/logout

backend/src/test/java/com/amano/backend/
├── auth/LoginServiceTest.java   # A: unitario (normalización, dummy hash, orden de checks)
├── auth/LoginIT.java            # R: antes SimulatedLoginIT (003) — contrato 200/400/401/403
├── auth/LogoutIT.java           # A: 204 + Max-Age=0
└── auth/SecurityConfigTest.java # M: visibilidad pública de /api/auth/login|logout

# Eliminados:
# backend/src/main/java/com/amano/backend/verification/SimulatedLoginController.java
# backend/src/test/java/com/amano/backend/verification/SimulatedLoginIT.java

frontend/src/
├── api/authClient.js            # M: login(), logout(), LOGIN_NETWORK_ERROR_MESSAGE
├── components/LoginForm.jsx     # A: formulario, manejo de 400/401/403/errores de red
├── pages/LoginPage.jsx          # A: ruta /login, redirect si hay sesión, enlace a /register
├── App.jsx                      # M: <Route path="/login">
├── auth/ProtectedRoute.jsx      # M: redirige a /login con state.from (antes /register)
├── pages/DashboardPage.jsx      # M: botón "Cerrar sesión"
└── reutiliza: components/TextField.jsx (show/hide),
               components/ResendCountdown.jsx + resendCountdown.js,
               validation/registerValidation.js (validateEmail/normalizeEmail)

frontend/src/**/*.test.jsx|js   # M/A: LoginPage, DashboardPage, ProtectedRoute, App, authClient tests
```

**Structure Decision**: monorepo web existente (Opción 2: `backend/` + `frontend/`); la feature no altera la estructura — solo agrega archivos dentro de los paquetes ya existentes (`auth/`, `common/`, `config/` en backend; `pages/`, `components/`, `api/`, `auth/` en frontend).

## Complexity Tracking

No hay violaciones de la constitución que justificar. Observación menor de mantenimiento (sin cambio de alcance):

| Elemento | Detalle | Acción sugerida |
|----------|---------|-----------------|
| Comentario obsoleto | `ProtectedRoute.jsx` comenta aún "redirected to `/register`" y `SecurityConfig.java` llama a `/api/auth/login` "provisional stub of feature 003" | Corrección de comentario en la fase de tareas/revisión si se desea; no afecta comportamiento |
