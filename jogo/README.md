# GrimReap — RPG 2D multiplayer com mundo e arte originais

**Reino:** GrimReap. Os seis continentes, cidades, campos e masmorras pertencem ao Reino de GrimReap.

Projeto organizado em três camadas de trabalho:

| Pasta | Conteúdo | Ordem de leitura |
|---|---|---|
| `docs/sdd/` | Especificações comportamentais (Spec-Driven Development). Cada cenário tem ID (ex.: `LOJA-01`) que aparece nos testes. | 1º |
| `docs/ddd/` | Linguagem ubíqua, contextos delimitados e mapa de contexto (Domain-Driven Design). | 2º |
| `docs/adr/` | Decisões de arquitetura com motivo e consequências. | 3º |
| `backend/` | Servidor Kotlin + Ktor. Código organizado por contexto e testes (TDD). | 4º |
| `cliente/` | Cliente HTML5 + TypeScript. Não é barreira de segurança. | 5º |
| `dados/` | Conteúdo do jogo em JSON: continentes, classes, monstros, itens, drops, mapas, NPCs, quests, economia e tilemaps. | 6º |
| `assets/` | Sprites e tiles placeholder com `manifest.json`, além de assets pixel art originais em `bakin_pixelart/`. | 7º |
| `ferramentas/` | `gerar_conteudo.py` gera o conteúdo do jogo; `gerar_assets_bakin_local.py` cria assets locais sem API externa. | 8º |
| `docs/balanceamento.md` | Fórmulas e valores de balanceamento (classes, monstros, drops, economia). | 9º |
| `mobile/` | Casca Capacitor para gerar APK. | 10º |

## Fluxo de trabalho

1. Alterar ou criar uma spec em `docs/sdd/` com cenários novos (ID único).
2. Escrever o teste correspondente, citando o ID do cenário no nome. Ele deve falhar.
3. Implementar o mínimo para o teste passar, dentro do contexto certo em `backend/src/main/kotlin/jogo/`.
4. Atualizar `docs/ddd/` se a linguagem ou os contextos mudarem.
5. Registrar em `docs/adr/` qualquer decisão que mude arquitetura, segurança ou regra de negócio.

## Pré-requisitos e validação local

- Backend: JDK 17 ou superior. O repositório inclui Gradle Wrapper 8.10.2; não é necessário instalar Gradle globalmente. O código é compilado para JVM 17.
- Cliente e mobile: Node.js 22 LTS e npm 10 ou superior. `.nvmrc` e `.node-version` apontam para a linha 22; as dependências requerem Node `^20.19.0 || >=22.12.0`, declarado nos dois `package.json`.
- Geração local de conteúdo/assets: Python 3.14 estável e Pillow 13.0.0. Python não é necessário para executar o jogo ou o backend.

No Windows, execute na raiz para reproduzir testes e build:

```powershell
.\run-tests.ps1
```

Comandos equivalentes por componente:

```powershell
Set-Location backend
.\gradlew.bat test
Set-Location ..\cliente
npm ci
npm test
npm run build
```

## Executar em produção

No Windows, defina um segredo JWT forte (mínimo de 32 bytes) e execute na raiz:

```powershell
$bytes = New-Object byte[] 32
$rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
$rng.GetBytes($bytes)
$env:JWT_SEGREDO = [Convert]::ToBase64String($bytes)
$rng.Dispose()
$env:URL_PUBLICA = "https://seu-dominio.example"
$env:MP_ACCESS_TOKEN = "seu-token-do-mercado-pago"
.\run-production.ps1
```

O script instala as dependências pelo lockfile, roda os testes e builds do cliente e backend, e então inicia o Ktor na porta `3001`. O Ktor serve o cliente e a API na mesma origem. `MP_ACCESS_TOKEN` só é necessário para checkout; `URL_PUBLICA` deve ser o endereço HTTPS público para webhooks. Coloque o servidor atrás de um proxy TLS (por exemplo, Caddy ou Nginx) que encaminhe para a porta 3001. O processo permanece em primeiro plano; use `Ctrl+C` para pará-lo. Não exponha o servidor publicamente sem TLS e sem configurar os segredos reais.

## Ambiente de desenvolvimento

No Windows, execute na raiz:

```powershell
.\run-development.ps1
```

