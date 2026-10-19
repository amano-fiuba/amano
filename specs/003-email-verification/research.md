# Research: Verificación de correo electrónico

**Feature**: `003-email-verification` | **Fecha**: 2026-10-19 | **Spec**: [spec.md](../spec.md)

Este documento resuelve las decisiones técnicas de la fase de investigación para la
verificación de correo, respetando el stack ya confirmado por la feature 001 (Java 25,
Spring Boot 4.1.1, React 19 + Vite 8, PostgreSQL 18, Flyway, JWT en cookie HttpOnly,
Docker Compose). Detalla solo lo nuevo respecto de 001.

## 1. Verificación de versiones de las dependencias nuevas

Fuente primaria: BOM del parent `spring-boot-starter-parent:4.1.1` y verificación externa
(2026-10-19).

| Tecnología | Versión objetivo | Estado | Evidencia |
|---|---|---|---|
| `spring-boot-starter-mail` | gestionada por Boot 4.1.1 | ✅ Estable | El starter (Jakarta Mail + AngusMail) forma parte del BOM de Spring Boot; no se fija versión ad hoc, en línea con la regla de 001. |
| GreenMail (core + `greenmail-junit5`) | **2.1.14** (2026-09-19) | ✅ Estable | Última versión estable de la línea 2.1.x (Jakarta Mail 2.1, Jakarta EE 10, Java 11+). Compatible con Java 25 y con JUnit 5 vía `GreenMailExtension`. Solo scope test. |
| SMTP Gmail | `smtp.gmail.com:587` + STARTTLS + contraseña de aplicación | ✅ Operativo | Requiere cuenta Google con 2FA y "contraseña de aplicación"; documentado en `.env.example` como placeholder. Fuera de pruebas automatizadas ( GreenMail siempre, Gmail real nunca — regla del equipo). |

> Regla de navegación: si GreenMail 2.1.14 presentara incompatibilidad con el entorno de
> test, se usaría la última 2.1.x compatible dentro de la misma línea, actualizando este
> artefacto antes del cambio. No se bajan umbrales de cobertura.

## 2. Decisiones

### D1. Envío asíncrono del correo con `@Async` y `JavaMailSender`

- **Decisión**: `spring-boot-starter-mail` aporta `JavaMailSender` autoconfigurado desde
  `spring.mail.*` (host, puerto, username, password, STARTTLS). El envío se ejecuta en un
  método `@Async` de `VerificationMailService` (habilitado con `@EnableAsync` en
  `BackendApplication`), de modo que el request de registro o de reenvío nunca espera al SMTP
  (FR-003, SC-001). El fallo del envío se captura y se registra (`log.warn`) sin propagarse:
  la cuenta ya existe y el usuario ve "Revisa tu correo" (FR-019).
- **Rationale**: Cumple el criterio de < 5 s de emisión y el caso borde de falla del servidor
  de correo; el thread del request queda liberado y la experiencia de usuario no depende de
  Gmail.
- **Alternativas consideradas**: envío síncrono en el request (viola SC-001 si Gmail tarda o
  falla); cola de mensajes dedicada (Kafka/Rabbit): sobredimensionado para el volumen del
  proyecto y añade infraestructura no solicitada; `@Scheduled` con reintentos de cola propia:
  más complejo sin requisito de reintentos (el usuario tiene el botón de reenvío).

### D2. Token de verificación: aleatorio criptográfico, guardado hasheado, un solo uso, 24 h

- **Decisión**: token = 32 bytes de `SecureRandom` codificados en Base64URL (256 bits de
  entropía). En la tabla `email_verification_tokens` se guarda únicamente su hash SHA-256
  (`token_hash`, índice UNIQUE); el valor en claro solo viaja en el enlace del correo. El
  registro de token tiene `expires_at = created_at + 24 h` y `used_at` (null hasta canjearlo).
  Emitir un token nuevo invalida los anteriores de la cuenta (se marcan usados o se eliminan
  los no usados al emitir — ver data-model.md). El canje busca por hash, verifica `used_at IS
  NULL` y `expires_at > now()`, marca `used_at` y cambia la cuenta a verificada en la misma
  transacción (FR-003, FR-009, FR-021, SC-003).
