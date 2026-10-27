package jogo.identidade.aplicacao

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import jogo.identidade.dominio.*
import jogo.identidade.infraestrutura.RepositorioIdentidadeSqlite
import org.bouncycastle.crypto.generators.Argon2BytesGenerator
import org.bouncycastle.crypto.params.Argon2Parameters
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Instant
import java.util.Base64
import java.util.Date
import java.util.UUID

class ServicoIdentidade(
    private val repositorio: RepositorioIdentidadeSqlite,
    segredoJwt: String,
    private val classesPermitidas: Set<String>,
    private val emissor: String = "jogo",
    private val audiencia: String = "jogo-cliente",
    private val duracaoSessaoSegundos: Long = 8 * 60 * 60,
) {
    private val chaveJwt = Algorithm.HMAC256(segredoJwt)
    private val random = SecureRandom()
    private val hashFalso = hashSenha("senha-inexistente-${UUID.randomUUID()}")

    fun cadastrar(emailInformado: String, senha: String): SessaoPublica {
        val email = normalizarEmail(emailInformado)
        validarSenha(senha)
        if (repositorio.contaPorEmail(email) != null) throw EmailJaCadastrado()
        val (hash, salt) = hashSenha(senha)
        val conta = try {
            repositorio.criarConta(email, email.substringBefore("@"), hash, salt)
        } catch (erro: java.sql.SQLException) {
            if (erro.message.orEmpty().contains("UNIQUE constraint failed")) throw EmailJaCadastrado()
            throw erro
        }
        return abrirSessao(conta)
    }

    fun autenticar(emailInformado: String, senha: String): SessaoPublica {
        val email = try {
            normalizarEmail(emailInformado)
        } catch (_: IllegalArgumentException) {
            null
        }
        val conta = email?.let(repositorio::contaPorEmail)
        val credencial = email?.let(repositorio::senhaPorEmail)
        if (credencial == null) {
            verificarSenha(senha.take(128), hashFalso.first, hashFalso.second)
            throw CredenciaisInvalidas()
        }
        if (!verificarSenha(senha, credencial.first, credencial.second)) throw CredenciaisInvalidas()
        return abrirSessao(conta ?: throw CredenciaisInvalidas())
    }

    fun autenticarSocial(identidade: IdentidadeSocial): Pair<Conta, String> {
        require(identidade.provedor in setOf("google", "discord", "facebook", "steam"))
        require(identidade.subject.isNotBlank() && identidade.subject.length <= 200)
        val conta = repositorio.contaSocial(identidade)
        val expira = expirarSessaoEm()
        return conta to repositorio.criarSessao(conta.id, expira)
    }

    fun criarCodigoOAuth(sessaoId: String): String {
        val conta = repositorio.contaDaSessao(sessaoId) ?: throw SessaoNaoEncontrada()
        val codigo = tokenAleatorio()
        repositorio.salvarCodigoOAuth(hashCodigo(codigo), conta.id, sessaoId, Instant.now().plusSeconds(60))
        return codigo
    }

    fun trocarCodigoOAuth(codigo: String): SessaoPublica? {
        if (codigo.length !in 40..64) return null
        val (conta, sessaoId) = repositorio.consumirCodigoOAuth(hashCodigo(codigo)) ?: return null
        val expira = repositorio.expiracaoSessao(sessaoId) ?: return null
        return respostaSessao(conta, sessaoId, expira)
    }

    fun dadosConta(sessaoId: String): DadosConta {
        val conta = repositorio.contaDaSessao(sessaoId) ?: throw SessaoNaoEncontrada()
        return DadosConta(
            conta = conta.publica(),
            personagens = repositorio.personagens(conta.id),
            personagemAtivoId = repositorio.personagemAtivo(sessaoId),
        )
    }

    fun criarPersonagem(sessaoId: String, nome: String, classeId: String): Personagem {
        val conta = repositorio.contaDaSessao(sessaoId) ?: throw SessaoNaoEncontrada()
        require(classeId in classesPermitidas) { "classe inválida" }
        return repositorio.criarPersonagem(conta.id, nome, classeId)
    }

    fun selecionarPersonagem(sessaoId: String, personagemId: String): Boolean =
        repositorio.selecionarPersonagem(sessaoId, personagemId)

    fun personagemAtivo(sessaoId: String): String? = repositorio.personagemAtivo(sessaoId)

    fun sessaoValida(sessaoId: String): Boolean =
        sessaoId.length <= 64 && repositorio.sessaoAtiva(sessaoId)

    fun encerrarSessao(sessaoId: String) = repositorio.revogarSessao(sessaoId)

    private fun abrirSessao(conta: Conta): SessaoPublica {
        val expira = expirarSessaoEm()
        val sessaoId = repositorio.criarSessao(conta.id, expira)
        return respostaSessao(conta, sessaoId, expira)
    }

    private fun respostaSessao(conta: Conta, sessaoId: String, expira: Instant): SessaoPublica {
        val criadoEm = Instant.now()
        val token = JWT.create()
            .withIssuer(emissor)
            .withAudience(audiencia)
            .withSubject(conta.id)
            .withJWTId(sessaoId)
            .withIssuedAt(Date.from(criadoEm))
            .withExpiresAt(Date.from(expira))
            .sign(chaveJwt)
        return SessaoPublica(
            acesso = token,
            expiraEmSegundos = expira.epochSecond - criadoEm.epochSecond,
            conta = conta.publica(),
            personagens = repositorio.personagens(conta.id),
            personagemAtivoId = repositorio.personagemAtivo(sessaoId),
        )
    }

    private fun expirarSessaoEm(): Instant =
        Instant.ofEpochSecond(Instant.now().epochSecond + duracaoSessaoSegundos)

    private fun normalizarEmail(valor: String): String {
        val email = valor.trim().lowercase()
        require(email.length <= 254 && EMAIL.matches(email)) { "email inválido" }
        return email
    }

    private fun validarSenha(senha: String) {
        require(senha.length in 12..128) { "a senha deve ter entre 12 e 128 caracteres" }
    }

    private fun hashSenha(senha: String): Pair<ByteArray, ByteArray> {
        val salt = ByteArray(16).also(random::nextBytes)
        val hash = ByteArray(32)
        val parametros = Argon2Parameters.Builder(Argon2Parameters.ARGON2_id)
            .withVersion(Argon2Parameters.ARGON2_VERSION_13)
            .withIterations(3)
            .withMemoryAsKB(65_536)
            .withParallelism(1)
            .withSalt(salt)
            .build()
        Argon2BytesGenerator().apply { init(parametros) }.generateBytes(senha.toCharArray(), hash)
        return hash to salt
    }

    private fun verificarSenha(senha: String, hashEsperado: ByteArray, salt: ByteArray): Boolean {
        if (senha.length > 128) return false
        val parametros = Argon2Parameters.Builder(Argon2Parameters.ARGON2_id)
            .withVersion(Argon2Parameters.ARGON2_VERSION_13)
            .withIterations(3)
            .withMemoryAsKB(65_536)
            .withParallelism(1)
            .withSalt(salt)
            .build()
        val calculado = ByteArray(hashEsperado.size)
        Argon2BytesGenerator().apply { init(parametros) }.generateBytes(senha.toCharArray(), calculado)
        return MessageDigest.isEqual(hashEsperado, calculado)
    }

    private fun tokenAleatorio(): String =
        Base64.getUrlEncoder().withoutPadding().encodeToString(ByteArray(32).also(random::nextBytes))

    private fun hashCodigo(codigo: String): String =
        MessageDigest.getInstance("SHA-256").digest(codigo.toByteArray()).joinToString("") { "%02x".format(it) }

    private fun Conta.publica() = ContaPublica(id, email, nomeExibicao)

    companion object {
        private val EMAIL = Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")
    }
}
