import "./theme.js";
import { API_BASE_URL } from "./api-config.js";
import { cadastrar, entrar, ErroApi, iniciarLoginSocial, lerSessao, obterConta, obterToken, trocarCodigoOAuth, } from "./auth.js";
const API = API_BASE_URL;
const mensagem = document.getElementById("mensagem");
const social = document.querySelector(".social");
const divisorSocial = document.querySelector(".auth-divider");
const abasAutenticacao = [
    { botao: document.getElementById("tab-entrar"), painel: document.getElementById("login-form") },
    { botao: document.getElementById("tab-cadastro"), painel: document.getElementById("cadastro-form") },
];
function ativarAbaAutenticacao(indice, moverFoco = false) {
    abasAutenticacao.forEach((aba, index) => {
        const ativa = index === indice;
        aba.botao.classList.toggle("is-active", ativa);
        aba.botao.setAttribute("aria-selected", String(ativa));
        aba.botao.tabIndex = ativa ? 0 : -1;
        aba.painel.hidden = !ativa;
        if (ativa && moverFoco)
            aba.botao.focus();
    });
    mensagem.textContent = "";
}
abasAutenticacao.forEach((aba, indice) => {
    aba.botao.addEventListener("click", () => ativarAbaAutenticacao(indice));
    aba.botao.addEventListener("keydown", (event) => {
        let proximoIndice;
        if (event.key === "ArrowRight")
            proximoIndice = (indice + 1) % abasAutenticacao.length;
        if (event.key === "ArrowLeft")
            proximoIndice = (indice - 1 + abasAutenticacao.length) % abasAutenticacao.length;
        if (event.key === "Home")
            proximoIndice = 0;
        if (event.key === "End")
            proximoIndice = abasAutenticacao.length - 1;
        if (proximoIndice === undefined)
            return;
        event.preventDefault();
        ativarAbaAutenticacao(proximoIndice, true);
    });
});
function informar(texto) {
    mensagem.textContent = texto;
}
function mensagemErro(erro) {
    if (erro instanceof ErroApi)
        return erro.message;
    if (erro instanceof Error && erro.message === "Failed to fetch") {
        return "Não foi possível conectar ao servidor.";
    }
    return "Ocorreu um erro inesperado. Tente novamente.";
}
function prosseguirParaPersonagens() {
    window.location.replace("/personagens.html");
}
function definirEnvio(form, enviando, textoPadrao) {
    const botao = form.querySelector('[type="submit"]');
    botao.disabled = enviando;
    botao.setAttribute("aria-busy", String(enviando));
    botao.querySelector("span").textContent = enviando ? "Aguarde…" : textoPadrao;
}
document.querySelectorAll("[data-password-toggle]").forEach((botao) => {
    botao.addEventListener("click", () => {
        const input = document.getElementById(botao.dataset.passwordToggle);
        const mostrar = input.type === "password";
        input.type = mostrar ? "text" : "password";
        botao.setAttribute("aria-pressed", String(mostrar));
        botao.setAttribute("aria-label", mostrar ? "Ocultar senha" : "Mostrar senha");
        input.focus();
    });
});
document.getElementById("login-form").addEventListener("submit", async (event) => {
    event.preventDefault();
    const form = event.currentTarget;
    const dados = new FormData(form);
    if (form.querySelector('[type="submit"]').disabled)
        return;
    try {
        definirEnvio(form, true, "Entrar no Reino");
        informar("Entrando…");
        await entrar(String(dados.get("email")), String(dados.get("senha")));
        prosseguirParaPersonagens();
    }
    catch (erro) {
        definirEnvio(form, false, "Entrar no Reino");
        informar(mensagemErro(erro));
    }
});
document.getElementById("cadastro-form").addEventListener("submit", async (event) => {
    event.preventDefault();
    const form = event.currentTarget;
    const dados = new FormData(form);
    if (form.querySelector('[type="submit"]').disabled)
        return;
    try {
        definirEnvio(form, true, "Criar minha conta");
        informar("Criando conta…");
        await cadastrar(String(dados.get("email")), String(dados.get("senha")));
        prosseguirParaPersonagens();
    }
    catch (erro) {
        definirEnvio(form, false, "Criar minha conta");
        informar(mensagemErro(erro));
    }
});
document.querySelectorAll("[data-provider]").forEach((botao) => {
    botao.addEventListener("click", () => iniciarLoginSocial(botao.dataset.provider));
});
async function processarRetornoSocial() {
    const params = new URLSearchParams(window.location.search);
    const codigo = params.get("oauth_code");
    const erro = params.get("auth_error");
    if (!codigo && !erro)
        return false;
    window.history.replaceState({}, document.title, window.location.pathname);
    if (erro) {
        informar("O login social não foi concluído. Tente novamente.");
        return true;
    }
    try {
        informar("Concluindo login social…");
        await trocarCodigoOAuth(codigo);
        prosseguirParaPersonagens();
    }
    catch (falha) {
        informar(mensagemErro(falha));
    }
    return true;
}
async function iniciar() {
    const processouRetornoSocial = await processarRetornoSocial();
    if (processouRetornoSocial && obterToken())
        return;
    if (lerSessao()) {
        try {
            await obterConta();
            prosseguirParaPersonagens();
            return;
        }
        catch (erro) {
            if (erro instanceof ErroApi && erro.status === 401) {
                sessionStorage.removeItem("jogo.sessao");
            }
            else {
                informar(mensagemErro(erro));
            }
        }
    }
    try {
        const response = await fetch(`${API}/api/auth/providers`);
        if (!response.ok)
            throw new Error("Não foi possível consultar os provedores de login.");
        const dados = await response.json();
        document.querySelectorAll("[data-provider]").forEach((botao) => {
            botao.hidden = !dados.provedores.includes(botao.dataset.provider ?? "");
        });
        const temProvedorSocial = dados.provedores.length > 0;
        social.hidden = !temProvedorSocial;
        divisorSocial.hidden = !temProvedorSocial;
    }
    catch (erro) {
        document.querySelectorAll("[data-provider]").forEach((botao) => { botao.hidden = true; });
        social.hidden = true;
        divisorSocial.hidden = true;
        informar(mensagemErro(erro));
    }
}
void iniciar();
