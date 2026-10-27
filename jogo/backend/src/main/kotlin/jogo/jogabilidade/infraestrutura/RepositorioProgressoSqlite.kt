package jogo.jogabilidade.infraestrutura

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.sql.Connection
import java.sql.DriverManager
import java.util.UUID

private const val VIDA_JOGADOR = 100

@Serializable
data class ProgressoPersonagem(
    val personagemId: String,
    val nivel: Int = 1,
    val experiencia: Long = 0,
    val inventario: Map<String, Int> = emptyMap(),
    val equipamentos: Map<String, String> = emptyMap(),
    val missoesAtivas: Map<String, Int> = emptyMap(),
    val vidaAtual: Int = VIDA_JOGADOR,
)

@Serializable
data class ClanJogo(val id: String, val nome: String, val codigoConvite: String)
@Serializable
data class MembroClanJogo(val id: String, val nome: String)
@Serializable
data class EstadoClanJogo(val clan: ClanJogo?, val membros: List<MembroClanJogo>)
@Serializable
data class RaidJogo(
    val id: String,
    val zonaId: String,
    val nivelChefe: Int,
    val vidaChefe: Int,
    val status: String,
    val membros: List<String>,
    val grupoDerrotado: Boolean = false,
)
@Serializable
data class EstadoRaidJogo(val raid: RaidJogo?)
@Serializable
data class ResultadoDanoPvP(val vidaRestante: Int, val derrotado: Boolean, val saqueId: String?)
@Serializable
data class BossMundiEstado(
    val id: String,
    val nome: String,
    val zonaId: String,
    val centro: List<Int>,
    val raio: Double,
    val vidaAtual: Int,
    val vidaMaxima: Int,
    val proximaAparicaoEpoch: Long?,
    val derrotadoPor: String?,
    val equipeDerrotou: String?,
    val derrotadoEmEpoch: Long?,
)

@Serializable
data class ResultadoAtaqueBossMundi(val boss: BossMundiEstado, val dano: Int, val derrotado: Boolean)

class RepositorioProgressoSqlite(caminho: String) : AutoCloseable {
    private val conexao: Connection
    private val json = Json { ignoreUnknownKeys = true }

