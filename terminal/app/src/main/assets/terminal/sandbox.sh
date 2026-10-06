# shellcheck disable=SC2034
force_color_prompt=yes

ARGS="--kill-on-exit"
ARGS="$ARGS -w /"

for system_mnt in /apex /odm /product /system /system_ext /vendor \
 /linkerconfig/ld.config.txt \
 /linkerconfig/com.android.art/ld.config.txt \
 /plat_property_contexts /property_contexts; do

 if [ -e "$system_mnt" ]; then
  system_mnt=$(realpath "$system_mnt")
  ARGS="$ARGS -b ${system_mnt}"
 fi
done
unset system_mnt

ARGS="$ARGS -b /sdcard"
ARGS="$ARGS -b /storage"
ARGS="$ARGS -b /dev"
ARGS="$ARGS -b /data"
ARGS="$ARGS -b /dev/urandom:/dev/random"
ARGS="$ARGS -b /proc"
ARGS="$ARGS -b $EXT_HOME:/home"
ARGS="$ARGS -b $EXT_HOME:/root"
ARGS="$ARGS -b $PRIVATE_DIR"
ARGS="$ARGS -b $LOCAL/stat:/proc/stat"
ARGS="$ARGS -b $LOCAL/vmstat:/proc/vmstat"

if [ -e "/proc/self/fd" ]; then
  ARGS="$ARGS -b /proc/self/fd:/dev/fd"
fi

if [ -e "/proc/self/fd/0" ]; then
  ARGS="$ARGS -b /proc/self/fd/0:/dev/stdin"
fi

if [ -e "/proc/self/fd/1" ]; then
  ARGS="$ARGS -b /proc/self/fd/1:/dev/stdout"
fi

if [ -e "/proc/self/fd/2" ]; then
  ARGS="$ARGS -b /proc/self/fd/2:/dev/stderr"
fi


ARGS="$ARGS -b $PRIVATE_DIR"
ARGS="$ARGS -b /sys"

# SandboxSetup pre-creates this directory before extraction. Always reapply the sticky
# world-writable mode; checking only for absence leaves Java-created 0755 directories
# unchanged and makes apt/temp-file consumers fail under /dev/shm.
mkdir -p "$LOCAL/sandbox/tmp"
chmod 1777 "$LOCAL/sandbox/tmp"

ARGS="$ARGS -b $LOCAL/sandbox/tmp:/dev/shm"

ARGS="$ARGS -r $LOCAL/sandbox"
ARGS="$ARGS -0"
ARGS="$ARGS --link2symlink"
ARGS="$ARGS --sysvipc"
ARGS="$ARGS -L"

chmod -R +x $LOCAL/bin

# proot is a shared object, so Android 10+ will not exec it directly. Run it through the
# platform linker instead; `termux.c` does the same, and both paths must agree.
PROOT_CMD="$PROOT"
if [ -x "$LINKER" ]; then
    PROOT_CMD="$LINKER $PROOT"
fi

# The bundled Ubuntu base rootfs ships dash as /bin/sh, not bash. Source initialization
# inside the container and then replace this setup shell with the interactive shell.
if [ $# -gt 0 ]; then
    command=$*
    exec $PROOT_CMD $ARGS /bin/sh -c '. "$LOCAL/bin/init"; exec /bin/sh -c "$1"' pawnmc-sandbox "$command"
else
    exec $PROOT_CMD $ARGS /bin/sh -c '. "$LOCAL/bin/init"; exec /bin/sh -i'
fi
