# Quickstart: Renovación de la interfaz (023)

## 1. Preparación

```bash
docker compose up --build
```

No hay migraciones. Hacen falta dos cuentas verificadas, Ana con foto de perfil y Bruno sin relación con Ana.

## 2. Validación manual

Hacer los pasos 1 y 2 en modo claro y en modo oscuro.

1. **Botones**: pasar el cursor por un botón principal: se eleva y su borde inferior se vuelve naranja. Apretarlo: se hunde y queda naranja con texto oscuro. Repetir con el "+" y con una acción destructiva, que sigue en rojo.
2. **Patrón**: en varias pantallas, el patrón se ve detrás del contenido, nunca sobre las tarjetas, y no bloquea clics.
3. **Foto en solicitudes**: Ana le envía una solicitud a Bruno. Bruno ve la foto de Ana en la campanita y en "Amigos". Con la solicitud pendiente, Ana pide `/api/users/{id de Bruno}/avatar` y recibe 404.
4. **Rechazo**: Bruno rechaza la solicitud. La foto de Ana vuelve a responder 404 para Bruno.
5. **Barra**: el orden es perfil, actividad personal, grupos, amigos, notificaciones, tema y cierre de sesión.
6. **Actividad**: con gastos, `/activity` los lista de más nuevo a más viejo. "Ver todo" de la pantalla principal lleva ahí. Con una cuenta sin gastos se ofrece registrar el primero.
7. **Grupo**: con más de 960px se ven miembros, gastos y actividad en tres columnas. Con 960px o menos se apilan en ese orden.
8. **Celular**: a 360px no hay scroll horizontal, y el panel de la campanita entra completo.
9. **Saldos**: ningún miembro muestra deudas, porque el backend todavía no los envía.
10. **Crear grupo**: con una cuenta con varios amigos, abrir "Crear nuevo grupo". Filtrar por parte de un nombre, tildar a dos personas, borrar el filtro y comprobar que siguen tildadas. Sumar un perfil simulado y crear el grupo: se abre con todos, y el historial muestra cada alta.
11. **Sin elegir a nadie**: intentar crear un grupo sin personas: se pide elegir al menos una.
12. **Sin amigos**: con una cuenta sin amigos, la pantalla lo indica y permite crear el grupo con un perfil simulado.
13. **Fotos en el buscador**: buscar a un amigo con foto y a una cuenta sin relación con foto. Se ve la foto del amigo y el avatar por defecto de la otra cuenta, sin imágenes rotas. Buscar un grupo con foto y uno sin foto.

## 3. Regresión

Solicitudes de amistad (011, 012), lista de amigos (013), gastos del grupo (017), pagos (018), abandonar grupo (020), edición del grupo y buscador (021).
