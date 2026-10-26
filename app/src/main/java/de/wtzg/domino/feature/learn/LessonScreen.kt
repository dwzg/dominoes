package de.wtzg.domino.feature.learn

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import de.wtzg.domino.engine.GameMode
import de.wtzg.domino.ui.components.DominoTile
import de.wtzg.domino.ui.components.TableView
import de.wtzg.domino.ui.components.TipCard

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun LessonScreen(
    id: String,
    onBack: () -> Unit,
    onOpen: (String) -> Unit,
    onPractice: (GameMode) -> Unit,
    onRead: (String) -> Unit,
) {
    val lesson = Lessons.byId(id) ?: return
    val index = Lessons.all.indexOf(lesson)
    val next = Lessons.all.getOrNull(index + 1)
    LaunchedEffect(id) { onRead(id) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(lesson.title) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(
                Modifier
                    .widthIn(max = 640.dp)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text(
                    lesson.category.title.uppercase(),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                lesson.blocks.forEach { block ->
                    when (block) {
                        is LessonBlock.Text -> Text(block.text, style = MaterialTheme.typography.bodyLarge)
                        is LessonBlock.Hand -> Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                block.tiles.forEach { DominoTile(it.a, it.b, half = 20.dp, elevation = 1.dp) }
                            }
                            Caption(block.caption)
                        }
                        is LessonBlock.Table -> Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            TableView(
                                board = block.board,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(if (block.spinner) 220.dp else 160.dp),
                            )
                            Caption(block.caption)
                        }
                        is LessonBlock.Tip -> TipCard(block.text)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                    lesson.practice?.let { mode ->
                        OutlinedButton(onClick = { onPractice(mode) }) { Text("Practise ${mode.title}") }
                    }
                    next?.let { n -> Button(onClick = { onOpen(n.id) }) { Text("Next lesson") } }
                }
            }
        }
    }
}

@Composable
private fun Caption(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
