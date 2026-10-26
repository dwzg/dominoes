package de.wtzg.domino.feature.learn

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import de.wtzg.domino.engine.Move
import de.wtzg.domino.engine.Puzzle
import de.wtzg.domino.engine.Strategy
import de.wtzg.domino.feature.play.GameViewModel
import de.wtzg.domino.feature.play.SEAT_NAMES
import de.wtzg.domino.ui.components.DominoTile
import de.wtzg.domino.ui.components.TableTarget
import de.wtzg.domino.ui.components.TableView

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PuzzleScreen(
    state: PuzzleUiState,
    onTapTile: (de.wtzg.domino.engine.Tile) -> Unit,
    onAnswer: (Move) -> Unit,
    onNext: () -> Unit,
    onBack: () -> Unit,
) {
    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                title = { Text("Puzzle · ${state.mode.title}") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
            )
        },
    ) { padding ->
        val p = state.puzzle
        if (p == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    CircularProgressIndicator()
                    Text("Setting up a position…")
                }
            }
            return@Scaffold
        }
        val answered = state.answer != null
        val targets = when {
            answered -> listOf(TableTarget(p.best.move, isHint = true))
            state.selected != null -> p.round.legalMoves().filter { it.tile == state.selected }.map { TableTarget(it, false) }
            else -> emptyList()
        }
        BoxWithConstraints(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 12.dp),
        ) {
            val table = @Composable { m: Modifier ->
                TableView(
                    board = p.round.board,
                    targets = targets,
                    onTarget = { if (!answered) onAnswer(it) },
                    halfWidth = if (maxWidth > maxHeight) 7f else 4f,
                    modifier = m,
                )
            }
            if (maxWidth > maxHeight) {
                Row(Modifier.fillMaxSize().padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    table(Modifier.weight(1.4f).fillMaxHeight())
                    Column(
                        Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) { Panel(state, p, onTapTile, onNext) }
                }
            } else {
                Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    table(Modifier.weight(1f).fillMaxWidth().padding(top = 4.dp))
                    Column(
                        Modifier.verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) { Panel(state, p, onTapTile, onNext) }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Panel(state: PuzzleUiState, p: Puzzle, onTapTile: (de.wtzg.domino.engine.Tile) -> Unit, onNext: () -> Unit) {
    val r = p.round
    // What you know: the opponent's tiles, what they've shown they lack, the boneyard.
    val facts = buildList {
        add("${SEAT_NAMES[1]} holds ${r.hands[1].size} tiles. Boneyard: ${r.boneyard.size}.")
        r.lacks[1].takeIf { it.isNotEmpty() }?.let { add("${SEAT_NAMES[1]} has shown no ${GameViewModel.numbers(it.sorted())}.") }
        if (r.rules.mode.scoresDuringPlay) add("The open ends add up to ${r.board.endCount()}.")
    }
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(12.dp)) {
            Text("Your move. What's the best play?", style = MaterialTheme.typography.titleSmall)
            facts.forEach { Text(it, style = MaterialTheme.typography.bodyMedium) }
        }
    }

    state.answer?.let { ans ->
        val good = state.solved == true
        val mine = p.ranked.firstOrNull { it.move == ans }
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = if (good) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.tertiaryContainer,
            ),
        ) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(if (good) "Well played!" else "Not quite", style = MaterialTheme.typography.titleMedium)
                Text("Best: ${Strategy.describe(p.best.move, r.board)}", fontWeight = FontWeight.Bold)
                p.best.pros.take(3).forEach { Text("• $it") }
                if (!good) {
                    Text("Your move: ${Strategy.describe(ans, r.board)}", fontWeight = FontWeight.Bold)
                    mine?.let { m -> whyNot(p, ans, m).forEach { Text("• $it") } }
                }
                Button(onClick = onNext, modifier = Modifier.padding(top = 4.dp)) { Text("Next puzzle") }
            }
        }
    }

    if (state.answer == null && state.selected != null) {
        Text("${state.selected} fits more than one end. Tap a glowing spot on the table.", style = MaterialTheme.typography.bodyMedium)
    }
    val legal = r.legalMoves()
    val hand = r.hands[0]
    val gap = if (hand.size > 9) 4.dp else 6.dp
    BoxWithConstraints(
        Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp),
    ) {
        // Same sizing as the hand in a game: as big as fits on one row, up to the game's maximum.
        val n = hand.size.coerceAtLeast(1)
        val half = ((maxWidth - 1.dp - gap * (n - 1)) / n).coerceIn(18.dp, 44.dp)
        FlowRow(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(gap, Alignment.CenterHorizontally),
            verticalArrangement = Arrangement.spacedBy(gap),
        ) {
            hand.forEach { t ->
                DominoTile(
                    t.a, t.b,
                    half = half,
                    highlighted = t == state.selected || (state.answer != null && t == p.best.move.tile),
                    dimmed = legal.none { it.tile == t },
                    elevation = 2.dp,
                    onClick = { onTapTile(t) },
                )
            }
        }
    }
}

/** Concrete reasons your move was weaker: what it gave up compared with the best one. */
private fun whyNot(p: Puzzle, ans: Move, mine: de.wtzg.domino.engine.Evaluation): List<String> {
    val r = p.round
    val out = mutableListOf<String>()
    val myPoints = r.pointsFor(ans)
    val bestPoints = r.pointsFor(p.best.move)
    if (bestPoints > myPoints) {
        out += if (myPoints == 0) "It scores nothing; the best move scores $bestPoints." else "It scores $myPoints; the best move scores $bestPoints."
    }
    out += mine.cons.take(2)
    if (out.isEmpty()) {
        out += if (ans.tile.pips < p.best.move.tile.pips) {
            "It keeps a heavier tile in your hand than the best move does."
        } else {
            "It's playable, but it leaves you fewer good options than the best move."
        }
    }
    return out
}
