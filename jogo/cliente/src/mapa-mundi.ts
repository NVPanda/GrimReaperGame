import "./theme.js";
import { ErroApi, obterBossesMundi, obterToken, type EstadoBossMundi } from "./auth.js";

interface ZonaMundo {
  id: string;
  nome: string;
  cont: string;
  tipo: "cidade" | "campo" | "masmorra";
  nivel: [number, number];
}

interface Continente {
  id: string;
  nome: string;
  ordem: number;
  nivel: [number, number];
}

interface PosicaoZona {
  zona: ZonaMundo;
  x: number;
  y: number;
  boss?: EstadoBossMundi;
}

const canvas = document.getElementById("map-world") as HTMLCanvasElement;
const contexto = canvas.getContext("2d");
const aviso = document.getElementById("map-message") as HTMLElement;
const detalhe = document.getElementById("selected-region") as HTMLElement;
const eventos = document.getElementById("boss-events") as HTMLElement;
const anchors: Record<string, [number, number]> = {
  humano: [220, 210],
  elfico: [510, 170],
  anao: [850, 250],
  driade: [370, 475],
  gelo: [700, 515],
  demoniaco: [990, 465],
};

let zonas: ZonaMundo[] = [];
let continentes: Continente[] = [];
let bosses: EstadoBossMundi[] = [];
let posicoes: PosicaoZona[] = [];
let selecionada: PosicaoZona | null = null;

if (!obterToken()) window.location.replace("/");

async function json<T>(path: string): Promise<T> {
  const response = await fetch(path);
  if (!response.ok) throw new Error(`Não foi possível carregar ${path}.`);
  return response.json() as Promise<T>;
}

function formatoDuracao(segundos: number): string {
  const restante = Math.max(0, segundos);
  const horas = Math.floor(restante / 3600);
  const minutos = Math.floor((restante % 3600) / 60);
  const segundosRestantes = restante % 60;
  return `${horas}h ${String(minutos).padStart(2, "0")}m ${String(segundosRestantes).padStart(2, "0")}s`;
}

function coordenadasZonas(): PosicaoZona[] {
  const offsets: Record<ZonaMundo["tipo"], [number, number][]> = {
    cidade: [[0, 0]],
    campo: [[-80, 60], [80, 60]],
    masmorra: [[0, 130]],
  };
  return continentes.flatMap((continente) => {
    const anchor = anchors[continente.id];
    if (!anchor) return [];
    const locais = zonas.filter((zona) => zona.cont === continente.id);
    return locais.map((zona) => {
      const indice = locais.filter((item) => item.tipo === zona.tipo).findIndex((item) => item.id === zona.id);
      const [dx, dy] = offsets[zona.tipo][indice] ?? [0, 0];
      return {
        zona,
        x: anchor[0] + dx,
        y: anchor[1] + dy,
        boss: bosses.find((boss) => boss.zonaId === zona.id),
      };
    });
  });
}

function desenharContinente(continente: Continente, cx: number, cy: number): void {
  const cores: Record<string, string> = {
    humano: "#936e4c",
    elfico: "#387a66",
    anao: "#89724e",
    driade: "#4d8251",
    gelo: "#6f9fac",
    demoniaco: "#874b4d",
  };
  const cor = cores[continente.id] ?? "#56717c";
  contexto!.save();
  contexto!.beginPath();
  for (let i = 0; i <= 48; i++) {
    const angulo = (i / 48) * Math.PI * 2;
    const irregularidade = 1 + 0.14 * Math.sin(3 * angulo + continente.ordem)
      + 0.08 * Math.cos(5 * angulo - continente.ordem);
    const x = cx + Math.cos(angulo) * 145 * irregularidade;
    const y = cy + Math.sin(angulo) * 95 * irregularidade;
    if (i === 0) contexto!.moveTo(x, y);
    else contexto!.lineTo(x, y);
  }
  contexto!.closePath();
  contexto!.fillStyle = cor;
  contexto!.globalAlpha = 0.45;
  contexto!.fill();
  contexto!.globalAlpha = 1;
  contexto!.strokeStyle = "#9bd7c4";
  contexto!.lineWidth = 2;
  contexto!.stroke();

  contexto!.fillStyle = "#d8c89a";
  contexto!.font = "700 15px system-ui";
  contexto!.textAlign = "center";
  contexto!.fillText(continente.nome.toUpperCase(), cx, cy - 112);
  contexto!.fillStyle = "#b2c6c4";
  contexto!.font = "12px system-ui";
  contexto!.fillText(`NÍVEIS ${continente.nivel[0]}–${continente.nivel[1]}`, cx, cy - 95);
  contexto!.restore();
}

