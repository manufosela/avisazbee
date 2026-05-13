import { initializeApp } from "firebase-admin/app";
import { getFirestore } from "firebase-admin/firestore";
import { getMessaging } from "firebase-admin/messaging";
import { onRequest } from "firebase-functions/v2/https";
import { onDocumentWritten } from "firebase-functions/v2/firestore";
import { CloudTasksClient } from "@google-cloud/tasks";

import { createHandler } from "./createAlertFromHomeAssistant.js";
import { createLifecycleHandler } from "./alertLifecycle.js";
import { createRepushHandler } from "./repushAlert.js";
import { defaultScheduler } from "./repushScheduler.js";

initializeApp();
const db = getFirestore();
const messaging = getMessaging();
const tasksClient = new CloudTasksClient();
const scheduler = defaultScheduler({ tasksClient });

const REGION = "europe-west1";

export const createAlertFromHomeAssistant = onRequest(
  { region: REGION, cors: false, maxInstances: 10 },
  createHandler({ db, messaging, scheduler }),
);

export const repushAlert = onRequest(
  { region: REGION, cors: false, maxInstances: 10, invoker: "private" },
  createRepushHandler({ db, messaging, scheduler }),
);

export const alertLifecycle = onDocumentWritten(
  { region: REGION, document: "alerts/{alertId}" },
  createLifecycleHandler({ db, messaging }),
);
