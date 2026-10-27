package jogo.identidade.infraestrutura

import jogo.identidade.dominio.Conta
import jogo.identidade.dominio.EstadoOAuth
import jogo.identidade.dominio.Personagem
import java.io.File
import java.sql.Connection
import java.sql.DriverManager
import java.sql.ResultSet
import java.time.Instant
import java.util.UUID

class RepositorioIdentidadeSqlite(caminho: String) : AutoCloseable {
    private val connection: Connection

    init {
        if (caminho != ":memory:") {
            File(caminho).canonicalFile.parentFile?.mkdirs()
        }
        connection = DriverManager.getConnection("jdbc:sqlite:$caminho")
        connection.createStatement().use { statement ->
            statement.execute("PRAGMA foreign_keys = ON")
            statement.execute("PRAGMA busy_timeout = 5000")
            statement.execute(
                """
                CREATE TABLE IF NOT EXISTS accounts (
                    id TEXT PRIMARY KEY,
                    email TEXT UNIQUE COLLATE NOCASE,
                    display_name TEXT NOT NULL,
                    password_hash BLOB,
                    password_salt BLOB,
                    created_at INTEGER NOT NULL
                )
                """.trimIndent()
            )
            statement.execute(
                """
                CREATE TABLE IF NOT EXISTS social_identities (
                    provider TEXT NOT NULL,
                    subject TEXT NOT NULL,
                    account_id TEXT NOT NULL REFERENCES accounts(id) ON DELETE CASCADE,
                    PRIMARY KEY (provider, subject)
                )
                """.trimIndent()
            )
            statement.execute(
                """
                CREATE TABLE IF NOT EXISTS characters (
                    id TEXT PRIMARY KEY,
                    account_id TEXT NOT NULL REFERENCES accounts(id) ON DELETE CASCADE,
                    name TEXT NOT NULL COLLATE NOCASE,
                    class_id TEXT NOT NULL,
                    created_at INTEGER NOT NULL,
                    birth_continent_id TEXT,
                    birth_zone_id TEXT,
                    saved_zone_id TEXT,
                    current_zone_id TEXT,
                    mc_balance INTEGER NOT NULL DEFAULT 10,
                    UNIQUE (account_id, name)
                )
                """.trimIndent()
            )
            listOf(
                "birth_continent_id TEXT",
                "birth_zone_id TEXT",
                "saved_zone_id TEXT",
                "current_zone_id TEXT",
                "mc_balance INTEGER NOT NULL DEFAULT 10",
            ).forEach { definition ->
                val column = definition.substringBefore(' ')
                val exists = connection.createStatement().use { migration ->
                    migration.executeQuery("PRAGMA table_info(characters)").use { rows ->
                        buildSet { while (rows.next()) add(rows.getString("name")) }.contains(column)
                    }
                }
                if (!exists) {
                    connection.createStatement().use { migration ->
                        migration.execute("ALTER TABLE characters ADD COLUMN $definition")
                    }
                }
            }
            statement.execute(
                """
                CREATE TABLE IF NOT EXISTS game_sessions (
                    id TEXT PRIMARY KEY,
                    account_id TEXT NOT NULL REFERENCES accounts(id) ON DELETE CASCADE,
                    character_id TEXT REFERENCES characters(id) ON DELETE SET NULL,
                    expires_at INTEGER NOT NULL,
                    revoked_at INTEGER
                )
                """.trimIndent()
            )
            statement.execute(
                """
                CREATE UNIQUE INDEX IF NOT EXISTS one_live_session_per_account
                ON game_sessions(account_id) WHERE revoked_at IS NULL
                """.trimIndent()
            )
            statement.execute(
                """
                CREATE TABLE IF NOT EXISTS oauth_states (
                    state TEXT PRIMARY KEY,
                    provider TEXT NOT NULL,
                    verifier TEXT,
                    callback_url TEXT NOT NULL,
                    expires_at INTEGER NOT NULL
                )
                """.trimIndent()
            )
            statement.execute(
                """
                CREATE TABLE IF NOT EXISTS oauth_codes (
                    code_hash TEXT PRIMARY KEY,
                    account_id TEXT NOT NULL REFERENCES accounts(id) ON DELETE CASCADE,
                    session_id TEXT NOT NULL REFERENCES game_sessions(id) ON DELETE CASCADE,
                    expires_at INTEGER NOT NULL
                )
                """.trimIndent()
            )
            statement.execute("CREATE INDEX IF NOT EXISTS sessions_by_account ON game_sessions(account_id)")
            statement.execute("CREATE INDEX IF NOT EXISTS characters_by_account ON characters(account_id)")
        }
    }

