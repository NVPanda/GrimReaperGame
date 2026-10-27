package jogo.anticheat.dominio

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class PoliticaRevisaoTest {

    @Test
    fun `ANTI-06 score abaixo de 0,7 nao gera acao`() {
        assertEquals(AcaoRevisao.NENHUMA, PoliticaRevisao.decidir(0.69))
    }

    @Test
    fun `ANTI-07 score de 0,7 envia para revisao humana`() {
        assertEquals(AcaoRevisao.ENVIAR_PARA_REVISAO, PoliticaRevisao.decidir(0.7))
    }

    @Test
    fun `ANTI-08 score de 0,95 limita acoes e exige revisao`() {
        assertEquals(AcaoRevisao.LIMITAR_ACOES_E_REVISAR, PoliticaRevisao.decidir(0.95))
    }

    @Test
    fun `ANTI-08 nao existe acao de banimento no conjunto de decisoes`() {
        assertEquals(false, AcaoRevisao.entries.any { it.name.contains("BANIR") })
    }

    @Test
    fun `ANTI-09 score fora de 0 a 1 e recusado`() {
        assertFailsWith<IllegalArgumentException> { PoliticaRevisao.decidir(1.2) }
    }
}
