# Recordatorios (Programación Mobile)

Autor: Yohan Velásquez

App Android en Kotlin con Jetpack Compose y Material 3 para gestionar recordatorios. Funciona sin conexión:
guarda todo en el dispositivo (Room y DataStore) y sincroniza con un servicio remoto cuando hay red.

## Versión actual: Actividad 3

- **Preferencias (DataStore)**: tema, orden del listado y mostrar/ocultar completados (`data/preferencias/`).
- **Recordatorios (Room)**: entidad, DAO con `Flow`, base de datos de una sola instancia (`data/local/`).
- **Repositorio local-first** (`data/RecordatorioRepositoryLocalFirst.kt`): la interfaz solo lee de Room; cada
  cambio se guarda primero en Room como *pendiente* y después se envía al servicio.
- **Servicio remoto (Retrofit + OkHttp)** (`data/remote/`): interfaz con funciones `suspend`, URL base
  configurable e interpretación de códigos HTTP, timeout y falta de conexión (`ErrorRed`).
- **Sincronización** (`data/sync/Sincronizador.kt`): envía lo pendiente y trae cambios remotos sin duplicar.
- **Mapeos explícitos** DTO ⇄ Entity ⇄ modelo de dominio (`data/Mapeos.kt`).

Versiones anteriores: tags `actividad-1` y `actividad-2`.

## Cómo configurar, compilar y ejecutar

Requisitos: Android Studio reciente (o el SDK de Android con la plataforma 36) y JDK 17.

1. Copia `local.properties.example` como `local.properties` (no se sube al repositorio) y ajusta:
   - `sdk.dir`: ruta de tu Android SDK (Android Studio lo crea solo al abrir el proyecto).
   - `recordatorios.apiBaseUrl`: URL base del servicio, terminada en `/`. **No pongas credenciales ni tokens.**
   - `recordatorios.usarBackendSimulado`: `true` (por defecto) simula el servicio dentro de la app, sin
     servidor real; `false` usa la URL anterior con una conexión real.
2. Abre la carpeta en Android Studio y espera la sincronización de Gradle, o usa la terminal:
   `./gradlew assembleDebug`.
3. Inicia un emulador o conecta un dispositivo y pulsa **Run**, o `./gradlew installDebug`.
4. Pruebas: `./gradlew testDebugUnitTest` (unitarias) y `./gradlew connectedDebugAndroidTest`
   (Room real, requiere emulador o dispositivo).

## Servicio simulado

Con `usarBackendSimulado=true`, un interceptor de OkHttp responde como un servicio REST sencillo
(`GET/POST /recordatorios`, `PUT/DELETE /recordatorios/{id}`) y guarda sus datos en un archivo del
dispositivo. Las llamadas pasan por Retrofit, OkHttp y Gson igual que con un servidor real.

El menú de tres puntos del listado permite forzar *sin conexión*, *timeout*, *error 500* y
*solicitud inválida (400)*. También funciona el modo avión real del dispositivo.

## Estructura

```
data/local/        Entity, DAO y base de datos de Room
data/preferencias/ DataStore: modelo y repositorio de preferencias
data/remote/       Retrofit, DTO, errores de red, servicio simulado, monitor de conectividad
data/sync/         Sincronizador (envío de pendientes + traída de cambios)
data/              Mapeos y repositorio local-first
model/             Modelo de dominio y validación del formulario
ui/                Pantallas (lista, formulario, detalle, preferencias), componentes y navegación
```
