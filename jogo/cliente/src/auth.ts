import { API_BASE_URL } from "./api-config.js";

export interface Personagem {
  id: string;
  nome: string;
  classeId: string;
  continenteNatalId: string | null;
  zonaNatalId: string | null;
  zonaSalvaId: string | null;
  zonaAtualId: string | null;
  saldoMc: number;
}

export interface ZonaMundo {
  id: string;
  nome: string;
  continenteId: string;
  tipo: string;
}

export interface DestinoTeleporte {
  zonaId: string;
  nome: string;
  continenteId: string;
  tarifaMc: number;
}

export interface EstadoMundo {
  personagem: Personagem;
  zonaAtual: ZonaMundo;
  saldoMc: number;
  destinos: DestinoTeleporte[];
}

export interface ProgressoPersonagem {
  personagemId: string;
  nivel: number;
  experiencia: number;
  inventario: Record<string, number>;
  equipamentos: Record<string, string>;
  missoesAtivas: Record<string, number>;
  vidaAtual: number;
}

export interface MembroClan {
  id: string;
  nome: string;
}

export interface Clan {
  id: string;
  nome: string;
  codigoConvite: string;
}

export interface EstadoClan {
  clan: Clan | null;
  membros: MembroClan[];
}

export interface Raid {
  id: string;
  zonaId: string;
  nivelChefe: number;
  vidaChefe: number;
  status: string;
  membros: string[];
  grupoDerrotado: boolean;
}

export interface ResultadoPvP {
  dano: number;
  vidaRestante: number;
  derrotado: boolean;
  equipamentoPerdido: string | null;
  saqueId: string | null;
  expiraEmEpoch: number | null;
  infracoesForaDaArea: number | null;
}

export interface EstadoBossMundi {
  id: string;
  nome: string;
  zonaId: string;
  centro: number[];
  raio: number;
  vidaAtual: number;
  vidaMaxima: number;
  proximaAparicaoEpoch: number | null;
  derrotadoPor: string | null;
  equipeDerrotou: string | null;
  derrotadoEmEpoch: number | null;
}

export interface ResultadoAtaqueBossMundi {
  boss: EstadoBossMundi;
  dano: number;
  derrotado: boolean;
}

export interface ResultadoCombate {
  monstroId: string;
  hpRestante: number;
  dano: number;
  derrotado: boolean;
  experienciaGanha: number;
  nivelAtual: number;
  drops: { itemId: string; quantidade: number }[];
}

export interface ContaPublica {
  id: string;
  email: string | null;
  nomeExibicao: string;
}

export interface Sessao {
  acesso: string;
  tipo: "Bearer";
  expiraEmSegundos: number;
  conta: ContaPublica;
  personagens: Personagem[];
  personagemAtivoId: string | null;
}

export interface DadosConta {
  conta: ContaPublica;
  personagens: Personagem[];
  personagemAtivoId: string | null;
}

const API = API_BASE_URL;
const CHAVE_SESSAO = "jogo.sessao";

export class ErroApi extends Error {
  constructor(message: string, readonly status: number) {
    super(message);
  }
}

export function obterToken(): string | null {
  const salvo = sessionStorage.getItem(CHAVE_SESSAO);
  if (!salvo) return null;
  try {
    const sessao = JSON.parse(salvo) as Sessao;
    return typeof sessao.acesso === "string" ? sessao.acesso : null;
  } catch {
    sessionStorage.removeItem(CHAVE_SESSAO);
    return null;
  }
}

export function guardarSessao(sessao: Sessao): void {
  sessionStorage.setItem(CHAVE_SESSAO, JSON.stringify(sessao));
}

async function requisicao<T>(rota: string, dados?: unknown): Promise<T> {
  const token = obterToken();
  const headers: Record<string, string> = {};
  if (dados !== undefined) headers["Content-Type"] = "application/json";
  if (token) headers.Authorization = `Bearer ${token}`;

  const response = await fetch(`${API}${rota}`, {
    method: dados === undefined ? "GET" : "POST",
    headers,
    body: dados === undefined ? undefined : JSON.stringify(dados),
    credentials: "same-origin",
  });
  if (response.status === 204) return undefined as T;
  const body = await response.json().catch(() => ({})) as { erro?: string };
  if (!response.ok) throw new ErroApi(body.erro ?? "A solicitação não foi aceita.", response.status);
  return body as T;
}

export async function cadastrar(email: string, senha: string): Promise<Sessao> {
  const sessao = await requisicao<Sessao>("/api/auth/register", { email, senha });
  guardarSessao(sessao);
  return sessao;
}

export async function entrar(email: string, senha: string): Promise<Sessao> {
  const sessao = await requisicao<Sessao>("/api/auth/login", { email, senha });
  guardarSessao(sessao);
  return sessao;
}

