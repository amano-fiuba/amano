# Tasks: Extensión de grupos y buscador

**Input**: Design documents from `/specs/021-groups-search-extension/`

**Prerequisites**: [plan.md](./plan.md), [spec.md](./spec.md), [research.md](./research.md), [data-model.md](./data-model.md), [contracts/groups-search-api.md](./contracts/groups-search-api.md), [quickstart.md](./quickstart.md)

**Tests**: la implementación está publicada en `551a2f0`, salvo T020; las pruebas abiertas cubren lo que quedó sin verificar.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: se puede ejecutar en paralelo
- **[Story]**: US1 a US4

---

## Phase 1: User Story 1 - Buscador (Priority: P1) 🎯 MVP

- [x] T001 [US1] `friendshipStatus` en `UserSearchResult` y `UserSearchService`; `findActiveBetween` en `FriendshipRequestRepository` — FR-001, FR-003, FR-004, FR-005
- [x] T002 [US1] `UsersSearchPage.jsx`: textos y estados por `friendshipStatus` — FR-002
- [x] T003 [US1] Pruebas: `UserSearchServiceTest` y `UsersSearchPage.test.jsx`
- [x] T004 [US1] Actualizar el comentario de `UsersSearchPage.jsx` que decía que la búsqueda no trae la relación
- [ ] T005 [P] [US1] `UserSearchIT`: `friendshipStatus` contra la base real, incluida la consulta `findActiveBetween` — FR-001

---

## Phase 2: User Story 2 - Editar los datos del grupo (Priority: P1)

- [x] T006 [US2] `V15`, `membersCanEditGroupData` y `UpdateGroupRequest` — FR-006, FR-018
- [x] T007 [US2] `GroupPermissionChecker`: `canEditGroupData`, `canManagePermissions`; `GroupService`: autorización por campo — FR-007, FR-008
- [x] T008 [US2] `GroupPermissionsPanel.jsx`: reemplazar la casilla — FR-006, FR-008
- [x] T009 [US2] `GroupEditForm.jsx`, `PencilIcon` y `GroupInfoView.jsx` — FR-011 a FR-015
- [x] T010 [US2] Pruebas: `GroupSettingsServiceTest`, `GroupSettingsUpdateIT`, `GroupEditForm.test.jsx`
- [ ] T011 [P] [US2] `GroupPermissionCheckerTest`: `canEditGroupData`, `canManagePermissions` y `canManagePhoto` por rol y casilla — FR-007, FR-008
- [ ] T012 [P] [US2] `GroupInfoView.test.jsx`: "Editar" visible para administrador y para miembro con casilla, oculto sin ella — FR-011

---

## Phase 3: User Story 3 - Eliminar la foto (Priority: P2)

- [x] T013 [US3] `DELETE /api/groups/{id}/photo`, `GroupService.deletePhoto` y `deleteGroupPhoto` — FR-009, FR-010
- [x] T014 [US3] `GroupPhotoPanel.jsx` controlado, con `GroupPhotoPanel.test.jsx` — FR-012, FR-013
- [ ] T015 [P] [US3] `GroupPhotoControllerIT`: 204 con y sin foto, `groups.photo` nulo, `PHOTO_CHANGED` una sola vez, 403 sin casilla, miembro con casilla puede — FR-007, FR-009, FR-010
- [ ] T016 [P] [US3] `groupsClient.test.js`: `deleteGroupPhoto` con éxito, rechazo y error de red

---

## Phase 4: User Story 4 - Etiqueta "Simulado" (Priority: P3)

- [x] T017 [US4] `MemberListView.jsx` y `.role-chip--simulated` — FR-017
- [ ] T018 [P] [US4] `MemberListView.test.jsx`: la etiqueta aparece en perfiles simulados y no en registrados — FR-017

---

## Phase 5: Polish

- [ ] T019 Recorrer quickstart.md §2
- [x] T020 Eliminar la casilla "No podrán editar ajustes del grupo": `V16`, `GroupPermissions`, `UpdateGroupRequest`, `GroupService`, `GroupPermissionChecker.canEditSettings` y sus pruebas — research.md D7, FR-019

## Notes

- Las specs 008, 010 y 011 no se editan; `spec.md` indica qué puntos quedan reemplazados.
- Los commits los realiza el equipo.
