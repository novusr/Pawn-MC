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
object PocketSampLocales {

    enum class Lang { ID, EN, ES, RU }

    /** The language of the running UI; the host (PawnMC) sets it at launch. */
    @Volatile
    var current: Lang = Lang.EN
        private set

    /** Called by the host before [PocketSamp.launch] so the UI matches the PawnMC setting. */
    fun setLanguage(lang: Lang) {
        current = lang
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
            Lang.ID to "Kembali ke PawnMC",
            Lang.EN to "Back to PawnMC",
            Lang.ES to "Volver a PawnMC",
            Lang.RU to "Назад в PawnMC",
        ),
        "tagline" to mapOf(
            Lang.ID to "Runtime portabel SA-MP & MySQL lokal",
            Lang.EN to "Local SA-MP & MySQL Portable Runtime",
            Lang.ES to "Runtime portátil local de SA-MP y MySQL",
            Lang.RU to "Локальный переносимый рантайм SA-MP и MySQL",
        ),
        "status.online" to mapOf(
            Lang.ID to "DARING",
            Lang.EN to "ONLINE",
            Lang.ES to "EN LÍNEA",
            Lang.RU to "В СЕТИ",
        ),
        "status.starting" to mapOf(
            Lang.ID to "MEMULAI",
            Lang.EN to "STARTING",
            Lang.ES to "INICIANDO",
            Lang.RU to "ЗАПУСК",
        ),
        "status.stopping" to mapOf(
            Lang.ID to "MENGHENTIKAN",
            Lang.EN to "STOPPING",
            Lang.ES to "DETENIENDO",
            Lang.RU to "ОСТАНОВКА",
        ),
        "status.offline" to mapOf(
            Lang.ID to "LUAR DINARING",
            Lang.EN to "OFFLINE",
            Lang.ES to "SIN CONEXIÓN",
            Lang.RU to "НЕ В СЕТИ",
        ),
        "action.start" to mapOf(
            Lang.ID to "MULAI SERVER",
            Lang.EN to "START SERVER",
            Lang.ES to "INICIAR SERVIDOR",
            Lang.RU to "ЗАПУСТИТЬ СЕРВЕР",
        ),
        "action.stop" to mapOf(
            Lang.ID to "HENTIKAN SERVER",
            Lang.EN to "STOP SERVER",
            Lang.ES to "DETENER SERVIDOR",
            Lang.RU to "ОСТАНОВИТЬ СЕРВЕР",
        ),
        "db.running" to mapOf(
            Lang.ID to "AKTIF",
            Lang.EN to "RUNNING",
            Lang.ES to "ACTIVO",
            Lang.RU to "РАБОТАЕТ",
        ),
        "perm.file.title" to mapOf(
            Lang.ID to "Izin Akses File Diperlukan",
            Lang.EN to "File access permission required",
            Lang.ES to "Se requiere permiso de acceso a archivos",
            Lang.RU to "Требуется разрешение на доступ к файлам",
        ),
        "perm.file.grant" to mapOf(
            Lang.ID to "Beri Izin Akses File",
            Lang.EN to "Grant file access",
            Lang.ES to "Conceder acceso a archivos",
            Lang.RU to "Разрешить доступ к файлам",
        ),
        "perm.battery.title" to mapOf(
            Lang.ID to "Stabilkan Proses Latar Belakang",
            Lang.EN to "Stabilise the background process",
            Lang.ES to "Estabilizar el proceso en segundo plano",
            Lang.RU to "Стабилизировать фоновый процесс",
        ),
        "perm.battery.action" to mapOf(
            Lang.ID to "Nonaktifkan Optimasi Baterai",
            Lang.EN to "Disable battery optimisation",
            Lang.ES to "Desactivar la optimización de batería",
            Lang.RU to "Отключить оптимизацию батареи",
        ),
        "perm.close" to mapOf(
            Lang.ID to "Tutup",
            Lang.EN to "Close",
            Lang.ES to "Cerrar",
            Lang.RU to "Закрыть",
        ),
        "perm.guide" to mapOf(
            Lang.ID to "Solusi HP",
            Lang.EN to "Device fixes",
            Lang.ES to "Soluciones del dispositivo",
            Lang.RU to "Решения для устройства",
        ),
        "folder.pick" to mapOf(
            Lang.ID to "Pilih Folder",
            Lang.EN to "Pick folder",
            Lang.ES to "Elegir carpeta",
            Lang.RU to "Выбрать папку",
        ),
        "folder.reset" to mapOf(
            Lang.ID to "Reset Default",
            Lang.EN to "Reset default",
            Lang.ES to "Restablecer",
            Lang.RU to "Сбросить",
        ),
        "folder.reset.done" to mapOf(
            Lang.ID to "Direset ke default PocketSAMP",
            Lang.EN to "Reset to the PocketSAMP default",
            Lang.ES to "Restablecido al valor de PocketSAMP",
            Lang.RU to "Сброшено на значение PocketSAMP",
        ),
        "folder.gamemode" to mapOf(
            Lang.ID to "Folder Gamemode",
            Lang.EN to "Gamemode folder",
            Lang.ES to "Carpeta de gamemode",
            Lang.RU to "Папка gamemode",
        ),
        "tab.dashboard" to mapOf(
            Lang.ID to "Dasbor",
            Lang.EN to "Dashboard",
            Lang.ES to "Panel",
            Lang.RU to "Панель",
        ),
        "tab.console" to mapOf(
            Lang.ID to "Konsol",
            Lang.EN to "Console",
            Lang.ES to "Consola",
            Lang.RU to "Консоль",
        ),
        "tab.database" to mapOf(
            Lang.ID to "Basis Data",
            Lang.EN to "Database",
            Lang.ES to "Base de datos",
            Lang.RU to "База данных",
        ),
        "tab.crashes" to mapOf(
            Lang.ID to "Log Crash",
            Lang.EN to "Crash Logs",
            Lang.ES to "Registros de fallos",
            Lang.RU to "Журнал сбоев",
        ),
        "tab.config" to mapOf(
            Lang.ID to "Konfig",
            Lang.EN to "Config",
            Lang.ES to "Config",
            Lang.RU to "Настройки",
        ),
        "config.title" to mapOf(
            Lang.ID to "Konfigurasi Server",
            Lang.EN to "Server Configuration",
            Lang.ES to "Configuración del servidor",
            Lang.RU to "Конфигурация сервера",
        ),
        "config.subtitle" to mapOf(
            Lang.ID to "Editor server.cfg",
            Lang.EN to "server.cfg editor",
            Lang.ES to "Editor de server.cfg",
            Lang.RU to "Редактор server.cfg",
        ),
        "config.save" to mapOf(
            Lang.ID to "Simpan",
            Lang.EN to "Save",
            Lang.ES to "Guardar",
            Lang.RU to "Сохранить",
        ),
        "config.saved" to mapOf(
            Lang.ID to "server.cfg berhasil disimpan!",
            Lang.EN to "server.cfg saved successfully!",
            Lang.ES to "¡server.cfg guardado!",
            Lang.RU to "server.cfg успешно сохранён!",
        ),
        "console.title" to mapOf(
            Lang.ID to "Konsol Server",
            Lang.EN to "Server Console",
            Lang.ES to "Consola del servidor",
            Lang.RU to "Консоль сервера",
        ),
        "db.title" to mapOf(
            Lang.ID to "Manajer Basis Data",
            Lang.EN to "Database Manager",
            Lang.ES to "Gestor de base de datos",
            Lang.RU to "Менеджер базы данных",
        ),
        "db.offline" to mapOf(
            Lang.ID to "Server basis data tidak aktif",
            Lang.EN to "Database server is offline",
            Lang.ES to "El servidor de base de datos está desconectado",
            Lang.RU to "Сервер базы данных не активен",
        ),
        "sql.query" to mapOf(
            Lang.ID to "Kueri SQL",
            Lang.EN to "SQL Query",
            Lang.ES to "Consulta SQL",
            Lang.RU to "SQL-запрос",
        ),
        "sql.run" to mapOf(
            Lang.ID to "Jalankan",
            Lang.EN to "Run",
            Lang.ES to "Ejecutar",
            Lang.RU to "Запустить",
        ),
        "crash.details" to mapOf(
            Lang.ID to "Detail Crash",
            Lang.EN to "Crash details",
            Lang.ES to "Detalles del fallo",
            Lang.RU to "Детали сбоя",
        ),
        "crash.copy" to mapOf(
            Lang.ID to "Salin Log Crash",
            Lang.EN to "Copy crash log",
            Lang.ES to "Copiar registro de fallos",
            Lang.RU to "Копировать журнал сбоя",
        ),
        "crash.reopen" to mapOf(
            Lang.ID to "Buka Ulang App",
            Lang.EN to "Reopen the app",
            Lang.ES to "Reabrir la app",
            Lang.RU to "Перезапустить приложение",
        ),
        "crash.delete" to mapOf(
            Lang.ID to "Hapus",
            Lang.EN to "Delete",
            Lang.ES to "Eliminar",
            Lang.RU to "Удалить",
        ),
        "crash.delete.all" to mapOf(
            Lang.ID to "Hapus Semua",
            Lang.EN to "Delete all",
            Lang.ES to "Eliminar todo",
            Lang.RU to "Удалить все",
        ),
        "crash.device" to mapOf(
            Lang.ID to "Informasi Perangkat",
            Lang.EN to "Device information",
            Lang.ES to "Información del dispositivo",
            Lang.RU to "Информация об устройстве",
        ),
        // --- strings that used to be hardcoded in Indonesian on the dashboard ------
        "folder.changed" to mapOf(
            Lang.ID to "Folder gamemode diubah ke",
            Lang.EN to "Gamemode folder changed to",
            Lang.ES to "Carpeta de gamemode cambiada a",
            Lang.RU to "Папка gamemode изменена на",
        ),
        "folder.pick.failed" to mapOf(
            Lang.ID to "Gagal mendapatkan path folder yang dipilih.",
            Lang.EN to "Could not resolve the selected folder path.",
            Lang.ES to "No se pudo resolver la ruta de la carpeta elegida.",
            Lang.RU to "Не удалось определить путь выбранной папки.",
        ),
        "dash.local.title" to mapOf(
            Lang.ID to "Main di HP Ini (Satu HP)",
            Lang.EN to "Play on this phone (single device)",
            Lang.ES to "Jugar en este teléfono (un equipo)",
            Lang.RU to "Игра на этом телефоне (одно устройство)",
        ),
        "dash.local.note" to mapOf(
            Lang.ID to "⭐ Sangat Disarankan: Koneksi internal langsung (0ms ping), tidak bergantung sinyal Wi-Fi, dan anti-putus saat beralih/membuka game.",
            Lang.EN to "⭐ Highly recommended: direct internal connection (0 ms ping), independent of Wi-Fi signal, and it never drops when switching to or opening the game.",
            Lang.ES to "⭐ Muy recomendado: conexión interna directa (0 ms de ping), sin depender de la señal Wi-Fi, y sin cortes al cambiar o abrir el juego.",
            Lang.RU to "⭐ Настоятельно рекомендуется: прямое внутреннее соединение (пинг 0 мс), не зависит от сигнала Wi-Fi и не обрывается при переключении на игру.",
        ),
        "dash.lan.title" to mapOf(
            Lang.ID to "Main Bareng Teman (Satu Wi-Fi)",
            Lang.EN to "Play with friends (same Wi-Fi)",
            Lang.ES to "Jugar con amigos (mismo Wi-Fi)",
            Lang.RU to "Игра с друзьями (один Wi-Fi)",
        ),
        "dash.lan.note" to mapOf(
            Lang.ID to "Bagikan alamat ini jika teman Anda ingin bergabung dari HP lain via Wi-Fi/Hotspot yang sama.",
            Lang.EN to "Share this address so friends can join from another phone over the same Wi-Fi/hotspot.",
            Lang.ES to "Comparte esta dirección para que amigos se unan desde otro teléfono por el mismo Wi-Fi/hotspot.",
            Lang.RU to "Поделитесь этим адресом, чтобы друзья могли подключиться с другого телефона через тот же Wi-Fi/точку доступа.",
        ),
        "copy.done" to mapOf(
            Lang.ID to "Disalin:",
            Lang.EN to "Copied:",
            Lang.ES to "Copiado:",
            Lang.RU to "Скопировано:",
        ),
        "copy.path.done" to mapOf(
            Lang.ID to "Path disalin ke clipboard!",
            Lang.EN to "Path copied to the clipboard!",
            Lang.ES to "¡Ruta copiada al portapapeles!",
            Lang.RU to "Путь скопирован в буфер обмена!",
        ),
        "perm.file.desc" to mapOf(
            Lang.ID to "Android memerlukan izin 'Akses Semua File' agar server dapat membaca folder gamemode Anda secara langsung.",
            Lang.EN to "Android requires the 'All files access' permission so the server can read your gamemode folder directly.",
            Lang.ES to "Android requiere el permiso 'Acceso a todos los archivos' para que el servidor lea tu carpeta de gamemode directamente.",
            Lang.RU to "Android требует разрешение «Доступ ко всем файлам», чтобы сервер мог напрямую читать вашу папку gamemode.",
        ),
        "perm.file.settings" to mapOf(
            Lang.ID to "Buka Pengaturan -> Aplikasi -> PocketSAMP -> Izin",
            Lang.EN to "Open Settings -> Apps -> PocketSAMP -> Permissions",
            Lang.ES to "Abre Ajustes -> Apps -> PocketSAMP -> Permisos",
            Lang.RU to "Откройте Настройки -> Приложения -> PocketSAMP -> Разрешения",
        ),
        "perm.file.button" to mapOf(
            Lang.ID to "Beri Izin Akses File",
            Lang.EN to "Grant file access",
            Lang.ES to "Conceder acceso a archivos",
            Lang.RU to "Разрешить доступ к файлам",
        ),
        "perm.battery.desc" to mapOf(
            Lang.ID to "Nonaktifkan optimasi baterai agar Android tidak membatasi/menjeda server saat Anda membuka game/launcher SA-MP.",
            Lang.EN to "Turn off battery optimisation so Android does not throttle or pause the server while you open the SA-MP game/launcher.",
            Lang.ES to "Desactiva la optimización de batería para que Android no limite ni pause el servidor al abrir el juego/launcher SA-MP.",
            Lang.RU to "Отключите оптимизацию батареи, чтобы Android не ограничивал и не приостанавливал сервер при запуске SA-MP.",
        ),
        "perm.battery.button" to mapOf(
            Lang.ID to "Nonaktifkan Optimasi Baterai",
            Lang.EN to "Disable battery optimisation",
            Lang.ES to "Desactivar optimización de batería",
            Lang.RU to "Отключить оптимизацию батареи",
        ),
        "guide.antidc.title" to mapOf(
            Lang.ID to "Panduan Anti-Disconnect (HyperOS / Android 14)",
            Lang.EN to "Anti-disconnect guide (HyperOS / Android 14)",
            Lang.ES to "Guía antidesconexión (HyperOS / Android 14)",
            Lang.RU to "Гид по защите от обрывов (HyperOS / Android 14)",
        ),
        "guide.antidc.step1" to mapOf(
            Lang.ID to "1. Gembok Aplikasi di Recent Apps (Penting di Xiaomi/HyperOS):",
            Lang.EN to "1. Lock the app in Recent Apps (important on Xiaomi/HyperOS):",
            Lang.ES to "1. Bloquea la app en Recent Apps (importante en Xiaomi/HyperOS):",
            Lang.RU to "1. Закрепите приложение в списке недавних (важно для Xiaomi/HyperOS):",
        ),
        "guide.antidc.step1.desc" to mapOf(
            Lang.ID to "Buka menu Recent Apps (Multitask HP) -> Tekan & tahan jendela PocketSAMP -> Ketuk ikon Gembok (Lock). Ini mencegah OS membekukan server saat Anda membuka game full screen.",
            Lang.EN to "Open Recent Apps (multitask) -> press and hold the PocketSAMP window -> tap the lock icon. This stops the OS from freezing the server while a full-screen game is open.",
            Lang.ES to "Abre Recent Apps (multitarea) -> mantén pulsada la ventana de PocketSAMP -> toca el candado. Así el SO no congela el servidor con un juego a pantalla completa.",
            Lang.RU to "Откройте недавние приложения (многозадачность) -> удерживайте окно PocketSAMP -> нажмите значок замка. Тогда ОС не заморозит сервер при полноэкранной игре.",
        ),
        "guide.antidc.step2" to mapOf(
            Lang.ID to "2. Gunakan IP 127.0.0.1:7777:",
            Lang.EN to "2. Use the 127.0.0.1:7777 IP:",
            Lang.ES to "2. Usa la IP 127.0.0.1:7777:",
            Lang.RU to "2. Используйте IP 127.0.0.1:7777:",
        ),
        "guide.antidc.step2.desc" to mapOf(
            Lang.ID to "Jika bermain di HP ini, masukkan 127.0.0.1:7777 di launcher SA-MP Anda. Jalur loopback internal ini tidak pernah diputus oleh Wi-Fi sleep Android.",
            Lang.EN to "If you play on this phone, enter 127.0.0.1:7777 in your SA-MP launcher. This internal loopback route is never cut by Android Wi-Fi sleep.",
            Lang.ES to "Si juegas en este teléfono, introduce 127.0.0.1:7777 en tu launcher SA-MP. Esta ruta loopback interna nunca la corta el Wi-Fi sleep de Android.",
            Lang.RU to "Если играете на этом телефоне, введите 127.0.0.1:7777 в SA-MP лаунчере. Этот внутренний loopback-путь никогда не обрывается спящим Wi-Fi Android.",
        ),
        "guide.antidc.step3" to mapOf(
            Lang.ID to "3. Matikan Pembatasan Proses Turunan (Android 12+):",
            Lang.EN to "3. Disable child-process restrictions (Android 12+):",
            Lang.ES to "3. Desactiva las restricciones de procesos hijo (Android 12+):",
            Lang.RU to "3. Отключите ограничения дочерних процессов (Android 12+):",
        ),
        "guide.antidc.step3.desc" to mapOf(
            Lang.ID to "Buka Pengaturan HP > Opsi Pengembang (Developer Options) > Aktifkan 'Nonaktifkan pembatasan proses turunan' (Disable child process restrictions) agar proses server QEMU tidak dibunuh sistem (Exit 137).",
            Lang.EN to "Open phone Settings > Developer options > enable 'Disable child process restrictions' so the system never kills the QEMU server process (Exit 137).",
            Lang.ES to "Abre Ajustes > Opciones de desarrollador > activa 'Desactivar restricciones de procesos hijo' para que el sistema nunca mate el proceso QEMU del servidor (Exit 137).",
            Lang.RU to "Откройте Настройки > Для разработчиков > включите «Отключить ограничения дочерних процессов», чтобы система не завершала процесс QEMU-сервера (Exit 137).",
        ),
        "guide.antidc.short" to mapOf(
            Lang.ID to "Jika sering muncul 'Lost Connection' saat beralih ke game full screen, ketuk 'Solusi HP' untuk langkah penstabilan.",
            Lang.EN to "If you often see 'Lost Connection' when switching to a full-screen game, tap 'Phone fixes' for the stabilisation steps.",
            Lang.ES to "Si ves 'Lost Connection' al pasar a un juego a pantalla completa, toca 'Soluciones' para ver los pasos.",
            Lang.RU to "Если часто появляется «Lost Connection» при переходе в полноэкранную игру, нажмите «Решения» для шагов стабилизации.",
        ),
        "folder.has.cfg" to mapOf(
            Lang.ID to "Server akan membaca server.cfg & gamemode Anda langsung dari folder ini.",
            Lang.EN to "The server will read your server.cfg & gamemode directly from this folder.",
            Lang.ES to "El servidor leerá tu server.cfg y gamemode directamente de esta carpeta.",
            Lang.RU to "Сервер будет читать server.cfg и gamemode напрямую из этой папки.",
        ),
        "folder.no.cfg" to mapOf(
            Lang.ID to "server.cfg belum ditemukan di folder ini. Letakkan gamemode Anda di sini atau simpan setting di tab Config.",
            Lang.EN to "server.cfg was not found in this folder yet. Put your gamemode here or edit the settings in the Config tab.",
            Lang.ES to "Aún no se encuentra server.cfg en esta carpeta. Pon tu gamemode aquí o edita los ajustes en la pestaña Config.",
            Lang.RU to "server.cfg пока не найден в этой папке. Поместите gamemode сюда или измените настройки во вкладке Config.",
        ),
    )
}
