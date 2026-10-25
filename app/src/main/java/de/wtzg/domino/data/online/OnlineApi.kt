package de.wtzg.domino.data.online

import de.wtzg.domino.engine.Difficulty
import de.wtzg.domino.protocol.Action
import de.wtzg.domino.protocol.CreateGameRequest
import de.wtzg.domino.protocol.GameSummary
import de.wtzg.domino.protocol.GameView
import de.wtzg.domino.protocol.OpenGame
import de.wtzg.domino.protocol.Registration
import de.wtzg.domino.protocol.ServerInfo
import de.wtzg.domino.protocol.Visibility
import kotlinx.coroutines.flow.Flow

/** A problem talking to the server, with a message fit to show the player. */
class OnlineException(message: String, val status: Int? = null) : Exception(message)

/** The game server, as the app uses it. */
interface OnlineApi {
    /** Which protocols the server speaks. Servers from before /version existed report protocol 1. */
    suspend fun serverInfo(): ServerInfo

    suspend fun register(name: String): Registration
    suspend fun rename(name: String)
    suspend fun games(): List<GameSummary>
    suspend fun create(request: CreateGameRequest): GameView
    suspend fun join(code: String): GameView
    suspend fun game(id: String): GameView
    suspend fun start(id: String, difficulty: Difficulty): GameView

    /** Public lobbies you could join. Needs a server with [de.wtzg.domino.protocol.ServerFeatures.PUBLIC_GAMES]. */
    suspend fun openGames(): List<OpenGame>

    /** The host lists the lobby publicly or hides it again. */
    suspend fun setVisibility(id: String, visibility: Visibility): GameView

    /** Returns the new view, or null if the game is gone (e.g. the host cancelled it). */
    suspend fun act(id: String, action: Action, version: Int): GameView?

    /** Your view of the game, sent again on every change. Reconnects by itself while collected. */
    fun live(id: String): Flow<GameView>
}
