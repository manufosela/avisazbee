# Cloud Functions de avisazbee

Node 20, ESM. Tres funciones desplegadas (region `europe-west1`):

- `createAlertFromHomeAssistant` (HTTPS, POST) — entrada desde Home Assistant.
  Valida `buttonId` + `secret` (bcrypt), escribe `alerts/{id}`, hace multicast
  FCM y encola la primera tarea de reaviso.
- `repushAlert` (HTTPS, POST, `invoker: private`) — destino de las Cloud
  Tasks de reaviso. Si la alerta sigue en `pending`, manda otra push,
  incrementa `repeatCount`/`lastRepeatAt` y re-encola la siguiente
  ronda. Si ya está `claimed`/`resolved`, no hace nada y la cadena se
  autodestruye.
- `alertLifecycle` (Firestore `onDocumentWritten` en `alerts/{alertId}`) —
  cuando una alerta pasa a `claimed` o `resolved`, manda una push
  silenciosa (priority normal) al resto del canal para refrescar UI/badges.
  No cancela tareas en vuelo: `repushAlert` ignora alertas no-pending.

## Payload HTTPS (HA → CF)

```http
POST /createAlertFromHomeAssistant
Content-Type: application/json

{
  "buttonId":  "E4:56:AC:FF:FE:5E:CD:AA",
  "secret":    "<plain secret guardado en HA>",
  "alertType": "short_press",
  "message":   "Botón pulsado en cocina"
}
```

Respuestas:

- `200 { alertId, delivered }` cuando la alerta se crea (delivered = tokens FCM atendidos).
- `400 invalid_payload` si falta `buttonId` o `secret`, o la MAC no es válida.
- `403 disabled` si `buttons/{id}.enabled = false`.
- `403 forbidden` si el `secret` no casa con `hashedSecret`.
- `404 not_found` si el botón no existe.
- `405 method_not_allowed` para verbos distintos de POST.
- `500 channel_missing` si el botón apunta a un canal inexistente.

## Reaviso automático con Cloud Tasks

Cada alerta nueva encola una tarea HTTP en una cola **Cloud Tasks** que,
al pasar `repeatIntervalSeconds` segundos, invoca `repushAlert`. Esta lee
la alerta y decide:

- Si sigue en `pending`: re-multicast FCM + incrementa `repeatCount` +
  re-encola con el mismo delay.
- Si está `claimed` o `resolved`: termina y la cadena no se renueva.

Diseño self-healing: no cancelamos tareas en vuelo. Tras un claim/resolve
puede llegar una push de más (la ya en cola); el handler la convierte en
no-op. Acceptable trade-off por evitar la complejidad de tracking de
nombres de tarea.

### Setup de Cloud Tasks (única vez, manual)

Antes del primer `firebase deploy --only functions`:

```bash
# 1) Crear la cola (región igual que las CF):
gcloud tasks queues create avisazbee-repush --location=europe-west1

# 2) Crear o reutilizar una service account con permiso
#    cloudtasks.enqueuer + cloudfunctions.invoker. La SA por defecto de
#    App Engine (PROJECT-ID@appspot.gserviceaccount.com) suele bastar.
#    Establece su email para que la tarea firme su llamada con OIDC:
firebase functions:secrets:set AVISAZBEE_TASKS_SA
# o pásala como env var al desplegar:
#   AVISAZBEE_TASKS_SA=PROJECT-ID@appspot.gserviceaccount.com
```

Variables de entorno respetadas (todas opcionales si no las defines):

| Variable                       | Default                                                | Para |
|--------------------------------|--------------------------------------------------------|------|
| `AVISAZBEE_TASKS_LOCATION`     | `europe-west1`                                         | Región de la cola. |
| `AVISAZBEE_REPUSH_QUEUE`       | `avisazbee-repush`                                     | Nombre de la cola. |
| `AVISAZBEE_FUNCTION_REGION`    | igual que `AVISAZBEE_TASKS_LOCATION`                   | Región del target HTTP. |
| `AVISAZBEE_REPUSH_URL`         | `https://{region}-{project}.cloudfunctions.net/repushAlert` | Override para test/emulator. |
| `AVISAZBEE_TASKS_SA`           | vacío                                                  | SA que firma el OIDC token. Sin esto, `repushAlert` (que es `invoker: private`) rechazará la llamada. |

Si la configuración no está completa el scheduler no encola la tarea y
deja un `warn` en logs; la alerta inicial llega pero no se reavisa.

## Desarrollo

```bash
cd functions
npm install
npm test           # vitest unit tests
npm run serve      # firebase emulators:start --only functions
npm run deploy     # firebase deploy --only functions  (requiere proyecto Firebase + plan Blaze)
```

El secret HTTP de cada botón se hashea con `bcrypt` cost 12 en el cliente
Android (Fase 1). La function compara con `bcrypt.compare`, timing-safe.

## TODO

- Rate-limit por IP / por `buttonId` (Fase 6).
- Auditoría: subcolección `alerts/{id}/deliveries` con un registro por
  `fcmToken` y estado (`sent`, `failed`, `unregistered`) para limpiar
  tokens muertos automáticamente.
