# A Mano

Aplicación web y mobile para dividir gastos compartidos entre un grupo de personas, inspirada en Splitwise.

A Mano permite registrar gastos personales y de grupo, calcular cuánto debe cada persona, registrar pagos y mantener un historial de la actividad. Es un proyecto universitario (FIUBA) en desarrollo activo: las funcionalidades principales ya están implementadas y cubiertas por pruebas.

La versión pública corre en <https://amano-fiuba.duckdns.org>.

## ¿Qué hace?

- **Cuentas y perfil:** registro con verificación por correo, inicio de sesión con token JWT (cookie `HttpOnly`), recuperación de contraseña, edición de perfil, avatar, monedas y CBU/CVU.
- **Gastos e ingresos personales:** carga de gastos (únicos y recurrentes, con distintos modos de entrada), pausa/finalización de series, búsqueda con filtros, ingresos y cálculo del saldo mensual.
- **Estadísticas:** resumen de ingresos y gastos, series por mes y/año, con gráficos.
- **Amistades:** búsqueda de usuarios, solicitudes de amistad, lista de amigos y gastos compartidos con un amigo sin crear un grupo.
- **Grupos:** creación y edición, miembros registrados y perfiles simulados, roles de administrador, permisos, foto del grupo, historial de actividad y opción de abandonar el grupo.
- **Gastos de grupo:** división en partes iguales, por montos o por porcentajes, múltiples monedas con cotizaciones de la API pública del BCRA, gastos recurrentes y vencimientos configurables.
- **Deudas y pagos:** estados (pendiente/vencida), propuesta de liquidación, pagos parciales y validación de pagos reportados por un administrador.
- **Recordatorios:** avisos automáticos de deudas y pagadores, y notificaciones manuales («silbatos») con límite de frecuencia.
- **Interfaz:** SPA con temas, notificaciones, estados de carga y acceso protegido por sesión.

Cada funcionalidad tiene su especificación en [`specs/`](specs/) (por ejemplo, `007-register-expense`, `010-groups-management`, `022-group-debt-management`, `034-income-statistics`).

## Stack y herramientas

| Capa | Tecnología |
| --- | --- |
| Frontend | React 19, Vite 8, React Router 8, Sass |
| Backend | Java 25, Spring Boot 4.1.1, Spring Security + JWT, Spring Data JPA, Flyway, Maven |
| Base de datos | PostgreSQL 18 |
| Pruebas | JUnit, Spring Boot Test, Testcontainers, GreenMail, JaCoCo (backend); Vitest, React Testing Library, ESLint (frontend) |
| Infraestructura | Docker + Docker Compose, nginx (frontend), Caddy (HTTPS en producción) |
| CI/CD | GitHub Actions, imágenes en GHCR, despliegue en Raspberry Pi |

Herramientas a instalar en la máquina:

| Herramienta | Versión de referencia | Verificación |
| --- | --- | --- |
| Git | cualquiera reciente | `git --version` |
| Node.js / npm | 24.18.0 / 11.16.0 | `node -v` / `npm -v` |
| Java (OpenJDK) | 25 | `java -version` |
| Maven | 3.9.11 | `mvn -v` |
| Docker + Docker Compose | 29.9.1 / 5.6.0 | `docker --version` / `docker compose version` |

Spring Boot, PostgreSQL, nginx y el resto de las dependencias se fijan en el proyecto y no hace falta instalarlos por separado. Requisitos mínimos: Node 20.19+ o 22.12+, Java 17+ y Maven 3.6.3+; de todas formas conviene igualar las versiones de la tabla.

Tu usuario debe poder usar Docker sin `sudo`: comprobalo con `docker ps`. Si da `permission denied`, agregá tu usuario al grupo y reiniciá la sesión:

```
sudo usermod -aG docker $USER
```

## Estructura del repositorio

```
amano/
├── backend/             ← API Spring Boot + Maven
│   ├── src/main/        ← código y migraciones Flyway (db/migration)
│   └── src/test/        ← pruebas (*Test rápidas, *IT de integración)
├── frontend/            ← SPA React + Vite
│   └── src/             ← páginas, componentes y pruebas (*.test.jsx)
├── specs/               ← especificaciones de las funcionalidades
├── scripts/             ← scripts para cargar datos de demostración
├── .github/workflows/   ← CI y publicación de imágenes
├── .env.example         ← modelo de variables de entorno (sin secretos)
├── compose.yaml         ← desarrollo: db, backend, frontend y adminer
├── compose.prod.yaml    ← producción: imágenes publicadas + Caddy
├── Caddyfile            ← proxy HTTPS de producción
└── README.md            ← esta guía
```

