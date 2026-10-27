package jogo.loja.infraestrutura

import jogo.loja.aplicacao.ConfirmacaoPagamento
import jogo.loja.aplicacao.PortaPagamento
import jogo.loja.dominio.ItemLoja
import jogo.loja.dominio.Pedido
import kotlinx.serialization.json.*
import java.math.BigDecimal
import java.math.RoundingMode
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

/**
 * Adaptador Mercado Pago (Checkout Pro). Confirme endpoints e campos na documentação oficial
 * antes de publicar: https://www.mercadopago.com.br/developers/pt/docs/checkout-pro/create-payment-preference
 */
class MercadoPagoAdaptador(
    private val accessToken: String,
    private val urlPublica: String,
    private val http: HttpClient = HttpClient.newHttpClient(),
) : PortaPagamento {

    private val base = "https://api.mercadopago.com"

    override fun criarCobranca(pedido: Pedido, item: ItemLoja): String {
        val corpo = buildJsonObject {
            put("items", buildJsonArray {
                add(buildJsonObject {
                    put("id", item.id)
                    put("title", item.nome)
                    put("quantity", 1)
                    put("unit_price", BigDecimal.valueOf(item.preco.centavos, 2))
                    put("currency_id", "BRL")
                })
            })
            put("external_reference", pedido.referencia)
            put("notification_url", "$urlPublica/webhook/mercadopago")
            put("auto_return", "approved")
        }
        val resposta = enviar("POST", "$base/checkout/preferences", corpo.toString())
        return Json.parseToJsonElement(resposta).jsonObject["init_point"]!!.jsonPrimitive.content
    }

    override fun consultarPorNotificacao(corpo: String): ConfirmacaoPagamento? {
        val id = extrairIdPagamento(corpo) ?: return null
        val resposta = enviar("GET", "$base/v1/payments/$id", null)
        return mapearPagamento(resposta)
    }

    private fun enviar(metodo: String, url: String, corpo: String?): String {
        val builder = HttpRequest.newBuilder(URI.create(url))
            .header("Authorization", "Bearer $accessToken")
            .header("Content-Type", "application/json")
        val requisicao = (if (metodo == "POST") builder.POST(HttpRequest.BodyPublishers.ofString(corpo ?: ""))
        else builder.GET()).build()
        val resposta = http.send(requisicao, HttpResponse.BodyHandlers.ofString())
        // Não inclui o corpo da resposta na exceção: pode conter dados sensíveis.
        check(resposta.statusCode() in 200..299) { "Mercado Pago respondeu ${resposta.statusCode()}" }
        return resposta.body()
    }
}

/** Extrai o ID do pagamento de uma notificação do tipo `payment`. Null para outros tipos (PAG-07). */
internal fun extrairIdPagamento(corpo: String): String? {
    val obj = runCatching { Json.parseToJsonElement(corpo).jsonObject }.getOrNull() ?: return null
    if (obj["type"]?.jsonPrimitive?.content != "payment") return null
    return obj["data"]?.jsonObject?.get("id")?.jsonPrimitive?.content
}

/** Converte o JSON de pagamento do provedor em [ConfirmacaoPagamento]. Reais viram centavos (PAG-08). */
internal fun mapearPagamento(corpo: String): ConfirmacaoPagamento {
    val obj = Json.parseToJsonElement(corpo).jsonObject
    val valorCentavos = obj["transaction_amount"]?.jsonPrimitive?.content?.let {
        BigDecimal(it).movePointRight(2).setScale(0, RoundingMode.HALF_EVEN).longValueExact()
    }
    return ConfirmacaoPagamento(
        referencia = obj["external_reference"]?.jsonPrimitive?.content ?: "",
        aprovado = obj["status"]?.jsonPrimitive?.content == "approved",
        valorPagoCentavos = valorCentavos,
    )
}
