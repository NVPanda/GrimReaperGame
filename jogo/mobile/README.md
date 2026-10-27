# Aplicativo Android GrimReap (Capacitor)

O pacote mobile documentado e validado atualmente é Android; ainda não existe projeto iOS configurado. A geração/assinatura iOS exige macOS e Xcode, além de testes em simulador ou aparelho Apple.

O app Android empacota o build de `../cliente` e usa o backend autoritativo Kotlin/Ktor pela rede. A presença, o combate, a conta e o WebSocket multiplayer continuam no Ktor; a ponte Android fornece somente métricas do dispositivo e eventos de pausa/retorno do app.

## Pré-requisitos

- Node.js 22 LTS (mínimo suportado pelo lockfile: 20.19 ou 22.12) e npm 10+
- JDK 17
- Android Studio e Android SDK Platform 34 + Build Tools 34.0.0
- Emulador Android ou dispositivo com depuração USB

## Configurar backend e cliente

1. Inicie o backend Ktor na porta 3001. Para Android Emulator, o endereço do host é `10.0.2.2`; para um dispositivo físico, use o IP local do computador e permita o acesso pela firewall. Configure `ORIGENS_PERMITIDAS=localhost` no backend (a origem do WebView Android é `https://localhost`).
2. Crie o build web e configure a origem pública/acessível do backend. No Android Emulator:

```powershell
Set-Location ..\cliente
npm ci
npm test
npm run build
Set-Location ..\mobile
npm ci
$env:GRIMREAP_API_URL = "http://10.0.2.2:3001"
```

Em dispositivo físico, substitua o endereço por, por exemplo, `http://192.168.1.20:3001`. Para produção, use a origem HTTPS pública. `GRIMREAP_API_URL` aceita apenas a origem, sem caminho, credenciais, query ou fragmento.

O script grava a origem em `cliente/dist/runtime-config.js` durante cada operação de sincronização e restringe a diretiva `connect-src` da CSP do pacote à origem HTTP(S) configurada e à origem WebSocket correspondente. Também deriva `server.cleartext`: HTTP local habilita tráfego sem TLS para debug; HTTPS desabilita-o para builds seguros. Os módulos de API, autenticação, movimento e WebSocket usam a origem configurada; os JSON e assets continuam carregados do pacote local. Não inclua segredos nesse arquivo.

## Gerar e sincronizar Android

Na primeira execução:

```powershell
npm run android:add
```

Depois de mudanças no cliente ou no plugin:

```powershell
npm run android:sync
```

Ambos os scripts recompilam o cliente, exigem `GRIMREAP_API_URL`, configuram o backend e sincronizam os arquivos e plugins nativos. Use a combinação validada de JDK 17 e API 34 para compilar o APK:

```powershell
Set-Location android
.\gradlew.bat assembleDebug
```

O APK de debug fica em `app\build\outputs\apk\debug\app-debug.apk`. Também é possível abrir o projeto com `npm run android:open`.

A última sincronização e compilação de debug foi validada com Temurin JDK 17 e Android SDK API 34 (`:app:assembleDebug`, `BUILD SUCCESSFUL`). Também compilou `:app:assembleRelease` com sucesso; o APK release é **não assinado** e ainda não está pronto para distribuição. Os APKs foram conferidos como `com.grimreap.game`, versão `1.0`, rótulo `GrimReap`. Ainda é necessário instalar/testar em emulador ou aparelho e assinar o release; não havia dispositivo disponível durante esta validação.

Para um pacote de produção, configure uma origem HTTPS real antes da sincronização e compile a variante release:

```powershell
$env:GRIMREAP_API_URL = "https://jogo.seudominio.com"
npm run android:sync
Set-Location android
.\gradlew.bat :app:assembleRelease
```

Esse build ainda precisa de assinatura release antes da distribuição. A sincronização HTTPS deixa `server.cleartext` desativado; não reutilize a configuração HTTP de debug.

## Ponte nativa

`android/app/src/main/java/com/grimreap/game/GameBridgePlugin.kt` registra o plugin `GameBridge` na `MainActivity`. `getRuntimeInfo()` fornece plataforma, fabricante/modelo, versão Android, dimensões físicas e densidade da tela. O evento `appStateChange` informa `isActive=true` ao retomar e `false` ao pausar. O cliente exibe a densidade/resolução e o estado nativo no cabeçalho da tela do mundo; em navegador web a ponte não está presente e o jogo continua usando o comportamento web normal.

O plugin não acessa o Go local e não transfere estado do jogo: toda regra de jogo e multiplayer permanece no backend Kotlin/Ktor.

## Segurança e distribuição

- O script de configuração habilita `cleartext` apenas quando `GRIMREAP_API_URL` usa HTTP, para debug em emulador/LAN. Para release, configure HTTPS antes de sincronizar e confirme `server.cleartext: false` em `capacitor.config.json`; não publique um pacote configurado para HTTP.
- O backend deve ter segredos reais e uma lista `ORIGENS_PERMITIDAS` adequada; não abra CORS para qualquer origem.
- O APK de debug usa chave de teste. Para distribuir, gere um pacote release assinado.
- Para acesso pela internet, use um domínio com certificado público e HTTPS/WSS, por exemplo atrás do Caddy. Mantenha Ktor na interface loopback e não exponha diretamente a porta 3001; veja o guia de proxy TLS no README principal.
- Checkout do Mercado Pago requer URL HTTPS pública e configuração de webhook acessível.
- OAuth social e retorno de pagamento devem ser validados no fluxo Android após configurar domínios/callbacks dos provedores.
