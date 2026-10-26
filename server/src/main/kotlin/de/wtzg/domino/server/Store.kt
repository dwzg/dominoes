package de.wtzg.domino.server

import de.wtzg.domino.protocol.ProtocolJson
import java.sql.Connection
import java.sql.DriverManager
import java.sql.ResultSet
import java.util.concurrent.ConcurrentHashMap

/** Where players and games live. */
interface Store {
    fun addPlayer(p: PlayerRecord)
    fun player(id: String): PlayerRecord?
    fun playerByTokenHash(hash: String): PlayerRecord?
    fun renamePlayer(id: String, name: String)
    fun saveGame(g: GameRecord)
    fun game(id: String): GameRecord?
    fun gameByCode(code: String): GameRecord?
    fun gamesOf(playerId: String): List<GameRecord>

    /** Whether [a] and [b] were ever at the same table. */
    fun haveShared(a: String, b: String): Boolean

    /** Public lobbies with a free seat, newest first. */
    fun openGames(limit: Int): List<GameRecord>

    /** Games where the player to move ran out of time by [now]. */
    fun overdueGames(now: Long): List<String>

    /** Also drops the invites to it. */
    fun deleteGame(id: String)

    // Friends. A friendship is kept in both directions; a request only from the asker.
    fun friendCode(playerId: String): String?
    fun setFriendCode(playerId: String, code: String)
    fun playerByFriendCode(code: String): PlayerRecord?
    fun friendsOf(playerId: String): List<String>

    /** [fromId]'s request is answered: they and [toId] become friends. */
    fun acceptRequest(fromId: String, toId: String, since: Long)

    /** Ends a friendship, any requests between the two, and their invites to each other. */
    fun unfriend(a: String, b: String)

    /** Who asked [playerId] to be friends, oldest first. */
    fun requestsTo(playerId: String): List<String>

    /** Who [playerId] asked, oldest first. */
    fun requestsFrom(playerId: String): List<String>
    fun addRequest(fromId: String, toId: String, at: Long)

    /** Game invites for [playerId], newest first. */
    fun invitesTo(playerId: String): List<InviteRecord>
    fun invitesFor(gameId: String): List<InviteRecord>
    fun addInvite(invite: InviteRecord)
    fun removeInvite(gameId: String, toId: String)
}

class InMemoryStore : Store {
    private val players = ConcurrentHashMap<String, PlayerRecord>()
    private val games = ConcurrentHashMap<String, GameRecord>()

    override fun addPlayer(p: PlayerRecord) { players[p.id] = p }
    override fun player(id: String) = players[id]
    override fun playerByTokenHash(hash: String) = players.values.firstOrNull { it.tokenHash == hash }
    override fun renamePlayer(id: String, name: String) { players.computeIfPresent(id) { _, p -> p.copy(name = name) } }
    override fun saveGame(g: GameRecord) { games[g.id] = g }
    override fun game(id: String) = games[id]
    override fun gameByCode(code: String) = games.values.firstOrNull { it.code == code }
    override fun gamesOf(playerId: String) = games.values.filter { playerId in it.playerIds }.sortedByDescending { it.updatedAt }
    override fun haveShared(a: String, b: String) = games.values.any { a in it.playerIds && b in it.playerIds }
    override fun openGames(limit: Int) = games.values.filter { it.isOpen }.sortedByDescending { it.updatedAt }.take(limit)
    override fun overdueGames(now: Long) = games.values.filter { g -> g.turnDeadline?.let { it <= now } == true }.map { it.id }
    override fun deleteGame(id: String) {
        games.remove(id)
        invites.removeIf { it.gameId == id }
    }

    private val codes = ConcurrentHashMap<String, String>()
    private val friends = ConcurrentHashMap<Pair<String, String>, Long>()
    private val requests = java.util.concurrent.CopyOnWriteArrayList<Triple<String, String, Long>>()
    private val invites = java.util.concurrent.CopyOnWriteArrayList<InviteRecord>()

