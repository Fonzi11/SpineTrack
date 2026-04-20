# SpineTrack

SpineTrack es una solucion IoT para monitoreo postural en tiempo real con app Android + Raspberry Pi.

## Como funciona (resumen)

1. La app Android autentica usuario en Firebase (UID).
2. En `Dispositivo`, la app empareja la Raspberry y envia comandos (`iniciar`, `calibrar`, `detener`) a Realtime Database.
3. La Raspberry ejecuta lectura de sensores, calibracion y deteccion postural.
4. La Raspberry publica:
   - Tiempo real por MQTT (HiveMQ).
   - Sesiones y eventos en Firebase Realtime Database.
5. La app consume:
   - En vivo desde MQTT.
   - Estadisticas, sesiones y perfil desde Firebase por UID.

## Estructura del proyecto

### `app/`
- App Android (Kotlin).
- `src/main/java/com/example/spinetrack/`: UI, ViewModels, repositorios y modelos.
- `src/main/res/`: layouts, strings, drawables, navegacion (`nav_graph.xml`).

### `scripts/`
- Scripts de Raspberry/Python.
- Archivo principal integrado: `scripts/spinetrack_full_integrated.py`.
- Maneja IMU, calibracion, control remoto, MQTT y Firebase admin.

### `gradle/`, `build.gradle.kts`, `settings.gradle.kts`
- Configuracion de compilacion Android/Gradle.

### `.github/`
- Automatizaciones y configuraciones de GitHub (si aplica en el repo).

## Flujo de calibracion

- Desde la app, en `Dispositivo`, al enviar `iniciar` o `calibrar`:
  - La Raspberry calibra giroscopio.
  - Luego calibra postura base.
  - Reporta estado en Firebase (`/dispositivos/{uid}/estado`).
- La app refleja visualmente el proceso y habilita sesion cuando termina.

## Seguridad

- No subir credenciales reales (`serviceAccount.json`, claves MQTT, secretos locales).
- Usar archivos plantilla y variables de entorno para configuracion sensible.
