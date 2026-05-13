import { initializeApp } from "firebase-admin/app";
import { getFirestore } from "firebase-admin/firestore";
import { getMessaging } from "firebase-admin/messaging";
import { onRequest } from "firebase-functions/v2/https";
import { onDocumentWritten } from "firebase-functions/v2/firestore";

import { createHandler } from "./createAlertFromHomeAssistant.js";
import { createLifecycleHandler } from "./alertLifecycle.js";

initializeApp();
const db = getFirestore();
const messaging = getMessaging();

export const createAlertFromHomeAssistant = onRequest(
  { region: "europe-west1", cors: false, maxInstances: 10 },
  createHandler({ db, messaging }),
);

export const alertLifecycle = onDocumentWritten(
  { region: "europe-west1", document: "alerts/{alertId}" },
  createLifecycleHandler({ db, messaging }),
);
