#!/usr/bin/env sh
set -eu

if [ -z "${VAULT_ADDR:-}" ]; then
  echo "VAULT_ADDR is not set; skipping."
  exit 0
fi

if [ -z "${VAULT_TOKEN:-}" ]; then
  echo "VAULT_TOKEN is not set; skipping."
  exit 0
fi

VAULT_MOUNT_PATH="${VAULT_MOUNT_PATH:-secret}"
VAULT_SECRET_PATH="${VAULT_SECRET_PATH:-secret/jwt/keys}"
JWT_KEY_ID="${JWT_KEY_ID:-kid-1}"
VAULT_INIT_GENERATE_KEYS="${VAULT_INIT_GENERATE_KEYS:-true}"
VAULT_INIT_INSTALL_OPENSSL="${VAULT_INIT_INSTALL_OPENSSL:-false}"
PRIVATE_KEY_FILE="${VAULT_INIT_PRIVATE_KEY_FILE:-/vault-keys/private-key.pem}"
PUBLIC_KEY_FILE="${VAULT_INIT_PUBLIC_KEY_FILE:-/vault-keys/public-key.pem}"

echo "Waiting for Vault to be reachable..."
for i in $(seq 1 30); do
  if vault status >/dev/null 2>&1; then
    break
  fi
  sleep 1
done

if ! vault status >/dev/null 2>&1; then
  echo "Vault not reachable; skipping."
  exit 0
fi

echo "Ensuring KV v2 mount at ${VAULT_MOUNT_PATH}/"
vault secrets enable -path="${VAULT_MOUNT_PATH}" -version=2 kv >/dev/null 2>&1 || true

if vault kv get "${VAULT_SECRET_PATH}/${JWT_KEY_ID}" >/dev/null 2>&1; then
  echo "Keys already exist at ${VAULT_SECRET_PATH}/${JWT_KEY_ID}; skipping seed."
  exit 0
fi

if [ ! -f "$PRIVATE_KEY_FILE" ] || [ ! -f "$PUBLIC_KEY_FILE" ]; then
  if [ "$VAULT_INIT_GENERATE_KEYS" = "true" ]; then
    echo "Key files not found; generating new RSA keypair in container."
    if ! command -v openssl >/dev/null 2>&1; then
      if [ "$VAULT_INIT_INSTALL_OPENSSL" = "true" ]; then
        if command -v apk >/dev/null 2>&1; then
          apk add --no-cache openssl >/dev/null 2>&1 || true
        elif command -v apt-get >/dev/null 2>&1; then
          apt-get update -y >/dev/null 2>&1 && apt-get install -y openssl >/dev/null 2>&1 || true
        fi
      fi
    fi
    if ! command -v openssl >/dev/null 2>&1; then
      echo "openssl not available; cannot generate keys."
      exit 1
    fi
    umask 077
    GEN_DIR="/tmp/vault-keys"
    mkdir -p "$GEN_DIR"
    PRIVATE_KEY_FILE="$GEN_DIR/private-key.pem"
    PUBLIC_KEY_FILE="$GEN_DIR/public-key.pem"
    openssl genrsa -out "$PRIVATE_KEY_FILE" 4096 >/dev/null 2>&1
    openssl rsa -in "$PRIVATE_KEY_FILE" -pubout -out "$PUBLIC_KEY_FILE" >/dev/null 2>&1
  else
    echo "Key files not found; skipping seed."
    echo "  Private: $PRIVATE_KEY_FILE"
    echo "  Public:  $PUBLIC_KEY_FILE"
    exit 0
  fi
fi

echo "Seeding JWT keys to ${VAULT_SECRET_PATH}/${JWT_KEY_ID}"
vault kv put "${VAULT_SECRET_PATH}/${JWT_KEY_ID}" \
  private=@"$PRIVATE_KEY_FILE" \
  public=@"$PUBLIC_KEY_FILE" \
  >/dev/null

echo "Vault init complete."
