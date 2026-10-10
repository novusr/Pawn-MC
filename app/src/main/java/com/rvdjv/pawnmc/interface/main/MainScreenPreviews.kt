package com.rvdjv.pawnmc.`interface`.main

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.rvdjv.pawnmc.`interface`.theme.PawnMCTheme

/**
 * Compose previews for the main screen's pieces.
 *
 * Kept in their own file so `MainScreen.kt` ends at the end of the screen itself. Having
 * them together also makes the coverage visible at a glance: the compile card in its
 * empty, populated and running states, the log panel idle, and the full screen.
 */
@Preview(showBackground = true)
@Composable
private fun CompileActionCardPreview() {
    PawnMCTheme {
        CompileActionCard(
            selectedFileName = "main.p",
            selectedFileSize = "12.4 KB",
            isCompiling = false,
            onChangeFileClick = {},
            onCompileClick = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun CompileActionCardEmptyPreview() {
    PawnMCTheme {
        CompileActionCard(
            selectedFileName = null,
            selectedFileSize = null,
            isCompiling = false,
            onChangeFileClick = {},
            onCompileClick = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun CompileActionCardCompilingPreview() {
    PawnMCTheme {
        CompileActionCard(
            selectedFileName = "gamemode.pwn",
            selectedFileSize = "48.2 KB",
            isCompiling = true,
            onChangeFileClick = {},
            onCompileClick = {}
        )
    }
}

@Preview(showBackground = true, heightDp = 340)
@Composable
private fun CompilerLogsSectionPreview() {
    PawnMCTheme {
        CompilerLogsSection(
            outputText = "// System ready. Upload a .p file to begin.",
            status = CompileStatus.IDLE,
            scrollState = rememberScrollState(),
            onCopyClick = {}
        )
    }
}

@Preview(showBackground = true, heightDp = 700)
@Composable
private fun MainScreenFullPreview() {
    PawnMCTheme {
        Column(modifier = Modifier.padding(SpaceL)) {
            ScreenHeader(
                onEditorClick = {},
                onSettingsClick = {},
                onSelfTestClick = {},
                actionsVisible = true
            )
            Spacer(modifier = Modifier.height(SpaceL))
            CompileActionCard(
                selectedFileName = "main.p",
                selectedFileSize = "12.4 KB",
                isCompiling = false,
                onChangeFileClick = {},
                onCompileClick = {}
            )
            Spacer(modifier = Modifier.height(SpaceXL))
            CompilerLogsSection(
                outputText = "// System ready. Upload a .p file to begin.",
                status = CompileStatus.IDLE,
                scrollState = rememberScrollState(),
                onCopyClick = {}
            )
        }
    }
}
