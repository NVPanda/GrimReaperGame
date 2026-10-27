import { describe, expect, it } from "vitest";
import { resolveApiBaseUrl } from "../src/api-config.js";

describe("configuração de origem da API", () => {
  it("uses the page origin when no backend override is configured", () => {
    expect(resolveApiBaseUrl("", "https://localhost")).toBe("https://localhost");
  });

  it("normalizes a configured Ktor origin", () => {
    expect(resolveApiBaseUrl("http://10.0.2.2:3001/", "https://localhost")).toBe("http://10.0.2.2:3001");
  });

  it("rejects API URLs with paths or unsupported schemes", () => {
    expect(() => resolveApiBaseUrl("https://game.example/api", "https://localhost")).toThrow(/origem do backend/);
    expect(() => resolveApiBaseUrl("file:///backend", "https://localhost")).toThrow(/HTTP ou HTTPS/);
  });
});