## Configuración inicial

1. Clonar el repositorio:

   ```
   git clone git@github.com:amano-fiuba/amano.git
   cd amano
   ```

   Si no tenés configurada una clave SSH, usá `git clone https://github.com/amano-fiuba/amano.git`.

2. Crear el archivo de configuración local a partir del ejemplo:

   ```
   cp .env.example .env
   ```

3. Generar valores propios para la contraseña de la base y el secreto de las sesiones, y reemplazarlos en `.env` (`POSTGRES_PASSWORD` y `JWT_SECRET`). Ejecutá el comando dos veces, una por valor:

   ```
   openssl rand -hex 16
   ```

   **Usar solo letras y números**: caracteres como `&`, `$`, `#` o `!` rompen la lectura del archivo desde la terminal. El `.env` no se sube a GitHub (está en `.gitignore`).

Variables principales (la lista completa y sus comentarios están en `.env.example`):

| Variable | Para qué sirve |
| --- | --- |
| `POSTGRES_DB`, `POSTGRES_USER`, `POSTGRES_PASSWORD` | Base de datos; `POSTGRES_PASSWORD` es obligatoria |
| `POSTGRES_PORT` | Puerto local de la base (5432 por defecto) |
| `JWT_SECRET` | Firma de las cookies de sesión; **obligatoria**, el backend no arranca sin ella |
| `JWT_EXPIRATION` | Duración de la sesión en segundos (86400 por defecto) |
| `MAIL_HOST`, `MAIL_PORT`, `MAIL_USERNAME`, `MAIL_PASSWORD` | SMTP para verificación y avisos; en desarrollo pueden quedar con los valores por defecto |
| `APP_BASE_URL` | Base de los links enviados por correo (`http://localhost:5173` con Vite, `http://localhost:8081` con Compose) |
| `BCRA_BASE_URL` | API pública de cotizaciones del BCRA, sin credenciales |
| `OVERDUE_CRON`, `REMINDERS_CRON` | Horarios de los trabajos diarios de deudas y recordatorios |

Las variables de producción (`COMPOSE_FILE`, `DATA_DIR`, `DOMAIN`) deben quedar comentadas en la computadora.

## Cómo levantarlo

Hay dos modos: el **stack completo** con Docker (todo junto, como en producción) y el **modo desarrollo** (solo la base en Docker; backend y frontend se ejecutan en la máquina con recarga automática).

### Stack completo con Docker

Desde la raíz del repositorio:

```
docker compose up -d --build
docker compose ps
```

La primera construcción tarda varios minutos porque descarga imágenes y dependencias. `db` debe aparecer como `healthy` y los demás servicios como `running`.

| Servicio | Dirección |
| --- | --- |
| Aplicación (frontend) | <http://localhost:8081> |
| API (a través de nginx) | <http://localhost:8081/api/health> |
| Base de datos | `127.0.0.1:5432` (o el `POSTGRES_PORT` configurado) |
| Adminer (explorador de la base) | <http://localhost:8082> |

El backend no publica puerto propio: se accede solo por `/api/`, que nginx reenvía al contenedor. Para apagar todo conservando los datos: `docker compose down`. Para empezar de cero (borra los datos): `docker compose down -v`.

### Modo desarrollo (con recarga automática)

1. Levantar solo la base de datos:

   ```
   docker compose up -d db
   ```

2. Levantar el backend (las variables se cargan solo para ese comando):

   ```
   cd backend
   (set -a; source ../.env; set +a; mvn spring-boot:run)
   ```

   Queda en <http://localhost:8080>; `curl http://localhost:8080/api/health` debe devolver `{"status":"UP"}`.

3. Levantar el frontend, en otra terminal:

   ```
   cd frontend
   npm install
   npm run dev
   ```

   Queda en <http://localhost:5173>. Vite reenvía `/api` al backend local. `npm install` solo hace falta la primera vez o cuando cambia `package.json`.

Para volver al modo desarrollo después de usar el stack completo: `docker compose stop backend frontend adminer`.

### Datos de demostración

