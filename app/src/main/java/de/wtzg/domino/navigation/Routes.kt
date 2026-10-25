package de.wtzg.domino.navigation

import kotlinx.serialization.Serializable

// Type-safe navigation destinations. Game modes travel as their enum name.

@Serializable object PlayRoute
@Serializable data class SetupRoute(val mode: String)
@Serializable object GameRoute
@Serializable object LearnRoute
@Serializable data class LessonRoute(val id: String)
@Serializable data class PuzzleRoute(val mode: String)
@Serializable object ScoresRoute
@Serializable object ScoreHistoryRoute
@Serializable object MoreRoute

/** Your online games. [code] comes from an invite link (dominoes://join/CODE). */
@Serializable data class OnlineRoute(val code: String? = null)
/** An invite link. [server] is set when the game is hosted on a phone on the same Wi-Fi. */
@Serializable data class JoinRoute(val code: String, val server: String? = null)
@Serializable object OnlineCreateRoute
/**
 * One online game; also opened from notifications (dominoes://game/ID). [local] games are
 * hosted on a phone on the same Wi-Fi rather than on the online server.
 */
@Serializable data class OnlineGameRoute(val id: String, val local: Boolean = false)

/** Games on the same Wi-Fi. [server] and [code] come from an invite to a game hosted on a phone. */
@Serializable data class LocalRoute(val server: String? = null, val code: String? = null)
@Serializable object LocalCreateRoute
