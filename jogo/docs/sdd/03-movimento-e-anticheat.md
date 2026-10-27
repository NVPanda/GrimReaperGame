# SDD 03: Movimento e anticheat

## Regras de negócio

- O servidor é a autoridade sobre a posição do personagem. O cliente envia intenção de movimento, não estado final.
- Movimento só é aceito se a distância percorrida é compatível com a velocidade máxima e o tempo decorrido, com tolerância.
- Um jogador não pode enviar mais ações por minuto do que o limite definido.
- Ação rejeitada não altera a posição registrada no servidor.
- A IA **nunca** bane, limita ou pune sozinha. Ela gera um score que vira alerta para revisão humana, conforme ADR 0002.
- Métricas enviadas à IA são agregadas e anonimizadas (sem nome, e-mail ou token).

## Cenários

**ANTI-01** Movimento dentro da velocidade permitida é aceito.
- Dado um personagem parado em (0,0) há 1 segundo, quando move para (5,0), então a ação é aceita e a posição vira (5,0).

**ANTI-02** Teletransporte é rejeitado.
- Dado um personagem em (0,0) há 1 segundo, quando tenta ir para (500,0), então a ação é rejeitada e a posição continua (0,0).

**ANTI-03** Excesso de ações por minuto é rejeitado.
- Dado um limite de 3 ações por minuto, quando um jogador envia a 4ª ação dentro do mesmo minuto, então a ação é rejeitada.

**ANTI-04** Janela de tempo de ações desliza.
- Dado um limite de 3 ações por minuto, quando 3 ações ocorrem e a próxima acontece após 61 segundos, então ela é aceita.

**ANTI-05** Coordenadas inválidas são recusadas antes da regra.
- Dado uma coordenada NaN ou fora de limite, quando é validada, então ocorre erro de entrada.

**ANTI-06** Score de risco abaixo de 0,7 não gera ação.
- Dado score 0,69, quando a política decide, então a ação é `NENHUMA`.

**ANTI-07** Score entre 0,7 e 0,95 envia para revisão humana.
- Dado score 0,7, quando a política decide, então a ação é `ENVIAR_PARA_REVISAO`.

**ANTI-08** Score acima de 0,95 limita ações e exige revisão, sem banir.
- Dado score 0,95, quando a política decide, então a ação é `LIMITAR_ACOES_E_REVISAR` e nenhuma ação de banimento existe no conjunto de decisões.

**ANTI-09** Score fora de 0 a 1 é recusado.
- Dado score 1,2, quando a política decide, então ocorre erro.
