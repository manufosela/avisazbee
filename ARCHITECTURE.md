# Arquitectura de avisazbee

Documento vivo. Captura las decisiones que sustentan el esqueleto inicial.
Cada fase posterior añadirá detalles concretos sin invalidar lo descrito aquí.

## 1. Modelo de producto

avisazbee organiza los avisos alrededor de **canales**. Un canal representa
un punto físico de emisión: el botón Zigbee en casa de Manu, en casa de la
abuela, en el coche, etc.

Personas alrededor de un canal:

- **Owner** — la persona que crea el canal. Suele ser un familiar/cuidador,
  **no** la persona dependiente. Puede borrar el canal, promover y degradar
  admins y transferir la propiedad.
- **Admin** — co-gestor delegado por el owner. Edita la política de
  escalada, añade y quita receivers, marca dispositivos como dialer y
  gestiona los botones del canal. No puede tocar otros admins ni borrar el
  canal.
- **Receiver** — recibe avisos y puede reclamarlos y resolverlos.
- **Persona dependiente** — sujeto del canal. Se modela como un receiver
  más, con su propia cuenta Google en su móvil; lo configura un familiar la
  primera vez. Su `Device` queda marcado como **dialer** del canal y es
  quien realiza las llamadas de escalada (ver §3.11).

Reglas operativas:

- El owner puede tener varios canales y un receiver puede estar suscrito a
  varios (decisión tomada explícitamente para no limitar casos reales: casa
  + oficina + coche).
- Los receivers entran a un canal con un **código de invitación corto** que
  el owner o un admin les pasa de viva voz o por mensaje.
- Un canal puede tener uno o varios **botones** físicos asociados (un
  Sonoff SNZB-01P por habitación, por ejemplo). Cada botón se identifica
  por su **IEEE MAC** y lleva su propio secret HTTP que vive sólo en Home
  Assistant; el cliente nunca lo vuelve a ver tras la creación. Cualquier
  pulsación genera una alerta dirigida al canal del botón.

### Cómo se atiende un aviso

Cuando la persona dependiente pulsa el botón, todos los receivers del canal
reciben un push ruidoso. A partir de ahí, la coordinación es explícita:

1. El primero que abre la app pulsa **"Atiendo yo"** (*claim*). El resto
   recibe una actualización silenciosa: ven en la app quién está atendiendo
   y dejan de sentirse obligados a ir.
2. Si **nadie** reclama el aviso, el backend lo vuelve a empujar
   automáticamente cada `repeatIntervalSeconds` (por defecto 30, se puede
   bajar a 10 para casos urgentes). Esto sucede aunque la app esté cerrada:
   no depende del cliente.
3. Cuando termina, quien atendió (o cualquier miembro, ej. en falsas
   alarmas) pulsa **"Cerrar"** y escribe un mensaje obligatorio:
   "Todo OK", "Ambulancia avisada", "Solo se le cayó algo"...
4. Cada usuario controla **cómo** suenan los avisos en sus dispositivos:
   sonido, vibración, ambos (por defecto) o silencio.

Quien reclama no es "el dueño legal" del aviso: si otra persona también
acude físicamente, allá ellos; la app no media en el conflicto, sólo
comunica intención.

## 2. Flujo end-to-end

