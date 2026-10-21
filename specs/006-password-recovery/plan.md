# Implementation Plan: Recuperación de contraseña

**Branch**: `feat/recuperar-contra` | **Date**: 2026-10-21 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/006-password-recovery/spec.md`

**Note**: El código ya está implementado y testeado en la rama actual (sin commitear al momento de redactar este plan). Este plan **documenta la implementación existente**; no propone una nueva ni modifica código.

## Summary

Recuperación de contraseña en dos pasos. **US1**: `POST /api/auth/forgot-password` siempre responde 200 con un mensaje neutro; si la cuenta existe y está verificada, `PasswordResetService` invalida sus tokens vigentes, emite uno nuevo (256 bits de `SecureRandom`, guardado como hash SHA-256 en la tabla nueva `password_reset_tokens`, vence en 1 hora) y `PasswordResetMailService` envía el enlace `/reset-password?token=...` de forma asíncrona con el SMTP de 003. **US2**: `POST /api/auth/reset-password` busca el token por hash, rechaza los no válidos (400) y los vencidos (410, eliminándolos), valida la nueva contraseña con la política de 001 (`PasswordPolicy`, idéntica a la de 005) y, en el éxito, guarda el hash BCrypt y elimina todos los tokens de la cuenta, sin abrir sesión. En el frontend, `ForgotPasswordPage` (`/forgot-password`) y `ResetPasswordPage` (`/reset-password`) reutilizan `TextField`, `react-hook-form` y `registerValidation.js`; `authClient` suma `forgotPassword` y `resetPassword`, y `LoginPage` gana el enlace "¿Olvidaste tu contraseña?" y el aviso de confirmación. Una migración nueva (`V3`), sin dependencias nuevas.

## Technical Context

**Language/Version**: Java 25 (backend); JavaScript con React 19.2 (frontend)

**Primary Dependencies**: Spring Boot 4.1.1 (Web MVC, Security, Validation, Data JPA, Flyway, Mail), BCrypt de Spring Security, `JavaMailSender` con `@Async` (003); React 19.2, react-router 8.4, react-hook-form 7.90, Vite 8.3 (proxy `/api` → `:8080`). **Sin dependencias nuevas.**

**Storage**: PostgreSQL con JPA + Flyway. **Una migración nueva**: `V3__password_reset_tokens.sql` (tabla `password_reset_tokens`, ver data-model.md). Modifica `users.password_hash` en el canje.

**Testing**: JUnit 5 + Mockito (`PasswordResetServiceTest`, `PasswordResetMailServiceTest`), Spring Boot Test + Testcontainers PostgreSQL + GreenMail (`ForgotPasswordIT`, `ResetPasswordIT`), `SecurityConfigTest`; Vitest + React Testing Library (`ForgotPasswordPage.test.jsx`, `ResetPasswordPage.test.jsx`, `authClient.test.js`, `LoginPage.test.jsx`); cobertura JaCoCo (`mvn verify`) y Vitest (`npm run test:coverage`), umbral 85 %

**Target Platform**: Servidor Linux vía Docker Compose; navegador web (desktop y móvil responsive)

**Project Type**: Aplicación web monorepo (backend REST + frontend SPA)

**Performance Goals**: SC-001 correo en < 1 min; la respuesta de la solicitud no espera al SMTP (envío `@Async`), lo que además iguala tiempos entre cuentas existentes e inexistentes

**Constraints**: Respuesta neutra en la solicitud (sin 4xx); token solo como hash; un token vigente por cuenta; nunca Gmail real en tests; sin dependencias nuevas; cobertura ≥ 85 % en ambos módulos; mensajes en español; sin tocar `RegistrationService` (el refactor a `PasswordPolicy` es de 005)

**Scale/Scope**: 2 endpoints, 2 pantallas nuevas (`/forgot-password`, `/reset-password`), 1 pantalla modificada (`/login`), 1 tabla nueva, ~11 archivos nuevos y ~10 modificados entre backend y frontend (ver Project Structure)

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| # | Principio | Estado | Evidencia |
|---|-----------|--------|-----------|
| I | Arquitectura y separación de responsabilidades | PASS | Paquete propio `passwordreset/` con la misma forma que `verification/` (entidad, repositorio, propiedades, servicio, servicio de correo, controlador); reutiliza `JavaMailSender`, `PasswordEncoder`, `ProblemDetailExceptionHandler`, `FieldValidationException`, `TextField`, `registerValidation.js` y `authClient`. `RegistrationService` no se toca (research.md §D9) |
| II | Desarrollo guiado por especificaciones | PASS | `specs/006-password-recovery/` con la estructura completa de Spec Kit; FR/SC citados en los comentarios del código y en los tests |
| III | Entrega incremental por historia | PASS | US1 se implementó, probó y validó manualmente antes de empezar US2 |
| IV | Alcance controlado y fidelidad al backlog | PASS | Fuera de alcance explícito: límite de frecuencia, revocación de sesiones, aviso por correo del cambio, recuperación de cuentas pendientes (spec §Assumptions) |
| V | Calidad, pruebas y cobertura | PASS | Tests escritos antes de cada paso; `mvn -B verify` (BUILD SUCCESS, JaCoCo OK) y `npm run test:coverage` (95,5 % de líneas) en verde |
| VI | Integridad de las reglas de negocio | PASS | Conserva FR-014 de 003 (las cuentas pendientes no reciben enlace) y la política de contraseñas de 001 |
| VII | Contratos REST y compatibilidad | PASS | `contracts/password-recovery-api.md`; formato ProblemDetail existente; ambos endpoints en `permitAll`; ningún contrato previo cambia |
| VIII | Validación y manejo de errores | PASS | Validación en cliente y servidor; 400 por campo; 400/410 con mensajes neutros; sin exponer tokens, hashes ni existencia de cuentas; fallas de SMTP registradas sin datos sensibles |
| IX | Seguridad y control de acceso | PASS | Token de 256 bits guardado como SHA-256, 1 hora, un solo uso, un vigente por cuenta; BCrypt para la contraseña; respuesta neutra; envío asíncrono. Riesgos residuales documentados (research.md §Riesgos) |
| X | Código mantenible y convenciones | PASS | Convenciones existentes (`*Test`/`*IT`, `*.test.jsx`, comentarios con trazabilidad FR); `PasswordPolicy` idéntica a 005 para no duplicar la regla |
| XI | Entorno reproducible e integración continua | PASS | Sin cambios de build; la migración corre sola al arrancar; propiedad nueva con valor por defecto (no requiere variables de entorno nuevas) |
| XII | Revisión por pares e integración controlada | PENDIENTE (posterior) | PR de `feat/recuperar-contra` aún no abierto |
| XIII | Commits y trazabilidad | PENDIENTE (posterior) | Los cambios están sin commitear; los commits los realiza el equipo manualmente |

**Gate resultado**: PASS (XII y XIII pendientes por naturaleza: se cumplen al commitear y abrir el PR). La única observación está en Complexity Tracking.

## Project Structure

### Documentation (this feature)

```text
specs/006-password-recovery/
├── plan.md                         # Este archivo (/speckit.plan)
├── research.md                     # Fase 0
├── data-model.md                   # Fase 1
├── quickstart.md                   # Fase 1
├── contracts/
│   └── password-recovery-api.md    # Fase 1: forgot-password y reset-password
├── checklists/
│   └── requirements.md             # /speckit.specify
└── tasks.md                        # Fase 2 (/speckit.tasks)
```

### Source Code (repository root)

```text
backend/src/main/java/com/amano/backend/
├── BackendApplication.java                     # M: @EnableConfigurationProperties suma PasswordResetProperties
├── passwordreset/
│   ├── PasswordResetToken.java                 # A: entidad de password_reset_tokens
│   ├── PasswordResetTokenRepository.java       # A: findByUserIdAndUsedAtIsNull, findByTokenHash, deleteByUserId
│   ├── PasswordResetProperties.java            # A: password-reset.token-expiration (1 h)
│   ├── PasswordResetService.java               # A: requestReset (US1), resetPassword (US2)
│   ├── PasswordResetMailService.java           # A: correo @Async con el enlace /reset-password
│   └── PasswordResetController.java            # A: POST /forgot-password, POST /reset-password
├── user/PasswordPolicy.java                    # A: copia idéntica del archivo de 005
├── common/
│   ├── PasswordResetTokenInvalidException.java # A: 400 "…no es válido"
│   ├── PasswordResetTokenExpiredException.java # A: 410 "…ha expirado"
│   └── ProblemDetailExceptionHandler.java      # M: handlers de las dos excepciones nuevas
└── config/SecurityConfig.java                  # M: permitAll suma /api/auth/forgot-password y /reset-password

