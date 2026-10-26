package de.wtzg.domino.server

import java.security.SecureRandom

/** An error the client caused, with the HTTP status to answer with. */
class ApiException(val status: Int, message: String) : RuntimeException(message)

internal fun badRequest(msg: String): Nothing = throw ApiException(400, msg)
internal fun forbidden(msg: String): Nothing = throw ApiException(403, msg)
internal fun notFound(msg: String): Nothing = throw ApiException(404, msg)
internal fun conflict(msg: String): Nothing = throw ApiException(409, msg)
internal fun updateApp(msg: String): Nothing = throw ApiException(426, msg)

/** Letters for game and friend codes: no 0/O or 1/I mix-ups. */
private const val CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"

/** A random code of [length] that [taken] says is still free. */
internal fun SecureRandom.freeCode(length: Int, taken: (String) -> Boolean): String {
    repeat(20) {
        val code = (1..length).map { CODE_ALPHABET[nextInt(CODE_ALPHABET.length)] }.joinToString("")
        if (!taken(code)) return code
    }
    error("Couldn't find a free code")
}
