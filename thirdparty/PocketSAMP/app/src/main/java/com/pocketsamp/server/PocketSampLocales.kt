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
    )
}