backend/src/main/resources/
├── application.properties                      # M: password-reset.token-expiration=3600
└── db/migration/V3__password_reset_tokens.sql  # A: tabla + índice

backend/src/test/java/com/amano/backend/
├── passwordreset/PasswordResetServiceTest.java     # A: 18 unitarios (US1 + US2)
├── passwordreset/PasswordResetMailServiceTest.java # A: 2 unitarios (contenido del correo, falla SMTP)
├── passwordreset/ForgotPasswordIT.java             # A: 3 de integración (US1)
├── passwordreset/ResetPasswordIT.java              # A: 5 de integración (US2, incluye login)
└── auth/SecurityConfigTest.java                    # M: visibilidad pública de los dos endpoints

frontend/src/
├── api/authClient.js                # M: forgotPassword(), resetPassword() y sus mensajes de red
├── pages/ForgotPasswordPage.jsx     # A: /forgot-password
├── pages/ResetPasswordPage.jsx      # A: /reset-password?token=...
├── pages/LoginPage.jsx              # M: enlace "¿Olvidaste tu contraseña?" y aviso state.notice
├── App.jsx                          # M: rutas /forgot-password y /reset-password
└── reutiliza: components/TextField.jsx, validation/registerValidation.js

frontend/src/**/*.test.jsx|js       # A: ForgotPasswordPage, ResetPasswordPage; M: authClient, LoginPage
```

**Structure Decision**: monorepo web existente (`backend/` + `frontend/`); la feature no altera la estructura. En backend agrega el paquete de dominio `passwordreset/` siguiendo el patrón de `verification/`, y archivos sueltos en `common/` y `user/`; en frontend, solo páginas y funciones del cliente.

## Complexity Tracking

No hay violaciones de la constitución que justificar. Observaciones de mantenimiento (sin cambio de alcance):

| Elemento | Detalle | Acción sugerida |
|----------|---------|-----------------|
| `PasswordPolicy` duplicado entre ramas | `user/PasswordPolicy.java` existe con contenido idéntico en `feat/edit-profile` (005) y en esta rama; mientras tanto `RegistrationService` mantiene su copia privada de la regla | Al mergear 005, verificar que el archivo siga idéntico; el refactor de `RegistrationService` llega con 005 |
| Hash SHA-256 duplicado | `PasswordResetService.hash` repite el método de `VerificationTokenService` (8 líneas) | Extraer a una utilidad común en una historia de refactor si aparece un tercer uso |
| Helpers de test duplicados | `decodedBody`, `sha256Hex` y `awaitResetMailsFor` se repiten en `ForgotPasswordIT`, `ResetPasswordIT` y los IT de 003 | Fuera de alcance; posible clase de soporte de tests en el futuro |