    @Synchronized
    fun criarConta(email: String, nomeExibicao: String, hash: ByteArray, salt: ByteArray): Conta {
        val id = UUID.randomUUID().toString()
        connection.prepareStatement(
            "INSERT INTO accounts(id, email, display_name, password_hash, password_salt, created_at) VALUES (?, ?, ?, ?, ?, ?)"
        ).use { statement ->
            statement.setString(1, id)
            statement.setString(2, email)
            statement.setString(3, nomeExibicao)
            statement.setBytes(4, hash)
            statement.setBytes(5, salt)
            statement.setLong(6, Instant.now().epochSecond)
            statement.executeUpdate()
        }
        return Conta(id, email, nomeExibicao)
    }

    @Synchronized
    fun contaPorEmail(email: String): Conta? =
        connection.prepareStatement("SELECT id, email, display_name FROM accounts WHERE email = ? COLLATE NOCASE").use {
            it.setString(1, email)
            it.executeQuery().use { rows -> if (rows.next()) rows.conta() else null }
        }

    @Synchronized
    fun senhaPorEmail(email: String): Pair<ByteArray, ByteArray>? =
        connection.prepareStatement("SELECT password_hash, password_salt FROM accounts WHERE email = ? COLLATE NOCASE").use {
            it.setString(1, email)
            it.executeQuery().use { rows ->
                if (rows.next()) {
                    val hash = rows.getBytes("password_hash")
                    val salt = rows.getBytes("password_salt")
                    if (hash != null && salt != null) hash to salt else null
                } else null
            }
        }

    @Synchronized
    fun contaSocial(identidade: jogo.identidade.dominio.IdentidadeSocial): Conta {
        connection.autoCommit = false
        try {
            connection.prepareStatement(
                """
                SELECT a.id, a.email, a.display_name
                FROM social_identities s JOIN accounts a ON a.id = s.account_id
                WHERE s.provider = ? AND s.subject = ?
                """.trimIndent()
            ).use {
                it.setString(1, identidade.provedor)
                it.setString(2, identidade.subject)
                it.executeQuery().use { rows ->
                    if (rows.next()) {
                        val existing = rows.conta()
                        connection.commit()
                        return existing
                    }
                }
            }

            val id = UUID.randomUUID().toString()
            val email = identidade.email?.takeIf { identidade.emailVerificado && !emailExiste(it) }
            val nome = identidade.nomeExibicao.takeIf(String::isNotBlank) ?: identidade.provedor
            connection.prepareStatement(
                "INSERT INTO accounts(id, email, display_name, created_at) VALUES (?, ?, ?, ?)"
            ).use {
                it.setString(1, id)
                it.setString(2, email)
                it.setString(3, nome.take(80))
                it.setLong(4, Instant.now().epochSecond)
                it.executeUpdate()
            }
            connection.prepareStatement(
                "INSERT INTO social_identities(provider, subject, account_id) VALUES (?, ?, ?)"
            ).use {
                it.setString(1, identidade.provedor)
                it.setString(2, identidade.subject)
                it.setString(3, id)
                it.executeUpdate()
            }
            connection.commit()
            return Conta(id, email, nome.take(80))
        } catch (erro: Exception) {
            connection.rollback()
            throw erro
        } finally {
            connection.autoCommit = true
        }
    }

    @Synchronized
    fun criarSessao(contaId: String, expiraEm: Instant): String {
        connection.autoCommit = false
        try {
            connection.prepareStatement(
                "UPDATE game_sessions SET revoked_at = ? WHERE account_id = ? AND revoked_at IS NULL"
            ).use {
                it.setLong(1, Instant.now().epochSecond)
                it.setString(2, contaId)
                it.executeUpdate()
            }
            val id = UUID.randomUUID().toString()
            connection.prepareStatement(
                "INSERT INTO game_sessions(id, account_id, expires_at) VALUES (?, ?, ?)"
            ).use {
                it.setString(1, id)
                it.setString(2, contaId)
                it.setLong(3, expiraEm.epochSecond)
                it.executeUpdate()
            }
            connection.commit()
            return id
        } catch (erro: Exception) {
            connection.rollback()
            throw erro
        } finally {
            connection.autoCommit = true
        }
    }

