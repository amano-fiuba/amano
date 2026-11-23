#!/bin/bash

docker compose exec -T db psql -U "$POSTGRES_USER" -d "$POSTGRES_DB"   -v ON_ERROR_STOP=1 -v demo_password='Demo1234!'   < scripts/seed-full-demo.sql
