package jogo.mundo

import jogo.anticheat.dominio.Posicao
import jogo.anticheat.dominio.ValidadorMovimento
import jogo.identidade.aplicacao.ServicoIdentidade
import jogo.identidade.dominio.InteracaoNpcInvalida
import jogo.identidade.dominio.NascimentoJaDefinido
import jogo.identidade.dominio.SaldoInsuficiente
import jogo.identidade.infraestrutura.RepositorioIdentidadeSqlite
import jogo.mundo.aplicacao.ServicoMundo
import jogo.mundo.aplicacao.ZonaMundo
import java.nio.file.Files
import java.sql.DriverManager
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull

class ServicoMundoTest {
    private val arquivo = Files.createTempFile("jogo-mundo-", ".sqlite")
    private val repositorio = RepositorioIdentidadeSqlite(arquivo.toString())
    private val movimento = ValidadorMovimento()
    private val zonas = listOf(
        ZonaMundo("vila_central", "Vila Central", "humano", "cidade"),
        ZonaMundo("vale_campos", "Vale dos Campos", "humano", "campo"),
        ZonaMundo("clareira_sagrada", "Clareira Sagrada", "elfico", "cidade"),
        ZonaMundo("cripta_humana", "Cripta Humana", "humano", "masmorra"),
        ZonaMundo("trono_abismo", "Trono do Abismo", "demoniaco", "masmorra"),
    )
    private val servicoIdentidade = ServicoIdentidade(
        repositorio,
        "segredo-de-teste-com-mais-de-trinta-e-dois-bytes",
        setOf("espadachim"),
    )
    private val servicoMundo = ServicoMundo(repositorio, movimento, zonas)

    @AfterTest
    fun limpar() {
        repositorio.close()
        Files.deleteIfExists(arquivo)
    }

    @Test
    fun `nascimento e imutavel e define cidade inicial como primeiro save`() {
        val sessao = criarPersonagemSelecionado()
        val nascimento = servicoMundo.definirNascimento(sessao, "humano", "vila_central")

        assertEquals("humano", nascimento.continenteNatalId)
        assertEquals("vila_central", nascimento.zonaNatalId)
        assertEquals("vila_central", nascimento.zonaSalvaId)
        assertEquals(10, nascimento.saldoMc)
        assertFailsWith<NascimentoJaDefinido> {
            servicoMundo.definirNascimento(sessao, "elfico", "clareira_sagrada")
        }
    }

    @Test
    fun `save exige NPC de cidade e altera local de respawn`() {
        val sessao = criarPersonagemSelecionado()
        val personagem = servicoMundo.definirNascimento(sessao, "humano", "vila_central")
        movimento.posicionar(personagem.id, Posicao(20.0, 15.0), System.currentTimeMillis())
        assertFailsWith<InteracaoNpcInvalida> { servicoMundo.salvarPonto(sessao) }

        movimento.posicionar(personagem.id, ServicoMundo.NPC_SAVE, System.currentTimeMillis())
        val salvo = servicoMundo.salvarPonto(sessao)
        assertEquals("vila_central", salvo.zonaSalvaId)
    }

    @Test
    fun `teleporte usa tarifa, debita MC e respawn retorna ao ultimo save`() {
        val sessao = criarPersonagemSelecionado()
        val personagem = servicoMundo.definirNascimento(sessao, "humano", "vila_central")
        movimento.posicionar(personagem.id, ServicoMundo.NPC_TELEPORTE, System.currentTimeMillis())
        assertFailsWith<SaldoInsuficiente> {
            servicoMundo.teletransportar(sessao, "clareira_sagrada")
        }

        DriverManager.getConnection("jdbc:sqlite:${arquivo}").use { connection ->
            connection.createStatement().use { it.executeUpdate("UPDATE characters SET mc_balance = 1000") }
        }
        val viagem = servicoMundo.teletransportar(sessao, "clareira_sagrada")
        assertEquals(0, viagem.saldoMc)
        assertEquals("clareira_sagrada", viagem.zonaAtual.id)
        assertEquals(100_000, viagem.destinos.first { it.zonaId == "trono_abismo" }.tarifaMc)

        movimento.posicionar(personagem.id, ServicoMundo.NPC_SAVE, System.currentTimeMillis())
        assertEquals("clareira_sagrada", servicoMundo.salvarPonto(sessao).zonaSalvaId)
        val retorno = servicoMundo.respawn(sessao)
        assertEquals("clareira_sagrada", retorno.zonaAtual.id)
        assertNotNull(retorno.personagem.continenteNatalId)
        assertEquals("humano", retorno.personagem.continenteNatalId)
    }

