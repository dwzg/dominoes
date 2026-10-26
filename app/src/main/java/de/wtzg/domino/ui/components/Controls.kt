package de.wtzg.domino.ui.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import de.wtzg.domino.engine.GameMode
import de.wtzg.domino.engine.Tile

/** A small heading above a setting, e.g. "Play to". */
@Composable
fun Label(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.titleSmall, modifier = modifier.padding(top = 8.dp))
}

/** Pick one of a few [options], shown as a full-width segmented row. */
@Composable
fun <T> ChoiceRow(
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    enabled: (T) -> Boolean = { true },
) {
    SingleChoiceSegmentedButtonRow(modifier.fillMaxWidth()) {
        options.forEachIndexed { i, option ->
            SegmentedButton(
                selected = option == selected,
                onClick = { onSelect(option) },
                enabled = enabled(option),
                shape = SegmentedButtonDefaults.itemShape(i, options.size),
            ) { Text(label(option)) }
        }
    }
}

/**
 * A switch with its title. The whole row toggles, so the switch is read out with
 * its title and is easy to hit.
 */
@Composable
fun SwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    titleStyle: TextStyle = MaterialTheme.typography.titleSmall,
) {
    Row(
        modifier
            .fillMaxWidth()
            .toggleable(value = checked, onValueChange = onCheckedChange, role = Role.Switch)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = titleStyle)
            subtitle?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Switch(checked = checked, onCheckedChange = null)
    }
}

/** A tappable card that leads somewhere else, with an optional icon and arrow. */
@Composable
fun NavCard(
    title: String,
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerLow,
    icon: ImageVector? = null,
    arrow: Boolean = true,
) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = containerColor, contentColor = contentColorFor(containerColor)),
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            icon?.let { Icon(it, contentDescription = null, modifier = Modifier.padding(end = 14.dp)) }
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(text, style = MaterialTheme.typography.bodyMedium)
            }
            if (arrow) Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
        }
    }
}

/** A short tip in a highlighted card. */
@Composable
fun TipCard(text: String, modifier: Modifier = Modifier) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text("Tip", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            Text(text)
        }
    }
}

/**
 * Tiles in a hand, side by side: as big as fits on one row up to [maxHalf], and only
 * wrapping as a last resort. [tile] draws each one at the given half size.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HandRow(
    tiles: List<Tile>,
    modifier: Modifier = Modifier,
    maxHalf: Dp = 44.dp,
    tile: @Composable (index: Int, tile: Tile, half: Dp) -> Unit,
) {
    val gap = if (tiles.size > 9) 4.dp else 6.dp
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val n = tiles.size.coerceAtLeast(1)
        // 1dp of slack so rounding never pushes the last tile onto a second row.
        val half: Dp = ((maxWidth - 1.dp - gap * (n - 1)) / n).coerceIn(18.dp, maxHalf)
        FlowRow(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(gap, Alignment.CenterHorizontally),
            verticalArrangement = Arrangement.spacedBy(gap),
        ) {
            tiles.forEachIndexed { i, t -> tile(i, t, half) }
        }
    }
}

/** Target scores offered for each game, always including its usual target. Used offline and online. */
fun GameMode.targetPresets(): List<Int> = when (this) {
    GameMode.DRAW, GameMode.BLOCK -> listOf(60, 100, 150)
    GameMode.ALL_FIVES -> listOf(100, 150, 250)
    GameMode.ALL_THREES -> listOf(60, 90, 150)
}

/** The preset targets for [mode] plus a "Custom" chip that shows any other target. */
@Composable
fun TargetChips(mode: GameMode, target: Int, onSelect: (Int) -> Unit, onCustom: () -> Unit, modifier: Modifier = Modifier) {
    val presets = mode.targetPresets()
    Row(modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        presets.forEach { t ->
            FilterChip(
                selected = target == t,
                onClick = { onSelect(t) },
                label = { Text(if (t == mode.defaultTarget) "$t · Standard" else "$t") },
            )
        }
        FilterChip(
            selected = target !in presets,
            onClick = onCustom,
            label = { Text(if (target in presets) "Custom" else "Custom: $target") },
        )
    }
}
