package jogo.loja.dominio

import kotlin.test.Test
import kotlin.test.assertEquals

class PedidoTest {

    private fun pedido() = Pedido(
        referencia = "ref-1",
        jogadorId = "jogador-9",
        itemId = "capa_01",
        valorEsperado = Preco(1990),
    )

    @Test
    fun `PAG-02 pagamento aprovado com valor correto libera o item`() {
        val p = pedido()
        assertEquals(ResultadoConfirmacao.Liberar, p.confirmarPagamento(1990))
        assertEquals(SituacaoPedido.PAGO, p.situacao)
    }

    @Test
    fun `PAG-03 notificacao repetida nao libera de novo`() {
        val p = pedido()
        p.confirmarPagamento(1990)
        assertEquals(ResultadoConfirmacao.JaLiberado, p.confirmarPagamento(1990))
    }

    @Test
    fun `PAG-04 valor divergente nao libera e marca o pedido`() {
        val p = pedido()
        assertEquals(ResultadoConfirmacao.ValorDivergente, p.confirmarPagamento(990))
        assertEquals(SituacaoPedido.VALOR_DIVERGENTE, p.situacao)
    }

    @Test
    fun `PAG-09 pedido divergente nao e reprocessado automaticamente`() {
        val p = pedido()
        p.confirmarPagamento(990)
        assertEquals(ResultadoConfirmacao.Ignorado, p.confirmarPagamento(1990))
        assertEquals(SituacaoPedido.VALOR_DIVERGENTE, p.situacao)
    }

    @Test
    fun `LOJA-08 pedido guarda o preco do momento da criacao`() {
        val p = pedido()
        assertEquals(1990L, p.valorEsperado.centavos)
    }
}
