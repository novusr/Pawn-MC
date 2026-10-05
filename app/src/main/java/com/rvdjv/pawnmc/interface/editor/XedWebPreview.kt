package com.rvdjv.pawnmc.`interface`.editor

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import kotlin.math.roundToInt

/**
 * A preview target the user can render inside the editor without leaving the app.
 */
internal data class XedPreviewSite(
    val label: String,
    val url: String,
    /**
     * Whether the page should be opened in the system browser instead of the preview.
     *
     * AI Studio serves a normal document, while ChatGPT and Copilot are client side
     * apps that regularly refuse to boot inside an embedded WebView (their bot
     * detection reacts to the WebView user agent). Those entries are handed to the
     * system browser, which is the only dependable way to open them.
     */
    val requiresExternalBrowser: Boolean = false
)

internal object XedPreviewSites {
    val all: List<XedPreviewSite> = listOf(
        XedPreviewSite("Google AI Studio", "https://aistudio.google.com/apps"),
        XedPreviewSite("ChatGPT", "https://chatgpt.com/", requiresExternalBrowser = true),
        XedPreviewSite("Microsoft Copilot", "https://copilot.microsoft.com/", requiresExternalBrowser = true),
        XedPreviewSite("Groq", "https://console.groq.com/playground", requiresExternalBrowser = true)
    )

    /**
     * Desktop Chrome user agent.
     *
     * The stock WebView user agent carries the `; wv` token and the WebView build
     * number, and several hosted front ends simply refuse to answer such a client.
     * Presenting a current desktop Chrome string gives the page the best chance of
     * rendering inside the preview box.
     */
    const val DESKTOP_USER_AGENT: String =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) " +
            "Chrome/131.0.0.0 Safari/537.36"
}

/**
 * Small embedded browser shown over the editor.
 *
 * The first step is a picker dialog so the user decides which site is rendered,
 * afterwards the chosen page is displayed inside a draggable preview box.
 */
@Composable
internal fun XedWebPreviewOverlay(
    sites: List<XedPreviewSite> = XedPreviewSites.all,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    var selected by remember { mutableStateOf<XedPreviewSite?>(null) }

    if (selected == null) {
        XedPreviewSitePicker(
            sites = sites,
            onPick = { site ->
                if (site.requiresExternalBrowser) {
                    // ChatGPT and Copilot refuse to boot inside a WebView, so the
                    // picker stays open and the page is handed to the browser.
                    context.openInExternalBrowser(site.url)
                } else {
                    selected = site
                }
            },
            onClose = onClose
        )
    } else {
        XedWebPreviewBox(site = selected!!, onClose = onClose)
    }
}

/**
 * Hands [url] to the system browser.
 *
 * `ACTION_VIEW` is used instead of a Custom Tab because the browser support library
 * is not a dependency of this app. The page opens in whichever browser the user has
 * installed, and the editor stays alive in the back stack.
 */
private fun Context.openInExternalBrowser(url: String) {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    runCatching { startActivity(intent) }
}

/**
 * Dialog listing every preview target; picking one starts the embedded browser.
 */
