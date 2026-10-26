package de.wtzg.domino.server

import de.wtzg.domino.protocol.ActionRequest
import de.wtzg.domino.protocol.AddFriendRequest
import de.wtzg.domino.protocol.ApiError
import de.wtzg.domino.protocol.CreateGameRequest
import de.wtzg.domino.protocol.GameView
import de.wtzg.domino.protocol.InviteRequest
import de.wtzg.domino.protocol.JoinRequest
import de.wtzg.domino.protocol.ProtocolJson
import de.wtzg.domino.protocol.ProtocolVersion
import de.wtzg.domino.protocol.RegisterRequest
import de.wtzg.domino.protocol.RenameRequest
import de.wtzg.domino.protocol.ServerFeatures
import de.wtzg.domino.protocol.ServerInfo
import de.wtzg.domino.protocol.SettingsRequest
import de.wtzg.domino.protocol.StartRequest
import de.wtzg.domino.protocol.VisibilityRequest
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.createApplicationPlugin
import io.ktor.server.application.install
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.plugins.UnsupportedMediaTypeException
import io.ktor.server.plugins.calllogging.CallLogging
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.origin
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.patch
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.ktor.server.websocket.WebSockets
import io.ktor.server.websocket.pingPeriod
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.slf4j.event.Level

/** The release version baked in at build time ("dev" for local builds). */
val serverVersion: String by lazy {
    val props = java.util.Properties()
    Thread.currentThread().contextClassLoader.getResourceAsStream("domino-server.properties")?.use(props::load)
    props.getProperty("version") ?: "dev"
}

/**
 * The protocol the calling app speaks. Apps that don't say (protocol 1) are assumed to be
 * the first version. Apps older than [ProtocolVersion.MIN_SUPPORTED] are asked to update.
 */
fun ApplicationCall.protocol(): Int {
    val p = request.headers[ProtocolVersion.HEADER]?.toIntOrNull()
        ?: request.queryParameters["protocol"]?.toIntOrNull()
        ?: 1
    if (p < ProtocolVersion.MIN_SUPPORTED) {
        throw ApiException(426, "This version of the app is too old for the server. Please update the app.")
    }
    return p
}

/** What this server can do beyond protocol 1; apps only offer what's listed. */
val SERVER_FEATURES = listOf(
    ServerFeatures.PUBLIC_GAMES,
    ServerFeatures.LOBBY_SETTINGS,
    ServerFeatures.TURN_LIMITS,
    ServerFeatures.REMATCH,
    ServerFeatures.FRIENDS,
)

/** Requests carry a few small JSON messages; anything bigger is turned away before it's read. */
private const val MAX_BODY_BYTES = 64L * 1024

private val BodyLimit = createApplicationPlugin("BodyLimit") {
    onCall { call ->
        val length = call.request.headers[io.ktor.http.HttpHeaders.ContentLength]?.toLongOrNull()
        if (length != null && length > MAX_BODY_BYTES) throw ApiException(413, "Request too large")
    }
}

/**
 * The game server. Every [turnCheckMs] it ends games whose player to move ran out of time.
 */
