package de.wtzg.domino.data.online

import de.wtzg.domino.engine.Difficulty
import de.wtzg.domino.protocol.Action
import de.wtzg.domino.protocol.ActionRequest
import de.wtzg.domino.protocol.ApiError
import de.wtzg.domino.protocol.CreateGameRequest
import de.wtzg.domino.protocol.GameSummary
import de.wtzg.domino.protocol.GameView
import de.wtzg.domino.protocol.JoinRequest
import de.wtzg.domino.protocol.OpenGame
import de.wtzg.domino.protocol.ProtocolJson
import de.wtzg.domino.protocol.RegisterRequest
import de.wtzg.domino.protocol.ProtocolVersion
import de.wtzg.domino.protocol.Registration
import de.wtzg.domino.protocol.ServerInfo
import de.wtzg.domino.protocol.SettingsRequest
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import de.wtzg.domino.protocol.RenameRequest
import de.wtzg.domino.protocol.StartRequest
import de.wtzg.domino.protocol.Visibility
import de.wtzg.domino.protocol.VisibilityRequest
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.URLBuilder
import io.ktor.http.URLProtocol
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.http.takeFrom
import io.ktor.serialization.kotlinx.json.json
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.io.IOException

/**
 * Builds the HTTP client used for the game server. Every request says which protocol
 * this app speaks, so the server can stay compatible with it.
 */
fun onlineHttpClient(
    engine: io.ktor.client.engine.HttpClientEngine? = null,
    appVersion: String = de.wtzg.domino.BuildConfig.VERSION_NAME,
): HttpClient {
    val config: io.ktor.client.HttpClientConfig<*>.() -> Unit = {
        defaultRequest {
            header(ProtocolVersion.HEADER, ProtocolVersion.CURRENT.toString())
            header(HttpHeaders.UserAgent, "Dominoes/$appVersion")
        }
        install(ContentNegotiation) { json(ProtocolJson) }
        install(WebSockets)
        install(HttpTimeout) {
            connectTimeoutMillis = 10_000
            requestTimeoutMillis = 20_000
        }
        expectSuccess = false
    }
    return if (engine != null) HttpClient(engine, config) else HttpClient(io.ktor.client.engine.okhttp.OkHttp, config)
}

/**
 * [OnlineApi] over HTTP and WebSockets. [token] is null until you've registered.
 */
class KtorOnlineApi(
    private val client: HttpClient,
    private val baseUrl: String,
    private val token: String?,
) : OnlineApi {

    private suspend inline fun <reified T> call(block: () -> HttpResponse): T {
        val response = try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            throw OnlineException("Can't reach the server. Check your connection.")
        } catch (e: Exception) {
            throw OnlineException("Can't reach the server (${e.message ?: e::class.simpleName}).")
        }
        if (!response.status.isSuccess()) {
            val msg = runCatching { response.body<ApiError>().message }.getOrNull() ?: "Server error ${response.status.value}"
            throw OnlineException(msg, response.status.value)
        }
        return response.body()
    }

    private fun HttpRequestBuilder.endpoint(path: String) {
        url.takeFrom(baseUrl.trimEnd('/') + path)
        token?.let { bearerAuth(it) }
    }

    private fun HttpRequestBuilder.jsonBody(body: Any) {
        contentType(ContentType.Application.Json)
        setBody(body)
    }

    override suspend fun serverInfo(): ServerInfo = try {
        call { client.get { endpoint("/version") } }
    } catch (e: OnlineException) {
        // Servers from before /version existed speak protocol 1.
        if (e.status == 404) ServerInfo(version = "unknown", protocol = 1, minProtocol = 1) else throw e
    }

    override suspend fun register(name: String): Registration =
        call { client.post { endpoint("/players"); jsonBody(RegisterRequest(name)) } }

    override suspend fun rename(name: String) {
        call<Registration> { client.patch { endpoint("/players/me"); jsonBody(RenameRequest(name)) } }
    }

    override suspend fun games(): List<GameSummary> = call { client.get { endpoint("/games") } }

    override suspend fun create(request: CreateGameRequest): GameView =
        call { client.post { endpoint("/games"); jsonBody(request) } }

    override suspend fun join(code: String): GameView =
        call { client.post { endpoint("/games/join"); jsonBody(JoinRequest(code)) } }

    override suspend fun game(id: String): GameView = call { client.get { endpoint("/games/$id") } }

    override suspend fun start(id: String, difficulty: Difficulty): GameView =
        call { client.post { endpoint("/games/$id/start"); jsonBody(StartRequest(difficulty)) } }

    override suspend fun openGames(): List<OpenGame> = call { client.get { endpoint("/games/open") } }

    override suspend fun updateSettings(id: String, settings: SettingsRequest): GameView =
        call { client.post { endpoint("/games/$id/settings"); jsonBody(settings) } }

    override suspend fun setVisibility(id: String, visibility: Visibility): GameView =
        call { client.post { endpoint("/games/$id/visibility"); jsonBody(VisibilityRequest(visibility)) } }

    override suspend fun act(id: String, action: Action, version: Int): GameView? {
        val response = try {
            client.post { endpoint("/games/$id/actions"); jsonBody(ActionRequest(action, version)) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw OnlineException("Can't reach the server. Check your connection.")
        }
        if (response.status == HttpStatusCode.NoContent) return null
        return call { response }
    }

    override fun live(id: String): Flow<GameView> = flow {
        val ws = URLBuilder().takeFrom(baseUrl.trimEnd('/') + "/games/$id/live").apply {
            protocol = if (protocol == URLProtocol.HTTPS) URLProtocol.WSS else URLProtocol.WS
            parameters.append("token", token.orEmpty())
            parameters.append("protocol", ProtocolVersion.CURRENT.toString())
        }.buildString()
        var backoff = 1_000L
        while (true) {
            try {
                client.webSocket(ws, request = { header(ProtocolVersion.HEADER, ProtocolVersion.CURRENT.toString()) }) {
                    backoff = 1_000L
                    for (frame in incoming) {
                        if (frame is Frame.Text) emit(ProtocolJson.decodeFromString(GameView.serializer(), frame.readText()))
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // Dropped connection: try again shortly.
            }
            delay(backoff)
            backoff = (backoff * 2).coerceAtMost(15_000L)
        }
    }

    companion object {
        /** Adds https:// if missing and drops trailing slashes. */
        fun normalize(url: String): String {
            val u = url.trim().trimEnd('/')
            if (u.isEmpty()) return u
            return if (u.startsWith("http://") || u.startsWith("https://")) u else "https://$u"
        }
    }
}