@Composable
private fun XedPreviewSitePicker(
    sites: List<XedPreviewSite>,
    onPick: (XedPreviewSite) -> Unit,
    onClose: () -> Unit
) {
    BackHandler(enabled = true, onBack = onClose)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .zIndex(30f)
            .background(Color.Black.copy(alpha = 0.45f))
            .pointerInput(Unit) { detectTapGestures { } }
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            shadowElevation = 12.dp,
            modifier = Modifier
                .align(Alignment.Center)
                .padding(24.dp)
                .fillMaxWidth()
                .widthIn(max = 420.dp)
                .testTag("xed_web_preview_picker")
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Preview situs",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = onClose, modifier = Modifier.size(40.dp)) {
                        Icon(Icons.Filled.Close, contentDescription = "Close")
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Text(
                    text = "Pilih situs yang akan ditampilkan di preview.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 10.dp)
                )
                sites.forEach { site ->
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clickable { onPick(site) }
                            .testTag("xed_web_preview_pick_${site.label}")
                    ) {
                        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = site.label,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.weight(1f)
                                )
                                if (site.requiresExternalBrowser) {
                                    Text(
                                        text = "Browser",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Text(
                                text = site.url,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Draggable preview window hosting a [WebView].
 *
 * The header row acts as the drag handle, the page itself keeps its normal
 * scrolling so long documents remain usable.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun XedWebPreviewBox(
    site: XedPreviewSite,
    onClose: () -> Unit
) {
    val density = LocalDensity.current
    var dragOffset by remember(site) { mutableStateOf(IntOffset.Zero) }
    val currentDragOffset = rememberUpdatedState(dragOffset)
    var progress by remember(site) { mutableStateOf(0) }
    var currentUrl by remember(site) { mutableStateOf(site.url) }
    // Held so the header can ask for a reload even before the view exists.
    var webView by remember(site) { mutableStateOf<WebView?>(null) }
    var reloadRequested by remember(site) { mutableStateOf(false) }

    LaunchedEffect(reloadRequested) {
        if (reloadRequested) {
            webView?.reload()
            reloadRequested = false
        }
    }

    BackHandler(enabled = true, onBack = onClose)

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .zIndex(30f)
            .testTag("xed_web_preview")
    ) {
        val horizontalLimit = with(density) { (maxWidth / 2f - 36.dp).toPx().coerceAtLeast(0f) }
        val verticalLimit = with(density) { (maxHeight / 2f - 48.dp).toPx().coerceAtLeast(0f) }
        val limitedDragOffset = IntOffset(
            dragOffset.x.coerceIn(-horizontalLimit.roundToInt(), horizontalLimit.roundToInt()),
            dragOffset.y.coerceIn(-verticalLimit.roundToInt(), verticalLimit.roundToInt())
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.12f))
                .pointerInput(Unit) { detectTapGestures { } }
        )

        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            shadowElevation = 12.dp,
            modifier = Modifier
                .align(Alignment.Center)
                .offset { limitedDragOffset }
                .fillMaxWidth(0.94f)
                .widthIn(max = 720.dp)
                .fillMaxHeight(0.86f)
                .heightIn(min = 240.dp)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .pointerInput(Unit) {
                            detectDragGestures { change, dragAmount ->
                                change.consume()
                                dragOffset = IntOffset(
                                    currentDragOffset.value.x + dragAmount.x.roundToInt(),
                                    currentDragOffset.value.y + dragAmount.y.roundToInt()
                                )
                            }
                        },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
                        Text(
                            text = site.label,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = currentUrl,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    if (progress in 1..99) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        val reload = { webView?.reload() }
                        IconButton(
                            onClick = {
                                progress = 10
                                if (webView == null) reloadRequested = true else reload()
                            },
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(Icons.Filled.Refresh, contentDescription = "Reload page")
                        }
                    }
                    IconButton(onClick = onClose, modifier = Modifier.size(40.dp)) {
                        Icon(Icons.Filled.Close, contentDescription = "Close")
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                AndroidView(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .testTag("xed_web_preview_view"),
                    factory = { context ->
                        WebView(context).also { created -> webView = created }.apply {
                            setBackgroundColor(android.graphics.Color.WHITE)
                            settings.apply {
                                javaScriptEnabled = true
                                domStorageEnabled = true
                                // The AI front ends reject the stock WebView agent
                                // (it carries the `; wv` token), so the desktop
                                // Chrome string is presented instead.
                                userAgentString = XedPreviewSites.DESKTOP_USER_AGENT
                                loadWithOverviewMode = true
                                useWideViewPort = true
                                builtInZoomControls = true
                                displayZoomControls = false
                                cacheMode = WebSettings.LOAD_DEFAULT
                                mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                                mediaPlaybackRequiresUserGesture = false
                            }
                            CookieManager.getInstance().setAcceptCookie(true)
                            CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                            webChromeClient = object : WebChromeClient() {
                                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                    progress = newProgress
                                }
                            }
                            webViewClient = object : WebViewClient() {
                                override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                    progress = 10
                                    url?.let { currentUrl = it }
                                }

                                override fun onPageFinished(view: WebView?, url: String?) {
                                    progress = 100
                                    url?.let { currentUrl = it }
                                }

                                override fun onReceivedError(
                                    view: WebView?,
                                    request: WebResourceRequest?,
                                    error: WebResourceError?
                                ) {
                                    // A blocked or failed request must not leave the
                                    // spinner running forever.
                                    progress = 100
                                }
                            }
                            loadUrl(site.url)
                        }
                    },
                    onRelease = { released ->
                        released.stopLoading()
                        released.destroy()
                        webView = null
                    }
                )
            }
        }
    }
}
