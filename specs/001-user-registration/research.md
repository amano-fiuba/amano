# Research: Registro de usuario

**Feature**: `001-user-registration` | **Fecha**: 2026-10-18 | **Spec**: [spec.md](../spec.md)

Este documento resuelve las decisiones técnicas de la fase de intención y verifica la
existencia/compatibilidad de las versiones del stack conforme al mandato del usuario
(Java 25, Spring Boot 4.1.1, Vite 8, PostgreSQL 18, Testcontainers, JaCoCo y Flyway).

## 1. Verificación de versiones del stack

Fuente primaria: archivos del repositorio que fijan las versiones
(`backend/pom.xml`, `frontend/package.json`, `compose.yaml`) y verificación externa (2026-10-18).

| Tecnología | Versión objetivo | Estado | Evidencia |
|---|---|---|---|
| Java | **25** (LTS) | ✅ Publicada y estable | `pom.xml` fija `java.version=25` y compila el proyecto existente; Spring Boot 4.1.1 es compatible hasta Java 26. |
| Spring Boot | **4.1.1** | ✅ Estable | Publicada 2026-08-20 (Maven Central). Ya es el parent del `backend/pom.xml` vigente. Requiere ≥ Java 17 y Spring Framework ≥ 7.0.9. |
| Spring Security | **7.1.1** (gestionada por Boot 4.1.1) | ✅ Estable | Gestionada por el BOM de Boot 4.1.1; se agrega el starter. |
| Spring Validation | Hibernate Validator **9.1.3** (gestionada) | ✅ Estable | Gestionada por el BOM; se agrega `spring-boot-starter-validation`. |
| Vite | **8.3.0** (línea estable 8.3.x actual) | ✅ Estable | La serie 8.0 (con Rolldown) salió estable el 2026-03-12; hoy `vite@8.3` es la versión con parches vigentes. `frontend/package.json` ya fija `^8.3.0`. Node.js requerido: 20.19+ o 22.12+. |
| React | **19.2.8** | ✅ Estable | Ya presente en `package.json`. |
| PostgreSQL | **18** (imagen `postgres:18-alpine`) | ✅ Estable | PG 18 es estable (rama 18.x vigente); el driver JDBC gestionado por Boot 4.1.1 es 42.7.13, compatible con PG 18. |
| Testcontainers | **gestionada por Boot 4.1.1** (módulos postgres y junit-jupiter) | ✅ Estable | Ya presentes en `pom.xml` con los módulos equivalentes de Boot 4; funcionan con Docker Compose/tests. |
| JaCoCo | **0.8.15** | ✅ Estable | Configurado en `pom.xml` con regla BUNDLE/LINE ≥ 0.85 y exclusiones documentadas. Soporta Java 25. |
| Flyway | **gestionada por Boot 4.1.1** + módulo `flyway-database-postgresql` | ✅ Estable | Ya presentes en `pom.xml`; migraciones en `src/main/resources/db/migration`. |
| Vitest / cobertura V8 | **5.0.5** con `@vitest/coverage-v8` 5.0.5 | ✅ Estable | Ya configurado; umbral lines/statements 85 en `vite.config.js`. |
| jsdom / RTL | jsdom 30.x, RTL 16.x, user-event 14.6.8 | ✅ Estable | Ya presentes. |

> Regla de navegación ante imprevistos: las dependencias de Spring provienen del BOM del parent, así
> que no se fijan versiones ad hoc. Si durante la implementación alguna versión resultara
> inalcanzable o rompiera la compatibilidad, se marcaría como limitación y se registraría la
> versión estable más cercana compatible dentro del ecosistema Spring/React, actualizando los
> artefactos SDD antes del cambio. En ningún caso se bajan los umbrales de cobertura.

## 2. Decisiones

### D1. Estrategia de sesión: JWT en cookie HttpOnly `SameSite=Strict` con Spring Security

- **Decisión**: Autenticación propia del feature con `BCryptPasswordEncoder` para credenciales y
  JWT firmado con `io.jsonwebtoken:jjwt` (`jjwt-api` + `jjwt-impl` + `jjwt-jackson`) enviado vía
  cookie HttpOnly `SameSite=Strict` expiración fija 24 h (`JWT_EXPIRATION=86400`, secreta por env
  `JWT_SECRET`). Spring Security permite el filtro de sesión JWT y devuelve 401 en `/api/users/**`.
  CSRF deshabilitado conscientemente (ver plan.md, Complexity Tracking).
- **Rationale**: Cumple FR-007/FR-008 (contraseña no visible ni recuperable) y FR-016 (sesión
  automática post-registro) con el mecanismo de seguridad confirmado por el stack del proyecto;
  evita exposición del token a JavaScript y ataques cross-site en origen único.