- **Rationale**: El hash en BD evita que un volcado de base de datos o una consulta interna
  permita verificar cuentas ajenas (mismo criterio que BCrypt para contraseñas, FR-007 de
  001); SHA-256 es suficiente por la alta entropía del valor preimagen (no es una contraseña
  de baja entropía, no requiere KDF lento). Un solo uso + invalidación por emisión cubre
  "cada nuevo token invalida el anterior".
- **Alternativas consideradas**: JWT como token de verificación (aprovecharía `JwtService`,
  pero el token quedaría firmado-y-legible y mezclaría el dominio de sesión con el de
  verificación; además la invalidación anticipada es natural con tabla, no con JWT sin
  revocación); token UUID (122 bits, suficiente pero inferior a 256 sin ahorro real); guardar
  el token en claro (menor superficie de hash pero expone tokens en cualquier fuga de BD).

### D3. El enlace del correo apunta a una página del frontend que canjea el token con POST

- **Decisión**: el correo contiene un enlace `${APP_BASE_URL}/verify-email?token=...`. La
  página del frontend (ruta `/verify-email`) lee el token de la URL y ejecuta un
  `POST /api/auth/verify-email` con `{token}` en cuerpo JSON; el GET de la página no consume
  el token. La URL del enlace se limpia de la barra de direcciones tras el canje
  (`navigate` con replace).
- **Rationale**: Los escáneres de correo de clientes y proveedores hacen GET a los enlaces
  recibidos; con GET consumirían el token de un solo uso y dejarían al usuario real con un
  "enlace ya utilizado" (FR-007 dispara incorrectamente). El POST requiere intención del
  usuario y es inmune a ese comportamiento (decisión pedida explícitamente por el equipo).
- **Alternativas consideradas**: enlace con GET que verifica (vulnerable a escáneres);
  enlace a un endpoint del backend que redirige (rompe la SPA y expone el backend como
  dueño del HTML); código numérico de 6 dígitos (fuera de alcance por spec).

### D4. Límite de reenvío: tabla por hash de correo, aplicada exista o no la cuenta

- **Decisión**: tabla `email_resend_limits` con `email_hash` (SHA-256 del correo normalizado,
  PK) y `last_resend_at`. En cada `POST /api/auth/resend-verification`: si
  `last_resend_at + 5 min > now()` → no se envía y la respuesta incluye el tiempo restante;
  si no → se envía (si hay cuenta pendiente) y se registra `last_resend_at` (FR-011, FR-013,
  SC-004). La respuesta es siempre `200` con el mensaje neutro idéntico más
  `remainingSeconds` (SC-005). El límite se aplica por dirección con independencia de la
  sesión o el dispositivo (supuesto documentado en la spec); por eso se guarda el hash del
  correo y no una referencia a la cuenta.
- **Rationale**: Cumple la privacidad (no se puede discernir si la cuenta existe: la
  respuesta y el temporizador son idénticos) y el límite "exista o no la cuenta" (FR-011).
  Guardar hash en lugar del correo evita retener direcciones de cuentas inexistentes en la
  BD (GDPR-like minimalidad, coherente con la eliminación a los 3 días).
- **Alternativas consideradas**: límite por IP (varía detrás de NATs móviles y no sigue a la
  dirección, que es la unidad que la spec define; además empeora con proxy/VPN); guardar el
  correo en claro en la tabla de límites (necesita retener direcciones sin cuenta);
  rate-limit genérico de Spring (`Bucket4j`, filter por IP): no modela la semántica por
  dirección con respuesta de tiempo restante y añade dependencia.

### D5. Estado de verificación en la cuenta: columna `email_verified_at` nullable

- **Decisión**: `ALTER TABLE users ADD COLUMN email_verified_at TIMESTAMPTZ NULL` (null =
  "no verificada"; con valor = fecha de verificación). Regla de acceso en el servidor
  expresada en `SecurityConfig` y en el stub de login: principal con cuenta no verificada →
  rechazo (FR-004, FR-014, SC-002).
