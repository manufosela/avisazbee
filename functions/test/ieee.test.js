import { describe, expect, it } from "vitest";
import { normaliseIeee } from "../src/ieee.js";

describe("normaliseIeee", () => {
  it("uppercases and replaces hyphens with colons", () => {
    expect(normaliseIeee("e4:56:ac:ff:fe:5e:cd:aa")).toBe("E4:56:AC:FF:FE:5E:CD:AA");
    expect(normaliseIeee("e4-56-ac-ff-fe-5e-cd-aa")).toBe("E4:56:AC:FF:FE:5E:CD:AA");
  });

  it("rejects malformed input", () => {
    expect(normaliseIeee("not-a-mac")).toBeNull();
    expect(normaliseIeee("AA:BB:CC")).toBeNull();
    expect(normaliseIeee(null)).toBeNull();
    expect(normaliseIeee(undefined)).toBeNull();
  });
});
