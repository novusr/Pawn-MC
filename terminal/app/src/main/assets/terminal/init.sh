# Startup script for the PawnMC sandbox shell.
#
# This runs inside the proot'ed Ubuntu rootfs for every session. The Xed original
# sourced bash-only helpers (shopt, ~/.bashrc, command_not_found_handle) and installed
# packages behind the user's back; here it stays POSIX so /bin/sh can run it, and the
# only setup it performs is what a Pawn workspace needs.
force_color_prompt=yes

export PATH=/bin:/sbin:/usr/bin:/usr/sbin:/usr/local/bin:/usr/local/sbin:$LOCAL/bin:$PATH
export SHELL="/bin/sh"
# No $PS1 here: the interactive shell sets its own prompt, and the Android side passes
# PS1 in the environment so the colours match the editor theme.

# $LOCAL is exported by the Android side; the helper script only defines functions.
if [ -n "$LOCAL" ] && [ -r "$LOCAL/bin/utils" ]; then
    . "$LOCAL/bin/utils"
fi

if [ -f "$LOCAL/.sandbox_degraded" ]; then
    warn "Running in degraded mode. Some features may not work. Reinstall the terminal."
fi

# Keep the container clock in sync with the device instead of a hard-coded zone.
CONTAINER_TIMEZONE="UTC"
ln -snf "/usr/share/zoneinfo/$CONTAINER_TIMEZONE" /etc/localtime 2>/dev/null
echo "$CONTAINER_TIMEZONE" > /etc/timezone 2>/dev/null

# The compiler the user selected lives in the app's native library dir, so expose it
# under a stable name instead of making them hunt for the versioned .so.
PAWNMC_BIN_DIR="$LOCAL/bin"
mkdir -p "$PAWNMC_BIN_DIR"
if [ -n "$PAWNCC" ] && [ -e "$PAWNCC" ]; then
    ln -sfn "$PAWNCC" "$PAWNMC_BIN_DIR/pawncc" 2>/dev/null
fi
export PATH="$PAWNMC_BIN_DIR:$PATH"

# Compiler output lands next to the source, so make the real Android path reachable.
if [ -d /sdcard ] && [ ! -e /storage/emulated/0 ]; then
    mkdir -p /storage/emulated 2>/dev/null
    ln -sfn /sdcard /storage/emulated/0 2>/dev/null
fi

printf '\n'
printf '  PawnMC sandbox\n'
printf '  Ubuntu under proot - you are root inside this container.\n'
printf '  /sdcard is the device storage you opened in Xed.\n'
printf '\n'

# Land in the folder the editor handed over, falling back to the container root.
cd "$WKDIR" 2>/dev/null || cd "$HOME" 2>/dev/null || cd /

# Anything the user dropped in /etc/pawnmc/initrc runs last, so it can override all of
# the above without them having to edit this file.
if [ -r /etc/pawnmc/initrc ]; then
    . /etc/pawnmc/initrc
fi
