import { afterEach, describe, expect, it, vi } from "vitest";
import { iniciarPonteNativa } from "../src/game-bridge.js";

describe("ponte nativa Capacitor", () => {
  afterEach(() => {
    Reflect.deleteProperty(globalThis, "window");
    vi.restoreAllMocks();
  });

  it("does nothing in a regular browser without the native plugin", async () => {
    const receberInfo = vi.fn();
    const mudarEstado = vi.fn();

    await expect(iniciarPonteNativa(receberInfo, mudarEstado)).resolves.toBe(false);

    expect(receberInfo).not.toHaveBeenCalled();
    expect(mudarEstado).not.toHaveBeenCalled();
  });

  it("loads native runtime info and subscribes to app lifecycle changes", async () => {
    const runtime = {
      platform: "android" as const,
      manufacturer: "Example",
      model: "Device",
      osVersion: "15",
      sdkVersion: 35,
      screenWidthPx: 1080,
      screenHeightPx: 2400,
      density: 3,
      densityDpi: 480,
    };
    const receberInfo = vi.fn();
    const mudarEstado = vi.fn();
    const addListener = vi.fn().mockResolvedValue({ remove: vi.fn() });
    Object.defineProperty(globalThis, "window", {
      configurable: true,
      value: {
        Capacitor: {
          Plugins: {
            GameBridge: {
              getRuntimeInfo: vi.fn().mockResolvedValue(runtime),
              addListener,
            },
          },
        },
      },
    });

    await expect(iniciarPonteNativa(receberInfo, mudarEstado)).resolves.toBe(true);

    expect(receberInfo).toHaveBeenCalledWith(runtime);
    expect(addListener).toHaveBeenCalledWith("appStateChange", mudarEstado);
  });
});
