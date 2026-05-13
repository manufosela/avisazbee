/**
 * Encola tareas Cloud Tasks que invocan a `repushAlert` cuando una alerta
 * lleva `repeatIntervalSeconds` sin reclamar. Diseño self-healing: la
 * tarea, al ejecutarse, lee la alerta y decide si reenviar y re-encolar
 * o no hacer nada. No se cancelan tareas en vuelo: una push de más tras
 * un claim/resolve es aceptable y simplifica el control de flujo.
 */
export class RepushScheduler {
  constructor({ tasksClient, projectId, location, queueId, targetUrl, serviceAccountEmail, logger = console }) {
    this.tasksClient = tasksClient;
    this.projectId = projectId;
    this.location = location;
    this.queueId = queueId;
    this.targetUrl = targetUrl;
    this.serviceAccountEmail = serviceAccountEmail;
    this.logger = logger;
  }

  get queuePath() {
    return `projects/${this.projectId}/locations/${this.location}/queues/${this.queueId}`;
  }

  /**
   * Programa el siguiente reaviso de `alertId` dentro de `delaySeconds`.
   * Devuelve true si la tarea se programó, false si la configuración no
   * está lista o si el cliente de tasks no está disponible (entornos de
   * test).
   */
  async schedule(alertId, delaySeconds) {
    if (!this.tasksClient || !this.projectId || !this.queueId) {
      this.logger.warn("RepushScheduler: configuración incompleta; no se encola tarea.");
      return false;
    }
    const scheduleSeconds = Math.floor(Date.now() / 1000) + Math.max(1, delaySeconds);
    const body = Buffer.from(JSON.stringify({ alertId })).toString("base64");
    const httpRequest = {
      httpMethod: "POST",
      url: this.targetUrl,
      headers: { "Content-Type": "application/json" },
      body,
    };
    if (this.serviceAccountEmail) {
      httpRequest.oidcToken = { serviceAccountEmail: this.serviceAccountEmail };
    }
    const task = {
      httpRequest,
      scheduleTime: { seconds: scheduleSeconds },
    };
    try {
      await this.tasksClient.createTask({ parent: this.queuePath, task });
      return true;
    } catch (error) {
      this.logger.warn(`RepushScheduler: createTask falló: ${error.message}`);
      return false;
    }
  }
}

/**
 * Crea un scheduler tomando configuración de env. Lo ejecuta solo el
 * entorno desplegado; en tests pasamos un scheduler fake.
 */
export function defaultScheduler({ tasksClient, logger = console } = {}) {
  const projectId = process.env.GCLOUD_PROJECT ?? process.env.GCP_PROJECT ?? "";
  const location = process.env.AVISAZBEE_TASKS_LOCATION ?? "europe-west1";
  const queueId = process.env.AVISAZBEE_REPUSH_QUEUE ?? "avisazbee-repush";
  const region = process.env.AVISAZBEE_FUNCTION_REGION ?? location;
  const targetUrl = process.env.AVISAZBEE_REPUSH_URL
    ?? (projectId ? `https://${region}-${projectId}.cloudfunctions.net/repushAlert` : "");
  const serviceAccountEmail = process.env.AVISAZBEE_TASKS_SA ?? "";
  return new RepushScheduler({
    tasksClient,
    projectId,
    location,
    queueId,
    targetUrl,
    serviceAccountEmail,
    logger,
  });
}