```
┌────────────────────┐  Zigbee   ┌────────────────────┐
│  Sonoff SNZB-01P   │──────────►│   Home Assistant   │
└────────────────────┘   (ZHA)   │   (automatización) │
                                 └─────────┬──────────┘
                                           │ HTTPS POST
                                           │ { buttonId, secret,
                                           │   alertType, message }
                                           ▼
                          ┌──────────────────────────────┐
                          │  Cloud Function HTTPS         │
                          │  createAlertFromHomeAssistant │
                          │                               │
                          │  1. lee buttons/{ieee}        │
                          │  2. rechaza si no existe o    │
                          │     enabled=false             │
                          │  3. bcrypt-compara el secret  │
                          │  4. resuelve channelId del    │
                          │     botón y guarda alert con  │
                          │     buttonId + buttonName     │
                          │  5. lee members + devices del │
                          │     canal y FCM multicast     │
                          │  6. encola Cloud Task         │
                          │     repushAlert(t+interval)   │
                          └──────────────┬───────────────┘
                                         │
              ┌──────────────────────────┼────────────────────────────┐
              ▼                          ▼                            ▼
   ┌──────────────────┐      ┌──────────────────┐         ┌──────────────────┐
   │ Firestore        │      │ Firestore        │         │      FCM         │
   │ alerts (write)   │      │ members + devices│         │ multicast push   │
   │                  │      │ (read)           │         │                  │
   └────────┬─────────┘      └──────────────────┘         └──────────────────┘
            │ onUpdate (claim / resolve)                            │
            │                                                       │
            ▼                                                       ▼
   ┌──────────────────┐                                ┌──────────────────────┐
   │ Cloud Function    │                                │  App Android (Kotlin │
   │ alertLifecycle    │   ──── push silenciosa ────►   │  + Compose) reactiva │
   │ - cancela reaviso │                                │  a Firestore + FCM   │
   │ - notifica resto  │                                │                      │
   └──────────────────┘                                └──────────────────────┘
```

Tres caminos para mantener al cliente al día:

1. **Push ruidosa (FCM)**: aviso inmediato y reavisos automáticos mientras
   nadie reclame.
2. **Push silenciosa**: cuando alguien hace `claim` o `resolve`, el resto
   recibe una notificación de datos sin sonido para refrescar UI/badge en
   background.
3. **Pull-en-tiempo-real (Firestore listener)**: la pantalla del canal
   escucha `alerts` filtrado por `channelId` y se actualiza incluso si las
   push se pierden.

## 3. Decisiones técnicas

### 3.1 Por qué Home Assistant llama a una Cloud Function

- La función actúa como **adapter** entre el dominio domótico y Firebase.
- Centraliza la validación (secret, payload) y el _fan-out_ a FCM sin exponer
  el SDK Admin en HA.
- Permite añadir lógica (rate-limit, deduplicación, traducciones) sin tocar HA.

Alternativa descartada: escribir directamente en Firestore desde HA. Requeriría
embeber un service account en HA o emitir tokens custom; más superficie de
ataque y menos control.

### 3.2 Autenticación

- **Google Sign-In** desde el minuto cero, vía Credentials API (`androidx.
  credentials` + `googleid`) más Firebase Auth. Justificación: app sólo
  Android donde la cuenta Google ya está presente; menor fricción y sin
  gestión de contraseñas.
- El `uid` de Firebase es la **identidad estable** del usuario en todo el
  sistema (miembros de canal, owner de dispositivos, autor de acuses).
- En `features/auth/domain/` se expone un `AuthRepository` que abstrae
  estos detalles detrás de `Flow<AppUser?> authStateChanges()` y
  `suspend fun signInWithGoogle()`. La UI y los casos de uso no conocen
  Firebase ni Credentials.

### 3.3 Arquitectura de la app (feature-first + clean lite)

```
app/src/main/java/com/manufosela/avisazbee/
├── app/                          # Composition root (Compose entry, theme).
├── features/
│   ├── auth/
│   │   ├── domain/               # AppUser, AuthRepository, sign-in/out use cases.
│   │   └── presentation/         # Sign-in screen + ViewModel.
│   ├── channels/
│   │   ├── domain/               # Channel, ChannelMember, ChannelDialer,
│   │   │                          # EscalationPolicy + repos + use cases.
│   │   ├── data/                 # Firestore impl.
│   │   └── presentation/         # Lista, detalle, alta, join, política.
│   ├── buttons/
│   │   ├── domain/               # Button + repo + use cases (create, rotate,
│   │   │                          # rename, enable, delete).
│   │   ├── data/                 # Firestore impl.
│   │   └── presentation/         # Alta y gestión de botones por canal.
│   ├── alerts/
│   │   ├── domain/               # Alert (+status), claim/resolve/watch.
│   │   ├── data/                 # Firestore impl.
│   │   └── presentation/         # Listado por canal, tile, claim/resolve.
│   ├── devices/
│   │   ├── domain/               # Device + repo + register.
│   │   └── data/                 # Firestore impl.
│   └── users/
│       ├── domain/               # NotificationPreferences + repo + use cases.
│       └── data/                 # Firestore impl.
├── shared/                       # RandomTokenGenerator y utilidades transversales.
└── infrastructure/firebase/      # FCM service, local notifications, escalation engine.
```

