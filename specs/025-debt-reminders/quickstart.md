# Quickstart: validar recordatorios automáticos de deudas

**Feature**: `025-debt-reminders` | **Spec**: [spec.md](./spec.md) | **Plan**: [plan.md](./plan.md)

Esta guía permite comprobar de punta a punta que los recordatorios y el aviso al pagador funcionan. No reemplaza a `tasks.md` ni contiene la implementación.

## Prerrequisitos

- Java 25 y Maven (backend).
- Node.js y npm (frontend).
- Docker y Docker Compose (PostgreSQL y pruebas con Testcontainers).
- Un SMTP de desarrollo solo para la prueba manual. Las pruebas automatizadas usan GreenMail y **nunca** un SMTP real.
- Un grupo con `debtDueDays` configurado (definido por 022) y al menos un gasto de grupo que genere deudas.

## 1. Verificaciones automatizadas

### Backend

```bash
cd backend
mvn -B verify
```

- Ejecuta pruebas unitarias, de integración con Testcontainers y GreenMail, y JaCoCo.
- Debe terminar en verde y respetar el umbral de cobertura del 85 %.

Para acotar a la feature:

```bash
cd backend
mvn -B test -Dtest='*Reminder*Test'
mvn -B verify -Dit.test='*Reminder*IT'
```

### Frontend

```bash
cd frontend
npm ci
npm run lint
npm run test:coverage
npm run build
```

- Debe terminar en verde y respetar el umbral de cobertura del 85 %.

## 2. Validación manual del backend

### 2.1 Levantar el entorno

```bash
docker compose up -d postgres
cd backend
mvn spring-boot:run
```

Variables relevantes:

| Variable | Uso |
|----------|-----|
| `MAIL_HOST`, `MAIL_PORT`, `MAIL_USERNAME`, `MAIL_PASSWORD` | SMTP de desarrollo. |
| `REMINDERS_CRON` | Cron del job de recordatorios. Para una prueba rápida, usar una ejecución cada minuto. |
| `OVERDUE_CRON` | Cron del job de vencimiento de 022. |
| `APP_BASE_URL` | Base de los enlaces de otros correos; no se usa en los recordatorios. |

Si se usa un SMTP de captura local, apuntar `MAIL_HOST`/`MAIL_PORT` a ese servicio. No usar una cuenta real para las pruebas.

### 2.2 Preparar los datos

1. Registrar y verificar dos cuentas (Ana y Juan) y crear un grupo con Ana como administradora.
2. Agregar a Juan como miembro y, si se quiere probar el caso simulado, agregar un perfil simulado con un correo válido.
3. Configurar `debtDueDays` del grupo (por ejemplo, 30) usando la API de 022 o la base de datos, porque la 025 no expone ese campo.
4. Registrar un gasto pagado por Ana con Juan y el perfil simulado como participantes, con una fecha tal que la fecha límite quede a 5 días de hoy.
5. Confirmar que se generaron las deudas correspondientes.

### 2.3 Configurar los recordatorios

Con la cookie de sesión de Ana:

```http
GET /api/groups/{groupId}/reminder-settings
```

Respuesta esperada: `sendDays = 5`, `message = null`.

```http
PUT /api/groups/{groupId}/reminder-settings
Content-Type: application/json

{
  "sendDays": 3,
  "message": "¡Hola! Falta poco para el vencimiento."
}
```

Respuesta esperada: los valores guardados.

Probar también:

- `sendDays` fuera de `[7, 5, 3, 1]` → 400 con `fields.sendDays`.
- Mensaje de 501 caracteres → 400 con `fields.message`.
- Mensaje vacío → 200 y `message = null`.
- Con la cookie de Juan (miembro no administrador) → 403.

### 2.4 Ejecutar el job

Con `REMINDERS_CRON` configurado para ejecutarse cada minuto, esperar la ejecución (o reiniciar el backend para que tome el cron). El job:

