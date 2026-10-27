package jogo

import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.ratelimit.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.http.content.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import jogo.anticheat.dominio.Posicao
import jogo.anticheat.dominio.ValidadorMovimento
import jogo.anticheat.dominio.Veredito
import jogo.compartilhado.Validacao
import jogo.identidade.aplicacao.ServicoIdentidade
import jogo.identidade.aplicacao.ServicoOAuth
import jogo.identidade.dominio.*
import jogo.identidade.infraestrutura.ProvedorSocialHttp
import jogo.identidade.infraestrutura.RepositorioIdentidadeSqlite
import jogo.loja.aplicacao.ServicoCheckout
import jogo.loja.infraestrutura.CatalogoJson
import jogo.loja.infraestrutura.MercadoPagoAdaptador
import jogo.loja.infraestrutura.PedidoRepositorioMemoria
import jogo.mundo.aplicacao.ServicoMundo
import jogo.mundo.aplicacao.TarifasTeleporte
import jogo.mundo.aplicacao.HubMultiplayer
import jogo.mundo.aplicacao.ZonaMundo
import jogo.jogabilidade.aplicacao.ServicoCombate
import jogo.jogabilidade.aplicacao.ServicoBossMundi
import jogo.jogabilidade.aplicacao.ServicoPvP
import jogo.jogabilidade.infraestrutura.EstadoClanJogo
import jogo.jogabilidade.infraestrutura.EstadoRaidJogo
import jogo.jogabilidade.infraestrutura.MembroClanJogo
import jogo.jogabilidade.infraestrutura.RepositorioProgressoSqlite
import jogo.seguranca.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import java.io.File
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.time.Clock

@Serializable data class CorpoCheckout(val itemId: String)
@Serializable data class CorpoMovimento(val x: Double, val y: Double, val timestampMs: Long)
@Serializable data class ItemPublico(val id: String, val nome: String, val descricao: String, val tipo: String, val precoCentavos: Long)
@Serializable data class CorpoCredenciais(val email: String, val senha: String)
@Serializable data class CorpoCriarPersonagem(val nome: String, val classeId: String)
@Serializable data class CorpoSelecionarPersonagem(val personagemId: String)
@Serializable data class CorpoCodigoOAuth(val codigo: String)
@Serializable data class CorpoNascimento(val continenteId: String, val zonaId: String)
@Serializable data class CorpoTeleporte(val destinoZonaId: String)
@Serializable data class CorpoAtaqueMonstro(val monstroId: String)
@Serializable data class CorpoAtaqueBossMundi(val bossId: String)
@Serializable data class CorpoVendaItem(val itemId: String, val quantidade: Int)
@Serializable data class CorpoMissao(val questId: String)
@Serializable data class CorpoItemJogo(val itemId: String)
@Serializable data class CorpoCriarClan(val nome: String)
@Serializable data class CorpoConviteClan(val codigoConvite: String)
@Serializable data class CorpoIniciarRaid(val membros: List<String>)
@Serializable data class CorpoAtacarJogador(val personagemId: String)
@Serializable data class CorpoSaquePvP(val saqueId: String)

fun main() {
    embeddedServer(
        Netty,
        host = System.getenv("HOST") ?: "0.0.0.0",
        port = System.getenv("PORTA")?.toInt() ?: 8080,
    ) {
        modulo()
    }.start(wait = true)
}

