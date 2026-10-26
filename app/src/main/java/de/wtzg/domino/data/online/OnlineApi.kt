package de.wtzg.domino.data.online

import de.wtzg.domino.engine.Difficulty
import de.wtzg.domino.protocol.Action
import de.wtzg.domino.protocol.CreateGameRequest
import de.wtzg.domino.protocol.FriendsView
import de.wtzg.domino.protocol.GameSummary
import de.wtzg.domino.protocol.GameView
import de.wtzg.domino.protocol.OpenGame
import de.wtzg.domino.protocol.Registration
import de.wtzg.domino.protocol.ServerInfo
import de.wtzg.domino.protocol.SettingsRequest
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

    /** The host changes the game in the lobby. Needs [de.wtzg.domino.protocol.ServerFeatures.LOBBY_SETTINGS]. */
    suspend fun updateSettings(id: String, settings: SettingsRequest): GameView

    /** Sets up, or joins, a new lobby with the same settings after a finished game. */
    suspend fun rematch(id: String): GameView

    /** The host lists the lobby publicly or hides it again. */
    suspend fun setVisibility(id: String, visibility: Visibility): GameView

    // Friends; all need a server with [de.wtzg.domino.protocol.ServerFeatures.FRIENDS].

    /** Your friend code, friends, friend requests and game invites. */
    suspend fun friends(): FriendsView

    /** Asks the player with this friend code to be friends. */
    suspend fun addFriend(code: String): FriendsView

    /** Asks someone you've played with to be friends. */
    suspend fun addFriendById(playerId: String): FriendsView
    suspend fun acceptFriend(playerId: String): FriendsView

    /** Unfriends, declines their request or takes back yours. */
    suspend fun removeFriend(playerId: String): FriendsView

    /** Invites friends to a lobby you're in. */
    suspend fun invite(gameId: String, playerIds: List<String>): GameView
    suspend fun declineInvite(gameId: String): FriendsView

    /** Returns the new view, or null if the game is gone (e.g. the host cancelled it). */
    suspend fun act(id: String, action: Action, version: Int): GameView?

    /**
     * Your view of the game, sent again on every change. Reconnects by itself while collected.
     * [onConnection] hears true once the server is sending, and false whenever the link drops.
     */
    fun live(id: String, onConnection: (Boolean) -> Unit = {}): Flow<GameView>
}
