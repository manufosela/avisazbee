# Cloud Functions de avisazbee

Node 20, ESM. Dos funciones desplegadas (region `europe-west1`):

- `createAlertFromHomeAssistant` (HTTPS, POST) — entrada desde Home Assistant.
  Valida `buttonId` + `secret` (bcrypt), escribe `alerts/{id}` y hace multicast
  FCM a todos los dispositivos `enabled` de los miembros del canal.
- `alertLifecycle` (Firestore `onDocumentWritten` en `alerts/{alertId}`) —
  cuando una alerta pasa a `claimed` o `resolved`, manda una push silenciosa
  (priority normal) al resto del canal para refrescar UI/badges.

## Payload HTTPS

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

- Encolar Cloud Task en `createAlertFromHomeAssistant` para el reaviso
  automático (Fase 4b). Por ahora la alerta queda escrita con
  `repeatIntervalSeconds` pero no se reavisa.
- Cancelar tareas pendientes desde `alertLifecycle` cuando hay claim o resolve.
- Rate-limit por IP / por `buttonId` (Fase 6).
