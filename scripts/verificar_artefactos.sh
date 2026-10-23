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
  salida="$("$BT/apksigner" verify --verbose --print-certs "$apk" 2>&1)" || { echo "Firma inválida: $apk"; exit 1; }
  grep -q '^Verifies' <<< "$salida" || { echo "Firma inválida: $apk"; exit 1; }
  echo "$apk: firma válida, $(grep -m1 'certificate SHA-256 digest' <<< "$salida" | sed 's/^ *//')"
done
echo "Todo coincide."
