# Quickstart: Extensión de grupos y buscador (021)

## 1. Preparación

```bash
docker compose up --build
```

`V15` y `V16` se aplican solas al arrancar. `V16` borra la columna `members_cannot_edit_settings`.

## 2. Validación manual

Con dos cuentas verificadas, Ana y Bruno.

1. **Buscador**: Ana busca a Bruno y envía la solicitud. Ana recarga, busca y ve "Solicitud enviada". Bruno busca a Ana y ve "Pendiente". Bruno acepta desde "Solicitudes" y ambos ven "Amigos".
2. **Editar como administrador**: Ana crea un grupo con Bruno, toca "Editar", cambia nombre y descripción y guarda. Vuelve a editar, cambia algo y cancela: no cambia nada.
3. **Foto**: Ana sube una foto y guarda. Luego "Eliminar foto" y guarda: vuelve el avatar por defecto y el historial registra el cambio.
4. **Casilla**: como Bruno no se ve "Editar". Ana activa "Pueden editar datos del grupo" y Bruno ya puede editar. "Configuración del grupo" sigue sin aparecerle.
5. **Simulado**: Ana agrega un perfil simulado y su fila muestra "Simulado" y "Miembro".

## 3. Regresión

Solicitudes de amistad (011, 012), lista de amigos (013), permisos y miembros del grupo (010).
