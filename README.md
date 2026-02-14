PawnMC
==========

_PawnMC_ expands the workflow of the Pawn scripting language with a complete, native compilation environment for your mobile device.

With this application, you can use on-the-go development techniques, manage local libraries, and build your projects directly on Android to make programming in Pawn more accessible, flexible, and efficient.

_PawnMC_ introduces a fully portable workspace, supporting standard libraries, custom includes, and complex project directories. Code can be compiled entirely offline with high execution speed and instant error reporting. The compiler can be used to iterate on your game modes and filterscripts wherever you are, without relying on a desktop workspace and without interrupting your creative flow.

The underlying engine utilizes the native architecture of your Android device, removing the need to transfer files to a PC just to verify your code in most cases.

## Documentation
See the [repository](//github.com/novusr/Pawn-MC) for documentation and guides on how to use this application and set up your mobile workspace.

## Installation
Download the latest [release](//github.com/novusr/Pawn-MC/releases/latest) for your Android device and install the provided APK.

Open a `.pwn` script within the application and you are ready to compile.

## Configuration
This application allows you to optionally configure a number of compilation features, such as specific compiler versions, custom include paths, and build flags. These are fully adjustable directly within the app interface to ensure compatibility with different SA-MP or open.mp project structures.

## Building
Use Gradle to build the project from source on your environment. Requires Git for cloning submodules.

```bash
git clone --recursive https://github.com/novusr/Pawn-MC.git
cd Pawn-MC
./gradlew assembleDebug