fun Application.dominoServer(service: GameService, hub: LiveHub, turnCheckMs: Long = 5_000) {
    install(ContentNegotiation) { json(ProtocolJson) }
    install(CallLogging) { level = Level.INFO }
    install(WebSockets) { pingPeriod = 20.seconds }
    install(BodyLimit)
    install(StatusPages) {
        exception<ApiException> { call, e -> call.respond(HttpStatusCode.fromValue(e.status), ApiError(e.message ?: "Error")) }
        exception<BadRequestException> { call, _ -> call.respond(HttpStatusCode.BadRequest, ApiError("Malformed request")) }
        exception<UnsupportedMediaTypeException> { call, _ ->
            call.respond(HttpStatusCode.UnsupportedMediaType, ApiError("Send JSON"))
        }
        exception<Throwable> { call, e ->
            call.application.environment.log.error("Unhandled error", e)
            call.respond(HttpStatusCode.InternalServerError, ApiError("Something went wrong"))
        }
    }

    fun ApplicationCall.player(): PlayerRecord {
        protocol() // turns away apps that are too old before anything else
        return service.authenticate(request.headers["Authorization"]?.removePrefix("Bearer ")?.trim())
            ?: throw ApiException(401, "Unknown player; register again")
    }

    /** Answers with the game as [p] sees it. */
    suspend fun ApplicationCall.respondGame(g: GameRecord, p: PlayerRecord, status: HttpStatusCode = HttpStatusCode.OK) =
        respond(status, service.view(g, p.id, hub.online(g.id), protocol()))

    /** A change to a game: tells everyone watching it, and answers with the new state. */
    suspend fun ApplicationCall.changed(g: GameRecord, p: PlayerRecord) {
        hub.changed(g.id)
        respondGame(g, p)
    }

    // Turn limits: end overdue games and tell anyone watching them.
    launch {
        while (isActive) {
            delay(turnCheckMs)
            try {
                service.expireOverdueTurns().forEach(hub::changed)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                environment.log.error("Checking turn limits failed", e)
            }
        }
    }

    routing {
        get("/health") { call.respondText("ok") }
        get("/version") {
            call.respond(
                ServerInfo(serverVersion, ProtocolVersion.CURRENT, ProtocolVersion.MIN_SUPPORTED, features = SERVER_FEATURES),
            )
        }

        post("/players") {
            call.protocol()
            call.respond(HttpStatusCode.Created, service.register(call.receive<RegisterRequest>().name))
        }
        patch("/players/me") {
            call.respond(service.rename(call.player(), call.receive<RenameRequest>().name))
        }

        get("/games") { call.respond(service.gamesOf(call.player(), call.protocol())) }

        // Friends, and invites to games from them.
        get("/friends") { call.respond(service.friends.page(call.player(), call.protocol())) }
        post("/friends") {
            val p = call.player()
            call.respond(service.friends.add(p, call.receive<AddFriendRequest>(), call.protocol()))
        }
        post("/friends/{id}/accept") { call.respond(service.friends.accept(call.player(), call.parameters["id"]!!, call.protocol())) }
        post("/friends/{id}/remove") { call.respond(service.friends.remove(call.player(), call.parameters["id"]!!, call.protocol())) }
        post("/invites/{gameId}/decline") {
            val p = call.player()
            service.friends.decline(p, call.parameters["gameId"]!!)?.let { hub.changed(it.id) }
            call.respond(service.friends.page(p, call.protocol()))
        }

        // Public lobbies anyone can join. Declared before /games/{id}; Ktor prefers the fixed path anyway.
        get("/games/open") { call.respond(service.openGames(call.player(), call.protocol())) }

        post("/games") {
            val p = call.player()
            call.respondGame(service.create(p, call.receive<CreateGameRequest>(), call.protocol()), p, HttpStatusCode.Created)
        }

        post("/games/join") {
            val p = call.player()
            call.changed(service.join(p, call.receive<JoinRequest>().code, call.protocol()), p)
        }

        get("/games/{id}") {
            val p = call.player()
            call.respondGame(service.game(p, call.parameters["id"]!!, call.protocol()), p)
        }

        // Sets up (or joins) a new lobby with the same settings after a finished game.
        post("/games/{id}/rematch") {
            val p = call.player()
            val id = call.parameters["id"]!!
            val g = service.rematch(p, id, call.protocol())
            hub.changed(id) // the old game now points at the rematch
            call.changed(g, p)
        }

        post("/games/{id}/settings") {
            val p = call.player()
            call.changed(service.updateSettings(p, call.parameters["id"]!!, call.receive<SettingsRequest>(), call.protocol()), p)
        }

        post("/games/{id}/visibility") {
            val p = call.player()
            call.changed(service.setVisibility(p, call.parameters["id"]!!, call.receive<VisibilityRequest>().visibility), p)
        }

        post("/games/{id}/invites") {
            val p = call.player()
            call.changed(service.invite(p, call.parameters["id"]!!, call.receive<InviteRequest>().playerIds), p)
        }

        post("/games/{id}/start") {
            val p = call.player()
            call.changed(service.start(p, call.parameters["id"]!!, call.receive<StartRequest>().difficulty), p)
        }

        post("/games/{id}/actions") {
            val p = call.player()
            val id = call.parameters["id"]!!
            val req = call.receive<ActionRequest>()
            val g = service.act(p, id, req.action, req.version)
            if (g == null) {
                hub.changed(id)
                call.respond(HttpStatusCode.NoContent)
            } else {
                call.changed(g, p)
            }
        }

        liveUpdates(service, hub)

        // Invite and friend links open a page that hands the code to the app. The server it
        // names matters for games hosted on a phone.
        get("/join/{code}") {
            val code = linkCode(call.parameters["code"]!!)
            call.respondText(
                linkPage(
                    title = "Join a dominoes game",
                    heading = "You're invited to dominoes",
                    text = "Open the Dominoes app and join with this code:",
                    code = code,
                    href = "dominoes://join/$code?server=${call.serverParam()}",
                ),
                ContentType.Text.Html,
            )
        }
        get("/friend/{code}") {
            val code = linkCode(call.parameters["code"]!!)
            call.respondText(
                linkPage(
                    title = "Add a friend in Dominoes",
                    heading = "Play dominoes with me",
                    text = "Open the Dominoes app, go to Play with friends, then Friends, and add this friend code:",
                    code = code,
                    href = "dominoes://friends?code=$code&amp;server=${call.serverParam()}",
                ),
                ContentType.Text.Html,
            )
        }
    }
}

