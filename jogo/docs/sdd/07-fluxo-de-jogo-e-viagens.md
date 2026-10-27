# SDD 07: entrada no jogo, nascimento e viagens

## Regras de negócio

- As etapas são páginas distintas: autenticação, seleção/criação de personagem, nascimento e mundo.
- Só uma sessão e um personagem ativo por conta, conforme ADR 0007.
- O continente e a cidade de nascimento são escolhidos uma vez por personagem e ficam permanentes.
- O nascimento inicial grava a primeira cidade de respawn. O NPC Save só funciona em cidade e atualiza o respawn para a cidade atual, sem alterar o continente natal.
- Ao entrar no jogo, o servidor move o personagem para a última cidade salva.
- Save e Teleporte exigem proximidade de até 1,5 tile do respectivo NPC; o servidor verifica zona e posição. Save fica nas cidades; Teleporte está em todas as regiões para permitir retorno.
- O NPC Teleporte apresenta todas as regiões e cobra, por destino: 100 MC no mesmo continente, 1.000 MC entre continentes, 10.000 MC para masmorra comum e 100.000 MC para Trono do Abismo (masmorra SS).
- A tarifa é calculada no servidor e debitada atomicamente com a viagem. Saldo insuficiente não altera o saldo nem a região.
- O saldo inicial é 10 MC por personagem. Entregas de quests e vendas permitidas ao mercador creditam MC; teletransportes debitam o saldo de forma atômica.

## Cenários

**MUNDO-01** Personagem novo só entra no fluxo de nascimento após ser selecionado.

**MUNDO-02** O servidor aceita uma escolha válida de cidade dentro do continente escolhido e a torna imutável.

**MUNDO-03** Login seguinte inicia o personagem na cidade de respawn salva.

**MUNDO-04** NPC Save altera o respawn somente se o personagem estiver em uma cidade e próximo do NPC.

**MUNDO-05** NPC Teleporte aceita destinos existentes, calcula o preço no servidor e atualiza saldo/região uma única vez.

**MUNDO-06** Teleporte longe do NPC, com destino inválido ou saldo insuficiente é recusado sem alterar o estado.

## Pendências

- Saldo inicial e cobrança estão persistidos, mas quests, venda de itens e outras formas de ganhar MC ainda não estão integradas.
- Mapas têm marcadores de NPC do protótipo, não personagens/NPCs com arte final e interação de diálogo.
- Combate, inventário, drops executáveis e respawn após morte não estão implementados.
