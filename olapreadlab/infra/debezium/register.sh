#!/usr/bin/env bash
set -euo pipefail

curl --fail --silent --show-error \
  --request PUT \
  --header 'Content-Type: application/json' \
  --data @/config/postgres-connector.json \
  http://debezium-connect:8083/connectors/olap-postgres/config

echo
echo "Debezium connector olap-postgres registered."
