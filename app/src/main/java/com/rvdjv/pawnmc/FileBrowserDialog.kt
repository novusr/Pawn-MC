package com.rvdjv.pawnmc

import android.app.Dialog
import android.os.Bundle
import android.os.Environment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.fragment.app.DialogFragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import java.io.File
import java.text.DecimalFormat

class FileBrowserDialog : DialogFragment() {

    enum class Mode { FILE, FOLDER }

    interface OnFileSelectedListener {
        fun onFileSelected(path: String)
    }

    interface OnFolderSelectedListener {
        fun onFolderSelected(path: String)
    }

    private var mode = Mode.FILE
    private var fileListener: OnFileSelectedListener? = null
    private var folderListener: OnFolderSelectedListener? = null
    private var fileExtensions: Set<String> = setOf("pwn", "p")

    private lateinit var currentDir: File
    private lateinit var tvBreadcrumb: TextView
    private lateinit var rvFiles: RecyclerView
    private lateinit var layoutEmpty: View
    private lateinit var layoutSelectFolder: View
    private lateinit var btnSelectFolder: MaterialButton
    private lateinit var adapter: FileAdapter

    override fun getTheme(): Int = R.style.Theme_PawnMC

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState)
        dialog.window?.setWindowAnimations(android.R.style.Animation_Dialog)
        return dialog
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.dialog_file_browser, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val toolbar = view.findViewById<MaterialToolbar>(R.id.toolbarBrowser)
        tvBreadcrumb = view.findViewById(R.id.tvBreadcrumb)
        rvFiles = view.findViewById(R.id.rvFiles)
        layoutEmpty = view.findViewById(R.id.layoutEmpty)
        layoutSelectFolder = view.findViewById(R.id.layoutSelectFolder)
        btnSelectFolder = view.findViewById(R.id.btnSelectFolder)

        toolbar.title = if (mode == Mode.FILE) "Select File" else "Select Folder"
        toolbar.setNavigationOnClickListener { dismiss() }

        if (mode == Mode.FOLDER) {
            layoutSelectFolder.visibility = View.VISIBLE
            btnSelectFolder.setOnClickListener {
                folderListener?.onFolderSelected(currentDir.absolutePath)
                dismiss()
            }
        }

        adapter = FileAdapter { entry ->
            if (entry.file.isDirectory) {
                navigateTo(entry.file)
            } else if (mode == Mode.FILE) {
                fileListener?.onFileSelected(entry.file.absolutePath)
                dismiss()
            }
        }

        rvFiles.layoutManager = LinearLayoutManager(requireContext())
        rvFiles.adapter = adapter

        val startPath = savedInstanceState?.getString(KEY_CURRENT_DIR)
            ?: Environment.getExternalStorageDirectory().absolutePath
        currentDir = File(startPath)
        navigateTo(currentDir)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        if (::currentDir.isInitialized) {
            outState.putString(KEY_CURRENT_DIR, currentDir.absolutePath)
        }
    }

    private fun navigateTo(dir: File) {
        currentDir = dir
        tvBreadcrumb.text = dir.absolutePath

        val entries = listEntries(dir)
        adapter.submitList(entries)

        if (entries.isEmpty()) {
            layoutEmpty.visibility = View.VISIBLE
            rvFiles.visibility = View.GONE
        } else {
            layoutEmpty.visibility = View.GONE
            rvFiles.visibility = View.VISIBLE
        }

        (tvBreadcrumb.parent as? android.widget.HorizontalScrollView)?.post {
            (tvBreadcrumb.parent as? android.widget.HorizontalScrollView)?.fullScroll(View.FOCUS_RIGHT)
        }
    }

    private fun listEntries(dir: File): List<FileEntry> {
        val files = dir.listFiles() ?: return emptyList()
        val result = mutableListOf<FileEntry>()

        val parent = dir.parentFile
        if (parent != null && parent.canRead()) {
            result.add(FileEntry(parent, isParent = true))
        }

        val filtered = files
            .filter { !it.name.startsWith(".") }
            .filter { file ->
                if (file.isDirectory) true
                else if (mode == Mode.FILE) {
                    val ext = file.extension.lowercase()
                    ext in fileExtensions
                } else false
            }
            .sortedWith(compareBy<File> { !it.isDirectory }.thenBy { it.name.lowercase() })

        for (f in filtered) {
            result.add(FileEntry(f, isParent = false))
        }

        return result
    }

    data class FileEntry(val file: File, val isParent: Boolean = false)

    private inner class FileAdapter(
        private val onClick: (FileEntry) -> Unit
    ) : RecyclerView.Adapter<FileAdapter.ViewHolder>() {

        private var items: List<FileEntry> = emptyList()

        fun submitList(newItems: List<FileEntry>) {
            items = newItems
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_file_entry, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.bind(items[position])
        }

        override fun getItemCount() = items.size

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            private val cvIcon: com.google.android.material.card.MaterialCardView = view.findViewById(R.id.cvIcon)
            private val ivIcon: ImageView = view.findViewById(R.id.ivIcon)
            private val tvFileName: TextView = view.findViewById(R.id.tvFileName)
            private val tvFileInfo: TextView = view.findViewById(R.id.tvFileInfo)
            private val ivChevron: ImageView = view.findViewById(R.id.ivChevron)

            fun bind(entry: FileEntry) {
                val context = itemView.context
                if (entry.isParent) {
                    cvIcon.setCardBackgroundColor(context.getColor(R.color.surface_elevated))
                    ivIcon.setImageResource(R.drawable.ic_arrow_up)
                    ivIcon.setColorFilter(context.getColor(R.color.text_secondary))
                    
                    tvFileName.text = ".."
                    tvFileInfo.visibility = View.GONE
                    ivChevron.visibility = View.GONE
                } else if (entry.file.isDirectory) {
                    cvIcon.setCardBackgroundColor(context.getColor(R.color.vue_green_surface))
                    ivIcon.setImageResource(R.drawable.ic_folder_open)
                    ivIcon.setColorFilter(context.getColor(R.color.vue_green_primary))
                    
                    tvFileName.text = entry.file.name
                    val count = entry.file.listFiles()?.size ?: 0
                    tvFileInfo.text = "$count items"
                    tvFileInfo.visibility = View.VISIBLE
                    ivChevron.visibility = View.VISIBLE
                } else {
                    cvIcon.setCardBackgroundColor(context.getColor(R.color.surface_elevated))
                    ivIcon.setImageResource(R.drawable.ic_file_code)
                    ivIcon.setColorFilter(context.getColor(R.color.text_secondary))
                    
                    tvFileName.text = entry.file.name
                    tvFileInfo.text = formatFileSize(entry.file.length())
                    tvFileInfo.visibility = View.VISIBLE
                    ivChevron.visibility = View.GONE
                }

                itemView.setOnClickListener { onClick(entry) }
            }
        }
    }

    companion object {
        private const val KEY_CURRENT_DIR = "current_dir"

        fun newFilePickerInstance(
            listener: OnFileSelectedListener,
            extensions: Set<String> = setOf("pwn", "p")
        ): FileBrowserDialog {
            return FileBrowserDialog().apply {
                mode = Mode.FILE
                fileListener = listener
                fileExtensions = extensions
            }
        }

        fun newFolderPickerInstance(listener: OnFolderSelectedListener): FileBrowserDialog {
            return FileBrowserDialog().apply {
                mode = Mode.FOLDER
                folderListener = listener
            }
        }

        fun formatFileSize(size: Long): String {
            if (size <= 0) return "0 B"
            val units = arrayOf("B", "KB", "MB", "GB")
            val digitGroups = (Math.log10(size.toDouble()) / Math.log10(1024.0)).toInt()
            val idx = digitGroups.coerceAtMost(units.size - 1)
            return DecimalFormat("#,##0.#").format(size / Math.pow(1024.0, idx.toDouble())) + " " + units[idx]
        }
    }
}
