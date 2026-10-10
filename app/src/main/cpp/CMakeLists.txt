# CMakeLists.txt for Pawn Compiler JNI Wrapper

cmake_minimum_required(VERSION 3.22.1)
project("pawnc" C CXX)

# Source path
set(PAWNC_3107_SOURCE_DIR "${CMAKE_SOURCE_DIR}/../../../../compilers/pawnc-3.10.7/source")
set(PAWNC_31011_SOURCE_DIR "${CMAKE_SOURCE_DIR}/../../../../compilers/pawnc-3.10.11/source")

set_property(DIRECTORY APPEND PROPERTY CMAKE_CONFIGURE_DEPENDS "${PAWNC_3107_SOURCE_DIR}/compiler/CMakeLists.txt")
set_property(DIRECTORY APPEND PROPERTY CMAKE_CONFIGURE_DEPENDS "${PAWNC_31011_SOURCE_DIR}/compiler/CMakeLists.txt")

# ---------------------------------------------------------------------------------
# Compiler version header
#
# The Pawn submodules ship `compiler/version.h.in`, but that template is only present in
# an initialised submodule, and PawnMC compiles the `compiler/` sources directly rather
# than through pawn-lang's own packaging helper. `version.h` therefore lives in
# `app/src/main/cpp` and both libraries include it from `${PAWN_VERSION_HEADER_DIR}`.
#
# The per-version numbers are still configured where a submodule is checked out: the
# `configure_file` calls emit `pawnc3107/version.h` / `pawnc31011/version.h` into the
# build tree, which sits *before* the source directory on the include path, so a full
# checkout keeps the submodule's own template while a fresh shallow clone still builds.
# ---------------------------------------------------------------------------------
set(PAWN_VERSION_HEADER_DIR "${CMAKE_CURRENT_SOURCE_DIR}")

# Version 3.10.7
set(VERSION_MAJOR 3)
set(VERSION_MINOR 10)
set(VERSION_BUILD 7)
set(VERSION_STR "${VERSION_MAJOR}.${VERSION_MINOR}.${VERSION_BUILD}")
math(EXPR VERSION_INT "${VERSION_MAJOR} << 8 | ${VERSION_MINOR}")
if(EXISTS "${PAWNC_3107_SOURCE_DIR}/compiler/version.h.in")
    configure_file(
        "${PAWNC_3107_SOURCE_DIR}/compiler/version.h.in"
        "${CMAKE_CURRENT_BINARY_DIR}/pawnc3107/version.h"
    )
    set(PAWN_VERSION_HEADER_3107 "${CMAKE_CURRENT_BINARY_DIR}/pawnc3107")
else()
    message(STATUS "pawnc-3.10.7 submodule missing version.h.in; using the bundled version.h")
    set(PAWN_VERSION_HEADER_3107 "${PAWN_VERSION_HEADER_DIR}")
endif()

# Version 3.10.11
set(VERSION_BUILD 11)
set(VERSION_STR "${VERSION_MAJOR}.${VERSION_MINOR}.${VERSION_BUILD}")
if(EXISTS "${PAWNC_31011_SOURCE_DIR}/compiler/version.h.in")
    configure_file(
        "${PAWNC_31011_SOURCE_DIR}/compiler/version.h.in"
        "${CMAKE_CURRENT_BINARY_DIR}/pawnc31011/version.h"
    )
    set(PAWN_VERSION_HEADER_31011 "${CMAKE_CURRENT_BINARY_DIR}/pawnc31011")
else()
    message(STATUS "pawnc-3.10.11 submodule missing version.h.in; using the bundled version.h")
    set(PAWN_VERSION_HEADER_31011 "${PAWN_VERSION_HEADER_DIR}")
endif()

# Common definition
set(COMMON_DEFINITIONS
    NO_MAIN
    NDEBUG
    LINUX
    PAWN_CELL_SIZE=32
    HAVE_UNISTD_H
    HAVE_INTTYPES_H
    HAVE_STDINT_H
    __BYTE_ORDER=1234 # Little-endian
    __LITTLE_ENDIAN=1234
    __BIG_ENDIAN=4321
)

set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} -O2 -flto")
set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -O2 -flto")
set(CMAKE_EXE_LINKER_FLAGS "${CMAKE_EXE_LINKER_FLAGS} -flto")
set(CMAKE_SHARED_LINKER_FLAGS "${CMAKE_SHARED_LINKER_FLAGS} -flto")

set(COMMON_C_FLAGS
    -Wno-implicit-function-declaration
    -Wno-int-conversion
    -Wno-pointer-sign
    -Wno-incompatible-pointer-types
    -Wno-format
    -Wno-unused-variable
    -Wno-unused-but-set-variable
    -Wno-parentheses
    -Wno-switch
    -Wno-dangling-else
    -Wno-empty-body
    -Wno-misleading-indentation
    -Wno-address-of-packed-member
    -fPIC
)

# Soruce files

