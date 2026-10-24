package com.pocketsamp.server.ui.screens

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.pocketsamp.server.Locales
import com.pocketsamp.server.ui.theme.SampThemeColors

/**
 * Local phpMyAdmin URL served by the bundled web stack.
 *
 * phpMyAdmin is a server-side PHP application, so it is reached over loopback the
 * same way the SA-MP server itself is: only pages that need the Android system
 * downloader leave this WebView.
 */
const val LOCAL_PHPMYADMIN_URL = "http://127.0.0.1/phpmyadmin/"

/**
 * Full-screen browser for phpMyAdmin.
 *
 * Deliberately a WebView instead of an external `ACTION_VIEW`: the page is served by
 * the local MariaDB host on loopback, and handing that to another app loses the
 * session cookie and the back stack. [onClose] pops this screen; the system back
 * gesture is routed to the same callback by the host.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun PhpMyAdminScreen(
    url: String = LOCAL_PHPMYADMIN_URL,
    isDbRunning: Boolean,
    onClose: () -> Unit,
    lang: Locales.Lang = Locales.current
) {
    fun t(key: String): String = Locales.text(lang, key)

    var webView by remember { mutableStateOf<WebView?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var currentUrl by remember { mutableStateOf(url) }

    BackHandler(enabled = true) { onClose() }

    // Release the WebView with the composable; a leaked one keeps its renderer
    // process alive for as long as the hosting activity lives.
    DisposableEffect(Unit) {
        onDispose {
            webView?.stopLoading()
            webView?.removeAllViews()
            webView?.destroy()
            webView = null
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SampThemeColors.bg)
    ) {
        // Compact toolbar: back to the Database tab, the URL being shown, reload.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onClose, modifier = Modifier.size(38.dp)) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = t("back.to.pawnmc"),
                    tint = SampThemeColors.textPrimary,
                    modifier = Modifier.size(20.dp)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = t("db.phpmyadmin"),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = SampThemeColors.textPrimary,
                    maxLines = 1
                )
                Text(
                    text = currentUrl,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    color = SampThemeColors.textMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            IconButton(
                onClick = {
                    loadError = null
                    webView?.reload()
                },
                modifier = Modifier.size(38.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = t("db.phpmyadmin.reload"),
                    tint = SampThemeColors.accent,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        if (isLoading) {
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth(),
                color = SampThemeColors.accent
            )
        }

        Box(modifier = Modifier.fillMaxSize()) {
            if (!isDbRunning) {
                // Without MariaDB the page would 404/refuse; explain instead of
                // rendering a blank white WebView.
                Column(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.OpenInBrowser,
                        contentDescription = null,
                        tint = SampThemeColors.textMuted,
                        modifier = Modifier.size(28.dp)
                    )
                    Text(
                        text = t("db.offline.hint"),
                        fontSize = 13.sp,
                        color = SampThemeColors.textMuted
                    )
                }
            } else {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { context ->
                        WebView(context).apply {
                            settings.apply {
                                javaScriptEnabled = true
                                domStorageEnabled = true
                                cacheMode = WebSettings.LOAD_DEFAULT
                                // phpMyAdmin is responsive; fitting the page width
                                // keeps its tables readable on a phone instead of
                                // forcing a zoomed-out desktop layout.
                                useWideViewPort = true
                                loadWithOverviewMode = true
                                builtInZoomControls = true
                                displayZoomControls = false
                            }
                            webViewClient = object : WebViewClient() {
                                override fun onPageStarted(
                                    view: WebView?,
                                    pageUrl: String?,
                                    favicon: Bitmap?
                                ) {
                                    isLoading = true
                                    loadError = null
                                    currentUrl = pageUrl ?: url
                                }

                                override fun onPageFinished(view: WebView?, pageUrl: String?) {
                                    isLoading = false
                                    currentUrl = pageUrl ?: currentUrl
                                }

                                override fun onReceivedError(
                                    view: WebView?,
                                    request: WebResourceRequest?,
                                    error: WebResourceError?
                                ) {
                                    // Only the main frame matters; a missing icon or
                                    // asset must not blank the whole page.
                                    if (request?.isForMainFrame == true) {
                                        isLoading = false
                                        loadError = error?.description?.toString()
                                    }
                                }
                            }
                            loadUrl(url)
                            webView = this
                        }
                    },
                    update = { view ->
                        webView = view
                    }
                )

                loadError?.let { message ->
                    Column(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .clip(RoundedCornerShape(12.dp))
                            .background(SampThemeColors.card)
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = t("db.phpmyadmin.error"),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = SampThemeColors.danger
                        )
                        Text(
                            text = message,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            color = SampThemeColors.textMuted
                        )
                    }
                }
            }
        }
    }
}

