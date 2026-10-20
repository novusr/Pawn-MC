package com.pocketsamp.server.ui.screens

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.pocketsamp.server.db.DatabaseManager
import com.pocketsamp.server.db.QueryResult
import com.pocketsamp.server.ui.theme.*
import kotlinx.coroutines.launch

@Composable
fun DatabaseScreen(
    dbManager: DatabaseManager,
    isDbRunning: Boolean
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var databases by remember { mutableStateOf<List<String>>(emptyList()) }
    var selectedDb by remember { mutableStateOf<String?>(null) }
    var tables by remember { mutableStateOf<List<String>>(emptyList()) }

    var sqlQuery by remember { mutableStateOf("SHOW TABLES;") }
    var queryResult by remember { mutableStateOf<QueryResult?>(null) }
    var isExecuting by remember { mutableStateOf(false) }

    var showCreateDialog by remember { mutableStateOf(false) }
    var newDbName by remember { mutableStateOf(TextFieldValue("")) }
    var isCreating by remember { mutableStateOf(false) }

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
                        Toast.makeText(context, "Importing SQL script...", Toast.LENGTH_SHORT).show()
                        val (success, msg) = dbManager.importSql(selectedDb!!, inputStream)
                        Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                        if (success) {
                            tables = dbManager.listTables(selectedDb!!)
                        }
                    }
                } catch (e: Exception) {
                    Toast.makeText(context, "Import Error: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        } else if (selectedDb == null) {
            Toast.makeText(context, "Select or create a database first!", Toast.LENGTH_SHORT).show()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SampThemeColors.bg)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Database Manager",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = SampThemeColors.textPrimary
                )
                Text(
                    text = if (isDbRunning) "Connected: 127.0.0.1:3306 (root)" else "Database server is offline",
                    fontSize = 13.sp,
                    color = if (isDbRunning) SampThemeColors.success else SampThemeColors.danger
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // Refresh Button
                IconButton(
                    onClick = { refreshDbs() },
                    enabled = isDbRunning,
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(SampThemeColors.card)
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Refresh",
                        tint = SampThemeColors.accent,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        if (!isDbRunning) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(12.dp))
                    .background(SampThemeColors.card)
                    .padding(20.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Start the server on Dashboard to connect to MySQL database.",
                    fontSize = 14.sp,
                    color = SampThemeColors.textMuted
                )
            }
        } else {
            // Database Selector Chips
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(databases) { db ->
                    val isSelected = db == selectedDb
                    FilterChip(
                        selected = isSelected,
                        onClick = {
                            selectedDb = db
                            scope.launch { tables = dbManager.listTables(db) }
                        },
                        label = { Text(db, fontSize = 14.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = SampThemeColors.accent,
                            selectedLabelColor = SampThemeColors.bg,
                            containerColor = SampThemeColors.card,
                            labelColor = SampThemeColors.textPrimary
                        )
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
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = "New",
                                    fontSize = 14.sp,
                                    color = SampThemeColors.textPrimary,
                                    modifier = Modifier.padding(start = 4.dp)
                                )
                            }
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            containerColor = SampThemeColors.card,
                            labelColor = SampThemeColors.textPrimary
                        )
                    )
                }
            }

            // SQL Query Input Box
            OutlinedTextField(
                value = sqlQuery,
                onValueChange = { sqlQuery = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("SQL Query", fontSize = 14.sp) },
                textStyle = LocalTextStyle.current.copy(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 14.sp,
                    color = SampThemeColors.textPrimary
                ),
                maxLines = 3,
                trailingIcon = {
                    IconButton(
                        onClick = {
                            scope.launch {
                                isExecuting = true
                                queryResult = dbManager.executeQuery(selectedDb, sqlQuery)
                                isExecuting = false
                            }
                        },
                        enabled = !isExecuting
                    ) {
                        if (isExecuting) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = "Run",
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

            // Result Area
            queryResult?.let { result ->
                if (result.error != null) {
                    Text(
                        text = "Error: ${result.error}",
                        color = SampThemeColors.danger,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp
                    )
                } else if (!result.isSelect) {
                    Text(
                        text = "Query OK, ${result.affectedRows} rows affected.",
                        color = SampThemeColors.success,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp
                    )
                } else {
                    // Result Table
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(12.dp))
                            .background(SampThemeColors.terminal)
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
                                            fontSize = 12.sp,
                                            fontFamily = FontFamily.Monospace,
                                            color = SampThemeColors.accent,
                                            modifier = Modifier.widthIn(min = 80.dp)
                                        )
                                    }
                                }
                                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), color = SampThemeColors.cardBorder)
                            }

                            // Rows
                            items(result.rows) { row ->
                                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                    row.forEach { cell ->
                                        Text(
                                            text = cell,
                                            fontSize = 12.sp,
                                            fontFamily = FontFamily.Monospace,
                                            color = SampThemeColors.textSecondary,
                                            modifier = Modifier.widthIn(min = 80.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Create Database dialog: the "+ New" chip opens it; the name goes straight
    // to MariaDB's CREATE DATABASE so the new database appears in the chip row
    // and can be selected immediately.
    if (showCreateDialog) {
        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            containerColor = SampThemeColors.card,
            titleContentColor = SampThemeColors.textPrimary,
            textContentColor = SampThemeColors.textSecondary,
            title = { Text("New Database", fontSize = 16.sp, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "The database is created on the running MariaDB server (letters, digits and underscores only).",
                        fontSize = 12.sp,
                        color = SampThemeColors.textSecondary
                    )
                    OutlinedTextField(
                        value = newDbName,
                        onValueChange = { newDbName = it },
                        singleLine = true,
                        label = { Text("Database name", fontSize = 14.sp) },
                        textStyle = LocalTextStyle.current.copy(
                            fontSize = 14.sp,
                            color = SampThemeColors.textPrimary
                        ),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = SampThemeColors.accent,
                            unfocusedBorderColor = SampThemeColors.cardBorder,
                            focusedContainerColor = SampThemeColors.bg,
                            unfocusedContainerColor = SampThemeColors.bg
                        ),
                        shape = RoundedCornerShape(10.dp)
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val name = newDbName.text.trim()
                        if (name.isEmpty() || isCreating) return@TextButton
                        scope.launch {
                            isCreating = true
                            val (success, msg) = dbManager.createDatabase(name)
                            isCreating = false
                            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                            if (success) {
                                selectedDb = name
                                refreshDbs()
                                showCreateDialog = false
                                newDbName = TextFieldValue("")
                            }
                        }
                    },
                    enabled = newDbName.text.isNotBlank() && !isCreating
                ) {
                    if (isCreating) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    } else {
                        Text("Create", fontSize = 14.sp, color = SampThemeColors.accent, fontWeight = FontWeight.SemiBold)
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateDialog = false }) {
                    Text("Cancel", color = SampThemeColors.textSecondary)
                }
            }
        )
    }
}
