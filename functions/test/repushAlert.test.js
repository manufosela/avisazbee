import { describe, expect, it, vi } from "vitest";
import { createRepushHandler } from "../src/repushAlert.js";

function fakeDoc(data) {
  return { exists: data !== undefined, data: () => data };
}

function fakeRes() {
  return {
    statusCode: 200,
    body: null,
    status(code) { this.statusCode = code; return this; },
    json(payload) { this.body = payload; return this; },
  };
}

describe("repushAlert", () => {
  it("rejects non-POST", async () => {
    const handler = createRepushHandler({ db: {}, messaging: {} });
    const res = fakeRes();
    await handler({ method: "GET", body: {} }, res);
    expect(res.statusCode).toBe(405);
  });

  it("rejects payload without alertId", async () => {
    const handler = createRepushHandler({ db: {}, messaging: {} });
    const res = fakeRes();
    await handler({ method: "POST", body: {} }, res);
    expect(res.statusCode).toBe(400);
  });

  it("returns gone when the alert disappeared", async () => {
    const db = {
      collection: () => ({ doc: () => ({ get: async () => fakeDoc(undefined) }) }),
    };
    const handler = createRepushHandler({ db, messaging: {} });
    const res = fakeRes();
    await handler({ method: "POST", body: { alertId: "x" } }, res);
    expect(res.body).toEqual({ status: "gone" });
  });

  it("no-ops and does not re-schedule when alert is claimed", async () => {
    const db = {
      collection: () => ({
        doc: () => ({
          get: async () => fakeDoc({ status: "claimed", channelId: "ch-1" }),
        }),
      }),
    };
    const schedule = vi.fn();
    const handler = createRepushHandler({
      db,
      messaging: { sendEachForMulticast: vi.fn() },
      scheduler: { schedule },
    });
    const res = fakeRes();
    await handler({ method: "POST", body: { alertId: "x" } }, res);
    expect(res.body.status).toBe("claimed");
    expect(schedule).not.toHaveBeenCalled();
  });

  it("re-multicasts, increments repeatCount and re-schedules when pending", async () => {
    const updates = [];
    const alertData = {
      status: "pending",
      channelId: "ch-1",
      title: "Aviso",
      message: "Pulsado",
      repeatIntervalSeconds: 30,
      repeatCount: 2,
    };
    const memberDocs = [
      { id: "uid-1", data: () => ({ notificationsEnabled: true }) },
    ];
    const deviceDocs = [
      { data: () => ({ fcmToken: "t-1" }) },
      { data: () => ({ fcmToken: "t-2" }) },
    ];
    const db = {
      collection: (name) => {
        switch (name) {
          case "alerts":
            return {
              doc: () => ({
                get: async () => fakeDoc(alertData),
                update: async (patch) => updates.push(patch),
              }),
            };
          case "channels":
            return {
              doc: () => ({
                get: async () => fakeDoc({ name: "Casa Manu" }),
                collection: () => ({ get: async () => ({ docs: memberDocs }) }),
              }),
            };
          case "devices":
            return {
              where: () => ({ where: () => ({ get: async () => ({ docs: deviceDocs }) }) }),
            };
          default:
            return { doc: () => ({ get: async () => fakeDoc(undefined) }) };
        }
      },
    };
    const sendEachForMulticast = vi.fn(async ({ tokens }) => ({ successCount: tokens.length }));
    const schedule = vi.fn();
    const handler = createRepushHandler({
      db,
      messaging: { sendEachForMulticast },
      scheduler: { schedule },
    });
    const res = fakeRes();
    await handler({ method: "POST", body: { alertId: "alert-1" } }, res);
    expect(res.body.status).toBe("pending");
    expect(res.body.delivered).toBe(2);
    expect(sendEachForMulticast).toHaveBeenCalledOnce();
    expect(updates[0].repeatCount).toBe(3);
    expect(updates[0].lastRepeatAt).toBeInstanceOf(Date);
    expect(schedule).toHaveBeenCalledWith("alert-1", 30);
  });
});