    override fun friendCode(playerId: String) = codes[playerId]
    override fun setFriendCode(playerId: String, code: String) {
        check(code !in codes.values) { "Friend code taken" }
        codes[playerId] = code
    }
    override fun playerByFriendCode(code: String) = codes.entries.firstOrNull { it.value == code }?.key?.let(players::get)
    override fun friendsOf(playerId: String) =
        friends.entries.filter { it.key.first == playerId }.sortedBy { it.value }.map { it.key.second }
    override fun acceptRequest(fromId: String, toId: String, since: Long) {
        requests.removeIf { it.first == fromId && it.second == toId }
        friends.putIfAbsent(fromId to toId, since)
        friends.putIfAbsent(toId to fromId, since)
    }
    override fun unfriend(a: String, b: String) {
        friends.remove(a to b)
        friends.remove(b to a)
        requests.removeIf { (it.first == a && it.second == b) || (it.first == b && it.second == a) }
        invites.removeIf { (it.fromId == a && it.toId == b) || (it.fromId == b && it.toId == a) }
    }
    override fun requestsTo(playerId: String) = requests.filter { it.second == playerId }.sortedBy { it.third }.map { it.first }
    override fun requestsFrom(playerId: String) = requests.filter { it.first == playerId }.sortedBy { it.third }.map { it.second }
    override fun addRequest(fromId: String, toId: String, at: Long) {
        if (requests.none { it.first == fromId && it.second == toId }) requests += Triple(fromId, toId, at)
    }
    override fun invitesTo(playerId: String) = invites.filter { it.toId == playerId }.sortedByDescending { it.sentAt }
    override fun invitesFor(gameId: String) = invites.filter { it.gameId == gameId }.sortedBy { it.sentAt }
    override fun addInvite(invite: InviteRecord) {
        invites.removeIf { it.gameId == invite.gameId && it.toId == invite.toId }
        invites += invite
    }
    override fun removeInvite(gameId: String, toId: String) { invites.removeIf { it.gameId == gameId && it.toId == toId } }
}

/**
 * SQLite on disk. Games are stored as JSON next to a small index of who plays in them.
 * One connection, used under a lock: plenty for a hobby server.
 */
class SqliteStore(path: String) : Store {
    private val conn: Connection = DriverManager.getConnection("jdbc:sqlite:$path")
    private val log = org.slf4j.LoggerFactory.getLogger(SqliteStore::class.java)

    init {
        conn.createStatement().use { it.executeUpdate("PRAGMA journal_mode=WAL") }
        migrate()
    }

    /**
     * Schema changes are numbered steps, applied once each and only ever adding things,
     * so a database keeps working when the server is updated, and an older server can
     * still read a database a newer one has touched (game records are JSON with defaults).
     * Each step and its version number are written together, or not at all.
     */
    private fun migrate() {
        val current = query("PRAGMA user_version") { it.getInt(1) }.single()
        if (current > MIGRATIONS.size) {
            log.warn("Database schema $current is newer than this server knows (${MIGRATIONS.size}); running anyway.")
            return
        }
        MIGRATIONS.drop(current).forEachIndexed { i, step ->
            transaction {
                step.forEach { exec(it) }
                exec("PRAGMA user_version = ${current + i + 1}")
            }
        }
    }

    private companion object {
        /** Append new steps at the end; never edit or remove one that has shipped. */
        val MIGRATIONS: List<List<String>> = listOf(
            // 1: first release
            listOf(
                "CREATE TABLE IF NOT EXISTS players (id TEXT PRIMARY KEY, name TEXT NOT NULL, " +
                    "token_hash TEXT NOT NULL UNIQUE, created_at INTEGER NOT NULL)",
                "CREATE TABLE IF NOT EXISTS games (id TEXT PRIMARY KEY, code TEXT NOT NULL UNIQUE, " +
                    "data TEXT NOT NULL, updated_at INTEGER NOT NULL)",
                "CREATE TABLE IF NOT EXISTS game_players (game_id TEXT NOT NULL, player_id TEXT NOT NULL, " +
                    "PRIMARY KEY (game_id, player_id))",
                "CREATE INDEX IF NOT EXISTS game_players_player ON game_players(player_id)",
            ),
            // 2: public lobbies. Every game before this was private, so there's nothing to fill in.
            listOf(
                "CREATE TABLE IF NOT EXISTS open_games (game_id TEXT PRIMARY KEY, updated_at INTEGER NOT NULL)",
                "CREATE INDEX IF NOT EXISTS open_games_updated ON open_games(updated_at)",
            ),
            // 3: turn time limits. Games before this had none, so there's nothing to fill in.
            listOf(
                "CREATE TABLE IF NOT EXISTS turn_deadlines (game_id TEXT PRIMARY KEY, deadline INTEGER NOT NULL)",
                "CREATE INDEX IF NOT EXISTS turn_deadlines_deadline ON turn_deadlines(deadline)",
            ),
            // 4: friends and game invites. Nobody had any before, so there's nothing to fill in.
            listOf(
                "CREATE TABLE IF NOT EXISTS friend_codes (player_id TEXT PRIMARY KEY, code TEXT NOT NULL UNIQUE)",
                "CREATE TABLE IF NOT EXISTS friends (player_id TEXT NOT NULL, friend_id TEXT NOT NULL, " +
                    "since INTEGER NOT NULL, PRIMARY KEY (player_id, friend_id))",
                "CREATE TABLE IF NOT EXISTS friend_requests (from_id TEXT NOT NULL, to_id TEXT NOT NULL, " +
                    "sent_at INTEGER NOT NULL, PRIMARY KEY (from_id, to_id))",
                "CREATE INDEX IF NOT EXISTS friend_requests_to ON friend_requests(to_id)",
                "CREATE TABLE IF NOT EXISTS game_invites (game_id TEXT NOT NULL, to_id TEXT NOT NULL, " +
                    "from_id TEXT NOT NULL, sent_at INTEGER NOT NULL, PRIMARY KEY (game_id, to_id))",
                "CREATE INDEX IF NOT EXISTS game_invites_to ON game_invites(to_id)",
            ),
        )
    }