O script instala as dependências com `npm ci`, compila o cliente e inicia o backend na porta `3001`, servindo a API e os arquivos do cliente em `http://localhost:3001`. Gera um segredo JWT temporário, limita o servidor a `127.0.0.1` e desativa checkout do Mercado Pago. Para mudar a porta, use `.\run-development.ps1 -Port 3002`. O processo fica em primeiro plano; use `Ctrl+C` para pará-lo. Após alterar o cliente, reinicie o script para gerar novamente os arquivos estáticos.

Para fazer o ciclo completo e abrir o navegador automaticamente:

```powershell
.\run-local.ps1
```

Esse runner executa primeiro `run-tests.ps1`; se os testes e o build passarem, inicia `run-development.ps1` em um console separado, espera a API responder e abre `http://localhost:3001` no navegador padrão. O servidor continua ativo no console separado; feche essa janela para pará-lo. Para usar outra porta, execute `.\run-local.ps1 -Port 3002`.

### HTTPS e WSS local com acesso remoto

Use Caddy como reverse proxy TLS: ele obtém/renova certificados e encaminha WebSocket automaticamente, sem configuração WSS separada. **Mantenha o Ktor em `127.0.0.1:3001` e nunca exponha a porta 3001 no roteador/firewall.**

Para acesso somente local/LAN, prefira uma VPN privada ou CA local sem encaminhamento público de portas; em Android, a CA precisa ser confiada pelo dispositivo/WebView. Um certificado autoassinado não confiado causa erro TLS e não é uma configuração segura. Se for encaminhar uma porta para acesso externo:

1. Use um domínio sob seu controle (por exemplo, `jogo.seudominio.com`) apontando para a rede/servidor. Encaminhe somente a porta 443 para a máquina; permita a porta 80 apenas se o método ACME escolhido precisar do desafio HTTP. Não exponha a 3001.
2. Configure o Caddyfile:

```caddyfile
jogo.seudominio.com {
    encode zstd gzip
    reverse_proxy 127.0.0.1:3001
}
```

3. Defina a origem HTTPS e a lista CORS. Inclua `localhost` para o WebView do Capacitor:

```powershell
$env:URL_PUBLICA = "https://jogo.seudominio.com"
$env:ORIGENS_PERMITIDAS = "localhost,jogo.seudominio.com"
```

4. Para qualquer acesso externo, execute `run-production.ps1` com segredos reais e banco persistente — nunca exponha o runner de desenvolvimento. Caddy encaminha HTTPS/WSS para `127.0.0.1:3001`. Configure os callbacks OAuth e webhooks com a mesma origem HTTPS. Para o app Android de produção, sincronize com a mesma URL:

```powershell
$env:GRIMREAP_API_URL = "https://jogo.seudominio.com"
npm run android:sync --prefix mobile
```

O sincronizador restringe a CSP às origens configuradas e desabilita `cleartext` para HTTPS. TLS protege o tráfego, mas não substitui autenticação, atualizações, backups nem regras de firewall.

O runner de desenvolvimento aceita `-PublicUrl` e `-AllowedOrigins` para testes atrás de proxy, mas deve permanecer acessível somente localmente/LAN/VPN; não o publique diretamente na internet.

## Ambiente de testes

Execute na raiz do projeto:

```powershell
.\run-tests.ps1
```

O script executa `backend\gradlew.bat test`, instala dependências reproduzíveis com `npm ci` dentro de `cliente/`, roda `npm test` e valida `npm run build`. O wrapper fixa Gradle 8.10.2 e baixa a distribuição automaticamente. O script encerra com erro no primeiro passo que falhar e não inicia servidor.

## Contas, personagens e login social

O cadastro/login por email usa senha com Argon2id e tem tela própria em `/`. Após entrar, o jogador segue para `/personagens.html`, escolhe um personagem e, se for a primeira vez, define o continente e a cidade natal em `/nascimento.html` antes de abrir `/jogo.html`. As telas são independentes; acessar uma etapa protegida sem a sessão ou personagem necessário retorna para a etapa anterior.

As contas, personagens, identidades sociais e sessões ficam no SQLite `backend/data/jogo.sqlite` por padrão. Para apagar os dados locais, pare o servidor e remova esse arquivo. Cada conta pode ter até cinco personagens, mas apenas um login/sessão por conta e um personagem ativo nessa sessão. Entrar novamente em outro dispositivo revoga a sessão anterior.

