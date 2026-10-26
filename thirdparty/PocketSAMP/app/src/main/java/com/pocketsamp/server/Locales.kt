package com.pocketsamp.server

/**
 * Localisation for the embedded PocketSAMP UI.
 *
 * The language is set by the host (PawnMC) from its own settings, so the embedded
 * runtime speaks whatever the main interface is configured to speak (EN/ID/ES/RU).
 * Strings live in one table instead of Android resource qualifiers because the
 * language must follow PawnMC's *setting*, not the device locale.
 *
 * English is the fallback for any missing entry.
 */
object Locales {

    enum class Lang { ID, EN, ES, RU, TL }

    /** The language of the running UI; the host (PawnMC) sets it at launch. */
    @Volatile
    var current: Lang = Lang.EN
        private set

    /**
     * Theme mode of the running UI; the host (PawnMC) sets it at launch so the
     * embedded interface follows the main app (LIGHT / DARK / SYSTEM).
     * The dark look is the semi-violet ramp, exactly like PawnMC's dark theme.
     */
    enum class Theme { LIGHT, DARK, SYSTEM }

    @Volatile
    var theme: Theme = Theme.SYSTEM
        private set

    /** Called by the host before [SampApp.launch] so the UI matches the PawnMC setting. */
    fun setLanguage(lang: Lang) {
        current = lang
    }

    /** Called by the host before [SampApp.launch] so the UI follows the PawnMC theme. */
    fun setTheme(mode: Theme) {
        theme = mode
    }

    /** Convenience lookup for composables that did not receive a lang parameter. */
    fun text(key: String): String = text(current, key)

    /** Look up [key]; falls back to the English entry, then the key itself. */
    fun text(lang: Lang, key: String): String {
        val table = translations[key] ?: return key
        return table[lang] ?: table[Lang.EN] ?: key
    }

