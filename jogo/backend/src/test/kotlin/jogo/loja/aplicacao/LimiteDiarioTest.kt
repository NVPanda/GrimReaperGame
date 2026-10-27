package jogo.loja.aplicacao

import jogo.loja.dominio.*
import jogo.loja.infraestrutura.PedidoRepositorioMemoria
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class LimiteDiarioTest {

    private val item = ItemLoja(
        id = "pocao_vida_pequena", nome = "Poção", descricao = "", tipo = TipoItem.COSMETICO,
        preco = Preco(490), status = StatusItem.ATIVO,
        disponivelDe = Instant.parse("2026-10-01T00:00:00Z"), disponivelAte = null,
        limitePorConta = 999, efeitoEmCombate = true, limiteDiario = 2,
    )

    private object PagamentoNulo : PortaPagamento {
        override fun criarCobranca(pedido: Pedido, item: ItemLoja) = "https://pagamento.test"
        override fun consultarPorNotificacao(corpo: String): ConfirmacaoPagamento? = null
    }

    @Test
    fun `LOJA-09 compra acima do limite diario e recusada no mesmo dia`() {
        val repo = PedidoRepositorioMemoria()
        val agora = Instant.parse("2026-10-17T15:00:00Z")
        val s = ServicoCheckout(
            catalogo = { Catalogo(listOf(item)) },
            pedidos = repo,
            pagamento = PagamentoNulo,
            relogio = Clock.fixed(agora, ZoneOffset.UTC),
            geraReferencia = { "ref-${System.nanoTime()}" },
            liberar = { _, _ -> },
        )
        s.iniciar("jogador-1", "pocao_vida_pequena")
        s.iniciar("jogador-1", "pocao_vida_pequena")
        assertFailsWith<LimiteDiarioAtingido> { s.iniciar("jogador-1", "pocao_vida_pequena") }
    }

    @Test
    fun `LOJA-09 limite diario e por jogador`() {
        val repo = PedidoRepositorioMemoria()
        val agora = Instant.parse("2026-10-17T15:00:00Z")
        val s = ServicoCheckout(
            catalogo = { Catalogo(listOf(item)) },
            pedidos = repo,
            pagamento = PagamentoNulo,
            relogio = Clock.fixed(agora, ZoneOffset.UTC),
            geraReferencia = { "ref-${System.nanoTime()}" },
            liberar = { _, _ -> },
        )
        s.iniciar("jogador-1", "pocao_vida_pequena")
        s.iniciar("jogador-1", "pocao_vida_pequena")
        s.iniciar("jogador-2", "pocao_vida_pequena")
        assertEquals(1, repo.contarNoDia("jogador-2", "pocao_vida_pequena",
            java.time.LocalDate.of(2026, 10, 17), ZoneId.of("UTC")))
    }
}