Reglas:

- `domain/` es Kotlin puro: no `androidx.*` ni Firebase. Sólo
  `kotlinx.coroutines` (Flow) y `java.time` están permitidos.
- `data/` traduce entre Firestore (`Map<String, Any?>`) y los modelos del
  dominio.
- `presentation/` consume **use cases** vía ViewModels inyectados con Hilt.
- Composition root: `AvisazbeeApplication` (@HiltAndroidApp) +
  `MainActivity` (@AndroidEntryPoint).

### 3.4 Stack

| Capa | Elección | Motivo |
|------|----------|--------|
| Lenguaje app | Kotlin 2.0 | Decidido — sólo Android. |
| UI | Jetpack Compose + Material 3 | Standard moderno Android. |
| DI | Hilt (Dagger) | Integración nativa con WorkManager y Compose. |
| Auth | Firebase Auth + Credentials API + Google Sign-In | Decidido (§3.2). |
| Backend events | Cloud Firestore (SDK Android) | Modelo flexible y listeners nativos. |
| Push | Firebase Cloud Messaging | Estándar Android. |
| Notificación local | `NotificationCompat` + `NotificationChannel` | API nativa. |
| Vibración | `Vibrator` / `VibratorManager` | API nativa. |
| Background work | WorkManager + Coroutines | Doze-aware, idiomatic. |
| HA → backend | Cloud Functions HTTPS (Node 20, ESM, JS) | Más simple que TypeScript. |
| Hash de secrets | `bcrypt` en la Function | Brute-force defense en backend. |
| Tests | JUnit 4 + MockK + Truth + Turbine | Stack estándar Android. |

### 3.5 Identidad y dispositivos

- **Identidad del usuario**: `uid` de Firebase Auth.
- **Identidad del dispositivo**: id propio (uuid persistido en DataStore al
  primer arranque). El FCM token se guarda como atributo y se actualiza al
  rotar.
- Un dispositivo siempre pertenece a un único `ownerUid`. Sus
  `subscribedChannels` son el subconjunto de los canales en los que el dueño
  ya es miembro (la Function valida esto antes de enviar).

### 3.6 Códigos de invitación y secrets de botón

- **Invite code**: 6 caracteres de un alfabeto de 32 sin caracteres
  ambiguos (0/O/1/I quedan fuera). Espacio de búsqueda ≈ 1.000 millones.
  Rotable.
- **Secret de botón**: 32 caracteres URL-safe, generados en cliente con
  `SecureRandom`, hasheados con `bcrypt` en el backend antes de
  almacenarse. El valor plano se muestra **una vez** al owner/admin para
  copiarlo a Home Assistant; nunca vuelve a leerse.
- Cada botón Zigbee es identificado por su **IEEE MAC**
  (`E4:56:AC:FF:FE:5E:CD:AA`) usado directamente como id del documento
  `buttons/{ieee}` en Firestore. El cliente normaliza la entrada del
  usuario a mayúsculas con dos puntos antes de cualquier llamada
  (`IeeeAddress.normalise`).

### 3.7 Ciclo de vida de la alerta

```
                claim                       resolve(msg)
   ┌──────────┐  →  ┌──────────────────┐    →   ┌──────────┐
   │ pending  │     │     claimed      │        │ resolved │ (terminal)
   │ (sonando│     │  claimedBy=uidX  │        │          │
   │  cada N)│     │                  │        │          │
   └──────────┘     └──────────────────┘        └──────────┘
        │                                              ▲
        └────────────── resolve(msg) ──────────────────┘
                       (falsa alarma)
```

- No hay "release" ni "robo de claim". Quien reclama figura como tal hasta
  resolución. Si otro miembro también responde físicamente, la app no media.
