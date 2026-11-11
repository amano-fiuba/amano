# Implementation Plan: Renovación de la interfaz

**Branch**: `feat/teclas-y-patron-manos` | **Date**: 2026-11-06 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/023-ui-refresh/spec.md`

## Summary

Spec retroactiva del trabajo de la rama: extiende 002, 008, 010 y 011 y reorganiza pantallas de 010, 014 y 015. Mantiene la estética actual y suma botones en relieve, un patrón de fondo, los colores del logo y esquinas más rectas. Agrega la foto de quien envía una solicitud, accesos en la barra, la pantalla `/activity`, la pantalla de grupo en tres columnas, con lugar listo para los saldos, la creación de grupos eligiendo amigos de una lista y las fotos en el buscador. Solo quedan abiertas pruebas faltantes y el recorrido manual ([tasks.md](./tasks.md)).

## Technical Context

**Language/Version**: Java 25 (backend); JavaScript con React 19.2 (frontend)

**Primary Dependencies**: Spring Boot 4.1.1; React, react-router, Vite, SCSS. **Sin dependencias nuevas.**

**Storage**: PostgreSQL. **Sin migraciones.**

**Testing**: JUnit 5 + Mockito, Spring Boot Test + Testcontainers, Vitest + Testing Library. Umbrales ≥ 85 %.

**Target Platform**: Servidor Linux vía Docker Compose; navegador web desktop y móvil.

**Project Type**: Aplicación web monorepo (backend REST + frontend SPA).

**Constraints**: cambios de contrato aditivos; contraste de al menos 4,5:1 sobre naranja; sin scroll horizontal a 360px; voseo.

## Constitution Check

| # | Principio | Estado | Evidencia |
|---|-----------|--------|-----------|
| I | Arquitectura | PASS | Se reutilizan el historial del grupo, `ExpenseResponse`, `AvatarVisibility` y `GET /api/users/me/friends` |
| II | Desarrollo guiado por especificaciones | **Desvío** | Las historias US1 a US4 se hicieron antes que la spec, que las regulariza. US5 y US6 se especificaron antes del código |
| III | Entrega incremental | **Desvío** | Seis historias independientes, pero en una sola rama (ver Complexity Tracking) |
| IV | Alcance controlado | PASS | Sección "Fuera de alcance"; los saldos no se calculan |
| V | Calidad y cobertura | PASS con nota | Umbrales cumplidos, con pruebas faltantes en tasks.md |
| VI | Integridad de reglas | PASS | Permisos, deudas y pagos no cambian |
| VII | Contratos REST | PASS | Campos y endpoint aditivos, documentados en el [contrato](./contracts/ui-refresh-api.md) |
| VIII | Validación y errores | PASS | `ProblemDetail` existentes |
| IX | Seguridad | PASS con nota | La foto se abre en un solo sentido; para revisión en el PR (research.md D4) |
| X | Código mantenible | PASS | Los ajustes visuales quedan en `detalles.scss` y dos variables de esquinas |
| XI | Entorno reproducible | PASS | Sin pasos manuales ni migraciones |
| XII | Revisión por pares | PENDIENTE (posterior) | PR aún no abierto |
| XIII | Commits y trazabilidad | PENDIENTE (posterior) | Los commits los realiza el equipo |

**Gate resultado**: PASS con desvíos en II y III, justificados abajo.

## Project Structure

```text
backend/src/main/java/com/amano/backend/
├── expense/{ExpenseController,ExpenseService,ExpenseRepository}.java   # GET /api/expenses
├── friendship/
│   ├── {FriendshipUserSummary,ReceivedFriendshipRequestResponse}.java  # avatarUrl
│   ├── FriendshipRequestService.java
│   └── FriendshipRequestRepository.java      # existsBySenderIdAndRecipientIdAndStatus
├── groups/GroupService.java                                            # members
├── groups/GroupMemberRepository.java                                   # findActiveMatesAmong
├── groups/dto/CreateGroupRequest.java
└── user/{AvatarVisibility,UserRepository,UserSearchResult,UserSearchService}.java

frontend/src/
├── assets/patron-fondo.svg
├── detalles.scss                             # teclas, naranja y patrón
├── index.scss                                # --radio-tarjeta, --radio-control
├── App.jsx                                   # ruta /activity
├── api/expenseClient.js                      # listExpenseHistory
├── components/{AppHeader,GlobalSearch,NavIcons,NotificationsMenu}.jsx
├── components/AppHeader.scss
├── components/main/PersonalActivity.jsx      # "Ver todo"
├── components/groups/{GroupFeed,GroupExpensesCard,MemberBalance,MemberListView}.jsx
├── components/groups/FriendPicker.jsx         # lista de amigos con casillas
└── pages/{ActivityPage,FriendsPage,UsersSearchPage}.jsx
    └── groups/{CreateGroupView,GroupInfoView}.jsx, groups.scss
```

## Complexity Tracking

| Desvío | Por qué | Alternativa descartada |
|---|---|---|
| Spec posterior al código | La estética se definió probando variantes y consultando al equipo | Especificar antes: no había decisión que documentar |
| Varias historias en una rama | Comparten estilos y la pantalla de grupo | Separar en ramas: conflictos constantes en los mismos SCSS |
| Máscara CSS en lugar de imagen | Un mismo archivo sirve para los dos temas | Dos imágenes por tema: cambiar la intensidad exige regenerarlas |
| Sobrescribir estilos en `detalles.scss` | Aplica las teclas a toda la app sin tocar cada pantalla | Editar cada SCSS: cambio enorme y difícil de revertir |
