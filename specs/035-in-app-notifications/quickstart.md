# Quickstart / Guía de validación: notificaciones in-app

**Feature**: [spec.md](./spec.md) · **Plan**: [plan.md](./plan.md) · **Contrato**: [contracts/notifications-api.md](./contracts/notifications-api.md) · **Datos**: [data-model.md](./data-model.md)

Esta guía permite validar la feature de extremo a extremo. No incluye código de implementación: los detalles están en `plan.md`, `data-model.md`, `contracts/` y `tasks.md`.

## Prerrequisitos

- Docker + Docker Compose, Java 25 y Node 24 (versiones vigentes en `README.md`).
- `.env` creado a partir de `.env.example`, con `JWT_SECRET`, `MAIL_*` y `APP_BASE_URL`.
- Rama de trabajo `feat/in-app-notifications` y dependencias instaladas (`backend` con Maven, `frontend` con `npm ci`).

## Puesta en marcha

Desde la raíz del repositorio:

```bash
docker compose up -d --build
docker compose ps
```

El backend no expone puerto propio: se accede por `/api/` a través de nginx (o por el proxy de Vite en desarrollo, `http://localhost:5173`). Si Flyway aplica la migración `V39__create_notifications.sql` correctamente, el backend arranca;una migración con errores impide el arranque (ver `README.md`). Opcionalmente se pueden cargar datos de demostración con `./scripts/seed-full-demo.sh` y crear notificaciones de prueba ejecutando las operaciones de negocio descritas abajo.

## Verificación automática obligatoria

```bash
cd backend && mvn -B verify
cd ../frontend && npm ci && npm run lint && npm run test:coverage && npm run build
```

Resultado esperado:

- Compilación y pruebas unitarias, web e integración en verde (incluye `TruncateCoverageIT`, que exige que las dos tablas nuevas estén en `TRUNCATED_TABLES`).
- JaCoCo y vitest v8 con cobertura mínima del 85 % de líneas (front additional: 85 % de statements). No se reducen umbrales ni se desactivan pruebas.
- Sin cambios en los contratos existentes ni en las reglas de negocio de 011/012, 025, 030, 032/033/034.

## Validación funcional end-to-end

| # | Escenario (US / SC) | Pasos | Resultado esperado |
|---|---------------------|-------|--------------------|
| 1 | Popover (US1, SC-001, SC-002) | Iniciar sesión con una cuenta verificada que tenga notificaciones; pulsar la campana de la barra superior | El badge muestra el número de no leídas ("9+" si > 9); el popover muestra hasta 5 avisos, no leídos primero y luego los más recientes; el pie ofrece "Ver todas las notificaciones" y "Configuración" |
| 2 | Acceso a la bandeja (US1, FR-004) | Pulsar "Ver todas las notificaciones" | Navega a `/notifications` y el popover se cierra; la ruta está protegida (sin sesión redirige al acceso) |
| 3 | Filtros y marcado (US2, SC-003, SC-004) | En `/notifications`, alternar "Todas"/"No leídas", marcar una y luego "Marcar todas como leídas"; recargar | El filtro "No leídas" coincide con el contador; el contador baja en uno y luego a cero sin recargar; el estado persiste tras recargar |
| 4 | Deep link de grupo (US3, SC-005) | Registrar un gasto en un grupo o provocar un recordatorio/silbato; abrir la notificación | Se marca como leída y navega a `/groups/{groupId}` |
| 5 | Deep link de deuda (US3, SC-005) | Provocar un vencimiento o pago; abrir la notificación | Se marca como leída y navega a `/groups/{groupId}/debts?debt={debtId}`, o a `/activity?debt={debtId}` si no hay grupo |
| 6 | Deep link social y destino eliminado (US3, SC-005) | Abrir una solicitud de amistad; eliminar un grupo y abrir su notificación | Navega a `/friends`; en el grupo eliminado navega a la sección general con aviso no bloqueante y la notificación queda leída |
| 7 | Preferencias (US4, SC-006) | Abrir el modal desde la bandeja y desde el popover; desactivar "Correo electrónico" y "Actividad de grupo"; guardar; recargar y volver a iniciar sesión | Los seis valores se muestran y persisten; los eventos de grupo no generan avisos, los de deuda sí; no se envían correos de notificación duplicados |
| 8 | Resiliencia (US5, SC-007) | Simular SMTP caído (o inaccesible) y un fallo de inserción en BD; ejecutar una operación que genera aviso | La operación de negocio responde con éxito, no se muestra error por notificaciones y el fallo queda registrado en logs |
| 9 | Aislamiento y errores (US5, SC-008) | Con otro usuario, intentar marcar un id de notificación ajeno y consultar/modificar preferencias con `userId` ajeno | Respuesta 404 neutral; no se modifica ni se revela información ajena |
| 10 | Accesibilidad y responsive (SC-009) | Recorrer campana, popover, bandeja y modal con teclado y lector de pantalla en ventana angosta | Roles y nombres accesibles, foco correcto, sin desplazamiento horizontal |

## Comprobación rápida del contrato (opcional)

Con una cookie de sesión válida (`amano_auth`), se pueden verificar las cinco operaciones documentadas en `contracts/notifications-api.md`:

```bash
# Listado para el popover y para la bandeja
curl -b 'amano_auth=<cookie>' 'http://localhost/api/v1/notifications?limit=5&order=unread-first'
curl -b 'amano_auth=<cookie>' 'http://localhost/api/v1/notifications?limit=20&order=history'

# Marcado individual y global
curl -X PATCH -b 'amano_auth=<cookie>' 'http://localhost/api/v1/notifications/{id}/read'
curl -X PATCH -b 'amano_auth=<cookie>' 'http://localhost/api/v1/notifications/read-all'

# Preferencias
curl -b 'amano_auth=<cookie>' 'http://localhost/api/v1/notifications/preferences'
curl -X PUT -H 'Content-Type: application/json' -b 'amano_auth=<cookie>' \
  -d '{"channels":{"inApp":true,"email":false},"eventTypes":{"remindersAndWhistles":true,"debtDueAndPendingCollections":true,"groupActivity":false,"friendActivity":true}}' \
  'http://localhost/api/v1/notifications/preferences'
```

Esperado: `200` en las operaciones válidas, `404` para ids inexistentes o ajenos, `400` con `properties.fields` si falta un booleano en `PUT`, y `401` sin sesión o con cuenta no verificada. Los campos y formatos se detallan en el contrato y en `data-model.md`.

## Solución de problemas

- `502` en `/api/...`: el backend todavía está arrancando; ver `docker compose logs backend`.
- Fallo de Flyway al arrancar: revisar la migración `V39`; no editar migraciones ya integradas, crear una nueva.
- `TruncateCoverageIT` en rojo: una tabla nueva no está en `TRUNCATED_TABLES` de `AbstractIntegrationIT`.
- Cobertura por debajo del umbral: revisar las pruebas listadas en `tasks.md` para la historia correspondiente; no reducir el umbral.
- Más detalles de entorno y ejecución en `README.md`.
