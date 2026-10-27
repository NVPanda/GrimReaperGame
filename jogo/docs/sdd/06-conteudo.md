# SDD 06: Conteúdo do jogo (mundo, classes, monstros, mapas, quests, itens)

Todo o conteúdo é gerado por `ferramentas/gerar_conteudo.py` e salvo em `dados/` (JSON editável). Os testes em `backend/src/test/kotlin/jogo/conteudo/ConteudoTest.kt` checam a integridade.

## Regras de negócio

- Seis continentes com faixas de nível em progressão: Humano (1–18), Élfico (14–30), Anão (24–38), Dríades (30–44), Gelo (38–50), Demoníaco (44–50).
- Cada continente tem uma cidade, pelo menos um campo e uma masmorra com um chefe.
- Cada continente tem espécies próprias. Nenhum monstro aparece em dois continentes.
- Classes têm atributos base somando 60 e crescimento somando 1,0. Cada classe tem três habilidades.
- Itens têm ranks F, E, D, C, B, A, S e SS. Item SS só cai de chefe (ADR 0003 e regra de drops).
- Rank de equipamento e categoria de uso do drop são dimensões distintas. As categorias de loot são:
  - **Básicos:** itens comuns usados em quests pequenas de pouca experiência.
  - **Intermediários:** itens comuns ou raros usados em quests de coleta que concedem experiência.
  - **Ranqueados:** drops de chance muito baixa trocados por itens no mercado negro.
  - **Ultra Raros:** itens de quests difíceis, prioritariamente em masmorras para grupos de até 10 jogadores.
  - **Ranqueados Ultra Raros:** materiais para craft e melhoria de armas, armaduras, anéis, pingentes e botas.
- Quests seguem três tipos: caça, coleta (plantio, pesca ou mineração) e masmorra.
- Moeda: Mitrum Coin (MC), abreviação MC.

## Cenários

**CONT-01** Existem seis continentes com ids únicos.
**CONT-02** Cada classe tem três habilidades, atributos base somando 60 e crescimento somando 1,0.
**CONT-03** Todo item de drop e todo alvo de coleta existem em `itens.json`.
**CONT-04** Toda zona lista apenas monstros existentes, e todo campo tem pelo menos um monstro comum.
**CONT-05** Toda quest aponta para um NPC e uma zona existentes.
**CONT-06** Item SS só é dropado por chefe.
**CONT-07** Todo monstro e todo item tem sprite no manifest e arquivo presente em `assets/`.
**CONT-08** Cada continente tem uma cidade, pelo menos um campo e uma masmorra com um chefe.

As cinco categorias acima classificam os drops. Combate, inventário, quests, mercado negro e melhoria de equipamento já têm implementação de protótipo no backend; participação de grupos e crafting permanecem parciais e não devem ser confundidos com sistemas completos de produção.

As quests do protótipo podem ser aceitas, progredidas e entregues ao NPC guia. A entrega exige que o personagem esteja na cidade do guia, próximo a ele, e concede a recompensa de MC uma única vez. A venda ao mercador também credita MC conforme o rank do item; o saldo é persistido no personagem.

**QUEST-01** Entrega de quest exige proximidade e concede a recompensa uma única vez.
- Dado uma quest concluída e o personagem na cidade do guia, quando tenta entregar a mais de 5 tiles, então a entrega é recusada sem alterar saldo ou progresso.
- Dado o mesmo personagem próximo ao guia, quando entrega a quest, então o MC e a experiência são creditados e a quest deixa de estar ativa.
- Dado uma quest já entregue, quando tenta entregá-la novamente, então a solicitação é recusada e nenhum crédito adicional é feito.
