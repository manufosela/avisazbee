/**
 * Cloud Task target.
 *
 *   POST /repushAlert
 *   { "alertId": "<id>" }
 *
 * Mientras la alerta siga en `pending` reenvía push ruidosa al canal,
 * incrementa `repeatCount`, actualiza `lastRepeatAt` y re-encola la
 * siguiente ronda con `repeatIntervalSeconds`. Si la alerta ya está
 * `claimed` o `resolved` no hace nada y la cadena se autodestruye.
 */
export function createRepushHandler({ db, messaging, scheduler = null, logger = console }) {
  return async function handler(req, res) {
    if (req.method !== "POST") {
      res.status(405).json({ error: "method_not_allowed" });
      return;
    }
    const alertId = req.body?.alertId;
    if (typeof alertId !== "string" || alertId.length === 0) {
      res.status(400).json({ error: "invalid_payload" });
      return;
    }

    const alertRef = db.collection("alerts").doc(alertId);
    const alertSnap = await alertRef.get();
    if (!alertSnap.exists) {
      res.status(200).json({ status: "gone" });
      return;
    }
    const alert = alertSnap.data();
    if (alert.status !== "pending") {
      res.status(200).json({ status: alert.status, delivered: 0 });
      return;
    }

    const channelId = alert.channelId;
    const channelSnap = await db.collection("channels").doc(channelId).get();
    if (!channelSnap.exists) {
      logger.warn(`repushAlert: channel ${channelId} missing for alert ${alertId}`);
      res.status(200).json({ status: "channel_missing", delivered: 0 });
      return;
    }
    const channelName = channelSnap.data().name ?? "avisazbee";

    const tokens = await collectFcmTokens(db, channelId);
    const dataPayload = {
      alertId,
      channelId,
      channelName,
      title: alert.title ?? `Aviso desde ${channelName}`,
      message: alert.message ?? "",
      repeat: "1",
    };
    let delivered = 0;
    if (tokens.length > 0 && typeof messaging.sendEachForMulticast === "function") {
      const response = await messaging.sendEachForMulticast({
        tokens,
        data: dataPayload,
        android: { priority: "high" },
      });
      delivered = response.successCount ?? 0;
    }

    await alertRef.update({
      repeatCount: (alert.repeatCount ?? 0) + 1,
      lastRepeatAt: new Date(),
    });

    if (scheduler) {
      await scheduler.schedule(alertId, alert.repeatIntervalSeconds ?? 30);
    }

    res.status(200).json({ status: "pending", delivered });
  };
}

async function collectFcmTokens(db, channelId) {
  const membersSnap = await db.collection("channels").doc(channelId).collection("members").get();
  const memberUids = membersSnap.docs
    .filter((m) => m.data().notificationsEnabled !== false)
    .map((m) => m.id);
  if (memberUids.length === 0) return [];
  const tokens = new Set();
  const CHUNK = 30;
  for (let i = 0; i < memberUids.length; i += CHUNK) {
    const chunk = memberUids.slice(i, i + CHUNK);
    const devicesSnap = await db.collection("devices")
      .where("ownerUid", "in", chunk)
      .where("enabled", "==", true)
      .get();
    for (const doc of devicesSnap.docs) {
      const data = doc.data();
      if (typeof data.fcmToken === "string" && data.fcmToken.length > 0) {
        tokens.add(data.fcmToken);
      }
    }
  }
  return Array.from(tokens);
}
