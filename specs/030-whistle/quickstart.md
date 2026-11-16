# Quickstart: Whistle — aviso manual a un deudor

**Feature**: specs/030-whistle | **Date**: 2026-11-15

Guía de validación de punta a punta. Referencias: [contracts/debt-whistle.md](contracts/debt-whistle.md), [contracts/debt-whistle-activity.md](contracts/debt-whistle-activity.md), [data-model.md](data-model.md).

## Prerrequisitos

- Docker (para Testcontainers y para PostgreSQL vía Docker Compose).
- JDK 25. Si la máquina solo tiene JRE, se puede correr el backend en el contenedor `eclipse-temurin:25-jdk` con el socket de Docker montado, como en la auditoría del 2026-11-15.
- Node con `npm ci` ejecutado en `frontend/`.
- Un SMTP de prueba para ver los correos. Por ejemplo, Mailpit o GreenMail en `localhost:1025` con `spring.mail.host` y `spring.mail.port` apuntando ahí. Nunca un SMTP real.

## Verificaciones automatizadas

```bash
# Backend (unitarias + integración + JaCoCo ≥ 85 %)
cd backend && ./mvnw -B verify

# Frontend (lint + pruebas + cobertura V8 ≥ 85 % + build)
cd frontend && npm ci && npm run lint && npm run test:coverage && npm run build
```

Pruebas clave (ver `tasks.md`):

- `DebtWhistleServiceTest`: orden de validación (D4), ventana de 24 h en el límite exacto, `FAILED` no cuenta y cálculo de `retryAfterSeconds`.
- `DebtWhistleIT`: un correo en GreenMail, códigos 403/404/409/422/429/503, evento `DEBT_WHISTLED`, deuda sin cambios y concurrencia.
- `WhistleButton.test.jsx`, `GroupDebtsView.test.jsx`, `ExpenseDebtList.test.jsx`, `groupExpensesClient.test.js` y `activityDescriptions.test.js`.

## Escenarios manuales

**Preparación**:

- Grupo "Viaje" con Ana (administradora), Juan (miembro registrado) y "Pepe" (perfil simulado).
- Ana registra "Cena" por $16.000, pagada por ella y repartida entre Ana y Juan. Juan queda con una deuda `PENDING` de $8.000.
- Ana registra "Nafta" por $6.000, repartida entre Ana y Pepe.
- Juan registra "Taxi", pagado por él y repartido con Ana, así que Ana le debe a Juan.

1. **Avisar (US1)**: Ana abre "Viaje" → Deudas → "Mis deudas pendientes".
   - En la fila "Juan te debe", pulsa "Avisar". Se abre el modal "Avisar a Juan" con el monto, el concepto y la aclaración de 24 horas.
   - Si lo cierra con la ×, no pasa nada.
   - Si confirma, aparece el aviso "Le avisamos a Juan" y en el SMTP de prueba hay un correo para Juan con el asunto "Ana te recuerda una deuda - Viaje".
   - La deuda sigue `PENDING` y por $8.000.
2. **Desde el detalle del gasto (US1)**: Ana abre el gasto "Cena". En la lista de deudas, la de Juan tiene el botón "Avisar", con el mismo comportamiento.
3. **No es acreedor (US1)**: en la deuda de "Taxi" (Ana le debe a Juan), Ana no ve "Avisar". Un `curl` al endpoint con la sesión de Ana sobre esa deuda responde 403.
4. **Perfil simulado (US1, sujeto a Q3)**: en la deuda de Pepe por "Nafta", el botón aparece deshabilitado con el texto "Los perfiles simulados no reciben avisos". Por API responde 422 y no llega ningún correo.
5. **Límite (US2)**: enseguida, Ana vuelve a avisar a Juan, por la misma deuda o por otra del grupo.
   - Aparece "Ya le avisaste a Juan hace poco. Vas a poder volver a hacerlo en 23 h 59 min" y no llega un correo nuevo.
   - Avisar a Juan desde otro grupo sí funciona.
6. **Doble clic (US2)**: con DevTools en "Slow 3G", Ana confirma y vuelve a confirmar rápido (o usa dos pestañas). Llega un solo correo.
7. **Falla de correo (US2 y edge cases)**: con el SMTP de prueba detenido, avisar muestra "No pudimos enviar el aviso…". Si se levanta el SMTP y se reintenta, el aviso sale (la falla no consumió el límite).
8. **Historial (US3)**: Deudas → Historial muestra "Ana le avisó a Juan por 8000.00 ARS (Cena)" una sola vez, y nada por los intentos fallidos o rechazados.
9. **Deuda saldada (edge case)**: Juan registra el pago total y Ana, con la pantalla vieja, pulsa "Avisar". Aparece "La deuda ya no tiene saldo pendiente" y la lista se recarga sin el botón.
10. **Accesibilidad**: navegando solo con teclado se llega a "Avisar", Enter abre el modal con el foco en la ×, Escape lo cierra y el lector anuncia "Avisar a Juan".

## Resultado esperado

- Un correo por aviso confirmado (SC-002) y ninguno por encima del límite (SC-003).
- Ningún correo para quien no es acreedor, deudas no avisables ni perfiles simulados (SC-004).
- Historial con el 100 % de los envíos y ninguno fallido (SC-005).
- Deudas sin cambios (SC-006).

## Resultado

_Pendiente: T034 (escenarios manuales 1 a 10) todavía no se ejecutó. Requiere la app levantada con un SMTP de prueba y un navegador; no se hizo en esta sesión._

Verificaciones automatizadas al 2026-11-15: backend y frontend en verde (ver plan.md, "Resultados de verificación").
