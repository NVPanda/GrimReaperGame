package jogo.conteudo

import kotlinx.serialization.json.*
import java.io.File
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Testes de integridade do conteúdo (dados/ e assets/).
 * Rodam a partir do diretório backend/, por isso os caminhos sobem um nível.
 * Cenários em docs/sdd/06-conteudo.md.
 */
class ConteudoTest {

    private val raiz = File("..").canonicalFile
    private fun json(nome: String): JsonElement =
        Json.parseToJsonElement(File(raiz, "dados/$nome").readText(Charsets.UTF_8))

    private fun JsonElement.lista() = jsonArray
    private fun JsonElement.texto(chave: String) = jsonObject[chave]!!.jsonPrimitive.content
    private fun JsonElement.inteiro(chave: String) = jsonObject[chave]!!.jsonPrimitive.int

    private val continentes = json("continentes.json").lista()
    private val classes = json("classes.json").lista()
    private val monstros = json("monstros.json").lista()
    private val itens = json("itens.json").lista()
    private val mapas = json("mapas.json").lista()
    private val npcs = json("npcs.json").lista()
    private val quests = json("quests.json").lista()

    private val idsItens = itens.map { it.texto("id") }.toSet()
    private val idsMonstros = monstros.map { it.texto("id") }.toSet()
    private val idsNpcs = npcs.map { it.texto("id") }.toSet()
    private val idsZonas = mapas.map { it.texto("id") }.toSet()