O continente/cidade natal é definitivo por personagem. O NPC Save em cidades visitadas atualiza o ponto de respawn sem alterar o local natal; ao voltar ao jogo, o personagem nasce na última cidade salva. Use o NPC Teleporte nas cidades para viajar entre as regiões: 100 MC no mesmo continente, 1.000 MC entre continentes, 10.000 MC para masmorras e 100.000 MC para o Trono do Abismo (classificado como dungeon SS). Cada personagem inicia com 10 MC; quests concluídas e venda de itens permitidos ao mercador creditam MC. As viagens continuam sujeitas ao saldo disponível e às tarifas definidas pelo servidor.

O login social aceita Google, Discord, Facebook e Steam. Registre as URLs de callback listadas em [.env.example](</F:/Projetos/Projetos Typescript/Projeto de jogo Web e Mobile/jogo_projeto/jogo/.env.example>) no painel de cada provedor e configure as variáveis correspondentes no ambiente do servidor. Por exemplo, no PowerShell:

```powershell
$env:GOOGLE_CLIENT_ID = "..."
$env:GOOGLE_CLIENT_SECRET = "..."
$env:DISCORD_CLIENT_ID = "..."
$env:DISCORD_CLIENT_SECRET = "..."
$env:FACEBOOK_CLIENT_ID = "..."
$env:FACEBOOK_CLIENT_SECRET = "..."
```

Somente os provedores com credenciais configuradas ficam disponíveis; Steam usa OpenID e requer domínio/realm autorizado no Steamworks. Para teste local, configure `URL_PUBLICA=http://localhost:3001` e cadastre os callbacks com essa origem. Em produção, configure a URL pública HTTPS. Não commite segredos. Recuperação de senha e verificação de e-mail ainda dependem de integração de envio de email.

## Como gerar o conteúdo e os assets

O gerador foi fixado para Python 3.14 estável e Pillow 13.0.0. Não use versões alfa/beta: elas podem ser incompatíveis com as extensões binárias do Pillow. `uv run --python 3.14` seleciona a versão estável disponível.

No Windows com `uv`:

```bash
uv venv --python 3.14 .venv
uv pip install --python .venv\Scripts\python.exe -r ferramentas/requirements.txt
.venv\Scripts\python ferramentas/gerar_conteudo.py
```

Também é possível criar o ambiente com `py -3.14 -m venv .venv` (Windows) ou `python3.14 -m venv .venv` (Linux/macOS) e instalar as dependências com `.venv\Scripts\python -m pip install -r ferramentas/requirements.txt` no Windows ou `.venv/bin/python -m pip install -r ferramentas/requirements.txt` no Linux/macOS.

Se Pillow já estiver instalado e ocorrer `PIL._imaging uses unknown slot ID`, confirme que está usando uma versão estável do Python e reinstale o wheel no mesmo ambiente:

```bash
.venv\Scripts\python -m pip install --force-reinstall --no-cache-dir -r ferramentas/requirements.txt
```

Isso regenera `dados/` e os assets base. Edite os JSON depois de gerar, se precisar ajustar valores, e rode os testes de conteúdo.

### Assets pixel art para o fluxo Bakin

O jogo se chama **GrimReap** e o mundo dos seis continentes é o **Reino de GrimReap**. O manifesto `assets/bakin_manifest.json` descreve uma coleção original de personagens, props, tiles e ícones com dimensões compatíveis com o fluxo de sprites 40x40/80x80; não reproduz arte de terceiros. A geração é procedural e local, sem enviar prompts/imagens a serviços externos e sem precisar de chave de API:

```powershell
uv run --python 3.14 --with-requirements ferramentas\requirements.txt ferramentas\gerar_assets_bakin_local.py
uv run --python 3.14 --with-requirements ferramentas\requirements.txt ferramentas\fable5_vision_check.py
```

Os PNGs ficam em `assets/bakin_pixelart/`. Personagens usam spritesheets 3x4 com transparência e Normal Maps `_n.png`; o auditor valida dimensões, alpha, 12 células preenchidas e os pares diffuse/normal. Ícones não precisam de mapa normal. Esta coleção é standalone: ainda não substitui o manifesto nem o renderer usados pelo jogo. O mapa estratégico aparece como **Mapa do Reino** em `/mapa-mundi.html`.

