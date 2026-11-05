package com.pocketsamp.server.user_interface.screens

import com.pocketsamp.server.Locales
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pocketsamp.server.user_interface.theme.SampThemeColors

/**
 * One explainer card of the PocketSAMP help screen.
 *
 * [body] is plain text (blank line = paragraph break); it is deliberately not markdown so
 * the help screen stays readable in every language without a renderer per locale.
 */
internal data class HelpSection(
    val icon: String,
    val title: String,
    val body: String
)

/**
 * The multi-language walkthrough shown by the (H) button in the floating tab rail.
 *
 * The copy is intentionally relaxed ("pelan-pelan") instead of reference-style: the help
 * button exists for first-time users who do not know what each tab does yet.
 */
internal object HelpContent {

    fun intro(lang: Locales.Lang): String = when (lang) {
        Locales.Lang.ID -> "Panduan singkat untuk menyiapkan dan menggunakan PocketSAMP."
        Locales.Lang.EN -> "A short guide to setting up and using PocketSAMP."
        Locales.Lang.TL -> "Uy! Ito ang maikling gabay sa PocketSAMP. Relax lang, basahin bawat bahagi — ilang minuto lang lahat."
        Locales.Lang.ES -> "¡Hola! Esta es la guía corta de PocketSAMP. Sin prisa, lee una sección a la vez — todo toma un par de minutos."
        Locales.Lang.RU -> "Привет! Это короткое руководство по PocketSAMP. Не спешите, читайте по одному разделу — всё займёт пару минут."
    }

    fun footer(lang: Locales.Lang): String = when (lang) {
        Locales.Lang.ID -> "Jika server bermasalah, periksa pesan di Console."
        Locales.Lang.EN -> "If the server has a problem, check the Console messages."
        Locales.Lang.TL -> "Kung may hindi pa malinaw, buksan ang Console at basahin ang log: halos lahat ng problema ng server makikita doon."
        Locales.Lang.ES -> "Si algo sigue rare, abre Console y lee el registro: casi todos los problemas del servidor se ven ahí."
        Locales.Lang.RU -> "Если что-то всё ещё не так, откройте Console и прочитайте журнал: почти все проблемы сервера видны там."
    }

