set -eu
# Fail clearly before trying to source helper files through an unset/bad LOCAL path.
if [ -z "${LOCAL:-}" ]; then
    printf 'Sandbox setup cannot start: required environment variable LOCAL is empty.\n' >&2
    exit 1
fi
if [ ! -r "$LOCAL/bin/utils" ]; then
    printf 'Sandbox setup cannot start: helper script is missing or unreadable: %s/bin/utils\n' "$LOCAL" >&2
    exit 1
fi
# `.` rather than `source`: this script is executed by /system/bin/sh (mksh).
. "$LOCAL/bin/utils"

STEP="startup"
TEMP_LOG=""

fail() {
    code=$?
    trap - 0
    if [ "$code" -ne 0 ]; then
        printf '\n' >&2
        error "Sandbox setup failed during: $STEP"
        printf '  Exit status: %s\n' "$code" >&2
        case "$code" in
            132) printf '  Signal hint: SIGILL (illegal CPU instruction); identify the exact tool from the step and command output above.\n' >&2 ;;
            139) printf '  Signal hint: SIGSEGV (process memory fault).\n' >&2 ;;
            137) printf '  Signal hint: SIGKILL (often memory pressure or forced process termination).\n' >&2 ;;
            134) printf '  Signal hint: SIGABRT (process aborted).\n' >&2 ;;
            126) printf '  Failure hint: command exists but could not be executed (permissions, ABI, or linker issue).\n' >&2 ;;
            127) printf '  Failure hint: required command was not found in PATH.\n' >&2 ;;
        esac
        printf '  Device ABI: %s\n' "${DEVICE_ABI:-unknown}" >&2
        printf '  Android API: %s\n' "${ANDROID_API_LEVEL:-unknown}" >&2
        if [ -x /system/bin/tar ]; then
            printf '  Android tar executable: yes (%s)\n' /system/bin/tar >&2
        else
            printf '  Android tar executable: no (%s)\n' /system/bin/tar >&2
        fi
        if [ -r "${SHIM:-/missing/link2symlink}" ]; then
            printf '  link2symlink readable: yes (%s)\n' "$SHIM" >&2
        else
            printf '  link2symlink readable: no (%s)\n' "${SHIM:-unknown}" >&2
        fi
        printf '  Rootfs archive: %s\n' "${ROOTFS_ARCHIVE:-unknown}" >&2
        if [ -n "${ROOTFS_ARCHIVE:-}" ] && [ -f "$ROOTFS_ARCHIVE" ]; then
            printf '  Archive size: %s bytes\n' "$(wc -c < "$ROOTFS_ARCHIVE" | tr -d '[:space:]')" >&2
        fi
        df -h "${LOCAL:-.}" >&2 || true
        printf '  Native library directory: %s\n' "${NATIVE_LIB_DIR:-unknown}" >&2
        printf '  Sandbox path: %s\n' "${SANDBOX_DIR:-unknown}" >&2
        printf '  Working directory: %s\n' "$PWD" >&2
        if [ -n "${TEMP_LOG:-}" ] && [ -s "$TEMP_LOG" ]; then
            printf '  Command output (%s), last 50 lines:\n' "$TEMP_LOG" >&2
            tail -n 50 "$TEMP_LOG" >&2 || true
        fi
    fi
    exit "$code"
}
trap fail 0
require_file() {
    label=$1
    path=$2
    if [ ! -f "$path" ]; then
        printf 'Missing %s: %s\n' "$label" "$path" >&2
        return 1
    fi
    if [ ! -r "$path" ]; then
        printf 'Not readable (%s): %s\n' "$label" "$path" >&2
        return 1
    fi
    return 0
}

require_dir() {
    label=$1
    path=$2
    if [ ! -d "$path" ]; then
        printf 'Missing %s directory: %s\n' "$label" "$path" >&2
        return 1
    fi
    if [ ! -w "$path" ]; then
        printf 'Not writable (%s directory): %s\n' "$label" "$path" >&2
        return 1
    fi
    return 0
}

STEP="validating sandbox paths and tools"
if [ -z "${TMP_DIR:-}" ] || [ -z "${NATIVE_LIB_DIR:-}" ]; then
    printf 'Required environment variable is empty (TMP_DIR or NATIVE_LIB_DIR).\n' >&2
    exit 1
fi
ROOTFS_ARCHIVE="$TMP_DIR/sandbox.tar.gz"
SANDBOX_DIR="$LOCAL/sandbox"
DEVICE_ABI="${PRIMARY_ABI:-unknown}"
SHIM="$NATIVE_LIB_DIR/liblink2symlink.so"