- `resolved` es terminal: la alerta queda inmutable en el historial.
- El campo `resolution` es obligatorio (validado en
  `ResolveAlertUseCase`) — incluso para falsa alarma el cierre lleva un
  texto explicativo aunque sea "OK, nada que reportar".

### 3.8 Reaviso automático

- Al crear una alerta, la Function encola una **Cloud Task** programada
  `createdAt + repeatIntervalSeconds`.
- La tarea ejecuta `repushAlert(alertId)`:
  - lee la alerta;
  - si sigue en `pending`, manda otra push, incrementa `repeatCount`,
    actualiza `lastRepeatAt` y se re-encola para el siguiente tick;
  - si está `claimed` o `resolved`, se autodesecha y no encola más.
- Una segunda Function `alertLifecycle` (Firestore trigger `onUpdate`)
  cancela tareas en cola cuando hay claim/resolve y emite la push silenciosa
  al resto de miembros.
- Cliente-side no hay scheduling: si el móvil está en background, el sistema
  de notificaciones nativo se encarga de despertarlo.

### 3.9 Preferencias de notificación

- Modelo `NotificationMode { sound, vibration, both, silent }` por usuario,
  persistido en `users/{uid}/preferences/notifications`. Default `both`.
- El push enviado por la Function **siempre** contiene `priority: high` y
  los datos del aviso; el cliente decide cómo materializarlo según el modo
  preferido del receptor (sonido/vibración/silencio).
- Se elige a propósito decidir el modo en cliente y no en servidor: así un
  mismo usuario puede cambiar de modo sin tocar la lógica de envío, y el
  modo no se filtra a otros miembros del canal.

### 3.10 Política de escalada

Si una alerta lleva `escalateAfterSeconds` (default **180 s** = 3 min) sin
que nadie haga claim, los **dialers** del canal empiezan a marcar números
de forma autónoma.

Configuración por canal, en `channels/{id}/config/escalation`:

```
enabled                : bool      (default true)
escalateAfterSeconds   : int       (min 30, default 180)
contacts               : [{ name, phoneE164 }]    # orden importa
perContactRingSeconds  : int       (10..90, default 25)
useSpeakerphone        : bool      (default true)
loopUntilAnswered      : bool      (default true)
```

Validación en `UpdateEscalationPolicyUseCase`:

- Si `enabled`, `contacts` no puede estar vacío.
- Cada `phoneE164` cumple `^\+?[0-9]{6,15}$`.
- Cada `name` no puede estar en blanco tras trim.
- Sólo owner o admin pueden cambiarla.

### 3.11 Motor de escalada en Android (dialer device)

El dialer es un dispositivo Android marcado como tal por el owner/admin
mediante `channels/{id}/dialers/{deviceId}`. Al ser Kotlin nativo, la
lógica vive directamente en `infrastructure/firebase/` sin bridges. Cuando
ese dispositivo recibe el push inicial de un aviso del canal:

1. Programa un trabajo (`WorkManager` único por `alertId`) con delay
   `escalateAfterSeconds`.
2. Al disparar, lee el estado de la alerta:
   - Si está `claimed` o `resolved`, descarta el trabajo y termina.
   - Si sigue `pending`, arranca un **foreground service** con notificación
     persistente "avisazbee está llamando…" y comienza el ciclo.
3. Ciclo:
   - Para cada `contact` en orden:
     - `Intent.ACTION_CALL` al `phoneE164`.
     - Activa altavoz si `useSpeakerphone` (`AudioManager`).
     - Suscribe `TelephonyCallback` (`READ_PHONE_STATE`):
       - Si no entra en `OFFHOOK` en `perContactRingSeconds`, cuelga
         (`endCall`) y siguiente.
       - Si entra en `OFFHOOK` pero la llamada dura **< 5 s** tras
         responder, asumimos buzón de voz y siguiente.
       - Si dura ≥ 5 s asumimos respuesta humana y el ciclo termina.
   - Si nadie atiende y `loopUntilAnswered`, vuelve al primer contacto.
