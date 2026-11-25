#!/bin/bash

set -euo pipefail

# Ejecutable desde cualquier directorio: el compose y .env viven en la raíz.
cd "$(dirname "$0")/.."

set -a
source .env
set +a

# Para ver los mails el mismo día: se siembra con datos relativos a hoy y se
# acelera el cron de los dos jobs que envían (vencimientos y recordatorios).
# Al terminar (incluso si falla) se restauran los valores originales.
FAST_CRON='*/1 * * * * *'
ORIG_OVERDUE_CRON="${OVERDUE_CRON-}"
ORIG_REMINDERS_CRON="${REMINDERS_CRON-}"
RESTORE_CRON=0

restore_cron() {
  if [ "$RESTORE_CRON" -ne 1 ]; then
    return 0
  fi
  RESTORE_CRON=0
  if [ -n "$ORIG_OVERDUE_CRON" ]; then
    export OVERDUE_CRON="$ORIG_OVERDUE_CRON"
  else
    unset OVERDUE_CRON
  fi
  if [ -n "$ORIG_REMINDERS_CRON" ]; then
    export REMINDERS_CRON="$ORIG_REMINDERS_CRON"
  else
    unset REMINDERS_CRON
  fi
  echo "Restaurando el cron habitual del backend..."
  # Sólo se recrea el backend: la base ya tiene los datos sembrados.
  docker compose up -d --no-deps --force-recreate backend >/dev/null || true
  echo "Cron restaurado. Los mails quedaron en la casilla de enviados del SMTP configurado (.env)."
}
trap restore_cron EXIT

echo "Levantando el stack con OVERDUE_CRON y REMINDERS_CRON cada minuto..."
export OVERDUE_CRON="$FAST_CRON"
export REMINDERS_CRON="$FAST_CRON"
docker compose up -d --build
RESTORE_CRON=1

# El backend aplica Flyway al arrancar. Si el seed corre antes, psql falla con
# "relation groups does not exist"; esperamos a que existan las tablas base y
# la de notificaciones (V39) antes de cargar los datos demo.
echo "Esperando a que Flyway aplique las migraciones en la base demo..."
for i in $(seq 1 90); do
  if docker compose exec -T db psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -tAc \
      "SELECT to_regclass('public.groups') IS NOT NULL
         AND to_regclass('public.notifications') IS NOT NULL" 2>/dev/null | grep -q '^t$'; then
    break
  fi
  if [ "$i" -eq 90 ]; then
    echo "ERROR: la base todavía no tiene el esquema de la aplicación." >&2
    echo "Iniciá el stack con 'docker compose up -d --build' y esperá a que el backend termine de aplicar Flyway." >&2
    exit 1
  fi
  sleep 1
done

docker compose exec -T db psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" \
  -v ON_ERROR_STOP=1 -v demo_password='Demo1234!' < scripts/seed-full-demo.sql

# Los mails los envían los jobs, no el seed. Esperamos a que no queden
# recordatorios con fecha de envío vencida ni deudas pendientes vencidas.
echo "Esperando a que el backend envíe recordatorios y marque vencimientos..."
TODAY_SQL="(now() AT TIME ZONE 'America/Argentina/Buenos_Aires')::date"
for i in $(seq 1 24); do
  PENDING_REMINDERS=$(docker compose exec -T db psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -tAc \
    "SELECT count(*) FROM reminders WHERE status = 'SCHEDULED' AND send_date <= $TODAY_SQL")
  PENDING_OVERDUE=$(docker compose exec -T db psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -tAc \
    "SELECT count(*) FROM debts d JOIN group_expenses ge ON ge.id = d.expense_id
      WHERE d.status = 'PENDING' AND d.group_id IS NOT NULL AND ge.due_date < $TODAY_SQL")
  if [ "$PENDING_REMINDERS" = "0" ] && [ "$PENDING_OVERDUE" = "0" ]; then
    break
  fi
  sleep 10
done

if [ "$PENDING_REMINDERS" = "0" ] && [ "$PENDING_OVERDUE" = "0" ]; then
  echo "Listo: los jobs ya procesaron los recordatorios y los vencimientos."
else
  echo "AVISO: quedan $PENDING_REMINDERS recordatorios y $PENDING_OVERDUE deudas por procesar;" >&2
  echo "revisá los logs del backend (docker compose logs -f backend)." >&2
fi
