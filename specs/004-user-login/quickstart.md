# Quickstart: validación del inicio de sesión (004)

**Fecha**: 2026-10-20 | **Spec**: [spec.md](./spec.md) | **Contrato**: [contracts/auth-api.md](./contracts/auth-api.md)

Guía para verificar de punta a punta que el login documentado funciona. No incluye implementación: solo comandos y escenarios observables.

## Prerrequisitos

- Docker disponible (levantamiento del stack: PostgreSQL + backend + frontend).
- Java 25 + Maven (backend) y Node.js (frontend) para correr las suites.
- Una cuenta **verificada** existente (creada desde `/register` y confirmada vía correo de 003) y una cuenta **no verificada** (creada pero sin confirmar).

## 1. Verificaciones automatizadas

```bash
# Backend: unitarios + integración (Testcontainers) + cobertura JaCoCo ≥ 85 %
cd backend && mvn -B verify

# Frontend: lint + tests + cobertura Vitest ≥ 85 % + build
cd frontend && npm ci && npm run lint && npm run test:coverage && npm run build
```

**Resultado esperado**: builds en verde y cobertura ≥ 85 % en ambos módulos.

Suites clave de esta feature:

| Suite | Cubre |
|-------|-------|
| `auth/LoginServiceTest` | Normalización (trim+minúsculas), campos vacíos sin consulta a BD, 401 idéntico con comparación BCrypt dummy, orden credenciales → verificación |
| `auth/LoginIT` | Contrato 200/400/401/403, cookie `amano_auth` (HttpOnly, SameSite=Strict), perfil sin `passwordHash`, `RESEND_VERIFICATION`, normalización end-to-end |
| `auth/LogoutIT` | 204 con `Max-Age=0` y `HttpOnly` |
| `auth/SecurityConfigTest` | `/api/auth/login` y `/api/auth/logout` públicos; recursos protegidos sin cookie → 401 |
| `pages/LoginPage.test.jsx` | Formulario, botón deshabilitado, link a registro, 400/401/403/red, destino `state.from`, usuario ya logueado |
| `api/authClient.test.js` | Forma de `login()`/`logout()`, `LOGIN_NETWORK_ERROR_MESSAGE`, `action` expuesto en 403 |
| `auth/ProtectedRoute.test.jsx`, `App.test.jsx`, `pages/DashboardPage.test.jsx` | Redirección a `/login`, cierre de sesión desde el dashboard |

## 2. Levantar el entorno

```bash
docker compose up --build
# Frontend: http://localhost:5173  |  Backend: http://localhost:8080 (proxy /api de Vite)
```

## 3. Escenarios manuales

### A. Login exitoso (US1 / FR-001, FR-007)

1. Abrir `http://localhost:5173/login`.
2. Verificar: campo de contraseña oculto con control "Mostrar/Ocultar", enlace "Regístrate" a `/register`.
3. Ingresar credenciales válidas con el correo en mayúsculas y con espacios (p. ej. `"  Ana@Example.COM "`), enviar.
4. **Esperado**: llega al dashboard; la petición se envió con `ana@example.com`; en las herramientas del navegador existe la cookie `amano_auth` (HttpOnly).

### B. Destino conservado (US1 / FR-011)

1. Sin sesión, abrir directamente `http://localhost:5173/profile`.
2. **Esperado**: redirección a `/login` (no a `/register`).
3. Iniciar sesión.
4. **Esperado**: vuelta a `/profile`.

### C. Campos vacíos (US2 / FR-003)

1. En `/login`, enviar el formulario vacío.
2. **Esperado**: error por campo en el cliente, sin llamada al backend.
3. Reproducir contra el backend directamente (cuerpo `{}` o con espacios) vía curl/Proxy:
   `curl -s -X POST http://localhost:8080/api/auth/login -H 'Content-Type: application/json' -d '{"email":"   ","password":""}'`
4. **Esperado**: 400 con `properties.fields.email` y `properties.fields.password` (ver contrato).

### D. Credenciales inválidas indistinguibles (US2 / FR-005 / SC-002)

1. Enviar un correo inexistente con cualquier contraseña.
2. Enviar una contraseña incorrecta para una cuenta existente.
3. **Esperado**: ambas respuestas son idénticas: 401 `"El correo electrónico o la contraseña son incorrectos"`, sin cookie; en pantalla se limpia la contraseña y queda el correo; tiempos de respuesta equivalentes.

### E. Cuenta no verificada (US3 / FR-006 / FR-012)

1. Iniciar sesión con una cuenta creada no verificada y su contraseña correcta.
2. **Esperado**: aviso "Tu cuenta aún no ha sido verificada. Por favor, revisa tu correo electrónico" con el botón "Reenviar correo de verificación" de 003 y su cuenta regresiva; sin cookie de sesión.
3. Repetir con contraseña incorrecta.
4. **Esperado**: el 401 genérico (no se revela el estado).

### F. Ya logueado visita /login (US1 / FR-008)

1. Con sesión activa, abrir `http://localhost:5173/login`.
2. **Esperado**: redirección inmediata al dashboard sin formulario.

### G. Error de red (US1 / FR-009)

1. Detener el backend y enviar el formulario con datos válidos.
2. **Esperado**: "No se pudo iniciar sesión. Intenta nuevamente" y botón habilitado para reintentar.

### H. Cierre de sesión (US4 / FR-010)

1. Con sesión activa, pulsar "Cerrar sesión" en el dashboard.
2. **Esperado**: navegación a `/login`; la cookie `amano_auth` desaparece (Max-Age=0); `/dashboard` vuelve a redirigir a `/login`.
3. Repetir la llamada de cierre sin sesión:
   `curl -i -X POST http://localhost:8080/api/auth/logout`
4. **Esperado**: siempre `204 No Content` con el `Set-Cookie` de expiración (idempotente).

## 4. Regresiones a observar

- Registro (001) y verificación (003) siguen operativos: `/register`, `/check-email`, enlaces de verificación y reenvío con límite de 5 min.
- Recursos protegidos (`/api/users/me`, `/dashboard`, `/profile`) sin sesión → 401 / redirección a `/login`.
- Cobertura ≥ 85 % en ambos módulos (`mvn verify`, `npm run test:coverage`).
