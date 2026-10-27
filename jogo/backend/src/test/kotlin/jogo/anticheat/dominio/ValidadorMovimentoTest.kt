package jogo.anticheat.dominio

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class ValidadorMovimentoTest {

    @Test
    fun `ANTI-01 movimento dentro da velocidade e aceito`() {
        val v = ValidadorMovimento(velocidadeMaxima = 8.0)
        v.posicionar("j1", Posicao(0.0, 0.0), agoraMs = 0)
        val veredito = v.validarMovimento("j1", Posicao(5.0, 0.0), agoraMs = 1000)
        assertEquals(Veredito.Aceita, veredito)
    }

    @Test
    fun `ANTI-02 teletransporte e rejeitado e a posicao nao muda`() {
        val v = ValidadorMovimento(velocidadeMaxima = 8.0)
        v.posicionar("j1", Posicao(0.0, 0.0), agoraMs = 0)
        assertIs<Veredito.Rejeitada>(v.validarMovimento("j1", Posicao(500.0, 0.0), agoraMs = 1000))
        // Tentativa seguinte, dentro do limite a partir da posição original, é aceita
        assertEquals(Veredito.Aceita, v.validarMovimento("j1", Posicao(5.0, 0.0), agoraMs = 2000))
    }

    @Test
    fun `ANTI-03 excesso de acoes por minuto e rejeitado`() {
        val v = ValidadorMovimento(acoesMaximasPorMinuto = 3)
        v.posicionar("j1", Posicao(0.0, 0.0), agoraMs = 0)
        v.validarMovimento("j1", Posicao(0.0, 0.0), agoraMs = 1000)
        v.validarMovimento("j1", Posicao(0.0, 0.0), agoraMs = 2000)
        v.validarMovimento("j1", Posicao(0.0, 0.0), agoraMs = 3000)
        assertIs<Veredito.Rejeitada>(v.validarMovimento("j1", Posicao(0.0, 0.0), agoraMs = 4000))
    }

    @Test
    fun `ANTI-04 janela de tempo das acoes desliza`() {
        val v = ValidadorMovimento(acoesMaximasPorMinuto = 3)
        v.posicionar("j1", Posicao(0.0, 0.0), agoraMs = 0)
        v.validarMovimento("j1", Posicao(0.0, 0.0), agoraMs = 1000)
        v.validarMovimento("j1", Posicao(0.0, 0.0), agoraMs = 2000)
        v.validarMovimento("j1", Posicao(0.0, 0.0), agoraMs = 3000)
        assertEquals(Veredito.Aceita, v.validarMovimento("j1", Posicao(0.0, 0.0), agoraMs = 64000))
    }
}
