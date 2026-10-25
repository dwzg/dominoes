package de.wtzg.domino.protocol

import de.wtzg.domino.engine.GameMode
import kotlinx.serialization.Serializable

/**
 * Keeping old apps and new servers (and the other way round) working together.
 *
 * Rules for changing the protocol (enforced by the compatibility tests in :server):
 * - Only add things. Never rename or remove a field, an enum value or a message type.
 * - New fields need a default value, so messages from older apps still decode.
 * - Anything an older app can't decode (a new game mode, event or action) gets the next
 *   [ProtocolVersion.CURRENT], and the server hides it from apps speaking an older protocol.
 * - Breaking changes go to new routes next to the old ones; old routes keep working until
 *   [ProtocolVersion.MIN_SUPPORTED] is raised, which tells old apps to update.
 */
object ProtocolVersion {
    /** The protocol this code speaks. */
    const val CURRENT = 1

    /** The oldest protocol the server still accepts. Raise only when you must. */
    const val MIN_SUPPORTED = 1

    /** Header the app sends with every request. Requests without it are protocol 1. */
    const val HEADER = "X-Dominoes-Protocol"
}

/** What the server tells apps about itself (GET /version). */
@Serializable
data class ServerInfo(
    /** Release version, e.g. "2.1.0" ("dev" for local builds). */
    val version: String,
    val protocol: Int,
    val minProtocol: Int,
    /** Optional things this server can do; see [ServerFeatures]. Older servers send none. */
    val features: List<String> = emptyList(),
)

/** Names for [ServerInfo.features]. A feature is only added, never renamed. */
object ServerFeatures {
    /** Public lobbies: GET /games/open and POST /games/{id}/visibility. */
    const val PUBLIC_GAMES = "public-games"
}

/** The protocol version a game mode was added in; apps speaking older protocols don't know it. */
val GameMode.sinceProtocol: Int
    get() = when (this) {
        GameMode.DRAW, GameMode.BLOCK, GameMode.ALL_FIVES, GameMode.ALL_THREES -> 1
    }

/** The protocol version an event type was added in. */
val PublicEvent.sinceProtocol: Int
    get() = when (this) {
        is PublicEvent.Played, is PublicEvent.Drew, is PublicEvent.Passed -> 1
    }
