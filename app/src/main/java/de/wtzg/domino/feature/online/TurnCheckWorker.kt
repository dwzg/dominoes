package de.wtzg.domino.feature.online

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import de.wtzg.domino.DominoApplication
import de.wtzg.domino.MainActivity
import de.wtzg.domino.R
import de.wtzg.domino.data.online.OnlineException
import de.wtzg.domino.protocol.FriendsView
import de.wtzg.domino.protocol.GameSummary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/**
 * Checks the server now and then and posts a notification when it's your turn in an
 * online game, a friend invites you to one, or someone wants to be your friend. Uses no
 * push service, so these can take up to ~15 minutes to show.
 */
class TurnCheckWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val repo = (applicationContext as DominoApplication).container.online
        if (repo.account.first().registration == null) return Result.success()
        val games = try {
            repo.api().games()
        } catch (_: OnlineException) {
            return Result.retry()
        }
        val foreground = withContext(Dispatchers.Main) {
            ProcessLifecycleOwner.get().lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
        }
        // Servers without friends answer 404; then there's simply nothing to tell.
        val friends = try {
            repo.api().friends()
        } catch (_: OnlineException) {
            null
        }
        val plan = planNotices(games, friends, repo.notified(), foreground)
        plan.notices.forEach { n -> notify(n.key, n.title, n.text, n.link, if (n.turn) TURNS else FRIENDS) }
        repo.setNotified(plan.seen)
        return Result.success()
    }

    private fun notify(key: String, title: String, text: String, link: String, channel: String) {
        val ctx = applicationContext
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        ensureChannel(ctx)
        val open = Intent(Intent.ACTION_VIEW, Uri.parse(link), ctx, MainActivity::class.java)
        val pending = PendingIntent.getActivity(ctx, key.hashCode(), open, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val n = NotificationCompat.Builder(ctx, channel)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(pending)
            .setAutoCancel(true)
            .setCategory(if (channel == TURNS) NotificationCompat.CATEGORY_REMINDER else NotificationCompat.CATEGORY_SOCIAL)
            .build()
        NotificationManagerCompat.from(ctx).notify(key.hashCode(), n)
    }

    companion object {
        private const val TURNS = "turns"
        private const val FRIENDS = "friends"
        private const val WORK = "turn-check"

        /** Takes down the "your turn" notification for [gameId], e.g. once you've opened the game. */
        fun clearTurnNotice(context: Context, gameId: String) {
            NotificationManagerCompat.from(context).cancel(gameId.hashCode())
        }

        fun ensureChannel(context: Context) {
            val nm = context.getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(
                NotificationChannel(TURNS, "Your turn", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "When it's your move in an online game"
                },
            )
            nm.createNotificationChannel(
                NotificationChannel(FRIENDS, "Friends and invites", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "When a friend invites you to a game, or someone wants to be your friend"
                },
            )
        }

        /** Runs the check every 15 minutes (Android's minimum) while online. Changes here reach existing installs too. */
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<TurnCheckWorker>(15, TimeUnit.MINUTES)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(WORK, ExistingPeriodicWorkPolicy.UPDATE, request)
        }
    }
}

/** One notification to post. [key] identifies it, so it's only posted once. */
data class Notice(val key: String, val title: String, val text: String, val link: String, val turn: Boolean)

/** What to post, and what to remember having told you about. */
data class NoticePlan(val notices: List<Notice>, val seen: Map<String, Int>)

private const val INVITE = "invite:"
private const val FRIEND = "friend:"

/**
 * Decides what to tell you about: your turn in a game (once per move), a friend's invite, or
 * a friend request (once each). Nothing is posted while the app is open, but it still counts
 * as seen. Finished games, answered invites and requests are forgotten. If [friends] couldn't
 * be checked this time, what we knew about them is kept, so nothing is announced twice.
 */
fun planNotices(games: List<GameSummary>, friends: FriendsView?, seen: Map<String, Int>, foreground: Boolean): NoticePlan {
    val turns = games.filter { it.isYourTurn }
    val invites = friends?.invites.orEmpty().map { (INVITE + it.gameId) to it }
    val requests = friends?.requests.orEmpty().map { (FRIEND + it.playerId) to it }
    val notices = if (foreground) emptyList() else buildList {
        turns.filter { (seen[it.id] ?: -1) < it.version }.forEach { g ->
            val others = g.seats.filterIndexed { i, _ -> i != g.yourSeat }.joinToString(", ")
            add(Notice(g.id, "Your turn in ${g.mode.title}", "Playing with $others", "dominoes://game/${g.id}", turn = true))
        }
        invites.filter { it.first !in seen }.forEach { (key, i) ->
            add(Notice(key, "${i.from} invites you to ${i.mode.title}", "Tap to join or decline", "dominoes://online", turn = false))
        }
        requests.filter { it.first !in seen }.forEach { (key, p) ->
            add(Notice(key, "${p.name} wants to be your friend", "Tap to answer", "dominoes://friends", turn = false))
        }
    }
    val friendKeys = if (friends == null) {
        seen.filterKeys { it.startsWith(INVITE) || it.startsWith(FRIEND) }
    } else {
        (invites + requests).associate { it.first to 0 }
    }
    return NoticePlan(notices, turns.associate { it.id to it.version } + friendKeys)
}
