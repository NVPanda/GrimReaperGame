package jogo.loja.infraestrutura

import jogo.loja.dominio.StatusItem
import jogo.loja.dominio.TipoItem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class CatalogoJsonTest {

    private val exemplo = """
        {
          "itens": [
            {
              "id": "cosm_capa_aurora_01",
              "nome": "Capa Aurora",
              "tipo": "cosmetico",
              "descricao": "Capa visual.",
              "preco": { "valor": 19.90, "moeda": "BRL" },
              "status": "ativo",
              "disponivel_de": "2026-10-01T00:00:00-03:00",
              "disponivel_ate": null,
              "limite_por_conta": 1,
              "classes_permitidas": ["todas"],
              "efeito_em_combate": false
            }
          ]
        }
    """.trimIndent()

    @Test
    fun `LOJA-06 carrega itens com preco convertido para centavos`() {
        val catalogo = CatalogoJson.carregar(exemplo)
        val item = catalogo.porId("cosm_capa_aurora_01")!!
        assertEquals(1990L, item.preco.centavos)
        assertEquals(TipoItem.COSMETICO, item.tipo)
        assertEquals(StatusItem.ATIVO, item.status)
    }

    @Test
    fun `LOJA-04 item de melhoria com preco fixo e efeito em combate e aceito e mantem o limite diario`() {
        val melhoria = exemplo
            .replace("\"efeito_em_combate\": false", "\"efeito_em_combate\": true, \"limite_diario\": 20")
        val item = CatalogoJson.carregar(melhoria).porId("cosm_capa_aurora_01")!!
        assertEquals(true, item.efeitoEmCombate)
        assertEquals(20, item.limiteDiario)
    }

    @Test
    fun `LOJA-05 ids duplicados sao rejeitados ao carregar`() {
        val item = exemplo.substringAfter("\"itens\": [").substringBeforeLast("]").trim()
        val duplicado = """{ "itens": [ $item, $item ] }"""
        assertFailsWith<IllegalArgumentException> { CatalogoJson.carregar(duplicado) }
    }
}