4. Cancelación instantánea: la push silenciosa de `claim` o `resolve`
   detiene el servicio y, si hay una llamada en curso, la cuelga.

**Permisos pedidos al móvil dialer**:

- `CALL_PHONE`
- `READ_PHONE_STATE`
- `MODIFY_AUDIO_SETTINGS`
- `USE_FULL_SCREEN_INTENT`
- `FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_PHONE_CALL`
- Exención de la optimización de batería ("Doze")

Sin estos permisos, el motor no arranca y la app deja un banner explicando
qué falta. La detección de "no contestaron" es heurística (timing +
duración mínima); funcional pero no 100% precisa.

**Caveats explícitamente asumidos por producto**:

- Si el móvil dialer está apagado, sin batería o sin cobertura, el motor no
  actúa. No hay fallback en cloud.
- El bucle continúa hasta respuesta humana o agotamiento del dispositivo.
- La detección puede confundir buzón de voz con respuesta; el cliente
  espera al menos 5 s tras `OFFHOOK` para asumir éxito.

## 4. Modelo de datos

### 4.1 `users/{uid}`

| Campo         | Tipo      | Notas |
|---------------|-----------|-------|
| `email`       | string?   | Tal como lo devuelve Google. |
| `displayName` | string?   | Nombre del perfil Google. |
| `photoUrl`    | string?   | Avatar del perfil Google. |
| `createdAt`   | Timestamp | Server timestamp en alta. |

### 4.2 `channels/{channelId}`

| Campo            | Tipo      | Notas |
|------------------|-----------|-------|
| `name`           | string    | "Casa Manu", "Coche"…. |
| `ownerUid`       | string    | `users/{uid}`. |
| `inviteCode`     | string    | 6 caracteres, alfabeto sin ambigüedad. |
| `inviteEnabled`  | bool      | `false` deshabilita nuevas adhesiones sin rotar el código. |
| `createdAt`      | Timestamp | Server timestamp. |

### 4.3 `channels/{channelId}/members/{uid}`

| Campo                  | Tipo      | Notas |
|------------------------|-----------|-------|
| `role`                 | string    | `"owner"` \| `"admin"` \| `"receiver"`. |
| `joinedAt`             | Timestamp | Server timestamp. |
| `notificationsEnabled` | bool      | Si `false`, la Function omite a este miembro. |

### 4.4 `buttons/{ieee}`

Identidad física de cada botón Zigbee. La key del documento es la dirección
IEEE MAC del botón (`E4:56:AC:FF:FE:5E:CD:AA`), normalizada a mayúsculas
con dos puntos.

| Campo          | Tipo      | Notas |
|----------------|-----------|-------|
| `name`         | string    | "Botón cocina", "Mando salón"… |
| `channelId`    | string    | Canal de notificación al que pertenece. |
| `enabled`      | bool      | Si `false`, la Function rechaza HA con 403. |
| `hashedSecret` | string    | `bcrypt` del secret que Home Assistant envía. |
| `createdAt`    | Timestamp | Server timestamp. |
| `createdBy`    | string    | uid del owner o admin que registró el botón. |

Reemplaza al modelo previo `channels/{id}/secrets/{secretId}`: el botón
**es** la entidad de autenticación, y queda 1 botón ↔ 1 canal. Un canal
puede tener varios botones (cocina, salón, baño…).

### 4.5 `devices/{deviceId}`

| Campo                | Tipo      | Notas |
|----------------------|-----------|-------|
| `ownerUid`           | string    | `users/{uid}` del dueño. |
| `fcmToken`           | string    | Token actual. |
| `platform`           | string    | `"android"` (única plataforma soportada). |
| `name`               | string    | Nombre amigable. |
| `enabled`            | bool      | Pausa global del dispositivo. |
| `subscribedChannels` | string[]  | Canales que escucha activamente. |
| `createdAt`          | Timestamp | Server timestamp. |
| `updatedAt`          | Timestamp | Pulse-de-vida en cada arranque real. |

### 4.6 `alerts/{alertId}`

