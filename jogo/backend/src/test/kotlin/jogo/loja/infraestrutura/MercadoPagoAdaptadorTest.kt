package jogo.loja.infraestrutura

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MercadoPagoAdaptadorTest {

    @Test
    fun `PAG-07 notificacao que nao e de pagamento nao produz ID`() {
        val corpo = """{"type":"merchant_order","data":{"id":"55"}}"""
        assertNull(extrairIdPagamento(corpo))
    }

    @Test
    fun `PAG-07 notificacao de pagamento produz o ID do pagamento`() {
        val corpo = """{"type":"payment","data":{"id":"123456"}}"""
        assertEquals("123456", extrairIdPagamento(corpo))
    }

    @Test
    fun `PAG-08 valor em reais do provedor vira centavos sem erro de arredondamento`() {
        val pagamento = """{"status":"approved","external_reference":"ref-1","transaction_amount":19.9}"""
        val confirmacao = mapearPagamento(pagamento)
        assertEquals(1990L, confirmacao.valorPagoCentavos)
    }

    @Test
    fun `PAG-05 status pending nao e aprovado`() {
        val pagamento = """{"status":"pending","external_reference":"ref-1","transaction_amount":19.9}"""
        assertFalse(mapearPagamento(pagamento).aprovado)
    }

    @Test
    fun `PAG-02 status approved e aprovado e carrega a referencia`() {
        val pagamento = """{"status":"approved","external_reference":"ref-1","transaction_amount":19.9}"""
        val confirmacao = mapearPagamento(pagamento)
        assertTrue(confirmacao.aprovado)
        assertEquals("ref-1", confirmacao.referencia)
    }
}
