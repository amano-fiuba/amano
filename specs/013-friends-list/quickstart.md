# Quickstart: Ver listado de amigos (013)

**Spec**: [spec.md](./spec.md) | **Contrato**: [contracts/friends-api.md](./contracts/friends-api.md)

## 1. Preparación

- Rama: `feat/visualizar-listado-amigos` (desde `sprint-3`, con 011 y 012).
- **Sin migración nueva**: alcanza con la `V12` de 011. Si tu base local tiene aplicada la antigua `V6__create_friendship_requests`, recreala (`docker compose down -v`).
- Levantar: base (`docker compose up -d db`), backend (`cd backend && (set -a; source ../.env; set +a; ./mvnw spring-boot:run)`, ver `README.md`, sección «Cómo levantarlo») y frontend (`cd frontend && npm run dev`).

### Pruebas automatizadas de la feature

| Prueba | Qué cubre |
|--------|-----------|
| `friendship/FriendServiceTest` | El servicio consulta con el id de la sesión y devuelve la lista del repositorio |
| `friendship/FriendListIT` | Ambas direcciones, exclusión de pendientes/rechazadas/terceros, orden, apellido nulo, fecha, sin datos privados, vacío, 401 |
| `api/friendsClient.test.js` | `listFriends`: URL, `credentials`, 200, 4xx, 5xx y red |
| `components/FriendsList.test.jsx` | Carga, lista, contador, fecha, vacío con enlace, error con reintento, 401, recarga por `reloadKey` |
| `pages/FriendsPage.test.jsx` | La lista se recarga al aceptar y no al rechazar |

```bash
cd backend && ./mvnw -B -Dtest=FriendServiceTest -Dit.test=FriendListIT -Dsurefire.failIfNoSpecifiedTests=false verify
cd frontend && npx vitest run src/api/friendsClient.test.js src/components/FriendsList.test.jsx src/pages/FriendsPage.test.jsx
```

## 2. Validación manual

Necesitás tres cuentas verificadas: **A** (Ana), **B** (Bruno) y **C** (Carla).

- **A. Lista vacía**: entrar como A y abrir "Amigos". "Mis amigos" muestra "Todavía no tenés amigos" y el enlace "Buscar usuarios" lleva a `/users/search`.
- **B. Amistad aceptada por la otra persona**: como A, buscar a B y pulsar "Agregar". Como B, abrir "Amigos" y pulsar "Aceptar". Sin recargar, B ve a A en "Mis amigos" con "Amigos desde" y la fecha de hoy. Como A, abrir "Amigos": aparece B.
- **C. Pendientes y rechazadas no cuentan**: como C, enviar solicitud a A. Como A, antes de responder, C no está en "Mis amigos". Rechazarla: C sigue sin aparecer y la lista no cambia.
- **D. Orden y contador**: con dos amigos o más, verificar el orden alfabético y el contador ("2 amigos").
- **E. Errores**: con el backend apagado, abrir "Amigos": "No se pudo cargar tu lista de amigos. Intentá nuevamente" con "Reintentar". Levantar el backend y pulsar "Reintentar": aparece la lista.
- **F. Acceso**: cerrar sesión y abrir `/friends`: redirige al inicio de sesión. `curl -i http://localhost:8080/api/users/me/friends` responde `401`.

## 3. Regresión

- Enviar, aceptar y rechazar solicitudes (011/012) siguen igual.
- Buscar usuarios (008) y grupos (010) no cambian.

## Resultado de la validación

- **Automatizada (2026-11-01, `feat/visualizar-listado-amigos`)**:
  - Backend `./mvnw -B verify`: BUILD SUCCESS. 249 unitarios y 161 de integración, 0 fallos; JaCoCo cumple el umbral (líneas 93,9 % global, 89,0 % en `friendship/`). De 013: `FriendServiceTest` (2) y `FriendListIT` (6). Las 4 líneas sin cubrir de `FriendController` son el 401 defensivo, que en la práctica responde antes el filtro de seguridad (mismo patrón que 011/012).
  - Frontend: `npm run lint` sin errores (3 advertencias previas en archivos ajenos), `npm run test:coverage` con 50 archivos y 512 pruebas en verde (líneas 90,98 % global; `FriendsList.jsx` 100 %), `npm run build` correcto.
- **Manual**: pendiente; la primera prueba visual la realiza la persona usuaria.