| Campo                   | Tipo       | Notas |
|-------------------------|------------|-------|
| `channelId`             | string     | Resuelto por la Function a partir de `buttons/{buttonId}.channelId`. |
| `buttonId`              | string     | IEEE MAC del botón que generó la alerta. |
| `buttonName`            | string     | Snapshot del nombre del botón al crear (no se confía en HA). |
| `type`                  | string     | `alertType` enviado por HA (`"short_press"`…). |
| `title`                 | string     | Texto de la notificación. |
| `message`               | string     | Cuerpo del aviso. |
| `source`                | string     | `"home_assistant"`. |
| `createdAt`             | Timestamp  | Server timestamp. |
| `status`                | string     | `"pending"` \| `"claimed"` \| `"resolved"`. |
| `claimedBy`             | string?    | uid del que reclamó (null en `pending`). |
| `claimedAt`             | Timestamp? | Server timestamp del claim. |
| `resolvedBy`            | string?    | uid del que cerró. |
| `resolvedAt`            | Timestamp? | Server timestamp del cierre. |
| `resolution`            | string?    | Mensaje obligatorio al cerrar. |
| `repeatIntervalSeconds` | number     | Cadencia del reaviso (default 30). |
| `repeatCount`           | number     | Reavisos ya emitidos por el backend. |
| `lastRepeatAt`          | Timestamp? | Último reaviso emitido. |

`buttonId` y `buttonName` se guardan como snapshot porque el botón puede
renombrarse o borrarse en el futuro; el historial de alertas debe seguir
siendo legible aunque el botón ya no exista.

Índices: composite `channelId + createdAt desc`, y un único composite
`status + createdAt asc` para que el job de reaviso recorra eficientemente
las alertas `pending`.

### 4.7 `users/{uid}/preferences/notifications`

| Campo  | Tipo   | Notas |
|--------|--------|-------|
| `mode` | string | `"sound"` \| `"vibration"` \| `"both"` (default) \| `"silent"`. |

Documento separado del perfil para que las reglas de seguridad puedan
diferenciarlo (sólo el propio uid lee/escribe).

### 4.8 `channels/{channelId}/config/escalation`

| Campo                   | Tipo                                | Notas |
|-------------------------|-------------------------------------|-------|
| `enabled`               | bool                                | Default `true`. |
| `escalateAfterSeconds`  | int                                 | Min 30. Default 180. |
| `contacts`              | array<{ `name`, `phoneE164` }>       | Orden = orden de llamada. |
| `perContactRingSeconds` | int                                 | 10..90. Default 25. |
| `useSpeakerphone`       | bool                                | Default `true`. |
| `loopUntilAnswered`     | bool                                | Default `true`. |

### 4.9 `channels/{channelId}/dialers/{deviceId}`

| Campo       | Tipo      | Notas |
|-------------|-----------|-------|
| `addedBy`   | string    | uid del owner/admin que marcó el device. |
| `addedAt`   | Timestamp | Server timestamp. |

Un canal puede tener cero o más dialers. En la práctica habrá uno (el móvil
de la persona dependiente) o dos (móvil + tablet de respaldo).

## 5. Seguridad

### 5.1 Llamada Home Assistant → Cloud Function

- Payload JSON `{ "buttonId", "secret", "alertType", "message" }`.
- La Function:
  1. valida que `buttonId` está presente y normalizable a IEEE MAC;
  2. lee `buttons/{buttonId}` — `404 not_found` si no existe;
  3. rechaza con `403 disabled` si `enabled = false`;
  4. compara `secret` con `hashedSecret` (`bcrypt.compare`, timing-safe);
  5. lee `channelId` y `name` desde el documento — **el nombre nunca se
     toma del payload de HA**, sólo de Firestore;
  6. crea el documento `alerts/{...}` con `buttonId`, `buttonName` y
     `channelId` ya resueltos;
  7. resuelve miembros + devices del canal y hace FCM multicast.
- Solo POST; el resto de métodos responde 405.
- Rate-limit (Cloud Armor o `firebase-functions/rate-limit`) cuando entre en
  producción real.

### 5.2 Firestore Security Rules (objetivo Fase 6)

