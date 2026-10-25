# Sensor-Monitor (mobile-a1)

Android-App (Kotlin, Jetpack Compose, MVVM, minSdk 26, targetSdk 36), die Accelerometer,
Barometer und GNSS ausliest – als Werte-Karten oder als Vico-Liniendiagramm der letzten 30 s.

## Build

```sh
./gradlew :app:assembleDebug      # APK: app/build/outputs/apk/debug/app-debug.apk
./gradlew :app:testDebugUnitTest  # JVM-Tests für Ringpuffer, Glättung, Warnregel, Rate
```

Benötigt ein JDK mit `javac` (z. B. das JBR von Android Studio) und die Android-SDK-Plattform 37.

## Aufbau

| Paket        | Inhalt                                                                  |
|--------------|-------------------------------------------------------------------------|
| `sensor`     | `SensorSource` (Accelerometer, Barometer), `GnssSource` (nur `GPS_PROVIDER`, 1 s) |
| `data`       | `RingBuffer` fester Größe, Sample-Typen                                 |
| `processing` | `MovingAverage` (10 Werte), `RateMeter` (Hz), `ShakeDetector` (> 25 m/s², 5 s Sperre) |
| `notify`     | `ShakeNotifier` – Benachrichtigung „Starke Erschütterung“                |
| `log`        | `ChainLogger` – SENSOR_EVENT → PROCESS → UI, jedes n-te Event           |
| `ui`         | `SensorViewModel`, Compose-Screens, Diagramme                           |

- `MainActivity.onStart`/`onStop` melden alle Listener an bzw. ab; im Hintergrund wird nicht gemessen.
- Die Oberfläche wird von einem Ticker mit höchstens 10 Hz aktualisiert.
- Ohne Benachrichtigungsberechtigung erscheint die Warnung als Banner in der App.

## Logging

```sh
adb logcat -s SENSOR_EVENT PROCESS UI
```

Jede Zeile trägt Sensor und laufende Nummer (z. B. `ACC #250`), sodass sich die Kette verfolgen lässt.
Gedrosselt: Accelerometer jedes 50., Barometer jedes 10., GNSS jedes 5. Event. Koordinaten werden nie geloggt.