    init {
        if (caminho != ":memory:") File(caminho).canonicalFile.parentFile?.mkdirs()
        conexao = DriverManager.getConnection("jdbc:sqlite:$caminho")
        conexao.createStatement().use { statement ->
            statement.execute("PRAGMA foreign_keys = ON")
            statement.execute("PRAGMA busy_timeout = 5000")
            statement.execute(
                """
                CREATE TABLE IF NOT EXISTS gameplay_profiles (
                    character_id TEXT PRIMARY KEY,
                    level INTEGER NOT NULL DEFAULT 1 CHECK(level BETWEEN 1 AND 50),
                    experience INTEGER NOT NULL DEFAULT 0 CHECK(experience >= 0),
                    inventory_json TEXT NOT NULL DEFAULT '{}',
                    equipment_json TEXT NOT NULL DEFAULT '{}',
                    quests_json TEXT NOT NULL DEFAULT '{}',
                    hp INTEGER NOT NULL DEFAULT 100 CHECK(hp BETWEEN 0 AND 100)
                )
                """.trimIndent(),
            )
            statement.execute(
                """
                CREATE TABLE IF NOT EXISTS clans (
                    id TEXT PRIMARY KEY,
                    name TEXT NOT NULL UNIQUE COLLATE NOCASE,
                    invite_code TEXT NOT NULL UNIQUE,
                    leader_character_id TEXT NOT NULL
                )
                """.trimIndent(),
            )
            statement.execute(
                """
                CREATE TABLE IF NOT EXISTS clan_members (
                    character_id TEXT PRIMARY KEY,
                    clan_id TEXT NOT NULL REFERENCES clans(id) ON DELETE CASCADE,
                    joined_at INTEGER NOT NULL
                )
                """.trimIndent(),
            )
            statement.execute(
                """
                CREATE TABLE IF NOT EXISTS ss_raids (
                    id TEXT PRIMARY KEY,
                    clan_id TEXT NOT NULL REFERENCES clans(id),
                    zone_id TEXT NOT NULL,
                    boss_level INTEGER NOT NULL,
                    boss_hp INTEGER NOT NULL,
                    status TEXT NOT NULL CHECK(status IN ('active', 'completed', 'aborted')),
                    created_at INTEGER NOT NULL
                )
                """.trimIndent(),
            )
            statement.execute(
                """
                CREATE TABLE IF NOT EXISTS ss_raid_members (
                    raid_id TEXT NOT NULL REFERENCES ss_raids(id) ON DELETE CASCADE,
                    character_id TEXT NOT NULL,
                    PRIMARY KEY (raid_id, character_id)
                )
                """.trimIndent(),
            )
            statement.execute("CREATE INDEX IF NOT EXISTS raids_by_character ON ss_raid_members(character_id)")
            val possuiVida = conexao.createStatement().use { migration ->
                migration.executeQuery("PRAGMA table_info(gameplay_profiles)").use { rows ->
                    buildSet { while (rows.next()) add(rows.getString("name")) }.contains("hp")
                }
            }
            if (!possuiVida) statement.execute("ALTER TABLE gameplay_profiles ADD COLUMN hp INTEGER NOT NULL DEFAULT 100")
            statement.execute(
                """
                CREATE TABLE IF NOT EXISTS pvp_loot (
                    id TEXT PRIMARY KEY,
                    killer_character_id TEXT NOT NULL,
                    victim_character_id TEXT NOT NULL,
                    item_id TEXT NOT NULL,
                    expires_at INTEGER NOT NULL
                )
                """.trimIndent(),
            )
            statement.execute(
                """
                CREATE TABLE IF NOT EXISTS pvp_infractions (
                    attacker_character_id TEXT PRIMARY KEY,
                    count INTEGER NOT NULL DEFAULT 0,
                    last_at INTEGER NOT NULL
                )
                """.trimIndent(),
            )
            statement.execute(
                """
                CREATE TABLE IF NOT EXISTS world_bosses (
                    boss_id TEXT PRIMARY KEY,
                    hp INTEGER NOT NULL DEFAULT 0 CHECK(hp >= 0),
                    next_spawn_at INTEGER NOT NULL,
                    killer_name TEXT,
                    team_name TEXT,
                    killed_at INTEGER
                )
                """.trimIndent(),
            )
        }
    }

    @Synchronized
    fun carregar(personagemId: String): ProgressoPersonagem {
        conexao.prepareStatement(
            "INSERT OR IGNORE INTO gameplay_profiles(character_id) VALUES (?)",
        ).use {
            it.setString(1, personagemId)
            it.executeUpdate()
        }
        return conexao.prepareStatement(
            "SELECT level, experience, inventory_json, equipment_json, quests_json, hp FROM gameplay_profiles WHERE character_id = ?",
        ).use {
            it.setString(1, personagemId)
            it.executeQuery().use { rows ->
                check(rows.next()) { "não foi possível inicializar o progresso do personagem" }
                ProgressoPersonagem(
                    personagemId,
                    rows.getInt("level"),
                    rows.getLong("experience"),
                    json.decodeFromString(rows.getString("inventory_json")),
                    json.decodeFromString(rows.getString("equipment_json")),
                    json.decodeFromString(rows.getString("quests_json")),
                    rows.getInt("hp"),
                )
            }
        }
    }

    @Synchronized
    fun salvar(progresso: ProgressoPersonagem) {
        conexao.prepareStatement(
            """
            INSERT INTO gameplay_profiles(character_id, level, experience, inventory_json, equipment_json, quests_json, hp)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT(character_id) DO UPDATE SET
                level = excluded.level,
                experience = excluded.experience,
                inventory_json = excluded.inventory_json,
                equipment_json = excluded.equipment_json,
                quests_json = excluded.quests_json,
                hp = excluded.hp
            """.trimIndent(),
        ).use {
            it.setString(1, progresso.personagemId)
            it.setInt(2, progresso.nivel)
            it.setLong(3, progresso.experiencia)
            it.setString(4, json.encodeToString(progresso.inventario))
            it.setString(5, json.encodeToString(progresso.equipamentos))
            it.setString(6, json.encodeToString(progresso.missoesAtivas))
            it.setInt(7, progresso.vidaAtual.coerceIn(0, VIDA_JOGADOR))
            it.executeUpdate()
        }
    }

