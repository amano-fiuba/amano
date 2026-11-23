# Quickstart: Ver historial de actividad del grupo (014)

**Spec**: [spec.md](./spec.md) | **Contrato**: [contracts/activity-api.md](./contracts/activity-api.md)

## 1. Preparación

- Rama: `feat/historial-actividad-grupal` (desde `sprint-3`).
- **Sin migración nueva**: alcanza con `V10` (010). La próxima migración libre sigue siendo `V13`.
- Levantar: base (`docker compose up -d db`), backend (`cd backend && (set -a; source ../.env; set +a; ./mvnw spring-boot:run)`, ver `README.md`, sección «Cómo levantarlo») y frontend (`cd frontend && npm run dev`).
- Para agregar amigos registrados a un grupo hace falta una amistad aceptada (011/012): desde el fix de 010 del 2026-11-04, grupos usa las amistades reales (`AcceptedFriendshipPort`). Para la validación manual de 014 alcanza con perfiles simulados.

### Pruebas automatizadas de la feature

| Prueba | Qué cubre |
|--------|-----------|
| `groups/ActivityEventServiceTest` | JSON válido con comillas y acentos, nulos omitidos, detalles de altas y bajas, enganches de gasto y pago con validaciones |
| `groups/GroupActivityServiceTest` | Filtro múltiple, 400, tope 100, valores por defecto, actores en lote, `details` inválido → `null`, 404 y 403 |
| `groups/GroupServiceTest`, `GroupMemberServiceTest`, `MemberRemovalServiceTest` | Detalles registrados en la alta inicial, altas y bajas |
| `groups/GroupActivityControllerIT` | Contrato HTTP: alta inicial, nombres con comillas, copia en bajas, filas viejas inválidas, gasto y pago, transacción obligatoria, filtro múltiple, 400, tope |
| `api/groupsClient.test.js` | `eventTypes` separado por comas, 4xx, 5xx y red |
| `pages/groups/activityDescriptions.test.js` | Todas las frases, formatos viejos, montos, agrupación por día |
| `pages/groups/GroupHistoryView.test.jsx` | Frases y días, gastos y pagos, vacío, filtro y paginación, error con reintento, 404, 401, respuestas viejas, "Volver al grupo" |

```bash
cd backend && ./mvnw -B -Dtest='ActivityEventServiceTest,GroupActivityServiceTest,GroupServiceTest,GroupMemberServiceTest,MemberRemovalServiceTest' -Dit.test=GroupActivityControllerIT -Dsurefire.failIfNoSpecifiedTests=false verify
cd frontend && npx vitest run src/api/groupsClient.test.js src/pages/groups/activityDescriptions.test.js src/pages/groups/GroupHistoryView.test.jsx
```

## 2. Validación manual

Con una cuenta verificada **A** (Ana):

- **A. Alta inicial**: crear un grupo con un perfil simulado "Abuela". Abrir "Ver historial de actividad": aparece "Ana agregó el perfil simulado Abuela al crear el grupo", bajo el encabezado del día de hoy y con la hora.
- **B. Nombres raros**: agregar el perfil simulado `Juan "el tano"`. El historial muestra el nombre tal cual, con las comillas.
- **C. Bajas**: quitar a `Juan "el tano"`. Aparece "Ana quitó el perfil simulado Juan "el tano"" y la alta anterior conserva el nombre aunque el perfil ya no exista.
- **D. Filtros**: elegir "Bajas": solo se ve la baja. Elegir "Gastos": "No hay actividad de este tipo en el grupo". Volver a "Toda la actividad".
- **E. Errores**: con el backend apagado, recargar el historial: "No se pudo cargar el historial. Intentá nuevamente" con "Reintentar". Levantar el backend y pulsar "Reintentar".
- **F. Acceso y API**: "Volver al grupo" lleva a `/groups/:id`. Con la cookie de A, `GET /api/groups/{id}/activity?eventType=NOPE` responde `400` y `?size=500` devuelve `"size": 100`.

Los eventos de gasto y pago no se pueden generar desde la interfaz hasta que exista el módulo de gastos de grupo. Están cubiertos por `GroupActivityControllerIT.expenseAndPaymentHooksAppearInTheHistory` y por las pruebas de la vista.

## 3. Regresión

- Crear grupos, agregar y quitar miembros, abandonar, permisos, foto y roles (010) siguen igual. La única diferencia es que crear un grupo ahora deja una entrada más en el historial.
- Amistades (011–013) y gastos personales (007/009) no cambian.

## Resultado de la validación

- **Automatizada (2026-11-03, `feat/historial-actividad-grupal`)**:
  - Backend `./mvnw -B verify`: BUILD SUCCESS. 264 unitarios y 169 de integración, 0 fallos; JaCoCo cumple el umbral (líneas 94,3 % global, 92,6 % en `groups/`; `ActivityEventService` y `GroupActivityService` 100 %). De 014: `ActivityEventServiceTest` (7), `GroupActivityServiceTest` (7) y 8 pruebas nuevas en `GroupActivityControllerIT` (13 en total).
  - Frontend: `npm run lint` sin errores (las 3 advertencias previas en archivos ajenos), `npm run test:coverage` con 51 archivos y 533 pruebas en verde (líneas 91,9 % global; `activityDescriptions.js` y `GroupHistoryView.jsx` 100 % de líneas), `npm run build` correcto.
- **Manual**: pendiente; la primera prueba visual la hace la persona usuaria.