- **Alternativas consideradas**: Tokens en `localStorage` (riesgo XSS y contramandado por el
  requisito de seguridad), cookies `SameSite=Lax` (menos restrictiva que Strict para una SPA
  same-origin), sesiones de servidor (no se alinea con el stack confirmado ni con el despliegue
  escalado backend/frontend separados).

### D2. Envío atómico del registro: `multipart/form-data` único a `POST /api/auth/register`

- **Decisión**: Todo el formulario (DTO campos JSON + archivo avatar opcional) en un único envío
  `multipart/form-data` multipart al endpoint `/api/auth/register`. Límites
  `spring.servlet.multipart.max-file-size=2MB` y `max-request-size=3MB`; `MaxUploadSizeExceededException`
  traducido a `400/413` con ProblemDetail programa de `common/`.
- **Rationale**: Cumple el flujo atómico de la spec (paso 4-6 del flujo principal y FR-015 para
  doble envío: un solo request procesa la primera solicitud); simplifica la integridad si falla
  la carga del avatar o hay correo duplicado (no deja datos parciales).
- **Alternativas consideradas**: Registro en 2 pasos (datos + subida de avatar separado), DTO JSON
  `x-www-form-urlencoded` (no escala a archivo avatar binario).

### D3. Normalización de correo y unicidad

- **Decisión**: `.trim()` (sin espacios leading/trailing) + `.toLowerCase()` en la capa de servicio
  antes de validar/guardar; columna `email` con índice UNIQUE en migración Flyway. Captura
  `DataIntegrityViolationException` → `409 Conflict` ProblemDetail "El correo electrónico ya se
  encuentra registrado" (cumple FR-005 y FR-017 ante carrera concurrente).
- **Rationale**: Cumple FR-005 y FR-017 ante carrera concurrente: registro case-insensitive y sin
  espacios por spec; el índice UNIQUE de la BD es la última línea de defensa ante registros
  concurrentes (SC-004).
- **Alternativas consideradas**: Tipo `CITEXT` de PostgreSQL (dependencia extra por sobre la
  normalización explícita ya decidida y verificable en código a nivel de servicio), validar
  disponibilidad con un request adicional previo al submit (más round trips, duplica costo;
  queda opcional a nivel de tasks si el UX lo requiere).

### D4. Contraseña: política y almacenamiento

- **Decisión**: `BCryptPasswordEncoder` para el hasheo (el hash no es recuperable por diseño).
  Validación de política (min 8 / max 64, ≥1 May., ≥1 Min., ≥1 dígito, ≥1 especial) con
  Bean Validation en el DTO de backend y regex/mensajería idéntica en el frontend (validación en vivo).
  Exclusión estricta del campo de contraseñas de DTOs de respuesta y logs.
- **Rationale**: BCrypt es el estándar Robusto disponible en Spring Security; varchar(255) no
  acota la cadena hash a un límite realizable; el max 64 decidido en clarificación (Q2) evita
  inputs absurdos sin recortar seguridad (BCrypt trunca silenciosamente a 72 bytes).
- **Alternativas consideradas**: Almacenar en texto claro (violación directa de FR-007), argon2id
  (requiere dependencia adicional sin beneficios prácticos en el scope del proyecto).

### D5. Storage de avatares: base de datos con tabla dedicada + default estático en frontend

- **Decisión**: Binarios JPG/JPEG/PNG ≤ 2 MB en tabla `user_avatars` (asociación `@OneToOne`
  desde `User` con `FetchType.LAZY`) para que las consultas habituales de perfil no carguen
  binarios. Si no se adjunta archivo, la UI resuelve el avatar predeterminado desde un recurso
  estático del propio frontend (`/avatar-default.png`), sin filas en la BD para el default.
  Endpoint `GET /api/users/{id}/avatar` requiere sesión y sirve el binario con `Content-Type`
  exacto (`image/jpeg` | `image/png`) y `X-Content-Type-Options: nosniff`.
- **Rationale**: Cumple FR-010 (formatos y tamaño y default), FR-021 y FR-009 (CBU y perfil solo
  visibles para el dueño; el avatar también se sirve solo para sesión autenticada en este feature).
- **Alternativas consideradas**: Servir binarios desde servicio de objetos externo (S3/MinIO:
  dependencia adicional innecesaria para el alcance), guardar el default en BD y servirlo por endpoint
  (duplicaría filas para datos estáticos).

### D6. Validación de avatar: MIME + magic bytes en la capa de servicio

- **Decisión**: Validación de imagen en dos niveles: el frontend valida `file.type` ∈
  (`image/jpeg`, `image/png`) y `file.size` ≤ 2 MB en vivo (FR-013); el backend, además del
  `Content-Type` provisto por el cliente, inspecciona los *magic bytes* reales del binario
  (≥signature JPEG FF D8 FF y PNG 89 50 4E 47), rechazando payloads engañosos con *mimetype*
  mentiroso (409/400 ProblemDetail).
