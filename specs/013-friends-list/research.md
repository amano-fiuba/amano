# Research: Ver listado de amigos (013)

**Fecha**: 2026-11-01 | **Rama**: `feat/visualizar-listado-amigos` | **Spec**: [spec.md](./spec.md)

**Propósito**: registrar las decisiones técnicas. No quedaron `NEEDS CLARIFICATION`: las decisiones de producto están en las Clarifications de la spec.

## D1 — Numeración `013`

- **Decisión**: la carpeta es `specs/013-friends-list`.
- **Rationale**: 001–012 ya están en `sprint-3` (011 y 012 entraron con el PR #29). Es el siguiente número libre.

## D2 — Sin migración: la amistad es una solicitud `ACCEPTED`

- **Decisión**: no se agrega ni modifica ningún archivo de `db/migration`; la lista se obtiene de `friendship_requests` (`V12`) filtrando `status = 'ACCEPTED'`.
- **Rationale**: 011 (D3) y 012 acordaron que la amistad es una solicitud aceptada. `uq_friendship_requests_accepted_pair` garantiza una sola fila por pareja, así que no hacen falta deduplicaciones. `responded_at` ya guarda la fecha de aceptación ("amigos desde").
- **Alternativas consideradas**: tabla `friendships` (duplica la relación y contradice 011/012); vista SQL (agrega una migración sin beneficio).

## D3 — Una sola consulta JPQL con `join ... on` y proyección a DTO

- **Decisión**: `FriendshipRequestRepository.findFriendsOf(userId)` hace un `join` explícito de `FriendshipRequest` con `User` sobre "la otra cuenta" (`case when r.senderId = :userId then r.recipientId else r.senderId end`), filtra `ACCEPTED` y participación de la sesión, ordena y proyecta con `select new FriendResponse(...)`.
- **Rationale**: una sola ida a la base, sin N+1 ni cargar entidades completas (la entidad `FriendshipRequest` no tiene relaciones JPA a propósito, research de 011). La proyección deja fuera por construcción los datos privados de `User` (FR-004, SC-005). Hibernate 7 admite `join` de entidades no relacionadas con `on`.
- **Alternativas consideradas**: dos consultas (solicitudes + `findAllById`) como la bandeja de 011, ordenando en Java (más código y otro criterio de orden); consulta nativa (pierde la verificación del modelo en arranque).

## D4 — Orden igual al buscador de 008

- **Decisión**: `order by lower(u.firstName), lower(u.lastName) nulls last, u.id`.
- **Rationale**: es el criterio de `UserRepository.searchVerified` (008); la persona ve las cuentas ordenadas igual en el buscador y en su lista. El id da un orden total y estable.

## D5 — Endpoint `GET /api/users/me/friends`

- **Decisión**: recurso nuevo bajo el prefijo `me/` que ya usan 011/012, en un controlador propio `FriendController` y un servicio `FriendService`.
- **Rationale**: hereda la regla de cuenta verificada de `SecurityConfig` sin tocarla (FR-013); el usuario sale de la sesión (FR-014). Separar controlador y servicio evita crecer `FriendshipRequestController`/`FriendshipRequestService`, que modelan solicitudes, y reduce conflictos con otras ramas.
- **Alternativas consideradas**: `GET /api/users/me/friendship-requests?status=ACCEPTED` (expone el modelo interno y devuelve solicitudes, no amigos).

## D6 — Fecha "amigos desde" = `responded_at`

- **Decisión**: el DTO expone `friendsSince` con el `responded_at` de la fila aceptada; la UI lo formatea `DD/MM/AAAA` con `Intl.DateTimeFormat('es-AR', { timeZone: 'America/Argentina/Buenos_Aires' })`.
- **Rationale**: es la fecha real en que la relación pasó a ser amistad (012 la registra en la misma sentencia). Fijar la zona horaria hace el formato determinista en cualquier navegador y en las pruebas.

## D7 — Sección nueva dentro de `FriendsPage`, con estado propio

- **Decisión**: "Mis amigos" se implementa como componente `FriendsList` (en `components/`) que recibe un `reloadKey`; `FriendsPage` lo renderiza antes de "Solicitudes" e incrementa `reloadKey` cuando una aceptación devuelve 200.
- **Rationale**: aísla carga/vacío/error de cada sección (FR-010) y deja `FriendsPage` casi intacta (solo una línea en el flujo de aceptar). El patrón `cancelled` del `useEffect` evita que una respuesta vieja pise una más nueva.
- **Alternativas consideradas**: agregar el estado a `FriendsPage` (crece un componente ya largo y mezcla estados); insertar al amigo localmente desde la respuesta del `PUT` (la respuesta de 012 trae al emisor pero no la fecha de forma garantizada ni el orden; reconsultar es más simple y fiel).

## D8 — Grupos fuera de alcance

- **Decisión**: `NoOpFriendshipPort` (010) no se reemplaza en 013.
- **Rationale**: las pruebas de grupos (más de 15 clases) usan el stub configurable `groups.friendship.stub-friend-emails`; reemplazarlo cambia el comportamiento de crear grupo y agregar miembros de otra feature. Queda como historia propia, que podrá reutilizar `findFriendsOf`.

## D9 — Estrategia de pruebas

- Backend unitario (Mockito, `FriendServiceTest`): delega en el repositorio con el id de la sesión y devuelve la lista tal cual.
- Backend integración (Testcontainers, `FriendListIT`): ambas direcciones, exclusión de `PENDING`/`REJECTED`/terceros, orden, apellido nulo, fecha, ausencia de datos privados, lista vacía, 401 sin sesión y no verificada.
- Frontend (Vitest): `friendsClient.test.js` (`listFriends`), `FriendsList.test.jsx` (carga, lista, contador, vacío con enlace, error con reintento, 401, fecha) y `FriendsPage.test.jsx` (recarga tras aceptar, no tras rechazar).
- Cobertura ≥ 85 % en ambos módulos (constitución V).
