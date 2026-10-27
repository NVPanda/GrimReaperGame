package jogo.loja.dominio

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class CatalogoTest {
    private val agora: Instant = Instant.parse("2026-10-17T12:00:00Z")

    private fun item(
        id: String,
        status: StatusItem = StatusItem.ATIVO,
        de: String = "2026-10-01T00:00:00Z",
        ate: String? = null,
    ) = ItemLoja(
        id = id,
        nome = "Item $id",
        descricao = "",
        tipo = TipoItem.COSMETICO,
        preco = Preco(1990),
        status = status,
        disponivelDe = Instant.parse(de),
        disponivelAte = ate?.let { Instant.parse(it) },
        limitePorConta = 1,
        efeitoEmCombate = false,
    )

    @Test
    fun `LOJA-01 item ativo dentro da janela aparece na vitrine`() {
        val catalogo = Catalogo(listOf(item("capa_01")))
        assertEquals(listOf("capa_01"), catalogo.vitrine(agora).map { it.id })
    }

    @Test
    fun `LOJA-02 item oculto ou removido nao aparece na vitrine`() {
        val catalogo = Catalogo(listOf(item("a", StatusItem.OCULTO), item("b", StatusItem.REMOVIDO)))
        assertTrue(catalogo.vitrine(agora).isEmpty())
    }

    @Test
    fun `LOJA-03 item fora da janela de venda nao pode ser comprado`() {
        val catalogo = Catalogo(listOf(item("futuro", de = "2026-12-01T00:00:00Z")))
        assertFailsWith<ItemIndisponivel> { catalogo.paraVenda("futuro", agora) }
    }

    @Test
    fun `LOJA-05 ids de item devem ser unicos`() {
        assertFailsWith<IllegalArgumentException> {
            Catalogo(listOf(item("repetido"), item("repetido")))
        }
    }

    @Test
    fun `LOJA-07 preco deve ser positivo`() {
        assertFailsWith<IllegalArgumentException> { Preco(0) }
        assertFailsWith<IllegalArgumentException> { Preco(-5) }
    }
}