    @Synchronized
    fun vender(
        sessaoId: String,
        personagemId: String,
        itemId: String,
        quantidade: Int,
        valorUnitario: Long,
    ): ProgressoPersonagem? {
        require(quantidade in 1..99 && valorUnitario >= 0)
        conexao.autoCommit = false
        try {
            val progresso = carregar(personagemId)
            val emEstoque = progresso.inventario[itemId] ?: 0
            val equipada = itemId in progresso.equipamentos.values
            if (emEstoque < quantidade || equipada) {
                conexao.rollback()
                return null
            }
            val credito = Math.multiplyExact(valorUnitario, quantidade.toLong())
            val saldoAtualizado = conexao.prepareStatement(
                """
                UPDATE characters SET mc_balance = mc_balance + ?
                WHERE id = ? AND mc_balance <= ?
                  AND id = (SELECT character_id FROM game_sessions
                            WHERE id = ? AND revoked_at IS NULL AND expires_at > ?)
                """.trimIndent(),
            ).use {
                it.setLong(1, credito)
                it.setString(2, personagemId)
                it.setLong(3, Long.MAX_VALUE - credito)
                it.setString(4, sessaoId)
                it.setLong(5, System.currentTimeMillis() / 1000)
                it.executeUpdate() == 1
            }
            if (!saldoAtualizado) {
                conexao.rollback()
                return null
            }
            val inventario = progresso.inventario.toMutableMap()
            val restante = emEstoque - quantidade
            if (restante == 0) inventario.remove(itemId) else inventario[itemId] = restante
            salvar(progresso.copy(inventario = inventario))
            conexao.commit()
            return progresso.copy(inventario = inventario)
        } catch (erro: Exception) {
            conexao.rollback()
            throw erro
        } finally {
            conexao.autoCommit = true
        }
    }

    @Synchronized
    fun recompensarMissao(
        sessaoId: String,
        personagemId: String,
        atualizado: ProgressoPersonagem,
        recompensaMc: Long,
    ): Boolean {
        require(recompensaMc >= 0)
        conexao.autoCommit = false
        try {
            val atualizadoNoBanco = conexao.prepareStatement(
                """
                UPDATE characters SET mc_balance = mc_balance + ?
                WHERE id = ? AND mc_balance <= ?
                  AND id = (SELECT character_id FROM game_sessions
                            WHERE id = ? AND revoked_at IS NULL AND expires_at > ?)
                """.trimIndent(),
            ).use {
                it.setLong(1, recompensaMc)
                it.setString(2, personagemId)
                it.setLong(3, Long.MAX_VALUE - recompensaMc)
                it.setString(4, sessaoId)
                it.setLong(5, System.currentTimeMillis() / 1000)
                it.executeUpdate() == 1
            }
            if (!atualizadoNoBanco) {
                conexao.rollback()
                return false
            }
            salvar(atualizado)
            conexao.commit()
            return true
        } catch (erro: Exception) {
            conexao.rollback()
            throw erro
        } finally {
            conexao.autoCommit = true
        }
    }

    @Synchronized
    fun criarClan(nome: String, personagemId: String): ClanJogo {
        require(nome.trim().length in 3..24) { "nome de clã deve ter entre 3 e 24 caracteres" }
        require(clan(personagemId) == null) { "personagem já pertence a um clã" }
        val id = UUID.randomUUID().toString()
        val codigo = UUID.randomUUID().toString().replace("-", "").take(10).uppercase()
        conexao.prepareStatement(
            "INSERT INTO clans(id, name, invite_code, leader_character_id) VALUES (?, ?, ?, ?)",
        ).use {
            it.setString(1, id)
            it.setString(2, nome.trim())
            it.setString(3, codigo)
            it.setString(4, personagemId)
            it.executeUpdate()
        }
        conexao.prepareStatement(
            "INSERT INTO clan_members(character_id, clan_id, joined_at) VALUES (?, ?, ?)",
        ).use {
            it.setString(1, personagemId)
            it.setString(2, id)
            it.setLong(3, System.currentTimeMillis() / 1000)
            it.executeUpdate()
        }
        return ClanJogo(id, nome.trim(), codigo)
    }

