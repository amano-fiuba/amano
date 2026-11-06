# Quickstart: Abandonar grupo (020)

## 1. Preparación

```bash
docker compose up --build
```

Sin migraciones nuevas.

### Pruebas automatizadas

```bash
cd backend && ./mvnw verify -Dtest='GroupLeaveServiceTest' -Dit.test='LeaveGroupIT'
cd frontend && npx vitest run src/components/groups/LeaveGroupModal.test.jsx src/components/groups/LeaveGroupPanel.test.jsx src/pages/groups/GroupInfoView.test.jsx
```

## 2. Validación manual

Con tres cuentas verificadas A, B y C, amigas entre sí.

1. **Miembro con otros**: A crea un grupo y agrega a B. Como B, "Abandonar grupo" pregunta si estás seguro. Confirmar: "Se abandonó el grupo" y `/groups` sin el grupo. A sigue viéndolo.
2. **Único administrador**: A agrega a C. Como A, el modal dice "Sos el último administrador…" y solo tiene la equis. Hacer admin a C y repetir: aparece la confirmación.
3. **Último miembro**: quedando solo A, el modal dice "Sos el último miembro: si confirmas, el grupo se eliminará". Confirmar: "Se eliminó el grupo" y `/groups`. El botón "atrás" no vuelve al grupo.
4. **Saldos pendientes**: con una deuda sin saldar, confirmar muestra el motivo y no da de baja. Saldarla (018) y repetir.
5. **Base de datos**: tras el paso 3, todo sigue ahí:

```sql
SELECT id, name FROM groups WHERE id = :g;                       -- 1 fila
SELECT user_id, left_at FROM group_members WHERE group_id = :g;  -- todas con left_at
SELECT count(*) FROM group_expenses WHERE group_id = :g;         -- igual que antes
```

## 3. Regresión

Quitar miembros, roles, deudas (017), pagos (018), historial (014) y el listado de grupos.
