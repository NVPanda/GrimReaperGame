package jogo.mundo.aplicacao

import jogo.anticheat.dominio.Posicao
import jogo.anticheat.dominio.ValidadorMovimento
import jogo.identidade.dominio.InteracaoNpcInvalida
import jogo.identidade.dominio.MasmorraSSBloqueada
import jogo.identidade.dominio.NascimentoJaDefinido
import jogo.identidade.dominio.NascimentoPendente
import jogo.identidade.dominio.Personagem
import jogo.identidade.dominio.SaldoInsuficiente
import jogo.identidade.dominio.SessaoNaoEncontrada
import jogo.identidade.infraestrutura.RepositorioIdentidadeSqlite
import kotlinx.serialization.Serializable
import kotlin.math.hypot

@Serializable
data class ZonaMundo(
    val id: String,
    val nome: String,
    val continenteId: String,
    val tipo: String,
)

@Serializable
data class DestinoTeleporte(
    val zonaId: String,
    val nome: String,
    val continenteId: String,
    val tarifaMc: Long,
)

@Serializable
data class EstadoMundo(
    val personagem: Personagem,
    val zonaAtual: ZonaMundo,
    val saldoMc: Long,
    val destinos: List<DestinoTeleporte>,
)

data class TarifasTeleporte(
    val cidadeMesmoContinente: Long = 100,
    val cidadeIntercontinental: Long = 1_000,
    val masmorra: Long = 10_000,
    val masmorraSs: Long = 100_000,
    val zonaMasmorraSs: String = "trono_abismo",
)

class ServicoMundo(
    private val repositorio: RepositorioIdentidadeSqlite,
    private val movimento: ValidadorMovimento,
    private val zonas: List<ZonaMundo>,
    private val tarifas: TarifasTeleporte = TarifasTeleporte(),
    private val podeEntrarNaMasmorraSs: (String) -> Boolean = { false },
) {
    private val zonasPorId = zonas.associateBy(ZonaMundo::id)

    init {
        require(zonasPorId.size == zonas.size) { "IDs de zona duplicados" }
        require(listOf(
            tarifas.cidadeMesmoContinente,
            tarifas.cidadeIntercontinental,
            tarifas.masmorra,
            tarifas.masmorraSs,
        ).all { it >= 0 }) { "tarifas de teleporte não podem ser negativas" }
        require(tarifas.zonaMasmorraSs in zonasPorId) { "zona de masmorra SS inexistente" }
    }

    fun definirNascimento(sessaoId: String, continenteId: String, zonaId: String): Personagem {
        val zona = zonasPorId[zonaId]
        require(zona != null && zona.tipo == "cidade" && zona.continenteId == continenteId) {
            "escolha uma cidade inicial válida no continente"
        }
        if (!repositorio.definirNascimento(sessaoId, continenteId, zonaId)) {
            val personagem = repositorio.personagemAtivoDados(sessaoId) ?: throw SessaoNaoEncontrada()
            if (personagem.zonaNatalId != null) throw NascimentoJaDefinido()
            throw SessaoNaoEncontrada()
        }
        posicionarCentro(personagemAtivo(sessaoId))
        return personagemAtivo(sessaoId)
    }

    fun respawn(sessaoId: String): EstadoMundo {
        val personagem = personagemAtivo(sessaoId)
        if (personagem.zonaNatalId == null || personagem.zonaSalvaId == null) throw NascimentoPendente()
        val zonaId = repositorio.respawn(sessaoId) ?: throw SessaoNaoEncontrada()
        val zona = zonasPorId[zonaId] ?: throw IllegalStateException("zona salva inexistente: $zonaId")
        val atualizado = personagemAtivo(sessaoId)
        posicionarCentro(atualizado)
        return estado(atualizado, zona)
    }

    fun salvarPonto(sessaoId: String): Personagem {
        val personagem = personagemAtivo(sessaoId)
        val zona = personagem.zonaAtualId?.let(zonasPorId::get) ?: throw InteracaoNpcInvalida()
        if (zona.tipo != "cidade" || !proximoDoNpc(personagem.id, NPC_SAVE)) throw InteracaoNpcInvalida()
        if (!repositorio.salvarPonto(sessaoId, zona.id)) throw InteracaoNpcInvalida()
        return personagemAtivo(sessaoId)
    }

    fun destinos(sessaoId: String): EstadoMundo {
        val personagem = personagemAtivo(sessaoId)
        val atual = personagem.zonaAtualId?.let(zonasPorId::get) ?: throw SessaoNaoEncontrada()
        return estado(personagem, atual)
    }

    fun teletransportar(sessaoId: String, destinoZonaId: String): EstadoMundo {
        val personagem = personagemAtivo(sessaoId)
        val origem = personagem.zonaAtualId?.let(zonasPorId::get) ?: throw SessaoNaoEncontrada()
        if (!proximoDoNpc(personagem.id, NPC_TELEPORTE)) {
            throw InteracaoNpcInvalida()
        }
        val destino = zonasPorId[destinoZonaId] ?: throw IllegalArgumentException("destino inválido")
        if (destino.id == tarifas.zonaMasmorraSs && !podeEntrarNaMasmorraSs(personagem.id)) {
            throw MasmorraSSBloqueada()
        }
        require(destino.id != origem.id) { "já está nessa região" }
        val tarifa = tarifa(origem, destino)
        if (!repositorio.teletransportar(sessaoId, origem.id, destino.id, tarifa)) {
            val atualizado = personagemAtivo(sessaoId)
            if (atualizado.zonaAtualId != origem.id) throw InteracaoNpcInvalida()
            if (atualizado.saldoMc < tarifa) throw SaldoInsuficiente()
            throw InteracaoNpcInvalida()
        }
        val atualizado = personagemAtivo(sessaoId)
        posicionarCentro(atualizado)
        return estado(atualizado, destino)
    }

    private fun estado(personagem: Personagem, zona: ZonaMundo) = EstadoMundo(
        personagem = personagem,
        zonaAtual = zona,
        saldoMc = personagem.saldoMc,
        destinos = zonas.asSequence()
            .filter { it.id != zona.id }
            .map { destino ->
                DestinoTeleporte(destino.id, destino.nome, destino.continenteId, tarifa(zona, destino))
            }
            .toList(),
    )

    private fun tarifa(origem: ZonaMundo, destino: ZonaMundo): Long = when {
        destino.id == tarifas.zonaMasmorraSs -> tarifas.masmorraSs
        destino.tipo == "masmorra" -> tarifas.masmorra
        origem.continenteId == destino.continenteId -> tarifas.cidadeMesmoContinente
        else -> tarifas.cidadeIntercontinental
    }

    private fun personagemAtivo(sessaoId: String): Personagem =
        repositorio.personagemAtivoDados(sessaoId) ?: throw SessaoNaoEncontrada()

    private fun posicionarCentro(personagem: Personagem) {
        movimento.posicionar(personagem.id, POSICAO_CENTRO, System.currentTimeMillis())
    }

    private fun proximoDoNpc(personagemId: String, npc: Posicao): Boolean {
        val atual = movimento.posicaoAtual(personagemId) ?: return false
        return hypot(atual.x - npc.x, atual.y - npc.y) <= DISTANCIA_INTERACAO
    }

    companion object {
        val NPC_SAVE = Posicao(18.0, 15.0)
        val NPC_TELEPORTE = Posicao(22.0, 15.0)
        private val POSICAO_CENTRO = Posicao(20.0, 15.0)
        private const val DISTANCIA_INTERACAO = 1.5
    }
}
