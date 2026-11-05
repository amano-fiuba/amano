# Quickstart: validación de la recuperación de contraseña (006)

**Fecha**: 2026-10-21 | **Spec**: [spec.md](./spec.md) | **Contrato**: [contracts/password-recovery-api.md](./contracts/password-recovery-api.md)

Guía para verificar de punta a punta que la recuperación de contraseña documentada funciona. No incluye implementación: solo comandos y escenarios observables.

## Prerrequisitos

- Docker disponible (PostgreSQL, y Testcontainers para las pruebas de integración).
- Java 25 + Maven (backend) y Node.js 24 (frontend).
- `.env` con la configuración SMTP de 003 (`MAIL_HOST`, `MAIL_PORT`, `MAIL_USERNAME`, `MAIL_PASSWORD`) y `APP_BASE_URL` apuntando al frontend que se usa (`http://localhost:5173` con `npm run dev`, `http://localhost:8081` con compose).
- Una cuenta **verificada** con una casilla de correo accesible y, para el escenario C, una cuenta **pendiente** (registrada sin confirmar).
- **Reiniciar el backend** tras actualizar la rama: Flyway aplica `V3__password_reset_tokens.sql` al arrancar.

## 1. Verificaciones automatizadas

```bash
# Backend: unitarios + integración (Testcontainers + GreenMail) + cobertura JaCoCo ≥ 85 %
cd backend && mvn -B verify

# Frontend: lint + tests + cobertura Vitest ≥ 85 % + build
cd frontend && npm ci && npm run lint && npm run test:coverage && npm run build
```

**Resultado esperado**: builds en verde y cobertura ≥ 85 % en ambos módulos. Las pruebas nunca contactan Gmail: usan GreenMail en proceso.

Suites clave de esta feature:

| Suite | Cubre |
|-------|-------|
| `passwordreset/PasswordResetServiceTest` | US1: mensaje neutro exacto, normalización, hash SHA-256 que coincide con el token enviado, vencimiento de 1 h, invalidación de los anteriores, tokens distintos por pedido, nada para cuentas inexistentes/pendientes/correo nulo. US2: cambio con BCrypt y borrado de tokens, token inexistente/nulo/invalidado (400), vencido (410 y borrado), política y confirmación (token conservado) |
| `passwordreset/PasswordResetMailServiceTest` | Asunto, destinatario, saludo, enlace `/reset-password?token=` y "1 hora"; la falla del SMTP no se propaga |
| `passwordreset/ForgotPasswordIT` | Correo real en GreenMail con token cuyo hash está en la base (nunca en claro); un pedido nuevo invalida el anterior; respuestas idénticas para cuenta verificada, inexistente y cuerpo vacío |
| `passwordreset/ResetPasswordIT` | Flujo completo: login con la nueva contraseña 200 y con la anterior 401; enlace reutilizado 400; enlace reemplazado 400 y el nuevo funciona; vencido 410 y eliminado; errores de campo 400 con el enlace todavía válido; token inventado y cuerpo vacío 400 |
| `auth/SecurityConfigTest` | `/api/auth/forgot-password` y `/api/auth/reset-password` accesibles sin sesión |
| `pages/ForgotPasswordPage.test.jsx` | Formulario, validación de correo, estado "Enviando…", éxito "Revisa tu bandeja de entrada", fallo de red |
| `pages/ResetPasswordPage.test.jsx` | Sin token, validación de política y coincidencia, estado "Guardando…", redirección a `/login` con aviso, 410, 400 con y sin campos, fallo de red |
| `api/authClient.test.js` | Forma de `forgotPassword()` y `resetPassword()`, mensajes fijos de red |
| `pages/LoginPage.test.jsx` | Enlace "¿Olvidaste tu contraseña?" |

## 2. Levantar el entorno

```bash
docker compose up -d db
cd backend && (set -a; source ../.env; set +a; mvn spring-boot:run)   # http://localhost:8080
cd frontend && npm run dev                                             # http://localhost:5173
```

Comprobar la migración:

```bash
docker compose exec db psql -U amano -d amano -c '\d password_reset_tokens'
```

## 3. Escenarios manuales

### A. Pedir el enlace (US1 / FR-001, FR-003, FR-005)

1. Abrir `http://localhost:5173/login` y pulsar "¿Olvidaste tu contraseña?".
2. **Esperado**: se navega a `/forgot-password`.
3. Ingresar el correo de la cuenta verificada con mayúsculas y espacios (p. ej. `"  Ana@Example.COM "`) y pulsar "Enviar enlace".
4. **Esperado**: el botón muestra "Enviando…" y luego aparece "Revisa tu bandeja de entrada" con el mensaje neutro.
5. **Esperado**: llega el correo "Restablece tu contraseña - A Mano" con el enlace `${APP_BASE_URL}/reset-password?token=...`.
6. En la base: `SELECT token_hash, expires_at - created_at FROM password_reset_tokens;` → 64 caracteres hex y `01:00:00`; el token del enlace **no** aparece en la tabla.

### B. Respuesta neutra (US1 / FR-002, SC-002)

```bash
for email in ana@example.com no.existe@ejemplo.com ""; do
  curl -s -X POST http://localhost:8080/api/auth/forgot-password \
    -H 'Content-Type: application/json' -d "{\"email\":\"$email\"}"; echo
done
```

**Esperado**: las tres líneas son idénticas: `{"message":"Si el correo existe, recibirás un enlace para restablecer tu contraseña"}`. Solo la cuenta verificada recibe correo.