- **Rationale**: Un booleano o un enum de estado resuelven lo mínimo; el timestamp aporta
  trazabilidad sin costo y deja el histórico de cuándo se verificó. La columna nullable
  respeta los CHECK/defaults del esquema existente sin tablas de estados.
- **Alternativas consideradas**: enum `status` (VERIFIED/PENDING): más extensible para
  estados futuros (baneo, etc.) pero inventa una máquina de estados que la spec no pide;
  tabla separada de verificación 1:1 (normalización artificial para un solo campo).

### D6. Limpieza de cuentas no verificadas a los 3 días: tarea `@Scheduled`

- **Decisión**: `@EnableScheduling` en `BackendApplication` y
  `UnverifiedAccountCleanupScheduler` con `@Scheduled` (cadencia horaria) que elimina las
  cuentas con `email_verified_at IS NULL` cuyo último token tiene más de 3 días. La
  eliminación es en runtime (JPA/BD), nunca en la migración V2 (sin DELETE en migraciones,
  regla del equipo). Los tokens y límites asociados se eliminan en cascada por FK
  (data-model.md).
- **Rationale**: Cumple FR-017 sin datos de producción que migrar; la cadencia horaria da
  una ventana efectiva de 3–4 días que es consistente con el criterio "se elimina a los 3
  días" y evita escanear la tabla continuamente. El usuario eliminado puede re-registrarse
  completo (el índice UNIQUE de correo queda libre por la cascada).
- **Alternativas consideradas**: limpieza perezosa al tocar la cuenta (una cuenta nunca
  tocada nunca se elimina, violando FR-017); cron más frecuente (minuto) sin beneficio
  medible y con más carga; job externo (script fuera de la app) rompe la reproducibilidad del
  Compose.

### D7. Contrato del registro modificado y login simulado

- **Decisión**: `POST /api/auth/register` deja de devolver `Set-Cookie` y de crear sesión
  (reemplaza FR-016/SC-002 de 001): responde `201` con `{"email": "<normalizado>"}` (lo
  necesario para que el frontend guarde el correo en sessionStorage y navegue a
  `/check-email`). El auto-login posterior a la verificación reutiliza `JwtService` +
  `AuthCookie` de 001 (D1 de 001) emitiendo la cookie en la respuesta del POST de canje.
  `POST /api/auth/login` es un stub (`SimulatedLoginController`, claramente marcado
  `PROVISORIO`) que valida credenciales contra BCrypt y aplica FR-014 (cuenta no verificada →
  403 con el mensaje exacto más el indicador `properties.action: RESEND_VERIFICATION`, que la
  feature real de login usará para ofrecer el enlace de reenvío; la UI de login queda fuera de
  alcance de esta feature); será reemplazado por la feature real de login.
- **Rationale**: Reutiliza los mecanismos de sesión y cookie ya probados por 001 (arquitectura
  y constitución IX); el stub permite validar la regla de bloqueo hoy sin implementar el
  login completo (fuera de alcance).
- **Alternativas consideradas**: inventar un mecanismo de sesión distinto para la
  verificación (fragmentaría la autenticación); login simulado en el frontend únicamente (no
  probaría la regla del servidor, que es donde debe garantizarse — constitución IX).

### D8. SMTP de Gmail por variables de entorno

- **Decisión**: `MAIL_HOST`, `MAIL_PORT`, `MAIL_USERNAME`, `MAIL_PASSWORD` (contraseña de
  aplicación) y `APP_BASE_URL` (base del enlace del correo) se leen desde
  `application.properties` con defaults de desarrollo; `.env` no se versiona (ya ignorado) y
  `.env.example` recibe placeholders comentados. `compose.yaml` (dev) y `compose.prod.yaml`
  (prod) propagan las variables al contenedor del backend.
