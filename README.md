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
Selamat datang di repositori resmi PawnMC.<br>
Di sini adalah tempat di mana PawnMC beroperasi, dan kami sangat menyambut kedatangan Anda dengan penuh rasa syukur.<br>
PawnMC adalah aplikasi Android yang secara khusus dibuat untuk mempermudah dan mempercepat proses pengembangan Anda sebagai developer Android.<br>
Kami juga ingin mengucapkan terima kasih yang sebesar-besarnya kepada para tester yang telah meluangkan waktu, tenaga, dan masukan berharga untuk membantu meningkatkan kualitas aplikasi ini.<br>
Apresiasi kami juga ditujukan kepada seluruh pengguna PawnMC yang telah mempercayai, menggunakan, dan mendukung perkembangan aplikasi ini dari waktu ke waktu.<br>
Semoga PawnMC terus bermanfaat, berkembang, dan menjadi tools yang membantu Anda dalam membuat proyek Pawn dengan lebih efisien.<br>
Discord Kami: https://discord.gg/2YqkmDvTch<br>

English | Notes<br>
Welcome to the official PawnMC repository.<br>
This is the place where PawnMC operates, and we warmly welcome you with great appreciation.<br>
PawnMC is an Android application specifically designed to make your work as an Android developer easier, faster, and more efficient.<br>
We would also like to express our sincere gratitude to all testers who have given their time, feedback, and support to help improve the quality of this app.<br>
Our appreciation also goes to all PawnMC users who have trusted, used, and supported this application throughout its development.<br>
We hope PawnMC will continue to be useful, grow further, and become a valuable tool for your Pawn development workflow.<br>
Official Community: https://discord.gg/2YqkmDvTch<br>

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
