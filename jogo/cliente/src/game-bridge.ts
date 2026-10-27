interface RuntimeInfo {
  platform: "android";
  manufacturer: string;
  model: string;
  osVersion: string;
  sdkVersion: number;
  screenWidthPx: number;
  screenHeightPx: number;
  density: number;
  densityDpi: number;
}

interface AppStateChange {
  isActive: boolean;
}

interface NativeListenerHandle {
  remove(): Promise<void>;
}

interface GameBridgePlugin {
  getRuntimeInfo(): Promise<RuntimeInfo>;
  addListener(
    eventName: "appStateChange",
    listener: (state: AppStateChange) => void,
  ): Promise<NativeListenerHandle>;
}

declare global {
  interface Window {
    Capacitor?: {
      Plugins?: {
        GameBridge?: GameBridgePlugin;
      };
    };
  }
}

export async function iniciarPonteNativa(
  aoReceberInfo: (info: RuntimeInfo) => void,
  aoMudarEstado: (estado: AppStateChange) => void,
): Promise<boolean> {
  const ponte = typeof window === "undefined" ? undefined : window.Capacitor?.Plugins?.GameBridge;
  if (!ponte) return false;

  const info = await ponte.getRuntimeInfo();
  await ponte.addListener("appStateChange", aoMudarEstado);
  aoReceberInfo(info);
  return true;
}