### Atualização GrimReap: identidade, engenharia e auditoria

- O nome do produto é **GrimReap** e o nome do mundo que reúne os seis continentes é **Reino de GrimReap**. A identidade aparece nas telas de login, seleção de personagem, nascimento, jogo e no título/navegação do mapa.
- O mapa antes apresentado como atlas agora é identificado na interface como **Mapa do Reino**. A rota técnica continua em `/mapa-mundi.html` para manter compatibilidade com links existentes; o conteúdo do canvas identifica o Reino de GrimReap.
- A casca mobile Capacitor define `appName: "GrimReap"` e o identificador `com.grimreap.game`. O projeto Android já foi gerado em `mobile/android`; a mudança de identificador deve ser tratada como identidade de um novo app ao publicar.
- O ciclo de engenharia foi reconciliado com o runtime real do repositório: o cliente atual renderiza o mundo jogável em Canvas 2D isométrico; o backend autoritativo é Kotlin/Ktor e oferece WebSocket por zona; Capacitor é a casca de empacotamento. Não foi introduzido outro renderer ou servidor autoritativo para competir com esse fluxo.
- `cliente/src/engine/BakinShader.ts` contém shaders auxiliares WebGL2, mas não é o renderer integrado do jogo. `backend/go-engine/main.go` continua sendo um protótipo Go isolado, não um servidor ativo do multiplayer.
- A integração nativa Capacitor foi iniciada após decisão explícita de manter o Ktor como autoridade: existe um projeto Android em `mobile/android`, e o plugin `GameBridge` está registrado na `MainActivity` para informar métricas de tela/dispositivo e eventos Android de pausa/retomada. O APK de debug compilou com sucesso em JDK 17 e SDK API 34; a execução em dispositivo/emulador ainda não foi validada. O plugin não chama o motor Go nem substitui o WebSocket Ktor.
- O cliente usa a origem configurável `GRIMREAP_API_URL` para as APIs e o WebSocket. `mobile/scripts/configure-api.mjs` grava essa origem em `cliente/dist/runtime-config.js` e restringe a CSP do pacote Android à origem da API e do WebSocket antes da sincronização Capacitor; a configuração não contém segredos. Na web, a CSP mantém as chamadas na própria origem e permite WebSocket. Veja [mobile/README.md](./mobile/README.md) para configuração por emulador, dispositivo e compilação.
- A tela de login usa uma composição original de fantasia com cenário e sprites 2D locais, layout responsivo, abas acessíveis de login/cadastro, controle de visibilidade da senha, feedback de status e seletor de tema. O painel social só aparece quando há provedores habilitados no servidor; a identidade visual e os textos são próprios de GrimReap.
- O spritesheet standalone `hero_adventurer` agora representa um aventureiro panda-vermelho steampunk/cyberpunk, com quatro direções, três quadros por direção, detalhes em latão e linhas ciano. O normal map é regenerado pelo gerador; 240x320, alpha, 12 quadros e normal map passaram na auditoria. Essa coleção Bakin continua fora do renderer ativo do jogo.
- **Pendência Android:** `npm run android:sync` concluiu com sucesso usando `http://10.0.2.2:3001`; `:app:assembleDebug` e `:app:assembleRelease` passaram com Temurin JDK 17 e Android SDK API 34. O APK de debug e o release sem assinatura foram verificados como `com.grimreap.game` (rótulo `GrimReap`). O `server.cleartext` permanece falso na configuração versionada e é habilitado pelo script somente para URLs HTTP de debug; HTTPS o desabilita. Não havia emulador configurado nem dispositivo conectado para validar execução ou assinar/distribuir release; E4 e V3 em [`.fable5_state.md`](./.fable5_state.md) continuam pendentes até esses passos.
- A coleção `assets/bakin_pixelart/` contém 10 imagens diffuse originais (dois personagens, um monstro, quatro props, um tileset e dois ícones) e oito Normal Maps. Sua geração e auditoria são locais e não dependem de API de IA. A coleção ainda não está conectada ao manifesto/renderizador ativo; o cliente continua usando os assets declarados em `assets/manifest.json`.
- O auditor `ferramentas/fable5_vision_check.py` falha com código de erro se encontrar dimensões incorretas, assets ausentes/ilegíveis, alpha inválido, células vazias nos spritesheets ou Normal Maps ausentes/incompatíveis. A auditoria deste ciclo encontrou as dez imagens e os oito mapas normais válidos; cada spritesheet tinha as 12 células ocupadas.
- O arquivo [`.fable5_state.md`](./.fable5_state.md) na raiz acompanha o ciclo, registra as decisões de arquitetura e o estado ainda não validado da compilação Android.

