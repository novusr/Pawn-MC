service_handler
===============

Foreground service yang menahan proses server tetap hidup ketika aplikasi
berpindah ke latar belakang atau layar dimatikan.

English
-------

The foreground service that keeps the server process alive while the app is in
the background or the screen is off.

Berkas
------

``RuntimeService.kt``
    Notifikasi persisten dengan status server, tombol hentikan, dan pembaruan
    log terakhir. Service ini juga merapikan proses anak saat dihentikan, supaya
    tidak ada ``samp03svr`` atau MariaDB yatim setelah aplikasi keluar.
