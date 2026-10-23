# Recordatorios — Actividad 1 (Programación Mobile)

Autor: Yohan Velásquez

App Android en Kotlin + Jetpack Compose (Material 3) que modela recordatorios con
data classes inmutables y sealed classes, valida los datos de entrada y muestra
una lista de recordatorios simulados con filtro por estado.

- `model/Modelos.kt`: `Recordatorio`, `Categoria`, `Prioridad`, `EstadoRecordatorio`, `EstadoUi`, validación.
- `data/DatosSimulados.kt`: List de recordatorios, Set de categorías, Map categoría → color, filtros.
- `ui/PantallaRecordatorios.kt`: pantalla inicial en Compose.

Ejecutar: abrir en Android Studio y pulsar Run, o `./gradlew installDebug` con un emulador encendido.
Tests: `./gradlew testDebugUnitTest`.
