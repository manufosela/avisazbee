import { describe, expect, it, vi } from "vitest";
import { RepushScheduler } from "../src/repushScheduler.js";

describe("RepushScheduler", () => {
  it("returns false when tasksClient is missing", async () => {
    const sched = new RepushScheduler({ projectId: "p", queueId: "q", location: "l" });
    expect(await sched.schedule("a", 30)).toBe(false);
  });

  it("returns false when projectId is missing", async () => {
    const sched = new RepushScheduler({
      tasksClient: { createTask: vi.fn() },
      queueId: "q",
    });
    expect(await sched.schedule("a", 30)).toBe(false);
  });

  it("builds the queue path and a task with base64 body and scheduleTime", async () => {
    const createTask = vi.fn(async () => [{}]);
    const sched = new RepushScheduler({
      tasksClient: { createTask },
      projectId: "proj-1",
      location: "europe-west1",
      queueId: "repush-q",
      targetUrl: "https://example.com/repushAlert",
      serviceAccountEmail: "sa@proj-1.iam.gserviceaccount.com",
    });
    const before = Math.floor(Date.now() / 1000);
    const ok = await sched.schedule("alert-1", 30);
    const after = Math.floor(Date.now() / 1000) + 30;
    expect(ok).toBe(true);
    expect(createTask).toHaveBeenCalledOnce();
    const call = createTask.mock.calls[0][0];
    expect(call.parent).toBe("projects/proj-1/locations/europe-west1/queues/repush-q");
    expect(call.task.httpRequest.url).toBe("https://example.com/repushAlert");
    expect(call.task.httpRequest.oidcToken.serviceAccountEmail).toBe(
      "sa@proj-1.iam.gserviceaccount.com",
    );
    const decoded = JSON.parse(Buffer.from(call.task.httpRequest.body, "base64").toString());
    expect(decoded).toEqual({ alertId: "alert-1" });
    expect(call.task.scheduleTime.seconds).toBeGreaterThanOrEqual(before + 29);
    expect(call.task.scheduleTime.seconds).toBeLessThanOrEqual(after + 1);
  });

  it("returns false when createTask throws", async () => {
    const createTask = vi.fn(async () => { throw new Error("nope"); });
    const sched = new RepushScheduler({
      tasksClient: { createTask },
      projectId: "p",
      location: "europe-west1",
      queueId: "q",
      targetUrl: "https://example.com/r",
      logger: { warn: () => {} },
    });
    expect(await sched.schedule("a", 30)).toBe(false);
  });
});