    @Synchronized
    fun clan(personagemId: String): ClanJogo? =
        conexao.prepareStatement(
            """
            SELECT c.id, c.name, c.invite_code FROM clans c
            JOIN clan_members m ON m.clan_id = c.id WHERE m.character_id = ?
            """.trimIndent(),
        ).use {
            it.setString(1, personagemId)
            it.executeQuery().use { rows ->
                if (rows.next()) ClanJogo(rows.getString(1), rows.getString(2), rows.getString(3)) else null
            }
        }

    @Synchronized
    fun juntarClan(codigoConvite: String, personagemId: String): ClanJogo? {
        if (clan(personagemId) != null) return null
        val encontrado = conexao.prepareStatement(
            "SELECT id, name, invite_code FROM clans WHERE invite_code = ?",
        ).use {
            it.setString(1, codigoConvite.uppercase())
            it.executeQuery().use { rows ->
                if (rows.next()) ClanJogo(rows.getString(1), rows.getString(2), rows.getString(3)) else null
            }
        } ?: return null
        conexao.prepareStatement(
            "INSERT INTO clan_members(character_id, clan_id, joined_at) VALUES (?, ?, ?)",
        ).use {
            it.setString(1, personagemId)
            it.setString(2, encontrado.id)
            it.setLong(3, System.currentTimeMillis() / 1000)
            it.executeUpdate()
        }
        return encontrado
    }

    @Synchronized
    fun membrosClan(personagemId: String): List<String> {
        val clanId = clan(personagemId)?.id ?: return emptyList()
        return conexao.prepareStatement(
            "SELECT character_id FROM clan_members WHERE clan_id = ? ORDER BY joined_at, character_id",
        ).use {
            it.setString(1, clanId)
            it.executeQuery().use { rows ->
                buildList { while (rows.next()) add(rows.getString(1)) }
            }
        }
    }

    @Synchronized
    fun estadoBossMundi(
        id: String,
        nome: String,
        zonaId: String,
        centro: List<Int>,
        raio: Double,
        vidaMaxima: Int,
        intervaloSpawnSegundos: Long,
        agoraEpoch: Long,
    ): BossMundiEstado {
        require(centro.size == 2 && raio > 0 && vidaMaxima > 0 && intervaloSpawnSegundos > 0)
        val proximoSpawnInicial = (agoraEpoch / intervaloSpawnSegundos + 1) * intervaloSpawnSegundos
        conexao.prepareStatement(
            "INSERT OR IGNORE INTO world_bosses(boss_id, hp, next_spawn_at) VALUES (?, 0, ?)",
        ).use {
            it.setString(1, id)
            it.setLong(2, proximoSpawnInicial)
            it.executeUpdate()
        }
        val atual = lerEstadoBossMundi(id, nome, zonaId, centro, raio, vidaMaxima)
        if (atual.vidaAtual == 0 && atual.proximaAparicaoEpoch?.let { agoraEpoch >= it } == true) {
            conexao.prepareStatement(
                """
                UPDATE world_bosses
                SET hp = ?, next_spawn_at = 0, killer_name = NULL, team_name = NULL, killed_at = NULL
                WHERE boss_id = ? AND hp = 0 AND next_spawn_at <= ?
                """.trimIndent(),
            ).use {
                it.setInt(1, vidaMaxima)
                it.setString(2, id)
                it.setLong(3, agoraEpoch)
                it.executeUpdate()
            }
        }
        return lerEstadoBossMundi(id, nome, zonaId, centro, raio, vidaMaxima)
    }