    @Test
    fun `CONT-01 existem seis continentes com ids unicos`() {
        val ids = continentes.map { it.texto("id") }
        assertEquals(6, ids.size)
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test
    fun `CONT-02 cada classe tem tres habilidades`() {
        classes.forEach { c -> assertEquals(3, c.jsonObject["habilidades"]!!.jsonArray.size, c.texto("id")) }
    }

    @Test
    fun `CONT-02 atributos base de toda classe somam 60`() {
        classes.forEach { c ->
            val soma = c.jsonObject["base"]!!.jsonObject.values.sumOf { it.jsonPrimitive.int }
            assertEquals(60, soma, "soma de ${c.texto("id")}")
        }
    }

    @Test
    fun `CONT-02 crescimento de toda classe soma 1,0`() {
        classes.forEach { c ->
            val soma = c.jsonObject["crescimento"]!!.jsonObject.values.sumOf { it.jsonPrimitive.double }
            assertTrue(abs(soma - 1.0) < 1e-9, "crescimento de ${c.texto("id")} soma $soma")
        }
    }

    @Test
    fun `CONT-03 todo item de drop existe em itens`() {
        monstros.forEach { m ->
            m.jsonObject["drops"]!!.jsonArray.forEach { d ->
                val item = d.jsonObject["item"]!!.jsonPrimitive.content
                assertTrue(item in idsItens, "${m.texto("id")} dropa item inexistente: $item")
            }
        }
    }

    @Test
    fun `CONT-03 todo alvo de quest de coleta existe em itens`() {
        quests.filter { it.texto("tipo") in setOf("plantio", "pesca", "mineracao") }.forEach { q ->
            assertTrue(q.texto("alvo") in idsItens, "quest ${q.texto("id")} aponta para item inexistente")
        }
    }

    @Test
    fun `CONT-04 toda zona so lista monstros existentes`() {
        mapas.forEach { z ->
            z.jsonObject["monstros"]!!.jsonArray.forEach { m ->
                assertTrue(m.jsonPrimitive.content in idsMonstros, "zona ${z.texto("id")} tem monstro inexistente")
            }
        }
    }

    @Test
    fun `CONT-04 todo campo tem pelo menos um monstro comum`() {
        val bosses = monstros.filter { it.jsonObject["boss"]!!.jsonPrimitive.boolean }.map { it.texto("id") }.toSet()
        mapas.filter { it.texto("tipo") == "campo" }.forEach { z ->
            val comuns = z.jsonObject["monstros"]!!.jsonArray.map { it.jsonPrimitive.content }.filter { it !in bosses }
            assertTrue(comuns.isNotEmpty(), "campo ${z.texto("id")} sem monstro comum")
        }
    }

    @Test
    fun `CONT-05 toda quest aponta para NPC e zona existentes`() {
        quests.forEach { q ->
            assertTrue(q.texto("npc") in idsNpcs, "quest ${q.texto("id")} com NPC inexistente")
            assertTrue(q.texto("zona") in idsZonas, "quest ${q.texto("id")} com zona inexistente")
        }
    }

    @Test
    fun `CONT-06 item SS so cai de boss`() {
        val comSS = monstros.filter { m ->
            m.jsonObject["drops"]!!.jsonArray.any { d ->
                d.jsonObject["item"]!!.jsonPrimitive.content.endsWith("_ss")
            }
        }
        comSS.forEach { m ->
            assertTrue(m.jsonObject["boss"]!!.jsonPrimitive.boolean, "${m.texto("id")} dropa SS sem ser boss")
        }
    }

    @Test
    fun `CONT-07 todo monstro e item tem sprite no manifest e arquivo existente`() {
        val manifesto = Json.parseToJsonElement(File(raiz, "assets/manifest.json").readText()).jsonObject
        monstros.forEach { m ->
            val entrada = manifesto["monstros"]!!.jsonObject[m.texto("id")]
            assertTrue(entrada != null, "monstro sem manifest: ${m.texto("id")}")
            assertTrue(File(raiz, entrada!!.jsonObject["arquivo"]!!.jsonPrimitive.content).exists())
        }
        itens.forEach { i ->
            val entrada = manifesto["itens"]!!.jsonObject[i.texto("id")]
            assertTrue(entrada != null, "item sem manifest: ${i.texto("id")}")
            assertTrue(File(raiz, entrada!!.jsonObject["arquivo"]!!.jsonPrimitive.content).exists())
        }
    }

    @Test
    fun `CONT-08 cada continente tem cidade, campo e masmorra com um boss`() {
        val idsChefes = monstros.filter { it.jsonObject["boss"]!!.jsonPrimitive.boolean }
            .map { it.texto("id") }.toSet()
        continentes.forEach { c ->
            val id = c.texto("id")
            val doContinente = mapas.filter { it.texto("cont") == id }
            assertEquals(1, doContinente.count { it.texto("tipo") == "cidade" }, "cidade em $id")
            assertTrue(doContinente.any { it.texto("tipo") == "campo" }, "campo em $id")
            val masmorras = doContinente.filter { it.texto("tipo") == "masmorra" }
            assertEquals(1, masmorras.size, "masmorra em $id")
            val chefesNaMasmorra = masmorras.single().jsonObject["monstros"]!!.jsonArray
                .count { it.jsonPrimitive.content in idsChefes }
            assertEquals(1, chefesNaMasmorra, "chefe na masmorra em $id")
        }
    }

    @Test
    fun `CONT-09 regras de PK e andares das masmorras sao expansivas e validas`() {
        val regras = json("jogabilidade.json").jsonObject
        val categorias = regras["loot"]!!.jsonObject["categorias"]!!.jsonArray
            .map { it.jsonPrimitive.content }
        assertEquals(
            setOf("basicos", "intermediarios", "ranqueados", "ultra_raros", "ranqueados_ultra_raros"),
            categorias.toSet(),
        )
        val masmorrasRegras = regras["masmorras"]!!.jsonArray
        assertEquals(6, masmorrasRegras.size)
        masmorrasRegras.forEach { masmorra ->
            val id = masmorra.texto("id")
            assertTrue(id in idsZonas)
            assertEquals("masmorra", mapas.single { it.texto("id") == id }.texto("tipo"))
            val pk = masmorra.jsonObject["circulo_pk"]!!.jsonObject
            assertTrue(pk["raio"]!!.jsonPrimitive.double > 0)
            val andares = masmorra.jsonObject["andares"]!!.jsonArray
            if (id == "trono_abismo") {
                assertTrue(andares.isEmpty(), "raid SS não pode dropar a própria chave de acesso")
            } else {
                assertEquals(listOf(10, 20, 30, 40, 50), andares.map { it.inteiro("nivel") })
            }
            andares.forEach { andar ->
                assertTrue(andar.texto("miniboss_id") in idsMonstros)
                assertTrue(andar.jsonObject["chave_drop_pct"]!!.jsonPrimitive.double > 0)
            }
        }
        val assalto = regras["raid_ss"]!!.jsonObject
        assertEquals("trono_abismo", assalto["zona_id"]!!.jsonPrimitive.content)
        assertEquals(10, assalto["membros_obrigatorios"]!!.jsonPrimitive.int)
        assertEquals(10, assalto["bonus_nivel_chefe"]!!.jsonPrimitive.int)
    }

    @Test
    fun `CONT-10 tarifas de teleporte incluem destino SS e valores nao negativos`() {
        val tarifas = json("economia.json").jsonObject["tarifas_teleporte_mc"]!!.jsonObject
        assertEquals("trono_abismo", tarifas["zona_masmorra_ss"]!!.jsonPrimitive.content)
        listOf(
            "cidade_mesmo_continente",
            "cidade_intercontinental",
            "masmorra",
            "masmorra_ss",
        ).forEach { chave ->
            assertTrue(tarifas[chave]!!.jsonPrimitive.long >= 0, "tarifa $chave inválida")
        }
    }

    @Test
    fun `CONT-11 seis continentes possuem miniboss regional e sprite original`() {
        val config = json("jogabilidade.json").jsonObject["bosses_mundi"]!!.jsonObject
        assertEquals(3_600, config["intervalo_spawn_segundos"]!!.jsonPrimitive.long)
        assertEquals(18_000, config["retorno_apos_derrota_segundos"]!!.jsonPrimitive.long)
        val regioes = config["regioes"]!!.jsonArray
        assertEquals(continentes.size, regioes.size)
        assertEquals(regioes.size, regioes.map { it.texto("id") }.toSet().size)
        assertEquals(regioes.size, regioes.map { it.texto("zona_id") }.toSet().size)
        val sprites = Json.parseToJsonElement(File(raiz, "assets/manifest.json").readText())
            .jsonObject["monstros"]!!.jsonObject
        regioes.forEach { boss ->
            val zona = mapas.single { it.texto("id") == boss.texto("zona_id") }
            assertEquals("campo", zona.texto("tipo"))
            val sprite = sprites[boss.texto("id")]
            assertTrue(sprite != null, "miniboss sem sprite: ${boss.texto("id")}")
            assertTrue(File(raiz, sprite!!.jsonObject["arquivo"]!!.jsonPrimitive.content).exists())
        }
        assertTrue(itens.any { it.texto("id") == "placa_cacada_mundi" })
    }
}
