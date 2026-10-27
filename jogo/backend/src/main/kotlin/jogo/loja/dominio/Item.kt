package jogo.loja.dominio

import java.time.Instant

enum class TipoItem { COSMETICO, TITULO, BADGE, CONSUMIVEL, EXPANSAO }

enum class StatusItem { ATIVO, OCULTO, ESGOTADO, REMOVIDO }

/** Preço em centavos de BRL. Inteiro para evitar erro de ponto flutuante. */
@JvmInline
value class Preco(val centavos: Long) {
    init {
        require(centavos > 0) { "preço deve ser positivo" }
    }
}

data class ItemLoja(
    val id: String,
    val nome: String,
    val descricao: String,
    val tipo: TipoItem,
    val preco: Preco,
    val status: StatusItem,
    val disponivelDe: Instant,
    val disponivelAte: Instant?,
    val limitePorConta: Int,
    val efeitoEmCombate: Boolean,
    val limiteDiario: Int? = null,
) {
    init {
        require(ID_VALIDO.matches(id)) { "id inválido: $id" }
        require(limitePorConta >= 1) { "limite por conta deve ser ao menos 1" }
        require(limiteDiario == null || limiteDiario >= 1) { "limite diário deve ser ao menos 1" }
        require(disponivelAte == null || disponivelAte.isAfter(disponivelDe)) { "janela de venda inválida" }
    }

    fun vendavelEm(agora: Instant): Boolean =
        status == StatusItem.ATIVO &&
            !agora.isBefore(disponivelDe) &&
            (disponivelAte == null || agora.isBefore(disponivelAte))

    private companion object {
        val ID_VALIDO = Regex("^[a-z0-9_]{1,64}$")
    }
}
