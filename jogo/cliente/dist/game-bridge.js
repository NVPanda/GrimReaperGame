export async function iniciarPonteNativa(aoReceberInfo, aoMudarEstado) {
    const ponte = typeof window === "undefined" ? undefined : window.Capacitor?.Plugins?.GameBridge;
    if (!ponte)
        return false;
    const info = await ponte.getRuntimeInfo();
    await ponte.addListener("appStateChange", aoMudarEstado);
    aoReceberInfo(info);
    return true;
}
