# Implementation Plan: Verificación de correo electrónico

**Branch**: `feat/email-verification` | **Date**: 2026-10-19 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/002-email-verification/spec.md`

## Summary

Modificar el registro de usuarios (feature 001) para que la cuenta se cree en estado
"no verificada", sin sesión automática (reemplaza FR-016 y SC-002 de 001), redirigiendo al
usuario a la pantalla "Revisa tu correo". El backend Spring Boot agrega verificación por
enlace con token de un solo uso (24 h, hasheado, aleatorio criptográfico), envío asíncrono
del correo vía `spring-boot-starter-mail` (SMTP Gmail, `@Async`), límite de reenvío de 1 cada
5 minutos por dirección de correo (tabla por hash de correo, aplicada exista o no la cuenta,
con respuesta neutra idéntica en todos los casos), auto-login al canjear un token válido
(reutilizando el mecanismo JWT/cookie HttpOnly de 001) y limpieza programada (`@Scheduled`)
de cuentas no verificadas a los 3 días del último token. El frontend (React 19 + Vite)
agrega las pantallas "Revisa tu correo" (con cuenta regresiva persistente), verificación
(canje POST del token, inmune a escáneres de correo) y enlace vencido. La regla "cuenta no
verificada no puede iniciar sesión" se valida con un login simulado claramente marcado como
provisorio. Migración Flyway `V2` (sin DELETE); tests de correo con GreenMail (nunca Gmail
real); los tests de 001 afectados por el reemplazo de FR-016/SC-002 se actualizan y se listan
en research.md.

## Technical Context

**Language/Version**: Java 25 (LTS) + Spring Boot 4.1.1 (Spring Framework 7, Spring Security 7). Frontend: React 19.2.8 con Vite 8.3.0 (Rolldown) y Node.js 20.19+/22.12+.

**Primary Dependencies**: Backend (existente): `spring-boot-starter-webmvc`, `spring-boot-starter-security`, `spring-boot-starter-data-jpa`, `spring-boot-starter-flyway` + `flyway-database-postgresql`, `jjwt` 0.13.0. **Nuevo**: `spring-boot-starter-mail` (gestionado por el BOM de Boot 4.1.1) y, solo test, `com.icegreen:greenmail` 2.1.14 (módulo core + `greenmail-junit5`). Frontend: sin dependencias nuevas (react-router 8.4 y react-hook-form ya presentes).

**Storage**: PostgreSQL 18 (imagen `postgres:18-alpine`) con Flyway. Migración `V2__email_verification.sql`: columna nueva `users.email_verified_at` + tablas `email_verification_tokens` y `email_resend_limits` (hash del correo). Sin DELETE en migraciones (eliminación de cuentas caducas solo en runtime).

**Testing**: Backend: JUnit Jupiter (`*Test`) + Testcontainers PostgreSQL (`*IT`) + JaCoCo ≥ 85 % (ya configurado en `mvn verify`); GreenMail para el SMTP de prueba (nunca Gmail real). Frontend: Vitest + React Testing Library, cobertura V8 ≥ 85 % (ya configurado).

**Target Platform**: Aplicación web responsive; despliegue con Docker Compose (`db`, `backend`, `frontend`); proxy `/api/*` (nginx en prod / dev proxy de Vite) mantiene origen único. El correo de verificación usa `APP_BASE_URL` para componer el enlace hacia el frontend.

**Project Type**: Monorepo web (backend `backend/` Java/Spring, frontend `frontend/` React/Vite, CI con GitHub Actions).

**Performance Goals**: Correo de verificación emitido en < 5 s tras registro o reenvío (SC-001); confirmación de token y solicitud de reenvío responden en < 2 s (SC-008).

**Constraints**: Cobertura ≥ 85 % en ambos módulos (no bajarse); cumplimiento estricto de FR-001..FR-021 y SC-001..SC-008; respuesta de reenvío idéntica en todos los casos (SC-005, anti-enumeración); token nunca expuesto en BD (solo hash); pruebas siempre contra GreenMail, jamás contra Gmail real; la feature del login real (otro integrante) consume la regla FR-014, validada aquí con stub.

**Scale/Scope**: Proyecto universitario; una feature que modifica 001; 21 requisitos funcionales, 4 historias de usuario (P1–P4); 2 tablas nuevas + 1 columna nueva; 3 pantallas frontend nuevas; 3 endpoints nuevos + cambio del contrato de registro.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principio | Veredicto | Evidencia |
|---|---|---|
| I. Arquitectura y separación de responsabilidades | ✅ PASS | Se reutiliza el monorepo existente; nuevo paquete `verification/` en backend sin tocar la responsabilidad de `auth/` (registro) más allá del cambio de comportamiento exigido por la spec; frontend agrega páginas siguiendo el patrón pages/components/api de 001. |
| II. Desarrollo SDD y trazabilidad | ✅ PASS | spec con 3 clarificaciones integradas; plan, research, data-model, contracts y quickstart trazables a FR/SC; tasks posteriores organizarán el trabajo por historias. |
| III. Entrega incremental por historia | ✅ PASS | Tasks se organizarán por US1 (registro no verificado + pantalla) → US2 (confirmación) → US3 (bloqueo/login simulado) → US4 (reenvío + límite), cada una revisable por separado vía PR a `sprint-n`. |
| IV. Alcance controlado | ✅ PASS | Solo lo pedido: se modifica 001 según reemplazos explícitos (FR-016, SC-002, alcance de FR-005); fuera de alcance respetado (login real, 2FA, OAuth, recuperación de clave). Única dependencia nueva de runtime: `spring-boot-starter-mail`, requerida por el envío de correo. |
| V. Calidad, pruebas y cobertura | ✅ PASS | Umbrales ≥ 85 % ya configurados; pruebas nuevas por requisito (token, límite, limpieza, flujos) con GreenMail + Testcontainers; tests de 001 afectados actualizados (lista en research.md §5). |
| VI. Integridad de reglas de negocio | ✅ PASS | Estados de cuenta explícitos (verificada/no verificada); token de un solo uso con invalidación por emisión; límite de reenvío administrado en servidor por hash de correo; sin estados inválidos posibles (transiciones en data-model.md). |
| VII. Contratos REST y compatibilidad | ✅ PASS | Contrato nuevo documentado en `contracts/email-verification-api.md`; el cambio del contrato de `POST /api/auth/register` (sin `Set-Cookie`) está explícito y es el reemplazo aprobado por la spec; frontend y backend comparten el contrato. |
| VIII. Validación y manejo de errores | ✅ PASS | ProblemDetail RFC 7807 reutilizado (400 inválido, 409 usado, 410 vencido); respuesta de reenvío siempre idéntica (neutra); sin exposición de detalles internos ni de la falla de correo (FR-019). |
| IX. Seguridad y control de acceso | ✅ PASS con nota | Se reutiliza el mecanismo JWT/cookie HttpOnly de 001 (D1 de 001) para el auto-login post-verificación; cuenta no verificada bloqueada en `/api/users/**` (FR-004) y en el stub de login (FR-014); token guardado hasheado (SHA-256); el login simulado es un stub provisorio explícitamente autorizado por la spec para validar la regla hasta que llegue la feature real. |
| X. Código mantenible | ✅ PASS | Convenciones existentes: paquetes `com.amano.backend.*`, código en inglés, artefactos en español; sin dependencias redundantes (mail starter + GreenMail evaluados en research.md). |
| XI. Entorno reproducible e CI | ✅ PASS | Reutiliza `compose.yaml`, Dockerfiles y CI existentes (`mvn -B verify` / `npm ci && lint && test:coverage && build`); se agregan variables de entorno de correo a compose y `.env.example` (placeholders). |
| XII. Revisión por pares | ✅ PASS | PR de feature dirigido a `sprint-n` conforme a constitución (fuera del alcance de este plan). |
| XIII. Commits y trazabilidad | ✅ PASS | Conventional Commits; referencias a tareas cuando existan. |

**Verdict**: ✅ GATE PASSED — sin violaciones que requieran la tabla de complejidad.

**Re-check post-Phase 1 (2026-10-19)**: ✅ — el diseño (research.md D1–D10, data-model.md,
contracts/email-verification-api.md, quickstart.md) mantiene todos los gates: el token se
guarda hasheado (IX), el contrato documenta el cambio incompatibile de register de forma
explícita (VII/VIII), la limpieza es runtime sin DELETE en migraciones (regla del equipo) y
las pruebas usan GreenMail sin tocar umbrales (V).

## Project Structure

### Documentation (this feature)

```text
specs/002-email-verification/
├── plan.md              # Este archivo (/speckit.plan)
├── research.md          # Salida Phase 0: decisiones técnicas, versiones y tests de 001 afectados
├── data-model.md        # Salida Phase 1: entidades, tablas, transiciones de estado
├── quickstart.md        # Salida Phase 1: guía de validación end-to-end
├── contracts/           # Salida Phase 1
│   └── email-verification-api.md
├── checklists/
│   └── requirements.md
└── tasks.md             # Phase 2 (/speckit.tasks) — NO se crea en /speckit.plan
```

### Source Code (repository root)

```text
backend/
├── pom.xml                                       # + spring-boot-starter-mail; test: greenmail, greenmail-junit5
└── src/
    ├── main/java/com/amano/backend/
    │   ├── BackendApplication.java               # + @EnableAsync, @EnableScheduling
    │   ├── auth/
    │   │   ├── AuthController.java               # register: sin Set-Cookie; respuesta {email}
    │   │   └── RegistrationService.java          # crea cuenta no verificada, emite token, dispara correo @Async; re-registro sobre pendiente
    │   ├── verification/
    │   │   ├── EmailVerificationController.java  # POST /api/auth/verify-email, POST /api/auth/resend-verification
    │   │   ├── EmailVerificationService.java     # ciclo de token, canje, límite de reenvío, respuestas neutras
    │   │   ├── VerificationToken.java            # entidad JPA (hash, vencimiento, uso)
    │   │   ├── VerificationTokenRepository.java
    │   │   ├── EmailResendLimit.java             # entidad JPA (hash de correo + último clic)
    │   │   ├── EmailResendLimitRepository.java
    │   │   ├── VerificationMailService.java      # @Async JavaMailSender + plantilla del correo
    │   │   ├── VerificationProperties.java       # mail.* + app.base-url + plazos (24 h, 5 min, 3 días)
    │   │   ├── UnverifiedAccountCleanupScheduler.java  # @Scheduled elimina caducas (>3 días)
    │   │   └── SimulatedLoginController.java     # POST /api/auth/login — STUB PROVISORIO (reemplazar por feature de login real)
    │   ├── common/ProblemDetailExceptionHandler.java  # + mapeos 409/410 de verificación
    │   ├── config/SecurityConfig.java            # + permitAll: verify-email, resend-verification, login (stub)
    │   └── user/
    │       ├── User.java                         # + emailVerifiedAt (null = no verificada)
    │       └── ...
    ├── main/resources/
    │   ├── application.properties                # + spring.mail.*, app.base-url, verification.*
    │   └── db/migration/V2__email_verification.sql
    └── test/java/com/amano/backend/
        ├── verification/…Test.java               # unitarias: token, límite, plantillas, limpieza
        ├── verification/…IT.java                 # GreenMail + Testcontainers: flujos completos
        └── (tests de 001 actualizados — ver research.md §5)

frontend/
└── src/
    ├── api/
    │   ├── authClient.js                         # register: cambia contrato ({email}, sin sesión); + verifyEmail(), resendVerification(), login() stub
    │   └── verificationClient.js                 # (opcional) client dedicado si authClient crece demasiado
    ├── pages/
    │   ├── RegisterPage.jsx                      # ya no setea sesión ni navega a /dashboard → /check-email
    │   ├── CheckEmailPage.jsx                    # NUEVO: "Revisa tu correo" (correo en sessionStorage), botón reenvío + cuenta regresiva
    │   ├── VerifyEmailPage.jsx                   # NUEVO: canjea token vía POST, setea sesión y navega al dashboard con bienvenida
    │   ├── ExpiredLinkPage.jsx                   # NUEVO: enlace vencido + formulario de reenvío (ingresa correo)
    │   └── DashboardPage.jsx                     # sin cambios estructurales; muestra bienvenida tras verificación
    ├── components/
    │   ├── ResendCountdown.jsx                   # NUEVO: botón deshabilitado con cuenta regresiva (remainingSeconds del servidor)
    │   └── ...
    ├── App.jsx                                   # + rutas /check-email, /verify-email, /verify-email/expired
    └── (tests *.test.jsx nuevos y actualizados — ver research.md §5)

.env.example                                      # + MAIL_HOST, MAIL_PORT, MAIL_USERNAME, MAIL_PASSWORD, APP_BASE_URL (placeholders)
compose.yaml                                      # backend: + vars de correo (no secretas: placeholders/dev)
compose.prod.yaml                                # backend: + vars de correo (secretos por env)
```

**Structure Decision**: Se mantiene la Option 2 (web application) del monorepo existente. El backend concentra la funcionalidad nueva en el paquete `verification/` (responsabilidad propia: ciclo de vida del token, correo y límite), con cambios puntuales en `auth/` (comportamiento del registro), `user/` (estado de verificación), `config/SecurityConfig` (rutas públicas nuevas) y `common/` (mapeos de error). El frontend agrega páginas/componentes bajo los patrones ya establecidos por 001 (`pages/`, `components/`, `api/`). No se crean carpetas nuevas en la raíz ni se refactoriza código ajeno al alcance.

## Complexity Tracking

> Sin violaciones constitucionales que justificar.

Observación consciente (no bloqueante, fuera de alcance): `compose.prod.yaml` no pasa
`JWT_SECRET`/`JWT_EXPIRATION` al backend pese a que el backend falla sin ellos (brecha
preexistente de 001, documentada en research.md §6 como riesgo operativo). Este plan sí agrega
las variables de correo a ambos compose, pero no corrige la brecha de JWT porque es un cambio
ajeno a la historia; se deja señalada para el equipo.
