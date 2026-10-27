package jogo.seguranca

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.plugins.defaultheaders.*
import io.ktor.server.plugins.ratelimit.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.response.*
import jogo.identidade.dominio.CredenciaisInvalidas
import jogo.identidade.dominio.EmailJaCadastrado
import jogo.identidade.dominio.InteracaoNpcInvalida
import jogo.identidade.dominio.MasmorraSSBloqueada
import jogo.identidade.dominio.LimitePersonagensAtingido
import jogo.identidade.dominio.NascimentoJaDefinido
import jogo.identidade.dominio.NascimentoPendente
import jogo.identidade.dominio.PersonagemNaoEncontrado
import jogo.identidade.dominio.SaldoInsuficiente
import jogo.jogabilidade.aplicacao.AcaoJogoRecusada
import jogo.jogabilidade.aplicacao.PkRecusado
import jogo.identidade.dominio.SessaoNaoEncontrada
import java.util.Date
import kotlin.time.Duration.Companion.minutes

/** Cabeçalhos de segurança em toda resposta (SDD 04). */
fun Application.configurarCabecalhos() {
    install(DefaultHeaders) {
        header("X-Content-Type-Options", "nosniff")
        header("X-Frame-Options", "DENY")
        header("Referrer-Policy", "no-referrer")
        header("Strict-Transport-Security", "max-age=31536000; includeSubDomains")
        header(
            "Content-Security-Policy",
            "default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; connect-src 'self' wss:; img-src 'self' data:; object-src 'none'; frame-ancestors 'none'"
        )
    }
}

/** CORS restrito às origens configuradas. Nunca use anyHost() em produção. */
fun Application.configurarCors(origens: List<String>) {
    install(CORS) {
        origens.forEach { allowHost(it, schemes = listOf("https", "http")) }
        allowMethod(HttpMethod.Get)
        allowMethod(HttpMethod.Post)
        allowHeader(HttpHeaders.Authorization)
        allowHeader(HttpHeaders.ContentType)
        allowCredentials = false
    }
}

/** Limite de taxa: por IP em rotas públicas e sensíveis, por jogador nas ações. */
fun Application.configurarLimiteDeTaxa() {
    install(RateLimit) {
        register(RateLimitName("sensivel")) {
            rateLimiter(limit = 20, refillPeriod = 1.minutes)
            requestKey { call -> call.request.local.remoteAddress }
        }
        register(RateLimitName("jogo")) {
            rateLimiter(limit = 120, refillPeriod = 1.minutes)
            requestKey { call -> call.principal<JWTPrincipal>()?.payload?.subject ?: call.request.local.remoteAddress }
        }
    }
}

/** Autenticação JWT com emissor, audiência e expiração verificados (SEG-01 a SEG-03). */
fun Application.configurarAutenticacao(
    segredo: String,
    emissor: String,
    audiencia: String,
    sessaoValida: (String) -> Boolean,
) {
    install(Authentication) {
        jwt("jogador") {
            verifier(
                JWT.require(Algorithm.HMAC256(segredo))
                    .withIssuer(emissor)
                    .withAudience(audiencia)
                    .build()
            )
            validate { credencial ->
                val expira = credencial.payload.expiresAt
                val sessionId = credencial.payload.id
                if (credencial.payload.subject != null && expira != null && expira.after(Date()) &&
                    !sessionId.isNullOrBlank() && sessaoValida(sessionId)
                ) {
                    JWTPrincipal(credencial.payload)
                } else {
                    null
                }
            }
            challenge { _, _ ->
                call.respond(HttpStatusCode.Unauthorized, mapOf("erro" to "não autenticado"))
            }
        }
    }
}

/** Erros viram respostas genéricas. Detalhes ficam só no log (SEG-08). */
fun Application.configurarErros() {
    install(StatusPages) {
        exception<CredenciaisInvalidas> { call, _ ->
            call.respond(HttpStatusCode.Unauthorized, mapOf("erro" to "email ou senha inválidos"))
        }
        exception<EmailJaCadastrado> { call, _ ->
            call.respond(HttpStatusCode.Conflict, mapOf("erro" to "email já cadastrado"))
        }
        exception<LimitePersonagensAtingido> { call, _ ->
            call.respond(HttpStatusCode.Conflict, mapOf("erro" to "limite de cinco personagens atingido"))
        }
        exception<PersonagemNaoEncontrado> { call, _ ->
            call.respond(HttpStatusCode.NotFound, mapOf("erro" to "personagem não encontrado nesta conta"))
        }
        exception<SessaoNaoEncontrada> { call, _ ->
            call.respond(HttpStatusCode.Conflict, mapOf("erro" to "selecione um personagem antes de jogar"))
        }
        exception<NascimentoJaDefinido> { call, _ ->
            call.respond(HttpStatusCode.Conflict, mapOf("erro" to "o continente e a cidade de nascimento já são definitivos"))
        }
        exception<NascimentoPendente> { call, _ ->
            call.respond(HttpStatusCode.Conflict, mapOf("erro" to "selecione o local de nascimento antes de entrar no mundo"))
        }
        exception<SaldoInsuficiente> { call, _ ->
            call.respond(HttpStatusCode.Conflict, mapOf("erro" to "saldo de Mitrum Coin insuficiente para esse teleporte"))
        }
        exception<InteracaoNpcInvalida> { call, _ ->
            call.respond(HttpStatusCode.Conflict, mapOf("erro" to "aproxime-se do NPC correto em uma cidade"))
        }
        exception<MasmorraSSBloqueada> { call, _ ->
            call.respond(HttpStatusCode.Conflict, mapOf("erro" to "o acesso à masmorra SS exige uma instância ativa de clã"))
        }
        exception<AcaoJogoRecusada> { call, causa ->
            call.respond(HttpStatusCode.Conflict, mapOf("erro" to (causa.message ?: "ação de jogo recusada")))
        }
        exception<PkRecusado> { call, causa ->
            call.respond(HttpStatusCode.Conflict, mapOf("erro" to (causa.message ?: "ação PK recusada")))
        }
        exception<IllegalArgumentException> { call, _ ->
            call.respond(HttpStatusCode.BadRequest, mapOf("erro" to "requisição inválida"))
        }
        exception<Throwable> { call, causa ->
            call.application.log.error("Erro interno", causa)
            call.respond(HttpStatusCode.InternalServerError, mapOf("erro" to "erro interno"))
        }
    }
}

/** Identidade vem sempre do token, nunca do corpo da requisição (SEG-04). */
fun ApplicationCall.jogadorId(): String =
    principal<JWTPrincipal>()?.payload?.subject ?: error("rota sem autenticação")

fun ApplicationCall.sessaoId(): String =
    principal<JWTPrincipal>()?.payload?.id ?: error("rota sem autenticação")
