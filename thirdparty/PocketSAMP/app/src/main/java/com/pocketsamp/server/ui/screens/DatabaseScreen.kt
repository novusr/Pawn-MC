package com.pocketsamp.server.ui.screens

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pocketsamp.server.PocketSampLocales
import com.pocketsamp.server.db.DatabaseManager
import com.pocketsamp.server.db.QueryResult
import com.pocketsamp.server.ui.theme.*
import kotlinx.coroutines.launch

/**
 * Local MariaDB control surface.
 *
 * Inputs come from [DatabaseManager] (list/create databases, list tables, run SQL,
 * import scripts). [onOpenPhpMyAdmin] opens the local phpMyAdmin in the built-in
 * browser, so the two ways of touching the same server — queries here, the full web
 * UI there — are one tap apart.
 */
@Composable
fun DatabaseScreen(
    dbManager: DatabaseManager,
    isDbRunning: Boolean,
    onOpenPhpMyAdmin: () -> Unit = {},
    lang: PocketSampLocales.Lang = PocketSampLocales.current
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current

    fun t(key: String): String = PocketSampLocales.text(lang, key)

    var databases by remember { mutableStateOf<List<String>>(emptyList()) }
    var selectedDb by remember { mutableStateOf<String?>(null) }
    var tables by remember { mutableStateOf<List<String>>(emptyList()) }

    var sqlQuery by remember { mutableStateOf("SHOW TABLES;") }
    var queryResult by remember { mutableStateOf<QueryResult?>(null) }
    var isExecuting by remember { mutableStateOf(false) }

    var showCreateDialog by remember { mutableStateOf(false) }
    var newDbName by remember { mutableStateOf("") }
    var isCreating by remember { mutableStateOf(false) }
    // Name of the database the user just created, echoed in a confirmation banner
    // above the chip row until the next creation or a database switch.
    var lastCreatedDb by remember { mutableStateOf<String?>(null) }

    fun refreshDbs() {
        if (!isDbRunning) return
        scope.launch {
            databases = dbManager.listDatabases()
            if (selectedDb == null && databases.isNotEmpty()) {
                selectedDb = databases.first()
            }
            selectedDb?.let { db ->
                tables = dbManager.listTables(db)
            }
        }
    }

    fun runQuery() {
        if (sqlQuery.isBlank() || isExecuting) return
        focusManager.clearFocus()
        scope.launch {
            isExecuting = true
            queryResult = dbManager.executeQuery(selectedDb, sqlQuery)
            isExecuting = false
            // A statement may have created or dropped a table, so the table count
            // in the header is refreshed alongside the result.
            selectedDb?.let { db -> tables = dbManager.listTables(db) }
        }
    }

    LaunchedEffect(Unit) {
        snapshotFlow { isDbRunning }
            .distinctUntilChanged()
            .collect { running -> if (running) refreshDbs() }
    }

    LaunchedEffect(isDbRunning) {
        if (isDbRunning) {
            refreshDbs()
        }
    }

    // SQL File Import Launcher: opens the system document picker so a chosen
    // .sql script can be imported into the selected database.
    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null && selectedDb != null) {
            scope.launch {
                try {
                    context.contentResolver.openInputStream(uri)?.use { inputStream ->
                        Toast.makeText(context, t("db.importing"), Toast.LENGTH_SHORT).show()
                        val (success, msg) = dbManager.importSql(selectedDb!!, inputStream)
                        Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                        if (success) {
                            tables = dbManager.listTables(selectedDb!!)
                        }
                    }
                } catch (e: Exception) {
                    Toast.makeText(context, "${t("db.import.error")}: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        } else if (selectedDb == null) {
            Toast.makeText(context, t("db.select.first"), Toast.LENGTH_SHORT).show()
        }
    }

    // The whole screen is one vertical scroll. The query result used to be the only
    // flexible child, so on a short screen the header, the chips and the SQL field
    // were squeezed and clipped; laying them out at their natural height and letting
    // the page scroll keeps every control usable.
    val pageScroll = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SampThemeColors.bg)
            .verticalScroll(pageScroll)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = t("db.title"),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = SampThemeColors.textPrimary,
                    maxLines = 1
                )
                Text(
                    text = if (isDbRunning) t("db.connected") else t("db.offline"),
                    fontSize = 12.sp,
                    color = if (isDbRunning) SampThemeColors.success else SampThemeColors.danger,
                    maxLines = 1
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                // Refresh Button
                IconButton(
                    onClick = { refreshDbs() },
                    enabled = isDbRunning,
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(SampThemeColors.card)
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = t("db.refresh"),
                        tint = if (isDbRunning) SampThemeColors.accent else SampThemeColors.textMuted,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        if (!isDbRunning) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(SampThemeColors.card)
                    .padding(18.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = t("db.offline.hint"),
                    fontSize = 13.sp,
                    color = SampThemeColors.textMuted
                )
                // MariaDB is optional and can stay down even with the SA-MP server
                // running (the shipped asset bundle has no daemon). Say so here, and
                // offer the console, where the engine logs the exact reason.
                Text(
                    text = t("db.unavailable.hint"),
                    fontSize = 12.sp,
                    color = SampThemeColors.textMuted
                )
            }
        } else {
            // Confirmation for the database the user just created: the name the
            // server accepted is echoed back above the chip row.
            lastCreatedDb?.let { created ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(SampThemeColors.success.copy(alpha = 0.12f))
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = SampThemeColors.success,
                        modifier = Modifier.size(18.dp)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = t("db.created"),
                            fontSize = 12.sp,
                            color = SampThemeColors.textSecondary,
                            maxLines = 1
                        )
                        Text(
                            text = created,
                            fontSize = 14.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = SampThemeColors.success,
                            maxLines = 1
                        )
                    }
                    TextButton(onClick = { lastCreatedDb = null }) {
                        Text(t("perm.close"), fontSize = 12.sp, color = SampThemeColors.textMuted)
                    }
                }
            }

            // Database Selector Chips
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(databases) { db ->
                    val isSelected = db == selectedDb
                    FilterChip(
                        selected = isSelected,
                        onClick = {
                            selectedDb = db
                            lastCreatedDb = null
                            scope.launch { tables = dbManager.listTables(db) }
                        },
                        label = { Text(db, fontSize = 12.sp, maxLines = 1) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = SampThemeColors.accent,
                            selectedLabelColor = SampThemeColors.bg,
                            containerColor = SampThemeColors.card,
                            labelColor = SampThemeColors.textPrimary
                        ),
                        modifier = Modifier.heightIn(max = 34.dp)
                    )
                }
                // Create database: sits in the empty lane after the last chip so
                // the empty area itself becomes the affordance, like the "add"
                // entry at the end of the workspace chip row.
                item {
                    FilterChip(
                        selected = false,
                        onClick = { showCreateDialog = true },
                        label = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = null,
                                    tint = SampThemeColors.success,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = t("db.new"),
                                    fontSize = 12.sp,
                                    color = SampThemeColors.textPrimary,
                                    modifier = Modifier.padding(start = 4.dp)
                                )
                            }
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            containerColor = SampThemeColors.card,
                            labelColor = SampThemeColors.textPrimary
                        ),
                        modifier = Modifier.heightIn(max = 34.dp)
                    )
                }
                // phpMyAdmin sits next to the database list because it is another
                // view of the same server, not a separate tool.
                item {
                    FilterChip(
                        selected = false,
                        onClick = onOpenPhpMyAdmin,
                        label = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.OpenInBrowser,
                                    contentDescription = null,
                                    tint = SampThemeColors.accent,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = t("db.phpmyadmin"),
                                    fontSize = 12.sp,
                                    color = SampThemeColors.textPrimary,
                                    modifier = Modifier.padding(start = 4.dp)
                                )
                            }
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            containerColor = SampThemeColors.card,
                            labelColor = SampThemeColors.textPrimary
                        ),
                        modifier = Modifier.heightIn(max = 34.dp)
                    )
                }
            }

            // Selected database summary: name, table count, and SQL import.
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = selectedDb ?: t("db.none"),
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    color = SampThemeColors.accent,
                    maxLines = 1,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = t("db.tables").replace("{count}", tables.size.toString()),
                    fontSize = 12.sp,
                    color = SampThemeColors.textSecondary,
                    maxLines = 1
                )
                TextButton(
                    onClick = { importLauncher.launch(arrayOf("*/*")) },
                    enabled = selectedDb != null,
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)
                ) {
                    Text(t("db.import"), fontSize = 12.sp, color = SampThemeColors.accent)
                }
            }

            // SQL Query Input Box
            OutlinedTextField(
                value = sqlQuery,
                onValueChange = { sqlQuery = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(t("sql.query"), fontSize = 13.sp) },
                textStyle = LocalTextStyle.current.copy(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp,
                    color = SampThemeColors.textPrimary
                ),
                maxLines = 4,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { runQuery() }),
                trailingIcon = {
                    IconButton(
                        onClick = { runQuery() },
                        enabled = !isExecuting && sqlQuery.isNotBlank()
                    ) {
                        if (isExecuting) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = t("sql.run"),
                                tint = SampThemeColors.accent
                            )
                        }
                    }
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = SampThemeColors.accent,
                    unfocusedBorderColor = SampThemeColors.cardBorder,
                    focusedContainerColor = SampThemeColors.card,
                    unfocusedContainerColor = SampThemeColors.card
                ),
                shape = RoundedCornerShape(12.dp)
            )

            // Result Area. Errors and OK messages are plain rows; a SELECT fills a
            // bounded, independently scrollable table so a long result cannot push
            // the query field off the page.
            queryResult?.let { result ->
                when {
                    result.error != null -> Text(
                        text = "${t("sql.error")}: ${result.error}",
                        color = SampThemeColors.danger,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp
                    )
                    !result.isSelect -> Text(
                        text = t("sql.ok").replace("{count}", result.affectedRows.toString()),
                        color = SampThemeColors.success,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp
                    )
                    else -> Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 60.dp, max = RESULT_MAX_HEIGHT)
                            .clip(RoundedCornerShape(12.dp))
                            .background(SampThemeColors.terminal)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState())
                                .padding(8.dp)
                        ) {
                            LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                // Headers
                                item {
                                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                        result.columns.forEach { col ->
                                            Text(
                                                text = col,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 11.sp,
                                                fontFamily = FontFamily.Monospace,
                                                color = SampThemeColors.accent,
                                                maxLines = 1,
                                                modifier = Modifier.widthIn(min = 70.dp)
                                            )
                                        }
                                    }
                                    HorizontalDivider(
                                        modifier = Modifier.padding(vertical = 4.dp),
                                        color = SampThemeColors.cardBorder
                                    )
                                }

                                // Rows
                                items(result.rows) { row ->
                                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                        row.forEach { cell ->
                                            Text(
                                                text = cell,
                                                fontSize = 11.sp,
                                                fontFamily = FontFamily.Monospace,
                                                color = SampThemeColors.textSecondary,
                                                maxLines = 1,
                                                modifier = Modifier.widthIn(min = 70.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Direct launch: phpMyAdmin in the built-in full-screen browser.
            Button(
                onClick = onOpenPhpMyAdmin,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = SampThemeColors.accent,
                    contentColor = SampThemeColors.accentOn
                ),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.OpenInBrowser,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = t("db.open.phpmyadmin"),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
            }

            // Bottom room so the floating tab rail never covers the launch button.
            Spacer(modifier = Modifier.height(56.dp))
        }
    }

    // Create Database dialog: the "+ New" chip opens it; the name goes straight
    // to MariaDB's CREATE DATABASE and the accepted name is echoed back in the
    // confirmation banner above the chip row.
    if (showCreateDialog) {
        val canCreate = newDbName.trim().matches(Regex("[A-Za-z0-9_]+")) && !isCreating
        fun create() {
            val name = newDbName.trim()
            if (!canCreate) return
            scope.launch {
                isCreating = true
                val (success, msg) = dbManager.createDatabase(name)
                isCreating = false
                Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                if (success) {
                    // Select the new database immediately and show its name, then
                    // refresh the chip row so it is there on the next open too.
                    selectedDb = name
                    databases = dbManager.listDatabases()
                    tables = dbManager.listTables(name)
                    lastCreatedDb = name
                    showCreateDialog = false
                    newDbName = ""
                }
            }
        }
        AlertDialog(
            onDismissRequest = { if (!isCreating) showCreateDialog = false },
            containerColor = SampThemeColors.card,
            titleContentColor = SampThemeColors.textPrimary,
            textContentColor = SampThemeColors.textSecondary,
            title = {
                Text(t("db.new.title"), fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = t("db.new.desc"),
                        fontSize = 12.sp,
                        color = SampThemeColors.textSecondary
                    )
                    OutlinedTextField(
                        value = newDbName,
                        onValueChange = { newDbName = it.take(64) },
                        singleLine = true,
                        enabled = !isCreating,
                        label = { Text(t("db.new.name"), fontSize = 13.sp) },
                        textStyle = LocalTextStyle.current.copy(
                            fontSize = 14.sp,
                            fontFamily = FontFamily.Monospace,
                            color = SampThemeColors.textPrimary
                        ),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { create() }),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = SampThemeColors.accent,
                            unfocusedBorderColor = SampThemeColors.cardBorder,
                            focusedContainerColor = SampThemeColors.bg,
                            unfocusedContainerColor = SampThemeColors.bg
                        ),
                        shape = RoundedCornerShape(10.dp)
                    )
                    Text(
                        text = t("db.new.rule"),
                        fontSize = 11.sp,
                        color = SampThemeColors.textMuted
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { create() },
                    enabled = canCreate
                ) {
                    if (isCreating) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    } else {
                        Text(
                            t("db.new.create"),
                            fontSize = 14.sp,
                            color = SampThemeColors.accent,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateDialog = false }, enabled = !isCreating) {
                    Text(t("perm.close"), color = SampThemeColors.textSecondary)
                }
            }
        )
    }
}

/** Height cap for the SELECT result table, so it never swallows the whole page. */
private val RESULT_MAX_HEIGHT = 260.dp

