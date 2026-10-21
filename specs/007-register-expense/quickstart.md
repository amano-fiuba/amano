# Quickstart: Registro de gasto personal (007)

**Feature**: `007-register-expense` | **Plan**: [plan.md](./plan.md) | **Contrato**: [contracts/expenses-api.md](./contracts/expenses-api.md)

Guía de verificación ejecutable. No contiene implementación: los cuerpos de modelos, servicios,
controladores, migración y pruebas se escriben en la fase de implementación (`tasks.md`).

## Prerrequisitos

- Java 25 y Maven (wrapper `./mvnw` disponible en `backend/`).
- Node 24 y `npm` en `frontend/`.
- Docker en ejecución **para las pruebas de integración** del backend (Testcontainers levanta
  `postgres:18-alpine`) y para el arranque con `compose.yaml`.
- Para arrancar en local: PostgreSQL y las variables de entorno de `.env.example`
  (`POSTGRES_*`, `JWT_SECRET`, etc.).

## Comandos de verificación del repositorio

```bash
# Backend: unitarias + web + integración (Testcontainers) + JaCoCo (umbral 85 % de líneas)
cd backend && mvn -B verify

# Frontend: instalación limpia + lint + pruebas con cobertura (umbral 85 %) + build
cd frontend && npm ci && npm run lint && npm run test:coverage && npm run build
```

Resultado esperado: ambas verificaciones en verde; reportes de cobertura
`backend/target/site/jacoco/index.html` y `frontend/coverage/` por encima del 85 %.

## Ejecutar la aplicación

```bash
# Backend (puerto 8080)
cd backend && ./mvnw spring-boot:run

# Frontend (puerto 5173, proxy /api → :8080)
cd frontend && npm run dev
```

Alternativa integrada con contenedores: `docker compose up` (ver `compose.yaml` y `Caddyfile`).
La migración `V4__create_expenses.sql` se aplica automáticamente al arrancar el backend; una
migración fallida impide el arranque (señal de error temprano).

## Escenarios de validación end-to-end

Se asume un usuario registrado (001) y verificado (003) que inició sesión (004); sin sesión,
`/expenses/new` redirige a `/login`.

### 1. Registro exitoso (US1, FR-001..FR-011, FR-031, FR-032, SC-001, SC-007)

1. Abrir `/expenses/new`. Verificar que la fecha está precargada con el día actual del
   dispositivo y que la moneda surge de la preferencia del perfil (ARS si no hay).
2. Completar monto `1.234,56`, moneda ARS, categoría "Alimentos", tipo "Único", medio "Efectivo"
   y descripción "Almuerzo"; confirmar.
3. Esperado: notificación de éxito, formulario reseteado (monto y descripción vacíos, fecha del
   día, moneda según perfil, categoría/tipo/medio sin selección) y permanencia en la pantalla.
4. En la base: una fila en `expenses` con `user_id` del usuario de la sesión, `amount =
   1234.56`, `currency = 'ARS'` y `idempotency_key` no nulo.

### 2. Validación en cliente (US2, FR-013..FR-020, SC-002)

Probar, uno por uno: monto vacío, `0`, negativo, `abc`, `1,234` (tres decimales), `1234.56`
(punto decimal), `1.000.000.000,00` (fuera de rango), fecha `31/02`, fecha futura, descripción
de 201 caracteres. En todos: mensaje junto al campo, envío bloqueado y **ninguna** fila nueva.
Corregir luego y verificar que el mensaje desaparece y el envío se habilita.

### 3. Precisión monetaria (FR-023, SC-004)

Registrar cada monto de la batería de SC-004 (`0,01` … `999.999.999,99`) en ARS, USD y EUR y
confirmar que se lee y muestra exactamente con la misma escala (`10,10` sigue siendo `10,10`).

### 4. Duplicados (FR-027, SC-003)

- **Doble clic**: confirmar dos veces rápido. Esperado: un solo gasto, botón "Guardando…"
  durante la operación.
- **Reintento tras timeout (mismo payload)**: forzar un timeout del cliente después de que el
  backend persistió (p. ej. interrumpir la respuesta) y reintentar el mismo envío con la misma
  `Idempotency-Key`. Esperado: `200`, un único gasto.
- **Misma clave con payload distinto**: tras el timeout de arriba, editar el monto y reenviar
  con la misma `Idempotency-Key`. Esperado: `422`, sin segundo gasto y con el original intacto;
  el frontend renueva la clave e informa que se debe confirmar de nuevo.
- **Concurrencia**: enviar dos solicitudes simultáneas con la misma clave. Esperado: una sola
  fila; respuestas `201`/`200` si el payload coincide, `201`/`422` si difiere.
- **Dos gastos idénticos deliberados**: registrar el mismo gasto dos veces (con reseteo entre
  medio). Esperado: dos filas, dos claves distintas.

### 5. Identidad y sesión (US3, FR-005, FR-006, FR-028, SC-001)

- Enviar una petición agregando `userId`/`participants`/`divisions` al cuerpo. Esperado: `400`,
  sin persistencia.
- Cerrar sesión y confirmar: redirección al login; los datos se conservan solo mientras el
  formulario siguió visible y no se restauran tras reautenticarse.
- Sin cookie: `401` con ProblemDetail.

### 6. Errores y resiliencia (FR-019, FR-026, FR-029, SC-005, SC-006)

- Simular `5xx` o caída de red al guardar: mensaje claro, sin notificación de éxito, sin fila,
  datos conservados.
- Revisar los cuerpos de error en todos los casos de la tabla de SC-006: sin trazas, SQL,
  nombres de clases/paquetes, tokens, correo ni datos de otros usuarios.

### 7. Accesibilidad y móvil (FR-033, FR-034, SC-009, SC-010)

- Recorrer el formulario solo con teclado (Tab/Shift+Tab/Enter/Espacio): todos los campos y el
  botón son alcanzables y operables; cada error queda asociado a su campo y el foco es visible.
- En un viewport móvil (p. ej. 375 px de ancho): todos los campos y el botón son visibles y
  utilizables sin desplazamiento horizontal.

## Verificación por capas con las herramientas del repo

```bash
# Solo unitarias del backend (rápidas)
cd backend && ./mvnw test

# Solo integración del backend (Testcontainers)
cd backend && ./mvnw verify -DskipUnitTests=false

# Frontend: un archivo de prueba
cd frontend && npx vitest run src/validation/expenseValidation.test.js
```

Herramientas ya configuradas: JUnit 5 + Mockito, Spring Boot Test + MockMvc + Testcontainers,
JaCoCo (en `mvn verify`), Vitest + React Testing Library y cobertura v8 (`npm run test:coverage`).
No se reducen umbrales ni se excluye código arbitrariamente (Constitución V).
