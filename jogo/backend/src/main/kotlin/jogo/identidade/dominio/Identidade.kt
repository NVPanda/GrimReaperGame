package jogo.identidade.dominio

import kotlinx.serialization.Serializable
import java.time.Instant

data class Conta(
    val id: String,
    val email: String?,
    val nomeExibicao: String,
)

@Serializable
data class Personagem(
    val id: String,
    val nome: String,
    val classeId: String,
    val continenteNatalId: String? = null,
    val zonaNatalId: String? = null,
    val zonaSalvaId: String? = null,
    val zonaAtualId: String? = null,
    val saldoMc: Long = 10,
)

@Serializable
data class ContaPublica(
    val id: String,
    val email: String?,
    val nomeExibicao: String,
)

@Serializable
data class SessaoPublica(
    val acesso: String,
    val tipo: String = "Bearer",
    val expiraEmSegundos: Long,
    val conta: ContaPublica,
    val personagens: List<Personagem>,
    val personagemAtivoId: String?,
)

@Serializable
data class DadosConta(
    val conta: ContaPublica,
    val personagens: List<Personagem>,
    val personagemAtivoId: String?,
)

data class EstadoOAuth(
    val provedor: String,
    val verificador: String?,
    val callbackUrl: String,
    val expiraEm: Instant,
)

data class IdentidadeSocial(
    val provedor: String,
    val subject: String,
    val email: String?,
    val emailVerificado: Boolean,
    val nomeExibicao: String,
)

class CredenciaisInvalidas : RuntimeException()
class EmailJaCadastrado : RuntimeException()
class LimitePersonagensAtingido : RuntimeException()
class PersonagemNaoEncontrado : RuntimeException()
class SessaoNaoEncontrada : RuntimeException()
class NascimentoJaDefinido : RuntimeException()
class NascimentoPendente : RuntimeException()
class SaldoInsuficiente : RuntimeException()
class InteracaoNpcInvalida : RuntimeException()
class MasmorraSSBloqueada : RuntimeException()
