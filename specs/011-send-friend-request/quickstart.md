# Quickstart: validación de solicitudes de amistad (011)

**Fecha**: 2026-10-30 | **Spec**: [spec.md](./spec.md) | **Contrato**: [contracts/friendship-requests-api.md](./contracts/friendship-requests-api.md)

Guía para verificar de punta a punta el envío y la bandeja de solicitudes pendientes. Solo
comandos y escenarios observables.

## Prerrequisitos

- Entorno de desarrollo de `first-steps.md` (base en Docker, backend con `mvn spring-boot:run`,
  frontend con `npm run dev`).
- **Dos cuentas verificadas** (A y B) para probar emisor y destinatario.
- **Reiniciar el backend** después de actualizar la rama: Flyway aplica
  `V12__create_friendship_requests.sql` al arrancar.

## 1. Verificaciones automatizadas

```bash
cd backend && ./mvnw -B verify
cd frontend && npm ci && npm run lint && npm run test:coverage && npm run build
```

| Suite | Cubre |
|-------|-------|
| `friendship/FriendshipRequestServiceTest` | Envío válido, auto-solicitud, pendiente misma e inversa dirección, amistad existente, destinatario inexistente/no verificado, `recipientId` inválido, traducción de la carrera, listado y orden |
| `friendship/FriendshipRequestIT` | Flujo HTTP completo sobre PostgreSQL: 201 y persistencia `PENDING`, listado solo del destinatario, 400/401/404/409, aislamiento entre cuentas y datos expuestos |
| `friendship/FriendshipRequestSchemaIT` | Restricciones de `V12`: auto-relación, catálogo de estados, coherencia de `responded_at`, unicidad pendiente/aceptada por pareja |
| `pages/UsersSearchPage.test.jsx` | Botón "Agregar" habilitado, carga, éxito, 409, 400, 404, red, 401 y que no se alteren otras filas |
| `pages/FriendsPage.test.jsx` | Carga, lista, vacío, error con reintento, 401, orden y datos visibles |
| `api/friendsClient.test.js` | Contrato del cliente: URL, método, cuerpo, `credentials`, parseo de 2xx/4xx/red |
| `components/SiteNav.test.jsx`, `App.test.jsx` | Acceso "Amigos", ruta protegida y redirección sin sesión |

## 2. Escenarios manuales

### A. Enviar una solicitud (US1)

1. Iniciar sesión como **A** (verificada) e ir a "Buscar usuarios".
2. Buscar a **B** y pulsar **Agregar** en su fila.
3. **Esperado**: el botón muestra "Enviando…", luego "Solicitud enviada" deshabilitado y
   aparece "Solicitud de amistad enviada".
4. Verificar en la base:

   ```sql
   SELECT id, sender_id, recipient_id, status, created_at
   FROM friendship_requests
   ORDER BY id DESC;
   ```

   **Esperado**: una fila `PENDING` con el id de A como `sender_id` y el de B como
   `recipient_id`.

### B. Notificación y bandeja (US3)

1. Cerrar sesión e iniciar como **B**.
2. Abrir "Amigos" desde la barra de navegación.
3. **Esperado**: la sección "Solicitudes" muestra a A con nombre, apellido, correo y avatar
   por defecto, y la etiqueta "Pendiente".
4. Con una cuenta **C** sin solicitudes pendientes, abrir "Amigos".
   **Esperado**: "No tenés solicitudes de amistad pendientes".
5. Iniciar como **A** y abrir "Amigos". **Esperado**: la solicitud enviada a B no aparece en
   la bandeja de A.

### C. Reglas de negocio (US2)

1. Como **A**, intentar enviar otra solicitud a **B**.
   **Esperado**: "Ya existe una solicitud de amistad pendiente entre ambos usuarios"; no se
   crea una segunda fila.
2. Como **B**, intentar enviar una solicitud a **A** (dirección inversa).
   **Esperado**: el mismo mensaje de duplicado; no se crea una fila nueva.
3. Por API, intentar enviarse una solicitud a la propia cuenta:

   ```bash
   curl -s -X POST http://localhost:8080/api/users/me/friendship-requests \
     -H 'Content-Type: application/json' -b 'amano_auth=<cookie-de-A>' \
     -d '{"recipientId": <id-de-A>}'
   ```

   **Esperado**: 400 "No podés enviarte una solicitud de amistad a vos mismo".
4. Enviar a un id inexistente. **Esperado**: 404 "No se encontró el usuario".
5. (Preparación manual de amistad) Insertar una fila `ACCEPTED` con `responded_at = now()`
   entre A y B; intentar enviar una nueva solicitud.
   **Esperado**: 409 "Ya son amigos".

### D. Acceso protegido y concurrencia (US4)

1. Sin sesión, llamar a `POST /api/users/me/friendship-requests` y a
   `GET /api/users/me/friendship-requests/received`.
   **Esperado**: 401 en ambos casos.
2. Con una cuenta **no verificada**, repetir. **Esperado**: 401.
3. Disparar dos envíos simultáneos de A hacia B (dos pestañas o `curl` en paralelo, partiendo
   de que no exista una pendiente).
   **Esperado**: una sola fila `PENDING`; el segundo intento responde 409.

### E. Errores y sesión

1. Detener el backend y pulsar "Agregar". **Esperado**: "No se pudo enviar la solicitud.
   Intentá nuevamente"; la fila no queda como "Solicitud enviada".
2. Abrir "Amigos" con el backend detenido. **Esperado**: "No se pudieron cargar tus
   solicitudes. Intentá nuevamente" con "Reintentar".
3. Expirar la sesión y volver a enviar. **Esperado**: redirección al acceso sin datos visibles.

## 3. Regresiones a observar

- El buscador de usuarios (008) busca igual que antes: mismo formulario, mismo mínimo de 2
  caracteres y mismos datos por resultado; solo cambia el botón "Agregar".
- El contrato `GET /api/users/search` no cambia (no se agregan campos de amistad).
- Las rutas protegidas existentes y la barra de navegación siguen funcionando; "Amigos" es un
  ítem adicional.
- No se modifican cuentas, gastos ni gastos recurrentes.

## Resultado de la validación

- **Automatizada**: pendiente de ejecutar después de la implementación.
- **Manual**: pendiente; la primera prueba visual la realiza la persona usuaria y luego se
  registran los resultados acá.
