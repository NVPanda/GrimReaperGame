package jogo.jogabilidade.aplicacao

import jogo.identidade.dominio.Personagem
import jogo.identidade.infraestrutura.RepositorioIdentidadeSqlite
import jogo.jogabilidade.infraestrutura.ProgressoPersonagem
import jogo.jogabilidade.infraestrutura.RepositorioProgressoSqlite
import jogo.mundo.aplicacao.HubMultiplayer
import jogo.anticheat.dominio.ValidadorMovimento
import jogo.anticheat.dominio.Posicao
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import kotlin.math.hypot
import kotlin.random.Random

class PkRecusado(mensagem: String) : RuntimeException(mensagem)

@Serializable
data class ResultadoPvP(
    val dano: Int,
    val vidaRestante: Int,
    val derrotado: Boolean,
    val equipamentoPerdido: String?,
    val saqueId: String?,
    val expiraEmEpoch: Long?,
    val infracoesForaDaArea: Int? = null,
)

class ServicoPvP(
    private val identidades: RepositorioIdentidadeSqlite,
    private val progresso: RepositorioProgressoSqlite,
    private val movimento: ValidadorMovimento,
    private val multiplayer: HubMultiplayer,
    regrasJson: String,
    itensJson: String,
    private val sorteio: Random = Random.Default,
    private val agoraEpoch: () -> Long = { System.currentTimeMillis() / 1000 },
) {
    private data class CirculoPk(val centro: Posicao, val raio: Double)
    private val circulos = Json.parseToJsonElement(regrasJson).jsonObject["masmorras"]!!.jsonArray.associate { entrada ->
        val objeto = entrada.jsonObject
        val circulo = objeto["circulo_pk"]!!.jsonObject
        val centro = circulo["centro"]!!.jsonArray
        objeto["id"]!!.jsonPrimitive.content to CirculoPk(
            Posicao(centro[0].jsonPrimitive.double, centro[1].jsonPrimitive.double),
            circulo["raio"]!!.jsonPrimitive.double,
        )
    }
    private val pesosRank = Json.parseToJsonElement(regrasJson).jsonObject["pk"]!!
        .jsonObject["pesos_rank_drop"]!!.jsonObject.mapValues { it.value.jsonPrimitive.double }
    private val chanceDrop = Json.parseToJsonElement(regrasJson).jsonObject["pk"]!!
        .jsonObject["chance_drop_equipamento_pct"]!!.jsonPrimitive.double
    private val tempoSaque = Json.parseToJsonElement(regrasJson).jsonObject["pk"]!!
        .jsonObject["tempo_saque_segundos"]!!.jsonPrimitive.long
    private val tempoSaqueLag = Json.parseToJsonElement(regrasJson).jsonObject["pk"]!!
        .jsonObject["tempo_saque_com_lag_segundos"]!!.jsonPrimitive.long
    private val itens = Json.parseToJsonElement(itensJson).jsonArray.associate { item ->
        val objeto = item.jsonObject
        objeto["id"]!!.jsonPrimitive.content to objeto
    }

    init {
        require(chanceDrop in 0.0..100.0)
        require(tempoSaque > 0 && tempoSaqueLag >= tempoSaque)
        require(circulos.isNotEmpty() && itens.isNotEmpty())
    }

    fun atacar(atacante: Personagem, vitimaId: String): ResultadoPvP {
        require(vitimaId != atacante.id) { "não é possível atacar a si mesmo" }
        val defensor = identidades.personagemPorId(vitimaId) ?: throw IllegalArgumentException("jogador não encontrado")
        val atacanteOnline = multiplayer.localizacao(atacante.id)
            ?: throw PkRecusado("conecte-se ao multiplayer antes de iniciar um combate PK")
        val defensorOnline = multiplayer.localizacao(defensor.id)
            ?: throw PkRecusado("o jogador alvo não está online")
        require(atacante.zonaAtualId == atacanteOnline.zonaId && defensor.zonaAtualId == defensorOnline.zonaId) {
            "posição multiplayer desatualizada"
        }
        require(atacanteOnline.zonaId == defensorOnline.zonaId && atacanteOnline.canalId == defensorOnline.canalId) {
            "jogadores não estão na mesma zona ou instância"
        }
        val circulo = circulos[atacanteOnline.zonaId]
        val dentro = circulo != null
            && hypot(atacanteOnline.posicao.x - circulo.centro.x, atacanteOnline.posicao.y - circulo.centro.y) <= circulo.raio
            && hypot(defensorOnline.posicao.x - circulo.centro.x, defensorOnline.posicao.y - circulo.centro.y) <= circulo.raio
        if (!dentro) {
            val infracoes = progresso.registrarInfracaoPk(atacante.id)
            throw PkRecusado("PK fora da área habilitada foi bloqueado (infração $infracoes)")
        }
        val perfilAtacante = progresso.carregar(atacante.id)
        val perfilDefensor = progresso.carregar(defensor.id)
        if (perfilDefensor.vidaAtual <= 0) throw PkRecusado("jogador alvo está derrotado")
        val dano = (perfilAtacante.nivel * 5 - perfilDefensor.nivel).coerceAtLeast(1)
        val perdeuItem = if (sorteio.nextDouble(0.0, 100.0) < chanceDrop) {
            escolherEquipamento(perfilDefensor)
        } else {
            null
        }
        val tempo = if (maxOf(atacanteOnline.latenciaMs, defensorOnline.latenciaMs) >= LIMIAR_LAG_MS) {
            tempoSaqueLag
        } else {
            tempoSaque
        }
        val resultado = progresso.aplicarDanoPvP(
            atacante.id,
            defensor.id,
            dano,
            perdeuItem,
            agoraEpoch() + tempo,
        )
        if (resultado.derrotado) {
            val respawn = identidades.retornarAoRespawn(defensor.id)
                ?: throw IllegalStateException("personagem derrotado não possui ponto de respawn")
            movimento.posicionar(respawn.id, POSICAO_RESPAWN, System.currentTimeMillis())
        }
        return ResultadoPvP(
            dano,
            resultado.vidaRestante,
            resultado.derrotado,
            perdeuItem,
            resultado.saqueId,
            resultado.saqueId?.let { agoraEpoch() + tempo },
        )
    }

    fun saquear(atacanteId: String, saqueId: String): String =
        progresso.saquearPvP(saqueId, atacanteId, agoraEpoch())
            ?: throw PkRecusado("saque expirado, já coletado ou reservado a outro jogador")

    private fun escolherEquipamento(perfil: ProgressoPersonagem): String? {
        val candidatos = perfil.equipamentos.values.mapNotNull { itemId ->
            val rank = itens[itemId]?.get("rank")?.jsonPrimitive?.content ?: return@mapNotNull null
            val peso = pesosRank[rank] ?: return@mapNotNull null
            itemId to peso
        }
        val pesoTotal = candidatos.sumOf { it.second }
        if (pesoTotal <= 0) return null
        var roleta = sorteio.nextDouble(0.0, pesoTotal)
        for ((itemId, peso) in candidatos) {
            roleta -= peso
            if (roleta < 0) return itemId
        }
        return candidatos.last().first
    }

    companion object {
        private const val LIMIAR_LAG_MS = 500
        private val POSICAO_RESPAWN = Posicao(20.0, 15.0)
    }
}
