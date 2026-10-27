package jogo.compartilhado

/** Validação de entrada. Tudo que vem do cliente é tratado como hostil (SDD 04). */
object Validacao {
    private val NOME = Regex("^[A-Za-z0-9_ ]{3,16}$")
    private val ID_ITEM = Regex("^[a-z0-9_]{1,64}$")

    fun nomePersonagem(valor: String): String {
        require(NOME.matches(valor)) { "nome inválido" }
        return valor
    }

    fun idItemSeguro(valor: String): String {
        require(ID_ITEM.matches(valor)) { "item inválido" }
        return valor
    }

    fun coordenada(valor: Double, limite: Double = 10_000.0): Double {
        require(valor.isFinite() && valor in -limite..limite) { "coordenada inválida" }
        return valor
    }

    /** Escapa texto que outros jogadores vão ver. Evita injeção de HTML/script no cliente. */
    fun textoSeguro(valor: String, maximo: Int = 200): String {
        require(valor.length <= maximo) { "texto longo demais" }
        return valor
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#39;")
            .filter { it == '\n' || it == '\t' || it >= ' ' }
    }
}
