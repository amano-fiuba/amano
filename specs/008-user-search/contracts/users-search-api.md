# Contract: Users Search API

**Feature**: `008-user-search` | **Spec**: [spec.md](../spec.md) | **Plan**: [plan.md](../plan.md)

Endpoint autenticado con la cookie `amano_auth` (definida en `001-user-registration`).
Formato de errores: RFC 7807 ProblemDetail (`application/problem+json`), reutilizando
`ProblemDetailExceptionHandler`. El frontend lo consume por el proxy unificado `/api/*`.

## `GET /api/users/search?q={term}`

Busca cuentas registradas y verificadas para que una persona autenticada y verificada
encuentre a otras. Es la base de futuras acciones de amistad o grupos, que **no** forman
parte de este contrato.

### Autorización

- Requiere sesión válida de una cuenta **verificada**.
- Sin cookie, cookie inválida/expirada o cuenta no verificada → `401` (regla heredada de
  `003`, aplicada por `SecurityConfig` sobre `/api/users/**`).
- El usuario que busca se identifica exclusivamente por el principal de la sesión; la
  petición no lleva su id.
- No se requiere ningún rol adicional.

### Parámetros de consulta

| Parámetro | Tipo | Obligatorio | Reglas |
|---|---|---|---|
| `q` | text | Sí (aunque puede faltar en la URL) | Se recorta al inicio/fin; se compara sin distinguir mayúsculas/minúsculas; debe tener al menos 2 caracteres después del recorte. |

La coincidencia es **parcial** sobre nombre, apellido o correo electrónico. Los caracteres
`%` y `_` se tratan como texto literal, no como comodines. No hay paginación.

### Respuesta 200

Arreglo JSON con **como máximo 20** elementos, ordenados por `firstName` y luego
`lastName` (ambos en orden ascendente e insensible a mayúsculas), con desempate estable.
Cada elemento contiene **solo** estos campos:

```json
[
  {
    "id": 12,
    "firstName": "Ana",
    "lastName": "Pérez",
    "email": "ana.perez@example.com"
  },
  {
    "id": 31,
    "firstName": "Bruno",
    "lastName": null,
    "email": "bruno@example.com"
  }
]
```

| Campo | Tipo | Descripción |
|---|---|---|
| `id` | number | Identificador de la cuenta. No se muestra en la lista; queda disponible para acciones futuras fuera de alcance. |
| `firstName` | string | Nombre de la cuenta. |
| `lastName` | string \| null | Apellido de la cuenta; puede ser `null`. |
| `email` | string | Correo electrónico de la cuenta. |

**Nunca** se incluyen CBU/CVU, monedas, contraseña, hash de contraseña, avatar, fecha de
verificación ni otros datos privados. El avatar por defecto es una decisión de
presentación del frontend y no viaja en la respuesta.

Una búsqueda válida sin coincidencias devuelve `200` con `[]`. El mensaje "No se encontraron
usuarios" es responsabilidad de la interfaz.

### Respuesta 400 — término inválido

Se usa `FieldValidationException`; el cuerpo sigue el formato de errores del proyecto.

```json
{
  "type": "about:blank",
  "title": "Validación fallida",
  "status": 400,
  "detail": "La solicitud contiene campos inválidos",
  "properties": {
    "fields": {
      "q": "Ingresá al menos 2 caracteres para buscar"
    }
  }
}
```

No se ejecuta ninguna consulta a la base de datos cuando el término es inválido.

### Respuesta 401 — sin sesión o cuenta no verificada

Mismo cuerpo que el resto del ámbito protegido, definido por `001`/`003`:

```json
{
  "type": "about:blank",
  "title": "No autorizado",
  "status": 401,
  "detail": "Necesitas iniciar sesión para continuar"
}
```

No se devuelve ningún resultado.

### Respuesta 500 — error inesperado

ProblemDetail genérico del manejador global, sin detalles internos. El frontend lo trata
como fallo de red con el mensaje fijo de reintento.

## Contrato del frontend

- **Cliente**: `searchUsers(q)` codifica el término con `encodeURIComponent`, envía
  `credentials: 'include'` y devuelve `{ ok: true, users }` en 2xx,
  `{ ok: false, status, detail, fields }` en 4xx y `{ ok: false, network: true }` en 5xx o
  fallo de red.
- **Pantalla protegida**: formulario con `TextField` y botón "Buscar". Con menos de 2
  caracteres recortados no se llama al endpoint y se muestra el aviso; no se conservan
  resultados previos.
- **Resultados**: cada fila muestra únicamente nombre, apellido (o "No informado" si es
  `null`), correo y el avatar `/avatar-default.png`. El `id` no se muestra.
- **Sin resultados**: mensaje exacto "No se encontraron usuarios".
- **Error de red**: mensaje fijo "No se pudo realizar la búsqueda. Intenta nuevamente",
  sin resultados obsoletos.
- **Orden de respuestas**: si llega una respuesta de una búsqueda anterior, se descarta;
  solo se muestra el resultado del último término buscado.
- **Acceso**: la pantalla vive en `/users/search`, protegida por `ProtectedRoute`, y se
  enlaza desde `SiteNav` como "Buscar usuarios". Sin sesión, la ruta redirige a `/login`.
