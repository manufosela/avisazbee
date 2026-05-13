# avisazbee

App Android para recibir avisos generados por botones Zigbee Sonoff
SNZB-01P a través de Home Assistant y Firebase. Pulsar un botón dispara
una alerta que llega a las personas asociadas al canal del botón.

> Estado actual: **Fase 0 — bootstrap Kotlin/Compose**. Estructura del
> proyecto Android, dominio (Kotlin puro) y documento de arquitectura
> listos. Falta cablear Firebase, Cloud Function y motor de escalada.

## Flujo

```
Botón Zigbee → Home Assistant → Cloud Function HTTPS → Firestore + FCM → App Android
```

Ver [`ARCHITECTURE.md`](ARCHITECTURE.md) para el diagrama completo,
decisiones técnicas, modelo de datos, seguridad y motor de escalada.

## Requisitos

- JDK 17+ (probado con 21).
- Android Studio Iguana o superior (AGP 8.7+).
- Cuenta y proyecto en Firebase con Auth (Google), Firestore y Cloud
  Messaging activados.
- Home Assistant con el botón Sonoff SNZB-01P emparejado vía ZHA.

## Estructura del repo

```
app/src/main/java/com/manufosela/avisazbee/
├── app/                          # Composition root (Compose entry).
├── features/
│   ├── auth/                     # AppUser + AuthRepository + use cases.
│   ├── channels/                 # Channel, members, dialers, escalation.
│   ├── buttons/                  # Button + repo + use cases (Zigbee IEEE).
│   ├── alerts/                   # Alert (status, claim, resolve, repeat).
│   ├── devices/                  # Device + register.
│   └── users/                    # NotificationPreferences.
├── shared/                       # RandomTokenGenerator, utilidades.
└── infrastructure/firebase/      # Pendiente: FCM, escalation engine.
app/src/test/                     # Dominio cubierto por JUnit + MockK.
```

## Puesta en marcha (esqueleto actual)

```bash
./gradlew help            # primer arranque descarga Gradle 8.13
./gradlew :app:compileDebugKotlin
./gradlew :app:testDebugUnitTest
```

Pendiente para arrancar la app:

1. `google-services.json` propio en `app/` (descargado de Firebase Console).
2. Habilitar el plugin `com.google.gms.google-services` en `app/build.gradle.kts`
   cuando se cablée Firebase (Fase 2).
3. Cloud Function `createAlertFromHomeAssistant` (Fase 4).
4. Automatización YAML para Home Assistant (Fase 5).

## Convenciones

- Identificadores en inglés; textos visibles al usuario en español.
- Cada caso de uso expone un único método `invoke(...)` (idiomatic Kotlin).
- `domain/` no depende de `androidx.*` ni de Firebase; sólo
  `kotlinx.coroutines` y `java.time`.
