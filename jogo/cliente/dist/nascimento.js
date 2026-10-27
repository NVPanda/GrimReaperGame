import "./theme.js";
import { definirNascimento, ErroApi, obterConta, obterToken, } from "./auth.js";
const continentesContainer = document.getElementById("continentes");
const mensagem = document.getElementById("mensagem");
const confirmar = document.getElementById("confirmar");
let cidadesPorContinente = new Map();
function informar(texto) {
    mensagem.textContent = texto;
}
function mensagemErro(erro) {
    if (erro instanceof ErroApi)
        return erro.message;
    if (erro instanceof Error && erro.message === "Failed to fetch") {
        return "Não foi possível conectar ao servidor.";
    }
    return "Não foi possível carregar a seleção de nascimento.";
}
async function json(url) {
    const response = await fetch(url);
    if (!response.ok)
        throw new Error(`Não foi possível carregar ${url}.`);
    return response.json();
}
function mostrarContinentes(continentes) {
    continentesContainer.replaceChildren();
    for (const continente of continentes) {
        const cidade = cidadesPorContinente.get(continente.id);
        if (!cidade)
            continue;
        const card = document.createElement("label");
        card.className = "continente";
        const radio = document.createElement("input");
        radio.type = "radio";
        radio.name = "continenteId";
        radio.value = continente.id;
        radio.required = true;
        const texto = document.createElement("span");
        const nome = document.createElement("strong");
        nome.textContent = continente.nome;
        const capital = document.createElement("span");
        capital.textContent = `${cidade.nome} · níveis ${continente.nivel[0]}–${continente.nivel[1]}`;
        const descricao = document.createElement("p");
        descricao.className = "muted";
        descricao.textContent = `Você nascerá em ${cidade.nome}. O continente natal não pode ser alterado.`;
        texto.append(nome, capital, descricao);
        card.append(radio, texto);
        continentesContainer.appendChild(card);
    }
    confirmar.disabled = continentesContainer.children.length === 0;
}
document.getElementById("nascimento-form").addEventListener("submit", async (event) => {
    event.preventDefault();
    const form = event.currentTarget;
    const continenteId = new FormData(form).get("continenteId");
    if (typeof continenteId !== "string") {
        informar("Selecione um continente.");
        return;
    }
    const cidade = cidadesPorContinente.get(continenteId);
    if (!cidade) {
        informar("Esse continente não tem uma cidade inicial disponível.");
        return;
    }
    confirmar.disabled = true;
    try {
        informar("Definindo o nascimento do personagem…");
        await definirNascimento(continenteId, cidade.id);
        window.location.replace("/jogo.html");
    }
    catch (erro) {
        if (erro instanceof ErroApi && erro.status === 409) {
            try {
                const conta = await obterConta();
                const personagem = conta.personagens.find((p) => p.id === conta.personagemAtivoId);
                if (personagem?.zonaNatalId) {
                    window.location.replace("/jogo.html");
                    return;
                }
            }
            catch (erroConta) {
                informar(mensagemErro(erroConta));
                confirmar.disabled = false;
                return;
            }
        }
        informar(mensagemErro(erro));
        confirmar.disabled = false;
    }
});
async function iniciar() {
    if (!obterToken()) {
        window.location.replace("/");
        return;
    }
    try {
        const conta = await obterConta();
        const personagem = conta.personagens.find((p) => p.id === conta.personagemAtivoId);
        if (!personagem) {
            window.location.replace("/personagens.html");
            return;
        }
        if (personagem.zonaNatalId) {
            window.location.replace("/jogo.html");
            return;
        }
        const [continentes, mapas] = await Promise.all([
            json("/dados/continentes.json"),
            json("/dados/mapas.json"),
        ]);
        cidadesPorContinente = new Map(mapas.filter((zona) => zona.tipo === "cidade").map((cidade) => [cidade.cont, cidade]));
        mostrarContinentes(continentes);
        informar("Escolha um continente. A cidade natal será o primeiro ponto de respawn.");
    }
    catch (erro) {
        informar(mensagemErro(erro));
    }
}
void iniciar();