- **Rationale**: Consistente con el patrón de 001 (`JWT_SECRET` por env); mantiene
  credenciales fuera del repo; `APP_BASE_URL` separa el nombre público del host (Caddy en
  prod con `DOMAIN`, dev proxy en local).
- **Alternativas consideradas**: hardcodear el host de Gmail (inflexible para tests y prod);
  usar variables de build (rompe la separación config/código y obliga a reconstruir la
  imagen por credencial).

### D9. Estrategia de pruebas de correo: GreenMail, nunca Gmail real

- **Decisión**: Las ITs de verificación usan `GreenMailExtension` (SMTP en puerto efímero)
  inyectado en `spring.mail.*` vía `@DynamicPropertySource`; los tests leen los mensajes
  recibidos, extraen el enlace/token y ejecutan el canje real. El asunto, el cuerpo y la
  presencia del enlace se verifican explícitamente. Nunca se envía correo a Gmail en CI ni
  en tests locales (regla del equipo). Las unidades del servicio usan `JavaMailSender`
  mockeado.
- **Rationale**: GreenMail es el estándar de facto para `JavaMailSender` en JUnit 5; es
  sand-boxed (no puede filtrar correos reales); cubre SC-001/SC-008 con pruebas
  deterministas.
- **Alternativas consideradas**: MailHog/Mailpit como contenedor (añade servicio a Compose y
  a CI para algo que GreenMail resuelve en proceso); `JavaMailSender` mockeado en todo (no
  valida plantilla ni conexión real con el protocolo).

### D10. Tests de 001 afectados por el reemplazo de FR-016 y SC-002

- **Decisión**: Se actualizan los tests de 001 que asumían sesión automática tras el
  registro. Lista completa y justificación en §5 de este documento.
- **Rationale**: La constitución V exige que las regresiones pertinentes sigan pasando y
  que el DoD verifique el comportamiento requerido; cambiar el contrato sin actualizar los
  tests dejaría la build roja o, peor, tests que validan el comportamiento viejo.
- **Alternativas consideradas**: mantener los tests viejos y agregar una bandera (duplicaría
  escenarios contradictorios: con y sin auto-login); borrar los tests viejos (pérdida de
  cobertura de los flujos que sí siguen vigentes, p.ej. 409 por correo verificado
  duplicado).

## 3. Riesgos y mitigaciones

| Riesgo | Impacto | Mitigación |
|---|---|---|
| Escáneres de correo consumen el enlace | Usuario real ve "enlace ya utilizado" | D3: el canje es POST desde la SPA, no GET sobre el token |
| Fuga de la BD expone tokens de verificación | Cuentas ajenas verificables | D2: solo hash SHA-256 persistido; valor claro solo en el correo |
| Enumeración de cuentas vía reenvío | Compromiso de privacidad | FR-012/SC-005: respuesta idéntica exista o no la cuenta; tests que comparan byte a byte las respuestas |
| Gmail bloquea o limita el envío (límites de cuentas Google) | Correos no entregados en dev/demo | D1: fallo no bloquea el registro; botón de reenvío; GreenMail para pruebas; documentar límites en `.env.example` |
| `@Async` sin executor propio satura el thread pool por defecto | Degradación bajo carga | El envío es O(1) por registro/reenvío y el límite de 5 min acota reenvíos; se documenta la posibilidad de un `ThreadPoolTaskExecutor` dedicado si se requiere (decisión de implementación, sin cambio de spec) |
| Caducidad de 3 días ejecuta DELETE sobre datos relacionados | Pérdida accidental | Solo `email_verified_at IS NULL`; cascada FK limitada a tokens y avatars/monedas de esa cuenta; IT de limpieza con datos de prueba; sin DELETE en migraciones (regla del equipo) |
| `compose.prod.yaml` no pasaba `JWT_SECRET` (brecha preexistente de 001) | Backend no arranca en prod | ✅ Cerrada fuera de esta historia en el PR #8 (`fix/compose-prod-jwt-env`, commit `ee3f8ce`), ya integrado en la rama `feat/email-verification`; ver plan.md |
| Cookie de sesión emitida por el canje en dispositivo distinto al registro | Sin riesgo añadido: el token es de un solo uso y de 24 h; el enlace solo puede canjearse una vez | Tests de doble canje (segundo canje → 409) |