    @Synchronized
    fun sessaoAtiva(id: String, agora: Instant = Instant.now()): Boolean =
        connection.prepareStatement(
            "SELECT 1 FROM game_sessions WHERE id = ? AND revoked_at IS NULL AND expires_at > ?"
        ).use {
            it.setString(1, id)
            it.setLong(2, agora.epochSecond)
            it.executeQuery().use(ResultSet::next)
        }

    @Synchronized
    fun contaDaSessao(id: String): Conta? =
        connection.prepareStatement(
            """
            SELECT a.id, a.email, a.display_name
            FROM game_sessions s JOIN accounts a ON a.id = s.account_id
            WHERE s.id = ? AND s.revoked_at IS NULL AND s.expires_at > ?
            """.trimIndent()
        ).use {
            it.setString(1, id)
            it.setLong(2, Instant.now().epochSecond)
            it.executeQuery().use { rows -> if (rows.next()) rows.conta() else null }
        }

    @Synchronized
    fun expiracaoSessao(id: String): Instant? =
        connection.prepareStatement(
            "SELECT expires_at FROM game_sessions WHERE id = ? AND revoked_at IS NULL"
        ).use {
            it.setString(1, id)
            it.executeQuery().use { rows ->
                if (rows.next()) Instant.ofEpochSecond(rows.getLong(1)) else null
            }
        }

    @Synchronized
    fun personagens(contaId: String): List<Personagem> =
        connection.prepareStatement(
            """
            SELECT id, name, class_id, birth_continent_id, birth_zone_id, saved_zone_id, current_zone_id, mc_balance
            FROM characters WHERE account_id = ? ORDER BY created_at, id
            """.trimIndent()
        ).use {
            it.setString(1, contaId)
            it.executeQuery().use { rows ->
                buildList { while (rows.next()) add(rows.personagem()) }
            }
        }

    @Synchronized
    fun criarPersonagem(contaId: String, nome: String, classeId: String): Personagem {
        val qtd = connection.prepareStatement("SELECT COUNT(*) FROM characters WHERE account_id = ?").use {
            it.setString(1, contaId)
            it.executeQuery().use { rows -> rows.next(); rows.getInt(1) }
        }
        if (qtd >= 5) throw jogo.identidade.dominio.LimitePersonagensAtingido()

        val personagem = Personagem(UUID.randomUUID().toString(), nome, classeId)
        try {
            connection.prepareStatement(
                "INSERT INTO characters(id, account_id, name, class_id, created_at) VALUES (?, ?, ?, ?, ?)"
            ).use {
                it.setString(1, personagem.id)
                it.setString(2, contaId)
                it.setString(3, personagem.nome)
                it.setString(4, personagem.classeId)
                it.setLong(5, Instant.now().epochSecond)
                it.executeUpdate()
            }
        } catch (erro: java.sql.SQLException) {
            if (erro.message.orEmpty().contains("UNIQUE constraint failed")) {
                throw IllegalArgumentException("nome de personagem já utilizado")
            }
            throw erro
        }
        return personagem
    }

    @Synchronized
    fun selecionarPersonagem(sessaoId: String, personagemId: String): Boolean =
        connection.prepareStatement(
            """
            UPDATE game_sessions
            SET character_id = ?
            WHERE id = ? AND revoked_at IS NULL AND expires_at > ?
              AND account_id = (SELECT account_id FROM characters WHERE id = ?)
            """.trimIndent()
        ).use {
            it.setString(1, personagemId)
            it.setString(2, sessaoId)
            it.setLong(3, Instant.now().epochSecond)
            it.setString(4, personagemId)
            it.executeUpdate() == 1
        }

    @Synchronized
    fun personagemAtivo(sessaoId: String): String? =
        connection.prepareStatement(
            "SELECT character_id FROM game_sessions WHERE id = ? AND revoked_at IS NULL AND expires_at > ?"
        ).use {
            it.setString(1, sessaoId)
            it.setLong(2, Instant.now().epochSecond)
            it.executeQuery().use { rows -> if (rows.next()) rows.getString(1) else null }
        }

    @Synchronized
    fun personagemAtivoDados(sessaoId: String): Personagem? =
        connection.prepareStatement(
            """
            SELECT c.id, c.name, c.class_id, c.birth_continent_id, c.birth_zone_id,
                   c.saved_zone_id, c.current_zone_id, c.mc_balance
            FROM game_sessions s JOIN characters c ON c.id = s.character_id
            WHERE s.id = ? AND s.revoked_at IS NULL AND s.expires_at > ?
            """.trimIndent()
        ).use {
            it.setString(1, sessaoId)
            it.setLong(2, Instant.now().epochSecond)
            it.executeQuery().use { rows -> if (rows.next()) rows.personagem() else null }
        }

