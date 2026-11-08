package com.rvdjv.pawnmc.user_interface.editor.portal

import com.rvdjv.pawnmc.apps_data.data_config.ContentConfig

/**
 * The `docs` markdown of the Developer Portal, written in the script itself.
 *
 * The text used to live in `_developer.toml` under `[terminal.docs]`; it was by
 * far the longest table in the asset and pushed every other key several hundred
 * lines down. Following the same pattern as the `blank.pwn` starter (content
 * authored directly in code), the markdown now lives here — same five
 * languages, English as the fallback, no asset read at all.
 */
internal object PortalDocs {

    /** Returns the docs markdown for [language]. */
    fun content(language: ContentConfig.AppLanguage): String = when (language) {
        ContentConfig.AppLanguage.ID -> ID
        ContentConfig.AppLanguage.ES -> ES
        ContentConfig.AppLanguage.RU -> RU
        ContentConfig.AppLanguage.TL -> TL
        ContentConfig.AppLanguage.EN -> EN
    }

    private const val EN = """# Developer Portal

This console runs the bundled **native pawncc** plus a set of built-in commands. Input is not an Android shell: only the commands listed here are recognized.

## Commands

- `help` prints the command list.
- `docs` displays this documentation.
- `pawncc <file> [flags...]` compiles the explicitly named file. Bare `pawncc` only prints usage.
- `switch [3.10.7|3.10.11]` selects the compiler version; without an argument, it toggles the console version.
- `clear` removes console history; the spelling `celar` is also accepted.
- `ls` lists the files and folders of the active workspace.
- `whoami`, `time`, `date`, `version`, `deviceinfo`, `github` print the console user name, clock, date, PawnMC version, device information, and the repository link.
- `which <command>` shows the location of a program or the name of a built-in command.

## Examples

```text
ls
pawncc gamemodes/mode.pwn -d=3
pawncc "gamemodes/test mode.pwn" -i=includes -o=mode.amx
switch 3.10.11
clear
```

Only flags written in the command are passed to pawncc. PawnMC Settings for flags, optimization, and include paths are not added. Specify needed options yourself, for example `-i=includes`. Source files must use `.pwn`, `.pawn`, `.p`, or `.inc` and stay inside the workspace.

## Limits

There is no `cd`, pipe, redirect, shell expansion, or launching other programs: `ls` only reads the workspace folder, not the whole filesystem. The console starts with **3.10.7**. If another native compiler library is already loaded by the app, restart after `switch` for the selection to take effect."""

    private const val ID = """# Developer Portal

Console ini menjalankan **pawncc native** dan sekumpulan perintah bawaan. Input bukan shell Android: hanya perintah yang tercantum di sini yang dikenali.

## Perintah

- `help` menampilkan daftar perintah.
- `docs` menampilkan dokumentasi ini.
- `pawncc <file> [flag...]` mengompilasi file yang disebut secara eksplisit. Perintah `pawncc` tanpa argumen hanya menampilkan cara pakai.
- `switch [3.10.7|3.10.11]` memilih versi compiler; tanpa argumen, versi console berganti.
- `clear` menghapus riwayat console; ejaan `celar` juga diterima.
- `ls` menampilkan file dan folder di workspace aktif.
- `whoami`, `time`, `date`, `version`, `deviceinfo`, `github` menampilkan nama pengguna console, jam, tanggal, versi PawnMC, informasi perangkat, dan tautan repositori.
- `which <perintah>` menunjukkan lokasi program atau nama perintah bawaan.

## Contoh

```text
ls
pawncc gamemodes/mode.pwn -d=3
pawncc "gamemodes/test mode.pwn" -i=includes -o=mode.amx
switch 3.10.11
clear
```

Hanya flag yang ditulis pada perintah yang diteruskan ke pawncc. Konfigurasi flag, optimisasi, dan include path dari Settings PawnMC tidak ditambahkan. Sertakan sendiri opsi yang diperlukan, misalnya `-i=includes`. File sumber harus berekstensi `.pwn`, `.pawn`, `.p`, atau `.inc` dan berada di dalam workspace.

## Batasan

Tidak ada `cd`, pipe, redirect, ekspansi shell, atau pemanggilan program lain: `ls` hanya membaca folder workspace, bukan seluruh sistem file. Versi console dimulai dari **3.10.7**. Jika library compiler versi lain sudah dimuat aplikasi, mulai ulang aplikasi setelah `switch` agar pilihan berlaku."""

