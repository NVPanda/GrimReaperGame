# ADR 0006: Loja com itens de melhoria de preço fixo e limite diário

**Status:** aceito. Substitui a regra "item da loja não altera combate" da versão anterior.

**Contexto:** a versão anterior proibia qualquer item da loja com efeito em combate. Isso foi uma escolha de design para evitar vantagem paga, e não exigência legal. O pedido do projeto inclui recursos de melhoria contínua vendidos na loja.

**Decisão:** a loja pode vender itens de melhoria (poções, impulsos de experiência, expansões de inventário) com as seguintes condições:
- Preço fixo, sem nenhum elemento aleatório.
- Efeito descrito de forma exata no catálogo.
- `limite_diario` por jogador, aplicado pelo servidor (LOJA-09).
- Limite por conta quando aplicável.
- Nenhum pacote, caixa ou sorteio.

**Motivo:** a venda de consumíveis por preço fixo é um modelo amplamente usado e não depende de sorteio. O limite diário reduz o risco de compra compulsiva e de vantagem desproporcional.

**Consequências:**
- O catálogo precisa de revisão de balanceamento antes de publicar (ver `docs/balanceamento.md`).
- Itens com efeito em combate passam pelo mesmo servidor autoritativo das ações (ADR 0001).
- Qualquer proposta com elemento aleatório pago continua fora do escopo (ADR 0003).
- Recomenda-se revisão jurídica do modelo de loja antes de cobrar, como já previsto em `docs/sdd/02-pagamentos.md`.