    @Synchronized
    fun personagemPorId(personagemId: String): Personagem? =
        connection.prepareStatement(
            """
            SELECT id, name, class_id, birth_continent_id, birth_zone_id,
                   saved_zone_id, current_zone_id, mc_balance
            FROM characters WHERE id = ?
            """.trimIndent(),
        ).use {
            it.setString(1, personagemId)
            it.executeQuery().use { rows -> if (rows.next()) rows.personagem() else null }
        }

    @Synchronized
    fun retornarAoRespawn(personagemId: String): Personagem? {
        connection.prepareStatement(
            "UPDATE characters SET current_zone_id = saved_zone_id WHERE id = ? AND saved_zone_id IS NOT NULL",
        ).use {
            it.setString(1, personagemId)
            if (it.executeUpdate() != 1) return null
        }
        return personagemPorId(personagemId)
    }

    @Synchronized
    fun definirNascimento(sessaoId: String, continenteId: String, zonaId: String): Boolean =
        connection.prepareStatement(
            """
            UPDATE characters
            SET birth_continent_id = ?, birth_zone_id = ?, saved_zone_id = ?, current_zone_id = ?
            WHERE id = (
                SELECT character_id FROM game_sessions
                WHERE id = ? AND revoked_at IS NULL AND expires_at > ?
            ) AND birth_zone_id IS NULL
            """.trimIndent()
        ).use {
            it.setString(1, continenteId)
            it.setString(2, zonaId)
            it.setString(3, zonaId)
            it.setString(4, zonaId)
            it.setString(5, sessaoId)
            it.setLong(6, Instant.now().epochSecond)
            it.executeUpdate() == 1
        }

    @Synchronized
    fun atualizarZonaAtual(sessaoId: String, zonaId: String): Boolean =
        connection.prepareStatement(
            """
            UPDATE characters
            SET current_zone_id = ?
            WHERE id = (
                SELECT character_id FROM game_sessions
                WHERE id = ? AND revoked_at IS NULL AND expires_at > ?
            ) AND birth_zone_id IS NOT NULL
            """.trimIndent()
        ).use {
            it.setString(1, zonaId)
            it.setString(2, sessaoId)
            it.setLong(3, Instant.now().epochSecond)
            it.executeUpdate() == 1
        }

    @Synchronized
    fun respawn(sessaoId: String): String? {
        connection.prepareStatement(
            """
            UPDATE characters
            SET current_zone_id = saved_zone_id
            WHERE id = (
                SELECT character_id FROM game_sessions
                WHERE id = ? AND revoked_at IS NULL AND expires_at > ?
            ) AND saved_zone_id IS NOT NULL
            """.trimIndent()
        ).use {
            it.setString(1, sessaoId)
            it.setLong(2, Instant.now().epochSecond)
            it.executeUpdate()
        }
        return personagemAtivoDados(sessaoId)?.zonaAtualId
    }

    @Synchronized
    fun salvarPonto(sessaoId: String, zonaAtualEsperada: String): Boolean =
        connection.prepareStatement(
            """
            UPDATE characters
            SET saved_zone_id = current_zone_id
            WHERE id = (
                SELECT character_id FROM game_sessions
                WHERE id = ? AND revoked_at IS NULL AND expires_at > ?
            ) AND current_zone_id = ?
            """.trimIndent()
        ).use {
            it.setString(1, sessaoId)
            it.setLong(2, Instant.now().epochSecond)
            it.setString(3, zonaAtualEsperada)
            it.executeUpdate() == 1
        }

    @Synchronized
    fun teletransportar(
        sessaoId: String,
        zonaAtualEsperada: String,
        destinoZonaId: String,
        tarifaMc: Long,
    ): Boolean =
        connection.prepareStatement(
            """
            UPDATE characters
            SET current_zone_id = ?, mc_balance = mc_balance - ?
            WHERE id = (
                SELECT character_id FROM game_sessions
                WHERE id = ? AND revoked_at IS NULL AND expires_at > ?
            ) AND current_zone_id = ? AND mc_balance >= ?
            """.trimIndent()
        ).use {
            it.setString(1, destinoZonaId)
            it.setLong(2, tarifaMc)
            it.setString(3, sessaoId)
            it.setLong(4, Instant.now().epochSecond)
            it.setString(5, zonaAtualEsperada)
            it.setLong(6, tarifaMc)
            it.executeUpdate() == 1
        }