    @Test
    fun `tarifas de dungeon comum e dungeon SS sao aplicadas`() {
        val sessao = criarPersonagemSelecionado()
        val personagem = servicoMundo.definirNascimento(sessao, "humano", "vila_central")
        movimento.posicionar(personagem.id, ServicoMundo.NPC_TELEPORTE, System.currentTimeMillis())
        val destinos = servicoMundo.destinos(sessao).destinos.associateBy { it.zonaId }

        assertEquals(100, destinos.getValue("vale_campos").tarifaMc)
        assertEquals(1_000, destinos.getValue("clareira_sagrada").tarifaMc)
        assertEquals(10_000, destinos.getValue("cripta_humana").tarifaMc)
        assertEquals(100_000, destinos.getValue("trono_abismo").tarifaMc)
    }

    @Test
    fun `portal em regiao permite retornar a cidade`() {
        val sessao = criarPersonagemSelecionado()
        val personagem = servicoMundo.definirNascimento(sessao, "humano", "vila_central")
        DriverManager.getConnection("jdbc:sqlite:$arquivo").use { connection ->
            connection.createStatement().use { it.executeUpdate("UPDATE characters SET mc_balance = 500") }
        }

        movimento.posicionar(personagem.id, ServicoMundo.NPC_TELEPORTE, System.currentTimeMillis())
        val ida = servicoMundo.teletransportar(sessao, "vale_campos")
        assertEquals("vale_campos", ida.zonaAtual.id)
        assertEquals(400, ida.saldoMc)

        movimento.posicionar(personagem.id, ServicoMundo.NPC_TELEPORTE, System.currentTimeMillis())
        val volta = servicoMundo.teletransportar(sessao, "vila_central")
        assertEquals("vila_central", volta.zonaAtual.id)
        assertEquals(300, volta.saldoMc)
    }

    @Test
    fun `migra personagens antigos sem apagar os dados existentes`() {
        val bancoAntigo = Files.createTempFile("jogo-mundo-legado-", ".sqlite")
        try {
            DriverManager.getConnection("jdbc:sqlite:$bancoAntigo").use { connection ->
                connection.createStatement().use { statement ->
                    statement.execute(
                        """
                        CREATE TABLE accounts (
                            id TEXT PRIMARY KEY, email TEXT UNIQUE, display_name TEXT NOT NULL,
                            password_hash BLOB, password_salt BLOB, created_at INTEGER NOT NULL
                        )
                        """.trimIndent(),
                    )
                    statement.execute(
                        """
                        CREATE TABLE characters (
                            id TEXT PRIMARY KEY, account_id TEXT NOT NULL, name TEXT NOT NULL,
                            class_id TEXT NOT NULL, created_at INTEGER NOT NULL, UNIQUE(account_id, name)
                        )
                        """.trimIndent(),
                    )
                    statement.execute(
                        "INSERT INTO accounts(id, email, display_name, created_at) VALUES ('conta-legada', 'legado@example.com', 'legado', 1)",
                    )
                    statement.execute(
                        "INSERT INTO characters(id, account_id, name, class_id, created_at) VALUES ('personagem-legado', 'conta-legada', 'Legado', 'espadachim', 1)",
                    )
                }
            }

            RepositorioIdentidadeSqlite(bancoAntigo.toString()).use { migrado ->
                val personagem = migrado.personagens("conta-legada").single()
                assertEquals("personagem-legado", personagem.id)
                assertEquals("Legado", personagem.nome)
                assertEquals(10, personagem.saldoMc)
                assertEquals(null, personagem.zonaNatalId)
            }
        } finally {
            Files.deleteIfExists(bancoAntigo)
        }
    }

    private fun criarPersonagemSelecionado(): String {
        val sessao = servicoIdentidade.cadastrar("jogador@exemplo.com", "uma senha segura 123")
        val personagem = servicoIdentidade.criarPersonagem(
            com.auth0.jwt.JWT.decode(sessao.acesso).id,
            "Heroi",
            "espadachim",
        )
        val sessaoId = com.auth0.jwt.JWT.decode(sessao.acesso).id
        servicoIdentidade.selecionarPersonagem(sessaoId, personagem.id)
        return sessaoId
    }
}
