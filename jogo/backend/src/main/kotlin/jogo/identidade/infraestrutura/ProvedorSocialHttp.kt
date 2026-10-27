package jogo.identidade.infraestrutura

import jogo.identidade.dominio.IdentidadeSocial
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.time.Duration

class ProvedorSocialHttp(
    private val ambiente: (String) -> String? = System::getenv,
    private val cliente: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .followRedirects(HttpClient.Redirect.NEVER)
        .build(),
) {
    private val json = Json { ignoreUnknownKeys = true }

    fun habilitado(provedor: String): Boolean = when (provedor) {
        "google" -> credenciais("GOOGLE").let { !it.first.isNullOrBlank() && !it.second.isNullOrBlank() }
        "discord" -> credenciais("DISCORD").let { !it.first.isNullOrBlank() && !it.second.isNullOrBlank() }
        "facebook" -> credenciais("FACEBOOK").let { !it.first.isNullOrBlank() && !it.second.isNullOrBlank() }
        "steam" -> true
        else -> false
    }

    fun urlAutorizacao(
        provedor: String,
        state: String,
        verifier: String?,
        callbackUrl: String,
        baseUrl: String,
    ): String {
        require(habilitado(provedor)) { "provedor social não configurado" }
        if (provedor == "steam") {
            val realm = URI(baseUrl).let { "${it.scheme}://${it.authority}/" }
            return query(
                "https://steamcommunity.com/openid/login",
                listOf(
                    "openid.ns" to "http://specs.openid.net/auth/2.0",
                    "openid.mode" to "checkid_setup",
                    "openid.return_to" to "$callbackUrl?state=$state",
                    "openid.realm" to realm,
                    "openid.identity" to "http://specs.openid.net/auth/2.0/identifier_select",
                    "openid.claimed_id" to "http://specs.openid.net/auth/2.0/identifier_select",
                ),
            )
        }

        val (clientId, _) = credenciais(provedor.uppercase())
        val endpoint = when (provedor) {
            "google" -> "https://accounts.google.com/o/oauth2/v2/auth"
            "discord" -> "https://discord.com/oauth2/authorize"
            "facebook" -> "https://www.facebook.com/${versaoFacebook()}/dialog/oauth"
            else -> error("provedor social não suportado")
        }
        val scopes = when (provedor) {
            "google" -> "openid email profile"
            "discord" -> "identify email"
            "facebook" -> "email public_profile"
            else -> error("provedor social não suportado")
        }
        val params = mutableListOf(
            "client_id" to clientId!!,
            "redirect_uri" to callbackUrl,
            "response_type" to "code",
            "scope" to scopes,
            "state" to state,
        )
        if (verifier != null) {
            params += "code_challenge" to codeChallenge(verifier)
            params += "code_challenge_method" to "S256"
        }
        if (provedor == "google") params += "prompt" to "select_account"
        return query(endpoint, params)
    }

    fun autenticarCodigo(provedor: String, code: String, verifier: String?, callbackUrl: String): IdentidadeSocial {
        require(provedor != "steam") { "Steam usa OpenID, não authorization code" }
        val (clientId, clientSecret) = credenciais(provedor.uppercase())
        require(!clientId.isNullOrBlank() && !clientSecret.isNullOrBlank()) { "provedor social não configurado" }

        val tokenUrl = when (provedor) {
            "google" -> "https://oauth2.googleapis.com/token"
            "discord" -> "https://discord.com/api/oauth2/token"
            "facebook" -> "https://graph.facebook.com/${versaoFacebook()}/oauth/access_token"
            else -> error("provedor social não suportado")
        }
        val form = mutableListOf(
            "client_id" to clientId,
            "client_secret" to clientSecret,
            "code" to code,
            "redirect_uri" to callbackUrl,
            "grant_type" to "authorization_code",
        )
        if (verifier != null) form += "code_verifier" to verifier
        val tokenRequest = if (provedor == "facebook") {
            HttpRequest.newBuilder(URI(query(tokenUrl, form))).timeout(Duration.ofSeconds(15)).GET().build()
        } else {
            HttpRequest.newBuilder(URI(tokenUrl))
                .timeout(Duration.ofSeconds(15))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(formBody(form)))
                .build()
        }
        val token = requisicao(tokenRequest)
        val accessToken = token.jsonObject["access_token"]?.jsonPrimitive?.content
            ?: throw IllegalArgumentException("provedor social recusou a autenticação")

        return when (provedor) {
            "google" -> {
                val perfil = requisicao(
                    HttpRequest.newBuilder(URI("https://openidconnect.googleapis.com/v1/userinfo"))
                        .header("Authorization", "Bearer $accessToken").GET().build(),
                )
                val dados = perfil.jsonObject
                IdentidadeSocial(
                    provedor,
                    dados.string("sub"),
                    dados.optionalString("email"),
                    dados["email_verified"]?.jsonPrimitive?.booleanOrNull == true,
                    dados.optionalString("name").orEmpty(),
                )
            }
            "discord" -> {
                val perfil = requisicao(
                    HttpRequest.newBuilder(URI("https://discord.com/api/users/@me"))
                        .header("Authorization", "Bearer $accessToken").GET().build(),
                )
                val dados = perfil.jsonObject
                IdentidadeSocial(
                    provedor,
                    dados.string("id"),
                    dados.optionalString("email"),
                    dados["verified"]?.jsonPrimitive?.booleanOrNull == true,
                    dados.optionalString("global_name") ?: dados.optionalString("username").orEmpty(),
                )
            }
            "facebook" -> {
                val perfil = requisicao(
                    HttpRequest.newBuilder(URI(query(
                        "https://graph.facebook.com/${versaoFacebook()}/me",
                        listOf("fields" to "id,name,email", "access_token" to accessToken),
                    ))).GET().build(),
                )
                val dados = perfil.jsonObject
                IdentidadeSocial(
                    provedor,
                    dados.string("id"),
                    dados.optionalString("email"),
                    dados["email"] != null,
                    dados.optionalString("name").orEmpty(),
                )
            }
            else -> error("provedor social não suportado")
        }
    }

    fun autenticarSteam(callbackUrl: String, state: String, parametros: Map<String, String>): IdentidadeSocial {
        require(parametros["openid.ns"] == "http://specs.openid.net/auth/2.0")
        require(parametros["openid.mode"] == "id_res")
        require(parametros["openid.op_endpoint"] == STEAM_ENDPOINT)
        require(parametros["openid.return_to"] == "$callbackUrl?state=$state")

        val subject = parametros["openid.claimed_id"]
            ?.let { Regex("https://steamcommunity\\.com/openid/id/(\\d+)").matchEntire(it) }
            ?.groupValues?.get(1)
            ?: throw IllegalArgumentException("identidade Steam inválida")
        require(parametros["openid.identity"] == "https://steamcommunity.com/openid/id/$subject")

        val body = parametros.filterKeys { it.startsWith("openid.") }
            .toMutableMap().apply { put("openid.mode", "check_authentication") }
        val response = cliente.send(
            HttpRequest.newBuilder(URI(STEAM_ENDPOINT))
                .timeout(Duration.ofSeconds(15))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(formBody(body.toList())))
                .build(),
            HttpResponse.BodyHandlers.ofString(),
        )
        require(response.statusCode() in 200..299 && Regex("(?m)^is_valid:true\\s*$").containsMatchIn(response.body())) {
            "Steam recusou a autenticação OpenID"
        }
        return IdentidadeSocial("steam", subject, null, false, "Steam $subject")
    }

    private fun requisicao(request: HttpRequest) =
        cliente.send(request, HttpResponse.BodyHandlers.ofString()).let { response ->
            if (response.statusCode() !in 200..299) {
                throw IllegalArgumentException("provedor social indisponível ou autenticação recusada")
            }
            json.parseToJsonElement(response.body())
        }

    private fun credenciais(prefixo: String): Pair<String?, String?> =
        ambiente("${prefixo}_CLIENT_ID") to ambiente("${prefixo}_CLIENT_SECRET")

    private fun versaoFacebook() = ambiente("FACEBOOK_GRAPH_VERSION") ?: "v22.0"

    private fun kotlinx.serialization.json.JsonObject.string(chave: String): String =
        this[chave]?.jsonPrimitive?.content?.takeIf(String::isNotBlank)
            ?: throw IllegalArgumentException("perfil social inválido")

    private fun kotlinx.serialization.json.JsonObject.optionalString(chave: String): String? =
        this[chave]?.jsonPrimitive?.contentOrNull?.takeIf(String::isNotBlank)

    private fun codeChallenge(verifier: String): String =
        java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(
            java.security.MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray()),
        )

    private fun formBody(params: List<Pair<String, String>>): String =
        params.joinToString("&") { "${encode(it.first)}=${encode(it.second)}" }

    private fun query(base: String, params: List<Pair<String, String>>): String =
        "$base?${formBody(params)}"

    private fun encode(value: String): String =
        URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20")

    companion object {
        private const val STEAM_ENDPOINT = "https://steamcommunity.com/openid/login"
    }
}
