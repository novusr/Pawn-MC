package com.pocketsamp.server
import android.content.Context
import android.os.Build
import android.widget.Toast
/** Android Nougat compatibility entry point; Android 8+ uses the embedded PocketSAMP runtime. */
object SampApp {
    fun install(context: Context) = Unit
    fun launch(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            Toast.makeText(context, "PocketSAMP requires Android 8.0 or newer", Toast.LENGTH_LONG).show()
        } else {
            context.startActivity(android.content.Intent(context, MainActivity::class.java).apply {
                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        }
    }
}
