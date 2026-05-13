# avisazbee

App Android para recibir avisos generados por botones Zigbee Sonoff
SNZB-01P a través de Home Assistant y Firebase. Pulsar un botón dispara
una alerta que llega a todas las personas asociadas al canal del botón.

> Estado actual: **Fases 0, 1, 3, 4, 5 y 6 completadas**. Solo falta la
> **Fase 2 (configuración de Firebase)** que es manual. Detalles abajo.

## Flujo

```
Botón Zigbee → Home Assistant → Cloud Function HTTPS → Firestore + FCM → App Android
```

Ver [`ARCHITECTURE.md`](ARCHITECTURE.md) para el diagrama completo,
decisiones técnicas, modelo de datos, seguridad y motor de escalada.

## Estructura del repo

```
app/                              # Aplicación Android Kotlin/Compose
├── src/main/java/com/manufosela/avisazbee/
│   ├── app/                      # Composition root + theme + navigation + splash
│   ├── features/
│   │   ├── auth/                 # AppUser + AuthRepository + sign-in/out + screen
│   │   ├── channels/             # Channel, members, dialers, escalation + screens
│   │   ├── buttons/              # Button + repo + screens (alta con secret one-shot)
│   │   ├── alerts/               # Alert + claim/resolve
│   │   ├── devices/              # Device + register (FCM token)
│   │   └── users/                # NotificationPreferences + screen
│   └── infrastructure/
│       ├── di/                   # FirebaseModule + DataModule + SharedModule (Hilt)
│       ├── device/               # DeviceIdProvider + DeviceRegistrar
│       ├── lifecycle/            # ActivityHolder para Credentials API
│       └── firebase/
│           ├── auth/             # FirebaseAuthRepository (Credentials API)
│           └── fcm/              # AvisazbeeMessagingService + AlertNotificationHelper
└── src/test/                     # JUnit + MockK del dominio
functions/                        # Cloud Functions Node 20 ESM
├── src/                          # createAlertFromHomeAssistant + alertLifecycle
└── test/                         # Vitest (8/8 verde)
home-assistant/                   # Ejemplo de automation.yaml para HA
firestore.rules                   # Reglas de seguridad por colección
firestore.indexes.json            # Índices compuestos
firebase.json                     # Firebase project config
```

## Fase 2 — qué tienes que hacer tú

La Fase 2 es **configuración manual** de Firebase. Pasos:

1. **Crear proyecto en [Firebase Console](https://console.firebase.google.com/)**:
   - Nombre sugerido: `avisazbee` o `avisazbee-prod`.
   - Añadir app Android con package `com.manufosela.avisazbee`.
   - Habilitar **Authentication → Google** como proveedor.
   - Habilitar **Firestore Database** (modo producción, región
     `europe-west1` para que coincida con las Cloud Functions).
   - Habilitar **Cloud Messaging** (incluido por defecto).
   - **Cambiar a plan Blaze** (necesario para Cloud Functions con salida
     a internet — Spark no lo permite). Tarifa por uso, prácticamente
     gratis para uso doméstico.

2. **Obtener SHA-1 del keystore debug** y registrarlo en Firebase
   Console → Configuración del proyecto → Tus apps → SHA-1:

   ```bash
   keytool -list -v -keystore ~/.android/debug.keystore \
     -alias androiddebugkey -storepass android -keypass android
   ```

3. **Descargar `google-services.json`** desde Firebase Console y dejarlo
   en `app/google-services.json` (ya está en `.gitignore`).

4. **Anotar el Web Client ID** (Firebase Console → Authentication →
   Sign-in method → Google → Web SDK configuration) y guardarlo en
   `local.properties`:

   ```properties
   GOOGLE_WEB_CLIENT_ID=12345-abc.apps.googleusercontent.com
   ```

5. **Activar el plugin `com.google.gms.google-services`** en
   `app/build.gradle.kts` (la línea aún no está; añade en plugins:
   `alias(libs.plugins.googleServices)` y la alias en
   `gradle/libs.versions.toml`).

6. **Desplegar Cloud Functions, reglas e índices** (requiere Firebase
   CLI: `npm install -g firebase-tools && firebase login`):

   ```bash
   firebase use --add                       # selecciona el proyecto
   cd functions && npm install && cd ..
   firebase deploy --only firestore:rules
   firebase deploy --only firestore:indexes
   firebase deploy --only functions
   ```

7. **Configurar Home Assistant**: copia el contenido de
   `home-assistant/automation.yaml.example` adaptándolo a tu setup
   (IEEE MAC del SNZB-01P, secret que la app te enseña al alta, URL de
   la Cloud Function desplegada).

## Verificación de la app (sin Firebase)

```bash
./gradlew :app:compileDebugKotlin
./gradlew :app:testDebugUnitTest
cd functions && npm test
```

Todo debe compilar y los tests pasar incluso sin `google-services.json`.
Solo el runtime real necesita el setup completo de Fase 2.

## Convenciones

- Identificadores en inglés; textos visibles al usuario en español.
- Cada caso de uso expone un único método `invoke(...)`.
- `domain/` no depende de `androidx.*` ni de Firebase; sólo
  `kotlinx.coroutines` y `java.time`.
- Conventional commits, PRs atómicos contra `main`.
