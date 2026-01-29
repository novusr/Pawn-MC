package com.rvdjv.pawnmc

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.Settings
import android.view.View
import android.widget.TextView
import android.widget.ScrollView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.progressindicator.LinearProgressIndicator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class MainActivity : AppCompatActivity() {

    private lateinit var tvSelectedFile: TextView
    private lateinit var tvOutput: TextView
    private lateinit var scrollOutput: ScrollView
    private lateinit var btnSelectFile: MaterialButton
    private lateinit var btnCompile: MaterialButton
    private lateinit var progressBar: LinearProgressIndicator

    private var selectedFilePath: String? = null
    private lateinit var config: CompilerConfig

    // file picker
    private val filePickerLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { handleSelectedFile(it) }
    }

    private val manageStorageLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (hasStoragePermission()) {
            appendOutput("Storage permission granted\n")
        } else {
            appendOutput("Storage permission denied\n")
        }
    }

    private val legacyStoragePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.entries.all { it.value }
        if (allGranted) {
            appendOutput("Storage permission granted\n")
        } else {
            appendOutput("Storage permission denied\n")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initViews()
        setupToolbar()
        setupListeners()
        checkStoragePermission()
        config = CompilerConfig(this)
        appendOutput("Using ${config.compilerVersion.label}\n")
        loadLastSelectedFile()
        handleIncomingIntent()
    }

    private fun setupToolbar() {
        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        toolbar.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.action_settings -> {
                    startActivity(Intent(this, SettingsActivity::class.java))
                    true
                }
                else -> false
            }
        }
    }

    private fun initViews() {
        tvSelectedFile = findViewById(R.id.tvSelectedFile)
        tvOutput = findViewById(R.id.tvOutput)
        scrollOutput = findViewById(R.id.scrollOutput)
        btnSelectFile = findViewById(R.id.btnSelectFile)
        btnCompile = findViewById(R.id.btnCompile)
        progressBar = findViewById(R.id.progressBar)
    }

    private fun setupListeners() {
        btnSelectFile.setOnClickListener {
            filePickerLauncher.launch(arrayOf("*/*"))
        }

        btnCompile.setOnClickListener {
            selectedFilePath?.let { path ->
                compileFile(path)
            }
        }
    }

    private fun checkStoragePermission() {
        if (!hasStoragePermission()) {
            AlertDialog.Builder(this)
                .setTitle("Storage Permission Required")
                .setMessage("This app needs access to all files to compile .pwn files and write .amx output to any location.")
                .setPositiveButton("Grant Permission") { _, _ ->
                    requestStoragePermission()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }

    private fun hasStoragePermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.READ_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun requestStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                data = Uri.parse("package:$packageName")
            }
            manageStorageLauncher.launch(intent)
        } else {
            legacyStoragePermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                )
            )
        }
    }

    private fun handleSelectedFile(uri: Uri) {
        val path = getPathFromUri(uri)
        
        if (path != null && (path.endsWith(".pwn", ignoreCase = true) || path.endsWith(".p", ignoreCase = true))) {
            selectedFilePath = path
            config.lastSelectedFilePath = path
            tvSelectedFile.text = File(path).name
            btnCompile.isEnabled = true
            appendOutput("Selected: $path\n")
        } else if (path != null) {
            tvSelectedFile.text = "Invalid file type"
            btnCompile.isEnabled = false
            appendOutput("Error: Please select a .pwn file\n")
        } else {
            tvSelectedFile.text = "Cannot access file"
            btnCompile.isEnabled = false
            appendOutput("Error: Cannot access file. Make sure storage permission is granted.\n")
        }
    }

    private fun getPathFromUri(uri: Uri): String? {
        if (uri.scheme == "file") {
            return uri.path
        }

        if (uri.scheme == "content") {
            try {
                val docId = DocumentsContract.getDocumentId(uri)
                
                if (docId.startsWith("primary:")) {
                    val relativePath = docId.removePrefix("primary:")
                    return "${Environment.getExternalStorageDirectory().absolutePath}/$relativePath"
                }
                
                if (docId.contains(":")) {
                    val parts = docId.split(":")
                    if (parts.size == 2) {
                        val type = parts[0]
                        val path = parts[1]
                        
                        when (type.lowercase()) {
                            "home" -> return "${Environment.getExternalStorageDirectory().absolutePath}/$path"
                            "downloads" -> return "${Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).absolutePath}/$path"
                            "raw" -> return path
                        }
                        
                        val externalDirs = getExternalFilesDirs(null)
                        for (dir in externalDirs) {
                            if (dir != null) {
                                val root = dir.absolutePath.substringBefore("/Android")
                                val testPath = "$root/$path"
                                if (File(testPath).exists()) {
                                    return testPath
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                // failed
            }

            try {
                contentResolver.query(uri, arrayOf("_data"), null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val index = cursor.getColumnIndex("_data")
                        if (index >= 0) {
                            return cursor.getString(index)
                        }
                    }
                }
            } catch (e: Exception) {
                // failed
            }
        }

        return null
    }

    private fun compileFile(filePath: String) {
        btnCompile.isEnabled = false
        btnSelectFile.isEnabled = false
        progressBar.visibility = View.VISIBLE
        tvOutput.text = ""

        lifecycleScope.launch {
            val options = config.buildOptions()
            val version = config.compilerVersion
            val result = withContext(Dispatchers.IO) {
                PawnCompiler.compile(filePath, options, version)
            }

            progressBar.visibility = View.GONE
            btnCompile.isEnabled = true
            btnSelectFile.isEnabled = true

            appendOutput(result.second)
        }
    }

    private fun loadLastSelectedFile() {
        val lastPath = config.lastSelectedFilePath
        if (lastPath != null && File(lastPath).exists()) {
            selectedFilePath = lastPath
            tvSelectedFile.text = File(lastPath).name
            btnCompile.isEnabled = true
            appendOutput("Loaded file: $lastPath\n")
        }
    }

    private fun handleIncomingIntent() {
        val action = intent.action
        val uri = intent.data

        if (Intent.ACTION_VIEW == action && uri != null) {
            handleSelectedFile(uri)
        }
    }

    private fun appendOutput(text: String) {
        tvOutput.append(text)
        scrollOutput.post {
            scrollOutput.fullScroll(View.FOCUS_DOWN)
        }
    }
}
