Xed Editor Help Guide
=====================

This guide covers the built-in Xed Editor inside PawnMC — its interface, its editing features, and the small conveniences that are easy to miss. Follow each section to get familiar with everything the editor can do.

Panduan Xed Editor
------------------

Panduan lengkap untuk Xed Editor bawaan PawnMC — antarmuka, fitur penyuntingan, dan kemudahan kecil yang mudah terlewat. Ikuti setiap bagian untuk mengenal semuanya.

.. contents:: Daftar Isi
   :local:
   :depth: 2

Antarmuka Utama
---------------

**Indonesia**

Editor terbuka dengan file aktif yang dipilih di layar utama atau dari panel workspace. Di bagian atas terdapat bilah judul dengan nama file dan tombol menu; di bawahnya adalah kanvas kode; di bagian paling bawah ada baris status yang menampilkan posisi kursor (baris-kolom) dan status penyimpanan.

**English (US)**

The editor opens with the active file chosen on the main screen or from the workspace panel. The top bar shows the file name and the menu button; below it is the code canvas; the status bar at the bottom shows the cursor position (line-column) and the save state.

**Tagalog**

Bubuksan ang editor na may aktibong file mula sa main screen o sa workspace panel. Nasa itaas ang file name at menu button; sa ibaba ang code canvas; sa pinakailalim ang status bar na may posisyon ng cursor at save state.

**Español**

El editor abre el archivo activo elegido en la pantalla principal o en el panel de workspace. La barra superior muestra el nombre del archivo y el botón de menú; debajo está el lienzo de código; la barra de estado inferior indica la posición del cursor y el estado de guardado.

**Русский**

Редактор открывается с активным файлом, выбранным на главном экране или в панели workspace. Вверху — имя файла и кнопка меню; ниже — область кода; в самом низу строка состояния с позицией курсора и состоянием сохранения.

Workspace
---------

**Indonesia**

* Buka folder sebagai workspace lewat menu "Show Workspace Panel".
* Hingga tiga workspace bisa terbuka sekaligus; masing-masing punya pohon file dan tab sendiri.
* Tab yang terbuka dan dokumen aktif diingat antar sesi, sehingga saat aplikasi dibuka lagi semuanya kembali seperti sebelumnya.
* Folder workspace juga disimpan di ``Documents/PawnMC/workspace/workspace.json``, sehingga tetap pulih bahkan setelah aplikasi diinstal ulang.

**English (US)**

* Open a folder as a workspace via the "Show Workspace Panel" menu item.
* Up to three workspaces can be open at once; each keeps its own file tree and tabs.
* Open tabs and the active document are remembered between sessions, so reopening the app restores everything.
* Workspace folders are also stored in ``Documents/PawnMC/workspace/workspace.json``, so they survive even a full reinstall.

**Tagalog**

* Buksan ang folder bilang workspace gamit ang "Show Workspace Panel".
* Hanggang tatlong workspace ang pwedeng buksan nang sabay; may sariling file tree at tabs ang bawat isa.
* Naalala ang mga tab at aktibong dokumento sa pagitan ng mga session.
* Nakaimbak din ang folders sa ``Documents/PawnMC/workspace/workspace.json`` para bumalik kahit mag-reinstall.

**Español**

* Abre una carpeta como workspace desde "Show Workspace Panel".
* Se pueden abrir hasta tres workspaces a la vez; cada uno tiene su propio árbol de archivos y pestañas.
* Las pestañas abiertas y el documento activo se recuerdan entre sesiones.
* Las carpetas también se guardan en ``Documents/PawnMC/workspace/workspace.json``, así sobreviven a una reinstalación.

**Русский**

* Откройте папку как workspace через пункт "Show Workspace Panel".
* Одновременно может быть открыто до трёх workspace; у каждого своё дерево файлов и вкладки.
* Открытые вкладки и активный документ сохраняются между запусками.
* Папки также хранятся в ``Documents/PawnMC/workspace/workspace.json`` и восстанавливаются даже после переустановки.

Fitur yang Jarang Diketahui
---------------------------

**Indonesia**

