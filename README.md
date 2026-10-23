# Recordatorios (Programación Mobile)

Autor: Yohan Velásquez

App Android en Kotlin con Jetpack Compose y Material 3 para gestionar recordatorios. Funciona sin conexión
(Room y DataStore), cada persona entra con su cuenta (Firebase Authentication) y sus recordatorios se
sincronizan con Cloud Firestore.

## Versión actual: Actividad 4 (4.0.0-rc1)

- **Autenticación** (`data/auth/`, `ui/auth/`): registro, inicio y cierre de sesión con correo y contraseña, sesión
  persistente, aviso cuando la sesión deja de ser válida. La app no guarda contraseñas ni tokens.
- **Cloud Firestore** (`data/remote/FirestoreFuenteRemota.kt`): copia remota en `users/{uid}/reminders/{id}`. El id del
  documento es el mismo `clientId` de Room, así reintentar no duplica.
- **Room por cuenta** (`data/local/`): cada fila tiene `ownerUid`; las consultas filtran por cuenta y al cerrar sesión se
  borran los datos de esa cuenta del dispositivo.
- **Repositorio local-first** y **Sincronizador** (`data/`): igual que en la Actividad 3, ahora con la fuente de Firestore.
- **Reglas de seguridad** (`firebase/firestore.rules`): solo la dueña lee y escribe sus registros y se valida la forma de los datos.
- **Release firmada** con dos variantes (`dev` y `prod`), R8 y versionado desde `gradle.properties`.

Versiones anteriores: tags `actividad-1`, `actividad-2` y `actividad-3` (la 3 usaba una API REST con Retrofit).

## Variantes

| Variante | Backend | applicationId | Para qué |
|---|---|---|---|
| `dev` | Emulator Suite de Firebase (sin datos reales) | `…recordatorios.dev` | Desarrollar y probar |
| `prod` | Proyecto real de Firebase | `…recordatorios` | Entregar a testers |

## Cómo configurar y ejecutar (variante dev, sin cuenta de Firebase)

Requisitos: Android Studio reciente (o el SDK con la plataforma 36), JDK 17, Node.js 18+ y Java 11+ (emuladores de Firebase).

1. Copia `local.properties.example` como `local.properties` y ajusta `sdk.dir`.
2. Levanta los emuladores:
   `cd firebase && npm install && npm run emuladores` (Auth en el puerto 9099 y Firestore en el 8080).
3. Compila e instala: `./gradlew installDevDebug` con un emulador de Android encendido
   (desde un dispositivo físico: `adb reverse tcp:9099 tcp:9099 && adb reverse tcp:8080 tcp:8080` y
   `recordatorios.emuladorHost=localhost` en `local.properties`).
4. Crea una cuenta desde la pantalla de acceso. El menú de tres puntos del listado (solo `dev`) fuerza fallos de red.

## Pruebas

- Unitarias: `./gradlew testDevDebugUnitTest`
- Room real (emulador de Android encendido): `./gradlew connectedDevDebugAndroidTest`
- Reglas de seguridad de Firestore: `cd firebase && npm run test:reglas`
- Acceso cruzado entre cuentas (con los emuladores encendidos): `python3 firebase/tests/acceso_cruzado.py`

## Variante prod con tu propio proyecto de Firebase

1. En la consola de Firebase crea un proyecto y activa **Authentication → Correo y contraseña** y **Firestore Database**.
2. Registra una app Android con el paquete `com.yohanvelasquez.recordatorios`, descarga `google-services.json` y guárdalo
   como `app/src/prod/google-services.json` (no se sube al repositorio).
3. Publica las reglas: `cd firebase && npx firebase deploy --only firestore:rules --project TU_PROYECTO`.

## Firma y entrega

1. Crea una clave: `keytool -genkeypair -keystore recordatorios-release.jks -alias recordatorios -keyalg RSA -keysize 2048 -validity 10000`
   y guárdala **fuera del repositorio y con respaldo**.
2. Copia `keystore.properties.example` como `keystore.properties` y completa la ruta y las contraseñas (archivo ignorado por git).
3. La versión se cambia solo en `gradle.properties` (`recordatorios.versionCode` y `recordatorios.versionName`).
4. `scripts/construir_release.sh prod` (o `dev`) ejecuta las pruebas, genera APK y AAB firmados, verifica la firma y deja
   en `entrega-release/` los archivos, las huellas SHA-256 y un registro con versión, variante, fecha y commit.
5. `scripts/verificar_artefactos.sh` comprueba, antes de entregar, que los archivos siguen coincidiendo con esas huellas.

## Estructura

```
data/auth/         Autenticación (interfaz + Firebase Authentication)
data/local/        Room: entidad con ownerUid, DAO por cuenta, migración 1→2
data/preferencias/ DataStore
data/remote/       Fuente remota (interfaz, Firestore, errores, simulación solo en dev)
data/sync/         Sincronizador
data/              Mapeos y repositorio local-first
model/             Modelo de dominio y validación
ui/                Acceso, listado, formulario, detalle, preferencias/cuenta, navegación
firebase/          Reglas de Firestore, configuración de emuladores y pruebas de seguridad
scripts/           Generación y verificación de artefactos release
```
