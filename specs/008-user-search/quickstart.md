# Quickstart: Buscador de usuarios registrados

**Feature**: `008-user-search` | **Spec**: [spec.md](./spec.md) | **Plan**: [plan.md](./plan.md)

Guía para validar la feature de punta a punta. Los contratos y el modelo están en
[contracts/users-search-api.md](./contracts/users-search-api.md) y
[data-model.md](./data-model.md).

## Prerrequisitos

- Java 25 y Maven (el repositorio usa el wrapper `./mvnw`).
- Node.js compatible con el `package.json` de `frontend/`.
- Docker disponible para Testcontainers y para el entorno integrado.
- Variables de entorno del proyecto (`.env` / `.env.example`). El backend de pruebas usa
  Testcontainers PostgreSQL; no requiere una base local.

## Verificación automatizada

Desde la raíz del repositorio:

```bash
cd backend && ./mvnw -B verify
cd ../frontend && npm ci && npm run lint && npm run test:coverage && npm run build
```

Resultado esperado:

- Backend: compilación correcta, pruebas unitarias e IT en verde y cobertura de líneas
  JaCoCo ≥ 85 %.
- Frontend: lint sin errores, Vitest en verde, cobertura de líneas ≥ 85 % y build de
  producción correcto.

Pruebas específicas de la feature:

```bash
cd backend && ./mvnw -B -Dtest=UserSearchServiceTest test
cd backend && ./mvnw -B -Dit.test=UserSearchIT verify
cd frontend && npx vitest run src/api/usersClient.test.js src/pages/UsersSearchPage.test.jsx src/components/SiteNav.test.jsx src/App.test.jsx
```

## Escenario manual con el entorno integrado

1. Levantar la aplicación:

   ```bash
   docker compose up -d --build
   ```

2. Crear y verificar al menos tres cuentas (por ejemplo, dos verificadas y una pendiente),
   con nombres, apellidos y correos que permitan probar coincidencias parciales. Para las
   verificadas, completar el flujo de verificación de 003.
3. Iniciar sesión con una cuenta verificada y abrir "Buscar usuarios" desde la barra de
   navegación (`/users/search`).
4. Buscar un fragmento de nombre, apellido o correo, en mayúsculas o minúsculas, con
   espacios al inicio/fin. Verificar que:
   - aparecen solo cuentas verificadas;
   - la propia cuenta no aparece;
   - la lista tiene como máximo 20 filas, ordenadas por nombre y apellido;
   - cada fila muestra solo nombre, apellido, correo y el avatar `/avatar-default.png`;
   - no se muestran CBU/CVU, monedas, contraseñas ni otros datos privados.
5. Con un término de 0 o 1 carácter (o solo espacios), verificar que no se ejecuta la
   búsqueda y aparece el aviso de mínimo 2 caracteres.
6. Buscar un término válido sin coincidencias y verificar el mensaje exacto
   "No se encontraron usuarios".
7. Simular un error de red (por ejemplo, detener el backend) y verificar el mensaje fijo
   "No se pudo realizar la búsqueda. Intenta nuevamente", sin resultados obsoletos.
8. Sin sesión, abrir `/users/search`: debe redirigir a `/login`.
9. Sin sesión, comprobar el endpoint directamente:

   ```bash
   curl -i 'http://localhost:8080/api/users/search?q=an'
   ```

   Resultado esperado: `401` con ProblemDetail (`title`: "No autorizado").
10. Con una cuenta autenticada pero **no verificada**, repetir el paso 9 usando su cookie:
    resultado esperado `401`, sin resultados.

## Comprobaciones puntuales del contrato

- `GET /api/users/search?q=  an  ` debe comportarse igual que `q=an` (recorte de espacios).
- `GET /api/users/search?q=%25` no debe interpretar `%` como comodín; busca el literal `%`.
- Una búsqueda con `q` de un solo carácter devuelve `400` con
  `properties.fields.q` = "Ingresá al menos 2 caracteres para buscar".
- La respuesta 200 es un arreglo; un resultado sin apellido puede traer
  `"lastName": null` y la interfaz muestra "No informado".
- La lista nunca supera 20 elementos y no existe paginación.