/** Live updates: the server sends your view of the game whenever it changes. */
private fun Route.liveUpdates(service: GameService, hub: LiveHub) {
    webSocket("/games/{id}/live") {
        val p = service.authenticate(call.request.queryParameters["token"])
        val id = call.parameters["id"]!!
        val protocol = runCatching { call.protocol() }.getOrNull()
        val start = if (protocol == null) null else p?.let { runCatching { service.game(it, id, protocol) }.getOrNull() }
        if (p == null || start == null || protocol == null) {
            close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "Not allowed"))
            return@webSocket
        }
        hub.connect(id, p.id)
        try {
            coroutineScope {
                // Clients only listen; this ends when they hang up.
                val listening = launch { for (frame in incoming) Unit }
                launch {
                    hub.changes(id).collect {
                        val g = try {
                            service.game(p, id, protocol)
                        } catch (_: ApiException) {
                            null
                        }
                        if (g == null) {
                            close(CloseReason(CloseReason.Codes.NORMAL, "Game gone"))
                            listening.cancel()
                        } else {
                            val view = service.view(g, p.id, hub.online(id), protocol)
                            outgoing.send(Frame.Text(ProtocolJson.encodeToString(GameView.serializer(), view)))
                        }
                    }
                }
                listening.join()
                coroutineContext.cancelChildren()
            }
        } finally {
            hub.disconnect(id, p.id)
        }
    }
}

private fun linkCode(raw: String) = raw.uppercase().filter { it.isLetterOrDigit() }.take(12)

private fun ApplicationCall.serverParam(): String {
    val o = request.origin
    return java.net.URLEncoder.encode("${o.scheme}://${o.serverHost}:${o.serverPort}", "UTF-8")
}

/** A small page for a link opened outside the app. [code] is letters and digits only, so it's safe in HTML. */
private fun linkPage(title: String, heading: String, text: String, code: String, href: String) = """
<!doctype html>
<html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>$title</title>
<style>
 body{font-family:system-ui,sans-serif;background:#1E6B3A;color:#fff;display:flex;min-height:100vh;margin:0;align-items:center;justify-content:center}
 main{background:#F8F6F0;color:#1B1C19;border-radius:24px;padding:28px;max-width:360px;margin:16px;text-align:center}
 .code{font-size:36px;letter-spacing:5px;font-weight:700;margin:12px 0}
 a{display:inline-block;background:#1E6B3A;color:#fff;padding:14px 22px;border-radius:999px;text-decoration:none;font-weight:600}
</style></head>
<body><main>
 <h1>$heading</h1>
 <p>$text</p>
 <div class="code">$code</div>
 <p><a href="$href">Open in the app</a></p>
</main></body></html>
""".trimIndent()
