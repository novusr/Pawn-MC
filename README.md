PawnMC
==========

_PawnMC_ expands the workflow of the Pawn scripting language with a complete, native compilation environment for your mobile device.
![img](https://raw.githubusercontent.com/novusr/Pawn-MC/refs/heads/main/configuration/content/PAWNMC.png)

With this application, you can use on-the-go development techniques, manage local libraries, and build your projects directly on Android to make programming in Pawn more accessible, flexible, and efficient.

_PawnMC_ introduces a fully portable workspace, supporting standard libraries, custom includes, and complex project directories. Code can be compiled entirely offline with high execution speed and instant error reporting. The compiler can be used to iterate on your game modes and filterscripts wherever you are, without relying on a desktop workspace and without interrupting your creative flow.

The underlying engine utilizes the native architecture of your Android device, removing the need to transfer files to a PC just to verify your code in most cases.

Indonesian | Tutorial
- https://youtu.be/ym9E1QH1ABU?si=xcL-mrqrx3RknBYs 
- https://youtu.be/sgG58JTytI4?si=r6r3TukcnwxD58SP
- https://youtu.be/xSit9n80DoM?si=tDVehEI6KHBvSZME 

Notes
==========
Indonesian | Notes<br>
Selamat Datang di repositori resmi dari PawnMC.<br>
Disini adalah tempat dimana PawnMC bisa dikatakan beroperasi, kami sangat menyambut baik kedatangan Anda disini.<br>
PawnMC Adalah aplikasi Android yang secara khusus diciptakan untuk mempermudah dan mempercepat tugas Anda, sebagai Developer Android.<br>
Kami sendiri sangat menempatkan harapan yang besar kepada kalian semua yang menggunakan aplikasi ini.<br>
Discord Kami: https://discord.gg/2YqkmDvTch<br>
-------------------------------------------------------<br>
Sangat disayangkan bahwasannya sebagian pengguna PawnMC beralih ke aplikasi serupa hanya karna PawnMC tidak memiliki akses untuk menyuting teks (Text Editor)<br>
PawnMC memang tidak memiliki Text Editor, karna sejak awal aplikasi ini dibangun dengan fokus untuk kompilasi dengan aneka fitur yang unik dan beragam<br>
Kami tidak bisa memaksakan untuk memasukan Text Editor luar ke PawnMC yang bisa merusak orisinalitas dari PawnMC itu sendiri.<br>
namun kami memiliki beberapa saran jika memang kalian membutuhkan Text Editor Android yang bisa dikatakan bisa untuk beriringan dengan PawnMC sebagai Compiler<br>
1. VSCode Dev (Link: https://vscode.dev/)
VSCode Dev itu seperti website penyuting teks tapi tentunya ini adalah website resmi Microsoft yang menyediakan layanan VSCode dalam peramban atau browser namun dengan batasan tentunya, seperti: tidak bisa mengakses terminal.
![img](https://raw.githubusercontent.com/novusr/Pawn-MC/refs/heads/main/configuration/content/VSCODE.png)
2. Xed Editor (Link: [Apps](https://github.com/Xed-Editor/Xed-Editor/releases/download/v3.4.5/xed-editor-v3.4.5.apk))
Xed Editor sendiri itu seperti aplikasi yang secara khusus bisa dikatakan adalah IDE yang cocok untuk dijadikan aplikasi tambahan sebagai penyuting teks dan di sisi lain PawnMC adalah Compiler nya
Xed Editor memiliki fitur seperti Syntax Highlight (Sintaks berwarna) dan dukungan banyak Bahasa serta mendukung penggunaan Terminal langsung seperti Termux
![img](https://raw.githubusercontent.com/novusr/Pawn-MC/refs/heads/main/configuration/content/XED.png)

English | Notes<br>
Welcome to the official PawnMC repository.<br>
This is where PawnMC can be said to operate, and we sincerely welcome you here.<br>
PawnMC is an Android application specifically created to make your work as an Android Developer easier and faster.<br>
We place great hope in everyone who uses this application.<br>
Official Community: https://discord.gg/2YqkmDvTch<br>
-------------------------------------------------------<br>
Unfortunately, some PawnMC users have switched to similar applications simply because PawnMC does not provide access to edit text through a Text Editor.<br>
PawnMC does not have a built-in Text Editor because, from the beginning, the application was developed with a focus on its Compiler (for compile), along with various unique and diverse features.<br>
We cannot simply integrate an external Text Editor into PawnMC, as doing so could compromise the originality of PawnMC itself.<br>
However, we have a few suggestions if you need an Android Text Editor that can work alongside PawnMC as your Compiler:<br>
1. VSCode Dev (Link: https://vscode.dev/)
VSCode Dev is essentially a web-based text editor. It is Microsoft's official VSCode service, providing VSCode directly through a web browser, although it comes with certain limitations, such as not having access to a terminal.
![img](https://raw.githubusercontent.com/novusr/Pawn-MC/refs/heads/main/configuration/content/VSCODE.png)
2. Xed Editor (Link: [Apps](https://github.com/Xed-Editor/Xed-Editor/releases/download/v3.4.5/xed-editor-v3.4.5.apk))
Xed Editor is an application that can be considered a suitable IDE to use alongside PawnMC as a Text Editor, while PawnMC serves as the Compiler.
Xed Editor provides features such as Syntax Highlighting, support for multiple programming languages, and direct Terminal access similar to Termux.
![img](https://raw.githubusercontent.com/novusr/Pawn-MC/refs/heads/main/configuration/content/XED.png)

## Documentation
See the [configuration folder](//github.com/novusr/Pawn-MC/tree/main/configuration) for documentation and guides on how to use this application and set up your mobile workspace.

See the project roadmap in [ROADMAP.md](ROADMAP.md) for a full architecture overview, feature breakdown, and file-by-file analysis of the current codebase.

## Installation
Download the latest [release](//github.com/novusr/Pawn-MC/releases/latest) for your Android device and install the provided APK.

## Configuration
This application allows you to optionally configure a number of compilation features, such as specific compiler versions, custom include paths, and build flags. These are fully adjustable directly within the app interface to ensure compatibility with different SA-MP or open.mp project structures.<br>
See: https://github.com/novusr/Pawn-MC/tree/main/configuration

## Building
Use Gradle to build the project from source on your environment. Requires Git for cloning submodules.

```bash
git clone --recursive https://github.com/novusr/Pawn-MC.git
cd Pawn-MC
./gradlew assembleDebug