require_file "staged Ubuntu rootfs archive" "$ROOTFS_ARCHIVE"
require_file "link2symlink compatibility library" "$SHIM"
require_file "Android tar utility" /system/bin/tar
require_dir "sandbox parent" "$LOCAL"
if [ ! -x /system/bin/tar ]; then
    printf 'Android tar utility is not executable: /system/bin/tar\n' >&2
    exit 1
fi
mkdir -p "$SANDBOX_DIR"
if [ ! -w "$SANDBOX_DIR" ]; then
    printf 'Sandbox extraction destination is not writable: %s\n' "$SANDBOX_DIR" >&2
    exit 1
fi
# A previous failed extraction leaves a half-populated rootfs behind (dangling
# link2symlink symlinks, setuid leftovers). Extracting over that state makes tar
# fail with "can't open: No such file or directory" on entries whose paths now
# resolve through those stale links, so every attempt starts from an empty tree.
# The setup marker is only written on success, so wiping here can never destroy
# a completed install: this code only runs when isInstalled() is false.
WIPE_LOG="$TMP_DIR/sandbox-wipe.log"
WIPE_DONE=""
wipe_sandbox() {
    # rm -rf can legitimately fail on Android (toybox refuses entries it cannot
    # unlink mid-recursion). Capture the failure instead of letting set -e abort,
    # and surface exactly which paths survived so the extraction log explains the
    # "can't open" errors that would otherwise look like an archive problem.
    WIPE_DONE=""
    : > "$WIPE_LOG"
    set +e
    rm -rf "$SANDBOX_DIR" >"$WIPE_LOG" 2>&1
    wipe_ret=$?
    set -e
    if [ "$wipe_ret" -ne 0 ] && [ -d "$SANDBOX_DIR" ]; then
        # Second pass: force again (toybox sometimes succeeds on the second run once
        # the first has already unlinked the leaf files), then give up gracefully.
        set +e
        rm -rf "$SANDBOX_DIR" >>"$WIPE_LOG" 2>&1
        wipe_ret=$?
        set -e
    fi
    if [ "$wipe_ret" -ne 0 ]; then
        WIPE_DONE="no"
        if [ -d "$SANDBOX_DIR" ]; then
            printf 'rm -rf left these top-level entries behind (exit %s):\n' "$wipe_ret" >&2
            ls "$SANDBOX_DIR" >&2 || true
        fi
        cat "$WIPE_LOG" >&2 || true
    else
        WIPE_DONE="yes"
    fi
    mkdir -p "$SANDBOX_DIR"
    return "$wipe_ret"
}
wipe_sandbox || true
WIPE_STATE_1="${WIPE_DONE:-?}"
archive_size=$(wc -c < "$ROOTFS_ARCHIVE" | tr -d '[:space:]')
if [ -z "$archive_size" ]; then
    printf 'Could not determine the rootfs archive size: %s\n' "$ROOTFS_ARCHIVE" >&2
    exit 1
fi
if [ "$archive_size" -lt 1024 ]; then
    printf 'Rootfs archive is implausibly small (%s bytes); download may be incomplete.\n' "$archive_size" >&2
    exit 1
fi
# Use the archive decompressor built into Android tar; this avoids assuming `gzip` is
# separately installed in /system/bin on every supported Android release.
TEMP_LOG="$TMP_DIR/archive-validation.log"
if /system/bin/tar -tzf "$ROOTFS_ARCHIVE" >/dev/null 2>"$TEMP_LOG"; then
    rm -f "$TEMP_LOG"
    TEMP_LOG=""
else
    tar_status=$?
    STEP="validating Ubuntu rootfs archive (tar/gzip integrity)"
    printf 'Android tar could not read archive (exit %s, %s bytes): %s\n' \
        "$tar_status" "$archive_size" "$ROOTFS_ARCHIVE" >&2
    printf 'The download may be truncated, invalid, or not a gzip-compressed tar archive.\n' >&2
    tail -n 50 "$TEMP_LOG" >&2 || true
    exit "$tar_status"
fi
STEP="checking app-private storage capacity"
free_kb=$(df -Pk "$LOCAL" | awk 'NR==2 {print $4}')
archive_kb=$(((archive_size + 1023) / 1024))
if [ -n "$free_kb" ] && [ "$free_kb" -lt $((archive_kb * 3)) ]; then
    printf 'Low storage: %s KiB available, archive is %s KiB; allow at least 3x archive size for extraction.\n' \
        "$free_kb" "$archive_kb" >&2
    exit 1
fi
info "Extracting the Ubuntu container…"

