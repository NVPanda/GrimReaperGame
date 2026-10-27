# DDD: contextos, linguagem ubíqua e mapa de contexto

## Linguagem ubíqua

| Termo | Significado no projeto |
|---|---|
| **Item da loja** | Produto vendável com preço fixo. Não é item de combate. |
| **Vitrine** | Lista de itens vendáveis em um momento. |
| **Pedido** | Intenção de compra de um jogador por um item, com preço congelado. |
| **Confirmação** | Resposta do provedor de pagamento sobre um pedido. |
| **Liberação** | Concessão do item ao jogador após confirmação válida. |
| **Movimento** | Intenção do jogador de mudar de posição, validada pelo servidor. |
| **Veredito** | Resultado da validação de uma ação (aceita ou rejeitada com motivo). |
| **Score de risco** | Número de 0 a 1 produzido pela IA. Não é punição. |
| **Revisão** | Análise humana de um alerta. É a única etapa que pode levar a sanção. |
| **Mitrum Coin (MC)** | Moeda interna. Fase 2. |

## Contextos delimitados

**Loja** (`jogo.loja`): catálogo, vitrine, pedido e regras de venda.
- Subdomínio núcleo.
- Entidades: `ItemLoja`, `Pedido`. Agregado raiz: `Pedido` (controla sua própria situação).
- Serviço de aplicação: `ServicoCheckout`.

**Pagamentos** (dentro de `jogo.loja`, camada de infraestrutura): provedores externos.
- Subdomínio de suporte. Acessado só pela porta `PortaPagamento`.
- Não conhece regra de venda. Só traduz o protocolo do provedor para `ConfirmacaoPagamento`.

**Anticheat** (`jogo.anticheat`): validação de movimento, métricas e política de revisão.
- Subdomínio de suporte, mas crítico para a integridade.
- Não altera diretamente o estado do jogo. Produz vereditos e alertas.

**Compartilhado** (`jogo.compartilhado`): validação de entrada e tipos comuns.
- Sem regra de negócio. Não depende de outros contextos.

**Segurança de API** (`jogo.seguranca`): plugins HTTP (cabeçalhos, CORS, JWT, limite de taxa, erros).
- Camada de borda. Não contém regra de negócio.

**Identidade** (`jogo.identidade`): conta, credenciais, identidades sociais, sessão e personagem.
- Contas e personagens são persistidos em SQLite. Senhas usam Argon2id; provedores sociais são adaptadores OAuth/OpenID.
- Uma conta tem no máximo cinco personagens e uma única sessão válida, que aponta para no máximo um personagem ativo.
- O token identifica a conta; ações de jogo usam o personagem ativo associado ao ID da sessão.
- A sessão anterior é revogada quando um novo login é efetuado.

## Mapa de contexto

```
Cliente web/mobile
      │  HTTP + JWT / OAuth
      ▼
Segurança de API ──► Identidade ──► SQLite
       │               │
       │               └──► Google / Discord / Facebook / Steam
       ├──────────────► Loja ──► (porta) PortaPagamento ──► Mercado Pago
                       │
                       └──► (evento futuro) Servidor de jogo: liberar item
Anticheat ◄── ações de movimento (Segurança de API chama)
   │
   └──► (porta) PortaAnaliseIA ──► modelo externo (só métricas anonimizadas)
```

Relação entre Loja e Anticheat: independentes. A Loja não consulta o Anticheat, e o Anticheat não altera saldo nem item.