    fun sections(lang: Locales.Lang): List<HelpSection> = when (lang) {
        Locales.Lang.ID -> listOf(
            HelpSection(
                "dashboard",
                "Antarmuka Utama",
                "Mulai atau hentikan server di sini. Salin alamat Local untuk bermain di perangkat ini, " +
                    "atau alamat LAN untuk pemain di jaringan Wi-Fi yang sama.\n\n" +
                    "Jika server.cfg belum ada, file dengan pengaturan awal akan dibuat saat server pertama kali dimulai."
            ),
            HelpSection(
                "console",
                "Console",
                "Pesan startup, plugin, dan error server tampil di sini. Bersihkan tampilan tanpa menghapus log tersimpan."
            ),
            HelpSection(
                "files",
                "File Manager",
                "Kelola file server dan subfolder di sini. Pilih folder untuk membuat file baru. Edit membuka file di Xed; Open workspace membukanya sebagai workspace."
            ),
            HelpSection(
                "database",
                "Database",
                "MariaDB berjalan bersama server. Buat database, lihat tabel, jalankan SQL, atau impor file .sql. Sesuaikan akun jika gamemode memerlukannya."
            ),
            HelpSection(
                "crashes",
                "Crash Logs",
                "Lihat dan salin laporan saat aplikasi berhenti mendadak. Hapus laporan lama untuk merapikan daftar."
            ),
            HelpSection(
                "config",
                "Configuration",
                "Ubah hostname, port, pemain, RCON, gamemode, filterscripts, dan plugins. Simpan untuk memperbarui server.cfg."
            )
        )
        Locales.Lang.EN -> listOf(
            HelpSection(
                "dashboard",
                "Main interface",
                "Start or stop the server here. Copy the Local address to play on this phone, or the LAN address for players on the same Wi-Fi network.\n\n" +
                    "If server.cfg is missing, the app creates it with default settings on first start."
            ),
            HelpSection(
                "console",
                "Console",
                "Startup messages, plugins, and server errors appear here. Clear the view without deleting saved logs."
            ),
            HelpSection(
                "files",
                "File Manager",
                "Manage server files and folders here. Select a folder to create an item inside it. Edit opens a file in Xed; Open workspace opens it in the editor workspace."
            ),
            HelpSection(
                "database",
                "Database",
                "MariaDB runs with the server. Create databases, view tables, run SQL, or import .sql files. Set credentials if your gamemode requires them."
            ),
            HelpSection(
                "crashes",
                "Crash logs",
                "View and copy reports when the app stops unexpectedly. Delete old reports to keep the list tidy."
            ),
            HelpSection(
                "config",
                "Configuration",
                "Edit hostname, port, player limit, RCON, gamemode, filterscripts, and plugins. Save to update server.cfg."
            )
        )
        Locales.Lang.TL -> listOf(
            HelpSection(
                "dashboard",
                "Pangunahing interface",
                "Dito binubuksan at isinasara ang server.\n\n" +
                    "Ang unang card ay may Local address (127.0.0.1) para maglaro sa phone na ito, " +
                    "at LAN address para sa mga kaibigang nasa parehong Wi-Fi/hotspot. I-tap para kopyahin.\n\n" +
                    "Ang pinakababang button ay Start/Stop. Kung wala pang server.cfg, gagamitin pa rin " +
                    "ng runner ang default (PocketSAMP/PawnMC Runner, port 7777, 20 players, rcon234) at " +
                    "gagawa ng file sa unang simula."
            ),
            HelpSection(
                "console",
                "Console",
                "Dito lumalabas ang lahat ng sinasabi ng server: startup, mga plugin na na-load, at " +
                    "error kapag hindi na-load ang gamemode.\n\n" +
                    "Kung may kakaiba, dito muna tumingin — kadalasan pula ang dahilan. Hindi binubura " +
                    "ng pag-clear ng view ang lumang log sa device."
            ),
            HelpSection(
                "files",
                "File Manager",
                "Dito inaayos ang mga file ng server. Nagbubukas at nagsasara ang folder sa mismong " +
                    "listahan tulad ng Xed Editor workspace, kaya kita agad ang laman ng subfolder.\n\n" +
                    "Pumili muna ng folder para gumawa ng file/folder sa loob nito; kung wala pang " +
                    "napili, sa kasalukuyang bukas na folder mapupunta ang bago.\n\n" +
                    "Ang Edit ay nagbubukas sa Xed, at Open workspace ay ibinibigay ang folder sa editor."
            ),
            HelpSection(
                "database",
                "Database",
                "Kasabay tumatakbo ang MariaDB engine. Dito ka puwedeng gumawa ng database, tumingin " +
                    "ng table, magpatakbo ng SQL, at mag-import ng .sql file.\n\n" +
                    "Karaniwang root na walang password ang account; ikaw ang magpalit kung iba ang " +
                    "kailangan ng gamemode mo."
            ),
            HelpSection(
                "crashes",
                "Crash logs",
                "Kapag biglang huminto ang app, nandito ang buong report. Buksan ang detalye, kopyahin " +
                    "ang mahalagang bahagi, at ipadala sa developer.\n\n" +
                    "Puwedeng burahin ang lumang report para maikli lang ang listahan."
            ),
            HelpSection(
                "config",
                "Configuration",
                "Editor ng server.cfg: hostname, port, max players, password ng RCON, gamemode, " +
                    "filterscripts at plugins.\n\n" +
                    "Kung wala pa ang file, ipinapakita ang default ng runner. Pindutin ang Save para " +
                    "maisulat ito sa server folder."
            )
        )
        Locales.Lang.ES -> listOf(
            HelpSection(
                "dashboard",
                "Interfaz principal",
                "Aquí se inicia y se detiene el servidor.\n\n" +
                    "La primera tarjeta muestra la dirección Local (127.0.0.1) para jugar en este " +
                    "teléfono y la dirección LAN para amigos en la misma Wi-Fi/hotspot. Toca una para copiarla.\n\n" +
                    "El botón inferior es Start/Stop. Sin server.cfg, el runner usa igualmente sus valores " +
                    "por defecto (PocketSAMP/PawnMC Runner, puerto 7777, 20 jugadores, rcon234) y crea el archivo al arrancar."
            ),
            HelpSection(
                "console",
                "Consola",
                "Aquí aparece todo lo que dice el servidor: arranque, plugins cargados y el error que " +
                    "impidió cargar un gamemode.\n\n" +
                    "Si algo va raro, mira aquí primero: la línea roja suele ser la causa. Limpiar la " +
                    "vista no borra los registros del dispositivo."
            ),
            HelpSection(
                "files",
                "File Manager",
                "Aquí se organizan los archivos del servidor. Las carpetas se abren y cierran en el " +
                    "mismo listado, como el workspace de Xed Editor, así que el contenido anidado se ve al instante.\n\n" +
                    "Selecciona una carpeta para crear archivos o carpetas dentro; si no hay selección, " +
                    "lo nuevo va a la carpeta abierta.\n\n" +
                    "Edit abre el archivo en Xed y Open workspace entrega la carpeta al editor."
            ),
            HelpSection(
                "database",
                "Base de datos",
                "El motor MariaDB corre junto al servidor. Desde aquí puedes crear bases, revisar tablas, " +
                    "ejecutar SQL e importar archivos .sql.\n\n" +
                    "La cuenta habitual es root sin contraseña; cámbiala si tu gamemode necesita otras credenciales."
            ),
            HelpSection(
                "crashes",
                "Informes de fallos",
                "Si la app se detuvo de golpe, el informe completo queda aquí. Abre el detalle, copia lo " +
                    "importante y envíalo al desarrollador.\n\n" +
                    "Los informes antiguos se pueden borrar para mantener la lista corta."
            ),
            HelpSection(
                "config",
                "Configuración",
                "El editor de server.cfg: hostname, puerto, jugadores máximos, contraseña RCON, gamemode, " +
                    "filterscripts y plugins.\n\n" +
                    "Si el archivo aún no existe se muestran los valores por defecto. Pulsa Save para " +
                    "escribirlos en la carpeta del servidor."
            )
        )
        Locales.Lang.RU -> listOf(
            HelpSection(
                "dashboard",
                "Главный экран",
                "Здесь сервер запускается и останавливается.\n\n" +
                    "Первая карточка — адрес Local (127.0.0.1) для игры на этом телефоне, ниже адрес LAN " +
                    "для друзей в той же Wi-Fi/hotspot сети. Нажмите на адрес, чтобы скопировать.\n\n" +
                    "Кнопка внизу — Start/Stop. Без server.cfg runner всё равно использует значения по " +
                    "умолчанию (PocketSAMP/PawnMC Runner, порт 7777, 20 игроков, rcon234) и создаёт файл при первом запуске."
            ),
            HelpSection(
                "console",
                "Консоль",
                "Здесь видно всё, что сообщает сервер: запуск, загруженные плагины и ошибку, из-за " +
                    "которой не загрузился gamemode.\n\n" +
                    "Если что-то не так, смотрите сюда: красная строка обычно и есть причина. Очистка " +
                    "вида не удаляет старые журналы с устройства."
            ),
            HelpSection(
                "files",
                "File Manager",
                "Здесь наводят порядок в файлах сервера. Папки раскрываются прямо в списке, как в " +
                    "workspace Xed Editor, поэтому вложенное содержимое видно сразу.\n\n" +
                    "Сначала выберите папку, чтобы создать файл или папку внутри неё; без выбора новый " +
                    "элемент попадает в открытую сейчас папку.\n\n" +
                    "Edit открывает файл в Xed, Open workspace передаёт папку редактору."
            ),
            HelpSection(
                "database",
                "База данных",
                "Движок MariaDB работает вместе с сервером. Здесь можно создавать базы, смотреть " +
                    "таблицы, выполнять SQL и импортировать .sql.\n\n" +
                    "Обычная учётная запись — root без пароля; смените её, если gamemode требует другое."
            ),
            HelpSection(
                "crashes",
                "Журналы сбоев",
                "Если приложение внезапно закрылось, полный отчёт лежит здесь. Откройте детали, " +
                    "скопируйте важное и отправьте разработчику.\n\n" +
                    "Старые отчёты можно удалить, чтобы список оставался коротким."
            ),
            HelpSection(
                "config",
                "Конфигурация",
                "Редактор server.cfg: hostname, порт, максимум игроков, пароль RCON, gamemode, " +
                    "filterscripts и plugins.\n\n" +
                    "Если файла ещё нет, показаны значения runner по умолчанию. Нажмите Save, чтобы " +
                    "записать их в папку сервера."
            )
        )
    }
}

