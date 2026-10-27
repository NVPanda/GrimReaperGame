package jogo.identidade.aplicacao

import jogo.identidade.dominio.EstadoOAuth
import jogo.identidade.infraestrutura.ProvedorSocialHttp
import jogo.identidade.infraestrutura.RepositorioIdentidadeSqlite
import java.security.SecureRandom
import java.time.Instant
import java.util.Base64

class ServicoOAuth(
    private val repositorio: RepositorioIdentidadeSqlite,
    private val identidade: ServicoIdentidade,
    private val provedores: ProvedorSocialHttp,
    private val urlPublica: String,
) {
    private val random = SecureRandom()

    fun urlInicio(provedor: String): String {
        require(provedor in PROVEDORES) { "provedor social inválido" }
        require(provedores.habilitado(provedor)) { "provedor social não configurado" }
        val state = aleatorio()
        val verifier = if (provedor == "google" || provedor == "discord") aleatorio() else null
        val callbackUrl = "${urlPublica.trimEnd('/')}/api/auth/social/$provedor/callback"
        repositorio.salvarEstadoOAuth(
            state,
            EstadoOAuth(provedor, verifier, callbackUrl, Instant.now().plusSeconds(300)),
        )
        return provedores.urlAutorizacao(provedor, state, verifier, callbackUrl, urlPublica)
    }

    fun finalizar(provedor: String, state: String, code: String?, parametros: Map<String, String>): String {
        val estado = repositorio.consumirEstadoOAuth(state) ?: throw IllegalArgumentException("fluxo OAuth inválido ou expirado")
        require(estado.provedor == provedor && provedor in PROVEDORES) { "fluxo OAuth inválido" }
        val identidadeSocial = if (provedor == "steam") {
            provedores.autenticarSteam(estado.callbackUrl, state, parametros)
        } else {
            require(!code.isNullOrBlank() && code.length <= 4096) { "código OAuth inválido" }
            provedores.autenticarCodigo(provedor, code, estado.verificador, estado.callbackUrl)
        }
        val (_, sessaoId) = identidade.autenticarSocial(identidadeSocial)
        return identidade.criarCodigoOAuth(sessaoId)
    }

    fun cancelar(provedor: String, state: String) {
        val estado = repositorio.consumirEstadoOAuth(state) ?: return
        require(estado.provedor == provedor) { "fluxo OAuth inválido" }
    }

    private fun aleatorio(): String =
        Base64.getUrlEncoder().withoutPadding().encodeToString(ByteArray(32).also(random::nextBytes))

    companion object {
        val PROVEDORES = setOf("google", "discord", "steam", "facebook")
    }
}
