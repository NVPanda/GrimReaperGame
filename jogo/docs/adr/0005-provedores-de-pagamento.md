# ADR 0005: Provedores de pagamento

**Status:** aceito, com verificação pendente antes de cada integração

**Contexto:** foi pedido integração com Mercado Pago, Wise, Nubank e Banco Inter. A pesquisa mostrou funções diferentes para cada um.

**Decisão:**
- **Mercado Pago (Checkout Pro):** provedor de cobrança. Implementado.
- **Banco Inter:** API de cobrança (boleto e Pix) para empresas. Candidato à fase 2, sujeito a credenciais e à documentação atual.
- **Wise:** API de pagamentos de saída. Usar apenas para repasses, nunca para receber de jogadores.
- **Nubank:** não foi encontrada API pública de cobrança para comerciantes. NuPay costuma ser acessado por agregador (EBANX, Malga, Paybyrd). Integrar pelo agregador, se necessário.

**Consequências:** toda integração segue a porta `PortaPagamento`. Trocar de provedor não altera a Loja. Antes de cada integração nova, conferir a documentação oficial vigente.
