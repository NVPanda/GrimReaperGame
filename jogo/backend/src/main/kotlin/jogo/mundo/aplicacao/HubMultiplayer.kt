package jogo.mundo.aplicacao

import io.ktor.server.websocket.DefaultWebSocketServerSession
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.send
import jogo.anticheat.dominio.Posicao
import jogo.anticheat.dominio.ValidadorMovimento
import jogo.identidade.dominio.Personagem
import jogo.identidade.infraestrutura.RepositorioIdentidadeSqlite
import jogo.jogabilidade.infraestrutura.RepositorioProgressoSqlite
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.slf4j.LoggerFactory
import java.io.IOException
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.channels.ClosedSendChannelException

@Serializable
data class JogadorPresente(
    val personagemId: String,
    val nome: String,
    val classeId: String,
    val x: Double,
    val y: Double,
)

@Serializable
data class EventoZona(
    val tipo: String,
    val jogador: JogadorPresente? = null,
    val jogadorId: String? = null,
    val jogadores: List<JogadorPresente> = emptyList(),
    val sentAt: Long? = null,
)

data class PresencaOnline(val zonaId: String, val canalId: String, val posicao: Posicao, val latenciaMs: Int)

class TicketMultiplayer internal constructor(
    val personagem: Personagem,
    val zonaId: String,
    val canalId: String,
    val posicao: Posicao,
    val expiraEmMs: Long,
)