set(PAWNC_3107_SOURCES
    ${PAWNC_3107_SOURCE_DIR}/compiler/sc1.c
    ${PAWNC_3107_SOURCE_DIR}/compiler/sc2.c
    ${PAWNC_3107_SOURCE_DIR}/compiler/sc3.c
    ${PAWNC_3107_SOURCE_DIR}/compiler/sc4.c
    ${PAWNC_3107_SOURCE_DIR}/compiler/sc5.c
    ${PAWNC_3107_SOURCE_DIR}/compiler/sc6.c
    ${PAWNC_3107_SOURCE_DIR}/compiler/sc7.c
    ${PAWNC_3107_SOURCE_DIR}/compiler/sci18n.c
    ${PAWNC_3107_SOURCE_DIR}/compiler/sclist.c
    ${PAWNC_3107_SOURCE_DIR}/compiler/scmemfil.c
    ${PAWNC_3107_SOURCE_DIR}/compiler/scstate.c
    ${PAWNC_3107_SOURCE_DIR}/compiler/scvars.c
    ${PAWNC_3107_SOURCE_DIR}/compiler/lstring.c
    ${PAWNC_3107_SOURCE_DIR}/compiler/memfile.c
    ${PAWNC_3107_SOURCE_DIR}/compiler/hashtable/wrap_hashtable.c
    ${PAWNC_3107_SOURCE_DIR}/linux/binreloc.c
)

set(PAWNC_31011_SOURCES
    ${PAWNC_31011_SOURCE_DIR}/compiler/sc1.c
    ${PAWNC_31011_SOURCE_DIR}/compiler/sc2.c
    ${PAWNC_31011_SOURCE_DIR}/compiler/sc3.c
    ${PAWNC_31011_SOURCE_DIR}/compiler/sc4.c
    ${PAWNC_31011_SOURCE_DIR}/compiler/sc5.c
    ${PAWNC_31011_SOURCE_DIR}/compiler/sc6.c
    ${PAWNC_31011_SOURCE_DIR}/compiler/sc7.c
    ${PAWNC_31011_SOURCE_DIR}/compiler/sci18n.c
    ${PAWNC_31011_SOURCE_DIR}/compiler/sclist.c
    ${PAWNC_31011_SOURCE_DIR}/compiler/scmemfil.c
    ${PAWNC_31011_SOURCE_DIR}/compiler/scstate.c
    ${PAWNC_31011_SOURCE_DIR}/compiler/scvars.c
    ${PAWNC_31011_SOURCE_DIR}/compiler/lstring.c
    ${PAWNC_31011_SOURCE_DIR}/compiler/memfile.c
    ${PAWNC_31011_SOURCE_DIR}/compiler/hashtable/wrap_hashtable.c
    ${PAWNC_31011_SOURCE_DIR}/linux/binreloc.c
)

# pawnc 3107
#
# The Pawn compiler itself comes from the `pawnc-3.10.7` submodule; everything under
# `CMAKE_CURRENT_SOURCE_DIR` is the PawnMC bridge, split by responsibility:
#
#   Compiler.cpp     JNI entry points, argument marshalling, cache-directory lookup
#   Diagnostics.cpp  source-argument classification, compile worker thread
#   Buffer.cpp       pc_printf / pc_error capture, warning and error counters
#   Source.cpp       pc_opensrc family backed by an in-memory script cache
#   Files.cpp        pc_openasm / pc_openbin / pc_createtmpsrc back ends
#   Bridge.h         the shared entry points; Compiler.h holds the shared defines
set(MC_BRIDGE_SOURCES
    ${CMAKE_CURRENT_SOURCE_DIR}/Compiler.cpp
    ${CMAKE_CURRENT_SOURCE_DIR}/Diagnostics.cpp
    ${CMAKE_CURRENT_SOURCE_DIR}/Buffer.cpp
    ${CMAKE_CURRENT_SOURCE_DIR}/Source.cpp
    ${CMAKE_CURRENT_SOURCE_DIR}/Files.cpp
)

add_library(pawnc3107 SHARED
    ${PAWNC_3107_SOURCES}
    ${MC_BRIDGE_SOURCES}
)

target_include_directories(pawnc3107 PRIVATE
    ${PAWN_VERSION_HEADER_3107}
    ${PAWNC_3107_SOURCE_DIR}/compiler
    ${PAWNC_3107_SOURCE_DIR}/linux
    ${PAWNC_3107_SOURCE_DIR}/amx
)

target_compile_definitions(pawnc3107 PRIVATE ${COMMON_DEFINITIONS})

target_compile_options(pawnc3107 PRIVATE
    $<$<COMPILE_LANGUAGE:C>:${COMMON_C_FLAGS}>
    $<$<COMPILE_LANGUAGE:CXX>:-std=c++17 -fPIC>
)

target_link_libraries(pawnc3107 android log)

# pawnc 31011
add_library(pawnc31011 SHARED
    ${PAWNC_31011_SOURCES}
    ${MC_BRIDGE_SOURCES}
)

target_include_directories(pawnc31011 PRIVATE
    ${PAWN_VERSION_HEADER_31011}
    ${PAWNC_31011_SOURCE_DIR}/compiler
    ${PAWNC_31011_SOURCE_DIR}/linux
    ${PAWNC_31011_SOURCE_DIR}/amx
)

target_compile_definitions(pawnc31011 PRIVATE
    ${COMMON_DEFINITIONS}
    sNAMEMAX=63
)

target_compile_options(pawnc31011 PRIVATE
    $<$<COMPILE_LANGUAGE:C>:${COMMON_C_FLAGS}>
    $<$<COMPILE_LANGUAGE:CXX>:-std=c++17 -fPIC>
)

target_link_libraries(pawnc31011 android log)
