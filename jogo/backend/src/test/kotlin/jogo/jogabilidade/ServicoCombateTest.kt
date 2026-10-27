package jogo.jogabilidade

import jogo.anticheat.dominio.Posicao
import jogo.anticheat.dominio.ValidadorMovimento
import jogo.identidade.aplicacao.ServicoIdentidade
import jogo.identidade.dominio.Personagem
import jogo.identidade.infraestrutura.RepositorioIdentidadeSqlite
import jogo.jogabilidade.aplicacao.ServicoCombate
import jogo.jogabilidade.infraestrutura.ProgressoPersonagem
import jogo.jogabilidade.infraestrutura.RepositorioProgressoSqlite
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.random.Random

class ServicoCombateTest {
    private val conteudo = File("..").canonicalFile
    private fun arquivo(nome: String) = File(conteudo, "dados/$nome").readText(Charsets.UTF_8)

    @Test
    fun `JOGO-01 combate verifica distancia e salva XP e drops no perfil`() {
        RepositorioProgressoSqlite(":memory:").use { repositorio ->
            val movimento = ValidadorMovimento()
            val personagem = Personagem(
                id = "player-a",
                nome = "Aventureiro",
                classeId = "espadachim",
                zonaAtualId = "vale_campos",
            )
            movimento.posicionar(personagem.id, Posicao(30.0, 29.0), 1_000)
            val servico = ServicoCombate(
                repositorio,
                movimento,
                arquivo("monstros.json"),
                arquivo("itens.json"),
                arquivo("mapas.json"),
                arquivo("quests.json"),
                arquivo("npcs.json"),
                arquivo("jogabilidade.json"),
            )

            assertFailsWith<IllegalArgumentException> { servico.atacar(personagem, "constructo_palha") }
            movimento.posicionar(personagem.id, Posicao(4.0, 4.0), 2_000)
            repositorio.salvar(ProgressoPersonagem(personagem.id, nivel = 50))
            val resultado = servico.atacar(personagem, "constructo_palha")

            assertEquals(true, resultado.derrotado)
            assertEquals(3, resultado.experienciaGanha)
            assertEquals(50, resultado.nivelAtual)
            assertEquals(3, servico.estado(personagem.id).experiencia)
        }
    }

    @Test
    fun `JOGO-04 insígnia SS só cai de miniboss quando o nível alcança o marco`() {
        RepositorioProgressoSqlite(":memory:").use { repositorio ->
            val personagem = Personagem(
                id = "explorador",
                nome = "Explorador",
                classeId = "espadachim",
                zonaAtualId = "cripta_humana",
            )
            val movimento = ValidadorMovimento().also {
                it.posicionar(personagem.id, Posicao(11.0, 15.0), 1_000)
            }
            val sempreCai = object : Random() {
                override fun nextBits(bitCount: Int): Int = 0
            }
            val progresso = ProgressoPersonagem(personagem.id, nivel = 9)
            repositorio.salvar(progresso)
            fun servico() = ServicoCombate(
                repositorio,
                movimento,
                arquivo("monstros.json"),
                arquivo("itens.json"),
                arquivo("mapas.json"),
                arquivo("quests.json"),
                arquivo("npcs.json"),
                arquivo("jogabilidade.json"),
                sorteio = sempreCai,
            )

            var combate = servico()
            var resultado = combate.atacar(personagem, "colosso_argila")
            while (!resultado.derrotado) resultado = combate.atacar(personagem, "colosso_argila")
            assertEquals(false, resultado.drops.any { it.itemId == "insignia_cla_ss" })

            repositorio.salvar(progresso.copy(nivel = 10))
            combate = servico()
            resultado = combate.atacar(personagem, "colosso_argila")
            while (!resultado.derrotado) resultado = combate.atacar(personagem, "colosso_argila")
            assertEquals(true, resultado.drops.any { it.itemId == "insignia_cla_ss" })

            combate = servico()
            resultado = combate.atacar(personagem, "colosso_argila")
            while (!resultado.derrotado) resultado = combate.atacar(personagem, "colosso_argila")
            assertEquals(true, resultado.drops.any { it.itemId == "insignia_cla_ss" })
        }
    }

