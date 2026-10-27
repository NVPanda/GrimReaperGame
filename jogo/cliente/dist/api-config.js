export function resolveApiBaseUrl(configuredUrl, pageOrigin) {
    const configured = configuredUrl?.trim();
    if (!configured)
        return pageOrigin;
    const url = new URL(configured, pageOrigin);
    if (url.protocol !== "http:" && url.protocol !== "https:") {
        throw new Error("GRIMREAP_API_URL deve usar HTTP ou HTTPS.");
    }
    if (url.username || url.password || url.search || url.hash || url.pathname !== "/") {
        throw new Error("GRIMREAP_API_URL deve ser a origem do backend, sem caminho, credenciais ou parâmetros.");
    }
    return url.origin;
}
const apiConfigurada = typeof window === "undefined" ? undefined : window.GRIMREAP_CONFIG?.apiBaseUrl;
const origemDaPagina = typeof window === "undefined" ? "http://localhost" : window.location.origin;
export const API_BASE_URL = resolveApiBaseUrl(apiConfigurada, origemDaPagina);
