# Pawn Compiler (Community Version)

This is the Pawn Community Compiler, a modified version of Pawn 3.2.3664 with bug fixes and enhancements for the SA-MP community.
* Reference: https://github.com/pawn-lang/compiler/tree/master/doc

## Version
3.10.11, 3.10.10, 3.10.9, 3.10.8, 3.10.7

## Basic Usage
The compiler is invoked from the command line. All options start with a dash ("-") or, on Microsoft Windows and DOS, with a forward slash ("/"). Example: `pawncc input.pwn -O2`

### Syntax for Values
Many options accept a value. The value can be:
* Glued to the option letter: `-d2`
* Separated by a colon: `-d:2`
* Separated by an equal sign: `-d=2`
All three formats are equivalent.

## Quick Reference Table
A quick list of the most common options. See the sections below for full details.

| Option | Quick&nbsp;Description | Typical&nbsp;Value | Default |
|--------|----------------------|------------------|---------|
| -O     | Optimization&nbsp;Level | 0,&nbsp;1,&nbsp;2      | 1       |
| -d     | Debug&nbsp;Level       | 0,&nbsp;1,&nbsp;2,&nbsp;3 | 1       |
| -v     | Verbosity&nbsp;Level   | 0,&nbsp;1,&nbsp;2      | 1       |
| -C     | Compact&nbsp;Encoding  | +&nbsp;(on),&nbsp;-&nbsp;(off) | +    |
| -S     | Stack/Heap&nbsp;Size   | cells               | 4096    |
| -Z     | SA-MP&nbsp;Compatibility | +&nbsp;(on),&nbsp;-&nbsp;(off) | -    |
| -w     | Disable&nbsp;Warning   | warning&nbsp;number  | -      |
| -E     | Warnings&nbsp;as&nbsp;Errors | +&nbsp;(on),&nbsp;-&nbsp;(off) | -    |

## Essential Options

### -a
**Output Assembler Code**: Generate a text file with the pseudo-assembler code for the pawn abstract machine, instead of a binary .amx file.

### -C[+/-]
**Compact Encoding**: Significantly reduces the size of the binary output file (typically by more than half). Use `-C+` to enable or `-C-` to disable. The option `-C` alone toggles the current setting.
**DEFAULT**: `-C+` (enabled)

### -c<name>
**Codepage**: Set the codepage for translating the source file (e.g., 1252 for Windows Latin-1). You can specify a number or a full path to a mapping file.

### -d<num>
**Debug Level**:
* `0` = No symbolic information, no run-time checks.
* `1` = Run-time checks (bounds checking, assertions), no symbolic information.
* `2` = Full debug information and dynamic checking.
* `3` = Same as `-d2`, but forces optimization level to `-O0`.
**DEFAULT**: `-d1`

### -D<path>
**Active Directory**: The directory where the compiler should search for input files and store output files. (Platform-specific support).

### -e<name>
**Error File**: Set the name of the file where all warning and error messages are written. When set, there is no output to the screen (quiet compile).

### -E[+/-]
**Warnings as Errors**: When enabled (`-E+`), all warnings are treated as errors and will cause the compilation to fail.
**DEFAULT**: `-E-` (off)

### -H<value>
**HWND** (Windows only): The compiler can post a completion message to the specified window handle. Used for IDE integration.

### -i<name>
**Include Path**: Set the path where the compiler can find include files. This option can appear multiple times to set several search paths.

### -l
**Create List File**: Perform only the file reading and preprocessing steps (preprocess only). Useful for checking macro expansion.

### -o<name>
**Output File**: Set the base name for the generated P-code (.amx) output file.

### -O<num>
**Optimization Level**:
* `0` = No optimization.
* `1` = JIT-compatible optimizations only.
* `2` = Full optimizations.
**DEFAULT**: `-O1`

### -p<name>
**Prefix File**: Set the name of an implicit "prefix" file that is parsed before the main input file. Overrides the default "default.inc". Using `-p` alone disables all implicit includes.

### -R[+/-]
**Recursion Report**: Add a detailed recursion report with call chains to the output.
**DEFAULT**: `-R-` (off)

### -r[name]
**Cross-Reference Report**: Write a cross-reference report. If a filename is provided, output goes to that file; otherwise, it goes to the console. The report is in XML format.

### -S<num>
**Stack/Heap Size**: The size of the stack and heap in cells (not bytes). The value is in cells.
**DEFAULT**: 4096 cells