    private val translations: Map<String, Map<Lang, String>> = mapOf(
        "back.to.pawnmc" to mapOf(
            Lang.ID to "Balik ke PawnMC",
            Lang.EN to "Back to PawnMC",
            Lang.TL to "Balik sa PawnMC",
            Lang.ES to "Volver a PawnMC",
            Lang.RU to "Назад в PawnMC",
        ),
        "tagline" to mapOf(
            Lang.ID to "Server SA-MP kamu sendiri, di sini",
            Lang.EN to "Your own SA-MP server, right here",
            Lang.TL to "Sariling server SA-MP mo, dito lang",
            Lang.ES to "Runtime portátil local de SA-MP y MySQL",
            Lang.RU to "Локальный переносимый рантайм SA-MP и MySQL",
        ),
        "status.online" to mapOf(
            Lang.ID to "AKTIF",
            Lang.EN to "LIVE",
            Lang.TL to "LIVE",
            Lang.ES to "EN LÍNEA",
            Lang.RU to "В СЕТИ",
        ),
        "status.starting" to mapOf(
            Lang.ID to "NYALA DULU",
            Lang.EN to "WAKING UP",
            Lang.TL to "GUMISING",
            Lang.ES to "INICIANDO",
            Lang.RU to "ЗАПУСК",
        ),
        "status.stopping" to mapOf(
            Lang.ID to "MATIIN DULU",
            Lang.EN to "SHUTTING DOWN",
            Lang.TL to "PAPATAYIN",
            Lang.ES to "DETENIENDO",
            Lang.RU to "ОСТАНОВКА",
        ),
        "status.offline" to mapOf(
            Lang.ID to "MATI",
            Lang.EN to "OFFLINE",
            Lang.TL to "PATAY",
            Lang.ES to "SIN CONEXIÓN",
            Lang.RU to "НЕ В СЕТИ",
        ),
        "action.start" to mapOf(
            Lang.ID to "NYALAKAN",
            Lang.EN to "LIGHT IT UP",
            Lang.TL to "PASINDIHAN",
            Lang.ES to "INICIAR SERVIDOR",
            Lang.RU to "ЗАПУСТИТЬ СЕРВЕР",
        ),
        "action.stop" to mapOf(
            Lang.ID to "UDAH DULU",
            Lang.EN to "CALL IT A DAY",
            Lang.TL to "TIGIL NA",
            Lang.ES to "DETENER SERVIDOR",
            Lang.RU to "ОСТАНОВИТЬ СЕРВЕР",
        ),
        "db.running" to mapOf(
            Lang.ID to "JALAN",
            Lang.EN to "RUNNING",
            Lang.TL to "TUMATAKBO",
            Lang.ES to "ACTIVO",
            Lang.RU to "РАБОТАЕТ",
        ),
        "perm.file.title" to mapOf(
            Lang.ID to "Kasih akses file dulu",
            Lang.EN to "Let PocketSAMP see your files",
            Lang.TL to "Bigyan ng access sa files",
            Lang.ES to "Se requiere permiso de acceso a archivos",
            Lang.RU to "Требуется разрешение на доступ к файлам",
        ),
        "perm.file.grant" to mapOf(
            Lang.ID to "Buka pengaturan akses file",
            Lang.EN to "Open the file access setting",
            Lang.TL to "Buksan ang file access",
            Lang.ES to "Conceder acceso a archivos",
            Lang.RU to "Разрешить доступ к файлам",
        ),
        "perm.battery.title" to mapOf(
            Lang.ID to "Biar server nggak tidur sendiri",
            Lang.EN to "Stop Android from napping your server",
            Lang.TL to "Wag hayaang makatulog ang server",
            Lang.ES to "Estabilizar el proceso en segundo plano",
            Lang.RU to "Стабилизировать фоновый процесс",
        ),
        "perm.battery.action" to mapOf(
            Lang.ID to "Matikan optimasi baterai",
            Lang.EN to "Turn off battery optimisation",
            Lang.TL to "Patayin ang battery optimisation",
            Lang.ES to "Desactivar la optimización de batería",
            Lang.RU to "Отключить оптимизацию батареи",
        ),
        "perm.close" to mapOf(
            Lang.ID to "Oke, ngerti",
            Lang.EN to "Got it",
            Lang.TL to "Sige, gets ko",
            Lang.ES to "Cerrar",
            Lang.RU to "Закрыть",
        ),
        "perm.guide" to mapOf(
            Lang.ID to "HP suka ngadat?",
            Lang.EN to "Phone acting up?",
            Lang.TL to "Nagpapasaway ang phone mo?",
            Lang.ES to "Soluciones del dispositivo",
            Lang.RU to "Решения для устройства",
        ),
        "folder.pick" to mapOf(
            Lang.ID to "Pilih folder",
            Lang.EN to "Pick a folder",
            Lang.TL to "Pumili ng folder",
            Lang.ES to "Elegir...",
            Lang.RU to "Выбрать...",
        ),
        "folder.reset" to mapOf(
            Lang.ID to "Balik ke awal",
            Lang.EN to "Back to default",
            Lang.TL to "Balik sa default",
            Lang.ES to "Restablecer",
            Lang.RU to "Сбросить",
        ),
        "folder.reset.done" to mapOf(
            Lang.ID to "Sudah balik ke folder awal",
            Lang.EN to "Back to the default folder",
            Lang.TL to "Balik na sa default folder",
            Lang.ES to "Restablecido al valor de PocketSAMP",
            Lang.RU to "Сброшено на значение PocketSAMP",
        ),
        "folder.gamemode" to mapOf(
            Lang.ID to "Folder gamemode SA-MP kamu",
            Lang.EN to "Your SA-MP gamemode folder",
            Lang.TL to "Gamemode folder ng SA-MP mo",
            Lang.ES to "Elegir... Carpeta de Gamemode SA-MP",
            Lang.RU to "Выбрать... Папку Gamemode SA-MP",
        ),
        "tab.dashboard" to mapOf(
            Lang.ID to "Beranda",
            Lang.EN to "Home",
            Lang.TL to "Bahay",
            Lang.ES to "Panel",
            Lang.RU to "Панель",
        ),
        "tab.console" to mapOf(
            Lang.ID to "Konsol",
            Lang.EN to "Console",
            Lang.TL to "Console",
            Lang.ES to "Consola",
            Lang.RU to "Консоль",
        ),
        "tab.database" to mapOf(
            Lang.ID to "Basis data",
            Lang.EN to "Database",
            Lang.TL to "Database",
            Lang.ES to "Base de datos",
            Lang.RU to "База данных",
        ),
        "tab.crashes" to mapOf(
            Lang.ID to "Crash",
            Lang.EN to "Crashes",
            Lang.TL to "Mga crash",
            Lang.ES to "Registros de fallos",
            Lang.RU to "Журнал сбоев",
        ),
        "tab.config" to mapOf(
            Lang.ID to "Atur",
            Lang.EN to "Config",
            Lang.TL to "Config",
            Lang.ES to "Config",
            Lang.RU to "Настройки",
        ),
        "config.title" to mapOf(
            Lang.ID to "Atur server",
            Lang.EN to "Server config",
            Lang.TL to "Config ng server",
            Lang.ES to "Configuración del servidor",
            Lang.RU to "Конфигурация сервера",
        ),
        "config.subtitle" to mapOf(
            Lang.ID to "Semua yang server baca dari server.cfg",
            Lang.EN to "Everything the server reads from server.cfg",
            Lang.TL to "Lahat ng binabasa ng server mula sa server.cfg",
            Lang.ES to "Editor de server.cfg",
            Lang.RU to "Редактор server.cfg",
        ),
        "config.save" to mapOf(
            Lang.ID to "Simpan",
            Lang.EN to "Save it",
            Lang.TL to "I-save",
            Lang.ES to "Guardar",
            Lang.RU to "Сохранить",
        ),
        "config.saved" to mapOf(
            Lang.ID to "Tersimpan. Server sudah pakai.",
            Lang.EN to "Saved. The server has it now.",
            Lang.TL to "Naka-save na. Gagamitin na ng server.",
            Lang.ES to "¡server.cfg guardado!",
            Lang.RU to "server.cfg успешно сохранён!",
        ),
        "console.title" to mapOf(
            Lang.ID to "Kata server",
            Lang.EN to "What the server is saying",
            Lang.TL to "Sinasabi ng server",
            Lang.ES to "Consola del servidor",
            Lang.RU to "Консоль сервера",
        ),
        "db.title" to mapOf(
            Lang.ID to "Basis data",
            Lang.EN to "Database",
            Lang.TL to "Database",
            Lang.ES to "Gestor de base de datos",
            Lang.RU to "Менеджер базы данных",
        ),
        "db.connected" to mapOf(
            Lang.ID to "Tersambung ke 127.0.0.1:3306 sebagai root",
            Lang.EN to "Connected on 127.0.0.1:3306 as root",
            Lang.TL to "Konektado sa 127.0.0.1:3306 bilang root",
            Lang.ES to "Conectado: 127.0.0.1:3306 (root)",
            Lang.RU to "Подключено: 127.0.0.1:3306 (root)",
        ),
        "db.offline.hint" to mapOf(
            Lang.ID to "Nyalakan server dari Beranda, lalu balik ke sini.",
            Lang.EN to "Start the server from Home, then come back here.",
            Lang.TL to "Simulan ang server sa Home, tapos balik dito.",
            Lang.ES to "Inicia el servidor en el Panel para conectarte a la base de datos MySQL.",
            Lang.RU to "Запустите сервер на панели, чтобы подключиться к базе данных MySQL.",
        ),
        "db.unavailable.hint" to mapOf(
            Lang.ID to "Basis data itu opsional — gamemode tetap jalan tanpa itu. Buka tab Konsol buat lihat kenapa dia nggak nyala.",
            Lang.EN to "Database is optional — your gamemode still runs without it. Open the Console tab to see why it stayed down.",
            Lang.TL to "Optional ang database — tatakbo pa rin ang gamemode kahit wala ito. Buksan ang Console tab para malaman bakit hindi umandar.",
            Lang.ES to "La base de datos es opcional: si el servidor SA-MP está en marcha pero MariaDB sigue detenido, abre la pestaña Consola para ver el motivo exacto (por ejemplo un paquete de binarios MariaDB incompleto).",
            Lang.RU to "База данных необязательна: если сервер SA-MP запущен, а MariaDB всё ещё не работает, откройте вкладку «Консоль» — там указана точная причина (например, неполный пакет бинарников MariaDB).",
        ),
        "db.refresh" to mapOf(
            Lang.ID to "Muat ulang daftar",
            Lang.EN to "Reload the list",
            Lang.TL to "I-reload ang listahan",
            Lang.ES to "Recargar la lista de bases de datos",
            Lang.RU to "Обновить список баз данных",
        ),
        "db.new" to mapOf(
            Lang.ID to "Baru",
            Lang.EN to "New",
            Lang.TL to "Bago",
            Lang.ES to "Nueva",
            Lang.RU to "Новая",
        ),
        "db.new.title" to mapOf(
            Lang.ID to "Bikin basis data",
            Lang.EN to "Make a database",
            Lang.TL to "Gumawa ng database",
            Lang.ES to "Nueva base de datos",
            Lang.RU to "Новая база данных",
        ),
        "db.new.desc" to mapOf(
            Lang.ID to "Langsung dibuat di MariaDB yang sedang jalan.",
            Lang.EN to "It goes straight onto the MariaDB instance that is running right now.",
            Lang.TL to "Diretso ito sa MariaDB na nakaandar ngayon.",
            Lang.ES to "La base de datos se crea en el servidor MariaDB en ejecución.",
            Lang.RU to "База данных создаётся на запущенном сервере MariaDB.",
        ),
        "db.new.name" to mapOf(
            Lang.ID to "Namanya",
            Lang.EN to "Name it",
            Lang.TL to "Pangalan",
            Lang.ES to "Nombre de la base de datos",
            Lang.RU to "Имя базы данных",
        ),
        "db.new.rule" to mapOf(
            Lang.ID to "Huruf, angka, dan garis bawah saja.",
            Lang.EN to "Letters, numbers and underscores only.",
            Lang.TL to "Letra, numero, at underscore lang.",
            Lang.ES to "Solo letras, números y guiones bajos.",
            Lang.RU to "Только буквы, цифры и подчёркивания.",
        ),
        "db.new.create" to mapOf(
            Lang.ID to "Bikin",
            Lang.EN to "Make it",
            Lang.TL to "Gawin",
            Lang.ES to "Crear",
            Lang.RU to "Создать",
        ),
        "db.created" to mapOf(
            Lang.ID to "Jadi — ini dia",
            Lang.EN to "Done — here it is",
            Lang.TL to "Ayos — ito na",
            Lang.ES to "Base de datos creada:",
            Lang.RU to "База данных создана:",
        ),
        "db.none" to mapOf(
            Lang.ID to "Belum ada yang dipilih",
            Lang.EN to "Nothing picked yet",
            Lang.TL to "Wala pang napili",
            Lang.ES to "Ninguna base de datos seleccionada",
            Lang.RU to "База данных не выбрана",
        ),
        "db.tables" to mapOf(
            Lang.ID to "{count} tabel",
            Lang.EN to "{count} tables",
            Lang.TL to "{count} tables",
            Lang.ES to "{count} tablas",
            Lang.RU to "{count} таблиц",
        ),
        "db.import" to mapOf(
            Lang.ID to "Muat .sql",
            Lang.EN to "Load a .sql",
            Lang.TL to "Mag-load ng .sql",
            Lang.ES to "Importar .sql",
            Lang.RU to "Импорт .sql",
        ),
        "db.importing" to mapOf(
            Lang.ID to "Lagi masukin SQL...",
            Lang.EN to "Dropping the SQL in...",
            Lang.TL to "Ipinapasok ang SQL...",
            Lang.ES to "Importando script SQL...",
            Lang.RU to "Импорт SQL-скрипта...",
        ),
        "db.import.error" to mapOf(
            Lang.ID to "Impor-nya gagal",
            Lang.EN to "That import didn't work",
            Lang.TL to "Hindi nagtagumpay ang import",
            Lang.ES to "Error de importación",
            Lang.RU to "Ошибка импорта",
        ),
        "db.select.first" to mapOf(
            Lang.ID to "Pilih basis data dulu",
            Lang.EN to "Pick a database first",
            Lang.TL to "Pumili muna ng database",
            Lang.ES to "¡Selecciona o crea una base de datos primero!",
            Lang.RU to "Сначала выберите или создайте базу данных!",
        ),
        "db.manage" to mapOf(
            Lang.ID to "Kelola",
            Lang.EN to "Manage",
            Lang.TL to "Pamahalaan",
            Lang.ES to "Gestionar",
            Lang.RU to "Управлять",
        ),
        "db.phpmyadmin" to mapOf(
            Lang.ID to "phpMyAdmin",
            Lang.EN to "phpMyAdmin",
            Lang.TL to "phpMyAdmin",
            Lang.ES to "phpMyAdmin",
            Lang.RU to "phpMyAdmin",
        ),
        "db.open.phpmyadmin" to mapOf(
            Lang.ID to "Buka phpMyAdmin",
            Lang.EN to "Open phpMyAdmin",
            Lang.TL to "Buksan ang phpMyAdmin",
            Lang.ES to "Abrir phpMyAdmin local",
            Lang.RU to "Открыть локальный phpMyAdmin",
        ),
        "db.phpmyadmin.reload" to mapOf(
            Lang.ID to "Muat ulang halaman",
            Lang.EN to "Reload the page",
            Lang.TL to "I-reload ang page",
            Lang.ES to "Recargar la página",
            Lang.RU to "Обновить страницу",
        ),
        "db.phpmyadmin.error" to mapOf(
            Lang.ID to "phpMyAdmin nggak kebuka",
            Lang.EN to "phpMyAdmin won't load",
            Lang.TL to "Ayaw mag-load ng phpMyAdmin",
            Lang.ES to "No se pudo cargar phpMyAdmin",
            Lang.RU to "Не удалось загрузить phpMyAdmin",
        ),
        "sql.error" to mapOf(
            Lang.ID to "SQL-nya nggak suka",
            Lang.EN to "It didn't like that",
            Lang.TL to "Hindi tinanggap",
            Lang.ES to "Error",
            Lang.RU to "Ошибка",
        ),
        "sql.ok" to mapOf(
            Lang.ID to "Beres — {count} baris kena",
            Lang.EN to "Done — {count} rows touched",
            Lang.TL to "Tapos — {count} rows ang naapektuhan",
            Lang.ES to "Consulta OK, {count} filas afectadas.",
            Lang.RU to "Запрос выполнен, затронуто строк: {count}.",
        ),
        "db.offline" to mapOf(
            Lang.ID to "Basis data belum nyala",
            Lang.EN to "The database isn't up",
            Lang.TL to "Wala pang database",
            Lang.ES to "El servidor de base de datos está desconectado",
            Lang.RU to "Сервер базы данных не активен",
        ),
        "sql.query" to mapOf(
            Lang.ID to "Tulis SQL kamu",
            Lang.EN to "Type your SQL",
            Lang.TL to "Isulat ang SQL mo",
            Lang.ES to "Consulta SQL",
            Lang.RU to "SQL-запрос",
        ),
        "sql.run" to mapOf(
            Lang.ID to "Jalankan",
            Lang.EN to "Go",
            Lang.TL to "Takbo",
            Lang.ES to "Ejecutar",
            Lang.RU to "Запустить",
        ),
        "crash.details" to mapOf(
            Lang.ID to "Apa yang rusak",
            Lang.EN to "What broke",
            Lang.TL to "Ano ang nasira",
            Lang.ES to "Detalles del fallo",
            Lang.RU to "Детали сбоя",
        ),
        "crash.copy" to mapOf(
            Lang.ID to "Salin log",
            Lang.EN to "Copy the log",
            Lang.TL to "Kopyahin ang log",
            Lang.ES to "Copiar registro de fallos",
            Lang.RU to "Копировать журнал сбоя",
        ),
        "crash.reopen" to mapOf(
            Lang.ID to "Buka lagi",
            Lang.EN to "Open it again",
            Lang.TL to "Buksan ulit",
            Lang.ES to "Reabrir la app",
            Lang.RU to "Перезапустить приложение",
        ),
        "crash.delete" to mapOf(
            Lang.ID to "Buang",
            Lang.EN to "Throw it away",
            Lang.TL to "Itapon",
            Lang.ES to "Eliminar",
            Lang.RU to "Удалить",
        ),
        "crash.delete.all" to mapOf(
            Lang.ID to "Buang semua",
            Lang.EN to "Throw them all",
            Lang.TL to "Itapon lahat",
            Lang.ES to "Eliminar todo",
            Lang.RU to "Удалить все",
        ),
        "crash.device" to mapOf(
            Lang.ID to "HP kamu",
            Lang.EN to "Your phone",
            Lang.TL to "Phone mo",
            Lang.ES to "Información del dispositivo",
            Lang.RU to "Информация об устройстве",
        ),
        /*
         * --- strings that used to be hardcoded in Indonesian on the dashboard ------
         */
        "folder.changed" to mapOf(
            Lang.ID to "Folder gamemode sekarang",
            Lang.EN to "Gamemode folder is now",
            Lang.TL to "Gamemode folder ngayon ay",
            Lang.ES to "Elegir... carpeta de gamemode cambiada a",
            Lang.RU to "Выбрать... папка gamemode изменена на",
        ),
        "folder.pick.failed" to mapOf(
            Lang.ID to "Foldernya nggak kebuka. Coba yang lain.",
            Lang.EN to "Couldn't open that folder. Try another one.",
            Lang.TL to "Hindi mabuksan ang folder. Subukan iba.",
            Lang.ES to "No se pudo resolver la ruta de la carpeta elegida.",
            Lang.RU to "Не удалось определить путь выбранной папки.",
        ),
        "dash.local.title" to mapOf(
            Lang.ID to "Main di HP ini",
            Lang.EN to "Just this phone",
            Lang.TL to "Dito lang sa phone",
            Lang.ES to "Jugar en este teléfono (un equipo)",
            Lang.RU to "Игра на этом телефоне (одно устройство)",
        ),
        "dash.local.note" to mapOf(
            Lang.ID to "Paling enak: ping nol, nggak butuh Wi-Fi, dan nggak putus pas kamu pindah aplikasi.",
            Lang.EN to "Best choice: no ping, no Wi-Fi needed, and it won't drop when you switch apps.",
            Lang.TL to "Pinakamainam: walang ping, hindi kailangan ng Wi-Fi, at hindi mapuputol pag lumipat ka ng app.",
            Lang.ES to "Muy recomendado: conexión interna directa (0 ms de ping), sin depender de la señal Wi-Fi, y sin cortes al cambiar o abrir el juego.",
            Lang.RU to "Настоятельно рекомендуется: прямое внутреннее соединение (пинг 0 мс), не зависит от сигнала Wi-Fi и не обрывается при переключении на игру.",
        ),
        "dash.lan.title" to mapOf(
            Lang.ID to "Main bareng teman",
            Lang.EN to "Playing with friends",
            Lang.TL to "Kasama ang mga kaibigan",
            Lang.ES to "Jugar con amigos (mismo Wi-Fi)",
            Lang.RU to "Игра с друзьями (один Wi-Fi)",
        ),
        "dash.lan.note" to mapOf(
            Lang.ID to "Kirim alamat ini ke siapa pun di Wi-Fi atau hotspot yang sama.",
            Lang.EN to "Send this address to anyone on the same Wi-Fi or hotspot.",
            Lang.TL to "Ipadala ang address na ito sa nasa parehong Wi-Fi o hotspot.",
            Lang.ES to "Comparte esta dirección para que amigos se unan desde otro teléfono por el mismo Wi-Fi/hotspot.",
            Lang.RU to "Поделитесь этим адресом, чтобы друзья могли подключиться с другого телефона через тот же Wi-Fi/точку доступа.",
        ),
        "copy.done" to mapOf(
            Lang.ID to "Tersalin",
            Lang.EN to "Copied",
            Lang.TL to "Nakopya",
            Lang.ES to "Copiado:",
            Lang.RU to "Скопировано:",
        ),
        "copy.path.done" to mapOf(
            Lang.ID to "Path tersalin",
            Lang.EN to "Path copied",
            Lang.TL to "Nakopya ang path",
            Lang.ES to "¡Ruta copiada al portapapeles!",
            Lang.RU to "Путь скопирован в буфер обмена!",
        ),
        "perm.file.desc" to mapOf(
            Lang.ID to "Android minta izin \"Akses semua file\" sebelum server bisa baca folder gamemode kamu. Cuma sekali tap kok.",
            Lang.EN to "Android needs \"All files access\" before the server can read your gamemode folder. It is a one-time tap.",
            Lang.TL to "Kailangan ng Android ang \"All files access\" bago mabasa ng server ang gamemode folder mo. Isang tap lang naman.",
            Lang.ES to "Android requiere el permiso 'Acceso a todos los archivos' para que el servidor lea tu carpeta de gamemode directamente.",
            Lang.RU to "Android требует разрешение «Доступ ко всем файлам», чтобы сервер мог напрямую читать вашу папку gamemode.",
        ),
        "perm.file.settings" to mapOf(
            Lang.ID to "Setelan > Aplikasi > PocketSAMP > Izin",
            Lang.EN to "Settings > Apps > PocketSAMP > Permissions",
            Lang.TL to "Settings > Apps > PocketSAMP > Permissions",
            Lang.ES to "Abre Ajustes -> Apps -> PocketSAMP -> Permisos",
            Lang.RU to "Откройте Настройки -> Приложения -> PocketSAMP -> Разрешения",
        ),
        "perm.file.button" to mapOf(
            Lang.ID to "Kasih aksesnya",
            Lang.EN to "Give it access",
            Lang.TL to "Bigyan ng access",
            Lang.ES to "Conceder acceso a archivos",
            Lang.RU to "Разрешить доступ к файлам",
        ),
        "perm.battery.desc" to mapOf(
            Lang.ID to "Matikan optimasi baterai, kalau nggak Android bisa nge-pause server pas kamu pindah ke game.",
            Lang.EN to "Turn off battery optimisation, otherwise Android may pause the server the moment you switch to the game.",
            Lang.TL to "Patayin ang battery optimisation, kung hindi pwedeng i-pause ng Android ang server pag lipat mo sa game.",
            Lang.ES to "Desactiva la optimización de batería para que Android no limite ni pause el servidor al abrir el juego/launcher SA-MP.",
            Lang.RU to "Отключите оптимизацию батареи, чтобы Android не ограничивал и не приостанавливал сервер при запуске SA-MP.",
        ),
        "perm.battery.button" to mapOf(
            Lang.ID to "Matikan aja",
            Lang.EN to "Turn it off",
            Lang.TL to "Patayin na",
            Lang.ES to "Desactivar optimización de batería",
            Lang.RU to "Отключить оптимизацию батареи",
        ),
        "guide.antidc.title" to mapOf(
            Lang.ID to "Sering ke-disconnect dari game?",
            Lang.EN to "Getting dropped from the game?",
            Lang.TL to "Laging nadidisconnect sa laro?",
            Lang.ES to "Guía antidesconexión (HyperOS / Android 14)",
            Lang.RU to "Гид по защите от обрывов (HyperOS / Android 14)",
        ),
        "guide.antidc.step1" to mapOf(
            Lang.ID to "1. Kunci PocketSAMP di Recents",
            Lang.EN to "1. Lock PocketSAMP in Recents",
            Lang.TL to "1. I-lock ang PocketSAMP sa Recents",
            Lang.ES to "1. Bloquea la app en Recent Apps (importante en Xiaomi/HyperOS):",
            Lang.RU to "1. Закрепите приложение в списке недавних (важно для Xiaomi/HyperOS):",
        ),
        "guide.antidc.step1.desc" to mapOf(
            Lang.ID to "Buka Recents, tahan kartu PocketSAMP, lalu tap gemboknya. Itu bikin sistem nggak nge-freeze server pas kamu main.",
            Lang.EN to "Open Recents, hold the PocketSAMP card, then tap the lock. That stops the system from freezing the server while you play.",
            Lang.TL to "Buksan ang Recents, hawakan ang PocketSAMP card, tapos i-tap ang lock. Yun ang pipigil sa system na i-freeze ang server habang naglalaro ka.",
            Lang.ES to "Abre Recent Apps (multitarea) -> mantén pulsada la ventana de PocketSAMP -> toca el candado. Así el SO no congela el servidor con un juego a pantalla completa.",
            Lang.RU to "Откройте недавние приложения (многозадачность) -> удерживайте окно PocketSAMP -> нажмите значок замка. Тогда ОС не заморозит сервер при полноэкранной игре.",
        ),
        "guide.antidc.step2" to mapOf(
            Lang.ID to "2. Masuk pakai 127.0.0.1:7777",
            Lang.EN to "2. Join with 127.0.0.1:7777",
            Lang.TL to "2. Sumali gamit ang 127.0.0.1:7777",
            Lang.ES to "2. Usa la IP 127.0.0.1:7777:",
            Lang.RU to "2. Используйте IP 127.0.0.1:7777:",
        ),
        "guide.antidc.step2.desc" to mapOf(
            Lang.ID to "Di HP ini alamat itu nggak bakal diputus Wi-Fi sleep.",
            Lang.EN to "On this phone that address never gets cut by Wi-Fi sleep.",
            Lang.TL to "Sa phone na ito, hindi napuputol ng Wi-Fi sleep ang address na iyon.",
            Lang.ES to "Si juegas en este teléfono, introduce 127.0.0.1:7777 en tu launcher SA-MP. Esta ruta loopback interna nunca la corta el Wi-Fi sleep de Android.",
            Lang.RU to "Если играете на этом телефоне, введите 127.0.0.1:7777 в SA-MP лаунчере. Этот внутренний loopback-путь никогда не обрывается спящим Wi-Fi Android.",
        ),
        "guide.antidc.step3" to mapOf(
            Lang.ID to "3. Lepas batas proses anak (Android 12+)",
            Lang.EN to "3. Lift child-process limits (Android 12+)",
            Lang.TL to "3. Alisin ang limit sa child process (Android 12+)",
            Lang.ES to "3. Desactiva las restricciones de procesos hijo (Android 12+):",
            Lang.RU to "3. Отключите ограничения дочерних процессов (Android 12+):",
        ),
        "guide.antidc.step3.desc" to mapOf(
            Lang.ID to "Di Opsi Pengembang, nyalakan \"Nonaktifkan pembatasan proses turunan\" biar proses server nggak dibunuh (Exit 137).",
            Lang.EN to "In Developer options, turn on \"Disable child process restrictions\" so the server process isn't killed (Exit 137).",
            Lang.TL to "Sa Developer options, i-on ang \"Disable child process restrictions\" para hindi mapatay ang server process (Exit 137).",
            Lang.ES to "Abre Ajustes > Opciones de desarrollador > activa 'Desactivar restricciones de procesos hijo' para que el sistema nunca mate el proceso QEMU del servidor (Exit 137).",
            Lang.RU to "Откройте Настройки > Для разработчиков > включите «Отключить ограничения дочерних процессов», чтобы система не завершала процесс QEMU-сервера (Exit 137).",
        ),
        "guide.antidc.short" to mapOf(
            Lang.ID to "Sering \"Lost Connection\"? Tap \"HP suka ngadat?\" buat solusinya.",
            Lang.EN to "\"Lost Connection\" a lot? Tap \"Phone acting up?\" for the fixes.",
            Lang.TL to "Palaging \"Lost Connection\"? Tap \"Nagpapasaway ang phone mo?\" para sa fix.",
            Lang.ES to "Si ves 'Lost Connection' al pasar a un juego a pantalla completa, toca 'Soluciones' para ver los pasos.",
            Lang.RU to "Если часто появляется «Lost Connection» при переходе в полноэкранную игру, нажмите «Решения» для шагов стабилизации.",
        ),
        "folder.has.cfg" to mapOf(
            Lang.ID to "Mantap — server.cfg dan gamemode kamu ada di sini.",
            Lang.EN to "Good — server.cfg and your gamemode are right here.",
            Lang.TL to "Ayos — nandito ang server.cfg at gamemode mo.",
            Lang.ES to "El servidor leerá tu server.cfg y gamemode directamente de esta carpeta.",
            Lang.RU to "Сервер будет читать server.cfg и gamemode напрямую из этой папки.",
        ),
        "folder.no.cfg" to mapOf(
            Lang.ID to "Belum ada server.cfg di sini. Taruh gamemode kamu, atau atur dari tab Config.",
            Lang.EN to "No server.cfg here yet. Drop your gamemode in, or set it up from the Config tab.",
            Lang.TL to "Wala pang server.cfg dito. Ilagay ang gamemode mo, o i-setup sa Config tab.",
            Lang.ES to "Aún no se encuentra server.cfg en esta carpeta. Pon tu gamemode aquí o edita los ajustes en la pestaña Config.",
            Lang.RU to "server.cfg пока не найден в этой папке. Поместите gamemode сюда или измените настройки во вкладке Config.",
        ),
    )
}
