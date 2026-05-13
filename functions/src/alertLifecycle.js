/**
 * Reacts to alert document writes to:
 *   - emit a silent FCM push to every device of the channel when an alert
 *     transitions to `claimed` or `resolved`, so other receivers update their
 *     UI without sound;
 *   - log the transition.
 *
 * NOTE: cancellation of pending repush tasks (Cloud Tasks) is a TODO; the
 * createAlertFromHomeAssistant function does not enqueue tasks yet.
 */
export function createLifecycleHandler({ db, messaging, logger = console }) {
  return async function handler(event) {
    const before = event.data?.before?.data?.();
    const after = event.data?.after?.data?.();
    if (!after) return;
    const previous = before?.status ?? null;
    const current = after.status;
    if (previous === current) return;
    if (current !== "claimed" && current !== "resolved") return;

    const tokens = await collectFcmTokens(db, after.channelId);
    if (tokens.length === 0) return;

    const payload = {
      alertId: event.params?.alertId ?? "",
      channelId: after.channelId,
      kind: current === "claimed" ? "claim" : "resolve",
      actor: current === "claimed" ? after.claimedBy ?? "" : after.resolvedBy ?? "",
    };
    try {
      await messaging.sendEachForMulticast({
        tokens,
        data: payload,
        android: { priority: "normal" },
      });
    } catch (error) {
      logger.warn(`alertLifecycle: silent push failed: ${error.message}`);
    }
  };
}

async function collectFcmTokens(db, channelId) {
  const membersSnap = await db.collection("channels").doc(channelId).collection("members").get();
  const uids = membersSnap.docs
    .filter((m) => m.data().notificationsEnabled !== false)
    .map((m) => m.id);
  if (uids.length === 0) return [];
  const tokens = new Set();
  const CHUNK = 30;
  for (let i = 0; i < uids.length; i += CHUNK) {
    const chunk = uids.slice(i, i + CHUNK);
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
