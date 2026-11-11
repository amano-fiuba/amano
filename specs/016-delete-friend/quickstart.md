# Quickstart: Eliminar amigo (016)

**Spec**: [spec.md](./spec.md) | **Contrato**: [contracts/delete-friend-api.md](./contracts/delete-friend-api.md)

## 1. Preparación

- Rama: `feat/delete-friend` (desde `sprint-4`, con 011, 012 y 013 integradas).
- **Sin migración nueva**: alcanza con la `V12` de 011.
- El módulo de deudas directas no existe: `NoOpDirectDebtPort` informa siempre "sin deuda". El bloqueo por deuda se valida con pruebas unitarias (mock del puerto); de punta a punta se prueba el camino sin deuda.
- Levantar: base (`docker compose up -d db`), backend (`cd backend && (set -a; source ../.env; set +a; ./mvnw spring-boot:run)`, ver `first-steps.md` §7) y frontend (`cd frontend && npm run dev`).

### Pruebas automatizadas de la feature

| Prueba | Qué cubre |
|--------|-----------|
| `friendship/FriendServiceTest` | Elegibilidad positiva/negativa, bloqueo por deuda, 404, validación de id, usuario de la sesión |
| `friendship/DeleteFriendIT` | Borrado real de `ACCEPTED`, desaparición para ambos, filas ajenas intactas, 400/401/403/404, re-agregar |
| `api/friendsClient.test.js` | `checkFriendDeletion` y `deleteFriend`: URL, método, `credentials`, 204, 4xx, 5xx y red |
| `components/DeleteFriendDialog.test.jsx` | Contenido, foco, cancelar, confirmar y "Eliminando…" |
| `components/FriendsList.test.jsx` | Apertura según elegibilidad, aviso de deuda, éxito, 403, 404, red y 401 |

```bash
cd backend && ./mvnw -B -Dtest=FriendServiceTest -Dit.test=DeleteFriendIT -Dsurefire.failIfNoSpecifiedTests=false verify
cd frontend && npx vitest run src/api/friendsClient.test.js src/components/DeleteFriendDialog.test.jsx src/components/FriendsList.test.jsx
```

## 2. Validación manual

Necesitás dos cuentas verificadas con amistad aceptada: **A** (Ana) y **B** (Bruno).

- **A. Camino feliz**: entrar como A, abrir "Amigos" y pulsar "Eliminar amigo" en la tarjeta de B. Aparece el diálogo con el nombre de B. Confirmar: la tarjeta desaparece y se muestra "Amigo eliminado correctamente".
- **B. Simetría**: entrar como B y abrir "Amigos": A ya no figura como amiga. En la base, no queda una fila `ACCEPTED` entre A y B.
- **C. Cancelar**: con otra amistad, abrir el diálogo y pulsar "Cancelar". No hay petición, la tarjeta permanece y no aparece confirmación.
- **D. Re-agregar**: como A, buscar a B y enviar una solicitud; como B, aceptarla. Ambos vuelven a figurar en "Mis amigos".
- **E. Grupos previos**: antes de eliminar, crear un grupo con A y B. Después de eliminar la amistad, ambos siguen siendo miembros del grupo y su historial no cambia. Intentar agregar a B a un grupo nuevo desde A: el sistema no lo ofrece por no ser amigo.
- **F. Deuda directa (unitario)**: `FriendServiceTest` con `DirectDebtPort` simulado en `true` comprueba que la elegibilidad devuelve `canDelete: false` y que `DELETE` lanza `PendingDirectDebtException` sin tocar el repositorio de borrado.
- **G. Errores de interfaz**: con el backend apagado, confirmar una eliminación: "No se pudo eliminar al amigo. Intentá nuevamente" y la tarjeta permanece. Con una amistad eliminada en otra pestaña, confirmar: se retira la tarjeta y se muestra "No sos amigo de ese usuario".
- **H. Acceso**: `curl -i -X DELETE http://localhost:8080/api/users/me/friends/1` responde `401`. Con sesión no verificada, también `401`. Con un id no numérico, `400`.
- **I. Historial**: registrar gastos personales (007) antes de eliminar; después de eliminar la amistad, los gastos y su historial siguen visibles sin cambios.

## 3. Regresión

- La lista de amigos (013) sigue mostrando los mismos datos, orden, contador y estado vacío.
- Enviar, aceptar y rechazar solicitudes (011/012) siguen igual.
- Los grupos (010) y sus miembros no cambian.
- Los gastos personales (007/009) y su historial no cambian.

## 4. Criterios de aceptación verificados

| Escenario | Referencia |
|-----------|------------|
| Seleccionar un amigo para eliminarlo | US1 / FR-001 |
| Solicitar confirmación antes de eliminar | US1 / FR-004, FR-005 |
| Dejar de figurar como amigo para ambos | US1 / FR-007, SC-002 |
| Informar que el amigo fue eliminado correctamente | US1 / FR-008, SC-001 |
| Conservar el historial de gastos compartidos | US3 / FR-017, SC-004 |
| Informar y bloquear por deuda directa | US2 / FR-011..FR-013, SC-003 |
| No bloquear por deudas de grupo | US2 / FR-014, SC-005 |
| Grupos previos permanecen y no se agrega a grupos nuevos | US3 / FR-018, FR-019 |
| Volver a ser amigos con una nueva solicitud | US3 / FR-010, SC-008 |
