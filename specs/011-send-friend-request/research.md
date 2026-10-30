# Research: Enviar solicitud de amistad (011)

**Fecha**: 2026-10-30 | **Rama**: `feat/send-friend-request` | **Spec**: [spec.md](./spec.md)

**Propósito**: registrar las decisiones de diseño. No quedaron `NEEDS CLARIFICATION`: las decisiones de producto están en las Clarifications de la spec y aquí se justifican las técnicas.

## D1 — Numeración `011`

- **Decisión**: la carpeta es `specs/011-send-friend-request`.
- **Rationale**: `010` queda reservado para otra historia del sprint y el equipo acordó el número `011` el 2026-10-30. El identificador evita ambigüedad en referencias cruzadas.
- **Alternativas consideradas**: reutilizar `008` (rompería la separación entre búsqueda y amistad).

## D2 — Tabla nueva `friendship_requests`

- **Decisión**: crear una migración `V6__create_friendship_requests.sql` con una sola tabla de solicitudes; no se altera `users`.
- **Rationale**: la solicitud es una entidad propia con estado y fechas, y las cuentas no deben cargar con columnas de relación. Sigue el patrón de migraciones inmutables de la constitución: los cambios de esquema se agregan con un archivo nuevo.
- **Alternativas consideradas**: tabla `friendships` simétrica con una fila por par (más difícil de auditar el flujo pendiente/rechazado); columnas en `users` (no representa N solicitudes).

## D3 — Una tabla con estados, amistad = solicitud aceptada

- **Decisión**: `friendship_requests` tiene `PENDING`, `ACCEPTED` y `REJECTED`. La amistad no tiene tabla propia: existe cuando hay una solicitud `ACCEPTED` entre la pareja.
- **Rationale**: el criterio de la historia habla de "estado Pendiente hasta que el destinatario la acepte o rechace"; una única entidad conserva el historial completo y permite evaluar "ya son amigos" hoy, aunque la aceptación se implemente en otra historia. Si más adelante la amistad necesita atributos propios, se agrega una migración.
- **Alternativas consideradas**: tabla de amigos separada desde 011 (duplica la relación y adelanta una decisión de una historia futura); solo `PENDING` sin catálogo de estados (imposibilitaría modelar la amistad).

## D4 — Endpoints bajo `/api/users/**`

- **Decisión**: `POST /api/users/me/friendship-requests` y `GET /api/users/me/friendship-requests/received`.
- **Rationale**: `SecurityConfig` ya exige sesión verificada para `/api/users/**`; colgar las rutas del recurso del usuario autenticado hereda la regla de 003 sin tocar la configuración de seguridad ni duplicar la validación. El emisor sale siempre del principal de la sesión.
- **Alternativas consideradas**: `/api/friendships/**` (requiere modificar `SecurityConfig` y agrega superficie sin beneficio); `/api/users/{id}/friendship-requests` (invita a que el cliente envíe el emisor por URL).

## D5 — Unicidad de la solicitud pendiente por pareja

- **Decisión**: índice único parcial sobre la pareja no ordenada:
  `UNIQUE (LEAST(sender_id, recipient_id), GREATEST(sender_id, recipient_id)) WHERE status = 'PENDING'`.
- **Rationale**: hace cumplir en la base la regla "no hay dos pendientes entre ambos usuarios", sin importar la dirección, incluso ante dos peticiones concurrentes o dos instancias del backend. El servicio además comprueba antes de insertar para devolver un 409 legible; la restricción es la última defensa.
- **Alternativas consideradas**: dos índices únicos por dirección (deja pasar una solicitud A→B y otra B→A); comprobar solo en el servicio (condición de carrera).
- **Nota**: se agrega también un índice único parcial análogo para `status = 'ACCEPTED'`, de modo que no pueda haber dos amistades para la misma pareja cuando la aceptación se implemente.

## D6 — Destinatario existente y verificado, con 404 neutro

- **Decisión**: el envío exige que el destinatario exista y tenga `email_verified_at`; en cualquier otro caso responde 404 "No se encontró el usuario".
- **Rationale**: 008 solo descubre cuentas verificadas, así que aceptar una solicitud hacia una cuenta no verificada crearía una relación no visible; el mensaje neutro evita revelar la existencia de cuentas ajenas y sigue el criterio de 006/008.
- **Alternativas consideradas**: 400 por "no verificada" (revela el estado de una cuenta); permitir la solicitud y filtrarla después (relación inconsistente con el buscador).

## D7 — Diferencia entre auto-solicitud, duplicado y amistad

