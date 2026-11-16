main
====

Layar utama PawnMC dan host seluruh aktivitas. Ini satu-satunya folder yang
menggabungkan tiga area utama: pemilihan file + kompilasi, pintu masuk ke
pengaturan, dan pintu masuk ke Xed Editor.

English
-------

PawnMC's main screen and the activity host. This is the only folder that joins the
three main areas: file selection + compilation, the door into Settings, and the
door into Xed Editor.

Berkas
------

``MainActivity.kt``
    Aktivitas tunggal. Menangani intent (file dari aplikasi lain, shortcut ke
    Xed), memilih antara ``MainScreen`` dan ``EditorScreen``, menjalankan
    kompilasi yang diminta editor, dan meneruskan ``diagnosticsRevision`` agar
    komentar ``[cause]`` langsung muncul.

``MainScreen.kt``
    Layout utama: header aksi, pemilih file, kartu status, ``OutputPanel`` (log
    kompilasi beserta scrollbar-nya), dan kartu sorotan pengaturan.

``MainViewModel.kt``
    Pemilik state kompilasi: file terpilih, jalannya compile, ``outputText``,
    ``lastExitCode``, dan ``diagnosticsRevision``.

``MainNotification.kt``
    Notifikasi sesi yang menampilkan proses kompilasi berjalan.

``SettingsTarget.kt``
    Enum tujuan deep-link ke bagian pengaturan.

Kartu sorotan pengaturan (``SettingsPromoCard``) tinggal di ``MainScreen.kt`` dan
membuka pengaturan tepat di bagian terkait lewat ``onOpenSettingsTarget``.