    @Synchronized
    fun revogarSessao(sessaoId: String) {
        connection.prepareStatement("UPDATE game_sessions SET revoked_at = ? WHERE id = ? AND revoked_at IS NULL").use {
            it.setLong(1, Instant.now().epochSecond)
            it.setString(2, sessaoId)
            it.executeUpdate()
        }
    }

    @Synchronized
    fun salvarEstadoOAuth(state: String, estado: EstadoOAuth) {
        connection.prepareStatement(
            "INSERT INTO oauth_states(state, provider, verifier, callback_url, expires_at) VALUES (?, ?, ?, ?, ?)"
        ).use {
            it.setString(1, state)
            it.setString(2, estado.provedor)
            it.setString(3, estado.verificador)
            it.setString(4, estado.callbackUrl)
            it.setLong(5, estado.expiraEm.epochSecond)
            it.executeUpdate()
        }
    }

    @Synchronized
    fun consumirEstadoOAuth(state: String, agora: Instant = Instant.now()): EstadoOAuth? {
        connection.autoCommit = false
        try {
            val estado = connection.prepareStatement(
                "SELECT provider, verifier, callback_url, expires_at FROM oauth_states WHERE state = ?"
            ).use {
                it.setString(1, state)
                it.executeQuery().use { rows ->
                    if (rows.next()) EstadoOAuth(
                        rows.getString("provider"),
                        rows.getString("verifier"),
                        rows.getString("callback_url"),
                        Instant.ofEpochSecond(rows.getLong("expires_at")),
                    ) else null
                }
            }
            connection.prepareStatement("DELETE FROM oauth_states WHERE state = ?").use {
                it.setString(1, state)
                it.executeUpdate()
            }
            connection.commit()
            return estado?.takeIf { it.expiraEm.isAfter(agora) }
        } catch (erro: Exception) {
            connection.rollback()
            throw erro
        } finally {
            connection.autoCommit = true
        }
    }

    @Synchronized
    fun salvarCodigoOAuth(hashCodigo: String, contaId: String, sessaoId: String, expiraEm: Instant) {
        connection.prepareStatement(
            "INSERT INTO oauth_codes(code_hash, account_id, session_id, expires_at) VALUES (?, ?, ?, ?)"
        ).use {
            it.setString(1, hashCodigo)
            it.setString(2, contaId)
            it.setString(3, sessaoId)
            it.setLong(4, expiraEm.epochSecond)
            it.executeUpdate()
        }
    }

    @Synchronized
    fun consumirCodigoOAuth(hashCodigo: String, agora: Instant = Instant.now()): Pair<Conta, String>? {
        connection.autoCommit = false
        try {
            val dados = connection.prepareStatement(
                """
                SELECT a.id, a.email, a.display_name, c.session_id, c.expires_at
                FROM oauth_codes c JOIN accounts a ON a.id = c.account_id
                WHERE c.code_hash = ?
                """.trimIndent()
            ).use {
                it.setString(1, hashCodigo)
                it.executeQuery().use { rows ->
                    if (rows.next()) Triple(rows.conta(), rows.getString("session_id"), rows.getLong("expires_at")) else null
                }
            }
            connection.prepareStatement("DELETE FROM oauth_codes WHERE code_hash = ?").use {
                it.setString(1, hashCodigo)
                it.executeUpdate()
            }
            connection.commit()
            if (dados == null || dados.third <= agora.epochSecond || !sessaoAtiva(dados.second, agora)) return null
            return dados.first to dados.second
        } catch (erro: Exception) {
            connection.rollback()
            throw erro
        } finally {
            connection.autoCommit = true
        }
    }

    private fun emailExiste(email: String): Boolean =
        connection.prepareStatement("SELECT 1 FROM accounts WHERE email = ? COLLATE NOCASE").use {
            it.setString(1, email)
            it.executeQuery().use(ResultSet::next)
        }

    private fun ResultSet.conta() = Conta(getString("id"), getString("email"), getString("display_name"))
    private fun ResultSet.personagem() = Personagem(
        id = getString("id"),
        nome = getString("name"),
        classeId = getString("class_id"),
        continenteNatalId = getString("birth_continent_id"),
        zonaNatalId = getString("birth_zone_id"),
        zonaSalvaId = getString("saved_zone_id"),
        zonaAtualId = getString("current_zone_id"),
        saldoMc = getLong("mc_balance"),
    )

    @Synchronized
    override fun close() = connection.close()
}
