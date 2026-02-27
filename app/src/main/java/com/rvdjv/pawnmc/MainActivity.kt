package com.rvdjv.pawnmc

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.view.View
import android.widget.TextView
import android.widget.ScrollView
import android.widget.ImageView
import androidx.activity.result.contract.ActivityResultContracts

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
    private lateinit var btnQuickEdit: ImageView

    private var selectedFilePath: String? = null
    private lateinit var config: CompilerConfig



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
        config = CompilerConfig.getInstance(this)
        appendOutput("Using ${config.compilerVersion.label}\n")
        loadLastSelectedFile()
        handleIncomingIntent()
        checkForAppUpdate()
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
        btnQuickEdit = findViewById(R.id.btnQuickEdit)
    }

    private fun setupListeners() {
        btnSelectFile.setOnClickListener {
            val dialog = FileBrowserDialog.newFilePickerInstance(
                object : FileBrowserDialog.OnFileSelectedListener {
                    override fun onFileSelected(path: String) {
                        handleSelectedFile(path)
                    }
                }
            )
            dialog.show(supportFragmentManager, "file_picker")
        }

        btnCompile.setOnClickListener {
            selectedFilePath?.let { path ->
                compileFile(path)
            }
        }

        btnQuickEdit.setOnClickListener {
            selectedFilePath?.let { path ->
                appendOutput("Opening quick editor for: $path\n")
                // TODO: Launch Editor Activity or Bottom Sheet later
            }
        }
    }

    private fun checkStoragePermission() {
        if (!hasStoragePermission()) {
            PawnDialog(this)
                .setIcon(R.drawable.ic_folder_open)
                .setTitle("Storage Permission Required")
                .setMessage("This app needs access to all files to compile pawn files and write amx output to any location.")
                .setPositiveButton("Grant") {
                    it.dismiss()
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

    private fun handleSelectedFile(path: String) {
        if (path.endsWith(".pwn", ignoreCase = true) || 
            path.endsWith(".p", ignoreCase = true) || 
            path.endsWith(".inc", ignoreCase = true)) {
            selectedFilePath = path
            config.lastSelectedFilePath = path
            tvSelectedFile.text = File(path).name
            btnCompile.isEnabled = true
            btnQuickEdit.visibility = View.VISIBLE
            appendOutput("Selected: $path\n")
        } else {
            tvSelectedFile.text = "Invalid file type"
            btnCompile.isEnabled = false
            btnQuickEdit.visibility = View.GONE
            appendOutput("Error: Please select a pawn file\n")
        }
    }

    private fun compileFile(filePath: String) {
        btnCompile.isEnabled = false
        btnSelectFile.isEnabled = false
        progressBar.visibility = View.VISIBLE
        tvOutput.text = ""

        lifecycleScope.launch {
            val options = config.buildOptions()
            val version = config.compilerVersion
            
            val startTime = System.currentTimeMillis()
            val result = withContext(Dispatchers.IO) {
                PawnCompiler.compile(filePath, options, version)
            }
            val endTime = System.currentTimeMillis()
            val duration = endTime - startTime

            progressBar.visibility = View.GONE
            btnCompile.isEnabled = true
            btnSelectFile.isEnabled = true

            appendOutput(result.second)
            
            val timeString = if (duration >= 1000) {
                String.format("%.2f seconds", duration / 1000.0)
            } else {
                "$duration ms"
            }
            appendOutput("\nCompilation time: $timeString\n")
        }
    }

    private fun loadLastSelectedFile() {
        if (!config.autoLoadLastFile) return

        val lastPath = config.lastSelectedFilePath
        if (lastPath != null && File(lastPath).exists()) {
            selectedFilePath = lastPath
            tvSelectedFile.text = File(lastPath).name
            btnCompile.isEnabled = true
            btnQuickEdit.visibility = View.VISIBLE
            appendOutput("Loaded file: $lastPath\n")
        }
    }

    private fun handleIncomingIntent() {
        val action = intent.action
        val uri = intent.data

        if (Intent.ACTION_VIEW == action && uri != null) {
            val path = uri.path
            if (path != null) {
                handleSelectedFile(path)
            }
        }
    }

    private fun appendOutput(text: String) {
        tvOutput.append(text)
        scrollOutput.post {
            scrollOutput.fullScroll(View.FOCUS_DOWN)
        }
    }

    private fun checkForAppUpdate() {
        lifecycleScope.launch {
            val checker = UpdateChecker(this@MainActivity)
            val result = checker.checkManifest() ?: return@launch

            // Force update
            if (result.forceUpdate && result.updateInfo != null) {
                showForceUpdateDialog(result.updateInfo)
                return@launch
            }

            // Regular update
            val update = result.updateInfo
            if (update != null && update.versionName != checker.getSkippedVersion()) {
                showUpdateDialog(update, checker)
            }
        }
    }

    private fun showForceUpdateDialog(update: UpdateChecker.UpdateInfo) {
        PawnDialog(this)
            .setIcon(R.drawable.ic_warning, R.color.accent_error)
            .setTitle("Update Required")
            .setMessage("Your version is no longer supported. Please update to v${update.versionName} to continue.\n\n${update.changelog}")
            .setCancelable(false)
            .setPositiveButton("Download") {
                it.dismiss()
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(update.downloadUrl)))
            }
            .show()
    }

    private fun showUpdateDialog(update: UpdateChecker.UpdateInfo, checker: UpdateChecker) {
        PawnDialog(this)
            .setIcon(R.drawable.ic_info, R.color.accent_info)
            .setTitle("Update Available \u2014 v${update.versionName}")
            .setMessage(update.changelog)
            .setPositiveButton("Download") {
                it.dismiss()
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(update.downloadUrl)))
            }
            .setNegativeButton("Later", null)
            .setNeutralButton("Skip Version") {
                it.dismiss()
                checker.setSkippedVersion(update.versionName)
            }
            .show()
    }
}
