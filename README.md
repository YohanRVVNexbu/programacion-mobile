# Recordatorios (Programación Mobile)

Autor: Yohan Velásquez

App Android en Kotlin con Jetpack Compose y Material 3 para gestionar recordatorios.

## Actividad 2

Pantallas de listado, creación y detalle conectadas con Navigation Compose. Cada pantalla tiene su ViewModel,
un UiState tipado y expone el estado con StateFlow (se recolecta con `collectAsStateWithLifecycle`). Los datos
vienen de un repositorio en memoria detrás de la interfaz `RecordatorioRepository`.

- `model/`: clases del dominio y validación del formulario.
- `data/`: interfaz del repositorio, implementación en memoria y datos de prueba.
- `ui/componentes/`: tarjeta, estados de carga/vacío/error y campos del formulario.
- `ui/lista`, `ui/formulario`, `ui/detalle`: pantalla + ViewModel de cada destino.
- `ui/navegacion/`: rutas y NavHost.

El menú de tres puntos del listado permite simular lista vacía, error al cargar y error al guardar.

Ejecutar: abrir en Android Studio y pulsar Run, o `./gradlew installDebug` con un emulador encendido.
Tests: `./gradlew testDebugUnitTest`.

La Actividad 1 está en el tag `actividad-1`.
