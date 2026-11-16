# Cómo correr y probar Whistle (spec 030)

Pasos para levantar la app con la feature 030 en cualquier PC, cargar datos de prueba y verificar el silbido. Para ver los correos sin mandar nada real se usa **Mailpit**, un SMTP de prueba con interfaz web.

## Requisitos

- Docker con Docker Compose.
- El repo en la rama con los cambios de la 030.

## 1. Configurar el `.env` (una sola vez)

```bash
cp .env.example .env
sed -i \
  -e "s/^POSTGRES_PASSWORD=.*/POSTGRES_PASSWORD=amano_local/" \
  -e "s/^JWT_SECRET=.*/JWT_SECRET=$(openssl rand -hex 32)/" \
  -e "s/^MAIL_HOST=.*/MAIL_HOST=mailpit/" \
  -e "s/^MAIL_PORT=.*/MAIL_PORT=1025/" \
  -e "s/^MAIL_USERNAME=.*/MAIL_USERNAME=/" \
  -e "s/^MAIL_PASSWORD=.*/MAIL_PASSWORD=/" \
  -e "s|^APP_BASE_URL=.*|APP_BASE_URL=http://localhost:8081|" .env
```

Si ya tenés un `.env`, no hagas el `cp`: cambiá solo `MAIL_HOST=mailpit`, `MAIL_PORT=1025` y dejá `MAIL_USERNAME` y `MAIL_PASSWORD` vacíos.

## 2. Agregar Mailpit (archivo fuera del repo)

```bash
cat > ~/amano-mailpit.yaml <<'EOF'
services:
  mailpit:
    image: axllent/mailpit
    environment:
      MP_SMTP_AUTH_ACCEPT_ANY: "1"
      MP_SMTP_AUTH_ALLOW_INSECURE: "1"
    ports:
      - "127.0.0.1:8025:8025"
  backend:
    depends_on:
      - mailpit
EOF
```

## 3. Levantar todo

```bash
docker compose -f compose.yaml -f ~/amano-mailpit.yaml up -d --build
docker compose logs -f backend | grep -E "V37|Started BackendApplication"
```

Cuando aparezcan la migración `V37 - create debt whistles` y `Started BackendApplication`, cortá el log con Ctrl+C.

- **App:** http://localhost:8081
- **Correos (Mailpit):** http://localhost:8025

## 4. Cargar datos de prueba

```bash
./scripts/seed-demo.sh
./scripts/seed-group-recurring-demo.sh
```

Esto crea el grupo **"Depto Demo 029"**: Lucía es la acreedora y Martín le debe varias deudas pendientes. La contraseña de todas las cuentas es `Demo1234!`.

## 5. Probar en el navegador

1. **Silbar.** Entrá como `lucia@amano.demo` → "Depto Demo 029" → **Deudas** → "Mis deudas pendientes". En una fila "Martín Gómez te debe", pulsá **Silbar** y confirmá.
   - Tiene que aparecer el aviso "Le avisamos a Martín Gómez".
   - En Mailpit tiene que llegar un correo para `martin@amano.demo` con el asunto "Lucía Fernández te recuerda una deuda - Depto Demo 029".
2. **Límite de 24 h.** Silbá otra deuda de Martín. Tiene que aparecer "Ya le silbaste a Martín Gómez hace poco…" y no tiene que llegar un correo nuevo.
3. **Historial.** En el historial del grupo tiene que figurar "Lucía Fernández le silbó a Martín Gómez por $ …".
4. **Deudor.** Entrá como `martin@amano.demo`: en sus deudas no tiene que aparecer "Silbar".
5. **Perfil simulado.** Como Lucía, agregá un perfil simulado al grupo y registrá un gasto pagado por ella y repartido con ese perfil. En esa deuda, el botón "Silbar" tiene que aparecer deshabilitado con el texto "Los perfiles simulados no reciben silbidos".
6. **Detalle del gasto.** Abrí un gasto con deudas de Martín: en la lista de deudas también tiene que aparecer "Silbar".

## 6. Verificación por API (opcional)

```bash
# Login como Lucía (guarda la cookie de sesión)
curl -s -c /tmp/lucia.txt -H 'Content-Type: application/json' \
  -d '{"email":"lucia@amano.demo","password":"Demo1234!"}' \
  http://localhost:8081/api/auth/login -o /dev/null -w "login: %{http_code}\n"

# Grupo y una deuda pendiente de Martín
source <(grep -E '^POSTGRES_(USER|DB)=' .env)
read GID DID < <(docker compose exec -T db psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -tA -F' ' -c \
  "SELECT d.group_id, d.id FROM debts d JOIN groups g ON g.id=d.group_id
   WHERE g.name='Depto Demo 029' AND d.status='PENDING' ORDER BY d.id DESC LIMIT 1")
echo "grupo=$GID deuda=$DID"

# Primer silbido: 201. El segundo: 429 con Retry-After.
curl -s -i -b /tmp/lucia.txt -X POST http://localhost:8081/api/groups/$GID/debts/$DID/whistle | head -20
curl -s -i -b /tmp/lucia.txt -X POST http://localhost:8081/api/groups/$GID/debts/$DID/whistle | grep -E "HTTP/|Retry-After|detail"

# Intentos registrados en la base
docker compose exec -T db psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -c \
  "SELECT id, debt_id, status, created_at, sent_at FROM debt_whistles ORDER BY id;"
```

Si ya silbaste desde el navegador, el primer `curl` también da 429, porque el límite es por par acreedor–deudor. Para empezar de cero, vaciá la tabla:

```bash
docker compose exec -T db psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -c "TRUNCATE debt_whistles;"
```

## 7. Tests automatizados

```bash
# Backend (necesita un JDK 25 y Docker para Testcontainers)
cd backend && ./mvnw -B verify

# Si la PC solo tiene JRE, con un contenedor (desde backend/):
docker run --rm --network host -u $(id -u):$(id -g) --group-add $(stat -c %g /var/run/docker.sock) \
  -e HOME=/tmp/h -v /var/run/docker.sock:/var/run/docker.sock -v "$PWD":/app -v $HOME/.m2:/tmp/h/.m2 \
  -w /app eclipse-temurin:25-jdk ./mvnw -B -Duser.home=/tmp/h verify

# Frontend
cd frontend && npm ci && npm run lint && npm run test:coverage && npm run build
```

En zsh no uses `GID` como nombre de variable: es una variable especial del shell.

## 8. Apagar

```bash
docker compose -f compose.yaml -f ~/amano-mailpit.yaml down
```

## Pendiente

Anotar el resultado de los pasos de la sección 5 en `quickstart.md` ("Resultado"). Con eso se completan T034 (escenarios manuales) y T032 (accesibilidad) de `tasks.md`.
