# CMakeLists.txt for Pawn Compiler JNI Wrapper

cmake_minimum_required(VERSION 3.22.1)
project("pawnc" C CXX)

# Source path
set(PAWNC_3107_SOURCE_DIR "${CMAKE_SOURCE_DIR}/../../../../compilers/pawnc-3.10.7/source")
set(PAWNC_31011_SOURCE_DIR "${CMAKE_SOURCE_DIR}/../../../../compilers/pawnc-3.10.11/source")

# Version 3.10.7
set(VERSION_MAJOR 3)
set(VERSION_MINOR 10)
set(VERSION_BUILD 7)
set(PAWNMC_PATCH 2)
set(VERSION_STR "${VERSION_MAJOR}.${VERSION_MINOR}.${VERSION_BUILD}-pawnmc.${PAWNMC_PATCH}")
math(EXPR VERSION_INT "${VERSION_MAJOR} << 8 | ${VERSION_MINOR}")
configure_file(
    "${PAWNC_3107_SOURCE_DIR}/compiler/version.h.in"
    "${CMAKE_CURRENT_BINARY_DIR}/pawnc3107/version.h"
)

# Version 3.10.11
set(VERSION_BUILD 11)
set(VERSION_STR "${VERSION_MAJOR}.${VERSION_MINOR}.${VERSION_BUILD}-pawnmc.${PAWNMC_PATCH}")
configure_file(
    "${PAWNC_31011_SOURCE_DIR}/compiler/version.h.in"
    "${CMAKE_CURRENT_BINARY_DIR}/pawnc31011/version.h"
)

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
add_library(pawnc3107 SHARED
    ${PAWNC_3107_SOURCES}
    ${CMAKE_CURRENT_SOURCE_DIR}/native-lib.cpp
)

target_include_directories(pawnc3107 PRIVATE
    ${CMAKE_CURRENT_BINARY_DIR}/pawnc3107
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
    ${CMAKE_CURRENT_SOURCE_DIR}/native-lib.cpp
)

target_include_directories(pawnc31011 PRIVATE
    ${CMAKE_CURRENT_BINARY_DIR}/pawnc31011
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
