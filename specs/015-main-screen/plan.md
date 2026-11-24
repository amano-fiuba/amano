# Implementation Plan: Pantalla principal con sesión y nueva navegación

**Branch**: `feat/pantalla-principal` | **Date**: 2026-11-05 | **Spec**: [spec.md](spec.md)

## Technical Context

Frontend en React 19, React Router y SCSS con Vitest. Backend en Spring Boot con JPA y PostgreSQL. Sin dependencias ni migraciones nuevas.

## Decisiones

- **Ruta `/`**: un componente `RootRoute` elige entre `HomePage` y `MainPage` según `useAuth`, y no renderiza nada durante `loading`.
- **Barra**: `SiteNav` conserva la rama pública y delega la rama con sesión en `AppHeader`, `GlobalSearch` y `NotificationsMenu`.
- **Última actividad**: una sola consulta agregada para todos los grupos del usuario (`max(created_at)` agrupado), para no consultar grupo por grupo. El orden del endpoint no cambia, por compatibilidad.
- **Sincronización de la campanita**: `respondFriendshipRequest` emite el evento `amano:friend-requests-changed` al responder con éxito. Así cualquier pantalla que responda una solicitud actualiza la campanita y `FriendsPage` no necesita cambios.
- **Búsqueda de grupos**: filtro en el cliente sobre `listGroups()`, cargado al abrir el buscador. No requiere endpoint.
- **Búsqueda de personas**: con espera de 300 ms desde la última tecla, descartando respuestas viejas.
- **Resultado de persona**: lleva a `/users/search?q=`, porque no hay perfil público y ahí está la acción de agregar.

## Etapas

1. Backend: `lastActivityAt` y `GET /api/expenses/recent`.
2. Barra superior, `/settings` y botón "+".
3. Buscador global y `?q=` en `/users/search`.
4. Pantalla principal y rutas de entrada.

## Constitution Check

- Reutiliza clientes y componentes existentes; sin dependencias nuevas.
- Contratos documentados en [contracts/main-screen-api.md](contracts/main-screen-api.md); cambios aditivos.
- Pruebas por componente y endpoint; umbral de cobertura sin cambios.