    private fun <T> locked(block: Connection.() -> T): T = synchronized(conn) { conn.block() }

    /** Runs [block] as one transaction: every write in it lands, or none does. */
    private fun <T> transaction(block: Connection.() -> T): T = locked {
        autoCommit = false
        try {
            block().also { commit() }
        } catch (e: Exception) {
            rollback()
            throw e
        } finally {
            autoCommit = true
        }
    }

    private fun Connection.exec(sql: String, vararg args: Any?): Int =
        prepareStatement(sql).use { st ->
            args.forEachIndexed { i, a -> st.setObject(i + 1, a) }
            st.executeUpdate()
        }

    private fun <T> Connection.select(sql: String, vararg args: Any?, row: (ResultSet) -> T): List<T> =
        prepareStatement(sql).use { st ->
            args.forEachIndexed { i, a -> st.setObject(i + 1, a) }
            st.executeQuery().use { rs -> buildList { while (rs.next()) add(row(rs)) } }
        }

    private fun exec(sql: String, vararg args: Any?) {
        locked { exec(sql, *args) }
    }

    private fun <T> query(sql: String, vararg args: Any?, row: (ResultSet) -> T): List<T> = locked { select(sql, *args, row = row) }

    private fun strings(sql: String, vararg args: Any?): List<String> = query(sql, *args) { it.getString(1) }

    // --- Players and games ---------------------------------------------------------------------

    private fun readPlayer(rs: ResultSet) =
        PlayerRecord(rs.getString("id"), rs.getString("name"), rs.getString("token_hash"), rs.getLong("created_at"))

    private fun decode(data: String) = ProtocolJson.decodeFromString(GameRecord.serializer(), data)

    private fun games(sql: String, vararg args: Any?): List<GameRecord> = query(sql, *args) { decode(it.getString(1)) }

    override fun addPlayer(p: PlayerRecord) =
        exec("INSERT INTO players (id, name, token_hash, created_at) VALUES (?, ?, ?, ?)", p.id, p.name, p.tokenHash, p.createdAt)

    override fun player(id: String) = query("SELECT * FROM players WHERE id = ?", id, row = ::readPlayer).firstOrNull()

    override fun playerByTokenHash(hash: String) = query("SELECT * FROM players WHERE token_hash = ?", hash, row = ::readPlayer).firstOrNull()

    override fun renamePlayer(id: String, name: String) = exec("UPDATE players SET name = ? WHERE id = ?", name, id)

    override fun saveGame(g: GameRecord) = transaction {
        exec(
            "INSERT INTO games (id, code, data, updated_at) VALUES (?, ?, ?, ?) " +
                "ON CONFLICT(id) DO UPDATE SET data = excluded.data, updated_at = excluded.updated_at",
            g.id, g.code, ProtocolJson.encodeToString(GameRecord.serializer(), g), g.updatedAt,
        )
        exec("DELETE FROM game_players WHERE game_id = ?", g.id)
        g.playerIds.forEach { pid -> exec("INSERT OR IGNORE INTO game_players (game_id, player_id) VALUES (?, ?)", g.id, pid) }
        if (g.isOpen) {
            exec("INSERT OR REPLACE INTO open_games (game_id, updated_at) VALUES (?, ?)", g.id, g.updatedAt)
        } else {
            exec("DELETE FROM open_games WHERE game_id = ?", g.id)
        }
        val deadline = g.turnDeadline
        if (deadline != null) {
            exec("INSERT OR REPLACE INTO turn_deadlines (game_id, deadline) VALUES (?, ?)", g.id, deadline)
        } else {
            exec("DELETE FROM turn_deadlines WHERE game_id = ?", g.id)
        }
        Unit
    }

    override fun game(id: String) = games("SELECT data FROM games WHERE id = ?", id).firstOrNull()

