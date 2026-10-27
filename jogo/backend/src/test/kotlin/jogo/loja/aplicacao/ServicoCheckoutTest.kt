package jogo.loja.aplicacao

import jogo.loja.dominio.*
import jogo.loja.infraestrutura.PedidoRepositorioMemoria
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.*

class ServicoCheckoutTest {

    private val relogio = Clock.fixed(Instant.parse("2026-10-17T12:00:00Z"), ZoneOffset.UTC)

    private val catalogo = Catalogo(
        listOf(
            ItemLoja(
                id = "capa_01", nome = "Capa", descricao = "", tipo = TipoItem.COSMETICO,
                preco = Preco(1990), status = StatusItem.ATIVO,
                disponivelDe = Instant.parse("2026-10-01T00:00:00Z"), disponivelAte = null,
                limitePorConta = 1, efeitoEmCombate = false,
            ),
            ItemLoja(
                id = "futuro", nome = "Futuro", descricao = "", tipo = TipoItem.TITULO,
                preco = Preco(990), status = StatusItem.ATIVO,
                disponivelDe = Instant.parse("2026-12-01T00:00:00Z"), disponivelAte = null,
                limitePorConta = 1, efeitoEmCombate = false,
            ),
        )
    )

    private class PagamentoFalso : PortaPagamento {
        val cobrancas = mutableListOf<Pedido>()
        var confirmacao: ConfirmacaoPagamento? = null

        override fun criarCobranca(pedido: Pedido, item: ItemLoja): String {
            cobrancas += pedido
            return "https://pagamento.test/${pedido.referencia}"
        }

        override fun consultarPorNotificacao(corpo: String): ConfirmacaoPagamento? = confirmacao
    }

    private class Liberacoes {
        val feitas = mutableListOf<Pair<String, String>>()
    }

    private lateinit var pagamento: PagamentoFalso
    private lateinit var liberacoes: Liberacoes
    private lateinit var servico: ServicoCheckout

    @BeforeTest
    fun preparar() {
        pagamento = PagamentoFalso()
        liberacoes = Liberacoes()
        servico = ServicoCheckout(
            catalogo = { catalogo },
            pedidos = PedidoRepositorioMemoria(),
            pagamento = pagamento,
            relogio = relogio,
            geraReferencia = { "ref-1" },
            liberar = { jogador, item -> liberacoes.feitas += jogador to item },
        )
    }

    @Test
    fun `PAG-01 iniciar compra devolve URL de pagamento`() {
        val url = servico.iniciar("jogador-9", "capa_01")
        assertEquals("https://pagamento.test/ref-1", url)
    }

    @Test
    fun `LOJA-06 preco do pedido vem do catalogo do servidor`() {
        servico.iniciar("jogador-9", "capa_01")
        assertEquals(1990L, pagamento.cobrancas.single().valorEsperado.centavos)
    }

    @Test
    fun `LOJA-03 compra de item fora da janela e recusada`() {
        assertFailsWith<ItemIndisponivel> { servico.iniciar("jogador-9", "futuro") }
    }

    @Test
    fun `PAG-02 pagamento aprovado com valor correto libera o item`() {
        servico.iniciar("jogador-9", "capa_01")
        pagamento.confirmacao = ConfirmacaoPagamento("ref-1", aprovado = true, valorPagoCentavos = 1990)
        servico.processarNotificacao("corpo")
        assertEquals(listOf("jogador-9" to "capa_01"), liberacoes.feitas)
    }

    @Test
    fun `PAG-03 notificacao repetida libera apenas uma vez`() {
        servico.iniciar("jogador-9", "capa_01")
        pagamento.confirmacao = ConfirmacaoPagamento("ref-1", aprovado = true, valorPagoCentavos = 1990)
        servico.processarNotificacao("corpo")
        servico.processarNotificacao("corpo")
        assertEquals(1, liberacoes.feitas.size)
    }

    @Test
    fun `PAG-04 valor divergente nao libera nada`() {
        servico.iniciar("jogador-9", "capa_01")
        pagamento.confirmacao = ConfirmacaoPagamento("ref-1", aprovado = true, valorPagoCentavos = 990)
        assertEquals(ResultadoConfirmacao.ValorDivergente, servico.processarNotificacao("corpo"))
        assertTrue(liberacoes.feitas.isEmpty())
    }

    @Test
    fun `PAG-05 pagamento nao aprovado nao libera`() {
        servico.iniciar("jogador-9", "capa_01")
        pagamento.confirmacao = ConfirmacaoPagamento("ref-1", aprovado = false, valorPagoCentavos = null)
        servico.processarNotificacao("corpo")
        assertTrue(liberacoes.feitas.isEmpty())
    }

    @Test
    fun `PAG-06 notificacao de referencia desconhecida e ignorada`() {
        pagamento.confirmacao = ConfirmacaoPagamento("nao-existe", aprovado = true, valorPagoCentavos = 1990)
        assertNull(servico.processarNotificacao("corpo"))
        assertTrue(liberacoes.feitas.isEmpty())
    }
}