### -s<num>
**Skip Lines**: The number of lines to skip at the beginning of the input file before starting compilation.

### -t<num>
**TAB Indent Size**: The number of space characters that represent a TAB. Setting this to 0 (`-t0`) disables warning 217 (loose indentation).
**DEFAULT**: 8

### -v<num>
**Verbosity Level**:
* `0` = Quiet compile. Only fatal errors are shown.
* `1` = Normal output (errors and warnings).
* `2` = Verbose. Adds a code/data/stack usage report to the normal output.
**DEFAULT**: `-v1`

### -w<num>
**Disable Warning**: Disable a specific compiler warning by its number (e.g., `-w217`).

### -X<num>
**Abstract Machine Size Limit**: The maximum total memory (in bytes) a compiled script may require (code + data + stack). Useful for embedded environments.

### -XD<num>
**Abstract Machine Data/Stack Limit**: The maximum memory for data and stack only (in bytes). Used with `-X` for finer control in embedded/ROM environments.

### -Z[+/-]
**Compatibility Mode**: Run in SA-MP compatibility mode. CRITICAL for SA-MP scripts.
**DEFAULT**: `-Z-` (off). Use `-Z+` for SA-MP.<br>
**For what?**: What is meant by compatibility here is support for Path Separators. For example, if you compile a script on Windows OS, you need `-Z+` because the forward slash `/` path separator is not directly supported in `#include "name/too.pwn"` — it will throw "cannot read from file" even if the file exists. Conversely, if you compile on Linux, the path separator is naturally supported.

### -\
**Use backslash ('\')** as the escape character (like C, C++, Java).

### -^
**Use caret ('^')** as the escape character (for compatibility with older Pawn versions).

### -;[+/-]
**Semicolon Requirement**: With `-;+`, every statement must end with a semicolon. With `-;-`, a semicolon is optional if the statement is the last one on a line.
**DEFAULT**: `-;-` (optional)<br>
**Sample:**
```c
#include "a_samp"

main() {
  printf("Hello, World")
                /*      ^ none ; pawno/pawncc xx.pwn -oxx.amx -;- */
  printf("Hello, World");
                /*      ^ have ; pawno/pawncc xx.pwn -oxx.amx -;+ */
}
```

### -([+/-]
**Parentheses Requirement**: With `-(+`, parentheses are required for function invocation. With `-(-`, they are optional in some contexts.
**DEFAULT**: `-(-` (optional)<br>
**For what?**: As in, what is the meaning of the explanation for the flag above.<br>
```c
#include "a_samp"

main() {
  printf "Hello, World!"; // if you try to call a function or something similar without '()',
                         // and if -(+ (active) then you cannot perform an operation like that.
}
```

### sym=val
**Define Constant**: Define a constant "sym" with the numeric value "val". The value is optional (`sym=` defines it as 0).<br>
**Sample:**
```c
#if mydef == 1
  // pawno/pawncc xx.pwn -oxx.amx mydef=1
  //...
#else
  // pawno/pawncc xx.pwn -oxx.amx mydef=0
  // pawno/pawncc xx.pwn -oxx.amx
  //...
#endif
```

### @filename
**Response File**: Read additional command-line options from the specified text file. Useful for long commands.

## Common Use Case Commands

### For SA-MP Development (New Script):
```bash
pawncc script.pwn -Z+ -d2 -v2
```
Enables SA-MP compatibility, full debug info, and verbose output.

### For SA-MP Production Build:
```bash
pawncc script.pwn -Z+ -O2 -d1 -C+
```
Enables SA-MP compatibility, full optimization, runtime checks, and compact encoding.

### For Debugging a Problem:
```bash
pawncc script.pwn -d3 -O0 -v2 -E+ -R+
```
Full symbolic debug, forces no optimization, verbose output, treats warnings as errors, and adds a recursion report.

### For Checking Syntax/Preprocessing:
```bash
pawncc script.pwn -l -i./include
```
Preprocess only and check include paths.

## Notes and Links

* The default settings favor safety (`-d1`) and smaller output size (`-C+`).
* The `-Z` flag (compatibility mode) is **ESSENTIAL** when compiling for SA-MP.
* Verbose level 2 (`-v2`) prints a memory usage report.
* Debug level 3 (`-d3`) implicitly sets optimization level to 0 (`-O0`).
* The official repository and source code can be found at:
  https://github.com/pawn-lang/compiler