    override fun gameByCode(code: String) = games("SELECT data FROM games WHERE code = ?", code).firstOrNull()

    override fun gamesOf(playerId: String) = games(
        "SELECT g.data FROM games g JOIN game_players gp ON gp.game_id = g.id WHERE gp.player_id = ? ORDER BY g.updated_at DESC",
        playerId,
    )

    override fun haveShared(a: String, b: String) = query(
        "SELECT 1 FROM game_players x JOIN game_players y ON x.game_id = y.game_id WHERE x.player_id = ? AND y.player_id = ? LIMIT 1",
        a, b,
    ) { true }.isNotEmpty()

    // Checked again from the record, in case an older server changed the game without updating the list.
    override fun openGames(limit: Int) =
        games("SELECT g.data FROM games g JOIN open_games o ON o.game_id = g.id ORDER BY o.updated_at DESC LIMIT ?", limit)
            .filter { it.isOpen }

    override fun overdueGames(now: Long) = strings("SELECT game_id FROM turn_deadlines WHERE deadline <= ?", now)

    override fun deleteGame(id: String) = transaction {
        listOf("game_invites", "turn_deadlines", "open_games", "game_players").forEach { exec("DELETE FROM $it WHERE game_id = ?", id) }
        exec("DELETE FROM games WHERE id = ?", id)
        Unit
    }

    // --- Friends ---------------------------------------------------------------------------------

    override fun friendCode(playerId: String) = strings("SELECT code FROM friend_codes WHERE player_id = ?", playerId).firstOrNull()

    // A plain insert: a clash fails rather than taking someone else's code.
    override fun setFriendCode(playerId: String, code: String) =
        exec("INSERT INTO friend_codes (player_id, code) VALUES (?, ?)", playerId, code)

    override fun playerByFriendCode(code: String) =
        strings("SELECT player_id FROM friend_codes WHERE code = ?", code).firstOrNull()?.let(::player)

    override fun friendsOf(playerId: String) = strings("SELECT friend_id FROM friends WHERE player_id = ? ORDER BY since", playerId)

    override fun acceptRequest(fromId: String, toId: String, since: Long) = transaction {
        exec("DELETE FROM friend_requests WHERE from_id = ? AND to_id = ?", fromId, toId)
        exec("INSERT OR IGNORE INTO friends (player_id, friend_id, since) VALUES (?, ?, ?)", fromId, toId, since)
        exec("INSERT OR IGNORE INTO friends (player_id, friend_id, since) VALUES (?, ?, ?)", toId, fromId, since)
        Unit
    }

    override fun unfriend(a: String, b: String) = transaction {
        exec("DELETE FROM friends WHERE (player_id = ? AND friend_id = ?) OR (player_id = ? AND friend_id = ?)", a, b, b, a)
        exec("DELETE FROM friend_requests WHERE (from_id = ? AND to_id = ?) OR (from_id = ? AND to_id = ?)", a, b, b, a)
        exec("DELETE FROM game_invites WHERE (from_id = ? AND to_id = ?) OR (from_id = ? AND to_id = ?)", a, b, b, a)
        Unit
    }

    override fun requestsTo(playerId: String) = strings("SELECT from_id FROM friend_requests WHERE to_id = ? ORDER BY sent_at", playerId)

    override fun requestsFrom(playerId: String) = strings("SELECT to_id FROM friend_requests WHERE from_id = ? ORDER BY sent_at", playerId)

    override fun addRequest(fromId: String, toId: String, at: Long) =
        exec("INSERT OR IGNORE INTO friend_requests (from_id, to_id, sent_at) VALUES (?, ?, ?)", fromId, toId, at)

    private fun readInvite(rs: ResultSet) = InviteRecord(rs.getString("game_id"), rs.getString("to_id"), rs.getString("from_id"), rs.getLong("sent_at"))

    override fun invitesTo(playerId: String) = query("SELECT * FROM game_invites WHERE to_id = ? ORDER BY sent_at DESC", playerId, row = ::readInvite)
    override fun invitesFor(gameId: String) = query("SELECT * FROM game_invites WHERE game_id = ? ORDER BY sent_at", gameId, row = ::readInvite)

    override fun addInvite(invite: InviteRecord) = exec(
        "INSERT OR REPLACE INTO game_invites (game_id, to_id, from_id, sent_at) VALUES (?, ?, ?, ?)",
        invite.gameId, invite.toId, invite.fromId, invite.sentAt,
    )

    override fun removeInvite(gameId: String, toId: String) = exec("DELETE FROM game_invites WHERE game_id = ? AND to_id = ?", gameId, toId)
}
