package jogo.jogabilidade

import jogo.anticheat.dominio.Posicao
import jogo.anticheat.dominio.ValidadorMovimento
import jogo.identidade.dominio.Personagem
import jogo.jogabilidade.aplicacao.ServicoBossMundi
import jogo.jogabilidade.infraestrutura.ProgressoPersonagem
import jogo.jogabilidade.infraestrutura.RepositorioProgressoSqlite
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ServicoBossMundiTest {
    private val conteudo = File("..").canonicalFile

    @Test
    fun `JOGO-05 miniboss mundial reaparece por hora e espera cinco horas depois da derrota`() {
        RepositorioProgressoSqlite(":memory:").use { repositorio ->
            val id = "cacador"
            val personagem = Personagem(
                id = id,
                nome = "Caçador",
                classeId = "espadachim",
                zonaAtualId = "vale_campos",
            )
            val zonaErrada = personagem.copy(zonaAtualId = "vila_central")
            val movimento = ValidadorMovimento().also {
                it.posicionar(id, Posicao(13.0, 13.0), 1_000)
            }
            repositorio.criarClan("Vigias", id)
            repositorio.salvar(ProgressoPersonagem(id, nivel = 50))
            var agora = 3_599L
            val servico = ServicoBossMundi(
                repositorio,
                movimento,
                File(conteudo, "dados/jogabilidade.json").readText(Charsets.UTF_8),
            ) { agora }

            val antesDoSpawn = servico.estados().single { it.id == "guardiao_raizes" }
            assertEquals(0, antesDoSpawn.vidaAtual)
            assertEquals(3_600L, antesDoSpawn.proximaAparicaoEpoch)
            assertFailsWith<IllegalArgumentException> { servico.atacar(zonaErrada, "guardiao_raizes") }
            assertFailsWith<IllegalArgumentException> { servico.atacar(personagem, "guardiao_raizes") }

            agora = 3_600
            movimento.posicionar(id, Posicao(0.0, 0.0), 2_000)
            assertFailsWith<IllegalArgumentException> { servico.atacar(personagem, "guardiao_raizes") }
            movimento.posicionar(id, Posicao(13.0, 13.0), 3_000)
            var ataque = servico.atacar(personagem, "guardiao_raizes")
            while (!ataque.derrotado) ataque = servico.atacar(personagem, "guardiao_raizes")

            assertEquals(0, ataque.boss.vidaAtual)
            assertEquals(21_600L, ataque.boss.proximaAparicaoEpoch)
            assertEquals("Caçador", ataque.boss.derrotadoPor)
            assertEquals("Vigias", ataque.boss.equipeDerrotou)
            assertEquals(1, repositorio.carregar(id).inventario["placa_cacada_mundi"])

            agora = 21_599
            val reiniciado = ServicoBossMundi(
                repositorio,
                movimento,
                File(conteudo, "dados/jogabilidade.json").readText(Charsets.UTF_8),
            ) { agora }
            assertEquals(0, reiniciado.estados().single { it.id == "guardiao_raizes" }.vidaAtual)
            agora = 21_600
            val reaparecido = servico.estados().single { it.id == "guardiao_raizes" }
            assertEquals(3_000, reaparecido.vidaAtual)
            assertEquals(null, reaparecido.proximaAparicaoEpoch)
        }
    }
}