class HubMultiplayer(
    private val identidades: RepositorioIdentidadeSqlite,
    private val movimento: ValidadorMovimento,
    private val progresso: RepositorioProgressoSqlite,
    private val agoraMs: () -> Long = System::currentTimeMillis,
) {
    private data class Conexao(
        val sessao: DefaultWebSocketServerSession,
        val zonaId: String,
        val canalId: String,
        val jogador: JogadorPresente,
        val latenciaMs: Int = 0,
    )

    private val log = LoggerFactory.getLogger(HubMultiplayer::class.java)
    private val tickets = ConcurrentHashMap<String, TicketMultiplayer>()
    private val conexoes = ConcurrentHashMap<String, Conexao>()
    private val salas = ConcurrentHashMap<String, ConcurrentHashMap<String, Conexao>>()
    private val lockConexoes = Any()

    fun criarTicket(sessaoId: String): String {
        val personagem = identidades.personagemAtivoDados(sessaoId)
            ?: throw IllegalStateException("não há personagem ativo para a sessão")
        val zonaId = personagem.zonaAtualId
            ?: throw IllegalStateException("personagem ainda não entrou no mundo")
        val posicao = movimento.posicaoAtual(personagem.id)
            ?: throw IllegalStateException("posição do personagem ainda não foi inicializada")
        val agora = agoraMs()
        tickets.entries.removeIf { it.value.expiraEmMs <= agora }
        val ticket = UUID.randomUUID().toString()
        tickets[ticket] = TicketMultiplayer(
            personagem,
            zonaId,
            canal(personagem.id, zonaId),
            posicao,
            agora + DURACAO_TICKET_MS,
        )
        return ticket
    }

    fun consumirTicket(ticket: String): TicketMultiplayer? {
        val emitido = tickets.remove(ticket) ?: return null
        return emitido.takeIf { it.expiraEmMs > agoraMs() }
    }

    fun localizacao(personagemId: String): PresencaOnline? =
        conexoes[personagemId]?.let { conexao ->
            PresencaOnline(
                conexao.zonaId,
                conexao.canalId,
                Posicao(conexao.jogador.x, conexao.jogador.y),
                conexao.latenciaMs,
            )
        }

    suspend fun processarTexto(
        sessao: DefaultWebSocketServerSession,
        personagemId: String,
        texto: String,
    ): Boolean {
        val mensagem = try {
            Json.parseToJsonElement(texto).jsonObject
        } catch (_: SerializationException) {
            return false
        }
        val conexao = conexoes[personagemId]?.takeIf { it.sessao === sessao } ?: return false
        return when (mensagem["tipo"]?.jsonPrimitive?.content) {
            "ping" -> {
                val sentAt = mensagem["sentAt"]?.jsonPrimitive?.content?.toLongOrNull() ?: return false
                sessao.send(Json.encodeToString(EventoZona(tipo = "pong", sentAt = sentAt)))
                true
            }
            "latencia" -> {
                val latencia = mensagem["ms"]?.jsonPrimitive?.content?.toIntOrNull() ?: return false
                if (latencia !in 0..60_000) return false
                conexoes[personagemId] = conexao.copy(latenciaMs = latencia)
                true
            }
            else -> false
        }
    }

    suspend fun entrar(sessao: DefaultWebSocketServerSession, ticket: TicketMultiplayer) {
        val jogador = JogadorPresente(
            ticket.personagem.id,
            ticket.personagem.nome,
            ticket.personagem.classeId,
            ticket.posicao.x,
            ticket.posicao.y,
        )
        val ocupacao = conexoes.values.count { it.canalId == ticket.canalId }
        if (ocupacao >= MAX_JOGADORES_POR_ZONA && !conexoes.containsKey(jogador.personagemId)) {
            sessao.close(CloseReason(CloseReason.Codes.TRY_AGAIN_LATER, "Zona cheia"))
            return
        }
        val conexao = Conexao(sessao, ticket.zonaId, ticket.canalId, jogador)
        var anterior: Conexao? = null
        val aceita = synchronized(lockConexoes) {
            val sala = salas.computeIfAbsent(ticket.canalId) { ConcurrentHashMap() }
            anterior = conexoes[jogador.personagemId]
            if (sala.size >= MAX_JOGADORES_POR_ZONA && anterior?.canalId != ticket.canalId) {
                false
            } else {
                anterior?.let { antiga ->
                    removerDaSala(jogador.personagemId, antiga)
                }
                conexoes[jogador.personagemId] = conexao
                sala[jogador.personagemId] = conexao
                true
            }
        }
        if (!aceita) {
            sessao.close(CloseReason(CloseReason.Codes.TRY_AGAIN_LATER, "Zona cheia"))
            return
        }
        if (anterior != null && anterior.sessao !== sessao) {
            anterior.sessao.close(CloseReason(CloseReason.Codes.NORMAL, "Sessão substituída"))
        }

        sessao.send(Json.encodeToString(EventoZona(
            tipo = "snapshot",
            jogadores = salas[ticket.canalId]?.values
                ?.filter { it.canalId == ticket.canalId }
                ?.map(Conexao::jogador)
                ?: emptyList(),
        )))
        publicar(ticket.canalId, EventoZona(tipo = "entrou", jogador = jogador), jogador.personagemId)
    }

    suspend fun atualizar(personagem: Personagem, posicao: Posicao) {
        val zonaMundoId = personagem.zonaAtualId ?: return
        val conexaoAtual = conexoes[personagem.id] ?: return
        val canalId = canal(personagem.id, zonaMundoId)
        val atualizado = conexaoAtual.copy(
            zonaId = zonaMundoId,
            canalId = canalId,
            jogador = conexaoAtual.jogador.copy(x = posicao.x, y = posicao.y),
        )
        val salaCheia = synchronized(lockConexoes) {
            if (conexoes[personagem.id] !== conexaoAtual) return
            val destino = salas.computeIfAbsent(canalId) { ConcurrentHashMap() }
            if (conexaoAtual.canalId != canalId && destino.size >= MAX_JOGADORES_POR_ZONA) {
                true
            } else {
                if (conexaoAtual.canalId != canalId) {
                    removerDaSala(personagem.id, conexaoAtual)
                }
                conexoes[personagem.id] = atualizado
                destino[personagem.id] = atualizado
                false
            }
        }
        if (salaCheia) {
            sair(conexaoAtual.sessao, personagem.id)
            conexaoAtual.sessao.close(CloseReason(CloseReason.Codes.TRY_AGAIN_LATER, "Zona cheia"))
            return
        }
        publicar(canalId, EventoZona(tipo = "movimento", jogador = atualizado.jogador))
        if (conexaoAtual.canalId != canalId) {
            publicar(conexaoAtual.canalId, EventoZona(tipo = "saiu", jogadorId = personagem.id))
            enviarSnapshot(atualizado)
        }
    }

    private fun canal(personagemId: String, zonaId: String): String {
        val raid = progresso.raidAtiva(personagemId)
        return if (raid?.zonaId == zonaId) "$zonaId:raid:${raid.id}" else zonaId
    }

    suspend fun sair(sessao: DefaultWebSocketServerSession, personagemId: String) {
        val conexao = conexoes[personagemId] ?: return
        val removida = synchronized(lockConexoes) {
            if (conexao.sessao !== sessao || !conexoes.remove(personagemId, conexao)) {
                false
            } else {
                removerDaSala(personagemId, conexao)
                true
            }
        }
        if (!removida) return
        publicar(conexao.canalId, EventoZona(tipo = "saiu", jogadorId = personagemId))
    }

    private suspend fun enviarSnapshot(conexao: Conexao) {
        val jogadores = salas[conexao.canalId]?.values
            ?.map(Conexao::jogador)
            ?: emptyList()
        enviar(conexao, EventoZona(tipo = "snapshot", jogadores = jogadores))
    }

    private suspend fun publicar(zonaId: String, evento: EventoZona, ignorarId: String? = null) {
        val destinos = salas[zonaId]?.entries
            ?.filter { it.key != ignorarId }
            ?: emptyList()
        val payload = Json.encodeToString(evento)
        for ((jogadorId, conexao) in destinos) {
            try {
                conexao.sessao.send(Frame.Text(payload))
            } catch (erro: ClosedSendChannelException) {
                removerConexaoInativa(jogadorId, conexao, erro)
            } catch (erro: IOException) {
                removerConexaoInativa(jogadorId, conexao, erro)
            }
        }
    }

    private suspend fun enviar(conexao: Conexao, evento: EventoZona) {
        try {
            conexao.sessao.send(Json.encodeToString(evento))
        } catch (erro: ClosedSendChannelException) {
            removerConexaoInativa(conexao.jogador.personagemId, conexao, erro)
        } catch (erro: IOException) {
            removerConexaoInativa(conexao.jogador.personagemId, conexao, erro)
        }
    }

    private suspend fun removerConexaoInativa(id: String, conexao: Conexao, erro: Exception) {
        val removida = synchronized(lockConexoes) {
            if (!conexoes.remove(id, conexao)) false
            else {
                removerDaSala(id, conexao)
                true
            }
        }
        if (!removida) return
        log.info("Conexão multiplayer encerrada para {}: {}", id, erro.message)
        publicar(conexao.canalId, EventoZona(tipo = "saiu", jogadorId = id), id)
    }

    private fun removerDaSala(personagemId: String, conexao: Conexao) {
        val sala = salas[conexao.canalId] ?: return
        sala.remove(personagemId, conexao)
        if (sala.isEmpty()) salas.remove(conexao.canalId, sala)
    }

    companion object {
        private const val DURACAO_TICKET_MS = 30_000L
        private const val MAX_JOGADORES_POR_ZONA = 100
    }
}