# Extraction follows the proven Xed-Editor order: PRoot with the full bind set
# first (it fakes a root environment so link()/chown()/device nodes always succeed,
# and it is the path the upstream terminal runs on this very device), then the
# direct Android tar with the link2symlink shim, then without the shim. Each
# strategy writes its own log so the final diagnostics tail the right output.
#
# PRoot args mirror the Xed setup.sh: every Android mount that exists gets bound
# into the container so /system/bin/sh and the archive path stay reachable.
build_proot_args() {
    PROOT_ARGS="--kill-on-exit -w /"
    for system_mnt in /apex /odm /product /system /system_ext /vendor \
        /linkerconfig/ld.config.txt \
        /linkerconfig/com.android.art/ld.config.txt \
        /plat_property_contexts /property_contexts; do
        if [ -e "$system_mnt" ]; then
            system_mnt=$(realpath "$system_mnt")
            PROOT_ARGS="$PROOT_ARGS -b ${system_mnt}"
        fi
    done
    PROOT_ARGS="$PROOT_ARGS -b /sdcard -b /storage -b /dev -b /data -b /dev/urandom:/dev/random -b /proc -b /sys"
    PROOT_ARGS="$PROOT_ARGS -b $PRIVATE_DIR"
    if [ -e /proc/self/fd ]; then
        PROOT_ARGS="$PROOT_ARGS -b /proc/self/fd:/dev/fd"
        [ -e /proc/self/fd/0 ] && PROOT_ARGS="$PROOT_ARGS -b /proc/self/fd/0:/dev/stdin"
        [ -e /proc/self/fd/1 ] && PROOT_ARGS="$PROOT_ARGS -b /proc/self/fd/1:/dev/stdout"
        [ -e /proc/self/fd/2 ] && PROOT_ARGS="$PROOT_ARGS -b /proc/self/fd/2:/dev/stderr"
    fi
    PROOT_ARGS="$PROOT_ARGS -r / -0 --link2symlink --sysvipc"
}

STEP="extracting rootfs through PRoot (Xed order)"
TEMP_LOG="$TMP_DIR/extraction-proot.log"
: > "$TEMP_LOG"
if [ -x "${PROOT:-}" ] && [ -n "${LINKER:-}" ]; then
    build_proot_args
    PROOT_CMD="$PROOT"
    if [ -x "$LINKER" ]; then
        PROOT_CMD="$LINKER $PROOT"
    fi
    set +e
    $PROOT_CMD $PROOT_ARGS /system/bin/sh -c "(cd $SANDBOX_DIR && tar -xf $ROOTFS_ARCHIVE)" >"$TEMP_LOG" 2>&1
    ret=$?
    set -e
    if [ "$ret" -ne 0 ]; then
        # Samsung devices do not like system binaries under proot; this matches the
        # same retry the Xed script performs with the linker on the command line.
        set +e
        $PROOT $PROOT_ARGS /system/bin/sh -c "(cd $SANDBOX_DIR && tar -xf $ROOTFS_ARCHIVE)" >"$TEMP_LOG" 2>&1
        ret=$?
        set -e
    fi
else
    printf 'PRoot binary not available for the primary extraction (PROOT=%s).\n' "${PROOT:-unset}" >&2
    ret=1
fi
if [ "$ret" -ne 0 ]; then
    # The shim is the usual suspect: it intercepts open()/link() to emulate
    # hardlinks, and on some devices (seen on API 28/x86_64 with entries such as
    # usr/bin/su and etc/pam.d/su) its redirected lookups miss the marker file it
    # staged earlier, so tar fails with ENOENT on entries it extracted fine on
    # every other filesystem. A plain Android tar creates the same hardlinks as
    # independent copies instead, which proot treats identically at runtime.
    warn "PRoot extraction failed (exit $ret); retrying with Android tar + link2symlink…"
    STEP="wiping sandbox between extraction attempts"
    wipe_sandbox || true
    WIPE_STATE_2="${WIPE_DONE:-?}"
    STEP="extracting rootfs with Android tar + link2symlink"
    TEMP_LOG="$TMP_DIR/extraction-shim.log"
    : > "$TEMP_LOG"
    set +e
    LD_PRELOAD="$(realpath "$SHIM")" /system/bin/tar -xzf "$ROOTFS_ARCHIVE" --no-same-owner -C "$SANDBOX_DIR" >"$TEMP_LOG" 2>&1
    ret=$?
    set -e
fi
if [ "$ret" -ne 0 ]; then
    # Last resort: extract through PRoot like the original setup did. PRoot fakes
    # a root environment so link() and chown() succeed regardless of filesystem
    # semantics, and it covers the remaining device-specific tar quirks. Only
    # reached after both direct strategies failed, so the SIGILL-prone path never
    # blocks devices where the direct extraction works.
    warn "tar extraction with the shim failed (exit $ret); retrying without the shim…"
    STEP="wiping sandbox before plain-tar extraction"
    wipe_sandbox || true
    WIPE_STATE_3="${WIPE_DONE:-?}"
    STEP="extracting rootfs with plain Android tar (no shim)"
    unset LD_PRELOAD
    TEMP_LOG="$TMP_DIR/extraction-noshim.log"
    : > "$TEMP_LOG"
    set +e
    /system/bin/tar -xzf "$ROOTFS_ARCHIVE" --no-same-owner -C "$SANDBOX_DIR" >"$TEMP_LOG" 2>&1
    ret=$?
    set -e
