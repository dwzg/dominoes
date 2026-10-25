package de.wtzg.domino.server

import de.wtzg.domino.protocol.ActionRequest
import de.wtzg.domino.protocol.ApiError
import de.wtzg.domino.protocol.CreateGameRequest
import de.wtzg.domino.protocol.JoinRequest
import de.wtzg.domino.protocol.ProtocolJson
import de.wtzg.domino.protocol.ProtocolVersion
import de.wtzg.domino.protocol.ServerFeatures
import de.wtzg.domino.protocol.ServerInfo
import de.wtzg.domino.protocol.SettingsRequest
import de.wtzg.domino.protocol.RegisterRequest
import de.wtzg.domino.protocol.RenameRequest
import de.wtzg.domino.protocol.StartRequest
import de.wtzg.domino.protocol.VisibilityRequest
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.install
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.plugins.calllogging.CallLogging
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.slf4j.event.Level
import kotlin.time.Duration.Companion.seconds

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
)

/**
 * The game server. Every [turnCheckMs] it ends games whose player to move ran out of time.
 */
fun Application.dominoServer(service: GameService, hub: LiveHub, turnCheckMs: Long = 5_000) {
    install(ContentNegotiation) { json(ProtocolJson) }
    install(CallLogging) { level = Level.INFO }
    install(WebSockets) { pingPeriod = 20.seconds }
    install(StatusPages) {
        exception<ApiException> { call, e -> call.respond(HttpStatusCode.fromValue(e.status), ApiError(e.message ?: "Error")) }
        exception<BadRequestException> { call, _ -> call.respond(HttpStatusCode.BadRequest, ApiError("Malformed request")) }
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

        // Public lobbies anyone can join. Declared before /games/{id}; Ktor prefers the fixed path anyway.
        get("/games/open") { call.respond(service.openGames(call.player(), call.protocol())) }

        post("/games") {
            val p = call.player()
            val g = service.create(p, call.receive<CreateGameRequest>())
            call.respond(HttpStatusCode.Created, service.view(g, p.id, hub.online(g.id), call.protocol()))
        }

        post("/games/join") {
            val p = call.player()
            val g = service.join(p, call.receive<JoinRequest>().code, call.protocol())
            hub.changed(g.id)
            call.respond(service.view(g, p.id, hub.online(g.id), call.protocol()))
        }

        get("/games/{id}") {
            val p = call.player()
            val g = service.game(p, call.parameters["id"]!!, call.protocol())
            call.respond(service.view(g, p.id, hub.online(g.id), call.protocol()))
        }

        // Sets up (or joins) a new lobby with the same settings after a finished game.
        post("/games/{id}/rematch") {
            val p = call.player()
            val id = call.parameters["id"]!!
            val g = service.rematch(p, id, call.protocol())
            hub.changed(id) // the old game now points at the rematch
            hub.changed(g.id)
            call.respond(service.view(g, p.id, hub.online(g.id), call.protocol()))
        }

        post("/games/{id}/settings") {
            val p = call.player()
            val g = service.updateSettings(p, call.parameters["id"]!!, call.receive<SettingsRequest>(), call.protocol())
            hub.changed(g.id)
            call.respond(service.view(g, p.id, hub.online(g.id), call.protocol()))
        }

        post("/games/{id}/visibility") {
            val p = call.player()
            val g = service.setVisibility(p, call.parameters["id"]!!, call.receive<VisibilityRequest>().visibility)
            hub.changed(g.id)
            call.respond(service.view(g, p.id, hub.online(g.id), call.protocol()))
        }

        post("/games/{id}/start") {
            val p = call.player()
            val g = service.start(p, call.parameters["id"]!!, call.receive<StartRequest>().difficulty)
            hub.changed(g.id)
            call.respond(service.view(g, p.id, hub.online(g.id), call.protocol()))
        }

        post("/games/{id}/actions") {
            val p = call.player()
            val id = call.parameters["id"]!!
            val req = call.receive<ActionRequest>()
            val g = service.act(p, id, req.action, req.version)
            hub.changed(id)
            if (g == null) call.respond(HttpStatusCode.NoContent) else call.respond(service.view(g, p.id, hub.online(id), call.protocol()))
        }

        // Live updates: the server sends your view of the game whenever it changes.
        webSocket("/games/{id}/live") {
            val p = service.authenticate(call.request.queryParameters["token"])
            val id = call.parameters["id"]!!
            val protocol = runCatching { call.protocol() }.getOrNull()
            val start = if (protocol == null) null else p?.let { runCatching { service.game(it, id, protocol) }.getOrNull() }
            if (p == null || start == null || protocol == null) {
                close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "Not allowed"))
                return@webSocket
            }
            suspend fun push() {
                val g = runCatching { service.game(p, id) }.getOrNull()
                if (g == null) {
                    close(CloseReason(CloseReason.Codes.NORMAL, "Game gone"))
                } else {
                    val view = service.view(g, p.id, hub.online(id), protocol)
                    outgoing.send(Frame.Text(ProtocolJson.encodeToString(de.wtzg.domino.protocol.GameView.serializer(), view)))
                }
            }
            val updates = hub.changes.filter { it == id }.onStart { emit(id) }.onEach { push() }.launchIn(this)
            hub.connect(id, p.id)
            try {
                for (frame in incoming) Unit // clients only listen
            } finally {
                updates.cancel()
                hub.disconnect(id, p.id)
            }
        }

        // Invite links open this page, which hands the code to the app.
        get("/join/{code}") {
            val code = call.parameters["code"]!!.uppercase().filter { it.isLetterOrDigit() }.take(12)
            call.respondText(invitePage(code), ContentType.Text.Html)
        }
    }
}

private fun invitePage(code: String) = """
<!doctype html>
<html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>Join a dominoes game</title>
<style>
 body{font-family:system-ui,sans-serif;background:#1E6B3A;color:#fff;display:flex;min-height:100vh;margin:0;align-items:center;justify-content:center}
 main{background:#F8F6F0;color:#1B1C19;border-radius:24px;padding:28px;max-width:360px;margin:16px;text-align:center}
 .code{font-size:40px;letter-spacing:6px;font-weight:700;margin:12px 0}
 a{display:inline-block;background:#1E6B3A;color:#fff;padding:14px 22px;border-radius:999px;text-decoration:none;font-weight:600}
</style></head>
<body><main>
 <h1>You're invited to dominoes</h1>
 <p>Open the Dominoes app and join with this code:</p>
 <div class="code">$code</div>
 <p><a href="dominoes://join/$code">Open in the app</a></p>
</main></body></html>
""".trimIndent()
