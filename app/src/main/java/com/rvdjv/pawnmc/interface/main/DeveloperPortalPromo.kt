package com.rvdjv.pawnmc.`interface`.main

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.rvdjv.pawnmc.data.config.AppLocalization
import com.rvdjv.pawnmc.data.config.CompilerConfig
import com.rvdjv.pawnmc.`interface`._Icons

/**
 * The one entry in the settings map that has no setting of its own.
 *
 * [target] is what a tap should focus in Settings. Most rows are a leaf — a switch or a
 * dialog — but the compiler version and the include-path list are sections a user may want
 * to be dropped straight onto, so the id travels with the row instead of being inferred
 * from the position in the list.
 */
data class DeveloperPortalEntry(
    val title: String,
    val description: String,
    val target: SettingsTarget
)

/** Where a [DeveloperPortalEntry] should land the user inside the Settings screen. */
enum class SettingsTarget {
    /** Top of the screen; the general section. */
    General,
    Language,
    CompilerVersion,
    CompilerOptions,
    IncludePaths,
    EditorAppearance,
    Updates,
    About
}

/**
 * Promotional card at the bottom of the main screen: every setting PawnMC has, with a
 * one-line explanation, as a scannable list.
 *
 * The card exists because a first-time user has no way to discover that the app is
 * configurable at all: the main screen is a file picker and a Compile button, and Settings
 * is a single icon in a collapsed corner menu. Rather than a static "look at Settings"
 * banner, each row names a real capability and carries the id of the section it belongs to,
 * so tapping a row opens Settings *at that section* — which is why the Settings screen grew
 * a scroll target in the same change.
 *
 * The whole list scrolls with the main screen rather than inside the card: an inner
 * scrollable would fight the page for the vertical drag, which is the same trap the
 * workspace panel already documents.
 */
@Composable
internal fun DeveloperPortalPromoCard(
    onEntryClick: (SettingsTarget) -> Unit,
    localizer: AppLocalization? = null,
    appLanguage: CompilerConfig.AppLanguage = CompilerConfig.AppLanguage.EN,
    modifier: Modifier = Modifier
) {
    fun text(key: String, fallback: String): String =
        localizer?.get(key, appLanguage, fallback) ?: fallback

    val entries = listOf(
        DeveloperPortalEntry(
            title = text("main.promo.language.title", "Language"),
            description = text(
                "main.promo.language.desc",
                "Indonesian, English, Spanish (Argentina) or Russian for the entire interface."
            ),
            target = SettingsTarget.Language
        ),
        DeveloperPortalEntry(
            title = text("main.promo.compiler.title", "Compiler version"),
            description = text(
                "main.promo.compiler.desc",
                "Build with Pawn 3.10.7 or 3.10.11, and choose whether a pawncc next to your script wins over the bundled one."
            ),
            target = SettingsTarget.CompilerVersion
        ),
        DeveloperPortalEntry(
            title = text("main.promo.options.title", "Compiler options"),
            description = text(
                "main.promo.options.desc",
                "Debug level, optimization, mandatory semicolons and parentheses, plus any extra pawncc flag you need."
            ),
            target = SettingsTarget.CompilerOptions
        ),
        DeveloperPortalEntry(
            title = text("main.promo.includes.title", "Include paths"),
            description = text(
                "main.promo.includes.desc",
                "Point the compiler at your project's include folder; pawno/include and gamemodes are found automatically."
            ),
            target = SettingsTarget.IncludePaths
        ),
        DeveloperPortalEntry(
            title = text("main.promo.ignorecase.title", "Filesystem"),
            description = text(
                "main.promo.ignorecase.desc",
                "Back a folder up and lowercase its names and #include references once, for projects written on Windows."
            ),
            target = SettingsTarget.CompilerOptions
        ),
        DeveloperPortalEntry(
            title = text("main.promo.explain.title", "Explain output"),
            description = text(
                "main.promo.explain.desc",
                "Annotate every warning, error and fatal message with a plain-language explanation."
            ),
            target = SettingsTarget.CompilerOptions
        ),
        DeveloperPortalEntry(
            title = text("main.promo.editor.title", "Editor appearance"),
            description = text(
                "main.promo.editor.desc",
                "Pick the code canvas colour, or let it follow the app theme."
            ),
            target = SettingsTarget.EditorAppearance
        ),
        DeveloperPortalEntry(
            title = text("main.promo.updates.title", "Updates"),
            description = text(
                "main.promo.updates.desc",
                "Check GitHub for a newer release and install it without leaving the app."
            ),
            target = SettingsTarget.Updates
        ),
        DeveloperPortalEntry(
            title = text("main.promo.theme.title", "Theme"),
            description = text(
                "main.promo.theme.desc",
                "Follow the Android system theme, or pin PawnMC to light or dark."
            ),
            target = SettingsTarget.General
        ),
        DeveloperPortalEntry(
            title = text("main.promo.about.title", "About and diagnostics"),
            description = text(
                "main.promo.about.desc",
                "Installed version, source repository, and the built-in self-test suite."
            ),
            target = SettingsTarget.About
        ),
        DeveloperPortalEntry(
            title = text("main.promo.forcestop.title", "Restart the app"),
            description = text(
                "main.promo.forcestop.desc",
                "Reopen PawnMC so a compiler-version change is applied from scratch."
            ),
            target = SettingsTarget.About
        ),
        DeveloperPortalEntry(
            title = text("main.promo.storage.title", "Workspace location"),
            description = text(
                "main.promo.storage.desc",
                "Your settings and starter workspace live in the PawnMC folder on shared storage, so you can edit them outside the app."
            ),
            target = SettingsTarget.About
        )
    )

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = CardShape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(vertical = 14.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = text("main.promo.title", "Everything PawnMC can be configured to do"),
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = text(
                    "main.promo.subtitle",
                    "Tap any entry to open Settings at that section."
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            Spacer(modifier = Modifier.height(10.dp))

            entries.forEach { entry ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickablePromoEntry { onEntryClick(entry.target) }
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Row(
                            modifier = Modifier.size(28.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = _Icons.Settings,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(15.dp)
                            )
                        }
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = entry.title,
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = entry.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontFamily = FontFamily.Default
                        )
                    }
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

/** Shared tap affordance of a promo entry, kept in one place so every row matches. */
private fun Modifier.clickablePromoEntry(onClick: () -> Unit): Modifier =
    this.then(Modifier.clickable(onClick = onClick))
