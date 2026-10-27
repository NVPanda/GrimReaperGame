package jogo.jogabilidade.aplicacao

import jogo.anticheat.dominio.Posicao
import jogo.anticheat.dominio.ValidadorMovimento
import jogo.identidade.dominio.Personagem
import jogo.jogabilidade.infraestrutura.ProgressoPersonagem
import jogo.jogabilidade.infraestrutura.RepositorioProgressoSqlite
import kotlinx.serialization.json.*
import kotlin.math.hypot
import kotlin.random.Random

class AcaoJogoRecusada(mensagem: String) : RuntimeException(mensagem)

@kotlinx.serialization.Serializable
data class ItemObtido(val itemId: String, val quantidade: Int)

@kotlinx.serialization.Serializable
data class ResultadoCombate(
    val monstroId: String,
    val hpRestante: Int,
    val dano: Int,
    val derrotado: Boolean,
    val experienciaGanha: Long,
    val nivelAtual: Int,
    val drops: List<ItemObtido>,
)

data class ItemJogo(
    val id: String,
    val nome: String,
    val tipo: String,
    val categoria: String,
    val rank: String,
    val slot: String?,
    val vendaMc: Long,
)

private data class DropMonstro(val itemId: String, val taxaPct: Double)
private data class MarcoMasmorra(val nivel: Int, val minibossId: String, val chanceChavePct: Double)
private data class MonstroJogo(
    val id: String,
    val nome: String,
    val nivel: Int,
    val chefe: Boolean,
    val vida: Int,
    val defesa: Int,
    val experiencia: Long,
    val drops: List<DropMonstro>,
)
private data class ZonaJogo(val id: String, val tipo: String, val monstros: List<String>)

