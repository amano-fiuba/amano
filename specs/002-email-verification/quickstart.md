# Quickstart: Verificación de correo electrónico

**Feature**: `002-email-verification` | **Spec**: [spec.md](../spec.md)

Referencias: [contrato de API](./contracts/email-verification-api.md), [modelo de
datos](./data-model.md), [decisiones](./research.md).

Guía para validar la feature end-to-end después de implementarla. No incluye código de
implementación; los comandos y valores esperados son los que deben observarse.

## Prerrequisitos

1. Docker y Docker Compose funcionando.
2. `.env` creado desde `.env.example` con todos los valores (los de correo pueden ser de
   GreenMail en local, ver Modo A, o de una cuenta Gmail con contraseña de aplicación):
   `POSTGRES_*`, `JWT_SECRET`, `MAIL_HOST`, `MAIL_PORT`, `MAIL_USERNAME`, `MAIL_PASSWORD`,
   `APP_BASE_URL`.
3. Rama `feat/email-verification` activa; migración `V2` aplicable al arrancar el backend.

## 1. Modo A — Compose completo (validación E2E)

1. Levantar el stack: `docker compose up --build -d`.
2. **Esperado**: contenedores `db`, `backend`, `frontend` healthy; el log del backend no
   muestra errores de Flyway (`V2` aplicada).
3. Registrarse desde `http://localhost:8081/register` con datos válidos de 001.
4. **Esperado**: redirección a `/check-email` mostrando "Revisa tu correo" con el correo
   ingresado visible; **sin** sesión iniciada (acceder a `/dashboard` redirige a
   `/register`) (FR-001, SC-002).
5. Verificar el correo recibido (inbox de Gmail con `MAIL_*` configuradas, o GreenMail):
   asunto y cuerpo en español con enlace `${APP_BASE_URL}/verify-email?token=...`.
   **Esperado**: correo emitido en < 5 s (SC-001).
6. Hacer clic en el enlace desde el mismo navegador.
   **Esperado**: pantalla de verificación canjea el token vía POST, inicia sesión y navega
   al dashboard con mensaje de bienvenida (FR-005, SC-008 < 2 s).
7. Abrir el mismo enlace una segunda vez (copiarlo en una pestaña nueva).
   **Esperado**: "Este enlace de verificación ya ha sido utilizado. Por favor, inicia sesión"
   (FR-007).

## 2. Modo B — Desarrollo con hot-reload y GreenMail local (sin Gmail real)

1. Levantar solo la BD y GreenMail con Compose; correr backend y frontend en modo dev con
   `MAIL_HOST`/`MAIL_PORT` apuntando a GreenMail (por ejemplo puerto 3025) y `APP_BASE_URL`
   al dev server de Vite (`http://localhost:5173`).
2. Repetir los pasos 3–7 del Modo A observando los mensajes en la consola de GreenMail.
3. **Esperado**: comportamiento idéntico al Modo A; ningún correo sale hacia Gmail.

## 3. Reenvío y límite de 5 minutos

1. Registrarse con un correo (pantalla "Revisa tu correo").
2. Hacer clic en "Reenviar correo de verificación".
   **Esperado**: mensaje neutro "Si el correo existe y no ha sido verificado, se enviará un
   nuevo enlace de confirmación"; correo nuevo recibido con token que invalida al anterior;
   cuenta regresiva de 5 min visible y botón aún habilitado (FR-010, FR-012, FR-009).
3. Recargar la página durante el límite y volver a hacer clic.
   **Esperado**: no llega correo nuevo; la pantalla muestra la cuenta regresiva con el tiempo
   restante real (FR-013, SC-006).
4. Repetir el clic con `curl` conservando el timestamp para comprobar la identidad de
   respuesta:
   ```bash
   curl -s -X POST http://localhost:8081/api/auth/resend-verification \
     -H 'Content-Type: application/json' -d '{"email":"no-existe@ejemplo.com"}'
   curl -s -X POST http://localhost:8081/api/auth/resend-verification \
     -H 'Content-Type: application/json' -d '{"email":"cuenta-verificada@ejemplo.com"}'
   ```
   **Esperado**: ambas respuestas idénticas en forma y mensaje (solo puede variar
   `remainingSeconds`); ninguna genera correo (SC-005).
