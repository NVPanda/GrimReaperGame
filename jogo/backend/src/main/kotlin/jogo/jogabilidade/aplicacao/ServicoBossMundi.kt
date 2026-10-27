package jogo.jogabilidade.aplicacao

import jogo.anticheat.dominio.ValidadorMovimento
import jogo.identidade.dominio.Personagem
import jogo.jogabilidade.infraestrutura.BossMundiEstado
import jogo.jogabilidade.infraestrutura.RepositorioProgressoSqlite
import kotlinx.serialization.json.*
import kotlin.math.hypot

private data class RegiaoBossMundi(
    val id: String,
    val nome: String,
    val zonaId: String,
    val centro: List<Int>,
    val raio: Double,
)

class ServicoBossMundi(
    private val progresso: RepositorioProgressoSqlite,
    private val movimento: ValidadorMovimento,
    jogabilidadeJson: String,
    private val agoraEpoch: () -> Long = { System.currentTimeMillis() / 1000 },
) {
    private val regra = Json.parseToJsonElement(jogabilidadeJson).jsonObject["bosses_mundi"]!!.jsonObject
    private val intervaloSpawn = regra["intervalo_spawn_segundos"]!!.jsonPrimitive.long
    private val retornoDerrota = regra["retorno_apos_derrota_segundos"]!!.jsonPrimitive.long
    private val vidaMaxima = regra["vida"]!!.jsonPrimitive.int
    private val regioes = regra["regioes"]!!.jsonArray.map { entrada ->
        val objeto = entrada.jsonObject
        val centro = objeto["centro"]!!.jsonArray.map { it.jsonPrimitive.int }
        RegiaoBossMundi(
            id = objeto["id"]!!.jsonPrimitive.content,
            nome = objeto["nome"]!!.jsonPrimitive.content,
            zonaId = objeto["zona_id"]!!.jsonPrimitive.content,
            centro = centro,
            raio = objeto["raio"]!!.jsonPrimitive.double,
        )
    }

    init {
        require(intervaloSpawn > 0 && retornoDerrota >= intervaloSpawn && vidaMaxima > 0)
        require(regioes.isNotEmpty() && regioes.map(RegiaoBossMundi::id).distinct().size == regioes.size)
        require(regioes.all { it.centro.size == 2 && it.raio > 0 })
    }

    @Synchronized
    fun estados(): List<BossMundiEstado> {
        val agora = agoraEpoch()
        return regioes.map { it.estado(agora) }
    }

    @Synchronized
    fun atacar(personagem: Personagem, bossId: String) =
        regioes.find { it.id == bossId }?.let { regiao ->
            require(personagem.zonaAtualId == regiao.zonaId) { "entre na região do miniboss antes de atacar" }
            val posicao = movimento.posicaoAtual(personagem.id)
                ?: throw IllegalStateException("posição do personagem ainda não foi inicializada")
            require(
                hypot(posicao.x - regiao.centro[0], posicao.y - regiao.centro[1]) <= regiao.raio,
            ) { "aproxime-se do círculo de aparição do miniboss" }
            val perfil = progresso.carregar(personagem.id)
            val equipe = progresso.clan(personagem.id)?.nome
            progresso.atacarBossMundi(
                id = regiao.id,
                nome = regiao.nome,
                zonaId = regiao.zonaId,
                centro = regiao.centro,
                raio = regiao.raio,
                vidaMaxima = vidaMaxima,
                intervaloSpawnSegundos = intervaloSpawn,
                retornoDerrotaSegundos = retornoDerrota,
                agoraEpoch = agoraEpoch(),
                dano = (perfil.nivel * DANO_POR_NIVEL).coerceAtLeast(1),
                personagemId = personagem.id,
                nomeJogador = personagem.nome,
                equipe = equipe,
            )
        } ?: throw IllegalArgumentException("região de miniboss desconhecida")

    private fun RegiaoBossMundi.estado(agora: Long) = progresso.estadoBossMundi(
        id = id,
        nome = nome,
        zonaId = zonaId,
        centro = centro,
        raio = raio,
        vidaMaxima = vidaMaxima,
        intervaloSpawnSegundos = intervaloSpawn,
        agoraEpoch = agora,
    )

    companion object {
        private const val DANO_POR_NIVEL = 8
    }
}
