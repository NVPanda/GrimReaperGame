# SDD 01: Loja

## Regras de negócio

- Todo item tem preço fixo em centavos de BRL. Nenhum item é sorteado nem tem chance de ganho.
- Um item só é vendido se estiver `ativo` e dentro da janela `disponivel_de` / `disponivel_ate`.
- Itens de melhoria (poções, impulsos) podem ter efeito em combate, com preço fixo e limite diário por jogador (ADR 0006).
- Nenhum item da loja tem chance de ganho. Não existe pacote aleatório.
- O preço cobrado é sempre o do servidor, no momento da criação do pedido. O cliente não envia valor.
- Itens já comprados não são retirados quando o item sai da loja.

## Cenários

**LOJA-01** Item ativo dentro da janela aparece na vitrine.
- Dado um item `ativo` com janela aberta, quando a vitrine é consultada, então o item aparece.

**LOJA-02** Item oculto ou removido não aparece na vitrine.
- Dado um item `oculto`, quando a vitrine é consultada, então o item não aparece.

**LOJA-03** Item fora da janela de venda não pode ser comprado.
- Dado um item `ativo` cuja `disponivel_de` está no futuro, quando alguém tenta iniciar a compra, então a compra é recusada.

**LOJA-04** Item de melhoria com preço fixo pode ter efeito em combate e mantém o limite diário.
- Dado um item com `efeito_em_combate: true` e `limite_diario: 20`, quando o catálogo é carregado, então o item é aceito com os dois valores preservados.

**LOJA-09** Compra acima do limite diário é recusada no mesmo dia.
- Dado um item com `limite_diario: 2`, quando o mesmo jogador faz a 3ª compra do dia, então a compra é recusada.
- Dado que outro jogador comprou o mesmo item, quando o primeiro jogador conta suas compras, então a contagem é individual.

**LOJA-05** IDs de item devem ser únicos.
- Dado um catálogo com dois itens de mesmo ID, quando é carregado, então ocorre erro.

**LOJA-06** Preço é lido do catálogo do servidor, não do pedido.
- Dado um item de preço 19,90, quando a compra é iniciada, então o pedido registra 1990 centavos.

**LOJA-07** Preço deve ser positivo.
- Dado um preço de zero ou negativo, quando o item é criado, então ocorre erro.

**LOJA-08** Mudança de preço no catálogo não afeta pedidos já criados.
- Dado um pedido criado a 19,90, quando o catálogo muda o preço para 24,90, então o pedido continua esperando 19,90.