fi
if [ "$ret" -ne 0 ]; then
    printf 'tar extraction failed (exit %s) through PRoot, with the link2symlink shim, and without it. Check archive validity, app storage space, and shim compatibility.\n' "$ret" >&2
    printf '  Wipe before attempts: proot=%s shim=%s noshim=%s (yes=clean wipe, no=leftovers survived)\n' \
        "${WIPE_STATE_1:-n/a}" "${WIPE_STATE_2:-n/a}" "${WIPE_STATE_3:-n/a}" >&2
    for log in "$TMP_DIR/extraction-proot.log" "$TMP_DIR/extraction-shim.log" "$TMP_DIR/extraction-noshim.log"; do
        if [ -s "$log" ]; then
            printf '  --- %s ---\n' "${log##*/}" >&2
            tail -n 20 "$log" >&2 || true
        fi
    done
    exit "$ret"
fi
if [ ! -x "$SANDBOX_DIR/bin/sh" ] && [ ! -x "$SANDBOX_DIR/bin/dash" ]; then
    STEP="validating extracted Ubuntu rootfs"
    printf 'Archive unpacked but no executable /bin/sh or /bin/dash was found under %s.\n' "$SANDBOX_DIR" >&2
    printf 'Top-level extracted paths:\n' >&2
    ls -la "$SANDBOX_DIR" >&2 || true
    exit 1
fi
if [ ! -e "$SANDBOX_DIR/usr/bin/dpkg" ]; then
    STEP="validating Ubuntu rootfs contents"
    printf 'Rootfs shell exists, but /usr/bin/dpkg is missing; archive may be the wrong image or extraction incomplete.\n' >&2
    exit 1
fi

info "Setting up the Ubuntu container…"

# values you want written
nameserver="nameserver 8.8.8.8
nameserver 8.8.4.4"

hosts="127.0.0.1   localhost.localdomain localhost

# IPv6.
::1         localhost.localdomain localhost ip6-localhost ip6-loopback
fe00::0     ip6-localnet
ff00::0     ip6-mcastprefix
ff02::1     ip6-allnodes
ff02::2     ip6-allrouters
ff02::3     ip6-allhosts"

STEP="configuring Ubuntu network and Android groups"
# ensure etc directory exists
mkdir -p "$SANDBOX_DIR/etc"

# write hostname
printf '%s\n' "PawnMC" > "$SANDBOX_DIR/etc/hostname"

# write resolv.conf (create file if not exists, then overwrite)
: > "$SANDBOX_DIR/etc/resolv.conf"
printf '%s\n' "$nameserver" > "$SANDBOX_DIR/etc/resolv.conf"

# write hosts
printf '%s\n' "$hosts" > "$SANDBOX_DIR/etc/hosts"

groupFile="$SANDBOX_DIR/etc/group"
aid="$(id -g)"

linesToAdd="
inet:x:3003
everybody:x:9997
android_app:x:20455
android_debug:x:50455
android_cache:x:$((10000 + aid))
android_storage:x:$((40000 + aid))
android_media:x:$((50000 + aid))
android_external_storage:x:1077
"

# create the file if it doesn't exist
[ -f "$groupFile" ] || : > "$groupFile"

existing="$(cat "$groupFile")"

# iterate through lines
echo "$linesToAdd" | while IFS= read -r line; do
    [ -z "$line" ] && continue
    gid="${line##*:}"  # get part after last colon
    case "$existing" in
        *:"$gid"*) : ;;   # already exists → skip
        *) printf '%s\n' "$line" >> "$groupFile" ;;
    esac
done

STEP="finalizing sandbox installation"
# Mark installation complete only after extraction and essential rootfs checks succeed.
touch "$LOCAL/.terminal_setup_ok_DO_NOT_REMOVE"
rm -f "$ROOTFS_ARCHIVE"
rm -f "$TEMP_LOG"
TEMP_LOG=""
trap - 0

# The Node.js APT hook the Xed build installed here is intentionally absent: Pawn
# compilation needs no JavaScript runtime, and hooking dpkg to rewrite /usr/bin/node
# would only add a failure mode.

# SandboxSetup runs this script only to stage/extract the rootfs. Do not launch an
# interactive PRoot session from that host-side bootstrap process.
if [ "${PAWNMC_SETUP_ONLY:-0}" = "1" ]; then
    exit 0
fi
if [ $# -gt 0 ]; then
    sh "$@"
else
    clear
    sh "$LOCAL/bin/sandbox"
fi
