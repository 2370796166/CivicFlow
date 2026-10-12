#!/bin/bash
set -Eeuo pipefail

for name in CIVICFLOW_NOTIFICATION_DB_APP_USERNAME CIVICFLOW_NOTIFICATION_DB_MIGRATION_USERNAME; do
  if [[ ! "${!name}" =~ ^[A-Za-z0-9_]+$ ]]; then
    echo "Invalid notification database account name" >&2
    exit 1
  fi
done

sql_string() { printf '%s' "$1" | sed "s/'/''/g"; }
app_user="$(sql_string "$CIVICFLOW_NOTIFICATION_DB_APP_USERNAME")"
app_password="$(sql_string "$CIVICFLOW_NOTIFICATION_DB_APP_PASSWORD")"
migration_user="$(sql_string "$CIVICFLOW_NOTIFICATION_DB_MIGRATION_USERNAME")"
migration_password="$(sql_string "$CIVICFLOW_NOTIFICATION_DB_MIGRATION_PASSWORD")"

mysql --host=mysql --port=3306 --user=root --default-character-set=utf8mb4 <<SQL
CREATE DATABASE IF NOT EXISTS civicflow_notification CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE USER IF NOT EXISTS '${app_user}'@'%' IDENTIFIED BY '${app_password}';
ALTER USER '${app_user}'@'%' IDENTIFIED BY '${app_password}';
GRANT SELECT, INSERT, UPDATE, DELETE ON civicflow_notification.* TO '${app_user}'@'%';
CREATE USER IF NOT EXISTS '${migration_user}'@'%' IDENTIFIED BY '${migration_password}';
ALTER USER '${migration_user}'@'%' IDENTIFIED BY '${migration_password}';
GRANT SELECT, INSERT, UPDATE, DELETE, CREATE, ALTER, DROP, INDEX, REFERENCES ON civicflow_notification.* TO '${migration_user}'@'%';
SQL
echo "Notification database account initialization completed"
