# SDD 02: Pagamentos

## Regras de negócio

- A cobrança é feita por um provedor regulado. O jogo nunca armazena dados de cartão.
- O item só é liberado quando o provedor confirma pagamento aprovado **e** o valor pago é igual ao valor do pedido.
- A confirmação é checada no provedor (consulta direta pelo ID), não apenas pelo corpo da notificação.
- Liberação é idempotente: o mesmo pagamento não libera o item duas vezes.
- Se o valor pago divergir, o pedido vai para `VALOR_DIVERGENTE` e não libera nada, até revisão humana.
- Provedores suportados nesta versão: Mercado Pago (Checkout Pro). Banco Inter, Nubank (via agregador) e Wise (apenas repasses) estão em `docs/adr/0005-provedores-de-pagamento.md`.

## Cenários

**PAG-01** Pedido criado gera URL de checkout do provedor.
- Dado um item vendável, quando o checkout é iniciado, então o provedor recebe o preço do catálogo e o servidor devolve a URL de pagamento.

**PAG-02** Pagamento aprovado com valor correto libera o item uma vez.
- Dado um pedido de 1990 centavos, quando o provedor confirma aprovado com 1990, então o item é liberado.

**PAG-03** Notificação repetida não libera de novo.
- Dado um pedido já pago, quando a mesma notificação chega novamente, então nada é liberado e o resultado é `JaLiberado`.

**PAG-04** Valor divergente não libera.
- Dado um pedido de 1990, quando o provedor confirma aprovado com 990, então nada é liberado e o pedido fica `VALOR_DIVERGENTE`.

**PAG-05** Pagamento não aprovado não libera.
- Dado um pedido criado, quando o provedor responde `pending` ou `rejected`, então nada é liberado.

**PAG-06** Notificação de referência desconhecida é ignorada.
- Dado um pedido que não existe no sistema, quando a notificação chega, então nada acontece e o resultado é nulo.

**PAG-07** Notificação de tipo diferente de pagamento é ignorada.
- Dado um corpo de notificação com `type` diferente de `payment`, quando é interpretado, então nenhum ID é extraído.

**PAG-08** Valor em reais do provedor é convertido para centavos sem erro de arredondamento.
- Dado `transaction_amount` de `19.9`, quando é convertido, então o valor é 1990 centavos.

**PAG-09** Pedido divergente não é reprocessado automaticamente.
- Dado um pedido em `VALOR_DIVERGENTE`, quando chega confirmação com o valor correto, então o pedido continua sem liberar.