- **Decisión**: auto-solicitud → 400; pendiente en cualquier dirección → 409; amistad existente → 409 con mensaje distinto; destinatario inexistente/no verificado → 404. Los mensajes exactos están en la spec y en el contrato.
- **Rationale**: cada caso tiene una causa de negocio distinta y la interfaz debe poder explicarla. El 400 es un dato inválido; los 409 son conflictos con el estado actual; el 404 protege la privacidad.
- **Alternativas consideradas**: un único 409 genérico (peor experiencia y menos trazabilidad para las pruebas).

## D8 — Notificación in-app en la pantalla "Amigos"

- **Decisión**: nueva ruta protegida `/friends` y acceso "Amigos" en `SiteNav`; la pantalla contiene solo la sección "Solicitudes" con las pendientes recibidas, más recientes primero. No hay correo, push ni WebSocket.
- **Rationale**: es la opción aprobada por el equipo y no requiere infraestructura nueva. La lista de amigos y las acciones de aceptar/rechazar pertenecen a otra historia y no se muestran como si funcionaran.
- **Alternativas consideradas**: badge en la barra sin pantalla (no permite ver el detalle); email (fuera de la historia aprobada); notificación en tiempo real (infraestructura inexistente).

## D9 — Estado del botón en el buscador sin cambiar el contrato de 008

- **Decisión**: habilitar el botón "Agregar" de `UsersSearchPage`; cada fila mantiene un estado local (`sending`, `sent`) durante la sesión de pantalla. No se modifica la respuesta de `GET /api/users/search`.
- **Rationale**: 008 devuelve solo datos de identidad y no conoce relaciones; agregarle un campo de amistad sería un cambio de contrato no pedido. Si la persona recarga y vuelve a pulsar, el backend responde 409 y la interfaz muestra el motivo, sin inventar un estado optimista persistente.
- **Alternativas consideradas**: extender el DTO de búsqueda con `friendshipStatus` (cambio de alcance); consultar la relación por cada fila (N+1 peticiones).

## D10 — Concurrencia y doble clic

- **Decisión**: el botón se deshabilita mientras el envío está en curso y, además, la base impone el índice único parcial. Si el `INSERT` pierde la carrera, el servicio traduce `DataIntegrityViolationException` al mismo 409 de "solicitud pendiente".
- **Rationale**: el bloqueo en la UI mejora la experiencia, pero no es una garantía; la restricción de base es la que realmente preserva el invariante (principio VI/VIII).
- **Alternativas consideradas**: bloqueo pesimista por pareja (más complejidad y contención sin necesidad).

## D11 — Exposición mínima de datos

- **Decisión**: la bandeja devuelve `id`, `status`, `createdAt` y un resumen del emisor con `id`, `firstName`, `lastName`, `email`; nunca CBU/CVU, monedas, contraseña ni avatar ajeno. El avatar por defecto es una decisión de presentación del frontend.
- **Rationale**: mismo criterio de minimización que 008; el correo es el único dato de contacto visible y el `id` queda disponible para la futura aceptación/rechazo.
- **Alternativas consideradas**: devolver el perfil completo (filtraría datos privados).

## D12 — Estrategia de pruebas

- **Decisión**:
  - Backend unitario con Mockito (`FriendshipRequestServiceTest`): validaciones, mensajes, mapeo y traducción de la carrera.
  - Backend de integración con Testcontainers (`FriendshipRequestIT`): flujo HTTP completo, persistencia, listado por destinatario, aislamiento entre cuentas, 401, 404, 409, duplicado inverso y restricciones de `V6`.
  - Frontend Vitest (`FriendshipRequestButton`/`UsersSearchPage`, `FriendsPage`, `friendsClient`, `SiteNav`, `App`): envío, estados de carga/éxito/error, bandeja, vacío, 401, reintento y acceso.
- **Rationale**: principio V (cobertura ≥ 85 %). Las validaciones críticas se prueban en backend porque el frontend no es una frontera de seguridad.

## Riesgos y mitigaciones

| Riesgo | Mitigación |
|--------|------------|
| Dos peticiones concurrentes crean dos pendientes | Índice único parcial por pareja no ordenada + traducción del conflicto |
| Relación con una cuenta no verificada | Validación de `email_verified_at` en el servicio y 404 neutro |
| Solicitud inversa tratada como distinta | Consulta de existencia pendiente en ambas direcciones + índice por pareja |
| La bandeja muestra solicitudes ajenas | Consulta filtrada por `recipient_id` de la sesión, nunca por un id del cliente |
| Datos privados en la respuesta | DTO acotado a nombre, apellido y correo; pruebas que verifican el JSON exacto |
| Crecimiento de aceptar/rechazar | El estado y los índices de `ACCEPTED` ya quedan preparados; la historia futura solo agrega transiciones y endpoints |
