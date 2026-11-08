# Quickstart: validar la 027

**Plan**: [plan.md](./plan.md) | **Contratos**: [contracts/](./contracts/)

## Requisitos

- Docker (Testcontainers para las pruebas de integración del backend).
- Java 25 + Maven; Node con `npm ci` hecho en `frontend/`.
- Dos usuarios verificados que sean amigos entre sí (A y B) y un grupo de A con al menos 10 miembros.

## Verificaciones automáticas (obligatorias, Principio V)

```bash
cd backend && mvn -B verify                       # JaCoCo ≥ 85 %
cd frontend && npm run lint && npm run test:coverage && npm run build   # Vitest ≥ 85 %
```

Las pruebas nunca llaman al BCRA real: `CurrencyCatalogProvider` y `ExchangeRateProvider` se reemplazan por dobles.

## Escenarios manuales

Levantar la app (`docker compose up` o `mvn spring-boot:run` + `npm run dev`) e iniciar sesión como A.

| # | Paso | Resultado esperado | Requisito |
|---|---|---|---|
| 1 | Botón "+" → pasar el cursor por "Registrar gasto" | Submenú con Gasto personal / con amigo / grupal, con íconos | FR-001 |
| 2 | Repetir con teclado (Tab, Enter, →, ↓, Escape) y en ancho 375 px | Se abre y cierra sin mouse; no se sale de la pantalla | FR-002, FR-003 |
| 3 | "Gasto personal" | Panel celeste sobre blanco, sin "Gasto compartido", Cancelar izq. / Guardar gasto der. | FR-018, FR-019 |
| 4 | Ícono de calendario → ir al mes anterior → elegir un día | El campo muestra esa fecha; días futuros deshabilitados | FR-010, SC-002 |
| 5 | Moneda → "Ver más…" → buscar "real" → elegir BRL → guardar | Gasto guardado en BRL; las preferencias del perfil no cambian | FR-011–FR-014, SC-003 |
| 6 | Cortar la red al BCRA (o doble que devuelva 503) → "Ver más…" | Mensaje de error + "Reintentar"; las monedas propias siguen disponibles | Edge case |
| 7 | Cargar monto y presionar "Cancelar" | Pide confirmación antes de descartar | FR-019 |
| 8 | "Gasto grupal" → elegir el grupo de 10 miembros | Formulario con Categoría, Tipo de gasto, Medio de pago | FR-020, FR-021 |
| 9 | Alternar Partes iguales / Personalizado / Porcentajes | Filas de igual altura; el modal no cambia de alto | FR-022, SC-004 |
| 10 | Con 20 miembros | El panel derecho scrollea; Cancelar/Guardar visibles | FR-023, FR-024, SC-005 |
| 11 | Guardar un gasto de grupo en BRL (grupo en ARS) | Se convierte con cotización del BCRA, como en 022 | FR-014, SC-007 |
| 12 | "Gasto con amigo" → elegir B → $10.000, partes iguales, pagó A | Gasto creado; B debe $5.000 a A | FR-030–FR-032 |
| 13 | Ir a Amigos → eliminar a B | Bloqueado por deuda directa pendiente | FR-032, US4-5 |
| 14 | Ver saldos y deudas del grupo de A y B (si comparten uno) | No incluyen el gasto con amigo | R5 |
| 15 | Usuario sin amigos / sin grupos → "Gasto con amigo" / "Gasto grupal" | Mensaje y acceso a Buscar usuarios / Crear grupo | US3-5, US4-3 |

## Pruebas de API rápidas

```bash
curl -b cookies.txt http://localhost:8080/api/currencies            # 200, 32 monedas como máximo
curl -b cookies.txt -X POST http://localhost:8080/api/users/me/friends/12/expenses \
  -H 'Content-Type: application/json' -H "Idempotency-Key: $(uuidgen)" -d @friend-expense.json   # 201
```

El cuerpo de ejemplo está en [contracts/shared-expenses-api.md](./contracts/shared-expenses-api.md).
