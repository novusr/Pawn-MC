package com.pocketsamp.server.user_interface.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pocketsamp.server.Locales
import com.pocketsamp.server.user_interface.theme.SampThemeColors

/**
 * Local phpMyAdmin URL.
 *
 * phpMyAdmin is a server-side PHP application: it needs a PHP-capable web server
 * in front of MariaDB. This build ships the MariaDB daemon and the in-app SQL
 * client, but not the web stack yet, so this URL is only reachable when the
 * user has installed their own local web server.
 */
const val LOCAL_PHPMYADMIN_URL = "http://127.0.0.1/phpmyadmin/"

/**
 * Status screen for phpMyAdmin.
 *
 * Honest state reporting instead of a white WebView: MariaDB running or not,
 * this screen explains exactly what phpMyAdmin needs, offers the in-app SQL
 * client route through the Database tab, and hands the URL to an external
 * browser for users who do run a local web stack. [onClose] pops this screen;
 * the system back gesture routes to the same callback.
 */
@Composable
fun _phpMyAdmin(
    url: String = LOCAL_PHPMYADMIN_URL,
    isDbRunning: Boolean,
    onClose: () -> Unit,
    lang: Locales.Lang = Locales.current
) {
    fun t(key: String): String = Locales.text(lang, key)
    val context = LocalContext.current

    BackHandler(enabled = true) { onClose() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SampThemeColors.bg)
    ) {
        /*
         * Compact toolbar: back to the Database tab, the URL being shown.
         */
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
                    color = SampThemeColors.textPrimary
                )
                Text(
                    text = url,
                    fontSize = 10.sp,
                    color = SampThemeColors.textMuted
                )
            }
        }

        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(20.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(SampThemeColors.card)
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.OpenInBrowser,
                    contentDescription = null,
                    tint = if (isDbRunning) SampThemeColors.accent else SampThemeColors.warn,
                    modifier = Modifier.size(26.dp)
                )
                Text(
                    text = t("db.phpmyadmin.notavailable.title"),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = SampThemeColors.textPrimary
                )
                Text(
                    text = (if (isDbRunning) {
                        t("db.phpmyadmin.notavailable.body")
                    } else {
                        t("db.offline.hint")
                    }),
                    fontSize = 11.sp,
                    color = SampThemeColors.textMuted
                )
                Text(
                    text = t("db.phpmyadmin.notavailable.sqlhint"),
                    fontSize = 11.sp,
                    color = SampThemeColors.textSecondary
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = {
                            runCatching {
                                context.startActivity(
                                    android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                                        data = android.net.Uri.parse(url)
                                        addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                    }
                                )
                            }
                        },
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                    ) {
                        Text(t("db.open.phpmyadmin.browser"), fontSize = 12.sp)
                    }
                    TextButton(
                        onClick = onClose,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                    ) {
                        Text(t("back.to.pawnmc"), fontSize = 12.sp)
                    }
                }
            }
        }
    }
}
