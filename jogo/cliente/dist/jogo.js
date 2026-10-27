import "./theme.js";
/** Mapa jogável com movimento validado, combate, progressão, clãs, raids e PK. */
import { API_BASE_URL } from "./api-config.js";
import { iniciarPonteNativa } from "./game-bridge.js";
import { carregarVitrine, criarCardItem, iniciarCompra } from "./loja.js";
import { enviarMovimento } from "./telemetria.js";
import { telaParaTile, tileParaTela } from "./isometria.js";
import { ErroApi, abandonarRaid, atacarBossMundi, atacarChefe, atacarJogador, obterConta, obterBossesMundi, obterClan, obterEstadoMundo, obterProgresso, obterRaid, obterTicketMultiplayer, obterToken, respawn, saquearPvP, salvarPonto, teletransportar, atacarMonstro, criarClan, entrarClan, iniciarRaid, venderItem, } from "./auth.js";
const URL_API = API_BASE_URL;
const LARGURA_TILE = 32;
const ALTURA_TILE = 16;
const TILES_IMPASSAVEIS = new Set([2, 3, 6]);
const INTERVALO_MOVIMENTO_MS = 150;
const NPC_SAVE = { x: 18, y: 15 };
const NPC_TELEPORTE = { x: 22, y: 15 };
const canvas = document.getElementById("mundo");
const ctx = canvas.getContext("2d");
ctx.imageSmoothingEnabled = false;
const estadoPlataforma = document.getElementById("estado-plataforma");
const painelMonstro = document.getElementById("monstro");
const painelBossMundi = document.getElementById("boss-mundi");
const estadoBossMundiElemento = document.getElementById("estado-boss-mundi");
const botaoAtacarBossMundi = document.getElementById("atacar-boss-mundi");
const painelProgresso = document.getElementById("progresso");
const painelMercador = document.getElementById("mercador");
const seletorVenda = document.getElementById("item-venda");
const quantidadeVenda = document.getElementById("quantidade-venda");
const botaoVenda = document.getElementById("vender-item");
const painelLoja = document.getElementById("loja");
const botaoLoja = document.getElementById("abrir-loja");
const aviso = document.getElementById("aviso-jogo");
const identidadeJogador = document.getElementById("identidade-jogador");
const localizacaoJogador = document.getElementById("localizacao-jogador");
const saldoJogador = document.getElementById("saldo-mc");
const estadoSave = document.getElementById("estado-save");
const botaoSave = document.getElementById("salvar-ponto");
const seletorDestino = document.getElementById("destino");
const tarifaTeleporte = document.getElementById("tarifa-teleporte");
const botaoTeleporte = document.getElementById("teletransportar");
const formularioClan = document.getElementById("formulario-clan");
const nomeClan = document.getElementById("nome-clan");
const codigoConviteClan = document.getElementById("convite-clan");
const botaoCriarClan = document.getElementById("criar-clan");
const botaoEntrarClan = document.getElementById("entrar-clan");
const estadoClanElemento = document.getElementById("estado-clan");
const membrosClanElemento = document.getElementById("membros-clan");
const botaoIniciarRaid = document.getElementById("iniciar-raid");
const estadoRaidElemento = document.getElementById("estado-raid");
const botaoAtacarChefe = document.getElementById("atacar-chefe");
const botaoAbandonarRaid = document.getElementById("abandonar-raid");
const painelPk = document.getElementById("painel-pk");
const seletorAlvoPk = document.getElementById("alvo-pk");
const botaoAtacarPk = document.getElementById("atacar-pk");
const botaoSaquePk = document.getElementById("saquear-pk");
const imagens = new Map();
let manifesto;
let zonas = [];
let continentes = [];
let monstrosDef = [];
let itensDef = [];
let mapa;
let regrasJogo;
let mundo;
let progressoJogador;
let estadoClan = { clan: null, membros: [] };
let bossesMundi = [];
let raidAtiva = null;
let saquePendente = null;
let monstroSelecionado = null;
let monstrosNaZona = [];
const jogadoresRemotos = new Map();
const jogador = { x: 0, y: 0, classe: "espadachim" };
let ultimoMovimento = 0;
let personagemAtivo = null;
let zonaCarregando = false;
let movimentoEmAndamento = false;
let interacaoNpcEmAndamento = false;
let dentroDaAreaPk = false;
let intervaloReconexao = 1000;
function telaIso(x, y) {
    return tileParaTela(x, y, mapa.altura, LARGURA_TILE, ALTURA_TILE);
}
function corTile(indice, x, y) {
    const paleta = continentes.find((item) => item.id === mapa.continente)?.paleta
        ?? ["#557844", "#806443", "#54718b"];
    return paleta[(indice + x + y) % paleta.length];
}
function desenharDiamante(x, y, indice) {
    const centro = telaIso(x, y);
    const meioW = LARGURA_TILE / 2;
    const meioH = ALTURA_TILE / 2;
    ctx.beginPath();
    ctx.moveTo(centro.x, centro.y - meioH);
    ctx.lineTo(centro.x + meioW, centro.y);
    ctx.lineTo(centro.x, centro.y + meioH);
    ctx.lineTo(centro.x - meioW, centro.y);
    ctx.closePath();
    ctx.fillStyle = corTile(indice, x, y);
    ctx.fill();
    ctx.strokeStyle = "rgba(15, 19, 20, 0.2)";
    ctx.lineWidth = 1;
    ctx.stroke();
    ctx.save();
    ctx.clip();
    const semente = (x * 17 + y * 31 + indice * 13) % 7;
    ctx.fillStyle = "rgba(255, 255, 255, 0.12)";
    ctx.fillRect(centro.x - 9 + semente, centro.y - 3, 3, 2);
    ctx.fillRect(centro.x + 2 - semente, centro.y + 2, 2, 2);
    ctx.fillStyle = "rgba(0, 0, 0, 0.12)";
    ctx.fillRect(centro.x - 3, centro.y - 6 + semente % 3, 2, 2);
    ctx.restore();
}
async function json(caminho) {
    const response = await fetch(caminho);
    if (!response.ok)
        throw new Error(`falha ao carregar ${caminho}`);
    return response.json();
}
function imagem(caminho) {
    const emCache = imagens.get(caminho);
    if (emCache)
        return Promise.resolve(emCache);
    return new Promise((resolve, reject) => {
        const img = new Image();
        img.onload = () => { imagens.set(caminho, img); resolve(img); };
        img.onerror = () => reject(new Error(`imagem ausente: ${caminho}`));
        img.src = caminho;
    });
}
function desenharNpc(x, y, cor, rotulo) {
    const posicao = telaIso(x, y);
    ctx.fillStyle = "#111319";
    ctx.fillRect(posicao.x - 10, posicao.y - 22, 20, 24);
    ctx.fillStyle = cor;
    ctx.fillRect(posicao.x - 7, posicao.y - 19, 14, 18);
    ctx.fillStyle = "#fff";
    ctx.font = "bold 11px system-ui";
    ctx.textAlign = "center";
    ctx.fillText(rotulo, posicao.x, posicao.y - 24);
}
function desenharTiles() {
    for (let soma = 0; soma < mapa.largura + mapa.altura - 1; soma++) {
        for (let y = Math.max(0, soma - mapa.largura + 1); y <= Math.min(mapa.altura - 1, soma); y++) {
            const x = soma - y;
            desenharDiamante(x, y, mapa.tiles[y][x]);
        }
    }
    desenharAreaPk();
    desenharAreaBossMundi();
    desenharEntidades();
}
function desenharEntidades() {
    desenharNpc(NPC_TELEPORTE.x, NPC_TELEPORTE.y, "#44a5ff", "PORTAL");
    const chefeAtivo = bossMundiAtual();
    if (chefeAtivo?.vidaAtual) {
        const sprite = manifesto.monstros[chefeAtivo.id];
        const posicaoChefe = telaIso(chefeAtivo.centro[0], chefeAtivo.centro[1]);
        if (sprite) {
            void imagem(sprite.arquivo).then((img) => {
                ctx.drawImage(img, 0, 0, 64, 64, posicaoChefe.x - 32, posicaoChefe.y - 56, 64, 64);
            }).catch(() => { aviso.textContent = `Não foi possível carregar o sprite de ${chefeAtivo.nome}.`; });
        }
        else {
            desenharNpc(chefeAtivo.centro[0], chefeAtivo.centro[1], "#ff536f", "MINIBOSS");
        }
    }
    if (mundo.zonaAtual.tipo === "cidade") {
        desenharNpc(NPC_SAVE.x, NPC_SAVE.y, "#42c978", "SAVE");
    }
    for (const monstro of monstrosNaZona) {
        const arquivo = manifesto.monstros[monstro.def.id];
        const posicao = telaIso(monstro.x, monstro.y);
        void imagem(arquivo.arquivo).then((img) => {
            ctx.drawImage(img, 0, 0, 64, 64, posicao.x - 32, posicao.y - 56, 64, 64);
        }).catch(() => { aviso.textContent = `Não foi possível carregar o sprite de ${monstro.def.nome}.`; });
    }
    for (const remoto of jogadoresRemotos.values()) {
        const sprite = manifesto.personagens[remoto.classeId];
        if (!sprite)
            continue;
        const posicao = telaIso(remoto.x, remoto.y);
        void imagem(sprite.arquivo).then((img) => {
            ctx.drawImage(img, 0, 0, 48, 48, posicao.x - 24, posicao.y - 44, 48, 48);
            ctx.fillStyle = "#fff";
            ctx.font = "11px system-ui";
            ctx.textAlign = "center";
            ctx.fillText(remoto.nome, posicao.x, posicao.y - 47);
        }).catch(() => { aviso.textContent = `Não foi possível carregar o sprite de ${remoto.nome}.`; });
    }
    const arquivoJogador = manifesto.personagens[jogador.classe];
    const posicao = telaIso(jogador.x, jogador.y);
    void imagem(arquivoJogador.arquivo).then((img) => {
        ctx.drawImage(img, 0, 0, 48, 48, posicao.x - 24, posicao.y - 44, 48, 48);
    }).catch(() => { aviso.textContent = "Não foi possível carregar o sprite do personagem."; });
}
function receberEventoZona(evento) {
    if (evento.tipo === "snapshot") {
        jogadoresRemotos.clear();
        for (const remoto of evento.jogadores ?? []) {
            if (remoto.personagemId !== personagemAtivo?.id) {
                jogadoresRemotos.set(remoto.personagemId, remoto);
            }
        }
    }
    else if (evento.tipo === "saiu" && evento.jogadorId) {
        jogadoresRemotos.delete(evento.jogadorId);
    }
    else if (evento.jogador && evento.jogador.personagemId !== personagemAtivo?.id) {
        jogadoresRemotos.set(evento.jogador.personagemId, evento.jogador);
    }
    desenhar();
    atualizarAlvosPk();
}
async function conectarMultiplayer() {
    if (!obterToken())
        return;
    try {
        const ticket = await obterTicketMultiplayer();
        const socketUrl = new URL("/api/multiplayer/zone", API_BASE_URL);
        socketUrl.protocol = socketUrl.protocol === "https:" ? "wss:" : "ws:";
        socketUrl.searchParams.set("ticket", ticket);
        const socket = new WebSocket(socketUrl);
        let heartbeat;
        socket.onopen = () => {
            intervaloReconexao = 1000;
            heartbeat = window.setInterval(() => {
                if (socket.readyState === WebSocket.OPEN) {
                    socket.send(JSON.stringify({ tipo: "ping", sentAt: Date.now() }));
                }
            }, 5000);
        };
        socket.onmessage = ({ data }) => {
            try {
                const evento = JSON.parse(String(data));
                if (evento.tipo === "pong" && typeof evento.sentAt === "number") {
                    socket.send(JSON.stringify({ tipo: "latencia", ms: Date.now() - evento.sentAt }));
                    return;
                }
                receberEventoZona(evento);
            }
            catch (erro) {
                console.error("Evento multiplayer inválido recebido.", erro);
            }
        };
        socket.onclose = () => {
            if (heartbeat !== undefined)
                window.clearInterval(heartbeat);
            jogadoresRemotos.clear();
            window.setTimeout(() => { void conectarMultiplayer(); }, intervaloReconexao);
            intervaloReconexao = Math.min(intervaloReconexao * 2, 15000);
        };
        socket.onerror = () => {
            aviso.textContent = "Conexão multiplayer interrompida; tentando reconectar.";
        };
    }
    catch (erro) {
        aviso.textContent = erro instanceof Error
            ? `Multiplayer indisponível: ${erro.message}`
            : "Não foi possível conectar ao multiplayer.";
        window.setTimeout(() => { void conectarMultiplayer(); }, intervaloReconexao);
        intervaloReconexao = Math.min(intervaloReconexao * 2, 15000);
    }
}
function desenhar() {
    ctx.clearRect(0, 0, canvas.width, canvas.height);
    desenharTiles();
}
function regraMasmorraAtual() {
    return regrasJogo.masmorras.find((masmorra) => masmorra.id === mundo.zonaAtual.id);
}
function bossMundiAtual() {
    return bossesMundi.find((boss) => boss.zonaId === mundo.zonaAtual.id);
}
function desenharAreaBossMundi() {
    const boss = bossMundiAtual();
    if (!boss)
        return;
    const centro = telaIso(boss.centro[0], boss.centro[1]);
    ctx.save();
    ctx.fillStyle = boss.vidaAtual > 0 ? "rgba(255, 50, 80, .12)" : "rgba(255, 50, 80, .05)";
    ctx.strokeStyle = boss.vidaAtual > 0 ? "rgba(255, 80, 105, .95)" : "rgba(255, 80, 105, .6)";
    ctx.lineWidth = 3;
    ctx.setLineDash(boss.vidaAtual > 0 ? [] : [8, 6]);
    ctx.beginPath();
    ctx.ellipse(centro.x, centro.y, boss.raio * Math.SQRT2 * LARGURA_TILE / 2, boss.raio * Math.SQRT2 * ALTURA_TILE / 2, 0, 0, Math.PI * 2);
    ctx.fill();
    ctx.stroke();
    ctx.restore();
}
function dentroDaAreaPkAtual() {
    const circulo = regraMasmorraAtual()?.circulo_pk;
    return circulo !== undefined
        && distancia(jogador, { x: circulo.centro[0], y: circulo.centro[1] }) <= circulo.raio;
}
function atualizarPainelBossMundi() {
    const boss = bossMundiAtual();
    painelBossMundi.hidden = boss === undefined;
    if (!boss) {
        botaoAtacarBossMundi.disabled = true;
        return;
    }
    const emCirculo = distancia(jogador, { x: boss.centro[0], y: boss.centro[1] }) <= boss.raio;
    if (boss.vidaAtual > 0) {
        estadoBossMundiElemento.textContent = `${boss.nome} ativo · HP ${boss.vidaAtual}/${boss.vidaMaxima}. ${emCirculo ? "Você está no círculo de combate." : "Entre no círculo vermelho para lutar."}`;
    }
    else {
        const retorno = boss.proximaAparicaoEpoch ?? Math.floor(Date.now() / 1000);
        const minutos = Math.max(0, Math.ceil((retorno - Date.now() / 1000) / 60));
        const horas = Math.floor(minutos / 60);
        estadoBossMundiElemento.textContent = boss.derrotadoPor
            ? `${boss.nome} foi derrotado por ${boss.equipeDerrotou ? `equipe ${boss.equipeDerrotou} / ` : ""}${boss.derrotadoPor}. Retorna em ${horas}h ${minutos % 60}m.`
            : `${boss.nome} aguarda aparição. Retorna em ${horas}h ${minutos % 60}m.`;
    }
    botaoAtacarBossMundi.disabled = boss.vidaAtual <= 0 || !emCirculo || movimentoEmAndamento;
}
async function atualizarBossesMundi() {
    bossesMundi = await obterBossesMundi();
    atualizarPainelBossMundi();
    desenhar();
}
function desenharAreaPk() {
    const regra = regraMasmorraAtual();
    if (!regra)
        return;
    const centro = telaIso(regra.circulo_pk.centro[0], regra.circulo_pk.centro[1]);
    const raio = regra.circulo_pk.raio;
    ctx.save();
    ctx.fillStyle = "rgba(200, 32, 52, 0.08)";
    ctx.strokeStyle = "rgba(255, 80, 95, 0.8)";
    ctx.lineWidth = 3;
    ctx.setLineDash([8, 5]);
    ctx.beginPath();
    ctx.ellipse(centro.x, centro.y, raio * Math.SQRT2 * LARGURA_TILE / 2, raio * Math.SQRT2 * ALTURA_TILE / 2, 0, 0, Math.PI * 2);
    ctx.fill();
    ctx.stroke();
    ctx.restore();
}
function distancia(a, b) {
    return Math.hypot(a.x - b.x, a.y - b.y);
}
function formatarTarifa(valor) {
    return `${valor.toLocaleString("pt-BR")} MC`;
}
function montarDestinos(destinos) {
    seletorDestino.replaceChildren();
    const opcoesPorContinente = new Map();
    for (const destino of destinos) {
        let grupo = opcoesPorContinente.get(destino.continenteId);
        if (!grupo) {
            grupo = document.createElement("optgroup");
            grupo.label = continentes.find((continente) => continente.id === destino.continenteId)?.nome
                ?? destino.continenteId;
            opcoesPorContinente.set(destino.continenteId, grupo);
            seletorDestino.appendChild(grupo);
        }
        const opcao = document.createElement("option");
        opcao.value = destino.zonaId;
        opcao.dataset.tarifa = String(destino.tarifaMc);
        opcao.textContent = `${destino.nome} — ${formatarTarifa(destino.tarifaMc)}`;
        grupo.appendChild(opcao);
    }
}
function atualizarInteracoes() {
    const emCidade = mundo.zonaAtual.tipo === "cidade";
    const pertoSave = emCidade && distancia(jogador, NPC_SAVE) <= 1.5;
    const pertoTeleporte = distancia(jogador, NPC_TELEPORTE) <= 1.5;
    botaoSave.disabled = !pertoSave || movimentoEmAndamento || zonaCarregando || interacaoNpcEmAndamento;
    botaoTeleporte.disabled = !pertoTeleporte || movimentoEmAndamento || zonaCarregando || interacaoNpcEmAndamento;
    seletorDestino.disabled = !pertoTeleporte || zonaCarregando || interacaoNpcEmAndamento;
    if (!emCidade) {
        estadoSave.textContent = `Ponto de respawn atual: ${mundo.personagem.zonaSalvaId ?? "não definido"}. NPC Save fica nas cidades.`;
    }
    else if (pertoSave) {
        estadoSave.textContent = `Respawn atual: ${mundo.personagem.zonaSalvaId ?? mundo.personagem.zonaNatalId}. Você está junto ao NPC Save.`;
    }
    else {
        estadoSave.textContent = "Aproxime-se do marcador SAVE no mapa para definir o respawn nesta cidade.";
    }
    const tarifa = Number(seletorDestino.selectedOptions[0]?.dataset.tarifa ?? 0);
    tarifaTeleporte.textContent = pertoTeleporte
        ? `Destino: ${seletorDestino.selectedOptions[0]?.textContent ?? "selecione uma região"}. Saldo: ${formatarTarifa(mundo.saldoMc)}.`
        : "Aproxime-se do marcador PORTAL no mapa para viajar.";
    if (pertoTeleporte && tarifa > mundo.saldoMc) {
        tarifaTeleporte.textContent += " Saldo insuficiente.";
        botaoTeleporte.disabled = true;
    }
}
async function carregarZona(zonaId) {
    zonaCarregando = true;
    atualizarInteracoes();
    try {
        mapa = await json(`dados/tilemaps/${zonaId}.json`);
        const zona = zonas.find((item) => item.id === zonaId);
        if (!zona)
            throw new Error(`zona desconhecida: ${zonaId}`);
        monstrosNaZona = zona.monstros
            .map((id) => monstrosDef.find((monstro) => monstro.id === id))
            .filter((monstro) => monstro !== undefined)
            .map((def, index) => ({ def, x: 4 + ((index * 7) % 30), y: 4 + ((index * 11) % 20) }));
        jogador.x = Math.floor(mapa.largura / 2);
        jogador.y = Math.floor(mapa.altura / 2);
        canvas.width = (mapa.largura + mapa.altura) * LARGURA_TILE / 2 + 64;
        canvas.height = (mapa.largura + mapa.altura) * ALTURA_TILE / 2 + 64;
        localizacaoJogador.textContent = `${mundo.zonaAtual.nome} · ${continentes.find((c) => c.id === mundo.zonaAtual.continenteId)?.nome ?? mundo.zonaAtual.continenteId}`;
        saldoJogador.textContent = `${mundo.saldoMc.toLocaleString("pt-BR")} MC`;
        estadoSave.textContent = `Respawn salvo: ${zonas.find((item) => item.id === mundo.personagem.zonaSalvaId)?.nome ?? mundo.personagem.zonaSalvaId ?? "não definido"}.`;
        montarDestinos(mundo.destinos);
        painelMercador.hidden = mundo.zonaAtual.tipo !== "cidade";
        atualizarOpcoesDeVenda();
        painelMonstro.textContent = "Clique em um monstro para ver informações.";
        dentroDaAreaPk = dentroDaAreaPkAtual();
        atualizarAvisoPk();
        atualizarPainelBossMundi();
        atualizarPainelRaid();
        aviso.textContent = "";
        desenhar();
    }
    finally {
        zonaCarregando = false;
        atualizarInteracoes();
        atualizarPainelBossMundi();
    }
}
async function aplicarEstado(estado) {
    mundo = estado;
    personagemAtivo = estado.personagem;
    await carregarZona(estado.zonaAtual.id);
}
function nomeItem(id) {
    return itensDef.find((item) => item.id === id)?.nome ?? id;
}
function mostrarMonstro(def) {
    monstroSelecionado = def;
    painelMonstro.replaceChildren();
    const titulo = document.createElement("h3");
    titulo.textContent = `${def.nome}${def.boss ? " (chefe)" : ""} — nível ${def.nivel}`;
    const lista = document.createElement("ul");
    for (const drop of def.drops) {
        const item = document.createElement("li");
        item.textContent = `${nomeItem(drop.item)}: ${drop.taxa_pct}%`;
        lista.appendChild(item);
    }
    const atacar = document.createElement("button");
    atacar.type = "button";
    atacar.textContent = "Atacar";
    atacar.addEventListener("click", () => { void atacarMonstroSelecionado(); });
    painelMonstro.append(titulo, lista, atacar);
}
function atualizarPainelProgresso() {
    painelProgresso.replaceChildren();
    const titulo = document.createElement("h2");
    titulo.textContent = `Nível ${progressoJogador.nivel} · Vida ${progressoJogador.vidaAtual}/100`;
    const xp = document.createElement("p");
    xp.textContent = `Experiência: ${progressoJogador.experiencia}`;
    const inventario = document.createElement("ul");
    const entradas = Object.entries(progressoJogador.inventario);
    for (const [itemId, quantidade] of entradas) {
        const linha = document.createElement("li");
        linha.textContent = `${nomeItem(itemId)} × ${quantidade}`;
        inventario.appendChild(linha);
    }
    if (entradas.length === 0) {
        const vazio = document.createElement("li");
        vazio.textContent = "Inventário vazio.";
        inventario.appendChild(vazio);
    }
    painelProgresso.append(titulo, xp, inventario);
}
function atualizarOpcoesDeVenda() {
    seletorVenda.replaceChildren();
    for (const [itemId, quantidade] of Object.entries(progressoJogador.inventario)) {
        const item = itensDef.find((definicao) => definicao.id === itemId);
        if (!item || item.venda_mc <= 0 || item.categoria_loot === "ultra_raros"
            || item.categoria_loot === "ranqueados_ultra_raros" || item.tipo === "equipamento")
            continue;
        const opcao = document.createElement("option");
        opcao.value = itemId;
        opcao.textContent = `${item.nome} × ${quantidade} — ${item.venda_mc} MC/un.`;
        opcao.dataset.quantidade = String(quantidade);
        seletorVenda.appendChild(opcao);
    }
    const habilitado = seletorVenda.options.length > 0 && mundo.zonaAtual.tipo === "cidade";
    seletorVenda.disabled = !habilitado;
    quantidadeVenda.disabled = !habilitado;
    botaoVenda.disabled = !habilitado;
}
async function atacarMonstroSelecionado() {
    if (!monstroSelecionado)
        return;
    try {
        const resultado = await atacarMonstro(monstroSelecionado.id);
        progressoJogador = await obterProgresso();
        atualizarPainelProgresso();
        atualizarOpcoesDeVenda();
        const loot = resultado.drops.map((drop) => `${nomeItem(drop.itemId)} × ${drop.quantidade}`).join(", ");
        aviso.textContent = resultado.derrotado
            ? `${monstroSelecionado.nome} derrotado. +${resultado.experienciaGanha} XP${loot ? ` · Drop: ${loot}` : " · Sem drops."}`
            : `Dano ${resultado.dano}. Vida restante: ${resultado.hpRestante}.`;
    }
    catch (erro) {
        aviso.textContent = erro instanceof ErroApi ? erro.message : "Não foi possível atacar o monstro.";
    }
}
botaoVenda.addEventListener("click", async () => {
    const itemId = seletorVenda.value;
    const quantidade = Number(quantidadeVenda.value);
    if (botaoVenda.disabled || !itemId || !Number.isInteger(quantidade) || quantidade < 1 || quantidade > 99)
        return;
    try {
        progressoJogador = await venderItem(itemId, quantidade);
        mundo = await obterEstadoMundo();
        saldoJogador.textContent = `${mundo.saldoMc.toLocaleString("pt-BR")} MC`;
        atualizarPainelProgresso();
        atualizarOpcoesDeVenda();
        aviso.textContent = `Venda concluída. Saldo: ${saldoJogador.textContent}.`;
    }
    catch (erro) {
        aviso.textContent = erro instanceof ErroApi ? erro.message : "Não foi possível vender os itens.";
    }
});
botaoAtacarBossMundi.addEventListener("click", async () => {
    const boss = bossMundiAtual();
    if (!boss || botaoAtacarBossMundi.disabled)
        return;
    botaoAtacarBossMundi.disabled = true;
    try {
        const resultado = await atacarBossMundi(boss.id);
        bossesMundi = bossesMundi.map((estado) => estado.id === boss.id ? resultado.boss : estado);
        if (resultado.derrotado) {
            progressoJogador = await obterProgresso();
            atualizarPainelProgresso();
            aviso.textContent = `${boss.nome} derrotado. A Placa de Caçada Mundi foi adicionada ao inventário.`;
        }
        else {
            aviso.textContent = `Dano causado: ${resultado.dano}. HP restante: ${resultado.boss.vidaAtual}.`;
        }
        atualizarPainelBossMundi();
        desenhar();
    }
    catch (erro) {
        aviso.textContent = erro instanceof ErroApi ? erro.message : "Não foi possível atacar o miniboss.";
    }
    finally {
        atualizarPainelBossMundi();
    }
});
botaoCriarClan.addEventListener("click", async () => {
    try {
        await criarClan(nomeClan.value.trim());
        await recarregarClan();
        aviso.textContent = "Clã criado. Convide os demais jogadores pelo código exibido.";
    }
    catch (erro) {
        aviso.textContent = erro instanceof ErroApi ? erro.message : "Não foi possível criar o clã.";
    }
});
botaoEntrarClan.addEventListener("click", async () => {
    try {
        await entrarClan(codigoConviteClan.value.trim());
        await recarregarClan();
        aviso.textContent = "Você entrou no clã.";
    }
    catch (erro) {
        aviso.textContent = erro instanceof ErroApi ? erro.message : "Não foi possível entrar no clã.";
    }
});
botaoIniciarRaid.addEventListener("click", async () => {
    const membros = Array.from(membrosClanElemento.querySelectorAll("input:checked"))
        .map((membro) => membro.value);
    if (botaoIniciarRaid.disabled)
        return;
    try {
        raidAtiva = await iniciarRaid(membros);
        atualizarPainelRaid();
        aviso.textContent = "Raid iniciada e insígnia consumida. Use o NPC Teleporte para entrar no Trono do Abismo.";
    }
    catch (erro) {
        aviso.textContent = erro instanceof ErroApi ? erro.message : "Não foi possível iniciar a raid.";
    }
});
botaoAtacarChefe.addEventListener("click", async () => {
    if (botaoAtacarChefe.disabled)
        return;
    try {
        raidAtiva = await atacarChefe();
        progressoJogador = await obterProgresso();
        atualizarPainelProgresso();
        atualizarPainelRaid();
        aviso.textContent = raidAtiva.status === "completed"
            ? "Chefe derrotado. Recompensas da raid entregues aos membros."
            : raidAtiva.grupoDerrotado
                ? "Todo o grupo foi derrotado; o chefe foi recalibrado e a equipe reanimada."
                : `Você causou dano ao chefe. HP restante: ${raidAtiva.vidaChefe}. Sua vida: ${progressoJogador.vidaAtual}.`;
        if (raidAtiva.status !== "active") {
            raidAtiva = null;
            await recarregarRaid();
        }
    }
    catch (erro) {
        aviso.textContent = erro instanceof ErroApi ? erro.message : "Não foi possível atacar o chefe.";
    }
});
botaoAbandonarRaid.addEventListener("click", async () => {
    if (botaoAbandonarRaid.disabled)
        return;
    try {
        const resultado = await abandonarRaid();
        await aplicarEstado(await respawn());
        raidAtiva = null;
        atualizarPainelRaid();
        aviso.textContent = `Raid encerrada para todos os membros: ${resultado.motivo}.`;
    }
    catch (erro) {
        aviso.textContent = erro instanceof ErroApi ? erro.message : "Não foi possível abandonar a raid.";
    }
});
botaoAtacarPk.addEventListener("click", async () => {
    const alvoId = seletorAlvoPk.value;
    if (botaoAtacarPk.disabled || !alvoId)
        return;
    try {
        const resultado = await atacarJogador(alvoId);
        if (resultado.saqueId)
            saquePendente = resultado.saqueId;
        atualizarAlvosPk();
        aviso.textContent = resultado.derrotado
            ? `Jogador derrotado. ${resultado.equipamentoPerdido ? `Equipamento perdido: ${nomeItem(resultado.equipamentoPerdido)}.` : "Nenhum equipamento caiu."}${resultado.saqueId ? " Saque disponível por tempo limitado." : ""}`
            : `Dano PK: ${resultado.dano}. Vida restante do alvo: ${resultado.vidaRestante}.`;
    }
    catch (erro) {
        aviso.textContent = erro instanceof ErroApi ? erro.message : "Não foi possível atacar o jogador.";
    }
});
botaoSaquePk.addEventListener("click", async () => {
    if (!saquePendente)
        return;
    try {
        const itemId = await saquearPvP(saquePendente);
        saquePendente = null;
        progressoJogador = await obterProgresso();
        atualizarPainelProgresso();
        atualizarAlvosPk();
        aviso.textContent = `Equipamento recolhido: ${nomeItem(itemId)}.`;
    }
    catch (erro) {
        aviso.textContent = erro instanceof ErroApi ? erro.message : "Não foi possível recolher o saque.";
    }
});
function aoClicarNoMapa(evento) {
    const rect = canvas.getBoundingClientRect();
    const pixelX = (evento.clientX - rect.left) * (canvas.width / rect.width);
    const pixelY = (evento.clientY - rect.top) * (canvas.height / rect.height);
    const { x, y } = telaParaTile(pixelX, pixelY, mapa.altura, LARGURA_TILE, ALTURA_TILE);
    const monstro = monstrosNaZona.find((alvo) => alvo.x === x && alvo.y === y);
    if (monstro)
        mostrarMonstro(monstro.def);
}
function atualizarAvisoPk() {
    const elemento = document.getElementById("aviso-pk");
    if (!elemento)
        return;
    elemento.hidden = !dentroDaAreaPk;
    if (dentroDaAreaPk) {
        elemento.textContent = "Área PK: combate entre jogadores habilitado. Equipamentos podem ser saqueados após derrota.";
    }
    atualizarAlvosPk();
}
function atualizarAlvosPk() {
    painelPk.hidden = !dentroDaAreaPk;
    seletorAlvoPk.replaceChildren();
    const regra = regraMasmorraAtual();
    const candidatos = regra
        ? [...jogadoresRemotos.values()].filter((remoto) => distancia(remoto, { x: regra.circulo_pk.centro[0], y: regra.circulo_pk.centro[1] })
            <= regra.circulo_pk.raio)
        : [];
    for (const remoto of candidatos) {
        const opcao = document.createElement("option");
        opcao.value = remoto.personagemId;
        opcao.textContent = remoto.nome;
        seletorAlvoPk.appendChild(opcao);
    }
    if (candidatos.length === 0) {
        const opcao = document.createElement("option");
        opcao.value = "";
        opcao.textContent = "Nenhum outro jogador na área";
        seletorAlvoPk.appendChild(opcao);
    }
    botaoAtacarPk.disabled = !dentroDaAreaPk || candidatos.length === 0;
    botaoSaquePk.hidden = saquePendente === null;
}
function atualizarPainelClan() {
    const clan = estadoClan.clan;
    formularioClan.hidden = clan !== null;
    membrosClanElemento.replaceChildren();
    if (!clan) {
        estadoClanElemento.textContent = "Crie um clã ou use um código de convite para entrar.";
        botaoIniciarRaid.disabled = true;
        return;
    }
    estadoClanElemento.textContent = `Clã ${clan.nome} · Convite: ${clan.codigoConvite} · Selecione dez membros para a raid.`;
    for (const membro of estadoClan.membros) {
        const rotulo = document.createElement("label");
        const selecao = document.createElement("input");
        selecao.type = "checkbox";
        selecao.value = membro.id;
        selecao.checked = membro.id === personagemAtivo?.id;
        selecao.addEventListener("change", atualizarBotaoIniciarRaid);
        const nome = document.createElement("span");
        nome.textContent = membro.nome;
        rotulo.append(selecao, nome);
        membrosClanElemento.appendChild(rotulo);
    }
    atualizarBotaoIniciarRaid();
}
function atualizarBotaoIniciarRaid() {
    const selecionados = Array.from(membrosClanElemento.querySelectorAll("input:checked"));
    botaoIniciarRaid.disabled = raidAtiva !== null
        || selecionados.length !== regrasJogo.raid_ss.membros_obrigatorios
        || !selecionados.some((membro) => membro.value === personagemAtivo?.id);
}
function atualizarPainelRaid() {
    if (!raidAtiva) {
        estadoRaidElemento.textContent = "Nenhuma raid ativa. São exigidos dez membros do mesmo clã e uma insígnia SS.";
        botaoAtacarChefe.hidden = true;
        botaoAbandonarRaid.hidden = true;
        botaoAtacarChefe.disabled = true;
        botaoAbandonarRaid.disabled = true;
        atualizarBotaoIniciarRaid();
        return;
    }
    estadoRaidElemento.textContent = raidAtiva.grupoDerrotado
        ? `Grupo derrotado. Chefe recalibrado para nível ${raidAtiva.nivelChefe}; a equipe foi reanimada.`
        : `Raid ativa · Chefe nível ${raidAtiva.nivelChefe} · HP ${raidAtiva.vidaChefe}.`;
    botaoAtacarChefe.hidden = false;
    botaoAbandonarRaid.hidden = false;
    botaoAtacarChefe.disabled = mundo.zonaAtual.id !== raidAtiva.zonaId || progressoJogador.vidaAtual <= 0;
    botaoAbandonarRaid.disabled = false;
    atualizarBotaoIniciarRaid();
}
async function recarregarClan() {
    estadoClan = await obterClan();
    atualizarPainelClan();
}
async function recarregarRaid() {
    raidAtiva = await obterRaid();
    atualizarPainelRaid();
}
async function moverJogador(dx, dy) {
    if (zonaCarregando || movimentoEmAndamento || interacaoNpcEmAndamento)
        return;
    const nx = jogador.x + dx;
    const ny = jogador.y + dy;
    if (nx < 0 || ny < 0 || nx >= mapa.largura || ny >= mapa.altura)
        return;
    if (TILES_IMPASSAVEIS.has(mapa.tiles[ny][nx]))
        return;
    const agora = Date.now();
    if (agora - ultimoMovimento < INTERVALO_MOVIMENTO_MS)
        return;
    ultimoMovimento = agora;
    const token = obterToken();
    if (!token || !personagemAtivo) {
        window.location.assign("/");
        return;
    }
    movimentoEmAndamento = true;
    atualizarInteracoes();
    try {
        if (!(await enviarMovimento(URL_API, token, nx, ny))) {
            aviso.textContent = "O servidor recusou o movimento.";
            return;
        }
        jogador.x = nx;
        jogador.y = ny;
        const entrouNaAreaPk = !dentroDaAreaPk && dentroDaAreaPkAtual();
        dentroDaAreaPk = dentroDaAreaPkAtual();
        atualizarAvisoPk();
        atualizarPainelBossMundi();
        aviso.textContent = entrouNaAreaPk ? "Atenção: você entrou na área PK." : "";
        desenhar();
    }
    catch {
        aviso.textContent = "Não foi possível validar o movimento com o servidor.";
    }
    finally {
        movimentoEmAndamento = false;
        atualizarInteracoes();
    }
}
const TECLAS = {
    ArrowUp: [0, -1], ArrowDown: [0, 1], ArrowLeft: [-1, 0], ArrowRight: [1, 0],
};
window.addEventListener("keydown", (event) => {
    const direcao = TECLAS[event.key];
    if (direcao) {
        event.preventDefault();
        void moverJogador(direcao[0], direcao[1]);
    }
});
canvas.addEventListener("click", aoClicarNoMapa);
seletorDestino.addEventListener("change", atualizarInteracoes);
botaoSave.addEventListener("click", async () => {
    if (botaoSave.disabled)
        return;
    interacaoNpcEmAndamento = true;
    atualizarInteracoes();
    try {
        mundo.personagem = await salvarPonto();
        aviso.textContent = `Respawn salvo em ${mundo.zonaAtual.nome}.`;
        atualizarInteracoes();
    }
    catch (erro) {
        aviso.textContent = erro instanceof ErroApi ? erro.message : "Não foi possível salvar o respawn.";
    }
    finally {
        interacaoNpcEmAndamento = false;
        atualizarInteracoes();
    }
});
botaoTeleporte.addEventListener("click", async () => {
    const destinoId = seletorDestino.value;
    if (botaoTeleporte.disabled || !destinoId)
        return;
    interacaoNpcEmAndamento = true;
    atualizarInteracoes();
    try {
        await aplicarEstado(await teletransportar(destinoId));
        aviso.textContent = `Você chegou a ${mundo.zonaAtual.nome}.`;
    }
    catch (erro) {
        aviso.textContent = erro instanceof ErroApi ? erro.message : "Não foi possível concluir o teleporte.";
    }
    finally {
        interacaoNpcEmAndamento = false;
        atualizarInteracoes();
    }
});
botaoLoja.addEventListener("click", async () => {
    painelLoja.hidden = !painelLoja.hidden;
    if (painelLoja.hidden)
        return;
    try {
        const itens = await carregarVitrine(URL_API);
        painelLoja.replaceChildren(...itens.map((item) => criarCardItem(item, comprar)));
    }
    catch {
        painelLoja.textContent = "Loja indisponível no momento.";
    }
});
async function comprar(itemId) {
    const token = obterToken();
    if (!token) {
        window.location.assign("/");
        return;
    }
    try {
        window.location.href = await iniciarCompra(URL_API, token, itemId);
    }
    catch {
        aviso.textContent = "Não foi possível iniciar o pagamento.";
    }
}
async function iniciar() {
    if (!obterToken()) {
        window.location.replace("/");
        return;
    }
    try {
        const conta = await obterConta();
        personagemAtivo = conta.personagens.find((personagem) => personagem.id === conta.personagemAtivoId) ?? null;
        if (!personagemAtivo) {
            window.location.replace("/personagens.html");
            return;
        }
        if (!personagemAtivo.zonaNatalId) {
            window.location.replace("/nascimento.html");
            return;
        }
        jogador.classe = personagemAtivo.classeId;
        identidadeJogador.textContent = `${personagemAtivo.nome} (${personagemAtivo.classeId})`;
    }
    catch (erro) {
        if (erro instanceof ErroApi && erro.status === 401)
            sessionStorage.removeItem("jogo.sessao");
        window.location.replace("/");
        return;
    }
    try {
        [manifesto, zonas, continentes, monstrosDef, itensDef, regrasJogo] = await Promise.all([
            json("assets/manifest.json"),
            json("dados/mapas.json"),
            json("dados/continentes.json"),
            json("dados/monstros.json"),
            json("dados/itens.json"),
            json("dados/jogabilidade.json"),
        ]);
        progressoJogador = await obterProgresso();
        await aplicarEstado(await respawn());
        await Promise.all([recarregarClan(), recarregarRaid()]);
        await atualizarBossesMundi();
        atualizarPainelProgresso();
        void iniciarPonteNativa((runtime) => {
            estadoPlataforma.hidden = false;
            estadoPlataforma.textContent =
                `Android · ${runtime.screenWidthPx}×${runtime.screenHeightPx} · ${runtime.density.toFixed(1)}× · ativo`;
        }, ({ isActive }) => {
            const estadoAtual = estadoPlataforma.textContent.split(" · ").slice(0, 3).join(" · ");
            estadoPlataforma.textContent = `${estadoAtual} · ${isActive ? "ativo" : "suspenso"}`;
        }).catch((erro) => {
            console.error("Não foi possível iniciar a ponte nativa Capacitor.", erro);
            estadoPlataforma.hidden = false;
            estadoPlataforma.textContent = "Recursos nativos indisponíveis";
        });
        void conectarMultiplayer();
        window.setInterval(() => {
            void atualizarBossesMundi().catch((erro) => {
                aviso.textContent = erro instanceof Error ? erro.message : "Não foi possível atualizar os minibosses.";
            });
        }, 15000);
    }
    catch (erro) {
        aviso.textContent = erro instanceof ErroApi ? erro.message : "Não foi possível iniciar o mundo.";
    }
}
void iniciar();
