CC=gcc
CFLAGS="-Wall -Wextra -Wpedantic"

$CC $CFLAGS -o build/file_sender -DSERVER_IP_ADDR=\""$1"\" file_sender.c