## Funcionalidades e regras implementadas

- **Mundo original:** 6 continentes, 24 zonas, 10 classes, monstros, itens, NPCs, quests e tilemaps são definidos em `dados/`. O cliente desenha o terreno em projeção isométrica 2:1 e usa arte placeholder gerada para este projeto. Não são usados sprites, mapas, nomes ou outros assets de Ragnarok ou da Gravity.
- **Mapa do Reino:** `/mapa-mundi.html` apresenta uma representação esquemática original dos seis continentes de GrimReap, cidades — Aurivela, Nácaris, Korvurna, Nascenraiz, Nívoria e Braseal —, campos, masmorras e rotas. Os marcadores são selecionáveis e as zonas com miniboss recebem um anel circular. É uma representação estratégica 2D; os mapas locais continuam em tiles isométricos 2:1.
- **Minibosses de campo:** há um miniboss original em cada continente, em região circular configurada em `dados/jogabilidade.json`. O spawn normal é alinhado à próxima marca de hora do relógio do servidor; uma vez ativo, o chefe permanece na região até ser derrotado. Cada derrota substitui o ciclo normal por um retorno exatamente cinco horas depois. A vida, o vencedor e o horário de retorno ficam persistidos no SQLite, portanto não são reiniciados ao reiniciar o servidor.
- **Registro de abate:** quem desfere o golpe final recebe uma Placa de Caçada Mundi no inventário. A interface do Mapa do Reino e a região correspondente exibem o nome do jogador e, quando aplicável, o clã/equipe responsável, além da contagem até o retorno. O círculo também aparece no mapa local; somente jogadores na zona e dentro da área podem atacar.
- **Arte procedural original:** o gerador produz um spritesheet original para cada miniboss e mantém texturas tileset por continente; os sprites são placeholders procedurais, sem material de terceiros ou dependência de geração externa.
- **Fluxo de entrada:** telas separadas de login, seleção de personagem e nascimento. O respawn usa a última cidade salva pelo NPC Save; o nascimento escolhido permanece registrado como origem.
- **Multiplayer por zona:** WebSocket autenticado com ticket de uso único, presença, snapshot, sincronização de entrada/saída e posição validada pela API. As conexões são separadas por zona e instância de raid.
- **Combate e progresso:** combate contra monstros com validação de zona e distância, HP, XP, subida de nível, respawn dos monstros e drops persistidos no inventário SQLite. Há suporte a equipamento, quests, venda de materiais, mercado negro e melhoria de equipamento pela API.
- **Cinco categorias de loot:** básicos para pequenas quests; intermediários para quests de coleta; ranqueados para trocas no mercado negro; ultra raros para conteúdo difícil; e ranqueados ultra raros para craft e melhoria. Os pesos e conteúdos são definidos nos JSON de `dados/`.
- **PK:** combate entre jogadores somente no círculo PK configurado de cada masmorra; fora dele o dano é bloqueado e a infração é registrada. O dano é validado pelo servidor. Uma derrota pode gerar saque de um equipamento equipado, com chance configurável de 5% e escolha ponderada pelo rank. O saque pertence ao atacante e expira em 30 segundos, ou 60 segundos se a latência observada atingir 500 ms.
- **Clãs e masmorra SS:** a interface permite criar clã, entrar por convite, selecionar dez membros e iniciar a raid. O servidor exige dez personagens distintos do mesmo clã e consome uma insígnia SS do inventário do grupo ao iniciar; não há reembolso. A raid cria instância isolada, o chefe começa dez níveis acima do nível mais alto do grupo e escala se um membro de nível superior entrar. O combate do chefe aplica dano aos jogadores; se todo o grupo for derrotado, os membros são reanimados e o chefe volta ao nível do grupo +10. Abandonar a instância encerra a raid para o grupo sem devolver a chave.
- **Interface de jogo:** painéis de progressão e inventário, NPCs de Save/Teleporte, mercador, operações de clã/raid, lista de jogadores presentes e combate/saque PK.
- **Temas visuais:** alternância persistente entre tema claro cyberpunk/steampunk inspirado na paleta quente do panda-vermelho (creme, terracota e latão) e tema escuro inspirado no panda comum (grafite, preto, marfim, ciano e rosa neon). As bordas de néon são finas, os estados de foco são visíveis e textos/elementos usam contraste reforçado. O seletor está disponível nas telas de login, personagens, nascimento e jogo.

