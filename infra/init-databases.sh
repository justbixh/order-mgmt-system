#!/bin/bash
# Runs once, when the Postgres data directory is first created.
# Database per service: each service gets its own database AND its own login that cannot see the others.
set -e
for svc in orders inventory payments notifications; do
  psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" <<SQL
    CREATE USER $svc WITH PASSWORD '$svc';
    CREATE DATABASE $svc OWNER $svc;
SQL
done
