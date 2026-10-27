import { API_BASE_URL } from "./api-config.js";
const API = API_BASE_URL;
const CHAVE_SESSAO = "jogo.sessao";
export class ErroApi extends Error {
    constructor(message, status) {
        super(message);
        this.status = status;
    }
}
export function obterToken() {
    const salvo = sessionStorage.getItem(CHAVE_SESSAO);
    if (!salvo)
        return null;
    try {
        const sessao = JSON.parse(salvo);
        return typeof sessao.acesso === "string" ? sessao.acesso : null;
    }
    catch {
        sessionStorage.removeItem(CHAVE_SESSAO);
        return null;
    }
}
export function guardarSessao(sessao) {
    sessionStorage.setItem(CHAVE_SESSAO, JSON.stringify(sessao));
}
async function requisicao(rota, dados) {
    const token = obterToken();
    const headers = {};
    if (dados !== undefined)
        headers["Content-Type"] = "application/json";
    if (token)
        headers.Authorization = `Bearer ${token}`;
    const response = await fetch(`${API}${rota}`, {
        method: dados === undefined ? "GET" : "POST",
        headers,
        body: dados === undefined ? undefined : JSON.stringify(dados),
        credentials: "same-origin",
    });
    if (response.status === 204)
        return undefined;
    const body = await response.json().catch(() => ({}));
    if (!response.ok)
        throw new ErroApi(body.erro ?? "A solicitação não foi aceita.", response.status);
    return body;
}
export async function cadastrar(email, senha) {
    const sessao = await requisicao("/api/auth/register", { email, senha });
    guardarSessao(sessao);
    return sessao;
}
export async function entrar(email, senha) {
    const sessao = await requisicao("/api/auth/login", { email, senha });
    guardarSessao(sessao);
    return sessao;
}
export async function trocarCodigoOAuth(codigo) {
    const sessao = await requisicao("/api/auth/oauth/exchange", { codigo });
    guardarSessao(sessao);
    return sessao;
}
export function obterConta() {
    return requisicao("/api/auth/me");
}
export function definirNascimento(continenteId, zonaId) {
    return requisicao("/api/mundo/nascimento", { continenteId, zonaId });
}
export function respawn() {
    return requisicao("/api/mundo/respawn", {});
}
export function obterEstadoMundo() {
    return requisicao("/api/mundo/estado");
}
export function salvarPonto() {
    return requisicao("/api/mundo/salvar", {});
}
export function teletransportar(destinoZonaId) {
    return requisicao("/api/mundo/teleporte", { destinoZonaId });
}
export async function obterTicketMultiplayer() {
    const resposta = await requisicao("/api/multiplayer/ticket", {});
    return resposta.ticket;
}
export function obterProgresso() {
    return requisicao("/api/jogabilidade/estado");
}
export function atacarMonstro(monstroId) {
    return requisicao("/api/jogabilidade/atacar", { monstroId });
}
export function venderItem(itemId, quantidade) {
    return requisicao("/api/jogabilidade/vender", { itemId, quantidade });
}
export function obterClan() {
    return requisicao("/api/jogabilidade/clans/meu");
}
export function criarClan(nome) {
    return requisicao("/api/jogabilidade/clans/criar", { nome });
}
export function entrarClan(codigoConvite) {
    return requisicao("/api/jogabilidade/clans/entrar", { codigoConvite });
}
export function iniciarRaid(membros) {
    return requisicao("/api/jogabilidade/raid/iniciar", { membros });
}
export async function obterRaid() {
    const estado = await requisicao("/api/jogabilidade/raid");
    return estado.raid;
}
export function atacarChefe() {
    return requisicao("/api/jogabilidade/raid/atacar-chefe", {});
}
export function abandonarRaid() {
    return requisicao("/api/jogabilidade/raid/sair", {});
}
export function atacarJogador(personagemId) {
    return requisicao("/api/jogabilidade/pk/atacar", { personagemId });
}
export async function saquearPvP(saqueId) {
    const resposta = await requisicao("/api/jogabilidade/pk/sacar", { saqueId });
    return resposta.itemId;
}
export function obterBossesMundi() {
    return requisicao("/api/jogabilidade/bosses-mundi");
}
export function atacarBossMundi(bossId) {
    return requisicao("/api/jogabilidade/bosses-mundi/atacar", { bossId });
}
export function criarPersonagem(nome, classeId) {
    return requisicao("/api/auth/characters", { nome, classeId });
}
export async function selecionarPersonagem(personagemId) {
    const dados = await requisicao("/api/auth/characters/select", { personagemId });
    const sessao = lerSessao();
    if (sessao)
        guardarSessao({ ...sessao, personagens: dados.personagens, personagemAtivoId: dados.personagemAtivoId });
    return dados;
}
export async function sair() {
    try {
        await requisicao("/api/auth/logout", {});
    }
    finally {
        sessionStorage.removeItem(CHAVE_SESSAO);
    }
}
export function lerSessao() {
    const salvo = sessionStorage.getItem(CHAVE_SESSAO);
    if (!salvo)
        return null;
    try {
        return JSON.parse(salvo);
    }
    catch {
        sessionStorage.removeItem(CHAVE_SESSAO);
        return null;
    }
}
export function iniciarLoginSocial(provedor) {
    window.location.assign(`${API}/api/auth/social/${encodeURIComponent(provedor)}`);
}