fun Application.modulo() {
    val segredo = System.getenv("JWT_SEGREDO") ?: error("defina JWT_SEGREDO")
    require(segredo.toByteArray(Charsets.UTF_8).size >= 32) { "JWT_SEGREDO deve ter pelo menos 32 bytes" }
    val origens = (System.getenv("ORIGENS_PERMITIDAS") ?: "localhost").split(",")
    val caminhoCatalogo = System.getenv("CATALOGO_PATH") ?: "src/main/resources/catalogo.json"
    val diretorioCliente = System.getenv("CLIENTE_DIST_PATH")?.let {
        File(it).canonicalFile.also { diretorio ->
            require(diretorio.isDirectory) { "CLIENTE_DIST_PATH não existe ou não é um diretório" }
        }
    }
    val classesPath = System.getenv("CLASSES_PATH") ?: "../dados/classes.json"
    val classesPermitidas = kotlinx.serialization.json.Json.parseToJsonElement(
        File(classesPath).readText(Charsets.UTF_8),
    ).jsonArray.map { it.jsonObject["id"]!!.jsonPrimitive.content }.toSet()
    val mapasPath = System.getenv("MAPAS_PATH") ?: "../dados/mapas.json"
    val zonasJogo = kotlinx.serialization.json.Json.parseToJsonElement(
        File(mapasPath).readText(Charsets.UTF_8),
    ).jsonArray.map { zona ->
        val dadosZona = zona.jsonObject
        ZonaMundo(
            id = dadosZona["id"]!!.jsonPrimitive.content,
            nome = dadosZona["nome"]!!.jsonPrimitive.content,
            continenteId = dadosZona["cont"]!!.jsonPrimitive.content,
            tipo = dadosZona["tipo"]!!.jsonPrimitive.content,
        )
    }
    val caminhoEconomia = System.getenv("ECONOMIA_PATH") ?: "../dados/economia.json"
    val caminhoJogabilidade = System.getenv("JOGABILIDADE_PATH") ?: "../dados/jogabilidade.json"
    val dadosTarifas = kotlinx.serialization.json.Json.parseToJsonElement(
        File(caminhoEconomia).readText(Charsets.UTF_8),
    ).jsonObject["tarifas_teleporte_mc"]!!.jsonObject
    val tarifasTeleporte = TarifasTeleporte(
        cidadeMesmoContinente = dadosTarifas["cidade_mesmo_continente"]!!.jsonPrimitive.long,
        cidadeIntercontinental = dadosTarifas["cidade_intercontinental"]!!.jsonPrimitive.long,
        masmorra = dadosTarifas["masmorra"]!!.jsonPrimitive.long,
        masmorraSs = dadosTarifas["masmorra_ss"]!!.jsonPrimitive.long,
        zonaMasmorraSs = dadosTarifas["zona_masmorra_ss"]!!.jsonPrimitive.content,
    )
    val repositorioIdentidade = RepositorioIdentidadeSqlite(System.getenv("IDENTIDADE_DB") ?: "data/jogo.sqlite")
    val repositorioProgresso = RepositorioProgressoSqlite(System.getenv("IDENTIDADE_DB") ?: "data/jogo.sqlite")
    val identidade = ServicoIdentidade(repositorioIdentidade, segredo, classesPermitidas)
    val urlPublica = (System.getenv("URL_PUBLICA") ?: "http://localhost:8080").trimEnd('/')
    val oauth = ServicoOAuth(
        repositorioIdentidade,
        identidade,
        ProvedorSocialHttp(),
        urlPublica,
    )
    val relogio = Clock.systemUTC()

    install(ContentNegotiation) { json() }
    install(WebSockets)
    configurarCabecalhos()
    configurarCors(origens)
    configurarLimiteDeTaxa()
    configurarAutenticacao(
        segredo,
        emissor = "jogo",
        audiencia = "jogo-cliente",
        sessaoValida = identidade::sessaoValida,
    )
    configurarErros()

    // Catálogo relido a cada chamada: alterações feitas pelo administrador valem sem reiniciar.
    val lerCatalogo = { CatalogoJson.carregar(File(caminhoCatalogo).readText()) }

    val pagamento = MercadoPagoAdaptador(
        accessToken = System.getenv("MP_ACCESS_TOKEN") ?: "",
        urlPublica = System.getenv("URL_PUBLICA") ?: "http://localhost:8080",
    )
    val checkout = ServicoCheckout(
        catalogo = lerCatalogo,
        pedidos = PedidoRepositorioMemoria(),
        pagamento = pagamento,
        relogio = relogio,
        // Integração com o servidor de jogo pendente (SDD 05): conceder item ao personagem.
        liberar = { _, _ -> },
    )
    val movimento = ValidadorMovimento()
    val mundo = ServicoMundo(
        repositorioIdentidade,
        movimento,
        zonasJogo,
        tarifasTeleporte,
        podeEntrarNaMasmorraSs = { personagemId -> repositorioProgresso.raidAtiva(personagemId) != null },
    )
    val multiplayer = HubMultiplayer(repositorioIdentidade, movimento, repositorioProgresso)
    val pvp = ServicoPvP(
        repositorioIdentidade,
        repositorioProgresso,
        movimento,
        multiplayer,
        File(caminhoJogabilidade).readText(Charsets.UTF_8),
        File(System.getenv("ITENS_PATH") ?: "../dados/itens.json").readText(Charsets.UTF_8),
    )
    val combate = ServicoCombate(
        repositorioProgresso,
        movimento,
        File(System.getenv("MONSTROS_PATH") ?: "../dados/monstros.json").readText(Charsets.UTF_8),
        File(System.getenv("ITENS_PATH") ?: "../dados/itens.json").readText(Charsets.UTF_8),
        File(mapasPath).readText(Charsets.UTF_8),
        File(System.getenv("QUESTS_PATH") ?: "../dados/quests.json").readText(Charsets.UTF_8),
        File(System.getenv("NPCS_PATH") ?: "../dados/npcs.json").readText(Charsets.UTF_8),
        File(caminhoJogabilidade).readText(Charsets.UTF_8),
    )
    val bossesMundi = ServicoBossMundi(
        repositorioProgresso,
        movimento,
        File(caminhoJogabilidade).readText(Charsets.UTF_8),
    )

    routing {
        webSocket("/api/multiplayer/zone") {
            val ticket = call.request.queryParameters["ticket"]?.let(multiplayer::consumirTicket)
            if (ticket == null) {
                close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "Ticket inválido ou expirado"))
                return@webSocket
            }
            multiplayer.entrar(this, ticket)
            try {
                for (frame in incoming) {
                    when (frame) {
                        is Frame.Close -> break
                        is Frame.Text -> {
                            if (!multiplayer.processarTexto(this, ticket.personagem.id, frame.readText())) {
                                close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "Mensagem multiplayer inválida"))
                                break
                            }
                        }
                        else -> Unit
                    }
                }
            } finally {
                multiplayer.sair(this, ticket.personagem.id)
            }
        }

        route("/api/auth") {
            get("/providers") {
                val provedoresConfigurados = ProvedorSocialHttp()
                call.respond(
                    mapOf("provedores" to ServicoOAuth.PROVEDORES
                        .filter(provedoresConfigurados::habilitado)
                        .sorted()),
                )
            }

            rateLimit(RateLimitName("sensivel")) {
                post("/register") {
                    val corpo = call.receive<CorpoCredenciais>()
                    val sessao = identidade.cadastrar(corpo.email, corpo.senha)
                    call.respond(HttpStatusCode.Created, sessao)
                }
                post("/login") {
                    val corpo = call.receive<CorpoCredenciais>()
                    call.respond(identidade.autenticar(corpo.email, corpo.senha))
                }
                post("/oauth/exchange") {
                    val corpo = call.receive<CorpoCodigoOAuth>()
                    val sessao = identidade.trocarCodigoOAuth(corpo.codigo)
                    if (sessao == null) {
                        call.respond(HttpStatusCode.Unauthorized, mapOf("erro" to "código de login inválido ou expirado"))
                    } else {
                        call.respond(sessao)
                    }
                }
            }

            rateLimit(RateLimitName("sensivel")) {
                get("/social/{provedor}") {
                    val provedor = call.parameters["provedor"] ?: throw IllegalArgumentException("provedor inválido")
                    if (provedor !in ServicoOAuth.PROVEDORES) throw IllegalArgumentException("provedor inválido")
                    if (!ProvedorSocialHttp().habilitado(provedor)) {
                        call.respond(HttpStatusCode.ServiceUnavailable, mapOf("erro" to "provedor social não configurado"))
                    } else {
                        call.respondRedirect(oauth.urlInicio(provedor), permanent = false)
                    }
                }
            }

            get("/social/{provedor}/callback") {
                val provedor = call.parameters["provedor"] ?: throw IllegalArgumentException("provedor inválido")
                val state = call.request.queryParameters["state"]
                    ?: throw IllegalArgumentException("resposta OAuth inválida")
                if (call.request.queryParameters["error"] != null) {
                    oauth.cancelar(provedor, state)
                    call.respondRedirect("$urlPublica/?auth_error=provider", permanent = false)
                    return@get
                }
                val callbackParameters = call.request.queryParameters.entries().associate { (key, values) ->
                    key to values.first()
                }
                val code = call.request.queryParameters["code"]
                val codigo = oauth.finalizar(provedor, state, code, callbackParameters)
                val encoded = URLEncoder.encode(codigo, StandardCharsets.UTF_8)
                call.respondRedirect("$urlPublica/?oauth_code=$encoded", permanent = false)
            }

            authenticate("jogador") {
                get("/me") {
                    call.respond(identidade.dadosConta(call.sessaoId()))
                }
                post("/logout") {
                    identidade.encerrarSessao(call.sessaoId())
                    call.respond(HttpStatusCode.NoContent)
                }
                post("/characters") {
                    val corpo = call.receive<CorpoCriarPersonagem>()
                    val nome = Validacao.nomePersonagem(corpo.nome)
                    call.respond(
                        HttpStatusCode.Created,
                        identidade.criarPersonagem(call.sessaoId(), nome, corpo.classeId),
                    )
                }
                post("/characters/select") {
                    val corpo = call.receive<CorpoSelecionarPersonagem>()
                    if (!identidade.selecionarPersonagem(call.sessaoId(), corpo.personagemId)) {
                        throw PersonagemNaoEncontrado()
                    }
                    movimento.posicionar(corpo.personagemId, Posicao(20.0, 15.0), System.currentTimeMillis())
                    call.respond(identidade.dadosConta(call.sessaoId()))
                }
            }
        }

        get("/api/catalogo") {
            val agora = relogio.instant()
            val itens = lerCatalogo().vitrine(agora).map {
                ItemPublico(it.id, it.nome, it.descricao, it.tipo.name.lowercase(), it.preco.centavos)
            }
            call.respond(itens)
        }

        authenticate("jogador") {
            post("/api/multiplayer/ticket") {
                call.respond(mapOf("ticket" to multiplayer.criarTicket(call.sessaoId())))
            }

            get("/api/jogabilidade/bosses-mundi") {
                call.respond(bossesMundi.estados())
            }
            post("/api/jogabilidade/bosses-mundi/atacar") {
                val corpo = call.receive<CorpoAtaqueBossMundi>()
                val personagem = repositorioIdentidade.personagemAtivoDados(call.sessaoId())
                    ?: throw SessaoNaoEncontrada()
                call.respond(bossesMundi.atacar(personagem, corpo.bossId))
            }
            get("/api/jogabilidade/estado") {
                val personagem = repositorioIdentidade.personagemAtivoDados(call.sessaoId())
                    ?: throw SessaoNaoEncontrada()
                call.respond(combate.estado(personagem.id))
            }
            post("/api/jogabilidade/atacar") {
                val corpo = call.receive<CorpoAtaqueMonstro>()
                val personagem = repositorioIdentidade.personagemAtivoDados(call.sessaoId())
                    ?: throw SessaoNaoEncontrada()
                call.respond(combate.atacar(personagem, corpo.monstroId))
            }
            post("/api/jogabilidade/equipar") {
                val corpo = call.receive<CorpoItemJogo>()
                val personagem = repositorioIdentidade.personagemAtivoDados(call.sessaoId())
                    ?: throw SessaoNaoEncontrada()
                call.respond(combate.equipar(personagem.id, corpo.itemId))
            }
            post("/api/jogabilidade/vender") {
                val corpo = call.receive<CorpoVendaItem>()
                val personagem = repositorioIdentidade.personagemAtivoDados(call.sessaoId())
                    ?: throw SessaoNaoEncontrada()
                call.respond(combate.vender(call.sessaoId(), personagem, corpo.itemId, corpo.quantidade))
            }
            post("/api/jogabilidade/missoes/aceitar") {
                val corpo = call.receive<CorpoMissao>()
                val personagem = repositorioIdentidade.personagemAtivoDados(call.sessaoId())
                    ?: throw SessaoNaoEncontrada()
                call.respond(combate.aceitarMissao(personagem, corpo.questId))
            }
            post("/api/jogabilidade/missoes/entregar") {
                val corpo = call.receive<CorpoMissao>()
                val personagem = repositorioIdentidade.personagemAtivoDados(call.sessaoId())
                    ?: throw SessaoNaoEncontrada()
                call.respond(combate.entregarMissao(call.sessaoId(), personagem, corpo.questId))
            }
            post("/api/jogabilidade/mercado-negro/trocar") {
                val corpo = call.receive<CorpoItemJogo>()
                val personagem = repositorioIdentidade.personagemAtivoDados(call.sessaoId())
                    ?: throw SessaoNaoEncontrada()
                call.respond(combate.trocarMercadoNegro(personagem.id, corpo.itemId))
            }
            post("/api/jogabilidade/equipamentos/aprimorar") {
                val corpo = call.receive<CorpoItemJogo>()
                val personagem = repositorioIdentidade.personagemAtivoDados(call.sessaoId())
                    ?: throw SessaoNaoEncontrada()
                call.respond(combate.melhorarEquipamento(personagem.id, corpo.itemId))
            }
            get("/api/jogabilidade/clans/meu") {
                val personagem = repositorioIdentidade.personagemAtivoDados(call.sessaoId())
                    ?: throw SessaoNaoEncontrada()
                val clan = repositorioProgresso.clan(personagem.id)
                val membros = repositorioProgresso.membrosClan(personagem.id).mapNotNull { membroId ->
                    repositorioIdentidade.personagemPorId(membroId)?.let { MembroClanJogo(it.id, it.nome) }
                }
                call.respond(EstadoClanJogo(clan, membros))
            }
            post("/api/jogabilidade/clans/criar") {
                val corpo = call.receive<CorpoCriarClan>()
                val personagem = repositorioIdentidade.personagemAtivoDados(call.sessaoId())
                    ?: throw SessaoNaoEncontrada()
                call.respond(
                    HttpStatusCode.Created,
                    repositorioProgresso.criarClan(corpo.nome, personagem.id),
                )
            }
            post("/api/jogabilidade/clans/entrar") {
                val corpo = call.receive<CorpoConviteClan>()
                val personagem = repositorioIdentidade.personagemAtivoDados(call.sessaoId())
                    ?: throw SessaoNaoEncontrada()
                val clan = repositorioProgresso.juntarClan(corpo.codigoConvite, personagem.id)
                if (clan == null) {
                    call.respond(HttpStatusCode.Conflict, mapOf("erro" to "convite inválido ou personagem já pertence a um clã"))
                } else {
                    call.respond(clan)
                }
            }
            post("/api/jogabilidade/raid/iniciar") {
                val corpo = call.receive<CorpoIniciarRaid>()
                val personagem = repositorioIdentidade.personagemAtivoDados(call.sessaoId())
                    ?: throw SessaoNaoEncontrada()
                val raid = repositorioProgresso.iniciarRaid(
                    personagem.id,
                    corpo.membros,
                    "trono_abismo",
                    "insignia_cla_ss",
                )
                if (raid == null) {
                    call.respond(
                        HttpStatusCode.Conflict,
                        mapOf("erro" to "raid exige dez membros do mesmo clã e uma insígnia de acesso SS"),
                    )
                } else {
                    call.respond(HttpStatusCode.Created, raid)
                }
            }
            get("/api/jogabilidade/raid") {
                val personagem = repositorioIdentidade.personagemAtivoDados(call.sessaoId())
                    ?: throw SessaoNaoEncontrada()
                call.respond(EstadoRaidJogo(repositorioProgresso.raidAtiva(personagem.id)))
            }
            post("/api/jogabilidade/raid/atacar-chefe") {
                val personagem = repositorioIdentidade.personagemAtivoDados(call.sessaoId())
                    ?: throw SessaoNaoEncontrada()
                val raid = repositorioProgresso.raidAtiva(personagem.id)
                if (raid == null || personagem.zonaAtualId != raid.zonaId) {
                    call.respond(HttpStatusCode.Conflict, mapOf("erro" to "entre na instância ativa para atacar o chefe"))
                } else {
                    call.respond(repositorioProgresso.atacarChefe(personagem.id)!!)
                }
            }
            post("/api/jogabilidade/raid/sair") {
                val personagem = repositorioIdentidade.personagemAtivoDados(call.sessaoId())
                    ?: throw SessaoNaoEncontrada()
                val raid = repositorioProgresso.abandonarRaid(personagem.id)
                if (raid == null) {
                    call.respond(HttpStatusCode.Conflict, mapOf("erro" to "não há raid ativa para abandonar"))
                } else {
                    raid.membros.forEach { membroId ->
                        repositorioIdentidade.retornarAoRespawn(membroId)?.let { membro ->
                            movimento.posicionar(membro.id, Posicao(20.0, 15.0), System.currentTimeMillis())
                            movimento.posicaoAtual(membro.id)?.let { multiplayer.atualizar(membro, it) }
                        }
                    }
                    call.respond(mapOf("status" to "aborted", "motivo" to "um membro abandonou a instância"))
                }
            }
            post("/api/jogabilidade/pk/atacar") {
                val corpo = call.receive<CorpoAtacarJogador>()
                val atacante = repositorioIdentidade.personagemAtivoDados(call.sessaoId())
                    ?: throw SessaoNaoEncontrada()
                val resultado = pvp.atacar(atacante, corpo.personagemId)
                if (resultado.derrotado) {
                    repositorioIdentidade.personagemPorId(corpo.personagemId)?.let { respawn ->
                        movimento.posicaoAtual(respawn.id)?.let { multiplayer.atualizar(respawn, it) }
                    }
                }
                call.respond(resultado)
            }
            post("/api/jogabilidade/pk/sacar") {
                val corpo = call.receive<CorpoSaquePvP>()
                val personagem = repositorioIdentidade.personagemAtivoDados(call.sessaoId())
                    ?: throw SessaoNaoEncontrada()
                call.respond(mapOf("itemId" to pvp.saquear(personagem.id, corpo.saqueId)))
            }

            rateLimit(RateLimitName("sensivel")) {
                post("/api/checkout") {
                    val corpo = call.receive<CorpoCheckout>()
                    val url = checkout.iniciar(call.jogadorId(), Validacao.idItemSeguro(corpo.itemId))
                    call.respond(mapOf("url_pagamento" to url))
                }
            }

            rateLimit(RateLimitName("jogo")) {
                post("/api/acao/mover") {
                    val corpo = call.receive<CorpoMovimento>()
                    val personagemAtivo = repositorioIdentidade.personagemAtivoDados(call.sessaoId())
                        ?: throw SessaoNaoEncontrada()
                    if (personagemAtivo.zonaNatalId == null) throw NascimentoPendente()
                    val destino = Posicao(Validacao.coordenada(corpo.x), Validacao.coordenada(corpo.y))
                    when (movimento.validarMovimento(personagemAtivo.id, destino, corpo.timestampMs)) {
                        is Veredito.Aceita -> {
                            val atualizado = repositorioIdentidade.personagemAtivoDados(call.sessaoId())
                            call.respond(mapOf("ok" to true))
                            atualizado?.let { multiplayer.atualizar(it, destino) }
                        }
                        // Resposta neutra: não revela qual regra disparou.
                        is Veredito.Rejeitada -> call.respond(HttpStatusCode.Conflict, mapOf("ok" to false))
                    }
                }
            }

            rateLimit(RateLimitName("jogo")) {
                post("/api/mundo/nascimento") {
                    val corpo = call.receive<CorpoNascimento>()
                    val estado = mundo.definirNascimento(call.sessaoId(), corpo.continenteId, corpo.zonaId)
                    call.respond(mundo.destinos(call.sessaoId()))
                    movimento.posicaoAtual(estado.id)?.let { multiplayer.atualizar(estado, it) }
                }
                post("/api/mundo/respawn") {
                    val personagem = repositorioIdentidade.personagemAtivoDados(call.sessaoId())
                        ?: throw SessaoNaoEncontrada()
                    val estado = if (repositorioProgresso.raidAtiva(personagem.id) != null) {
                        mundo.destinos(call.sessaoId())
                    } else {
                        mundo.respawn(call.sessaoId())
                    }
                    call.respond(estado)
                    movimento.posicaoAtual(estado.personagem.id)?.let {
                        multiplayer.atualizar(estado.personagem, it)
                    }
                }
                get("/api/mundo/estado") {
                    call.respond(mundo.destinos(call.sessaoId()))
                }
                post("/api/mundo/salvar") {
                    call.respond(mundo.salvarPonto(call.sessaoId()))
                }
                post("/api/mundo/teleporte") {
                    val corpo = call.receive<CorpoTeleporte>()
                    val estado = mundo.teletransportar(call.sessaoId(), corpo.destinoZonaId)
                    repositorioProgresso.raidAtiva(estado.personagem.id)?.let { raid ->
                        repositorioProgresso.atualizarNivelChefeAoEntrar(raid.id, estado.personagem.id)
                    }
                    call.respond(estado)
                    movimento.posicaoAtual(estado.personagem.id)?.let {
                        multiplayer.atualizar(estado.personagem, it)
                    }
                }
            }
        }

        post("/webhook/mercadopago") {
            checkout.processarNotificacao(call.receiveText())
            // Sempre 200 para evitar reenvios desnecessários. O resultado fica no pedido e no log.
            call.respond(HttpStatusCode.OK)
        }

        if (diretorioCliente != null) {
            staticFiles("/", diretorioCliente)
        }
    }
}
