# PawnMC/pawncc Configuration

* Reference: https://github.com/pawn-lang/compiler/tree/master/doc
* Version: *3.10.xx* ([pawn-lang/compiler](https://github.com/pawn-lang/compiler) / [openmultiplayer/compiler](https://github.com/openmultiplayer/compiler) / Pawn MC)
* Default settings prioritize safety (`-d2`) and smaller binaries (`-C+`) also (`-;+`) (`-(+`)
* Official source code and repository: https://github.com/pawn-lang/compiler https://github.com/novusr/Pawnc-3.10.11 https://github.com/novusr/Pawnc-3.10.7
* More (useful links):
  * Pawn Basic https://sampwiki.blast.hk/wiki/Scripting_Basics
  * Pawn Basic 2 https://sampwiki.blast.hk/wiki/PAWN_for_Beginners
  * Scripting Function https://sampwiki.blast.hk/wiki/Category:Scripting_Functions
  * Scripting Callbacks https://sampwiki.blast.hk/wiki/Category:Scripting_Callbacks
  * Scripting Editor https://sampwiki.blast.hk/wiki/Scripting_Editors
  * Script Examples https://sampwiki.blast.hk/wiki/Script_Examples
  * SA-MP NPC Docs https://sampwiki.blast.hk/wiki/Category:NPC
  * Script Resources https://sampwiki.blast.hk/wiki/Category:Scripting_Documentation
  * MySQL Tutorial https://sampwiki.blast.hk/wiki/Mysql_tutorial
  * MySQL Tutorial 2 https://sampwiki.blast.hk/wiki/MySQL
  * SQLite https://sampwiki.blast.hk/wiki/Category:SQLite
  * Server Issues https://open.mp/docs/server/CommonServerIssues
  * Controlling https://open.mp/docs/server/ControllingServer
  * Crash Addresses https://open.mp/docs/client/CrashAddresses
  * String Manipulation https://open.mp/docs/tutorials/stringmanipulation
  * Binary https://open.mp/docs/tutorials/Binary
  * Cooldown https://open.mp/docs/tutorials/cooldowns
  * Variable System https://open.mp/docs/tutorials/servervariablesystem
  * Query Mechanism https://open.mp/docs/tutorials/QueryMechanism
  * Advanced Structures https://open.mp/docs/tutorials/AdvancedStructures
  * Plugin Development https://open.mp/docs/tutorials/PluginDevelopmentGuide
  * Pickup Guide https://open.mp/docs/tutorials/PickupGuide
  * Menu Guide https://open.mp/docs/tutorials/MenuGuide
  * Errors/Warnings https://sampwiki.blast.hk/wiki/Errors_List

## PawnMC Basic Usage
Open a (`.pawn` - `.pwn` - `.p`) script within the application and you are ready to compile.
![img](https://raw.githubusercontent.com/novusr/Pawn-MC/refs/heads/main/configuration/PAWNMC.png)

## Custom Flags

The compiler is invoked from the command line. Every option starts with a dash (`-`) or, on Microsoft Windows and DOS, a forward slash (`/`).

### Syntax for Values

A lot of options accept a value. You can pass it in several ways:

* Glued directly to the option: `-d2`
* Using a colon: `-d:2`
* Using an equal sign: `-d=2`

They're all exactly the same thing.

## Quick Reference Table

A quick look at the most commonly used options. Check the sections below for the full explanation.

| Option | Quick Description              | Typical Value   | Default |
| ------ | ------------------------------ | --------------- | ------- |
| `-O`   | Optimization Level             | 0, 1, 2         | 1       |
| `-d`   | Debug Level                    | 0, 1, 2, 3      | 1       |
| `-v`   | Verbosity Level                | 0, 1, 2         | 1       |
| `-C`   | Compact Encoding               | + (on), - (off) | +       |
| `-S`   | Stack/Heap Size                | cells           | 4096    |
| `-w`   | Disable Warning                | warning number  | -       |
| `-E`   | Warnings as Errors             | + (on), - (off) | -       |
| `-Z`   | Cross-Platform Path Separators | + (on), - (off) | -       |

## Essential Options

### `-a`

**Output Assembler Code**

Generate a text file containing Pawn Abstract Machine pseudo-assembler instructions instead of a compiled `.amx` binary.

---

### `-C[+/-]`

**Compact Encoding**

Shrinks the generated binary significantly — often by more than half.

* `-C+` → Enable compact encoding.
* `-C-` → Disable compact encoding.
* `-C` → Toggle the current state.

**DEFAULT:** `-C+` (enabled)

---

### `-c<name>`

**Codepage**

Sets the codepage used when translating the source file.

Examples:

* `1252`
* `932`
* Full path to a custom mapping file

---

### `-d<num>`

**Debug Level**

* `0` → No debug info, no runtime checks.
* `1` → Runtime checks enabled, no debug symbols.
* `2` → Full debug symbols and runtime checking.
* `3` → Same as `-d2`, but automatically forces `-O0`.

**DEFAULT:** `-d1`

---

### `-D<path>`

**Active Directory**

Sets the working directory used for locating input files and generating outputs.

(Platform-specific support.)

---

### `-e<name>`

**Error File**

Writes all warnings and errors to a file instead of the console.

Perfect for automated builds and IDE integrations.

---

### `-E[+/-]`

**Warnings as Errors**

Treat every warning as a compilation error.

* `-E+` → Enabled.
* `-E-` → Disabled.

**DEFAULT:** `-E-`

---

### `-H<value>`

**HWND** *(Windows only)*

Posts a completion message to the specified window handle.

Mostly used by IDEs and editor integrations.

---

### `-i<name>`

**Include Path**

Adds a directory to the include search list.

Example:

```yml
pawno\pawncc.exe gamemodes\unit.pwn -ogamemodes\unit.amx -i=includes -i=core -i=modules
```

---

### `-l`

**Create List File**

Preprocess only.

Reads the source, expands macros/includes, and generates the preprocessed output without producing an `.amx`.

---

### `-o<name>`

**Output File**

Sets the output `.amx` filename.

---

### `-O<num>`

**Optimization Level**

* `0` → No optimization.
* `1` → JIT-safe optimizations only.
* `2` → Full optimization pass.

**DEFAULT:** `-O1`

---

### `-p<name>`

**Prefix File**

Specifies an implicit include file that gets parsed before the main script.

Overrides the default `default.inc`.

Using `-p` without a filename disables automatic prefix inclusion entirely.

---

### `-R[+/-]`

**Recursion Report**

Adds detailed recursion-chain information to compiler output.

**DEFAULT:** `-R-`

---

### `-r[name]`

**Cross-Reference Report**

Generates an XML cross-reference report.

* Without filename → printed to console.
* With filename → written to file.

---

### `-S<num>`

**Stack / Heap Size**

Sets the stack and heap size in cells.

**DEFAULT:** `4096`

---

### `-s<num>`

**Skip Lines**

Skips the specified number of lines at the start of the source file before compilation begins.

---

### `-t<num>`

**TAB Indent Size**

Controls how many spaces a TAB represents.

* `-t0` disables warning 217 (loose indentation).

**DEFAULT:** `8`

---

### `-v<num>`

**Verbosity Level**

* `0` → Quiet. Only fatal errors.
* `1` → Standard warnings and errors.
* `2` → Extra details, including memory usage reports.

**DEFAULT:** `-v1`

---

### `-w<num>`

**Disable Warning**

Disables a specific warning.

Example:

```yml
-w217
```

---

### `-X<num>`

**Abstract Machine Size Limit**

Sets the maximum total memory usage (code + data + stack) allowed for the compiled script.

Mainly useful for embedded environments.

---

### `-XD<num>`

**Data / Stack Memory Limit**

Limits data and stack memory usage only.

Usually used together with `-X`.

---

### `-Z[+/-]`

**Cross-Platform Path Separators**

Controls how path separators are interpreted by the compiler.

Pawn MC already provides automatic cross-platform path handling by default, so this option is usually unnecessary for most projects.

* `-Z+` -> Force compatibility mode and accept both `/` and `\` as path separators regardless of the current operating system.
* `-Z-` -> Use the compiler's native path parsing behavior.

Examples:

```yml
-i=includes/core
-i=includes\core
```

With `-Z+`, both path styles are always accepted.

This option mainly exists for compatibility, testing, legacy build systems, or environments where explicit separator handling is preferred.

**DEFAULT:** Automatic behavior (Pawn MC)

---

### `-\`

**Backslash Escape Mode**

Use backslash (`\`) as the escape character.

Compatible with C, C++, Java, C#, and many other languages.

---

### `-^`

**Caret Escape Mode**

Use caret (`^`) as the escape character.

Provided for compatibility with older Pawn codebases.

---

### `-;[+/-]`

**Semicolon Requirement**

* `-;+` → Every statement must end with `;`
* `-;-` → The last statement on a line may omit `;`

**DEFAULT:** `-;-`

---

### `-([+/-]`

**Parentheses Requirement**

Controls whether parentheses are required for function calls.

* `-(+` → Parentheses required.
* `-(-` → Parentheses may be omitted in supported contexts.

**DEFAULT:** `-(-`

---

### `sym=val`

**Define Constant**

Defines a compile-time constant.

Examples:

```yml
DEBUG=1
TESTMODE=1
VERSION=42
```

If the value is omitted:

```yml
DEBUG=
```

it becomes:

```yml
DEBUG = 0
```

---

### `@filename`

**Response File**

Loads additional command-line options from a text file.

Useful when the command line starts getting ridiculously long.

Example:

```yml
pawncc @build.cfg
```