    private const val TL = """# Developer Portal

Pinapatakbo ng console na ito ang **native pawncc** kasama ang mga built-in na command. Hindi shell ng Android ang input: mga command lang na nakalista dito ang kinikilala.

## Mga command

- `help` ipinapakita ang listahan ng command.
- `docs` ipinapakita ang dokumentasyong ito.
- `pawncc <file> [flags...]` kinokompile ang file na tahasang pinangalanan. Kung walang argumento, usage lang ang ipinapakita ng `pawncc`.
- `switch [3.10.7|3.10.11]` pumipili ng bersyon ng compiler; kung walang argumento, papagpalitin ang bersyon ng console.
- `clear` binubura ang history ng console; tinatanggap din ang baybay na `celar`.
- `ls` naglilista ng file at folder sa aktibong workspace.
- `whoami`, `time`, `date`, `version`, `deviceinfo`, `github` nagpapakita ng pangalan ng user, oras, petsa, bersyon ng PawnMC, impormasyon ng device, at link ng repository.
- `which <command>` nagpapakita ng lokasyon ng program o ng built-in na command.

## Mga halimbawa

```text
ls
pawncc gamemodes/mode.pwn -d=3
pawncc "gamemodes/test mode.pwn" -i=includes -o=mode.amx
switch 3.10.11
clear
```

Ang mga flag lang na nakasulat sa command ang ipinapasa sa pawncc. Hindi idinadagdag ang flag, optimisasyon, at include path mula sa Settings ng PawnMC. Ikaw mismo ang maglagay ng kailangang option, halimbawa `-i=includes`. Dapat `.pwn`, `.pawn`, `.p` o `.inc` ang source file at nasa loob ito ng workspace.

## Mga limitasyon

Walang `cd`, pipe, redirect, shell expansion, o pagpapatakbo ng ibang programa: binabasa lang ng `ls` ang folder ng workspace, hindi ang buong filesystem. Nagsisimula ang console sa **3.10.7**. Kung may ibang native compiler library na naka-load sa app, i-restart ito pagkatapos ng `switch` para magkabisa ang pinili."""

    private const val ES = """# Developer Portal

Esta consola ejecuta el **pawncc nativo** incluido y un conjunto de comandos integrados. La entrada no es una shell de Android: solo se reconocen los comandos listados aca.

## Comandos

- `help` muestra la lista de comandos.
- `docs` muestra esta documentacion.
- `pawncc <archivo> [flags...]` compila el archivo indicado explicitamente. `pawncc` sin argumentos solo muestra el modo de uso.
- `switch [3.10.7|3.10.11]` elige la version del compilador; sin argumento, alterna la version de la consola.
- `clear` borra el historial de la consola; tambien se acepta la grafia `celar`.
- `ls` lista los archivos y carpetas del workspace activo.
- `whoami`, `time`, `date`, `version`, `deviceinfo`, `github` muestran el usuario de la consola, la hora, la fecha, la version de PawnMC, la informacion del dispositivo y el enlace del repositorio.
- `which <comando>` muestra la ubicacion de un programa o el nombre de un comando integrado.

## Ejemplos

```text
ls
pawncc gamemodes/mode.pwn -d=3
pawncc "gamemodes/test mode.pwn" -i=includes -o=mode.amx
switch 3.10.11
clear
```

Solo se pasan a pawncc los flags escritos en el comando. No se agregan los flags, la optimizacion ni las rutas de include de la configuracion de PawnMC. Especifica vos mismo las opciones que necesites, por ejemplo `-i=includes`. Los archivos fuente deben usar `.pwn`, `.pawn`, `.p` o `.inc` y deben quedar dentro del workspace.

## Limites

No hay `cd`, pipes, redireccion, expansion de shell ni ejecucion de otros programas: `ls` solo lee la carpeta del workspace, no todo el sistema de archivos. La consola arranca con **3.10.7**. Si la app ya cargo otra libreria nativa del compilador, reinicia despues de `switch` para que la eleccion surta efecto."""

    private const val RU = """# Developer Portal

Eta konsol zapuskaet vstroennyj **native pawncc** i nabor vstroennykh komand. Vvod — ne obolochka Android: raspoznayutsya tolko perechislennye zdes komandy.

## Komandy

- `help` pechataet spisok komand.
- `docs` pokazyvaet etu dokumentatsiyu.
- `pawncc <file> [flags...]` kompiliruet yavno ukazannyj fajl. Bez argumentov `pawncc` tolko pechataet spravku.
- `switch [3.10.7|3.10.11]` vybiraet versiyu kompilyatora; bez argumenta pereklyuchaet versiyu konsoli.
- `clear` ochishchaet istoriyu konsoli; takzhe prinimaetsya napisanie `celar`.
- `ls` perechislyaet fajly i papki aktivnoj rabochej oblasti.
- `whoami`, `time`, `date`, `version`, `deviceinfo`, `github` pokazyvayut imya polzovatelya, vremya, datu, versiyu PawnMC, svedeniya ob ustrojstve i ssylku na repozitorij.
- `which <command>` pokazyvaet put k programme ili imya vstroennoj komandy.

## Primery

```text
ls
pawncc gamemodes/mode.pwn -d=3
pawncc "gamemodes/test mode.pwn" -i=includes -o=mode.amx
switch 3.10.11
clear
```

V pawncc peredayutsya tolko flagi, napisannye v komande. Flagi, optimizatsiya i include-puti iz nastroek PawnMC ne dobavlyayutsya. Ukazhite nuzhnye parametry sami, naprimer `-i=includes`. Iskhodnye fajly dolzhny imet rasshirenie `.pwn`, `.pawn`, `.p` ili `.inc` i nakhoditsya vnutri rabochej oblasti.

## Ogranicheniya

Net `cd`, konvejerov, perenapravleniya, raskrytiya obolochki ili zapuska drugikh programm: `ls` chitaet tolko papku rabochej oblasti, a ne vsyu fajlovuyu sistemu. Konsol nachinaet s versii **3.10.7**. Esli prilozheniem uzhe zagruzhena drugaya biblioteka kompilyatora, perezapustite ego posle `switch`, chtoby vybor vstupil v silu."""
}