## Limites conhecidos e próximos passos

- O multiplayer é uma implementação funcional **em um único processo**, com limite de 100 conexões por zona/instância. Não há pub/sub, particionamento entre processos, reconexão distribuída ou infraestrutura de MMO em escala de produção.
- O combate contra monstros e os estados de HP dos monstros são mantidos em memória do processo; progresso, inventário, equipamentos, quests, clãs, raids e saques ficam no SQLite. Reiniciar o processo reinicia HP/retorno de monstros.
- O saldo de MC é persistido junto à personagem, mas ainda não há extrato separado de créditos e débitos.
- A regra dos minibosses mundi é global por processo/instalação do banco SQLite, mas ainda não usa pub/sub ou coordenação distribuída entre múltiplos servidores. A atualização do mapa consulta os estados periodicamente; o anúncio não é um push WebSocket.
- O crédito de “equipe” do miniboss usa o clã do jogador que desfere o golpe final; o jogo ainda não tem composição/participação formal de grupo para dividir a autoria ou a placa.
- Monstros ainda não atacam jogadores. A raid tem combate e wipe de grupo, mas não possui IA, habilidades, ataques temporizados ou coordenação de grupo em tempo real. Os dez integrantes são selecionados a partir do roster do clã; ainda não existe sistema de convite/prontidão, requisito de estarem online simultaneamente ou dissolução automática ao desconectar.
- A insígnia SS só pode cair do miniboss configurado para a masmorra quando o nível do personagem alcança um dos marcos 10, 20, 30, 40 ou 50. A chance é aplicada a cada derrota elegível; a progressão ainda usa o nível do personagem, sem estado independente de andar ou salas distintas por andar.
- A penalidade por ataque fora da área PK registra infrações e recusa dano; ainda não há carma, multa ou suspensão progressiva. O cliente oferece seleção entre jogadores presentes, mas a validação e a autorização de saque são sempre feitas no backend.
- A arte é um placeholder original para validar a projeção e o fluxo de jogo, não arte final. O conteúdo também não é um projeto importável de uma engine comercial.
- O checkout ainda não concede itens ao personagem, e os pedidos de compra permanecem em memória. Antes de produção, é necessário persistir pedidos e integrar a confirmação do pagamento à entrega do item.
- Login social depende de credenciais e callbacks configurados. Recuperação de senha e verificação de e-mail dependem de integração de envio de e-mail.

## Validação

Use `.\run-tests.ps1` na raiz para executar testes e build do backend e cliente. O script usa o Gradle Wrapper 8.10.2 e `npm ci`. A execução após as mudanças passou: backend `BUILD SUCCESSFUL`, 5 arquivos/10 testes do cliente aprovados e build TypeScript concluído. `go test main.go` verifica a compilação do protótipo isolado (não há testes Go); `go vet main.go` não relatou problemas. O auditor Bakin confirmou dimensões, alpha, células 3x4 e mapas normais, incluindo o novo sprite do panda-vermelho.

O app Capacitor foi sincronizado com o plugin nativo. `:app:assembleDebug` passou com Temurin JDK 17 e Android SDK API 34. Ainda falta executar o APK em emulador/dispositivo e verificar pause/resume, autenticação, API e WebSocket; o checklist nativo permanece aberto até esse teste.

Os testes unitários cobrem as regras principais de progresso, requisitos/consumo de chave da raid e saque PK; ainda faltam testes de integração de rotas, WebSocket, reconexão, latência e fluxo completo de navegador. Os provedores OAuth externos também exigem credenciais reais para validação ponta a ponta. A validação do protótipo Go não significa que ele faça parte do backend ativo.

Pastas antigas (`loja/`, `jogo-backend/`, `jogo-mobile/`) foram substituídas por esta estrutura (ADR 0004).
