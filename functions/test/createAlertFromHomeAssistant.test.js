import { describe, expect, it, vi } from "vitest";
import { createHandler } from "../src/createAlertFromHomeAssistant.js";

function fakeDoc(data) {
  return {
    exists: data !== undefined,
    data: () => data,
  };
}

function fakeCollection(docs = {}) {
  return {
    doc: (id) => {
      if (!docs[id]) {
        const stored = { data: undefined, id };
        docs[id] = {
          id,
          get: async () => fakeDoc(stored.data),
          set: async (value) => { stored.data = value; },
          collection: () => fakeCollection({}),
        };
      }
      return docs[id];
    },
    where: () => fakeCollection(docs),
    get: async () => ({
      docs: Object.values(docs)
        .filter((d) => d.id) // skip pseudo-collection markers
        .map((d) => ({ id: d.id, data: () => d.data ?? {} })),
    }),
  };
}

function fakeRes() {
  return {
    statusCode: 200,
    body: null,
    status(code) { this.statusCode = code; return this; },
    json(payload) { this.body = payload; return this; },
  };
}

describe("createAlertFromHomeAssistant", () => {
  it("rejects non-POST methods", async () => {
    const db = { collection: vi.fn() };
    const messaging = { sendEachForMulticast: vi.fn() };
    const handler = createHandler({ db, messaging, bcryptCompare: vi.fn() });
    const res = fakeRes();
    await handler({ method: "GET", body: {} }, res);
    expect(res.statusCode).toBe(405);
  });

  it("rejects payload without buttonId or secret", async () => {
    const db = { collection: vi.fn() };
    const messaging = { sendEachForMulticast: vi.fn() };
    const handler = createHandler({ db, messaging, bcryptCompare: vi.fn() });
    const res = fakeRes();
    await handler({ method: "POST", body: { secret: "x" } }, res);
    expect(res.statusCode).toBe(400);
    expect(res.body).toEqual({ error: "invalid_payload" });
  });

  it("rejects unknown buttons with 404", async () => {
    const buttons = { "E4:56:AC:FF:FE:5E:CD:AA": { get: async () => fakeDoc(undefined) } };
    const db = {
      collection: (name) => {
        if (name === "buttons") {
          return { doc: (id) => buttons[id] ?? { get: async () => fakeDoc(undefined) } };
        }
        return { doc: () => ({ get: async () => fakeDoc(undefined) }) };
      },
    };
    const handler = createHandler({
      db,
      messaging: { sendEachForMulticast: vi.fn() },
      bcryptCompare: vi.fn(),
    });
    const res = fakeRes();
    await handler({
      method: "POST",
      body: { buttonId: "E4:56:AC:FF:FE:5E:CD:AA", secret: "plain" },
    }, res);
    expect(res.statusCode).toBe(404);
  });

  it("rejects disabled buttons with 403", async () => {
    const db = {
      collection: (name) => {
        if (name === "buttons") {
          return {
            doc: () => ({
              get: async () => fakeDoc({ enabled: false, hashedSecret: "h", channelId: "ch-1" }),
            }),
          };
        }
        return { doc: () => ({ get: async () => fakeDoc(undefined) }) };
      },
    };
    const handler = createHandler({
      db,
      messaging: { sendEachForMulticast: vi.fn() },
      bcryptCompare: vi.fn(),
    });
    const res = fakeRes();
    await handler({
      method: "POST",
      body: { buttonId: "E4:56:AC:FF:FE:5E:CD:AA", secret: "plain" },
    }, res);
    expect(res.statusCode).toBe(403);
    expect(res.body).toEqual({ error: "disabled" });
  });

  it("rejects wrong secret with 403", async () => {
    const db = {
      collection: (name) => {
        if (name === "buttons") {
          return {
            doc: () => ({
              get: async () => fakeDoc({ enabled: true, hashedSecret: "h", channelId: "ch-1", name: "B" }),
            }),
          };
        }
        return { doc: () => ({ get: async () => fakeDoc(undefined) }) };
      },
    };
    const handler = createHandler({
      db,
      messaging: { sendEachForMulticast: vi.fn() },
      bcryptCompare: async () => false,
    });
    const res = fakeRes();
    await handler({
      method: "POST",
      body: { buttonId: "E4:56:AC:FF:FE:5E:CD:AA", secret: "wrong" },
    }, res);
    expect(res.statusCode).toBe(403);
    expect(res.body).toEqual({ error: "forbidden" });
  });

  it("writes the alert and multicasts to enabled devices", async () => {
    const buttonDoc = {
      get: async () => fakeDoc({
        enabled: true,
        hashedSecret: "h",
        channelId: "ch-1",
        name: "Botón cocina",
        repeatIntervalSeconds: 30,
      }),
    };
    const channelDoc = { get: async () => fakeDoc({ name: "Casa Manu" }) };
    const alertWrites = [];
    let alertId = 0;
    const alertCollection = {
      doc: () => ({
        id: `alert-${++alertId}`,
        set: async (value) => alertWrites.push(value),
      }),
    };
    const memberDocs = [
      { id: "uid-1", data: () => ({ notificationsEnabled: true }) },
      { id: "uid-2", data: () => ({ notificationsEnabled: true }) },
    ];
    const deviceDocs = [
      { id: "dev-1", data: () => ({ fcmToken: "t-1" }) },
      { id: "dev-2", data: () => ({ fcmToken: "t-2" }) },
    ];
    const db = {
      collection: (name) => {
        switch (name) {
          case "buttons":
            return { doc: () => buttonDoc };
          case "channels":
            return {
              doc: () => ({
                get: channelDoc.get,
                collection: () => ({
                  get: async () => ({ docs: memberDocs }),
                }),
              }),
            };
          case "alerts":
            return alertCollection;
          case "devices":
            return {
              where: () => ({
                where: () => ({
                  get: async () => ({ docs: deviceDocs }),
                }),
              }),
            };
          default:
            return { doc: () => ({ get: async () => fakeDoc(undefined) }) };
        }
      },
    };
    const sendEachForMulticast = vi.fn(async ({ tokens }) => ({ successCount: tokens.length }));
    const handler = createHandler({
      db,
      messaging: { sendEachForMulticast },
      bcryptCompare: async () => true,
    });
    const res = fakeRes();
    await handler({
      method: "POST",
      body: {
        buttonId: "e4:56:ac:ff:fe:5e:cd:aa",
        secret: "plain",
        alertType: "short_press",
        message: "Botón pulsado",
      },
    }, res);
    expect(res.statusCode).toBe(200);
    expect(res.body.delivered).toBe(2);
    expect(alertWrites).toHaveLength(1);
    expect(alertWrites[0]).toMatchObject({
      channelId: "ch-1",
      buttonId: "E4:56:AC:FF:FE:5E:CD:AA",
      buttonName: "Botón cocina",
      status: "pending",
      source: "home_assistant",
      type: "short_press",
    });
    expect(sendEachForMulticast).toHaveBeenCalledOnce();
    const call = sendEachForMulticast.mock.calls[0][0];
    expect(new Set(call.tokens)).toEqual(new Set(["t-1", "t-2"]));
    expect(call.data.channelName).toBe("Casa Manu");
  });
});
