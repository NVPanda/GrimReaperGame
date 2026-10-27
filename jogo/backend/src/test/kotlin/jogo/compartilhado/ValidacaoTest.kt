package jogo.compartilhado

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ValidacaoTest {

    @Test
    fun `SEG-05 nome valido e aceito`() {
        assertEquals("Guerreiro_01", Validacao.nomePersonagem("Guerreiro_01"))
    }

    @Test
    fun `SEG-05 nome com marcacao HTML e recusado`() {
        assertFailsWith<IllegalArgumentException> { Validacao.nomePersonagem("<script>x") }
    }

    @Test
    fun `ANTI-05 coordenada NaN e recusada`() {
        assertFailsWith<IllegalArgumentException> { Validacao.coordenada(Double.NaN) }
    }

    @Test
    fun `ANTI-05 coordenada fora do limite e recusada`() {
        assertFailsWith<IllegalArgumentException> { Validacao.coordenada(20_000.0) }
    }

    @Test
    fun `SEG-06 texto de outro jogador e escapado`() {
        assertEquals("&lt;b&gt;oi&lt;/b&gt;", Validacao.textoSeguro("<b>oi</b>"))
    }

    @Test
    fun `SEG-07 texto acima do tamanho maximo e recusado`() {
        assertFailsWith<IllegalArgumentException> { Validacao.textoSeguro("a".repeat(201), maximo = 200) }
    }
}
