RESET='\033[0m'

BOLD_BLUE='\033[1;34m'
BOLD_YELLOW='\033[1;33m'
BOLD_RED='\033[1;31m'

BLUE_BG='\033[1;44m'
YELLOW_BG='\033[1;43m'
RED_BG='\033[1;41m'

# Only `info`, `warn` and `error` are used by the sandbox and setup scripts; the Xed
# extras (ask, install_nodejs/uninstall_nodejs) were dropped because nothing here
# installs a JavaScript runtime.
info() {
  printf "\n${BLUE_BG}  INFO  ${RESET} ${BOLD_BLUE}%s${RESET}\n" "$1"
}

warn() {
  printf "\n${YELLOW_BG}  WARN  ${RESET} ${BOLD_YELLOW}%s${RESET}\n" "$1"
}

error() {
  printf "\n${RED_BG} ERROR ${RESET} ${BOLD_RED}%s${RESET}\n" "$1"
}