* **Lompat ke Baris / Kolom** — menu "Jump to Line..." menerima nomor baris dan, lewat "Tampilkan Kolom", nomor kolom. Nomor berbasis satu (baris 1 adalah baris pertama).
* **Tutup Otomatis Kurung** — opsi "Enable/Disable auto-close brackets" tepat di bawah "Jump to Line...". Saat *mati* (bawaan), mengetik ``(``, ``{``, ``[`` tidak pernah menyisipkan penutupnya, jadi ``()`` atau ``{}`` cepat tidak pernah berlipat. Saat *nyala*, pasangan disisipkan otomatis dan mengetik penutup tepat setelahnya melewati penutup yang sudah ada alih-alih menambah yang baru.
* **Lompat ke Kurung Berpasangan** — menu "Jump to Matching Bracket" melompat ke pasangan ``()``/``{}``/``[]`` dari posisi kursor.
* **Quick Keys** — bilah simbol cepat di bawah kanvas untuk karakter yang jarang ada di keyboard ponsel (``;``, ``(``, ``"``, dan lain-lain); bisa disembunyikan lewat menu.
* **Nomor Baris + Kolom** — gutter menampilkan ``baris-kolom`` dengan label yang mengikuti kursor.
* **Pencarian & Ganti seluruh workspace** — dari panel workspace, pencarian dan replace berjalan di semua file dalam folder yang terbuka.
* **Hash Generator** — menu "Hash Generator" menghitung hash (SHA-256 dan lainnya) untuk teks yang ditulis atau file yang dipilih.
* **Developer Portal** — terminal mini (perintah ``help``, ``docs``, ``pawncc``, ``switch``, ``ls``, ``time``, ``deviceinfo``) untuk kompilasi cepat tanpa meninggalkan editor.
* **Word Wrap / Read-Only / Line Numbers** — ketiganya bisa di-toggle dari menu dan hanya berlaku untuk sesi ini.

**English (US)**

* **Jump to Line / Column** — the "Jump to Line..." menu item accepts a line number and, via "Show Column", a column number. Numbers are one-based (line 1 is the first line).
* **Auto-close Brackets** — the "Enable/Disable auto-close brackets" option right below "Jump to Line...". When *off* (the default), typing ``(``, ``{``, ``[`` never inserts its closer, so a fast ``()`` or ``{}`` never doubles. When *on*, the pair is inserted automatically and typing the closer right after it steps over the existing one instead of adding another.
* **Jump to Matching Bracket** — "Jump to Matching Bracket" jumps to the matching ``()``/``{}``/``[]`` from the cursor.
* **Quick Keys** — the quick symbol bar under the canvas for characters the phone keyboard hides (``;``, ``(``, ``"``, and so on); hide it from the menu.
* **Line + Column Numbers** — the gutter shows ``line-column`` with a label that follows the cursor.
* **Workspace-wide Search & Replace** — from the workspace panel, search and replace run over every file in the open folder.
* **Hash Generator** — the "Hash Generator" menu computes hashes (SHA-256 and friends) for typed text or a picked file.
* **Developer Portal** — a mini terminal (``help``, ``docs``, ``pawncc``, ``switch``, ``ls``, ``time``, ``deviceinfo``) for quick compiles without leaving the editor.
* **Word Wrap / Read-Only / Line Numbers** — all three toggle from the menu and only last for the session.

**Tagalog**

* **Jump to Line / Column** — tinatanggap ng "Jump to Line..." ang line number at, sa "Show Column", ang column number.
* **Auto-close Brackets** — ang "Enable/Disable auto-close brackets" option pagkatapos ng "Jump to Line...". Kapag *off* (default), walang auto-insert na closing bracket; kapag *on*, awtomatikong mailalagay ang pair.
* **Quick Keys** — mabilisang simbol bar para sa mga karakter na madalang sa phone keyboard.
* **Search & Replace** — tumatakbo sa lahat ng file ng bukas na folder.
* **Hash Generator** — kalkulado ng hash para sa teksto o file.
* **Developer Portal** — maliit na terminal para sa mabilisang compile.

**Español**

* **Ir a línea / columna** — "Jump to Line..." acepta número de línea y, con "Show Column", número de columna (base 1).
* **Cierre automático de llaves** — la opción "Enable/Disable auto-close brackets" justo debajo de "Jump to Line...". Desactivada (por defecto) nunca inserta el cierre; activada, inserta el par y escribir el cierre salta sobre el existente.
* **Quick Keys** — barra de símbolos rápidos bajo el lienzo.
* **Búsqueda y reemplazo** — sobre todos los archivos de la carpeta abierta.
* **Hash Generator** — calcula hashes de texto o archivo.
* **Developer Portal** — terminal mínimo para compilar rápido.

**Русский**

* **Переход к строке / колонке** — "Jump to Line..." принимает номер строки и, через "Show Column", номер колонки (нумерация с 1).
* **Автозакрытие скобок** — опция "Enable/Disable auto-close brackets" сразу под "Jump to Line...". Выключена (по умолчанию) — закрывающая скобка никогда не вставляется сама; включена — пара вставляется, а ввод закрывающей перескакивает через уже стоящую.
* **Quick Keys** — панель быстрых символов под областью кода.
* **Поиск и замена** — по всем файлам открытой папки.
* **Hash Generator** — вычисляет хэш текста или файла.
* **Developer Portal** — мини-терминал для быстрой компиляции.

Menyimpan dan Kompilasi
-----------------------

**Indonesia**

"Save" menulis file aktif; "Save All" menulis setiap file yang berubah di semua workspace yang terbuka. Menekan kompilasi dari editor menyimpan semuanya terlebih dahulu — kompilasi dibatalkan bila ada file yang gagal disimpan. File sementara ``blank.pwn`` tidak bisa disimpan: gunakan "Save As" untuk membuat file Pawn asli.

**English (US)**

"Save" writes the active file; "Save All" writes every changed file across all open workspaces. Compiling from the editor saves everything first — the compile is cancelled when any file fails to save. The temporary ``blank.pwn`` cannot be saved: use "Save As" to create a real Pawn file.

**Tagalog**

Ang "Save" ay nagsusulat sa aktibong file; ang "Save All" sa lahat ng nabago. Bago mag-compile, lahat ay sina-save muna — kanselado ang compile kapag may file na hindi na-save. Hindi ma-save ang temporaryong ``blank.pwn``: gumamit ng "Save As".

**Español**

"Save" escribe el archivo activo; "Save All" escribe todos los modificados. Antes de compilar se guarda todo — la compilación se cancela si algún archivo no se guarda. El temporal ``blank.pwn`` no se puede guardar: usa "Save As".

**Русский**

"Save" записывает активный файл; "Save All" — все изменённые во всех открытых workspace. Перед компиляцией всё сохраняется — при ошибке сохранения компиляция отменяется. Временный ``blank.pwn`` сохранить нельзя: используйте "Save As".

Pintasan Keyboard
-----------------

**Indonesia**

Dengan keyboard fisik: ``Ctrl+A`` pilih semua, ``Ctrl+C`` salin, ``Ctrl+X`` potong, ``Ctrl+V`` tempel, ``Delete`` di awal baris kosong menghapus satu tingkat indentasi.

**English (US)**

With a hardware keyboard: ``Ctrl+A`` select all, ``Ctrl+C`` copy, ``Ctrl+X`` cut, ``Ctrl+V`` paste, ``Delete`` at an empty indent removes one indent level.

**Tagalog**

Sa physical keyboard: ``Ctrl+A`` piliin lahat, ``Ctrl+C`` kopyahin, ``Ctrl+X`` i-cut, ``Ctrl+V`` i-paste.

**Español**

Con teclado físico: ``Ctrl+A`` seleccionar todo, ``Ctrl+C`` copiar, ``Ctrl+X`` cortar, ``Ctrl+V`` pegar.

**Русский**

С аппаратной клавиатурой: ``Ctrl+A`` — выделить всё, ``Ctrl+C`` — копировать, ``Ctrl+X`` — вырезать, ``Ctrl+V`` — вставить.
