# Balanceamento

Valores originais do projeto. Não copiam fichas nem tabelas de sistemas publicados. Os números são pontos de partida e precisam de playtest antes de publicar.

## Classes

- **Atributos base:** seis atributos (força, agilidade, vitalidade, inteligência, destreza, sorte) somando sempre 60. Assim nenhuma classe começa mais forte que outra.
- **Crescimento:** cada classe distribui 1,0 de crescimento por nível entre os atributos do seu papel.
- **Habilidades:** custo de SP entre 5 e 12. Dano entre 60% e 170% do ataque base. Efeitos secundários não podem somar mais de um efeito forte por habilidade.

Índice de comparação entre classes: soma dos atributos ponderados pelo papel, com tolerância de ±10%. Ajuste o `crescimento` antes de mudar atributos base.

## Monstros

- **Vida:** `(60 + 18 × nível)`. Chefes têm vida 3× a de um comum.
- **Ataque:** `(4 + 1,6 × nível)`. Chefes têm 1,6×.
- **Defesa:** `(2 + 0,9 × nível)`. Chefes têm 1,4×.
- **Experiência:** `nível^1,6`. Chefes têm 6×.
- **Respawn:** 20 segundos para comuns, 900 segundos (15 minutos) para chefes.

## Itens e drops

- **Bônus de equipamento por rank:** `(3 + 4 × índice do rank)`, com multiplicador de 1,2 para S e 1,4 para SS.
- **Taxa de drop por rank (%):** F 40, E 25, D 12, C 6, B 2,5, A 1, S 0,2. SS aparece só em chefe, com 0,5%.
- **Rank do monstro:** definido pelo nível (F até 9, E até 17, D até 25, C até 33, B até 39, A até 44, S acima).
- **Chefes:** garantem material do continente (60%) e equipamento do rank do nível (15%).

## Economia

- **Venda ao NPC mercador:** MC por rank, de 2 (F) a 500 (SS).
- **Quests:** recompensa de 30 × ordem do continente para caça, 20 × ordem para coleta e 120 × ordem para masmorra.
- **Loja:** preço fixo. Consumíveis com limite diário, conforme ADR 0006.

## Restrições

- MC não é convertível em dinheiro real.
- Nenhum item tem chance de ganho pago.
- Itens SS não podem ser comprados.