export async function trocarCodigoOAuth(codigo: string): Promise<Sessao> {
  const sessao = await requisicao<Sessao>("/api/auth/oauth/exchange", { codigo });
  guardarSessao(sessao);
  return sessao;
}

export function obterConta(): Promise<DadosConta> {
  return requisicao<DadosConta>("/api/auth/me");
}

export function definirNascimento(continenteId: string, zonaId: string): Promise<EstadoMundo> {
  return requisicao<EstadoMundo>("/api/mundo/nascimento", { continenteId, zonaId });
}

export function respawn(): Promise<EstadoMundo> {
  return requisicao<EstadoMundo>("/api/mundo/respawn", {});
}

export function obterEstadoMundo(): Promise<EstadoMundo> {
  return requisicao<EstadoMundo>("/api/mundo/estado");
}

export function salvarPonto(): Promise<Personagem> {
  return requisicao<Personagem>("/api/mundo/salvar", {});
}

export function teletransportar(destinoZonaId: string): Promise<EstadoMundo> {
  return requisicao<EstadoMundo>("/api/mundo/teleporte", { destinoZonaId });
}

export async function obterTicketMultiplayer(): Promise<string> {
  const resposta = await requisicao<{ ticket: string }>("/api/multiplayer/ticket", {});
  return resposta.ticket;
}

export function obterProgresso(): Promise<ProgressoPersonagem> {
  return requisicao<ProgressoPersonagem>("/api/jogabilidade/estado");
}

export function atacarMonstro(monstroId: string): Promise<ResultadoCombate> {
  return requisicao<ResultadoCombate>("/api/jogabilidade/atacar", { monstroId });
}

export function venderItem(itemId: string, quantidade: number): Promise<ProgressoPersonagem> {
  return requisicao<ProgressoPersonagem>("/api/jogabilidade/vender", { itemId, quantidade });
}

export function obterClan(): Promise<EstadoClan> {
  return requisicao<EstadoClan>("/api/jogabilidade/clans/meu");
}

export function criarClan(nome: string): Promise<Clan> {
  return requisicao<Clan>("/api/jogabilidade/clans/criar", { nome });
}

export function entrarClan(codigoConvite: string): Promise<Clan> {
  return requisicao<Clan>("/api/jogabilidade/clans/entrar", { codigoConvite });
}

export function iniciarRaid(membros: string[]): Promise<Raid> {
  return requisicao<Raid>("/api/jogabilidade/raid/iniciar", { membros });
}

export async function obterRaid(): Promise<Raid | null> {
  const estado = await requisicao<{ raid: Raid | null }>("/api/jogabilidade/raid");
  return estado.raid;
}

export function atacarChefe(): Promise<Raid> {
  return requisicao<Raid>("/api/jogabilidade/raid/atacar-chefe", {});
}

export function abandonarRaid(): Promise<{ status: string; motivo: string }> {
  return requisicao<{ status: string; motivo: string }>("/api/jogabilidade/raid/sair", {});
}

export function atacarJogador(personagemId: string): Promise<ResultadoPvP> {
  return requisicao<ResultadoPvP>("/api/jogabilidade/pk/atacar", { personagemId });
}

export async function saquearPvP(saqueId: string): Promise<string> {
  const resposta = await requisicao<{ itemId: string }>("/api/jogabilidade/pk/sacar", { saqueId });
  return resposta.itemId;
}

export function obterBossesMundi(): Promise<EstadoBossMundi[]> {
  return requisicao<EstadoBossMundi[]>("/api/jogabilidade/bosses-mundi");
}

export function atacarBossMundi(bossId: string): Promise<ResultadoAtaqueBossMundi> {
  return requisicao<ResultadoAtaqueBossMundi>("/api/jogabilidade/bosses-mundi/atacar", { bossId });
}

export function criarPersonagem(nome: string, classeId: string): Promise<Personagem> {
  return requisicao<Personagem>("/api/auth/characters", { nome, classeId });
}

export async function selecionarPersonagem(personagemId: string): Promise<DadosConta> {
  const dados = await requisicao<DadosConta>("/api/auth/characters/select", { personagemId });
  const sessao = lerSessao();
  if (sessao) guardarSessao({ ...sessao, personagens: dados.personagens, personagemAtivoId: dados.personagemAtivoId });
  return dados;
}

export async function sair(): Promise<void> {
  try {
    await requisicao<void>("/api/auth/logout", {});
  } finally {
    sessionStorage.removeItem(CHAVE_SESSAO);
  }
}

export function lerSessao(): Sessao | null {
  const salvo = sessionStorage.getItem(CHAVE_SESSAO);
  if (!salvo) return null;
  try {
    return JSON.parse(salvo) as Sessao;
  } catch {
    sessionStorage.removeItem(CHAVE_SESSAO);
    return null;
  }
}

export function iniciarLoginSocial(provedor: string): void {
  window.location.assign(`${API}/api/auth/social/${encodeURIComponent(provedor)}`);
}
