/**
 * Tela da loja (SDD 01). Funções puras ficam separadas do DOM para teste.
 * Preço vem sempre do servidor. O cliente só exibe.
 */
/** Formata centavos como moeda brasileira. Ex.: 1990 -> "R$ 19,90". */
export function formatarPrecoBRL(centavos) {
    return new Intl.NumberFormat("pt-BR", { style: "currency", currency: "BRL" }).format(centavos / 100);
}
/** Cria o elemento de um item. Textos são inseridos com textContent, nunca como HTML. */
export function criarCardItem(item, aoComprar) {
    const card = document.createElement("div");
    card.className = "card";
    const tipo = document.createElement("span");
    tipo.className = "tipo";
    tipo.textContent = item.tipo;
    const nome = document.createElement("h3");
    nome.textContent = item.nome;
    const descricao = document.createElement("p");
    descricao.textContent = item.descricao;
    const preco = document.createElement("span");
    preco.className = "preco";
    preco.textContent = formatarPrecoBRL(item.precoCentavos);
    const botao = document.createElement("button");
    botao.textContent = "Comprar";
    botao.addEventListener("click", () => aoComprar(item.id));
    card.append(tipo, nome, descricao, preco, botao);
    return card;
}
export async function carregarVitrine(urlApi) {
    const resposta = await fetch(`${urlApi}/api/catalogo`);
    if (!resposta.ok)
        throw new Error(`vitrine indisponível: ${resposta.status}`);
    return (await resposta.json());
}
export async function iniciarCompra(urlApi, token, itemId) {
    const resposta = await fetch(`${urlApi}/api/checkout`, {
        method: "POST",
        headers: { "Content-Type": "application/json", Authorization: `Bearer ${token}` },
        body: JSON.stringify({ itemId }),
        credentials: "omit",
    });
    if (!resposta.ok)
        throw new Error(`compra recusada: ${resposta.status}`);
    const dados = (await resposta.json());
    return dados.url_pagamento;
}
