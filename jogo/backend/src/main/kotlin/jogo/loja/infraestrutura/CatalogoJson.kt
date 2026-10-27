package jogo.loja.infraestrutura

import jogo.loja.dominio.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.OffsetDateTime

/** Lê o catalogo.json (formato de SDD 01 e do arquivo de administração). */
object CatalogoJson {
    private val json = Json { ignoreUnknownKeys = true }

    fun carregar(texto: String): Catalogo {
        val arquivo = json.decodeFromString<ArquivoJson>(texto)
        return Catalogo(arquivo.itens.map { it.paraDominio() })
    }

    @Serializable
    private data class ArquivoJson(val itens: List<ItemJson>)

    @Serializable
    private data class PrecoJson(val valor: Double, val moeda: String = "BRL")

    @Serializable
    private data class ItemJson(
        val id: String,
        val nome: String,
        val tipo: String,
        val descricao: String = "",
        val preco: PrecoJson,
        val status: String,
        @SerialName("disponivel_de") val disponivelDe: String,
        @SerialName("disponivel_ate") val disponivelAte: String? = null,
        @SerialName("limite_por_conta") val limitePorConta: Int,
        @SerialName("efeito_em_combate") val efeitoEmCombate: Boolean,
        @SerialName("limite_diario") val limiteDiario: Int? = null,
    ) {
        fun paraDominio() = ItemLoja(
            id = id,
            nome = nome,
            descricao = descricao,
            tipo = TipoItem.valueOf(tipo.uppercase()),
            preco = Preco(BigDecimal.valueOf(preco.valor).movePointRight(2).setScale(0, RoundingMode.HALF_EVEN).longValueExact()),
            status = StatusItem.valueOf(status.uppercase()),
            disponivelDe = OffsetDateTime.parse(disponivelDe).toInstant(),
            disponivelAte = disponivelAte?.let { OffsetDateTime.parse(it).toInstant() },
            limitePorConta = limitePorConta,
            efeitoEmCombate = efeitoEmCombate,
            limiteDiario = limiteDiario,
        )
    }
}