## 4. Supuestos técnicos incorporados

- `APP_BASE_URL` en local apunta al dev server de Vite (por ejemplo `http://localhost:5173`)
  y en prod al dominio de Caddy; el frontend debe servir la ruta `/verify-email` como
  fallback SPA (`try_files ... /index.html` ya está configurado en nginx y Caddy proxya al
  frontend).
- El correo de verificación se envía en español (consistente con el resto de la interfaz y
  la supuesta de la spec).
- La cadencia de la limpieza (horaria) y los plazos (24 h token, 5 min reenvío, 3 días
  caducidad) se centralizan en `VerificationProperties` para poder probarlos con valores
  cortos en ITs sin tocar la lógica.

## 5. Tests de 001 afectados por el reemplazo de FR-016 y SC-002

El registro ya no inicia sesión ni devuelve cookie, y el correo duplicado solo aplica a
cuentas verificadas. Estos tests de 001 deben actualizarse:

### Backend (`backend/src/test/java/com/amano/backend/`)

| Test | Por qué se afecta | Ajuste previsto |
|---|---|---|
| `auth/RegistrationIT.java` | Asume `Set-Cookie` + `201` con perfil y acceso posterior a `/me`; asume 409 para cualquier duplicado | Quitar aserción de cookie; nueva respuesta `{email}`; duplicado sobre **verificada** → 409; duplicado sobre **no verificada** → 201 (reemplazo, sin cookie); ITs de flujo completo ahora canjean el token leído de GreenMail antes de esperar sesión |
| `auth/RegistrationProfileIT.java` | Usa la cookie del registro para probar avatar/monedas vía `/me` | Helper de test que crea la cuenta verificada (o canjea el token vía GreenMail) antes de usar `/me` |
| `auth/RegistrationServiceTest.java` | Verifica que `register()` devuelve `User` con sesión; ahora también debe emitir token y disparar correo | Ampliar mocks (`VerificationTokenRepository`, servicio de correo); asertar estado no verificado y emisión de token; cubrir rama de re-registro sobre pendiente |
| `auth/SecurityConfigTest.java` | Reglas `permitAll` cambian (verify-email, resend, login stub) y `/api/users/**` ahora también debe rechazar cuentas no verificadas | ActualizarMatchers y agregar casos 401/403 de cuenta no verificada |
| `auth/JwtServiceTest.java` | Sin cambio funcional previsto | Verificar que sigue pasando sin ajustes (regresión) |
| `user/UserEntitiesTest.java` | `User` gana `emailVerifiedAt` | Agregar round-trip del campo nuevo |

### Frontend (`frontend/src/`)

| Test | Por qué se afecta | Ajuste previsto |
|---|---|---|
| `pages/RegisterPage.test.jsx` | Esperaba `setUser` + `navigate('/dashboard')` tras registrarse | Esperar navegación a `/check-email` sin sesión; guardar correo en sessionStorage |
| `api/authClient.test.js` | Contrato de register cambia (sin cookie, cuerpo `{email}`) y se agregan `verifyEmail`, `resendVerification`, `login` | Actualizar aserciones del register y cubrir los métodos nuevos |
| `components/RegisterForm.validation.test.jsx` | El envío exitoso dispara `onRegistered(profile)`; el flujo nuevo no tiene perfil | Ajustar el callback/mocking al nuevo contrato (`onRegistered(email)`) |
| `App.test.jsx` | Rutas nuevas | Smoke de rutas nuevas (render bajo router) |
| `auth/AuthContext.test.jsx` / `ProtectedRoute.test.jsx` | Sin cambios de comportamiento esperados | Verificar regresión; `/dashboard` ahora se llega tras verificar, no tras registrar |

> Durante `/speckit.tasks` cada fila se convertirá en tareas concretas con sus archivos
> afectados, manteniendo la trazabilidad exigida por la constitución (II y V).
