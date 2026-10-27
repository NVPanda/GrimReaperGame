package jogo.anticheat.aplicacao

/**
 * Porta para o modelo de IA. Recebe apenas métricas agregadas e anonimizadas
 * (sem nome, e-mail ou token) e devolve score de risco entre 0 e 1.
 * O resultado alimenta a PoliticaRevisao. Não decide sanção sozinho (ADR 0002).
 *
 * Nenhum adaptador concreto existe nesta versão.
 */
interface PortaAnaliseIA {
    fun pontuar(metricasAnonimizadas: Map<String, Double>): Double
}
