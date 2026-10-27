# ADR 0003: Sem NFT com chance de ganho e sem sorteio pago

**Status:** aceito

**Contexto:** foi proposto um sistema em que o jogador pagaria ou jogaria para ter chance de obter ativos com valor financeiro, como NFTs raros.

**Decisão:** não implementar. Recompensas raras só podem ser obtidas por jogo (drop por taxa publicada, conquista, ranking) ou compra de preço fixo que entrega exatamente o item descrito (SDD 01). Nenhum ativo com valor de troca externo é criado nesta versão.

**Motivo:** combinação de pagamento, chance e ativo com valor pode caracterizar aposta ou jogo de azar, além de possível valor mobiliário. Esses temas exigem análise jurídica prévia, que não foi feita.

**Consequências:** o modelo de monetização fica limitado a preço fixo. Qualquer proposta futura desse tipo precisa de novo ADR e parecer jurídico antes da especificação.
