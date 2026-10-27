# ADR 0001: Servidor é a autoridade sobre estado do jogo

**Status:** aceito

**Contexto:** o cliente HTML5/TypeScript roda no navegador ou no celular do jogador, que pode alterar qualquer código ou requisição.

**Decisão:** o servidor calcula posição, valida movimento, define preço e decide liberação de item. O cliente envia intenções.

**Consequências:** o servidor precisa de regras explícitas e testadas (SDD 03). Latência pode aumentar. Ofuscação do cliente não é tratada como controle de segurança.
