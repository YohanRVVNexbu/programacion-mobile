#!/usr/bin/env bash
# Genera el APK y el AAB release firmados de una variante y deja en entrega-release/ los archivos finales,
# sus huellas SHA-256 y un registro con la versión, la variante, la fecha y el commit.
#
# Uso:  scripts/construir_release.sh prod     (versión para testers; necesita app/src/prod/google-services.json)
#       scripts/construir_release.sh dev      (contra el emulador de Firebase; solo para pruebas internas)
set -euo pipefail
cd "$(dirname "$0")/.."

AMBIENTE="${1:-prod}"
case "$AMBIENTE" in dev|prod) ;; *) echo "Variante inválida: $AMBIENTE (usa dev o prod)"; exit 1 ;; esac
Amb="$(tr '[:lower:]' '[:upper:]' <<< "${AMBIENTE:0:1}")${AMBIENTE:1}"

[ -f keystore.properties ] || { echo "Falta keystore.properties (copia keystore.properties.example y completa los datos de la clave)."; exit 1; }
if [ "$AMBIENTE" = prod ] && [ ! -f app/src/prod/google-services.json ]; then
  echo "Falta app/src/prod/google-services.json (se descarga de la consola de Firebase; ver app/google-services.json.example)."; exit 1
fi

SDK="${ANDROID_HOME:-$(sed -n 's/^sdk.dir=//p' local.properties)}"
BT="$(ls -d "$SDK"/build-tools/* | sort -V | tail -1)"

echo "== Pruebas unitarias (mismo código para ambas variantes)"
./gradlew testDevDebugUnitTest --console=plain -q

echo "== Compilando ${AMBIENTE}Release (APK y AAB)"
./gradlew "assemble${Amb}Release" "bundle${Amb}Release" --console=plain -q

APK_ORIGEN="app/build/outputs/apk/${AMBIENTE}/release/app-${AMBIENTE}-release.apk"
AAB_ORIGEN="app/build/outputs/bundle/${AMBIENTE}Release/app-${AMBIENTE}-release.aab"
[ -f "$APK_ORIGEN" ] && [ -f "$AAB_ORIGEN" ] || { echo "No se generaron los artefactos."; exit 1; }

# La versión se lee del propio APK compilado, no de los archivos de configuración.
INFO="$("$BT/aapt2" dump badging "$APK_ORIGEN" | sed -n '1p')"
PAQUETE="$(sed -n "s/^package: name='\([^']*\)'.*/\1/p" <<< "$INFO")"
CODIGO="$(sed -n "s/.*versionCode='\([^']*\)'.*/\1/p" <<< "$INFO")"
NOMBRE="$(sed -n "s/.*versionName='\([^']*\)'.*/\1/p" <<< "$INFO")"

DESTINO=entrega-release
mkdir -p "$DESTINO"
# En dev el versionName ya termina en "-dev", así que el nombre del archivo identifica la variante en ambos casos.
BASE="Recordatorios-${NOMBRE}-release"
cp "$APK_ORIGEN" "$DESTINO/$BASE.apk"
cp "$AAB_ORIGEN" "$DESTINO/$BASE.aab"

echo "== Verificando firma"
"$BT/apksigner" verify --verbose --print-certs "$DESTINO/$BASE.apk" > "$DESTINO/$BASE.apk.firma.txt"
grep -q "Verifies" "$DESTINO/$BASE.apk.firma.txt" || { echo "El APK no tiene una firma válida."; exit 1; }
jarsigner -verify "$DESTINO/$BASE.aab" > "$DESTINO/$BASE.aab.firma.txt" 2>&1 || true
grep -q "jar verified" "$DESTINO/$BASE.aab.firma.txt" || { echo "El AAB no tiene una firma válida."; exit 1; }

( cd "$DESTINO" && shasum -a 256 "$BASE.apk" "$BASE.aab" > "$BASE.SHA256SUMS.txt" )

COMMIT="$(git rev-parse --short HEAD)"
SUCIO="$(git status --porcelain --untracked-files=no | wc -l | tr -d ' ')"
{
  echo "Aplicación:          $PAQUETE"
  echo "Versión:             $NOMBRE (versionCode $CODIGO)"
  echo "Variante:            ${AMBIENTE}Release"
  echo "Fecha de generación: $(date '+%Y-%m-%d %H:%M:%S %z')"
  echo "Commit:              $COMMIT$([ "$SUCIO" != 0 ] && echo " (con $SUCIO archivo(s) modificado(s) sin commit)")"
  echo "Certificado:         $(grep -m1 'certificate SHA-256 digest' "$DESTINO/$BASE.apk.firma.txt" | sed 's/.*: //')"
  echo
  echo "SHA-256:"; sed 's/^/  /' "$DESTINO/$BASE.SHA256SUMS.txt"
} | tee "$DESTINO/$BASE.registro.txt"
[ "$SUCIO" = 0 ] || echo "AVISO: hay cambios sin commit; para entregar, haz commit y vuelve a generar."
