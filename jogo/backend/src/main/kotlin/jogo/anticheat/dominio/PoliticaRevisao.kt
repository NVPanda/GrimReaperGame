package jogo.anticheat.dominio

/**
 * Ações possíveis para um score de risco (ADR 0002).
 * Não existe opção de banimento: sanção definitiva exige revisão humana.
 */
enum class AcaoRevisao { NENHUMA, ENVIAR_PARA_REVISAO, LIMITAR_ACOES_E_REVISAR }

object PoliticaRevisao {
    const val LIMIAR_REVISAO = 0.7
    const val LIMIAR_LIMITE = 0.95

    fun decidir(score: Double): AcaoRevisao {
        require(score in 0.0..1.0) { "score deve estar entre 0 e 1" }
        return when {
            score >= LIMIAR_LIMITE -> AcaoRevisao.LIMITAR_ACOES_E_REVISAR
            score >= LIMIAR_REVISAO -> AcaoRevisao.ENVIAR_PARA_REVISAO
            else -> AcaoRevisao.NENHUMA
        }
    }
}