    @Synchronized
    fun atacarBossMundi(
        id: String,
        nome: String,
        zonaId: String,
        centro: List<Int>,
        raio: Double,
        vidaMaxima: Int,
        intervaloSpawnSegundos: Long,
        retornoDerrotaSegundos: Long,
        agoraEpoch: Long,
        dano: Int,
        personagemId: String,
        nomeJogador: String,
        equipe: String?,
    ): ResultadoAtaqueBossMundi {
        require(dano > 0 && retornoDerrotaSegundos >= intervaloSpawnSegundos)
        val estado = estadoBossMundi(
            id, nome, zonaId, centro, raio, vidaMaxima, intervaloSpawnSegundos, agoraEpoch,
        )
        require(estado.vidaAtual > 0) {
            "o miniboss não está ativo; próxima aparição em ${estado.proximaAparicaoEpoch}"
        }
        val vida = (estado.vidaAtual - dano).coerceAtLeast(0)
        val derrotado = vida == 0
        conexao.autoCommit = false
        try {
            val atualizado = conexao.prepareStatement(
                """
                UPDATE world_bosses
                SET hp = ?, next_spawn_at = ?, killer_name = ?, team_name = ?, killed_at = ?
                WHERE boss_id = ? AND hp = ?
                """.trimIndent(),
            ).use {
                it.setInt(1, vida)
                it.setLong(2, if (derrotado) agoraEpoch + retornoDerrotaSegundos else 0L)
                it.setString(3, if (derrotado) nomeJogador else null)
                it.setString(4, if (derrotado) equipe else null)
                if (derrotado) it.setLong(5, agoraEpoch) else it.setNull(5, java.sql.Types.BIGINT)
                it.setString(6, id)
                it.setInt(7, estado.vidaAtual)
                it.executeUpdate() == 1
            }
            check(atualizado) { "estado do miniboss foi alterado; atualize a região e tente novamente" }
            if (derrotado) {
                val perfil = carregar(personagemId)
                val inventario = perfil.inventario.toMutableMap()
                inventario[ID_PLACA_CACADA_MUNDI] = (inventario[ID_PLACA_CACADA_MUNDI] ?: 0) + 1
                salvar(perfil.copy(inventario = inventario))
            }
            conexao.commit()
        } catch (erro: Exception) {
            conexao.rollback()
            throw erro
        } finally {
            conexao.autoCommit = true
        }
        return ResultadoAtaqueBossMundi(
            lerEstadoBossMundi(id, nome, zonaId, centro, raio, vidaMaxima),
            dano.coerceAtMost(estado.vidaAtual),
            derrotado,
        )
    }

    private fun lerEstadoBossMundi(
        id: String,
        nome: String,
        zonaId: String,
        centro: List<Int>,
        raio: Double,
        vidaMaxima: Int,
    ): BossMundiEstado =
        conexao.prepareStatement(
            "SELECT hp, next_spawn_at, killer_name, team_name, killed_at FROM world_bosses WHERE boss_id = ?",
        ).use {
            it.setString(1, id)
            it.executeQuery().use { rows ->
                check(rows.next()) { "estado do miniboss não foi inicializado" }
                val proxima = rows.getLong("next_spawn_at").let { if (it == 0L) null else it }
                BossMundiEstado(
                    id = id,
                    nome = nome,
                    zonaId = zonaId,
                    centro = centro,
                    raio = raio,
                    vidaAtual = rows.getInt("hp"),
                    vidaMaxima = vidaMaxima,
                    proximaAparicaoEpoch = proxima,
                    derrotadoPor = rows.getString("killer_name"),
                    equipeDerrotou = rows.getString("team_name"),
                    derrotadoEmEpoch = rows.getLong("killed_at").let { if (rows.wasNull()) null else it },
                )
            }
        }