function desenharRota(origem: PosicaoZona, destino: PosicaoZona): void {
  contexto!.save();
  contexto!.beginPath();
  contexto!.moveTo(origem.x, origem.y);
  contexto!.quadraticCurveTo((origem.x + destino.x) / 2, (origem.y + destino.y) / 2 - 16, destino.x, destino.y);
  contexto!.strokeStyle = "rgba(207, 223, 201, .35)";
  contexto!.lineWidth = 2;
  contexto!.setLineDash([6, 7]);
  contexto!.stroke();
  contexto!.restore();
}

function desenharMarcador(posicao: PosicaoZona): void {
  const selecionado = selecionada?.zona.id === posicao.zona.id;
  const cor = posicao.zona.tipo === "cidade" ? "#65e9dc"
    : posicao.zona.tipo === "masmorra" ? "#d88bff" : "#f1c56e";
  if (posicao.boss) {
    contexto!.save();
    contexto!.beginPath();
    contexto!.arc(posicao.x, posicao.y, 36, 0, Math.PI * 2);
    contexto!.fillStyle = posicao.boss.vidaAtual > 0 ? "rgba(255, 80, 105, .16)" : "rgba(255, 80, 105, .08)";
    contexto!.fill();
    contexto!.strokeStyle = posicao.boss.vidaAtual > 0 ? "#ff5a70" : "rgba(255, 90, 112, .65)";
    contexto!.lineWidth = 3;
    contexto!.setLineDash(posicao.boss.vidaAtual > 0 ? [] : [7, 6]);
    contexto!.stroke();
    contexto!.restore();
  }
  contexto!.beginPath();
  contexto!.arc(posicao.x, posicao.y, selecionado ? 9 : 7, 0, Math.PI * 2);
  contexto!.fillStyle = cor;
  contexto!.fill();
  contexto!.strokeStyle = selecionado ? "#ffffff" : "#16242a";
  contexto!.lineWidth = selecionado ? 3 : 2;
  contexto!.stroke();
  contexto!.fillStyle = "#f4f2e8";
  contexto!.font = "12px system-ui";
  contexto!.textAlign = "center";
  contexto!.fillText(posicao.zona.nome, posicao.x, posicao.y + 23);
}

function desenharMapa(): void {
  if (!contexto) return;
  contexto.clearRect(0, 0, canvas.width, canvas.height);
  contexto.fillStyle = "#12232a";
  contexto.fillRect(0, 0, canvas.width, canvas.height);
  contexto.save();
  contexto.strokeStyle = "rgba(109, 190, 194, .08)";
  contexto.lineWidth = 1;
  for (let x = 0; x < canvas.width; x += 40) {
    contexto.beginPath(); contexto.moveTo(x, 0); contexto.lineTo(x, canvas.height); contexto.stroke();
  }
  for (let y = 0; y < canvas.height; y += 40) {
    contexto.beginPath(); contexto.moveTo(0, y); contexto.lineTo(canvas.width, y); contexto.stroke();
  }
  contexto.restore();
  contexto.fillStyle = "rgba(118, 216, 202, .12)";
  contexto.font = "700 38px system-ui";
  contexto.textAlign = "left";
  contexto.fillText("REINO DE GRIMREAP", 34, 56);
  contexto.fillStyle = "#91aaa8";
  contexto.font = "13px system-ui";
  contexto.fillText("CONTINENTES // CARTA DE NAVEGAÇÃO", 36, 78);

  for (const continente of continentes) {
    const anchor = anchors[continente.id];
    if (anchor) desenharContinente(continente, anchor[0], anchor[1]);
  }
  for (const continente of continentes) {
    const cidade = posicoes.find((p) => p.zona.cont === continente.id && p.zona.tipo === "cidade");
    if (!cidade) continue;
    for (const destino of posicoes.filter((p) => p.zona.cont === continente.id && p.zona.id !== cidade.zona.id)) {
      desenharRota(cidade, destino);
    }
  }
  posicoes.forEach(desenharMarcador);
}

