package jogo.loja.aplicacao

import jogo.loja.dominio.Catalogo
import jogo.loja.dominio.ItemLoja
import jogo.loja.dominio.Pedido
import jogo.loja.dominio.ResultadoConfirmacao
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

/** Porta de persistência de pedidos. */
interface PedidoRepositorio {
    fun salvar(pedido: Pedido)
    fun porReferencia(referencia: String): Pedido?

    /** Quantos pedidos o jogador fez para este item no dia civil informado (LOJA-09). */
    fun contarNoDia(jogadorId: String, itemId: String, dia: LocalDate, zona: ZoneId): Int
}

class LimiteDiarioAtingido(itemId: String) : RuntimeException("limite diário atingido para $itemId")

/** Resultado normalizado de uma consulta ao provedor. */
data class ConfirmacaoPagamento(
    val referencia: String,
    val aprovado: Boolean,
    val valorPagoCentavos: Long?,
)

/** Porta do provedor de pagamento. Implementada por adaptadores (ex.: Mercado Pago). */
interface PortaPagamento {
    fun criarCobranca(pedido: Pedido, item: ItemLoja): String

    /** Interpreta a notificação e consulta o provedor. Nunca confia só no corpo recebido. */
    fun consultarPorNotificacao(corpo: String): ConfirmacaoPagamento?
}

/**
 * Caso de uso de compra. [liberar] é o ponto de integração com o servidor de jogo:
 * conceder o item ao personagem. Ainda não integrado (ver SDD 05).
 */
class ServicoCheckout(
    private val catalogo: () -> Catalogo,
    private val pedidos: PedidoRepositorio,
    private val pagamento: PortaPagamento,
    private val relogio: Clock,
    private val geraReferencia: () -> String = { UUID.randomUUID().toString().replace("-", "") },
    private val liberar: (jogadorId: String, itemId: String) -> Unit,
) {
    fun iniciar(jogadorId: String, itemId: String): String {
        val agora = Instant.now(relogio)
        val item = catalogo().paraVenda(itemId, agora)
        val limite = item.limiteDiario
        if (limite != null) {
            val hoje = LocalDate.ofInstant(agora, relogio.zone)
            if (pedidos.contarNoDia(jogadorId, item.id, hoje, relogio.zone) >= limite) {
                throw LimiteDiarioAtingido(item.id)
            }
        }
        val pedido = Pedido(geraReferencia(), jogadorId, item.id, item.preco, criadoEm = agora)
        pedidos.salvar(pedido)
        return pagamento.criarCobranca(pedido, item)
    }

    /** Devolve null quando a notificação não corresponde a nenhum pedido aprovado. */
    fun processarNotificacao(corpo: String): ResultadoConfirmacao? {
        val confirmacao = pagamento.consultarPorNotificacao(corpo) ?: return null
        if (!confirmacao.aprovado) return null
        val pedido = pedidos.porReferencia(confirmacao.referencia) ?: return null
        val valor = confirmacao.valorPagoCentavos ?: return ResultadoConfirmacao.ValorDivergente

        val resultado = pedido.confirmarPagamento(valor)
        pedidos.salvar(pedido)
        if (resultado == ResultadoConfirmacao.Liberar) {
            liberar(pedido.jogadorId, pedido.itemId)
        }
        return resultado
    }
}