    @Synchronized
    fun iniciarRaid(
        personagemId: String,
        membros: List<String>,
        zonaId: String,
        itemChaveId: String,
    ): RaidJogo? {
        require(membros.size == MEMBROS_RAID && membros.distinct().size == MEMBROS_RAID) {
            "o assalto exige exatamente dez personagens distintos"
        }
        require(personagemId in membros) { "o personagem ativo precisa participar do assalto" }
        val clanId = clan(personagemId)?.id ?: return null
        conexao.autoCommit = false
        try {
            val mesmoClan = membros.all { clan(it)?.id == clanId }
            if (!mesmoClan) {
                conexao.rollback()
                return null
            }
            val temRaidAtivo = membros.any { raidAtiva(it) != null }
            if (temRaidAtivo) {
                conexao.rollback()
                return null
            }
            val portador = membros.firstOrNull { membro ->
                carregar(membro).inventario.getOrDefault(itemChaveId, 0) > 0
            }
            if (portador == null) {
                conexao.rollback()
                return null
            }
            val perf = carregar(portador)
            val inventario = perf.inventario.toMutableMap()
            val restante = inventario.getValue(itemChaveId) - 1
            if (restante == 0) inventario.remove(itemChaveId) else inventario[itemChaveId] = restante
            salvar(perf.copy(inventario = inventario))
            val nivelChefe = membros.maxOf { carregar(it).nivel } + NIVEL_BOSS_ACIMA_DO_MAIS_FORTE
            val raidId = UUID.randomUUID().toString()
            conexao.prepareStatement(
                """
                INSERT INTO ss_raids(id, clan_id, zone_id, boss_level, boss_hp, status, created_at)
                VALUES (?, ?, ?, ?, ?, 'active', ?)
                """.trimIndent(),
            ).use {
                it.setString(1, raidId)
                it.setString(2, clanId)
                it.setString(3, zonaId)
                it.setInt(4, nivelChefe)
                it.setInt(5, VIDA_BASE_BOSS + nivelChefe * VIDA_POR_NIVEL_BOSS)
                it.setLong(6, System.currentTimeMillis() / 1000)
                it.executeUpdate()
            }
            conexao.prepareStatement("INSERT INTO ss_raid_members(raid_id, character_id) VALUES (?, ?)").use { stmt ->
                membros.forEach { membro ->
                    stmt.setString(1, raidId)
                    stmt.setString(2, membro)
                    stmt.addBatch()
                }
                stmt.executeBatch()
            }
            conexao.commit()
            return raidAtiva(personagemId)
        } catch (erro: Exception) {
            conexao.rollback()
            throw erro
        } finally {
            conexao.autoCommit = true
        }
    }

    @Synchronized
    fun raidAtiva(personagemId: String): RaidJogo? =
        conexao.prepareStatement(
            """
            SELECT r.id, r.zone_id, r.boss_level, r.boss_hp, r.status
            FROM ss_raids r JOIN ss_raid_members m ON m.raid_id = r.id
            WHERE m.character_id = ? AND r.status = 'active'
            """.trimIndent(),
        ).use {
            it.setString(1, personagemId)
            it.executeQuery().use query@{ rows ->
                if (!rows.next()) return@query null
                val raidId = rows.getString("id")
                val membros = conexao.prepareStatement(
                    "SELECT character_id FROM ss_raid_members WHERE raid_id = ? ORDER BY character_id",
                ).use { membrosStmt ->
                    membrosStmt.setString(1, raidId)
                    membrosStmt.executeQuery().use { membrosRows ->
                        buildList { while (membrosRows.next()) add(membrosRows.getString(1)) }
                    }
                }
                RaidJogo(
                    raidId,
                    rows.getString("zone_id"),
                    rows.getInt("boss_level"),
                    rows.getInt("boss_hp"),
                    rows.getString("status"),
                    membros,
                )
            }
        }

    @Synchronized
    fun abandonarRaid(personagemId: String): RaidJogo? {
        val raid = raidAtiva(personagemId) ?: return null
        conexao.prepareStatement("UPDATE ss_raids SET status = 'aborted' WHERE id = ? AND status = 'active'").use {
            it.setString(1, raid.id)
            it.executeUpdate()
        }
        return raid.copy(status = "aborted")
    }

    @Synchronized
    fun registrarInfracaoPk(personagemId: String): Int {
        conexao.prepareStatement(
            """
            INSERT INTO pvp_infractions(attacker_character_id, count, last_at)
            VALUES (?, 1, ?)
            ON CONFLICT(attacker_character_id) DO UPDATE SET
                count = count + 1,
                last_at = excluded.last_at
            """.trimIndent(),
        ).use {
            it.setString(1, personagemId)
            it.setLong(2, System.currentTimeMillis() / 1000)
            it.executeUpdate()
        }
        return conexao.prepareStatement(
            "SELECT count FROM pvp_infractions WHERE attacker_character_id = ?",
        ).use {
            it.setString(1, personagemId)
            it.executeQuery().use { rows -> check(rows.next()); rows.getInt(1) }
        }
    }