function atualizarDetalhe(): void {
  detalhe.replaceChildren();
  if (!selecionada) {
    detalhe.textContent = "Selecione um marcador no mapa.";
    return;
  }
  const titulo = document.createElement("strong");
  titulo.textContent = selecionada.zona.nome;
  const dados = document.createElement("p");
  dados.textContent = `${selecionada.zona.tipo} · nível ${selecionada.zona.nivel[0]}–${selecionada.zona.nivel[1]}`;
  detalhe.append(titulo, dados);
  if (selecionada.boss) {
    const boss = selecionada.boss;
    const estado = document.createElement("p");
    estado.textContent = boss.vidaAtual > 0
      ? `${boss.nome} está ativo · HP ${boss.vidaAtual}/${boss.vidaMaxima}. Região circular marcada no mapa.`
      : `${boss.nome} retornará em ${formatoDuracao((boss.proximaAparicaoEpoch ?? 0) - Math.floor(Date.now() / 1000))}.`;
    detalhe.appendChild(estado);
  }
  const instrucao = document.createElement("small");
  instrucao.textContent = "Use o NPC Teleporte dentro do jogo para viajar até essa região.";
  detalhe.appendChild(instrucao);
}

function atualizarEventos(): void {
  eventos.replaceChildren();
  const agora = Math.floor(Date.now() / 1000);
  for (const boss of bosses) {
    const cartao = document.createElement("section");
    cartao.className = "boss-entry";
    const titulo = document.createElement("h3");
    titulo.textContent = boss.nome;
    const zona = zonas.find((item) => item.id === boss.zonaId);
    const local = document.createElement("small");
    local.textContent = zona?.nome ?? boss.zonaId;
    const estado = document.createElement("p");
    if (boss.vidaAtual > 0) {
      estado.className = "boss-active";
      estado.textContent = `ATIVO · ${boss.vidaAtual}/${boss.vidaMaxima} HP`;
    } else {
      estado.className = "boss-waiting";
      const faltam = (boss.proximaAparicaoEpoch ?? agora) - agora;
      estado.textContent = `Retorna em ${formatoDuracao(faltam)}`;
    }
    cartao.append(titulo, local, estado);
    if (boss.derrotadoPor) {
      const anuncio = document.createElement("p");
      const vencedor = boss.equipeDerrotou
        ? `equipe ${boss.equipeDerrotou}`
        : boss.derrotadoPor;
      const falta = (boss.proximaAparicaoEpoch ?? agora) - agora;
      anuncio.textContent = `${boss.nome} derrotado por ${vencedor} (${boss.derrotadoPor}); retorna em ${formatoDuracao(falta)}.`;
      cartao.appendChild(anuncio);
    }
    eventos.appendChild(cartao);
  }
}

async function carregarEstadosBoss(): Promise<void> {
  bosses = await obterBossesMundi();
  posicoes = coordenadasZonas();
  selecionada = posicoes.find((p) => p.zona.id === new URLSearchParams(location.search).get("zona")) ?? selecionada;
  desenharMapa();
  atualizarEventos();
  atualizarDetalhe();
}

canvas.addEventListener("click", (event) => {
  const rect = canvas.getBoundingClientRect();
  const x = (event.clientX - rect.left) * (canvas.width / rect.width);
  const y = (event.clientY - rect.top) * (canvas.height / rect.height);
  selecionada = posicoes.find((posicao) => Math.hypot(x - posicao.x, y - posicao.y) <= 44) ?? null;
  desenharMapa();
  atualizarDetalhe();
});

async function iniciar(): Promise<void> {
  if (!contexto) {
    aviso.textContent = "Seu navegador não conseguiu iniciar o mapa.";
    return;
  }
  try {
    [zonas, continentes] = await Promise.all([
      json<ZonaMundo[]>("dados/mapas.json"),
      json<Continente[]>("dados/continentes.json"),
    ]);
    continentes.sort((a, b) => a.ordem - b.ordem);
    await carregarEstadosBoss();
    aviso.textContent = "Mapa atualizado. Os círculos vermelhos indicam as regiões de miniboss.";
    window.setInterval(() => {
      void carregarEstadosBoss().catch((erro: unknown) => {
        aviso.textContent = erro instanceof Error ? erro.message : "Não foi possível atualizar os eventos mundiais.";
      });
    }, 15_000);
  } catch (erro) {
    aviso.textContent = erro instanceof ErroApi
      ? erro.message
      : erro instanceof Error ? erro.message : "Não foi possível carregar o mapa do Reino.";
  }
}

void iniciar();
