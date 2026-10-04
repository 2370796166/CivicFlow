#!/bin/sh
set -eu

base_url="http://nacos:8848/nacos"
admin_response="/tmp/nacos-admin-response.json"

case "$NACOS_NAMESPACE" in
  ''|*[!A-Za-z0-9_-]*)
    echo "NACOS_NAMESPACE may contain only letters, digits, underscores, and hyphens" >&2
    exit 1
    ;;
esac

# Since Nacos 2.4 the first administrator password must be initialized explicitly.
# A repeated call can return 400/403 when the administrator already exists; login below
# is the idempotency check and fails if the persisted password differs from .env.
admin_status="$(
  curl -sS -o "$admin_response" -w '%{http_code}' \
    -X POST "$base_url/v1/auth/users/admin" \
    --data-urlencode "password=$NACOS_PASSWORD" || true
)"

case "$admin_status" in
  200|400|403|409) ;;
  *)
    echo "Unexpected response while initializing the Nacos administrator (HTTP $admin_status)" >&2
    exit 1
    ;;
esac

login_response="$(
  curl -fsS -X POST "$base_url/v1/auth/login" \
    --data-urlencode "username=$NACOS_USERNAME" \
    --data-urlencode "password=$NACOS_PASSWORD"
)"
access_token="$(printf '%s' "$login_response" | sed -n 's/.*"accessToken"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/p')"

if [ -z "$access_token" ]; then
  echo "Nacos login did not return an access token. Check the password retained in the nacos volume." >&2
  exit 1
fi

namespace_list="$(
  curl -fsS -G "$base_url/v2/console/namespace/list" \
    --data-urlencode "accessToken=$access_token"
)"

if printf '%s' "$namespace_list" | grep -Eq "\"namespace\"[[:space:]]*:[[:space:]]*\"${NACOS_NAMESPACE}\""; then
  echo "Nacos namespace '$NACOS_NAMESPACE' already exists."
else
  curl -fsS -X POST "$base_url/v2/console/namespace" \
    --data-urlencode "accessToken=$access_token" \
    --data-urlencode "namespaceId=$NACOS_NAMESPACE" \
    --data-urlencode "namespaceName=$NACOS_NAMESPACE" \
    --data-urlencode "namespaceDesc=CivicFlow local development environment" >/dev/null
  echo "Created Nacos namespace '$NACOS_NAMESPACE'."
fi

echo "Nacos local authentication and namespace initialization completed."