### C. Cuenta pendiente (US1 / FR-004)

1. Pedir el enlace para la cuenta pendiente.
2. **Esperado**: el mismo mensaje neutro y ningún correo de recuperación.

### D. Solo vale el último enlace (US1 / FR-007)

1. Pedir dos enlaces seguidos para la misma cuenta.
2. Abrir el **primero** y enviar una contraseña válida.
3. **Esperado**: "El enlace para restablecer la contraseña no es válido" y "Solicitar un nuevo enlace".
4. Abrir el **segundo** y repetir.
5. **Esperado**: funciona (escenario E).

### E. Cambiar la contraseña (US2 / FR-010, FR-015, SC-005)

1. Abrir el enlace del correo.
2. **Esperado**: pantalla "Elegir una nueva contraseña" con "Nueva contraseña" y "Confirmar contraseña", ambas ocultas.
3. Ingresar una contraseña válida (p. ej. `NuevaClave123!`) dos veces y pulsar "Guardar contraseña".
4. **Esperado**: el botón muestra "Guardando…" y luego se llega a `/login` con el aviso "Tu contraseña fue actualizada. Ya puedes iniciar sesión".
5. Iniciar sesión con la contraseña **anterior**. **Esperado**: "El correo electrónico o la contraseña son incorrectos".
6. Iniciar sesión con la **nueva**. **Esperado**: llega al dashboard.
7. En la base: `SELECT COUNT(*) FROM password_reset_tokens;` → `0` para esa cuenta.

### F. Reutilizar el enlace (US2 / FR-012, SC-004)

1. Volver a abrir el enlace del escenario E y enviar otra contraseña válida.
2. **Esperado**: "El enlace para restablecer la contraseña no es válido"; la contraseña de E sigue funcionando.

### G. Errores de campo sin perder el enlace (US2 / FR-014, FR-016, SC-006)

1. Pedir un enlace nuevo y abrirlo.
2. Ingresar `corta` y salir del campo. **Esperado**: "La contraseña debe tener al menos 8 caracteres"; el botón no envía.
3. Ingresar `sinmayuscula1!`. **Esperado**: "La contraseña debe incluir una mayúscula, una minúscula, un número y un símbolo".
4. Ingresar una válida y otra distinta en la confirmación. **Esperado**: "Las contraseñas no coinciden".
5. Forzar el error en el servidor y comprobar que el enlace sigue sirviendo:

   ```bash
   TOKEN=<token del enlace>
   curl -s -X POST http://localhost:8080/api/auth/reset-password -H 'Content-Type: application/json' \
     -d "{\"token\":\"$TOKEN\",\"password\":\"corta\",\"confirmPassword\":\"corta\"}"
   ```

   **Esperado**: 400 con `properties.fields.password`.
6. Completar el cambio desde la pantalla con una contraseña válida. **Esperado**: funciona.

### H. Enlace vencido (US2 / FR-013)

1. Pedir un enlace y, sin abrirlo, vencerlo en la base:

   ```sql
   UPDATE password_reset_tokens
      SET created_at = now() - interval '2 hours', expires_at = now() - interval '1 hour';
   ```

2. Abrir el enlace y enviar una contraseña válida.
3. **Esperado**: "El enlace para restablecer la contraseña ha expirado" y "Solicitar un nuevo enlace"; la fila desapareció de la tabla y la contraseña no cambió.

### I. Enlace sin token (US2 / FR-016)

1. Abrir `http://localhost:5173/reset-password` sin parámetros.
2. **Esperado**: "El enlace para restablecer la contraseña no es válido" sin formulario y sin llamada al backend (pestaña Red del navegador).

### J. Error de red (FR-016)

1. Detener el backend.
2. Enviar el formulario de `/forgot-password`. **Esperado**: "No se pudo enviar la solicitud. Intenta nuevamente" y botón habilitado.
3. Enviar el formulario de `/reset-password?token=x` con contraseñas válidas. **Esperado**: "No se pudo actualizar la contraseña. Intenta nuevamente" y botón habilitado.

### K. Falla del SMTP (FR-008)

1. Arrancar el backend con `MAIL_HOST` inválido y pedir un enlace para la cuenta verificada.
2. **Esperado**: la pantalla muestra igual "Revisa tu bandeja de entrada"; el log del backend muestra `Password reset email to ... could not be sent` **sin** el token.

## 4. Regresiones a observar

- Registro (001), verificación y reenvío (003) y login/logout (004) siguen operativos.
- `/api/users/**` sin sesión sigue respondiendo 401.
- Arranque limpio sobre una base existente: Flyway aplica `V3` sin tocar `V1`/`V2` y Hibernate valida el esquema (`ddl-auto=validate`).
- Cobertura ≥ 85 % en ambos módulos.

## Resultado de la validación (2026-10-21)

- **Automatizada**: `mvn -B verify` → BUILD SUCCESS, "All coverage checks have been met" (`PasswordResetServiceTest` 18, `PasswordResetMailServiceTest` 2, `ForgotPasswordIT` 3, `ResetPasswordIT` 5, `SecurityConfigTest` 13). `npm run test:coverage` → 19 archivos y 143 tests en verde, 95,54 % de líneas; `npm run lint` sin errores (1 advertencia previa en `RegisterForm.jsx`, ajena a esta feature); `npm run build` OK.
- **Manual**: US1 validada por el equipo con Gmail real (escenario A: el correo llega). US2 pendiente de validación manual (escenarios D–K).
