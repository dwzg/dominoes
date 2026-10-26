package de.wtzg.domino.feature.learn

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import de.wtzg.domino.data.Stats
import de.wtzg.domino.engine.GameMode
import de.wtzg.domino.ui.components.SectionHeader
import de.wtzg.domino.ui.components.TabTopBar
import de.wtzg.domino.ui.components.TipCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LearnScreen(
    stats: Stats,
    onOpenLesson: (String) -> Unit,
    onPuzzle: (GameMode) -> Unit,
) {
    val tip = remember { Lessons.tips.random() }
    val read = stats.lessonsRead.count { id -> Lessons.all.any { it.id == id } }
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
        topBar = { TabTopBar("Learn", scroll) },
    ) { padding ->
        LazyColumn(
            Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item { PuzzleCard(stats, onPuzzle) }
            item { TipCard(tip) }
            item {
                SectionHeader("Lessons")
                Text(
                    "$read of ${Lessons.all.size} read",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                LinearProgressIndicator(
                    progress = { read.toFloat() / Lessons.all.size },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp, bottom = 4.dp),
                )
            }
            LessonCategory.entries.forEach { cat ->
                val lessons = Lessons.all.filter { it.category == cat }
                if (lessons.isNotEmpty()) {
                    item(key = cat.name) {
                        Text(
                            cat.title,
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 12.dp),
                        )
                    }
                    items(lessons, key = { it.id }) { lesson ->
                        LessonRow(lesson, done = lesson.id in stats.lessonsRead) { onOpenLesson(lesson.id) }
                    }
                }
            }
        }
    }
}

@Composable
private fun PuzzleCard(stats: Stats, onPuzzle: (GameMode) -> Unit) {
    val p = stats.puzzles
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Puzzles: find the best move", style = MaterialTheme.typography.titleMedium)
            Text(
                "Real positions from games between strong players. Pick your move and the coach explains the best one.",
                style = MaterialTheme.typography.bodyMedium,
            )
            if (p.attempted > 0) {
                Text(
                    "Solved ${p.solved} of ${p.attempted} · streak ${p.streak} · best ${p.bestStreak}",
                    style = MaterialTheme.typography.labelLarge,
                )
            }
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GameMode.entries.forEach { m ->
                    AssistChip(onClick = { onPuzzle(m) }, label = { Text(m.title) })
                }
            }
        }
    }
}

@Composable
private fun LessonRow(lesson: Lesson, done: Boolean, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(lesson.title, style = MaterialTheme.typography.titleMedium)
                Text(lesson.summary, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (done) {
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = "Read",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .padding(start = 8.dp)
                        .size(20.dp),
                )
            }
        }
    }
}
