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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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

    LaunchedEffect(isDbRunning) {
        if (isDbRunning) {
            refreshDbs()
        }
    }

    // SQL File Import Launcher
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
            .background(BgDark)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
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
                    color = TextPrimary
                )
                Text(
                    text = if (isDbRunning) "Connected: 127.0.0.1:3306 (root)" else "Database server is offline",
                    fontSize = 12.sp,
                    color = if (isDbRunning) StatusGreen else StatusRed
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // Import .sql Button
                IconButton(
                    onClick = { importLauncher.launch(arrayOf("application/sql", "text/plain", "*/*")) },
                    enabled = isDbRunning,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(SurfaceCard)
                ) {
                    Icon(
                        imageVector = Icons.Default.FileOpen,
                        contentDescription = "Import .sql",
                        tint = AccentOrange,
                        modifier = Modifier.size(18.dp)
                    )
                }

                // Refresh Button
                IconButton(
                    onClick = { refreshDbs() },
                    enabled = isDbRunning,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(SurfaceCard)
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Refresh",
                        tint = AccentCyan,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        if (!isDbRunning) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(12.dp))
                    .background(SurfaceCard)
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Start the server on Dashboard to connect to MySQL database.",
                    fontSize = 13.sp,
                    color = TextMuted
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
                        label = { Text(db, fontSize = 12.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = AccentCyan,
                            selectedLabelColor = BgDark,
                            containerColor = SurfaceCard,
                            labelColor = TextPrimary
                        )
                    )
                }
            }

            // SQL Query Input Box
            OutlinedTextField(
                value = sqlQuery,
                onValueChange = { sqlQuery = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("SQL Query", fontSize = 12.sp) },
                textStyle = LocalTextStyle.current.copy(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    color = TextPrimary
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
                                tint = AccentCyan
                            )
                        }
                    }
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = AccentCyan,
                    unfocusedBorderColor = SurfaceCardBorder,
                    focusedContainerColor = SurfaceCard,
                    unfocusedContainerColor = SurfaceCard
                ),
                shape = RoundedCornerShape(12.dp)
            )

            // Result Area
            queryResult?.let { result ->
                if (result.error != null) {
                    Text(
                        text = "Error: ${result.error}",
                        color = StatusRed,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp
                    )
                } else if (!result.isSelect) {
                    Text(
                        text = "Query OK, ${result.affectedRows} rows affected.",
                        color = StatusGreen,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp
                    )
                } else {
                    // Result Table
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFF07080B))
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
                                            color = AccentCyan,
                                            modifier = Modifier.widthIn(min = 80.dp)
                                        )
                                    }
                                }
                                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), color = SurfaceCardBorder)
                            }

                            // Rows
                            items(result.rows) { row ->
                                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                    row.forEach { cell ->
                                        Text(
                                            text = cell,
                                            fontSize = 11.sp,
                                            fontFamily = FontFamily.Monospace,
                                            color = TextSecondary,
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
}
