crash_handler
=============

Penangkap crash, termasuk crash pada proses native server.

English
-------

Crash capture, including crashes of the native server process.

Berkas
------

``Handler.kt``
    ``Thread.UncaughtExceptionHandler`` untuk exception Kotlin/Java.

``Manager.kt``
    Mengatur siklus crash: mengumpulkan konteks, menulis laporan, lalu memilih
    apakah aplikasi dihentikan atau dipulihkan.

``Report.kt``
    Bentuk laporan (stack trace, versi aplikasi, perangkat, log terakhir).

``Dialog.kt``
    Dialog pemulihan: lihat laporan, salin, kirim, atau lanjutkan.

Catatan
-------

Laporan crash tidak pernah diunggah otomatis; pengguna yang memutuskan untuk
menyalin atau mengirimnya.
