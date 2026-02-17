package com.rvdjv.pawnmc

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.DocumentsContract
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.RadioGroup
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.android.material.textfield.TextInputEditText

class SettingsActivity : AppCompatActivity() {

    private lateinit var config: CompilerConfig

    private lateinit var rgCompilerVersion: RadioGroup
    private lateinit var rgDebug: RadioGroup
    private lateinit var switchSemicolons: SwitchMaterial
    private lateinit var switchParentheses: SwitchMaterial
    private lateinit var switchSampCompat: SwitchMaterial
    private lateinit var etCustomFlags: TextInputEditText
    private lateinit var llIncludePaths: LinearLayout
    private lateinit var tvEmptyPaths: TextView
    private lateinit var btnAddIncludePath: MaterialButton
    private lateinit var tvAppVersion: TextView
    private lateinit var tvBuildNumber: TextView
    private lateinit var btnGitHub: MaterialButton

    private val includePaths = mutableListOf<String>()

    // Fflder picker
    private val folderPickerLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        uri?.let { handleSelectedFolder(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        setupToolbar()
        config = CompilerConfig(this)

        initViews()
        loadSettings()
        setupListeners()
    }

    private fun setupToolbar() {
        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        toolbar.setNavigationOnClickListener {
            config.customFlags = etCustomFlags.text?.toString() ?: ""
            finish()
        }
    }

    private fun initViews() {
        rgCompilerVersion = findViewById(R.id.rgCompilerVersion)
        rgDebug = findViewById(R.id.rgDebug)
        switchSemicolons = findViewById(R.id.switchSemicolons)
        switchParentheses = findViewById(R.id.switchParentheses)
        switchSampCompat = findViewById(R.id.switchSampCompat)
        etCustomFlags = findViewById(R.id.etCustomFlags)
        llIncludePaths = findViewById(R.id.llIncludePaths)
        tvEmptyPaths = findViewById(R.id.tvEmptyPaths)
        btnAddIncludePath = findViewById(R.id.btnAddIncludePath)
        tvAppVersion = findViewById(R.id.tvAppVersion)
        tvBuildNumber = findViewById(R.id.tvBuildNumber)
        btnGitHub = findViewById(R.id.btnGitHub)
    }

    private fun loadSettings() {
        // compiler version
        when (config.compilerVersion) {
            CompilerConfig.CompilerVersion.V3107 -> rgCompilerVersion.check(R.id.rbV3107)
            CompilerConfig.CompilerVersion.V31011 -> rgCompilerVersion.check(R.id.rbV31011)
        }

        // debug
        when (config.debugLevel) {
            CompilerConfig.DebugLevel.D0 -> rgDebug.check(R.id.rbD0)
            CompilerConfig.DebugLevel.D1 -> rgDebug.check(R.id.rbD1)
            CompilerConfig.DebugLevel.D2 -> rgDebug.check(R.id.rbD2)
            CompilerConfig.DebugLevel.D3 -> rgDebug.check(R.id.rbD3)
        }

        // switches
        switchSemicolons.isChecked = config.mandatorySemicolons
        switchParentheses.isChecked = config.mandatoryParentheses
        switchSampCompat.isChecked = config.sampCompatibility

        // custom flags
        etCustomFlags.setText(config.customFlags)

        // include paths
        includePaths.clear()
        includePaths.addAll(config.includePaths)
        refreshIncludePathsUI()

        // about
        val packageInfo = packageManager.getPackageInfo(packageName, 0)
        tvAppVersion.text = "v${packageInfo.versionName}"
        tvBuildNumber.text = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            packageInfo.longVersionCode.toString()
        } else {
            @Suppress("DEPRECATION")
            packageInfo.versionCode.toString()
        }
    }

    private fun setupListeners() {
        rgCompilerVersion.setOnCheckedChangeListener { _, checkedId ->
            val newVersion = when (checkedId) {
                R.id.rbV3107 -> CompilerConfig.CompilerVersion.V3107
                R.id.rbV31011 -> CompilerConfig.CompilerVersion.V31011
                else -> CompilerConfig.CompilerVersion.V31011
            }
            
            config.compilerVersion = newVersion
            
            if (PawnCompiler.isRestartRequired(newVersion)) {
                showRestartDialog(newVersion)
            }
        }



        rgDebug.setOnCheckedChangeListener { _, checkedId ->
            config.debugLevel = when (checkedId) {
                R.id.rbD0 -> CompilerConfig.DebugLevel.D0
                R.id.rbD1 -> CompilerConfig.DebugLevel.D1
                R.id.rbD2 -> CompilerConfig.DebugLevel.D2
                R.id.rbD3 -> CompilerConfig.DebugLevel.D3
                else -> CompilerConfig.DebugLevel.D1
            }
        }

        switchSemicolons.setOnCheckedChangeListener { _, isChecked ->
            config.mandatorySemicolons = isChecked
        }

        switchParentheses.setOnCheckedChangeListener { _, isChecked ->
            config.mandatoryParentheses = isChecked
        }

        switchSampCompat.setOnCheckedChangeListener { _, isChecked ->
            config.sampCompatibility = isChecked
        }

        etCustomFlags.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                config.customFlags = etCustomFlags.text?.toString() ?: ""
            }
        }

        btnAddIncludePath.setOnClickListener {
            folderPickerLauncher.launch(null)
        }

        btnGitHub.setOnClickListener {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/novusr/Pawn-MC"))
            startActivity(intent)
        }
    }

    private fun handleSelectedFolder(uri: Uri) {
        val path = getPathFromTreeUri(uri)
        if (path != null && path !in includePaths) {
            includePaths.add(path)
            config.includePaths = includePaths
            refreshIncludePathsUI()
        }
    }

    private fun getPathFromTreeUri(uri: Uri): String? {
        try {
            val docId = DocumentsContract.getTreeDocumentId(uri)
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
                            if (java.io.File(testPath).exists()) {
                                return testPath
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) { }
        return null
    }

    private fun refreshIncludePathsUI() {
        llIncludePaths.removeAllViews()
        // show/hide
        if (includePaths.isEmpty()) {
            tvEmptyPaths.visibility = View.VISIBLE
        } else {
            tvEmptyPaths.visibility = View.GONE
        }

        for (path in includePaths) {
            val itemView = LayoutInflater.from(this)
                .inflate(R.layout.item_include_path, llIncludePaths, false)

            itemView.findViewById<TextView>(R.id.tvPath).text = path
            itemView.findViewById<ImageButton>(R.id.btnRemove).setOnClickListener {
                includePaths.remove(path)
                config.includePaths = includePaths
                refreshIncludePathsUI()
            }

            llIncludePaths.addView(itemView)
        }
    }

    override fun onPause() {
        super.onPause()
        // save custom flags
        config.customFlags = etCustomFlags.text?.toString() ?: ""
    }

    private fun showRestartDialog(newVersion: CompilerConfig.CompilerVersion) {
        val currentVersion = PawnCompiler.getLoadedVersion()
        
        AlertDialog.Builder(this)
            .setTitle("Restart Required")
            .setMessage(
                "Compiler version has been changed from ${currentVersion?.label ?: "unknown"} " +
                "to ${newVersion.label}.\n\n" +
                "Due to Android limitations, the change will take effect after restarting the app.\n\n" +
                "Would you like to restart now?"
            )
            .setPositiveButton("Restart Now") { _, _ ->
                restartApp()
            }
            .setNegativeButton("Later") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    private fun restartApp() {
        val intent = packageManager.getLaunchIntentForPackage(packageName)
        intent?.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(intent)
        finishAffinity()
        Runtime.getRuntime().exit(0)
    }
}