- **Rationale**: Cumple FR-010/FR-022 y el principio IX de seguridad (no confiar solo en la UI).
- **Alternativas consideradas**: Confiar solo en la declaración del tipo de archivo (falsificable),
  abrir la imagen con ImageIO (más CPU por request sin necesidad en el alcance).

### D7. Modelo de monedas: `@ElementCollection` multiselección con default app-side

- **Decisión**: `user_currencies` como tabla dedicada con FK a `users` y columna `currency_code`
  (enum-like `ARS|USD|EUR`) modelada como `@ElementCollection` en JPA; default ARS aplicado en la
  lectura si el usuario no tiene ninguna preferencia (no se persiste fila por defecto, decisión
  clarify Q1: selección múltiple, ningún default persistido).
- **Rationale**: Es el modelo más simple y consistente con la clarificación Q1 (subconjunto de un
  catálogo cerrado, multiselección sin default persistido). La conversión enum ↔ código de moneda
  se centraliza en `CurrencyConversion`.
- **Alternativas consideradas**: Un solo string CSV de nuevas monedas sobre la tabla `users` (evita
  FK pero rompe la normalización y dificulta migraciones futuras), moneda única preferida (contra
  la decisión de clarificación Q1: selección múltiple).

### D8. Frontend: react-hook-form + validación en vivo y mensajes compartidos

- **Decisión**: `react-hook-form` (dentro del stack confirmado) para los campos de registro, con
  `mode: 'onBlur'` para campos texto/clave y `onChange` para contraseñas (coincidencia), CBU y
  avatar (FR-013). Reglas de validación centralizadas en `frontend/src/validation/registerValidation.js`
  (mensajes en español, exactos a la spec) para poder probarlas unitariamente con Vitest sin DOM.
  `react-router` para rutas protegidas (Login/redirect dashboard) y estando de registro sin sesión.
- **Rationale**: Cumple FR-013 y la UX de errores en < 2 s (SC-003); centralizar reglas reduce
  drift entre UI y pruebas; el router protege el dashboard según FR-016/FR-021.
- **Alternativas consideradas**: Formularios controlados manuales (más boilerplate de estado y
  testeo ad hoc de cada regla), validaciones inline duplicadas en cada componente (mensajes
  inconsistentes).

### D9. Proxy de desarrollo y proxy de producción unificados en `/api/*`

- **Decisión**: Reutilizar el proxy `/api/*` → `http://backend:8080` ya existente en
  `frontend/nginx.conf` (producción) y agregar el equivalente en `server.proxy` de Vite para el
  desarrollo local (dev server en 5173). Cambios en `vite.config.js` mínimos.
- **Rationale**: Consistencia con el origen único y `SameSite=Strict` en desarrollo y producción;
  evita CORS y pruebas equivocadas contra el backend con distinto puerto.
- **Alternativas consideradas**: CORS habilitado en el backend (innecesario bajo proxy único y más
  molesto en cada entorno), URL absoluta del backend en la SPA (rompe políticas de cookie SameSite
  y de CORS según entorno).

### D10. Errores estandarizados con ProblemDetail (RFC 7807)

- **Decisión**: Handler global `ProblemDetailExceptionHandler` con `@RestControllerAdvice` que
  traduce las excepciones de validación del backend (`MethodArgumentNotValidException`,
  `HandlerMethodValidationException`), duplicado de correo, avatar/imagen inválida y tamaño máximo
  de multipart a respuestas RFC 7807 con mensajes de usuario en español, y 401 para recursos sin
  sesión.
- **Rationale**: Cumple FR-018/FR-019 (mensajes de error consistentes y en < 2 s a la UI, manejables
  uniformemente desde el frontend) y el principio VIII de la constitución (no exponer detalles internos).
- **Alternativas consideradas**: Manejo ad-hoc de errores por endpoint (duplicación y mensajes
  inconsistentes).

## 3. Riesgos y mitigaciones

| Riesgo | Mitigación |
|---|---|
| Carrera concurrente de dos registros con el mismo correo | UNIQUE BD + catch `DataIntegrityViolationException` → 409 (D3). La prueba IT con Testcontainers valida el comportamiento con PostgreSQL real. |
| Cookie `SameSite=Strict` con subdominios en algunos navegadores | Origen único garantizado por el proxy del servicio `frontend` (nginx.conf y dev proxy de Vite); no se habilita CORS. |
| Uploads de avatar por encima del límite del proxy | Límite declarado en el backend (2 MB) y validación previa en el frontend; `MaxUploadSizeExceededException` mapeado a `400/413` con ProblemDetail (D2). |
| Cobertura insuficiente tras agregar frontend o backend | Umbrales ya configurados a ≥ 85 % en ambas plataformas; los tasks incluirán pruebas por requisito desde la primera historia de usuario. |
| Password hash filtrable por logs/DTOs | Exclusión explícita del campo en el `UserMapper` y en cualquier `toString`/log de trazabilidad; se verificará en las plantillas de tests. |
