package jogo.identidade

import jogo.identidade.aplicacao.ServicoIdentidade
import jogo.identidade.dominio.CredenciaisInvalidas
import jogo.identidade.dominio.EmailJaCadastrado
import jogo.identidade.dominio.EstadoOAuth
import jogo.identidade.dominio.IdentidadeSocial
import jogo.identidade.dominio.LimitePersonagensAtingido
import jogo.identidade.infraestrutura.RepositorioIdentidadeSqlite
import jogo.identidade.infraestrutura.ProvedorSocialHttp
import java.nio.file.Files
import java.time.Instant
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.assertFailsWith

class ServicoIdentidadeTest {
    private val repositorio = RepositorioIdentidadeSqlite(":memory:")
    private val servico = ServicoIdentidade(
        repositorio,
        "segredo-de-teste-com-mais-de-trinta-e-dois-bytes",
        setOf("espadachim", "mago"),
    )

    @AfterTest
    fun fecharRepositorio() {
        repositorio.close()
    }

    @Test
    fun `conta com senha cria sessao que pode ser revogada`() {
        val cadastro = servico.cadastrar("Jogador@exemplo.com", "uma senha segura 123")
        assertEquals("jogador@exemplo.com", cadastro.conta.email)
        assertTrue(servico.sessaoValida(cadastro.acessoId()))

        val login = servico.autenticar("JOGADOR@EXEMPLO.COM", "uma senha segura 123")
        assertNotEquals(cadastro.acesso, login.acesso)
        assertFalse(servico.sessaoValida(cadastro.acessoId()))
        assertTrue(servico.sessaoValida(login.acessoId()))

        servico.encerrarSessao(login.acessoId())
        assertFalse(servico.sessaoValida(login.acessoId()))
    }

    @Test
    fun `email duplicado e credenciais invalidas sao recusados`() {
        servico.cadastrar("jogador@exemplo.com", "uma senha segura 123")
        assertFailsWith<EmailJaCadastrado> {
            servico.cadastrar("JOGADOR@EXEMPLO.COM", "outra senha segura 123")
        }
        assertFailsWith<CredenciaisInvalidas> {
            servico.autenticar("jogador@exemplo.com", "senha errada 123")
        }
        assertFailsWith<CredenciaisInvalidas> {
            servico.autenticar("invalido", "senha errada 123")
        }
    }

    @Test
    fun `conta suporta cinco personagens e seleciona apenas um`() {
        val sessao = servico.cadastrar("jogador@exemplo.com", "uma senha segura 123")
        val personagens = (1..5).map {
            servico.criarPersonagem(sessao.acessoId(), "Heroi$it", "espadachim")
        }
        assertFailsWith<LimitePersonagensAtingido> {
            servico.criarPersonagem(sessao.acessoId(), "Heroi6", "mago")
        }

        assertTrue(servico.selecionarPersonagem(sessao.acessoId(), personagens[2].id))
        val conta = servico.dadosConta(sessao.acessoId())
        assertEquals(5, conta.personagens.size)
        assertEquals(personagens[2].id, conta.personagemAtivoId)
        assertFalse(servico.selecionarPersonagem(sessao.acessoId(), "personagem-de-outra-conta"))
    }

    @Test
    fun `login social reutiliza identidade e codigo OAuth so pode ser trocado uma vez`() {
        val social = IdentidadeSocial("discord", "discord-user-1", "social@example.com", true, "Jogador Social")
        val (_, primeiraSessao) = servico.autenticarSocial(social)
        val codigo = servico.criarCodigoOAuth(primeiraSessao)
        assertNotNull(servico.trocarCodigoOAuth(codigo))
        assertNull(servico.trocarCodigoOAuth(codigo))

        val (mesmaConta, segundaSessao) = servico.autenticarSocial(social)
        assertNotEquals(primeiraSessao, segundaSessao)
        assertTrue(servico.sessaoValida(segundaSessao))
        assertFalse(servico.sessaoValida(primeiraSessao))
        assertEquals("Jogador Social", mesmaConta.nomeExibicao)
    }

    @Test
    fun `codigo OAuth invalido nao revela sessao`() {
        assertNull(servico.trocarCodigoOAuth("codigo-invalido"))
    }

    @Test
    fun `conta e personagens persistem apos reabrir o SQLite`() {
        val arquivo = Files.createTempFile("jogo-identidade-", ".sqlite")
        Files.deleteIfExists(arquivo)
        try {
            val primeiroRepositorio = RepositorioIdentidadeSqlite(arquivo.toString())
            val primeiraSessao = ServicoIdentidade(
                primeiroRepositorio,
                "segredo-de-teste-com-mais-de-trinta-e-dois-bytes",
                setOf("espadachim"),
            ).cadastrar("persistente@example.com", "uma senha segura 123")
            val personagem = primeiroRepositorio.criarPersonagem(
                primeiraSessao.conta.id,
                "Persistente",
                "espadachim",
            )
            primeiroRepositorio.close()

            val segundoRepositorio = RepositorioIdentidadeSqlite(arquivo.toString())
            try {
                val segundaSessao = ServicoIdentidade(
                    segundoRepositorio,
                    "segredo-de-teste-com-mais-de-trinta-e-dois-bytes",
                    setOf("espadachim"),
                ).autenticar("persistente@example.com", "uma senha segura 123")
                assertEquals(personagem.id, segundoRepositorio.personagens(segundaSessao.conta.id).single().id)
            } finally {
                segundoRepositorio.close()
            }
        } finally {
            Files.deleteIfExists(arquivo)
        }
    }

    @Test
    fun `estado OAuth expira e so pode ser consumido uma vez`() {
        val agora = Instant.parse("2026-10-18T12:00:00Z")
        repositorio.salvarEstadoOAuth("state-1", EstadoOAuth("google", "verifier", "https://jogo.test/callback", agora.plusSeconds(30)))
        assertEquals("google", repositorio.consumirEstadoOAuth("state-1", agora)?.provedor)
        assertNull(repositorio.consumirEstadoOAuth("state-1", agora))

        repositorio.salvarEstadoOAuth("state-2", EstadoOAuth("discord", "verifier", "https://jogo.test/callback", agora.minusSeconds(1)))
        assertNull(repositorio.consumirEstadoOAuth("state-2", agora))
    }

    @Test
    fun `provedor social cria URL OAuth com state e PKCE`() {
        val provedor = ProvedorSocialHttp(
            ambiente = { chave ->
                when (chave) {
                    "GOOGLE_CLIENT_ID" -> "client-id"
                    "GOOGLE_CLIENT_SECRET" -> "client-secret"
                    else -> null
                }
            },
        )
        val url = provedor.urlAutorizacao(
            "google",
            "state-123",
            "verifier-seguro",
            "https://jogo.test/api/auth/social/google/callback",
            "https://jogo.test",
        )
        assertTrue(url.startsWith("https://accounts.google.com/o/oauth2/v2/auth?"))
        assertTrue(url.contains("state=state-123"))
        assertTrue(url.contains("code_challenge_method=S256"))
        assertTrue(url.contains("redirect_uri=https%3A%2F%2Fjogo.test"))
    }

    private fun jogo.identidade.dominio.SessaoPublica.acessoId(): String =
        com.auth0.jwt.JWT.decode(acesso).id
}