- crea las filas de recordatorio y aviso que falten;
- reprograma las programadas si cambió la configuración;
- cancela las que ya no corresponden;
- envía las que llegan a su fecha.

### 2.5 Resultados esperados

- Juan recibe **un** correo por deuda, con grupo, concepto, monto y fecha límite.
- El perfil simulado recibe su correo en el correo cargado.
- Ana recibe **un** aviso por gasto, con los días restantes y su situación de deudas.
- Si Ana no debe nada, el aviso lo dice explícitamente; si debe, informa el total pendiente.
- Los correos de deudas en `PAYMENT_REPORTED` incluyen "pagado a verificar".
- Una deuda saldada antes del envío no recibe correo.
- Un gasto sin deudas pendientes no genera aviso.
- En el historial del grupo aparecen `DEBT_REMINDER_SENT` y `PAYER_DUE_NOTICE_SENT`, solo por envíos exitosos.
- Ejecutar el job de nuevo no reenvía nada.

### 2.6 Casos límite manuales

| Caso | Cómo provocarlo | Resultado esperado |
|------|-----------------|--------------------|
| Grupo sin `debtDueDays` | Dejar el campo en `NULL` | No se crean recordatorios ni errores |
| Fecha de envío ya pasada | Configurar `sendDays` mayor que los días restantes, con la fecha límite futura | Se envía en la ejecución siguiente |
| Deuda vencida | Usar un gasto con fecha límite pasada | No se envía; la deuda ya la marca 022 como `OVERDUE` |
| Cambio de tiempo | Cambiar de 5 a 7 días con un recordatorio programado | Se reprograma; el ya enviado no cambia |
| Cambio de mensaje | Cambiar el mensaje antes del envío | El próximo correo usa el mensaje nuevo |
| Fallo de entrega | Usar un correo inválido para un perfil simulado | El recordatorio queda `FAILED`; los administradores reciben el aviso; no se reintenta |
| Dos deudas con la misma fecha | Registrar un gasto con dos deudores | Cada deudor recibe un correo por su deuda |
| Dos gastos del mismo pagador | Registrar dos gastos con la misma fecha límite | El pagador recibe un aviso por cada gasto |

## 3. Validación manual del frontend

```bash
cd frontend
npm run dev
```

1. Entrar al grupo como Ana (administradora) y abrir "Configuración del grupo".
2. Ver el panel "Recordatorios" con el selector de días y el campo de mensaje.
3. Guardar un mensaje y un tiempo; ver el snackbar de éxito y que los valores persisten al recargar.
4. Intentar guardar más de 500 caracteres; ver el error en el formulario.
5. Entrar como Juan (miembro no administrador): el panel no debe aparecer.
6. Abrir el historial del grupo: ver las frases de los recordatorios y el filtro "Recordatorios".

## 4. Criterios de aceptación cubiertos

| Criterio | Dónde se valida |
|----------|-----------------|
| SC-001: un recordatorio por deuda, cero reenvíos | Pruebas de integración y ejecución repetida del job |
| SC-002: cero recordatorios de deudas saldadas | Pruebas con deudas `PAID`/`VERIFIED` |
| SC-003: recordatorios operativos sin configuración | Grupo con `debtDueDays` y configuración predeterminada |
| SC-004: el correo identifica grupo, concepto, monto y fecha límite | Revisión del cuerpo del correo |
| SC-005: cambio de configuración en menos de 1 minuto | Panel de frontend |
| SC-006: el 100 % de los envíos exitosos queda en el historial | Eventos `DEBT_REMINDER_SENT` y `PAYER_DUE_NOTICE_SENT` |
| SC-007: un aviso por gasto con deuda pendiente | Pruebas de aviso al pagador |

## 5. Limpieza

- Detener el backend y el contenedor de PostgreSQL.
- Si se usó un SMTP de captura, detenerlo.
- No dejar correos reales ni credenciales en el repositorio.