    @Test
    fun `JOGO-02 assalto SS exige dez membros do mesmo clã e consome uma chave`() {
        RepositorioProgressoSqlite(":memory:").use { repositorio ->
            val ids = (1..10).map { "membro-$it" }
            val clan = repositorio.criarClan("Companhia", ids.first())
            ids.drop(1).forEach { assertNotNull(repositorio.juntarClan(clan.codigoConvite, it)) }
            ids.forEachIndexed { index, id ->
                val inventario = if (index == 0) mapOf("insignia_cla_ss" to 1) else emptyMap()
                repositorio.salvar(ProgressoPersonagem(id, nivel = 50, inventario = inventario))
            }

            val raid = repositorio.iniciarRaid(ids.first(), ids, "trono_abismo", "insignia_cla_ss")

            assertNotNull(raid)
            assertEquals(60, raid.nivelChefe)
            assertEquals("active", raid.status)
            assertEquals(0, repositorio.carregar(ids.first()).inventario["insignia_cla_ss"] ?: 0)
            assertEquals(10, raid.membros.size)
            assertEquals("aborted", repositorio.abandonarRaid(ids[4])?.status)
            assertEquals(0, repositorio.carregar(ids.first()).inventario["insignia_cla_ss"] ?: 0)
            val portador = repositorio.carregar(ids.first())
            repositorio.salvar(
                portador.copy(inventario = portador.inventario + ("insignia_cla_ss" to 1)),
            )
            val raidSeguinte = repositorio.iniciarRaid(ids.first(), ids, "trono_abismo", "insignia_cla_ss")
                ?: error("a segunda raid não iniciou")
            var chefe = raidSeguinte
            var proximoMembro = 0
            while (chefe.status == "active") {
                chefe = repositorio.atacarChefe(ids[proximoMembro % ids.size])
                    ?: error("assalto desapareceu")
                proximoMembro++
            }
            assertEquals("completed", chefe.status)
            assertEquals(1, repositorio.carregar(ids.last()).inventario["nucleo_ranqueado"])
            assertEquals(0, repositorio.carregar(ids.first()).inventario["insignia_cla_ss"] ?: 0)
        }
    }

    @Test
    fun `JOGO-03 saque PK é exclusivo e expira`() {
        RepositorioProgressoSqlite(":memory:").use { repositorio ->
            repositorio.salvar(
                ProgressoPersonagem(
                    personagemId = "vitima",
                    equipamentos = mapOf("arma" to "arma_f"),
                    vidaAtual = 25,
                ),
            )
            val morte = repositorio.aplicarDanoPvP(
                atacanteId = "atacante",
                vitimaId = "vitima",
                dano = 25,
                equipamentoDropado = "arma_f",
                expiraEmEpoch = 2_000,
            )

            assertEquals(true, morte.derrotado)
            assertEquals(100, repositorio.carregar("vitima").vidaAtual)
            assertEquals(emptyMap(), repositorio.carregar("vitima").equipamentos)
            assertEquals(null, repositorio.saquearPvP(morte.saqueId!!, "outro-jogador", 1_000))
            assertEquals("arma_f", repositorio.saquearPvP(morte.saqueId, "atacante", 1_000))
            assertEquals(1, repositorio.carregar("atacante").inventario["arma_f"])
            assertEquals(null, repositorio.saquearPvP(morte.saqueId, "atacante", 2_001))
        }
    }

    @Test
    fun `QUEST-01 entrega de missão exige proximidade e credita MC apenas uma vez`() {
        val banco = Files.createTempFile("jogo-missao-", ".sqlite")
        try {
            RepositorioIdentidadeSqlite(banco.toString()).use { identidade ->
                RepositorioProgressoSqlite(banco.toString()).use { progresso ->
                    val servicoIdentidade = ServicoIdentidade(
                        identidade,
                        "segredo-de-teste-com-mais-de-trinta-e-dois-bytes",
                        setOf("espadachim"),
                    )
                    val sessao = servicoIdentidade.cadastrar("missao@exemplo.com", "uma senha segura 123")
                    val personagemCriado = servicoIdentidade.criarPersonagem(
                        sessao.acessoId(),
                        "Heroi",
                        "espadachim",
                    )
                    servicoIdentidade.selecionarPersonagem(sessao.acessoId(), personagemCriado.id)
                    val personagem = identidade.personagemAtivoDados(sessao.acessoId())!!
                        .copy(zonaAtualId = "vila_central")
                    progresso.salvar(
                        ProgressoPersonagem(
                            personagemId = personagem.id,
                            missoesAtivas = mapOf("humano_caca_01" to 10),
                        ),
                    )
                    val movimento = ValidadorMovimento()
                    movimento.posicionar(personagem.id, Posicao(30.0, 25.0), 1_000)
                    val combate = ServicoCombate(
                        progresso,
                        movimento,
                        arquivo("monstros.json"),
                        arquivo("itens.json"),
                        arquivo("mapas.json"),
                        arquivo("quests.json"),
                        arquivo("npcs.json"),
                        arquivo("jogabilidade.json"),
                    )

                    assertFailsWith<IllegalArgumentException> {
                        combate.entregarMissao(sessao.acessoId(), personagem, "humano_caca_01")
                    }
                    assertEquals(10, identidade.personagemAtivoDados(sessao.acessoId())!!.saldoMc)

                    movimento.posicionar(personagem.id, Posicao(20.0, 15.0), 2_000)
                    val atualizado = combate.entregarMissao(
                        sessao.acessoId(),
                        personagem,
                        "humano_caca_01",
                    )

                    assertEquals(emptyMap(), atualizado.missoesAtivas)
                    assertEquals(60, atualizado.experiencia)
                    assertEquals(40, identidade.personagemAtivoDados(sessao.acessoId())!!.saldoMc)
                    assertFailsWith<IllegalStateException> {
                        combate.entregarMissao(sessao.acessoId(), personagem, "humano_caca_01")
                    }
                    assertEquals(40, identidade.personagemAtivoDados(sessao.acessoId())!!.saldoMc)
                }
            }
        } finally {
            Files.deleteIfExists(banco)
        }
    }

    private fun jogo.identidade.dominio.SessaoPublica.acessoId(): String =
        com.auth0.jwt.JWT.decode(acesso).id
}
