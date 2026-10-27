import { readdirSync, readFileSync, writeFileSync } from "node:fs";
import { resolve } from "node:path";
import { fileURLToPath } from "node:url";

const mobileDir = resolve(fileURLToPath(new URL("..", import.meta.url)));
const configuredUrl = process.env.GRIMREAP_API_URL?.trim();
if (!configuredUrl) {
  throw new Error("Defina GRIMREAP_API_URL com a origem HTTP(S) do servidor Kotlin/Ktor antes de sincronizar o Android.");
}

let apiOrigin;
try {
  const url = new URL(configuredUrl);
  if (!["http:", "https:"].includes(url.protocol) || url.username || url.password
    || url.pathname !== "/" || url.search || url.hash) {
    throw new Error("Use apenas a origem HTTP(S), sem credenciais, caminho ou parâmetros.");
  }
  apiOrigin = url.origin;
} catch (error) {
  throw new Error(`GRIMREAP_API_URL inválida: ${error instanceof Error ? error.message : String(error)}`);
}

const runtimeConfig = `window.GRIMREAP_CONFIG = { apiBaseUrl: ${JSON.stringify(apiOrigin)} };\n`;
const output = resolve(mobileDir, "../cliente/dist/runtime-config.js");
writeFileSync(output, runtimeConfig, "utf8");
const capacitorConfigPath = resolve(mobileDir, "capacitor.config.json");
const capacitorConfig = JSON.parse(readFileSync(capacitorConfigPath, "utf8"));
capacitorConfig.server = {
  ...capacitorConfig.server,
  cleartext: new URL(apiOrigin).protocol === "http:",
};
writeFileSync(capacitorConfigPath, `${JSON.stringify(capacitorConfig, null, 2)}\n`, "utf8");
const socketOrigin = new URL(apiOrigin);
socketOrigin.protocol = socketOrigin.protocol === "https:" ? "wss:" : "ws:";
const connectSources = `connect-src 'self' ${apiOrigin} ${socketOrigin.origin};`;
const webDir = resolve(mobileDir, "../cliente/dist");
for (const filename of readdirSync(webDir).filter((name) => name.endsWith(".html"))) {
  const htmlPath = resolve(webDir, filename);
  const html = readFileSync(htmlPath, "utf8");
  const secureHtml = html.replace(/connect-src\s+[^;]+;/, connectSources);
  if (secureHtml === html) {
    throw new Error(`Não encontrei a diretiva connect-src para configurar em ${filename}.`);
  }
  writeFileSync(htmlPath, secureHtml, "utf8");
}
console.log(`Backend mobile configurado: ${apiOrigin}; cleartext ${capacitorConfig.server.cleartext ? "habilitado para HTTP local" : "desabilitado para HTTPS"}; CSP restrita à origem de API e WebSocket.`);