Con el stack levantado y el backend ya migrado, se pueden cargar cuentas y gastos de ejemplo:

```
./scripts/seed-full-demo.sh
```

Los detalles están en los comentarios de cada script.

## Pruebas y calidad

Backend (desde `backend/`, sin cargar `.env` ni levantar la base):

```
mvn test       # pruebas rápidas (unitarias y web)
mvn verify     # rápidas + integración + cobertura; necesita Docker
```

Las pruebas de integración (`*IT`) levantan su propio PostgreSQL con Testcontainers y usan GreenMail para el correo: no tocan la base de desarrollo ni envían correos reales. JaCoCo exige al menos 85 % de cobertura de líneas.

Frontend (desde `frontend/`):

```
npm test               # ejecuta las pruebas una vez
npm run test:coverage  # pruebas + verificación de cobertura (85 %)
npm run test:watch     # vuelve a ejecutar al guardar
npm run lint           # revisa estilo y errores con ESLint
npm run build          # compila para producción
```

Antes de abrir un Pull Request, corré lo mismo que corre GitHub:

```
cd backend && mvn -B verify
cd ../frontend && npm ci && npm run lint && npm run test:coverage && npm run build
```

## Integración continua y despliegue

- `.github/workflows/ci.yml`: en cada Pull Request (y al llegar a `main`) corre `mvn -B verify` en el backend y `npm ci`, lint, cobertura y build en el frontend, en paralelo.
- `.github/workflows/publish-images.yml`: al integrar a `main`, publica imágenes ARM64 en GHCR (`amano-backend` y `amano-frontend`).
- La Raspberry Pi se actualiza sola cada pocos minutos, descarga las imágenes nuevas y reinicia los servicios. `compose.prod.yaml` usa esas imágenes y `Caddyfile` maneja el HTTPS automático.

**No usar `compose.prod.yaml` en la computadora**: las imágenes son ARM64 y el archivo intenta abrir los puertos 80 y 443. Además, todo lo que llega a `main` queda publicado en Internet: no integrar secretos ni datos que no puedan estar expuestos.

## Contribuir

`main` está protegida: los cambios entran por Pull Request con los checks en verde, la rama actualizada y las revisiones resueltas. Se usan _Conventional Commits_ (`feat:`, `fix:`, `docs:`, `chore:`, `test:`, `ci:`). Nunca subir el `.env` ni carpetas generadas (`node_modules/`, `target/`, `dist/`).

Las decisiones de diseño y los contratos de cada funcionalidad están en [`specs/`](specs/).

## Problemas frecuentes

- **`Could not resolve placeholder 'POSTGRES_PASSWORD'` / error con `JWT_SECRET` al arrancar el backend en modo desarrollo:** no cargaste el `.env`. Usá el comando con `source ../.env` de la sección anterior.
- **`password authentication failed for user "amano"`:** la contraseña del `.env` no coincide con la del volumen existente. Recreá la base (borra los datos locales) con `docker compose down -v && docker compose up -d db`.
- **Error de parseo del `.env` con caracteres como `&` o `#`:** la contraseña tiene símbolos que el shell interpreta. Generá otra solo con letras y números y recreá la base como arriba.
- **`permission denied` al usar Docker:** agregá tu usuario al grupo `docker` (ver la sección de herramientas).
- **Un puerto está ocupado:** el backend de desarrollo usa el 8080, el frontend de Compose el 8081, Adminer el 8082 y la base el 5432. Cambiá el valor correspondiente en `.env` o en `compose.yaml` y volvé a levantar.
- **`502 Bad Gateway` en `/api/...` con el stack completo:** el backend todavía está arrancando o nginx no lo vio. Esperá unos segundos, revisá `docker compose logs backend` y, si hace falta, `docker compose restart frontend`.
- **Las pruebas de integración fallan con un error de Docker/Testcontainers:** comprobá que `docker ps` funcione; las `*IT` necesitan Docker aunque no usen tu base de desarrollo.
- **Los checks del Pull Request quedan en rojo:** entrá a **Details** del check, buscá el paso fallido y reproducilo localmente con los comandos de "Pruebas y calidad".

Las migraciones de Flyway corren automáticamente al arrancar el backend. Si una falla, el backend no inicia: no edites migraciones ya integradas, creá una nueva.

## Licencia

MIT — ver [LICENSE](LICENSE).