class ServicoCombate(
    private val progresso: RepositorioProgressoSqlite,
    private val movimento: ValidadorMovimento,
    monstrosJson: String,
    itensJson: String,
    mapasJson: String,
    questsJson: String,
    npcsJson: String,
    jogabilidadeJson: String,
    private val sorteio: Random = Random.Default,
    private val agoraMs: () -> Long = System::currentTimeMillis,
) {
    private val monstros = Json.parseToJsonElement(monstrosJson).jsonArray.associate { elemento ->
        val objeto = elemento.jsonObject
        val atributos = objeto["atributos"]!!.jsonObject
        val monstro = MonstroJogo(
            objeto["id"]!!.jsonPrimitive.content,
            objeto["nome"]!!.jsonPrimitive.content,
            objeto["nivel"]!!.jsonPrimitive.int,
            objeto["boss"]!!.jsonPrimitive.boolean,
            atributos["vida"]!!.jsonPrimitive.int,
            atributos["defesa"]!!.jsonPrimitive.int,
            atributos["xp"]!!.jsonPrimitive.long,
            objeto["drops"]!!.jsonArray.map { drop ->
                DropMonstro(
                    drop.jsonObject["item"]!!.jsonPrimitive.content,
                    drop.jsonObject["taxa_pct"]!!.jsonPrimitive.double,
                )
            },
        )
        monstro.id to monstro
    }
    private val itens = Json.parseToJsonElement(itensJson).jsonArray.associate { elemento ->
        val objeto = elemento.jsonObject
        val item = ItemJogo(
            id = objeto["id"]!!.jsonPrimitive.content,
            nome = objeto["nome"]!!.jsonPrimitive.content,
            tipo = objeto["tipo"]!!.jsonPrimitive.content,
            categoria = objeto["categoria_loot"]?.jsonPrimitive?.content ?: "equipamento",
            rank = objeto["rank"]!!.jsonPrimitive.content,
            slot = objeto["slot"]?.takeUnless { it is JsonNull }?.jsonPrimitive?.content,
            vendaMc = objeto["venda_mc"]!!.jsonPrimitive.long,
        )
        item.id to item
    }
    private val zonas = Json.parseToJsonElement(mapasJson).jsonArray.associate { elemento ->
        val objeto = elemento.jsonObject
        val zona = ZonaJogo(
            objeto["id"]!!.jsonPrimitive.content,
            objeto["tipo"]!!.jsonPrimitive.content,
            objeto["monstros"]!!.jsonArray.map { it.jsonPrimitive.content },
        )
        zona.id to zona
    }
    private val quests = Json.parseToJsonElement(questsJson).jsonArray.associate { elemento ->
        val objeto = elemento.jsonObject
        objeto["id"]!!.jsonPrimitive.content to QuestJogo(
            objeto["tipo"]!!.jsonPrimitive.content,
            objeto["alvo"]!!.jsonPrimitive.content,
            objeto["zona"]!!.jsonPrimitive.content,
            objeto["npc"]!!.jsonPrimitive.content,
            objeto["nivel_minimo"]!!.jsonPrimitive.int,
            objeto["quantidade"]!!.jsonPrimitive.int,
            objeto["recompensa_mc"]!!.jsonPrimitive.long,
        )
    }
    private val zonasNpc = Json.parseToJsonElement(npcsJson).jsonArray.associate { elemento ->
        val objeto = elemento.jsonObject
        objeto["id"]!!.jsonPrimitive.content to objeto["zona"]!!.jsonPrimitive.content
    }
    private val marcosMasmorra = Json.parseToJsonElement(jogabilidadeJson).jsonObject["masmorras"]!!
        .jsonArray.associate { masmorra ->
            val objeto = masmorra.jsonObject
            objeto["id"]!!.jsonPrimitive.content to objeto["andares"]!!.jsonArray.map { andar ->
                MarcoMasmorra(
                    andar.jsonObject["nivel"]!!.jsonPrimitive.int,
                    andar.jsonObject["miniboss_id"]!!.jsonPrimitive.content,
                    andar.jsonObject["chave_drop_pct"]!!.jsonPrimitive.double,
                )
            }.sortedBy(MarcoMasmorra::nivel)
        }
    private val vidaMonstros = mutableMapOf<Pair<String, String>, Pair<Int, Long>>()

    init {
        require(monstros.isNotEmpty() && itens.isNotEmpty() && zonas.isNotEmpty())
        require(zonas.values.flatMap(ZonaJogo::monstros).all(monstros::containsKey))
        require(monstros.values.flatMap(MonstroJogo::drops).all { it.itemId in itens })
        require(itens.containsKey(ID_INSIGNIA_SS))
        require(marcosMasmorra.all { (zonaId, marcos) ->
            zonas[zonaId]?.tipo == "masmorra"
                && marcos.map(MarcoMasmorra::nivel).distinct().size == marcos.size
                && marcos.all {
                    it.nivel in 1..NIVEL_MAXIMO
                        && it.minibossId in monstros
                        && it.chanceChavePct in 0.0..100.0
                }
        })
    }

    @Synchronized
    fun estado(personagemId: String): ProgressoPersonagem = progresso.carregar(personagemId)

    @Synchronized
    fun atacar(personagem: Personagem, monstroId: String): ResultadoCombate {
        val zonaId = personagem.zonaAtualId ?: throw AcaoJogoRecusada("personagem ainda não entrou no mundo")
        val zona = zonas[zonaId] ?: throw IllegalArgumentException("zona inválida")
        require(monstroId in zona.monstros) { "monstro não existe nesta zona" }
        val monstro = monstros[monstroId] ?: throw IllegalArgumentException("monstro inválido")
        val posicao = movimento.posicaoAtual(personagem.id)
            ?: throw AcaoJogoRecusada("posição do personagem ainda não foi inicializada")
        val indice = zona.monstros.indexOf(monstroId)
        val posicaoMonstro = Pair(4 + (indice * 7) % 30, 4 + (indice * 11) % 20)
        require(hypot(posicao.x - posicaoMonstro.first, posicao.y - posicaoMonstro.second) <= DISTANCIA_ATAQUE) {
            "aproxime-se do monstro antes de atacar"
        }

        val perfil = progresso.carregar(personagem.id)
        val chave = zonaId to monstroId
        val estado = vidaMonstros.getOrPut(chave) { monstro.vida to 0L }
        if (estado.first <= 0 && agoraMs() < estado.second) {
            throw AcaoJogoRecusada("o monstro ainda está retornando")
        }
        val vidaAtual = if (estado.first <= 0) monstro.vida else estado.first
        val dano = (perfil.nivel * DANO_POR_NIVEL - monstro.defesa).coerceAtLeast(1)
        val vidaRestante = (vidaAtual - dano).coerceAtLeast(0)
        val derrotado = vidaRestante == 0
        val drops = if (derrotado) sortearDrops(zonaId, perfil, monstro) else emptyList()
        val xp = if (derrotado) monstro.experiencia else 0L
        val atualizado = progredir(
            perfil.copy(inventario = adicionarItens(perfil.inventario, drops)),
            xp,
            monstroId,
            drops,
        )
        progresso.salvar(atualizado)
        vidaMonstros[chave] = if (derrotado) {
            0 to (agoraMs() + if (monstro.chefe) TEMPO_RETORNO_CHEFE_MS else TEMPO_RETORNO_COMUM_MS)
        } else {
            vidaRestante to 0L
        }
        return ResultadoCombate(
            monstroId,
            vidaRestante,
            dano,
            derrotado,
            xp,
            atualizado.nivel,
            drops,
        )
    }

    @Synchronized
    fun equipar(personagemId: String, itemId: String): ProgressoPersonagem {
        val item = itens[itemId] ?: throw IllegalArgumentException("item inexistente")
        require(item.tipo == "equipamento" && item.slot != null) { "item não pode ser equipado" }
        val estado = progresso.carregar(personagemId)
        require((estado.inventario[itemId] ?: 0) > 0) { "item não está no inventário" }
        val equipamentos = estado.equipamentos.toMutableMap()
        val anterior = equipamentos.put(item.slot, itemId)
        val inventario = estado.inventario.toMutableMap()
        removerItem(inventario, itemId)
        if (anterior != null) inventario[anterior] = (inventario[anterior] ?: 0) + 1
        return estado.copy(inventario = inventario, equipamentos = equipamentos).also(progresso::salvar)
    }

    fun vender(
        sessaoId: String,
        personagem: Personagem,
        itemId: String,
        quantidade: Int,
    ): ProgressoPersonagem {
        val zona = personagem.zonaAtualId?.let(zonas::get) ?: throw IllegalStateException("zona inválida")
        require(zona.tipo == "cidade") { "vendas só podem ser realizadas em cidades" }
        val item = itens[itemId] ?: throw IllegalArgumentException("item inexistente")
        require(item.vendaMc > 0 && item.categoria !in setOf("ultra_raros", "ranqueados_ultra_raros")) {
            "este item não pode ser vendido ao mercador"
        }
        return progresso.vender(sessaoId, personagem.id, itemId, quantidade, item.vendaMc)
            ?: throw IllegalStateException("item indisponível ou personagem/sessão inválidos")
    }

    @Synchronized
    fun aceitarMissao(personagem: Personagem, questId: String): ProgressoPersonagem {
        val quest = quests[questId] ?: throw IllegalArgumentException("missão inexistente")
        val perfil = progresso.carregar(personagem.id)
        require(perfil.nivel >= quest.nivelMinimo) { "nível insuficiente para aceitar a missão" }
        require(personagem.zonaAtualId == zonasNpc[quest.npc]) { "fale com o guia da missão na cidade" }
        validarProximidadeGuia(personagem.id)
        require(questId !in perfil.missoesAtivas) { "missão já está ativa" }
        return perfil.copy(missoesAtivas = perfil.missoesAtivas + (questId to 0)).also(progresso::salvar)
    }

    @Synchronized
    fun entregarMissao(
        sessaoId: String,
        personagem: Personagem,
        questId: String,
    ): ProgressoPersonagem {
        val quest = quests[questId] ?: throw IllegalArgumentException("missão inexistente")
        require(personagem.zonaAtualId == zonasNpc[quest.npc]) { "entregue a missão ao guia que a ofereceu" }
        validarProximidadeGuia(personagem.id)
        val perfil = progresso.carregar(personagem.id)
        val quantidadeFeita = perfil.missoesAtivas[questId]
            ?: throw IllegalStateException("missão não está ativa")
        require(quantidadeFeita >= quest.quantidade) { "objetivo da missão ainda não foi concluído" }
        var inventario = perfil.inventario
        if (quest.tipo in setOf("plantio", "pesca", "mineracao")) {
            val possuido = inventario[quest.alvo] ?: 0
            require(possuido >= quest.quantidade) { "itens necessários não estão no inventário" }
            inventario = inventario.toMutableMap().also { itens ->
                val restante = possuido - quest.quantidade
                if (restante == 0) itens.remove(quest.alvo) else itens[quest.alvo] = restante
            }
        }
        val missoes = perfil.missoesAtivas - questId
        val atualizado = progredir(
            perfil.copy(inventario = inventario, missoesAtivas = missoes),
            quest.recompensaMc * XP_POR_MC,
            "",
            emptyList(),
        )
        if (!progresso.recompensarMissao(sessaoId, personagem.id, atualizado, quest.recompensaMc)) {
            throw IllegalStateException("personagem ou sessão inválidos para receber a recompensa")
        }
        return atualizado
    }

    private fun validarProximidadeGuia(personagemId: String) {
        val posicao = movimento.posicaoAtual(personagemId)
            ?: throw IllegalStateException("posição do personagem ainda não foi inicializada")
        require(hypot(posicao.x - POSICAO_GUIA.x, posicao.y - POSICAO_GUIA.y) <= DISTANCIA_GUIA) {
            "aproxime-se do guia da missão"
        }
    }

    @Synchronized
    fun trocarMercadoNegro(personagemId: String, itemId: String): ProgressoPersonagem {
        val item = itens[itemId] ?: throw IllegalArgumentException("item inexistente")
        require(item.tipo == "equipamento" && item.rank in setOf("A", "S")) {
            "o mercado negro só troca marcas por equipamentos A ou S"
        }
        val custo = if (item.rank == "A") MARCAS_RANK_A else MARCAS_RANK_S
        val perfil = progresso.carregar(personagemId)
        val quantidade = perfil.inventario[ID_MARCA_MERCADO] ?: 0
        require(quantidade >= custo) { "marcas insuficientes para a troca" }
        val inventario = perfil.inventario.toMutableMap()
        val restante = quantidade - custo
        if (restante == 0) inventario.remove(ID_MARCA_MERCADO) else inventario[ID_MARCA_MERCADO] = restante
        inventario[itemId] = (inventario[itemId] ?: 0) + 1
        return perfil.copy(inventario = inventario).also(progresso::salvar)
    }

    @Synchronized
    fun melhorarEquipamento(personagemId: String, equipamentoId: String): ProgressoPersonagem {
        val equipamento = itens[equipamentoId] ?: throw IllegalArgumentException("equipamento inexistente")
        require(equipamento.tipo == "equipamento" && equipamento.slot != null) {
            "somente equipamentos podem ser aprimorados"
        }
        val indice = RANKS.indexOf(equipamento.rank)
        require(indice >= 0 && indice < RANKS.lastIndex) { "equipamento já está no rank máximo" }
        val proximoRank = RANKS[indice + 1]
        val destinoId = "${equipamento.slot}_${proximoRank.lowercase()}"
        require(destinoId in itens) { "receita de aprimoramento indisponível" }
        val custo = if (proximoRank == "SS") 3 else 1
        val perfil = progresso.carregar(personagemId)
        val equipado = perfil.equipamentos[equipamento.slot] == equipamentoId
        val quantidade = perfil.inventario[equipamentoId] ?: 0
        require(equipado || quantidade > 0) { "equipamento não está no inventário" }
        val nucleos = perfil.inventario[ID_NUCLEO_RANQUEADO] ?: 0
        require(nucleos >= custo) { "núcleos de aprimoramento insuficientes" }
        val inventario = perfil.inventario.toMutableMap()
        if (!equipado) removerItem(inventario, equipamentoId)
        val nucleosRestantes = nucleos - custo
        if (nucleosRestantes == 0) inventario.remove(ID_NUCLEO_RANQUEADO)
        else inventario[ID_NUCLEO_RANQUEADO] = nucleosRestantes
        val equipamentos = perfil.equipamentos.toMutableMap()
        if (equipado) equipamentos[equipamento.slot] = destinoId
        else inventario[destinoId] = (inventario[destinoId] ?: 0) + 1
        return perfil.copy(inventario = inventario, equipamentos = equipamentos).also(progresso::salvar)
    }

    private fun sortearDrops(
        zonaId: String,
        perfil: ProgressoPersonagem,
        monstro: MonstroJogo,
    ): List<ItemObtido> {
        val obtidos = monstro.drops
            .filter { it.itemId != ID_INSIGNIA_SS }
            .filter { sorteio.nextDouble(0.0, 100.0) < it.taxaPct }
            .map { ItemObtido(it.itemId, 1) }
            .toMutableList()
        val marcoDisponivel = marcosMasmorra[zonaId]
            ?.lastOrNull { it.minibossId == monstro.id && it.nivel <= perfil.nivel }
        if (marcoDisponivel != null
            && sorteio.nextDouble(0.0, 100.0) < marcoDisponivel.chanceChavePct
        ) {
            obtidos += ItemObtido(ID_INSIGNIA_SS, 1)
        }
        return obtidos
    }

    private fun progredir(
        perfil: ProgressoPersonagem,
        xp: Long,
        monstroId: String,
        drops: List<ItemObtido>,
    ): ProgressoPersonagem {
        val missoes = perfil.missoesAtivas.toMutableMap()
        if (xp > 0) {
            missoes.keys.toList().forEach { questId ->
                val quest = quests[questId] ?: return@forEach
                if (quest.tipo == "caca" || quest.tipo == "masmorra") {
                    if (quest.alvo == monstroId) missoes[questId] = (missoes[questId] ?: 0) + 1
                }
            }
        }
        if (drops.isNotEmpty()) {
            missoes.keys.toList().forEach { questId ->
                val quest = quests[questId] ?: return@forEach
                if (quest.tipo in setOf("plantio", "pesca", "mineracao")) {
                    val quantidade = drops.filter { it.itemId == quest.alvo }.sumOf(ItemObtido::quantidade)
                    if (quantidade > 0) missoes[questId] = (missoes[questId] ?: 0) + quantidade
                }
            }
        }
        var nivel = perfil.nivel
        var experiencia = perfil.experiencia + xp
        while (nivel < NIVEL_MAXIMO && experiencia >= xpProximoNivel(nivel)) {
            experiencia -= xpProximoNivel(nivel)
            nivel++
        }
        return perfil.copy(nivel = nivel, experiencia = experiencia, missoesAtivas = missoes)
    }

    private fun adicionarItens(
        inventario: Map<String, Int>,
        drops: List<ItemObtido>,
    ): Map<String, Int> {
        val atualizado = inventario.toMutableMap()
        drops.forEach { item -> atualizado[item.itemId] = (atualizado[item.itemId] ?: 0) + item.quantidade }
        return atualizado
    }

    private fun removerItem(inventario: MutableMap<String, Int>, itemId: String) {
        val quantidade = inventario[itemId] ?: throw AcaoJogoRecusada("item indisponível")
        if (quantidade <= 1) inventario.remove(itemId) else inventario[itemId] = quantidade - 1
    }

    companion object {
        private const val NIVEL_MAXIMO = 50
        private const val DANO_POR_NIVEL = 10
        private const val DISTANCIA_ATAQUE = 2.5
        private const val TEMPO_RETORNO_COMUM_MS = 20_000L
        private const val TEMPO_RETORNO_CHEFE_MS = 900_000L
        private const val XP_POR_MC = 2
        private const val DISTANCIA_GUIA = 5.0
        private val POSICAO_GUIA = Posicao(20.0, 15.0)
        private const val ID_MARCA_MERCADO = "marca_mercado_negro"
        private const val ID_NUCLEO_RANQUEADO = "nucleo_ranqueado"
        private const val ID_INSIGNIA_SS = "insignia_cla_ss"
        private const val MARCAS_RANK_A = 5
        private const val MARCAS_RANK_S = 10
        private val RANKS = listOf("F", "E", "D", "C", "B", "A", "S", "SS")
        fun xpProximoNivel(nivel: Int): Long = (100.0 * Math.pow(nivel.toDouble(), 1.6)).toLong().coerceAtLeast(1)
    }

}

private data class QuestJogo(
    val tipo: String,
    val alvo: String,
    val zona: String,
    val npc: String,
    val nivelMinimo: Int,
    val quantidade: Int,
    val recompensaMc: Long,
)

data class EntradaMissao(val questId: String, val progresso: Int)
