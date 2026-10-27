package jogo.loja.infraestrutura

import jogo.loja.aplicacao.PedidoRepositorio
import jogo.loja.dominio.Pedido
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.ConcurrentHashMap

/** Implementação em memória, para desenvolvimento e testes. Trocar por banco de dados em produção. */
class PedidoRepositorioMemoria : PedidoRepositorio {
    private val mapa = ConcurrentHashMap<String, Pedido>()

    override fun salvar(pedido: Pedido) {
        mapa[pedido.referencia] = pedido
    }

    override fun porReferencia(referencia: String): Pedido? = mapa[referencia]

    override fun contarNoDia(jogadorId: String, itemId: String, dia: LocalDate, zona: ZoneId): Int =
        mapa.values.count {
            it.jogadorId == jogadorId && it.itemId == itemId && LocalDate.ofInstant(it.criadoEm, zona) == dia
        }
}