    @Synchronized
    fun aplicarDanoPvP(
        atacanteId: String,
        vitimaId: String,
        dano: Int,
        equipamentoDropado: String?,
        expiraEmEpoch: Long,
    ): ResultadoDanoPvP {
        require(dano > 0)
        conexao.autoCommit = false
        try {
            val vitima = carregar(vitimaId)
            if (vitima.vidaAtual <= 0) {
                conexao.rollback()
                throw IllegalStateException("jogador já foi derrotado")
            }
            val vida = (vitima.vidaAtual - dano).coerceAtLeast(0)
            val derrotado = vida == 0
            var saqueId: String? = null
            var equipamentos = vitima.equipamentos
            if (derrotado && equipamentoDropado != null) {
                val slot = equipamentos.entries.firstOrNull { it.value == equipamentoDropado }?.key
                    ?: throw IllegalStateException("equipamento não está mais equipado")
                equipamentos = equipamentos - slot
                saqueId = UUID.randomUUID().toString()
                conexao.prepareStatement(
                    """
                    INSERT INTO pvp_loot(id, killer_character_id, victim_character_id, item_id, expires_at)
                    VALUES (?, ?, ?, ?, ?)
                    """.trimIndent(),
                ).use {
                    it.setString(1, saqueId)
                    it.setString(2, atacanteId)
                    it.setString(3, vitimaId)
                    it.setString(4, equipamentoDropado)
                    it.setLong(5, expiraEmEpoch)
                    it.executeUpdate()
                }
            }
            salvar(vitima.copy(
                equipamentos = equipamentos,
                vidaAtual = if (derrotado) VIDA_JOGADOR else vida,
            ))
            conexao.commit()
            return ResultadoDanoPvP(vida, derrotado, saqueId)
        } catch (erro: Exception) {
            conexao.rollback()
            throw erro
        } finally {
            conexao.autoCommit = true
        }
    }

    @Synchronized
    fun saquearPvP(saqueteId: String, atacanteId: String, agoraEpoch: Long): String? {
        conexao.autoCommit = false
        try {
            val itemId = conexao.prepareStatement(
                """
                SELECT item_id FROM pvp_loot
                WHERE id = ? AND killer_character_id = ? AND expires_at > ?
                """.trimIndent(),
            ).use {
                it.setString(1, saqueteId)
                it.setString(2, atacanteId)
                it.setLong(3, agoraEpoch)
                it.executeQuery().use { rows -> if (rows.next()) rows.getString(1) else null }
            } ?: run {
                conexao.rollback()
                return null
            }
            val perfil = carregar(atacanteId)
            val inventario = perfil.inventario.toMutableMap()
            inventario[itemId] = (inventario[itemId] ?: 0) + 1
            salvar(perfil.copy(inventario = inventario))
            conexao.prepareStatement("DELETE FROM pvp_loot WHERE id = ?").use {
                it.setString(1, saqueteId)
                it.executeUpdate()
            }
            conexao.commit()
            return itemId
        } catch (erro: Exception) {
            conexao.rollback()
            throw erro
        } finally {
            conexao.autoCommit = true
        }
    }

    @Synchronized
    fun atualizarNivelChefeAoEntrar(raidId: String, personagemId: String): RaidJogo? {
        val raid = raidAtiva(personagemId) ?: return null
        if (raid.id != raidId) return null
        val nivelNovo = carregar(personagemId).nivel + NIVEL_BOSS_ACIMA_DO_MAIS_FORTE
        if (nivelNovo > raid.nivelChefe) {
            val vidaAdicional = (nivelNovo - raid.nivelChefe) * VIDA_POR_NIVEL_BOSS
            conexao.prepareStatement(
                "UPDATE ss_raids SET boss_level = ?, boss_hp = boss_hp + ? WHERE id = ? AND status = 'active'",
            ).use {
                it.setInt(1, nivelNovo)
                it.setInt(2, vidaAdicional)
                it.setString(3, raid.id)
                it.executeUpdate()
            }
        }
        return raidAtiva(personagemId)
    }

