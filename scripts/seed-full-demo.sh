#!/bin/bash

set -euo pipefail

# Ejecutable desde cualquier directorio: el compose y .env viven en la raíz.
cd "$(dirname "$0")/.."

set -a
source .env
set +a

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