/**
 * The "PocketSAMP, slowly" screen opened from the (H) button.
 *
 * It is a plain scrolling read rather than a wizard: the reader chooses the pace, and
 * each card explains one tab in the same order the floating rail shows them.
 */
@Composable
fun _Help(lang: Locales.Lang) {
    fun t(key: String): String = Locales.text(lang, key)
    val sections = remember(lang) { HelpContent.sections(lang) }
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SampThemeColors.bg)
            .verticalScroll(scrollState)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = t("help.title"),
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = SampThemeColors.textPrimary
        )
        Text(
            text = HelpContent.intro(lang),
            style = MaterialTheme.typography.bodySmall,
            color = SampThemeColors.textSecondary
        )

        sections.forEachIndexed { index, section ->
            Card(
                colors = CardDefaults.cardColors(containerColor = SampThemeColors.card),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(26.dp)
                                .clip(CircleShape)
                                .background(SampThemeColors.accent.copy(alpha = 0.16f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "${index + 1}",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = SampThemeColors.accent
                            )
                        }
                        Spacer(Modifier.size(10.dp))
                        Text(
                            text = section.title,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = SampThemeColors.textPrimary
                        )
                    }
                    Text(
                        text = section.body,
                        style = MaterialTheme.typography.bodySmall,
                        color = SampThemeColors.textSecondary
                    )
                }
            }
        }

        Card(colors = CardDefaults.cardColors(containerColor = SampThemeColors.surface)) {
            Text(
                text = HelpContent.footer(lang),
                modifier = Modifier.padding(14.dp),
                style = MaterialTheme.typography.bodySmall,
                color = SampThemeColors.textMuted
            )
        }
        Spacer(Modifier.height(64.dp))
    }
}
