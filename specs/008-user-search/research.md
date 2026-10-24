# Research: Buscador de usuarios registrados

**Feature**: `008-user-search` | **Date**: 2026-10-24

Esta investigación se basa en el código y las convenciones existentes del repositorio; no
requiere decisiones de stack nuevas. No quedan incógnitas de tipo `NEEDS CLARIFICATION`.

## D1. Ubicación y protección del endpoint

- **Decisión**: exponer `GET /api/users/search?q=` en `UserController`, bajo el ámbito
  `/api/users/**` ya protegido por `SecurityConfig` con `verifiedAccount(userRepository)`.
- **Rationale**: el spec exige heredar la regla de 003 sin enumerar rutas nuevas. La
  configuración actual aplica la regla a todo `/api/users/**`, por lo que cualquier
  endpoint nuevo bajo ese prefijo queda cubierto (sin sesión o cuenta no verificada →
  401). La ruta no colisiona con `/api/users/me` ni con `/api/users/{id}/avatar`.
- **Alternativas consideradas**: crear un controlador o un prefijo nuevos (habría requerido
  tocar la regla de seguridad y duplicar responsabilidades); usar `/api/users` (confunde la
  intención y complica la convivencia con rutas existentes).

## D2. Consulta de búsqueda en `UserRepository`

- **Decisión**: agregar un método JPQL que reciba el término normalizado, el id de la
  sesión y un `Pageable`, filtrando cuentas verificadas, excluyendo al propio usuario y
  aplicando coincidencia parcial con `lower(...)` + `locate(...)` sobre nombre, apellido y
  correo. Ordenar por `lower(firstName)`, `lower(lastName)` e `id`, con
  `PageRequest.of(0, 20)`.
- **Rationale**: `locate` interpreta el término como texto literal, evitando que `%` o `_`
  del usuario se comporten como comodines de `LIKE`. El límite y el orden se aplican en la
  consulta, no en memoria. `id` como último criterio da un orden estable cuando nombre y
  apellido empatan. La tabla `users` ya contiene todos los datos necesarios.
- **Alternativas consideradas**: método derivado de Spring Data con `ContainingIgnoreCase`
  y combinaciones `Or` (precedencia frágil, difícil de acotar a 20 y de ordenar con
  `null` en apellido); `LIKE` con patrón construido en el servicio (requiere escapar
  comodines y es más propenso a errores); filtrar en Java (trae filas de más y desperdicia
  el límite).

## D3. Normalización y validación del término

- **Decisión**: `UserSearchService` recorta los espacios al inicio/fin, convierte a
  minúsculas con `Locale.ROOT` y exige al menos 2 caracteres después del recorte. Si no se
  cumple, lanza `FieldValidationException` con `fields.q` y el mensaje "Ingresá al menos 2
  caracteres para buscar". El frontend aplica la misma regla antes de llamar.
- **Rationale**: el backend es la fuente de verdad (constitución VIII) y el frontend evita
  una llamada innecesaria. La validación usa el mecanismo de errores ya existente, que
  responde 400 ProblemDetail con mensajes por campo.
- **Alternativas consideradas**: validar solo en el cliente (no protege la API); tolerar
  términos cortos devolviendo vacío (oculta un error de uso y dificulta el diagnóstico).

## D4. DTO de resultado

- **Decisión**: `UserSearchResult(Long id, String firstName, String lastName, String
  email)`, sin ningún otro campo.
- **Rationale**: cumple el alcance aprobado y evita exponer CBU/CVU, monedas, contraseña,
  avatares o datos derivados. El `id` permite futuras acciones fuera de esta feature sin
  cambiar el contrato de presentación; la lista visible solo usa nombre, apellido y correo.
- **Alternativas consideradas**: reutilizar `UserAccountResponse` (expone campos privados y
  viola el spec); devolver la entidad `User` (expone el hash de contraseña y acopla la API
  a la persistencia).

## D5. Cliente y página del frontend

- **Decisión**: crear `api/usersClient.js` con `searchUsers(q)`. Usar
  `encodeURIComponent(q)` en la URL, `credentials: 'include'`, y las convenciones de
  `authClient.js`/`expenseClient.js`: `{ ok: true, users }` en 2xx, `{ ok: false, status,
  detail, fields }` en 4xx y `{ ok: false, network: true }` en 5xx o fallo de red. La
  página `UsersSearchPage` usa un formulario con `TextField` y botón "Buscar"; muestra
  aviso si el término recortado tiene menos de 2 caracteres, lista los resultados, muestra
  "No se encontraron usuarios" si la búsqueda válida no devuelve filas y un mensaje fijo de
  reintento ante error de red. El apellido ausente se presenta como "No informado", igual
  que en 002.
- **Rationale**: sigue los patrones existentes de cliente y páginas protegidas; la
  búsqueda por envío explícito evita una consulta por cada tecla y hace evidente la regla
  de mínimo. El manejo de una secuencia de solicitud descarta respuestas obsoletas.
- **Alternativas consideradas**: búsqueda en vivo con debounce (más consultas y mayor
  complejidad de pruebas, no requerida por el spec); `fetch` directo en la página (rompe la
  separación cliente/UI existente).

## D6. Avatar y navegación

- **Decisión**: todos los resultados muestran `/avatar-default.png`; agregar el enlace
  "Buscar usuarios" a `/users/search` en `SiteNav` y proteger la ruta con `ProtectedRoute`
  en `App.jsx`.
- **Rationale**: el endpoint de avatar actual solo expone el avatar propio; mostrar
  avatares ajenos está fuera de alcance. La ruta protegida replica el patrón de las páginas
  existentes y la navegación global es el punto de acceso requerido.
- **Alternativas consideradas**: llamar a `/api/users/{id}/avatar` para cada resultado
  (devuelve 404 por diseño y genera tráfico inútil); agregar la página solo al dashboard
  (no cumple el acceso desde `SiteNav`).

## D7. Estrategia de pruebas y cobertura

- **Decisión**: `UserSearchServiceTest` con Mockito, `UserSearchIT` con MockMvc y
  Testcontainers PostgreSQL, y pruebas Vitest para `usersClient`, `UsersSearchPage`,
  `SiteNav` y la ruta protegida en `App`. Ejecutar `mvn -B verify` y
  `npm run test:coverage`.
- **Rationale**: cubre la lógica de validación/normalización, el contrato real contra la
  base de datos y la integración de la UI, respetando el umbral constitucional del 85 %.
- **Alternativas consideradas**: probar la consulta solo con mocks (no valida JPQL ni
  PostgreSQL real); pruebas end-to-end de navegador (fuera del stack de pruebas actual).

## D8. Sin cambios de esquema ni dependencias

- **Decisión**: no crear migraciones ni agregar dependencias. La feature solo lee `users`.
- **Rationale**: los campos necesarios ya existen (`first_name`, `last_name`, `email`,
  `email_verified_at`); la regla de verificación ya está aplicada por seguridad. Se respeta
  el alcance y se evita deuda innecesaria.
- **Alternativas consideradas**: agregar índices o una tabla de búsqueda (optimización
  especulativa, fuera de alcance); agregar una librería de búsqueda (dependencia nueva no
  justificada).
