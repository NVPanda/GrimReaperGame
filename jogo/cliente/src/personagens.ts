import "./theme.js";
import {
  criarPersonagem,
  ErroApi,
  obterConta,
  obterToken,
  sair,
  selecionarPersonagem,
  type DadosConta,
  type Personagem,
} from "./auth.js";

const API = window.location.origin;
const mensagem = document.getElementById("mensagem") as HTMLElement;
const lista = document.getElementById("personagens") as HTMLElement;
const form = document.getElementById("criar-personagem") as HTMLFormElement;
const seletorClasse = form.elements.namedItem("classeId") as HTMLSelectElement;
let conta: DadosConta;
let enviando = false;

function informar(texto: string): void {
  mensagem.textContent = texto;
}

function mensagemErro(erro: unknown): string {
  if (erro instanceof ErroApi) return erro.message;
  if (erro instanceof Error && erro.message === "Failed to fetch") {
    return "Não foi possível conectar ao servidor.";
  }
  return "Ocorreu um erro inesperado. Tente novamente.";
}

function abrirJornada(personagem: Personagem): void {
  window.location.assign(personagem.zonaNatalId ? "/jogo.html" : "/nascimento.html");
}

function renderizarPersonagens(): void {
  lista.replaceChildren();
  if (conta.personagens.length === 0) {
    const vazio = document.createElement("p");
    vazio.className = "muted";
    vazio.textContent = "Você ainda não criou um personagem.";
    lista.appendChild(vazio);
  }

  for (const personagem of conta.personagens) {
    const linha = document.createElement("div");
    linha.className = "character";
    const descricao = document.createElement("span");
    const estadoNascimento = personagem.zonaNatalId
      ? `Nascimento: ${personagem.continenteNatalId} · Respawn: ${personagem.zonaSalvaId ?? personagem.zonaNatalId} · ${personagem.saldoMc} MC`
      : "Ainda não escolheu o local de nascimento";
    descricao.textContent = `${personagem.nome} — ${personagem.classeId} · ${estadoNascimento}`;
    const botao = document.createElement("button");
    const ativo = personagem.id === conta.personagemAtivoId;
    botao.type = "button";
    botao.textContent = ativo ? "Continuar" : "Selecionar";
    botao.disabled = enviando;
    botao.addEventListener("click", () => void escolherPersonagem(personagem));
    linha.append(descricao, botao);
    lista.appendChild(linha);
  }

  form.hidden = conta.personagens.length >= 5;
  seletorClasse.disabled = enviando;
}

async function atualizarConta(): Promise<void> {
  if (!obterToken()) {
    window.location.replace("/");
    return;
  }
  try {
    conta = await obterConta();
  } catch (erro) {
    if (erro instanceof ErroApi && erro.status === 401) {
      sessionStorage.removeItem("jogo.sessao");
      window.location.replace("/");
      return;
    }
    informar(mensagemErro(erro));
    return;
  }
  document.getElementById("nome-conta")!.textContent = conta.conta.nomeExibicao;
  document.getElementById("email-conta")!.textContent = conta.conta.email ?? "Conta vinculada a rede social";
  renderizarPersonagens();
}

async function escolherPersonagem(personagem: Personagem): Promise<void> {
  if (enviando) return;
  enviando = true;
  renderizarPersonagens();
  try {
    informar(`Selecionando ${personagem.nome}…`);
    conta = await selecionarPersonagem(personagem.id);
    const selecionado = conta.personagens.find((p) => p.id === conta.personagemAtivoId);
    if (!selecionado) throw new Error("O personagem ativo não foi retornado pelo servidor.");
    abrirJornada(selecionado);
  } catch (erro) {
    informar(mensagemErro(erro));
    enviando = false;
    renderizarPersonagens();
  }
}

async function carregarClasses(): Promise<void> {
  const response = await fetch(`${API}/dados/classes.json`);
  if (!response.ok) throw new Error("Não foi possível carregar as classes.");
  const classes = await response.json() as Array<{ id: string; nome: string }>;
  for (const classe of classes) {
    const option = document.createElement("option");
    option.value = classe.id;
    option.textContent = classe.nome;
    seletorClasse.appendChild(option);
  }
}

form.addEventListener("submit", async (event) => {
  event.preventDefault();
  if (enviando) return;
  const dados = new FormData(form);
  enviando = true;
  renderizarPersonagens();
  try {
    informar("Criando personagem…");
    const personagem = await criarPersonagem(String(dados.get("nome")), String(dados.get("classeId")));
    conta = await selecionarPersonagem(personagem.id);
    const selecionado = conta.personagens.find((p) => p.id === conta.personagemAtivoId);
    if (!selecionado) throw new Error("O personagem criado não foi retornado pelo servidor.");
    abrirJornada(selecionado);
  } catch (erro) {
    informar(mensagemErro(erro));
    enviando = false;
    renderizarPersonagens();
  }
});

document.getElementById("logout")!.addEventListener("click", async () => {
  try {
    await sair();
    window.location.replace("/");
  } catch (erro) {
    informar(mensagemErro(erro));
  }
});

async function iniciar(): Promise<void> {
  if (!obterToken()) {
    window.location.replace("/");
    return;
  }
  try {
    await Promise.all([atualizarConta(), carregarClasses()]);
  } catch (erro) {
    informar(mensagemErro(erro));
  }
}

void iniciar();