    @Synchronized
    fun atacarChefe(personagemId: String): RaidJogo? {
        val raid = raidAtiva(personagemId) ?: return null
        conexao.autoCommit = false
        try {
            val atacante = carregar(personagemId)
            if (atacante.vidaAtual <= 0) {
                conexao.rollback()
                throw IllegalStateException("aguarde a derrota do grupo para recomeçar o combate")
            }
            val dano = (atacante.nivel * 20).coerceAtLeast(1)
            val vida = (raid.vidaChefe - dano).coerceAtLeast(0)
            conexao.prepareStatement(
                "UPDATE ss_raids SET boss_hp = ?, status = ? WHERE id = ? AND status = 'active'",
            ).use {
                it.setInt(1, vida)
                it.setString(2, if (vida == 0) "completed" else "active")
                it.setString(3, raid.id)
                it.executeUpdate()
            }
            if (vida == 0) {
                raid.membros.forEach { membro ->
                    val perfil = carregar(membro)
                    val inventario = perfil.inventario.toMutableMap()
                    inventario[ID_NUCLEO_RANQUEADO] = (inventario[ID_NUCLEO_RANQUEADO] ?: 0) + 1
                    salvar(perfil.copy(inventario = inventario))
                }
            } else {
                val danoDoChefe = (raid.nivelChefe - atacante.nivel + 5).coerceAtLeast(1)
                salvar(atacante.copy(vidaAtual = (atacante.vidaAtual - danoDoChefe).coerceAtLeast(0)))
                val grupoDerrotado = raid.membros.all { carregar(it).vidaAtual <= 0 }
                if (grupoDerrotado) {
                    val nivelBase = raid.membros.maxOf { carregar(it).nivel } + NIVEL_BOSS_ACIMA_DO_MAIS_FORTE
                    conexao.prepareStatement(
                        "UPDATE ss_raids SET boss_level = ?, boss_hp = ? WHERE id = ? AND status = 'active'",
                    ).use {
                        it.setInt(1, nivelBase)
                        it.setInt(2, VIDA_BASE_BOSS + nivelBase * VIDA_POR_NIVEL_BOSS)
                        it.setString(3, raid.id)
                        it.executeUpdate()
                    }
                    raid.membros.forEach { membro ->
                        val perfil = carregar(membro)
                        salvar(perfil.copy(vidaAtual = VIDA_JOGADOR))
                    }
                }
            }
            conexao.commit()
            val atualizado = lerRaid(raid.id)
            return atualizado?.copy(grupoDerrotado = vida > 0 && raid.membros.all { carregar(it).vidaAtual == VIDA_JOGADOR })
        } catch (erro: Exception) {
            conexao.rollback()
            throw erro
        } finally {
            conexao.autoCommit = true
        }
    }

    private fun lerRaid(raidId: String): RaidJogo? =
        conexao.prepareStatement(
            "SELECT id, zone_id, boss_level, boss_hp, status FROM ss_raids WHERE id = ?",
        ).use {
            it.setString(1, raidId)
            it.executeQuery().use query@{ rows ->
                if (!rows.next()) return@query null
                val membros = conexao.prepareStatement(
                    "SELECT character_id FROM ss_raid_members WHERE raid_id = ? ORDER BY character_id",
                ).use { membrosStmt ->
                    membrosStmt.setString(1, raidId)
                    membrosStmt.executeQuery().use { membrosRows ->
                        buildList { while (membrosRows.next()) add(membrosRows.getString(1)) }
                    }
                }
                RaidJogo(
                    rows.getString("id"), rows.getString("zone_id"), rows.getInt("boss_level"),
                    rows.getInt("boss_hp"), rows.getString("status"), membros,
                )
            }
        }

    override fun close() = conexao.close()

    companion object {
        private const val MEMBROS_RAID = 10
        private const val NIVEL_BOSS_ACIMA_DO_MAIS_FORTE = 10
        private const val VIDA_BASE_BOSS = 1_000
        private const val VIDA_POR_NIVEL_BOSS = 200
        private const val ID_NUCLEO_RANQUEADO = "nucleo_ranqueado"
        private const val ID_PLACA_CACADA_MUNDI = "placa_cacada_mundi"
    }
}
