# Quickstart: Validación del Registro de usuario

**Feature**: `001-user-registration` | **Spec**: [spec.md](spec.md)
Referencias: contratos en [`contracts/`](contracts/), modelo de datos en [`data-model.md`](data-model.md).

Esta guía valida la funcionalidad de forma end-to-end sin leer código. No incluye
implementación; los pasos de construcción detallados residen en `tasks.md` (Phase 2).

## Prerrequisitos

- Docker (motor en ejecución) — para el entorno integrado y para Testcontainers.
- JDK 25 y Maven (wrapper `backend/mvnw` incluido) — para backend.
- Node.js 20.19+ o 22.12+ — para frontend.
- Archivo `.env` en la raíz (plantilla: `.env.example`) con `POSTGRES_DB`, `POSTGRES_USER`,
  `POSTGRES_PASSWORD` y las variables nuevas `JWT_SECRET` (secreta p/ firma) y
  `JWT_EXPIRATION=86400`.

## 1. Levantar el entorno integrado

### Modo A — Stack completo con Docker Compose (validación E2E)

```bash
docker compose up --build
```

Espera esperado:

- `db` pasa a estado `healthy` (healthcheck `pg_isready` existente).
- `backend` arranca tras `db` (`depends_on: service_healthy`) y ejecuta las migraciones de Flyway
  al inicio (migración `V1__create_user_registration`).
- `frontend` Nginx soporta la SPA en `http://localhost:8081` y redirige internamente `/api/*` al
  backend (origen único).

### Modo B — Desarrollo con hot reload (backend local + `npm run dev`)

- Backend: `cd backend && ./mvnw spring-boot:run` (escucha en `http://localhost:8080`;
  requiere `.env` con `JWT_SECRET`/`JWT_EXPIRATION` y acceso al `db` de compose, o un PostgreSQL local).
- Frontend: `cd frontend && npm run dev` — el Dev Proxy de Vite (`server.proxy` de `/api`,
  tarea T005 en tasks.md) reenvía `/api/*` a `http://localhost:8080` manteniendo el origen único
  y las cookies `SameSite=Strict`.
- Nota: el proxy interno de Nginx (production/Modo A) solo actúa dentro del contenedor; con el
  Modo B nunca hay que exponer el puerto 8080 del backend en compose.
- Para la validación E2E de esta guía (§2–§5) usar el Modo A.

## 2. Escenario E2E manual — registro feliz + sesión automática (US1)

1. Navegar a `http://localhost:8081` y entrar a la pantalla de Registro.
2. Completar: nombre `Ana`, correo `Ana.Perez@Example.COM `, contraseña `Password123!`
   (más la confirmación idéntica), aceptar Términos y Condiciones; opcionalmente apellido,
   CBU `285-05910-0123 4567890123` (con separadores, para probar la normalización Q3),
   imagen JPG/PNG < 2 MB y monedas `USD` + `EUR` (multiselección Q1).
3. Presionar **Registrarse**.

Esperado:

- El botón se deshabilita inmediatamente durante el envío (FR-015).
- En < 1 s normalmente: redirección al dashboard; muestra el perfil consultado (`GET /api/users/me`)
  con `email` guardado como `ana.perez@example.com` (trim+lowercase), CBU normalizado a 22 dígitos,
  monedas `USD, EUR` (si no marcó ninguna, se representa `ARS` por defecto) (SC-001/SC-002).
- No se muestra la contraseña en ningún punto (FR-007/FR-008).
- Cookie `amano_auth` presente, HttpOnly, SameSite=Strict (DevTools → Application/Storage).

## 3. Escenario — correo duplicado y variantes de escritura (US1/US2)

Repetir el registro con el **mismo correo en otra escritura**: `   ANA.PEREZ@example.com `.

Esperado:

- Mensaje "El correo electrónico ya se encuentra registrado" en menos de 2 s, el botón queda
  rehabilitado y los datos del formulario se conservan (FR-017/FR-018/SC-003/SC-004).
- Verificar con la API (opcional): `curl -i -X POST http://localhost:8081/api/auth/register -F "firstName=Ana" -F "email=ana.perez@example.com" -F "password=Password123!" -F "acceptTerms=true" -w "\nHTTP %{http_code}\n"` → HTTP 409 con ProblemDetail RFC 7807.

## 4. Escenario — validación en vivo y fallos de envío (US2)

En la pantalla de Registro, verificar que al perder foco (o al escribir, para contraseña/CBU/imagen):

- Correo sin formato válido → mensaje de formato; nombre/apellido de 1 o 51+ caracteres → mensajes
  de longitud; contraseñas no coincidentes → mensaje de coincidencia; CBU con 21/23 dígitos (después
  de descartar espacios/guiones) → mensaje de 22 dígitos; imagen > 2 MB o GIF/WEBP (formato no válido)
  → mensaje de formato/tamaño — sin limpiar los datos ya cargados (FR-013, FR-018).
- Botón registrarse con `acceptTerms` sin marcar → no parten peticiones (en la pestaña Network).
- Doble envío: presionando repetidamente el botón, solo sale **una** petición en la pestaña Network
  (FR-015).
- Internet apagado (o backend detenido) al enviar → mensaje
  "No se pudo completar el registro. Intenta nuevamente", formulario intacto y botón rehabilitado
  en < 2 s (FR-019).
- Abandono: navegar a otra pantalla o cerrar la pestaña sin enviar → al volver no hay cuentas
  creadas con esos datos (FR-020, SC-006).

## 5. Escenario — avatar y datos opcionales (US3)

- Registrar un usuario **sin** avatar y **sin** monedas → dashboard muestra avatar predeterminado
  (`/avatar-default.png`, sin petición a `/api/users/{id}/avatar`) y monedas `ARS` (FR-010/FR-011).
- Registrar con avatar válido y luego pedir
  `curl -i http://localhost:8081/api/users/<id>/avatar --cookie "amano_auth=<jwt>"` → `200` con
  `Content-Type: image/jpeg|image/png` y `X-Content-Type-Options: nosniff` (contract users-api).
- Sin cookie (o cookies expiradas) → `401`; con usuario sin avatar propio → `404` y el
  frontend usará el default (FR-010/D5).

## 6. Pruebas automatizadas (mínimas obligatorias de calidad, Constitution V)

Backend (desde `backend/`):

```bash
./mvnw -B verify          # unitarias (*Test) + ITs (*IT con Testcontainers) + cobertura JaCoCo ≥ 85%
```

Frontend (desde `frontend/`):

```bash
npm ci
npm run lint
npm run test             # Vitest en jsdom
npm run test:coverage    # cobertura V8 ≥ 85 % (lines y statements)
npm run build            # build de producción con Vite 8/Rolldown
```

Ambas verificaciones deben quedar en verde antes de integrar (pull request desde
`feat/user-registration` hacia `sprint-n`, Constitution XII).

## Estado final esperado

- Registro completo de un usuario con datos válidos termina en el dashboard en < 1 min (SC-001),
  con sus datos visibles de inmediato en el perfil (SC-002).
- Ningún registro duplicado ni data parcial: intento con correo repite 409 y no queda ninguna de las
  dos cuentas parciales (SC-004).
- Ante fallos: mensaje visible y botón rehabilitado en < 2 s (SC-003); abandonos no persisten (SC-006).
- La contraseña jamás aparece en texto plano, ni en respuestas (`/api/users/me`), ni en
  cookies, ni en logs (SC-005).
