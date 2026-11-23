#!/usr/bin/env bash
set -euo pipefail

runtime=${CLOUD_RUNTIME_DIR:?Set the absolute secrets directory}
[[ $runtime == /* ]]
umask 077
install -d -m 700 "$runtime"

for name in mysql-root-password center-db-password course-db-password audit-service-key bootstrap-password; do
  if [[ ! -e "$runtime/$name" ]]; then
    if [[ $name == bootstrap-password ]]; then
      value="Cloud!A1$(openssl rand -hex 24)"
    else
      value=$(openssl rand -hex 32)
    fi
    (set -o noclobber; printf '%s' "$value" > "$runtime/$name")
  fi
  [[ -s "$runtime/$name" ]]
done

work=$(mktemp -d "$runtime/.keygen.XXXXXXXX")
trap 'rm -rf -- "$work"' EXIT
if [[ ! -e "$runtime/jwt-private.pem" && ! -e "$runtime/jwt-public.pem" ]]; then
  openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:3072 -out "$work/jwt-private.pem" 2>/dev/null
  openssl pkey -in "$work/jwt-private.pem" -pubout -out "$work/jwt-public.pem"
  mv -- "$work/"jwt-{private,public}.pem "$runtime/"
fi
[[ "$(openssl pkey -in "$runtime/jwt-private.pem" -pubout)" == "$(openssl pkey -pubin -in "$runtime/jwt-public.pem" -pubout)" ]]

if [[ ! -e "$runtime/tls.crt" && ! -e "$runtime/tls.key" && ! -e "$runtime/ca.crt" ]]; then
  cat > "$work/ca.cnf" <<'EXT'
[req]
distinguished_name = dn
x509_extensions = ca_extensions
[dn]
[ca_extensions]
basicConstraints = critical,CA:TRUE,pathlen:0
keyUsage = critical,keyCertSign,cRLSign
subjectKeyIdentifier = hash
authorityKeyIdentifier = keyid:always
EXT
  openssl req -x509 -newkey rsa:3072 -nodes -sha256 -days 90 \
    -subj '/CN=OOAD RBAC Cloud Integration CA' \
    -config "$work/ca.cnf" \
    -keyout "$work/ca.key" -out "$work/ca.crt" 2>/dev/null
  openssl req -new -newkey rsa:3072 -nodes -subj '/CN=49.4.114.16' \
    -keyout "$work/tls.key" -out "$work/tls.csr" 2>/dev/null
  cat > "$work/server.ext" <<'EXT'
basicConstraints=critical,CA:FALSE
keyUsage=critical,digitalSignature,keyEncipherment
extendedKeyUsage=serverAuth
subjectAltName=IP:49.4.114.16,IP:127.0.0.1,DNS:localhost
subjectKeyIdentifier=hash
authorityKeyIdentifier=keyid:always
EXT
  openssl x509 -req -sha256 -days 30 -in "$work/tls.csr" \
    -CA "$work/ca.crt" -CAkey "$work/ca.key" -CAcreateserial \
    -extfile "$work/server.ext" -out "$work/tls.crt"
  mv -- "$work/"{ca.crt,tls.crt,tls.key} "$runtime/"
fi
openssl verify -x509_strict -purpose sslserver -CAfile "$runtime/ca.crt" -verify_ip 49.4.114.16 "$runtime/tls.crt"
[[ "$(openssl x509 -in "$runtime/tls.crt" -pubkey -noout)" == "$(openssl pkey -in "$runtime/tls.key" -pubout)" ]]
openssl x509 -in "$runtime/ca.crt" -noout -fingerprint -sha256
