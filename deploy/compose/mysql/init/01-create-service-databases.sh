#!/bin/bash
set -Eeuo pipefail

readonly MYSQL_CLIENT=(
  mysql
  --protocol=socket
  -uroot
  "-p${MYSQL_ROOT_PASSWORD}"
  --default-character-set=utf8mb4
)

validate_account_name() {
  local account_name="$1"
  if [[ ! "$account_name" =~ ^[A-Za-z0-9_]+$ ]]; then
    echo "Invalid MySQL account name: only letters, digits, and underscores are allowed" >&2
    exit 1
  fi
}

sql_string() {
  printf '%s' "$1" | sed "s/'/''/g"
}

create_service_database() {
  local schema_name="$1"
  local app_username="$2"
  local app_password="$3"
  local migration_username="$4"
  local migration_password="$5"

  validate_account_name "$app_username"
  validate_account_name "$migration_username"

  local escaped_app_username escaped_app_password escaped_migration_username escaped_migration_password
  escaped_app_username="$(sql_string "$app_username")"
  escaped_app_password="$(sql_string "$app_password")"
  escaped_migration_username="$(sql_string "$migration_username")"
  escaped_migration_password="$(sql_string "$migration_password")"

  "${MYSQL_CLIENT[@]}" <<-SQL
	CREATE DATABASE IF NOT EXISTS \`${schema_name}\`
	  CHARACTER SET utf8mb4
	  COLLATE utf8mb4_0900_ai_ci;

	CREATE USER IF NOT EXISTS '${escaped_app_username}'@'%' IDENTIFIED BY '${escaped_app_password}';
	ALTER USER '${escaped_app_username}'@'%' IDENTIFIED BY '${escaped_app_password}';
	GRANT SELECT, INSERT, UPDATE, DELETE
	  ON \`${schema_name}\`.* TO '${escaped_app_username}'@'%';

	CREATE USER IF NOT EXISTS '${escaped_migration_username}'@'%' IDENTIFIED BY '${escaped_migration_password}';
	ALTER USER '${escaped_migration_username}'@'%' IDENTIFIED BY '${escaped_migration_password}';
	GRANT SELECT, INSERT, UPDATE, DELETE, CREATE, ALTER, DROP, INDEX, REFERENCES
	  ON \`${schema_name}\`.* TO '${escaped_migration_username}'@'%';
SQL
}

create_service_database \
  civicflow_auth \
  "$CIVICFLOW_AUTH_DB_APP_USERNAME" \
  "$CIVICFLOW_AUTH_DB_APP_PASSWORD" \
  "$CIVICFLOW_AUTH_DB_MIGRATION_USERNAME" \
  "$CIVICFLOW_AUTH_DB_MIGRATION_PASSWORD"

create_service_database \
  civicflow_resource \
  "$CIVICFLOW_RESOURCE_DB_APP_USERNAME" \
  "$CIVICFLOW_RESOURCE_DB_APP_PASSWORD" \
  "$CIVICFLOW_RESOURCE_DB_MIGRATION_USERNAME" \
  "$CIVICFLOW_RESOURCE_DB_MIGRATION_PASSWORD"

create_service_database \
  civicflow_appointment \
  "$CIVICFLOW_APPOINTMENT_DB_APP_USERNAME" \
  "$CIVICFLOW_APPOINTMENT_DB_APP_PASSWORD" \
  "$CIVICFLOW_APPOINTMENT_DB_MIGRATION_USERNAME" \
  "$CIVICFLOW_APPOINTMENT_DB_MIGRATION_PASSWORD"

create_service_database \
  civicflow_queue \
  "$CIVICFLOW_QUEUE_DB_APP_USERNAME" \
  "$CIVICFLOW_QUEUE_DB_APP_PASSWORD" \
  "$CIVICFLOW_QUEUE_DB_MIGRATION_USERNAME" \
  "$CIVICFLOW_QUEUE_DB_MIGRATION_PASSWORD"

echo "CivicFlow service schemas and least-privilege local accounts are ready."
