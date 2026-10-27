package jogo.loja.dominio

import java.time.Instant

enum class SituacaoPedido { CRIADO, PAGO, VALOR_DIVERGENTE }

sealed interface ResultadoConfirmacao {
    data object Liberar : ResultadoConfirmacao
    data object JaLiberado : ResultadoConfirmacao
    data object ValorDivergente : ResultadoConfirmacao
    data object Ignorado : ResultadoConfirmacao
}

/**
 * Agregado raiz da compra. Guarda o preço no momento da criação (LOJA-08)
 * e controla a própria transição de situação (PAG-02 a PAG-04, PAG-09).
 */
class Pedido(
    val referencia: String,
    val jogadorId: String,
    val itemId: String,
    val valorEsperado: Preco,
    val criadoEm: Instant = Instant.EPOCH,
) {
    var situacao: SituacaoPedido = SituacaoPedido.CRIADO
        private set

    fun confirmarPagamento(valorPagoCentavos: Long): ResultadoConfirmacao {
        return when (situacao) {
            SituacaoPedido.PAGO -> ResultadoConfirmacao.JaLiberado
            SituacaoPedido.VALOR_DIVERGENTE -> ResultadoConfirmacao.Ignorado
            SituacaoPedido.CRIADO -> {
                if (valorPagoCentavos == valorEsperado.centavos) {
                    situacao = SituacaoPedido.PAGO
                    ResultadoConfirmacao.Liberar
                } else {
                    situacao = SituacaoPedido.VALOR_DIVERGENTE
                    ResultadoConfirmacao.ValorDivergente
                }
            }
        }
    }
}