- `users/{uid}`: el propio uid lee/escribe; nadie más.
- `channels/{id}`:
  - Lectura permitida a miembros (existe `members/{uid}`).
  - Escritura del documento principal sólo al `ownerUid`; la unión de un
    receiver con código de invitación se hace mediante Cloud callable que
    valida el `inviteCode` (evita exponer un endpoint de "listar canales
    por código").
- `channels/{id}/members/{uid}`:
  - El propio `uid` puede leer su documento y modificar
    `notificationsEnabled`.
  - El owner puede leer todos y cambiar `role` (admin↔receiver). Las
    transiciones se hacen por Cloud callable que también valida que el
    canal no quede sin owner.
  - Los admins pueden leer todos los miembros y eliminar receivers.
- `buttons/{ieee}`:
  - Lectura para miembros del `channelId` al que apunta el botón.
  - Escritura **denegada** a clientes. La app crea/renombra/rota/desactiva
    botones a través de Cloud callables que validan la pertenencia al
    canal y el rol (owner/admin). Esto evita que un cliente registre un
    botón en un canal que no controla.
- `channels/{id}/config/escalation`: lectura para miembros, escritura para
  owner y admins. La Cloud callable de actualización aplica las
  validaciones de §3.10.
- `channels/{id}/dialers/{deviceId}`: lectura para miembros, escritura para
  owner y admins.
- `devices/{deviceId}`: lectura/escritura sólo al `ownerUid`.
- `alerts/{alertId}`: lectura permitida a miembros del `channelId`. La
  creación está reservada al Admin SDK (la Function HTTP de HA). Las
  transiciones `pending → claimed` y `*→ resolved` se hacen mediante Cloud
  callables que ejecutan una transacción Firestore: rechazan si el estado
  origen no es legal, evitando carreras entre dos receptores pulsando a la
  vez.
- `users/{uid}/preferences/{anything}`: lectura/escritura sólo para el
  propio uid.

### 5.3 Cosas que NO van al cliente

- Service account.
- Secrets en claro (sólo se muestran una vez en el alta y nunca se persisten).
- URL/configuración de HA.

## 6. Mejoras futuras

- **Hilo de comentarios en la alerta** (más allá del mensaje único de
  cierre) en una subcolección `alerts/{id}/messages`.
- **Reclamo asistido**: ofrecer al claimer un botón "ya no puedo, paso turno"
  que vuelva la alerta a `pending` y dispare un reaviso inmediato.
- **Escalado manual**: tras resolución, permitir reabrir si la situación se
  complica (`resolved → claimed` con motivo).
- **Diferentes tipos de pulsación** (`short_press`, `long_press`,
  `double_press`) por botón, con plantillas de mensaje por tipo y canal.
- **Override de cadencia por canal**: hoy `repeatIntervalSeconds` se fija al
  crear; permitir defaults por canal y por hora del día.
- **Notificación silenciosa de mantenimiento** (refresco FCM token, baja
  ordenada de dispositivos cerrando sesión).
- **Múltiples tipos de evento** (`zigbee_button_long_press`, `motion`, …)
  discriminados por `type`, con plantillas de mensaje por tipo y canal.
- **Plantillas personalizadas por canal**: el owner define title/message por
  defecto desde la app y HA solo dispara `type`.
- **Auditoría**: subcolección `alerts/{id}/deliveries` con un registro por
  `fcmToken` y estado (`sent`, `failed`, `unregistered`) para limpiar tokens
  muertos automáticamente.
- **Observabilidad**: log estructurado en la Function, dashboard de entregas.
- **Tests E2E**: Firebase Emulator Suite + Flutter integration tests.

## 7. Convenciones de código

- Identificadores en inglés; textos visibles al usuario en español.
- Comentarios `///` (dartdoc) en inglés cuando aportan algo.
- Sin fallbacks silenciosos: si un servicio crítico falla en arranque, se
  refleja en la UI.
- Cada caso de uso tiene un solo método público `call(...)`.
- `domain/` es Dart puro: no `package:flutter/*` ni `package:firebase_*`.
