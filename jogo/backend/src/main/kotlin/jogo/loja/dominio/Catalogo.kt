package jogo.loja.dominio

import java.time.Instant

class ItemIndisponivel(id: String) : RuntimeException("item indisponível: $id")

/** Conjunto de itens. Garante IDs únicos (LOJA-05). */
class Catalogo(itens: List<ItemLoja>) {
    private val itens: List<ItemLoja> = itens

    init {
        require(itens.map { it.id }.toSet().size == itens.size) { "ids de item duplicados" }
    }

    fun porId(id: String): ItemLoja? = itens.find { it.id == id }

    fun vitrine(agora: Instant): List<ItemLoja> = itens.filter { it.vendavelEm(agora) }

    fun paraVenda(id: String, agora: Instant): ItemLoja {
        val item = porId(id) ?: throw ItemIndisponivel(id)
        if (!item.vendavelEm(agora)) throw ItemIndisponivel(id)
        return item
    }
}
