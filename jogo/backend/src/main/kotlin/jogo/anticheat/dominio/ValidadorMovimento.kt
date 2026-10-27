package jogo.anticheat.dominio

import java.util.concurrent.ConcurrentHashMap
import kotlin.math.hypot

data class Posicao(val x: Double, val y: Double)

sealed interface Veredito {
    data object Aceita : Veredito
    data class Rejeitada(val motivo: String) : Veredito
}

/**
 * Regras server-authoritative de movimento (SDD 03).
 * Ação rejeitada não altera a posição registrada.
 */
class ValidadorMovimento(
    private val velocidadeMaxima: Double = 8.0,
    private val acoesMaximasPorMinuto: Int = 300,
    private val tolerancia: Double = 0.5,
) {
    private class Estado {
        var posicao = Posicao(0.0, 0.0)
        var ultimoMs: Long = 0L
        val janelaMs = ArrayDeque<Long>()
    }

    private val estados = ConcurrentHashMap<String, Estado>()

    /** Define a posição inicial do personagem, por exemplo ao entrar no mapa. */
    fun posicionar(jogadorId: String, posicao: Posicao, agoraMs: Long) {
        val estado = estados.getOrPut(jogadorId) { Estado() }
        synchronized(estado) {
            estado.posicao = posicao
            estado.ultimoMs = agoraMs
        }
    }

    fun posicaoAtual(jogadorId: String): Posicao? =
        estados[jogadorId]?.let { estado -> synchronized(estado) { estado.posicao } }

    fun validarMovimento(jogadorId: String, destino: Posicao, agoraMs: Long): Veredito {
        val estado = estados.getOrPut(jogadorId) { Estado() }
        synchronized(estado) {
            estado.janelaMs.addLast(agoraMs)
            while (estado.janelaMs.isNotEmpty() && agoraMs - estado.janelaMs.first() > 60_000) {
                estado.janelaMs.removeFirst()
            }
            if (estado.janelaMs.size > acoesMaximasPorMinuto) {
                return Veredito.Rejeitada("taxa de ações acima do limite")
            }

            val dtSegundos = (agoraMs - estado.ultimoMs).coerceAtLeast(1L) / 1000.0
            val distancia = hypot(destino.x - estado.posicao.x, destino.y - estado.posicao.y)
            if (distancia > velocidadeMaxima * dtSegundos + tolerancia) {
                return Veredito.Rejeitada("movimento acima da velocidade permitida")
            }

            estado.posicao = destino
            estado.ultimoMs = agoraMs
            return Veredito.Aceita
        }
    }
}