5. Esperar a que venza el plazo (o manipular `last_resend_at` en la BD para la demo).
   **Esperado**: la cuenta regresiva llega a cero y un clic procesa un reenvío real.

## 4. Enlaces vencidos, inválidos y cuenta inexistente

1. Un enlace con token vencido (> 24 h; en ITs se acorta el plazo vía properties):
   **Esperado**: pantalla que explica que el enlace expiró y conduce a la pantalla de
   reenvío donde el usuario ingresa su correo (FR-006).
2. Un enlace con token alterado (cambiar un carácter):
   **Esperado**: "El enlace de verificación no es válido" (FR-008).
3. Reenvío para una cuenta eliminada por la limpieza (> 3 días): **Esperado**: respuesta
   neutra idéntica, sin correo (FR-018); la pantalla puede sugerir registrarse de nuevo.

## 5. Bloqueo de cuenta no verificada y login simulado

1. Registrarse y, sin verificar, intentar `POST /api/auth/login` con credenciales correctas:
   ```bash
   curl -s -i -X POST http://localhost:8081/api/auth/login \
     -H 'Content-Type: application/json' \
     -d '{"email":"nuevo@ejemplo.com","password":"ClaveForte1!"}'
   ```
   **Esperado**: `403` con "Tu cuenta aún no ha sido verificada. Por favor, revisa tu correo
   electrónico" y sin `Set-Cookie` (FR-014).
2. Intentar acceder a `/api/users/me` sin sesión o con sesión simulada de cuenta no
   verificada: **Esperado**: `401` (FR-004, SC-002).
3. Tras verificar el enlace, repetir el login: **Esperado**: `200` con cookie y perfil
   (el resto de reglas de login quedan fuera de esta feature).

## 6. Re-registro sobre cuenta pendiente y duplicado verificado

1. Registrarse con `pendiente@ejemplo.com` (queda no verificada); registrarse de nuevo con
   el mismo correo y **otra contraseña**.
   **Esperado**: `201`, pantalla "Revisa tu correo", sin segunda cuenta; la contraseña nueva
   reemplaza a la anterior (FR-015); si el límite de reenvío está vigente, el correo espera
   hasta que venza (Q2) mostrando la cuenta regresiva.
2. Verificar la cuenta y registrarse de nuevo con el mismo correo.
   **Esperado**: 409 "El correo electrónico ya se encuentra registrado" (FR-016, comportamiento
   de 001).

## 7. Limpieza a los 3 días

1. Crear una cuenta no verificada; acortar el plazo de caducidad vía properties (por ejemplo
   a minutos) o manipular `created_at` del token en la BD.
2. Esperar a la ejecución de la tarea programada (horaria por defecto).
   **Esperado**: la cuenta desaparece; el correo queda libre para un registro completo nuevo
   (todos los datos, no solo la clave) (FR-017, SC-007); los enlaces anteriores responden
   como inválidos (FR-018).

## 8. Falla del servidor de correo

1. Configurar `MAIL_HOST` inválido y registrarse.
   **Esperado**: la cuenta se crea igual, el usuario ve "Revisa tu correo" sin detalles
   internos del fallo, y el backend loguea el error de envío (FR-019).

## 9. Pruebas automatizadas

```bash
# Backend (unitarias + integración + cobertura JaCoCo >= 85 %)
cd backend && ./mvnw -B verify

# Frontend (lint + pruebas + cobertura V8 >= 85 % + build)
cd frontend && npm ci && npm run lint && npm run test:coverage && npm run build
```

**Esperado**: ambas verificaciones en verde, incluyendo los tests de 001 actualizados
(lista en research.md §5) y los nuevos tests de verificación (GreenMail, nunca Gmail real).

## Estado final esperado

- Ninguna cuenta no verificada accede a recursos protegidos ni inicia sesión (SC-002).
- Ningún token se reutiliza ni se usa pasadas 24 h (SC-003).
- Máximo un correo de reenvío cada 5 min por dirección, con respuesta neutra idéntica
  (SC-004, SC-005) y temporizador correcto tras recargas (SC-006).
- Cuentas no verificadas eliminadas a los 3 días, correo liberado (SC-007).
- Correo emitido en < 5 s (SC-001); canje y reenvío responden en < 2 s (SC-008).
- `mvn -B verify` y las verificaciones de frontend en verde con cobertura ≥ 85 %.
