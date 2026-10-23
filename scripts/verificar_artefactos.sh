#!/usr/bin/env bash
# Comprueba que los archivos de entrega-release/ son exactamente los que se generaron y validaron:
# huellas SHA-256 y firma del APK. Úsalo justo antes de entregar.
set -euo pipefail
cd "$(dirname "$0")/../entrega-release"
SDK="${ANDROID_HOME:-$(sed -n 's/^sdk.dir=//p' ../local.properties)}"
BT="$(ls -d "$SDK"/build-tools/* | sort -V | tail -1)"
shopt -s nullglob
for sumas in *.SHA256SUMS.txt; do
  shasum -a 256 -c "$sumas"
done
for apk in *.apk; do
  "$BT/apksigner" verify --print-certs "$apk" | grep -E "Signer #1 certificate SHA-256|^Verifies" || { echo "Firma inválida: $apk"; exit 1; }
done
echo "Todo coincide."
