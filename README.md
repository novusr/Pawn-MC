PawnMC
==========

_PawnMC_ expands the workflow of the Pawn scripting language with a complete, native compilation environment for your mobile device.

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

English | Notes<br>
Welcome to the official PawnMC repository.<br>
This is where PawnMC can be said to operate, and we sincerely welcome you here.<br>
PawnMC is an Android application specifically created to make your work as an Android Developer easier and faster.<br>
We place great hope in everyone who uses this application.<br>

## Documentation
See the [configuration folder](//github.com/novusr/Pawn-MC/tree/main/configuration) for documentation and guides on how to use this application and set up your mobile workspace.

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
