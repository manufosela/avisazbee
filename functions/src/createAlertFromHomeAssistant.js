import bcrypt from "bcrypt";
import { normaliseIeee } from "./ieee.js";

/**
 * Builds the HTTPS handler. Dependencies are injected so tests can replace
 * Firestore, FCM and bcrypt with in-memory fakes.
 */
export function createHandler({ db, messaging, bcryptCompare = bcrypt.compare, logger = console }) {
  return async function handler(req, res) {
    if (req.method !== "POST") {
      res.status(405).json({ error: "method_not_allowed" });
      return;
    }
    const payload = req.body ?? {};
    const buttonId = normaliseIeee(payload.buttonId);
    const secret = typeof payload.secret === "string" ? payload.secret : null;
    const alertType = typeof payload.alertType === "string" ? payload.alertType : "short_press";
    const message = typeof payload.message === "string" ? payload.message : "";
    if (!buttonId || !secret) {
      res.status(400).json({ error: "invalid_payload" });
      return;
    }

    const buttonSnap = await db.collection("buttons").doc(buttonId).get();
    if (!buttonSnap.exists) {
      res.status(404).json({ error: "not_found" });
      return;
    }
    const button = buttonSnap.data();
    if (button.enabled === false) {
      res.status(403).json({ error: "disabled" });
      return;
    }
    const matches = await bcryptCompare(secret, button.hashedSecret ?? "");
    if (!matches) {
      res.status(403).json({ error: "forbidden" });
      return;
    }

    const channelId = button.channelId;
    const channelSnap = await db.collection("channels").doc(channelId).get();
    if (!channelSnap.exists) {
      logger.warn(`createAlertFromHomeAssistant: channel ${channelId} not found for button ${buttonId}`);
      res.status(500).json({ error: "channel_missing" });
      return;
    }
    const channelName = channelSnap.data().name ?? "avisazbee";

    const alertRef = db.collection("alerts").doc();
    const now = new Date();
    const alertDoc = {
      channelId,
      buttonId,
      buttonName: button.name ?? buttonId,
      type: alertType,
      title: `Aviso desde ${channelName}`,
      message,
      source: "home_assistant",
      createdAt: now,
      status: "pending",
      repeatIntervalSeconds: button.repeatIntervalSeconds ?? 30,
      repeatCount: 0,
    };
    await alertRef.set(alertDoc);

    const tokens = await collectFcmTokens(db, channelId);
    if (tokens.length === 0) {
      res.status(200).json({ alertId: alertRef.id, delivered: 0 });
      return;
    }

    const dataPayload = {
      alertId: alertRef.id,
      channelId,
      channelName,
      title: alertDoc.title,
      message: alertDoc.message,
    };
    const delivered = await sendMulticast({ messaging, tokens, dataPayload, logger });
    res.status(200).json({ alertId: alertRef.id, delivered });
  };
}

async function collectFcmTokens(db, channelId) {
  const membersSnap = await db.collection("channels").doc(channelId).collection("members").get();
  const memberUids = membersSnap.docs
    .filter((m) => m.data().notificationsEnabled !== false)
    .map((m) => m.id);
  if (memberUids.length === 0) return [];
  const tokens = new Set();
  const CHUNK = 30; // Firestore `in` query limit
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

async function sendMulticast({ messaging, tokens, dataPayload, logger }) {
  if (typeof messaging.sendEachForMulticast !== "function") {
    logger.warn("Messaging mock lacks sendEachForMulticast");
    return 0;
  }
  const response = await messaging.sendEachForMulticast({
    tokens,
    data: dataPayload,
    android: { priority: "high" },
  });
  return response.successCount ?? 0;
}
