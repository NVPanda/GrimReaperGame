# ADR 0008: nascimento, ponto de respawn e teleporte

## Status

Aceita.

## Contexto

O protótipo precisava conduzir o jogador do login à seleção de personagem e ao primeiro mundo. Também é necessário preservar o local natal, permitir que o jogador escolha onde renasce e habilitar deslocamentos entre regiões sem aceitar teleporte decidido apenas pelo cliente.

## Decisão

- Separar as páginas de login/cadastro, seleção de personagem, escolha de nascimento e jogo.
- Cada personagem escolhe uma cidade natal uma única vez. A cidade e o continente de nascimento ficam imutáveis no SQLite.
- O nascimento inicial também cria o primeiro ponto salvo. O NPC Save em uma cidade visitada pode atualizar a cidade de respawn, inclusive em outro continente; o continente natal continua registrado.
- Ao entrar no jogo, o servidor reposiciona o personagem na última cidade salva.
- NPC Save só existe em cidades; NPC Teleporte existe em todas as regiões para permitir ida e retorno. Ambos exigem proximidade de até 1,5 tile, validada pelo servidor.
- O NPC Teleporte permite selecionar regiões do mapa. Destinos no mesmo continente custam 100 MC; em outro continente, 1.000 MC; masmorras comuns, 10.000 MC; Trono do Abismo, 100.000 MC.
- Personagens começam com 10 MC. A cobrança é atômica com a mudança de região; saldo insuficiente recusa a viagem.
- NPCs de protótipo ficam em `(18, 15)` (Save, apenas cidades) e `(22, 15)` (Teleporte, todas as regiões); o ponto de entrada é `(20, 15)`.

## Consequências

- A escolha de nascimento não pode ser refeita usando outra sessão ou cliente modificado.
- O ponto salvo fica por personagem e sobrevive ao reinício do servidor.
- A tarifa é definida no servidor com base nos dados de zona e nas regras de `dados/economia.json`, não no valor recebido do cliente.
- O saldo inicial é menor do que qualquer tarifa. Até quests, venda de itens ou outra fonte de MC ser implementada, personagens não conseguem pagar viagens; o servidor recusa sem debitar saldo.
- SQLite atende ao protótipo de instância única. O estado de movimento continua em memória e é reancorado na cidade salva quando o jogador entra novamente.
- A taxa de 100.000 MC define o Trono do Abismo como a masmorra SS do protótipo; essa classificação precisa ser revisada se o conteúdo de masmorras mudar.

## Alternativas consideradas

- Tratar nascimento como ponto de respawn permanente: descartado porque impediria o NPC Save de alterar a cidade de retorno.
- Confiar no cliente para destino e tarifa: descartado porque permitiria viagens gratuitas ou destinos inválidos.
- Persistir posição tile a tile: adiado; o protótipo só persiste nascimento e cidade salva.
